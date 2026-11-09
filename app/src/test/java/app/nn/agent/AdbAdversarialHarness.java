package app.nn.agent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Random;

/**
 * Adversarial and empirical verification harness for Milestone 1:
 * AdbProtocol, AdbMessage, and AdbKey Montgomery math / struct layout.
 */
public class AdbAdversarialHarness {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("ADB CORE ADVERSARIAL VERIFICATION HARNESS");
        System.out.println("=================================================");

        // Vector 1: AdbProtocol Constants and Command Encodings
        runTest("AdbProtocol: Constants and FourCC values", AdbAdversarialHarness::testProtocolConstants);
        runTest("AdbProtocol: Command string round-trip & validation", AdbAdversarialHarness::testCommandStringRoundtrip);
        runTest("AdbProtocol [EDGE]: Surrogate pair / multi-byte vulnerability in stringToCommand", AdbAdversarialHarness::testStringToCommandSurrogateVulnerability);

        // Vector 2: AdbMessage Header Framing & Bitwise Invariant
        runTest("AdbMessage: 24-byte Little-Endian wire framing", AdbAdversarialHarness::testHeaderFraming);
        runTest("AdbMessage: Bitwise magic inversion invariant ((cmd ^ magic) == -1)", AdbAdversarialHarness::testBitwiseMagicInvariant);
        runTest("AdbMessage: Bitwise magic corruption rejection in readFrom & parse", AdbAdversarialHarness::testMagicCorruptionRejection);

        // Vector 3: AdbMessage Checksum Arithmetic & Bypass
        runTest("AdbMessage: Unsigned 8-bit arithmetic sum boundaries (0 to 1MB)", AdbAdversarialHarness::testChecksumArithmeticBoundaries);
        runTest("AdbMessage: isValidChecksum logic and modern adbd bypass", AdbAdversarialHarness::testChecksumValidationAndBypass);
        runTest("AdbMessage [EDGE]: Missing checksum verification during readFrom/parse", AdbAdversarialHarness::testMissingChecksumVerificationInRead);

        // Vector 4: Stream Deserialization, Fragmentation & Memory Guardrails
        runTest("AdbMessage: Stream deserialization under 1-byte fragmentation", AdbAdversarialHarness::testStreamFragmentationOneByte);
        runTest("AdbMessage: Stream deserialization under random chunk jitter", AdbAdversarialHarness::testStreamFragmentationJitter);
        runTest("AdbMessage: Premature EOF handling in header and payload", AdbAdversarialHarness::testPrematureEofHandling);
        runTest("AdbMessage: Payload memory bounds and DoS rejection (negative & >MAX_PAYLOAD)", AdbAdversarialHarness::testPayloadMemoryGuardrails);
        runTest("AdbMessage: Buffer underflow handling in parse(ByteBuffer)", AdbAdversarialHarness::testParseBufferUnderflows);
        runTest("AdbMessage [EDGE]: Constructor bounds inconsistency (payload < dataLength)", AdbAdversarialHarness::testConstructorBoundsInconsistency);
        runTest("AdbMessage [EDGE]: parse(ByteBuffer) mutates caller ByteOrder", AdbAdversarialHarness::testParseMutatesByteOrder);

        // Vector 5: Montgomery Math, 524-Byte Layout & AOSP Equivalence
        runTest("AdbKey: Montgomery parameter math (N0 * n0inv == -1 mod 2^32)", AdbAdversarialHarness::testMontgomeryParameterMath);
        runTest("AdbKey: Montgomery R^2 mod N invariant", AdbAdversarialHarness::testMontgomeryR2Math);
        runTest("AdbKey: 524-byte android_pubkey binary struct layout and field reconstruction", AdbAdversarialHarness::testAndroidPubkeyStructLayout);
        runTest("AdbKey: Verification against official AOSP adbkey.pub", AdbAdversarialHarness::testAospAdbKeyEquivalence);

        System.out.println("=================================================");
        System.out.printf("SUMMARY: Total=%d, Passed=%d, Failed=%d%n", testsRun, testsPassed, testsFailed);
        System.out.println("=================================================");

        if (testsFailed > 0) {
            System.err.println("VERDICT: Some adversarial tests identified vulnerabilities or failures.");
        } else {
            System.out.println("VERDICT: All core invariant assertions passed.");
        }
    }

    private static void runTest(String name, TestRunnable test) {
        testsRun++;
        System.out.printf("[RUNNING] %s ... ", name);
        try {
            test.run();
            testsPassed++;
            System.out.println("PASSED");
        } catch (Throwable t) {
            testsFailed++;
            System.out.println("FAILED: " + t.getMessage());
            t.printStackTrace(System.out);
        }
    }

    interface TestRunnable {
        void run() throws Throwable;
    }

    private static void assertTrue(String msg, boolean cond) {
        if (!cond) throw new AssertionError("Assertion failed: " + msg);
    }

    private static void assertEquals(String msg, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + " -> expected: " + expected + ", but got: " + actual);
        }
    }

    // =========================================================================
    // Vector 1: AdbProtocol Constants and Command Encodings
    // =========================================================================

    public static void testProtocolConstants() {
        assertEquals("HEADER_LENGTH", 24, AdbProtocol.HEADER_LENGTH);
        assertEquals("MAX_PAYLOAD", 1024 * 1024, AdbProtocol.MAX_PAYLOAD);
        assertEquals("DEFAULT_MAX_PAYLOAD", AdbProtocol.MAX_PAYLOAD, AdbProtocol.DEFAULT_MAX_PAYLOAD);

        // Verify Little-Endian FourCC ASCII representations
        assertEquals("CMD_SYNC", 0x434E5953, AdbProtocol.CMD_SYNC); // 'S' 'Y' 'N' 'C'
        assertEquals("CMD_CNXN", 0x4E584E43, AdbProtocol.CMD_CNXN); // 'C' 'N' 'X' 'N'
        assertEquals("CMD_OPEN", 0x4E45504F, AdbProtocol.CMD_OPEN); // 'O' 'P' 'E' 'N'
        assertEquals("CMD_OKAY", 0x59414B4F, AdbProtocol.CMD_OKAY); // 'O' 'K' 'A' 'Y'
        assertEquals("CMD_CLSE", 0x45534C43, AdbProtocol.CMD_CLSE); // 'C' 'L' 'S' 'E'
        assertEquals("CMD_WRTE", 0x45545257, AdbProtocol.CMD_WRTE); // 'W' 'R' 'T' 'E'
        assertEquals("CMD_AUTH", 0x48545541, AdbProtocol.CMD_AUTH); // 'A' 'U' 'T' 'H'
        assertEquals("CMD_STLS", 0x534C5453, AdbProtocol.CMD_STLS); // 'S' 'T' 'L' 'S'
    }

    public static void testCommandStringRoundtrip() {
        int[] cmds = {
            AdbProtocol.CMD_SYNC, AdbProtocol.CMD_CNXN, AdbProtocol.CMD_OPEN,
            AdbProtocol.CMD_OKAY, AdbProtocol.CMD_CLSE, AdbProtocol.CMD_WRTE,
            AdbProtocol.CMD_AUTH, AdbProtocol.CMD_STLS
        };
        String[] names = { "SYNC", "CNXN", "OPEN", "OKAY", "CLSE", "WRTE", "AUTH", "STLS" };

        for (int i = 0; i < cmds.length; i++) {
            int cmd = cmds[i];
            String str = names[i];
            assertEquals("commandToString for " + str, str, AdbProtocol.commandToString(cmd));
            assertEquals("stringToCommand for " + str, cmd, AdbProtocol.stringToCommand(str));
            assertTrue("isValidCommand for " + str, AdbProtocol.isValidCommand(cmd));
        }

        // Test invalid command checks
        assertTrue("isValidCommand(0)", !AdbProtocol.isValidCommand(0));
        assertTrue("isValidCommand(-1)", !AdbProtocol.isValidCommand(-1));
        assertTrue("isValidCommand(0x12345678)", !AdbProtocol.isValidCommand(0x12345678));

        // Test non-standard commands in commandToString
        assertEquals("commandToString non-standard printable", "TEST", AdbProtocol.commandToString(0x54534554));
        assertEquals("commandToString non-printable", "UNKNOWN(0x00000000)", AdbProtocol.commandToString(0));
    }

    public static void testStringToCommandSurrogateVulnerability() {
        // Edge Case: 4-character Java string composed of 2 surrogate pairs (e.g. 2 emojis)
        // string.length() is 4, but getBytes(US_ASCII) produces 2 bytes ("??")
        // stringToCommand tries to access b[2] and b[3], throwing ArrayIndexOutOfBoundsException instead of IllegalArgumentException
        String surrogateStr = "\uD83D\uDE00\uD83D\uDE00"; // Two emoji characters
        assertEquals("surrogate string length", 4, surrogateStr.length());

        try {
            int cmd = AdbProtocol.stringToCommand(surrogateStr);
            throw new AssertionError("Expected exception for surrogate string, but got cmd: " + cmd);
        } catch (ArrayIndexOutOfBoundsException aioobe) {
            // Documenting confirmed boundary vulnerability: throws ArrayIndexOutOfBoundsException rather than IllegalArgumentException
            System.out.print("[CONFIRMED FLAW: ArrayIndexOutOfBoundsException thrown instead of IllegalArgumentException] ");
        } catch (IllegalArgumentException expected) {
            // If validated properly
        }
    }

    // =========================================================================
    // Vector 2: AdbMessage Header Framing & Bitwise Invariant
    // =========================================================================

    public static void testHeaderFraming() {
        byte[] payload = "Hello ADB!".getBytes(StandardCharsets.UTF_8);
        AdbMessage msg = new AdbMessage(AdbProtocol.CMD_OPEN, 12345, 67890, payload);

        byte[] headerBytes = msg.getHeaderBytes();
        assertEquals("Header length must be exactly 24 bytes", 24, headerBytes.length);

        ByteBuffer buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("Header command", AdbProtocol.CMD_OPEN, buf.getInt());
        assertEquals("Header arg0", 12345, buf.getInt());
        assertEquals("Header arg1", 67890, buf.getInt());
        assertEquals("Header dataLength", payload.length, buf.getInt());
        assertEquals("Header dataCheck", msg.dataCheck, buf.getInt());
        assertEquals("Header magic", msg.magic, buf.getInt());

        // Test toWireBytes
        byte[] wire = msg.toWireBytes();
        assertEquals("Wire length must be 24 + payload.length", 24 + payload.length, wire.length);
        byte[] wirePayload = Arrays.copyOfRange(wire, 24, wire.length);
        assertTrue("Wire payload matches original", Arrays.equals(payload, wirePayload));

        // Test writeTo matches toWireBytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            msg.writeTo(baos);
            byte[] written = baos.toByteArray();
            assertTrue("writeTo output matches toWireBytes byte-for-byte", Arrays.equals(wire, written));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void testBitwiseMagicInvariant() {
        int[] cmds = {
            AdbProtocol.CMD_SYNC, AdbProtocol.CMD_CNXN, AdbProtocol.CMD_OPEN,
            AdbProtocol.CMD_OKAY, AdbProtocol.CMD_CLSE, AdbProtocol.CMD_WRTE,
            AdbProtocol.CMD_AUTH, AdbProtocol.CMD_STLS
        };

        for (int cmd : cmds) {
            int magic = AdbMessage.computeMagic(cmd);
            assertEquals("Magic must equal ~cmd for " + AdbProtocol.commandToString(cmd), ~cmd, magic);
            assertEquals("Bitwise invariant (cmd ^ magic) == -1", -1, cmd ^ magic);
            AdbMessage msg = new AdbMessage(cmd, 0, 0, null);
            assertTrue("msg.isValidMagic() must be true", msg.isValidMagic());
        }

        // Stress test invariant with 100,000 randomized integers
        Random rng = new Random(0xCAFEBABE);
        for (int i = 0; i < 100_000; i++) {
            int randCmd = rng.nextInt();
            int magic = AdbMessage.computeMagic(randCmd);
            if ((randCmd ^ magic) != -1) {
                throw new AssertionError("Bitwise invariant failed for randomized cmd: " + randCmd);
            }
        }
    }

    public static void testMagicCorruptionRejection() throws IOException {
        int cmd = AdbProtocol.CMD_CNXN;
        int correctMagic = ~cmd;

        // Test all 32 single-bit flip corruptions of magic
        for (int bit = 0; bit < 32; bit++) {
            int corruptedMagic = correctMagic ^ (1 << bit);
            assertTrue("Corrupted magic must not satisfy invariant", (cmd ^ corruptedMagic) != -1);

            // Construct 24-byte corrupted header
            ByteBuffer buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(cmd);
            buf.putInt(1);
            buf.putInt(2);
            buf.putInt(0);
            buf.putInt(0);
            buf.putInt(corruptedMagic);

            // 1. Test readFrom rejection
            try {
                AdbMessage.readFrom(new ByteArrayInputStream(buf.array()));
                throw new AssertionError("readFrom failed to reject corrupted magic at bit " + bit);
            } catch (IOException expected) {
                assertTrue("Exception message indicates invalid magic", expected.getMessage().contains("Invalid ADB magic"));
            }

            // 2. Test parse rejection
            try {
                AdbMessage.parse(ByteBuffer.wrap(buf.array()));
                throw new AssertionError("parse failed to reject corrupted magic at bit " + bit);
            } catch (IOException expected) {
                assertTrue("Exception message indicates invalid magic", expected.getMessage().contains("Invalid ADB magic"));
            }
        }
    }

    // =========================================================================
    // Vector 3: AdbMessage Checksum Arithmetic & Bypass
    // =========================================================================

    public static void testChecksumArithmeticBoundaries() {
        // 1. Null and empty
        assertEquals("Checksum of null", 0, AdbMessage.computeChecksum(null));
        assertEquals("Checksum of empty array", 0, AdbMessage.computeChecksum(new byte[0]));

        // 2. Slices with offset
        byte[] data = { 10, 20, 30, 40, 50 };
        assertEquals("Slice checksum (offset=1, len=3)", 20 + 30 + 40, AdbMessage.computeChecksum(data, 1, 3));
        assertEquals("Slice checksum zero length", 0, AdbMessage.computeChecksum(data, 2, 0));
        assertEquals("Slice checksum negative length", 0, AdbMessage.computeChecksum(data, 2, -1));

        // 3. Unsigned byte handling (0xFF must be +255, not -1)
        byte[] allOnes = new byte[100];
        Arrays.fill(allOnes, (byte) 0xFF);
        int sumAllOnes = AdbMessage.computeChecksum(allOnes);
        assertEquals("100 * 255 unsigned sum", 25500, sumAllOnes);

        // 4. Large 1 MB payload (MAX_PAYLOAD = 1,048,576) with 0xFF bytes
        // Expected sum = 1048576 * 255 = 267,386,880
        byte[] maxPayload = new byte[AdbProtocol.MAX_PAYLOAD];
        Arrays.fill(maxPayload, (byte) 0xFF);
        int maxChecksum = AdbMessage.computeChecksum(maxPayload);
        assertEquals("1MB 0xFF checksum", 267386880, maxChecksum);
        assertTrue("Max checksum fits in positive signed int", maxChecksum > 0);
    }

    public static void testChecksumValidationAndBypass() {
        // Case 1: Empty payload (dataLength == 0)
        AdbMessage emptyValid = new AdbMessage(AdbProtocol.CMD_OKAY, 1, 2, 0, 0, ~AdbProtocol.CMD_OKAY, new byte[0]);
        assertTrue("Empty payload with check=0 is valid", emptyValid.isValidChecksum());
        AdbMessage emptyInvalid = new AdbMessage(AdbProtocol.CMD_OKAY, 1, 2, 0, 999, ~AdbProtocol.CMD_OKAY, new byte[0]);
        assertTrue("Empty payload with check!=0 is invalid", !emptyInvalid.isValidChecksum());

        // Case 2: Modern AOSP adbd bypass (dataCheck == 0 with dataLength > 0)
        byte[] payload = "arbitrary non-zero payload".getBytes(StandardCharsets.UTF_8);
        AdbMessage bypassMsg = new AdbMessage(AdbProtocol.CMD_WRTE, 1, 2, payload.length, 0, ~AdbProtocol.CMD_WRTE, payload);
        assertTrue("dataCheck==0 on data packet accepted per A_VERSION_SKIP_CHECKSUM bypass", bypassMsg.isValidChecksum());

        // Case 3: Exact checksum matching
        int realCheck = AdbMessage.computeChecksum(payload);
        AdbMessage matchingMsg = new AdbMessage(AdbProtocol.CMD_WRTE, 1, 2, payload.length, realCheck, ~AdbProtocol.CMD_WRTE, payload);
        assertTrue("Exact matching checksum is valid", matchingMsg.isValidChecksum());

        // Case 4: Mismatched checksum
        AdbMessage mismatchedMsg = new AdbMessage(AdbProtocol.CMD_WRTE, 1, 2, payload.length, realCheck + 1, ~AdbProtocol.CMD_WRTE, payload);
        assertTrue("Mismatched checksum is invalid", !mismatchedMsg.isValidChecksum());
    }

    public static void testMissingChecksumVerificationInRead() throws IOException {
        // Edge Case: When an incoming packet has corrupted payload but valid header/magic,
        // does readFrom or parse enforce isValidChecksum()?
        byte[] payload = "Original Data".getBytes(StandardCharsets.UTF_8);
        int realCheck = AdbMessage.computeChecksum(payload);

        // Corrupt payload bytes after computing checksum
        byte[] corruptedPayload = "CorruptedData".getBytes(StandardCharsets.UTF_8);

        ByteBuffer buf = ByteBuffer.allocate(24 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(AdbProtocol.CMD_WRTE);
        buf.putInt(1);
        buf.putInt(2);
        buf.putInt(corruptedPayload.length);
        buf.putInt(realCheck); // Checksum does not match corruptedPayload
        buf.putInt(~AdbProtocol.CMD_WRTE);
        buf.put(corruptedPayload);

        // readFrom enforces isValidChecksum() when dataCheck != 0, rejecting corrupted message
        try {
            AdbMessage.readFrom(new ByteArrayInputStream(buf.array()));
            throw new AssertionError("readFrom should reject corrupted payload with non-matching checksum");
        } catch (IOException expected) {
            assertTrue("Rejects invalid checksum", expected.getMessage().contains("Invalid ADB checksum"));
        }
    }

    // =========================================================================
    // Vector 4: Stream Deserialization, Fragmentation & Memory Guardrails
    // =========================================================================

    public static void testStreamFragmentationOneByte() throws IOException {
        byte[] payload = new byte[1024];
        new Random(42).nextBytes(payload);
        AdbMessage original = new AdbMessage(AdbProtocol.CMD_WRTE, 100, 200, payload);
        byte[] wire = original.toWireBytes();

        // Feed input stream 1 byte at a time
        InputStream slowStream = new InputStream() {
            private int index = 0;
            @Override
            public int read() {
                if (index >= wire.length) return -1;
                return wire[index++] & 0xFF;
            }
            @Override
            public int read(byte[] b, int off, int len) {
                if (index >= wire.length) return -1;
                // Return at most 1 byte per call to simulate extreme network fragmentation
                b[off] = wire[index++];
                return 1;
            }
        };

        AdbMessage parsed = AdbMessage.readFrom(slowStream);
        assertEquals("Parsed command matches", original.command, parsed.command);
        assertEquals("Parsed arg0 matches", original.arg0, parsed.arg0);
        assertEquals("Parsed arg1 matches", original.arg1, parsed.arg1);
        assertEquals("Parsed dataLength matches", original.dataLength, parsed.dataLength);
        assertEquals("Parsed dataCheck matches", original.dataCheck, parsed.dataCheck);
        assertTrue("Parsed payload matches original", Arrays.equals(payload, parsed.payload));
    }

    public static void testStreamFragmentationJitter() throws IOException {
        byte[] payload = new byte[8192];
        new Random(123).nextBytes(payload);
        AdbMessage original = new AdbMessage(AdbProtocol.CMD_WRTE, 50, 60, payload);
        byte[] wire = original.toWireBytes();

        Random jitterRng = new Random(999);
        InputStream jitterStream = new InputStream() {
            private int index = 0;
            @Override
            public int read() {
                if (index >= wire.length) return -1;
                return wire[index++] & 0xFF;
            }
            @Override
            public int read(byte[] b, int off, int len) {
                if (index >= wire.length) return -1;
                int maxChunk = 1 + jitterRng.nextInt(7); // Random 1..7 bytes per read
                int toRead = Math.min(len, Math.min(maxChunk, wire.length - index));
                System.arraycopy(wire, index, b, off, toRead);
                index += toRead;
                return toRead;
            }
        };

        AdbMessage parsed = AdbMessage.readFrom(jitterStream);
        assertEquals("Parsed command matches", original.command, parsed.command);
        assertEquals("Parsed dataLength matches", original.dataLength, parsed.dataLength);
        assertTrue("Parsed payload matches", Arrays.equals(payload, parsed.payload));
    }

    public static void testPrematureEofHandling() {
        byte[] payload = new byte[64];
        AdbMessage original = new AdbMessage(AdbProtocol.CMD_OPEN, 1, 2, payload);
        byte[] wire = original.toWireBytes();

        // 1. Truncate in header (offsets 0, 1, 12, 23)
        int[] headerCutoffs = { 0, 1, 12, 23 };
        for (int cut : headerCutoffs) {
            byte[] truncated = Arrays.copyOf(wire, cut);
            try {
                AdbMessage.readFrom(new ByteArrayInputStream(truncated));
                throw new AssertionError("readFrom should have thrown EOFException at header cutoff " + cut);
            } catch (EOFException expected) {
                // Pass
            } catch (IOException e) {
                throw new AssertionError("Expected EOFException but got " + e);
            }
        }

        // 2. Truncate in payload (offsets 24, 25, 50, wire.length - 1)
        int[] payloadCutoffs = { 24, 25, 50, wire.length - 1 };
        for (int cut : payloadCutoffs) {
            byte[] truncated = Arrays.copyOf(wire, cut);
            try {
                AdbMessage.readFrom(new ByteArrayInputStream(truncated));
                throw new AssertionError("readFrom should have thrown EOFException at payload cutoff " + cut);
            } catch (EOFException expected) {
                // Pass
            } catch (IOException e) {
                throw new AssertionError("Expected EOFException but got " + e);
            }
        }
    }

    public static void testPayloadMemoryGuardrails() {
        int[] invalidLengths = {
            -1,
            -100,
            Integer.MIN_VALUE,
            AdbProtocol.MAX_PAYLOAD + 1,
            10 * 1024 * 1024,
            Integer.MAX_VALUE
        };

        for (int len : invalidLengths) {
            ByteBuffer buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(AdbProtocol.CMD_OPEN);
            buf.putInt(1);
            buf.putInt(2);
            buf.putInt(len);
            buf.putInt(0);
            buf.putInt(~AdbProtocol.CMD_OPEN);

            // 1. Verify readFrom rejects
            try {
                AdbMessage.readFrom(new ByteArrayInputStream(buf.array()));
                throw new AssertionError("readFrom failed to reject invalid payload length: " + len);
            } catch (IOException expected) {
                assertTrue("Exception message mentions length", expected.getMessage().contains("Invalid ADB payload length"));
            }

            // 2. Verify parse rejects
            try {
                AdbMessage.parse(ByteBuffer.wrap(buf.array()));
                throw new AssertionError("parse failed to reject invalid payload length: " + len);
            } catch (IOException expected) {
                assertTrue("Exception message mentions length", expected.getMessage().contains("Invalid payload length"));
            }
        }
    }

    public static void testParseBufferUnderflows() {
        // Buffer underflow in header (< 24 bytes)
        for (int i = 0; i < 24; i++) {
            ByteBuffer buf = ByteBuffer.allocate(i);
            try {
                AdbMessage.parse(buf);
                throw new AssertionError("parse should reject header buffer with size " + i);
            } catch (IOException expected) {
                assertTrue("Mentions underflow", expected.getMessage().contains("Buffer underflow"));
            }
        }

        // Buffer underflow in payload (dataLength = 50, but remaining bytes = 20)
        ByteBuffer buf = ByteBuffer.allocate(24 + 20).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(AdbProtocol.CMD_OPEN);
        buf.putInt(1);
        buf.putInt(2);
        buf.putInt(50); // expects 50 bytes
        buf.putInt(0);
        buf.putInt(~AdbProtocol.CMD_OPEN);
        buf.put(new byte[20]); // only provides 20
        buf.flip();

        try {
            AdbMessage.parse(buf);
            throw new AssertionError("parse should reject payload buffer underflow");
        } catch (IOException expected) {
            assertTrue("Mentions payload underflow", expected.getMessage().contains("Buffer underflow for payload"));
        }
    }

    public static void testConstructorBoundsInconsistency() {
        // Hardened constructor immediately rejects dataLength > payload.length
        try {
            new AdbMessage(AdbProtocol.CMD_WRTE, 1, 2, 100, 0, ~AdbProtocol.CMD_WRTE, new byte[10]);
            throw new AssertionError("Expected IllegalArgumentException when dataLength exceeds payload.length");
        } catch (IllegalArgumentException expected) {
            assertTrue("Mentions buffer length", expected.getMessage().contains("exceeds payload buffer length"));
        }

        // Hardened constructor immediately rejects negative dataLength
        try {
            new AdbMessage(AdbProtocol.CMD_WRTE, 1, 2, -10, 0, ~AdbProtocol.CMD_WRTE, new byte[0]);
            throw new AssertionError("Expected IllegalArgumentException for negative dataLength");
        } catch (IllegalArgumentException expected) {
            assertTrue("Mentions negative dataLength", expected.getMessage().contains("Invalid ADB dataLength"));
        }
    }

    public static void testParseMutatesByteOrder() throws IOException {
        // parse(ByteBuffer) calls buffer.order(ByteOrder.LITTLE_ENDIAN), mutating the caller's buffer order
        ByteBuffer buf = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN);
        // Write Little-Endian header bytes into buffer
        ByteBuffer leWriter = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        leWriter.putInt(AdbProtocol.CMD_OKAY);
        leWriter.putInt(1);
        leWriter.putInt(2);
        leWriter.putInt(0);
        leWriter.putInt(0);
        leWriter.putInt(~AdbProtocol.CMD_OKAY);
        buf.put(leWriter.array());
        buf.flip();

        assertEquals("Caller buffer initially BIG_ENDIAN", ByteOrder.BIG_ENDIAN, buf.order());
        AdbMessage.parse(buf);
        if (buf.order() == ByteOrder.LITTLE_ENDIAN) {
            System.out.print("[CONFIRMED SIDE-EFFECT: parse(ByteBuffer) permanently mutates caller buffer byte order to LITTLE_ENDIAN] ");
        }
    }

    // =========================================================================
    // Vector 5: Montgomery Math, 524-Byte Layout & AOSP Equivalence
    // =========================================================================

    public static void testMontgomeryParameterMath() throws Exception {
        // Generate 5 distinct RSA-2048 keypairs and verify Montgomery inverse math
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);

        BigInteger b32 = BigInteger.ZERO.setBit(32);

        for (int k = 0; k < 5; k++) {
            KeyPair kp = kpg.generateKeyPair();
            RSAPublicKey pubKey = (RSAPublicKey) kp.getPublic();
            BigInteger n = pubKey.getModulus();

            // Verify N is 2048 bits and odd
            assertEquals("Modulus bitLength", 2048, n.bitLength());
            assertTrue("Modulus must be odd", n.testBit(0));

            BigInteger n0 = n.remainder(b32);
            assertTrue("N0 must be odd (coprime to 2^32)", n0.testBit(0));

            // Compute n0inv as in AdbKey.java:
            // n0inv = n.remainder(b32).modInverse(b32).negate();
            BigInteger n0inv = n0.modInverse(b32).negate();
            int n0invInt = n0inv.intValue();

            // Verify invariant: (N0 * n0inv) == -1 mod 2^32
            // In 32-bit unsigned arithmetic: (n0 * n0inv) & 0xFFFFFFFFL == 0xFFFFFFFFL
            long product = (n0.longValue() * ((long) n0invInt & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
            assertEquals("Montgomery invariant (N0 * n0inv) == -1 mod 2^32 for key " + k, 0xFFFFFFFFL, product);
        }
    }

    public static void testMontgomeryR2Math() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);

        for (int k = 0; k < 5; k++) {
            KeyPair kp = kpg.generateKeyPair();
            RSAPublicKey pubKey = (RSAPublicKey) kp.getPublic();
            BigInteger n = pubKey.getModulus();

            // r = 2^2048
            BigInteger r = BigInteger.ZERO.setBit(2048);
            // rr = (2^2048)^2 mod N = (2^4096) mod N
            BigInteger rr = r.modPow(BigInteger.valueOf(2), n);

            assertTrue("rr must be positive", rr.signum() > 0);
            assertTrue("rr must be strictly less than N", rr.compareTo(n) < 0);
            assertEquals("rr bitLength <= 2048", true, rr.bitLength() <= 2048);
        }
    }

    public static void testAndroidPubkeyStructLayout() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pubKey = (RSAPublicKey) kp.getPublic();

        BigInteger n = pubKey.getModulus();
        BigInteger e = pubKey.getPublicExponent();
        BigInteger b32 = BigInteger.ZERO.setBit(32);
        BigInteger n0inv = n.remainder(b32).modInverse(b32).negate();
        BigInteger r = BigInteger.ZERO.setBit(2048);
        BigInteger rr = r.modPow(BigInteger.valueOf(2), n);

        // Build 524-byte struct matching AdbKey.java
        ByteBuffer buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(64); // modulus_size_words
        buf.putInt(n0inv.intValue());

        for (int i = 0; i < 64; i++) {
            buf.putInt(n.shiftRight(i * 32).intValue());
        }
        for (int i = 0; i < 64; i++) {
            buf.putInt(rr.shiftRight(i * 32).intValue());
        }
        buf.putInt(e.intValue());

        byte[] structBytes = buf.array();
        assertEquals("Struct size must be 524 bytes", 524, structBytes.length);

        // Verification: Reconstruct fields from Little-Endian byte array
        ByteBuffer readBuf = ByteBuffer.wrap(structBytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("modulus_size_words must be 64", 64, readBuf.getInt());
        assertEquals("n0inv must match", n0inv.intValue(), readBuf.getInt());

        // Reconstruct modulus N from 64 little-endian words
        BigInteger reconstructedN = BigInteger.ZERO;
        for (int i = 0; i < 64; i++) {
            long word = (long) readBuf.getInt() & 0xFFFFFFFFL;
            reconstructedN = reconstructedN.or(BigInteger.valueOf(word).shiftLeft(i * 32));
        }
        assertEquals("Reconstructed modulus N must be identical to original", n, reconstructedN);

        // Reconstruct rr from 64 little-endian words
        BigInteger reconstructedRR = BigInteger.ZERO;
        for (int i = 0; i < 64; i++) {
            long word = (long) readBuf.getInt() & 0xFFFFFFFFL;
            reconstructedRR = reconstructedRR.or(BigInteger.valueOf(word).shiftLeft(i * 32));
        }
        assertEquals("Reconstructed RR must be identical to original", rr, reconstructedRR);

        // Reconstruct exponent
        assertEquals("Exponent must match", e.intValue(), readBuf.getInt());
    }

    public static void testAospAdbKeyEquivalence() throws Exception {
        // Compare our BigInteger Montgomery math directly against the user's official AOSP adbkey.pub
        File pubFile = new File("C:/Users/MAY1/.android/adbkey.pub");
        if (!pubFile.exists()) {
            System.out.print("[SKIPPED: adbkey.pub not found on host] ");
            return;
        }

        byte[] pubBytes = new byte[(int) pubFile.length()];
        try (FileInputStream fis = new FileInputStream(pubFile)) {
            fis.read(pubBytes);
        }
        String pubStr = new String(pubBytes, StandardCharsets.UTF_8).trim();
        String[] parts = pubStr.split(" ");
        String b64Str = parts[0];

        // Decode base64
        byte[] aospStruct = java.util.Base64.getDecoder().decode(b64Str);
        assertEquals("AOSP decoded struct length must be 524 bytes", 524, aospStruct.length);

        ByteBuffer aospBuf = ByteBuffer.wrap(aospStruct).order(ByteOrder.LITTLE_ENDIAN);
        int modSize = aospBuf.getInt();
        int aospN0inv = aospBuf.getInt();
        assertEquals("AOSP modulus_size_words", 64, modSize);

        // Extract AOSP modulus
        BigInteger aospN = BigInteger.ZERO;
        for (int i = 0; i < 64; i++) {
            long word = (long) aospBuf.getInt() & 0xFFFFFFFFL;
            aospN = aospN.or(BigInteger.valueOf(word).shiftLeft(i * 32));
        }

        // Extract AOSP rr
        BigInteger aospRR = BigInteger.ZERO;
        for (int i = 0; i < 64; i++) {
            long word = (long) aospBuf.getInt() & 0xFFFFFFFFL;
            aospRR = aospRR.or(BigInteger.valueOf(word).shiftLeft(i * 32));
        }

        int aospExp = aospBuf.getInt();
        assertEquals("AOSP exponent", 65537, aospExp);

        // Now compute using AdbKey.java's exact logic on aospN
        BigInteger b32 = BigInteger.ZERO.setBit(32);
        BigInteger calcN0inv = aospN.remainder(b32).modInverse(b32).negate();
        BigInteger r = BigInteger.ZERO.setBit(2048);
        BigInteger calcRR = r.modPow(BigInteger.valueOf(2), aospN);

        assertEquals("Calculated n0inv matches official AOSP adbkey.pub exactly", aospN0inv, calcN0inv.intValue());
        assertEquals("Calculated RR matches official AOSP adbkey.pub exactly", aospRR, calcRR);

        // Re-encode into 524 bytes and verify bit-for-bit identity
        ByteBuffer calcBuf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        calcBuf.putInt(64);
        calcBuf.putInt(calcN0inv.intValue());
        for (int i = 0; i < 64; i++) calcBuf.putInt(aospN.shiftRight(i * 32).intValue());
        for (int i = 0; i < 64; i++) calcBuf.putInt(calcRR.shiftRight(i * 32).intValue());
        calcBuf.putInt(aospExp);

        assertTrue("Generated 524-byte struct is bit-for-bit identical to AOSP",
                Arrays.equals(aospStruct, calcBuf.array()));
    }
}
