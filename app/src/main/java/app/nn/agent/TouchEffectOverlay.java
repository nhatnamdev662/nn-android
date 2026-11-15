package app.nn.agent;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Visual effects when NN Agent operates the phone (similar to Claude Computer Use):
 * 1. Ambient breathing neon border glow around the entire screen perimeter + top HUD indicator while agent is in control.
 * 2. High-visibility instant tap indicator with double shockwave ripples, glowing reticle, and action tag ("CHẠM" / "GIỮ").
 * 3. Deliberate, smooth gliding fingertip pointer with multi-layered luminous trail, landing pulse, and directional HUD pill along swipe/scroll vectors.
 *
 * Drawn in a transparent, touch-through overlay window (FLAG_NOT_TOUCHABLE).
 */
final class TouchEffectOverlay {

    private static abstract class Effect {
        final long start = SystemClock.uptimeMillis();
        abstract boolean draw(Canvas c, float d, int color, Paint pCore, Paint pAura, Paint pHalo, Paint pText);
    }

    private static final class TapEffect extends Effect {
        final float x, y;
        final boolean longPress;
        final String tag;
        private static final long DURATION_MS = 680L;

        TapEffect(NnService svc, float x, float y, boolean longPress) {
            this.x = x;
            this.y = y;
            this.longPress = longPress;
            this.tag = svc.getString(longPress ? R.string.touch_overlay_hold : R.string.touch_overlay_tap);
        }

        @Override
        boolean draw(Canvas c, float d, int color, Paint pCore, Paint pAura, Paint pHalo, Paint pText) {
            long elapsed = SystemClock.uptimeMillis() - start;
            if (elapsed >= DURATION_MS) return false;
            float progress = elapsed / (float) DURATION_MS;
            float alpha = 1f - progress;

            // Ease-out curve for expanding shockwaves
            float ease = (float) (1.0 - Math.pow(1.0 - progress, 2.5));

            // Shockwave 1: Primary expanding ripple wave
            float maxR1 = 46 * d;
            float r1 = 12 * d + (maxR1 - 12 * d) * ease;
            pAura.setStyle(Paint.Style.STROKE);
            pAura.setStrokeWidth(3f * d * (1f - progress * 0.6f));
            pAura.setColor(Theme.withAlpha(color, (int) (0xEE * Math.max(0, 1f - progress * 1.2f))));
            c.drawCircle(x, y, r1, pAura);

            // Shockwave 2: Secondary harmonic ripple (delayed slightly)
            if (progress > 0.10f) {
                float p2 = (progress - 0.10f) / 0.90f;
                float ease2 = (float) (1.0 - Math.pow(1.0 - p2, 2.0));
                float maxR2 = 32 * d;
                float r2 = 8 * d + (maxR2 - 8 * d) * ease2;
                pAura.setStrokeWidth(1.8f * d * (1f - p2));
                pAura.setColor(Theme.withAlpha(color, (int) (0x99 * (1f - p2))));
                c.drawCircle(x, y, r2, pAura);
            }

            // Outer dark contrast halo (ensures pointer is sharp on white backgrounds)
            pHalo.setStyle(Paint.Style.FILL);
            pHalo.setColor(Theme.withAlpha(0x000000, (int) (0x77 * alpha)));
            c.drawCircle(x, y, 16 * d, pHalo);

            // Glowing neon reticle ring
            pHalo.setStyle(Paint.Style.STROKE);
            pHalo.setStrokeWidth(2.2f * d);
            pHalo.setColor(Theme.withAlpha(color, (int) (0xEE * alpha)));
            c.drawCircle(x, y, 14 * d, pHalo);

            // Core glowing dot (laser touch)
            pCore.setStyle(Paint.Style.FILL);
            pCore.setColor(Theme.withAlpha(color, (int) (0xFF * alpha)));
            c.drawCircle(x, y, 11 * d, pCore);

            // Specular center bright spark (pure white, gives 3D depth)
            pCore.setColor(Theme.withAlpha(0xFFFFFF, (int) (0xEE * alpha)));
            c.drawCircle(x, y, 4 * d, pCore);

            // Action HUD Tag: small high-contrast pill above touch point ("CHẠM" or "GIỮ")
            pText.setStyle(Paint.Style.FILL);
            pText.setTextSize(10 * d);
            pText.setTypeface(Typeface.DEFAULT_BOLD);
            float tw = pText.measureText(tag);
            float pw = tw + 12 * d;
            float ph = 16 * d;
            float px = x - pw / 2f;
            float py = y - 22 * d - ph;

            if (py > 24 * d) {
                // Background capsule
                pCore.setColor(Theme.withAlpha(0x18181B, (int) (0xEE * alpha)));
                RectF pillR = new RectF(px, py, px + pw, py + ph);
                c.drawRoundRect(pillR, ph / 2f, ph / 2f, pCore);
                // Border
                pAura.setStyle(Paint.Style.STROKE);
                pAura.setStrokeWidth(1.2f * d);
                pAura.setColor(Theme.withAlpha(color, (int) (0xAA * alpha)));
                c.drawRoundRect(pillR, ph / 2f, ph / 2f, pAura);
                // Text
                pText.setColor(Theme.withAlpha(0xFFFFFF, (int) (0xFF * alpha)));
                c.drawText(tag, px + 6 * d, py + ph - 4.5f * d, pText);
            }

            return true;
        }
    }

    private static final class SwipeEffect extends Effect {
        final float x1, y1, x2, y2;
        final long animDuration;
        final String dirText;
        private static final long FADE_MS = 280L;

        SwipeEffect(NnService svc, float x1, float y1, float x2, float y2, long duration) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            // Visual glide duration: deliberate, smooth, visible human-like speed (~620ms)
            this.animDuration = Math.max(580L, Math.min(950L, duration * 2L));
            this.dirText = directionLabel(svc, x1, y1, x2, y2);
        }

        @Override
        boolean draw(Canvas c, float d, int color, Paint pCore, Paint pAura, Paint pHalo, Paint pText) {
            long elapsed = SystemClock.uptimeMillis() - start;
            long total = animDuration + FADE_MS;
            if (elapsed >= total) return false;

            float moveProgress = Math.min(1f, elapsed / (float) animDuration);
            float fadeProgress = elapsed > animDuration ? (elapsed - animDuration) / (float) FADE_MS : 0f;
            float alpha = 1f - fadeProgress;

            // Ease in-out sinusoidal motion
            float curved = (float) (Math.sin((moveProgress - 0.5f) * Math.PI) * 0.5f + 0.5f);
            float fx = x1 + (x2 - x1) * curved;
            float fy = y1 + (y2 - y1) * curved;

            // 1. Origin Anchor Dot (shows where the gesture began)
            float originAlpha = Math.max(0, 1f - moveProgress * 1.6f) * alpha;
            if (originAlpha > 0.05f) {
                pHalo.setStyle(Paint.Style.STROKE);
                pHalo.setStrokeWidth(2f * d);
                pHalo.setColor(Theme.withAlpha(color, (int) (0x99 * originAlpha)));
                c.drawCircle(x1, y1, 10 * d, pHalo);
                pCore.setStyle(Paint.Style.FILL);
                pCore.setColor(Theme.withAlpha(color, (int) (0xCC * originAlpha)));
                c.drawCircle(x1, y1, 5 * d, pCore);
            }

            // 2. Luminous Motion Trail
            // Outer diffuse aura
            pAura.setStyle(Paint.Style.STROKE);
            pAura.setStrokeCap(Paint.Cap.ROUND);
            pAura.setStrokeWidth(8 * d);
            pAura.setColor(Theme.withAlpha(color, (int) (0x44 * alpha)));
            c.drawLine(x1, y1, fx, fy, pAura);

            // Inner intense neon beam
            pAura.setStrokeWidth(3.8f * d);
            pAura.setColor(Theme.withAlpha(color, (int) (0xDD * alpha)));
            c.drawLine(x1, y1, fx, fy, pAura);

            // Center laser wire
            pAura.setStrokeWidth(1.5f * d);
            pAura.setColor(Theme.withAlpha(0xFFFFFF, (int) (0xAA * alpha)));
            c.drawLine(x1, y1, fx, fy, pAura);

            // 3. Gliding Fingertip (The Virtual Agent Pointer)
            // Outer dark halo for contrast
            pHalo.setStyle(Paint.Style.FILL);
            pHalo.setColor(Theme.withAlpha(0x000000, (int) (0x77 * alpha)));
            c.drawCircle(fx, fy, 16 * d, pHalo);

            // Glowing neon fingertip ring
            pHalo.setStyle(Paint.Style.STROKE);
            pHalo.setStrokeWidth(2.5f * d);
            pHalo.setColor(Theme.withAlpha(color, (int) (0xEE * alpha)));
            c.drawCircle(fx, fy, 14 * d, pHalo);

            // Fingertip disc
            pCore.setStyle(Paint.Style.FILL);
            pCore.setColor(Theme.withAlpha(color, (int) (0xDD * alpha)));
            c.drawCircle(fx, fy, 11 * d, pCore);

            // Specular center light
            pCore.setColor(Theme.withAlpha(0xFFFFFF, (int) (0xFF * alpha)));
            c.drawCircle(fx, fy, 4.5f * d, pCore);

            // 4. Destination Landing Wave (blooms as finger approaches target)
            if (moveProgress > 0.80f) {
                float destP = (moveProgress - 0.80f) / 0.20f;
                float destR = (8 + 20 * destP) * d;
                pAura.setStyle(Paint.Style.STROKE);
                pAura.setStrokeWidth(2f * d * (1f - destP));
                pAura.setColor(Theme.withAlpha(color, (int) (0xAA * (1f - destP) * alpha)));
                c.drawCircle(x2, y2, destR, pAura);
            }

            // 5. Directional HUD Pill ("VUỐT ↓" / "VUỐT ↑" / etc.)
            pText.setStyle(Paint.Style.FILL);
            pText.setTextSize(10 * d);
            pText.setTypeface(Typeface.DEFAULT_BOLD);
            float tw = pText.measureText(dirText);
            float pw = tw + 12 * d;
            float ph = 16 * d;
            float px = fx - pw / 2f;
            float py = fy - 22 * d - ph;

            if (py > 24 * d) {
                pCore.setColor(Theme.withAlpha(0x18181B, (int) (0xEE * alpha)));
                RectF pillR = new RectF(px, py, px + pw, py + ph);
                c.drawRoundRect(pillR, ph / 2f, ph / 2f, pCore);
                pAura.setStyle(Paint.Style.STROKE);
                pAura.setStrokeWidth(1.2f * d);
                pAura.setColor(Theme.withAlpha(color, (int) (0xAA * alpha)));
                c.drawRoundRect(pillR, ph / 2f, ph / 2f, pAura);
                pText.setColor(Theme.withAlpha(0xFFFFFF, (int) (0xFF * alpha)));
                c.drawText(dirText, px + 6 * d, py + ph - 4.5f * d, pText);
            }

            return true;
        }

        private static String directionLabel(NnService svc, float x1, float y1, float x2, float y2) {
            float dx = x2 - x1, dy = y2 - y1;
            if (Math.abs(dy) >= Math.abs(dx)) {
                return svc.getString(dy > 0 ? R.string.touch_overlay_swipe_down : R.string.touch_overlay_swipe_up);
            } else {
                return svc.getString(dx > 0 ? R.string.touch_overlay_swipe_right : R.string.touch_overlay_swipe_left);
            }
        }
    }

    private final NnService svc;
    private final WindowManager wm;
    private final float d;
    private final View view;
    private WindowManager.LayoutParams lp;
    private boolean attached;

    private boolean borderActive;
    private float borderAlpha = 0f;
    private ValueAnimator pulseAnimator;
    private ValueAnimator fadeAnimator;

    private final List<Effect> effects = new ArrayList<>();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderAura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    TouchEffectOverlay(NnService svc) {
        this.svc = svc;
        this.wm = svc.getSystemService(WindowManager.class);
        this.d = svc.getResources().getDisplayMetrics().density;

        borderPaint.setStyle(Paint.Style.STROKE);
        borderAura.setStyle(Paint.Style.STROKE);

        view = new View(svc) {
            @Override
            protected void onDraw(Canvas c) {
                render(c);
            }
        };

        initWindow();
    }

    private void initWindow() {
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
    }

    private void ensureAttached() {
        if (attached) return;
        try {
            wm.addView(view, lp);
            attached = true;
            view.setVisibility(View.GONE);
        } catch (Exception ignored) {
        }
    }

    void showTap(float x, float y) {
        showTap(x, y, false);
    }

    void showTap(float x, float y, boolean longPress) {
        if (!Prefs.touchEffects(svc)) return;
        svc.runOnMain(() -> {
            ensureAttached();
            effects.add(new TapEffect(svc, x, y, longPress));
            view.setVisibility(View.VISIBLE);
            view.invalidate();
        });
    }

    void showSwipe(float x1, float y1, float x2, float y2, long duration) {
        if (!Prefs.touchEffects(svc)) return;
        svc.runOnMain(() -> {
            ensureAttached();
            effects.add(new SwipeEffect(svc, x1, y1, x2, y2, duration));
            view.setVisibility(View.VISIBLE);
            view.invalidate();
        });
    }

    void startBorderGlow() {
        if (!Prefs.touchEffects(svc)) return;
        svc.runOnMain(() -> {
            ensureAttached();
            if (borderActive) return;
            borderActive = true;
            if (fadeAnimator != null) fadeAnimator.cancel();

            if (pulseAnimator == null) {
                pulseAnimator = ValueAnimator.ofFloat(0.35f, 0.90f);
                pulseAnimator.setDuration(1100);
                pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
                pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                pulseAnimator.addUpdateListener(a -> {
                    borderAlpha = (float) a.getAnimatedValue();
                    if (view.getVisibility() == View.VISIBLE) {
                        view.invalidate();
                    }
                });
            }
            pulseAnimator.start();
            view.setVisibility(View.VISIBLE);
            view.invalidate();
        });
    }

    void stopBorderGlow() {
        svc.runOnMain(() -> {
            if (!borderActive) return;
            borderActive = false;
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
            }
            fadeAnimator = ValueAnimator.ofFloat(borderAlpha, 0f);
            fadeAnimator.setDuration(350);
            fadeAnimator.addUpdateListener(a -> {
                borderAlpha = (float) a.getAnimatedValue();
                view.invalidate();
            });
            fadeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    borderAlpha = 0f;
                    if (effects.isEmpty()) {
                        view.setVisibility(View.GONE);
                    }
                }
            });
            fadeAnimator.start();
        });
    }

    private void render(Canvas c) {
        Rect screen = wm.getCurrentWindowMetrics().getBounds();
        int color = Ui.ACCENT;

        // 1. Ambient breathing border glow (Edge Lighting + Top HUD Indicator)
        if (borderAlpha > 0.01f) {
            float stroke = 4.5f * d;
            borderPaint.setStrokeWidth(stroke);
            borderPaint.setColor(Theme.withAlpha(color, (int) (0xEE * borderAlpha)));

            borderAura.setStrokeWidth(stroke * 2.8f);
            borderAura.setColor(Theme.withAlpha(color, (int) (0x38 * borderAlpha)));

            RectF r = new RectF(screen);
            float inset = stroke * 1.3f;
            r.inset(inset, inset);
            float corner = 28 * d;
            c.drawRoundRect(r, corner, corner, borderAura);
            c.drawRoundRect(r, corner, corner, borderPaint);

            // Top Status Capsule (subtle HUD banner below notch)
            float cx = screen.centerX();
            String title = svc.getString(R.string.touch_overlay_controlling);
            textPaint.setTextSize(10.5f * d);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            float tw = textPaint.measureText(title);
            float bw = tw + 16 * d;
            float bh = 20 * d;
            float by = 30 * d;
            RectF badgeR = new RectF(cx - bw / 2f, by, cx + bw / 2f, by + bh);

            corePaint.setStyle(Paint.Style.FILL);
            corePaint.setColor(Theme.withAlpha(0x18181B, (int) (0xEE * borderAlpha)));
            c.drawRoundRect(badgeR, bh / 2f, bh / 2f, corePaint);

            auraPaint.setStyle(Paint.Style.STROKE);
            auraPaint.setStrokeWidth(1.2f * d);
            auraPaint.setColor(Theme.withAlpha(color, (int) (0xBB * borderAlpha)));
            c.drawRoundRect(badgeR, bh / 2f, bh / 2f, auraPaint);

            textPaint.setColor(Theme.withAlpha(color, (int) (0xFF * borderAlpha)));
            c.drawText(title, cx - tw / 2f, by + bh - 6 * d, textPaint);
        }

        // 2. Active Touch Effects
        boolean hasActiveEffects = false;
        if (!effects.isEmpty()) {
            Iterator<Effect> it = effects.iterator();
            while (it.hasNext()) {
                Effect e = it.next();
                if (e.draw(c, d, color, corePaint, auraPaint, haloPaint, textPaint)) {
                    hasActiveEffects = true;
                } else {
                    it.remove();
                }
            }
        }

        if (hasActiveEffects) {
            view.postInvalidateOnAnimation();
        } else if (!borderActive && borderAlpha <= 0.01f) {
            view.setVisibility(View.GONE);
        }
    }

    void destroy() {
        svc.runOnMain(() -> {
            if (pulseAnimator != null) pulseAnimator.cancel();
            if (fadeAnimator != null) fadeAnimator.cancel();
            effects.clear();
            if (attached) {
                try {
                    wm.removeView(view);
                } catch (Exception ignored) {
                }
                attached = false;
            }
        });
    }
}
