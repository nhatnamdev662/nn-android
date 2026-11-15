package app.nn.agent;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The point_out tool's marks: a box or circle around an element, an arrow and a short label, so the user sees where to
 * look or what to do. Each mark is a small window of its own that touches go through: no full-screen overlay (see
 * ControlIndicator). Marks follow their element as the screen scrolls or the keyboard opens, and go when it's gone,
 * when the app in front changes, when the agent acts on the phone again, or when their time is up. They hide while
 * NN's panel is open and while the agent looks at or touches the screen.
 *
 * When the user does what a mark points at (flips the switch, taps the button, fills in the field and moves on), the
 * mark turns green with a check and goes; whoever waits for that (point_out with wait, ask with highlight) is told.
 */
final class Marks {
    /** One mark: around an element from read_screen (node), or around a point (node null). */
    static final class Mark {
        final AccessibilityNodeInfo node;
        /** A switch or checkbox in the element or its row: flipping it is the step. */
        final AccessibilityNodeInfo toggle;
        final Rect at;
        final String label;
        final boolean field, wasOn;
        final String startText;
        View view;
        WindowManager.LayoutParams lp;
        boolean onScreen = true, done, gone;
        /** What the user did, for the agent: "tapped “Device Connect”". */
        String did;
        // where it's drawn, in screen pixels (see layout)
        final RectF shape = new RectF(), labelBox = new RectF();
        float ax1, ay1, ax2, ay2;
        boolean arrow;
        String text = "";
        int step;
        boolean isSwipe;
        String direction;
        float sx1, sy1, sx2, sy2;
        boolean hasCustomEnd;
        int x2, y2;

        Mark(AccessibilityNodeInfo node, AccessibilityNodeInfo toggle, Rect at, String label) {
            this.node = node;
            this.toggle = toggle;
            this.at = new Rect(at);
            this.label = label == null ? "" : label.trim();
            field = node != null && toggle == null && node.isEditable();
            wasOn = toggle != null && toggle.isChecked();
            startText = (field || (node != null && node.getText() != null)) ? typed(node) : null;
        }

        /** NN Agent can tell when the user has done this one (it's on an element, not a bare point). */
        boolean detectable() {
            return node != null;
        }

        /** A tap does it (a button or a row, not a switch or a text field). */
        boolean byTap() {
            return node != null && toggle == null && !field;
        }

        /** What to call it for the agent: its label, else the element's own text. */
        String name() {
            if (!label.isEmpty()) return label;
            CharSequence t = node == null ? null : node.getText() != null ? node.getText() : node.getContentDescription();
            String s = t == null ? "" : t.toString().trim();
            if (s.isEmpty()) return "the marked element";
            return s.length() > 40 ? s.substring(0, 39) + "…" : s;
        }
    }

    private static final long FOLLOW_MS = 350, DONE_SHOWN_MS = 1100;

    private final NnService svc;
    private final WindowManager wm;
    private final float d;
    /** On the screen right now. */
    private final List<Mark> marks = new ArrayList<>();
    /** Everything the last show() put up, also the marks done or gone since (for the result). */
    private final List<Mark> shown = new ArrayList<>();
    private String style = "box";
    private long until;
    private boolean panelOpen, passThrough;
    private ValueAnimator pulse;
    private float glow = 1f;
    /** Told once how it ended, when someone waits for the user to do it (main thread). */
    private java.util.function.Consumer<String> onEnd;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG), aura = new Paint(Paint.ANTI_ALIAS_FLAG),
            labelBg = new Paint(Paint.ANTI_ALIAS_FLAG), labelEdge = new Paint(Paint.ANTI_ALIAS_FLAG),
            ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG), badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG),
            badgeHalo = new Paint(Paint.ANTI_ALIAS_FLAG), trailLine = new Paint(Paint.ANTI_ALIAS_FLAG),
            fingerAura = new Paint(Paint.ANTI_ALIAS_FLAG), fingerHalo = new Paint(Paint.ANTI_ALIAS_FLAG),
            fingerCore = new Paint(Paint.ANTI_ALIAS_FLAG), fingerDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG),
            badgeText = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    Marks(NnService svc) {
        this.svc = svc;
        this.wm = svc.getSystemService(WindowManager.class);
        this.d = svc.getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[]{halo, line, aura, labelEdge, ripplePaint, trailLine}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        badgeBg.setStyle(Paint.Style.FILL);
        badgeHalo.setStyle(Paint.Style.FILL);
        fingerAura.setStyle(Paint.Style.FILL);
        fingerHalo.setStyle(Paint.Style.STROKE);
        fingerCore.setStyle(Paint.Style.FILL);
        fingerDot.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14,
                svc.getResources().getDisplayMetrics()));
        textPaint.setTypeface(Ui.sans(svc, 600));
        badgeText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                svc.getResources().getDisplayMetrics()));
        badgeText.setTypeface(Ui.sans(svc, 700));
        badgeText.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Shows these marks instead of any others (main thread); style: box, circle or arrow. For ms, 0: until cleared.
     * onEnd (may be null) hears once how it ended: "DONE: …" when the user did what every mark points at, else why not.
     */
    void show(List<Mark> list, String style, long ms, java.util.function.Consumer<String> onEnd) {
        clear("CLEARED: other marks replaced these before the user did it.");
        this.style = style == null ? "box" : style;
        this.onEnd = onEnd;
        until = ms > 0 ? SystemClock.uptimeMillis() + ms : Long.MAX_VALUE;
        halo.setColor(Theme.withAlpha(Ui.BG, 0xB0));
        labelEdge.setColor(Ui.BG);
        badgeHalo.setColor(Theme.withAlpha(Ui.BG, 0xD0));
        textPaint.setColor(Ui.ON_ACCENT);
        badgeText.setColor(Ui.ON_ACCENT);
        boolean taps = false;
        int stepIdx = 1;
        boolean multi = list.size() > 1;
        for (Mark m : list) {
            if (multi) m.step = stepIdx++;
            if (add(m)) {
                shown.add(m);
                taps |= m.byTap() || m.isSwipe;
            }
        }
        if (marks.isEmpty()) {
            end(list.isEmpty() ? "CLEARED: no marks." : "GONE: nothing to point at is on the screen anymore.");
            return;
        }
        svc.listenForClicks(taps); // only while a mark waits for a tap
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(900);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> {
            glow = (float) a.getAnimatedValue();
            for (Mark m : marks) m.view.invalidate();
        });
        pulse.start();
        vibrate(VibrationEffect.EFFECT_TICK);
        svc.mainHandler().postDelayed(follow, FOLLOW_MS);
    }

    /** Takes every mark down (main thread); a waiter hears why. */
    void clear() {
        clear("CLEARED: the marks were taken down before the user did it.");
    }

    void clear(String why) {
        if (marks.isEmpty() && shown.isEmpty() && onEnd == null) return; // nothing up (most app switches)
        end(why);
        takeDown();
        shown.clear();
    }

    /** Someone waits for the user to do what the marks point at. */
    boolean waiting() {
        return onEnd != null;
    }

    /** NN's panel is open: the marks wait behind it (main thread). */
    void setPanelOpen(boolean open) {
        panelOpen = open;
        applyVisibility();
    }

    /** The agent looks at or touches the screen: out of the way meanwhile (main thread). */
    void setPassThrough(boolean on) {
        passThrough = on;
        applyVisibility();
    }

    /** Something was tapped (an accessibility event; main thread): the step of a mark it belongs to is done. */
    void onClicked(AccessibilityNodeInfo src, long uptime) {
        if (src == null || svc.autoPauser().own(uptime)) return; // NN's own tap isn't the user doing it
        for (Mark m : new ArrayList<>(marks)) {
            if (!m.done && (m.byTap() || m.isSwipe) && (within(src, m.node) || within(m.node, src))) {
                markDone(m, (m.isSwipe ? "swiped “" : "tapped “") + m.name() + "”");
            }
        }
    }

    /** The screen or a container was scrolled/swiped (an accessibility event; main thread). */
    void onScrolled(AccessibilityNodeInfo src, long uptime) {
        if (svc.autoPauser().own(uptime)) return;
        for (Mark m : new ArrayList<>(marks)) {
            if (m.isSwipe && !m.done) {
                markDone(m, "swiped “" + m.name() + "”");
            }
        }
    }

    /** Is anc the node itself or one of its nearest ancestors? */
    private static boolean within(AccessibilityNodeInfo n, AccessibilityNodeInfo anc) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; cur != null && i < 6; i++) {
            if (cur.equals(anc)) return true;
            cur = cur.getParent();
        }
        return false;
    }

    private static String typed(AccessibilityNodeInfo n) {
        CharSequence t = n.isShowingHintText() ? null : n.getText();
        return t == null ? "" : t.toString();
    }

    private void applyVisibility() {
        for (Mark m : marks) m.view.setVisibility(panelOpen || passThrough || !m.onScreen ? View.INVISIBLE : View.VISIBLE);
    }

    private boolean add(Mark m) {
        m.view = new View(svc) {
            @Override
            protected void onDraw(Canvas c) {
                paint(m, this, c);
            }
        };
        layout(m);
        m.lp = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        m.lp.gravity = Gravity.TOP | Gravity.START;
        m.lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        m.lp.setFitInsetsTypes(0);
        frame(m);
        try {
            wm.addView(m.view, m.lp);
        } catch (Exception e) {
            return false;
        }
        marks.add(m);
        m.view.setVisibility(panelOpen || passThrough ? View.INVISIBLE : View.VISIBLE);
        return true;
    }

    private void remove(Mark m) {
        try {
            wm.removeView(m.view);
        } catch (Exception ignored) {
        }
        marks.remove(m);
        if (marks.isEmpty()) takeDown();
    }

    /** The windows, the pulse and the follower go (what was shown stays known for the result). */
    private void takeDown() {
        svc.mainHandler().removeCallbacks(follow);
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        for (Mark m : marks) {
            try {
                wm.removeView(m.view);
            } catch (Exception ignored) {
            }
        }
        marks.clear();
        svc.listenForClicks(false);
    }

    /** The user did this one: green, a check, and it goes. */
    private void markDone(Mark m, String did) {
        if (m.done || m.gone) return;
        m.done = true;
        m.did = did;
        vibrate(VibrationEffect.EFFECT_CLICK);
        layout(m); // the label gets its check
        frame(m);
        try {
            wm.updateViewLayout(m.view, m.lp);
        } catch (Exception ignored) {
        }
        m.view.invalidate();
        m.view.animate().alpha(0f).setStartDelay(DONE_SHOWN_MS - 400).setDuration(400).start();
        svc.mainHandler().postDelayed(() -> remove(m), DONE_SHOWN_MS);
        settle();
    }

    private void vibrate(int effectId) {
        try {
            Vibrator vib = svc.getSystemService(Vibrator.class);
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 29) {
                    vib.vibrate(VibrationEffect.createPredefined(effectId));
                } else {
                    vib.vibrate(20);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Every mark NN Agent can watch is done or gone: tell the waiter. */
    private void settle() {
        if (onEnd == null) return;
        List<String> did = new ArrayList<>(), missed = new ArrayList<>();
        for (Mark m : shown) {
            if (!m.detectable()) continue;
            if (m.done) did.add(m.did);
            else if (m.gone) missed.add("“" + m.name() + "”");
            else return; // still waiting for this one
        }
        if (did.isEmpty() && missed.isEmpty()) return;
        if (missed.isEmpty()) end("DONE: the user did it: " + TextUtils.join("; ", did) + ".");
        else if (did.isEmpty()) end("GONE: " + TextUtils.join(", ", missed) + " left the screen before the user did it "
                + "(they may have gone elsewhere).");
        else end("PARTLY DONE: the user " + TextUtils.join("; ", did) + ", but " + TextUtils.join(", ", missed)
                + " left the screen first.");
    }

    private void end(String result) {
        java.util.function.Consumer<String> c = onEnd;
        onEnd = null;
        if (c != null) c.accept(result);
    }

    /**
     * Follows each element: moved (a scroll, the keyboard) → the mark moves; off screen → hidden; gone → removed.
     * Also notices a flipped switch, and a field typed in that the user has left.
     */
    private final Runnable follow = new Runnable() {
        @Override
        public void run() {
            if (SystemClock.uptimeMillis() >= until) {
                clear("NOT DONE: the user didn't do it in time.");
                return;
            }
            Rect r = new Rect();
            for (Mark m : new ArrayList<>(marks)) {
                if (m.node == null || m.done) continue;
                if (!m.node.refresh() || !m.node.isVisibleToUser()) {
                    if (m.isSwipe) {
                        markDone(m, "swiped “" + m.name() + "”");
                    } else {
                        m.gone = true;
                        remove(m);
                    }
                    continue;
                }
                if (m.toggle != null && m.toggle.refresh() && m.toggle.isChecked() != m.wasOn) {
                    markDone(m, (m.wasOn ? "turned off “" : "turned on “") + m.name() + "”");
                    continue;
                }
                if (m.field && !m.node.isFocused() && !typed(m.node).isEmpty() && !typed(m.node).equals(m.startText)) {
                    markDone(m, "filled in “" + m.name() + "”");
                    continue;
                }
                if (m.isSwipe && m.startText != null) {
                    String cur = typed(m.node);
                    if (!cur.isEmpty() && !cur.equals(m.startText)) {
                        markDone(m, "swiped “" + m.name() + "”");
                        continue;
                    }
                }
                m.node.getBoundsInScreen(r);
                m.onScreen = m.node.isVisibleToUser() && !r.isEmpty();
                if (m.onScreen && !r.equals(m.at)) {
                    if (m.isSwipe && (Math.abs(r.centerX() - m.at.centerX()) > 30 * d || Math.abs(r.centerY() - m.at.centerY()) > 30 * d)) {
                        markDone(m, "swiped “" + m.name() + "”");
                        continue;
                    }
                    m.at.set(r);
                    layout(m);
                    frame(m);
                    try {
                        wm.updateViewLayout(m.view, m.lp);
                    } catch (Exception ignored) {
                    }
                    m.view.invalidate();
                }
            }
            settle();
            if (marks.isEmpty()) return;
            applyVisibility();
            svc.mainHandler().postDelayed(this, FOLLOW_MS);
        }
    };

    // ---------------------------------------------------------------- drawing

    /** Where the shape, label and arrow go for the element's place now, all in screen pixels. */
    private void layout(Mark m) {
        Rect screen = wm.getCurrentWindowMetrics().getBounds();
        RectF t = new RectF(m.at);
        if ("circle".equals(style)) {
            float w = Math.max(t.width() * 1.25f + 12 * d, 48 * d), h = Math.max(t.height() * 1.35f + 12 * d, 48 * d);
            t.set(t.centerX() - w / 2, t.centerY() - h / 2, t.centerX() + w / 2, t.centerY() + h / 2);
        } else {
            t.inset(-6 * d, -6 * d);
        }
        m.shape.set(t);
        m.arrow = "arrow".equals(style) || !m.label.isEmpty();
        float maxText = screen.width() - 56 * d;
        String label = m.done ? "✓ " + m.label : m.label;
        m.text = m.label.isEmpty() ? (m.done ? "✓" : "")
                : TextUtils.ellipsize(label, textPaint, maxText, TextUtils.TruncateAt.END).toString();
        float gap = m.arrow ? 34 * d : 0;
        float lw = m.text.isEmpty() ? 0 : textPaint.measureText(m.text) + 24 * d;
        float lh = m.text.isEmpty() ? 0 : textPaint.getFontSpacing() + 14 * d;
        float top = 32 * d, bottom = screen.height() - 56 * d;
        boolean above = t.top - gap - lh >= top || (t.bottom + gap + lh > bottom && t.top - screen.top > bottom - t.bottom);
        float lx = Math.max(12 * d, Math.min(t.centerX() - lw / 2, screen.width() - 12 * d - lw));
        float ly = above ? t.top - gap - lh : t.bottom + gap;
        m.labelBox.set(lx, ly, lx + lw, ly + lh);
        float tipX = "arrow".equals(style) ? t.centerX() : Math.max(t.left + 10 * d, Math.min(t.centerX(), t.right - 10 * d));
        m.ax2 = tipX;
        m.ay2 = above ? t.top + ("arrow".equals(style) ? 4 * d : -3 * d) : t.bottom - ("arrow".equals(style) ? 4 * d : -3 * d);
        m.ax1 = lw > 0 ? Math.max(lx + 16 * d, Math.min(tipX, lx + lw - 16 * d)) : tipX;
        m.ay1 = lw > 0 ? (above ? ly + lh : ly) : (above ? m.ay2 - gap : m.ay2 + gap);

        boolean swipe = "swipe".equals(style) || m.isSwipe;
        if (!swipe && m.label != null) {
            String lower = m.label.toLowerCase();
            if (lower.contains("vuốt") || lower.contains("swipe") || lower.contains("trượt")) {
                swipe = true;
            }
        }
        m.isSwipe = swipe;
        if (m.isSwipe) {
            if (m.hasCustomEnd) {
                m.sx1 = t.centerX();
                m.sy1 = t.centerY();
                m.sx2 = m.x2;
                m.sy2 = m.y2;
            } else {
                String dir = m.direction;
                if (dir == null || dir.isEmpty()) {
                    String lower = m.label != null ? m.label.toLowerCase() : "";
                    if (lower.contains("phải") || lower.contains("right")) dir = "right";
                    else if (lower.contains("lên") || lower.contains("up")) dir = "up";
                    else if (lower.contains("xuống") || lower.contains("down")) dir = "down";
                    else dir = "left";
                }
                float spanH = Math.max(90 * d, Math.min(t.width() * 0.9f, 220 * d));
                float spanV = Math.max(90 * d, Math.min(t.height() * 0.9f, 220 * d));
                if ("right".equals(dir)) {
                    m.sx1 = t.centerX() - spanH / 2;
                    m.sy1 = t.centerY();
                    m.sx2 = t.centerX() + spanH / 2;
                    m.sy2 = t.centerY();
                } else if ("up".equals(dir)) {
                    m.sx1 = t.centerX();
                    m.sy1 = t.centerY() + spanV / 2;
                    m.sx2 = t.centerX();
                    m.sy2 = t.centerY() - spanV / 2;
                } else if ("down".equals(dir)) {
                    m.sx1 = t.centerX();
                    m.sy1 = t.centerY() - spanV / 2;
                    m.sx2 = t.centerX();
                    m.sy2 = t.centerY() + spanV / 2;
                } else { // "left"
                    m.sx1 = t.centerX() + spanH / 2;
                    m.sy1 = t.centerY();
                    m.sx2 = t.centerX() - spanH / 2;
                    m.sy2 = t.centerY();
                }
            }
        }
    }

    /** The mark's window: just around what it draws (plus room for the glow and ripple). */
    private void frame(Mark m) {
        RectF f = new RectF(m.shape);
        if (!"arrow".equals(style)) f.inset(-18 * d, -18 * d);
        if (m.labelBox.width() > 0) f.union(m.labelBox);
        if (m.arrow) {
            f.union(m.ax1, m.ay1);
            f.union(m.ax2, m.ay2);
        }
        if (m.isSwipe) {
            f.union(m.sx1, m.sy1);
            f.union(m.sx2, m.sy2);
            f.inset(-26 * d, -26 * d);
        }
        f.inset(-14 * d, -14 * d);
        m.lp.x = Math.round(f.left);
        m.lp.y = Math.round(f.top);
        m.lp.width = Math.max(1, Math.round(f.width()));
        m.lp.height = Math.max(1, Math.round(f.height()));
    }

    private void paint(Mark m, View v, Canvas c) {
        // in screen pixels, wherever the system actually put the window
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        c.translate(-loc[0], -loc[1]);
        int color = m.done ? Ui.OK : Ui.ACCENT; // done: green
        float g = m.done ? 1f : glow;
        line.setColor(color);
        fill.setColor(Theme.withAlpha(color, 0x1A));
        labelBg.setColor(color);
        badgeBg.setColor(color);
        float r = 14 * d;
        aura.setColor(Theme.withAlpha(color, (int) (0x22 + 0x44 * g)));
        aura.setStrokeWidth((8 + 6 * g) * d);
        halo.setStrokeWidth(7 * d);
        line.setStrokeWidth(3 * d);

        // Animated Swipe Gesture & Touch Trail
        if (m.isSwipe && !m.done) {
            trailLine.setStrokeWidth(3.5f * d);
            trailLine.setColor(Theme.withAlpha(color, 0x66));
            c.drawLine(m.sx1, m.sy1, m.sx2, m.sy2, trailLine);

            arrow(c, m.sx1, m.sy1, m.sx2, m.sy2, halo, 7 * d);
            arrow(c, m.sx1, m.sy1, m.sx2, m.sy2, line, 3.5f * d);

            long now = SystemClock.uptimeMillis();
            float phase = (now % 1400) / 1400f; // 0..1 over 1.4s
            float progress = (float) (Math.sin((phase - 0.5f) * Math.PI) * 0.5f + 0.5f);
            float fx = m.sx1 + (m.sx2 - m.sx1) * progress;
            float fy = m.sy1 + (m.sy2 - m.sy1) * progress;

            float touchAlpha = phase < 0.15f ? (phase / 0.15f) : (phase > 0.85f ? (1f - (phase - 0.85f) / 0.15f) : 1f);
            int pointAlpha = Math.max(0, Math.min(255, (int) (255 * touchAlpha)));

            fingerAura.setColor(Theme.withAlpha(color, (int) (0x33 * touchAlpha)));
            c.drawCircle(fx, fy, 22 * d, fingerAura);

            fingerHalo.setStrokeWidth(3 * d);
            fingerHalo.setColor(Theme.withAlpha(Ui.BG, (int) (0xB0 * touchAlpha)));
            c.drawCircle(fx, fy, 13 * d, fingerHalo);

            fingerCore.setColor(Theme.withAlpha(color, pointAlpha));
            c.drawCircle(fx, fy, 12 * d, fingerCore);

            fingerDot.setColor(Theme.withAlpha(Ui.ON_ACCENT, (int) (0xCC * touchAlpha)));
            c.drawCircle(fx, fy, 4 * d, fingerDot);
        }

        // Radar Beacon Ripple Wave: radiates outward with fading alpha
        if (!m.done && !"arrow".equals(style)) {
            float expand = (4 + 14 * g) * d;
            int rippleAlpha = Math.max(0, (int) (0x77 * (1f - g)));
            ripplePaint.setColor(Theme.withAlpha(color, rippleAlpha));
            ripplePaint.setStrokeWidth(2.5f * d);
            RectF rippleRect = new RectF(m.shape);
            rippleRect.inset(-expand, -expand);
            if ("circle".equals(style)) {
                c.drawOval(rippleRect, ripplePaint);
            } else {
                c.drawRoundRect(rippleRect, r + expand, r + expand, ripplePaint);
            }
        }

        if ("circle".equals(style)) {
            c.drawOval(m.shape, fill);
            c.drawOval(m.shape, aura);
            c.drawOval(m.shape, halo);
            c.drawOval(m.shape, line);
        } else if (!"arrow".equals(style)) {
            c.drawRoundRect(m.shape, r, r, fill);
            c.drawRoundRect(m.shape, r, r, aura);
            c.drawRoundRect(m.shape, r, r, halo);
            c.drawRoundRect(m.shape, r, r, line);
        }
        if (m.arrow) {
            arrow(c, m, halo, 7 * d);
            arrow(c, m, line, 3 * d);
        }
        if (!m.text.isEmpty()) {
            labelEdge.setStrokeWidth(2 * d);
            float lr = m.labelBox.height() / 2;
            c.drawRoundRect(m.labelBox, lr, lr, labelBg);
            c.drawRoundRect(m.labelBox, lr, lr, labelEdge);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float base = m.labelBox.centerY() - (fm.ascent + fm.descent) / 2;
            c.drawText(m.text, m.labelBox.left + 12 * d, base, textPaint);
        }
        // Step Badge (① ② ③) on top-left corner if multi-step
        if (m.step > 0 && !"arrow".equals(style)) {
            float bx = m.shape.left + 6 * d;
            float by = m.shape.top + 6 * d;
            float br = 10 * d;
            c.drawCircle(bx, by, br + 2 * d, badgeHalo);
            c.drawCircle(bx, by, br, badgeBg);
            Paint.FontMetrics bfm = badgeText.getFontMetrics();
            float bbase = by - (bfm.ascent + bfm.descent) / 2;
            c.drawText(m.done ? "✓" : String.valueOf(m.step), bx, bbase, badgeText);
        }
    }

    private void arrow(Canvas c, Mark m, Paint p, float width) {
        arrow(c, m.ax1, m.ay1, m.ax2, m.ay2, p, width);
    }

    private void arrow(Canvas c, float x1, float y1, float x2, float y2, Paint p, float width) {
        p.setStrokeWidth(width);
        c.drawLine(x1, y1, x2, y2, p);
        double a = Math.atan2(y2 - y1, x2 - x1), head = 11 * d, spread = Math.toRadians(30);
        c.drawLine(x2, y2, (float) (x2 - head * Math.cos(a - spread)), (float) (y2 - head * Math.sin(a - spread)), p);
        c.drawLine(x2, y2, (float) (x2 - head * Math.cos(a + spread)), (float) (y2 - head * Math.sin(a + spread)), p);
    }
}
