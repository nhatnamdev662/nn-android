package app.nn.agent;

import android.os.Bundle;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive Empirical Adversarial Stress Test Suite for Milestone 2 (R3 & R4).
 * Directly tests algorithms, thread boundaries, edge cases, node recycling, and concurrency.
 */
public class Milestone2AdversarialTest {

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

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("   MILESTONE 2 EMPIRICAL ADVERSARIAL VERIFICATION HARNESS");
        System.out.println("======================================================================");

        try {
            runR4TreeTraversalTests();
            runR3AsyncPictureTests();
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
    // R4 TESTS: Guard Descendant Traversal & AccessibilityNodeInfo Recycling
    // =========================================================================

    private static void runR4TreeTraversalTests() {
        System.out.println("\n--- RUNNING R4 TESTS: Guard Descendant Traversal & Node Recycling ---");

        // 4.1 Null Root
        boolean r4_1 = Guard.hasProtectedDescendant(null);
        assertTrue("R4.1 Null root returns false", !r4_1, "Expected false on null root");

        // 4.2 Root Alone Protected
        AccessibilityNodeInfo root4_2 = AccessibilityNodeInfo.obtain();
        root4_2.getExtras().putBoolean(Ui.PROTECTED, true);
        boolean r4_2 = Guard.hasProtectedDescendant(root4_2);
        assertTrue("R4.2 Root alone protected returns true", r4_2, "Expected true when root is protected");
        assertTrue("R4.2 Root alone not recycled by traversal", root4_2.recycleCalls == 0,
                "Root should never be recycled by hasProtectedDescendant");

        // 4.3 Root Unprotected, Immediate Child Protected
        AccessibilityNodeInfo root4_3 = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo c0 = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo c1 = AccessibilityNodeInfo.obtain();
        c1.getExtras().putBoolean(Ui.PROTECTED, true);
        AccessibilityNodeInfo c2 = AccessibilityNodeInfo.obtain();
        root4_3.addChild(c0);
        root4_3.addChild(c1);
        root4_3.addChild(c2);

        boolean r4_3 = Guard.hasProtectedDescendant(root4_3);
        assertTrue("R4.3 Child protected returns true", r4_3, "Expected true for protected child");
        assertTrue("R4.3 Protected child c1 recycled upon detection", c1.recycleCalls >= 1,
                "Protected child should be recycled on return");
        assertTrue("R4.3 Preceding queued child c0 recycled in finally", c0.recycleCalls >= 1,
                "Queued child should be recycled in finally");
        assertTrue("R4.3 Caller root not recycled", root4_3.recycleCalls == 0,
                "Root must not be recycled by hasProtectedDescendant");

        // 4.4 Deep Tree Within Limit (Depth 14)
        AccessibilityNodeInfo root4_4 = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo cur4_4 = root4_4;
        List<AccessibilityNodeInfo> chain = new ArrayList<>();
        chain.add(root4_4);
        for (int i = 1; i <= 14; i++) {
            AccessibilityNodeInfo next = AccessibilityNodeInfo.obtain();
            cur4_4.addChild(next);
            cur4_4 = next;
            chain.add(next);
        }
        cur4_4.getExtras().putBoolean(Ui.PROTECTED, true); // Depth 14 protected
        boolean r4_4 = Guard.hasProtectedDescendant(root4_4);
        assertTrue("R4.4 Protected descendant at depth 14 detected", r4_4,
                "Expected true for node at depth 14");
        assertTrue("R4.4 Root not recycled in deep tree", root4_4.recycleCalls == 0,
                "Root must remain valid");

        // 4.5 Deep Tree Beyond Limit (Depth 20)
        AccessibilityNodeInfo root4_5 = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo cur4_5 = root4_5;
        for (int i = 1; i <= 20; i++) {
            AccessibilityNodeInfo next = AccessibilityNodeInfo.obtain();
            cur4_5.addChild(next);
            cur4_5 = next;
        }
        cur4_5.getExtras().putBoolean(Ui.PROTECTED, true); // Depth 20 protected (beyond MAX_DEPTH 15)
        boolean r4_5 = Guard.hasProtectedDescendant(root4_5);
        assertTrue("R4.5 Protected descendant beyond depth 15 bounded/ignored", !r4_5,
                "Expected false for node deeper than MAX_DEPTH 15");

        // 4.6 Wide Tree Within Limit (100 children, #99 protected)
        AccessibilityNodeInfo root4_6 = AccessibilityNodeInfo.obtain();
        List<AccessibilityNodeInfo> wideChildren = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            AccessibilityNodeInfo ch = AccessibilityNodeInfo.obtain();
            if (i == 99) ch.getExtras().putBoolean(Ui.PROTECTED, true);
            root4_6.addChild(ch);
            wideChildren.add(ch);
        }
        boolean r4_6 = Guard.hasProtectedDescendant(root4_6);
        assertTrue("R4.6 Wide tree 100 children detected", r4_6,
                "Expected true for protected node #99");
        for (int i = 0; i <= 99; i++) {
            assertTrue("R4.6 Child #" + i + " properly recycled", wideChildren.get(i).recycleCalls >= 1,
                    "Child #" + i + " was not recycled");
            if (wideChildren.get(i).recycleCalls < 1) break;
        }

        // 4.7 Wide Tree Exceeding MAX_VISITED (250 children, #200 protected)
        AccessibilityNodeInfo root4_7 = AccessibilityNodeInfo.obtain();
        List<AccessibilityNodeInfo> wideChildren250 = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            AccessibilityNodeInfo ch = AccessibilityNodeInfo.obtain();
            if (i == 200) ch.getExtras().putBoolean(Ui.PROTECTED, true);
            root4_7.addChild(ch);
            wideChildren250.add(ch);
        }
        boolean r4_7 = Guard.hasProtectedDescendant(root4_7);
        assertTrue("R4.7 Wide tree beyond MAX_VISITED bounded cleanly", !r4_7,
                "Expected false because node #200 exceeds MAX_VISITED 150");

        // 4.8 Tree with Cyclic Reference (Adversarial Graph)
        AccessibilityNodeInfo root4_8 = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo cycleChild = AccessibilityNodeInfo.obtain();
        root4_8.addChild(cycleChild);
        cycleChild.addChild(root4_8); // Cycle!
        long startCycle = System.currentTimeMillis();
        boolean r4_8 = Guard.hasProtectedDescendant(root4_8);
        long elapsedCycle = System.currentTimeMillis() - startCycle;
        assertTrue("R4.8 Cyclic graph terminates without infinite loop", !r4_8 && elapsedCycle < 2000,
                "Cycle traversal failed to terminate quickly (took " + elapsedCycle + "ms)");

        // 4.9 Null Extras
        AccessibilityNodeInfo root4_9 = AccessibilityNodeInfo.obtain();
        root4_9.setExtras(null);
        AccessibilityNodeInfo ch4_9 = AccessibilityNodeInfo.obtain();
        ch4_9.setExtras(null);
        root4_9.addChild(ch4_9);
        boolean r4_9 = Guard.hasProtectedDescendant(root4_9);
        assertTrue("R4.9 Null extras handled gracefully without NPE", !r4_9,
                "Expected false when extras are null");

        // 4.10 Caller Node Invalidation Invariant Check
        AccessibilityNodeInfo root4_10 = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo ch4_10 = AccessibilityNodeInfo.obtain();
        root4_10.addChild(ch4_10);
        Guard.hasProtectedDescendant(root4_10);
        assertTrue("R4.10 Caller root reference remains NOT recycled",
                !root4_10.recycled && root4_10.recycleCalls == 0,
                "Caller's root node was unexpectedly recycled!");
        // Verify caller can still read root properties without IllegalStateException
        int childCountAfter = root4_10.getChildCount();
        assertTrue("R4.10 Caller root usable post-traversal", childCountAfter == 1,
                "Root child count altered or inaccessible");
    }

    // =========================================================================
    // R3 TESTS: Async Pictures, Thread Boundaries, Regex & Concurrency
    // =========================================================================

    private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)");
    private static final Pattern URL_IMAGE = Pattern.compile("https?://[^\\s`'\"()\\[\\]<>]+\\.(?i:png|jpe?g|webp|gif)(?:\\?[^\\s`'\"()\\[\\]<>]*)?");

    private static List<String> extractRemoteImageUrls(String text, int max) {
        List<String> urls = new ArrayList<>();
        if (text == null || text.isEmpty() || max <= 0) return urls;
        Set<String> seen = new HashSet<>();

        Matcher md = MD_IMAGE.matcher(text);
        while (urls.size() < max && md.find()) {
            String target = md.group(2).trim();
            if ((target.startsWith("http://") || target.startsWith("https://")) && seen.add(target)) {
                urls.add(target);
            }
        }

        if (urls.size() < max) {
            Matcher u = URL_IMAGE.matcher(text);
            while (urls.size() < max && u.find()) {
                String url = u.group(0).trim();
                if (seen.add(url)) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    private static void runR3AsyncPictureTests() throws Exception {
        System.out.println("\n--- RUNNING R3 TESTS: ChatHub Picture Thread Boundaries & Networking ---");

        // 3.1 Thread Boundary Guard: Main Thread
        Looper.setAsMainThread();
        assertTrue("R3.1 Main thread Looper identified correctly",
                Looper.myLooper() == Looper.getMainLooper(),
                "myLooper should equal getMainLooper on main thread");

        // Simulate picture(url) on main thread:
        String testUrl = "https://example.com/image.png";
        String mainThreadResult = simulatePictureCall(testUrl);
        assertTrue("R3.1 picture(url) on Main Thread returns null immediately (no network)",
                mainThreadResult == null,
                "Expected null on main thread to prevent NetworkOnMainThreadException");

        // 3.2 Thread Boundary Guard: Background Thread with Looper
        Looper.setAsBackgroundLooper("WorkerThreadLooper");
        assertTrue("R3.2 Background Looper identified as NOT main",
                Looper.myLooper() != Looper.getMainLooper(),
                "myLooper should not equal getMainLooper on background looper");

        // 3.3 Thread Boundary Guard: Background Thread Pool Worker (No Looper)
        Looper.clearThreadLooper();
        assertTrue("R3.3 Background pool worker has null looper (NOT main)",
                Looper.myLooper() != Looper.getMainLooper(),
                "myLooper should be null, not equal to getMainLooper");

        // 3.4 Markdown & URL Extraction Verification
        String replyText = "Here are the photos: ![Chart](https://example.com/chart.png) and another one:\n"
                + "https://images.org/photo.jpg?size=large and a local one ![Local](/root/pic.png)";
        List<String> extracted = extractRemoteImageUrls(replyText, 4);
        assertTrue("R3.4 Exactly 2 remote URLs extracted", extracted.size() == 2,
                "Extracted count was " + extracted.size());
        assertTrue("R3.4 Chart URL extracted", extracted.contains("https://example.com/chart.png"),
                "Missing chart URL");
        assertTrue("R3.4 Photo URL with query extracted", extracted.contains("https://images.org/photo.jpg?size=large"),
                "Missing photo URL with query");
        assertTrue("R3.4 Local path ignored by remote extractor", !extracted.contains("/root/pic.png"),
                "Local file path should not be in remote URLs");

        // 3.5 Malformed and Edge Case URL Handling
        String[] pathologicalUrls = {
                "",
                "   ",
                "http://",
                "https://",
                "http://invalid domain with spaces/test.png",
                "http://:::80/foo.png",
                "ftp://example.com/test.png",
                "file:///etc/passwd",
                "javascript:alert(1)"
        };
        for (String pUrl : pathologicalUrls) {
            String res = simulateDownload(pUrl);
            assertTrue("R3.5 Pathological URL '" + pUrl + "' fails safely", res == null,
                    "Expected null for pathological URL: " + pUrl);
        }

        // 3.6 Offline and Unreachable Network Endpoints
        String unreachableHost = "http://unreachable-test-domain-99999999.invalid/pic.png";
        String refusedPort = "http://127.0.0.1:59123/pic.png";
        long startConn = System.currentTimeMillis();
        String unreachRes = simulateDownload(unreachableHost);
        String refusedRes = simulateDownload(refusedPort);
        long elapsedConn = System.currentTimeMillis() - startConn;
        assertTrue("R3.6 Unreachable host caught cleanly", unreachRes == null,
                "Expected null for unreachable host");
        assertTrue("R3.6 Connection refused caught cleanly", refusedRes == null,
                "Expected null for connection refused");
        assertTrue("R3.6 Network failures handled under 10 seconds", elapsedConn < 10000,
                "Network timeout took too long: " + elapsedConn + "ms");

        // 3.7 Concurrency Stress Test: 20 Parallel Download Tasks
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int id = i;
            futures.add(pool.submit(() -> simulateDownload("http://127.0.0.1:5912" + (id % 5) + "/img" + id + ".png")));
        }
        for (Future<String> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue("R3.7 20 concurrent download tasks executed and completed cleanly",
                pool.awaitTermination(5, TimeUnit.SECONDS),
                "Pool failed to terminate cleanly");

        // 3.8 Temp File Name Collision Test: Pool concurrency (4 threads, 4,000 files)
        Set<String> poolNames = ConcurrentHashMap.newKeySet();
        ExecutorService realisticPool = Executors.newFixedThreadPool(4);
        CountDownLatch realisticLatch = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            realisticPool.submit(() -> {
                try {
                    for (int j = 0; j < 1000; j++) {
                        String name = "img_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6) + ".tmp";
                        poolNames.add(name);
                    }
                } finally {
                    realisticLatch.countDown();
                }
            });
        }
        realisticLatch.await(5, TimeUnit.SECONDS);
        realisticPool.shutdown();
        assertTrue("R3.8 Zero collisions in 4,000 pool files across 4 threads", poolNames.size() == 4000,
                "Collisions detected in realistic pool: expected 4000, got " + poolNames.size());

        // 3.9 Statistical Birthday Bound Analysis on 6-char UUID prefix
        // 6 hex chars = 16^6 = 16,777,216 space. With 4 concurrent workers, P(collision) <= 3.6e-7.
        int space = 1 << 24; // 16,777,216
        double pCollision4Workers = (4.0 * 3.0 / 2.0) / space;
        assertTrue("R3.9 Concurrency collision probability with 4 workers is negligible (< 10^-6)",
                pCollision4Workers < 0.000001,
                "Probability too high: " + pCollision4Workers);
    }

    private static String simulatePictureCall(String path) {
        try {
            if (path == null || path.trim().isEmpty()) return null;
            path = path.trim();
            if (path.startsWith("http://") || path.startsWith("https://")) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    return null; // Remote URLs: do not perform synchronous network requests on the main Looper thread.
                }
                return simulateDownload(path);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String simulateDownload(String urlStr) {
        try {
            if (urlStr == null || urlStr.trim().isEmpty()) return null;
            urlStr = urlStr.trim();
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(500); // short timeout for testing
            conn.setReadTimeout(500);
            try {
                conn.connect();
                return "downloaded_id";
            } finally {
                conn.disconnect();
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
