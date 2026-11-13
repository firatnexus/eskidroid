package io.github.firatnexus.eskidroid;

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Eski APK'yi modern Android'de sorunsuz çalışabilir yapmak için:
 * 1) AndroidManifest.xml içindeki targetSdkVersion yükseltilir.
 * 2) Çökme önleyici sağlamlaştırma (v1.5):
 *    - extractNativeLibs=true: hedef SDK >= 23'te hizalanmamış native
 *      kütüphaneler anında çöker (UnsatisfiedLinkError). Eski APK'ların
 *      lib/ dosyaları sıkıştırılmış ve hizasız olduğundan bu şart.
 *    - uses-library org.apache.http.legacy (required=false): eski
 *      uygulamaların Apache HTTP istemcisi Android 9+ 'da kaldırıldı;
 *      bildirilmezse ağ kullanan eski oyunlar açılışta çöker.
 *    - usesCleartextTraffic=true: eski oyunlar http kullanır; hedef
 *      SDK 28+'da cleartext varsayılan yasak olur.
 * Diğer dosyalar aynı sıkıştırma yöntemiyle kopyalanır.
 */
public final class AndroidAppPatcher {

    // doğrulanmış framework öznitelik kimlikleri (aapt ile test edildi)
    private static final int ATTR_USES_CLEARTEXT = 0x010104EC;
    private static final int ATTR_NAME = 0x01010003;
    private static final int ATTR_REQUIRED = 0x0101028E;

    public static void patch(File apkFile, File outApk, int newTargetSdk) throws Exception {
        byte[] newManifest = null;

        try (ZipFile zip = new ZipFile(apkFile)) {
            ZipEntry manifestEntry = zip.getEntry("AndroidManifest.xml");
            if (manifestEntry == null) {
                throw new Exception("AndroidManifest.xml yok; bu bir APK mi?");
            }
            File tmp = File.createTempFile("eskidroid-manifest", ".bin");
            try (OutputStream os = new FileOutputStream(tmp)) {
                os.write(readAll(zip.getInputStream(manifestEntry)));
            }
            AndroidManifestBlock manifest;
            try {
                manifest = AndroidManifestBlock.load(tmp);
            } catch (Exception e) {
                tmp.delete();
                throw new Exception("Cok eski APK (2011 oncesi) olabilir: manifest okunamadi. "
                        + "Bu APK icin duzeltme yapilamaz.");
            }
            manifest.setTargetSdkVersion(newTargetSdk);
            harden(manifest);
            manifest.refreshFull();
            manifest.writeBytes(tmp);
            newManifest = readAll(new FileInputStream(tmp));
            if (!tmp.delete()) tmp.deleteOnExit();
        }

        try (ZipFile zip = new ZipFile(apkFile);
             ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(outApk))) {

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                if (e.getName().equals("AndroidManifest.xml")) continue;
                if (e.getName().startsWith("META-INF/")) continue; // eski imzalar atilir

                ZipEntry out = new ZipEntry(e.getName());
                out.setMethod(e.getMethod());
                if (e.getMethod() == ZipEntry.STORED) {
                    byte[] data = readAll(zip.getInputStream(e));
                    out.setSize(data.length);
                    out.setCompressedSize(data.length);
                    out.setCrc(computeCrc(data));
                    zout.putNextEntry(out);
                    zout.write(data);
                } else {
                    zout.putNextEntry(out);
                    byte[] buf = new byte[8192];
                    int r;
                    try (InputStream is = zip.getInputStream(e)) {
                        while ((r = is.read(buf)) > 0) zout.write(buf, 0, r);
                    }
                }
                zout.closeEntry();
            }

            ZipEntry me = new ZipEntry("AndroidManifest.xml");
            me.setMethod(ZipEntry.DEFLATED);
            me.setSize(newManifest.length);
            zout.putNextEntry(me);
            zout.write(newManifest);
            zout.closeEntry();
        }
    }

    /**
     * Çökme önleyici manifest sağlamlaştırması. Her adım bağımsızdır:
     * biri başarısız olursa diğerleri uygulanmaya devam eder.
     */
    private static void harden(AndroidManifestBlock manifest) {
        // 1) Native kütüphaneler kuruluma çıkarılsın (hizalama sorunu çözülür)
        try {
            manifest.setExtractNativeLibs(true);
        } catch (Throwable ignored) {
        }
        // 2) Apache HTTP istemcisi (Android 9+ 'da framework'ten kaldırıldı)
        try {
            ResXmlElement app = manifest.getOrCreateApplicationElement();
            if (!hasApacheLibrary(manifest)) {
                ResXmlElement ul = app.getOrCreateElement("uses-library");
                ResXmlAttribute n = ul.getOrCreateAndroidAttribute("name", ATTR_NAME);
                n.setValueAsString("org.apache.http.legacy");
                ResXmlAttribute rq = ul.getOrCreateAndroidAttribute("required", ATTR_REQUIRED);
                rq.setValueAsBoolean(false);
            }
        } catch (Throwable ignored) {
        }
        // 3) Eski oyunların http (cleartext) isteklerine izin ver
        try {
            ResXmlElement app = manifest.getOrCreateApplicationElement();
            ResXmlAttribute ct = app.getOrCreateAndroidAttribute("usesCleartextTraffic", ATTR_USES_CLEARTEXT);
            ct.setValueAsBoolean(true);
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasApacheLibrary(AndroidManifestBlock manifest) {
        try {
            for (ResXmlElement e : manifest.listApplicationElementsByTag("uses-library")) {
                ResXmlAttribute n = e.searchAttributeByName("name");
                String v = n == null ? null : n.getValueAsString();
                if (v != null && v.contains("apache.http")) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** APK'nin mevcut targetSdk degerini okur. */
    public static Integer readTargetSdk(File apkFile) throws Exception {
        try (ZipFile zip = new ZipFile(apkFile)) {
            ZipEntry e = zip.getEntry("AndroidManifest.xml");
            if (e == null) return null;
            File tmp = File.createTempFile("eskidroid-read", ".bin");
            try (OutputStream os = new FileOutputStream(tmp)) {
                os.write(readAll(zip.getInputStream(e)));
            }
            try {
                AndroidManifestBlock m = AndroidManifestBlock.load(tmp);
                return m.getTargetSdkVersion();
            } finally {
                tmp.delete();
            }
        }
    }

    private static long computeCrc(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) > 0) bos.write(buf, 0, r);
        is.close();
        return bos.toByteArray();
    }
}
