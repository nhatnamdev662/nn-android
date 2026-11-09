package app.nn.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Manages foreground notifications and Quick-Input (RemoteInput) for Wireless Debugging pairing.
 * Allows entering the 6-digit pairing code directly from the notification shade without leaving
 * the Developer Options screen.
 */
public final class AdbPairingService {
    private static final String TAG = "AdbPairingService";

    public static final String CHANNEL_ID = "adb_pairing";
    public static final int NOTIFICATION_ID = 8769;

    public static final String ACTION_PAIR_CODE = "app.nn.agent.ADB_PAIR_CODE";
    public static final String EXTRA_PORT = "port";
    public static final String KEY_TEXT_REPLY = "adb_code";

    private AdbPairingService() {}

    public static void showNotification(Context context, int detectedPort) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        createChannel(context, nm);

        // RemoteInput action for entering 6-digit pairing code
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(context.getString(R.string.adb_notify_code_hint))
                .build();

        Intent pairIntent = new Intent(context, PairingReceiver.class)
                .setAction(ACTION_PAIR_CODE)
                .putExtra(EXTRA_PORT, detectedPort);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pairPi = PendingIntent.getBroadcast(context, 0, pairIntent, flags);

        Notification.Action pairAction = new Notification.Action.Builder(
                R.drawable.ms_check,
                context.getString(R.string.adb_pair_action),
                pairPi
        ).addRemoteInput(remoteInput).build();

        // Developer Options shortcut intent
        Intent devIntent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent devPi = PendingIntent.getActivity(
                context,
                1,
                devIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nb = new Notification.Builder(context, CHANNEL_ID);
        } else {
            nb = new Notification.Builder(context);
        }

        nb.setSmallIcon(R.drawable.ic_launcher_monochrome)
          .setContentTitle(context.getString(R.string.adb_notify_pair_title))
          .setContentText(context.getString(R.string.adb_notify_pair_text))
          .setContentIntent(devPi)
          .addAction(pairAction)
          .addAction(new Notification.Action.Builder(
                  null,
                  context.getString(R.string.setup_adb_open_dev_options),
                  devPi
          ).build())
          .setAutoCancel(true);

        nm.notify(NOTIFICATION_ID, nb.build());
    }

    public static void cancelNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
    }

    private static void createChannel(Context context, NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.adb_notify_channel),
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription(context.getString(R.string.setup_adb_section_info));
            nm.createNotificationChannel(ch);
        }
    }

    /**
     * BroadcastReceiver to handle RemoteInput submission from notification shade.
     */
    public static class PairingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_PAIR_CODE.equals(intent.getAction())) {
                return;
            }

            Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            int port = intent.getIntExtra(EXTRA_PORT, AdbManager.detectedPairingPort);

            if (remoteInput != null) {
                CharSequence codeSeq = remoteInput.getCharSequence(KEY_TEXT_REPLY);
                if (codeSeq != null) {
                    String code = codeSeq.toString().trim();
                    if (code.length() == 6 && port > 0) {
                        AdbManager.pair(port, code, (success, error) -> {
                            if (success) {
                                cancelNotification(context);
                                Toast.makeText(context, R.string.adb_notify_pair_success, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(context, context.getString(R.string.adb_pair_failed, error), Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        Toast.makeText(context, R.string.adb_pair_invalid_code, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }
}
