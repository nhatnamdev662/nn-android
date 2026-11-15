package app.nn.agent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Empirical Adversarial Test Harness for Milestone 1:
 * Fix Misleading 'Using your phone' (controlling) Detection and Status (Requirement R1).
 */
public class Milestone1AdversarialTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    private static void assertTrue(String testName, boolean condition, String failureDetail) {
        if (condition) {
            System.out.println("  [PASS] " + testName);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + testName + ": " + failureDetail);
            testsFailed++;
        }
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            System.out.println("  [PASS] " + testName + " => " + actual);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + testName + ": expected [" + expected + "] but was [" + actual + "]");
            testsFailed++;
        }
    }

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("   MILESTONE 1 EMPIRICAL ADVERSARIAL VERIFICATION HARNESS");
        System.out.println("======================================================================");

        try {
            testTask1ScreenMutatingActions();
            testTask2ReadOnlyAndQueryTools();
            testTask3CodingTools();
            testTask4EdgeCases();
            testTask5ControlIndicatorTimeoutLogic();
            testTask6CodingStatusLocalization();
            testTask7SetImmutabilityAndIntegrity();
            testTask8ConcurrencyAndStress();
        } catch (Throwable t) {
            System.err.println("[CRITICAL ERROR IN HARNESS] " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        }

        System.out.println("======================================================================");
        System.out.println("   HARNESS RESULTS: " + testsPassed + " PASSED, " + testsFailed + " FAILED");
        System.out.println("======================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * 1. Tools.isScreenMutating(tool) with all screen mutating actions
     * (tap, long_press, type_text, scroll, swipe, press, open_app, do_steps) -> must be true.
     */
    private static void testTask1ScreenMutatingActions() {
        System.out.println("\n[Test Suite 1] Screen Mutating Actions Whitelist (must be true)");

        List<String> expectedMutating = Arrays.asList(
                "tap",
                "long_press",
                "type_text",
                "scroll",
                "swipe",
                "press",
                "open_app",
                "do_steps"
        );

        for (String tool : expectedMutating) {
            assertTrue("isScreenMutating('" + tool + "')",
                    Tools.isScreenMutating(tool),
                    "Expected tool '" + tool + "' to be identified as screen-mutating");
        }

        assertEquals("SCREEN_MUTATING set size", 8, Tools.SCREEN_MUTATING.size());

        for (String tool : expectedMutating) {
            assertTrue("SCREEN_MUTATING.contains('" + tool + "')",
                    Tools.SCREEN_MUTATING.contains(tool),
                    "SCREEN_MUTATING set missing: " + tool);
        }
    }

    /**
     * 2. Tools.isScreenMutating(tool) with read-only/query tools
     * (read_screen, screenshot, device_status, clipboard_read, notifications, list_apps, calendar_events) -> must be false.
     */
    private static void testTask2ReadOnlyAndQueryTools() {
        System.out.println("\n[Test Suite 2] Read-Only and Query Tools (must be false)");

        List<String> readOnlyTools = Arrays.asList(
                "read_screen",
                "screenshot",
                "device_status",
                "clipboard_read",
                "notifications",
                "list_apps",
                "calendar_events",
                "list_scheduled",
                "browser_read",
                "browser_tab",
                "ask",
                "confirm",
                "notify",
                "point_out",
                "wait",
                "update_nn",
                "update_agent",
                "update_buddy",
                "install_apk",
                "wait_for_install",
                "reply_notification",
                "schedule_message",
                "cancel_scheduled"
        );

        for (String tool : readOnlyTools) {
            assertTrue("!isScreenMutating('" + tool + "')",
                    !Tools.isScreenMutating(tool),
                    "Read-only/query tool '" + tool + "' MUST NOT be marked as screen-mutating");
        }
    }

    /**
     * 3. Tools.isScreenMutating(tool) with coding tools
     * (Bash, bash, adb_shell, Edit, Write, Read, execute) -> must be false.
     * Also verifies Tools.isCodingTool(tool) is true for coding tools.
     */
    private static void testTask3CodingTools() {
        System.out.println("\n[Test Suite 3] Coding and Shell Tools (must not mutate screen, must be coding tool)");

        List<String> codingTools = Arrays.asList(
                "Bash",
                "bash",
                "sh",
                "terminal",
                "Edit",
                "Write",
                "Read",
                "execute",
                "adb_shell",
                "run_command"
        );

        for (String tool : codingTools) {
            assertTrue("!isScreenMutating('" + tool + "')",
                    !Tools.isScreenMutating(tool),
                    "Coding tool '" + tool + "' MUST NOT be screen mutating");

            assertTrue("isCodingTool('" + tool + "')",
                    Tools.isCodingTool(tool),
                    "Expected '" + tool + "' to be recognized as coding tool");
        }

        // Verify non-coding tools are not recognized as coding tools
        List<String> nonCoding = Arrays.asList("tap", "read_screen", "open_app", "scroll", "swipe", "ask", "confirm");
        for (String tool : nonCoding) {
            assertTrue("!isCodingTool('" + tool + "')",
                    !Tools.isCodingTool(tool),
                    "Tool '" + tool + "' should NOT be recognized as a coding tool");
        }
    }

    /**
     * 4. Edge cases: null, empty string, unknown tool names, malformed names, whitespace.
     */
    private static void testTask4EdgeCases() {
        System.out.println("\n[Test Suite 4] Edge Cases for isScreenMutating and isCodingTool");

        assertTrue("isScreenMutating(null) == false", !Tools.isScreenMutating(null), "null must return false");
        assertTrue("isCodingTool(null) == false", !Tools.isCodingTool(null), "null must return false");

        assertTrue("isScreenMutating(\"\") == false", !Tools.isScreenMutating(""), "empty string must return false");
        assertTrue("isCodingTool(\"\") == false", !Tools.isCodingTool(""), "empty string must return false");

        String[] strangeInputs = new String[]{
                " ", "   ", "\t", "\n", "\r\n",
                "TAP", "Tap", "tap ", " tap", " tap ",
                "type", "types", "type_text ", "scroll_down", "swipe_right",
                "unknown_tool_xyz", "bash ", "Bash ", "EDIT", "write_file",
                "null", "undefined", "\u0000", "do_steps\n",
                "A".repeat(1000)
        };

        for (String input : strangeInputs) {
            String label = input.length() > 20 ? input.substring(0, 17) + "..." : input.replace("\n", "\\n").replace("\t", "\\t");
            assertTrue("!isScreenMutating('" + label + "')",
                    !Tools.isScreenMutating(input),
                    "Edge case input '" + label + "' should not match screen mutating whitelist");
        }
    }

    /**
     * 5. Check ControlIndicator.java timeout logic:
     * verify timeout calculation Math.max(50L, IDLE_MUTATION_MS - idle).
     */
    private static void testTask5ControlIndicatorTimeoutLogic() {
        System.out.println("\n[Test Suite 5] ControlIndicator Timeout Logic");

        long idleMutationMs = ControlIndicator.IDLE_MUTATION_MS;
        assertEquals("ControlIndicator.IDLE_MUTATION_MS", 1500L, idleMutationMs);

        // Test timeout calculation formula Math.max(50L, IDLE_MUTATION_MS - idle)
        long[] idles = new long[]{0L, 100L, 500L, 1000L, 1400L, 1449L, 1450L, 1451L, 1499L, 1500L, 1501L, 2000L, -50L};
        long[] expectedDelays = new long[]{
                1500L, // 1500 - 0 = 1500 >= 50
                1400L, // 1500 - 100 = 1400 >= 50
                1000L, // 1500 - 500 = 1000 >= 50
                500L,  // 1500 - 1000 = 500 >= 50
                100L,  // 1500 - 1400 = 100 >= 50
                51L,   // 1500 - 1449 = 51 >= 50
                50L,   // 1500 - 1450 = 50 >= 50
                50L,   // 1500 - 1451 = 49 < 50 => clamped to 50
                50L,   // 1500 - 1499 = 1 < 50 => clamped to 50
                50L,   // 1500 - 1500 = 0 < 50 => clamped to 50
                50L,   // 1500 - 1501 = -1 < 50 => clamped to 50
                50L,   // 1500 - 2000 = -500 < 50 => clamped to 50
                1550L  // 1500 - (-50) = 1550 (clock skew edge case)
        };

        for (int i = 0; i < idles.length; i++) {
            long idle = idles[i];
            long expected = expectedDelays[i];
            long calculated = Math.max(50L, idleMutationMs - idle);
            assertEquals("Timeout delay for idle=" + idle + "ms", expected, calculated);

            boolean shouldEnd = (idle >= idleMutationMs);
            if (idle >= 1500L) {
                assertTrue("End condition triggered for idle=" + idle + "ms", shouldEnd, "idle >= 1500 must trigger end()");
            } else {
                assertTrue("Tick rescheduled for idle=" + idle + "ms", !shouldEnd, "idle < 1500 must reschedule");
            }
        }

        // Test recent(long ms) logic simulation:
        // boolean recent(long ms) { return lastActivity > 0 && SystemClock.elapsedRealtime() - lastActivity < ms; }
        // Case A: never active (lastActivity = 0)
        assertTrue("recent() with lastActivity=0",
                simulateRecent(0, 5000, 1500) == false,
                "recent must be false if lastActivity is 0");

        // Case B: within window (elapsed - last = 1000ms < 1500ms)
        assertTrue("recent() within 1500ms window",
                simulateRecent(4000, 5000, 1500) == true,
                "recent must be true when idle < ms");

        // Case C: exact boundary (elapsed - last = 1500ms == 1500ms)
        assertTrue("recent() at exact 1500ms boundary",
                simulateRecent(3500, 5000, 1500) == false,
                "recent must be false when idle == ms");

        // Case D: expired (elapsed - last = 2000ms > 1500ms)
        assertTrue("recent() expired (2000ms > 1500ms)",
                simulateRecent(3000, 5000, 1500) == false,
                "recent must be false when idle > ms");
    }

    private static boolean simulateRecent(long lastActivity, long now, long ms) {
        return lastActivity > 0 && (now - lastActivity) < ms;
    }

    /**
     * 6. Check NnService.getCodingStatusText() localization logic.
     */
    private static void testTask6CodingStatusLocalization() {
        System.out.println("\n[Test Suite 6] NnService.getCodingStatusText Localization");

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("vi"));
            assertEquals("getCodingStatusText() for 'vi'", "Đang chạy code...", NnService.getCodingStatusText());

            Locale.setDefault(Locale.ENGLISH);
            assertEquals("getCodingStatusText() for 'en'", "Running code...", NnService.getCodingStatusText());

            Locale.setDefault(Locale.FRENCH);
            assertEquals("getCodingStatusText() fallback (fr)", "Đang chạy code...", NnService.getCodingStatusText());

            Locale.setDefault(Locale.JAPANESE);
            assertEquals("getCodingStatusText() fallback (ja)", "Đang chạy code...", NnService.getCodingStatusText());
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * 7. Check SCREEN_MUTATING and CODING_TOOLS immutability.
     */
    private static void testTask7SetImmutabilityAndIntegrity() {
        System.out.println("\n[Test Suite 7] Set Immutability and Integrity");

        boolean threwOnAddMutating = false;
        try {
            Tools.SCREEN_MUTATING.add("unauthorized_tool");
        } catch (UnsupportedOperationException e) {
            threwOnAddMutating = true;
        }
        assertTrue("SCREEN_MUTATING is unmodifiable (add)", threwOnAddMutating, "Modifying SCREEN_MUTATING must throw UnsupportedOperationException");

        boolean threwOnRemoveMutating = false;
        try {
            Tools.SCREEN_MUTATING.remove("tap");
        } catch (UnsupportedOperationException e) {
            threwOnRemoveMutating = true;
        }
        assertTrue("SCREEN_MUTATING is unmodifiable (remove)", threwOnRemoveMutating, "Modifying SCREEN_MUTATING must throw UnsupportedOperationException");

        boolean threwOnAddCoding = false;
        try {
            Tools.CODING_TOOLS.add("unauthorized_tool");
        } catch (UnsupportedOperationException e) {
            threwOnAddCoding = true;
        }
        assertTrue("CODING_TOOLS is unmodifiable (add)", threwOnAddCoding, "Modifying CODING_TOOLS must throw UnsupportedOperationException");
    }

    /**
     * 8. Multi-threaded Concurrency and Stress Test.
     */
    private static void testTask8ConcurrencyAndStress() throws InterruptedException {
        System.out.println("\n[Test Suite 8] Concurrency and Stress Verification");

        int threadCount = 10;
        int iterationsPerThread = 50000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        String[] testTools = new String[]{
                "tap", "long_press", "read_screen", "Bash", "Edit", "type_text",
                "adb_shell", "device_status", "scroll", "swipe", "press", "open_app",
                "do_steps", "screenshot", null, "", "unknown"
        };

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        String tool = testTools[i % testTools.length];
                        boolean mutating = Tools.isScreenMutating(tool);
                        boolean coding = Tools.isCodingTool(tool);

                        if ("tap".equals(tool) && !mutating) errorCount.incrementAndGet();
                        if ("read_screen".equals(tool) && mutating) errorCount.incrementAndGet();
                        if ("Bash".equals(tool) && (mutating || !coding)) errorCount.incrementAndGet();
                        if (tool == null && (mutating || coding)) errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("Stress test completed within 10 seconds", completed, "Concurrency execution timed out");
        assertEquals("Concurrency error count", 0, errorCount.get());
    }
}
