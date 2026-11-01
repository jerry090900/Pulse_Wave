package com.jerry.sharpwave;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class LockScreenActivity extends Activity {
    private static volatile LockScreenActivity instance;

    private SharpVisualizerView visualizerView;

    static boolean isShowing() {
        return instance != null;
    }

    static void pushSpectrum(float[] spectrum, int sampleRate) {
        LockScreenActivity activity = instance;
        if (activity != null) activity.visualizerView.submitSpectrum(spectrum, sampleRate);
    }

    static void pushWaveform(byte[] waveform) {
        LockScreenActivity activity = instance;
        if (activity != null) activity.visualizerView.setWaveform(waveform);
    }

    static void applyStyle(int spikeWidth, int gap, int sensitivity, int palette) {
        LockScreenActivity activity = instance;
        if (activity != null) {
            activity.visualizerView.setStyle(spikeWidth, gap, sensitivity, palette);
        }
    }

    static void close() {
        LockScreenActivity activity = instance;
        if (activity == null) return;
        activity.runOnUiThread(activity::finish);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(false);

        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0f);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        visualizerView = new SharpVisualizerView(this);
        visualizerView.setStyle(
                Prefs.spikeWidth(this),
                Prefs.gap(this),
                Prefs.sensitivity(this),
                Prefs.palette(this));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        int heightPx = Math.round(Prefs.height(this) * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                heightPx,
                Gravity.TOP | Gravity.START);
        root.addView(visualizerView, params);
        setContentView(root);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        instance = this;
    }

    @Override
    protected void onStop() {
        if (instance == this) instance = null;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
