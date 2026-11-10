package app.nn.agent;

import android.content.Context;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs NN's helper script (assets/linux/nn-run) inside the built-in Linux. The helper streams
 * the agent's progress back to the app over the local server (POST /event), like before.
 */
final class Helper {
    private static final String[][] FILES = {
            // asset, path inside Linux, mode
            {"linux/nn-run", "/root/bin/nn-run", "700"},
            {"linux/codex-remote", "/root/bin/codex-remote", "700"}, // the Codex session (CodexRemote)
            {"linux/nn-browser", "/root/bin/nn-browser", "700"}, // the background browser (Browser)
            {"linux/profile.sh", "/etc/profile.d/nn.sh", "644"},
            {"linux/arecord", "/usr/local/bin/arecord", "755"},
            {"linux/asound-cards", "/etc/nn/asound-cards", "644"},
            {"linux/arecord", "/usr/local/bin/rec", "755"}, // SoX's recorder, same arguments that matter (-r, -c)
            {"linux/system-prompt.md", "/root/.nn/system-prompt.md", "600"},
            {"linux/GUIDE.md", "/root/.nn/GUIDE.md", "644"},
            {"linux/browser-prompt.md", "/root/.nn/browser-prompt.md", "600"},
            {"linux/AGENTS.md", "/root/.nn/work/AGENTS.md", "600"},
            {"linux/android-app/SKILL.md", "/root/.nn/work/.claude/skills/android-app/SKILL.md", "600"},
            {"linux/android-app/build-apk.sh", "/root/.nn/work/.claude/skills/android-app/build-apk.sh", "700"},
            {"linux/android-app/setup-toolchain.sh", "/root/.nn/work/.claude/skills/android-app/setup-toolchain.sh", "700"},
    };
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    private static int syncedFor = -1;

    private Helper() {
    }

    /** Whether chats can run: Linux is installed (each agent's own readiness is checked by Account). */
    static boolean ready(Context c) {
        return Linux.installed(c);
    }

    /** Runs `nn-run <token> args…` in the background. Throws with a readable message if Linux isn't set up. */
    static void run(Context c, String... args) {
        final Context app = c.getApplicationContext();
        if (!Linux.installed(app)) {
            throw new IllegalStateException(app.getString(R.string.helper_linux_missing)); // shown to the user
        }
        final String cmdName = args.length > 0 ? args[0] : "";
        final String sessionId = args.length > 1 ? args[1] : null;
        final String[] cmd = new String[args.length + 3];
        cmd[0] = "/bin/bash";
        cmd[1] = "/root/bin/nn-run";
        cmd[2] = Prefs.token(app);
        System.arraycopy(args, 0, cmd, 3, args.length);
        pool.execute(() -> {
            try {
                sync(app);
                writePermissions(app);
                Process p = Linux.builder(app, cmd).start();
                p.getOutputStream().close();
                // the helper reports through the local server; its own output is only kept for debugging
                File log = new File(app.getCacheDir(), "helper.log");
                try (InputStream in = p.getInputStream(); OutputStream out = new FileOutputStream(log, log.length() < 200_000)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                int code = p.waitFor();
                if ("ask".equals(cmdName) && sessionId != null) {
                    ChatHub.get(app).onHelperFinished(sessionId, code);
                }
            } catch (Exception e) {
                android.util.Log.w("NN Agent", "helper failed", e);
                if ("ask".equals(cmdName) && sessionId != null) {
                    ChatHub.get(app).onHelperFinished(sessionId, -1);
                }
            }
        });
    }

    /** The user's App permissions choices, which nn-run appends to the agent's instructions. */
    static void writePermissions(Context c) {
        // the background browser's instructions go along only while it's on (nn-run looks for this file)
        File on = new File(Linux.root(c), "root/.nn/browser-on");
        try {
            if (Browser.available(c)) on.createNewFile();
            else on.delete();
        } catch (IOException ignored) {
        }
        StringBuilder trusted = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : Prefs.appSafetyAll(c).entrySet()) {
            if ("trust".equals(e.getValue())) trusted.append("- ").append(appName(c, e.getKey())).append(" (").append(e.getKey()).append(")\n");
        }
        String text = "";
        if (trusted.length() > 0) text += "Các ứng dụng người dùng đặt mức Tin cậy (Trust):\n" + trusted;
        if (Prefs.autonomousDefault(c)) {
            text += (text.isEmpty() ? "" : "\n") + "Người dùng cũng tin cậy mọi ứng dụng chưa thiết lập riêng, ngoại trừ các ứng dụng nhắn tin, "
                    + "mạng xã hội, cuộc gọi, email, mua sắm, thanh toán và ngân hàng.\n";
        }
        StringBuilder never = new StringBuilder();
        for (Guard.Rule r : Guard.rules(c, "allow")) never.append("- “").append(r.label).append("” trong ")
                .append(r.pkg == null ? "mọi ứng dụng" : appName(c, r.pkg)).append("\n");
        if (never.length() > 0) {
            text += (text.isEmpty() ? "" : "\n") + "Các nút người dùng đánh dấu \"Không bao giờ hỏi\" (bấm trực tiếp không cần confirm "
                    + "khi tác vụ yêu cầu):\n" + never;
        }
        File f = new File(Linux.root(c), "root/.nn/permissions.md");
        try {
            if (text.isEmpty()) {
                f.delete();
            } else {
                try (OutputStream o = new FileOutputStream(f)) {
                    o.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            android.util.Log.w("NN Agent", "permissions not written", e);
        }
    }

    private static String appName(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** Copies the helper files again now (Check NN's fix when Linux still has an older copy). */
    static synchronized void resync(Context c) throws IOException {
        syncedFor = -1;
        sync(c);
    }

    /** Copies the helper files from the APK into Linux once per app version. */
    static synchronized void sync(Context c) throws IOException {
        if (syncedFor == BuildInfo.VERSION_CODE && new File(Linux.root(c), "root/bin/nn-run").length() > 500) return;
        File root = Linux.root(c);
        for (String[] f : FILES) {
            File out = new File(root, f[1].substring(1));
            out.getParentFile().mkdirs();
            try (InputStream in = c.getAssets().open(f[0]); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            }
            try {
                Os.chmod(out.getPath(), Integer.parseInt(f[2], 8));
            } catch (Exception ignored) {
            }
        }
        new File(root, "root/.nn/sessions").mkdirs();
        syncedFor = BuildInfo.VERSION_CODE;
    }
}
