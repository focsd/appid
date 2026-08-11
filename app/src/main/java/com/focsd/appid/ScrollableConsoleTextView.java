package com.focsd.appid;

import android.content.Context;
import android.widget.TextView;

/** Text console that exposes touch clicks correctly to accessibility services. */
final class ScrollableConsoleTextView extends TextView {
    ScrollableConsoleTextView(Context context) {
        super(context);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
