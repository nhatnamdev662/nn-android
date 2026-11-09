package app.nn.agent;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.ByteArrayInputStream;
import java.util.Base64;


import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.x500.X500Principal;

/**
 * Manages RSA-2048 identity keys, X.509 certificates, android_pubkey wire encoding,
 * and TLSv1.3 SSLContext for ADB Wireless Debugging pairing and loopback connections.
 */
public final class AdbKey {
    private static final String KEY_ALIAS = "buddy_adb_rsa_v1";
    private static final String KEY_NAME = "nn-agent@android";

    private static volatile AdbKey sInstance;

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final X509Certificate certificate;
    private final byte[] adbPublicKeyWire;
    private final SSLContext sslContext;

    public AdbKey(RSAPrivateKey privateKey, RSAPublicKey publicKey, X509Certificate cert) throws Exception {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.certificate = cert;
        this.adbPublicKeyWire = encodeAndroidPubkey(publicKey, KEY_NAME);
        this.sslContext = initSslContext(privateKey, cert);
    }

    public static AdbKey get() throws Exception {
        return get(null);
    }

    public static AdbKey get(Context context) throws Exception {
        if (sInstance == null) {
            synchronized (AdbKey.class) {
                if (sInstance == null) {
                    sInstance = loadOrCreate(context);
                }
            }
        }
        return sInstance;
    }

    public static SSLContext getSslContextStatic() throws Exception {
        return get().getSslContext();
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public byte[] getAdbPublicKey() {
        return adbPublicKeyWire;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    private static final String PREF_PRIV_KEY = "adb_priv_key_pk8_b64";
    private static final String PREF_CERT = "adb_cert_der_b64";
    private static final String FILE_PRIV_KEY = "adb_key.pk8";
    private static final String FILE_CERT = "adb_cert.der";

    private static AdbKey loadOrCreate(Context context) throws Exception {
        Context ctx = context != null ? context : BuddyApp.ctx;

        byte[] privBytes = null;
        byte[] certBytes = null;

        if (ctx != null) {
            java.io.File keyFile = new java.io.File(ctx.getFilesDir(), FILE_PRIV_KEY);
            java.io.File certFile = new java.io.File(ctx.getFilesDir(), FILE_CERT);
            if (keyFile.exists() && certFile.exists()) {
                try {
                    privBytes = readFileBytes(keyFile);
                    certBytes = readFileBytes(certFile);
                } catch (Throwable ignored) {}
            }

            if (privBytes == null || certBytes == null) {
                try {
                    android.content.SharedPreferences sp = ctx.getSharedPreferences("app.nn.agent_preferences", Context.MODE_PRIVATE);
                    String privB64 = sp.getString(PREF_PRIV_KEY, null);
                    String certB64 = sp.getString(PREF_CERT, null);
                    if (privB64 != null && certB64 != null) {
                        privBytes = Base64.getDecoder().decode(privB64);
                        certBytes = Base64.getDecoder().decode(certB64);
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (privBytes != null && certBytes != null) {
            try {
                java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(privBytes);
                java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
                RSAPrivateKey privKey = (RSAPrivateKey) kf.generatePrivate(spec);
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
                RSAPublicKey pubKey = (RSAPublicKey) cert.getPublicKey();
                return new AdbKey(privKey, pubKey, cert);
            } catch (Throwable t) {
                android.util.Log.w("AdbKey", "Failed to restore persistent ADB key, generating new one", t);
            }
        }

        // Generate persistent software RSA-2048 keypair (AxManager / Shizuku architecture)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPrivateKey privKey = (RSAPrivateKey) kp.getPrivate();
        RSAPublicKey pubKey = (RSAPublicKey) kp.getPublic();
        X509Certificate cert = generateSelfSignedCertificate(kp);

        if (ctx != null) {
            try {
                byte[] encPriv = privKey.getEncoded();
                byte[] encCert = cert.getEncoded();
                java.io.File keyFile = new java.io.File(ctx.getFilesDir(), FILE_PRIV_KEY);
                java.io.File certFile = new java.io.File(ctx.getFilesDir(), FILE_CERT);
                writeFileBytes(keyFile, encPriv);
                writeFileBytes(certFile, encCert);

                android.content.SharedPreferences sp = ctx.getSharedPreferences("app.nn.agent_preferences", Context.MODE_PRIVATE);
                sp.edit()
                        .putString(PREF_PRIV_KEY, Base64.getEncoder().encodeToString(encPriv))
                        .putString(PREF_CERT, Base64.getEncoder().encodeToString(encCert))
                        .apply();
            } catch (Throwable t) {
                android.util.Log.w("AdbKey", "Failed to persist ADB key", t);
            }
        }

        return new AdbKey(privKey, pubKey, cert);
    }

    private static byte[] readFileBytes(java.io.File file) throws Exception {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static void writeFileBytes(java.io.File file, byte[] data) throws Exception {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
            fos.write(data);
            fos.flush();
        }
    }

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privKey = (RSAPrivateKey) keyPair.getPrivate();

        // Minimal DER structure for self-signed X.509v3 cert
        byte[] encoded = buildSelfSignedDer(pubKey, privKey);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static byte[] buildSelfSignedDer(RSAPublicKey pubKey, RSAPrivateKey privKey) throws Exception {
        // Serial number: 1
        byte[] serial = new byte[]{ 0x02, 0x01, 0x01 };

        // Signature Algorithm: SHA256withRSA (OID 1.2.840.113549.1.1.11)
        byte[] sigAlg = new byte[]{
            0x30, 0x0D,
            0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B,
            0x05, 0x00
        };

        // Issuer & Subject: CN=buddy
        byte[] name = new byte[]{
            0x30, 0x13,
            0x31, 0x11,
            0x30, 0x0F,
            0x06, 0x03, 0x55, 0x04, 0x03,
            0x0C, 0x08, 'b', 'u', 'd', 'd', 'y', 'a', 'd', 'b'
        };

        // Validity: 2026-01-01 to 2049-12-31 23:59:59 UTC (UTCTime per RFC 5280, YY < 50 is 20YY)
        byte[] validity = new byte[]{
            0x30, 0x1E,
            0x17, 0x0D, '2', '6', '0', '1', '0', '1', '0', '0', '0', '0', '0', '0', 'Z',
            0x17, 0x0D, '4', '9', '1', '2', '3', '1', '2', '3', '5', '9', '5', '9', 'Z'
        };

        // SubjectPublicKeyInfo (from pubKey.getEncoded())
        byte[] spki = pubKey.getEncoded();

        // TBSCertificate SEQUENCE
        int tbsLen = serial.length + sigAlg.length + name.length + validity.length + name.length + spki.length;
        byte[] tbsHeader = encodeDerLength(0x30, tbsLen);
        ByteBuffer tbsBuf = ByteBuffer.allocate(tbsHeader.length + tbsLen);
        tbsBuf.put(tbsHeader);
        tbsBuf.put(serial);
        tbsBuf.put(sigAlg);
        tbsBuf.put(name);
        tbsBuf.put(validity);
        tbsBuf.put(name);
        tbsBuf.put(spki);
        byte[] tbsBytes = tbsBuf.array();

        // Sign TBS with SHA256withRSA
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initSign(privKey);
        sig.update(tbsBytes);
        byte[] signature = sig.sign();

        // Signature BIT STRING
        byte[] sigBitStringHeader = encodeDerLength(0x03, signature.length + 1);
        byte[] sigBitString = new byte[sigBitStringHeader.length + 1 + signature.length];
        System.arraycopy(sigBitStringHeader, 0, sigBitString, 0, sigBitStringHeader.length);
        sigBitString[sigBitStringHeader.length] = 0x00; // unused bits
        System.arraycopy(signature, 0, sigBitString, sigBitStringHeader.length + 1, signature.length);

        // Certificate SEQUENCE
        int certLen = tbsBytes.length + sigAlg.length + sigBitString.length;
        byte[] certHeader = encodeDerLength(0x30, certLen);
        ByteBuffer certBuf = ByteBuffer.allocate(certHeader.length + certLen);
        certBuf.put(certHeader);
        certBuf.put(tbsBytes);
        certBuf.put(sigAlg);
        certBuf.put(sigBitString);

        return certBuf.array();
    }

    private static byte[] encodeDerLength(int tag, int length) {
        if (length < 128) {
            return new byte[]{ (byte) tag, (byte) length };
        } else if (length < 256) {
            return new byte[]{ (byte) tag, (byte) 0x81, (byte) length };
        } else {
            return new byte[]{ (byte) tag, (byte) 0x82, (byte) (length >> 8), (byte) (length & 0xFF) };
        }
    }

    /**
     * Encodes RSA public key into standard 524-byte android_pubkey binary format,
     * base64 encodes it, and appends " " + keyName + "\0".
     */
    public static byte[] encodeAndroidPubkey(RSAPublicKey pubKey, String keyName) {
        BigInteger n = pubKey.getModulus();
        BigInteger e = pubKey.getPublicExponent();

        // 1. b32 = 2^32
        BigInteger b32 = BigInteger.ZERO.setBit(32);

        // 2. n0inv = -1 / N[0] mod 2^32
        BigInteger n0inv = n.remainder(b32).modInverse(b32).negate();

        // 3. rr = (2^2048)^2 mod N
        BigInteger r = BigInteger.ZERO.setBit(2048);
        BigInteger rr = r.modPow(BigInteger.valueOf(2), n);

        // 4. Serialize 524 bytes in Little-Endian byte order
        ByteBuffer buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(64); // modulus_size_words
        buf.putInt(n0inv.intValue());

        // Modulus: 64 words
        for (int i = 0; i < 64; i++) {
            buf.putInt(n.shiftRight(i * 32).intValue());
        }

        // rr: 64 words
        for (int i = 0; i < 64; i++) {
            buf.putInt(rr.shiftRight(i * 32).intValue());
        }

        // Exponent
        buf.putInt(e.intValue());

        // 5. Base64 encode and append " " + keyName + "\0"
        byte[] b64 = Base64.getEncoder().encode(buf.array());
        byte[] nameBytes = (" " + keyName + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] wire = new byte[b64.length + nameBytes.length];
        System.arraycopy(b64, 0, wire, 0, b64.length);
        System.arraycopy(nameBytes, 0, wire, b64.length, nameBytes.length);
        return wire;
    }

    private static SSLContext initSslContext(RSAPrivateKey privKey, X509Certificate cert) throws Exception {
        SSLContext sc = SSLContext.getInstance("TLSv1.3");
        sc.init(
                new KeyManager[]{ new AdbKeyManager(privKey, cert) },
                new TrustManager[]{ new AdbTrustManager() },
                new SecureRandom()
        );
        return sc;
    }

    private static class AdbKeyManager extends X509ExtendedKeyManager {
        private final String alias = "adbkey";
        private final RSAPrivateKey privateKey;
        private final X509Certificate[] certChain;

        AdbKeyManager(RSAPrivateKey privateKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certChain = new X509Certificate[]{ certificate };
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, java.net.Socket socket) {
            return alias;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            return alias;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return this.alias.equals(alias) ? certChain : null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return this.alias.equals(alias) ? privateKey : null;
        }

        @Override public String[] getClientAliases(String keyType, Principal[] issuers) { return new String[]{ alias }; }
        @Override public String[] getServerAliases(String keyType, Principal[] issuers) { return null; }
        @Override public String chooseServerAlias(String keyType, Principal[] issuers, java.net.Socket socket) { return null; }
        @Override public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) { return null; }
    }

    private static class AdbTrustManager extends X509ExtendedTrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {}
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}
