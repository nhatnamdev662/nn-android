package app.nn.agent;

import android.content.Context;
import android.util.Log;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Handles Wireless Debugging pairing (Android 11+) over TLSv1.3 with system adbd.
 * Exports RFC 5705 keying material, executes SPAKE2 key exchange, and registers
 * the client's public key via AES-128-GCM encrypted PeerInfo framing.
 */
public final class AdbPairingClient implements Closeable {
    private static final String TAG = "AdbPairingClient";

    public static final byte PACKET_TYPE_SPAKE2_MSG = 0x00;
    public static final byte PACKET_TYPE_PEER_INFO  = 0x01;
    public static final byte PEER_INFO_TYPE_KEY     = 0x00; // ADB_RSA_PUB_KEY

    public interface PairingCallback {
        void onSuccess();
        void onFailure(String error, Throwable t);
    }

    private final String host;
    private final int port;
    private final String pairingCode;
    private final AdbKey adbKey;

    private Socket tcpSocket;
    private SSLSocket sslSocket;
    private DataInputStream dis;
    private DataOutputStream dos;

    public AdbPairingClient(String host, int port, String pairingCode, AdbKey adbKey) {
        this.host = host;
        this.port = port;
        this.pairingCode = pairingCode != null ? pairingCode.trim() : "";
        this.adbKey = adbKey;
    }

    public AdbPairingClient(String host, int port, String pairingCode) throws Exception {
        this(host, port, pairingCode, AdbKey.get());
    }

    /**
     * Static convenience method matching Interface Contracts in SCOPE.md.
     */
    public static boolean pair(String host, int port, String pairingCode) throws Exception {
        return pair(host, port, pairingCode, AdbKey.get());
    }

    public static boolean pair(String host, int port, String pairingCode, AdbKey key) throws Exception {
        AdbPairingClient client = new AdbPairingClient(host, port, pairingCode, key);
        try {
            client.pair();
            return true;
        } finally {
            client.close();
        }
    }

    /**
     * Executes the pairing exchange synchronously.
     * Blocks until completion or throws IOException / SecurityException.
     */
    public void pair() throws Exception {
        Log.i(TAG, "Initiating pairing with " + host + ":" + port);

        // 1. Establish TCP socket
        tcpSocket = new Socket();
        tcpSocket.setTcpNoDelay(true);
        tcpSocket.connect(new InetSocketAddress(host, port), 10000);

        // 2. Upgrade to TLSv1.3
        SSLSocketFactory sf = adbKey.getSslContext().getSocketFactory();
        sslSocket = (SSLSocket) sf.createSocket(tcpSocket, host, port, true);
        sslSocket.setEnabledProtocols(new String[]{ "TLSv1.3" });
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();

        dis = new DataInputStream(sslSocket.getInputStream());
        dos = new DataOutputStream(sslSocket.getOutputStream());

        // 3. Export RFC 5705 keying material (64 bytes)
        byte[] keyMaterial = exportKeyingMaterial(sslSocket, "adb-label\0", null, 64);
        if (keyMaterial == null || keyMaterial.length != 64) {
            throw new IOException("Failed to export 64-byte keying material from TLS session");
        }

        // 4. Construct SPAKE2 password = pairingCode + keyMaterial (70 bytes)
        byte[] codeBytes = pairingCode.getBytes(StandardCharsets.UTF_8);
        byte[] password = new byte[codeBytes.length + keyMaterial.length];
        System.arraycopy(codeBytes, 0, password, 0, codeBytes.length);
        System.arraycopy(keyMaterial, 0, password, codeBytes.length, keyMaterial.length);

        // 5. Initialize SPAKE2 context (Alice role)
        // Alice = "adb pair client", Bob = "adb pair server"
        AdbSpake2 spake = AdbSpake2.createAlice(password);
        byte[] clientSpakeMsg = spake.generateMessage();

        // 6. Send SPAKE2 message to server
        writePacket(PACKET_TYPE_SPAKE2_MSG, clientSpakeMsg);

        // 7. Receive SPAKE2 message from server
        byte[] serverSpakeMsg = readPacket(PACKET_TYPE_SPAKE2_MSG);

        // 8. Compute shared key & derive AES-128-GCM key via HKDF-SHA256
        byte[] sharedKey = spake.processMessage(serverSpakeMsg);
        byte[] aesKey = hkdfSha256(
                sharedKey,
                null, // Salt: null (32 zeroes)
                "adb pairing_auth aes-128-gcm key".getBytes(StandardCharsets.US_ASCII),
                16
        );

        AesGcmState cipherState = new AesGcmState(aesKey);

        // 9. Build PeerInfo (8192 bytes total)
        byte[] peerInfo = new byte[8192];
        peerInfo[0] = PEER_INFO_TYPE_KEY;
        byte[] pubkeyWire = adbKey.getAdbPublicKey();
        System.arraycopy(pubkeyWire, 0, peerInfo, 1, Math.min(pubkeyWire.length, 8191));

        // 10. Encrypt PeerInfo (8192 bytes + 16-byte tag = 8208 bytes)
        byte[] encryptedPeerInfo = cipherState.encrypt(peerInfo);
        writePacket(PACKET_TYPE_PEER_INFO, encryptedPeerInfo);

        // 11. Receive & decrypt server's PeerInfo
        byte[] serverEncrypted = readPacket(PACKET_TYPE_PEER_INFO);
        if (serverEncrypted.length != 8208) {
            throw new IOException("Unexpected server encrypted PeerInfo size: " + serverEncrypted.length);
        }

        byte[] serverPeerInfo = cipherState.decrypt(serverEncrypted);
        if (serverPeerInfo == null || serverPeerInfo.length == 0) {
            throw new IOException("Server returned empty PeerInfo");
        }
        Log.i(TAG, "Pairing exchange succeeded with " + host + ":" + port + " (peer type: " + serverPeerInfo[0] + ")");

        Log.i(TAG, "Pairing exchange succeeded with " + host + ":" + port);
    }

    private void writePacket(byte type, byte[] payload) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN);
        header.put((byte) 0x01); // Version
        header.put(type);        // Type
        header.putInt(payload.length);
        dos.write(header.array());
        dos.write(payload);
        dos.flush();
    }

    private byte[] readPacket(byte expectedType) throws IOException {
        byte[] header = new byte[6];
        dis.readFully(header);
        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        byte version = bb.get();
        byte type = bb.get();
        int size = bb.getInt();

        if (version != 1) {
            throw new IOException("Unsupported pairing packet version: " + version);
        }
        if (type != expectedType) {
            throw new IOException("Unexpected packet type: " + type + ", expected: " + expectedType);
        }
        if (size < 0 || size > 16384) {
            throw new IOException("Illegal packet payload size: " + size);
        }

        byte[] payload = new byte[size];
        dis.readFully(payload);
        return payload;
    }

    @Override
    public void close() {
        try { if (dis != null) dis.close(); } catch (Throwable ignored) {}
        try { if (dos != null) dos.close(); } catch (Throwable ignored) {}
        try { if (sslSocket != null) sslSocket.close(); } catch (Throwable ignored) {}
        try { if (tcpSocket != null) tcpSocket.close(); } catch (Throwable ignored) {}
    }

    // --- Cryptographic Helpers ---

    public static byte[] exportKeyingMaterial(SSLSocket socket, String label, byte[] context, int length) throws Exception {
        try {
            Class<?> c = Class.forName("android.net.ssl.SSLSockets");
            Method m = c.getMethod("exportKeyingMaterial", SSLSocket.class, String.class, byte[].class, int.class);
            byte[] res = (byte[]) m.invoke(null, socket, label, context, length);
            if (res != null) return res;
        } catch (Throwable ignored) {}

        try {
            Class<?> c = Class.forName("com.android.org.conscrypt.Conscrypt");
            Method m = c.getMethod("exportKeyingMaterial", SSLSocket.class, String.class, byte[].class, int.class);
            byte[] res = (byte[]) m.invoke(null, socket, label, context, length);
            if (res != null) return res;
        } catch (Throwable ignored) {}

        try {
            Class<?> c = Class.forName("org.conscrypt.Conscrypt");
            Method m = c.getMethod("exportKeyingMaterial", SSLSocket.class, String.class, byte[].class, int.class);
            byte[] res = (byte[]) m.invoke(null, socket, label, context, length);
            if (res != null) return res;
        } catch (Throwable ignored) {}

        Method m = socket.getClass().getMethod("exportKeyingMaterial", String.class, byte[].class, int.class);
        return (byte[]) m.invoke(socket, label, context, length);
    }

    public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) throws Exception {
        if (salt == null || salt.length == 0) {
            salt = new byte[32];
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);

        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info != null ? info : new byte[0]);
        mac.update((byte) 1);
        byte[] t1 = mac.doFinal();

        byte[] okm = new byte[length];
        System.arraycopy(t1, 0, okm, 0, length);
        return okm;
    }

    public static class AesGcmState {
        private final SecretKeySpec keySpec;
        private long sendCounter = 0;
        private long recvCounter = 0;

        public AesGcmState(byte[] key16) {
            this.keySpec = new SecretKeySpec(key16, "AES");
        }

        public byte[] encrypt(byte[] plaintext) throws Exception {
            byte[] nonce = makeNonce(sendCounter++);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, nonce));
            return cipher.doFinal(plaintext);
        }

        public byte[] decrypt(byte[] ciphertext) throws Exception {
            byte[] nonce = makeNonce(recvCounter++);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, nonce));
            return cipher.doFinal(ciphertext);
        }

        private static byte[] makeNonce(long counter) {
            byte[] nonce = new byte[12];
            ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN).putLong(counter);
            return nonce;
        }
    }

    /**
     * SPAKE25519 Key Agreement Implementation for ADB Wireless Debugging pairing.
     * Pure Java implementation with zero native dependencies, fully compatible with
     * AOSP adbd pairing_auth and BoringSSL.
     */
    public static final class AdbSpake2 {
        // Curve parameters for Ed25519: -x^2 + y^2 = 1 + d * x^2 * y^2 mod p
        private static final java.math.BigInteger P = java.math.BigInteger.valueOf(2).pow(255).subtract(java.math.BigInteger.valueOf(19));
        private static final java.math.BigInteger L = java.math.BigInteger.valueOf(2).pow(252).add(new java.math.BigInteger("27742317777372353535851937790883648493"));
        private static final java.math.BigInteger D = java.math.BigInteger.valueOf(-121665).multiply(java.math.BigInteger.valueOf(121666).modInverse(P)).mod(P);
        private static final java.math.BigInteger SQRT_M1 = java.math.BigInteger.valueOf(2).modPow(P.subtract(java.math.BigInteger.ONE).divide(java.math.BigInteger.valueOf(4)), P);

        // Fixed points N and M defined by BoringSSL SPAKE2
        private static final Point N_POINT = new Point(
            new java.math.BigInteger("49918732221787544735331783592030787422991506689877079631459872391322455579424"),
            new java.math.BigInteger("54629554431565467720832445949441049581317094546788069926228343916274969994000")
        );
        private static final Point M_POINT = new Point(
            new java.math.BigInteger("31406539342727633121250288103050113562375374900226415211311216773867585644232"),
            new java.math.BigInteger("21177308356423958466833845032658859666296341766942662650232962324899758529114")
        );

        // Ed25519 standard base point B
        private static final Point B_POINT;
        static {
            java.math.BigInteger yB = java.math.BigInteger.valueOf(4).multiply(java.math.BigInteger.valueOf(5).modInverse(P)).mod(P);
            java.math.BigInteger u = yB.multiply(yB).subtract(java.math.BigInteger.ONE).mod(P);
            java.math.BigInteger v = D.multiply(yB).multiply(yB).add(java.math.BigInteger.ONE).mod(P);
            java.math.BigInteger x2 = u.multiply(v.modInverse(P)).mod(P);
            java.math.BigInteger xB = x2.modPow(P.add(java.math.BigInteger.valueOf(3)).divide(java.math.BigInteger.valueOf(8)), P);
            if (!xB.multiply(xB).mod(P).equals(x2)) {
                xB = xB.multiply(SQRT_M1).mod(P);
            }
            if (xB.testBit(0)) {
                xB = P.subtract(xB);
            }
            B_POINT = new Point(xB, yB);
        }

        private static final byte[] CLIENT_NAME = "adb pair client\0".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] SERVER_NAME = "adb pair server\0".getBytes(StandardCharsets.US_ASCII);

        private byte[] myMsg;
        private byte[] passwordHash;
        private java.math.BigInteger passwordScalar;
        private java.math.BigInteger privateKey;
        private boolean processed = false;

        private AdbSpake2() {}

        public static AdbSpake2 createAlice(byte[] password) {
            if (password == null || password.length == 0) {
                throw new IllegalArgumentException("SPAKE2 password must not be empty");
            }
            try {
                AdbSpake2 spake = new AdbSpake2();

                // 1. Compute SHA-512 hash of password
                MessageDigest md512 = MessageDigest.getInstance("SHA-512");
                spake.passwordHash = md512.digest(password);

                // 2. Reduce modulo L and clear cofactor bits (BoringSSL parity)
                byte[] beHash = new byte[65];
                for (int i = 0; i < 64; i++) beHash[64 - i] = spake.passwordHash[i];
                java.math.BigInteger pwScalar = new java.math.BigInteger(beHash).mod(L);
                if (pwScalar.testBit(0)) pwScalar = pwScalar.add(L);
                if (pwScalar.testBit(1)) pwScalar = pwScalar.add(L.multiply(java.math.BigInteger.valueOf(2)));
                if (pwScalar.testBit(2)) pwScalar = pwScalar.add(L.multiply(java.math.BigInteger.valueOf(4)));
                spake.passwordScalar = pwScalar;

                // 3. Generate random private ephemeral key x_priv, multiplied by 8
                byte[] randBytes = new byte[64];
                new java.security.SecureRandom().nextBytes(randBytes);
                byte[] beRand = new byte[65];
                for (int i = 0; i < 64; i++) beRand[64 - i] = randBytes[i];
                spake.privateKey = new java.math.BigInteger(beRand).mod(L).multiply(java.math.BigInteger.valueOf(8));

                // 4. Compute P* = x_priv * B + passwordScalar * M
                Point P_pt = B_POINT.multiply(spake.privateKey);
                Point maskA = M_POINT.multiply(spake.passwordScalar);
                Point P_star = P_pt.add(maskA);
                spake.myMsg = P_star.encode();

                return spake;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize SPAKE2 Alice role", e);
            }
        }

        public byte[] generateMessage() {
            if (myMsg == null) {
                throw new IllegalStateException("SPAKE2 message not initialized or already destroyed");
            }
            return myMsg.clone();
        }

        public byte[] processMessage(byte[] theirMessage) {
            if (processed) {
                throw new IllegalStateException("processMessage can only be called once");
            }
            if (theirMessage == null || theirMessage.length != 32) {
                throw new IllegalArgumentException("theirMessage must be exactly 32 bytes");
            }
            try {
                // 1. Decode peer's point Q*
                Point Q_star = Point.decode(theirMessage);
                if (Q_star == null) {
                    throw new IllegalStateException("Invalid curve point received from server");
                }

                // 2. Unmask peer's value: Q = Q* - passwordScalar * N
                Point peersMask = N_POINT.multiply(passwordScalar);
                Point Q_recovered = Q_star.subtract(peersMask);

                // 3. Compute shared DH point K = x_priv * Q
                Point K = Q_recovered.multiply(privateKey);
                byte[] dhShared = K.encode();

                // 4. Compute transcript hash via SHA-512 with LE 64-bit length prefixes
                MessageDigest sha = MessageDigest.getInstance("SHA-512");
                updateWithPrefix(sha, CLIENT_NAME);
                updateWithPrefix(sha, SERVER_NAME);
                updateWithPrefix(sha, myMsg);
                updateWithPrefix(sha, theirMessage);
                updateWithPrefix(sha, dhShared);
                updateWithPrefix(sha, passwordHash);

                processed = true;
                return sha.digest(); // 64 bytes
            } catch (Exception e) {
                throw new IllegalStateException("SPAKE2 key agreement verification failed", e);
            } finally {
                destroy();
            }
        }

        public void destroy() {
            if (privateKey != null) {
                privateKey = java.math.BigInteger.ZERO;
            }
            if (passwordScalar != null) {
                passwordScalar = java.math.BigInteger.ZERO;
            }
            if (passwordHash != null) {
                java.util.Arrays.fill(passwordHash, (byte) 0);
            }
            if (myMsg != null) {
                java.util.Arrays.fill(myMsg, (byte) 0);
            }
        }

        private static void updateWithPrefix(MessageDigest md, byte[] data) {
            long len = data.length;
            byte[] lenLe = new byte[8];
            for (int i = 0; i < 8; i++) {
                lenLe[i] = (byte) (len & 0xFF);
                len >>>= 8;
            }
            md.update(lenLe);
            md.update(data);
        }

        private static final class Point {
            final java.math.BigInteger x;
            final java.math.BigInteger y;

            Point(java.math.BigInteger x, java.math.BigInteger y) {
                this.x = x.mod(P);
                this.y = y.mod(P);
            }

            Point add(Point o) {
                java.math.BigInteger dxy = D.multiply(x).multiply(o.x).multiply(y).multiply(o.y).mod(P);
                java.math.BigInteger numX = x.multiply(o.y).add(y.multiply(o.x)).mod(P);
                java.math.BigInteger denX = java.math.BigInteger.ONE.add(dxy).mod(P);
                java.math.BigInteger numY = y.multiply(o.y).add(x.multiply(o.x)).mod(P);
                java.math.BigInteger denY = java.math.BigInteger.ONE.subtract(dxy).mod(P);
                return new Point(numX.multiply(denX.modInverse(P)), numY.multiply(denY.modInverse(P)));
            }

            Point subtract(Point o) {
                return add(new Point(P.subtract(o.x), o.y));
            }

            Point multiply(java.math.BigInteger k) {
                Point res = new Point(java.math.BigInteger.ZERO, java.math.BigInteger.ONE);
                Point cur = this;
                for (int i = 0; i < k.bitLength(); i++) {
                    if (k.testBit(i)) res = res.add(cur);
                    cur = cur.add(cur);
                }
                return res;
            }

            byte[] encode() {
                byte[] be = y.toByteArray();
                byte[] le = new byte[32];
                for (int i = 0; i < be.length && i < 32; i++) {
                    le[i] = be[be.length - 1 - i];
                }
                if (x.testBit(0)) {
                    le[31] |= (byte) 0x80;
                }
                return le;
            }

            static Point decode(byte[] s) {
                if (s == null || s.length != 32) return null;
                byte[] copy = java.util.Arrays.copyOf(s, 32);
                int sign = (copy[31] & 0x80) != 0 ? 1 : 0;
                copy[31] &= 0x7F;
                byte[] be = new byte[33];
                for (int i = 0; i < 32; i++) {
                    be[32 - i] = copy[i];
                }
                java.math.BigInteger y = new java.math.BigInteger(be);
                if (y.compareTo(P) >= 0) return null;

                java.math.BigInteger u = y.multiply(y).subtract(java.math.BigInteger.ONE).mod(P);
                java.math.BigInteger v = D.multiply(y).multiply(y).add(java.math.BigInteger.ONE).mod(P);
                if (v.equals(java.math.BigInteger.ZERO)) {
                    return u.equals(java.math.BigInteger.ZERO) ? new Point(java.math.BigInteger.ZERO, java.math.BigInteger.ZERO) : null;
                }
                java.math.BigInteger x2 = u.multiply(v.modInverse(P)).mod(P);
                java.math.BigInteger x = x2.modPow(P.add(java.math.BigInteger.valueOf(3)).divide(java.math.BigInteger.valueOf(8)), P);
                if (!x.multiply(x).mod(P).equals(x2)) {
                    x = x.multiply(SQRT_M1).mod(P);
                    if (!x.multiply(x).mod(P).equals(x2)) {
                        return null;
                    }
                }
                if ((x.testBit(0) ? 1 : 0) != sign) {
                    x = P.subtract(x);
                }
                return new Point(x, y);
            }
        }
    }
}
