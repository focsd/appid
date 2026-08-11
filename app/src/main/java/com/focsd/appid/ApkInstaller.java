package com.focsd.appid;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

final class ApkInstaller {
    private ApkInstaller() {
    }

    static String open(Context context, File source) {
        if (source == null || !source.isFile()) return "The built APK file is missing.";
        File installDirectory = new File(context.getCacheDir(), "installer");
        if (!installDirectory.isDirectory() && !installDirectory.mkdirs()) {
            return "Could not create the installer cache.";
        }
        File pending = new File(installDirectory, "pending.apk");
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(pending, false)) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                total += read;
            }
            if (total < 4) return "The built APK is invalid.";
        } catch (IOException error) {
            return "Could not prepare the APK: " + error.getMessage();
        }

        Uri uri = Uri.parse("content://" + context.getPackageName() + ".apk/pending.apk");
        Intent installer = new Intent(Intent.ACTION_VIEW);
        installer.setDataAndType(uri, "application/vnd.android.package-archive");
        installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!(context instanceof android.app.Activity)) {
            installer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(installer);
            return null;
        } catch (Exception error) {
            return "Android package installer could not be opened: " + error.getMessage();
        }
    }

    static void clearPending(Context context) {
        File pending = new File(new File(context.getCacheDir(), "installer"), "pending.apk");
        if (pending.isFile()) pending.delete();
    }
}
