package com.focsd.appid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class BuiltApksActivity extends Activity {
    private final List<BuiltApkStore.Record> records = new ArrayList<>();
    private BuiltApkAdapter adapter;
    private TextView countText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Built APKs");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        OperationStore.markApkLibrarySeen(this);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView title = text("Built APK library", 22f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView description = text(
                "Installed placeholders stay listed even after their installer APK is removed. Tap an entry to install, uninstall, copy its App ID, or manage its saved installer.",
                13f);
        description.setTextColor(Color.DKGRAY);
        root.addView(description);

        countText = text("No saved APKs", 13f);
        countText.setPadding(0, dp(6), 0, dp(4));
        root.addView(countText);

        Button deleteAll = new Button(this);
        deleteAll.setAllCaps(false);
        deleteAll.setText("Delete all saved installer APKs…");
        deleteAll.setOnClickListener(v -> confirmDeleteAll());
        root.addView(deleteAll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(this);
        adapter = new BuiltApkAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                showActions(records.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView empty = text(
                "No saved or installed placeholders. Build a placeholder, then return here.", 15f);
        empty.setGravity(android.view.Gravity.CENTER);
        root.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        list.setEmptyView(empty);

        SystemBarInsets.applyTo(root);
        return root;
    }

    private void reload() {
        records.clear();
        records.addAll(BuiltApkStore.list(this));
        adapter.notifyDataSetChanged();
        int count = records.size();
        int saved = 0;
        int installed = 0;
        for (BuiltApkStore.Record record : records) {
            if (record.hasApk()) saved++;
            if (record.installedPlaceholder) installed++;
        }
        countText.setText(count == 0 ? "No placeholders" :
                count + (count == 1 ? " placeholder" : " placeholders") + " • " +
                        installed + " installed • " + saved +
                        (saved == 1 ? " saved installer" : " saved installers"));
    }

    private void showActions(BuiltApkStore.Record record) {
        List<String> actions = new ArrayList<>();
        if (record.hasApk()) actions.add(record.realAppInstalled()
                ? "Install placeholder (real app must be uninstalled first)" : "Install APK");
        if (record.installed) actions.add(record.installedPlaceholder
                ? "Uninstall placeholder…" : "Uninstall real app…");
        actions.add("Copy App ID");
        if (record.hasApk()) actions.add("Delete saved installer APK");
        new AlertDialog.Builder(this)
                .setTitle(record.title)
                .setItems(actions.toArray(new String[0]),
                        (dialog, which) -> {
                            String action = actions.get(which);
                            if (action.startsWith("Install")) install(record);
                            else if (action.startsWith("Uninstall")) confirmUninstall(record);
                            else if (action.startsWith("Copy")) copyPackage(record.packageName);
                            else confirmDelete(record);
                        })
                .setNegativeButton("Close", null)
                .show();
    }

    private void install(BuiltApkStore.Record record) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow AppId to install APKs")
                    .setMessage("Grant the one-time Install unknown apps permission, then tap this APK again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open settings", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception error) {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        }
                    })
                    .show();
            return;
        }
        String error = ApkInstaller.open(this, record.file);
        if (error != null) toast(error);
    }

    private void confirmDelete(BuiltApkStore.Record record) {
        new AlertDialog.Builder(this)
                .setTitle("Delete saved installer APK?")
                .setMessage(record.title + "\n" + record.packageName +
                        "\n\nThe installed placeholder, if present, stays installed and listed. A separate Termux Downloads copy is not removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (BuiltApkStore.deleteApk(this, record)) {
                        reload();
                        toast("Deleted saved installer for " + record.packageName);
                    } else {
                        toast("Could not delete the APK");
                    }
                })
                .show();
    }

    private void confirmDeleteAll() {
        int saved = 0;
        for (BuiltApkStore.Record record : records) if (record.hasApk()) saved++;
        if (saved == 0) {
            toast("There are no saved installer APKs");
            return;
        }
        int savedCount = saved;
        new AlertDialog.Builder(this)
                .setTitle("Delete all saved installer APKs?")
                .setMessage("This removes " + savedCount +
                        " private installer copies. Installed placeholders stay installed and listed. Termux Downloads copies are not removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete all", (dialog, which) -> {
                    int deleted = 0;
                    for (BuiltApkStore.Record record : new ArrayList<>(records)) {
                        if (record.hasApk() && BuiltApkStore.deleteApk(this, record)) deleted++;
                    }
                    reload();
                    toast("Deleted " + deleted + " APKs");
                })
                .show();
    }

    private void confirmUninstall(BuiltApkStore.Record record) {
        String kind = record.installedPlaceholder ? "placeholder" : "real app";
        String warning = record.installedPlaceholder
                ? "Android will ask you to confirm removing this placeholder."
                : "This package is a real app, not an AppId placeholder. Continue only if you intend to remove the real app and its local data.";
        new AlertDialog.Builder(this)
                .setTitle("Uninstall " + kind + "?")
                .setMessage(record.title + "\n" + record.packageName + "\n\n" + warning)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open uninstaller", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_DELETE,
                                Uri.parse("package:" + record.packageName)));
                    } catch (Exception error) {
                        toast("Android could not open the uninstaller");
                    }
                })
                .show();
    }

    private void copyPackage(String packageName) {
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("App ID", packageName));
        toast("Copied: " + packageName);
    }

    @SuppressWarnings("deprecation")
    private Drawable archiveIcon(BuiltApkStore.Record record) {
        PackageManager manager = getPackageManager();
        if (!record.hasApk()) {
            ApplicationInfo installed = PlaceholderPackages.installedInfo(this,
                    record.packageName);
            return installed == null
                    ? getApplicationInfo().loadIcon(manager) : installed.loadIcon(manager);
        }
        PackageInfo info;
        if (Build.VERSION.SDK_INT >= 33) {
            info = manager.getPackageArchiveInfo(record.file.getAbsolutePath(),
                    PackageManager.PackageInfoFlags.of(0));
        } else {
            info = manager.getPackageArchiveInfo(record.file.getAbsolutePath(), 0);
        }
        if (info == null || info.applicationInfo == null) {
            return getApplicationInfo().loadIcon(manager);
        }
        ApplicationInfo application = info.applicationInfo;
        application.sourceDir = record.file.getAbsolutePath();
        application.publicSourceDir = record.file.getAbsolutePath();
        try {
            return application.loadIcon(manager);
        } catch (Exception error) {
            return getApplicationInfo().loadIcon(manager);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
    }

    private String status(BuiltApkStore.Record record) {
        String installed = record.installedPlaceholder ? "Installed placeholder"
                : record.realAppInstalled() ? "Real app installed" : "Not installed";
        if (!record.hasApk()) return installed + " • installer deleted";
        String date = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(record.builtAt));
        return installed + " • installer " + formatSize(record.file.length()) + " • " + date;
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 35, 40));
        return view;
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

    private final class BuiltApkAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return records.size();
        }

        @Override
        public BuiltApkStore.Record getItem(int position) {
            return records.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row;
            if (convertView == null) {
                LinearLayout layout = new LinearLayout(BuiltApksActivity.this);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setPadding(dp(4), dp(10), dp(4), dp(10));
                row = new Row();
                row.icon = new ImageView(BuiltApksActivity.this);
                layout.addView(row.icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
                LinearLayout column = new LinearLayout(BuiltApksActivity.this);
                column.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                columnParams.setMargins(dp(12), 0, 0, 0);
                layout.addView(column, columnParams);
                row.title = text("", 16f);
                row.title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                row.packageName = text("", 13f);
                row.packageName.setTextColor(Color.rgb(35, 95, 150));
                row.detail = text("", 12f);
                row.detail.setTextColor(Color.DKGRAY);
                column.addView(row.title);
                column.addView(row.packageName);
                column.addView(row.detail);
                layout.setTag(row);
                convertView = layout;
            } else {
                row = (Row) convertView.getTag();
            }
            BuiltApkStore.Record record = getItem(position);
            row.icon.setImageDrawable(archiveIcon(record));
            row.icon.setContentDescription(record.title + " icon");
            row.title.setText(record.title);
            row.packageName.setText(record.packageName);
            row.detail.setText(status(record) + " • " + record.color);
            return convertView;
        }
    }

    private static final class Row {
        ImageView icon;
        TextView title;
        TextView packageName;
        TextView detail;
    }
}
