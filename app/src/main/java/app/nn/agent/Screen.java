package app.nn.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Base for NN's screens: applies the theme and rebuilds smoothly when appearance changes. */
abstract class Screen extends Activity {
    private String themeKey;

    @Override
    protected void onCreate(Bundle state) {
        Theme.apply(this);
        themeKey = Theme.key();
        super.onCreate(state);
        overridePendingTransition(0, 0);
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

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    /** Re-apply the theme in place (after a change made on this screen). */
    protected void rethemed() {
        Theme.apply(this);
        themeKey = Theme.key();
    }
}
