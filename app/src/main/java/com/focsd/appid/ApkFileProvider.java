package com.focsd.appid;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkFileProvider extends ContentProvider {
    private static final String APK_PATH = "/pending.apk";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        requirePendingApk(uri);
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(
            Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File apk = requirePendingApk(uri);
        String[] columns = projection != null ? projection :
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add("AppId-placeholder.apk");
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
        File apk = requirePendingApk(uri);
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

    private File requirePendingApk(Uri uri) {
        if (getContext() == null || uri == null || !APK_PATH.equals(uri.getPath()) ||
                !((getContext().getPackageName() + ".apk").equals(uri.getAuthority()))) {
            throw new IllegalArgumentException("Unsupported APK URI");
        }
        return new File(new File(getContext().getCacheDir(), "installer"), "pending.apk");
    }
}
