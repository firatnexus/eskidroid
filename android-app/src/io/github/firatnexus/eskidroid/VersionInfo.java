package io.github.firatnexus.eskidroid;

/** İndirilebilir bir sürüm (APK) bilgisi. Veri olmayan alanlar boş/kalınlıksal. */
public final class VersionInfo {
    public String label = "";
    public String url = "";
    public long size = -1;
    public long date = -1;       // ms; bilinmiyorsa -1
    public long versionCode = -1; // F-Droid'te gerçek kod; URL'lerde kullanılır
    public boolean unavailable;  // HEAD 404 → kaynakta yok
}
