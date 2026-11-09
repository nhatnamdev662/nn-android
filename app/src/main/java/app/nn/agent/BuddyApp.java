package app.nn.agent;

import android.content.Context;

/** Backwards-compatibility alias for NnApp. */
public class BuddyApp extends NnApp {
    public static Context context() {
        return NnApp.context();
    }

    public static String str(int id, Object... args) {
        return NnApp.str(id, args);
    }

    public static String plural(int id, int n, Object... args) {
        return NnApp.plural(id, n, args);
    }
}
