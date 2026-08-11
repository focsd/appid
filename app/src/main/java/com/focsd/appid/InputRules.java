package com.focsd.appid;

import java.util.Locale;
import java.util.regex.Pattern;

final class InputRules {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"
    );
    private static final Pattern COLOR_PATTERN = Pattern.compile("#?[0-9A-Fa-f]{6}");

    private InputRules() {
    }

    static boolean isPackageId(String value) {
        return value != null && PACKAGE_PATTERN.matcher(value).matches();
    }

    static String normalizeColor(String value) {
        if (value == null || !COLOR_PATTERN.matcher(value).matches()) return null;
        String hex = value.startsWith("#") ? value.substring(1) : value;
        return "#" + hex.toUpperCase(Locale.US);
    }
}
