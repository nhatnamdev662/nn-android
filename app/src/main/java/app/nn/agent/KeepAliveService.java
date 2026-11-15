package app.nn.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Lightweight foreground service to keep NN Agent from being killed by MIUI/HyperOS or Android memory cleaner.
 * Also hosts the unified ongoing system notification and state machine.
 */
public class KeepAliveService extends Service {
    public static final String CHANNEL_SILENT = "nn_keepalive";
    public static final String CHANNEL_APPROVAL = "nn_approval";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_UPDATE_STATE = "app.nn.agent.ACTION_UPDATE_STATE";
    public static final String EXTRA_STATE = "extra_state";
    public static final String EXTRA_DETAIL = "extra_detail";

    public enum State {
        IDLE,
        THINKING,
        CODING,
        CONTROLLING,
        WAITING_APPROVAL,
        BUBBLE_HIDDEN
    }

    private static volatile KeepAliveService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private State currentState = State.IDLE;
    private String currentDetail = null;

    public static void start(Context c) {
        try {
            Intent intent = new Intent(c, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.startForegroundService(intent);
            } else {
                c.startService(intent);
            }
        } catch (Throwable t) {
            android.util.Log.w("KeepAlive", "Failed to start KeepAliveService", t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, KeepAliveService.class));
        } catch (Throwable ignored) {
        }
    }

    public static State getState() {
        return instance != null ? instance.currentState : State.IDLE;
    }

    public static void updateState(Context c, State state, String detail) {
        if (state == null) return;
        KeepAliveService svc = instance;
        if (svc != null) {
            svc.applyState(state, detail);
        } else if (c != null) {
            try {
                Intent intent = new Intent(c, KeepAliveService.class);
                intent.setAction(ACTION_UPDATE_STATE);
                intent.putExtra(EXTRA_STATE, state.name());
                if (detail != null) intent.putExtra(EXTRA_DETAIL, detail);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    c.startForegroundService(intent);
                } else {
                    c.startService(intent);
                }
            } catch (Throwable t) {
                android.util.Log.w("KeepAlive", "Failed to update KeepAliveService state", t);
            }
        }
    }

    public void applyState(State state, String detail) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> applyState(state, detail));
            return;
        }
        this.currentState = state;
        this.currentDetail = detail;
        Notification n = buildNotification(state, detail);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            try {
                nm.notify(NOTIFICATION_ID, n);
            } catch (SecurityException ignored) {
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        cancelLegacyNotifications();
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        instance = this;
        cancelLegacyNotifications();
        createNotificationChannels();

        if (intent != null && ACTION_UPDATE_STATE.equals(intent.getAction())) {
            String stateName = intent.getStringExtra(EXTRA_STATE);
            if (stateName != null) {
                try {
                    currentState = State.valueOf(stateName);
                } catch (IllegalArgumentException ignored) {
                }
            }
            currentDetail = intent.getStringExtra(EXTRA_DETAIL);
        }

        Notification n = buildNotification(currentState, currentDetail);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Throwable t) {
            android.util.Log.w("KeepAlive", "Failed to startForeground", t);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void cancelLegacyNotifications() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            try {
                nm.cancel(1); // Legacy HiddenNotice ID
                nm.cancel(7); // Legacy ControlIndicator ID
            } catch (Throwable ignored) {
            }
        }
    }

    private void createNotificationChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // CHANNEL_SILENT: IMPORTANCE_LOW, silent, no heads-up, ongoing
        NotificationChannel chSilent = new NotificationChannel(
                CHANNEL_SILENT,
                getString(R.string.keepalive_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        chSilent.setDescription(getString(R.string.keepalive_channel_desc));
        chSilent.setShowBadge(false);
        chSilent.setSound(null, null);
        chSilent.enableVibration(false);
        nm.createNotificationChannel(chSilent);

        // CHANNEL_APPROVAL: IMPORTANCE_HIGH, heads-up banner, gentle vibration, lockscreen public
        NotificationChannel chApproval = new NotificationChannel(
                CHANNEL_APPROVAL,
                getString(R.string.approval_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        chApproval.setDescription(getString(R.string.approval_channel_desc));
        chApproval.enableVibration(true);
        chApproval.setVibrationPattern(new long[]{0, 80});
        chApproval.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(chApproval);
    }

    private Notification buildNotification(State state, String detail) {
        PendingIntent openApp = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String channel = (state == State.WAITING_APPROVAL) ? CHANNEL_APPROVAL : CHANNEL_SILENT;
        Notification.Builder b = new Notification.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        switch (state) {
            case WAITING_APPROVAL: {
                b.setContentTitle(getString(R.string.notification_approval_title))
                 .setContentText(detail != null && !detail.isEmpty() ? detail : getString(R.string.card_label_approval))
                 .setContentIntent(openApp)
                 .setVisibility(Notification.VISIBILITY_PUBLIC)
                 .setPriority(Notification.PRIORITY_HIGH)
                 .setVibrate(new long[]{0, 80});

                PendingIntent approve = PendingIntent.getBroadcast(this, 101,
                        new Intent(this, HiddenNotice.class).setAction(HiddenNotice.ACTION_APPROVE),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent deny = PendingIntent.getBroadcast(this, 102,
                        new Intent(this, HiddenNotice.class).setAction(HiddenNotice.ACTION_DENY),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

                b.addAction(new Notification.Action.Builder(null, getString(R.string.card_approve), approve).build());
                b.addAction(new Notification.Action.Builder(null, getString(R.string.card_deny), deny).build());
                break;
            }

            case BUBBLE_HIDDEN: {
                PendingIntent restoreBubble = PendingIntent.getBroadcast(this, 103,
                        new Intent(this, HiddenNotice.class).setAction(HiddenNotice.ACTION_SHOW),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

                b.setContentTitle(getString(R.string.app_name))
                 .setContentText(getString(R.string.notice_hidden_title))
                 .setContentIntent(restoreBubble)
                 .addAction(new Notification.Action.Builder(null, getString(R.string.notice_restore_bubble), restoreBubble).build());
                break;
            }

            case CONTROLLING: {
                PendingIntent stop = PendingIntent.getBroadcast(this, 104,
                        new Intent(this, HiddenNotice.class).setAction(ControlIndicator.ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

                Bundle extras = new Bundle();
                extras.putBoolean("android.requestPromotedOngoing", true);

                b.setContentTitle(getString(R.string.control_title))
                 .setContentText(getString(R.string.control_text))
                 .setCategory(Notification.CATEGORY_PROGRESS)
                 .setOnlyAlertOnce(true)
                 .setContentIntent(openApp)
                 .addExtras(extras)
                 .addAction(new Notification.Action.Builder(null, getString(R.string.control_stop), stop).build());

                if (Build.VERSION.SDK_INT >= 36) {
                    b.setShortCriticalText("NN");
                }
                break;
            }

            case CODING: {
                b.setContentTitle(getString(R.string.keepalive_title))
                 .setContentText(detail != null && !detail.isEmpty() ? detail : getString(R.string.status_coding))
                 .setContentIntent(openApp);
                break;
            }

            case THINKING: {
                b.setContentTitle(getString(R.string.keepalive_title))
                 .setContentText(detail != null && !detail.isEmpty() ? detail : getString(R.string.hub_thinking))
                 .setContentIntent(openApp);
                break;
            }

            case IDLE:
            default: {
                b.setContentTitle(getString(R.string.keepalive_title))
                 .setContentText(detail != null && !detail.isEmpty() ? detail : getString(R.string.keepalive_text))
                 .setContentIntent(openApp);
                break;
            }
        }

        return b.build();
    }
}
