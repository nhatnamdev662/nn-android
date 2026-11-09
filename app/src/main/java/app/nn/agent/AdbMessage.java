package app.nn.agent;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Encapsulates an immutable 24-byte ADB message packet header and optional payload buffer.
 * Provides serialization, deserialization, Little-Endian framing, magic validation,
 * and unsigned 8-bit checksum calculation conforming to AOSP adb specifications.
 */
public final class AdbMessage {

    private static final byte[] EMPTY_PAYLOAD = new byte[0];

    /** 32-bit Little-Endian FourCC command identifier (e.g., A_CNXN, A_OPEN). */
    public final int command;

    /** First command-dependent argument. */
    public final int arg0;

    /** Second command-dependent argument. */
    public final int arg1;

    /** Length of the subsequent payload in bytes (0 to MAX_PAYLOAD). */
    public final int dataLength;

    /** Unsigned 8-bit arithmetic sum of all bytes in the payload. */
    public final int dataCheck;

    /** Bitwise inversion of command (command ^ 0xFFFFFFFF). */
    public final int magic;

    /** Payload byte array (never null; empty array if dataLength == 0). */
    public final byte[] payload;

    /**
     * Full explicit constructor with parameter bounds validation.
     *
     * @throws IllegalArgumentException if dataLength is negative, exceeds MAX_PAYLOAD,
     *                                  or exceeds actual payload buffer length.
     */
    public AdbMessage(int command, int arg0, int arg1, int dataLength,
                      int dataCheck, int magic, byte[] payload) {
        if (dataLength < 0 || dataLength > AdbProtocol.MAX_PAYLOAD) {
            throw new IllegalArgumentException(
                    "Invalid ADB dataLength: " + dataLength + " (must be between 0 and " + AdbProtocol.MAX_PAYLOAD + ")");
        }
        byte[] actualPayload = (payload != null) ? payload : EMPTY_PAYLOAD;
        if (dataLength > actualPayload.length) {
            throw new IllegalArgumentException(
                    "dataLength " + dataLength + " exceeds payload buffer length " + actualPayload.length);
        }
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.dataLength = dataLength;
        this.dataCheck = dataCheck;
        this.magic = magic;
        this.payload = actualPayload;
    }

    /**
     * Convenience constructor that automatically computes dataLength, dataCheck,
     * and magic from the provided command, arguments, and payload.
     *
     * @param command FourCC command integer (e.g. AdbProtocol.CMD_OPEN)
     * @param arg0    First command argument
     * @param arg1    Second command argument
     * @param payload Payload byte array (null treated as empty)
     */
    public AdbMessage(int command, int arg0, int arg1, byte[] payload) {
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.payload = (payload != null) ? payload : EMPTY_PAYLOAD;
        this.dataLength = this.payload.length;
        this.dataCheck = computeChecksum(this.payload, 0, this.dataLength);
        this.magic = computeMagic(command);
    }

    // -------------------------------------------------------------------------
    // Checksum & Magic Invariants
    // -------------------------------------------------------------------------

    /**
     * Computes the ADB magic value for a command (command ^ 0xFFFFFFFF).
     */
    public static int computeMagic(int command) {
        return ~command;
    }

    /**
     * Computes the unsigned 8-bit sum checksum of a byte array.
     * Conforms to AOSP calculate_apacket_checksum().
     *
     * @param payload Byte array to sum
     * @return 32-bit unsigned sum
     */
    public static int computeChecksum(byte[] payload) {
        if (payload == null) {
            return 0;
        }
        return computeChecksum(payload, 0, payload.length);
    }

    /**
     * Computes the unsigned 8-bit sum checksum of a slice of a byte array.
     *
     * @param payload Byte array
     * @param offset  Start offset
     * @param length  Number of bytes to sum
     * @return 32-bit unsigned sum
     */
    public static int computeChecksum(byte[] payload, int offset, int length) {
        if (payload == null || length <= 0) {
            return 0;
        }
        int sum = 0;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            sum += (payload[i] & 0xFF);
        }
        return sum;
    }

    /**
     * Verifies that magic matches bitwise inverted command: (command ^ magic) == -1.
     */
    public boolean isValidMagic() {
        return (command ^ magic) == -1;
    }

    /**
     * Verifies that the payload checksum matches dataCheck.
     * Accepts dataCheck == 0 if dataLength > 0 when peer uses A_VERSION_SKIP_CHECKSUM.
     */
    public boolean isValidChecksum() {
        if (dataLength == 0) {
            return dataCheck == 0;
        }
        // In modern AOSP adbd (A_VERSION_SKIP_CHECKSUM), data_check can be 0 on data packets
        if (dataCheck == 0) {
            return true;
        }
        return computeChecksum(payload, 0, dataLength) == dataCheck;
    }

    // -------------------------------------------------------------------------
    // Serialization & Wire I/O
    // -------------------------------------------------------------------------

    /**
     * Serializes the 24-byte packet header into a Little-Endian byte array.
     */
    public byte[] getHeaderBytes() {
        byte[] bytes = new byte[AdbProtocol.HEADER_LENGTH];
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(command);
        buf.putInt(arg0);
        buf.putInt(arg1);
        buf.putInt(dataLength);
        buf.putInt(dataCheck);
        buf.putInt(magic);
        return bytes;
    }

    /**
     * Serializes the entire packet (24-byte header + payload) into a single byte array.
     */
    public byte[] toWireBytes() {
        byte[] bytes = new byte[AdbProtocol.HEADER_LENGTH + dataLength];
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(command);
        buf.putInt(arg0);
        buf.putInt(arg1);
        buf.putInt(dataLength);
        buf.putInt(dataCheck);
        buf.putInt(magic);
        if (dataLength > 0 && payload != null) {
            System.arraycopy(payload, 0, bytes, AdbProtocol.HEADER_LENGTH, dataLength);
        }
        return bytes;
    }

    /**
     * Writes the 24-byte Little-Endian header and any payload directly to an OutputStream.
     * Flushes the stream after transmission.
     *
     * @param out Target output stream
     * @throws IOException on I/O error
     */
    public void writeTo(OutputStream out) throws IOException {
        byte[] header = getHeaderBytes();
        out.write(header);
        if (dataLength > 0 && payload != null) {
            out.write(payload, 0, dataLength);
        }
        out.flush();
    }

    /**
     * Alias for writeTo(out).
     */
    public void write(OutputStream out) throws IOException {
        writeTo(out);
    }

    /**
     * Reads and parses a single AdbMessage from an InputStream.
     * Blocks until all 24 bytes of the header and all dataLength bytes of the payload
     * are received, or throws EOFException if stream closes prematurely.
     *
     * @param in Source input stream
     * @return Parsed AdbMessage
     * @throws IOException on I/O error, invalid magic, or length overflow
     */
    public static AdbMessage readFrom(InputStream in) throws IOException {
        byte[] headerBytes = new byte[AdbProtocol.HEADER_LENGTH];
        readFully(in, headerBytes, 0, AdbProtocol.HEADER_LENGTH);

        ByteBuffer buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        int command = buf.getInt();
        int arg0 = buf.getInt();
        int arg1 = buf.getInt();
        int dataLength = buf.getInt();
        int dataCheck = buf.getInt();
        int magic = buf.getInt();

        // Guard 1: Magic verification
        if ((command ^ magic) != -1) {
            throw new IOException(String.format(
                    "Invalid ADB magic: command=0x%08X (%s), magic=0x%08X (expected 0x%08X)",
                    command, AdbProtocol.commandToString(command), magic, ~command));
        }

        // Guard 2: OOM / DoS length validation
        if (dataLength < 0 || dataLength > AdbProtocol.MAX_PAYLOAD) {
            throw new IOException(String.format(
                    "Invalid ADB payload length: %d (max allowed %d)",
                    dataLength, AdbProtocol.MAX_PAYLOAD));
        }

        byte[] payload = EMPTY_PAYLOAD;
        if (dataLength > 0) {
            payload = new byte[dataLength];
            readFully(in, payload, 0, dataLength);
        }

        // Guard 3: Checksum validation (enforced when dataCheck != 0)
        if (dataCheck != 0 && dataLength > 0) {
            int computedCheck = computeChecksum(payload, 0, dataLength);
            if (computedCheck != dataCheck) {
                throw new IOException(String.format(
                        "Invalid ADB checksum: received=0x%08X, computed=0x%08X for command=0x%08X (%s)",
                        dataCheck, computedCheck, command, AdbProtocol.commandToString(command)));
            }
        }

        return new AdbMessage(command, arg0, arg1, dataLength, dataCheck, magic, payload);
    }

    /**
     * Alias for readFrom(in).
     */
    public static AdbMessage read(InputStream in) throws IOException {
        return readFrom(in);
    }

    /**
     * Parses an AdbMessage from a Little-Endian ByteBuffer containing header and payload.
     *
     * @param buffer Source buffer positioned at start of message
     * @return Parsed AdbMessage
     * @throws IOException on parsing error
     */
    public static AdbMessage parse(ByteBuffer buffer) throws IOException {
        if (buffer.remaining() < AdbProtocol.HEADER_LENGTH) {
            throw new IOException("Buffer underflow: requires 24 bytes header");
        }
        ByteOrder originalOrder = buffer.order();
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int command = buffer.getInt();
            int arg0 = buffer.getInt();
            int arg1 = buffer.getInt();
            int dataLength = buffer.getInt();
            int dataCheck = buffer.getInt();
            int magic = buffer.getInt();

            if ((command ^ magic) != -1) {
                throw new IOException("Invalid ADB magic");
            }
            if (dataLength < 0 || dataLength > AdbProtocol.MAX_PAYLOAD) {
                throw new IOException("Invalid payload length: " + dataLength);
            }
            if (buffer.remaining() < dataLength) {
                throw new IOException("Buffer underflow for payload: expected " + dataLength +
                                      ", remaining " + buffer.remaining());
            }

            byte[] payload = EMPTY_PAYLOAD;
            if (dataLength > 0) {
                payload = new byte[dataLength];
                buffer.get(payload);
            }

            if (dataCheck != 0 && dataLength > 0) {
                int computedCheck = computeChecksum(payload, 0, dataLength);
                if (computedCheck != dataCheck) {
                    throw new IOException(String.format(
                            "Invalid ADB checksum: received=0x%08X, computed=0x%08X",
                            dataCheck, computedCheck));
                }
            }

            return new AdbMessage(command, arg0, arg1, dataLength, dataCheck, magic, payload);
        } finally {
            buffer.order(originalOrder);
        }
    }

    /**
     * Reads exactly length bytes from in into buffer starting at offset.
     *
     * @throws EOFException if EOF reached before length bytes are read
     */
    private static void readFully(InputStream in, byte[] buffer, int offset, int length)
            throws IOException {
        int readTotal = 0;
        while (readTotal < length) {
            int count = in.read(buffer, offset + readTotal, length - readTotal);
            if (count < 0) {
                throw new EOFException("Premature EOF: expected " + length + 
                                       " bytes, read " + readTotal);
            }
            readTotal += count;
        }
    }

    // -------------------------------------------------------------------------
    // Factory Construction Methods
    // -------------------------------------------------------------------------

    /**
     * Creates an A_CNXN connection handshake message.
     */
    public static AdbMessage cnxn(int version, int maxPayload, String banner) {
        byte[] payload = (banner != null) ? banner.getBytes(StandardCharsets.UTF_8) : EMPTY_PAYLOAD;
        return new AdbMessage(AdbProtocol.CMD_CNXN, version, maxPayload, payload);
    }

    /**
     * Creates an A_STLS TLS upgrade request or confirmation message.
     */
    public static AdbMessage stls(int stlsVersion) {
        return new AdbMessage(AdbProtocol.CMD_STLS, stlsVersion, 0, EMPTY_PAYLOAD);
    }

    /**
     * Creates an A_OPEN stream request message.
     * Ensures destination is null-terminated for 100% compatibility across all Android versions.
     */
    public static AdbMessage open(int localId, String destination) {
        if (destination == null) destination = "";
        if (!destination.endsWith("\0")) {
            destination = destination + "\0";
        }
        byte[] payload = destination.getBytes(StandardCharsets.UTF_8);
        return new AdbMessage(AdbProtocol.CMD_OPEN, localId, 0, payload);
    }

    /**
     * Creates an A_OKAY acknowledgement message.
     */
    public static AdbMessage okay(int localId, int remoteId) {
        return new AdbMessage(AdbProtocol.CMD_OKAY, localId, remoteId, EMPTY_PAYLOAD);
    }

    /**
     * Creates an A_CLSE channel close message.
     */
    public static AdbMessage clse(int localId, int remoteId) {
        return new AdbMessage(AdbProtocol.CMD_CLSE, localId, remoteId, EMPTY_PAYLOAD);
    }

    /**
     * Creates an A_WRTE data packet with a raw byte array payload.
     */
    public static AdbMessage wrte(int localId, int remoteId, byte[] payload) {
        return new AdbMessage(AdbProtocol.CMD_WRTE, localId, remoteId, payload);
    }

    /**
     * Creates an A_WRTE data packet with a UTF-8 string payload.
     */
    public static AdbMessage wrte(int localId, int remoteId, String text) {
        byte[] payload = (text != null) ? text.getBytes(StandardCharsets.UTF_8) : EMPTY_PAYLOAD;
        return new AdbMessage(AdbProtocol.CMD_WRTE, localId, remoteId, payload);
    }

    /**
     * Creates an A_AUTH authentication message.
     */
    public static AdbMessage auth(int authType, byte[] data) {
        return new AdbMessage(AdbProtocol.CMD_AUTH, authType, 0, data);
    }

    // -------------------------------------------------------------------------
    // Diagnostic & String Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the payload decoded as a UTF-8 string.
     */
    public String getPayloadString() {
        if (dataLength == 0 || payload == null) {
            return "";
        }
        // Strip trailing null byte if present
        int len = dataLength;
        if (len > 0 && payload[len - 1] == 0) {
            len--;
        }
        return new String(payload, 0, len, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return String.format(
                "AdbMessage{cmd=%s(0x%08X), arg0=0x%08X(%d), arg1=0x%08X(%d), len=%d, check=0x%08X, magic=0x%08X}",
                AdbProtocol.commandToString(command), command, arg0, arg0, arg1, arg1,
                dataLength, dataCheck, magic);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdbMessage)) return false;
        AdbMessage that = (AdbMessage) o;
        return command == that.command &&
               arg0 == that.arg0 &&
               arg1 == that.arg1 &&
               dataLength == that.dataLength &&
               dataCheck == that.dataCheck &&
               magic == that.magic &&
               Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = command;
        result = 31 * result + arg0;
        result = 31 * result + arg1;
        result = 31 * result + dataLength;
        result = 31 * result + dataCheck;
        result = 31 * result + magic;
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
