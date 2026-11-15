package app.nn.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Base for NN's screens: applies the theme and transitions smoothly between tabs and sub-screens. */
abstract class Screen extends Activity {
    private String themeKey;
    private boolean skipFinishTransition;

    @Override
    protected void onCreate(Bundle state) {
        Theme.apply(this);
        themeKey = Theme.key();
        Ui.unlockHighRefreshRate(this);
        super.onCreate(state);
        Intent in = getIntent();
        boolean noAnim = in != null && ((in.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0
                || in.hasExtra("nn_tab_dir"));
        if (noAnim || isTaskRoot()) {
            overridePendingTransition(0, 0);
        } else {
            overridePendingTransition(R.anim.nn_screen_in, R.anim.nn_screen_hold_out);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Theme.load(this);
        if (!Theme.key().equals(themeKey)) {
            finish();
            startActivity(getIntent().addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
            overridePendingTransition(0, 0);
        }
    }

    void finishForTabSwitch() {
        skipFinishTransition = true;
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        if (skipFinishTransition) {
            skipFinishTransition = false;
            overridePendingTransition(0, 0);
            return;
        }
        Intent in = getIntent();
        boolean noAnim = in != null && (in.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0;
        if (noAnim || isTaskRoot()) overridePendingTransition(0, 0);
        else overridePendingTransition(R.anim.nn_screen_hold_in, R.anim.nn_screen_out);
    }

    /** Re-apply the theme in place (after a change made on this screen). */
    protected void rethemed() {
        Theme.apply(this);
        themeKey = Theme.key();
    }
}
