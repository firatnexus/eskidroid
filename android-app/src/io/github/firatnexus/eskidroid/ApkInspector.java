package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Yerel APK incelemesi: minSdk, targetSdk, izin listesi, paket adı.
 * Yalnızca Depo'daki (indirilmiş) sürümler için kullanılır — sürüm kartlarına
 * "yerel" meta verisi sağlar. Yamalama/imzalama mantığına dokunmaz.
 */
public final class ApkInspector {

    public static final class Info {
        public String packageName;
        public int minSdk = -1;
        public int targetSdk = -1;
        public List<String> permissions = new ArrayList<>();
    }

    private ApkInspector() {
    }

    public static Info inspect(Context ctx, File apk) {
        Info info = new Info();
        if (apk == null || !apk.exists()) return info;
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.GET_PERMISSIONS);
            if (pi == null) return info;
            info.packageName = pi.packageName;
            if (pi.applicationInfo != null) {
                try {
                    info.minSdk = pi.applicationInfo.minSdkVersion;
                } catch (Throwable ignored) {
                }
                try {
                    info.targetSdk = pi.applicationInfo.targetSdkVersion;
                } catch (Throwable ignored) {
                }
            }
            if (pi.requestedPermissions != null) {
                for (String p : pi.requestedPermissions) {
                    if (p != null) info.permissions.add(p);
                }
            }
        } catch (Throwable ignored) {
        }
        return info;
    }
}
