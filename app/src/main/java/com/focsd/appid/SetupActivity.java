package com.focsd.appid;

/** Dedicated back-stack destination for the Setup & tools screen. */
public final class SetupActivity extends AppIdActivity {
    @Override
    protected boolean isSetupDestination() {
        return true;
    }
}
