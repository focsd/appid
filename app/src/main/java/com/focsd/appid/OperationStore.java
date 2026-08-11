package com.focsd.appid;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class OperationStore {
    static final String PREFS = "operation_state";
    static final String KEY_TOKEN = "token";
    static final String KEY_LABEL = "label";
    static final String KEY_TYPE = "type";
    static final String KEY_STATE = "state";
    static final String KEY_STAGE = "stage";
    static final String KEY_DETAIL = "detail";
    static final String KEY_CONSOLE = "console";
    static final String KEY_REPORT = "environment_report";
    static final String KEY_INSTALL_PACKAGE = "install_package";
    static final String KEY_INSTALL_OBSERVED = "install_observed";
    static final String KEY_BUILD_TITLE = "build_title";
    static final String KEY_BUILD_COLOR = "build_color";
    static final String KEY_UPDATED_AT = "updated_at";

    private static final int MAX_CONSOLE_CHARS = 100_000;

    private OperationStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized void start(Context context, String token, String label, String type, String installPackage) {
        String startLine = timestamp() + "  " + label + " started\n";
        prefs(context).edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_LABEL, label)
                .putString(KEY_TYPE, type)
                .putString(KEY_STATE, "running")
                .putString(KEY_STAGE, label)
                .putString(KEY_DETAIL, "Starting…")
                .putString(KEY_CONSOLE, startLine)
                .putString(KEY_INSTALL_PACKAGE, installPackage == null ? "" : installPackage)
                .putBoolean(KEY_INSTALL_OBSERVED, false)
                .remove(KEY_BUILD_TITLE)
                .remove(KEY_BUILD_COLOR)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
        appendDailyLog(context, "\n=== " + startLine);
    }

    static synchronized void setBuildSpec(
            Context context, String token, String title, String color) {
        if (!tokenMatches(context, token)) return;
        prefs(context).edit()
                .putString(KEY_BUILD_TITLE, value(title))
                .putString(KEY_BUILD_COLOR, value(color))
                .apply();
    }

    static synchronized boolean tokenMatches(Context context, String token) {
        return token != null && token.equals(prefs(context).getString(KEY_TOKEN, ""));
    }

    static synchronized void progress(Context context, String token, String stage, String detail, String report) {
        if (!tokenMatches(context, token)) return;
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(KEY_STAGE, value(stage))
                .putString(KEY_DETAIL, value(detail))
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis());
        if ("Failed".equalsIgnoreCase(value(stage))) {
            editor.putString(KEY_STATE, "failed");
        }
        if (report != null && !report.trim().isEmpty()) {
            editor.putString(KEY_REPORT, report);
        }
        editor.apply();
        if (stage != null && !stage.trim().isEmpty()) {
            appendConsole(context, timestamp() + "  " + stage +
                    (detail == null || detail.trim().isEmpty() ? "" : " — " + detail) + "\n");
        }
    }

    static synchronized void appendLiveLog(Context context, String token, String log) {
        if (!tokenMatches(context, token) || log == null || log.trim().isEmpty()) return;
        appendConsole(context, log + "\n");
    }

    static synchronized void finishCommand(
            Context context,
            String token,
            boolean success,
            int exitCode,
            int internalError,
            String internalMessage,
            String stdout,
            String stderr
    ) {
        if (!tokenMatches(context, token)) return;
        SharedPreferences preferences = prefs(context);
        if ("stopping".equals(preferences.getString(KEY_STATE, "")) ||
                "cancelled".equals(preferences.getString(KEY_STATE, ""))) return;
        String type = preferences.getString(KEY_TYPE, "");
        boolean installObserved = preferences.getBoolean(KEY_INSTALL_OBSERVED, false);
        String previousStage = preferences.getString(KEY_STAGE, "");
        String previousDetail = preferences.getString(KEY_DETAIL, "");
        if (success && "setup".equals(type) &&
                !("Environment ready".equals(previousStage) ||
                        "Environment incomplete".equals(previousStage))) {
            success = false;
            internalMessage = "Setup ended before completing all four stages. Retry with the latest AppId build.";
        }
        boolean awaitingInstaller = success && "build_install".equals(type) && !installObserved;
        String state = awaitingInstaller ? "awaiting_install" : (success ? "success" : "failed");
        String stage;
        String detail;
        if (success && installObserved) {
            stage = "APK installed";
            detail = preferences.getString(KEY_INSTALL_PACKAGE, "");
        } else if (awaitingInstaller) {
            stage = "Awaiting Android installer";
            detail = "Confirm installation in Android. AppId will detect a successful package add or update.";
        } else if (success && ("setup".equals(type) || "check".equals(type) || "build".equals(type))) {
            stage = previousStage.isEmpty() ? "Command complete" : previousStage;
            detail = previousDetail.isEmpty() ? "Exit code 0" : previousDetail;
        } else {
            stage = success ? "Command complete" : "Command failed";
            detail = internalMessage != null && !internalMessage.trim().isEmpty()
                    ? internalMessage
                    : "Exit code " + exitCode +
                    (internalError == -1 ? "" : ", Termux error " + internalError);
        }

        StringBuilder transcript = new StringBuilder();
        transcript.append("\n").append(timestamp()).append("  RESULT\n");
        transcript.append("exitCode=").append(exitCode).append(" termuxInternalError=")
                .append(internalError == -1 ? "none" : internalError).append("\n");
        if (internalMessage != null && !internalMessage.trim().isEmpty()) {
            transcript.append("internal error: ").append(internalMessage).append("\n");
        }
        if (stdout != null && !stdout.isEmpty()) {
            transcript.append("\n--- stdout ---\n").append(stdout);
            if (!stdout.endsWith("\n")) transcript.append('\n');
        }
        if (stderr != null && !stderr.isEmpty()) {
            transcript.append("\n--- stderr ---\n").append(stderr);
            if (!stderr.endsWith("\n")) transcript.append('\n');
        }

        preferences.edit()
                .putString(KEY_STATE, state)
                .putString(KEY_STAGE, stage)
                .putString(KEY_DETAIL, detail)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
        appendConsole(context, transcript.toString());
    }

    static synchronized void failToStart(Context context, String token, String message) {
        if (!tokenMatches(context, token)) return;
        prefs(context).edit()
                .putString(KEY_STATE, "failed")
                .putString(KEY_STAGE, "Could not start command")
                .putString(KEY_DETAIL, value(message))
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
        appendConsole(context, timestamp() + "  START FAILED — " + value(message) + "\n");
    }

    static synchronized void markStopping(Context context, String token) {
        if (!tokenMatches(context, token)) return;
        prefs(context).edit()
                .putString(KEY_STATE, "stopping")
                .putString(KEY_STAGE, "Stopping operation")
                .putString(KEY_DETAIL, "Sending targeted stop signals to the AppId process tree…")
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
        appendConsole(context, timestamp() + "  STOP REQUESTED\n");
    }

    static synchronized void finishCancellation(
            Context context, String token, boolean success, String stdout, String stderr) {
        if (!tokenMatches(context, token)) return;
        prefs(context).edit()
                .putString(KEY_STATE, success ? "cancelled" : "failed")
                .putString(KEY_STAGE, success ? "Operation stopped" : "Stop command failed")
                .putString(KEY_DETAIL, success
                        ? "The AppId Termux process tree was terminated."
                        : "Open Termux and stop the process manually.")
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
        StringBuilder output = new StringBuilder(timestamp())
                .append(success ? "  STOPPED\n" : "  STOP FAILED\n");
        if (stdout != null && !stdout.isEmpty()) output.append(stdout).append('\n');
        if (stderr != null && !stderr.isEmpty()) output.append(stderr).append('\n');
        appendConsole(context, output.toString());
    }

    static synchronized void packageInstalled(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        if (!"build_install".equals(preferences.getString(KEY_TYPE, ""))) return;
        if (!packageName.equals(preferences.getString(KEY_INSTALL_PACKAGE, ""))) return;
        preferences.edit()
                .putBoolean(KEY_INSTALL_OBSERVED, true)
                .putString(KEY_STATE, "success")
                .putString(KEY_STAGE, "APK installed")
                .putString(KEY_DETAIL, packageName)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
        appendConsole(context, timestamp() + "  INSTALL SUCCESS — " + packageName + "\n");
    }

    static synchronized void installerLaunchFailed(Context context, String token, String message) {
        if (!tokenMatches(context, token)) return;
        prefs(context).edit()
                .putString(KEY_STATE, "failed")
                .putString(KEY_STAGE, "Could not open Android installer")
                .putString(KEY_DETAIL, value(message))
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
        appendConsole(context, timestamp() + "  INSTALLER FAILED — " + value(message) + "\n");
    }

    static synchronized boolean isRunning(Context context) {
        SharedPreferences preferences = prefs(context);
        String state = preferences.getString(KEY_STATE, "");
        boolean running = "running".equals(state) || "stopping".equals(state);
        if (running && "Failed".equalsIgnoreCase(preferences.getString(KEY_STAGE, ""))) {
            preferences.edit().putString(KEY_STATE, "failed").apply();
            return false;
        }
        return running;
    }

    static synchronized String getToken(Context context) {
        return prefs(context).getString(KEY_TOKEN, "");
    }

    static synchronized SharedPreferences snapshot(Context context) {
        return prefs(context);
    }

    static synchronized void clearConsole(Context context) {
        prefs(context).edit().putString(KEY_CONSOLE, "").apply();
    }

    static synchronized String readTodayLog(Context context) {
        File file = dailyLogFile(context);
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), Integer.MAX_VALUE)];
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) break;
                offset += count;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    static synchronized String todayLogName() {
        return logDate() + ".log";
    }

    private static void appendConsole(Context context, String addition) {
        SharedPreferences preferences = prefs(context);
        String combined = preferences.getString(KEY_CONSOLE, "") + addition;
        if (combined.length() > MAX_CONSOLE_CHARS) {
            combined = "[older output truncated]\n" +
                    combined.substring(combined.length() - MAX_CONSOLE_CHARS);
        }
        preferences.edit().putString(KEY_CONSOLE, combined).apply();
        appendDailyLog(context, addition);
    }

    private static void appendDailyLog(Context context, String addition) {
        File file = dailyLogFile(context);
        File parent = file.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return;
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(addition.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // The on-screen console remains available if persistent logging fails.
        }
    }

    private static File dailyLogFile(Context context) {
        return new File(new File(context.getFilesDir(), "logs"), todayLogName());
    }

    private static String logDate() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String timestamp() {
        return new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date());
    }
}
