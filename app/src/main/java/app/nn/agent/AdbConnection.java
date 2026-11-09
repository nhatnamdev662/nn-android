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

        public ExecutionResult(int exitCode, String stdout, String stderr, long executionTimeMs) {
            this.exitCode = exitCode;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
            this.executionTimeMs = executionTimeMs;
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

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int HANDSHAKE_TIMEOUT_MS = 10000;
    private static final int DEFAULT_EXEC_TIMEOUT_MS = 15000;

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

                // 3. Read initial reply from adbd
                AdbMessage reply = AdbMessage.read(rawIn);

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
     * Executes a shell command on the connected Android system.
     * Uses monotonic clock (System.nanoTime()) for all timeout accounting.
     */
    public static ExecutionResult execute(String command, int timeoutMs) throws IOException {
        long startNano = System.nanoTime();

        if (command == null || command.trim().isEmpty()) {
            return new ExecutionResult(0, "", "", 0);
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
            int exitCode = 0;

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

                        // Demux payload
                        if (msg.payload.length > 0) {
                            if (sSupportsShellV2) {
                                int offset = 0;
                                while (offset + 5 <= msg.payload.length) {
                                    int id = msg.payload[offset] & 0xFF;
                                    int len = (msg.payload[offset + 1] & 0xFF)
                                            | ((msg.payload[offset + 2] & 0xFF) << 8)
                                            | ((msg.payload[offset + 3] & 0xFF) << 16)
                                            | ((msg.payload[offset + 4] & 0xFF) << 24);
                                    offset += 5;

                                    if (offset + len > msg.payload.length) {
                                        break;
                                    }

                                    if (id == AdbProtocol.SHELL_V2_STDOUT) {
                                        stdoutBytes.write(msg.payload, offset, len);
                                    } else if (id == AdbProtocol.SHELL_V2_STDERR) {
                                        stderrBytes.write(msg.payload, offset, len);
                                    } else if (id == AdbProtocol.SHELL_V2_EXIT) {
                                        if (len >= 1) {
                                            exitCode = msg.payload[offset] & 0xFF;
                                        }
                                    }
                                    offset += len;
                                }
                            } else {
                                stdoutBytes.write(msg.payload);
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
                return new ExecutionResult(
                    exitCode,
                    new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8),
                    new String(stderrBytes.toByteArray(), StandardCharsets.UTF_8),
                    elapsed
                );

            } catch (SocketTimeoutException ste) {
                if (channelOpen) {
                    try {
                        AdbMessage clse = new AdbMessage(AdbProtocol.A_CLSE, localId, remoteId, new byte[0]);
                        clse.write(sOutputStream);
                        sOutputStream.flush();
                    } catch (Throwable ignored) {}
                }
                long elapsed = (System.nanoTime() - startNano) / 1_000_000L;
                return new ExecutionResult(
                    -3,
                    new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8),
                    "Execution timed out after " + effectiveTimeout + "ms",
                    elapsed
                );

            } catch (Throwable t) {
                disconnect();
                long elapsed = (System.nanoTime() - startNano) / 1_000_000L;
                return new ExecutionResult(
                    -4,
                    new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8),
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
