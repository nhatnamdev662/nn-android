package app.nn.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Makes it obvious when an agent is operating the phone, so the user knows not to touch it:
 * a status-bar chip (an Android 16 "Live Update", like Google Maps navigation; a plain ongoing
 * notification on older Android) and the bubble in its "in control" state with a Stop button.
 * Shown from the first phone action until the task ends.
 *
 * No extra overlay windows on purpose: some phones' security features (e.g. OxygenOS "Block
 * suspicious app activities") treat a full-screen overlay from an accessibility app as an attack.
 */
final class ControlIndicator {
    static final String CHANNEL = "control", ACTION_STOP = "app.nn.agent.STOP_CONTROL";
    private static final int NOTIFICATION_ID = 7;
    /** Responsive timeout: 4s after screen interaction stops when no background chat is running. */
    static final long IDLE_MUTATION_MS = 4_000L;

    private final NnService svc;
    private boolean active;
    private long lastActivity;

    ControlIndicator(NnService svc) {
        this.svc = svc;
    }

    boolean active() {
        return active;
    }

    /** A phone tool was used within the last ms (main thread). */
    boolean recent(long ms) {
        return lastActivity > 0 && SystemClock.elapsedRealtime() - lastActivity < ms;
    }

    /** A phone tool is being used (main thread). */
    void onActivity() {
        lastActivity = SystemClock.elapsedRealtime();
        svc.mainHandler().removeCallbacks(tick);
        svc.mainHandler().postDelayed(tick, IDLE_MUTATION_MS);
        if (active) return;
        active = true;
        TouchEffectOverlay te = svc.touchEffect();
        if (te != null) te.startBorderGlow();
        Bubble b = svc.bubble();
        if (b != null) b.setControlling(true);
        KeepAliveService.updateState(svc, KeepAliveService.State.CONTROLLING, null);
        svc.autoPauser().start(); // the user taking the phone back pauses it
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            // While any agent chat session is still working on the task, keep control & border glow active!
            if (svc.hasRunningChats()) {
                svc.mainHandler().postDelayed(this, 1000L);
                return;
            }
            long idle = SystemClock.elapsedRealtime() - lastActivity;
            if (idle >= IDLE_MUTATION_MS) {
                end();
            } else {
                svc.mainHandler().postDelayed(this, Math.max(50L, IDLE_MUTATION_MS - idle));
            }
        }
    };

    void end() {
        if (!active) return;
        active = false;
        TouchEffectOverlay te = svc.touchEffect();
        if (te != null) te.stopBorderGlow();
        svc.mainHandler().removeCallbacks(tick);
        svc.autoPauser().stop();
        Bubble b = svc.bubble();
        if (b != null) b.setControlling(false);
        try {
            svc.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {
        }
        KeepAliveService.updateState(svc, svc.hasRunningChats() ? KeepAliveService.State.THINKING : KeepAliveService.State.IDLE, null);
    }

    // ------------------------------------------------------- notification

    /** The channel for this and other notices about a task (see KeepAwake): quiet, no sound or vibration. */
    static void channel(android.content.Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.control_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(c.getString(R.string.control_channel_description));
        ch.setSound(null, null);
        ch.enableVibration(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private void notifyChip() {
        KeepAliveService.updateState(svc, KeepAliveService.State.CONTROLLING, null);
        try {
            svc.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {
        }
    }
}
