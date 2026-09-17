package com.focsd.appid;

import android.content.Context;

import androidx.appcompat.widget.AppCompatTextView;

/** Text console that exposes touch clicks correctly to accessibility services. */
final class ScrollableConsoleTextView extends AppCompatTextView {
    ScrollableConsoleTextView(Context context) {
        super(context);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
