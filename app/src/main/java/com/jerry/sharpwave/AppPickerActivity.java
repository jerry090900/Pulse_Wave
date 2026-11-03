package com.jerry.sharpwave;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppPickerActivity extends Activity {
    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> visibleApps = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selected.addAll(Prefs.selectedApps(this));
        loadApps();
        View ui = buildUi();
        setContentView(ui);
        configureWindow();
        ui.requestApplyInsets();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(16));
        root.setBackgroundColor(color(R.color.bg));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(dp(20), dp(24) + insets.getSystemWindowInsetTop(),
                    dp(20), dp(16) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        TextView title = label("Selected apps", 28, color(R.color.text));
        root.addView(title);

        TextView subtitle = label("PulseWave appears while one of these apps was recently in the foreground and audio is active. Grant Usage Access for this detection to work; it cannot identify exactly which app is producing sound.", 14, color(R.color.text_secondary));
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setTextColor(color(R.color.text));
        search.setHintTextColor(color(R.color.text_secondary));
        search.setSingleLine(true);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(roundDrawable(color(R.color.surface_elevated), color(R.color.divider)));
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button selectAll = secondaryButton("Select all");
        Button clear = secondaryButton("Clear all");
        actions.addView(selectAll, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.setPadding(0, dp(10), 0, dp(10));
        root.addView(actions);

        ListView list = new ListView(this);
        list.setBackgroundColor(color(R.color.bg));
        list.setDividerHeight(1);
        list.setDivider(new android.graphics.drawable.ColorDrawable(color(R.color.divider)));
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button save = primaryButton("Save selected apps");
        root.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
        selectAll.setOnClickListener(v -> { for (AppItem item : visibleApps) selected.add(item.packageName); adapter.notifyDataSetChanged(); });
        clear.setOnClickListener(v -> { for (AppItem item : visibleApps) selected.remove(item.packageName); adapter.notifyDataSetChanged(); });
        save.setOnClickListener(v -> {
            Prefs.saveSelectedApps(this, selected);
            setResult(RESULT_OK);
            finish();
        });
        return root;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(launcher, 0);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolved) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            CharSequence label = info.loadLabel(pm);
            allApps.add(new AppItem(label == null ? pkg : label.toString(), pkg));
        }
        allApps.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        visibleApps.addAll(allApps);
    }

    private void filter(String q) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        visibleApps.clear();
        for (AppItem app : allApps) {
            if (needle.isEmpty() || app.label.toLowerCase(Locale.ROOT).contains(needle) || app.packageName.toLowerCase(Locale.ROOT).contains(needle)) {
                visibleApps.add(app);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private TextView label(String text, int sp, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        v.setIncludeFontPadding(false); v.setLineSpacing(0f, 1.12f); return v;
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(color(R.color.text));
        button.setAllCaps(false);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setStateListAnimator(null);
        button.setBackground(roundDrawable(color(R.color.surface_elevated), color(R.color.divider)));
        return button;
    }

    private Button primaryButton(String text) {
        Button button = secondaryButton(text);
        button.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        button.setBackground(roundDrawable(color(R.color.accent), color(R.color.accent_pressed)));
        return button;
    }

    private GradientDrawable roundDrawable(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int color(int resource) {
        return getResources().getColor(resource);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private class AppAdapter extends BaseAdapter {
        public int getCount() { return visibleApps.size(); }
        public Object getItem(int position) { return visibleApps.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View convertView, ViewGroup parent) {
            CheckBox box = convertView instanceof CheckBox ? (CheckBox) convertView : new CheckBox(AppPickerActivity.this);
            AppItem item = visibleApps.get(position);
            box.setText(item.label + "\n" + item.packageName);
            box.setTextColor(color(R.color.text));
            box.setTextSize(15);
            box.setPadding(dp(8), dp(6), dp(8), dp(6));
            box.setMinHeight(dp(58));
            box.setOnCheckedChangeListener(null);
            box.setChecked(selected.contains(item.packageName));
            box.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selected.add(item.packageName); else selected.remove(item.packageName);
            });
            return box;
        }
    }

    private static final class AppItem {
        final String label;
        final String packageName;
        AppItem(String label, String packageName) { this.label = label; this.packageName = packageName; }
    }
}
