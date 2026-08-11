package com.focsd.appid;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

final class PlaceholderPackages {
    static final String CREATOR_KEY = "app.placeholder.creator";
    static final String CREATOR_VALUE = "com.focsd.appid";
    private static final String LEGACY_CREATOR_VALUE = "tools.appidcreator";
    private static final String TITLE_KEY = "app.placeholder.titleB64";
    private static final String COLOR_KEY = "app.placeholder.color";

    private PlaceholderPackages() {
    }

    static boolean isPlaceholder(ApplicationInfo info) {
        Bundle metadata = info == null ? null : info.metaData;
        if (metadata == null) return false;
        String creator = metadata.getString(CREATOR_KEY);
        if (CREATOR_VALUE.equals(creator) || LEGACY_CREATOR_VALUE.equals(creator)) return true;
        // Compatibility with placeholders built before the explicit creator marker existed.
        return metadata.containsKey(TITLE_KEY) && metadata.containsKey(COLOR_KEY);
    }

    static String title(ApplicationInfo info, String fallback) {
        Bundle metadata = info == null ? null : info.metaData;
        if (metadata == null) return fallback;
        Object rawTitle = metadataValue(metadata, TITLE_KEY);
        String encoded = rawTitle == null ? "" : String.valueOf(rawTitle);
        if (encoded.isEmpty()) return fallback;
        try {
            return new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    static String color(ApplicationInfo info, String fallback) {
        Bundle metadata = info == null ? null : info.metaData;
        if (metadata == null) return fallback;
        Object rawColor = metadataValue(metadata, COLOR_KEY);
        String color = rawColor == null ? fallback : String.valueOf(rawColor);
        return color.startsWith("#") ? color : "#" + color;
    }

    @SuppressWarnings("deprecation")
    private static Object metadataValue(Bundle metadata, String key) {
        // BaseBundle has no type-neutral replacement; metadata may be String or Integer.
        return metadata.get(key);
    }

    @SuppressWarnings("deprecation")
    static ApplicationInfo installedInfo(Context context, String packageName) {
        try {
            PackageManager manager = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= 33) {
                return manager.getApplicationInfo(packageName,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
            }
            return manager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }
}
