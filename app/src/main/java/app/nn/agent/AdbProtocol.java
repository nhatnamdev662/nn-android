package app.nn.agent;

import java.nio.charset.StandardCharsets;

/**
 * Protocol constants, version numbers, command identifiers, and limit specifications
 * for the Android Debug Bridge (ADB) protocol.
 *
 * Conforms to AOSP packages/modules/adb wire format and Android 11+ Wireless Debugging.
 */
public final class AdbProtocol {

    private AdbProtocol() {
        // Prevent instantiation of utility constant class
    }

    // -------------------------------------------------------------------------
    // Packet Framing Constants
    // -------------------------------------------------------------------------

    /**
     * Exact byte length of the ADB packet header (6 x 32-bit Little-Endian integers).
     */
    public static final int HEADER_LENGTH = 24;

    /**
     * Maximum payload size allowed for connection and authentication packets
     * prior to protocol negotiation (4 KB).
     */
    public static final int MAX_PAYLOAD_V1 = 4 * 1024; // 4096 bytes

    /**
     * Maximum payload size supported in modern ADB protocol (1 MB).
     */
    public static final int MAX_PAYLOAD = 1024 * 1024; // 1048576 bytes

    /**
     * Default maximum payload buffer size advertised by BUddy in A_CNXN packets.
     */
    public static final int DEFAULT_MAX_PAYLOAD = MAX_PAYLOAD;

    // -------------------------------------------------------------------------
    // FourCC Command Codes (Little-Endian 32-bit Integers)
    // -------------------------------------------------------------------------

    /** Legacy file synchronization service: "SYNC" (0x434E5953) */
    public static final int CMD_SYNC = 0x434E5953;
    public static final int A_SYNC = CMD_SYNC;

    /** Connection handshake: "CNXN" (0x4E584E43) */
    public static final int CMD_CNXN = 0x4E584E43;
    public static final int A_CNXN = CMD_CNXN;

    /** Open stream service: "OPEN" (0x4E45504F) */
    public static final int CMD_OPEN = 0x4E45504F;
    public static final int A_OPEN = CMD_OPEN;

    /** Stream ready / acknowledge: "OKAY" (0x59414B4F) */
    public static final int CMD_OKAY = 0x59414B4F;
    public static final int A_OKAY = CMD_OKAY;

    /** Close stream channel: "CLSE" (0x45534C43) */
    public static final int CMD_CLSE = 0x45534C43;
    public static final int A_CLSE = CMD_CLSE;

    /** Stream write data: "WRTE" (0x45545257) */
    public static final int CMD_WRTE = 0x45545257;
    public static final int A_WRTE = CMD_WRTE;

    /** Legacy RSA authentication challenge/response: "AUTH" (0x48545541) */
    public static final int CMD_AUTH = 0x48545541;
    public static final int A_AUTH = CMD_AUTH;

    /** TLS upgrade request/response for Wireless Debugging: "STLS" (0x534C5453) */
    public static final int CMD_STLS = 0x534C5453;
    public static final int A_STLS = CMD_STLS;

    // -------------------------------------------------------------------------
    // Protocol Versions
    // -------------------------------------------------------------------------

    /** Minimum supported protocol version (0x01000000) */
    public static final int VERSION_MIN = 0x01000000;

    /** Protocol version introducing checksum bypass on data packets (0x01000001) */
    public static final int VERSION_SKIP_CHECKSUM = 0x01000001;

    /** Current ADB protocol version sent in A_CNXN packets (0x01000001) */
    public static final int CONNECT_VERSION = VERSION_SKIP_CHECKSUM;
    public static final int A_VERSION = CONNECT_VERSION;

    /** Maximum payload constant alias */
    public static final int A_MAXDATA = MAX_PAYLOAD;

    /** Minimum TLS protocol version (0x01000000) */
    public static final int STLS_VERSION_MIN = 0x01000000;

    /** Stream-based TLS protocol version sent in A_STLS packets (0x01000000) */
    public static final int STLS_VERSION = 0x01000000;

    // -------------------------------------------------------------------------
    // Shell v2 Subpacket Protocol IDs
    // -------------------------------------------------------------------------
    public static final int SHELL_V2_STDOUT = 1;
    public static final int SHELL_V2_STDERR = 2;
    public static final int SHELL_V2_EXIT = 3;
    public static final int SHELL_V2_CLOSE_STDOUT = 4;
    public static final int SHELL_V2_CLOSE_STDERR = 5;

    // -------------------------------------------------------------------------
    // Authentication Sub-types (used in arg0 of CMD_AUTH)
    // -------------------------------------------------------------------------

    /** Random token sent by device for host to sign */
    public static final int AUTH_TYPE_TOKEN = 1;

    /** Signed token returned by host to device */
    public static final int AUTH_TYPE_SIGNATURE = 2;

    /** Client RSA public key sent to device for authorization */
    public static final int AUTH_TYPE_RSAPUBLICKEY = 3;

    // -------------------------------------------------------------------------
    // Service Destinations (used in payload of CMD_OPEN)
    // -------------------------------------------------------------------------

    /** Shell service prefix: runs interactive shell or single command */
    public static final String SERVICE_SHELL = "shell:";

    /** Exec service prefix: executes command directly without PTY */
    public static final String SERVICE_EXEC = "exec:";

    /** File sync service prefix */
    public static final String SERVICE_SYNC = "sync:";

    /** Reverse port forwarding service prefix */
    public static final String SERVICE_REVERSE = "reverse:";

    /** Android Binder Bridge service */
    public static final String SERVICE_ABB = "abb:";

    /** Android Binder Bridge exec service */
    public static final String SERVICE_ABB_EXEC = "abb_exec:";

    // -------------------------------------------------------------------------
    // Connection Banners
    // -------------------------------------------------------------------------

    /** Standard client connection banner */
    public static final String BANNER_HOST = "host::\0";

    /** Client connection banner advertising extended features */
    public static final String BANNER_HOST_FEATURES =
            "host::features=shell_v2,cmd,stat_v2,ls_v2,fixed_push_mkdir\0";

    // -------------------------------------------------------------------------
    // Utility Methods
    // -------------------------------------------------------------------------

    /**
     * Converts a 32-bit Little-Endian command integer to its 4-character ASCII representation.
     *
     * @param command 32-bit command code
     * @return 4-character string or "UNKNOWN(0x...)" if unrecognized
     */
    public static String commandToString(int command) {
        switch (command) {
            case CMD_SYNC: return "SYNC";
            case CMD_CNXN: return "CNXN";
            case CMD_OPEN: return "OPEN";
            case CMD_OKAY: return "OKAY";
            case CMD_CLSE: return "CLSE";
            case CMD_WRTE: return "WRTE";
            case CMD_AUTH: return "AUTH";
            case CMD_STLS: return "STLS";
            default:
                byte[] b = new byte[4];
                b[0] = (byte) (command & 0xFF);
                b[1] = (byte) ((command >> 8) & 0xFF);
                b[2] = (byte) ((command >> 16) & 0xFF);
                b[3] = (byte) ((command >> 24) & 0xFF);
                for (byte x : b) {
                    if (x < 32 || x > 126) {
                        return String.format("UNKNOWN(0x%08X)", command);
                    }
                }
                return new String(b, StandardCharsets.US_ASCII);
        }
    }

    /**
     * Converts a 4-character ASCII string into its 32-bit Little-Endian command integer.
     *
     * @param str 4-character ASCII string (e.g. "CNXN")
     * @return 32-bit command integer
     * @throws IllegalArgumentException if string is not exactly 4 characters or contains non-ASCII characters
     */
    public static int stringToCommand(String str) {
        if (str == null || str.length() != 4) {
            throw new IllegalArgumentException("Command string must be exactly 4 characters: " + str);
        }
        for (int i = 0; i < 4; i++) {
            char c = str.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException(
                        "Command string contains non-ASCII character at index " + i + ": " + (int) c);
            }
        }
        return (str.charAt(0) & 0xFF) |
               ((str.charAt(1) & 0xFF) << 8) |
               ((str.charAt(2) & 0xFF) << 16) |
               ((str.charAt(3) & 0xFF) << 24);
    }


    /**
     * Validates whether a given integer is a known standard ADB command.
     */
    public static boolean isValidCommand(int command) {
        return command == CMD_SYNC || command == CMD_CNXN || command == CMD_OPEN ||
               command == CMD_OKAY || command == CMD_CLSE || command == CMD_WRTE ||
               command == CMD_AUTH || command == CMD_STLS;
    }
}
