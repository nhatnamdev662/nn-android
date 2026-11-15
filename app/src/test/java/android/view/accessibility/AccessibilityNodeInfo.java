package android.view.accessibility;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

public class AccessibilityNodeInfo {
    private Bundle extras = new Bundle();
    private CharSequence packageName = "app.nn.agent";
    private AccessibilityNodeInfo parent;
    private final List<AccessibilityNodeInfo> children = new ArrayList<>();
    private AccessibilityWindowInfo window;
    public boolean recycled = false;
    public int recycleCalls = 0;

    public AccessibilityNodeInfo() {}

    public static AccessibilityNodeInfo obtain() {
        return new AccessibilityNodeInfo();
    }

    public Bundle getExtras() {
        return extras;
    }

    public void setExtras(Bundle extras) {
        this.extras = extras;
    }

    public CharSequence getPackageName() {
        return packageName;
    }

    public void setPackageName(CharSequence packageName) {
        this.packageName = packageName;
    }

    public AccessibilityNodeInfo getParent() {
        return parent;
    }

    public void setParent(AccessibilityNodeInfo parent) {
        this.parent = parent;
    }

    public AccessibilityWindowInfo getWindow() {
        return window;
    }

    public void setWindow(AccessibilityWindowInfo window) {
        this.window = window;
    }

    public int getChildCount() {
        return children.size();
    }

    public AccessibilityNodeInfo getChild(int index) {
        if (index < 0 || index >= children.size()) return null;
        return children.get(index);
    }

    public void addChild(AccessibilityNodeInfo child) {
        if (child != null) {
            child.setParent(this);
            children.add(child);
        }
    }

    public void recycle() {
        recycled = true;
        recycleCalls++;
    }
}
