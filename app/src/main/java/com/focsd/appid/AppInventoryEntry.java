package com.focsd.appid;

import android.graphics.drawable.Drawable;

final class AppInventoryEntry {
    final String label;
    final String packageName;
    final int uid;
    final String versionName;
    final long versionCode;
    final boolean systemApp;
    final boolean placeholderApp;
    final Drawable icon;
    final long todayForegroundMillis;
    final boolean usageAvailable;
    final long occupiedBytes;
    final boolean storageAvailable;

    AppInventoryEntry(
            String label,
            String packageName,
            int uid,
            String versionName,
            long versionCode,
            boolean systemApp,
            boolean placeholderApp,
            Drawable icon,
            long todayForegroundMillis,
            boolean usageAvailable,
            long occupiedBytes,
            boolean storageAvailable
    ) {
        this.label = label;
        this.packageName = packageName;
        this.uid = uid;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.systemApp = systemApp;
        this.placeholderApp = placeholderApp;
        this.icon = icon;
        this.todayForegroundMillis = todayForegroundMillis;
        this.usageAvailable = usageAvailable;
        this.occupiedBytes = occupiedBytes;
        this.storageAvailable = storageAvailable;
    }
}
