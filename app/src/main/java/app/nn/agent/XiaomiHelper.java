package app.nn.agent;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Helper to detect Xiaomi / HyperOS / MIUI and navigate directly to Xiaomi-specific settings.
 */
final class XiaomiHelper {
    private XiaomiHelper() {}

    static boolean isXiaomi() {
        String man = Build.MANUFACTURER;
        String brand = Build.BRAND;
        return (man != null && (man.equalsIgnoreCase("xiaomi") || man.equalsIgnoreCase("redmi") || man.equalsIgnoreCase("poco")))
                || (brand != null && (brand.equalsIgnoreCase("xiaomi") || brand.equalsIgnoreCase("redmi") || brand.equalsIgnoreCase("poco")));
    }

    static void openPopupPermission(Context c) {
        try {
            Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
            intent.putExtra("extra_pkgname", c.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
        } catch (Exception e) {
            openAppDetails(c);
        }
    }

    static void openAutostart(Context c) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
        } catch (Exception e) {
            openAppDetails(c);
        }
    }

    static void openBatterySaver(Context c) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
            intent.putExtra("package_name", c.getPackageName());
            intent.putExtra("package_label", c.getString(R.string.app_name));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
        } catch (Exception e) {
            openAppDetails(c);
        }
    }

    static void openAppDetails(Context c) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + c.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
