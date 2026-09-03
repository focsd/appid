package com.focsd.appid;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/** Applies AppId's semantic palette to both the collapsed and popup spinner rows. */
final class ThemedArrayAdapter<T> extends ArrayAdapter<T> {
    ThemedArrayAdapter(Context context, List<T> items) {
        super(context, android.R.layout.simple_spinner_item, items);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        color(view, false);
        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        color(view, true);
        return view;
    }

    private void color(View view, boolean popup) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(ThemePalette.primaryText(getContext()));
        }
        if (popup) view.setBackgroundColor(ThemePalette.surface(getContext()));
    }
}
