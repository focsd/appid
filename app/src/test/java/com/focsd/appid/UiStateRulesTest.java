package com.focsd.appid;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class UiStateRulesTest {
    @Test
    public void successfulBuildProgressIsHidden() {
        assertFalse(UiStateRules.shouldShowCreatorProgress("success", "build"));
        assertFalse(UiStateRules.shouldShowCreatorProgress("success", "build_install"));
        assertTrue(UiStateRules.shouldShowCreatorProgress("running", "build"));
        assertTrue(UiStateRules.shouldShowCreatorProgress("failed", "build"));
        assertTrue(UiStateRules.shouldShowCreatorProgress("success", "setup"));
    }

    @Test
    public void setupDestinationDoesNotInitializeTheCreatorScreen() {
        assertTrue(UiStateRules.shouldInitializeCreator(false));
        assertFalse(UiStateRules.shouldInitializeCreator(true));
    }

    @Test
    public void onlyAnUnresolvedInstallerStateIsSettledOnResume() {
        assertTrue(UiStateRules.shouldSettleAwaitingInstaller("awaiting_install"));
        assertFalse(UiStateRules.shouldSettleAwaitingInstaller("success"));
        assertFalse(UiStateRules.shouldSettleAwaitingInstaller("failed"));
        assertFalse(UiStateRules.shouldSettleAwaitingInstaller(null));
    }

    @Test
    public void unreadApkRequiresASequenceNewerThanTheAcknowledgement() {
        assertFalse(UiStateRules.hasUnreadApk(0L, 0L));
        assertTrue(UiStateRules.hasUnreadApk(11L, 10L));
        assertFalse(UiStateRules.hasUnreadApk(11L, 11L));
        assertFalse(UiStateRules.hasUnreadApk(10L, 11L));
    }

    @Test
    public void apkSequenceIsMonotonicEvenWhenClockDoesNotAdvance() {
        assertEquals(101L, UiStateRules.nextApkSequence(100L, 90L));
        assertEquals(500L, UiStateRules.nextApkSequence(100L, 500L));
        assertEquals(Long.MAX_VALUE,
                UiStateRules.nextApkSequence(Long.MAX_VALUE, 1L));
    }
}
