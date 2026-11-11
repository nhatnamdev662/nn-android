package app.nn.agent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import static app.nn.agent.Ui.*;

/**
 * The bubble's compact live PiP card of the background browser while an agent uses it:
 * mini header bar (globe + host + crisp X close button) over a live frame viewport.
 * Tap thumbnail to open full screen (BrowserActivity); tap X or swipe sideways to dismiss cleanly.
 */
final class BrowserThumb extends LinearLayout implements Browser.Frames {
    interface Actions {
        void open();

        void dismiss();
    }

    private final Actions actions;
    private final LinearLayout header;
    private final View divider;
    private final ImageView globe;
    private final TextView hostLabel;
    private final ImageButton closeBtn;
    private final FrameCanvas canvasView;
    private final int slop, fling;
    private VelocityTracker velocity;
    private float downX, downY;
    private boolean dragging;
    private Bitmap bitmap;

    BrowserThumb(Context c, Actions actions) {
        super(c);
        this.actions = actions;
        setOrientation(VERTICAL);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        fling = vc.getScaledMinimumFlingVelocity() * 4;
        setClipToOutline(true);
        setElevation(dp(c, 6));
        setContentDescription(c.getString(R.string.bubble_browser_thumb));

        // Mini header bar: globe icon + host name + X dismiss button
        header = new LinearLayout(c);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(dp(c, 8), dp(c, 3), dp(c, 2), dp(c, 3));

        globe = icon(c, R.drawable.ms_public, ACCENT_TEXT, 12);
        header.addView(globe);

        hostLabel = new TextView(c);
        hostLabel.setSingleLine(true);
        hostLabel.setEllipsize(TextUtils.TruncateAt.END);
        hostLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hostLabel.setTypeface(sans(c, 600));
        hostLabel.setPaddingRelative(dp(c, 5), 0, dp(c, 2), 0);
        hostLabel.setText(c.getString(R.string.browser_pill_active));
        header.addView(hostLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        closeBtn = new ImageButton(c);
        closeBtn.setImageResource(R.drawable.ms_close);
        closeBtn.setContentDescription(c.getString(R.string.bubble_browser_thumb_hide));
        closeBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        closeBtn.setBackground(null);
        int p = dp(c, 4);
        closeBtn.setPadding(p, p, p, p);
        closeBtn.setOnClickListener(v -> actions.dismiss());
        header.addView(closeBtn, new LayoutParams(dp(c, 24), dp(c, 24)));

        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, dp(c, 26)));

        divider = new View(c);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, dp(c, 1)));

        canvasView = new FrameCanvas(c);
        addView(canvasView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        restyle();
    }

    /** Updates colors after Settings changes theme. */
    void restyle() {
        setBackground(rounded(getContext(), CARD2, 14));
        setForeground(outlined(getContext(), Color.TRANSPARENT, 14, ACCENT, 1));
        header.setBackgroundColor(CARD);
        divider.setBackgroundColor(LINE);
        globe.setImageTintList(android.content.res.ColorStateList.valueOf(ACCENT_TEXT));
        hostLabel.setTextColor(TEXT);
        closeBtn.setImageTintList(android.content.res.ColorStateList.valueOf(MUTED));
    }

    /** Updates displayed domain label in the mini header. */
    void setHost(String host) {
        String label = host != null && !host.isEmpty() ? host : getContext().getString(R.string.browser_pill_active);
        if (!label.contentEquals(hostLabel.getText())) hostLabel.setText(label);
    }

    /** Clears frame buffer when switching tabs. */
    void clear() {
        bitmap = null;
        canvasView.invalidate();
    }

    @Override
    public void onFrame(Bitmap b, float w, float h) {
        post(() -> {
            bitmap = b;
            canvasView.invalidate();
        });
    }

    private final class FrameCanvas extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF dst = new RectF();

        FrameCanvas(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (bitmap == null) return;
            dst.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(bitmap, null, dst, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    dragging = false;
                    if (velocity != null) velocity.recycle();
                    velocity = VelocityTracker.obtain();
                    velocity.addMovement(e);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (velocity != null) velocity.addMovement(e);
                    if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) dragging = true;
                    if (dragging) {
                        BrowserThumb.this.setTranslationX(dx);
                        BrowserThumb.this.setAlpha(Math.max(0.2f, 1 - Math.abs(dx) / Math.max(1, BrowserThumb.this.getWidth())));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    float vx = 0;
                    if (velocity != null) {
                        velocity.addMovement(e);
                        velocity.computeCurrentVelocity(1000);
                        vx = velocity.getXVelocity();
                        velocity.recycle();
                        velocity = null;
                    }
                    if (dragging) {
                        if (Math.abs(dx) > BrowserThumb.this.getWidth() / 3f || (Math.abs(vx) > fling && Math.signum(vx) == Math.signum(dx))) {
                            away(dx);
                        } else {
                            BrowserThumb.this.animate().translationX(0).alpha(1).setDuration(160).start();
                        }
                    } else if (Math.hypot(dx, dy) < slop) {
                        performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (velocity != null) velocity.recycle();
                    velocity = null;
                    BrowserThumb.this.animate().translationX(0).alpha(1).setDuration(160).start();
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            actions.open();
            return true;
        }
    }

    /** Slides out sideways then invokes dismiss. */
    private void away(float dx) {
        animate().translationX(Math.signum(dx) * getWidth()).alpha(0).setDuration(180).withEndAction(() -> {
            setTranslationX(0);
            setAlpha(1);
            actions.dismiss();
        }).start();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClickable(true);
        info.setDismissable(true);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS,
                getContext().getString(R.string.bubble_browser_thumb_hide)));
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_DISMISS) {
            actions.dismiss();
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
