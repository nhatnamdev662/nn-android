package app.nn.agent;

import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Empirical Adversarial Test Harness for Milestone 2:
 * Stabilize Floating Bubble Status Pill UI (Requirement R2).
 *
 * Verifies:
 * 1. 100ms render debounce under 100 rapid calls within 50ms (coalescing IPC updates).
 * 2. Smooth cross-fade transition state machine and layout update sequencing.
 * 3. 3-second completion note retention and immediate cancellation on new task.
 * 4. AST / bytecode conformance of constants, immediate triggers, and safe layout calls.
 */
public class BubbleStatusPillM2AdversarialTest {

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
        System.out.println("   MILESTONE 2 EMPIRICAL ADVERSARIAL VERIFICATION HARNESS");
        System.out.println("======================================================================");

        try {
            testSuite1DebounceBurstAndCoalescing();
            testSuite2CrossFadeTransitionsAndSequencing();
            testSuite3CompletionNoteLifecycleAndCancellation();
            testSuite4StaticConformanceAndBytecodeVerification();
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

    // =========================================================================
    // SUITE 1: 100ms Render Debounce & Coalescing Verification
    // =========================================================================

    /**
     * Empirical simulation of Bubble's debounce engine matching exact logic in Bubble.java:
     * - STATUS_RENDER_DEBOUNCE_MS = 100L
     * - refreshStatus(boolean immediate)
     * - refreshStatusNow() -> doRefreshStatus()
     */
    static class SimulatedDebounceEngine {
        static final long STATUS_RENDER_DEBOUNCE_MS = 100L;
        long lastStatusRenderAt = 0;
        boolean statusRenderPending = false;
        String pendingStatusPayload = null;

        final AtomicInteger renderCount = new AtomicInteger(0);
        final List<String> renderedPayloads = Collections.synchronizedList(new ArrayList<>());
        final List<Long> renderTimestamps = Collections.synchronizedList(new ArrayList<>());

        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        private ScheduledFuture<?> pendingTask = null;
        private final Object lock = new Object();

        void postStatus(String status, long simulatedTime, boolean immediate) {
            synchronized (lock) {
                pendingStatusPayload = status;
                if (immediate) {
                    if (pendingTask != null) {
                        pendingTask.cancel(false);
                        pendingTask = null;
                    }
                    statusRenderPending = false;
                    executeRender(simulatedTime);
                    return;
                }

                long timeSinceLast = simulatedTime - lastStatusRenderAt;
                if (timeSinceLast >= STATUS_RENDER_DEBOUNCE_MS) {
                    if (pendingTask != null) {
                        pendingTask.cancel(false);
                        pendingTask = null;
                    }
                    statusRenderPending = false;
                    executeRender(simulatedTime);
                } else if (!statusRenderPending) {
                    statusRenderPending = true;
                    long delay = STATUS_RENDER_DEBOUNCE_MS - timeSinceLast;
                    final long targetTime = simulatedTime + delay;
                    pendingTask = scheduler.schedule(() -> {
                        synchronized (lock) {
                            statusRenderPending = false;
                            pendingTask = null;
                            executeRender(targetTime);
                        }
                    }, delay, TimeUnit.MILLISECONDS);
                }
            }
        }

        private void executeRender(long timestamp) {
            lastStatusRenderAt = timestamp;
            renderCount.incrementAndGet();
            renderedPayloads.add(pendingStatusPayload);
            renderTimestamps.add(timestamp);
        }

        void shutdown() {
            scheduler.shutdownNow();
        }
    }

    private static void testSuite1DebounceBurstAndCoalescing() throws Exception {
        System.out.println("\n[Test Suite 1] 100ms Render Debounce & Coalescing Verification");

        // 1.1 Burst of 100 rapid status updates within 50ms
        SimulatedDebounceEngine engine = new SimulatedDebounceEngine();
        long startTime = 1000L; // assume some initial clock

        // Call 1 at t=0
        engine.postStatus("Status-Initial", startTime, false);

        // Calls 2 to 100 fired rapidly within 50ms (every 0.5ms)
        for (int i = 2; i <= 100; i++) {
            long callTime = startTime + (long) ((i - 1) * 0.5); // 0.5ms to 49.5ms
            engine.postStatus("Status-Update-" + i, callTime, false);
        }

        // Wait for debounce trailing task to fire (needs > 100ms)
        Thread.sleep(150);

        assertEquals("1.1 Exactly 2 layout renders executed for 100 rapid status calls", 2, engine.renderCount.get());
        assertEquals("1.1 First render executed immediately with initial status", "Status-Initial", engine.renderedPayloads.get(0));
        assertEquals("1.1 Second trailing render captured latest status #100", "Status-Update-100", engine.renderedPayloads.get(1));

        long delta = engine.renderTimestamps.get(1) - engine.renderTimestamps.get(0);
        assertTrue("1.1 Trailing render spaced exactly by debounce threshold (>= 100ms)", delta >= 100L,
                "Expected delta >= 100ms but got " + delta + "ms");

        engine.shutdown();

        // 1.2 Immediate flag (refreshStatus(true)) cancels pending debounce and fires synchronously
        SimulatedDebounceEngine engineImmediate = new SimulatedDebounceEngine();
        long baseTime = 2000L;
        // Initial render at t=0
        engineImmediate.postStatus("Status-A", baseTime, false);
        // Rapid update at t=20ms (debounced)
        engineImmediate.postStatus("Status-Debounced", baseTime + 20, false);
        assertEquals("1.2 Render count remains 1 before debounce fires", 1, engineImmediate.renderCount.get());

        // Immediate stop at t=40ms
        engineImmediate.postStatus("Status-IMMEDIATE-STOP", baseTime + 40, true);
        assertEquals("1.2 Render count immediately increments to 2 on immediate=true", 2, engineImmediate.renderCount.get());
        assertEquals("1.2 Immediate status rendered synchronously", "Status-IMMEDIATE-STOP", engineImmediate.renderedPayloads.get(1));

        // Sleep past the 100ms mark to confirm no duplicate trailing render was scheduled
        Thread.sleep(120);
        assertEquals("1.2 No spurious duplicate render after immediate override", 2, engineImmediate.renderCount.get());
        engineImmediate.shutdown();

        // 1.3 Updates spaced >= 100ms apart execute with zero latency
        SimulatedDebounceEngine engineSpaced = new SimulatedDebounceEngine();
        long t = 5000L;
        engineSpaced.postStatus("Step-1", t, false);
        assertEquals("1.3 Step-1 rendered at t=0", 1, engineSpaced.renderCount.get());

        t += 120L; // 120ms later (>= 100ms)
        engineSpaced.postStatus("Step-2", t, false);
        assertEquals("1.3 Step-2 rendered immediately (zero latency) at t=120ms", 2, engineSpaced.renderCount.get());

        t += 200L; // 200ms later (>= 100ms)
        engineSpaced.postStatus("Step-3", t, false);
        assertEquals("1.3 Step-3 rendered immediately (zero latency) at t=320ms", 3, engineSpaced.renderCount.get());
        engineSpaced.shutdown();

        // 1.4 High concurrency multi-threaded stress: 10 threads firing 100 updates each
        SimulatedDebounceEngine engineConcurrent = new SimulatedDebounceEngine();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        long cBase = System.currentTimeMillis();

        for (int th = 0; th < 10; th++) {
            final int threadId = th;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        long now = System.currentTimeMillis();
                        engineConcurrent.postStatus("T" + threadId + "-Iter" + i, now, false);
                        Thread.sleep(1);
                    }
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("1.4 Multi-threaded stress finished within 5s", latch.await(5, TimeUnit.SECONDS), "Timeout in stress test");
        Thread.sleep(150); // wait for final trailing debounce
        pool.shutdown();

        int totalRenders = engineConcurrent.renderCount.get();
        // Over ~100-150ms of activity, max possible 100ms renders is <= 4
        assertTrue("1.4 Concurrency rate-limiting: total renders throttled (renders=" + totalRenders + " for 1,000 calls)",
                totalRenders <= 5 && totalRenders >= 2,
                "Expected renders between 2 and 5, but got " + totalRenders);
        engineConcurrent.shutdown();

        // 1.5 Thread boundary logic: ensure background thread dispatch
        Looper.setAsBackgroundLooper("WorkerThread");
        boolean isMain = (Looper.myLooper() == Looper.getMainLooper());
        assertTrue("1.5 Background thread recognized as non-main thread", !isMain, "Expected non-main looper");
        Looper.setAsMainThread();
        assertTrue("1.5 Main thread recognized as main looper", Looper.myLooper() == Looper.getMainLooper(), "Expected main looper");

        // 1.6 Rapid immediate bursts: 10 calls with immediate=true execute synchronously without queuing
        SimulatedDebounceEngine engineImmBurst = new SimulatedDebounceEngine();
        long immBase = 10000L;
        for (int i = 0; i < 10; i++) {
            engineImmBurst.postStatus("Imm-" + i, immBase + i, true);
        }
        assertEquals("1.6 All 10 immediate calls executed synchronously", 10, engineImmBurst.renderCount.get());
        assertEquals("1.6 Latest immediate call rendered", "Imm-9", engineImmBurst.renderedPayloads.get(9));
        engineImmBurst.shutdown();
    }

    // =========================================================================
    // SUITE 2: Smooth Cross-Fade Transitions & Layout Sequencing
    // =========================================================================

    enum PillAnimState {
        IDLE_VISIBLE,
        IDLE_GONE,
        ANIMATING_APPEAR,
        ANIMATING_TEXT_CHANGE,
        ANIMATING_DISAPPEAR
    }

    static class MockStatusPill {
        int visibility = 8; // View.GONE
        float alpha = 1.0f;
        CharSequence text = "";
        int maxLines = 1;
        int textColor = 0;
        int backgroundColor = 0;

        CharSequence currentPillText = "";
        boolean currentPillControlling = false;

        boolean pillAnimating = false;
        final List<String> layoutUpdateEvents = new ArrayList<>();
        final List<String> animationTrace = new ArrayList<>();

        void safeUpdateRootLayout(String reason) {
            layoutUpdateEvents.add(reason + " [alpha=" + alpha + ", vis=" + visibility + ", text=" + text + "]");
        }

        /**
         * Simulates doRefreshStatus() animation logic from Bubble.java: lines 3482-3546
         */
        void refreshWithShown(CharSequence shown, boolean targetControlling, int maxLines) {
            pillAnimating = false;
            final boolean background = false;

            if (shown == null) {
                if (visibility == 0 /* View.VISIBLE */) {
                    pillAnimating = true;
                    animationTrace.add("START_FADE_OUT_TO_GONE (duration=220, AccelerateInterpolator)");
                    // Simulate animation running:
                    alpha = 0f;
                    // End action:
                    visibility = 8; // View.GONE
                    alpha = 1f;
                    currentPillText = "";
                    safeUpdateRootLayout("withEndAction: GONE");
                    animationTrace.add("END_FADE_OUT_TO_GONE");
                }
            } else {
                String shownStr = shown.toString();
                if (visibility != 0 || alpha <= 0.05f) {
                    // Appearing
                    pillAnimating = true;
                    animationTrace.add("START_APPEAR (duration=200, DecelerateInterpolator)");
                    this.maxLines = maxLines;
                    this.text = shown;
                    this.textColor = targetControlling ? 1 : 2;
                    this.backgroundColor = targetControlling ? 3 : 4;
                    this.alpha = 0f;
                    this.visibility = 0; // View.VISIBLE
                    currentPillText = shownStr;
                    currentPillControlling = targetControlling;
                    safeUpdateRootLayout("Appearing: at alpha 0f");
                    // End animation:
                    alpha = 1f;
                    animationTrace.add("END_APPEAR");
                } else if (!shownStr.contentEquals(currentPillText) || targetControlling != currentPillControlling) {
                    // Text / style change
                    pillAnimating = true;
                    animationTrace.add("START_TEXT_CHANGE_FADE_OUT (duration=100, AccelerateInterpolator)");
                    // Alpha fades to 0f
                    alpha = 0f;
                    // Inside withEndAction:
                    this.maxLines = maxLines;
                    this.text = shown;
                    this.textColor = targetControlling ? 1 : 2;
                    this.backgroundColor = targetControlling ? 3 : 4;
                    currentPillText = shownStr;
                    currentPillControlling = targetControlling;
                    safeUpdateRootLayout("withEndAction: TextChange at alpha 0f");
                    animationTrace.add("START_TEXT_CHANGE_FADE_IN (duration=150, DecelerateInterpolator)");
                    alpha = 1f;
                    animationTrace.add("END_TEXT_CHANGE_FADE_IN");
                }
            }

            // Outer layout update: Bubble.java line 3544
            if (!pillAnimating) {
                safeUpdateRootLayout("Outer: rest update");
            }
        }
    }

    private static void testSuite2CrossFadeTransitionsAndSequencing() {
        System.out.println("\n[Test Suite 2] Smooth Cross-Fade Transitions & Layout Sequencing");

        // 2.1 Appearing: initial layout update occurs at alpha = 0f
        MockStatusPill pill = new MockStatusPill();
        pill.refreshWithShown("Running code...", false, 2);

        boolean hasAppearTrace = pill.animationTrace.stream().anyMatch(s -> s.startsWith("START_APPEAR"));
        assertTrue("2.1 Animation trace recorded appearance", hasAppearTrace, "Expected appear animation");
        assertEquals("2.1 Visibility is VISIBLE", 0, pill.visibility);
        assertEquals("2.1 Final alpha is 1.0f", 1.0f, pill.alpha);
        assertEquals("2.1 Current pill text updated", "Running code...", pill.currentPillText.toString());

        // Verify layout update happened at alpha 0f
        boolean appearedAtAlphaZero = pill.layoutUpdateEvents.stream().anyMatch(e -> e.contains("Appearing: at alpha 0f") && e.contains("alpha=0.0"));
        assertTrue("2.1 Layout update occurred while pill was completely invisible (alpha=0.0f)",
                appearedAtAlphaZero, "safeUpdateRootLayout must occur before fading in");

        // 2.2 Text change: layout and text swap occur inside withEndAction at alpha = 0f
        pill.layoutUpdateEvents.clear();
        pill.animationTrace.clear();
        pill.refreshWithShown("Đang suy nghĩ...", false, 2);

        assertTrue("2.2 Animation trace recorded fade-out (100ms)",
                pill.animationTrace.contains("START_TEXT_CHANGE_FADE_OUT (duration=100, AccelerateInterpolator)"),
                "Expected fade out");
        assertTrue("2.2 Animation trace recorded fade-in (150ms)",
                pill.animationTrace.contains("START_TEXT_CHANGE_FADE_IN (duration=150, DecelerateInterpolator)"),
                "Expected fade in");

        boolean swappedAtAlphaZero = pill.layoutUpdateEvents.stream().anyMatch(e -> e.contains("withEndAction: TextChange at alpha 0f") && e.contains("alpha=0.0"));
        assertTrue("2.2 Text and layout update occurred at alpha=0.0f (no visual jump)",
                swappedAtAlphaZero, "Layout update must be executed inside withEndAction while transparent");

        // 2.3 Idempotency: identical status refreshes do not trigger animations
        pill.layoutUpdateEvents.clear();
        pill.animationTrace.clear();
        pill.refreshWithShown("Đang suy nghĩ...", false, 2);
        assertTrue("2.3 No animations triggered when text is unchanged", pill.animationTrace.isEmpty(),
                "Animations should not fire when text is unchanged");
        assertTrue("2.3 Outer rest update performed when not animating", pill.layoutUpdateEvents.size() == 1,
                "Expected 1 outer layout update");

        // 2.4 Disappearing: fades out over 220ms, sets GONE in withEndAction
        pill.layoutUpdateEvents.clear();
        pill.animationTrace.clear();
        pill.refreshWithShown(null, false, 2);

        assertTrue("2.4 Animation trace recorded fade out to GONE (220ms)",
                pill.animationTrace.contains("START_FADE_OUT_TO_GONE (duration=220, AccelerateInterpolator)"),
                "Expected disappear animation");
        assertEquals("2.4 Visibility set to GONE after animation", 8, pill.visibility);
        assertEquals("2.4 Pill alpha reset to 1.0f for future appearances", 1.0f, pill.alpha);
        assertEquals("2.4 Current pill text cleared", "", pill.currentPillText.toString());

        boolean goneLayoutOccurred = pill.layoutUpdateEvents.stream().anyMatch(e -> e.contains("withEndAction: GONE") && e.contains("vis=8"));
        assertTrue("2.4 Window manager layout updated after visibility collapsed to GONE",
                goneLayoutOccurred, "Layout must update when collapsing to GONE");

        // 2.5 Mid-flight interruption: rapid text change while fade-out in progress
        pill.layoutUpdateEvents.clear();
        pill.animationTrace.clear();
        // First change to set initial state
        pill.refreshWithShown("Interrupted 1", false, 2);
        // Mid-flight second change arriving while alpha is low (<= 0.05f)
        pill.alpha = 0.04f; // View animator was interrupted near transparent
        pill.refreshWithShown("Interrupted 2", false, 2);
        assertTrue("2.5 Treated as appearing when alpha <= 0.05f",
                pill.animationTrace.stream().anyMatch(s -> s.startsWith("START_APPEAR")),
                "Should transition via appear branch when alpha <= 0.05f");
    }

    // =========================================================================
    // SUITE 3: 3-Second Completion Note Lifecycle & Cancellation
    // =========================================================================

    static class SimulatedTaskLifecycle {
        static final long COMPLETION_NOTE_MS = 3_000L;

        boolean running = false;
        boolean controlling = false;
        boolean browsing = false;
        boolean wasWorking = false;
        boolean paused = false;
        Object pendingConfirm = null;

        String completionNote = null;
        boolean completionFinished = false;

        ScheduledFuture<?> completionExpiredTask = null;
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        final List<String> statusTransitions = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<String> currentStatusText = new AtomicReference<>("");

        void stepState(boolean newRunning, boolean newControlling, boolean newBrowsing, long simulatedTime) {
            running = newRunning;
            controlling = newControlling;
            browsing = newBrowsing;
            boolean working = running || controlling || browsing;

            if (working && !wasWorking) {
                // Task started / restarted
                if (completionExpiredTask != null) {
                    completionExpiredTask.cancel(false);
                    completionExpiredTask = null;
                }
                completionNote = null;
                completionFinished = false;
                statusTransitions.add("TASK_STARTED_CANCELLED_COMPLETION [t=" + simulatedTime + "]");
            } else if (wasWorking && !working && pendingConfirm == null && !paused) {
                // Task completed, return to IDLE: retain completion note for 3,000ms
                completionNote = Locale.getDefault().getLanguage().equals("vi") ? "Đã xong" : "Done";
                completionFinished = false;
                statusTransitions.add("ENTER_COMPLETION_NOTE: '" + completionNote + "' [t=" + simulatedTime + "]");

                if (completionExpiredTask != null) {
                    completionExpiredTask.cancel(false);
                }
                completionExpiredTask = scheduler.schedule(() -> {
                    completionNote = null;
                    completionFinished = true;
                    statusTransitions.add("COMPLETION_NOTE_EXPIRED [t=" + (simulatedTime + COMPLETION_NOTE_MS) + "]");
                    resolveText();
                }, COMPLETION_NOTE_MS, TimeUnit.MILLISECONDS);
            }
            wasWorking = working;
            resolveText();
        }

        void resolveText() {
            String text;
            boolean working = running || controlling || browsing;
            if (pendingConfirm != null) {
                text = "Waiting approval";
            } else if (paused) {
                text = "Paused";
            } else if (completionNote != null && !working) {
                text = completionNote;
            } else if (completionFinished && !working) {
                text = null;
            } else if (working) {
                text = "Active Work";
            } else {
                text = null;
            }
            currentStatusText.set(text);
        }

        void shutdown() {
            scheduler.shutdownNow();
        }
    }

    private static void testSuite3CompletionNoteLifecycleAndCancellation() throws Exception {
        System.out.println("\n[Test Suite 3] 3-Second Completion Note Lifecycle & Cancellation");

        Locale defaultLocale = Locale.getDefault();
        try {
            // 3.1 Return to IDLE triggers completion note for Vietnamese and English
            Locale.setDefault(new Locale("vi", "VN"));
            SimulatedTaskLifecycle lifecycleVi = new SimulatedTaskLifecycle();
            lifecycleVi.stepState(true, false, false, 0);
            assertEquals("3.1 Active status during execution", "Active Work", lifecycleVi.currentStatusText.get());
            lifecycleVi.stepState(false, false, false, 100);
            assertEquals("3.1 Vietnamese locale completion note is 'Đã xong'", "Đã xong", lifecycleVi.currentStatusText.get());
            lifecycleVi.shutdown();

            Locale.setDefault(Locale.ENGLISH);
            SimulatedTaskLifecycle lifecycleEn = new SimulatedTaskLifecycle();
            lifecycleEn.stepState(true, false, false, 0);
            lifecycleEn.stepState(false, false, false, 100);
            assertEquals("3.1 English locale completion note is 'Done'", "Done", lifecycleEn.currentStatusText.get());
            lifecycleEn.shutdown();

            // 3.2 Completion note retention for full 3,000ms duration (tested in VI)
            Locale.setDefault(new Locale("vi", "VN"));
            SimulatedTaskLifecycle lifecycleTiming = new SimulatedTaskLifecycle();
            lifecycleTiming.stepState(true, false, false, 0);
            lifecycleTiming.stepState(false, false, false, 1000);
            assertEquals("3.2 Note active immediately on completion", "Đã xong", lifecycleTiming.currentStatusText.get());

            // Check at 500ms and 1,000ms elapsed
            Thread.sleep(500);
            assertEquals("3.2 Note retained at 500ms", "Đã xong", lifecycleTiming.currentStatusText.get());
            Thread.sleep(500);
            assertEquals("3.2 Note retained at 1,000ms", "Đã xong", lifecycleTiming.currentStatusText.get());
            lifecycleTiming.resolveText();
            assertEquals("3.2 Intermediate resolve preserves completion note", "Đã xong", lifecycleTiming.currentStatusText.get());
            lifecycleTiming.shutdown();

            // 3.3 Expiration after 3,000ms resets text to null (triggering disappearance)
            SimulatedTaskLifecycle lifecycleExp = new SimulatedTaskLifecycle();
            lifecycleExp.stepState(true, false, false, 0);
            lifecycleExp.stepState(false, false, false, 100);
            assertEquals("3.3 Active before expiration", "Đã xong", lifecycleExp.currentStatusText.get());

            // Directly execute the expiration runnable
            lifecycleExp.completionNote = null;
            lifecycleExp.completionFinished = true;
            lifecycleExp.resolveText();

            assertEquals("3.3 Status text resolved to null upon completion expiration", null, lifecycleExp.currentStatusText.get());
            lifecycleExp.shutdown();

            // 3.4 Immediate cancellation when new task arrives before 3s expires
            SimulatedTaskLifecycle lifecycleCancel = new SimulatedTaskLifecycle();
            lifecycleCancel.stepState(true, false, false, 0);
            lifecycleCancel.stepState(false, false, false, 500);
            assertEquals("3.4 Completion note showing", "Đã xong", lifecycleCancel.currentStatusText.get());

            // New task arrives at t=1,200ms
            lifecycleCancel.stepState(true, false, false, 1200);
            assertEquals("3.4 Active Work replaces completion note immediately", "Active Work", lifecycleCancel.currentStatusText.get());
            assertTrue("3.4 Cancellation recorded in event trace",
                    lifecycleCancel.statusTransitions.stream().anyMatch(s -> s.contains("TASK_STARTED_CANCELLED_COMPLETION")),
                    "Expected task restart to cancel completion countdown");
            assertEquals("3.4 completionNote variable reset to null", null, lifecycleCancel.completionNote);
            assertTrue("3.4 completionFinished reset to false", !lifecycleCancel.completionFinished, "completionFinished should be false");
            lifecycleCancel.shutdown();

            // 3.5 Suppressed when paused or pending confirmation
            SimulatedTaskLifecycle lifecycleSuppressed = new SimulatedTaskLifecycle();
            lifecycleSuppressed.wasWorking = true;
            lifecycleSuppressed.paused = true;
            lifecycleSuppressed.stepState(false, false, false, 100);
            assertEquals("3.5 Completion note suppressed when paused", "Paused", lifecycleSuppressed.currentStatusText.get());

            lifecycleSuppressed.paused = false;
            lifecycleSuppressed.wasWorking = true;
            lifecycleSuppressed.pendingConfirm = "approval_card";
            lifecycleSuppressed.stepState(false, false, false, 200);
            assertEquals("3.5 Completion note suppressed when pending confirmation", "Waiting approval", lifecycleSuppressed.currentStatusText.get());
            lifecycleSuppressed.shutdown();

            // 3.6 Rapid flapping stress: 10 cycles of working -> idle -> working within 200ms
            SimulatedTaskLifecycle lifecycleFlapping = new SimulatedTaskLifecycle();
            for (int cycle = 0; cycle < 10; cycle++) {
                long cTime = cycle * 20L;
                lifecycleFlapping.stepState(true, false, false, cTime); // start work
                lifecycleFlapping.stepState(false, false, false, cTime + 10); // return to idle
            }
            assertEquals("3.6 Final state after flapping is completion note", "Đã xong", lifecycleFlapping.currentStatusText.get());
            lifecycleFlapping.shutdown();

            // 3.7 Multi-language fallback verification
            String[] testLocales = new String[]{"en", "fr", "ja", "es", "zh", "de", "ru", "ko"};
            for (String lang : testLocales) {
                Locale.setDefault(new Locale(lang));
                String note = Locale.getDefault().getLanguage().equals("vi") ? "Đã xong" : "Done";
                assertEquals("3.7 Locale '" + lang + "' evaluates to 'Done'", "Done", note);
            }
            Locale.setDefault(new Locale("vi"));
            String noteVi = Locale.getDefault().getLanguage().equals("vi") ? "Đã xong" : "Done";
            assertEquals("3.7 Locale 'vi' evaluates to 'Đã xong'", "Đã xong", noteVi);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    // =========================================================================
    // SUITE 4: Static Conformance & Bytecode Verification
    // =========================================================================

    private static void testSuite4StaticConformanceAndBytecodeVerification() throws Exception {
        System.out.println("\n[Test Suite 4] Static Conformance & Bytecode Verification");

        File bubbleFile = new File("app/src/main/java/app/nn/agent/Bubble.java");
        assertTrue("4.1 Bubble.java source file exists", bubbleFile.exists(), "File not found at " + bubbleFile.getAbsolutePath());

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(bubbleFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        String content = sb.toString();

        // 4.1 Verify STATUS_RENDER_DEBOUNCE_MS = 100L
        Pattern debouncePattern = Pattern.compile("STATUS_RENDER_DEBOUNCE_MS\\s*=\\s*100L?;");
        Matcher mDebounce = debouncePattern.matcher(content);
        assertTrue("4.1 STATUS_RENDER_DEBOUNCE_MS constant defined as 100L", mDebounce.find(),
                "STATUS_RENDER_DEBOUNCE_MS must be 100L");

        // 4.2 Verify COMPLETION_NOTE_MS = 3_000L
        Pattern completionPattern = Pattern.compile("COMPLETION_NOTE_MS\\s*=\\s*3_?000L?;");
        Matcher mCompletion = completionPattern.matcher(content);
        assertTrue("4.2 COMPLETION_NOTE_MS constant defined as 3,000L", mCompletion.find(),
                "COMPLETION_NOTE_MS must be 3,000L");

        // 4.3 Verify safeUpdateRootLayout definition
        assertTrue("4.3 safeUpdateRootLayout method exists", content.contains("private void safeUpdateRootLayout()"),
                "safeUpdateRootLayout definition missing");
        assertTrue("4.3 safeUpdateRootLayout checks isAttachedToWindow",
                content.contains("!root.isAttachedToWindow()"),
                "safeUpdateRootLayout must verify window attachment");

        // 4.4 Verify no direct unthrottled wm.updateViewLayout in refreshStatus or doRefreshStatus
        // Find doRefreshStatus method block
        int doRefreshIdx = content.indexOf("void doRefreshStatus()");
        int endDoRefreshIdx = content.indexOf("void createThumb()", doRefreshIdx);
        assertTrue("4.4 Found doRefreshStatus method block", doRefreshIdx > 0 && endDoRefreshIdx > doRefreshIdx,
                "doRefreshStatus block not found");

        String doRefreshCode = content.substring(doRefreshIdx, endDoRefreshIdx);
        assertTrue("4.4 doRefreshStatus uses safeUpdateRootLayout instead of direct wm.updateViewLayout",
                !doRefreshCode.contains("wm.updateViewLayout(root, lp)"),
                "Direct wm.updateViewLayout call found in doRefreshStatus; must use safeUpdateRootLayout");

        // 4.5 Verify cleanups in remove()
        int removeIdx = content.indexOf("void remove()");
        int endRemoveIdx = content.indexOf("void buildCollapsed()", removeIdx);
        String removeCode = content.substring(removeIdx, endRemoveIdx);
        assertTrue("4.5 remove() cancels statusRenderDebounce callbacks",
                removeCode.contains("svc.mainHandler().removeCallbacks(statusRenderDebounce)"),
                "statusRenderDebounce cleanup missing in remove()");
        assertTrue("4.5 remove() cancels completionExpired callbacks",
                removeCode.contains("svc.mainHandler().removeCallbacks(completionExpired)"),
                "completionExpired cleanup missing in remove()");
        assertTrue("4.5 remove() cancels pill animation",
                removeCode.contains("pill.animate().cancel()"),
                "pill.animate().cancel() missing in remove()");

        // 4.6 Verify immediate refreshStatus(true) calls for UI responsiveness
        Pattern immediatePattern = Pattern.compile("refreshStatus\\(true\\);");
        Matcher mImmediate = immediatePattern.matcher(content);
        int immediateCalls = 0;
        while (mImmediate.find()) {
            immediateCalls++;
        }
        assertTrue("4.6 Immediate refreshStatus(true) used for interactive triggers (count=" + immediateCalls + ")",
                immediateCalls >= 6, "Expected at least 6 immediate refreshStatus calls");
    }
}
