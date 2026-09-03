package com.focsd.appid;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Binder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkFileProvider extends ContentProvider {
    private static final String APK_PATH = "/pending.apk";
    private static final String PLATFORM_PATH = "/platform-source";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        requireFile(uri);
        return APK_PATH.equals(uri.getPath())
                ? "application/vnd.android.package-archive"
                : "application/octet-stream";
    }

    @Override
    public Cursor query(
            Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File apk = requireFile(uri);
        String[] columns = projection != null ? projection :
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(APK_PATH.equals(uri.getPath())
                        ? "AppId-placeholder.apk" : "platform-source.bin");
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(apk.length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("APK is read-only");
        }
        File apk = requireFile(uri);
        if (!apk.isFile()) {
            throw new FileNotFoundException("No pending APK");
        }
        return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    private File requireFile(Uri uri) {
        if (getContext() == null || uri == null ||
                !(APK_PATH.equals(uri.getPath()) || PLATFORM_PATH.equals(uri.getPath())) ||
                !((getContext().getPackageName() + ".apk").equals(uri.getAuthority()))) {
            throw new IllegalArgumentException("Unsupported AppId file URI");
        }
        if (APK_PATH.equals(uri.getPath())) {
            String caller = getContext().getPackageManager().getNameForUid(Binder.getCallingUid());
            if (caller == null || !(caller.equals(getContext().getPackageName())
                    || caller.startsWith("com.google.android.packageinstaller")
                    || caller.startsWith("com.android.permissioncontroller")
                    || caller.startsWith("com.android.packageinstaller"))) {
                throw new SecurityException("Installer APK is restricted");
            }
            return new File(new File(getContext().getCacheDir(), "installer"), "pending.apk");
        }
        // The platform source is intentionally shared only with Termux. The
        // provider is exported so Android's `content read` command can resolve
        // it reliably across Android/Termux versions, but no other package may
        // use this path.
        String[] callers = getContext().getPackageManager()
                .getPackagesForUid(Binder.getCallingUid());
        boolean termux = false;
        if (callers != null) {
            for (String caller : callers) {
                if ("com.termux".equals(caller)) {
                    termux = true;
                    break;
                }
            }
        }
        if (!termux) throw new SecurityException("Platform source is restricted to Termux");
        return new File(getContext().getFilesDir(), "platform-source");
    }
}
