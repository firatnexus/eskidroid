package io.github.firatnexus.eskidroid;

import java.util.ArrayList;
import java.util.List;

/**
 * İndirilebilir bir sürüm (APK) bilgisi. Veri olmayan alanlar boş kalır;
 * detay ekranı yalnızca dolu alanları gösterir.
 */
public final class VersionInfo {
    public String label = "";
    public String url = "";
    public long size = -1;
    public long date = -1;        // ms; bilinmiyorsa -1
    public long versionCode = -1; // F-Droid/GitHub'da gerçek kod; URL'lerde kullanılır
    public boolean unavailable;  // HEAD 404 → kaynakta yok

    // ---- detay ekranı zengin verileri (kaynak sağlıyorsa) ----
    public String minSdk;         // "24" gibi; F-Droid sayfası/yerel APK
    public String targetSdk;
    public String abi;            // "arm64-v8a, armeabi-v7a" gibi
    public int permCount = -1;    // izin sayısı (yerel APK'dan)
    public List<String> permissions = new ArrayList<>();
    public String whatsNew;       // değişiklik notu (F-Droid "New in version" / GitHub release body)
    public boolean recommended;   // cihaza uygun sürüm işareti
    public String sourceTag;      // hangi kaynaktan geldi ("fdroid" | "archive" | "github")

    public boolean hasMeta() {
        return minSdk != null || targetSdk != null || abi != null || permCount >= 0;
    }
}
