package app.nn.agent;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Full-screen theme-synchronized image viewer (lightbox) with pinch/double-tap zoom, pan,
 * save to Download/NN, and native share.
 */
final class ImageViewerDialog {

    private ImageViewerDialog() {
    }

    public static void show(final Activity a, final String id) {
        if (a == null || a.isFinishing() || id == null) return;
        final File file = Attachments.file(a, id);
        if (file == null || !file.exists()) {
            Toast.makeText(a, a.getString(R.string.attach_cant_open, Attachments.name(id)), Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                w.getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }

        final FrameLayout root = new FrameLayout(a);
        // Frosted / dark translucent backdrop synchronized with app theme
        root.setBackgroundColor(Theme.withAlpha(Ui.BG, 0xF6));

        final ZoomImageView iv = new ZoomImageView(a);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Load image bitmap or play animated GIF
        final Bitmap fullBmp = Attachments.full(a, id);
        if (fullBmp != null) {
            iv.setImageBitmap(fullBmp);
        } else {
            Bitmap thumb = Attachments.thumb(a, id, 1080);
            if (thumb != null) iv.setImageBitmap(thumb);
        }
        if (id.toLowerCase().endsWith(".gif")) {
            Attachments.playGif(a, id, iv);
        }

        // Top bar container
        final LinearLayout topBar = new LinearLayout(a);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int statusBarH = getStatusBarHeight(a);
        topBar.setPaddingRelative(Ui.dp(a, 12), statusBarH + Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 10));
        topBar.setBackgroundColor(Theme.withAlpha(Ui.CARD, 0xD0));

        // Close button
        ImageButton btnClose = Ui.iconButton(a, R.drawable.ms_close, "Close", Ui.TEXT);
        btnClose.setOnClickListener(v -> d.dismiss());
        topBar.addView(btnClose, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));

        // Title (filename)
        TextView title = new TextView(a);
        title.setText(Attachments.name(id));
        title.setTextColor(Ui.TEXT);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setPaddingRelative(Ui.dp(a, 10), 0, Ui.dp(a, 10), 0);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(title, tp);

        // Save to Download/NN button
        ImageButton btnSave = Ui.iconButton(a, R.drawable.ms_download, "Save", Ui.TEXT);
        btnSave.setOnClickListener(v -> {
            Uri saved = Attachments.saveToGallery(a, id);
            if (saved != null) {
                Toast.makeText(a, "Saved to Download/NN/" + Attachments.name(id), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(a, "Failed to save image", Toast.LENGTH_SHORT).show();
            }
        });
        topBar.addView(btnSave, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));

        // Share button
        ImageButton btnShare = Ui.iconButton(a, R.drawable.ms_share, "Share", Ui.TEXT);
        btnShare.setOnClickListener(v -> {
            try {
                String displayName = Attachments.name(id);
                File out = FilesProvider.shareFile(a, displayName);
                try (InputStream in = new FileInputStream(file); OutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                }
                Uri u = FilesProvider.shareUri(displayName);
                Intent send = new Intent(Intent.ACTION_SEND);
                String mime = Attachments.isImage(id) ? (id.toLowerCase().endsWith(".png") ? "image/png"
                        : id.toLowerCase().endsWith(".gif") ? "image/gif"
                        : id.toLowerCase().endsWith(".webp") ? "image/webp" : "image/jpeg") : "application/octet-stream";
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, u);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(send, displayName);
                a.startActivity(chooser);
            } catch (Exception e) {
                Toast.makeText(a, "Couldn't share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        topBar.addView(btnShare, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topParams.gravity = Gravity.TOP;
        root.addView(topBar, topParams);

        // Tap on image toggles top bar
        iv.setOnSingleTapListener(() -> {
            if (topBar.getVisibility() == View.VISIBLE) {
                topBar.animate().alpha(0f).translationY(-topBar.getHeight()).setDuration(180)
                        .withEndAction(() -> topBar.setVisibility(View.GONE)).start();
            } else {
                topBar.setVisibility(View.VISIBLE);
                topBar.animate().alpha(1f).translationY(0f).setDuration(180).start();
            }
        });

        d.setContentView(root);
        d.show();
    }

    private static int getStatusBarHeight(Context c) {
        int resId = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? c.getResources().getDimensionPixelSize(resId) : Ui.dp(c, 24);
    }

    /**
     * Interactive ImageView with smooth pinch-to-zoom, pan, and double-tap zoom.
     */
    static class ZoomImageView extends ImageView {
        private final Matrix baseMatrix = new Matrix();
        private final Matrix suppMatrix = new Matrix();
        private final Matrix drawMatrix = new Matrix();
        private final RectF displayRect = new RectF();

        private ScaleGestureDetector scaleDetector;
        private GestureDetector gestureDetector;
        private Runnable singleTapListener;

        private float currentScale = 1.0f;
        private static final float MIN_SCALE = 1.0f;
        private static final float MAX_SCALE = 5.0f;

        public ZoomImageView(Context context) {
            super(context);
            init(context);
        }

        private void init(Context context) {
            setScaleType(ScaleType.MATRIX);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float factor = detector.getScaleFactor();
                    float newScale = currentScale * factor;
                    if (newScale < MIN_SCALE) factor = MIN_SCALE / currentScale;
                    else if (newScale > MAX_SCALE) factor = MAX_SCALE / currentScale;

                    currentScale *= factor;
                    suppMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    checkAndDisplayMatrix();
                    return true;
                }
            });

            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (singleTapListener != null) singleTapListener.run();
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    float targetScale = currentScale > 1.2f ? 1.0f : 2.8f;
                    float factor = targetScale / currentScale;
                    currentScale = targetScale;
                    if (targetScale == 1.0f) {
                        suppMatrix.reset();
                    } else {
                        suppMatrix.postScale(factor, factor, e.getX(), e.getY());
                    }
                    checkAndDisplayMatrix();
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if (currentScale > 1.0f) {
                        suppMatrix.postTranslate(-distanceX, -distanceY);
                        checkAndDisplayMatrix();
                        return true;
                    }
                    return false;
                }
            });
        }

        public void setOnSingleTapListener(Runnable l) {
            this.singleTapListener = l;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean handled = scaleDetector.onTouchEvent(event);
            handled = gestureDetector.onTouchEvent(event) || handled;
            if (event.getAction() == MotionEvent.ACTION_UP && currentScale < 1.0f) {
                currentScale = 1.0f;
                suppMatrix.reset();
                checkAndDisplayMatrix();
            }
            return handled || super.onTouchEvent(event);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            updateBaseMatrix();
        }

        private void updateBaseMatrix() {
            android.graphics.drawable.Drawable d = getDrawable();
            if (d == null) return;
            float viewWidth = getWidth();
            float viewHeight = getHeight();
            int drawableWidth = d.getIntrinsicWidth();
            int drawableHeight = d.getIntrinsicHeight();
            if (drawableWidth <= 0 || drawableHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return;

            baseMatrix.reset();
            float widthScale = viewWidth / drawableWidth;
            float heightScale = viewHeight / drawableHeight;
            float scale = Math.min(widthScale, heightScale);

            baseMatrix.postScale(scale, scale);
            baseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2f,
                    (viewHeight - drawableHeight * scale) / 2f);
            checkAndDisplayMatrix();
        }

        private void checkAndDisplayMatrix() {
            drawMatrix.set(baseMatrix);
            drawMatrix.postConcat(suppMatrix);
            setImageMatrix(drawMatrix);

            android.graphics.drawable.Drawable d = getDrawable();
            if (d != null) {
                displayRect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                drawMatrix.mapRect(displayRect);
                float deltaX = 0, deltaY = 0;
                float viewWidth = getWidth(), viewHeight = getHeight();

                if (displayRect.width() <= viewWidth) {
                    deltaX = (viewWidth - displayRect.width()) / 2f - displayRect.left;
                } else if (displayRect.left > 0) {
                    deltaX = -displayRect.left;
                } else if (displayRect.right < viewWidth) {
                    deltaX = viewWidth - displayRect.right;
                }

                if (displayRect.height() <= viewHeight) {
                    deltaY = (viewHeight - displayRect.height()) / 2f - displayRect.top;
                } else if (displayRect.top > 0) {
                    deltaY = -displayRect.top;
                } else if (displayRect.bottom < viewHeight) {
                    deltaY = viewHeight - displayRect.bottom;
                }

                if (deltaX != 0 || deltaY != 0) {
                    suppMatrix.postTranslate(deltaX, deltaY);
                    drawMatrix.set(baseMatrix);
                    drawMatrix.postConcat(suppMatrix);
                    setImageMatrix(drawMatrix);
                }
            }
        }
    }
}
