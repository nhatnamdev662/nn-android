package android.os;

/**
 * Test mock for android.os.Looper to test thread boundary checks.
 */
public class Looper {
    private static final Looper MAIN_LOOPER = new Looper("main");
    private static final ThreadLocal<Looper> CURRENT = new ThreadLocal<>();

    private final String name;

    private Looper(String name) {
        this.name = name;
    }

    public static Looper getMainLooper() {
        return MAIN_LOOPER;
    }

    public static Looper myLooper() {
        return CURRENT.get();
    }

    public static void setAsMainThread() {
        CURRENT.set(MAIN_LOOPER);
    }

    public static void setAsBackgroundLooper(String name) {
        CURRENT.set(new Looper(name));
    }

    public static void clearThreadLooper() {
        CURRENT.remove();
    }

    @Override
    public String toString() {
        return "Looper{" + name + "}";
    }
}
