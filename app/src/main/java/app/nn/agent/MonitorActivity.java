package app.nn.agent;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static app.nn.agent.Ui.*;

/** Terminal hub tab: launch a new shell or jump back into / close any open Terminal tab. */
public class MonitorActivity extends Screen {
    private static final long TICK_MS = 1000;

    private LinearLayout running, emptyCard, statusPill;
    private TextView runCount, statusPillText, termSub;
    private View statusDot;
    private FrameLayout navSlot;
    private int lastBadge = -1;
    private String lastSig;
    private boolean visible;
    private final Map<Integer, TextView> elapsedByTab = new HashMap<>();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            render(false);
            running.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = column(this);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);
        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = column(this);
        list.setPaddingRelative(0, dp(this, 12), 0, dp(this, 24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        navSlot = new FrameLayout(this);
        lastBadge = TermSession.openCount();
        navSlot.addView(bottomNav(this, NAV_SESSIONS, lastBadge));
        root.addView(navSlot, full());
        setContentView(root);

        // Header
        LinearLayout head = column(this);
        head.setPaddingRelative(dp(this, 20), dp(this, 8), dp(this, 16), dp(this, 4));
        LinearLayout titleRow = row(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title(this, getString(R.string.terminal_title), 28), weight1());
        statusPill = pillBadge(this, CARD, LINE);
        statusDot = dot(this, OK, 6);
        statusPill.addView(statusDot);
        statusPillText = monoText(this, "", 11, MUTED);
        statusPillText.setPaddingRelative(dp(this, 6), 0, 0, 0);
        statusPill.addView(statusPillText);
        statusPill.setVisibility(View.GONE);
        titleRow.addView(statusPill);
        head.addView(titleRow, full());
        TextView sub = text(this, getString(R.string.monitor_terminal_idle), 14, MUTED);
        sub.setPaddingRelative(0, dp(this, 4), 0, 0);
        head.addView(sub);
        list.addView(head, full());

        // Primary CTA: start a new Terminal tab
        LinearLayout tc = surfaceCard(this, LinearLayout.HORIZONTAL, Theme.withAlpha(ACCENT, 0x55), true);
        tc.setGravity(Gravity.CENTER_VERTICAL);
        tc.setPaddingRelative(dp(this, 14), dp(this, 14), dp(this, 12), dp(this, 14));
        FrameLayout ti = new FrameLayout(this);
        ti.setBackground(rounded(this, CODE, 12));
        ti.addView(monoText(this, ">_", 14, ACCENT), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        tc.addView(ti, new LinearLayout.LayoutParams(dp(this, 40), dp(this, 40)));
        LinearLayout tt = column(this);
        tt.setPaddingRelative(dp(this, 12), 0, dp(this, 8), 0);
        tt.addView(title(this, getString(R.string.terminal_new_session), 15));
        termSub = text(this, Linux.installed(this)
                ? getString(R.string.monitor_terminal_idle)
                : getString(R.string.terminal_needs_linux), 13, MUTED);
        tt.addView(termSub);
        tc.addView(tt, weight1());
        tc.addView(icon(this, R.drawable.ms_add, ACCENT_TEXT, 22));
        tc.setOnClickListener(v -> startActivity(new Intent(this, TerminalActivity.class).putExtra("new_tab", true)));
        list.addView(tc, cardParams(14));

        // Open Terminal tabs section
        LinearLayout rh = row(this);
        rh.setGravity(Gravity.CENTER_VERTICAL);
        TextView rl = sectionLabel(this, getString(R.string.monitor_running_now));
        rl.setPaddingRelative(dp(this, 20), dp(this, 18), dp(this, 8), dp(this, 4));
        rh.addView(rl);
        runCount = monoText(this, "", 12, OK);
        runCount.setTypeface(mono(this, 600));
        runCount.setPaddingRelative(0, dp(this, 14), 0, 0);
        rh.addView(runCount, weight1());
        list.addView(rh, full());

        running = column(this);
        list.addView(running, full());
        emptyCard = emptyCard(this, R.drawable.ms_terminal, getString(R.string.monitor_nothing_running));
    }

    private LinearLayout.LayoutParams cardParams(int topDp) {
        LinearLayout.LayoutParams cp = full();
        cp.setMargins(dp(this, 16), dp(this, topDp), dp(this, 16), 0);
        return cp;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        visible = true;
        if (termSub != null) {
            termSub.setText(Linux.installed(this)
                    ? getString(R.string.monitor_terminal_idle)
                    : getString(R.string.terminal_needs_linux));
        }
        int open = TermSession.openCount();
        if (navSlot != null && (getIntent() != null && getIntent().hasExtra("nn_tab_dir") || open != lastBadge)) {
            lastBadge = open;
            navSlot.removeAllViews();
            navSlot.addView(bottomNav(this, NAV_SESSIONS, open), full());
        }
        render(true);
        running.postDelayed(ticker, TICK_MS);
    }

    @Override
    protected void onPause() {
        visible = false;
        running.removeCallbacks(ticker);
        super.onPause();
    }

    private void render(boolean forceRebuild) {
        List<TermSession> openTerms = TermSession.openSessions();
        int n = openTerms.size();

        if (n != lastBadge) {
            lastBadge = n;
            navSlot.removeAllViews();
            navSlot.addView(bottomNav(this, NAV_SESSIONS, n), full());
        }
        runCount.setText(n > 0 ? String.valueOf(n) : "");
        if (n > 0) {
            statusPill.setVisibility(View.VISIBLE);
            statusPillText.setText(getResources().getQuantityString(R.plurals.terminal_sessions_count, n, n));
        } else {
            statusPill.setVisibility(View.GONE);
        }

        StringBuilder sigSb = new StringBuilder();
        sigSb.append(n);
        for (TermSession ts : openTerms) {
            sigSb.append('|').append(ts.number).append(':').append(ts.label());
        }
        String sig = sigSb.toString();

        if (!forceRebuild && sig.equals(lastSig) && running.getChildCount() > 0) {
            long now = System.currentTimeMillis();
            for (TermSession ts : openTerms) {
                TextView el = elapsedByTab.get(ts.number);
                if (el != null) el.setText(DateUtils.formatElapsedTime(Math.max(0, (now - ts.startedAt) / 1000L)));
            }
            return;
        }
        lastSig = sig;
        elapsedByTab.clear();
        running.removeAllViews();

        if (openTerms.isEmpty()) {
            running.addView(emptyCard, cardParams(4));
            return;
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < openTerms.size(); i++) {
            final TermSession ts = openTerms.get(i);
            LinearLayout card = surfaceCard(this, LinearLayout.VERTICAL, LINE, true);
            card.setPaddingRelative(dp(this, 14), dp(this, 14), dp(this, 14), dp(this, 14));

            LinearLayout top = row(this);
            top.setGravity(Gravity.TOP);
            FrameLayout ti = new FrameLayout(this);
            ti.setBackground(rounded(this, CODE, 12));
            ti.addView(monoText(this, ">_", 14, ACCENT), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            top.addView(ti, new LinearLayout.LayoutParams(dp(this, 40), dp(this, 40)));

            LinearLayout info = column(this);
            info.setPaddingRelative(dp(this, 12), 0, 0, 0);
            LinearLayout first = row(this);
            first.setGravity(Gravity.CENTER_VERTICAL);
            first.addView(single(title(this, ts.label(), 15)), weight1());
            TextView el = monoText(this, DateUtils.formatElapsedTime(Math.max(0, (now - ts.startedAt) / 1000L)), 11, MUTED);
            el.setPaddingRelative(dp(this, 8), 0, 0, 0);
            first.addView(el);
            elapsedByTab.put(ts.number, el);
            info.addView(first, full());

            LinearLayout tags = row(this);
            tags.setGravity(Gravity.CENTER_VERTICAL);
            tags.setPaddingRelative(0, dp(this, 6), 0, 0);
            TextView tag = monoText(this, getString(R.string.monitor_tag_terminal) + " #" + ts.number, 11, ACCENT_TEXT);
            tag.setPaddingRelative(dp(this, 6), dp(this, 1), dp(this, 6), dp(this, 1));
            tag.setBackground(rounded(this, ACCENT_SOFT, 6));
            tags.addView(tag);
            info.addView(tags, full());
            top.addView(info, weight1());
            card.addView(top, full());

            LinearLayout btns = row(this);
            Button open = button(this, getString(R.string.tools_card_open_url, getString(R.string.terminal_title)), false);
            open.setOnClickListener(v -> startActivity(new Intent(this, TerminalActivity.class).putExtra("tab", ts.number)));
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(this, 40));
            op.setMarginEnd(dp(this, 8));
            btns.addView(open, op);

            Button close = outlinedButton(this, getString(R.string.terminal_close), ERR);
            close.setOnClickListener(v -> confirmSheet(this, getString(R.string.terminal_close_title, ts.label()),
                    getString(R.string.terminal_close_running), getString(R.string.terminal_close), true, () -> {
                        ts.finish();
                        render(true);
                    }));
            btns.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(this, 40)));

            LinearLayout.LayoutParams bp = full();
            bp.topMargin = dp(this, 12);
            card.addView(btns, bp);
            card.setOnClickListener(v -> startActivity(new Intent(this, TerminalActivity.class).putExtra("tab", ts.number)));
            running.addView(card, cardParams(i == 0 ? 4 : 8));
        }
    }
}
