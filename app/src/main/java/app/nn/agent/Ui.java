package app.nn.agent;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Shared look for the app screens: theme colours (set by {@link Theme}), type, and building blocks. */
final class Ui {
    // Palette: filled by Theme.load() from the appearance settings (dark/light, accent, surface).
    static int BG = 0xFF171513, CARD = 0xFF24211E, CARD2 = 0xFF3A3632, TEXT = 0xFFF4EFE6, MUTED = 0xFFA39E95,
            ACCENT = 0xFFD97757, OK = 0xFF5CC08A, WARN = 0xFFE5B84B, USER = 0xFF4A3A2E, ERR = 0xFFE58A7A,
            ON_ACCENT = 0xFF1A1714,   // text on ACCENT
            ACCENT_TEXT = 0xFFD97757, // accent used as text on BG/CARD (darker in light mode)
            NAV = 0xFF1E1B19,         // bottom nav background
            CODE = 0xFF0F0E0D,        // terminal/code block background
            CODE_TEXT = 0xFFF4EFE6,
            LINE = 0x0FF4EFE6,        // dividers
            OUTLINE = 0x1FF4EFE6,     // chip/input borders
            ACCENT_SOFT = 0x29D97757,
            ERR_SOFT = 0x1FE58A7A;
    static boolean DARK = true;

    private static final Map<String, Typeface> FONTS = new HashMap<>();

    private Ui() {
    }

    // ------------------------------------------------------------------ basics

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** The app's language, for changing the case of text the user sees (Turkish i/İ). */
    static java.util.Locale locale(Context c) {
        return c.getResources().getConfiguration().getLocales().get(0);
    }

    /** Geist at a weight (400…700); falls back to the system font. */
    static Typeface sans(Context c, int weight) {
        return font(c, "fonts/Geist.ttf", weight, weight >= 600 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    /** Geist Mono at a weight; falls back to the system monospace. */
    static Typeface mono(Context c, int weight) {
        return font(c, "fonts/GeistMono.ttf", weight, Typeface.MONOSPACE);
    }

    private static Typeface font(Context c, String path, int weight, Typeface fallback) {
        String k = path + weight;
        Typeface t = FONTS.get(k);
        if (t == null) {
            try {
                t = new Typeface.Builder(c.getAssets(), path).setFontVariationSettings("'wght' " + weight).build();
            } catch (Exception e) {
                t = fallback;
            }
            if (t == null) t = fallback;
            FONTS.put(k, t);
        }
        return t;
    }

    static TextView text(Context c, String s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(sans(c, 400));
        return t;
    }

    static TextView title(Context c, String s, int sp) {
        TextView t = text(c, s, sp, TEXT);
        t.setTypeface(sans(c, 600));
        return t;
    }

    static TextView monoText(Context c, String s, int sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(mono(c, 400));
        return t;
    }

    /** The quiet "free · limited" tag beside an agent's name in the pickers (Account.freeTier). */
    static TextView freeTag(Context c) {
        TextView tag = monoText(c, c.getString(R.string.agent_tag_free), 11, MUTED);
        tag.setPaddingRelative(dp(c, 6), dp(c, 1), dp(c, 6), dp(c, 1));
        tag.setBackground(rounded(c, CARD2, 6));
        return tag;
    }

    static TextView single(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static GradientDrawable rounded(Context c, int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable outlined(Context c, int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = rounded(c, fill, radiusDp);
        d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    /** Per-corner radii in dp: top-left, top-right, bottom-right, bottom-left. */
    static GradientDrawable corners(Context c, int color, int tl, int tr, int br, int bl) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float a = dp(c, tl), b = dp(c, tr), e = dp(c, br), f = dp(c, bl);
        d.setCornerRadii(new float[]{a, a, b, b, e, e, f, f});
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(color));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return i;
    }

    static View dot(Context c, int color, int sizeDp) {
        View v = new View(c);
        v.setBackground(oval(color));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        p.gravity = Gravity.CENTER_VERTICAL;
        v.setLayoutParams(p);
        return v;
    }

    static int ripple(Context c, boolean borderless) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(borderless ? android.R.attr.selectableItemBackgroundBorderless
                : android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }

    /** A transparent icon button with a round ripple; 48dp touch target. */
    static ImageButton iconButton(Context c, int icon, String description) {
        return iconButton(c, icon, description, TEXT);
    }

    /** Subtle scale-down on press and snappy spring return on release without blocking clicks. */
    static <T extends View> T pressScale(T v, float downScale) {
        v.setOnTouchListener((view, ev) -> {
            int act = ev.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                view.animate().cancel();
                view.animate().scaleX(downScale).scaleY(downScale)
                        .setDuration(55)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                view.animate().cancel();
                view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(110)
                        .setInterpolator(new OvershootInterpolator(1.0f))
                        .start();
            }
            return false;
        });
        return v;
    }

    static ImageButton iconButton(Context c, int icon, String description, int tint) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(tint));
        b.setContentDescription(description);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackgroundResource(ripple(c, true));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        return pressScale(b, 0.90f);
    }

    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setTextColor(primary ? ON_ACCENT : TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(sans(c, 600));
        b.setPaddingRelative(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        b.setMinHeight(dp(c, 40));
        b.setMinimumHeight(dp(c, 40));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setStateListAnimator(null);
        b.setBackground(rounded(c, primary ? ACCENT : CARD2, 12));
        return pressScale(b, 0.96f);
    }

    static Button outlinedButton(Context c, String label, int color) {
        Button b = button(c, label, false);
        b.setTextColor(color);
        b.setBackground(outlined(c, 0x00000000, 12, Theme.withAlpha(color, 0x80), 1));
        return b;
    }

    static LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weight1() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    // ------------------------------------------------------------- components

    /** 12sp/600 muted ALL CAPS label; padding 20/16/20/4. */
    static TextView sectionLabel(Context c, String s) {
        TextView t = text(c, s.toUpperCase(locale(c)), 12, MUTED);
        t.setTypeface(sans(c, 600));
        t.setLetterSpacing(0.06f);
        t.setPaddingRelative(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 4));
        return t;
    }

    /** Circular NN mascot avatar (drawn NN face, or custom photo picked in Settings). */
    static View nnAvatar(Context c, int sizeDp) {
        FrameLayout av = new FrameLayout(c);
        av.setBackground(oval(ACCENT_SOFT));
        ImageView face = new ImageView(c);
        Face.set(face, R.drawable.bubble_face);
        int nnDp = Math.max(12, Math.round(sizeDp * 0.82f));
        int fs = dp(c, Face.size(c, nnDp, sizeDp));
        av.addView(face, new FrameLayout.LayoutParams(fs, fs, Gravity.CENTER));
        av.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        av.setContentDescription("NN");
        return av;
    }

    /** @deprecated Use {@link #nnAvatar(Context, int)} instead. */
    @Deprecated
    static View buddyAvatar(Context c, int sizeDp) {
        return nnAvatar(c, sizeDp);
    }

    /** 40×40 brand avatar tile for Claude Code, Codex, OpenCode, Antigravity or Grok. */
    static View agentTile(Context c, String backend) {
        return agentTile(c, backend, 40);
    }

    /** Sized brand avatar tile for Claude Code, Codex, OpenCode, Antigravity or Grok. */
    static View agentTile(Context c, String backend, int sizeDp) {
        FrameLayout box = new FrameLayout(c);
        int bg, border, res;
        boolean tintMono = false;
        if ("codex".equals(backend)) {
            bg = 0x2210A37F;
            border = 0x4410A37F;
            res = R.drawable.agent_codex;
        } else if ("opencode".equals(backend)) {
            bg = CARD2;
            border = OUTLINE;
            res = R.drawable.agent_opencode;
            tintMono = true;
        } else if ("agy".equals(backend)) {
            bg = 0x1F4285F4;
            border = 0x444285F4;
            res = R.drawable.agent_antigravity;
        } else if ("grok".equals(backend)) {
            bg = CARD2;
            border = OUTLINE;
            res = R.drawable.agent_grok;
            tintMono = true;
        } else {
            bg = 0x26D97757;
            border = 0x44D97757;
            res = R.drawable.agent_claude;
        }
        int radius = sizeDp >= 48 ? 16 : sizeDp >= 34 ? 12 : Math.max(6, sizeDp / 3);
        box.setBackground(outlined(c, bg, radius, border, 1));
        int pad = dp(c, Math.max(4, sizeDp / 5));
        box.setPaddingRelative(pad, pad, pad, pad);
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tintMono) {
            iv.setImageTintList(ColorStateList.valueOf(TEXT));
        }
        box.addView(iv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        box.setContentDescription(Account.nameOf(backend));
        return box;
    }

    /** Outlined mono chip with a trailing expand icon. */
    static LinearLayout chip(Context c, String s) {
        LinearLayout l = row(c);
        l.setPaddingRelative(dp(c, 8), dp(c, 3), dp(c, 4), dp(c, 3));
        l.setBackground(outlined(c, 0x00000000, 8, OUTLINE, 1));
        TextView t = single(monoText(c, s, 12, MUTED));
        l.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1)); // a chip short of room shortens its text, not the icon
        ImageView i = icon(c, R.drawable.ms_expand_more, MUTED, 16);
        l.addView(i);
        return l;
    }

    private static final int TAG_CHIP_ON = 0x4E4E0001;
    private static final int TAG_CHIP_ANIM = 0x4E4E0002;

    /** The "Linux tools" chip beside a chat's model chip: a terminal icon and the name (see paintLinuxChip). */
    static LinearLayout linuxChip(Context c) {
        LinearLayout l = row(c);
        l.setPaddingRelative(dp(c, 6), dp(c, 3), dp(c, 8), dp(c, 3));
        l.addView(icon(c, R.drawable.ms_terminal, MUTED, 14));
        TextView t = single(monoText(c, c.getString(R.string.chat_linux_tools), 12, MUTED));
        t.setPaddingRelative(dp(c, 4), 0, 0, 0);
        l.addView(t);
        return pressScale(l, 0.95f);
    }

    /** On: accent, like the agent pickers' "default" tag; off: outlined and muted, like the model chip. */
    static void paintLinuxChip(Context c, LinearLayout chip, boolean on) {
        Boolean prevObj = (Boolean) chip.getTag(TAG_CHIP_ON);
        chip.setTag(TAG_CHIP_ON, on);
        chip.setSelected(on); // for TalkBack: on reads as selected
        ImageView iv = (ImageView) chip.getChildAt(0);
        TextView tv = (TextView) chip.getChildAt(1);
        if (prevObj == null || prevObj == on) {
            int color = on ? ACCENT_TEXT : MUTED;
            iv.setImageTintList(ColorStateList.valueOf(color));
            tv.setTextColor(color);
            chip.setBackground(on ? rounded(c, ACCENT_SOFT, 8) : outlined(c, 0x00000000, 8, OUTLINE, 1));
            return;
        }
        Object prevAnim = chip.getTag(TAG_CHIP_ANIM);
        if (prevAnim instanceof ValueAnimator) ((ValueAnimator) prevAnim).cancel();
        boolean fromOn = prevObj;
        int cFrom = fromOn ? ACCENT_TEXT : MUTED, cTo = on ? ACCENT_TEXT : MUTED;
        int bgFrom = fromOn ? ACCENT_SOFT : 0x00000000, bgTo = on ? ACCENT_SOFT : 0x00000000;
        int stFrom = fromOn ? 0x00000000 : OUTLINE, stTo = on ? 0x00000000 : OUTLINE;
        int strokeW = dp(c, 1);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 8));
        chip.setBackground(bg);
        ArgbEvaluator eval = new ArgbEvaluator();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(130);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            int tc = (Integer) eval.evaluate(f, cFrom, cTo);
            iv.setImageTintList(ColorStateList.valueOf(tc));
            tv.setTextColor(tc);
            bg.setColor((Integer) eval.evaluate(f, bgFrom, bgTo));
            bg.setStroke(strokeW, (Integer) eval.evaluate(f, stFrom, stTo));
        });
        va.start();
        chip.setTag(TAG_CHIP_ANIM, va);
        chip.animate().cancel();
        chip.animate().scaleX(1.03f).scaleY(1.03f).setDuration(60)
                .withEndAction(() -> chip.animate().scaleX(1f).scaleY(1f)
                        .setDuration(90).setInterpolator(new DecelerateInterpolator()).start())
                .start();
    }

    /** The Execution Mode (Hỏi trước / Tự động) chip beside Linux tools chip in composer dock. */
    static LinearLayout execChip(Context c) {
        LinearLayout l = row(c);
        l.setPaddingRelative(dp(c, 6), dp(c, 3), dp(c, 8), dp(c, 3));
        l.addView(icon(c, R.drawable.ms_shield_person, MUTED, 14));
        TextView t = single(monoText(c, c.getString(R.string.chat_exec_ask), 12, MUTED));
        t.setPaddingRelative(dp(c, 4), 0, 0, 0);
        l.addView(t);
        return pressScale(l, 0.95f);
    }

    /** Paints the execution mode chip: off = Muted outline (Ask first); on = Amber/Gold tint (Auto execution). */
    static void paintExecChip(Context c, LinearLayout chip, boolean autoExec) {
        ImageView iv = (ImageView) chip.getChildAt(0);
        TextView tv = (TextView) chip.getChildAt(1);
        if (!autoExec) {
            iv.setImageResource(R.drawable.ms_shield_person);
            iv.setImageTintList(ColorStateList.valueOf(MUTED));
            tv.setText(c.getString(R.string.chat_exec_ask));
            tv.setTextColor(MUTED);
            chip.setBackground(outlined(c, 0x00000000, 8, OUTLINE, 1));
        } else {
            iv.setImageResource(R.drawable.ms_bolt);
            int gold = 0xFFE5C07B;
            iv.setImageTintList(ColorStateList.valueOf(gold));
            tv.setText(c.getString(R.string.chat_exec_auto));
            tv.setTextColor(gold);
            chip.setBackground(rounded(c, 0x2AE5C07B, 8));
        }
    }

    /** Shows the Codex-style execution mode selection bottom sheet. */
    static void showExecModeSheet(Activity a, Sessions.Session ses, Runnable onStateChanged) {
        LinearLayout c = column(a);
        c.setPaddingRelative(dp(a, 16), dp(a, 6), dp(a, 16), dp(a, 20));

        TextView h = title(a, a.getString(R.string.chat_exec_sheet_title), 18);
        h.setPaddingRelative(0, 0, 0, dp(a, 4));
        c.addView(h);

        TextView sub = text(a, a.getString(R.string.chat_exec_sheet_desc), 13, MUTED);
        sub.setPaddingRelative(0, 0, 0, dp(a, 16));
        c.addView(sub);

        final Dialog[] d = new Dialog[1];

        // Card 1: Ask First (Default)
        LinearLayout cardAsk = row(a);
        cardAsk.setPaddingRelative(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        cardAsk.setBackground(outlined(a, !ses.autoExec ? 0x14FFFFFF : 0x00000000, 14, !ses.autoExec ? ACCENT : OUTLINE, 1));
        FrameLayout icAsk = new FrameLayout(a);
        icAsk.setBackground(rounded(a, CARD2, 12));
        icAsk.addView(icon(a, R.drawable.ms_shield_person, !ses.autoExec ? ACCENT_TEXT : MUTED, 22),
                new FrameLayout.LayoutParams(dp(a, 22), dp(a, 22), Gravity.CENTER));
        cardAsk.addView(icAsk, new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));

        LinearLayout tAsk = column(a);
        tAsk.setPaddingRelative(dp(a, 12), 0, dp(a, 8), 0);
        tAsk.addView(title(a, a.getString(R.string.chat_exec_mode_ask_title), 15));
        tAsk.addView(text(a, a.getString(R.string.chat_exec_mode_ask_desc), 13, MUTED));
        cardAsk.addView(tAsk, weight1());

        if (!ses.autoExec) {
            cardAsk.addView(icon(a, R.drawable.ms_check, ACCENT_TEXT, 20),
                    new LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)));
        }
        cardAsk.setOnClickListener(v -> {
            if (ses.autoExec) {
                ses.autoExec = false;
                if (onStateChanged != null) onStateChanged.run();
            }
            if (d[0] != null) d[0].dismiss();
        });
        c.addView(cardAsk, full());

        // Gap
        View gap = new View(a);
        c.addView(gap, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(a, 10)));

        // Card 2: Full Access (Auto-run)
        LinearLayout cardAuto = row(a);
        cardAuto.setPaddingRelative(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        cardAuto.setBackground(outlined(a, ses.autoExec ? 0x22E5C07B : 0x00000000, 14, ses.autoExec ? 0xFFE5C07B : OUTLINE, 1));
        FrameLayout icAuto = new FrameLayout(a);
        icAuto.setBackground(rounded(a, CARD2, 12));
        icAuto.addView(icon(a, R.drawable.ms_bolt, ses.autoExec ? 0xFFE5C07B : MUTED, 22),
                new FrameLayout.LayoutParams(dp(a, 22), dp(a, 22), Gravity.CENTER));
        cardAuto.addView(icAuto, new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));

        LinearLayout tAuto = column(a);
        tAuto.setPaddingRelative(dp(a, 12), 0, dp(a, 8), 0);
        tAuto.addView(title(a, a.getString(R.string.chat_exec_mode_auto_title), 15));
        tAuto.addView(text(a, a.getString(R.string.chat_exec_mode_auto_desc), 13, MUTED));
        cardAuto.addView(tAuto, weight1());

        if (ses.autoExec) {
            cardAuto.addView(icon(a, R.drawable.ms_check, 0xFFE5C07B, 20),
                    new LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)));
        }
        cardAuto.setOnClickListener(v -> {
            if (ses.autoExec) {
                if (d[0] != null) d[0].dismiss();
                return;
            }
            if (d[0] != null) d[0].dismiss();
            showFullAccessWarningDialog(a, () -> {
                ses.autoExec = true;
                if (onStateChanged != null) onStateChanged.run();
            });
        });
        c.addView(cardAuto, full());

        d[0] = bottomSheet(a, c);
        d[0].show();
    }

    /** Safety warning dialog before enabling Full Access execution mode. */
    static void showFullAccessWarningDialog(Activity a, Runnable onConfirm) {
        LinearLayout c = column(a);
        c.setPaddingRelative(dp(a, 20), dp(a, 8), dp(a, 20), dp(a, 24));

        LinearLayout top = row(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(icon(a, R.drawable.ms_warning, 0xFFE5C07B, 24));
        TextView h = title(a, a.getString(R.string.chat_exec_warn_title), 17);
        h.setPaddingRelative(dp(a, 10), 0, 0, 0);
        top.addView(h, weight1());
        c.addView(top, full());

        TextView msg = text(a, a.getString(R.string.chat_exec_warn_msg), 14, TEXT);
        msg.setPaddingRelative(0, dp(a, 14), 0, dp(a, 20));
        msg.setLineSpacing(dp(a, 2), 1.25f);
        c.addView(msg, full());

        final Dialog[] d = new Dialog[1];
        LinearLayout btns = row(a);
        btns.setGravity(Gravity.END);

        Button cancelBtn = button(a, a.getString(R.string.chat_exec_warn_cancel), false);
        cancelBtn.setBackground(outlined(a, 0x00000000, 10, OUTLINE, 1));
        cancelBtn.setTextColor(MUTED);
        cancelBtn.setOnClickListener(v -> {
            if (d[0] != null) d[0].dismiss();
        });
        btns.addView(cancelBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(a, 40)));

        Button enableBtn = button(a, a.getString(R.string.chat_exec_warn_enable), true);
        enableBtn.setBackground(rounded(a, 0xFFE5C07B, 10));
        enableBtn.setTextColor(0xFF000000);
        enableBtn.setTypeface(sans(a, 600));
        LinearLayout.LayoutParams enP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(a, 40));
        enP.setMarginStart(dp(a, 10));
        enableBtn.setOnClickListener(v -> {
            if (d[0] != null) d[0].dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        btns.addView(enableBtn, enP);

        c.addView(btns, full());

        d[0] = bottomSheet(a, c);
        d[0].show();
    }

    /** A red note with a warning icon (what could go wrong with a setting). */
    static LinearLayout warning(Context c, String s) {
        LinearLayout w = row(c);
        w.setGravity(Gravity.TOP);
        w.setPaddingRelative(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        w.setBackground(rounded(c, ERR_SOFT, 12));
        w.addView(icon(c, R.drawable.ms_warning, ERR, 18));
        TextView t = text(c, s, 13, ERR);
        t.setPaddingRelative(dp(c, 10), 0, 0, 0);
        w.addView(t, weight1());
        return w;
    }

    /** A plain flat row with a ripple (for list rows). */
    static LinearLayout listRow(Context c) {
        LinearLayout r = row(c);
        r.setPaddingRelative(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        r.setBackgroundResource(ripple(c, false));
        r.setGravity(Gravity.TOP);
        return r;
    }

    /** Rounded surface card with subtle 1dp hairline border and clipped ripple when clickable. */
    static LinearLayout surfaceCard(Context c, int orientation, int strokeColor, boolean clickable) {
        LinearLayout box = orientation == LinearLayout.HORIZONTAL ? row(c) : column(c);
        box.setBackground(outlined(c, CARD, 16, strokeColor, 1));
        box.setClipToOutline(true);
        if (clickable) {
            box.setForeground(c.getDrawable(ripple(c, false)));
            pressScale(box, 0.985f);
        }
        return box;
    }

    /** Compact pill badge (e.g. model name, live refresh status). */
    static LinearLayout pillBadge(Context c, int fill, int strokeColor) {
        LinearLayout p = row(c);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPaddingRelative(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        p.setBackground(strokeColor != 0 ? outlined(c, fill, 999, strokeColor, 1) : rounded(c, fill, 999));
        return p;
    }

    /** Empty-state card with a soft circular icon badge and muted message. */
    static LinearLayout emptyCard(Context c, int iconRes, String message) {
        LinearLayout card = surfaceCard(c, LinearLayout.HORIZONTAL, LINE, false);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPaddingRelative(dp(c, 14), dp(c, 14), dp(c, 16), dp(c, 14));
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(oval(CARD2));
        badge.addView(icon(c, iconRes, MUTED, 18),
                new FrameLayout.LayoutParams(dp(c, 18), dp(c, 18), Gravity.CENTER));
        card.addView(badge, new LinearLayout.LayoutParams(dp(c, 36), dp(c, 36)));
        TextView tv = text(c, message, 14, MUTED);
        tv.setPaddingRelative(dp(c, 12), 0, 0, 0);
        card.addView(tv, weight1());
        return card;
    }

    interface Toggle {
        void onToggle(boolean on);
    }

    /** Title + subtitle with a switch on the right. */
    /** Accessibility extra that marks a view (and everything inside it) as off-limits to agents. */
    static final String PROTECTED = "app.nn.protected";

    /**
     * Agents may never tap or type in this view, even when "Let agents tap NN's screens" is on (see Guard).
     * Invisible to the user and to screen readers: it's only an extra on the accessibility node.
     */
    static <T extends View> T protect(T v) {
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.getExtras().putBoolean(PROTECTED, true);
            }
        });
        return v;
    }

    static LinearLayout switchRow(Context c, String title, String sub, boolean on, Toggle l) {
        LinearLayout r = row(c);
        r.setPaddingRelative(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        r.setBackgroundResource(ripple(c, false));
        LinearLayout texts = column(c);
        texts.addView(title(c, title, 15));
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(c, sub, 13, MUTED);
            s.setPaddingRelative(0, dp(c, 2), dp(c, 12), 0);
            texts.addView(s);
        }
        r.addView(texts, weight1());
        final FrameLayout track = new FrameLayout(c);
        final View knob = new View(c);
        final boolean[] state = {on};
        final float[] progress = {on ? 1f : 0f};
        final ValueAnimator[] anim = {null};
        final int travel = dp(c, 20);
        final ArgbEvaluator eval = new ArgbEvaluator();
        final GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dp(c, 14));
        track.setBackground(trackBg);
        final GradientDrawable knobBg = new GradientDrawable();
        knobBg.setShape(GradientDrawable.OVAL);
        knob.setBackground(knobBg);
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(dp(c, 20), dp(c, 20),
                Gravity.CENTER_VERTICAL | Gravity.START);
        kp.setMarginStart(dp(c, 4));
        track.addView(knob, kp);

        final Runnable applyFrame = () -> {
            float p = progress[0];
            float cp = Math.max(0f, Math.min(1f, p));
            trackBg.setColor((Integer) eval.evaluate(cp, CARD2, ACCENT));
            knobBg.setColor((Integer) eval.evaluate(cp, MUTED, ON_ACCENT));
            boolean rtl = r.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            knob.setTranslationX((rtl ? -travel : travel) * p);
            float mid = (float) Math.sin(Math.PI * cp);
            knob.setScaleX(1f + 0.16f * mid);
            knob.setScaleY(1f - 0.06f * mid);
        };
        applyFrame.run();
        r.addView(track, new LinearLayout.LayoutParams(dp(c, 48), dp(c, 28)));
        r.setOnClickListener(v -> {
            state[0] = !state[0];
            if (anim[0] != null) anim[0].cancel();
            ValueAnimator va = ValueAnimator.ofFloat(progress[0], state[0] ? 1f : 0f);
            va.setDuration(140);
            va.setInterpolator(new OvershootInterpolator(0.75f));
            va.addUpdateListener(a -> {
                progress[0] = (float) a.getAnimatedValue();
                applyFrame.run();
            });
            va.start();
            anim[0] = va;
            l.onToggle(state[0]);
        });
        return r;
    }

    interface Pick {
        void onPick(String value);
    }

    /** Segmented control: options are {value, label}. */
    static LinearLayout segmented(Context c, String[][] options, String selected, boolean monoLabels, Pick l) {
        return segmented(c, options, selected, monoLabels, l, null, null);
    }

    /**
     * Like {@link #segmented(Context, String[][], String, boolean, Pick)}, but the options in {@code disabled} are
     * dimmed and can't be picked: tapping one calls {@code whyNot} (e.g. to say what to set up first).
     */
    static LinearLayout segmented(Context c, String[][] options, String selected, boolean monoLabels, Pick l,
                                  java.util.Set<String> disabled, Pick whyNot) {
        LinearLayout box = row(c);
        box.setPaddingRelative(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        box.setBackground(rounded(c, BG, 12));
        for (String[] o : options) {
            boolean on = o[0].equals(selected);
            TextView t = text(c, o[1], 14, on ? TEXT : MUTED);
            t.setTypeface(monoLabels ? mono(c, on ? 600 : 400) : sans(c, on ? 600 : 500));
            t.setGravity(Gravity.CENTER);
            int side = dp(c, options.length > 4 ? 4 : 10);
            t.setPaddingRelative(side, dp(c, 8), side, dp(c, 8));
            // one line; a label too long for its segment (often a translation) shrinks instead of wrapping
            t.setMaxLines(1);
            t.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            if (on) t.setBackground(rounded(c, CARD2, 10));
            else t.setBackgroundResource(ripple(c, false));
            boolean off = !on && disabled != null && disabled.contains(o[0]);
            if (off) t.setAlpha(0.38f);
            pressScale(t, 0.96f);
            t.setOnClickListener(v -> {
                if (off) {
                    if (whyNot != null) whyNot.onPick(o[0]);
                } else if (!on) {
                    for (int ci = 0; ci < box.getChildCount(); ci++) {
                        View child = box.getChildAt(ci);
                        if (child instanceof TextView) {
                            TextView ct = (TextView) child;
                            boolean sel = ct == t;
                            ct.setBackground(sel ? rounded(c, CARD2, 10) : null);
                            if (!sel) ct.setBackgroundResource(ripple(c, false));
                            ct.setTextColor(sel ? TEXT : MUTED);
                            ct.setTypeface(monoLabels ? mono(c, sel ? 600 : 400) : sans(c, sel ? 600 : 500));
                        }
                    }
                    t.animate().scaleX(1.02f).scaleY(1.02f).setDuration(55)
                            .withEndAction(() -> t.animate().scaleX(1f).scaleY(1f)
                                    .setDuration(85).setInterpolator(new DecelerateInterpolator()).start())
                            .start();
                    l.onPick(o[0]);
                }
            });
            box.addView(t, weight1());
        }
        return box;
    }

    /** A radio button's look (for lists of choices in sheets). */
    static View radio(Context c, boolean on) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(outlined(c, 0x00000000, 11, on ? ACCENT : MUTED, 2));
        if (on) {
            View in = new View(c);
            in.setBackground(oval(ACCENT));
            f.addView(in, new FrameLayout.LayoutParams(dp(c, 10), dp(c, 10), Gravity.CENTER));
        }
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22)));
        return f;
    }

    interface ModelPickListener {
        void onPickModel(String model);
        void onPickEffort(String effort);
    }

    /** Pick a model for an agent (no synthetic "Default" row); delegates to the modern card/search picker. */
    static void modelSheet(Activity a, String backend, String current, String defaultHint, Pick l) {
        modelSheet(a, backend, current, defaultHint, true, l);
    }

    /** services: also the API key services' models (not for runs outside a chat, like the reviewer's). */
    static void modelSheet(Activity a, String backend, String current, String defaultHint, boolean services, Pick l) {
        String activeModel = (current == null || current.isEmpty()) ? Models.agentDefault(backend) : current;
        modelEffortSheet(a, backend, activeModel, "", null, false, false, services, new ModelPickListener() {
            @Override
            public void onPickModel(String model) {
                l.onPick(model);
            }

            @Override
            public void onPickEffort(String effort) {
            }
        });
    }

    /**
     * Unified modern Model & Effort bottom sheet matching the agent brand design language:
     * sticky header with Ui.agentTile (swipe handle or tap outside to close), compact single-row effort
     * selector across all 5 agents, search when >8 models, grouped flat card list with checkmark only on active
     * model, and collapsible custom model ID input (no boxy noise or permanent bottom form clutter).
     */
    static void modelEffortSheet(Activity a, String backend, String currentModel, String currentEffort,
                                 Sessions.Session ses, boolean showEffort, boolean saveDefault,
                                 boolean services, ModelPickListener listener) {
        Models.refresh(a);
        final String[] selModel = {(currentModel == null || currentModel.isEmpty())
                ? Models.agentDefault(backend) : currentModel};
        final String[] selEffort = {currentEffort == null ? "" : currentEffort};
        final Dialog[] d = new Dialog[1];

        // 1. Sticky Header (swipe down on handle/header or tap outside dim to dismiss)
        LinearLayout header = row(a);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(0, 0, 0, dp(a, 14));
        header.addView(agentTile(a, backend, 40));
        LinearLayout titleCol = column(a);
        titleCol.setPaddingRelative(dp(a, 12), 0, 0, 0);
        titleCol.addView(single(title(a, a.getString(R.string.chat_model_title, Account.nameOf(backend)), 18)));
        final TextView sub = single(monoText(a, "", 12, ACCENT_TEXT));
        titleCol.addView(sub);
        header.addView(titleCol, weight1());

        final Runnable updateSub = () -> {
            String ml = Models.label(backend, selModel[0]);
            boolean hasEff = showEffort && Models.supportsEffort(backend, selModel[0]);
            if (hasEff && !selEffort[0].isEmpty()) {
                sub.setText(ml + " · " + Models.effortLabel(selEffort[0]));
            } else {
                sub.setText(ml);
            }
        };
        updateSub.run();

        // 2. Scrollable Middle Content
        LinearLayout c = column(a);
        int ctx = ses != null ? Models.contextPercent(ses) : -1;
        if (ctx >= 0) {
            LinearLayout ctxRow = row(a);
            ctxRow.setGravity(Gravity.CENTER_VERTICAL);
            ctxRow.setPaddingRelative(dp(a, 12), dp(a, 10), dp(a, 10), dp(a, 10));
            ctxRow.setBackground(rounded(a, BG, 12));
            LinearLayout ct = column(a);
            ct.addView(text(a, a.getString(R.string.chat_context_used, Models.tokens(ses.ctxUsed),
                    Models.tokens(ses.ctxWindow), ctx), 13, TEXT));
            ct.addView(text(a, a.getString(R.string.chat_context_help, ses.brainName()), 11, MUTED));
            ctxRow.addView(ct, weight1());
            if ("claude".equals(backend) && !ses.running) {
                Button compact = button(a, a.getString(R.string.chat_context_compact), false);
                compact.setMinHeight(0);
                compact.setMinimumHeight(0);
                compact.setOnClickListener(v -> {
                    d[0].dismiss();
                    String err = ChatHub.get(a).send(ses, "/compact");
                    if (err != null) android.widget.Toast.makeText(a, err, android.widget.Toast.LENGTH_LONG).show();
                });
                LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dp(a, 36));
                cbp.setMarginStart(dp(a, 8));
                ctxRow.addView(compact, cbp);
            }
            LinearLayout.LayoutParams ccp = full();
            ccp.bottomMargin = dp(a, 12);
            c.addView(ctxRow, ccp);
        }

        final LinearLayout effortWrap = column(a);
        final FrameLayout effortSlot = new FrameLayout(a);
        final TextView effortBadge = monoText(a, Models.effortLabel(selEffort[0]), 12, ACCENT_TEXT);
        if (showEffort) {
            LinearLayout eTop = row(a);
            eTop.setGravity(Gravity.CENTER_VERTICAL);
            eTop.setPaddingRelative(dp(a, 2), 0, dp(a, 2), dp(a, 6));
            eTop.addView(sectionLabelInline(a, a.getString(R.string.chat_effort)), weight1());
            eTop.addView(effortBadge);
            effortWrap.addView(eTop, full());

            LinearLayout.LayoutParams esp = full();
            esp.bottomMargin = dp(a, 14);
            effortWrap.addView(effortSlot, esp);
            c.addView(effortWrap, full());
        }

        final Runnable[] renderModels = new Runnable[1];
        if (showEffort) {
            final Runnable[] drawSeg = new Runnable[1];
            drawSeg[0] = () -> {
                effortSlot.removeAllViews();
                String[][] efforts = Models.efforts(backend, selModel[0]);
                if (efforts == null) {
                    effortWrap.setVisibility(View.GONE);
                    return;
                }
                effortWrap.setVisibility(View.VISIBLE);
                effortBadge.setText(Models.effortLabel(selEffort[0]));
                effortSlot.addView(segmented(a, efforts, selEffort[0], false, v -> {
                    selEffort[0] = v;
                    updateSub.run();
                    if (saveDefault) Prefs.setAgentEffort(a, backend, v);
                    listener.onPickEffort(v);
                    drawSeg[0].run();
                }), full());
            };
            drawSeg[0].run();
        }

        final java.util.List<String[]> opts = Models.options(backend, services);
        int realCount = 0;
        for (String[] o : opts) if (o[0] != null) realCount++;

        final String[] query = {""};
        if (realCount > 8) {
            LinearLayout searchBox = row(a);
            searchBox.setGravity(Gravity.CENTER_VERTICAL);
            searchBox.setPaddingRelative(dp(a, 12), dp(a, 4), dp(a, 8), dp(a, 4));
            searchBox.setBackground(rounded(a, BG, 12));
            final android.widget.EditText se = new android.widget.EditText(a);
            se.setHint(R.string.models_search_hint);
            se.setHintTextColor(MUTED);
            se.setTextColor(TEXT);
            se.setTextSize(14);
            se.setTypeface(sans(a, 400));
            se.setSingleLine(true);
            se.setBackground(null);
            se.setPaddingRelative(dp(a, 2), dp(a, 6), dp(a, 6), dp(a, 6));
            searchBox.addView(se, weight1());
            final ImageButton clearSearch = iconButton(a, R.drawable.ms_close, a.getString(R.string.bubble_close), MUTED);
            clearSearch.setLayoutParams(new LinearLayout.LayoutParams(dp(a, 32), dp(a, 32)));
            clearSearch.setVisibility(View.GONE);
            clearSearch.setOnClickListener(v -> se.setText(""));
            searchBox.addView(clearSearch);
            se.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    query[0] = s != null ? s.toString().trim().toLowerCase(java.util.Locale.ROOT) : "";
                    clearSearch.setVisibility(query[0].isEmpty() ? View.GONE : View.VISIBLE);
                    if (renderModels[0] != null) renderModels[0].run();
                }
            });
            LinearLayout.LayoutParams sbp = full();
            sbp.bottomMargin = dp(a, 12);
            c.addView(searchBox, sbp);
        }

        final LinearLayout listBox = column(a);
        c.addView(listBox, full());

        final boolean[] known = {false};
        renderModels[0] = () -> {
            listBox.removeAllViews();
            known[0] = false;
            String q = query[0];
            String pendingHeader = null;
            LinearLayout group = null;
            int shownRows = 0;

            for (final String[] o : opts) {
                if (o[0] == null) {
                    pendingHeader = o[1];
                    group = null;
                    continue;
                }
                if (!q.isEmpty()) {
                    String nLow = o[1].toLowerCase(java.util.Locale.ROOT);
                    String idLow = o[0].toLowerCase(java.util.Locale.ROOT);
                    if (!nLow.contains(q) && !idLow.contains(q)) continue;
                }
                if (pendingHeader != null) {
                    TextView sl = sectionLabel(a, pendingHeader);
                    sl.setPaddingRelative(dp(a, 2), dp(a, 12), 0, dp(a, 6));
                    listBox.addView(sl);
                    pendingHeader = null;
                    group = null;
                }
                if (group == null) {
                    group = column(a);
                    group.setPaddingRelative(dp(a, 6), dp(a, 6), dp(a, 6), dp(a, 6));
                    group.setBackground(rounded(a, BG, 16));
                    LinearLayout.LayoutParams gp = full();
                    gp.bottomMargin = dp(a, 8);
                    listBox.addView(group, gp);
                } else {
                    View line = new View(a);
                    line.setBackgroundColor(LINE);
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(a, 1));
                    dlp.setMargins(dp(a, 10), dp(a, 2), dp(a, 10), dp(a, 2));
                    group.addView(line, dlp);
                }

                boolean on = o[0].equals(selModel[0]);
                known[0] |= on;

                LinearLayout row = row(a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPaddingRelative(dp(a, 12), dp(a, 11), dp(a, 12), dp(a, 11));
                row.setBackground(on ? rounded(a, ACCENT_SOFT, 12) : null);
                row.setForeground(a.getDrawable(ripple(a, false)));

                LinearLayout t = column(a);
                t.setPaddingRelative(0, 0, dp(a, 10), 0);
                TextView titleView = single(title(a, o[1], 15));
                if (on) titleView.setTextColor(ACCENT_TEXT);
                t.addView(titleView);
                String detail = Models.detail(backend, o[0], "");
                if (!detail.isEmpty()) t.addView(single(monoText(a, detail, 12, MUTED)));
                row.addView(t, weight1());
                if (on) row.addView(icon(a, R.drawable.ms_check, ACCENT_TEXT, 18));

                row.setOnClickListener(v -> {
                    selModel[0] = o[0];
                    if (ses != null && !Models.supportsEffort(backend, o[0])) ses.effort = "";
                    if (saveDefault) Prefs.setAgentDefault(a, backend, o[0]);
                    listener.onPickModel(o[0]);
                    d[0].dismiss();
                });
                group.addView(row, full());
                shownRows++;
            }
            if (shownRows == 0 && !q.isEmpty()) {
                TextView empty = text(a, a.getString(R.string.models_empty_search, query[0]), 14, MUTED);
                empty.setGravity(Gravity.CENTER);
                empty.setPaddingRelative(dp(a, 12), dp(a, 16), dp(a, 12), dp(a, 12));
                listBox.addView(empty, full());
            }
        };
        renderModels[0].run();

        // Subtle collapsible custom model ID link (no permanent dev form clutter)
        String sampleHint = a.getString(R.string.chat_model_other_hint, "claude".equals(backend) ? "claude-opus-5-5"
                : "opencode".equals(backend) ? "anthropic/claude-sonnet-5-5" : "agy".equals(backend) ? "gemini-3.8-flash-high"
                : "grok".equals(backend) ? "grok-4.7" : "gpt-6.1-sol");
        final boolean showCustomInit = !known[0] && !selModel[0].isEmpty();
        final LinearLayout customToggle = row(a);
        customToggle.setGravity(Gravity.CENTER_VERTICAL);
        customToggle.setPaddingRelative(dp(a, 8), dp(a, 10), dp(a, 8), dp(a, 6));
        customToggle.setBackgroundResource(ripple(a, false));
        customToggle.addView(icon(a, R.drawable.ms_code, MUTED, 18));
        TextView ctLabel = text(a, sampleHint, 13, MUTED);
        ctLabel.setPaddingRelative(dp(a, 10), 0, 0, 0);
        customToggle.addView(ctLabel, weight1());
        customToggle.setVisibility(showCustomInit ? View.GONE : View.VISIBLE);
        c.addView(customToggle, full());

        final LinearLayout other = row(a);
        other.setGravity(Gravity.CENTER_VERTICAL);
        other.setPaddingRelative(0, dp(a, 6), 0, dp(a, 4));
        final android.widget.EditText et = new android.widget.EditText(a);
        et.setHint(sampleHint);
        et.setHintTextColor(MUTED);
        et.setTextColor(TEXT);
        et.setTextSize(14);
        et.setTypeface(mono(a, 400));
        et.setSingleLine(true);
        if (showCustomInit) et.setText(selModel[0]);
        et.setPaddingRelative(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        et.setBackground(rounded(a, BG, 12));
        other.addView(et, weight1());
        Button use = button(a, a.getString(R.string.chat_model_use), true);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(a, 42));
        up.setMarginStart(dp(a, 8));
        other.addView(use, up);
        other.setVisibility(showCustomInit ? View.VISIBLE : View.GONE);
        c.addView(other, full());

        customToggle.setOnClickListener(v -> {
            customToggle.setVisibility(View.GONE);
            other.setVisibility(View.VISIBLE);
            et.requestFocus();
            android.view.inputmethod.InputMethodManager imm = a.getSystemService(
                    android.view.inputmethod.InputMethodManager.class);
            if (imm != null) imm.showSoftInput(et, 0);
        });
        use.setOnClickListener(v -> {
            String custom = et.getText().toString().trim();
            if (custom.isEmpty()) return;
            if (ses != null && !Models.supportsEffort(backend, custom)) ses.effort = "";
            if (saveDefault) Prefs.setAgentDefault(a, backend, custom);
            listener.onPickModel(custom);
            d[0].dismiss();
        });

        d[0] = bottomSheet(a, header, c, null);
        d[0].show();
    }

    private static TextView sectionLabelInline(Context c, String s) {
        TextView t = monoText(c, s.toUpperCase(locale(c)), 11, MUTED);
        t.setLetterSpacing(0.1f);
        return t;
    }

    private static View checkBadge(Context c, boolean on) {
        FrameLayout f = new FrameLayout(c);
        if (on) {
            f.setBackground(oval(ACCENT));
            ImageView iv = icon(c, R.drawable.ms_check, ON_ACCENT, 13);
            f.addView(iv, new FrameLayout.LayoutParams(dp(c, 13), dp(c, 13), Gravity.CENTER));
        } else {
            f.setBackground(outlined(c, 0x00000000, 10, MUTED, 2));
        }
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)));
        return f;
    }

    /** Terminal-style block with an accent "$ " prompt. */
    static TextView codeBlock(Context c, String cmd) {
        TextView t = monoText(c, "", 12, CODE_TEXT);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("$ " + cmd);
        sb.setSpan(new android.text.style.ForegroundColorSpan(ACCENT), 0, 2, 0);
        t.setText(sb);
        t.setLineSpacing(0, 1.4f);
        t.setPaddingRelative(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        t.setBackground(rounded(c, CODE, 12));
        return t;
    }

    static ProgressBar thinBar(Context c) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        p.setIndeterminate(true);
        p.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        p.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 2)));
        return p;
    }

    /**
     * Make web links and email addresses in t tappable, opened with a new task (for the bubble, which isn't an
     * activity). Then runs before each one opens (e.g. to fold the panel).
     */
    static void linkifyForService(TextView t, Runnable then) {
        android.text.SpannableString s = new android.text.SpannableString(t.getText());
        boolean found = android.text.util.Linkify.addLinks(s, android.text.util.Linkify.WEB_URLS | android.text.util.Linkify.EMAIL_ADDRESSES);
        for (android.text.style.URLSpan u : s.getSpans(0, s.length(), android.text.style.URLSpan.class)) {
            int a = s.getSpanStart(u), b = s.getSpanEnd(u);
            s.removeSpan(u);
            s.setSpan(new LinkSpan(u.getURL(), then), a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // markdown links (see markdown) are LinkSpans already
        if (!found && s.getSpans(0, s.length(), LinkSpan.class).length == 0) return;
        t.setText(s);
        t.setLinkTextColor(ACCENT_TEXT);
        t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }

    /**
     * Makes both Markdown [label](url) LinkSpans and bare addresses tappable inside a selectable TextView
     * without breaking text selection or Linkify clobbering existing LinkSpans.
     */
    static void linkifySelectable(final TextView tv) {
        CharSequence cur = tv.getText();
        android.text.SpannableStringBuilder s = cur instanceof android.text.SpannableStringBuilder
                ? (android.text.SpannableStringBuilder) cur : new android.text.SpannableStringBuilder(cur);
        android.text.style.ClickableSpan[] existing = s.getSpans(0, s.length(), android.text.style.ClickableSpan.class);
        android.text.SpannableStringBuilder copy = new android.text.SpannableStringBuilder(s.toString());
        if (android.text.util.Linkify.addLinks(copy, android.text.util.Linkify.WEB_URLS | android.text.util.Linkify.EMAIL_ADDRESSES)) {
            for (android.text.style.URLSpan u : copy.getSpans(0, copy.length(), android.text.style.URLSpan.class)) {
                int a = copy.getSpanStart(u), b = copy.getSpanEnd(u);
                boolean overlaps = false;
                for (android.text.style.ClickableSpan cs : existing) {
                    int ea = s.getSpanStart(cs), eb = s.getSpanEnd(cs);
                    if (a < eb && b > ea) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    s.setSpan(new LinkSpan(u.getURL(), null), a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        tv.setText(s, TextView.BufferType.SPANNABLE);
        tv.setLinkTextColor(ACCENT_TEXT);
        tv.setOnTouchListener(new android.view.View.OnTouchListener() {
            private float downX, downY;
            private long downAt;

            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    downAt = android.os.SystemClock.uptimeMillis();
                } else if (action == android.view.MotionEvent.ACTION_UP) {
                    if (android.os.SystemClock.uptimeMillis() - downAt < 400
                            && Math.hypot(event.getX() - downX, event.getY() - downY) < dp(v.getContext(), 10)
                            && !tv.hasSelection()) {
                        int x = (int) event.getX() - tv.getTotalPaddingLeft() + tv.getScrollX();
                        int y = (int) event.getY() - tv.getTotalPaddingTop() + tv.getScrollY();
                        android.text.Layout layout = tv.getLayout();
                        if (layout != null && y >= 0 && y < layout.getHeight()) {
                            int line = layout.getLineForVertical(y);
                            if (x >= layout.getLineLeft(line) && x <= layout.getLineRight(line)) {
                                int off = layout.getOffsetForHorizontal(line, x);
                                CharSequence cs = tv.getText();
                                if (cs instanceof android.text.Spanned) {
                                    android.text.style.ClickableSpan[] links = ((android.text.Spanned) cs)
                                            .getSpans(off, off, android.text.style.ClickableSpan.class);
                                    if (links.length > 0) {
                                        links[0].onClick(tv);
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
                return false;
            }
        });
    }

    /** A markdown link, [text](https://…); one level of parentheses in the address too (Wikipedia's have them). */
    private static final java.util.regex.Pattern MD_LINK = java.util.regex.Pattern.compile(
            "\\[([^\\[\\]\\n]+)\\]\\((https?://[^\\s()]*(?:\\([^\\s()]*\\)[^\\s()]*)*)\\)");

    /** The same in plain text, for one-line previews: [text](url) becomes text, **bold** bold, and so on. */
    /**
     * What NN Agent downloads, with F-Droid's notice in its build: downloaded programs (NN's Linux, the agents, the
     * browser) come from their makers, not from F-Droid, and F-Droid asks that users know this before they agree.
     */
    static String downloadNote(Context c, String s) {
        return Flavor.FDROID ? s + "\n" + c.getString(R.string.fdroid_download_note) : s;
    }

    static String linkText(String text) {
        return markdown(text, null).toString();
    }

    private static final java.util.regex.Pattern MD_INLINE = java.util.regex.Pattern.compile(
            "`([^`\\n]+)`" // 1: code
                    + "|!\\[([^\\[\\]\\n]*)\\]\\(([^\\s()]+)\\)" // 2, 3: an image (shown under the reply, see ChatHub.pictures)
                    + "|" + MD_LINK.pattern() // 4, 5: a link
                    + "|\\*\\*(?!\\s)(.+?)\\*\\*|__(?!\\s)(.+?)__" // 6, 7: bold
                    + "|~~(.+?)~~" // 8: struck through
                    + "|(?<![\\w*])\\*(?![\\s*])(.+?)(?<!\\s)\\*(?![\\w*])" // 9: italic; not a * b * c
                    + "|(?<![\\w_])_(?![\\s_])(.+?)(?<!\\s)_(?![\\w_])" // 10: italic; not snake_case
                    + "|\\$([^$\\n]+)\\$"); // 11: math / formula
    private static final java.util.regex.Pattern MD_HEADING = java.util.regex.Pattern.compile("#{1,6}\\s+(.*?)\\s*#*\\s*"),
            MD_BULLET = java.util.regex.Pattern.compile("(\\s*)[-*+]\\s+(.*)"),
            MD_NUMBER = java.util.regex.Pattern.compile("(\\s*)(\\d{1,3}[.)])\\s+(.*)"),
            MD_RULE = java.util.regex.Pattern.compile("\\s*([-*_])(\\s*\\1){2,}\\s*"),
            MD_TABLE_LINE = java.util.regex.Pattern.compile("\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*"),
            CALLOUT_HEAD = java.util.regex.Pattern.compile("^>\\s*\\[!(NOTE|TIP|WARNING|CAUTION|IMPORTANT)\\]\\s*(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE),
            ACTION_URI_PAT = java.util.regex.Pattern.compile("(tel:[0-9+]+|mailto:[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}|geo:[0-9.-]+,[0-9.-]+[^\\s)\\]]*|app://[a-zA-Z0-9._]+)");

    /**
     * An agent's reply in Markdown, as formatted text: headings, bold, italic, struck through, `code` and code blocks,
     * lists, quotes, rules, tables (monospaced) and links, [text](url): the text, opening the address when tapped
     * (bare addresses are left to Linkify). Then runs before a link opens (e.g. to fold the panel).
     */
    static CharSequence markdown(String text, Runnable then) {
        android.text.SpannableStringBuilder out = new android.text.SpannableStringBuilder();
        String[] lines = text.split("\n", -1);
        int code = -1; // where the open code block started
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            int start = out.length();
            java.util.regex.Matcher m;
            if (l.trim().startsWith("```")) {
                if (code < 0) {
                    String tag = l.trim().substring(3).trim();
                    if (!tag.isEmpty()) {
                        out.append("[").append(tag.toUpperCase(java.util.Locale.ROOT)).append("]\n");
                        set(out, new android.text.style.ForegroundColorSpan(MUTED), start);
                        set(out, new android.text.style.RelativeSizeSpan(0.85f), start);
                    }
                    code = out.length();
                    continue;
                }
                if (out.length() > code && out.charAt(out.length() - 1) == '\n') out.delete(out.length() - 1, out.length());
                monospace(out, code, out.length());
                code = -1;
            } else if (code >= 0) {
                out.append(l);
            } else if ((m = MD_HEADING.matcher(l)).matches()) {
                inline(out, m.group(1), then);
                int level = l.indexOf(' ');
                set(out, new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start);
                set(out, new android.text.style.RelativeSizeSpan(level == 1 ? 1.25f : level == 2 ? 1.15f : 1.05f), start);
            } else if (MD_RULE.matcher(l).matches()) {
                out.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
                set(out, new android.text.style.ForegroundColorSpan(MUTED), start);
            } else if ((m = MD_BULLET.matcher(l)).matches()) {
                indent(out, m.group(1));
                String contentStr = m.group(2);
                if (contentStr.startsWith("[ ] ") || contentStr.startsWith("[x] ") || contentStr.startsWith("[X] ")) {
                    boolean checked = !contentStr.startsWith("[ ] ");
                    out.append(checked ? "\u2611 " : "\u2610 ");
                    inline(out, contentStr.substring(4), then);
                    if (checked) set(out, new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start);
                } else {
                    out.append("\u2022  ");
                    inline(out, contentStr, then);
                }
            } else if ((m = MD_NUMBER.matcher(l)).matches()) {
                indent(out, m.group(1));
                out.append(m.group(2)).append(' ');
                inline(out, m.group(3), then);
            } else if (l.startsWith(">")) {
                String q = l.replaceFirst(">\\s?", "");
                String qTrim = q.trim();
                if (qTrim.equalsIgnoreCase("[!NOTE]")) {
                    out.append("\u2139 NOTE\n");
                    set(out, new android.text.style.ForegroundColorSpan(ACCENT_TEXT), start);
                    set(out, new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start);
                } else if (qTrim.equalsIgnoreCase("[!TIP]")) {
                    out.append("\uD83D\uDCA1 TIP\n");
                    set(out, new android.text.style.ForegroundColorSpan(0xFF10B981), start);
                    set(out, new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start);
                } else if (qTrim.equalsIgnoreCase("[!IMPORTANT]")) {
                    out.append("\u2757 IMPORTANT\n");
                    set(out, new android.text.style.ForegroundColorSpan(ACCENT_TEXT), start);
                    set(out, new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start);
                } else if (qTrim.equalsIgnoreCase("[!WARNING]") || qTrim.equalsIgnoreCase("[!CAUTION]")) {
                    out.append("\u26A0 " + (qTrim.equalsIgnoreCase("[!WARNING]") ? "WARNING" : "CAUTION") + "\n");
                    set(out, new android.text.style.ForegroundColorSpan(ERR), start);
                    set(out, new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start);
                } else {
                    inline(out, q, then);
                    set(out, new android.text.style.QuoteSpan(MUTED), start);
                }
            } else if (l.trim().startsWith("|") && l.trim().endsWith("|") && l.trim().length() > 1) {
                if (MD_TABLE_LINE.matcher(l).matches()) continue; // |---|---|
                out.append(l.trim());
                monospace(out, start, out.length());
            } else {
                inline(out, l, then);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        if (code >= 0) monospace(out, code, out.length()); // never closed
        return out;
    }

    private static void inline(android.text.SpannableStringBuilder out, String s, Runnable then) {
        java.util.regex.Matcher m = MD_INLINE.matcher(s);
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            int a = out.length();
            if (m.group(1) != null) {
                out.append(m.group(1));
                monospace(out, a, out.length());
            } else if (m.group(3) != null) {
                String alt = m.group(2).trim();
                if (!alt.isEmpty()) {
                    out.append(alt);
                    set(out, new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), a);
                    set(out, new android.text.style.ForegroundColorSpan(MUTED), a);
                }
            } else if (m.group(5) != null) {
                out.append(m.group(4));
                set(out, new LinkSpan(m.group(5), then), a);
            } else if (m.group(6) != null || m.group(7) != null) {
                inline(out, m.group(6) != null ? m.group(6) : m.group(7), then);
                set(out, new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), a);
            } else if (m.group(8) != null) {
                inline(out, m.group(8), then);
                set(out, new android.text.style.StrikethroughSpan(), a);
            } else if (m.group(9) != null || m.group(10) != null) {
                inline(out, m.group(9) != null ? m.group(9) : m.group(10), then);
                set(out, new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), a);
            } else if (m.group(11) != null) {
                out.append(m.group(11));
                monospace(out, a, out.length());
                set(out, new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), a);
                set(out, new android.text.style.ForegroundColorSpan(ACCENT_TEXT), a);
            }
            last = m.end();
        }
        out.append(s, last, s.length());
    }

    private static void indent(android.text.SpannableStringBuilder out, String spaces) {
        for (int i = 0; i < Math.min(3, spaces.replace("\t", "  ").length() / 2); i++) out.append("    ");
    }

    private static void monospace(android.text.SpannableStringBuilder out, int a, int b) {
        if (b <= a) return;
        out.setSpan(new android.text.style.TypefaceSpan("monospace"), a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new android.text.style.ForegroundColorSpan(ACCENT_TEXT), a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new android.text.style.RelativeSizeSpan(0.95f), a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void set(android.text.SpannableStringBuilder out, Object span, int from) {
        if (out.length() > from) out.setSpan(span, from, out.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * For copying part of a reply as shown (start to end): a link whose text hides its address gets it after it,
     * "text (address)", so the address isn't lost outside NN Agent.
     */
    static String linkCopy(CharSequence shown, int start, int end) {
        if (!(shown instanceof android.text.Spanned)) return shown.subSequence(start, end).toString();
        android.text.Spanned s = (android.text.Spanned) shown;
        LinkSpan[] links = s.getSpans(start, end, LinkSpan.class);
        java.util.Arrays.sort(links, (x, y) -> s.getSpanStart(x) - s.getSpanStart(y));
        StringBuilder b = new StringBuilder();
        int at = start;
        for (LinkSpan l : links) {
            int a = Math.max(start, s.getSpanStart(l)), z = Math.min(end, s.getSpanEnd(l));
            String label = s.subSequence(s.getSpanStart(l), s.getSpanEnd(l)).toString();
            if (a < at || l.url.contains(label)) continue; // a bare address or email shows itself already
            b.append(s, at, z).append(" (").append(l.url).append(')');
            at = z;
        }
        return b.append(s, at, end).toString();
    }

    /** Extracts the body of all ``` code blocks in markdown text, or "" if none. */
    static String extractCode(String text) {
        if (text == null || !text.contains("```")) return "";
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("```[a-zA-Z0-9_-]*\\n([\\s\\S]*?)```").matcher(text);
        while (m.find()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(m.group(1).trim());
        }
        return sb.toString();
    }

    static void showCodeSheet(Activity a, String language, String codeContent) {
        String effectiveLang = (language != null && !language.trim().isEmpty())
                ? language.trim().toLowerCase(Locale.ROOT)
                : SyntaxHighlighter.detectLanguage(codeContent);
        String langLabel = effectiveLang.toUpperCase(Locale.ROOT);
        String[] lines = codeContent.split("\n", -1);

        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPaddingRelative(dp(a, 16), 0, dp(a, 16), dp(a, 16));

        // Header
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(0, 0, 0, dp(a, 10));

        TextView titleTv = monoText(a, langLabel + " (" + lines.length + " lines)", 13, ACCENT_TEXT);
        titleTv.setTypeface(sans(a, 700));
        header.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout copyBtn = new LinearLayout(a);
        copyBtn.setOrientation(LinearLayout.HORIZONTAL);
        copyBtn.setGravity(Gravity.CENTER_VERTICAL);
        copyBtn.setPaddingRelative(dp(a, 8), dp(a, 4), dp(a, 8), dp(a, 4));
        copyBtn.setBackground(rounded(a, CARD2, 8));
        ImageView copyIcon = icon(a, R.drawable.ms_content_copy, TEXT, 14);
        copyBtn.addView(copyIcon);
        TextView copyLabel = text(a, " " + a.getString(R.string.chat_action_copy), 12, TEXT);
        copyBtn.addView(copyLabel);
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = a.getSystemService(ClipboardManager.class);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Code", codeContent));
                Toast.makeText(a, R.string.chat_code_copied, Toast.LENGTH_SHORT).show();
            }
        });
        header.addView(copyBtn, wrap());

        // Body with line numbers
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setBackground(outlined(a, CODE, 12, OUTLINE, 1));

        StringBuilder lineNums = new StringBuilder();
        for (int i = 1; i <= lines.length; i++) {
            if (i > 1) lineNums.append('\n');
            lineNums.append(i);
        }
        TextView lineNumTv = monoText(a, lineNums.toString(), 12, 0xFF5C6370);
        lineNumTv.setGravity(Gravity.END);
        lineNumTv.setPaddingRelative(dp(a, 10), dp(a, 12), dp(a, 8), dp(a, 12));
        lineNumTv.setLineSpacing(dp(a, 2), 1.25f);
        body.addView(lineNumTv, wrap());

        View div = new View(a);
        div.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(a, 1), LinearLayout.LayoutParams.MATCH_PARENT);
        dlp.topMargin = dp(a, 6);
        dlp.bottomMargin = dp(a, 6);
        body.addView(div, dlp);

        HorizontalScrollView hScroll = new HorizontalScrollView(a);
        hScroll.setHorizontalScrollBarEnabled(false);
        CharSequence highlighted = SyntaxHighlighter.highlight(codeContent, effectiveLang);
        TextView codeTv = monoText(a, "", 12, CODE_TEXT);
        codeTv.setText(highlighted);
        codeTv.setTextIsSelectable(true);
        codeTv.setPaddingRelative(dp(a, 8), dp(a, 12), dp(a, 12), dp(a, 12));
        codeTv.setLineSpacing(dp(a, 2), 1.25f);
        hScroll.addView(codeTv, wrap());
        body.addView(hScroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        sheet.addView(body, full());
        bottomSheet(a, header, sheet, null).show();
    }

    static View buildCodeCard(Context c, String language, String codeContent) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(outlined(c, CODE, 12, OUTLINE, 1));

        String effectiveLang = (language != null && !language.trim().isEmpty())
                ? language.trim().toLowerCase(Locale.ROOT)
                : SyntaxHighlighter.detectLanguage(codeContent);
        String langLabel = effectiveLang.toUpperCase(Locale.ROOT);

        // Header
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(dp(c, 12), dp(c, 7), dp(c, 10), dp(c, 7));
        header.setBackgroundColor(0x22000000);

        TextView langTv = monoText(c, langLabel, 11, MUTED);
        header.addView(langTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        String[] lines = codeContent.split("\n", -1);
        boolean showLineNumbers = lines.length >= 3;
        boolean canExpand = lines.length > 8;
        final boolean[] isExpanded = new boolean[]{!canExpand};

        // Terminal run button (bash / sh / shell / zsh when Linux environment is installed)
        boolean isShell = "bash".equals(effectiveLang) || "sh".equals(effectiveLang) || "shell".equals(effectiveLang) || "zsh".equals(effectiveLang);
        LinearLayout outputBox = null;
        if (isShell && Linux.installed(c)) {
            LinearLayout runBtn = new LinearLayout(c);
            runBtn.setOrientation(LinearLayout.HORIZONTAL);
            runBtn.setGravity(Gravity.CENTER_VERTICAL);
            runBtn.setPaddingRelative(dp(c, 7), dp(c, 3), dp(c, 7), dp(c, 3));
            runBtn.setBackground(rounded(c, ACCENT_SOFT, 6));
            ImageView runIcon = icon(c, R.drawable.ic_play, ACCENT_TEXT, 12);
            runBtn.addView(runIcon);
            TextView runLabel = text(c, " " + c.getString(R.string.chat_action_run), 11, ACCENT_TEXT);
            runBtn.addView(runLabel);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMarginEnd(dp(c, 6));
            header.addView(runBtn, rlp);

            outputBox = new LinearLayout(c);
            outputBox.setOrientation(LinearLayout.VERTICAL);
            outputBox.setBackgroundColor(0xFF0A0908);
            outputBox.setPaddingRelative(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
            outputBox.setVisibility(View.GONE);

            View outDiv = new View(c);
            outDiv.setBackgroundColor(0x1FFFFFFF);
            outputBox.addView(outDiv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 1)));

            TextView outTv = monoText(c, "", 11, 0xFF98C379);
            outTv.setTextIsSelectable(true);
            outTv.setLineSpacing(dp(c, 2), 1.2f);
            outTv.setPaddingRelative(0, dp(c, 6), 0, 0);
            outputBox.addView(outTv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            final LinearLayout fOutBox = outputBox;
            runBtn.setOnClickListener(v -> {
                runBtn.setEnabled(false);
                runLabel.setText(" " + c.getString(R.string.chat_action_running));
                fOutBox.setVisibility(View.VISIBLE);
                outTv.setText("> " + c.getString(R.string.chat_action_running));
                outTv.setTextColor(MUTED);
                new Thread(() -> {
                    String res;
                    try {
                        res = Linux.exec(c, codeContent, 30);
                    } catch (Throwable t) {
                        res = "Error: " + t.getMessage();
                    }
                    final String finalRes = res;
                    card.post(() -> {
                        runBtn.setEnabled(true);
                        runLabel.setText(" " + c.getString(R.string.chat_action_run));
                        outTv.setText(finalRes == null || finalRes.trim().isEmpty() ? "(completed with no output)" : finalRes.trim());
                        outTv.setTextColor(finalRes != null && finalRes.startsWith("Error:") ? ERR : 0xFF98C379);
                    });
                }).start();
            });
        }

        // In-card expand/collapse button (if > 8 lines)
        final LinearLayout expandBtn;
        TextView expandLabel = null;
        ImageView expandIcon = null;
        if (canExpand) {
            expandBtn = new LinearLayout(c);
            expandBtn.setOrientation(LinearLayout.HORIZONTAL);
            expandBtn.setGravity(Gravity.CENTER_VERTICAL);
            expandBtn.setPaddingRelative(dp(c, 6), dp(c, 3), dp(c, 6), dp(c, 3));
            expandBtn.setBackground(rounded(c, 0x1FFFFFFF, 6));
            expandIcon = icon(c, R.drawable.ms_expand_more, MUTED, 13);
            expandBtn.addView(expandIcon);
            expandLabel = text(c, " " + c.getString(R.string.chat_code_expand) + " (" + lines.length + ")", 11, MUTED);
            expandBtn.addView(expandLabel);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            elp.setMarginEnd(dp(c, 6));
            header.addView(expandBtn, elp);
        } else {
            expandBtn = null;
        }

        // Fullscreen / Zoom button
        LinearLayout fullBtn = new LinearLayout(c);
        fullBtn.setOrientation(LinearLayout.HORIZONTAL);
        fullBtn.setGravity(Gravity.CENTER_VERTICAL);
        fullBtn.setPaddingRelative(dp(c, 6), dp(c, 3), dp(c, 6), dp(c, 3));
        fullBtn.setBackground(rounded(c, 0x1FFFFFFF, 6));
        ImageView fullIcon = icon(c, R.drawable.ms_open_in_full, MUTED, 13);
        fullBtn.addView(fullIcon);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.setMarginEnd(dp(c, 6));
        header.addView(fullBtn, flp);

        // Copy action button
        LinearLayout copyBtn = new LinearLayout(c);
        copyBtn.setOrientation(LinearLayout.HORIZONTAL);
        copyBtn.setGravity(Gravity.CENTER_VERTICAL);
        copyBtn.setPaddingRelative(dp(c, 6), dp(c, 3), dp(c, 6), dp(c, 3));
        copyBtn.setBackground(rounded(c, 0x1FFFFFFF, 6));
        ImageView copyIcon = icon(c, R.drawable.ms_content_copy, MUTED, 13);
        copyBtn.addView(copyIcon);
        TextView copyLabel = text(c, " " + c.getString(R.string.chat_action_copy), 11, MUTED);
        copyBtn.addView(copyLabel);
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = c.getSystemService(ClipboardManager.class);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Code", codeContent));
                Toast.makeText(c, R.string.chat_code_copied, Toast.LENGTH_SHORT).show();
            }
        });
        header.addView(copyBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.HORIZONTAL);

        TextView lineNumTv = null;
        if (showLineNumbers) {
            StringBuilder lineNums = new StringBuilder();
            for (int i = 1; i <= lines.length; i++) {
                if (i > 1) lineNums.append('\n');
                lineNums.append(i);
            }
            lineNumTv = monoText(c, lineNums.toString(), 12, 0xFF5C6370);
            lineNumTv.setGravity(Gravity.END);
            lineNumTv.setPaddingRelative(dp(c, 10), dp(c, 10), dp(c, 8), dp(c, 12));
            lineNumTv.setLineSpacing(dp(c, 2), 1.25f);
            if (canExpand) lineNumTv.setMaxLines(8);
            body.addView(lineNumTv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            View divider = new View(c);
            divider.setBackgroundColor(0x1FFFFFFF);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(c, 1), LinearLayout.LayoutParams.MATCH_PARENT);
            dlp.topMargin = dp(c, 6);
            dlp.bottomMargin = dp(c, 6);
            body.addView(divider, dlp);
        }

        // Horizontal scrollable code body
        HorizontalScrollView hScroll = new HorizontalScrollView(c);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        CharSequence highlighted = SyntaxHighlighter.highlight(codeContent, effectiveLang);
        TextView codeTv = monoText(c, "", 12, CODE_TEXT);
        codeTv.setText(highlighted);
        codeTv.setTextIsSelectable(true);
        codeTv.setPaddingRelative(dp(c, showLineNumbers ? 8 : 12), dp(c, 10), dp(c, 12), dp(c, 12));
        codeTv.setLineSpacing(dp(c, 2), 1.25f);
        if (canExpand) codeTv.setMaxLines(8);
        hScroll.addView(codeTv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams hsp = showLineNumbers
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        body.addView(hScroll, hsp);

        card.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Bottom expand strip if > 8 lines
        TextView expandBottomBar = null;
        if (canExpand) {
            expandBottomBar = monoText(c, "▾ " + c.getString(R.string.chat_code_expand) + " (" + (lines.length - 8) + " dòng nữa)", 11, ACCENT_TEXT);
            expandBottomBar.setGravity(Gravity.CENTER);
            expandBottomBar.setPaddingRelative(0, dp(c, 6), 0, dp(c, 7));
            expandBottomBar.setBackgroundColor(0x11000000);
            card.addView(expandBottomBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        if (canExpand) {
            final TextView fExpandLabel = expandLabel;
            final ImageView fExpandIcon = expandIcon;
            final TextView fExpandBottomBar = expandBottomBar;
            final TextView fLineNumTv = lineNumTv;
            final TextView fCodeTv = codeTv;
            View.OnClickListener toggleExpand = v -> {
                isExpanded[0] = !isExpanded[0];
                int maxL = isExpanded[0] ? Integer.MAX_VALUE : 8;
                if (fLineNumTv != null) fLineNumTv.setMaxLines(maxL);
                fCodeTv.setMaxLines(maxL);
                if (fExpandLabel != null) {
                    fExpandLabel.setText(" " + (isExpanded[0] ? c.getString(R.string.chat_code_collapse) : (c.getString(R.string.chat_code_expand) + " (" + lines.length + ")")));
                }
                if (fExpandIcon != null) {
                    fExpandIcon.setImageResource(isExpanded[0] ? R.drawable.ms_expand_less : R.drawable.ms_expand_more);
                }
                if (fExpandBottomBar != null) {
                    fExpandBottomBar.setText(isExpanded[0] ? ("▴ " + c.getString(R.string.chat_code_collapse)) : ("▾ " + c.getString(R.string.chat_code_expand) + " (" + (lines.length - 8) + " dòng nữa)"));
                }
            };
            if (expandBtn != null) expandBtn.setOnClickListener(toggleExpand);
            if (expandBottomBar != null) expandBottomBar.setOnClickListener(toggleExpand);
        }

        final TextView fLineNumTvFinal = lineNumTv;
        final TextView fCodeTvFinal = codeTv;
        final TextView fExpandLabelFinal = expandLabel;
        final ImageView fExpandIconFinal = expandIcon;
        final TextView fExpandBottomBarFinal = expandBottomBar;
        fullBtn.setOnClickListener(v -> {
            if (c instanceof Activity) {
                showCodeSheet((Activity) c, language, codeContent);
            } else if (canExpand) {
                isExpanded[0] = true;
                if (fLineNumTvFinal != null) fLineNumTvFinal.setMaxLines(Integer.MAX_VALUE);
                fCodeTvFinal.setMaxLines(Integer.MAX_VALUE);
                if (fExpandLabelFinal != null) fExpandLabelFinal.setText(" " + c.getString(R.string.chat_code_collapse));
                if (fExpandIconFinal != null) fExpandIconFinal.setImageResource(R.drawable.ms_expand_less);
                if (fExpandBottomBarFinal != null) fExpandBottomBarFinal.setText("▴ " + c.getString(R.string.chat_code_collapse));
            }
        });

        if (outputBox != null) {
            card.addView(outputBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return card;
    }

    static java.util.List<java.util.List<String>> parseMarkdownTable(java.util.List<String> lines) {
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        if (lines == null || lines.isEmpty()) return rows;
        for (String raw : lines) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            if (MD_TABLE_LINE.matcher(l).matches()) continue;
            if (l.startsWith("|")) l = l.substring(1);
            if (l.endsWith("|")) l = l.substring(0, l.length() - 1);
            String[] cells = l.split("\\|", -1);
            java.util.List<String> row = new java.util.ArrayList<>();
            for (String cell : cells) {
                row.add(cell.trim());
            }
            if (!row.isEmpty()) rows.add(row);
        }
        return rows;
    }

    static View buildTableCard(Context c, java.util.List<String> tableLines) {
        java.util.List<java.util.List<String>> rows = parseMarkdownTable(tableLines);
        if (rows.isEmpty()) return null;

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(outlined(c, CARD, 12, OUTLINE, 1));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setClipToOutline(true);
        }

        int colCount = 0;
        for (java.util.List<String> r : rows) {
            if (r.size() > colCount) colCount = r.size();
        }

        // Header toolbar
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(dp(c, 12), dp(c, 7), dp(c, 10), dp(c, 7));
        header.setBackgroundColor(0x22000000);

        int dataRows = Math.max(0, rows.size() - 1);
        String lang = c.getResources().getConfiguration().locale != null
                ? c.getResources().getConfiguration().locale.getLanguage() : "vi";
        String rowUnit = "vi".equals(lang) ? "dòng" : "rows";
        String rowSuffix = dataRows > 1 ? dataRows + " " + rowUnit : (dataRows == 1 ? "1 " + ("vi".equals(lang) ? "dòng" : "row") : "");
        String title = c.getString(R.string.chat_table_label).toUpperCase(Locale.ROOT)
                + (rowSuffix.isEmpty() ? "" : " (" + rowSuffix + ")");
        TextView titleTv = monoText(c, title, 11, MUTED);
        header.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // Copy Table button
        LinearLayout copyBtn = new LinearLayout(c);
        copyBtn.setOrientation(LinearLayout.HORIZONTAL);
        copyBtn.setGravity(Gravity.CENTER_VERTICAL);
        copyBtn.setPaddingRelative(dp(c, 6), dp(c, 3), dp(c, 6), dp(c, 3));
        copyBtn.setBackground(rounded(c, 0x1FFFFFFF, 6));
        ImageView copyIcon = icon(c, R.drawable.ms_content_copy, MUTED, 13);
        copyBtn.addView(copyIcon);
        TextView copyLabel = text(c, " " + c.getString(R.string.chat_action_copy), 11, MUTED);
        copyBtn.addView(copyLabel);

        final StringBuilder tsv = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            java.util.List<String> row = rows.get(r);
            for (int col = 0; col < row.size(); col++) {
                if (col > 0) tsv.append('\t');
                tsv.append(row.get(col));
            }
            tsv.append('\n');
        }
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = c.getSystemService(ClipboardManager.class);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Table", tsv.toString()));
                Toast.makeText(c, R.string.chat_table_copied, Toast.LENGTH_SHORT).show();
            }
        });
        header.addView(copyBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Horizontal scroll for table grid
        HorizontalScrollView hScroll = new HorizontalScrollView(c);
        hScroll.setFillViewport(true);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        // Compute base column widths across all rows
        int[] colWidths = new int[colCount];
        int totalBaseWidth = 0;
        for (int col = 0; col < colCount; col++) {
            int maxLen = 0;
            for (java.util.List<String> r : rows) {
                if (col < r.size()) {
                    int l = r.get(col).length();
                    if (l > maxLen) maxLen = l;
                }
            }
            int w;
            if (col == 0 && maxLen <= 4) {
                w = dp(c, 46);
            } else if (maxLen <= 8) {
                w = dp(c, 80);
            } else if (maxLen <= 18) {
                w = dp(c, 110);
            } else if (maxLen <= 35) {
                w = dp(c, 170);
            } else {
                w = dp(c, 230);
            }
            colWidths[col] = w;
            totalBaseWidth += w;
        }
        totalBaseWidth += Math.max(0, colCount - 1) * dp(c, 1);

        // Responsive stretch: if table is narrower than available screen/card width,
        // expand columns to fill 100% of card width so there's no dark empty gap on the right.
        int screenWidth = c.getResources().getDisplayMetrics().widthPixels;
        int availableWidth = Math.max(dp(c, 280), screenWidth - dp(c, 32));
        if (totalBaseWidth < availableWidth) {
            int extra = availableWidth - totalBaseWidth;
            if (colCount == 2) {
                // For 2-column key-value tables, keep column 0 compact and give extra to column 1
                colWidths[1] += extra;
            } else if (colCount > 2) {
                boolean hasIndex = (colWidths[0] <= dp(c, 50));
                int startCol = hasIndex ? 1 : 0;
                int stretchCount = colCount - startCol;
                if (stretchCount > 0) {
                    int perCol = extra / stretchCount;
                    int rem = extra % stretchCount;
                    for (int i = startCol; i < colCount; i++) {
                        colWidths[i] += perCol + (i == colCount - 1 ? rem : 0);
                    }
                }
            } else if (colCount == 1) {
                colWidths[0] += extra;
            }
        }

        LinearLayout tableBox = new LinearLayout(c);
        tableBox.setOrientation(LinearLayout.VERTICAL);

        for (int r = 0; r < rows.size(); r++) {
            java.util.List<String> row = rows.get(r);
            boolean isHeader = (r == 0);

            LinearLayout rowView = new LinearLayout(c);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            if (isHeader) {
                rowView.setBackgroundColor(0x18FFFFFF);
            } else if (r % 2 == 1) {
                rowView.setBackgroundColor(0x06FFFFFF); // Zebra striping
            }

            for (int col = 0; col < colCount; col++) {
                String val = col < row.size() ? row.get(col) : "";
                // Use sans-serif text for natural, readable typography (monospace only for code blocks)
                TextView cellTv = text(c, "", 12, TEXT);
                cellTv.setText(markdown(val, null));
                if (isHeader) {
                    cellTv.setTypeface(sans(c, 700));
                } else {
                    cellTv.setTextIsSelectable(true);
                }
                cellTv.setSingleLine(false);
                cellTv.setLineSpacing(dp(c, 2), 1.2f);
                cellTv.setPaddingRelative(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));

                rowView.addView(cellTv, new LinearLayout.LayoutParams(colWidths[col], LinearLayout.LayoutParams.WRAP_CONTENT));

                if (col < colCount - 1) {
                    View colDiv = new View(c);
                    colDiv.setBackgroundColor(0x14FFFFFF);
                    rowView.addView(colDiv, new LinearLayout.LayoutParams(dp(c, 1), LinearLayout.LayoutParams.MATCH_PARENT));
                }
            }

            tableBox.addView(rowView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            if (isHeader) {
                View div = new View(c);
                div.setBackgroundColor(0x2AFFFFFF);
                tableBox.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 1)));
            } else if (r < rows.size() - 1) {
                View div = new View(c);
                div.setBackgroundColor(0x0EFFFFFF);
                tableBox.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 1)));
            }
        }

        hScroll.addView(tableBox, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        card.addView(hScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    static View buildCalloutCard(Context c, String type, String content, Runnable onLinkTapped) {
        String upper = type.toUpperCase(Locale.ROOT);
        int accentColor = ACCENT_TEXT;
        int iconRes = R.drawable.ms_description;
        String label = "NOTE";

        if ("TIP".equals(upper)) {
            accentColor = OK;
            iconRes = R.drawable.ms_auto_awesome;
            label = "TIP";
        } else if ("WARNING".equals(upper)) {
            accentColor = WARN;
            iconRes = R.drawable.ms_error;
            label = "WARNING";
        } else if ("CAUTION".equals(upper)) {
            accentColor = ERR;
            iconRes = R.drawable.ms_error;
            label = "CAUTION";
        } else if ("IMPORTANT".equals(upper)) {
            accentColor = ACCENT_TEXT;
            iconRes = R.drawable.ms_error;
            label = "IMPORTANT";
        }

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(outlined(c, Theme.withAlpha(accentColor, 0x14), 10, Theme.withAlpha(accentColor, 0x40), 1));
        card.setPaddingRelative(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));

        // Left accent bar
        View bar = new View(c);
        bar.setBackground(rounded(c, accentColor, 2));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(c, 3), LinearLayout.LayoutParams.MATCH_PARENT);
        barLp.setMarginEnd(dp(c, 10));
        card.addView(bar, barLp);

        // Content column
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView ic = icon(c, iconRes, accentColor, 14);
        head.addView(ic);
        TextView titleTv = monoText(c, " " + label, 11, accentColor);
        titleTv.setTypeface(sans(c, 700));
        head.addView(titleTv);
        col.addView(head, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView bodyTv = text(c, "", 14, TEXT);
        setupMarkdownTextView(c, bodyTv, markdown(content, onLinkTapped), onLinkTapped);
        bodyTv.setPaddingRelative(0, dp(c, 4), 0, 0);
        col.addView(bodyTv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    static View buildActionChips(Context c, String text, Runnable onLinkTapped) {
        if (text == null || text.isEmpty()) return null;
        java.util.regex.Matcher m = ACTION_URI_PAT.matcher(text);
        java.util.List<String> uris = new java.util.ArrayList<>();
        while (m.find()) {
            String uri = m.group(1);
            if (!uris.contains(uri)) uris.add(uri);
        }
        if (uris.isEmpty()) return null;

        HorizontalScrollView hScroll = new HorizontalScrollView(c);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPaddingRelative(0, dp(c, 6), 0, dp(c, 2));

        for (String uri : uris) {
            String label;
            int iconRes;
            int iconTint = ACCENT_TEXT;

            if (uri.startsWith("tel:")) {
                label = uri.substring(4);
                iconRes = R.drawable.ms_graphic_eq;
            } else if (uri.startsWith("mailto:")) {
                label = uri.substring(7);
                iconRes = R.drawable.ms_attach_file;
            } else if (uri.startsWith("geo:")) {
                label = "Map";
                iconRes = R.drawable.ms_description;
            } else if (uri.startsWith("app://")) {
                label = uri.substring(6);
                iconRes = R.drawable.ms_code;
            } else {
                label = uri;
                iconRes = R.drawable.ms_description;
            }

            LinearLayout chip = new LinearLayout(c);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackground(outlined(c, CARD2, 14, OUTLINE, 1));
            chip.setPaddingRelative(dp(c, 10), dp(c, 5), dp(c, 12), dp(c, 5));

            ImageView ic = icon(c, iconRes, iconTint, 14);
            chip.addView(ic);
            TextView lbl = text(c, " " + label, 12, TEXT);
            chip.addView(lbl);

            chip.setOnClickListener(v -> {
                if (onLinkTapped != null) onLinkTapped.run();
                try {
                    Intent intent;
                    if (uri.startsWith("tel:")) {
                        intent = new Intent(Intent.ACTION_DIAL, android.net.Uri.parse(uri));
                    } else if (uri.startsWith("app://")) {
                        String pkg = uri.substring(6);
                        intent = c.getPackageManager().getLaunchIntentForPackage(pkg);
                        if (intent == null) {
                            intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg));
                        }
                    } else {
                        intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                    }
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        c.startActivity(intent);
                    }
                } catch (Exception e) {
                    Toast.makeText(c, R.string.ui_no_app_for_link, Toast.LENGTH_SHORT).show();
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(c, 6));
            row.addView(chip, lp);
        }

        hScroll.addView(row, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return hScroll;
    }

    private static void setupMarkdownTextView(Context c, TextView tv, CharSequence md, Runnable onLinkTapped) {
        tv.setText(md);
        tv.setLineSpacing(dp(c, 3), 1.30f);
        tv.setPaddingRelative(0, dp(c, 2), 0, dp(c, 4));
        if (c instanceof Activity) {
            tv.setTextIsSelectable(true);
            linkifySelectable(tv);
            copyWithLinks(tv);
        } else {
            linkifyForService(tv, onLinkTapped);
        }
    }

    private static void flushText(Context c, LinearLayout container, StringBuilder textBuf, Runnable onLinkTapped) {
        if (textBuf.length() > 0) {
            String t = textBuf.toString().trim();
            if (!t.isEmpty()) {
                TextView tv = text(c, "", 15, TEXT);
                setupMarkdownTextView(c, tv, markdown(t, onLinkTapped), onLinkTapped);
                container.addView(tv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            textBuf.setLength(0);
        }
    }

    private static void flushTable(Context c, LinearLayout container, java.util.List<String> tableBuf, StringBuilder textBuf) {
        if (tableBuf != null && !tableBuf.isEmpty()) {
            View tableCard = buildTableCard(c, tableBuf);
            if (tableCard != null) {
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                tp.setMargins(0, dp(c, 6), 0, dp(c, 8));
                container.addView(tableCard, tp);
            } else {
                for (String tl : tableBuf) {
                    if (textBuf.length() > 0) textBuf.append('\n');
                    textBuf.append(tl);
                }
            }
        }
    }

    private static void flushCallout(Context c, LinearLayout container, String calloutType, StringBuilder calloutBuf, Runnable onLinkTapped) {
        if (calloutType != null && calloutBuf != null && calloutBuf.length() > 0) {
            View calloutCard = buildCalloutCard(c, calloutType, calloutBuf.toString().trim(), onLinkTapped);
            LinearLayout.LayoutParams callp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            callp.setMargins(0, dp(c, 6), 0, dp(c, 8));
            container.addView(calloutCard, callp);
        }
    }

    static void renderMarkdownWithCodeCards(Context c, LinearLayout container, String text, Runnable onLinkTapped) {
        if (text == null || text.isEmpty()) return;

        String[] lines = text.split("\n", -1);
        StringBuilder textBuf = new StringBuilder();
        StringBuilder codeBuf = null;
        String lang = "";
        java.util.List<String> tableBuf = null;
        String calloutType = null;
        StringBuilder calloutBuf = null;

        for (String l : lines) {
            String trimmed = l.trim();

            // 1. Inside a code block
            if (codeBuf != null) {
                if (trimmed.startsWith("```")) {
                    View codeCard = buildCodeCard(c, lang, codeBuf.toString());
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    cp.setMargins(0, dp(c, 6), 0, dp(c, 8));
                    container.addView(codeCard, cp);
                    codeBuf = null;
                    lang = "";
                } else {
                    if (codeBuf.length() > 0) codeBuf.append('\n');
                    codeBuf.append(l);
                }
                continue;
            }

            // 2. Starting a code block
            if (trimmed.startsWith("```")) {
                flushCallout(c, container, calloutType, calloutBuf, onLinkTapped);
                calloutType = null;
                calloutBuf = null;
                flushTable(c, container, tableBuf, textBuf);
                tableBuf = null;
                flushText(c, container, textBuf, onLinkTapped);
                lang = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
                codeBuf = new StringBuilder();
                continue;
            }

            // 3. Callout block
            java.util.regex.Matcher cm = CALLOUT_HEAD.matcher(trimmed);
            if (cm.matches()) {
                flushCallout(c, container, calloutType, calloutBuf, onLinkTapped);
                calloutType = null;
                calloutBuf = null;
                flushTable(c, container, tableBuf, textBuf);
                tableBuf = null;
                flushText(c, container, textBuf, onLinkTapped);
                calloutType = cm.group(1);
                calloutBuf = new StringBuilder();
                String firstLineRest = cm.group(2);
                if (firstLineRest != null && !firstLineRest.trim().isEmpty()) {
                    calloutBuf.append(firstLineRest.trim());
                }
                continue;
            }

            if (calloutBuf != null) {
                if (trimmed.startsWith(">")) {
                    String sub = l.replaceFirst(">\\s?", "");
                    if (calloutBuf.length() > 0) calloutBuf.append('\n');
                    calloutBuf.append(sub);
                    continue;
                } else {
                    flushCallout(c, container, calloutType, calloutBuf, onLinkTapped);
                    calloutType = null;
                    calloutBuf = null;
                    // Fall through to process 'l' below
                }
            }

            // 4. Table block
            boolean isTable = (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 1)
                    || (tableBuf != null && MD_TABLE_LINE.matcher(trimmed).matches());
            if (isTable) {
                flushText(c, container, textBuf, onLinkTapped);
                if (tableBuf == null) tableBuf = new java.util.ArrayList<>();
                tableBuf.add(l);
                continue;
            } else if (tableBuf != null) {
                flushTable(c, container, tableBuf, textBuf);
                tableBuf = null;
                // Fall through to process 'l' below
            }

            // 5. Normal text
            if (textBuf.length() > 0) textBuf.append('\n');
            textBuf.append(l);
        }

        flushCallout(c, container, calloutType, calloutBuf, onLinkTapped);
        flushTable(c, container, tableBuf, textBuf);
        flushText(c, container, textBuf, onLinkTapped);

        // 6. Action Chips (Deep Links)
        View actionChips = buildActionChips(c, text, onLinkTapped);
        if (actionChips != null) {
            LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            acp.setMargins(0, dp(c, 4), 0, dp(c, 4));
            container.addView(actionChips, acp);
        }
    }

    /** Copy in a selectable reply keeps the addresses of its links (see linkCopy). */
    static void copyWithLinks(TextView t) {
        t.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                return true;
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                if (item.getItemId() != android.R.id.copy) return false;
                int a = Math.min(t.getSelectionStart(), t.getSelectionEnd()), b = Math.max(t.getSelectionStart(), t.getSelectionEnd());
                if (a < 0 || b <= a) return false;
                t.getContext().getSystemService(android.content.ClipboardManager.class)
                        .setPrimaryClip(android.content.ClipData.newPlainText("NN", linkCopy(t.getText(), a, b)));
                mode.finish();
                return true;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode mode) {
            }
        });
    }

    /**
     * A link that opens with a new task (so it works from the bubble, which isn't an activity). Not a URLSpan: Linkify,
     * which replaces those, leaves it be. Then runs before it opens.
     */
    static final class LinkSpan extends android.text.style.ClickableSpan {
        final String url;
        private final Runnable then;

        LinkSpan(String url, Runnable then) {
            this.url = url;
            this.then = then;
        }

        @Override
        public void onClick(android.view.View v) {
            if (then != null) then.run();
            try {
                v.getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                android.widget.Toast.makeText(v.getContext(), R.string.ui_no_app_for_link, android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    static ProgressBar spinner(Context c, int sizeDp) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleSmall);
        p.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        p.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return p;
    }

    // --------------------------------------------------------------- bottom sheet

    /** Interactive bottom sheet dialog: swipe down from handle/header/top-of-scroll, inset-safe & height-capped. */
    static Dialog bottomSheet(Activity a, View content) {
        return bottomSheet(a, null, content, null);
    }

    /** Interactive bottom sheet with optional sticky header above scroll and sticky footer below scroll. */
    static Dialog bottomSheet(Activity a, View header, View content, View footer) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int baseH = dp(a, 20);
        final int[] scrollCap = { (int) (a.getResources().getDisplayMetrics().heightPixels * 0.84f) };
        final ScrollView scroll = new ScrollView(a) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, h);
                if (getMeasuredHeight() > scrollCap[0]) {
                    super.onMeasure(w, MeasureSpec.makeMeasureSpec(scrollCap[0], MeasureSpec.AT_MOST));
                }
            }
        };
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);

        final FrameLayout handleWrap = new FrameLayout(a);
        handleWrap.setPaddingRelative(0, dp(a, 10), 0, dp(a, 12));
        View handle = new View(a);
        handle.setBackground(rounded(a, OUTLINE, 2));
        handleWrap.addView(handle, new FrameLayout.LayoutParams(dp(a, 40), dp(a, 4), Gravity.CENTER));

        final class SheetLayout extends LinearLayout {
            private float downX, downY;
            private boolean dragging, touchOnTopChrome, dismissing;
            private VelocityTracker vt;
            private final int slop = ViewConfiguration.get(a).getScaledTouchSlop();

            SheetLayout() {
                super(a);
                setOrientation(VERTICAL);
            }

            @Override
            protected void onMeasure(int w, int h) {
                int screenH = getResources().getDisplayMetrics().heightPixels;
                int maxTotal = (int) (screenH * 0.88f);
                if (MeasureSpec.getMode(h) != MeasureSpec.UNSPECIFIED) {
                    maxTotal = Math.min(maxTotal, MeasureSpec.getSize(h));
                }
                int widthMode = MeasureSpec.getMode(w);
                int widthSize = MeasureSpec.getSize(w);
                int innerW = Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());
                int childW = widthMode == MeasureSpec.UNSPECIFIED ? w
                        : MeasureSpec.makeMeasureSpec(innerW, MeasureSpec.EXACTLY);
                int unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

                int chrome = getPaddingTop() + getPaddingBottom();
                handleWrap.measure(childW, unspec);
                chrome += handleWrap.getMeasuredHeight();
                if (header != null && header.getVisibility() != GONE) {
                    header.measure(childW, unspec);
                    chrome += header.getMeasuredHeight();
                }
                if (footer != null && footer.getVisibility() != GONE) {
                    footer.measure(childW, unspec);
                    chrome += footer.getMeasuredHeight();
                }
                scrollCap[0] = Math.max(dp(a, 100), maxTotal - chrome);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxTotal, MeasureSpec.AT_MOST));
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (dismissing) return true;
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = ev.getRawX();
                        downY = ev.getRawY();
                        dragging = false;
                        int chromeBottom = getPaddingTop() + handleWrap.getHeight()
                                + (header != null && header.getVisibility() != GONE ? header.getHeight() : 0);
                        touchOnTopChrome = ev.getY() <= chromeBottom;
                        if (vt != null) vt.recycle();
                        vt = VelocityTracker.obtain();
                        vt.addMovement(ev);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (vt != null) vt.addMovement(ev);
                        float dy = ev.getRawY() - downY;
                        float dx = ev.getRawX() - downX;
                        if (!dragging && dy > slop && dy > Math.abs(dx) * 1.15f) {
                            if (touchOnTopChrome || !scroll.canScrollVertically(-1)) {
                                dragging = true;
                                return true;
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        if (vt != null) {
                            vt.recycle();
                            vt = null;
                        }
                        break;
                }
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (dismissing) return true;
                if (vt == null) vt = VelocityTracker.obtain();
                vt.addMovement(ev);
                Window win = d.getWindow();
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = ev.getRawX();
                        downY = ev.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dy = Math.max(0f, ev.getRawY() - downY);
                        if (dy > slop) dragging = true;
                        setTranslationY(dy);
                        if (win != null && getHeight() > 0) {
                            win.setDimAmount(0.55f * Math.max(0.08f, 1f - dy / getHeight()));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        vt.computeCurrentVelocity(1000);
                        float vy = vt.getYVelocity();
                        vt.recycle();
                        vt = null;
                        float cur = getTranslationY();
                        float thresh = Math.min(dp(a, 100), Math.max(dp(a, 60), getHeight() * 0.24f));
                        if (cur > thresh || (vy > dp(a, 750) && cur > dp(a, 16))) {
                            dismissing = true;
                            animate().translationY(Math.max(getHeight(), dp(a, 240)))
                                    .setDuration(170)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .withEndAction(() -> {
                                        if (win != null) win.setWindowAnimations(0);
                                        if (d.isShowing()) d.dismiss();
                                    }).start();
                        } else if (cur > 0) {
                            animate().translationY(0f).setDuration(160)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                            if (win != null) win.setDimAmount(0.55f);
                        }
                        dragging = false;
                        return true;
                }
                return super.onTouchEvent(ev);
            }
        }

        int navInit = 0;
        if (Build.VERSION.SDK_INT >= 23) {
            WindowInsets actInsets = a.getWindow().getDecorView().getRootWindowInsets();
            if (actInsets != null) {
                navInit = Build.VERSION.SDK_INT >= 30
                        ? actInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
                        : actInsets.getSystemWindowInsetBottom();
            }
        }
        final SheetLayout box = new SheetLayout();
        box.setPaddingRelative(dp(a, 20), 0, dp(a, 20), Math.max(baseH, navInit + dp(a, 12)));
        box.setBackground(corners(a, CARD, 28, 28, 0, 0));
        box.addView(handleWrap, full());
        if (header != null) box.addView(header, full());
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(scroll, full());
        if (footer != null) box.addView(footer, full());

        box.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottomInset = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                bottomInset = insets.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.ime()).bottom;
            } else {
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            if (bottomInset == 0 && Build.VERSION.SDK_INT >= 23) {
                WindowInsets actInsets = a.getWindow().getDecorView().getRootWindowInsets();
                if (actInsets != null) {
                    bottomInset = Build.VERSION.SDK_INT >= 30
                            ? actInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
                            : actInsets.getSystemWindowInsetBottom();
                }
            }
            v.setPaddingRelative(dp(a, 20), 0, dp(a, 20), Math.max(baseH, bottomInset + dp(a, 12)));
            return insets;
        });

        d.setContentView(box);
        d.setCanceledOnTouchOutside(true);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.55f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        return d;
    }

    static final String PRIVACY_URL = "https://github.com/nhatnamdev662/nn-android/blob/main/docs/PRIVACY.md";

    /**
     * Opens Android's accessibility settings, after the user has agreed to what NN's accessibility service does
     * (Google Play's prominent disclosure: shown in the app, before the permission, with an explicit choice).
     */
    static void openAccessibilitySettings(Activity a) {
        Runnable open = () -> a.startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        if (Prefs.a11yConsent(a)) {
            open.run();
            return;
        }
        LinearLayout c = column(a);
        c.addView(title(a, a.getString(R.string.ui_a11y_title), 18));
        String[] points = {
                a.getString(R.string.ui_a11y_what),
                a.getString(R.string.ui_a11y_data),
                a.getString(R.string.ui_a11y_no_servers),
                a.getString(R.string.ui_a11y_asks),
        };
        for (String pt : points) {
            TextView t = text(a, pt, 14, MUTED);
            t.setPaddingRelative(0, dp(a, 8), 0, 0);
            c.addView(t);
        }
        TextView policy = text(a, a.getString(R.string.ui_privacy_policy), 14, ACCENT_TEXT);
        policy.setPaddingRelative(0, dp(a, 10), 0, 0);
        policy.setOnClickListener(v -> a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(PRIVACY_URL))));
        c.addView(policy);
        LinearLayout btns = row(a);
        btns.setPaddingRelative(0, dp(a, 18), 0, 0);
        Button no = button(a, a.getString(R.string.ui_no_thanks), false);
        Button yes = button(a, a.getString(R.string.ui_agree_continue), true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(a, 48), 1);
        btns.addView(no, bp);
        LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(0, dp(a, 48), 1);
        yp.setMarginStart(dp(a, 10));
        btns.addView(yes, yp);
        c.addView(btns, full());
        Dialog d = bottomSheet(a, c);
        no.setOnClickListener(v -> d.dismiss());
        yes.setOnClickListener(v -> {
            Prefs.setA11yConsent(a);
            d.dismiss();
            open.run();
        });
        d.show();
    }

    /** A sheet asking to confirm; the action runs on the destructive button. */
    static void confirmSheet(Activity a, String title, String message, String action, boolean destructive, Runnable onYes) {
        LinearLayout c = column(a);
        c.addView(title(a, title, 18));
        if (message != null) {
            TextView m = text(a, message, 14, MUTED);
            m.setPaddingRelative(0, dp(a, 6), 0, dp(a, 18));
            c.addView(m);
        }
        LinearLayout btns = row(a);
        Button no = button(a, a.getString(android.R.string.cancel), false);
        Button yes = button(a, action, !destructive);
        if (destructive) {
            yes.setTextColor(ERR);
            yes.setBackground(rounded(a, ERR_SOFT, 12));
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(a, 48), 1);
        btns.addView(no, bp);
        LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(0, dp(a, 48), 1);
        yp.setMarginStart(dp(a, 10));
        btns.addView(yes, yp);
        c.addView(btns, full());
        Dialog d = bottomSheet(a, c);
        no.setOnClickListener(v -> d.dismiss());
        yes.setOnClickListener(v -> {
            d.dismiss();
            onYes.run();
        });
        d.show();
    }

    // ---------------------------------------------------------------- bottom nav

    static final int NAV_CHATS = 0, NAV_SESSIONS = 1, NAV_SETTINGS = 2;

    /** Chats · Sessions · Settings, with the active item highlighted. */
    static LinearLayout bottomNav(Activity a, int active, int sessionsBadge) {
        LinearLayout bar = row(a);
        bar.setBackgroundColor(NAV);
        bar.setGravity(Gravity.CENTER);
        int openTerms = TermSession.openCount();
        Object[][] items = {
                {R.drawable.ms_chat_bubble, a.getString(R.string.ui_nav_chats), MainActivity.class},
                {R.drawable.ms_terminal, a.getString(R.string.terminal_title), MonitorActivity.class},
                {R.drawable.ms_tune, a.getString(R.string.ui_nav_settings), SetupActivity.class},
        };
        Intent curIntent = a.getIntent();
        final int tabDir = curIntent != null ? curIntent.getIntExtra("nn_tab_dir", 0) : 0;
        if (tabDir != 0) curIntent.removeExtra("nn_tab_dir");
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            boolean on = i == active;
            LinearLayout it = column(a);
            it.setGravity(Gravity.CENTER_HORIZONTAL);
            it.setPaddingRelative(0, dp(a, 10), 0, dp(a, 10));
            final FrameLayout pill = new FrameLayout(a);
            pill.setBackground(on ? rounded(a, ACCENT_SOFT, 16) : null);
            ImageView ic = icon(a, (Integer) items[i][0], on ? ACCENT_TEXT : MUTED, 22);
            pill.addView(ic, new FrameLayout.LayoutParams(dp(a, 22), dp(a, 22), Gravity.CENTER));
            if (i == NAV_SESSIONS && openTerms > 0) {
                TextView b = monoText(a, String.valueOf(openTerms), 10, 0xFFFFFFFF);
                b.setTypeface(mono(a, 600));
                b.setGravity(Gravity.CENTER);
                b.setBackground(rounded(a, OK, 8));
                b.setPaddingRelative(dp(a, 4), 0, dp(a, 4), 0);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(a, 16), Gravity.TOP | Gravity.END);
                bp.topMargin = dp(a, 1);
                bp.setMarginEnd(dp(a, 8));
                pill.addView(b, bp);
            }
            if (on && tabDir != 0) {
                pill.setScaleX(0.88f);
                pill.setScaleY(0.88f);
                pill.animate().scaleX(1f).scaleY(1f)
                        .setDuration(130)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
            it.addView(pill, new LinearLayout.LayoutParams(dp(a, 60), dp(a, 32)));
            TextView label = text(a, (String) items[i][1], 12, on ? ACCENT_TEXT : MUTED);
            label.setTypeface(sans(a, on ? 600 : 500));
            label.setPaddingRelative(0, dp(a, 4), 0, 0);
            label.setGravity(Gravity.CENTER);
            it.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            it.setBackgroundResource(ripple(a, false));
            it.setOnClickListener(v -> {
                if (idx == active) return;
                boolean right = idx > active;
                Intent in = new Intent(a, (Class<?>) items[idx][2])
                        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .putExtra("nn_tab_dir", right ? 1 : -1);
                if (idx == NAV_CHATS) {
                    in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                } else {
                    in.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                }
                a.startActivity(in);
                a.overridePendingTransition(0, 0);
            });
            bar.addView(it, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        LinearLayout wrap = column(a);
        View line = new View(a);
        line.setBackgroundColor(LINE);
        wrap.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(a, 1)));
        wrap.addView(bar, full());
        if (tabDir != 0) {
            wrap.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    if (!(v.getParent() instanceof ViewGroup)) return;
                    ViewGroup navSlot = (ViewGroup) v.getParent();
                    if (!(navSlot.getParent() instanceof ViewGroup)) return;
                    ViewGroup parent = (ViewGroup) navSlot.getParent();
                    View content = parent.getChildAt(0);
                    if (content == null || content == navSlot) return;
                    content.animate().cancel();
                    content.setTranslationX(dp(a, 14) * tabDir);
                    content.setAlpha(0.72f);
                    content.animate().translationX(0f).alpha(1f)
                            .setDuration(120)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
        }
        return wrap;
    }

    /** An animated 3-bouncing-dots bubble with the NN avatar for chat lists while an agent thinks. */
    static class TypingIndicator extends LinearLayout {
        private final View[] dots = new View[3];
        private final android.animation.ValueAnimator[] anims = new android.animation.ValueAnimator[3];

        TypingIndicator(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.BOTTOM);

            int d8 = dp(c, 8);
            int d24 = dp(c, 24);

            FrameLayout av = new FrameLayout(c);
            av.setBackground(oval(ACCENT_SOFT));
            ImageView face = new ImageView(c);
            Face.set(face, R.drawable.bubble_face);
            int fs = dp(c, Face.size(c, 20, 24));
            av.addView(face, new FrameLayout.LayoutParams(fs, fs, Gravity.CENTER));
            LayoutParams avp = new LayoutParams(d24, d24);
            avp.setMarginEnd(d8);
            addView(av, avp);

            LinearLayout bubble = new LinearLayout(c);
            bubble.setOrientation(HORIZONTAL);
            bubble.setGravity(Gravity.CENTER_VERTICAL);
            bubble.setBackground(corners(c, CARD, 18, 18, 18, 6));
            int padH = dp(c, 16);
            int padV = dp(c, 12);
            bubble.setPaddingRelative(padH, padV, padH, padV);

            int dotSize = dp(c, 6);
            int dotMargin = dp(c, 4);
            for (int i = 0; i < 3; i++) {
                View dot = new View(c);
                dot.setBackground(oval(MUTED));
                LayoutParams lp = new LayoutParams(dotSize, dotSize);
                if (i > 0) lp.setMarginStart(dotMargin);
                bubble.addView(dot, lp);
                dots[i] = dot;
            }
            addView(bubble, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            startAnimation();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            stopAnimation();
        }

        boolean isAnimating() {
            for (android.animation.ValueAnimator a : anims) {
                if (a != null && a.isRunning()) return true;
            }
            return false;
        }

        void startAnimation() {
            if (isAnimating()) return;
            stopAnimation();
            final float up = -dp(getContext(), 4);
            final float down = dp(getContext(), 2);
            for (int i = 0; i < 3; i++) {
                final View d = dots[i];
                android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(down, up);
                a.setDuration(320);
                a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                a.setStartDelay(i * 150L);
                a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                a.addUpdateListener(anim -> d.setTranslationY((float) anim.getAnimatedValue()));
                anims[i] = a;
                a.start();
            }
        }

        void stopAnimation() {
            for (int i = 0; i < 3; i++) {
                if (anims[i] != null) {
                    anims[i].removeAllUpdateListeners();
                    anims[i].cancel();
                    anims[i] = null;
                }
                if (dots[i] != null) {
                    dots[i].setTranslationY(0);
                }
            }
        }
    }

    /** Unlocks 90Hz/120Hz/144Hz high refresh rate on the Activity window. */
    static void unlockHighRefreshRate(Activity a) {
        if (a == null) return;
        Window w = a.getWindow();
        if (w == null) return;
        Display d = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { d = a.getDisplay(); } catch (Throwable ignored) {}
        }
        if (d == null && a.getWindowManager() != null) {
            try { d = a.getWindowManager().getDefaultDisplay(); } catch (Throwable ignored) {}
        }
        WindowManager.LayoutParams lp = w.getAttributes();
        if (unlockHighRefreshRate(lp, d)) {
            w.setAttributes(lp);
        }
    }

    /** Unlocks 90Hz/120Hz/144Hz high refresh rate on a WindowManager.LayoutParams. Returns true if modified. */
    static boolean unlockHighRefreshRate(WindowManager.LayoutParams lp, Display display) {
        if (lp == null) return false;
        boolean changed = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && display != null) {
                Display.Mode[] modes = display.getSupportedModes();
                Display.Mode maxMode = null;
                for (Display.Mode m : modes) {
                    if (maxMode == null || m.getRefreshRate() > maxMode.getRefreshRate()) {
                        maxMode = m;
                    }
                }
                if (maxMode != null) {
                    lp.preferredDisplayModeId = maxMode.getModeId();
                    changed = true;
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                lp.preferredRefreshRate = 120.0f;
                changed = true;
            }
        } catch (Throwable ignored) {}
        return changed;
    }
}
