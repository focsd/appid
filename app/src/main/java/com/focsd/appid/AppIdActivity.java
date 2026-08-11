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
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

abstract class AppIdActivity extends Activity {

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

    private static final String DEFAULT_COLOR = "#F2F2F2";
    private static final int MENU_PRIMARY_VIEW = 1;
    private static final int MENU_APK_LIBRARY = 2;
    private static final int MENU_INSTALLED_APPS = 3;
    private static final int MENU_ABOUT = 4;
    static final String EXTRA_TARGET_PACKAGE = "com.focsd.appid.extra.TARGET_PACKAGE";
    static final String EXTRA_TARGET_TITLE = "com.focsd.appid.extra.TARGET_TITLE";
    private static final String STATE_SETUP_VIEW = "setup_view";
    private static final String STATE_SELECTED_PACKAGE = "selected_package";
    private static final String STATE_REASON = "reason";
    private static final String STATE_ACTION = "action";

    private Spinner appInput;
    private EditText reasonInput;
    private EditText actionInput;
    private TextView statusText;
    private TextView progressText;
    private TextView environmentText;
    private ScrollableConsoleTextView consoleText;
    private TextView creatorProgressText;
    private TextView creatorSetupStatus;
    private View creatorView;
    private View setupView;
    private boolean showingSetup;
    private String activeProgressToken;

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CommandResultService.ACTION_RESULT_UPDATED.equals(intent.getAction())) {
                restoreOperationUi();
                invalidateOptionsMenu();
                return;
            }

            String token = intent.getStringExtra("token");
            if (!OperationStore.tokenMatches(AppIdActivity.this, token)) return;

            String stage = intent.getStringExtra("stage");
            String detail = intent.getStringExtra("detail");
            String report = intent.getStringExtra("report");
            String log = intent.getStringExtra("log");
            if (log != null && !log.trim().isEmpty()) {
                OperationStore.appendLiveLog(AppIdActivity.this, token, log);
            }
            if (stage != null && !stage.trim().isEmpty()) {
                OperationStore.progress(AppIdActivity.this, token, stage, detail, report);
            }
            restoreOperationUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerProgressReceiver();
        if (!UiStateRules.shouldInitializeCreator(isSetupDestination())) {
            showingSetup = true;
            setupView = buildSetupView();
            showSetupView();
            return;
        }
        creatorView = buildCreatorView();
        if (savedInstanceState != null) {
            loadReplaceableApps(savedInstanceState.getString(STATE_SELECTED_PACKAGE));
            reasonInput.setText(savedInstanceState.getString(STATE_REASON, ""));
            actionInput.setText(savedInstanceState.getString(STATE_ACTION, ""));
        }
        showCreatorView();
        maybeRunInitialEnvironmentCheck();
    }

    protected boolean isSetupDestination() {
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (appInput == null || reasonInput == null || actionInput == null) return;
        ReplaceableApp selected = selectedApp();
        outState.putBoolean(STATE_SETUP_VIEW, showingSetup);
        outState.putString(STATE_SELECTED_PACKAGE,
                selected == null ? null : selected.packageName);
        outState.putString(STATE_REASON, reasonInput.getText().toString());
        outState.putString(STATE_ACTION, actionInput.getText().toString());
    }

    private View buildCreatorView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(20));
        root.setBackgroundColor(Color.rgb(250, 250, 252));

        TextView heading = text("Replace an app with a better choice");
        heading.setTextSize(25f);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(Color.rgb(28, 31, 38));
        root.addView(heading);

        TextView intro = text("Choose the app, name your reason, and decide what to do instead.");
        intro.setTextSize(14f);
        intro.setTextColor(Color.rgb(92, 96, 105));
        intro.setPadding(0, dp(4), 0, dp(16));
        root.addView(intro);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(14), dp(16), dp(16));
        form.setBackground(roundedBackground(Color.WHITE, 16));
        form.setElevation(dp(2));
        root.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView appPrompt = fieldLabel("Replace");
        appPrompt.setTextSize(15f);
        form.addView(appPrompt);
        appInput = new Spinner(this);
        appInput.setContentDescription("App to replace");
        appInput.setMinimumHeight(dp(52));
        form.addView(appInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        loadReplaceableApps(null);

        TextView reasonPrompt = fieldLabel("Why are you removing it?");
        reasonPrompt.setPadding(0, dp(12), 0, 0);
        form.addView(reasonPrompt);
        reasonInput = editText("Reason", "I want to finish writing my book.");
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reasonInput.setSingleLine(false);
        reasonInput.setMinLines(2);
        reasonInput.setMaxLines(3);
        form.addView(reasonInput);

        TextView actionPrompt = fieldLabel("Instead, when I get the urge:");
        actionPrompt.setPadding(0, dp(8), 0, 0);
        form.addView(actionPrompt);
        actionInput = editText("Replacement action", "Read 2 pages");
        actionInput.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(actionInput);

        Button create = button("CREATE", v -> buildPlaceholder());
        create.setAllCaps(true);
        create.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        create.setTextColor(Color.WHITE);
        create.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(45, 79, 150)));
        create.setMinimumHeight(dp(52));
        form.addView(create);

        creatorSetupStatus = text("Checking builder status…");
        creatorSetupStatus.setTextSize(13f);
        creatorSetupStatus.setPadding(dp(12), dp(10), dp(12), dp(8));
        creatorSetupStatus.setBackground(roundedBackground(Color.rgb(238, 241, 247), 12));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(14), 0, 0);
        root.addView(creatorSetupStatus, statusParams);

        creatorProgressText = text("No active operation");
        creatorProgressText.setTextSize(13f);
        creatorProgressText.setTextColor(Color.rgb(92, 96, 105));
        creatorProgressText.setPadding(dp(4), dp(10), dp(4), 0);
        root.addView(creatorProgressText);

        TextView navigationHint = text("Setup, logs, APK library, and installed-app tools are in the top-right menu.");
        navigationHint.setTextSize(12f);
        navigationHint.setTextColor(Color.rgb(110, 113, 120));
        navigationHint.setPadding(dp(4), dp(6), dp(4), 0);
        root.addView(navigationHint);

        SystemBarInsets.applyTo(root);
        return root;
    }

    private View buildSetupView() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView heading = text("Setup & tools");
        heading.setTextSize(25f);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(heading);
        TextView subtitle = text("Configure Termux, inspect dependencies, and troubleshoot builds.");
        subtitle.setTextSize(14f);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        String targetTitle = getIntent().getStringExtra(EXTRA_TARGET_TITLE);
        String targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        if (targetPackage != null && !targetPackage.isEmpty()) {
            TextView target = text("Selected app: " +
                    (targetTitle == null || targetTitle.isEmpty() ? targetPackage : targetTitle) +
                    "\n" + targetPackage);
            target.setTextSize(13f);
            target.setTextColor(Color.rgb(92, 96, 105));
            target.setPadding(dp(10), dp(8), dp(10), dp(8));
            target.setBackground(roundedBackground(Color.rgb(244, 245, 248), 10));
            root.addView(target);
        }

        root.addView(sectionTitle("Termux connection"));
        statusText = text("");
        statusText.setTextSize(14f);
        statusText.setPadding(dp(10), dp(8), dp(10), dp(8));
        statusText.setBackground(roundedBackground(Color.rgb(244, 245, 248), 10));
        root.addView(statusText);

        Button copyInit = button("1. Copy first-run Termux command", v -> copyInitCommand());
        root.addView(copyInit);
        Button openTermux = button("2. Open Termux", v -> openTermux());
        root.addView(openTermux);
        Button permission = button("3. Grant AppId Termux permission", v -> requestOrOpenRunCommandPermission());
        if (isPackageInstalled(TERMUX_PACKAGE) && !hasRunCommandSupport()) {
            permission.setText("3. Check Termux compatibility");
        }
        root.addView(permission);
        Button setup = button("4. Install / repair environment", v -> setupBuilder());
        root.addView(setup);
        Button check = button("Check dependencies now", v -> checkEnvironment());
        root.addView(check);

        TextView firstRun = text(
                "First run: paste the copied command into Termux, fully close and reopen Termux, " +
                "grant the additional Run commands permission, then install the environment. " +
                "Display over other apps is not required."
        );
        firstRun.setTextSize(13f);
        firstRun.setTextColor(Color.DKGRAY);
        firstRun.setPadding(dp(4), dp(8), dp(4), dp(8));
        root.addView(firstRun);

        root.addView(sectionTitle("Dependency status"));
        environmentText = text("Dependency status: not checked");
        environmentText.setTextSize(12f);
        environmentText.setTypeface(Typeface.MONOSPACE);
        environmentText.setTextIsSelectable(true);
        environmentText.setMovementMethod(new ScrollingMovementMethod());
        environmentText.setGravity(Gravity.TOP | Gravity.START);
        environmentText.setPadding(dp(10), dp(8), dp(10), dp(8));
        environmentText.setBackground(roundedBackground(Color.rgb(246, 247, 249), 10));
        root.addView(environmentText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(260)));

        root.addView(sectionTitle("Current operation"));

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
        root.addView(sectionTitle("Selected app actions"));

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
        root.addView(sectionTitle("Advanced & Android settings"));
        Button manualSetup = button("Copy manual environment bootstrap", v -> copyManualEnvironmentSetup());
        root.addView(manualSetup);
        Button unknownSources = button("Allow AppId to install APKs", v -> openUnknownSourcesSettings());
        root.addView(unknownSources);

        SystemBarInsets.applyTo(scroll);
        return scroll;
    }

    private void showCreatorView() {
        showingSetup = false;
        setTitle("AppId");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(false);
        setContentView(creatorView);
        invalidateOptionsMenu();
        refreshStatus();
        restoreOperationUi();
    }

    private void showSetupView() {
        showingSetup = true;
        setTitle("Setup & tools");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(setupView);
        invalidateOptionsMenu();
        refreshStatus();
        restoreOperationUi();
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
        creatorView.post(this::checkEnvironment);
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

    private void buildPlaceholder() {
        if (isOperationRunning()) return;
        ReplaceableApp selectedApp = selectedAppOrNull();
        if (selectedApp == null) return;
        String title = selectedApp.label;
        String packageId = selectedApp.packageName;
        String reason = reasonInput.getText().toString().trim();
        String replacementAction = actionInput.getText().toString().trim();
        String color = DEFAULT_COLOR;

        if (reason.isEmpty()) {
            showError("Enter why you are removing " + title + ".");
            return;
        }
        if (replacementAction.isEmpty()) {
            showError("Enter what you want to do instead.");
            return;
        }
        if (reason.length() > 280 || replacementAction.length() > 120) {
            showError("Keep the reason under 280 characters and the replacement action under 120 characters.");
            return;
        }
        if (!preflightTermux()) return;
        String environmentReport = OperationStore.snapshot(this)
                .getString(OperationStore.KEY_REPORT, "");
        if (!environmentReport.contains("OK       environment schema: 5") ||
                !environmentReport.contains("OK       replacement template: version 2") ||
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
        String reasonB64 = Base64.encodeToString(
                reason.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP
        );
        String actionB64 = Base64.encodeToString(
                replacementAction.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP
        );

        new AlertDialog.Builder(this)
                .setTitle("Create replacement for " + title + "?")
                .setMessage(
                        "You removed it because:\n\"" + reason + "\"\n\n" +
                        "Instead: " + replacementAction + "\n\n" +
                        "Package: " + packageId + "\n\n" +
                        "If the original app with this package ID is still installed, Android will reject the placeholder because the signing certificates differ."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> {
                    String progressToken = startProgress(
                            "Replacement build",
                            "build",
                            packageId
                    );
                    OperationStore.setBuildSpec(
                            AppIdActivity.this, progressToken, title, color);
                    runTermuxCommand(
                            TERMUX_BASH,
                            new String[]{
                                    "-c", builderScript, "focsd-appid-build",
                                    "--package", packageId,
                                    "--title-b64", titleB64,
                                    "--reason-b64", reasonB64,
                                    "--action-b64", actionB64,
                                    "--color", color.substring(1),
                                    "--install", "0",
                                    "--progress-token", progressToken
                            },
                            null,
                            true,
                            progressToken
                    );
                    toast("Replacement build started in Termux");
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
        SharedPreferences state = OperationStore.snapshot(this);
        String stage = state.getString(OperationStore.KEY_STAGE, "Operation progress: idle");
        String detail = state.getString(OperationStore.KEY_DETAIL, "");
        String progress = stage + (detail.isEmpty() ? "" : "\n" + detail);
        if (progressText != null) progressText.setText(progress);
        if (creatorProgressText != null) {
            String operationState = state.getString(OperationStore.KEY_STATE, "");
            String operationType = state.getString(OperationStore.KEY_TYPE, "");
            boolean showProgress = UiStateRules.shouldShowCreatorProgress(
                    operationState, operationType);
            creatorProgressText.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            if (showProgress) {
                creatorProgressText.setText("Operation progress: idle".equals(stage)
                        ? "No active operation"
                        : progress);
            }
        }

        String console = state.getString(OperationStore.KEY_CONSOLE, "");
        if (consoleText != null) {
            consoleText.setText(console.isEmpty() ? "No command output yet." : console);
            consoleText.post(() -> {
                int scroll = consoleText.getLayout() == null ? 0 :
                        consoleText.getLayout().getLineTop(consoleText.getLineCount()) - consoleText.getHeight();
                consoleText.scrollTo(0, Math.max(0, scroll));
            });
        }

        String report = state.getString(OperationStore.KEY_REPORT, "");
        if (!report.isEmpty() && environmentText != null) environmentText.setText(report);
        activeProgressToken = OperationStore.isRunning(this) ?
                state.getString(OperationStore.KEY_TOKEN, "") : null;
        if (!report.isEmpty()) refreshStatus();
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
        if (showingSetup) {
            String targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
            if (InputRules.isPackageId(targetPackage)) return targetPackage;
            showError("Return to Create, choose an installed app, then open Setup & tools again.");
            return null;
        }
        ReplaceableApp selected = selectedAppOrNull();
        return selected == null ? null : selected.packageName;
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
        boolean termux = isPackageInstalled(TERMUX_PACKAGE);
        boolean supported = termux && hasRunCommandSupport();
        boolean permission = supported && checkSelfPermission(TERMUX_PERMISSION_RUN_COMMAND) ==
                PackageManager.PERMISSION_GRANTED;
        if (statusText != null) {
            statusText.setText(
                    "Termux: " + (termux ? "found" : "not found") + "\n" +
                    "RUN_COMMAND support: " + (supported ? "available" : "unavailable") + "\n" +
                    "RUN_COMMAND permission: " +
                    (!supported ? "not applicable" : (permission ? "granted" : "not granted"))
            );
        }
        if (creatorSetupStatus != null) {
            String report = OperationStore.snapshot(this)
                    .getString(OperationStore.KEY_REPORT, "");
            boolean ready = permission &&
                    report.contains("OK       environment schema: 5") &&
                    report.contains("OK       replacement template: version 2") &&
                    report.contains("READY    All required dependencies are available.");
            if (ready) {
                creatorSetupStatus.setText("✓ Builder ready");
                creatorSetupStatus.setTextColor(Color.rgb(28, 105, 62));
                creatorSetupStatus.setBackground(
                        roundedBackground(Color.rgb(232, 246, 237), 12));
            } else {
                creatorSetupStatus.setText("Setup required • Open Setup & tools from the menu");
                creatorSetupStatus.setTextColor(Color.rgb(135, 79, 24));
                creatorSetupStatus.setBackground(
                        roundedBackground(Color.rgb(252, 242, 226), 12));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        OperationStore.settleAwaitingInstaller(this);
        BuiltApkStore.cleanupInstalledInstallers(this);
        refreshStatus();
        if (appInput != null) {
            ReplaceableApp selected = selectedApp();
            loadReplaceableApps(selected == null ? null : selected.packageName);
        }
        restoreOperationUi();
        invalidateOptionsMenu();
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
        if (!showingSetup) {
            menu.add(0, MENU_PRIMARY_VIEW, 0, "Setup & tools")
                    .setIcon(R.drawable.ic_tools)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        boolean newApk = OperationStore.hasNewApk(this);
        menu.add(0, MENU_APK_LIBRARY, 1, newApk ? "APK library • New" : "APK library")
                .setIcon(newApk ? R.drawable.ic_apk_library_badged : R.drawable.ic_apk_library)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, MENU_INSTALLED_APPS, 2, "Installed apps")
                .setIcon(R.drawable.ic_apps);
        menu.add(0, MENU_ABOUT, 3, "About")
                .setIcon(R.drawable.ic_info);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home && showingSetup) {
            finish();
            return true;
        }
        if (item.getItemId() == MENU_PRIMARY_VIEW) {
            Intent setup = new Intent(this, SetupActivity.class);
            ReplaceableApp selected = selectedApp();
            if (selected != null) {
                setup.putExtra(EXTRA_TARGET_PACKAGE, selected.packageName);
                setup.putExtra(EXTRA_TARGET_TITLE, selected.label);
            }
            startActivity(setup);
            return true;
        }
        if (item.getItemId() == MENU_APK_LIBRARY) {
            startActivity(new Intent(this, BuiltApksActivity.class));
            return true;
        }
        if (item.getItemId() == MENU_INSTALLED_APPS) {
            startActivity(new Intent(this, AppViewerActivity.class));
            return true;
        }
        if (item.getItemId() == MENU_ABOUT) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("About AppId")
                .setMessage(
                        "AppId creates deliberately tiny placeholder APKs. It delegates compilation and signing to Termux and uses no network permission itself.\n\n" +
                        "Generated replacements contain one Activity, no external libraries or Internet permission, and display your reason plus the action you chose to do instead.\n\n" +
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

    @SuppressWarnings("deprecation")
    private void loadReplaceableApps(String preferredPackage) {
        if (appInput == null) return;
        PackageManager manager = getPackageManager();
        List<ApplicationInfo> installed;
        if (Build.VERSION.SDK_INT >= 33) {
            installed = manager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
        } else {
            installed = manager.getInstalledApplications(PackageManager.GET_META_DATA);
        }
        List<ReplaceableApp> choices = new ArrayList<>();
        for (ApplicationInfo info : installed) {
            boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (system || getPackageName().equals(info.packageName) ||
                    manager.getLaunchIntentForPackage(info.packageName) == null) continue;
            boolean placeholder = PlaceholderPackages.isPlaceholder(info);
            CharSequence rawLabel = manager.getApplicationLabel(info);
            String label = rawLabel == null || rawLabel.length() == 0
                    ? info.packageName : rawLabel.toString();
            if (placeholder) label = PlaceholderPackages.title(info, label);
            choices.add(new ReplaceableApp(label, info.packageName, placeholder));
        }
        choices.sort(Comparator
                .comparing((ReplaceableApp app) -> app.label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(app -> app.packageName, String.CASE_INSENSITIVE_ORDER));
        if (choices.isEmpty()) {
            choices.add(new ReplaceableApp("No replaceable apps found", null, false));
        }

        ArrayAdapter<ReplaceableApp> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appInput.setAdapter(adapter);
        if (preferredPackage != null) {
            for (int index = 0; index < choices.size(); index++) {
                if (preferredPackage.equals(choices.get(index).packageName)) {
                    appInput.setSelection(index);
                    break;
                }
            }
        }
    }

    private ReplaceableApp selectedApp() {
        if (appInput == null || !(appInput.getSelectedItem() instanceof ReplaceableApp)) return null;
        ReplaceableApp selected = (ReplaceableApp) appInput.getSelectedItem();
        return selected.packageName == null ? null : selected;
    }

    private ReplaceableApp selectedAppOrNull() {
        ReplaceableApp selected = selectedApp();
        if (selected == null) showError("Choose an installed app to replace.");
        return selected;
    }

    private static final class ReplaceableApp {
        final String label;
        final String packageName;
        final boolean placeholder;

        ReplaceableApp(String label, String packageName, boolean placeholder) {
            this.label = label;
            this.packageName = packageName;
            this.placeholder = placeholder;
        }

        @Override
        public String toString() {
            if (packageName == null) return label;
            return (placeholder ? "★ " : "") + label + " — " + packageName;
        }
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

    private TextView fieldLabel(String value) {
        TextView label = text(value);
        label.setTextSize(14f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(Color.rgb(48, 51, 58));
        return label;
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
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
