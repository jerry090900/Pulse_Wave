package com.jerry.sharpwave;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/** Small built-in HSV + exact-HEX color editor; changes preview immediately. */
final class StudioColorPicker {
    interface OnColorChange { void changed(int color); }

    private StudioColorPicker() {}

    static void show(Activity activity, String title, int original,
            OnColorChange onChange) {
        float[] hsv = new float[3];
        Color.colorToHSV(original, hsv);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        layout.setPadding(pad, dp(activity, 10), pad, dp(activity, 8));
        TextView sample = new TextView(activity);
        sample.setGravity(android.view.Gravity.CENTER);
        sample.setTextSize(19);
        sample.setMinHeight(dp(activity, 58));
        layout.addView(sample);
        EditText exactHex = new EditText(activity);
        exactHex.setSingleLine(true);
        exactHex.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        exactHex.setTextSize(17);
        exactHex.setText(String.format(Locale.US, "#%06X", original & 0xFFFFFF));
        layout.addView(exactHex);

        final SeekBar[] controls = new SeekBar[3];
        final int[] max = {360, 100, 100};
        final String[] labels = {"Hue", "Saturation", "Brightness"};
        final int[] current = {
                Math.round(hsv[0]), Math.round(hsv[1] * 100), Math.round(hsv[2] * 100)
        };
        Runnable redraw = () -> {
            int chosen = Color.HSVToColor(new float[]{
                    current[0], current[1] / 100f, current[2] / 100f
            });
            sample.setText(String.format(Locale.US, "#%06X", chosen & 0xFFFFFF));
            sample.setTextColor(Color.luminance(chosen) < .48
                    ? Color.WHITE : Color.BLACK);
            GradientDrawable background = new GradientDrawable();
            background.setColor(chosen);
            background.setCornerRadius(dp(activity, 12));
            sample.setBackground(background);
            onChange.changed(chosen);
        };
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView titleLabel = new TextView(activity);
            titleLabel.setText(labels[i]);
            titleLabel.setTextSize(14);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = dp(activity, 10);
            layout.addView(titleLabel, titleParams);
            SeekBar slider = new SeekBar(activity);
            slider.setMax(max[i]);
            slider.setProgress(current[i]);
            slider.setProgressTintList(ColorStateList.valueOf(0xFF9F000E));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress,
                        boolean fromUser) {
                    if (!fromUser) return;
                    current[index] = progress;
                    redraw.run();
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            controls[i] = slider;
            layout.addView(slider);
        }
        Button applyHex = new Button(activity);
        applyHex.setAllCaps(false);
        applyHex.setText("Apply HEX color");
        applyHex.setOnClickListener(v -> {
            String input = exactHex.getText().toString().trim();
            if (!input.matches("#?[0-9a-fA-F]{6}")) {
                exactHex.setError("Enter 6 hexadecimal digits, e.g. #19CFFA");
                return;
            }
            int color = Color.parseColor(input.startsWith("#") ? input : "#" + input);
            Color.colorToHSV(color, hsv);
            current[0] = Math.round(hsv[0]);
            current[1] = Math.round(hsv[1] * 100);
            current[2] = Math.round(hsv[2] * 100);
            for (int i = 0; i < controls.length; i++) {
                controls[i].setProgress(current[i]);
            }
            onChange.changed(color);
            sample.setText(String.format(Locale.US, "#%06X", color & 0xFFFFFF));
            sample.setTextColor(Color.luminance(color) < .48
                    ? Color.WHITE : Color.BLACK);
            GradientDrawable background = new GradientDrawable();
            background.setColor(color);
            background.setCornerRadius(dp(activity, 12));
            sample.setBackground(background);
        });
        layout.addView(applyHex);
        // A dialog should never silently persist an edited color after Cancel.
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title).setView(layout)
                .setPositiveButton("Keep color", null)
                .setNegativeButton("Cancel", (d, which) -> onChange.changed(original))
                .create();
        dialog.setOnCancelListener(d -> onChange.changed(original));
        dialog.show();
        // Initial drawing does not alter the user's original selection.
        sample.setText(String.format(Locale.US, "#%06X", original & 0xFFFFFF));
        sample.setTextColor(Color.luminance(original) < .48
                ? Color.WHITE : Color.BLACK);
        GradientDrawable initial = new GradientDrawable();
        initial.setColor(original);
        initial.setCornerRadius(dp(activity, 12));
        sample.setBackground(initial);
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + .5f);
    }
}
