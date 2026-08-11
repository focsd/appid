package com.focsd.appid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

public class PackageInstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) return;
        String packageName = intent.getData().getSchemeSpecificPart();
        if (packageName == null || packageName.isEmpty()) return;
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            OperationStore.packageInstalled(context, packageName);
            ApplicationInfo info = PlaceholderPackages.installedInfo(context, packageName);
            if (PlaceholderPackages.isPlaceholder(info)) {
                BuiltApkStore.deleteInstallerAfterInstall(context, packageName);
                ApkInstaller.clearPending(context);
            }
        }

        Intent update = new Intent(CommandResultService.ACTION_RESULT_UPDATED);
        update.setPackage(context.getPackageName());
        context.sendBroadcast(update);
    }
}
