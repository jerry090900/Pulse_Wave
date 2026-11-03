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
    private static final Object INSTANCE_LOCK = new Object();
    private static volatile LockScreenActivity instance;

    private SharpVisualizerView visualizerView;

    static boolean isShowing() {
        synchronized (INSTANCE_LOCK) {
            LockScreenActivity activity = instance;
            return isUsableInstance(activity);
        }
    }

    static boolean isUsableInstance(boolean hasInstance, boolean finishing, boolean destroyed) {
        return hasInstance && !finishing && !destroyed;
    }

    private static boolean isUsableInstance(LockScreenActivity activity) {
        return activity != null && isUsableInstance(true, activity.isFinishing(),
                activity.isDestroyed());
    }

    static void pushSpectrum(float[] spectrum, int sampleRate) {
        synchronized (INSTANCE_LOCK) {
            LockScreenActivity activity = instance;
            if (isUsableInstance(activity) && activity.visualizerView != null) {
                activity.visualizerView.submitSpectrum(spectrum, sampleRate);
            }
        }
    }

    static void pushWaveform(byte[] waveform) {
        synchronized (INSTANCE_LOCK) {
            LockScreenActivity activity = instance;
            if (isUsableInstance(activity) && activity.visualizerView != null) {
                activity.visualizerView.setWaveform(waveform);
            }
        }
    }

    static void pushCapturedWaveform(byte[] waveform) {
        synchronized (INSTANCE_LOCK) {
            LockScreenActivity activity = instance;
            if (isUsableInstance(activity) && activity.visualizerView != null) {
                activity.visualizerView.submitWaveform(waveform);
            }
        }
    }

    static void clearSpectrum() {
        synchronized (INSTANCE_LOCK) {
            LockScreenActivity activity = instance;
            if (isUsableInstance(activity) && activity.visualizerView != null) {
                activity.visualizerView.clearSpectrum();
            }
        }
    }

    static void applyStyle(int spikeWidth, int gap, int sensitivity, int palette) {
        synchronized (INSTANCE_LOCK) {
            LockScreenActivity activity = instance;
            if (isUsableInstance(activity) && activity.visualizerView != null) {
                activity.visualizerView.setStyle(spikeWidth, gap, sensitivity, palette);
            }
        }
    }

    static void close() {
        LockScreenActivity activity;
        synchronized (INSTANCE_LOCK) {
            activity = instance;
        }
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
        synchronized (INSTANCE_LOCK) {
            instance = this;
        }
    }

    @Override
    protected void onStop() {
        synchronized (INSTANCE_LOCK) {
            if (instance == this) instance = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        synchronized (INSTANCE_LOCK) {
            if (instance == this) instance = null;
        }
        super.onDestroy();
    }
}
