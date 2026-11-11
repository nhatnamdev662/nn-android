package app.nn.agent;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import static app.nn.agent.Ui.*;

/**
 * The background browser's live view (see Browser): compact 1-line pill with foldable preview in ChatActivity,
 * or full Chrome-style window in BrowserActivity. Supports touch/scroll/typing delegation, hiding from chat,
 * and cleanly closing the tab.
 */
final class BrowserPanel extends LinearLayout implements Browser.Listener {
    private final Activity a;
    private final String key;
    private final boolean full;
    private final FrameView frame;
    private final View frameDivider;
    private final LinearLayout controlsBar, typing, dialogRow;
    private final View statusDot;
    private final TextView titleText, address, dialogText;
    private final Button resume;
    private final ImageButton fold, kb;
    private final EditText field;
    private Runnable onHide;
    private boolean askedAddress;
    private boolean folded = true, watching, shown;

    BrowserPanel(Activity a, String key, boolean full) {
        super(a);
        this.a = a;
        this.key = key;
        this.full = full;
        if (full) this.folded = false;
        setOrientation(VERTICAL);
        if (!full) {
            setBackground(outlined(a, CARD, 16, OUTLINE, 1));
            setClipToOutline(true);
        }

        // Top header / compact pill bar
        LinearLayout head = row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (full) {
            head.setBackgroundColor(NAV);
            head.setPaddingRelative(dp(a, 6), dp(a, 6), dp(a, 8), dp(a, 6));
            ImageButton close = iconButton(a, R.drawable.ms_close, a.getString(R.string.chat_back), MUTED);
            close.setOnClickListener(v -> a.finish());
            head.addView(close, new LayoutParams(dp(a, 42), dp(a, 42)));

            // Rounded Chrome-like address bar pill
            LinearLayout addrPill = row(a);
            addrPill.setGravity(Gravity.CENTER_VERTICAL);
            addrPill.setBackground(outlined(a, CARD, 22, OUTLINE, 1));
            addrPill.setPaddingRelative(dp(a, 12), 0, dp(a, 12), 0);
            addrPill.setOnClickListener(v -> editAddress());
            ImageView globe = icon(a, R.drawable.ms_public, MUTED, 16);
            addrPill.addView(globe);
            address = single(text(a, "", 14, TEXT));
            address.setEllipsize(TextUtils.TruncateAt.END);
            address.setPaddingRelative(dp(a, 8), 0, 0, 0);
            addrPill.addView(address, weight1());
            LayoutParams ap = new LayoutParams(0, dp(a, 40), 1);
            ap.setMarginStart(dp(a, 4));
            head.addView(addrPill, ap);

            titleText = null;
            statusDot = null;
        } else {
            head.setPaddingRelative(dp(a, 10), dp(a, 6), dp(a, 4), dp(a, 6));
            statusDot = dot(a, OK, 8);
            head.addView(statusDot);

            ImageView globe = icon(a, R.drawable.ms_public, ACCENT_TEXT, 16);
            LayoutParams gp = new LayoutParams(dp(a, 16), dp(a, 16));
            gp.setMarginStart(dp(a, 8));
            head.addView(globe, gp);

            LinearLayout titles = column(a);
            titles.setPaddingRelative(dp(a, 8), 0, dp(a, 4), 0);
            titleText = single(text(a, a.getString(R.string.browser_pill_active), 13, TEXT));
            titleText.setTypeface(sans(a, 600));
            titles.addView(titleText);
            address = single(monoText(a, "", 11, MUTED));
            address.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            titles.addView(address);
            titles.setOnClickListener(v -> {
                folded = !folded;
                refresh();
            });
            head.addView(titles, weight1());
        }

        resume = button(a, a.getString(R.string.browser_continue), true);
        resume.setMinHeight(dp(a, 32));
        resume.setMinimumHeight(dp(a, 32));
        resume.setPaddingRelative(dp(a, 12), 0, dp(a, 12), 0);
        resume.setTextSize(12);
        resume.setOnClickListener(v -> {
            NnService svc = NnService.get();
            if (svc != null) svc.resume();
            refresh();
        });
        LayoutParams rp = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(a, 32));
        rp.setMarginStart(dp(a, 6));
        head.addView(resume, rp);

        if (!full) {
            fold = iconButton(a, R.drawable.ms_expand_more, a.getString(R.string.browser_fold), MUTED);
            fold.setOnClickListener(v -> {
                folded = !folded;
                refresh();
            });
            head.addView(fold, new LayoutParams(dp(a, 34), dp(a, 34)));

            ImageButton expand = iconButton(a, R.drawable.ms_open_in_full, a.getString(R.string.browser_full_screen), MUTED);
            expand.setOnClickListener(v -> a.startActivity(new Intent(a, BrowserActivity.class).putExtra("key", key)));
            head.addView(expand, new LayoutParams(dp(a, 34), dp(a, 34)));

            ImageButton hideBtn = iconButton(a, R.drawable.ms_visibility_off, a.getString(R.string.browser_hide), MUTED);
            hideBtn.setOnClickListener(v -> {
                if (onHide != null) onHide.run();
            });
            head.addView(hideBtn, new LayoutParams(dp(a, 34), dp(a, 34)));

            ImageButton closeTab = iconButton(a, R.drawable.ms_close, a.getString(R.string.browser_close_tab), MUTED);
            closeTab.setOnClickListener(v -> {
                Browser.closeTab(key);
                Toast.makeText(a, R.string.browser_tab_closed, Toast.LENGTH_SHORT).show();
            });
            head.addView(closeTab, new LayoutParams(dp(a, 34), dp(a, 34)));
        } else {
            fold = null;
        }
        addView(head, full());

        if (full) {
            View topLine = new View(a);
            topLine.setBackgroundColor(LINE);
            addView(topLine, new LayoutParams(LayoutParams.MATCH_PARENT, dp(a, 1)));
        }

        // JavaScript alert/confirm row
        dialogRow = row(a);
        dialogRow.setGravity(Gravity.CENTER_VERTICAL);
        dialogRow.setPaddingRelative(dp(a, 12), dp(a, 6), dp(a, 8), dp(a, 6));
        dialogRow.setBackground(rounded(a, CARD2, 12));
        dialogText = text(a, "", 13, TEXT);
        dialogRow.addView(dialogText, weight1());
        Button no = button(a, a.getString(android.R.string.cancel), false);
        no.setOnClickListener(v -> answerDialog(false));
        dialogRow.addView(no, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(a, 34)));
        Button yes = button(a, a.getString(android.R.string.ok), true);
        yes.setOnClickListener(v -> answerDialog(true));
        LayoutParams yp = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(a, 34));
        yp.setMarginStart(dp(a, 6));
        dialogRow.addView(yes, yp);
        LayoutParams drp = full();
        drp.setMargins(dp(a, 8), dp(a, 6), dp(a, 8), dp(a, 6));
        addView(dialogRow, drp);

        // Live rendered web viewport
        frame = new FrameView();
        frame.setContentDescription(a.getString(R.string.browser_live_title));
        addView(frame, full ? new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1)
                : new LayoutParams(LayoutParams.MATCH_PARENT, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.26f)));

        // Typing bar (hidden by default in both modes, toggled by Keyboard icon)
        typing = row(a);
        typing.setGravity(Gravity.CENTER_VERTICAL);
        typing.setPaddingRelative(dp(a, 8), dp(a, 6), dp(a, 8), dp(a, 6));
        if (full) typing.setBackgroundColor(NAV);
        field = new EditText(a);
        field.setHint(R.string.browser_type_hint);
        field.setHintTextColor(MUTED);
        field.setTextColor(TEXT);
        field.setTextSize(15);
        field.setSingleLine(true);
        field.setImeOptions(EditorInfo.IME_ACTION_SEND);
        // No learning/suggestions for sensitive fields/passwords
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        field.setPaddingRelative(dp(a, 12), dp(a, 8), dp(a, 12), dp(a, 8));
        field.setBackground(outlined(a, BG, 12, OUTLINE, 1));
        field.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                typeNow();
                return true;
            }
            return false;
        });
        typing.addView(field, weight1());
        Button bs = button(a, "⌫", false);
        bs.setContentDescription(a.getString(R.string.browser_backspace));
        bs.setOnClickListener(v -> {
            touched();
            Browser.later(() -> Browser.key(key, "Backspace"));
        });
        LayoutParams bp = new LayoutParams(dp(a, 46), dp(a, 38));
        bp.setMarginStart(dp(a, 6));
        typing.addView(bs, bp);
        Button enter = button(a, a.getString(R.string.browser_enter), false);
        enter.setOnClickListener(v -> {
            touched();
            String t = field.getText().toString();
            field.setText("");
            Browser.later(() -> {
                if (!t.isEmpty()) Browser.insertText(key, t);
                Browser.key(key, "Enter");
            });
        });
        LayoutParams ep = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(a, 38));
        ep.setMarginStart(dp(a, 6));
        typing.addView(enter, ep);
        typing.setVisibility(GONE);
        addView(typing, full());

        frameDivider = new View(a);
        frameDivider.setBackgroundColor(LINE);
        addView(frameDivider, new LayoutParams(LayoutParams.MATCH_PARENT, dp(a, 1)));

        // Bottom control toolbar (Back, Forward, Reload, Keyboard, Close Tab/Fullscreen)
        controlsBar = row(a);
        controlsBar.setGravity(Gravity.CENTER_VERTICAL);
        if (full) {
            controlsBar.setBackgroundColor(NAV);
            controlsBar.setPaddingRelative(dp(a, 8), dp(a, 4), dp(a, 8), dp(a, 4));
        } else {
            controlsBar.setBackgroundColor(CARD);
            controlsBar.setPaddingRelative(dp(a, 6), dp(a, 2), dp(a, 6), dp(a, 2));
        }

        ImageButton backBtn = toolbarBtn(R.drawable.ms_arrow_back, a.getString(R.string.browser_back), MUTED, v -> {
            touched();
            Browser.later(() -> Browser.back(key));
        });
        ImageButton fwdBtn = toolbarBtn(R.drawable.ms_arrow_forward, a.getString(R.string.browser_forward), MUTED, v -> {
            touched();
            Browser.later(() -> Browser.forward(key));
        });
        ImageButton reloadBtn = toolbarBtn(R.drawable.ms_refresh, a.getString(R.string.browser_reload), MUTED, v -> {
            Browser.later(() -> Browser.reload(key));
        });
        kb = toolbarBtn(R.drawable.ms_keyboard, a.getString(R.string.browser_keyboard), MUTED, v -> toggleTyping());

        int btnH = dp(a, full ? 44 : 36);
        controlsBar.addView(backBtn, new LayoutParams(0, btnH, 1));
        controlsBar.addView(fwdBtn, new LayoutParams(0, btnH, 1));
        controlsBar.addView(reloadBtn, new LayoutParams(0, btnH, 1));
        controlsBar.addView(kb, new LayoutParams(0, btnH, 1));

        if (full) {
            ImageButton closeTabBtn = toolbarBtn(R.drawable.ms_tab_close, a.getString(R.string.browser_close_tab), ERR, v -> {
                Browser.closeTab(key);
                Toast.makeText(a, R.string.browser_tab_closed, Toast.LENGTH_SHORT).show();
                a.finish();
            });
            controlsBar.addView(closeTabBtn, new LayoutParams(0, btnH, 1));
        } else {
            ImageButton fullBtn = toolbarBtn(R.drawable.ms_open_in_full, a.getString(R.string.browser_full_screen), ACCENT_TEXT,
                    v -> a.startActivity(new Intent(a, BrowserActivity.class).putExtra("key", key)));
            controlsBar.addView(fullBtn, new LayoutParams(0, btnH, 1));
        }
        addView(controlsBar, full());

        refresh();
    }

    private ImageButton toolbarBtn(int iconRes, String desc, int tint, View.OnClickListener l) {
        ImageButton b = iconButton(a, iconRes, desc, tint);
        b.setBackgroundResource(ripple(a, true));
        b.setOnClickListener(l);
        return b;
    }

    void setOnHideListener(Runnable onHide) {
        this.onHide = onHide;
    }

    void setExpanded(boolean expanded) {
        if (full) return;
        this.folded = !expanded;
        refresh();
    }

    boolean isExpanded() {
        return !folded;
    }

    // ------------------------------------------------------------------ state

    /** The screen showing it came to the front (true) or went away (false). */
    void setShown(boolean on) {
        shown = on;
        if (on) Browser.addListener(this);
        else Browser.removeListener(this);
        refresh();
    }

    @Override
    public void onBrowserChanged() {
        refresh();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && full) refresh(); // may ask for an address (see refresh)
    }

    /** Where the marked items are now (see Browser.markBoxes), looked up again while the marks show. */
    private volatile JSONArray boxes;
    private final Runnable followMarks = new Runnable() {
        @Override
        public void run() {
            if (Browser.marks(key) == null || !isAttachedToWindow()) {
                boxes = null;
                frame.invalidate();
                return;
            }
            Browser.later(() -> {
                boxes = Browser.markBoxes(key, false);
                post(frame::invalidate);
            });
            postDelayed(this, 500); // they move as the page scrolls
        }
    };

    /** From the browser's and the chat's state (main thread). */
    void refresh() {
        removeCallbacks(followMarks);
        followMarks.run();
        String[] page = Browser.page(key);
        String url = page == null ? "" : page[0];
        String pageTitle = page == null ? "" : page[1];
        boolean blank = url.isEmpty() || "about:blank".equals(url);

        NnService svc = NnService.get();
        boolean paused = svc != null && svc.pausedForBrowser();

        if (full) {
            address.setText(blank ? a.getString(R.string.browser_address_hint) : url);
            address.setTextColor(blank ? MUTED : TEXT);
            if (blank && !askedAddress && hasWindowFocus()) {
                askedAddress = true;
                post(this::editAddress);
            }
        } else {
            String host = Browser.hostLabel(url);
            String topTitle = !pageTitle.isEmpty() ? pageTitle
                    : !host.isEmpty() ? host
                    : a.getString(R.string.browser_pill_active);
            if (titleText != null) titleText.setText(topTitle);
            if (paused) {
                address.setText(a.getString(R.string.browser_pill_paused));
                address.setTextColor(ACCENT_TEXT);
            } else {
                address.setText(!host.isEmpty() ? host : blank ? a.getString(R.string.browser_no_page) : url);
                address.setTextColor(MUTED);
            }
            if (statusDot != null) {
                statusDot.setBackground(oval(paused || Browser.dialog(key) != null ? ACCENT : OK));
            }
        }

        resume.setVisibility(paused ? VISIBLE : GONE);
        if (fold != null) fold.setImageResource(folded ? R.drawable.ms_expand_more : R.drawable.ms_expand_less);
        frame.setVisibility(folded ? GONE : VISIBLE);
        frameDivider.setVisibility(folded ? GONE : VISIBLE);
        controlsBar.setVisibility(folded ? GONE : VISIBLE);
        if (folded) typing.setVisibility(GONE);
        if (kb != null) {
            kb.setImageTintList(ColorStateList.valueOf(typing.getVisibility() == VISIBLE ? ACCENT_TEXT : MUTED));
        }

        JSONObject d = Browser.dialog(key);
        dialogRow.setVisibility(d == null || (folded && !full) ? GONE : VISIBLE);
        if (d != null) dialogText.setText(a.getString(R.string.browser_dialog, d.optString("message")));

        boolean watch = shown && !folded && Browser.available(a);
        if (watch != watching) {
            watching = watch;
            if (watch) Browser.watch(a, key, frame);
            else Browser.unwatch(key, frame);
        }
    }

    /** The user used the page: the agent's numbers are stale, and a working agent waits (see NnService). */
    private void touched() {
        Browser.touched(key);
        NnService svc = NnService.get();
        if (svc != null) svc.pauseForBrowser(key);
        refresh();
    }

    private void toggleTyping() {
        showTyping(typing.getVisibility() != VISIBLE);
    }

    private void showTyping(boolean on) {
        typing.setVisibility(on ? VISIBLE : GONE);
        if (kb != null) {
            kb.setImageTintList(ColorStateList.valueOf(on ? ACCENT_TEXT : MUTED));
        }
        if (on) {
            folded = false;
            refresh();
            field.requestFocus();
            field.postDelayed(() -> {
                InputMethodManager imm = a.getSystemService(InputMethodManager.class);
                if (imm != null) imm.showSoftInput(field, 0);
            }, 150);
        } else {
            field.clearFocus();
            InputMethodManager imm = a.getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        }
    }

    private void typeNow() {
        String t = field.getText().toString();
        if (t.isEmpty()) return;
        field.setText("");
        touched();
        Browser.later(() -> Browser.insertText(key, t));
    }

    private void answerDialog(boolean ok) {
        touched();
        Browser.later(() -> Browser.answerDialog(key, ok));
    }

    /** Full screen: tap the address to go somewhere else. */
    private void editAddress() {
        LinearLayout c = column(a);
        c.addView(title(a, a.getString(R.string.browser_go_title), 18));
        EditText url = new EditText(a);
        String[] page = Browser.page(key);
        url.setText(page == null || "about:blank".equals(page[0]) ? "" : page[0]);
        url.setSelectAllOnFocus(true);
        url.setHint(R.string.browser_address_hint);
        url.setHintTextColor(MUTED);
        url.setTextColor(TEXT);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setImeOptions(EditorInfo.IME_ACTION_GO);
        url.setPaddingRelative(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        url.setBackground(outlined(a, BG, 12, OUTLINE, 1));
        LayoutParams up = full();
        up.topMargin = dp(a, 12);
        c.addView(url, up);
        Button go = button(a, a.getString(R.string.browser_go), true);
        LayoutParams gp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(a, 48));
        gp.topMargin = dp(a, 12);
        c.addView(go, gp);
        android.app.Dialog d = bottomSheet(a, c);
        Runnable open = () -> {
            String u = url.getText().toString().trim();
            d.dismiss();
            if (u.isEmpty()) return;
            String to = u.matches("(?i)^https?://.*") ? u : u.contains(".") && !u.contains(" ") ? "https://" + u
                    : "https://duckduckgo.com/?q=" + android.net.Uri.encode(u);
            touched();
            Browser.later(() -> {
                if (Browser.ensure(a, 120_000) == null) Browser.navigate(key, to);
            });
        };
        go.setOnClickListener(v -> open.run());
        url.setOnEditorActionListener((v, id, e) -> {
            open.run();
            return true;
        });
        d.show();
        url.requestFocus();
        url.postDelayed(() -> {
            InputMethodManager imm = a.getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(url, 0);
        }, 200);
    }

    // ------------------------------------------------------------------ frames

    /** Draws the frames and turns touches into taps and scrolls on the page. */
    private final class FrameView extends View implements Browser.Frames {
        private Bitmap bitmap;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF dst = new RectF();
        private final int slop = ViewConfiguration.get(a).getScaledTouchSlop();
        private float downX, downY, lastX, lastY, pendingDy, pendingDx;
        private boolean dragging;
        private long lastWheel;

        private final Paint markStroke = new Paint(Paint.ANTI_ALIAS_FLAG), markFill = new Paint(Paint.ANTI_ALIAS_FLAG),
                markText = new Paint(Paint.ANTI_ALIAS_FLAG);

        FrameView() {
            super(a);
            setBackgroundColor(full ? BG : CARD2);
            markStroke.setStyle(Paint.Style.STROKE);
            markStroke.setStrokeWidth(dp(a, 3));
            markStroke.setColor(ACCENT);
            markFill.setColor(ACCENT);
            markText.setColor(ON_ACCENT);
            markText.setTextSize(dp(a, 13));
            markText.setTypeface(sans(a, 600));
        }

        @Override
        public void onFrame(Bitmap b, float w, float h) {
            post(() -> {
                bitmap = b;
                invalidate();
            });
        }

        /** Where the page is drawn: full width in the chat (its top part shows), the whole page full screen. */
        private void place() {
            float vw = getWidth(), vh = getHeight();
            float s = full ? Math.min(vw / Browser.WIDTH, vh / Browser.HEIGHT) : vw / Browser.WIDTH;
            float w = Browser.WIDTH * s, h = Browser.HEIGHT * s;
            float x = (vw - w) / 2, y = full ? (vh - h) / 2 : 0;
            dst.set(x, y, x + w, y + h);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (bitmap == null) {
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                t.setColor(MUTED);
                t.setTextSize(dp(a, 13));
                t.setTextAlign(Paint.Align.CENTER);
                String msg = a.getString(Browser.state == Browser.State.INSTALLING ? R.string.browser_installing
                        : R.string.browser_starting);
                c.drawText(msg, getWidth() / 2f, getHeight() / 2f, t);
                return;
            }
            place();
            c.save();
            c.clipRect(0, 0, getWidth(), getHeight());
            c.drawBitmap(bitmap, null, dst, paint);
            drawMarks(c);
            c.restore();
        }

        /** What an agent's question asks the user to use (see Browser.marks): a box round each, its label above. */
        private void drawMarks(Canvas c) {
            JSONArray m = Browser.marks(key), b = boxes;
            if (m == null || b == null) return;
            float s = dst.width() / Browser.WIDTH, pad = dp(a, 4);
            for (int i = 0; i < m.length() && i < b.length(); i++) {
                JSONObject r = b.optJSONObject(i);
                if (r == null) continue;
                RectF box = new RectF(dst.left + (float) r.optDouble("x") * s - pad, dst.top + (float) r.optDouble("y") * s - pad,
                        dst.left + (float) (r.optDouble("x") + r.optDouble("w")) * s + pad,
                        dst.top + (float) (r.optDouble("y") + r.optDouble("h")) * s + pad);
                if (box.bottom < dst.top || box.top > dst.bottom) continue; // scrolled out of view
                c.drawRoundRect(box, dp(a, 8), dp(a, 8), markStroke);
                String label = m.optJSONObject(i).optString("label");
                if (label.isEmpty()) continue;
                float tw = markText.measureText(label), th = markText.getTextSize();
                float lx = Math.max(dst.left, Math.min(box.left, dst.right - tw - 2 * pad - dp(a, 8)));
                float ly = box.top - th - 2 * pad > dst.top ? box.top - th - 2 * pad : box.bottom + pad; // above, or below at the top
                RectF pill = new RectF(lx, ly, lx + tw + 2 * pad + dp(a, 8), ly + th + 2 * pad);
                c.drawRoundRect(pill, dp(a, 6), dp(a, 6), markFill);
                c.drawText(label, pill.left + pad + dp(a, 4), pill.bottom - pad - markText.descent(), markText);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            place();
            float s = dst.width() / Browser.WIDTH;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    downX = lastX = e.getX();
                    downY = lastY = e.getY();
                    dragging = false;
                    pendingDx = pendingDy = 0;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                        dragging = true;
                        touched();
                    }
                    if (dragging) {
                        pendingDy += (lastY - e.getY()) / s;
                        pendingDx += (lastX - e.getX()) / s;
                        lastX = e.getX();
                        lastY = e.getY();
                        if (System.currentTimeMillis() - lastWheel > 60) flushWheel(e, s);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        flushWheel(e, s);
                    } else if (dst.contains(e.getX(), e.getY())) {
                        double x = (e.getX() - dst.left) / s, y = (e.getY() - dst.top) / s;
                        touched();
                        Browser.later(() -> Browser.click(key, x, y));
                        performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }

        private void flushWheel(MotionEvent e, float s) {
            lastWheel = System.currentTimeMillis();
            if (Math.abs(pendingDy) < 1 && Math.abs(pendingDx) < 1) return;
            final double dx = pendingDx, dy = pendingDy;
            final double x = Math.max(1, Math.min(Browser.WIDTH - 1, (e.getX() - dst.left) / s));
            final double y = Math.max(1, Math.min(Browser.HEIGHT - 1, (e.getY() - dst.top) / s));
            pendingDx = pendingDy = 0;
            Browser.later(() -> Browser.wheel(key, x, y, dx, dy));
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }
}
