package app.nn.agent;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The unified notification action receiver for approvals, bubble hidden, and control actions. */
public final class HiddenNotice extends BroadcastReceiver {
    public static final String ACTION_APPROVE = "app.nn.agent.ACTION_APPROVE";
    public static final String ACTION_DENY = "app.nn.agent.ACTION_DENY";
    public static final String ACTION_SHOW = "app.nn.agent.SHOW_BUBBLE";
    public static final int ID = 1;

    static void show(Context c) {
        KeepAliveService.updateState(c, KeepAliveService.State.BUBBLE_HIDDEN, null);
        cancel(c);
    }

    static void cancel(Context c) {
        try {
            c.getSystemService(NotificationManager.class).cancel(ID);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        if (ACTION_APPROVE.equals(action)) {
            NnService s = NnService.get();
            if (s != null && s.bubble() != null) {
                s.bubble().answerFromApp(true);
            }
            return;
        }

        if (ACTION_DENY.equals(action)) {
            NnService s = NnService.get();
            if (s != null && s.bubble() != null) {
                s.bubble().answerFromApp(false);
            }
            return;
        }

        if (ACTION_SHOW.equals(action)) {
            Prefs.setBubbleEnabled(c, true);
            cancel(c);
            NnService s = NnService.get();
            if (s != null) {
                if (s.bubble() != null) s.bubble().restoreBubble();
                s.applyBubbleSetting();
                KeepAliveService.updateState(s, KeepAliveService.State.IDLE, null);
            }
            return;
        }

        if (ControlIndicator.ACTION_STOP.equals(action)) {
            NnService s = NnService.get();
            if (s != null) s.takeBackControl();
            return;
        }

        if (CodexLive.ACTION_END.equals(action)) { // the live voice notification's End
            CodexLive.end(null);
            return;
        }

        if (AutoPause.ACTION_RESUME.equals(action)) { // "Call ended": resume the paused task
            NnService s = NnService.get();
            if (s != null) s.resume();
            AutoPause.cancelCallEnded(c); // also when it had ended already
            return;
        }
    }
}
