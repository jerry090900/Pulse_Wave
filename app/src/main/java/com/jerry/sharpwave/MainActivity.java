package com.jerry.sharpwave;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class MainActivity extends Activity {
    private TextView overlayStatus;
    private TextView usageAccessStatus;
    private TextView audioStatus;
    private TextView mediaSessionStatus;
    private TextView selectedCount;
    private TextView lockScreenStatus;
    private boolean returningFromStudio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View ui = buildUi();
        setContentView(ui);
        configureWindow();
        ui.requestApplyInsets();
        requestRuntimePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        OverlayService.setAppUiVisible(true);
        if (returningFromStudio) {
            returningFromStudio = false;
            View refreshed = buildUi();
            setContentView(refreshed);
            refreshed.requestApplyInsets();
        }
        updateStatus();
    }

    @Override
    protected void onPause() {
        OverlayService.setAppUiVisible(false);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
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

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackgroundColor(color(R.color.bg));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setBackgroundColor(color(R.color.bg));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(dp(20), dp(24) + insets.getSystemWindowInsetTop(),
                    dp(20), dp(28) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        TextView eyebrow = eyebrow("AUDIO VISUALIZER");
        addBlock(root, eyebrow, 0, 8);

        TextView title = text("PulseWave", 34, color(R.color.text));
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        addBlock(root, title, 0, 4);

        TextView desc = text("Audio-reactive visuals and your personal phone design studio.",
                16, color(R.color.text_secondary));
        addBlock(root, desc, 0, 18);

        LinearLayout hero = panel();
        TextView heroLabel = eyebrow("LIVE VISUALIZER");
        hero.addView(heroLabel, matchParams(0, 0));
        ImageView waveform = new ImageView(this);
        waveform.setImageResource(R.drawable.pulsewave_waveform);
        waveform.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        waveform.setContentDescription("PulseWave waveform");
        hero.addView(waveform, matchParams(14, 0, 70));
        TextView heroTitle = text("Sharp motion. Quiet interface.", 18, color(R.color.text));
        heroTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        hero.addView(heroTitle, matchParams(12, 0));
        TextView heroNote = text("Full-width spikes stay out of the way until the sound moves.",
                13, color(R.color.text_secondary));
        hero.addView(heroNote, matchParams(5, 0));
        addBlock(root, hero, 0, 18);

        section(root, "PULSEWAVE STUDIO",
                "Create audio styles, photo clock designs and decorative fingerprint rings.");
        LinearLayout studioPanel = panel();
        StudioPreviewView studioPreview = new StudioPreviewView(this);
        StudioPreset activeStudio = StudioStore.activeAudio(this);
        studioPreview.setScene(activeStudio != null ? activeStudio
                : StudioStore.load(this).get(0));
        studioPanel.addView(studioPreview, matchParams(0, 0, 152));
        TextView studioNote = text("Gallery · Edit · Live demo · Save · Apply", 13,
                color(R.color.text_secondary));
        studioPanel.addView(studioNote, matchParams(9, 7));
        Button openStudio = primaryButton("Open PulseWave Studio");
        openStudio.setOnClickListener(v -> {
            returningFromStudio = true;
            startActivity(new Intent(this, StudioActivity.class));
        });
        studioPanel.addView(openStudio, matchParams(4, 0, 52));
        addBlock(root, studioPanel, 0, 24);

        section(root, "ACCESS", "Give PulseWave the access it needs to listen and draw.");
        LinearLayout access = panel();
        TextView accessNote = text("These permissions stay on-device and are used only for the overlay and selected-app detection.",
                13, color(R.color.text_secondary));
        access.addView(accessNote, matchParams(0, 0));
        addDivider(access);

        overlayStatus = statusText();
        addAccessItem(access, "Display over other apps", overlayStatus,
                "Open display settings", v -> openOverlaySettings());
        addDivider(access);
        usageAccessStatus = statusText();
        addAccessItem(access, "Usage access", usageAccessStatus,
                "Open usage settings", v -> openUsageAccessSettings());
        addDivider(access);
        audioStatus = statusText();
        addAccessItem(access, "Audio permission", audioStatus,
                "Grant audio permission", v -> requestRuntimePermissions());
        addDivider(access);

        mediaSessionStatus = statusText();
        addAccessItem(access, "Music notification access (optional)", mediaSessionStatus,
                "Open Android notification access", v -> openMusicAccessSettings());
        LinearLayout musicRow = horizontalRow();
        TextView musicCopy = text("Use playing media sessions from selected apps",
                14, color(R.color.text));
        musicRow.addView(musicCopy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch musicDetection = new Switch(this);
        musicDetection.setShowText(false);
        musicDetection.setThumbTintList(switchThumbTint());
        musicDetection.setTrackTintList(switchTrackTint());
        musicDetection.setChecked(Prefs.mediaSessionDetectionEnabled(this));
        musicRow.addView(musicDetection, wrapParams());
        access.addView(musicRow, matchParams(8, 5));
        musicDetection.setOnCheckedChangeListener((buttonView, enabled) -> {
            Prefs.get(this).edit().putBoolean(Prefs.KEY_MEDIA_SESSION_DETECTION, enabled).apply();
            refreshService();
            updateStatus();
        });
        TextView mediaDisclosure = text(
                "Android's notification-listener permission can expose all notifications. "
                + "PulseWave uses it only to check whether selected music apps have playing "
                + "media sessions. It does not read or save notification text, messages, or artwork. "
                + "You can turn this off here or revoke access in Android Settings.",
                12, color(R.color.text_secondary));
        access.addView(mediaDisclosure, matchParams(5, 0));
        addBlock(root, access, 0, 24);

        section(root, "SELECTED APPS", "Choose which foreground apps can activate the visualizer.");
        LinearLayout selectedPanel = panel();
        LinearLayout selectedHeading = horizontalRow();
        TextView selectedLabel = text("Detection sources", 16, color(R.color.text));
        selectedHeading.addView(selectedLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        selectedCount = text("0 apps selected", 13, color(R.color.text_secondary));
        selectedCount.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        selectedHeading.addView(selectedCount, wrapParams());
        selectedPanel.addView(selectedHeading, matchParams(0, 0));
        TextView selectedNote = text("PulseWave checks recent foreground activity and active audio. It cannot identify the exact app producing sound.",
                13, color(R.color.text_secondary));
        selectedPanel.addView(selectedNote, matchParams(7, 0));
        Button selectedApps = secondaryButton("Choose selected apps");
        selectedApps.setOnClickListener(v -> startActivity(new Intent(this, AppPickerActivity.class)));
        selectedPanel.addView(selectedApps, matchParams(14, 0, 50));
        addBlock(root, selectedPanel, 0, 24);

        section(root, "VISUAL SIGNATURE", "Tune the spike shape and color without leaving the main screen.");
        LinearLayout controls = panel();
        addSlider(controls, "Spike width", Prefs.KEY_SPIKE_WIDTH, 2, 12, Prefs.spikeWidth(this));
        addDivider(controls);
        addSlider(controls, "Gap", Prefs.KEY_GAP, 0, 8, Prefs.gap(this));
        addDivider(controls);
        addSlider(controls, "Maximum height", Prefs.KEY_HEIGHT, 40, 220, Prefs.height(this));
        addDivider(controls);
        addSlider(controls, "Sensitivity", Prefs.KEY_SENSITIVITY, 60, 260, Prefs.sensitivity(this));
        addDivider(controls);

        TextView paletteLabel = text("Palette", 15, color(R.color.text));
        paletteLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        controls.addView(paletteLabel, matchParams(12, 0));
        RadioGroup palette = new RadioGroup(this);
        String[] names = Prefs.PALETTE_NAMES;
        int currentPalette = Prefs.palette(this);
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setTextColor(color(R.color.text_secondary));
            rb.setTextSize(14);
            rb.setButtonTintList(radioTint());
            rb.setMinHeight(dp(42));
            rb.setId(1000 + i);
            palette.addView(rb, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            if (i == currentPalette) rb.setChecked(true);
        }
        palette.setOnCheckedChangeListener((group, checkedId) -> {
            int value = Math.max(0, checkedId - 1000);
            Prefs.get(this).edit().putInt(Prefs.KEY_PALETTE, value).apply();
            StudioStore.clearAppliedAudio(this);
            refreshService();
        });
        controls.addView(palette, matchParams(4, 0));
        addBlock(root, controls, 0, 24);

        section(root, "POSITION & AUDIO RESPONSE",
                "Center the visualizer and make louder bass hits produce taller bars.");
        LinearLayout motionPanel = panel();
        motionPanel.addView(text("Screen position", 15, color(R.color.text)),
                matchParams(0, 6));
        RadioGroup positions = new RadioGroup(this);
        String[] positionLabels = {"Top", "Middle (recommended)", "Bottom"};
        int selectedPosition = Prefs.overlayPosition(this);
        for (int index = 0; index < positionLabels.length; index++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(positionLabels[index]);
            rb.setTextColor(color(R.color.text_secondary));
            rb.setButtonTintList(radioTint());
            rb.setId(6000 + index);
            positions.addView(rb);
            if (index == selectedPosition) rb.setChecked(true);
        }
        positions.setOnCheckedChangeListener((group, id) -> {
            Prefs.get(this).edit().putInt(Prefs.KEY_OVERLAY_POSITION,
                    Math.max(Prefs.POSITION_TOP, id - 6000)).apply();
            refreshService();
        });
        motionPanel.addView(positions, matchParams(0, 13));
        addMotionToggle(motionPanel, "Center the bass",
                "Low frequencies peak in the center; higher notes spread to both edges.",
                Prefs.KEY_CENTER_BASS, Prefs.centeredBass(this));
        addDivider(motionPanel);
        addMotionToggle(motionPanel, "Solid-color bars (legacy mode)",
                "For the old visualizer only. Studio designs use their own two-color setting.",
                Prefs.KEY_SINGLE_COLOR, Prefs.singleColorBars(this));
        addDivider(motionPanel);
        addMotionToggle(motionPanel, "Follow playback loudness",
                "Uses Android's as-played output signal. Switch off if Bluetooth output is too weak.",
                Prefs.KEY_AS_PLAYED, Prefs.asPlayed(this));
        addDivider(motionPanel);
        addSlider(motionPanel, "Bass response (%)", Prefs.KEY_BASS_GAIN, 100, 240,
                Prefs.bassGain(this));
        motionPanel.addView(text("Signal comes from Android's output-mix Visualizer, not the music app's private audio session. Some devices cannot provide an accurate mix.",
                12, color(R.color.text_secondary)), matchParams(8, 0));
        addBlock(root, motionPanel, 0, 24);

        section(root, "EXPERIMENTAL LOCK SCREEN", "Screen-on keyguard support varies by Android device.");
        LinearLayout lockPanel = panel();
        LinearLayout lockHeading = horizontalRow();
        LinearLayout lockCopy = new LinearLayout(this);
        lockCopy.setOrientation(LinearLayout.VERTICAL);
        TextView lockTitle = text("Show on the screen-on lock screen", 16, color(R.color.text));
        lockCopy.addView(lockTitle, matchParams(0, 0));
        TextView lockDescription = text("Best effort; it never wakes or holds the display on.",
                13, color(R.color.text_secondary));
        lockCopy.addView(lockDescription, matchParams(4, 0));
        lockHeading.addView(lockCopy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch lockScreen = new Switch(this);
        lockScreen.setShowText(false);
        lockScreen.setThumbTintList(switchThumbTint());
        lockScreen.setTrackTintList(switchTrackTint());
        lockScreen.setChecked(Prefs.lockScreenEnabled(this));
        lockHeading.addView(lockScreen, wrapParams());
        lockPanel.addView(lockHeading, matchParams(0, 0));
        lockScreen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Prefs.get(this).edit().putBoolean(Prefs.KEY_LOCK_SCREEN, isChecked).apply();
            updateStatus();
            refreshService();
        });
        lockScreenStatus = statusText();
        lockPanel.addView(lockScreenStatus, matchParams(14, 0));
        TextView lockNote = text("Screen-on only. Android may hide overlays or block background launches on a secure lock screen. PulseWave cannot replace the actual lock screen, fingerprint animation, or display-off AOD.",
                13, color(R.color.text_secondary));
        lockPanel.addView(lockNote, matchParams(8, 0));
        addBlock(root, lockPanel, 0, 24);

        section(root, "SERVICE", "Start the listener when you want PulseWave ready in the background.");
        LinearLayout servicePanel = panel();
        Button start = primaryButton("Start PulseWave");
        start.setOnClickListener(v -> {
            requestRuntimePermissions();
            Intent i = new Intent(this, OverlayService.class);
            startForegroundService(i);
            updateStatus();
        });
        servicePanel.addView(start, matchParams(0, 0, 52));
        Button testOverlay = secondaryButton("Preview overlay · 5 seconds");
        testOverlay.setOnClickListener(v -> testOverlay());
        servicePanel.addView(testOverlay, matchParams(10, 0, 52));
        Button checkSignal = secondaryButton("Check live audio signal");
        checkSignal.setOnClickListener(v ->
                Toast.makeText(this, OverlayService.audioSignalStatus(), Toast.LENGTH_LONG).show());
        servicePanel.addView(checkSignal, matchParams(10, 0, 52));
        Button stop = secondaryButton("Stop PulseWave");
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_STOP);
            startService(i);
        });
        servicePanel.addView(stop, matchParams(10, 0, 52));
        TextView note = text("The visualizer uses Android's low-quality output-mix Visualizer API. It does not save audio or read notifications.",
                13, color(R.color.text_secondary));
        servicePanel.addView(note, matchParams(14, 0));
        addBlock(root, servicePanel, 0, 8);
        return scroll;
    }

    private void addAccessItem(LinearLayout panel, String label, TextView status,
            String actionLabel, View.OnClickListener listener) {
        LinearLayout row = horizontalRow();
        TextView name = text(label, 15, color(R.color.text));
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(status, wrapParams());
        panel.addView(row, matchParams(0, 0));
        Button action = secondaryButton(actionLabel);
        action.setOnClickListener(listener);
        panel.addView(action, matchParams(8, 0, 48));
    }

    private void addMotionToggle(LinearLayout parent, String title, String details,
            String key, boolean current) {
        LinearLayout line = horizontalRow();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 15, color(R.color.text)), matchParams(0, 0));
        copy.addView(text(details, 12, color(R.color.text_secondary)), matchParams(4, 0));
        line.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = new Switch(this);
        toggle.setShowText(false);
        toggle.setThumbTintList(switchThumbTint());
        toggle.setTrackTintList(switchTrackTint());
        toggle.setChecked(current);
        line.addView(toggle, wrapParams());
        toggle.setOnCheckedChangeListener((buttonView, value) -> {
            Prefs.get(this).edit().putBoolean(key, value).apply();
            refreshService();
        });
        parent.addView(line, matchParams(6, 10));
    }

    private void addSlider(LinearLayout panel, String title, String key,
            int min, int max, int current) {
        LinearLayout row = horizontalRow();
        TextView label = text(title, 14, color(R.color.text));
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueLabel = text(String.valueOf(current), 14, color(R.color.text_secondary));
        valueLabel.setGravity(Gravity.RIGHT);
        row.addView(valueLabel, wrapParams());
        panel.addView(row, matchParams(0, 0));

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, current - min)));
        bar.setProgressTintList(ColorStateList.valueOf(color(R.color.accent)));
        bar.setThumbTintList(ColorStateList.valueOf(color(R.color.accent_pressed)));
        bar.setSplitTrack(false);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueLabel.setText(String.valueOf(value));
                if (fromUser) {
                    Prefs.get(MainActivity.this).edit().putInt(key, value).apply();
                    if (!Prefs.KEY_BASS_GAIN.equals(key)) {
                        StudioStore.clearAppliedAudio(MainActivity.this);
                    }
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}

            public void onStopTrackingTouch(SeekBar seekBar) { refreshService(); }
        });
        panel.addView(bar, matchParams(2, 0, 34));
    }

    private void refreshService() {
        if (!OverlayService.isRunning()) return;
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
                Toast.makeText(this, "Open Settings > Apps > PulseWave > Display over other apps", Toast.LENGTH_LONG).show();
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

    private void openMusicAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this,
                    "Open Android Settings > Apps > Special app access > Notification access",
                    Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "PulseWave preview shown for 5 seconds", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not start the PulseWave overlay service", Toast.LENGTH_LONG).show();
        }
    }

    private void updateStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean usage = UsageAccess.isGranted(this);
        boolean audio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        overlayStatus.setText(overlay ? "READY" : "NEEDS ACCESS");
        overlayStatus.setTextColor(overlay ? color(R.color.status_success) : color(R.color.status_warning));

        usageAccessStatus.setText(usage ? "READY" : "NEEDS ACCESS");
        usageAccessStatus.setTextColor(usage ? color(R.color.status_success) : color(R.color.status_warning));

        audioStatus.setText(audio ? "GRANTED" : "NEEDS ACCESS");
        audioStatus.setTextColor(audio ? color(R.color.status_success) : color(R.color.status_warning));

        boolean enabled = Prefs.mediaSessionDetectionEnabled(this);
        boolean granted = MusicSessionListener.hasAccess(this);
        mediaSessionStatus.setText(!enabled ? "OFF" : granted ? "GRANTED" : "NEEDS ACCESS");
        mediaSessionStatus.setTextColor(!enabled ? color(R.color.text_secondary)
                : granted ? color(R.color.status_success) : color(R.color.status_warning));

        Set<String> selected = Prefs.selectedApps(this);
        selectedCount.setText(selected.size() + (selected.size() == 1 ? " app selected" : " apps selected"));

        boolean lockScreen = Prefs.lockScreenEnabled(this);
        lockScreenStatus.setText(lockScreen ? "BEST EFFORT • NOT VERIFIED" : "EXPERIMENT DISABLED");
        lockScreenStatus.setTextColor(lockScreen ? color(R.color.status_warning) : color(R.color.text_secondary));
    }

    private void section(LinearLayout root, String name, String description) {
        TextView heading = eyebrow(name);
        addBlock(root, heading, 0, 5);
        TextView body = text(description, 13, color(R.color.text_secondary));
        addBlock(root, body, 0, 10);
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(roundDrawable(color(R.color.surface), 18, color(R.color.divider), 1));
        return panel;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(color(R.color.divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(12);
        params.bottomMargin = dp(10);
        parent.addView(divider, params);
    }

    private TextView eyebrow(String value) {
        TextView view = text(value, 11, color(R.color.accent_pressed));
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setLetterSpacing(0.14f);
        return view;
    }

    private TextView statusText() {
        TextView view = text("CHECKING", 11, color(R.color.text_secondary));
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        return view;
    }

    private Button secondaryButton(String label) {
        Button button = button(label);
        button.setBackground(roundDrawable(color(R.color.surface_elevated), 14,
                color(R.color.divider), 1));
        return button;
    }

    private Button primaryButton(String label) {
        Button button = button(label);
        button.setTextColor(color(R.color.text));
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setBackground(roundDrawable(color(R.color.accent), 14,
                color(R.color.accent_pressed), 1));
        return button;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(color(R.color.text));
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setStateListAnimator(null);
        return button;
    }

    private TextView text(String value, int sp, int textColor) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(textColor);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private LinearLayout.LayoutParams matchParams(int top, int left, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
        params.topMargin = dp(top);
        params.leftMargin = dp(left);
        params.rightMargin = dp(left);
        return params;
    }

    private LinearLayout.LayoutParams matchParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addBlock(LinearLayout parent, View child, int top, int bottom) {
        parent.addView(child, matchParams(top, bottom));
    }

    private GradientDrawable roundDrawable(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private ColorStateList radioTint() {
        return new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {color(R.color.accent_pressed), color(R.color.text_secondary)});
    }

    private ColorStateList switchThumbTint() {
        return new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {color(R.color.accent_pressed), color(R.color.text_secondary)});
    }

    private ColorStateList switchTrackTint() {
        return new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {color(R.color.accent), color(R.color.divider)});
    }

    private int color(int resource) {
        return getResources().getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
