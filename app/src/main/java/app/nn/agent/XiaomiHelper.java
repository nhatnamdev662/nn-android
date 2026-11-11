package app.nn.agent;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Helper to detect Xiaomi, Vivo/iQOO, OPPO/Realme/OnePlus, and Samsung devices and navigate
 * directly to OEM-specific background/autostart/battery settings.
 */
final class XiaomiHelper {
    private XiaomiHelper() {}

    private static boolean matchesBrand(String... names) {
        String man = Build.MANUFACTURER;
        String brand = Build.BRAND;
        for (String n : names) {
            if ((man != null && man.equalsIgnoreCase(n)) || (brand != null && brand.equalsIgnoreCase(n))) {
                return true;
            }
        }
        return false;
    }

    static boolean isXiaomi() {
        return matchesBrand("xiaomi", "redmi", "poco");
    }

    static boolean isVivo() {
        return matchesBrand("vivo", "iqoo");
    }

    static boolean isOppo() {
        return matchesBrand("oppo", "realme", "oneplus");
    }

    static boolean isSamsung() {
        return matchesBrand("samsung");
    }

    static boolean needsOemGuide() {
        return isXiaomi() || isVivo() || isOppo() || isSamsung();
    }

    static String oemLabel() {
        if (isXiaomi()) return "Xiaomi / HyperOS";
        if (isVivo()) return "Vivo / iQOO";
        if (isOppo()) return "OPPO / ColorOS";
        if (isSamsung()) return "Samsung / One UI";
        return Build.MANUFACTURER != null ? Build.MANUFACTURER : "Android";
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

    static void openAutostartForOem(Context c) {
        if (isXiaomi()) {
            openAutostart(c);
            return;
        }
        String[][] candidates;
        if (isVivo()) {
            candidates = new String[][]{
                    {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                    {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                    {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
                    {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"},
            };
        } else if (isOppo()) {
            candidates = new String[][]{
                    {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                    {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
                    {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                    {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            };
        } else {
            openAppDetails(c);
            return;
        }
        for (String[] pair : candidates) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(pair[0], pair[1]));
                i.putExtra("packagename", c.getPackageName());
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetails(c);
    }

    static void openBatteryForOem(Context c) {
        if (isXiaomi()) {
            openBatterySaver(c);
            return;
        }
        if (isVivo()) {
            String[][] vivoBat = {
                    {"com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"},
                    {"com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"},
            };
            for (String[] pair : vivoBat) {
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName(pair[0], pair[1]));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(i);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
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
