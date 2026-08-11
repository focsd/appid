package com.focsd.appid;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class AppInventoryAdapter extends BaseAdapter {
    static final int SORT_NAME = 0;
    static final int SORT_PACKAGE = 1;
    static final int SORT_SCREEN_TIME = 2;
    static final int SORT_STORAGE = 3;

    private final Context context;
    private final List<AppInventoryEntry> allApps = new ArrayList<>();
    private final List<AppInventoryEntry> visibleApps = new ArrayList<>();
    private boolean includeSystem;
    private String query = "";
    private int sortMode = SORT_NAME;

    AppInventoryAdapter(Context context) {
        this.context = context;
    }

    void setApps(List<AppInventoryEntry> apps) {
        allApps.clear();
        allApps.addAll(apps);
        applyFilterAndSort();
    }

    void setIncludeSystem(boolean includeSystem) {
        this.includeSystem = includeSystem;
        applyFilterAndSort();
    }

    void setQuery(String query) {
        this.query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        applyFilterAndSort();
    }

    void setSortMode(int sortMode) {
        this.sortMode = sortMode;
        applyFilterAndSort();
    }

    List<AppInventoryEntry> getVisibleApps() {
        return new ArrayList<>(visibleApps);
    }

    private void applyFilterAndSort() {
        visibleApps.clear();
        for (AppInventoryEntry app : allApps) {
            if (!includeSystem && app.systemApp) continue;
            if (!query.isEmpty()) {
                String searchable = (app.label + " " + app.packageName + " " + app.uid)
                        .toLowerCase(Locale.ROOT);
                if (!searchable.contains(query)) continue;
            }
            visibleApps.add(app);
        }
        Comparator<AppInventoryEntry> comparator;
        if (sortMode == SORT_PACKAGE) {
            comparator = (first, second) -> compareThen(
                    first.packageName, second.packageName, first.label, second.label);
        } else if (sortMode == SORT_SCREEN_TIME) {
            comparator = (first, second) -> {
                int availability = Boolean.compare(second.usageAvailable, first.usageAvailable);
                if (availability != 0) return availability;
                int usage = Long.compare(second.todayForegroundMillis,
                        first.todayForegroundMillis);
                return usage != 0 ? usage : compareThen(
                        first.label, second.label, first.packageName, second.packageName);
            };
        } else if (sortMode == SORT_STORAGE) {
            comparator = (first, second) -> {
                int availability = Boolean.compare(second.storageAvailable,
                        first.storageAvailable);
                if (availability != 0) return availability;
                int storage = Long.compare(second.occupiedBytes, first.occupiedBytes);
                return storage != 0 ? storage : compareThen(
                        first.label, second.label, first.packageName, second.packageName);
            };
        } else {
            comparator = (first, second) -> compareThen(
                    first.label, second.label, first.packageName, second.packageName);
        }
        Collections.sort(visibleApps, comparator);
        notifyDataSetChanged();
    }

    private int compareThen(String first, String second, String firstTie, String secondTie) {
        int comparison = String.CASE_INSENSITIVE_ORDER.compare(first, second);
        return comparison != 0
                ? comparison
                : String.CASE_INSENSITIVE_ORDER.compare(firstTie, secondTie);
    }

    @Override
    public int getCount() {
        return visibleApps.size();
    }

    @Override
    public AppInventoryEntry getItem(int position) {
        return visibleApps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setMinimumHeight(dp(78));

            holder.icon = new ImageView(context);
            row.addView(holder.icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textParams.setMargins(dp(14), 0, 0, 0);
            row.addView(textColumn, textParams);

            holder.name = new TextView(context);
            holder.name.setTextSize(16f);
            holder.name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            holder.name.setTextColor(Color.rgb(35, 35, 40));
            holder.name.setSingleLine(true);
            textColumn.addView(holder.name);

            holder.packageId = new TextView(context);
            holder.packageId.setTextSize(13f);
            holder.packageId.setTextColor(Color.rgb(35, 95, 150));
            holder.packageId.setSingleLine(true);
            textColumn.addView(holder.packageId);

            holder.meta = new TextView(context);
            holder.meta.setTextSize(12f);
            holder.meta.setTextColor(Color.rgb(95, 95, 105));
            holder.meta.setMaxLines(2);
            textColumn.addView(holder.meta);
            row.setTag(holder);
            convertView = row;
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AppInventoryEntry app = getItem(position);
        holder.icon.setImageDrawable(app.icon);
        holder.icon.setContentDescription(app.label + " icon");
        holder.name.setText(app.label);
        holder.packageId.setText(app.packageName);
        String usage = app.usageAvailable
                ? "Today: " + formatDuration(app.todayForegroundMillis)
                : "Today: usage access required";
        String storage = app.storageAvailable
                ? "Space: " + formatBytes(app.occupiedBytes)
                : "Space: usage access required";
        holder.meta.setText(usage + " • " + storage + "\nUID " + app.uid + " • v" +
                app.versionName + " (" + app.versionCode + ")" +
                (app.placeholderApp ? " • placeholder" : " • regular app") +
                (app.systemApp ? " • system" : ""));
        return convertView;
    }

    static String formatDuration(long millis) {
        return AppValueFormatter.duration(millis);
    }

    static String formatBytes(long bytes) {
        return AppValueFormatter.bytes(bytes, Locale.getDefault());
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class ViewHolder {
        ImageView icon;
        TextView name;
        TextView packageId;
        TextView meta;
    }
}
