package com.jerry.sharpwave;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.Set;

public class OverlayService extends Service {
    public static final String ACTION_REFRESH = "com.jerry.sharpwave.REFRESH";
    public static final String ACTION_STOP = "com.jerry.sharpwave.STOP";
    public static final String ACTION_PREVIEW = "com.jerry.sharpwave.PREVIEW";
    public static final String EXTRA_PREVIEW_ONLY = "preview_only";

    private static final String CHANNEL_ID = "sharpwave_running";
    private static final int NOTIFICATION_ID = 4401;
    private static final long PREVIEW_DURATION_MS = 5000L;
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long INITIAL_USAGE_LOOKBACK_MS = 12L * 60L * 60L * 1000L;
    private static final long USAGE_OVERLAP_MS = 2000L;
    private static final long SELECTED_GRACE_PERIOD_MS = 8000L;
    private static final long AUDIO_HOLD_MS = 2500L;
    private static final long LOCK_HOST_GRACE_MS = 1500L;
    private static final long LOCK_SCREEN_SELECTED_HOLD_MS = 12L * 60L * 60L * 1000L;
    private static final float FFT_ACTIVITY_THRESHOLD = 2.0f;
    private static volatile boolean running;

    public static boolean isRunning() {
        return running;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final byte[] previewWave = new byte[1024];
    private int previewPhase;
    private boolean previewActive;
    private boolean previewOnly;
    private WindowManager windowManager;
    private SharpVisualizerView visualizerView;
    private Visualizer visualizer;
    private float[] fftSpectrum = new float[0];
    private boolean overlayAdded;
    private boolean mediaActive;
    private String activeSourcePackage;
    private long usageQueryStart;
    private String latestForegroundPackage;
    private long latestForegroundAt;
    private String latestAppPackage;
    private long latestAppAt;
    private String latestSelectedPackage;
    private long latestSelectedAt;
    private long lastSelectedForegroundAt;
    private long lastAudioAt;
    private String lastNotificationText;
    private boolean screenInteractive;
    private boolean keyguardLocked;
    private boolean lockHostRequested;
    private boolean lockHostUnavailable;

    private final Runnable endPreview = new Runnable() {
        @Override public void run() {
            previewActive = false;
            updateOverlayState();
            if (previewOnly && !mediaActive) {
                previewOnly = false;
                stopSelf();
            }
        }
    };

    private final Runnable previewTicker = new Runnable() {
        @Override public void run() {
            if (!previewActive || visualizerView == null) return;
            for (int i = 0; i < previewWave.length; i++) {
                double wave = Math.sin((i * 0.06) + (previewPhase * 0.22));
                double envelope = 0.35 + 0.65 * Math.abs(Math.sin(previewPhase * 0.11));
                previewWave[i] = (byte) (128 + (int) Math.round(wave * envelope * 110));
            }
            previewPhase++;
            visualizerView.setWaveform(previewWave);
            LockScreenActivity.pushWaveform(previewWave);
            handler.postDelayed(this, 50L);
        }
    };

    private final Runnable lockHostCheck = new Runnable() {
        @Override public void run() {
            if (!lockHostRequested) return;
            lockHostRequested = false;
            if (!LockScreenActivity.isShowing()) {
                lockHostUnavailable = true;
                updateNotification("Lock-screen host blocked by this device");
            }
        }
    };

    private final Runnable usagePoller = new Runnable() {
        @Override public void run() {
            updateMediaActivity();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for a selected music app"));
        lastNotificationText = "Waiting for a selected music app";
        updateDisplayState();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        visualizerView = new SharpVisualizerView(this);
        applyStyle();
        startVisualizer();
        handler.post(usagePoller);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action == null) previewOnly = false;
            if (ACTION_STOP.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            if (ACTION_REFRESH.equals(action)) {
                applyStyle();
                updateDisplayState();
                if (overlayAdded) recreateOverlay();
                updateOverlayState();
            }
            if (ACTION_PREVIEW.equals(action)) {
                previewOnly = intent.getBooleanExtra(EXTRA_PREVIEW_ONLY, false);
                startPreview();
            }
        }
        return START_STICKY;
    }

    private void applyStyle() {
        int spikeWidth = Prefs.spikeWidth(this);
        int gap = Prefs.gap(this);
        int sensitivity = Prefs.sensitivity(this);
        int palette = Prefs.palette(this);
        if (visualizerView != null) {
            visualizerView.setStyle(spikeWidth, gap, sensitivity, palette);
        }
        LockScreenActivity.applyStyle(spikeWidth, gap, sensitivity, palette);
    }

    private void updateDisplayState() {
        PowerManager powerManager = getSystemService(PowerManager.class);
        screenInteractive = powerManager != null && powerManager.isInteractive();
        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        keyguardLocked = keyguardManager != null && keyguardManager.isKeyguardLocked();
    }

    private void startPreview() {
        previewActive = true;
        handler.removeCallbacks(endPreview);
        handler.postDelayed(endPreview, PREVIEW_DURATION_MS);
        updateOverlayState();
    }

    private void updateMediaActivity() {
        long nowWall = System.currentTimeMillis();
        long nowMono = SystemClock.elapsedRealtime();
        updateDisplayState();

        if (!UsageAccess.isGranted(this)) {
            latestForegroundPackage = null;
            latestForegroundAt = 0L;
            latestAppPackage = null;
            latestAppAt = 0L;
            latestSelectedPackage = null;
            latestSelectedAt = 0L;
            lastSelectedForegroundAt = 0L;
            usageQueryStart = 0L;
        } else {
            Set<String> selected = Prefs.selectedApps(this);
            long start = usageQueryStart == 0L
                    ? nowWall - INITIAL_USAGE_LOOKBACK_MS
                    : Math.max(0L, usageQueryStart - USAGE_OVERLAP_MS);
            if (start > nowWall) start = nowWall;
            try {
                UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
                UsageEvents events = manager == null ? null : manager.queryEvents(start, nowWall);
                if (events != null) {
                    UsageEvents.Event event = new UsageEvents.Event();
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event);
                        if (event.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) continue;
                        String pkg = event.getPackageName();
                        if (pkg == null || pkg.isEmpty()) continue;
                        String className = event.getClassName();
                        boolean ourLockHost = getPackageName().equals(pkg)
                                && (LockScreenActivity.isShowing()
                                        || (className != null
                                                && className.endsWith("LockScreenActivity")));
                        long timestamp = event.getTimeStamp();
                        if (timestamp >= latestForegroundAt) {
                            latestForegroundAt = timestamp;
                            latestForegroundPackage = pkg;
                        }
                        if (!isKeyguardSurface(pkg)
                                && !ourLockHost
                                && timestamp >= latestAppAt) {
                            latestAppAt = timestamp;
                            latestAppPackage = pkg;
                        }
                        if (selected.contains(pkg) && timestamp >= latestSelectedAt) {
                            latestSelectedAt = timestamp;
                            latestSelectedPackage = pkg;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            usageQueryStart = nowWall;
        }

        boolean selectedForeground = latestSelectedPackage != null
                && latestSelectedPackage.equals(latestForegroundPackage);
        if (selectedForeground) lastSelectedForegroundAt = nowWall;
        long selectedAge = latestSelectedAt > 0L ? nowWall - latestSelectedAt : Long.MAX_VALUE;

        boolean lockScreenHold = false;
        if (Prefs.lockScreenEnabled(this) && keyguardLocked && screenInteractive) {
            boolean keyguardSurface = isKeyguardSurface(latestForegroundPackage)
                    || LockScreenActivity.isShowing();
            boolean selectedWasLastApp = latestSelectedPackage != null
                    && latestSelectedPackage.equals(latestAppPackage);
            long selectedForegroundAge = lastSelectedForegroundAt > 0L
                    ? nowWall - lastSelectedForegroundAt
                    : Long.MAX_VALUE;
            lockScreenHold = keyguardSurface
                    && selectedWasLastApp
                    && selectedForegroundAge >= 0L
                    && selectedForegroundAge <= LOCK_SCREEN_SELECTED_HOLD_MS;
        }

        boolean recentSelected = !selectedForeground
                && latestSelectedPackage != null
                && ((selectedAge >= 0L && selectedAge <= SELECTED_GRACE_PERIOD_MS)
                        || lockScreenHold);

        // Android's global music-active/Visualizer signal cannot attribute output to a package.
        // Keep the most recently selected foreground source eligible only while output continues.
        if (latestSelectedPackage != null && selectedForeground) {
            activeSourcePackage = latestSelectedPackage;
        }
        if (activeSourcePackage != null
                && !Prefs.selectedApps(this).contains(activeSourcePackage)) {
            activeSourcePackage = null;
        }
        boolean audioActive = audioActivityPresent(nowMono);
        boolean backgroundSelected = !selectedForeground && activeSourcePackage != null
                && audioActive && (!keyguardLocked || lockScreenHold);
        String source = resolveSelectedSource(latestSelectedPackage, selectedForeground,
                activeSourcePackage, recentSelected, backgroundSelected, audioActive);
        activeSourcePackage = nextActiveSource(source, latestSelectedPackage,
                audioActive, selectedForeground);
        mediaActive = source != null && audioActive;
        updateOverlayState();
    }

    static String resolveSelectedSource(String selectedPackage, boolean selectedForeground,
            String rememberedPackage, boolean recentSelected, boolean backgroundSelected,
            boolean audioActive) {
        if (selectedForeground || recentSelected) return selectedPackage;
        return backgroundSelected && audioActive ? rememberedPackage : null;
    }

    static String nextActiveSource(String source, String selectedPackage,
            boolean audioActive, boolean selectedForeground) {
        if (source != null) return source;
        if (!audioActive || selectedForeground) {
            return selectedForeground ? selectedPackage : null;
        }
        return selectedPackage;
    }

    static boolean isAudioActivityPresent(long lastAudioAt, long nowMono,
            long holdMillis, boolean musicActive) {
        return (lastAudioAt > 0L && nowMono - lastAudioAt <= holdMillis) || musicActive;
    }

    private static boolean isKeyguardSurface(String packageName) {
        if (packageName == null) return false;
        return packageName.contains("systemui")
                || packageName.contains("keyguard")
                || packageName.contains("lockscreen")
                || packageName.contains("lock_screen")
                || packageName.contains("aod");
    }

    private boolean audioActivityPresent(long nowMono) {
        AudioManager audioManager = getSystemService(AudioManager.class);
        boolean musicActive = audioManager != null && audioManager.isMusicActive();
        return isAudioActivityPresent(lastAudioAt, nowMono, AUDIO_HOLD_MS, musicActive);
    }

    private void updateOverlayState() {
        updateDisplayState();
        boolean active = mediaActive || previewActive;
        boolean lockScreenMode = Prefs.lockScreenEnabled(this);

        if (!screenInteractive) {
            closeLockScreenHost();
            stopPreviewTicker();
            hideOverlay();
            if (active && lockScreenMode) {
                updateNotification("Screen off - true AOD drawing is unavailable");
            } else if (active) {
                updateNotification("Screen off - overlay paused");
            } else if (!UsageAccess.isGranted(this)) {
                updateNotification("Grant Usage Access to detect selected apps");
            } else {
                updateNotification("Waiting for a selected music app");
            }
            return;
        }

        if (active && Settings.canDrawOverlays(this)) {
            showOverlay();
            if (previewActive && !mediaActive) startPreviewTicker(); else stopPreviewTicker();
            if (lockScreenMode && keyguardLocked) {
                showLockScreenHost();
                if (LockScreenActivity.isShowing()) {
                    updateNotification(mediaActive
                            ? "Lock-screen experiment: reacting to " + appLabel(activeSourcePackage)
                            : "Lock-screen experiment: test overlay");
                } else if (lockHostUnavailable) {
                    updateNotification("Lock-screen host blocked; overlay best effort");
                } else {
                    updateNotification("Starting lock-screen host");
                }
            } else {
                closeLockScreenHost();
                if (mediaActive) {
                    updateNotification("Reacting to " + appLabel(activeSourcePackage));
                } else {
                    updateNotification("Showing test overlay");
                }
            }
        } else {
            closeLockScreenHost();
            stopPreviewTicker();
            hideOverlay();
            if (active) {
                updateNotification("Overlay access is off - allow display over other apps");
            } else if (!UsageAccess.isGranted(this)) {
                updateNotification("Grant Usage Access to detect selected apps");
            } else {
                updateNotification("Waiting for a selected music app");
            }
        }
    }

    private void showLockScreenHost() {
        if (LockScreenActivity.isShowing()) {
            lockHostRequested = false;
            lockHostUnavailable = false;
            return;
        }
        if (lockHostRequested || lockHostUnavailable) return;
        lockHostRequested = true;
        Intent intent = new Intent(this, LockScreenActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(intent);
            handler.postDelayed(lockHostCheck, LOCK_HOST_GRACE_MS);
        } catch (Exception ignored) {
            lockHostRequested = false;
            lockHostUnavailable = true;
        }
    }

    private void closeLockScreenHost() {
        handler.removeCallbacks(lockHostCheck);
        lockHostRequested = false;
        lockHostUnavailable = false;
        LockScreenActivity.close();
    }

    private String appLabel(String packageName) {
        if (packageName == null) return "selected app";
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (Exception ignored) {
            return packageName;
        }
    }

    private void startPreviewTicker() {
        handler.removeCallbacks(previewTicker);
        handler.post(previewTicker);
    }

    private void stopPreviewTicker() {
        handler.removeCallbacks(previewTicker);
    }

    private void showOverlay() {
        if (overlayAdded) return;
        int heightPx = Math.round(Prefs.height(this) * getResources().getDisplayMetrics().density);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (Prefs.lockScreenEnabled(this)) {
            flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                heightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        params.setTitle("SharpWave visualizer");
        try {
            windowManager.addView(visualizerView, params);
            overlayAdded = true;
        } catch (Exception ignored) {
            overlayAdded = false;
        }
    }

    private void recreateOverlay() {
        hideOverlay();
        if (mediaActive || previewActive) showOverlay();
    }

    private void hideOverlay() {
        if (!overlayAdded) return;
        try { windowManager.removeView(visualizerView); } catch (Exception ignored) {}
        overlayAdded = false;
    }

    private void startVisualizer() {
        if (visualizer != null) return;
        try {
            visualizer = new Visualizer(0);
            int[] range = Visualizer.getCaptureSizeRange();
            int captureSize = range[1];
            visualizer.setCaptureSize(captureSize);
            visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            fftSpectrum = new float[captureSize / 2];
            int rate = Math.max(1, Visualizer.getMaxCaptureRate());
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {}

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    if (fft == null || fft.length < 4) return;
                    int bins = fft.length / 2;
                    float[] spectrum = fftSpectrum;
                    if (spectrum.length != bins) {
                        spectrum = new float[bins];
                        fftSpectrum = spectrum;
                    }
                    float peak = 0f;
                    for (int i = 0; i < bins; i++) {
                        int real = fft[i * 2];
                        int imaginary = fft[i * 2 + 1];
                        float magnitude = (float) Math.sqrt(real * real + imaginary * imaginary);
                        spectrum[i] = magnitude;
                        if (magnitude > peak) peak = magnitude;
                    }
                    if (peak >= FFT_ACTIVITY_THRESHOLD) {
                        lastAudioAt = SystemClock.elapsedRealtime();
                    }
                    if (visualizerView != null) visualizerView.submitSpectrum(spectrum, samplingRate);
                    LockScreenActivity.pushSpectrum(spectrum, samplingRate);
                }
            }, rate, false, true);
            visualizer.setEnabled(true);
        } catch (Exception e) {
            stopVisualizer();
        }
    }

    private void stopVisualizer() {
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (Exception ignored) {}
            try { visualizer.release(); } catch (Exception ignored) {}
            visualizer = null;
        }
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "SharpWave service", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps the user-enabled top visualizer ready for selected media apps.");
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("SharpWave")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        if (text.equals(lastNotificationText)) return;
        lastNotificationText = text;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(endPreview);
        handler.removeCallbacks(previewTicker);
        handler.removeCallbacks(usagePoller);
        handler.removeCallbacks(lockHostCheck);
        closeLockScreenHost();
        stopVisualizer();
        hideOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
