package com.focsd.appid;

import java.util.Locale;

final class AppValueFormatter {
    private AppValueFormatter() {
    }

    static String duration(long millis) {
        if (millis <= 0) return "0m";
        long totalMinutes = Math.max(1, millis / 60_000L);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) return minutes + "m";
        return hours + "h " + minutes + "m";
    }

    static String bytes(long bytes, Locale locale) {
        if (bytes < 1024L) return Math.max(0L, bytes) + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024d;
            unit++;
        } while (value >= 1024d && unit < units.length - 1);
        return String.format(locale, value >= 100d ? "%.0f %s" : "%.1f %s",
                value, units[unit]);
    }

    static long safeAdd(long first, long second) {
        if (first < 0L || second < 0L) return 0L;
        return Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second;
    }
}
