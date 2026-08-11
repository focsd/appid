package com.focsd.appid;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;

import java.io.File;
import java.io.IOException;

public class CommandResultService extends Service {
    static final String EXTRA_OPERATION_TOKEN = "com.focsd.appid.extra.OPERATION_TOKEN";
    static final String EXTRA_CANCELLATION = "com.focsd.appid.extra.CANCELLATION";
    static final String ACTION_RESULT_UPDATED = "com.focsd.appid.COMMAND_RESULT_UPDATED";
    private static final String APK_BEGIN = "FOCSD_APPID_APK_BASE64_BEGIN\n";
    private static final String APK_END = "\nFOCSD_APPID_APK_BASE64_END";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String token = intent.getStringExtra(EXTRA_OPERATION_TOKEN);
        Bundle result = intent.getBundleExtra("result");
        if (result == null) {
            OperationStore.failToStart(this, token, "Termux returned no result bundle.");
            notifyUi();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        int exitCode = result.containsKey("exitCode") ?
                result.getInt("exitCode", -1) : result.getInt("exit_code", -1);
        int internalError = result.getInt("err", Activity.RESULT_OK);
        String internalMessage = result.getString("errmsg", "");
        String stdout = result.getString("stdout", "");
        String stderr = result.getString("stderr", "");
        boolean success = exitCode == 0 && internalError == Activity.RESULT_OK;

        if (intent.getBooleanExtra(EXTRA_CANCELLATION, false)) {
            OperationStore.finishCancellation(this, token, success, stdout, stderr);
            notifyUi();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        byte[] apk = null;
        int payloadStart = stdout.indexOf(APK_BEGIN);
        int payloadEnd = payloadStart < 0 ? -1 : stdout.indexOf(APK_END, payloadStart);
        if (payloadStart >= 0 && payloadEnd > payloadStart) {
            String encoded = stdout.substring(payloadStart + APK_BEGIN.length(), payloadEnd)
                    .replaceAll("\\s", "");
            try {
                apk = Base64.decode(encoded, Base64.DEFAULT);
                stdout = stdout.substring(0, payloadStart) +
                        "[APK payload transferred to AppId: " + apk.length + " bytes]\n" +
                        stdout.substring(payloadEnd + APK_END.length());
            } catch (IllegalArgumentException e) {
                success = false;
                internalMessage = "The APK transfer from Termux was invalid.";
            }
        }

        android.content.SharedPreferences operation = OperationStore.snapshot(this);
        String operationType = operation.getString(OperationStore.KEY_TYPE, "");
        boolean buildOperation = "build".equals(operationType) ||
                "build_install".equals(operationType);
        boolean installRequested = "build_install".equals(operationType);
        if (success && buildOperation && apk == null) {
            success = false;
            internalMessage = "Termux completed the build, but did not return the APK to AppId. " +
                    "Retry using this updated AppId build.";
        }
        File libraryFile = null;
        if (success && buildOperation) {
            try {
                BuiltApkStore.Record saved = BuiltApkStore.save(
                        this,
                        apk,
                        operation.getString(OperationStore.KEY_INSTALL_PACKAGE, ""),
                        operation.getString(OperationStore.KEY_BUILD_TITLE, ""),
                        operation.getString(OperationStore.KEY_BUILD_COLOR, ""));
                libraryFile = saved.file;
                stdout += "\nSaved in AppId built APK library: " + saved.packageName + "\n";
            } catch (IOException error) {
                success = false;
                internalMessage = error.getMessage();
            }
        }

        OperationStore.finishCommand(
                this, token, success, exitCode, internalError,
                internalMessage, stdout, stderr
        );
        if (success && installRequested && libraryFile != null) {
            String error = ApkInstaller.open(this, libraryFile);
            if (error != null) OperationStore.installerLaunchFailed(this, token, error);
        }
        notifyUi();
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void notifyUi() {
        Intent update = new Intent(ACTION_RESULT_UPDATED);
        update.setPackage(getPackageName());
        sendBroadcast(update);
    }

}
