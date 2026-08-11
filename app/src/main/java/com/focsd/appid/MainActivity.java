package com.focsd.appid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_SERVICE = "com.termux.app.RunCommandService";
    private static final String TERMUX_ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    private static final String TERMUX_PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND";
    private static final String TERMUX_INSTALL_GUIDE =
            "https://github.com/termux/termux-app#installation";
    private static final String ACTION_BUILD_PROGRESS =
            "com.focsd.appid.BUILD_PROGRESS";
    // These belong to the external Termux package, so Context file APIs cannot resolve them.
    @SuppressLint("SdCardPath")
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    @SuppressLint("SdCardPath")
    private static final String TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash";

    private static final String INIT_COMMAND =
            "mkdir -p ~/.termux; touch ~/.termux/termux.properties; " +
            "grep -q '^allow-external-apps=true$' ~/.termux/termux.properties || " +
            "printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties";

    private static final String DEFAULT_TITLE = "Pause";
    private static final String DEFAULT_PACKAGE = "com.example.placeholder";
    private static final String DEFAULT_COLOR = "#F2F2F2";

    private EditText titleInput;
    private EditText packageInput;
    private EditText colorInput;
    private TextView statusText;
    private TextView progressText;
    private TextView environmentText;
    private ScrollableConsoleTextView consoleText;
    private View colorPreview;
    private String activeProgressToken;

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CommandResultService.ACTION_RESULT_UPDATED.equals(intent.getAction())) {
                restoreOperationUi();
                return;
            }

            String token = intent.getStringExtra("token");
            if (!OperationStore.tokenMatches(MainActivity.this, token)) return;

            String stage = intent.getStringExtra("stage");
            String detail = intent.getStringExtra("detail");
            String report = intent.getStringExtra("report");
            String log = intent.getStringExtra("log");
            if (log != null && !log.trim().isEmpty()) {
                OperationStore.appendLiveLog(MainActivity.this, token, log);
            }
            if (stage != null && !stage.trim().isEmpty()) {
                OperationStore.progress(MainActivity.this, token, stage, detail, report);
            }
            restoreOperationUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerProgressReceiver();
        setTitle("AppId");
        setContentView(buildUi());
        refreshStatus();
        restoreOperationUi();
        maybeRunInitialEnvironmentCheck();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView intro = text(
                "Create a tiny local placeholder APK on the phone. Termux performs the actual compile, package and signing steps."
        );
        intro.setTextSize(16f);
        root.addView(intro);

        Button appViewer = button("Installed apps, App IDs, screen time & storage",
                v -> startActivity(new Intent(this, AppViewerActivity.class)));
        root.addView(appViewer);
        Button builtApks = button("Built APK library",
                v -> startActivity(new Intent(this, BuiltApksActivity.class)));
        root.addView(builtApks);

        root.addView(spacer(14));
        root.addView(sectionTitle("Placeholder"));

        titleInput = editText("Placeholder title", DEFAULT_TITLE);
        root.addView(titleInput);

        packageInput = editText("Target package ID", DEFAULT_PACKAGE);
        packageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        root.addView(packageInput);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorInput = editText("Background color", DEFAULT_COLOR);
        colorInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        LinearLayout.LayoutParams colorLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        colorRow.addView(colorInput, colorLp);
        colorPreview = new View(this);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        previewLp.setMargins(dp(10), 0, 0, 0);
        colorRow.addView(colorPreview, previewLp);
        root.addView(colorRow);
        updateColorPreview();
        colorInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) updateColorPreview();
        });

        root.addView(spacer(10));
        Button buildOnly = button("Build APK", v -> buildPlaceholder(false));
        root.addView(buildOnly);
        Button buildInstall = button("Build & install", v -> buildPlaceholder(true));
        root.addView(buildInstall);

        progressText = text("Operation progress: idle");
        progressText.setTextSize(14f);
        progressText.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.addView(progressText);
        Button stopOperation = button("Stop current Termux operation", v -> confirmStopOperation());
        root.addView(stopOperation);

        root.addView(sectionTitle("Termux console"));
        consoleText = new ScrollableConsoleTextView(this);
        consoleText.setText("No command output yet.");
        consoleText.setTextSize(12f);
        consoleText.setTypeface(Typeface.MONOSPACE);
        consoleText.setTextColor(Color.rgb(225, 235, 225));
        consoleText.setBackgroundColor(Color.rgb(28, 31, 28));
        consoleText.setPadding(dp(10), dp(10), dp(10), dp(10));
        consoleText.setTextIsSelectable(true);
        consoleText.setMovementMethod(new ScrollingMovementMethod());
        consoleText.setVerticalScrollBarEnabled(false);
        consoleText.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        consoleText.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
                view.performClick();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        consoleText.setGravity(Gravity.TOP | Gravity.START);
        root.addView(consoleText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));

        TextView consoleHint = text("Swipe inside the console to scroll output.");
        consoleHint.setTextSize(12f);
        root.addView(consoleHint);

        LinearLayout consoleActions = new LinearLayout(this);
        consoleActions.setOrientation(LinearLayout.HORIZONTAL);
        Button copyConsole = button("Copy console", v -> copyConsole());
        Button clearConsole = button("Clear console", v -> clearConsole());
        consoleActions.addView(copyConsole, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        consoleActions.addView(clearConsole, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(consoleActions);
        Button copyDailyLog = button("Copy today's saved log", v -> copyTodayLog());
        root.addView(copyDailyLog);

        root.addView(spacer(18));
        root.addView(sectionTitle("Replace / restore flow"));

        TextView replacementNote = text(
                "Android will not update an installed third-party app with a placeholder signed by a different key. " +
                "For the same package ID, uninstall the original first, then install the placeholder. " +
                "To restore the original later, uninstall the placeholder first."
        );
        replacementNote.setTextSize(14f);
        root.addView(replacementNote);

        Button info = button("Open target app info", v -> openTargetInfo());
        root.addView(info);
        Button uninstall = button("Uninstall target…", v -> confirmUninstall());
        root.addView(uninstall);

        root.addView(spacer(18));
        root.addView(sectionTitle("Termux development environment"));

        statusText = text("");
        statusText.setTextSize(14f);
        root.addView(statusText);

        environmentText = text("Dependency status: not checked");
        environmentText.setTextSize(13f);
        environmentText.setTypeface(Typeface.MONOSPACE);
        environmentText.setTextIsSelectable(true);
        environmentText.setMovementMethod(new ScrollingMovementMethod());
        environmentText.setGravity(Gravity.TOP | Gravity.START);
        environmentText.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.addView(environmentText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320)));

        Button copyInit = button("1. Copy first-run Termux command", v -> copyInitCommand());
        root.addView(copyInit);
        Button openTermux = button("2. Open Termux", v -> openTermux());
        root.addView(openTermux);
        Button permission = button("3. Grant AppId Termux permission", v -> requestOrOpenRunCommandPermission());
        if (isPackageInstalled(TERMUX_PACKAGE) && !hasRunCommandSupport()) {
            permission.setText("3. Check Termux compatibility");
        }
        root.addView(permission);
        Button check = button("4. Check all environment dependencies", v -> checkEnvironment());
        root.addView(check);
        Button setup = button("5. Install / repair full environment", v -> setupBuilder());
        root.addView(setup);
        Button manualSetup = button("Copy manual environment bootstrap", v -> copyManualEnvironmentSetup());
        root.addView(manualSetup);
        Button unknownSources = button("Allow AppId to install APKs", v -> openUnknownSourcesSettings());
        root.addView(unknownSources);

        TextView firstRun = text(
                "First run: paste the copied command into Termux, then fully close/reopen Termux. " +
                "Grant AppId the additional “Run commands in Termux environment” permission, then install the environment. " +
                "Commands run as background jobs, so Termux does not need Display over other apps permission."
        );
        firstRun.setTextSize(13f);
        root.addView(firstRun);

        SystemBarInsets.applyTo(scroll);
        return scroll;
    }

    private void setupBuilder() {
        if (isOperationRunning()) return;
        if (!preflightTermux()) return;
        try {
            String script = readAsset("setup_termux.sh");
            String builderB64 = encodeAsset("build_placeholder.sh");
            String checkerB64 = encodeAsset("check_environment.sh");
            String progressToken = startProgress("Environment setup", "setup", null);
            runTermuxCommand(
                    TERMUX_BASH,
                    new String[]{"-c", script, "focsd-appid-setup", progressToken,
                            builderB64, checkerB64},
                    null,
                    true,
                    progressToken
            );
            toast("Environment setup started in Termux");
        } catch (IOException e) {
            showError("Could not read embedded builder scripts: " + e.getMessage());
        }
    }

    private void checkEnvironment() {
        if (isOperationRunning()) return;
        if (!preflightTermux()) return;
        try {
            String script = readAsset("check_environment.sh");
            String progressToken = startProgress("Environment check", "check", null);
            runTermuxCommand(
                    TERMUX_BASH,
                    new String[]{"-c", script, "focsd-appid-check", progressToken},
                    null,
                    true,
                    progressToken
            );
        } catch (IOException e) {
            showError("Could not read the environment checker: " + e.getMessage());
        }
    }

    private void maybeRunInitialEnvironmentCheck() {
        SharedPreferences state = OperationStore.snapshot(this);
        if (!state.getString(OperationStore.KEY_REPORT, "").isEmpty()) return;
        if (OperationStore.isRunning(this)) return;
        if (!isPackageInstalled(TERMUX_PACKAGE) || !hasRunCommandSupport()) return;
        if (checkSelfPermission(TERMUX_PERMISSION_RUN_COMMAND) !=
                PackageManager.PERMISSION_GRANTED) return;
        progressText.post(this::checkEnvironment);
    }

    private void copyManualEnvironmentSetup() {
        try {
            String scriptB64 = encodeAsset("setup_termux.sh");
            String builderB64 = encodeAsset("build_placeholder.sh");
            String checkerB64 = encodeAsset("check_environment.sh");
            String command = "bash -c \"$(printf '%s' '" + scriptB64 +
                    "' | base64 -d)\" focsd-appid-setup '' '" + builderB64 + "' '" + checkerB64 + "'";
            copyText("AppId environment bootstrap", command);
            toast("Complete Termux environment bootstrap copied");
        } catch (IOException e) {
            showError("Could not prepare the environment bootstrap: " + e.getMessage());
        }
    }

    private String encodeAsset(String name) throws IOException {
        return Base64.encodeToString(
                readAsset(name).getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP
        );
    }

    private void buildPlaceholder(boolean install) {
        if (isOperationRunning()) return;
        String title = valueOrDefault(titleInput, DEFAULT_TITLE);
        String packageId = valueOrDefault(packageInput, DEFAULT_PACKAGE);
        String color = normalizeColor(valueOrDefault(colorInput, DEFAULT_COLOR));

        if (title.isEmpty()) {
            showError("Enter a placeholder title.");
            return;
        }
        if (!InputRules.isPackageId(packageId)) {
            showError("Package ID must look like com.example.app and contain only letters, digits and underscores.");
            return;
        }
        if (color == null) {
            showError("Background color must be a 6-digit hex value such as #F2F2F2.");
            return;
        }
        if (install && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow AppId to install APKs")
                    .setMessage("Android requires this one-time permission before AppId can open a locally built APK in the package installer.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open settings", (dialog, which) -> openUnknownSourcesSettings())
                    .show();
            return;
        }
        if (!preflightTermux()) return;
        String environmentReport = OperationStore.snapshot(this)
                .getString(OperationStore.KEY_REPORT, "");
        if (!environmentReport.contains("OK       environment schema: 4") ||
                !environmentReport.contains("OK       launcher icon renderer: version 2") ||
                !environmentReport.contains("READY    All required dependencies are available.")) {
            new AlertDialog.Builder(this)
                    .setTitle("Termux environment is not ready")
                    .setMessage("Complete Install / repair full environment and wait for the dependency audit to report READY before building an APK.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Install / repair", (dialog, which) -> setupBuilder())
                    .show();
            return;
        }
        final String builderScript;
        try {
            builderScript = readAsset("build_placeholder.sh");
        } catch (IOException e) {
            showError("Could not read the embedded builder: " + e.getMessage());
            return;
        }

        String titleB64 = Base64.encodeToString(
                title.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP
        );

        new AlertDialog.Builder(this)
                .setTitle(install ? "Build and install?" : "Build placeholder APK?")
                .setMessage(
                        "Package: " + packageId + "\n\n" +
                        "If the original app with this package ID is still installed, Android will reject the placeholder because the signing certificates differ."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> {
                    String progressToken = startProgress(
                            install ? "Build and install" : "Placeholder build",
                            install ? "build_install" : "build",
                            packageId
                    );
                    OperationStore.setBuildSpec(
                            MainActivity.this, progressToken, title, color);
                    runTermuxCommand(
                            TERMUX_BASH,
                            new String[]{
                                    "-c", builderScript, "focsd-appid-build",
                                    "--package", packageId,
                                    "--title-b64", titleB64,
                                    "--color", color.substring(1),
                                    "--install", install ? "1" : "0",
                                    "--progress-token", progressToken
                            },
                            null,
                            true,
                            progressToken
                    );
                    toast(install ? "Build started; installer will open when ready" : "Build started in Termux");
                })
                .show();
    }

    private String startProgress(String operation, String type, String installPackage) {
        activeProgressToken = UUID.randomUUID().toString();
        OperationStore.start(this, activeProgressToken, operation, type, installPackage);
        restoreOperationUi();
        return activeProgressToken;
    }

    private boolean isOperationRunning() {
        if (!OperationStore.isRunning(this)) return false;
        toast("A setup or build operation is already running");
        return true;
    }

    private void confirmStopOperation() {
        if (!OperationStore.isRunning(this)) {
            toast("No AppId Termux operation is running");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Stop current operation?")
                .setMessage("This stops only the Termux process tree started by AppId. Other Termux sessions are left running.")
                .setNegativeButton("Keep running", null)
                .setPositiveButton("Stop", (dialog, which) -> stopCurrentOperation())
                .show();
    }

    private void stopCurrentOperation() {
        if (!preflightTermux()) return;
        String token = OperationStore.getToken(this);
        OperationStore.markStopping(this, token);
        restoreOperationUi();
        String cancelScript =
                "set -u\n" +
                "self=$$\n" +
                "empty=''\n" +
                "setup_pattern=\"focsd-appid-${empty}setup\"\n" +
                "build_pattern=\"focsd-appid-${empty}build\"\n" +
                "pids=''\n" +
                "collect_tree() {\n" +
                "  local parent=\"$1\" child\n" +
                "  for child in $(pgrep -P \"$parent\" 2>/dev/null || true); do collect_tree \"$child\"; done\n" +
                "  case \" $pids \" in *\" $parent \"*) ;; *) pids=\"$pids $parent\" ;; esac\n" +
                "}\n" +
                "for pid in $(pgrep -f \"$setup_pattern\" 2>/dev/null; pgrep -f \"$build_pattern\" 2>/dev/null); do\n" +
                "  [ \"$pid\" = \"$self\" ] || collect_tree \"$pid\"\n" +
                "done\n" +
                "for pid in $pids; do kill -TERM \"$pid\" 2>/dev/null || true; done\n" +
                "sleep 2\n" +
                "for pid in $pids; do kill -KILL \"$pid\" 2>/dev/null || true; done\n" +
                "printf 'Stopped AppId process tree:%s\\n' \"${pids:- none}\"\n";
        runTermuxCancelCommand(cancelScript, token);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerProgressReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_BUILD_PROGRESS);
        filter.addAction(CommandResultService.ACTION_RESULT_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(progressReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(progressReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(progressReceiver);
        super.onDestroy();
    }

    private boolean preflightTermux() {
        if (!isPackageInstalled(TERMUX_PACKAGE)) {
            showError("Termux is not installed or not visible to AppId.");
            return false;
        }
        if (!hasRunCommandSupport()) {
            showUnsupportedTermux();
            return false;
        }
        if (checkSelfPermission(TERMUX_PERMISSION_RUN_COMMAND) !=
                PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Termux permission required")
                    .setMessage("Grant AppId the “Run commands in Termux environment” permission, then try again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open settings", (d, w) -> openOwnAppSettings())
                    .show();
            return false;
        }
        return true;
    }

    private void runTermuxCommand(
            String commandPath,
            String[] args,
            String stdin,
            boolean background,
            String operationToken
    ) {
        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE, TERMUX_SERVICE);
        intent.setAction(TERMUX_ACTION_RUN_COMMAND);
        intent.putExtra("com.termux.RUN_COMMAND_PATH", commandPath);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args);
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        if (stdin != null) {
            intent.putExtra("com.termux.RUN_COMMAND_STDIN", stdin);
        }

        Intent resultIntent = new Intent(this, CommandResultService.class);
        resultIntent.putExtra(CommandResultService.EXTRA_OPERATION_TOKEN, operationToken);
        int pendingFlags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent resultPendingIntent = PendingIntent.getService(
                this,
                operationToken.hashCode() & 0x7fffffff,
                resultIntent,
                pendingFlags
        );
        intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPendingIntent);
        intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "AppId");
        intent.putExtra(
                "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION",
                "Runs an AppId environment or APK build operation."
        );
        try {
            startService(intent);
        } catch (Exception e) {
            OperationStore.failToStart(this, operationToken,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            restoreOperationUi();
            showError(
                    "Could not start the Termux command. Check allow-external-apps=true and the Termux RUN_COMMAND permission.\n\n" +
                    e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }

    private void runTermuxCancelCommand(String script, String operationToken) {
        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE, TERMUX_SERVICE);
        intent.setAction(TERMUX_ACTION_RUN_COMMAND);
        intent.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                new String[]{"-c", script, "focsd-appid-cancel"});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");

        Intent resultIntent = new Intent(this, CommandResultService.class);
        resultIntent.putExtra(CommandResultService.EXTRA_OPERATION_TOKEN, operationToken);
        resultIntent.putExtra(CommandResultService.EXTRA_CANCELLATION, true);
        int pendingFlags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent resultPendingIntent = PendingIntent.getService(
                this,
                (operationToken.hashCode() ^ 0x43414e43) & 0x7fffffff,
                resultIntent,
                pendingFlags
        );
        intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPendingIntent);
        intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Stop AppId operation");
        try {
            startService(intent);
        } catch (Exception e) {
            OperationStore.failToStart(this, operationToken,
                    "Could not send stop command: " + e.getMessage());
            restoreOperationUi();
        }
    }

    private void restoreOperationUi() {
        if (progressText == null || consoleText == null || environmentText == null) return;
        SharedPreferences state = OperationStore.snapshot(this);
        String stage = state.getString(OperationStore.KEY_STAGE, "Operation progress: idle");
        String detail = state.getString(OperationStore.KEY_DETAIL, "");
        progressText.setText(stage + (detail.isEmpty() ? "" : "\n" + detail));

        String console = state.getString(OperationStore.KEY_CONSOLE, "");
        consoleText.setText(console.isEmpty() ? "No command output yet." : console);
        consoleText.post(() -> {
            int scroll = consoleText.getLayout() == null ? 0 :
                    consoleText.getLayout().getLineTop(consoleText.getLineCount()) - consoleText.getHeight();
            consoleText.scrollTo(0, Math.max(0, scroll));
        });

        String report = state.getString(OperationStore.KEY_REPORT, "");
        if (!report.isEmpty()) environmentText.setText(report);
        activeProgressToken = OperationStore.isRunning(this) ?
                state.getString(OperationStore.KEY_TOKEN, "") : null;
    }

    private void copyConsole() {
        String console = OperationStore.snapshot(this).getString(OperationStore.KEY_CONSOLE, "");
        if (console.isEmpty()) {
            toast("Console is empty");
            return;
        }
        copyText("AppId Termux console", console);
        toast("Console output copied");
    }

    private void clearConsole() {
        OperationStore.clearConsole(this);
        restoreOperationUi();
        toast("Console cleared; the daily log was preserved");
    }

    private void copyTodayLog() {
        String log = OperationStore.readTodayLog(this);
        if (log.isEmpty()) {
            toast("Today's saved log is empty");
            return;
        }
        copyText("AppId " + OperationStore.todayLogName(), log);
        toast(OperationStore.todayLogName() + " copied");
    }

    private void copyInitCommand() {
        copyText("AppId Termux setup", INIT_COMMAND);
        toast("Termux init command copied");
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private void openTermux() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
        if (launch == null) {
            showError("Termux is not installed.");
            return;
        }
        startActivity(launch);
    }

    private void requestOrOpenRunCommandPermission() {
        if (!isPackageInstalled(TERMUX_PACKAGE)) {
            showError("Install/open Termux first.");
            return;
        }
        if (!hasRunCommandSupport()) {
            showUnsupportedTermux();
            return;
        }
        if (checkSelfPermission(TERMUX_PERMISSION_RUN_COMMAND) !=
                PackageManager.PERMISSION_GRANTED) {
            try {
                requestPermissions(new String[]{TERMUX_PERMISSION_RUN_COMMAND}, 4100);
                return;
            } catch (Exception ignored) {
                // Some Android builds expose this custom permission only in App Info.
            }
        }
        openOwnAppSettings();
    }

    private void openOwnAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void openUnknownSourcesSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        } catch (Exception ignored) {
        }
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void openTargetInfo() {
        String packageId = validatedPackageOrNull();
        if (packageId == null) return;
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageId)));
        } catch (Exception e) {
            showError("Android could not open app info for " + packageId + ".");
        }
    }

    private void confirmUninstall() {
        String packageId = validatedPackageOrNull();
        if (packageId == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Uninstall target app?")
                .setMessage(
                        "Android will show its normal uninstall confirmation for:\n\n" + packageId +
                        "\n\nUninstalling can delete that app's local data. AppId does not bypass the system confirmation."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open uninstall", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageId));
                    startActivity(i);
                })
                .show();
    }

    private String validatedPackageOrNull() {
        String packageId = valueOrDefault(packageInput, DEFAULT_PACKAGE);
        if (!InputRules.isPackageId(packageId)) {
            showError("Enter a valid target package ID first.");
            return null;
        }
        return packageId;
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean hasRunCommandSupport() {
        try {
            getPackageManager().getPermissionInfo(TERMUX_PERMISSION_RUN_COMMAND, 0);
            getPackageManager().getServiceInfo(
                    new ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE),
                    0
            );
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void showUnsupportedTermux() {
        new AlertDialog.Builder(this)
                .setTitle("This Termux build is not compatible")
                .setMessage(
                        "The installed Termux build does not provide the RUN_COMMAND permission and service that AppId needs. " +
                        "The current Google Play build is not compatible with this integration.\n\n" +
                        "Install a maintained Termux release from F-Droid or the official Termux GitHub releases. " +
                        "Android may require uninstalling the Play build first because the releases use different signing keys. " +
                        "Back up anything important in Termux before switching."
                )
                .setNegativeButton("Close", null)
                .setPositiveButton("Open install guide", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TERMUX_INSTALL_GUIDE)));
                    } catch (Exception e) {
                        showError("Could not open the Termux installation guide.");
                    }
                })
                .show();
    }

    private void refreshStatus() {
        if (statusText == null) return;
        boolean termux = isPackageInstalled(TERMUX_PACKAGE);
        boolean supported = termux && hasRunCommandSupport();
        boolean permission = supported && checkSelfPermission(TERMUX_PERMISSION_RUN_COMMAND) ==
                PackageManager.PERMISSION_GRANTED;
        statusText.setText(
                "Termux: " + (termux ? "found" : "not found") + "\n" +
                "RUN_COMMAND support: " + (supported ? "available" : "unavailable") + "\n" +
                "RUN_COMMAND permission: " +
                (!supported ? "not applicable" : (permission ? "granted" : "not granted"))
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        BuiltApkStore.cleanupInstalledInstallers(this);
        refreshStatus();
        updateColorPreview();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatus();
        if (requestCode == 4100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toast("Termux command permission granted");
        } else if (requestCode == 4100) {
            toast("If no permission dialog appeared, use App Info → Permissions → Additional permissions");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "About");
        menu.add(0, 2, 1, "Copy Termux init command");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            showAbout();
            return true;
        }
        if (item.getItemId() == 2) {
            copyInitCommand();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("About AppId")
                .setMessage(
                        "AppId creates deliberately tiny placeholder APKs. It delegates compilation and signing to Termux and uses no network permission itself.\n\n" +
                        "Generated placeholders contain one Activity, no external libraries, no Internet permission, a configurable plain background, a title, and a small menu with About and Close.\n\n" +
                        "It cannot silently replace or uninstall another app. Android's package-signature and installer confirmations still apply.\n\n" +
                        "App ID: com.focsd.appid\nWebsite: https://focsd.com"
                )
                .setNeutralButton("Visit focsd.com", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://focsd.com")));
                    } catch (Exception error) {
                        toast("No browser is available to open focsd.com");
                    }
                })
                .setPositiveButton("OK", null)
                .show();
    }

    private String readAsset(String name) throws IOException {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String normalizeColor(String raw) {
        return InputRules.normalizeColor(raw);
    }

    private void updateColorPreview() {
        if (colorPreview == null || colorInput == null) return;
        String normalized = normalizeColor(colorInput.getText().toString().trim());
        if (normalized == null) normalized = "#F2F2F2";
        try {
            colorPreview.setBackgroundColor(Color.parseColor(normalized));
        } catch (IllegalArgumentException ignored) {
            colorPreview.setBackgroundColor(Color.LTGRAY);
        }
    }

    private EditText editText(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(value);
        e.setContentDescription(hint);
        e.setSingleLine(true);
        e.setTextSize(16f);
        e.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(4), 0, dp(6));
        e.setLayoutParams(lp);
        return e;
    }

    private String valueOrDefault(EditText input, String defaultValue) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.rgb(45, 45, 45));
        t.setLineSpacing(0f, 1.08f);
        return t;
    }

    private TextView sectionTitle(String value) {
        TextView t = text(value);
        t.setTextSize(20f);
        t.setPadding(0, dp(4), 0, dp(6));
        return t;
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    private int dp(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("AppId")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}
