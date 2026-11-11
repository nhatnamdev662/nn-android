package app.nn.agent;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import static app.nn.agent.Ui.*;

/**
 * Shared UI helpers and pairing/connect bottom-sheets for Wireless Debugging (ADB),
 * reused by both {@link SetupActivity} and {@link OnboardingActivity}.
 */
final class AdbUi {

    static final class DialogSession {
        Dialog guideDialog;
        Dialog pairDialog;

        void dismissAll() {
            if (guideDialog != null && guideDialog.isShowing()) {
                try { guideDialog.dismiss(); } catch (Throwable ignored) {}
                guideDialog = null;
            }
            if (pairDialog != null && pairDialog.isShowing()) {
                try { pairDialog.dismiss(); } catch (Throwable ignored) {}
                pairDialog = null;
            }
        }
    }

    private AdbUi() {
    }

    static boolean isOptionalYellow(Context c) {
        return !AdbManager.isConnected()
                && !Prefs.adbEnabled(c)
                && (Prefs.adbSkipped(c) || Prefs.adbPort(c) <= 0);
    }

    static LinearLayout statusBanner(Context c) {
        LinearLayout sc = column(c);
        sc.setBackground(rounded(c, CARD2, 12));
        sc.setPaddingRelative(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));

        LinearLayout sr = row(c);
        sr.setGravity(Gravity.CENTER_VERTICAL);

        AdbManager.State as = AdbManager.state;
        boolean isConn = AdbManager.isConnected();
        boolean inProgress = as == AdbManager.State.CONNECTING
                || as == AdbManager.State.PAIRING
                || as == AdbManager.State.PAIRED;
        boolean optional = isOptionalYellow(c);

        int dotColor = isConn ? OK : inProgress ? ACCENT : optional ? WARN : ERR;
        sr.addView(dot(c, dotColor, 10));

        LinearLayout stCol = column(c);
        stCol.setPaddingRelative(dp(c, 12), 0, 0, 0);

        String statusTitle;
        int titleColor;
        if (isConn) {
            statusTitle = c.getString(R.string.setup_adb_status_active);
            titleColor = OK;
        } else if (as == AdbManager.State.CONNECTING || as == AdbManager.State.PAIRED) {
            statusTitle = c.getString(R.string.setup_adb_status_connecting);
            titleColor = TEXT;
        } else if (as == AdbManager.State.PAIRING) {
            statusTitle = c.getString(R.string.setup_adb_status_pairing);
            titleColor = TEXT;
        } else if (optional) {
            statusTitle = c.getString(R.string.setup_adb_status_optional);
            titleColor = WARN;
        } else {
            statusTitle = c.getString(R.string.setup_adb_status_inactive);
            titleColor = TEXT;
        }

        TextView stTitle = title(c, statusTitle, 15);
        stTitle.setTextColor(titleColor);
        stCol.addView(stTitle);

        String statusSub;
        if (isConn) {
            statusSub = c.getString(R.string.setup_adb_status_sub_connected, AdbManager.port);
        } else if (as == AdbManager.State.CONNECTING || as == AdbManager.State.PAIRED) {
            statusSub = AdbManager.port > 0
                    ? c.getString(R.string.setup_adb_status_connecting_port, AdbManager.port)
                    : c.getString(R.string.setup_adb_status_connecting);
        } else if (optional) {
            statusSub = c.getString(R.string.setup_adb_status_sub_optional);
        } else if (!AdbManager.detail.isEmpty()) {
            statusSub = AdbManager.detail;
        } else {
            statusSub = c.getString(R.string.setup_adb_status_sub_disconnected);
        }

        TextView stSub = monoText(c, statusSub, 12, MUTED);
        stSub.setPaddingRelative(0, dp(c, 3), 0, 0);
        stCol.addView(stSub);

        sr.addView(stCol, weight1());
        sc.addView(sr, full());
        return sc;
    }

    static void openDeveloperOptions(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Throwable t) {
            try {
                a.startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
            } catch (Throwable ignored) {
            }
            Toast.makeText(a, R.string.setup_adb_open_dev_options, Toast.LENGTH_SHORT).show();
        }
    }

    static void showGuideDialog(Activity a, DialogSession sess, Runnable onRefresh) {
        if (!AdbManager.isWifiAvailable(a)) {
            Toast.makeText(a, R.string.setup_adb_status_wifi_required, Toast.LENGTH_LONG).show();
            return;
        }
        AdbManager.startDiscovery(a);
        AdbPairingService.showSearchingNotification(a);

        LinearLayout c = column(a);
        TextView h = title(a, a.getString(R.string.adb_guide_sheet_title), 18);
        h.setPaddingRelative(0, 0, 0, dp(a, 10));
        c.addView(h);

        ScrollView sv = new ScrollView(a);
        LinearLayout content = column(a);
        content.setPaddingRelative(0, dp(a, 4), 0, dp(a, 8));

        // 1. Top Searching Card
        LinearLayout searchCard = column(a);
        searchCard.setBackground(rounded(a, CARD2, 14));
        searchCard.setPaddingRelative(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14));

        TextView searchTitle = title(a, a.getString(R.string.adb_guide_searching_title), 15);
        searchCard.addView(searchTitle);

        TextView searchAction = text(a, a.getString(R.string.adb_guide_stop_search), 13, ACCENT_TEXT);
        searchAction.setTypeface(sans(a, 600));
        searchAction.setPaddingRelative(0, dp(a, 6), 0, 0);
        final boolean[] isSearching = {true};
        searchAction.setOnClickListener(v -> {
            if (isSearching[0]) {
                isSearching[0] = false;
                AdbPairingService.cancelNotification(a);
                searchTitle.setText(a.getString(R.string.adb_notify_pair_failed));
                searchAction.setText(a.getString(R.string.adb_guide_start_search));
            } else {
                isSearching[0] = true;
                AdbManager.startDiscovery(a);
                AdbPairingService.showSearchingNotification(a);
                searchTitle.setText(a.getString(R.string.adb_guide_searching_title));
                searchAction.setText(a.getString(R.string.adb_guide_stop_search));
            }
        });
        searchCard.addView(searchAction);
        content.addView(searchCard, full());

        // 2. Notification Prompt Card
        LinearLayout notifyCard = row(a);
        notifyCard.setBackground(rounded(a, Theme.withAlpha(0xFF2196F3, 0x1A), 14));
        notifyCard.setPaddingRelative(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        notifyCard.setGravity(Gravity.CENTER_VERTICAL);

        ImageView bellIcon = icon(a, R.drawable.ms_notifications, 0xFF64B5F6, 22);
        notifyCard.addView(bellIcon);

        LinearLayout notifyCol = column(a);
        notifyCol.setPaddingRelative(dp(a, 12), 0, 0, 0);
        notifyCol.addView(text(a, a.getString(R.string.adb_guide_notify_banner), 13, TEXT));

        NotificationManager nm = a.getSystemService(NotificationManager.class);
        boolean notifyAllowed = nm != null && nm.areNotificationsEnabled();
        if (!notifyAllowed) {
            Button notifyBtn = button(a, a.getString(R.string.adb_guide_notify_btn), false);
            notifyBtn.setOnClickListener(v -> {
                try {
                    a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
                } catch (Throwable ignored) {}
            });
            LinearLayout.LayoutParams np = wrap();
            np.topMargin = dp(a, 6);
            notifyCol.addView(notifyBtn, np);
        }
        notifyCard.addView(notifyCol, weight1());
        content.addView(notifyCard, topMarginParams(a, 10));

        // 3. Local Network / MIUI note
        LinearLayout netCard = row(a);
        netCard.setBackground(rounded(a, CARD2, 14));
        netCard.setPaddingRelative(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        netCard.addView(icon(a, R.drawable.ms_description, MUTED, 20));
        LinearLayout netCol = column(a);
        netCol.setPaddingRelative(dp(a, 12), 0, 0, 0);
        netCol.addView(text(a, a.getString(R.string.adb_guide_network_note), 13, MUTED));
        netCard.addView(netCol, weight1());
        content.addView(netCard, topMarginParams(a, 10));

        // Step ①
        LinearLayout step1Row = row(a);
        step1Row.setPaddingRelative(0, dp(a, 12), 0, dp(a, 8));
        TextView b1 = text(a, "①", 18, TEXT);
        b1.setTypeface(sans(a, 700));
        step1Row.addView(b1);

        LinearLayout col1 = column(a);
        col1.setPaddingRelative(dp(a, 12), 0, 0, 0);
        col1.addView(text(a, a.getString(R.string.adb_guide_step1), 14, TEXT));
        TextView note1 = text(a, a.getString(R.string.adb_guide_step1_note), 13, 0xFFE57373);
        note1.setPaddingRelative(0, dp(a, 6), 0, dp(a, 6));
        col1.addView(note1);

        Button devBtn = button(a, a.getString(R.string.adb_guide_open_dev), false);
        devBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ms_open_in_full, 0, 0, 0);
        devBtn.setCompoundDrawablePadding(dp(a, 8));
        devBtn.setOnClickListener(v -> openDeveloperOptions(a));
        LinearLayout.LayoutParams dpParams = wrap();
        dpParams.topMargin = dp(a, 6);
        col1.addView(devBtn, dpParams);

        step1Row.addView(col1, weight1());
        content.addView(step1Row, topMarginParams(a, 6));

        // Step ②
        LinearLayout step2Row = row(a);
        step2Row.setPaddingRelative(0, dp(a, 12), 0, dp(a, 8));
        TextView b2 = text(a, "②", 18, TEXT);
        b2.setTypeface(sans(a, 700));
        step2Row.addView(b2);

        LinearLayout col2 = column(a);
        col2.setPaddingRelative(dp(a, 12), 0, 0, 0);
        col2.addView(text(a, a.getString(R.string.adb_guide_step2), 14, TEXT));

        TextView enterInApp = text(a, a.getString(R.string.adb_guide_step2_enter_here), 13, ACCENT_TEXT);
        enterInApp.setTypeface(sans(a, 600));
        enterInApp.setPaddingRelative(0, dp(a, 6), 0, 0);
        enterInApp.setOnClickListener(v -> {
            if (sess != null) sess.dismissAll();
            showPairDialog(a, sess, onRefresh);
        });
        col2.addView(enterInApp);

        step2Row.addView(col2, weight1());
        content.addView(step2Row, topMarginParams(a, 6));

        // Step ③
        LinearLayout step3Row = row(a);
        step3Row.setPaddingRelative(0, dp(a, 12), 0, dp(a, 8));
        TextView b3 = text(a, "③", 18, TEXT);
        b3.setTypeface(sans(a, 700));
        step3Row.addView(b3);

        LinearLayout col3 = column(a);
        col3.setPaddingRelative(dp(a, 12), 0, 0, 0);
        col3.addView(text(a, a.getString(R.string.adb_guide_step3), 14, TEXT));
        step3Row.addView(col3, weight1());
        content.addView(step3Row, topMarginParams(a, 6));

        sv.addView(content);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        svp.topMargin = dp(a, 4);
        svp.bottomMargin = dp(a, 12);
        c.addView(sv, svp);

        Dialog d = bottomSheet(a, c);
        if (sess != null) sess.guideDialog = d;
        d.setOnDismissListener(di -> {
            if (sess != null && sess.guideDialog == di) sess.guideDialog = null;
            if (AdbManager.state != AdbManager.State.PAIRING && AdbManager.detectedPairingPort <= 0) {
                AdbPairingService.cancelNotification(a);
            }
        });
        d.show();
    }

    static void showPairDialog(Activity a, DialogSession sess, Runnable onRefresh) {
        AdbManager.startDiscovery(a);
        LinearLayout c = column(a);

        TextView h = title(a, a.getString(R.string.adb_pair_sheet_title), 18);
        h.setPaddingRelative(0, 0, 0, dp(a, 6));
        c.addView(h);

        TextView desc = text(a, a.getString(R.string.adb_pair_sheet_desc), 13, MUTED);
        desc.setPaddingRelative(0, 0, 0, dp(a, 14));
        c.addView(desc);

        c.addView(text(a, a.getString(R.string.adb_pair_code_label), 12, MUTED));
        EditText codeInput = new EditText(a);
        codeInput.setHint("123456");
        codeInput.setHintTextColor(MUTED);
        codeInput.setTextColor(TEXT);
        codeInput.setTextSize(22);
        codeInput.setTypeface(mono(a, 600));
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        codeInput.setGravity(Gravity.CENTER);
        codeInput.setLetterSpacing(0.12f);
        codeInput.setPaddingRelative(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        codeInput.setBackground(outlined(a, BG, 12, OUTLINE, 1));
        c.addView(codeInput, topMarginParams(a, 6));

        TextView portLabel = text(a, a.getString(R.string.adb_pair_port_label), 12, MUTED);
        portLabel.setPaddingRelative(0, dp(a, 12), 0, dp(a, 4));
        c.addView(portLabel);

        EditText portInput = new EditText(a);
        portInput.setHint(AdbManager.detectedPairingPort > 0 ? String.valueOf(AdbManager.detectedPairingPort) : "38491");
        if (AdbManager.detectedPairingPort > 0) {
            portInput.setText(String.valueOf(AdbManager.detectedPairingPort));
        }
        portInput.setHintTextColor(MUTED);
        portInput.setTextColor(TEXT);
        portInput.setTextSize(15);
        portInput.setTypeface(mono(a, 400));
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        portInput.setPaddingRelative(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        portInput.setBackground(outlined(a, BG, 12, OUTLINE, 1));
        c.addView(portInput, full());

        LinearLayout btns = row(a);
        Button cancel = button(a, a.getString(android.R.string.cancel), false);
        Button pair = button(a, a.getString(R.string.adb_pair_action), true);
        btns.addView(cancel, new LinearLayout.LayoutParams(0, dp(a, 44), 1));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(a, 44), 1);
        pp.setMarginStart(dp(a, 10));
        btns.addView(pair, pp);
        c.addView(btns, topMarginParams(a, 16));

        Button devBtn = outlinedButton(a, a.getString(R.string.setup_adb_open_dev_options), ACCENT_TEXT);
        devBtn.setOnClickListener(v -> openDeveloperOptions(a));
        c.addView(devBtn, topMarginParams(a, 10));

        Dialog d = bottomSheet(a, c);
        if (sess != null) sess.pairDialog = d;
        d.setOnDismissListener(di -> {
            if (sess != null && sess.pairDialog == di) sess.pairDialog = null;
        });
        cancel.setOnClickListener(v -> d.dismiss());
        pair.setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim();
            String portStr = portInput.getText().toString().trim();
            if (code.length() != 6) {
                Toast.makeText(a, R.string.adb_pair_invalid_code, Toast.LENGTH_SHORT).show();
                return;
            }
            int port = AdbManager.detectedPairingPort;
            if (!portStr.isEmpty()) {
                try { port = Integer.parseInt(portStr); } catch (Exception ignored) {}
            }
            if (port <= 0 || port > 65535) {
                Toast.makeText(a, R.string.adb_pair_invalid_port, Toast.LENGTH_SHORT).show();
                return;
            }

            pair.setEnabled(false);
            pair.setText(R.string.adb_pair_pairing);
            AdbManager.pair(port, code, (ok, why) -> a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (sess != null) sess.dismissAll();
                else d.dismiss();
                if (ok) {
                    Prefs.setAdbSkipped(a, false);
                    Prefs.setAdbEnabled(a, true);
                    AdbManager.ensure(a);
                    Toast.makeText(a, R.string.adb_pair_success, Toast.LENGTH_SHORT).show();
                    if (onRefresh != null) onRefresh.run();
                } else {
                    Toast.makeText(a, a.getString(R.string.adb_pair_failed, why), Toast.LENGTH_LONG).show();
                }
            }));
        });
        d.show();
    }

    static void showConnectDialog(Activity a, Runnable onRefresh) {
        LinearLayout c = column(a);

        TextView h = title(a, a.getString(R.string.adb_connect_sheet_title), 18);
        h.setPaddingRelative(0, 0, 0, dp(a, 6));
        c.addView(h);

        TextView desc = text(a, a.getString(R.string.adb_connect_sheet_desc), 13, MUTED);
        desc.setPaddingRelative(0, 0, 0, dp(a, 14));
        c.addView(desc);

        c.addView(text(a, a.getString(R.string.adb_connect_sheet_port_label), 12, MUTED));
        EditText portInput = new EditText(a);
        int curPort = AdbManager.detectedConnectPort > 0 ? AdbManager.detectedConnectPort : Prefs.adbPort(a);
        portInput.setHint(curPort > 0 ? String.valueOf(curPort) : "37111");
        if (curPort > 0) {
            portInput.setText(String.valueOf(curPort));
        }
        portInput.setHintTextColor(MUTED);
        portInput.setTextColor(TEXT);
        portInput.setTextSize(22);
        portInput.setTypeface(mono(a, 600));
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        portInput.setGravity(Gravity.CENTER);
        portInput.setLetterSpacing(0.08f);
        portInput.setPaddingRelative(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        portInput.setBackground(outlined(a, BG, 12, OUTLINE, 1));
        c.addView(portInput, topMarginParams(a, 6));

        LinearLayout btns = row(a);
        Button cancel = button(a, a.getString(android.R.string.cancel), false);
        Button connect = button(a, a.getString(R.string.adb_connect_sheet_action), true);
        btns.addView(cancel, new LinearLayout.LayoutParams(0, dp(a, 44), 1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(a, 44), 1);
        cp.setMarginStart(dp(a, 10));
        btns.addView(connect, cp);
        c.addView(btns, topMarginParams(a, 16));

        Button devBtn = outlinedButton(a, a.getString(R.string.setup_adb_open_dev_options), ACCENT_TEXT);
        devBtn.setOnClickListener(v -> openDeveloperOptions(a));
        c.addView(devBtn, topMarginParams(a, 10));

        Dialog d = bottomSheet(a, c);
        cancel.setOnClickListener(v -> d.dismiss());
        connect.setOnClickListener(v -> {
            String portStr = portInput.getText().toString().trim();
            int p = 0;
            try { p = Integer.parseInt(portStr); } catch (Exception ignored) {}
            if (p <= 0 || p > 65535) {
                Toast.makeText(a, R.string.adb_pair_invalid_port, Toast.LENGTH_SHORT).show();
                return;
            }
            d.dismiss();
            Prefs.setAdbSkipped(a, false);
            Prefs.setAdbEnabled(a, true);
            Prefs.setAdbPort(a, p);
            AdbManager.connect(p);
            if (onRefresh != null) onRefresh.run();
        });
        d.show();
    }

    private static LinearLayout.LayoutParams topMarginParams(Context c, int dpVal) {
        LinearLayout.LayoutParams p = full();
        p.topMargin = dp(c, dpVal);
        return p;
    }
}
