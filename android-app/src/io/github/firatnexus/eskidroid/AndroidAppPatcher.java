package io.github.firatnexus.eskidroid;

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;

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
 * Eski APK'yi modern Android'de kurulabilir yapmak icin:
 * AndroidManifest.xml icindeki targetSdkVersion >= 23 yapilir.
 * Diger dosyalar ayni sikistirma metoduyla kopyalanir.
 */
public final class AndroidAppPatcher {

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
