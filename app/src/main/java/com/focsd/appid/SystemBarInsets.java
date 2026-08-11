package com.focsd.appid;

import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

final class SystemBarInsets {
    private SystemBarInsets() {
    }

    // The legacy inset accessors are required for this app's Android 8-10 support.
    @SuppressWarnings("deprecation")
    static void applyTo(View view) {
        final int initialLeft = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        view.setOnApplyWindowInsetsListener((target, windowInsets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets safe = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = windowInsets.getSystemWindowInsetLeft();
                top = windowInsets.getSystemWindowInsetTop();
                right = windowInsets.getSystemWindowInsetRight();
                bottom = windowInsets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout = windowInsets.getDisplayCutout();
                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }
            }
            target.setPadding(
                    initialLeft + left,
                    initialTop + top,
                    initialRight + right,
                    initialBottom + bottom);
            return windowInsets;
        });
        view.requestApplyInsets();
    }
}
