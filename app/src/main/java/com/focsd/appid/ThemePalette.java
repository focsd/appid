package com.focsd.appid;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * Shared semantic colors for views that are assembled in Java rather than XML.
 * Keeping the palette here prevents an otherwise system-themed screen from
 * becoming unreadable because one label or card retained a light-only color.
 */
final class ThemePalette {
    private ThemePalette() {
    }

    static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static int background(Context context) {
        return isNight(context) ? Color.rgb(28, 30, 34) : Color.rgb(250, 250, 252);
    }

    static int surface(Context context) {
        return isNight(context) ? Color.rgb(38, 41, 47) : Color.WHITE;
    }

    static int mutedSurface(Context context) {
        return isNight(context) ? Color.rgb(45, 48, 55) : Color.rgb(244, 245, 248);
    }

    static int primaryText(Context context) {
        return isNight(context) ? Color.rgb(242, 242, 246) : Color.rgb(35, 35, 40);
    }

    static int secondaryText(Context context) {
        return isNight(context) ? Color.rgb(188, 192, 202) : Color.rgb(92, 96, 105);
    }

    static int link(Context context) {
        return isNight(context) ? Color.rgb(157, 183, 255) : Color.rgb(35, 95, 150);
    }

    static int primaryAction(Context context) {
        return isNight(context) ? Color.rgb(89, 122, 199) : Color.rgb(45, 79, 150);
    }

    static int onPrimaryAction() {
        return Color.WHITE;
    }

    static int successText(Context context) {
        return isNight(context) ? Color.rgb(151, 224, 178) : Color.rgb(28, 105, 62);
    }

    static int successSurface(Context context) {
        return isNight(context) ? Color.rgb(30, 65, 45) : Color.rgb(232, 246, 237);
    }

    static int warningText(Context context) {
        return isNight(context) ? Color.rgb(244, 196, 132) : Color.rgb(135, 79, 24);
    }

    static int warningSurface(Context context) {
        return isNight(context) ? Color.rgb(70, 50, 29) : Color.rgb(252, 242, 226);
    }

    static int consoleText(Context context) {
        return isNight(context) ? Color.rgb(225, 235, 225) : Color.rgb(36, 55, 40);
    }

    static int consoleSurface(Context context) {
        return isNight(context) ? Color.rgb(20, 23, 20) : Color.rgb(239, 245, 239);
    }
}
