package com.focsd.appid;

final class UiStateRules {
    private UiStateRules() {
    }

    static boolean shouldShowCreatorProgress(String operationState, String operationType) {
        boolean completedBuild = "success".equals(operationState) &&
                ("build".equals(operationType) || "build_install".equals(operationType));
        return !completedBuild;
    }

    static boolean shouldInitializeCreator(boolean setupDestination) {
        return !setupDestination;
    }

    static boolean shouldSettleAwaitingInstaller(String operationState) {
        return "awaiting_install".equals(operationState);
    }

    static boolean hasUnreadApk(long latestSequence, long seenSequence) {
        return latestSequence > 0L && latestSequence > seenSequence;
    }

    static long nextApkSequence(long previousSequence, long clockValue) {
        if (previousSequence == Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(previousSequence + 1L, Math.max(1L, clockValue));
    }
}
