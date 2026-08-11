package com.focsd.appid;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class AppViewerActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppInventoryAdapter adapter;
    private TextView countText;
    private TextView usageStatus;
    private ProgressBar progressBar;
    private int loadGeneration;
    private Future<?> loadTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Installed apps");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUsageStatus();
        loadApps();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(8));
        root.addView(header);

        TextView title = text("App IDs, screen time and storage", 22f);
        header.addView(title);
        countText = text("Loading installed apps…", 13f);
        countText.setTextColor(Color.DKGRAY);
        header.addView(countText);

        SearchView search = new SearchView(this);
        search.setIconifiedByDefault(false);
        search.setQueryHint("Search name, App ID, or UID");
        header.addView(search, matchWrap());

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.VERTICAL);
        header.addView(filters, matchWrap());

        CheckBox includeSystem = new CheckBox(this);
        includeSystem.setText("Include system apps");
        filters.addView(includeSystem, matchWrap());

        Spinner sortSpinner = new Spinner(this);
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"Sort: app name", "Sort: App ID",
                        "Sort: screen time (highest first)",
                        "Sort: occupied space (largest first)"});
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        filters.addView(sortSpinner, matchWrap());

        usageStatus = text("", 13f);
        usageStatus.setPadding(0, dp(5), 0, dp(3));
        header.addView(usageStatus);
        Button usageAccess = button("Grant usage and storage access");
        usageAccess.setOnClickListener(v -> openUsageAccessSettings());
        header.addView(usageAccess, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = button("Refresh");
        Button copy = button("Copy visible IDs");
        actions.addView(refresh, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(copy, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(actions, matchWrap());

        TextView hint = text("Tap copies App ID • Long-press opens App info", 12f);
        hint.setTextColor(Color.DKGRAY);
        header.addView(hint);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));

        ListView list = new ListView(this);
        list.setDividerHeight(1);
        adapter = new AppInventoryAdapter(this);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.setQuery(newText);
                updateCount();
                return true;
            }
        });
        includeSystem.setOnCheckedChangeListener((buttonView, checked) -> {
            adapter.setIncludeSystem(checked);
            updateCount();
        });
        sortSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                int[] sortModes = {
                        AppInventoryAdapter.SORT_NAME,
                        AppInventoryAdapter.SORT_PACKAGE,
                        AppInventoryAdapter.SORT_SCREEN_TIME,
                        AppInventoryAdapter.SORT_STORAGE
                };
                adapter.setSortMode(sortModes[Math.min(position, sortModes.length - 1)]);
                if (position >= 2 && !hasUsageAccess()) {
                    toast("Grant Usage access to populate this sort");
                }
                updateCount();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        refresh.setOnClickListener(v -> loadApps());
        copy.setOnClickListener(v -> copyVisible());
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppInventoryEntry app = adapter.getItem(position);
            copyText("App ID", app.packageName);
            toast("Copied: " + app.packageName);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            openAppInfo(adapter.getItem(position).packageName);
            return true;
        });
        SystemBarInsets.applyTo(root);
        return root;
    }

    @SuppressWarnings("deprecation")
    private void loadApps() {
        final int generation = ++loadGeneration;
        final boolean usageAllowed = hasUsageAccess();
        if (loadTask != null) loadTask.cancel(true);
        progressBar.setVisibility(View.VISIBLE);
        countText.setText(usageAllowed
                ? "Reading installed apps, usage and storage…"
                : "Loading installed apps…");
        loadTask = executor.submit(() -> {
            PackageManager packageManager = getPackageManager();
            List<ApplicationInfo> installed;
            if (Build.VERSION.SDK_INT >= 33) {
                installed = packageManager.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
            } else {
                installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            }

            Map<String, UsageStats> usage = Collections.emptyMap();
            StorageStatsManager storageManager = null;
            if (usageAllowed) {
                UsageStatsManager manager = (UsageStatsManager)
                        getSystemService(Context.USAGE_STATS_SERVICE);
                Calendar start = Calendar.getInstance();
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                try {
                    Map<String, UsageStats> result = manager.queryAndAggregateUsageStats(
                            start.getTimeInMillis(), System.currentTimeMillis());
                    if (result != null) usage = result;
                } catch (RuntimeException ignored) {
                }
                storageManager = (StorageStatsManager)
                        getSystemService(Context.STORAGE_STATS_SERVICE);
            }

            List<AppInventoryEntry> apps = new ArrayList<>();
            int processed = 0;
            for (ApplicationInfo info : installed) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    CharSequence labelValue = packageManager.getApplicationLabel(info);
                    String label = labelValue == null ? info.packageName : labelValue.toString();
                    PackageInfo packageInfo = getPackageInfoCompat(packageManager, info.packageName);
                    String versionName = packageInfo.versionName == null ? "?" : packageInfo.versionName;
                    long versionCode = Build.VERSION.SDK_INT >= 28
                            ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
                    boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
                            (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                    boolean placeholder = PlaceholderPackages.isPlaceholder(info);
                    UsageStats appUsage = usage.get(info.packageName);
                    long foreground = appUsage == null ? 0L : appUsage.getTotalTimeInForeground();
                    long occupiedBytes = 0L;
                    boolean storageAvailable = false;
                    if (storageManager != null) {
                        try {
                            StorageStats stats = storageManager.queryStatsForPackage(
                                    info.storageUuid == null
                                            ? StorageManager.UUID_DEFAULT : info.storageUuid,
                                    info.packageName,
                                    UserHandle.getUserHandleForUid(info.uid));
                            occupiedBytes = AppValueFormatter.safeAdd(
                                    stats.getAppBytes(), stats.getDataBytes());
                            storageAvailable = true;
                        } catch (Exception ignored) {
                            // A package or storage volume may disappear during the scan.
                        }
                    }
                    apps.add(new AppInventoryEntry(
                            label, info.packageName, info.uid, versionName, versionCode, system,
                            placeholder,
                            packageManager.getApplicationIcon(info), foreground, usageAllowed,
                            occupiedBytes, storageAvailable));
                } catch (Exception ignored) {
                    // Packages can be replaced while the inventory is being read.
                }
                processed++;
                if (usageAllowed && processed % 10 == 0) {
                    int completed = processed;
                    runOnUiThread(() -> {
                        if (generation == loadGeneration && !isFinishing() && !isDestroyed()) {
                            countText.setText("Reading usage and storage: " + completed + "/" +
                                    installed.size());
                        }
                    });
                }
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                adapter.setApps(apps);
                progressBar.setVisibility(View.GONE);
                updateCount();
            });
        });
    }

    @SuppressWarnings("deprecation")
    private PackageInfo getPackageInfoCompat(PackageManager manager, String packageName)
            throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT >= 33) {
            return manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
        }
        return manager.getPackageInfo(packageName, 0);
    }

    @SuppressWarnings("deprecation")
    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        } else {
            mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void updateUsageStatus() {
        usageStatus.setText(hasUsageAccess()
                ? "Today’s screen time and occupied storage are available."
                : "Screen time and storage require Android Usage access.");
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception error) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
                toast("Android could not open Usage access settings");
            }
        }
    }

    private void updateCount() {
        int count = adapter.getCount();
        countText.setText(count + (count == 1 ? " app shown" : " apps shown"));
    }

    private void copyVisible() {
        List<AppInventoryEntry> apps = adapter.getVisibleApps();
        if (apps.isEmpty()) {
            toast("Nothing to copy");
            return;
        }
        StringBuilder output = new StringBuilder();
        for (AppInventoryEntry app : apps) {
            output.append(app.label).append('\t').append(app.packageName);
            if (app.usageAvailable) {
                output.append('\t').append("today=")
                        .append(AppInventoryAdapter.formatDuration(app.todayForegroundMillis));
            }
            if (app.storageAvailable) {
                output.append('\t').append("space=")
                        .append(AppInventoryAdapter.formatBytes(app.occupiedBytes));
            }
            output.append('\n');
        }
        copyText("Visible app IDs", output.toString().trim());
        toast("Copied " + apps.size() + " app IDs");
    }

    private void openAppInfo(String packageName) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)));
        } catch (Exception error) {
            toast("Could not open app info");
        }
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 35, 40));
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        loadGeneration++;
        if (loadTask != null) loadTask.cancel(true);
        executor.shutdownNow();
        super.onDestroy();
    }
}
