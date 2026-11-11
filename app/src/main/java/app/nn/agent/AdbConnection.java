package app.nn.agent;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * High-performance, loopback ADB client for Android 11+ Wireless Debugging.
 * Handles TCP socket connection, in-place TLSv1.3 upgrade (A_STLS),
 * shell channel multiplexing (A_OPEN, A_WRTE, A_OKAY, A_CLSE),
 * shell_v2 demuxing, dynamic timeouts, and security guardrails.
 */
public final class AdbConnection {

    public static class ExecutionResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final long executionTimeMs;
        public final byte[] rawStdout;

        public ExecutionResult(int exitCode, String stdout, String stderr, long executionTimeMs) {
            this(exitCode, stdout, stderr, executionTimeMs, null);
        }

        public ExecutionResult(int exitCode, String stdout, String stderr, long executionTimeMs, byte[] rawStdout) {
            this.exitCode = exitCode;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
            this.executionTimeMs = executionTimeMs;
            this.rawStdout = rawStdout;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        @Override
        public String toString() {
            return "ExecutionResult{exitCode=" + exitCode
                    + ", stdout='" + stdout + '\''
                    + ", stderr='" + stderr + '\''
                    + ", executionTimeMs=" + executionTimeMs + '}';
        }
    }

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        TLS_UPGRADING,
        CONNECTED,
        ERROR
    }

    public interface DisconnectListener {
        void onDisconnected(String reason);
    }

    private static volatile DisconnectListener sDisconnectListener;

    public static void setDisconnectListener(DisconnectListener listener) {
        sDisconnectListener = listener;
    }

    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int HANDSHAKE_TIMEOUT_MS = 8000;
    private static final int DEFAULT_EXEC_TIMEOUT_MS = 15000;
    private static final int MAX_TEXT_OUTPUT_BYTES = 1024 * 1024;
    private static final int MAX_RAW_OUTPUT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STDERR_BYTES = 256 * 1024;

    // =========================================================================
    // Destructive Command Blacklist & Sanitization Guardrails
    // =========================================================================
    private static final Pattern CMD_DELIMITERS = Pattern.compile("(?:\\s*(?:&&|\\|\\||[;&|\\n\\r])\\s*)+");
    private static final Pattern BACKTICKS = Pattern.compile("`([^`]+)`");
    private static final Pattern SHELL_WRAPPERS = Pattern.compile(
        "(?:(?:sh|bash|mksh|su)\\s+(?:-[a-zA-Z0-9]*c[a-zA-Z0-9]*\\s+)?|eval\\s+)['\"]([^'\"]+)['\"]",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UNQUOTED_SHELL = Pattern.compile(
        "^(?:sh|bash|mksh|su)\\s+-[a-zA-Z0-9]*c[a-zA-Z0-9]*\\s+(.*)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UNQUOTED_EVAL = Pattern.compile(
        "^eval\\s+(.*)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRANSPARENT_PREFIXES = Pattern.compile(
        "^(?:/system/bin/|/bin/|busybox\\s+|toybox\\s+|nohup\\s+|exec\\s+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REBOOT_PATTERN = Pattern.compile(
        "^(reboot(\\s+.*)?|recovery(\\s+.*)?|fastboot(\\s+.*)?|svc\\s+power\\s+(reboot|shutdown)(\\s+.*)?)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WIPE_PATTERN = Pattern.compile(
        "^(wipe(\\s+.*)?|format(\\s+.*)?|mkfs(\\..*)?(\\s+.*)?|make_f2fs(\\s+.*)?|mke2fs(\\s+.*)?)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PM_PATTERN = Pattern.compile(
        "^pm\\s+(clear|uninstall)(\\s+.*)?$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SETENFORCE_PATTERN = Pattern.compile(
        "^setenforce\\s+(0|permissive)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RECURSIVE_FLAG = Pattern.compile("^-[a-zA-Z]*[rR]");
    private static final Pattern FORCE_FLAG = Pattern.compile("^-[a-zA-Z]*[fF]");

    private static final String[] CRITICAL_ROOTS = {
        "/system", "/data", "/sdcard", "/storage", "/vendor", "/product", "/apex"
    };

    private static final ReentrantLock EXECUTION_LOCK = new ReentrantLock();
    private static final AtomicInteger NEXT_LOCAL_ID = new AtomicInteger(1);

    // Active connection state
    private static Socket sRawSocket;
    private static SSLSocket sTlsSocket;
    private static InputStream sInputStream;
    private static OutputStream sOutputStream;
    private static String sConnectedHost;
    private static int sConnectedPort = -1;
    private static boolean sSupportsShellV2 = false;
    private static volatile ConnectionState sState = ConnectionState.DISCONNECTED;

    private AdbConnection() {}

    /**
     * Checks whether the ADB client currently has an active, authenticated connection.
     * Supports both TLS-upgraded connections (sTlsSocket) and unencrypted TCP connections (sRawSocket).
     */
    public static synchronized boolean isConnected() {
        if (sState != ConnectionState.CONNECTED) {
            return false;
        }
        if (sTlsSocket != null) {
            return !sTlsSocket.isClosed() && sTlsSocket.isConnected();
        }
        return sRawSocket != null && !sRawSocket.isClosed() && sRawSocket.isConnected();
    }

    public static synchronized String getConnectedHost() {
        return sConnectedHost;
    }

    public static synchronized int getConnectedPort() {
        return sConnectedPort;
    }

    public static synchronized ConnectionState getState() {
        return sState;
    }

    /**
     * Establishes a connection to the on-device ADB daemon at host:port.
     * Performs plain TCP connection, A_CNXN handshake, TLS upgrade (A_STLS),
     * and banner validation.
     *
     * Acquires EXECUTION_LOCK to ensure mutual exclusion with command execution.
     */
    public static void connect(String host, int port) throws IOException {
        EXECUTION_LOCK.lock();
        try {
            synchronized (AdbConnection.class) {
                if (isConnected() && host.equals(sConnectedHost) && port == sConnectedPort) {
                    return; // Already connected to target
                }
                disconnectInternal();
                sState = ConnectionState.CONNECTING;
                sConnectedHost = host;
                sConnectedPort = port;
            }

            try {
                // 1. Establish raw TCP loopback socket
                Socket rawSocket = new Socket();
                rawSocket.setTcpNoDelay(true);
                rawSocket.setKeepAlive(true);
                rawSocket.setSendBufferSize(256 * 1024);
                rawSocket.setReceiveBufferSize(256 * 1024);
                rawSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                rawSocket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

                OutputStream rawOut = rawSocket.getOutputStream();
                InputStream rawIn = rawSocket.getInputStream();

                // 2. Send initial A_CNXN message
                byte[] banner = "host::\0".getBytes(StandardCharsets.UTF_8);
                AdbMessage cnxn = new AdbMessage(
                    AdbProtocol.A_CNXN,
                    AdbProtocol.A_VERSION,
                    AdbProtocol.A_MAXDATA,
                    banner
                );
                cnxn.write(rawOut);
                rawOut.flush();

                // 3. Read initial reply from adbd (handle legacy RSA A_AUTH challenge if present)
                AdbMessage reply = AdbMessage.read(rawIn);
                boolean sentSignature = false;
                boolean sentPubKey = false;
                while (reply.command == AdbProtocol.A_AUTH) {
                    if (reply.arg0 != AdbProtocol.AUTH_TYPE_TOKEN) {
                        rawSocket.close();
                        throw new IOException("Unsupported A_AUTH type: " + reply.arg0);
                    }
                    AdbKey key;
                    try {
                        key = AdbKey.get();
                    } catch (Exception e) {
                        rawSocket.close();
                        throw new IOException("Failed to load AdbKey for AUTH: " + e.getMessage(), e);
                    }
                    if (!sentSignature) {
                        byte[] sig;
                        try {
                            sig = key.signToken(reply.payload);
                        } catch (Exception e) {
                            rawSocket.close();
                            throw new IOException("Failed to sign ADB AUTH token: " + e.getMessage(), e);
                        }
                        new AdbMessage(AdbProtocol.A_AUTH, AdbProtocol.AUTH_TYPE_SIGNATURE, 0, sig).write(rawOut);
                        rawOut.flush();
                        sentSignature = true;
                        reply = AdbMessage.read(rawIn);
                    } else if (!sentPubKey) {
                        // Device didn't recognize our signature yet: send public key and allow brief user confirmation
                        rawSocket.setSoTimeout(3000);
                        new AdbMessage(AdbProtocol.A_AUTH, AdbProtocol.AUTH_TYPE_RSAPUBLICKEY, 0, key.getAdbPublicKey()).write(rawOut);
                        rawOut.flush();
                        sentPubKey = true;
                        reply = AdbMessage.read(rawIn);
                    } else {
                        rawSocket.close();
                        throw new IOException("ADB RSA authentication rejected");
                    }
                }

                SSLSocket tlsSocket = null;
                InputStream inStream;
                OutputStream outStream;
                boolean supportsShellV2;

                if (reply.command == AdbProtocol.A_STLS) {
                    synchronized (AdbConnection.class) {
                        sState = ConnectionState.TLS_UPGRADING;
                    }

                    // Send matching A_STLS response
                    AdbMessage stlsReply = new AdbMessage(
                        AdbProtocol.A_STLS,
                        AdbProtocol.STLS_VERSION,
                        0,
                        new byte[0]
                    );
                    stlsReply.write(rawOut);
                    rawOut.flush();

                    // 4. In-place upgrade to SSLSocket
                    SSLContext sslContext;
                    try {
                        sslContext = AdbKey.get().getSslContext();
                    } catch (Exception e) {
                        rawSocket.close();
                        throw new IOException("Failed to initialize SSLContext from AdbKey: " + e.getMessage(), e);
                    }

                    SSLSocketFactory factory = sslContext.getSocketFactory();
                    tlsSocket = (SSLSocket) factory.createSocket(rawSocket, host, port, true);
                    tlsSocket.setUseClientMode(true);
                    tlsSocket.setEnabledProtocols(new String[]{"TLSv1.3"});
                    tlsSocket.setTcpNoDelay(true);
                    tlsSocket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                    tlsSocket.startHandshake();

                    inStream = tlsSocket.getInputStream();
                    outStream = tlsSocket.getOutputStream();

                    // 5. Read post-TLS A_CNXN device banner
                    AdbMessage postTlsCnxn = AdbMessage.read(inStream);
                    if (postTlsCnxn.command != AdbProtocol.A_CNXN) {
                        tlsSocket.close();
                        rawSocket.close();
                        throw new IOException("Expected post-TLS A_CNXN, received command: 0x"
                                + Integer.toHexString(postTlsCnxn.command));
                    }

                    String bannerStr = new String(postTlsCnxn.payload, StandardCharsets.UTF_8);
                    supportsShellV2 = bannerStr.contains("shell_v2");
                    tlsSocket.setSoTimeout(0);

                } else if (reply.command == AdbProtocol.A_CNXN) {
                    // Plain unencrypted connection accepted (e.g. emulator or legacy adbd)
                    inStream = rawIn;
                    outStream = rawOut;
                    String bannerStr = new String(reply.payload, StandardCharsets.UTF_8);
                    supportsShellV2 = bannerStr.contains("shell_v2");
                    rawSocket.setSoTimeout(0);
                } else {
                    rawSocket.close();
                    throw new IOException("Unexpected handshake response command: 0x"
                            + Integer.toHexString(reply.command));
                }

                synchronized (AdbConnection.class) {
                    sRawSocket = rawSocket;
                    sTlsSocket = tlsSocket;
                    sInputStream = inStream;
                    sOutputStream = outStream;
                    sSupportsShellV2 = supportsShellV2;
                    sState = ConnectionState.CONNECTED;
                }

            } catch (Throwable t) {
                disconnect();
                synchronized (AdbConnection.class) {
                    sState = ConnectionState.ERROR;
                }
                throw (t instanceof IOException) ? (IOException) t : new IOException("Connection failed: " + t.getMessage(), t);
            }
        } finally {
            EXECUTION_LOCK.unlock();
        }
    }

    /**
     * Closes the active ADB connection and releases all socket and stream resources.
     * Sockets are closed immediately to interrupt any thread waiting on blocking I/O.
     */
    public static synchronized void disconnect() {
        disconnectInternal();
    }

    private static void disconnectInternal() {
        sState = ConnectionState.DISCONNECTED;
        sSupportsShellV2 = false;

        if (sInputStream != null) {
            try { sInputStream.close(); } catch (Throwable ignored) {}
            sInputStream = null;
        }
        if (sOutputStream != null) {
            try { sOutputStream.close(); } catch (Throwable ignored) {}
            sOutputStream = null;
        }
        if (sTlsSocket != null) {
            try { sTlsSocket.close(); } catch (Throwable ignored) {}
            sTlsSocket = null;
        }
        if (sRawSocket != null) {
            try { sRawSocket.close(); } catch (Throwable ignored) {}
            sRawSocket = null;
        }
    }

    /**
     * Executes a shell command on the connected Android system and decodes stdout/stderr as UTF-8 text.
     * Uses monotonic clock (System.nanoTime()) for all timeout accounting.
     */
    public static ExecutionResult execute(String command, int timeoutMs) throws IOException {
        return executeInternal(command, timeoutMs, false);
    }

    /**
     * Executes a shell command and returns raw stdout bytes (useful for binary output such as {@code screencap -p}),
     * or null if execution failed or exited non-zero.
     */
    public static byte[] executeRaw(String command, int timeoutMs) {
        try {
            ExecutionResult res = executeInternal(command, timeoutMs, true);
            return res.exitCode == 0 ? res.rawStdout : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ExecutionResult executeInternal(String command, int timeoutMs, boolean binaryRaw) throws IOException {
        long startNano = System.nanoTime();

        if (command == null || command.trim().isEmpty()) {
            return new ExecutionResult(0, "", "", 0, binaryRaw ? new byte[0] : null);
        }

        // 1. Guardrail validation
        if (isDangerousCommand(command)) {
            return new ExecutionResult(-2, "", "Blocked by security guardrails: dangerous command detected", 0);
        }

        // 2. Connection state check
        if (!isConnected()) {
            return new ExecutionResult(-1, "", "ADB not connected", 0);
        }

        int effectiveTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_EXEC_TIMEOUT_MS;
        int maxStdout = binaryRaw ? MAX_RAW_OUTPUT_BYTES : MAX_TEXT_OUTPUT_BYTES;

        EXECUTION_LOCK.lock();
        try {
            if (!isConnected()) {
                return new ExecutionResult(-1, "", "ADB not connected", 0);
            }

            int localId = allocateLocalId();
            int remoteId = 0;
            boolean channelOpen = false;

            ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
            boolean stdoutTruncated = false;
            boolean stderrTruncated = false;
            int exitCode = 0;

            // Persistent streaming state for shell,v2,raw frames spanning A_WRTE chunks
            byte[] v2Header = new byte[5];
            int v2HeaderPos = 0;
            int v2PacketId = 0;
            int v2Remaining = 0;
            boolean v2GotExitByte = false;

            try {
                // Determine destination string (prefer shell,v2,raw: if supported)
                String destination = (sSupportsShellV2 ? "shell,v2,raw:" : "shell:") + command + "\0";
                byte[] destBytes = destination.getBytes(StandardCharsets.UTF_8);

                // Send A_OPEN packet
                AdbMessage openMsg = new AdbMessage(
                    AdbProtocol.A_OPEN,
                    localId,
                    0,
                    destBytes
                );
                openMsg.write(sOutputStream);
                sOutputStream.flush();

                // Wait for A_OKAY or A_CLSE
                while (true) {
                    long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;
                    long remaining = effectiveTimeout - elapsedMs;
                    if (remaining <= 0) {
                        throw new SocketTimeoutException("Timed out waiting for channel open");
                    }
                    setSocketReadTimeout((int) remaining);

                    AdbMessage msg = AdbMessage.read(sInputStream);
                    if (msg.arg1 != localId) {
                        if (msg.command == AdbProtocol.A_WRTE && msg.arg0 != 0 && msg.arg1 != 0) {
                            tryCloseStaleChannel(msg.arg1, msg.arg0);
                        }
                        continue; // Message belongs to another/stale channel
                    }

                    if (msg.command == AdbProtocol.A_OKAY) {
                        remoteId = msg.arg0;
                        channelOpen = true;
                        break;
                    } else if (msg.command == AdbProtocol.A_CLSE) {
                        long elapsed = (System.nanoTime() - startNano) / 1_000_000L;
                        return new ExecutionResult(-4, "", "Channel open rejected by adbd", elapsed);
                    }
                }

                // Process streaming chunks (A_WRTE) until A_CLSE
                while (true) {
                    long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;
                    long remaining = effectiveTimeout - elapsedMs;
                    if (remaining <= 0) {
                        throw new SocketTimeoutException("Execution timed out");
                    }
                    setSocketReadTimeout((int) remaining);

                    AdbMessage msg = AdbMessage.read(sInputStream);
                    if (msg.arg1 != localId) {
                        if (msg.command == AdbProtocol.A_WRTE && msg.arg0 != 0 && msg.arg1 != 0) {
                            tryCloseStaleChannel(msg.arg1, msg.arg0);
                        }
                        continue;
                    }

                    if (msg.command == AdbProtocol.A_WRTE) {
                        // Immediately acknowledge receipt with A_OKAY to open flow window
                        AdbMessage ack = new AdbMessage(
                            AdbProtocol.A_OKAY,
                            localId,
                            remoteId,
                            new byte[0]
                        );
                        ack.write(sOutputStream);
                        sOutputStream.flush();

                        // Demux payload across A_WRTE boundaries
                        if (msg.payload.length > 0) {
                            if (sSupportsShellV2) {
                                int pos = 0;
                                while (pos < msg.payload.length) {
                                    if (v2Remaining == 0) {
                                        int take = Math.min(5 - v2HeaderPos, msg.payload.length - pos);
                                        System.arraycopy(msg.payload, pos, v2Header, v2HeaderPos, take);
                                        pos += take;
                                        v2HeaderPos += take;
                                        if (v2HeaderPos == 5) {
                                            v2PacketId = v2Header[0] & 0xFF;
                                            int frameLen = (v2Header[1] & 0xFF)
                                                    | ((v2Header[2] & 0xFF) << 8)
                                                    | ((v2Header[3] & 0xFF) << 16)
                                                    | ((v2Header[4] & 0xFF) << 24);
                                            v2HeaderPos = 0;
                                            if (frameLen < 0) {
                                                break;
                                            }
                                            v2Remaining = frameLen;
                                            v2GotExitByte = false;
                                        }
                                    } else {
                                        int take = Math.min(v2Remaining, msg.payload.length - pos);
                                        if (v2PacketId == AdbProtocol.SHELL_V2_STDOUT) {
                                            stdoutTruncated |= appendBounded(stdoutBytes, msg.payload, pos, take, maxStdout);
                                        } else if (v2PacketId == AdbProtocol.SHELL_V2_STDERR) {
                                            stderrTruncated |= appendBounded(stderrBytes, msg.payload, pos, take, MAX_STDERR_BYTES);
                                        } else if (v2PacketId == AdbProtocol.SHELL_V2_EXIT && !v2GotExitByte && take > 0) {
                                            exitCode = msg.payload[pos] & 0xFF;
                                            v2GotExitByte = true;
                                        }
                                        pos += take;
                                        v2Remaining -= take;
                                    }
                                }
                            } else {
                                stdoutTruncated |= appendBounded(stdoutBytes, msg.payload, 0, msg.payload.length, maxStdout);
                            }
                        }

                    } else if (msg.command == AdbProtocol.A_CLSE) {
                        // Command finished; acknowledge channel closure
                        AdbMessage clseReply = new AdbMessage(
                            AdbProtocol.A_CLSE,
                            localId,
                            remoteId,
                            new byte[0]
                        );
                        clseReply.write(sOutputStream);
                        sOutputStream.flush();
                        channelOpen = false;
                        break;
                    }
                }

                long elapsed = (System.nanoTime() - startNano) / 1_000_000L;
                return buildResult(exitCode, stdoutBytes, stderrBytes, stdoutTruncated, stderrTruncated, elapsed, binaryRaw);

            } catch (SocketTimeoutException ste) {
                if (channelOpen) {
                    try {
                        AdbMessage clse = new AdbMessage(AdbProtocol.A_CLSE, localId, remoteId, new byte[0]);
                        clse.write(sOutputStream);
                        sOutputStream.flush();
                    } catch (Throwable ignored) {}
                }
                long elapsed = (System.nanoTime() - startNano) / 1_000_000L;
                String partialOut = binaryRaw ? "" : new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8);
                return new ExecutionResult(
                    -3,
                    partialOut,
                    "Execution timed out after " + effectiveTimeout + "ms",
                    elapsed
                );

            } catch (Throwable t) {
                disconnect();
                DisconnectListener dl = sDisconnectListener;
                if (dl != null) {
                    try {
                        dl.onDisconnected(t.getMessage());
                    } catch (Throwable ignored) {}
                }
                long elapsed = (System.nanoTime() - startNano) / 1_000_000L;
                String partialOut = binaryRaw ? "" : new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8);
                return new ExecutionResult(
                    -4,
                    partialOut,
                    "I/O error during execution: " + t.getMessage(),
                    elapsed
                );
            } finally {
                // Restore socket timeout to infinite while idle
                try {
                    setSocketReadTimeout(0);
                } catch (Throwable ignored) {}
            }

        } finally {
            EXECUTION_LOCK.unlock();
        }
    }

    private static boolean appendBounded(ByteArrayOutputStream dst, byte[] src, int off, int len, int limit) {
        int avail = limit - dst.size();
        if (avail <= 0) return len > 0;
        int writeLen = Math.min(len, avail);
        if (writeLen > 0) dst.write(src, off, writeLen);
        return writeLen < len;
    }

    private static ExecutionResult buildResult(int exitCode, ByteArrayOutputStream stdoutBytes,
                                               ByteArrayOutputStream stderrBytes, boolean stdoutTruncated,
                                               boolean stderrTruncated, long elapsed, boolean binaryRaw) {
        byte[] raw = binaryRaw ? stdoutBytes.toByteArray() : null;
        String out = "";
        if (!binaryRaw) {
            out = new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8);
            if (stdoutTruncated) out += "\n…[output truncated at 1 MB]";
        }
        String err = new String(stderrBytes.toByteArray(), StandardCharsets.UTF_8);
        if (stderrTruncated) err += "\n…[stderr truncated at 256 KB]";
        return new ExecutionResult(exitCode, out, err, elapsed, raw);
    }

    private static void tryCloseStaleChannel(int staleLocalId, int staleRemoteId) {
        try {
            AdbMessage clse = new AdbMessage(AdbProtocol.A_CLSE, staleLocalId, staleRemoteId, new byte[0]);
            clse.write(sOutputStream);
            sOutputStream.flush();
        } catch (Throwable ignored) {}
    }

    private static void setSocketReadTimeout(int timeoutMs) throws IOException {
        if (sTlsSocket != null && !sTlsSocket.isClosed()) {
            sTlsSocket.setSoTimeout(timeoutMs);
        } else if (sRawSocket != null && !sRawSocket.isClosed()) {
            sRawSocket.setSoTimeout(timeoutMs);
        }
    }

    private static int allocateLocalId() {
        int id = NEXT_LOCAL_ID.getAndIncrement();
        if (id <= 0) {
            NEXT_LOCAL_ID.compareAndSet(id, 1);
            id = NEXT_LOCAL_ID.getAndIncrement();
        }
        return id;
    }

    /**
     * Checks if a command or any chained / embedded sub-command contains dangerous operations.
     * De-chains on delimiters, unwraps subshells, normalizes whitespace and utility prefixes,
     * and performs semantic verification of operands.
     *
     * @param command Command string to inspect.
     * @return true if dangerous command detected, false if safe.
     */
    public static boolean isDangerousCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        // 1. Recursive unwrap subshells: $(...)
        List<String> subshells = extractBalancedSubshells(command);
        for (String sub : subshells) {
            if (isDangerousCommand(sub)) {
                return true;
            }
        }

        // 2. Recursive unwrap backticks: `...`
        Matcher bMatcher = BACKTICKS.matcher(command);
        while (bMatcher.find()) {
            if (isDangerousCommand(bMatcher.group(1))) {
                return true;
            }
        }

        // 3. Shell / Eval wrappers: sh -c '...', eval "..."
        Matcher wMatcher = SHELL_WRAPPERS.matcher(command);
        while (wMatcher.find()) {
            if (isDangerousCommand(wMatcher.group(1))) {
                return true;
            }
        }

        Matcher uShellMatcher = UNQUOTED_SHELL.matcher(command.trim());
        if (uShellMatcher.matches()) {
            if (isDangerousCommand(uShellMatcher.group(1))) {
                return true;
            }
        }

        Matcher uEvalMatcher = UNQUOTED_EVAL.matcher(command.trim());
        if (uEvalMatcher.matches()) {
            if (isDangerousCommand(uEvalMatcher.group(1))) {
                return true;
            }
        }

        // 4. Tokenize on command separators (; && || | & \n \r)
        String[] segments = CMD_DELIMITERS.split(command);
        for (String seg : segments) {
            String trimmed = seg.trim();
            if (!trimmed.isEmpty() && checkSegmentDangerous(trimmed)) {
                return true;
            }
        }

        return false;
    }

    private static List<String> extractBalancedSubshells(String s) {
        List<String> results = new ArrayList<>();
        int idx = 0;
        while (true) {
            int pos = s.indexOf("$(", idx);
            if (pos == -1) break;
            int depth = 0;
            int start = pos + 2;
            int end = -1;
            for (int i = start; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (depth == 0) {
                        end = i;
                        break;
                    } else {
                        depth--;
                    }
                }
            }
            if (end != -1) {
                results.add(s.substring(start, end));
                idx = start;
            } else {
                idx = pos + 2;
            }
        }
        return results;
    }

    private static boolean checkSegmentDangerous(String segment) {
        String seg = segment.replaceAll("\\s+", " ").trim();
        String norm = TRANSPARENT_PREFIXES.matcher(seg).replaceFirst("").trim();
        String lowerNorm = norm.toLowerCase();

        // 1. Reboot / Recovery / Fastboot / Power
        if (REBOOT_PATTERN.matcher(norm).matches()) {
            return true;
        }

        // 2. Wipe / Format / mkfs
        if (WIPE_PATTERN.matcher(norm).matches()) {
            return true;
        }

        // 3. dd block device raw overwrite
        if (lowerNorm.startsWith("dd ") || lowerNorm.equals("dd")) {
            if (lowerNorm.contains("of=/dev/block")
                    || (lowerNorm.contains("of=/dev/") && (lowerNorm.contains("if=/dev/zero") || lowerNorm.contains("if=/dev/urandom")))) {
                return true;
            }
        }

        // 4. pm clear / pm uninstall
        if (PM_PATTERN.matcher(norm).matches()) {
            return true;
        }

        // 5. setenforce 0 / permissive
        if (SETENFORCE_PATTERN.matcher(norm).matches()) {
            return true;
        }

        // 6. rm file system deletion
        if (lowerNorm.startsWith("rm ") || lowerNorm.equals("rm")) {
            String[] tokens = norm.split(" ");
            boolean hasRecursive = false;
            boolean hasForce = false;
            List<String> targets = new ArrayList<>();

            for (int i = 1; i < tokens.length; i++) {
                String token = tokens[i].trim();
                if (token.isEmpty()) continue;

                if (token.startsWith("-")) {
                    if (RECURSIVE_FLAG.matcher(token).find() || token.equalsIgnoreCase("--recursive")) {
                        hasRecursive = true;
                    }
                    if (FORCE_FLAG.matcher(token).find() || token.equalsIgnoreCase("--force")) {
                        hasForce = true;
                    }
                } else {
                    targets.add(token);
                }
            }

            // Flagged wildcard wiping (* or /*)
            if ((hasRecursive || hasForce) && norm.contains("*")) {
                return true;
            }

            for (String target : targets) {
                String clean = target.replaceAll("/+", "/")
                                     .replaceAll("/\\./", "/")
                                     .replaceAll("/+$", "");

                // Root wipe
                if (clean.isEmpty() || clean.equals("/")) {
                    return true;
                }

                // Explicit wildcard
                if (clean.equals("*") || clean.endsWith("/*")) {
                    if (hasRecursive || hasForce || clean.equals("*")) {
                        return true;
                    }
                }

                // Protected system directories and subdirectories
                for (String cr : CRITICAL_ROOTS) {
                    if (clean.equalsIgnoreCase(cr)
                            || clean.toLowerCase().startsWith(cr + "/")
                            || clean.toLowerCase().startsWith(cr + "*")) {
                        if (hasRecursive || hasForce || clean.equalsIgnoreCase(cr)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
