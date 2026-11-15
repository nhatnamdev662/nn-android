package android.view.accessibility;

public class AccessibilityWindowInfo {
    public static final int TYPE_APPLICATION = 1;
    public static final int TYPE_INPUT_METHOD = 2;
    public static final int TYPE_SYSTEM = 3;
    public static final int TYPE_ACCESSIBILITY_OVERLAY = 4;
    public static final int TYPE_SPLIT_SCREEN_DIVIDER = 5;

    private int type = TYPE_APPLICATION;
    private AccessibilityNodeInfo root;

    public AccessibilityWindowInfo() {}

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public AccessibilityNodeInfo getRoot() {
        return root;
    }

    public void setRoot(AccessibilityNodeInfo root) {
        this.root = root;
    }
}
