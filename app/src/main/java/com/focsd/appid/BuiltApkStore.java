package com.focsd.appid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BuiltApkStore {
    private static final String PREFS = "built_apks";
    private static final String KEY_PACKAGES = "packages";

    private BuiltApkStore() {
    }

    static final class Record {
        final String packageName;
        final String title;
        final String color;
        final long builtAt;
        final File file;
        final boolean installed;
        final boolean installedPlaceholder;

        Record(String packageName, String title, String color, long builtAt, File file,
               boolean installed, boolean installedPlaceholder) {
            this.packageName = packageName;
            this.title = title;
            this.color = color;
            this.builtAt = builtAt;
            this.file = file;
            this.installed = installed;
            this.installedPlaceholder = installedPlaceholder;
        }

        boolean hasApk() {
            return file.isFile();
        }

        boolean realAppInstalled() {
            return installed && !installedPlaceholder;
        }
    }

    static synchronized Record save(
            Context context, byte[] apk, String packageName, String title, String color)
            throws IOException {
        if (!InputRules.isPackageId(packageName)) {
            throw new IOException("Build metadata contains an invalid package ID");
        }
        if (apk == null || apk.length < 4 || apk[0] != 'P' || apk[1] != 'K') {
            throw new IOException("Transferred APK is invalid");
        }
        File directory = directory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create the built APK library");
        }
        File target = fileFor(context, packageName);
        File temporary = new File(directory, packageName + ".apk.tmp");
        File backup = new File(directory, packageName + ".apk.backup");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(apk);
            output.getFD().sync();
        }
        if (backup.exists() && !backup.delete()) {
            temporary.delete();
            throw new IOException("Could not clear an old library backup");
        }
        if (target.exists() && !target.renameTo(backup)) {
            temporary.delete();
            throw new IOException("Could not preserve the previous library APK");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            if (backup.exists()) backup.renameTo(target);
            throw new IOException("Could not commit the APK to the library");
        }
        if (backup.exists()) backup.delete();

        long builtAt = System.currentTimeMillis();
        SharedPreferences preferences = prefs(context);
        Set<String> packages = packageSet(preferences);
        packages.add(packageName);
        preferences.edit()
                .putStringSet(KEY_PACKAGES, packages)
                .putString(key(packageName, "title"), emptyDefault(title, packageName))
                .putString(key(packageName, "color"), normalizeColor(color))
                .putLong(key(packageName, "built_at"), builtAt)
                .apply();
        ApplicationInfo installed = PlaceholderPackages.installedInfo(context, packageName);
        return new Record(packageName, emptyDefault(title, packageName), normalizeColor(color),
                builtAt, target, installed != null, PlaceholderPackages.isPlaceholder(installed));
    }

    @SuppressWarnings("deprecation")
    static synchronized List<Record> list(Context context) {
        cleanupInstalledInstallers(context);
        SharedPreferences preferences = prefs(context);
        Set<String> packages = packageSet(preferences);
        PackageManager manager = context.getPackageManager();
        List<ApplicationInfo> installedApps;
        if (Build.VERSION.SDK_INT >= 33) {
            installedApps = manager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
        } else {
            installedApps = manager.getInstalledApplications(PackageManager.GET_META_DATA);
        }
        Map<String, ApplicationInfo> installed = new HashMap<>();
        for (ApplicationInfo info : installedApps) {
            installed.put(info.packageName, info);
            if (PlaceholderPackages.isPlaceholder(info)) packages.add(info.packageName);
        }

        List<Record> records = new ArrayList<>();
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        for (String packageName : new HashSet<>(packages)) {
            File file = fileFor(context, packageName);
            ApplicationInfo info = installed.get(packageName);
            if (!file.isFile() && info == null) {
                packages.remove(packageName);
                removeMetadata(editor, packageName);
                changed = true;
                continue;
            }
            boolean placeholder = PlaceholderPackages.isPlaceholder(info);
            String fallbackTitle = packageName;
            if (info != null) {
                CharSequence label = manager.getApplicationLabel(info);
                if (label != null && label.length() > 0) fallbackTitle = label.toString();
                fallbackTitle = PlaceholderPackages.title(info, fallbackTitle);
            }
            String title = preferences.getString(key(packageName, "title"), fallbackTitle);
            String color = preferences.getString(key(packageName, "color"),
                    PlaceholderPackages.color(info, "#F2F2F2"));
            long builtAt = preferences.getLong(key(packageName, "built_at"),
                    file.isFile() ? file.lastModified() : 0L);
            records.add(new Record(packageName, title, color, builtAt, file,
                    info != null, placeholder));
            if (!preferences.contains(key(packageName, "title"))) {
                editor.putString(key(packageName, "title"), title)
                        .putString(key(packageName, "color"), color)
                        .putLong(key(packageName, "built_at"), builtAt);
                changed = true;
            }
        }
        if (!packages.equals(packageSet(preferences))) changed = true;
        if (changed) editor.putStringSet(KEY_PACKAGES, packages).apply();
        records.sort((first, second) -> {
            if (first.installedPlaceholder != second.installedPlaceholder) {
                return first.installedPlaceholder ? -1 : 1;
            }
            return Long.compare(second.builtAt, first.builtAt);
        });
        return records;
    }

    static synchronized boolean deleteApk(Context context, Record record) {
        if (record == null) return false;
        boolean removed = !record.file.exists() || record.file.delete();
        if (!removed) return false;
        ApplicationInfo installed = PlaceholderPackages.installedInfo(context,
                record.packageName);
        if (!PlaceholderPackages.isPlaceholder(installed)) {
            removeRecord(context, record.packageName);
        }
        return true;
    }

    static synchronized void deleteInstallerAfterInstall(Context context, String packageName) {
        File file = fileFor(context, packageName);
        if (file.isFile()) file.delete();
        // Metadata deliberately remains while the placeholder is installed.
    }

    static synchronized int cleanupInstalledInstallers(Context context) {
        int deleted = 0;
        File[] installers = directory(context).listFiles(
                file -> file.isFile() && file.getName().endsWith(".apk"));
        if (installers == null) return 0;
        for (File file : installers) {
            String packageName = file.getName().substring(0,
                    file.getName().length() - ".apk".length());
            ApplicationInfo info = PlaceholderPackages.installedInfo(context, packageName);
            if (!PlaceholderPackages.isPlaceholder(info)) continue;
            if (file.delete()) deleted++;
        }
        if (deleted > 0) ApkInstaller.clearPending(context);
        return deleted;
    }

    private static void removeRecord(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        Set<String> packages = packageSet(preferences);
        packages.remove(packageName);
        SharedPreferences.Editor editor = preferences.edit().putStringSet(KEY_PACKAGES, packages);
        removeMetadata(editor, packageName);
        editor.apply();
    }

    private static void removeMetadata(SharedPreferences.Editor editor, String packageName) {
        editor.remove(key(packageName, "title"))
                .remove(key(packageName, "color"))
                .remove(key(packageName, "built_at"));
    }

    private static Set<String> packageSet(SharedPreferences preferences) {
        return new HashSet<>(preferences.getStringSet(KEY_PACKAGES, Collections.emptySet()));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File directory(Context context) {
        return new File(context.getFilesDir(), "built-apks");
    }

    private static File fileFor(Context context, String packageName) {
        return new File(directory(context), packageName + ".apk");
    }

    private static String key(String packageName, String suffix) {
        return packageName + "." + suffix;
    }

    private static String normalizeColor(String color) {
        String normalized = InputRules.normalizeColor(color);
        return normalized == null ? "#F2F2F2" : normalized;
    }

    private static String emptyDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
