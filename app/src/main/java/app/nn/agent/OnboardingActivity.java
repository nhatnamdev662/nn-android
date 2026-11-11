package app.nn.agent;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static app.nn.agent.Ui.*;

/**
 * First-run setup wizard: one step per screen with explicit navigation.
 * Steps never auto-advance behind the user's back; returning from system Settings or
 * ADB pairing notifications restores the active onboarding step cleanly.
 */
public class OnboardingActivity extends Screen implements Account.Listener, AdbManager.Listener {
    static final int WELCOME = 0, BRAIN = 1, ACCESS = 2, AGENTS = 3, BACKGROUND = 4, ADB = 5, SAFETY = 6, TRY = 7;
    private static final int LAST = TRY;
    static final String EXTRA_RESUME_UNFINISHED = "resume_unfinished";

    private static final int BADGE_NONE = 0, BADGE_REQUIRED = 1, BADGE_OPTIONAL = 2;

    private ScrollView scroll;
    private LinearLayout body, bottom, stepBar;
    private TextView progress;
    private Button skipBtn;
    private int step;
    private int accessTries;
    private boolean stuckOpen;
    private final AdbUi.DialogSession adbUi = new AdbUi.DialogSession();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Prefs.onboardVersion(this) < 3) {
            int saved = Prefs.onboardStep(this);
            if (Prefs.onboardVersion(this) < 2) {
                // 1.x saved its step with the old numbering (it had Termux steps): resume at Accessibility instead
                if (saved > BRAIN) Prefs.setOnboardStep(this, ACCESS);
            } else if (saved >= ADB) {
                // v2 did not have the ADB step between BACKGROUND and SAFETY
                Prefs.setOnboardStep(this, saved + 1);
            }
            Prefs.setOnboardVersion(this, 3);
        }

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_RESUME_UNFINISHED, false)) {
            step = firstUnfinishedStep();
            Prefs.setOnboardStep(this, step);
        } else {
            step = Math.min(Prefs.onboardStep(this), LAST);
        }

        LinearLayout root = column(this);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        LinearLayout top = row(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPaddingRelative(dp(this, 20), dp(this, 10), dp(this, 8), dp(this, 2));
        progress = monoText(this, "", 12, MUTED);
        top.addView(progress, weight1());
        skipBtn = button(this, getString(R.string.onboarding_skip_setup), false);
        skipBtn.setBackgroundResource(ripple(this, false));
        skipBtn.setTextColor(MUTED);
        skipBtn.setOnClickListener(v -> confirmSheet(this, getString(R.string.onboarding_skip_title),
                getString(R.string.onboarding_skip_text), getString(R.string.onboarding_skip_action), false, this::finishGuide));
        top.addView(skipBtn);
        root.addView(top, full());

        stepBar = row(this);
        stepBar.setPaddingRelative(dp(this, 20), dp(this, 4), dp(this, 20), dp(this, 10));
        root.addView(stepBar, full());

        scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        body = column(this);
        body.setPaddingRelative(dp(this, 24), dp(this, 12), dp(this, 24), dp(this, 24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        bottom = column(this);
        bottom.setPaddingRelative(dp(this, 20), dp(this, 8), dp(this, 20), dp(this, 14));
        root.addView(bottom, full());
        setContentView(root);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_RESUME_UNFINISHED, false)) {
            step = firstUnfinishedStep();
            Prefs.setOnboardStep(this, step);
        }
    }

    private int firstUnfinishedStep() {
        int[] checkable = {ACCESS, AGENTS, BACKGROUND, ADB};
        for (int s : checkable) {
            if (!done(this, s)) return s;
        }
        return BRAIN;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        Prefs.setAdbReturnToOnboarding(this, true);
        Account.addListener(this);
        AdbManager.get(this);
        AdbManager.addListener(this);
        if (step == ADB) {
            AdbManager.startDiscovery(this);
            AdbManager.probeNow(this);
        }
        if (AdbManager.isConnected()) adbUi.dismissAll();
        for (String a : wanted(this)) Account.of(a).refresh(this);
        render();
    }

    @Override
    protected void onPause() {
        Account.removeListener(this);
        AdbManager.removeListener(this);
        super.onPause();
    }

    @Override
    public void onAccountChanged() {
        if (isFinishing() || isDestroyed()) return;
        render();
    }

    @Override
    public void onAdbStateChanged(AdbManager.State state, String host, int port, String error) {
        if (isFinishing() || isDestroyed()) return;
        if (AdbManager.isConnected()) {
            adbUi.dismissAll();
        }
        if (step == ADB) {
            render();
        }
    }

    @Override
    public void onAdbPairingDiscovered(String host, int port) {
        if (isFinishing() || isDestroyed()) return;
        if (step == ADB) render();
    }

    @Override
    public void onBackPressed() {
        if (step > WELCOME) {
            go(step - 1);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------- state

    static boolean done(Context c, int step) {
        switch (step) {
            case ACCESS:
                return SetupState.accessibilityEnabled(c);
            case AGENTS:
                for (String a : wanted(c)) if (!SetupState.signedIn(c, a)) return false;
                return true;
            case BACKGROUND:
                PowerManager pm = c.getSystemService(PowerManager.class);
                return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
            case ADB:
                return AdbManager.isConnected();
            default:
                return false;
        }
    }

    private static boolean isCheckableStep(int s) {
        return s == ACCESS || s == AGENTS || s == BACKGROUND || s == ADB;
    }

    static String[] wanted(Context c) {
        String a = Prefs.agents(c);
        if ("both".equals(a)) return new String[]{"claude", "codex"};
        return new String[]{Account.known(a) ? a : "claude"};
    }

    private void go(int s) {
        step = Math.max(WELCOME, Math.min(LAST, s));
        Prefs.setOnboardStep(this, step);
        stuckOpen = false;
        if (step == ADB) {
            AdbManager.startDiscovery(this);
            AdbManager.probeNow(this);
        }
        render();
        if (scroll != null) scroll.scrollTo(0, 0);
    }

    private void finishGuide() {
        Prefs.setAdbReturnToOnboarding(this, false);
        Prefs.setOnboarded(this, true);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    // ------------------------------------------------------------ render

    private void render() {
        body.removeAllViews();
        bottom.removeAllViews();
        progress.setText(step == WELCOME ? "" : getString(R.string.onboarding_progress, step, LAST));
        skipBtn.setVisibility(step == TRY ? View.GONE : View.VISIBLE);
        renderStepBar();

        boolean ok = done(this, step);
        switch (step) {
            case WELCOME:
                welcome();
                break;
            case BRAIN:
                brain();
                break;
            case ACCESS:
                access(ok);
                break;
            case AGENTS:
                agents(ok);
                break;
            case BACKGROUND:
                background(ok);
                break;
            case ADB:
                adbStep(ok);
                break;
            case SAFETY:
                safety();
                break;
            case TRY:
                tryIt();
                break;
            default:
                break;
        }
    }

    private void renderStepBar() {
        stepBar.removeAllViews();
        if (step == WELCOME) {
            stepBar.setVisibility(View.GONE);
            return;
        }
        stepBar.setVisibility(View.VISIBLE);
        for (int s = BRAIN; s <= LAST; s++) {
            View seg = new View(this);
            int color;
            if (s == step) {
                color = ACCENT;
            } else if (isCheckableStep(s)) {
                if (done(this, s)) color = OK;
                else if (s < step) color = WARN;
                else color = CARD2;
            } else {
                color = s < step ? Theme.withAlpha(ACCENT, 0x77) : CARD2;
            }
            seg.setBackground(rounded(this, color, 3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(this, 4), 1);
            if (s < LAST) lp.setMarginEnd(dp(this, 5));
            stepBar.addView(seg, lp);
        }
    }

    private void welcome() {
        LinearLayout c = column(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPaddingRelative(0, dp(this, 36), 0, 0);
        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundResource(R.drawable.ic_launcher_background);
        tile.setClipToOutline(true);
        tile.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(OnboardingActivity.this, 24));
            }
        });
        ImageView face = new ImageView(this);
        face.setImageResource(R.drawable.bubble_face);
        tile.addView(face, new FrameLayout.LayoutParams(dp(this, 64), dp(this, 64), Gravity.CENTER));
        c.addView(tile, new LinearLayout.LayoutParams(dp(this, 88), dp(this, 88)));
        TextView t = title(this, getString(R.string.onboarding_welcome_title), 28);
        t.setGravity(Gravity.CENTER);
        t.setPaddingRelative(0, dp(this, 20), 0, dp(this, 6));
        c.addView(t);
        TextView s = text(this, getString(R.string.onboarding_welcome_text), 16, MUTED);
        s.setGravity(Gravity.CENTER);
        c.addView(s);
        body.addView(c, full());
        LinearLayout list = column(this);
        list.setPaddingRelative(0, dp(this, 28), 0, 0);
        bullet(list, R.drawable.ms_chat_bubble, getString(R.string.onboarding_welcome_ask_title),
                getString(R.string.onboarding_welcome_ask_text));
        bullet(list, R.drawable.ms_shield_person, getString(R.string.onboarding_welcome_plan_title),
                getString(R.string.onboarding_welcome_plan_text));
        bullet(list, R.drawable.ms_check, getString(R.string.onboarding_welcome_control_title),
                getString(R.string.onboarding_welcome_control_text));
        body.addView(list, full());
        TextView time = monoText(this, getString(R.string.onboarding_welcome_time), 12, MUTED);
        time.setGravity(Gravity.CENTER);
        time.setPaddingRelative(0, dp(this, 20), 0, 0);
        body.addView(time, full());
        primary(getString(R.string.onboarding_get_started), v -> go(BRAIN));
    }

    private void brain() {
        header(R.drawable.ms_forum, getString(R.string.onboarding_brain_title),
                getString(R.string.onboarding_brain_text), false, BADGE_NONE);
        String cur = Prefs.agents(this);
        String[][] opts = {
                {"claude", "Claude Code", getString(R.string.onboarding_brain_claude)},
                {"codex", "Codex", getString(R.string.onboarding_brain_codex)},
                {"opencode", "OpenCode", getString(R.string.onboarding_brain_opencode)},
                {"agy", "Antigravity", getString(R.string.onboarding_brain_agy)},
                {"grok", "Grok", getString(R.string.onboarding_brain_grok)},
                {"both", "Claude Code + Codex", getString(R.string.onboarding_brain_both)},
        };
        for (String[] o : opts) {
            boolean on = o[0].equals(cur);
            LinearLayout r = row(this);
            r.setPaddingRelative(dp(this, 14), dp(this, 14), dp(this, 14), dp(this, 14));
            r.setBackground(outlined(this, CARD, 16, on ? ACCENT : OUTLINE, on ? 2 : 1));
            if ("both".equals(o[0])) {
                LinearLayout two = row(this);
                TextView a = agentTile(this, "claude");
                a.setLayoutParams(new LinearLayout.LayoutParams(dp(this, 28), dp(this, 40)));
                two.addView(a);
                TextView b = agentTile(this, "codex");
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(this, 28), dp(this, 40));
                bp.setMarginStart(dp(this, 2));
                two.addView(b, bp);
                r.addView(two);
            } else {
                r.addView(agentTile(this, o[0]));
            }
            LinearLayout t = column(this);
            t.setPaddingRelative(dp(this, 14), 0, dp(this, 8), 0);
            LinearLayout tr = row(this);
            tr.addView(title(this, o[1], 16));
            if ("claude".equals(o[0])) {
                TextView tag = monoText(this, getString(R.string.onboarding_recommended), 11, ACCENT_TEXT);
                tag.setPaddingRelative(dp(this, 6), dp(this, 1), dp(this, 6), dp(this, 1));
                tag.setBackground(rounded(this, ACCENT_SOFT, 6));
                LinearLayout.LayoutParams tp = wrap();
                tp.setMarginStart(dp(this, 8));
                tr.addView(tag, tp);
            }
            if (Account.known(o[0]) && Account.of(o[0]).freeTier()) {
                LinearLayout.LayoutParams tp = wrap();
                tp.setMarginStart(dp(this, 8));
                tr.addView(freeTag(this), tp);
            }
            t.addView(tr);
            t.addView(text(this, o[2], 13, MUTED));
            r.addView(t, weight1());
            r.addView(radio(on));
            r.setOnClickListener(v -> {
                Prefs.setAgents(this, o[0]);
                Prefs.setBackend(this, Account.known(o[0]) ? o[0] : "claude");
                render();
            });
            LinearLayout.LayoutParams p = full();
            p.topMargin = dp(this, 10);
            body.addView(r, p);
        }
        primary(getString(R.string.onboarding_continue), v -> go(ACCESS));
        footerNav(WELCOME, null, null);
    }

    private void access(boolean ok) {
        header(R.drawable.ms_shield_person, getString(R.string.onboarding_access_title),
                getString(ok ? R.string.onboarding_access_done : R.string.onboarding_access_text),
                ok, BADGE_REQUIRED);
        if (ok) {
            completedCard(getString(R.string.onboarding_access_done),
                    getString(R.string.onboarding_access_open),
                    v -> openAccessibilitySettings(this));
            primary(getString(R.string.onboarding_continue), v -> go(AGENTS));
            footerNav(BRAIN, null, null);
            return;
        }
        String agents = Prefs.agents(this);
        note("opencode".equals(agents) ? getString(R.string.onboarding_access_privacy_opencode, agentNames())
                : "both".equals(agents) ? getString(R.string.onboarding_access_privacy_both)
                : getString(R.string.onboarding_access_privacy, agentNames(),
                "codex".equals(agents) ? "OpenAI" : "agy".equals(agents) ? "Google" : "grok".equals(agents) ? "xAI"
                        : "Anthropic"));
        if (accessTries > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) restrictedHelp();
        note(getString(R.string.onboarding_access_list_hint));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stuck(getString(R.string.onboarding_access_greyed_title),
                    getString(R.string.onboarding_access_greyed_text), null, null);
            if (stuckOpen) {
                Button infoBtn = outlinedButton(this, getString(R.string.onboarding_app_info), TEXT);
                infoBtn.setOnClickListener(v -> openAppInfo(getPackageName()));
                LinearLayout.LayoutParams ip = full();
                ip.topMargin = dp(this, 8);
                body.addView(infoBtn, ip);
            }
        }
        primary(getString(R.string.onboarding_access_open), v -> {
            accessTries++;
            openAccessibilitySettings(this);
        });
        footerNav(BRAIN, getString(R.string.onboarding_do_later), v -> go(AGENTS));
    }

    private void agents(boolean ok) {
        header(R.drawable.ms_terminal,
                getString(ok ? R.string.onboarding_agents_done_title : R.string.onboarding_agents_title),
                ok && "both".equals(Prefs.agents(this)) ? getString(R.string.onboarding_agents_done_both)
                        : getString(ok ? R.string.onboarding_agents_done : R.string.onboarding_agents_text, agentNames()),
                ok, BADGE_REQUIRED);
        boolean first = true;
        for (String a : wanted(this)) {
            Account acc = Account.of(a);
            boolean installed = SetupState.installed(this, a), signed = SetupState.signedIn(this, a);
            boolean installing = acc.step == Account.Step.INSTALLING;
            LinearLayout card = column(this);
            card.setPaddingRelative(dp(this, 16), dp(this, 14), dp(this, 16), dp(this, 14));
            card.setBackground(signed
                    ? outlined(this, CARD, 16, Theme.withAlpha(OK, 0x55), 1)
                    : rounded(this, CARD, 16));
            LinearLayout r = row(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.addView(agentTile(this, a));
            LinearLayout t = column(this);
            t.setPaddingRelative(dp(this, 12), 0, 0, 0);
            t.addView(title(this, acc.name(), 15));
            t.addView(single(monoText(this, signed ? (acc.opencode() ? getString(R.string.onboarding_agent_ready,
                    acc.plan.isEmpty() ? getString(R.string.onboarding_agent_free_models) : acc.planLabel())
                    : acc.email.isEmpty() ? getString(R.string.onboarding_agent_signed_in)
                    : getString(R.string.onboarding_agent_signed_in_as, acc.email))
                    : getString(installing ? R.string.onboarding_agent_installing : installed
                    ? R.string.onboarding_agent_installed : R.string.onboarding_agent_not_installed), 12,
                    signed ? OK : MUTED)));
            r.addView(t, weight1());
            if (signed) r.addView(icon(this, R.drawable.ms_check, OK, 22));
            else if (installing) r.addView(spinner(this, 20));
            card.addView(r, full());
            if (!signed) {
                TextView how = text(this, installing ? acc.detail
                        : acc.step == Account.Step.ERROR ? acc.detail
                        : installed ? getString(acc.claude() ? R.string.onboarding_agent_sign_in_claude : acc.opencode()
                        ? R.string.onboarding_agent_checking_opencode : acc.agy() ? R.string.onboarding_agent_sign_in_agy
                        : acc.grok() ? R.string.onboarding_agent_sign_in_grok : R.string.onboarding_agent_sign_in_codex)
                        : Ui.downloadNote(this, getString(acc.claude() ? R.string.onboarding_agent_download_claude
                        : acc.opencode() ? R.string.onboarding_agent_download_opencode
                        : acc.agy() ? R.string.onboarding_agent_download_agy
                        : acc.grok() ? R.string.onboarding_agent_download_grok
                        : R.string.onboarding_agent_download_codex)), 14,
                        acc.step == Account.Step.ERROR ? ERR : MUTED);
                how.setPaddingRelative(0, dp(this, 10), 0, installing ? 0 : dp(this, 10));
                card.addView(how);
                if (!installing && !(installed && acc.opencode())) {
                    Button b = button(this, installed ? getString(acc.claude() ? R.string.onboarding_sign_in_claude
                            : acc.agy() ? R.string.onboarding_sign_in_google : acc.grok() ? R.string.onboarding_sign_in_grok
                            : R.string.onboarding_sign_in_chatgpt)
                            : getString(R.string.onboarding_install_agent, acc.name()), first);
                    b.setOnClickListener(v -> {
                        if (installed) startActivity(new Intent(this, SignInActivity.class).putExtra("agent", a));
                        else acc.install(this);
                    });
                    card.addView(b, wrap());
                    first = false;
                }
            }
            LinearLayout.LayoutParams p = full();
            p.topMargin = dp(this, 14);
            body.addView(card, p);
        }
        if (ok) {
            primary(getString(R.string.onboarding_continue), v -> go(BACKGROUND));
            footerNav(ACCESS, null, null);
        } else {
            note(getString(R.string.onboarding_agents_wifi));
            stuck(getString(R.string.onboarding_stuck_sign_in_q), getString(R.string.onboarding_stuck_sign_in_a),
                    getString(R.string.onboarding_stuck_install_q), getString(R.string.onboarding_stuck_install_a));
            footerNav(ACCESS, getString(R.string.onboarding_do_later), v -> go(BACKGROUND));
        }
    }

    private void background(boolean ok) {
        header(R.drawable.ms_build_circle, getString(R.string.onboarding_background_title),
                getString(ok ? R.string.onboarding_background_done : R.string.onboarding_background_text),
                ok, BADGE_OPTIONAL);
        if (ok) {
            completedCard(getString(R.string.onboarding_background_done), null, null);
            primary(getString(R.string.onboarding_continue), v -> go(ADB));
            footerNav(AGENTS, null, null);
        } else {
            primary(getString(R.string.onboarding_allow), v -> startActivity(new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));
            footerNav(AGENTS, getString(R.string.onboarding_skip_for_now), v -> go(ADB));
        }
    }

    private void adbStep(boolean ok) {
        header(R.drawable.ms_terminal, getString(R.string.onboarding_adb_title),
                getString(ok ? R.string.onboarding_adb_done : R.string.onboarding_adb_text),
                ok, BADGE_OPTIONAL);
        LinearLayout statusBox = AdbUi.statusBanner(this);
        LinearLayout.LayoutParams sp = full();
        sp.topMargin = dp(this, 18);
        if (ok) {
            LinearLayout card = column(this);
            card.setBackground(outlined(this, CARD, 16, Theme.withAlpha(OK, 0x55), 1));
            card.setPaddingRelative(dp(this, 4), dp(this, 4), dp(this, 4), dp(this, 4));
            card.addView(statusBox, full());
            body.addView(card, sp);

            primary(getString(R.string.onboarding_continue), v -> go(SAFETY));
            footerNav(BACKGROUND, null, null);
            return;
        }
        body.addView(statusBox, sp);

        Button portBtn = outlinedButton(this, getString(R.string.onboarding_adb_connect_port), ACCENT_TEXT);
        portBtn.setOnClickListener(v -> AdbUi.showConnectDialog(this, this::render));
        LinearLayout.LayoutParams pp = full();
        pp.topMargin = dp(this, 14);
        body.addView(portBtn, pp);

        stuck(getString(R.string.onboarding_adb_dev_q), getString(R.string.onboarding_adb_dev_a), null, null);
        if (stuckOpen) {
            Button aboutBtn = outlinedButton(this, getString(R.string.onboarding_adb_open_about), TEXT);
            aboutBtn.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
                } catch (Throwable ignored) {
                }
            });
            LinearLayout.LayoutParams ap = full();
            ap.topMargin = dp(this, 8);
            body.addView(aboutBtn, ap);
        }

        primary(getString(R.string.onboarding_adb_pair), v -> AdbUi.showGuideDialog(this, adbUi, this::render));
        footerNav(BACKGROUND, getString(R.string.onboarding_skip_for_now), v -> {
            Prefs.setAdbSkipped(this, true);
            go(SAFETY);
        });
    }

    private void safety() {
        header(R.drawable.ms_shield_person, getString(R.string.onboarding_safety_title),
                getString(R.string.onboarding_safety_text), false, BADGE_NONE);
        boolean auto = Prefs.autonomousDefault(this);
        String[][] opts = {
                {"ask", getString(R.string.onboarding_safety_ask_title), getString(R.string.onboarding_safety_ask_text)},
                {"auto", getString(R.string.onboarding_safety_auto_title), getString(R.string.onboarding_safety_auto_text)},
        };
        for (String[] o : opts) {
            boolean on = "auto".equals(o[0]) == auto;
            LinearLayout r = row(this);
            r.setPaddingRelative(dp(this, 14), dp(this, 14), dp(this, 14), dp(this, 14));
            r.setBackground(outlined(this, CARD, 16, on ? ACCENT : OUTLINE, on ? 2 : 1));
            LinearLayout t = column(this);
            t.setPaddingRelative(0, 0, dp(this, 8), 0);
            t.addView(title(this, o[1], 16));
            t.addView(text(this, o[2], 13, MUTED));
            r.addView(t, weight1());
            r.addView(radio(on));
            r.setOnClickListener(v -> {
                Prefs.setAutonomousDefault(this, "auto".equals(o[0]));
                render();
            });
            LinearLayout.LayoutParams p = full();
            p.topMargin = dp(this, 10);
            body.addView(r, p);
        }
        note(getString(R.string.onboarding_safety_note));
        primary(getString(R.string.onboarding_continue), v -> go(TRY));
        footerNav(ADB, null, null);
    }

    private void tryIt() {
        header(R.drawable.ms_check, getString(R.string.onboarding_try_title),
                getString(R.string.onboarding_try_text), true, BADGE_NONE);
        String prompt = getString(R.string.onboarding_try_prompt);
        TextView ex = monoText(this, getString(R.string.onboarding_try_prompt_quoted, prompt), 13, TEXT);
        ex.setPaddingRelative(dp(this, 14), dp(this, 12), dp(this, 14), dp(this, 12));
        ex.setBackground(rounded(this, CARD, 12));
        LinearLayout.LayoutParams ep = full();
        ep.topMargin = dp(this, 16);
        body.addView(ex, ep);
        note(getString(R.string.onboarding_try_tip));
        primary(getString(R.string.onboarding_try_now), v -> {
            Prefs.setAdbReturnToOnboarding(this, false);
            Prefs.setOnboarded(this, true);
            ChatHub hub = ChatHub.get(this);
            Sessions.Session s = hub.create(Prefs.backend(this));
            hub.select(s);
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            startActivity(new Intent(this, ChatActivity.class).putExtra("id", s.id)
                    .putExtra("draft", prompt));
            finish();
        });
        footerNav(SAFETY, getString(R.string.onboarding_go_to_chats), v -> finishGuide());
    }

    // ------------------------------------------------------------ pieces

    private void header(int iconRes, String title, String sub, boolean ok, int badgeMode) {
        LinearLayout topRow = row(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(oval(ok ? Theme.withAlpha(OK, 0x2E) : ACCENT_SOFT));
        ic.addView(icon(this, ok ? R.drawable.ms_check : iconRes, ok ? OK : ACCENT_TEXT, 28),
                new FrameLayout.LayoutParams(dp(this, 28), dp(this, 28), Gravity.CENTER));
        topRow.addView(ic, new LinearLayout.LayoutParams(dp(this, 56), dp(this, 56)));

        if (ok || badgeMode != BADGE_NONE) {
            String badgeLabel;
            int badgeFg;
            int badgeBg;
            if (ok) {
                badgeLabel = getString(R.string.onboarding_step_done_badge);
                badgeFg = OK;
                badgeBg = Theme.withAlpha(OK, 0x24);
            } else if (badgeMode == BADGE_REQUIRED) {
                badgeLabel = getString(R.string.onboarding_badge_required);
                badgeFg = ACCENT_TEXT;
                badgeBg = ACCENT_SOFT;
            } else {
                badgeLabel = getString(R.string.onboarding_badge_optional);
                badgeFg = MUTED;
                badgeBg = CARD2;
            }
            TextView pill = monoText(this, badgeLabel, 11, badgeFg);
            pill.setTypeface(mono(this, 600));
            pill.setPaddingRelative(dp(this, 10), dp(this, 4), dp(this, 10), dp(this, 4));
            pill.setBackground(rounded(this, badgeBg, 8));
            LinearLayout.LayoutParams bp = wrap();
            bp.setMarginStart(dp(this, 12));
            topRow.addView(pill, bp);
        }

        LinearLayout.LayoutParams tp = full();
        tp.topMargin = dp(this, 6);
        body.addView(topRow, tp);

        TextView t = title(this, title, 26);
        t.setPaddingRelative(0, dp(this, 16), 0, dp(this, 6));
        body.addView(t);
        body.addView(text(this, sub, 16, MUTED));
    }

    private void completedCard(String summary, String actionLabel, View.OnClickListener actionListener) {
        LinearLayout card = column(this);
        card.setBackground(outlined(this, CARD, 16, Theme.withAlpha(OK, 0x55), 1));
        card.setPaddingRelative(dp(this, 16), dp(this, 14), dp(this, 16), dp(this, 14));

        LinearLayout r = row(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(icon(this, R.drawable.ms_check, OK, 22));
        TextView tv = text(this, summary, 15, TEXT);
        tv.setPaddingRelative(dp(this, 12), 0, 0, 0);
        r.addView(tv, weight1());
        card.addView(r, full());

        if (actionLabel != null && actionListener != null) {
            Button reopen = outlinedButton(this, actionLabel, MUTED);
            reopen.setOnClickListener(actionListener);
            LinearLayout.LayoutParams bp = full();
            bp.topMargin = dp(this, 12);
            card.addView(reopen, bp);
        }

        LinearLayout.LayoutParams cp = full();
        cp.topMargin = dp(this, 18);
        body.addView(card, cp);
    }

    private void note(String s) {
        TextView t = text(this, s, 14, MUTED);
        LinearLayout.LayoutParams p = full();
        p.topMargin = dp(this, 14);
        body.addView(t, p);
    }

    private void bullet(LinearLayout list, int iconRes, String title, String sub) {
        LinearLayout r = row(this);
        r.setGravity(Gravity.TOP);
        r.setPaddingRelative(0, dp(this, 10), 0, dp(this, 10));
        r.addView(icon(this, iconRes, ACCENT_TEXT, 22));
        LinearLayout t = column(this);
        t.setPaddingRelative(dp(this, 14), 0, 0, 0);
        t.addView(title(this, title, 15));
        t.addView(text(this, sub, 14, MUTED));
        r.addView(t, weight1());
        list.addView(r, full());
    }

    /** Shown after a first try at the accessibility switch on Android 13+. */
    private void restrictedHelp() {
        LinearLayout c = column(this);
        c.setPaddingRelative(dp(this, 16), dp(this, 14), dp(this, 16), dp(this, 14));
        c.setBackground(outlined(this, CARD, 16, Theme.withAlpha(ACCENT, 0x59), 1));
        c.addView(title(this, getString(R.string.onboarding_access_greyed_title), 15));
        TextView t = text(this, getString(R.string.onboarding_access_restricted_steps), 14, MUTED);
        t.setLineSpacing(0, 1.2f);
        t.setPaddingRelative(0, dp(this, 6), 0, 0);
        c.addView(t);
        Button infoBtn = outlinedButton(this, getString(R.string.onboarding_app_info), ACCENT_TEXT);
        infoBtn.setOnClickListener(v -> openAppInfo(getPackageName()));
        LinearLayout.LayoutParams ibp = full();
        ibp.topMargin = dp(this, 12);
        c.addView(infoBtn, ibp);
        LinearLayout.LayoutParams p = full();
        p.topMargin = dp(this, 16);
        body.addView(c, p);
    }

    private void stuck(String q1, String a1, String q2, String a2) {
        LinearLayout c = column(this);
        LinearLayout head = row(this);
        head.setPaddingRelative(0, dp(this, 10), 0, dp(this, 10));
        head.setBackgroundResource(ripple(this, false));
        TextView h = title(this, getString(R.string.onboarding_stuck), 14);
        h.setTextColor(ACCENT_TEXT);
        head.addView(h, weight1());
        head.addView(icon(this, stuckOpen ? R.drawable.ms_expand_less : R.drawable.ms_expand_more, ACCENT_TEXT, 20));
        head.setOnClickListener(v -> {
            stuckOpen = !stuckOpen;
            render();
        });
        c.addView(head, full());
        if (stuckOpen) {
            qa(c, q1, a1);
            if (q2 != null) qa(c, q2, a2);
        }
        LinearLayout.LayoutParams p = full();
        p.topMargin = dp(this, 18);
        body.addView(c, p);
    }

    private void qa(LinearLayout c, String q, String a) {
        TextView qt = title(this, q, 14);
        qt.setPaddingRelative(0, dp(this, 6), 0, dp(this, 2));
        c.addView(qt);
        c.addView(text(this, a, 14, MUTED));
    }

    private View radio(boolean on) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(outlined(this, 0x00000000, 11, on ? ACCENT : MUTED, 2));
        if (on) {
            View in = new View(this);
            in.setBackground(oval(ACCENT));
            f.addView(in, new FrameLayout.LayoutParams(dp(this, 10), dp(this, 10), Gravity.CENTER));
        }
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(this, 22), dp(this, 22)));
        return f;
    }

    private void primary(String label, View.OnClickListener l) {
        Button b = button(this, label, true);
        b.setOnClickListener(l);
        bottom.addView(b, bigButton());
    }

    private void footerNav(int backStep, String rightLabel, View.OnClickListener rightClick) {
        LinearLayout r = row(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = full();
        rp.topMargin = bottom.getChildCount() > 0 ? dp(this, 6) : 0;

        if (backStep >= WELCOME) {
            Button backBtn = button(this, "← " + getString(R.string.onboarding_back), false);
            backBtn.setBackgroundResource(ripple(this, false));
            backBtn.setTextColor(MUTED);
            backBtn.setOnClickListener(v -> go(backStep));
            r.addView(backBtn, rightLabel != null
                    ? new LinearLayout.LayoutParams(0, dp(this, 44), 1)
                    : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(this, 44)));
        }

        if (rightLabel != null && rightClick != null) {
            Button rightBtn = button(this, rightLabel, false);
            rightBtn.setBackgroundResource(ripple(this, false));
            rightBtn.setTextColor(TEXT);
            rightBtn.setOnClickListener(rightClick);
            LinearLayout.LayoutParams rlp = backStep >= WELCOME
                    ? new LinearLayout.LayoutParams(0, dp(this, 44), 1)
                    : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(this, 44));
            if (backStep >= WELCOME) rlp.setMarginStart(dp(this, 8));
            r.addView(rightBtn, rlp);
        }

        bottom.addView(r, rp);
    }

    private LinearLayout.LayoutParams bigButton() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(this, 52));
    }

    private String agentNames() {
        String a = Prefs.agents(this);
        return "both".equals(a) ? getString(R.string.onboarding_agent_names_both) : Account.nameOf(a);
    }

    private void openAppInfo(String pkg) {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        render();
    }
}
