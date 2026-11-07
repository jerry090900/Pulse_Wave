package com.jerry.sharpwave;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * PulseWave-owned scene editor. Photo and fingerprint effects are in-app previews,
 * not replacements for the OEM's secure AOD or system biometric unlock animation.
 */
public final class StudioActivity extends Activity {
    private static final int REQUEST_PHOTO = 410;
    private static final int BG = 0xFF09090B;
    private static final int SURFACE = 0xFF19191D;
    private static final int TEXT = 0xFFF8F8F8;
    private static final int MUTED = 0xFFB8B8BD;
    private static final int ACCENT = 0xFF9F000E;

    private StudioPreset editing;
    private StudioPreviewView preview;
    private EditText nameField;
    private final Button[] colorSwatches = new Button[3];
    private static final String[] COLOR_LABELS = {
            "Color 1 • Line A", "Color 2 • Line B", "Color 3 • Line C"
    };

    @Override
    protected void onResume() {
        super.onResume();
        OverlayService.setAppUiVisible(true);
    }

    @Override
    protected void onPause() {
        OverlayService.setAppUiVisible(false);
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showGallery();
    }

    private ScrollView page(String heading, String description, boolean backToGallery) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(30));
        scroll.addView(content);
        content.setTag("content");
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button(backToGallery ? "‹ Gallery" : "‹ Back", false);
        back.setOnClickListener(v -> {
            if (backToGallery) showGallery();
            else finish();
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(110), dp(45)));
        content.addView(top);
        content.addView(text(heading, 30, TEXT, true), match(12, 0));
        content.addView(text(description, 14, MUTED, false), match(5, 20));
        setContentView(scroll);
        return scroll;
    }

    private LinearLayout contents(ScrollView page) {
        return (LinearLayout) page.findViewWithTag("content");
    }

    private void showGallery() {
        editing = null;
        preview = null;
        nameField = null;
        for (int i = 0; i < colorSwatches.length; i++) colorSwatches[i] = null;
        LinearLayout content = contents(page("PulseWave Studio",
                "Choose a design, edit it, preview it, then save and apply. No proprietary styles copied.", false));
        info(content, "AUDIO VISUALIZERS • PHOTO CLOCKS • FINGERPRINT FX");

        for (StudioPreset preset : StudioStore.load(this)) {
            LinearLayout card = card();
            StudioPreviewView thumbnail = new StudioPreviewView(this);
            thumbnail.setScene(preset);
            thumbnail.setAnimated(false);
            card.addView(thumbnail, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(145)));
            String active = StudioStore.isApplied(this, preset) ? "  •  SELECTED" : "";
            card.addView(text(preset.name + active, 18, TEXT, true), match(12, 3));
            card.addView(text(preset.category(), 12, MUTED, false), match(0, 8));
            LinearLayout actions = row();
            Button edit = button("Edit", true);
            edit.setOnClickListener(v -> showEditor(preset.copy()));
            actions.addView(edit, weight());
            Button apply = button(preset.isAudio() ? "Apply overlay" : "Apply preview", false);
            apply.setOnClickListener(v -> applyPreset(preset));
            actions.addView(apply, weight());
            card.addView(actions);
            content.addView(card, match(0, 14));
        }
        info(content, "Only audio styles control the existing overlay. Photo clocks and fingerprint rings are preview-only until a compatible display host is implemented.");
    }

    private void showEditor(StudioPreset preset) {
        editing = preset.copy();
        editing.normalize();
        LinearLayout content = contents(page("Edit design", editing.category()
                + " • Changes update the generated preview before saving.", true));
        nameField = new EditText(this);
        nameField.setSingleLine(true);
        nameField.setText(editing.name);
        nameField.setSelectAllOnFocus(true);
        nameField.setHint("Name your design");
        nameField.setHintTextColor(MUTED);
        nameField.setTextColor(TEXT);
        nameField.setTextSize(18);
        nameField.setBackground(tint(SURFACE, 12));
        nameField.setPadding(dp(12), dp(10), dp(12), dp(10));
        content.addView(nameField, match(0, 14));

        preview = new StudioPreviewView(this);
        preview.setScene(editing);
        preview.setAnimated(true);
        content.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        content.addView(text(editing.isAudio()
                ? "SIMULATED MUSIC • LIVE OVERLAY RENDERER"
                : "IN-APP DESIGN PREVIEW", 11, MUTED, false),
                match(8, 14));

        if (editing.isAudio()) {
            slider(content, "Width", 2, 18, editing.width, value -> {
                editing.width = value; updatePreview();
            });
            slider(content, "Gap", 0, 10, editing.gap, value -> {
                editing.gap = value; updatePreview();
            });
            slider(content, "Height", 40, 220, editing.height, value -> {
                editing.height = value; updatePreview();
            });
            slider(content, "Sensitivity", 60, 260, editing.sensitivity, value -> {
                editing.sensitivity = value; updatePreview();
            });
            info(content, "LINE COLORS");
            RadioGroup colorModes = new RadioGroup(this);
            String[] labels = {
                    "One color", "Two colors (classic)", "3 overlapping color lines",
                    "3 overlapping lines • Music Color Flow"
            };
            for (int i = 0; i < labels.length; i++) {
                RadioButton option = new RadioButton(this);
                option.setId(7300 + i);
                option.setText(labels[i]);
                option.setTextColor(TEXT);
                option.setTextSize(14);
                colorModes.addView(option);
                if (editing.effectiveColorMode() == i) option.setChecked(true);
            }
            colorModes.setOnCheckedChangeListener((group, id) -> {
                editing.setColorMode(id - 7300);
                updatePreview();
            });
            content.addView(colorModes, match(0, 10));
            info(content, "Tap a color to edit its hue, saturation, brightness or exact HEX.");
            swatch(content, "Color 1 • Line A", 0);
            swatch(content, "Color 2 • Line B", 1);
            swatch(content, "Color 3 • Line C", 2);
            slider(content, "Music flow strength", 0, 100, editing.flowStrength, value -> {
                editing.flowStrength = value; updatePreview();
            });
            info(content, "Bars: three colored lines occupy each SAME bar position. "
                    + "Each line listens to a nearby audio frequency and can reach a "
                    + "different length. The longer line shows at its tip; shorter "
                    + "lines overlap in front. Music Color Flow rotates the line colors "
                    + "with the existing beats. Wave stays unchanged. Demo audio is simulated.");
        } else if (editing.type == StudioPreset.PHOTO) {
            Button choose = button("Choose a photo", true);
            choose.setOnClickListener(v -> choosePhoto());
            content.addView(choose, match(0, 10));
            slider(content, "Photo opacity", 15, 100, editing.opacity, value -> {
                editing.opacity = value; updatePreview();
            });
            info(content, "Selected photos stay on your device. This is a screen-on preview, not a replacement for the system AOD.");
        } else {
            slider(content, "Ring size", 50, 220, editing.ringSize, value -> {
                editing.ringSize = value; updatePreview();
            });
            slider(content, "Vertical position", 15, 90, editing.offsetY, value -> {
                editing.offsetY = value; updatePreview();
            });
            slider(content, "Opacity", 15, 100, editing.opacity, value -> {
                editing.opacity = value; updatePreview();
            });
            info(content, "Decorative effect only. This app cannot detect or replace Android's fingerprint-unlock animation.");
        }

        if (editing.type != StudioPreset.PHOTO) {
            info(content, "COLOR PRESETS");
            palette(content, "White / Red / Cyan", 0xFFFFFFFF, 0xFF9F000E, 0xFF19CFFA);
            palette(content, "Ice / Blue / Violet", 0xFFDDF6FF, 0xFF236DDE, 0xFFBA65FF);
            palette(content, "Amber / Orange / Pink", 0xFFFFE5AA, 0xFFE78322, 0xFFE566AB);
            palette(content, "Magenta / White / Teal", 0xFFFFD7EF, 0xFFFFFFFF, 0xFF09CCA8);
        }
        LinearLayout actions = row();
        Button save = button("Save", false);
        save.setOnClickListener(v -> savePreset(false));
        actions.addView(save, weight());
        Button apply = button(editing.isAudio() ? "Save + Apply" : "Save + Use", true);
        apply.setOnClickListener(v -> savePreset(true));
        actions.addView(apply, weight());
        content.addView(actions, match(20, 10));
        Button duplicate = button("Duplicate as new preset", false);
        duplicate.setOnClickListener(v -> {
            if (!captureName()) return;
            showEditor(editing.duplicate());
            Toast.makeText(this, "Copy ready to edit — save to keep it", Toast.LENGTH_SHORT).show();
        });
        content.addView(duplicate, match(4, 12));
    }

    private void updatePreview() {
        if (preview != null) preview.setScene(editing);
    }

    private void palette(LinearLayout parent, String label,
            int primary, int accent, int third) {
        Button choice = button(label, false);
        choice.setOnClickListener(v -> {
            editing.primary = primary;
            editing.accent = accent;
            editing.third = third;
            updatePreview();
            refreshSwatches();
            Toast.makeText(this, "Palette updated", Toast.LENGTH_SHORT).show();
        });
        parent.addView(choice, match(0, 5));
    }

    private int swatchColor(int slot) {
        if (slot == 0) return editing.primary;
        if (slot == 1) return editing.accent;
        return editing.third;
    }

    private void setSwatchColor(int slot, int color) {
        if (slot == 0) editing.primary = color;
        else if (slot == 1) editing.accent = color;
        else editing.third = color;
        updatePreview();
    }

    private void styleSwatch(Button button, String label, int color) {
        button.setText(String.format(java.util.Locale.US, "%s      #%06X",
                label, color & 0xFFFFFF));
        button.setTextColor(Color.luminance(color) < .42 ? Color.WHITE : Color.BLACK);
        button.setBackground(tint(color, 13));
    }

    private void refreshSwatches() {
        for (int i = 0; i < colorSwatches.length; i++) {
            if (colorSwatches[i] != null) {
                styleSwatch(colorSwatches[i], COLOR_LABELS[i], swatchColor(i));
            }
        }
    }

    private void swatch(LinearLayout parent, String label, int slot) {
        Button swatch = button(label, false);
        colorSwatches[slot] = swatch;
        styleSwatch(swatch, label, swatchColor(slot));
        swatch.setOnClickListener(v -> StudioColorPicker.show(this, label,
                swatchColor(slot), color -> {
                    setSwatchColor(slot, color);
                    styleSwatch(swatch, label, color);
                }));
        parent.addView(swatch, match(5, 5));
    }

    private boolean captureName() {
        if (editing == null || nameField == null) return false;
        editing.name = StudioPreset.safeName(nameField.getText().toString());
        editing.normalize();
        return true;
    }

    private void savePreset(boolean apply) {
        if (!captureName()) return;
        try {
            if (apply) {
                StudioStore.apply(this, editing);
                if (editing.isAudio() && OverlayService.isRunning()) {
                    startService(new Intent(this, OverlayService.class)
                            .setAction(OverlayService.ACTION_REFRESH));
                }
            } else {
                StudioStore.save(this, editing);
            }
        } catch (Exception error) {
            Toast.makeText(this, "Couldn't save design: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (apply && !editing.isAudio()) {
            Toast.makeText(this, "Selected for Studio preview; system AOD is unchanged",
                    Toast.LENGTH_LONG).show();
        } else if (apply) {
            Toast.makeText(this, "Audio style saved for PulseWave overlay",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Preset saved", Toast.LENGTH_SHORT).show();
        }
        showGallery();
    }

    private void applyPreset(StudioPreset p) {
        try {
            StudioStore.apply(this, p);
            if (p.isAudio() && OverlayService.isRunning()) {
                startService(new Intent(this, OverlayService.class)
                        .setAction(OverlayService.ACTION_REFRESH));
            }
            Toast.makeText(this, p.isAudio() ? "Applied to music overlay"
                    : "Selected preview only — system AOD unchanged", Toast.LENGTH_LONG).show();
            showGallery();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't apply design: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void choosePhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "No compatible image picker available", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_PHOTO || result != RESULT_OK || data == null
                || data.getData() == null || editing == null
                || editing.type != StudioPreset.PHOTO) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            editing.photoUri = uri.toString();
            updatePreview();
        } catch (SecurityException e) {
            Toast.makeText(this, "Couldn't keep photo access. Choose another image.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private interface Change { void changed(int value); }

    private void slider(LinearLayout parent, String label, int min, int max, int value,
            Change change) {
        LinearLayout line = row();
        line.addView(text(label, 15, TEXT, false), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView current = text(String.valueOf(value), 14, MUTED, false);
        line.addView(current);
        parent.addView(line, match(10, 0));
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(StudioPreset.clamp(value, min, max) - min);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(0xFFFF6670));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int next = min + progress;
                    current.setText(String.valueOf(next));
                    change.changed(next);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        parent.addView(bar, match(0, 4));
    }

    private LinearLayout card() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(tint(SURFACE, 20));
        return panel;
    }

    private void info(LinearLayout parent, String message) {
        parent.addView(text(message, 12, MUTED, false), match(10, 16));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button button(String label, boolean emphasized) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(TEXT);
        b.setBackground(tint(emphasized ? ACCENT : 0xFF323237, 14));
        return b;
    }

    private GradientDrawable tint(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView text(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams match(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(49), 1f);
        p.setMargins(dp(2), 0, dp(2), 0);
        return p;
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density + .5f);
    }

    @Override
    public void onBackPressed() {
        if (editing != null) showGallery();
        else super.onBackPressed();
    }
}
