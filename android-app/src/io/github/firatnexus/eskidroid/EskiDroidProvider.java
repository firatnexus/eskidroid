package io.github.firatnexus.eskidroid;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Kurulum ekranına APK'yi content:// ile sunan basit sağlayıcı.
 * Uygulamanın önbellek klasöründeki dosyalara okuma izni verir.
 */
public class EskiDroidProvider extends ContentProvider {

    public static final String AUTHORITY = "io.github.firatnexus.eskidroid.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Kurulum için content:// adresi üretir. */
    public static Uri uriFor(File apk) {
        return Uri.parse("content://" + AUTHORITY + "/apk/" + Uri.encode(apk.getName()));
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.contains("w")) {
            throw new SecurityException("Salt okunur");
        }
        String name = Uri.decode(uri.getLastPathSegment());
        File dir = getContext().getCacheDir();
        File f = new File(dir, name);
        if (!f.getAbsoluteFile().getParentFile().equals(dir.getAbsoluteFile())) {
            throw new SecurityException("Geçersiz yol");
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
