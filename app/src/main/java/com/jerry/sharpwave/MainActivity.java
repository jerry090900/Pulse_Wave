package com.jerry.sharpwave;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class MainActivity extends Activity {
    private TextView overlayStatus;
    private TextView usageAccessStatus;
    private TextView audioStatus;
    private TextView selectedCount;
    private TextView lockScreenStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestRuntimePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        root.setBackgroundColor(Color.rgb(16,16,16));
        scroll.addView(root);

        TextView title = text("SharpWave", 30, Color.WHITE);
        title.setTypeface(null, 1);
        root.addView(title);
        TextView desc = text("Full-width sharp audio spikes at the top of your screen.", 15, Color.LTGRAY);
        desc.setPadding(0, dp(3), 0, dp(10));
        root.addView(desc);

        TextView limitation = text("Detection limitation: SharpWave no longer uses notification access. It uses Android Usage Access to check which selected app was recently in the foreground and shows the overlay only while audio is active. It cannot identify exactly which app is producing sound.", 13, Color.rgb(220,180,100));
        limitation.setPadding(0, 0, 0, dp(8));
        root.addView(limitation);

        TextView accessTitle = text("Access", 20, Color.WHITE);
        accessTitle.setTypeface(null, 1);
        accessTitle.setPadding(0, dp(8), 0, dp(6));
        root.addView(accessTitle);

        overlayStatus = text("", 15, Color.LTGRAY);
        root.addView(overlayStatus);

        Button overlayPermission = button("Open display-over-other-apps settings");
        overlayPermission.setOnClickListener(v -> openOverlaySettings());
        root.addView(overlayPermission);

        usageAccessStatus = text("", 15, Color.LTGRAY);
        usageAccessStatus.setPadding(0, dp(10), 0, 0);
        root.addView(usageAccessStatus);

        Button usageAccess = button("Open usage access settings");
        usageAccess.setOnClickListener(v -> openUsageAccessSettings());
        root.addView(usageAccess);

        audioStatus = text("", 15, Color.LTGRAY);
        audioStatus.setPadding(0, dp(10), 0, 0);
        root.addView(audioStatus);

        Button audioPermission = button("Grant audio permission");
        audioPermission.setOnClickListener(v -> requestRuntimePermissions());
        root.addView(audioPermission);

        selectedCount = text("Selected apps: 0", 16, Color.WHITE);
        selectedCount.setPadding(0, dp(16), 0, dp(6));
        root.addView(selectedCount);

        Button selectedApps = button("Choose selected apps");
        selectedApps.setOnClickListener(v -> startActivity(new Intent(this, AppPickerActivity.class)));
        root.addView(selectedApps);

        section(root, "Spike shape");
        addSlider(root, "Spike width", Prefs.KEY_SPIKE_WIDTH, 2, 12, Prefs.spikeWidth(this));
        addSlider(root, "Gap", Prefs.KEY_GAP, 0, 8, Prefs.gap(this));
        addSlider(root, "Maximum height", Prefs.KEY_HEIGHT, 40, 220, Prefs.height(this));
        addSlider(root, "Sensitivity", Prefs.KEY_SENSITIVITY, 60, 260, Prefs.sensitivity(this));

        section(root, "Colors");
        RadioGroup palette = new RadioGroup(this);
        String[] names = Prefs.PALETTE_NAMES;
        int currentPalette = Prefs.palette(this);
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]); rb.setTextColor(Color.WHITE); rb.setTextSize(15); rb.setId(1000 + i);
            palette.addView(rb);
            if (i == currentPalette) rb.setChecked(true);
        }
        palette.setOnCheckedChangeListener((group, checkedId) -> {
            int value = Math.max(0, checkedId - 1000);
            Prefs.get(this).edit().putInt(Prefs.KEY_PALETTE, value).apply();
            refreshService();
        });
        root.addView(palette);

        section(root, "Experimental lock screen");
        CheckBox lockScreen = new CheckBox(this);
        lockScreen.setText("Try drawing on the screen-on lock screen");
        lockScreen.setTextColor(Color.WHITE);
        lockScreen.setTextSize(15);
        lockScreen.setChecked(Prefs.lockScreenEnabled(this));
        lockScreen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Prefs.get(this).edit().putBoolean(Prefs.KEY_LOCK_SCREEN, isChecked).apply();
            updateStatus();
            refreshService();
        });
        root.addView(lockScreen);

        lockScreenStatus = text("", 13, Color.GRAY);
        lockScreenStatus.setPadding(0, dp(2), 0, dp(4));
        root.addView(lockScreenStatus);

        TextView lockNote = text("Scope: the lock screen while the display is on. Android does not allow a normal app to draw on the real always-on display while the panel is off, so SharpWave does not fake that by waking or holding the screen on. When enabled, the service tries a transparent show-when-locked host plus a best-effort lock-screen overlay flag on the screen-on keyguard, and falls back to the normal overlay if the OEM or display state blocks it.", 13, Color.rgb(220,180,100));
        lockNote.setPadding(0, 0, 0, dp(4));
        root.addView(lockNote);

        section(root, "Service");
        Button start = button("Start SharpWave");
        start.setOnClickListener(v -> {
            requestRuntimePermissions();
            Intent i = new Intent(this, OverlayService.class);
            startForegroundService(i);
            updateStatus();
        });
        root.addView(start);

        Button testOverlay = button("Test overlay (5 seconds)");
        testOverlay.setOnClickListener(v -> testOverlay());
        root.addView(testOverlay);

        Button stop = button("Stop SharpWave");
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_STOP);
            startService(i);
        });
        root.addView(stop);

        TextView note = text("Selected-app detection uses foreground app usage events plus audio/visualizer activity; it does not read notifications. The visualizer itself uses Android's low-quality output-mix Visualizer API; it does not save audio.", 13, Color.GRAY);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);
        return scroll;
    }

    private void addSlider(LinearLayout root, String title, String key, int min, int max, int current) {
        TextView label = text(title + ": " + current, 15, Color.WHITE);
        root.addView(label);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(current - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                label.setText(title + ": " + value);
                if (fromUser) Prefs.get(MainActivity.this).edit().putInt(key, value).apply();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { refreshService(); }
        });
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }

    private void refreshService() {
        try {
            Intent i = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_REFRESH);
            startService(i);
        } catch (Exception ignored) {}
    }

    private void requestRuntimePermissions() {
        java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) requestPermissions(permissions.toArray(new String[0]), 50);
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception e2) {
                Toast.makeText(this, "Open Settings > Apps > SharpWave > Display over other apps", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Open Settings > Apps > Special app access > Usage access", Toast.LENGTH_LONG).show();
        }
    }

    private void testOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Display over other apps is off. Enable it first.", Toast.LENGTH_LONG).show();
            openOverlaySettings();
            return;
        }
        Intent i = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_PREVIEW);
        if (!OverlayService.isRunning()) {
            i.putExtra(OverlayService.EXTRA_PREVIEW_ONLY, true);
        }
        try {
            startForegroundService(i);
            Toast.makeText(this, "Test overlay shown for 5 seconds", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not start the overlay service", Toast.LENGTH_LONG).show();
        }
    }

    private void updateStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean usage = UsageAccess.isGranted(this);
        boolean audio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        overlayStatus.setText("Display over other apps: " + (overlay ? "ENABLED" : "DISABLED"));
        overlayStatus.setTextColor(overlay ? Color.rgb(130,220,150) : Color.rgb(220,180,100));

        usageAccessStatus.setText("Usage access: " + (usage ? "ENABLED" : "DISABLED"));
        usageAccessStatus.setTextColor(usage ? Color.rgb(130,220,150) : Color.rgb(220,180,100));

        audioStatus.setText("Audio permission: " + (audio ? "GRANTED" : "NOT GRANTED"));
        audioStatus.setTextColor(audio ? Color.rgb(130,220,150) : Color.rgb(220,180,100));

        Set<String> selected = Prefs.selectedApps(this);
        selectedCount.setText("Selected apps: " + selected.size());

        boolean lockScreen = Prefs.lockScreenEnabled(this);
        lockScreenStatus.setText("Lock-screen experiment: " + (lockScreen ? "ENABLED" : "DISABLED"));
        lockScreenStatus.setTextColor(lockScreen ? Color.rgb(130,220,150) : Color.GRAY);
    }

    private void section(LinearLayout root, String name) {
        TextView v = text(name, 20, Color.WHITE); v.setTypeface(null, 1); v.setPadding(0, dp(22), 0, dp(8)); root.addView(v);
    }

    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setGravity(Gravity.CENTER_VERTICAL); b.setMinHeight(dp(52)); return b;
    }

    private TextView text(String text, int sp, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(color); return v;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
