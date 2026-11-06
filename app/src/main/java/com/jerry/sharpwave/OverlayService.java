package com.jerry.sharpwave;

import android.Manifest;
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

import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
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
    // Short, bounded continuation after a known selected app is backgrounded.
    private static final long LOCK_SCREEN_SELECTED_HOLD_MS = 2L * 60L * 1000L;
    private static final float FFT_ACTIVITY_THRESHOLD = 2.0f;
    private static final long VISUALIZER_RETRY_INITIAL_DELAY_MS = 1000L;
    private static final long VISUALIZER_RETRY_MAX_DELAY_MS = 30_000L;
    private static final long VISUALIZER_CALLBACK_STALL_TIMEOUT_MS = 3L * POLL_INTERVAL_MS;
    private static volatile boolean running;
    private static volatile OverlayService activeInstance;

    public static boolean isRunning() {
        return running;
    }

    /** Local-only troubleshooting; a global mix cannot identify a specific music app. */
    public static String audioSignalStatus() {
        OverlayService service = activeInstance;
        if (!running || service == null) return "Visualizer service is stopped";
        if (!service.screenInteractive) return "Visualizer paused while screen is off";
        if (service.visualizer == null) {
            return "Visualizer unavailable or waiting to reconnect";
        }
        long now = SystemClock.elapsedRealtime();
        if (service.lastVisualizerCallbackAt <= 0
                || now - service.lastVisualizerCallbackAt > 1500L) {
            return "No recent Android audio capture callbacks";
        }
        if (service.lastAudioAt > 0L && now - service.lastAudioAt <= 1500L) {
            return "Receiving output-mix FFT peaks (not attributed to a specific app)";
        }
        return "Capture callbacks active, but no measurable audio peaks";
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final byte[] previewWave = new byte[1024];
    private int previewPhase;
    private boolean previewActive;
    private boolean previewOnly;
    private WindowManager windowManager;
    private SharpVisualizerView visualizerView;
    private volatile Visualizer visualizer;
    private final Object visualizerCallbackLock = new Object();
    private volatile long visualizerGeneration;
    private volatile long lastVisualizerCallbackAt;
    private long visualizerRetryAt;
    private boolean captureAsPlayed = true;
    private long visualizerRetryDelayMs = VISUALIZER_RETRY_INITIAL_DELAY_MS;
    private volatile boolean destroyed;
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
    private volatile long lastAudioAt;
    private Set<String> previousSelectedApps = Collections.emptySet();
    private final Map<String, Long> selectedMembershipSince = new HashMap<>();
    private boolean selectedAppsInitialized;
    private String lastNotificationText;
    private volatile boolean screenInteractive;
    private boolean keyguardLocked;
    private boolean lockHostRequested;
    private boolean lockHostUnavailable;

    private final Runnable endPreview = new Runnable() {
        @Override public void run() {
            previewActive = false;
            if (shouldClearPreviewResponse(mediaActive)) clearVisualizerResponse();
            updateOverlayState();
            if (shouldStopPreviewOnly(previewOnly, previewActive, mediaActive)) {
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
        destroyed = false;
        activeInstance = this;
        running = true;
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for a selected music app"));
        lastNotificationText = "Waiting for a selected music app";
        updateDisplayState();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        visualizerView = new SharpVisualizerView(this);
        applyStyle();
        reconcileVisualizer(false);
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
        return previewOnly ? START_NOT_STICKY : START_STICKY;
    }

    private void applyStyle() {
        int spikeWidth = Prefs.spikeWidth(this);
        int gap = Prefs.gap(this);
        int sensitivity = Prefs.sensitivity(this);
        int palette = Prefs.palette(this);
        if (visualizerView != null) {
            visualizerView.setStyle(spikeWidth, gap, sensitivity, palette);
            visualizerView.setStudioScene(StudioStore.activeAudio(this));
            visualizerView.setPresentation(Prefs.centeredBass(this),
                    Prefs.singleColorBars(this), Prefs.bassGain(this));
        }
        LockScreenActivity.applyStyle(spikeWidth, gap, sensitivity, palette);
        LockScreenActivity.applyPresentation(Prefs.centeredBass(this),
                Prefs.singleColorBars(this), Prefs.bassGain(this));
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
        Set<String> selected = Prefs.selectedApps(this);
        if (!selectedAppsInitialized) {
            previousSelectedApps = new HashSet<>(selected);
            selectedAppsInitialized = true;
        } else if (!selected.equals(previousSelectedApps)) {
            for (String packageName : previousSelectedApps) {
                if (!selected.contains(packageName)) selectedMembershipSince.remove(packageName);
            }
            for (String packageName : selected) {
                if (!previousSelectedApps.contains(packageName)) {
                    selectedMembershipSince.put(packageName, nowWall);
                }
            }
            if (latestSelectedPackage != null && !selected.contains(latestSelectedPackage)) {
                latestSelectedPackage = null;
                latestSelectedAt = 0L;
                lastSelectedForegroundAt = 0L;
            }
            activeSourcePackage = retainSelectedSource(activeSourcePackage, selected);
            previousSelectedApps = new HashSet<>(selected);
        }

        if (!UsageAccess.isGranted(this)) {
            latestForegroundPackage = null;
            latestForegroundAt = 0L;
            latestAppPackage = null;
            latestAppAt = 0L;
            latestSelectedPackage = null;
            latestSelectedAt = 0L;
            lastSelectedForegroundAt = 0L;
            activeSourcePackage = null;
            usageQueryStart = 0L;
        } else {
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
                        if (isCurrentSelectedEvent(pkg, selected, timestamp,
                                selectedMembershipSince)
                                && timestamp >= latestSelectedAt) {
                            latestSelectedAt = timestamp;
                            latestSelectedPackage = pkg;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            usageQueryStart = nowWall;
        }

        boolean selectedForeground = isSelectedForeground(latestForegroundPackage, selected,
                keyguardLocked);
        String selectedPackage = selectedForeground
                ? latestForegroundPackage : latestSelectedPackage;
        if (selectedForeground) {
            latestSelectedPackage = selectedPackage;
            latestSelectedAt = nowWall;
        }
        boolean selectedPackageEnabled = selectedPackage != null
                && selected.contains(selectedPackage);
        if (selectedForeground) lastSelectedForegroundAt = nowWall;
        long selectedAge = latestSelectedAt > 0L ? nowWall - latestSelectedAt : Long.MAX_VALUE;

        boolean selectedWasLastApp = selectedPackageEnabled
                && latestSelectedPackage != null
                && latestSelectedPackage.equals(latestAppPackage);
        boolean lockScreenHold = false;
        if (Prefs.lockScreenEnabled(this) && keyguardLocked && screenInteractive) {
            long selectedForegroundAge = lastSelectedForegroundAt > 0L
                    ? nowWall - lastSelectedForegroundAt
                    : Long.MAX_VALUE;
            // Continue recent selected playback even if home/Studio was the last app.
            lockScreenHold = shouldKeepSelectedOnLock(
                    Prefs.lockScreenEnabled(this), keyguardLocked, screenInteractive,
                    selectedPackageEnabled,
                    activeSourcePackage != null
                            && activeSourcePackage.equals(selectedPackage),
                    selectedForegroundAge, LOCK_SCREEN_SELECTED_HOLD_MS);
        }

        boolean recentSelected = !selectedForeground
                && selectedPackageEnabled
                && ((selectedAge >= 0L && selectedAge <= SELECTED_GRACE_PERIOD_MS)
                        || lockScreenHold);

        // Android's global music-active/Visualizer signal cannot attribute output to a package.
        // Keep the most recently selected foreground source eligible only while output continues.
        if (latestSelectedPackage != null && selectedForeground) {
            activeSourcePackage = latestSelectedPackage;
        }
        if (activeSourcePackage != null
                && !selected.contains(activeSourcePackage)) {
            activeSourcePackage = null;
        }
        boolean audioPermission = hasRecordAudioPermission();
        boolean audioActive = audioPermission && audioActivityPresent(nowMono);
        boolean backgroundSelected = !selectedForeground && activeSourcePackage != null
                && audioActive && (!keyguardLocked || lockScreenHold);
        String source = audioPermission
                ? resolveSelectedSource(selectedPackage, selectedForeground,
                        activeSourcePackage, recentSelected, backgroundSelected, audioActive)
                : null;
        String nextSource = audioPermission
                ? nextActiveSource(source, selectedPackage, selectedForeground)
                : null;
        if (nextSource == null && audioPermission) {
            nextSource = retainSelectedSourceDuringTransition(activeSourcePackage, selected,
                    screenInteractive, keyguardLocked, audioActive, selectedWasLastApp);
        }
        activeSourcePackage = nextSource;
        mediaActive = source != null && audioActive;
        reconcileVisualizer(selectedForeground);
        updateOverlayState();
        if (shouldStopPreviewOnly(previewOnly, previewActive, mediaActive)) {
            previewOnly = false;
            stopSelf();
        }
    }

    static String resolveSelectedSource(String selectedPackage, boolean selectedForeground,
            String rememberedPackage, boolean recentSelected, boolean backgroundSelected,
            boolean audioActive) {
        if (selectedForeground || recentSelected) return selectedPackage;
        return backgroundSelected && audioActive ? rememberedPackage : null;
    }

    static String nextActiveSource(String source, String selectedPackage,
            boolean selectedForeground) {
        if (source != null) return source;
        return selectedForeground ? selectedPackage : null;
    }

    static boolean isSelectedForeground(String foregroundPackage, Set<String> selected) {
        return isSelectedForeground(foregroundPackage, selected, false);
    }

    static boolean isSelectedForeground(String foregroundPackage, Set<String> selected,
            boolean keyguardLocked) {
        return !keyguardLocked
                && foregroundPackage != null
                && selected != null
                && selected.contains(foregroundPackage);
    }

    static boolean shouldKeepSelectedOnLock(boolean enabled, boolean locked,
            boolean interactive, boolean selectedEnabled, boolean sameSelectedSource,
            long lastSelectedAgeMs, long maxHoldMs) {
        return enabled && locked && interactive && selectedEnabled && sameSelectedSource
                && lastSelectedAgeMs >= 0L && lastSelectedAgeMs <= maxHoldMs;
    }

    static String retainSelectedSource(String sourcePackage, Set<String> selected) {
        return sourcePackage != null && selected != null && selected.contains(sourcePackage)
                ? sourcePackage : null;
    }

    static String retainSelectedSourceDuringTransition(String sourcePackage, Set<String> selected,
            boolean screenInteractive, boolean keyguardLocked, boolean audioActive,
            boolean selectedWasLastApp) {
        if (!audioActive || (screenInteractive && !keyguardLocked)) return null;
        if (screenInteractive && keyguardLocked && !selectedWasLastApp) return null;
        return retainSelectedSource(sourcePackage, selected);
    }

    static boolean isCurrentSelectedEvent(String packageName, Set<String> selected,
            long eventTimestamp, Map<String, Long> selectedSince) {
        if (packageName == null || selected == null || !selected.contains(packageName)) {
            return false;
        }
        Long membershipStart = selectedSince == null ? null : selectedSince.get(packageName);
        return membershipStart == null || eventTimestamp >= membershipStart;
    }

    static boolean shouldStopPreviewOnly(boolean previewOnly, boolean previewActive,
            boolean mediaActive) {
        return previewOnly && !previewActive && !mediaActive;
    }

    static boolean shouldClearPreviewResponse(boolean mediaActive) {
        return !mediaActive;
    }

    static boolean isAudioActivityPresent(long lastAudioAt, long nowMono,
            long holdMillis, boolean musicActive) {
        return (lastAudioAt > 0L && nowMono - lastAudioAt <= holdMillis) || musicActive;
    }

    static boolean shouldCaptureVisualizer(boolean screenInteractive, boolean selectedForeground,
            boolean mediaActive) {
        return shouldCaptureVisualizer(screenInteractive, selectedForeground, mediaActive,
                true, true);
    }

    static boolean shouldCaptureVisualizer(boolean screenInteractive, boolean selectedForeground,
            boolean mediaActive, boolean audioPermission, boolean overlayPermission) {
        return audioPermission && overlayPermission
                && screenInteractive && (selectedForeground || mediaActive);
    }

    static long nextVisualizerRetryDelay(long currentDelay, long initialDelay, long maxDelay) {
        long initial = Math.max(1L, initialDelay);
        long maximum = Math.max(initial, maxDelay);
        if (currentDelay <= 0L) return initial;
        long current = Math.min(currentDelay, maximum);
        if (current >= maximum || current > maximum / 2L) return maximum;
        return current * 2L;
    }

    static boolean isVisualizerRetryDue(long now, long retryAt) {
        return retryAt <= 0L || now >= retryAt;
    }

    static boolean isVisualizerCallbackStalled(long lastCallbackAt, long now,
            long timeoutMillis) {
        return lastCallbackAt > 0L && now >= lastCallbackAt
                && now - lastCallbackAt > Math.max(0L, timeoutMillis);
    }

    private void reconcileVisualizer(boolean selectedForeground) {
        boolean eligible = shouldCaptureVisualizer(screenInteractive, selectedForeground,
                mediaActive, hasRecordAudioPermission(), Settings.canDrawOverlays(this));
        if (!eligible) {
            resetVisualizerRetry();
            stopVisualizer();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        Visualizer current = visualizer;
        if (current != null && captureAsPlayed != Prefs.asPlayed(this)) {
            stopVisualizer();
            resetVisualizerRetry();
            current = null;
        }
        if (current != null) {
            boolean enabled;
            try {
                enabled = current.getEnabled();
            } catch (Exception ignored) {
                enabled = false;
            }
            if (enabled && !isVisualizerCallbackStalled(lastVisualizerCallbackAt, now,
                    VISUALIZER_CALLBACK_STALL_TIMEOUT_MS)) {
                return;
            }
            stopVisualizer();
            scheduleVisualizerRetry(now);
            return;
        }

        if (isVisualizerRetryDue(now, visualizerRetryAt)) startVisualizer();
    }

    private boolean hasRecordAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
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
            } else if (!hasRecordAudioPermission()) {
                updateNotification("Grant audio permission to visualize");
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
            } else if (!hasRecordAudioPermission()) {
                updateNotification("Grant audio permission to visualize");
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
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
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
        int position = Prefs.overlayPosition(this);
        params.gravity = (position == Prefs.POSITION_TOP ? Gravity.TOP
                : position == Prefs.POSITION_BOTTOM ? Gravity.BOTTOM
                : Gravity.CENTER_VERTICAL) | Gravity.START;
        params.x = 0;
        params.y = 0;
        params.setTitle("PulseWave visualizer");
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

    private void resetVisualizerRetry() {
        visualizerRetryAt = 0L;
        visualizerRetryDelayMs = VISUALIZER_RETRY_INITIAL_DELAY_MS;
    }

    private void scheduleVisualizerRetry(long now) {
        long delay = Math.max(1L, visualizerRetryDelayMs);
        visualizerRetryAt = now > Long.MAX_VALUE - delay
                ? Long.MAX_VALUE : now + delay;
        visualizerRetryDelayMs = nextVisualizerRetryDelay(delay,
                VISUALIZER_RETRY_INITIAL_DELAY_MS, VISUALIZER_RETRY_MAX_DELAY_MS);
    }

    private void startVisualizer() {
        if (destroyed) return;
        if (visualizer != null) {
            try {
                if (visualizer.getEnabled()) {
                    resetVisualizerRetry();
                    return;
                }
            } catch (Exception ignored) {
            }
            stopVisualizer();
        }
        final long generation = ++visualizerGeneration;
        Visualizer candidate = null;
        try {
            candidate = new Visualizer(0);
            visualizer = candidate;
            int[] range = Visualizer.getCaptureSizeRange();
            int captureSize = range[1];
            if (candidate.setCaptureSize(captureSize) != Visualizer.SUCCESS) {
                throw new IllegalStateException("Visualizer capture size setup failed");
            }
            boolean preferredAsPlayed = Prefs.asPlayed(this);
            int scaling = preferredAsPlayed ? Visualizer.SCALING_MODE_AS_PLAYED
                    : Visualizer.SCALING_MODE_NORMALIZED;
            int scalingResult = candidate.setScalingMode(scaling);
            if (scalingResult != Visualizer.SUCCESS) {
                if (scaling == Visualizer.SCALING_MODE_NORMALIZED
                        || candidate.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
                            != Visualizer.SUCCESS) {
                    throw new IllegalStateException("Visualizer scaling setup failed");
                }
                preferredAsPlayed = false;
            }
            captureAsPlayed = preferredAsPlayed;
            fftSpectrum = new float[captureSize / 2];
            int rate = Math.max(1, Visualizer.getMaxCaptureRate());
            final Visualizer callbackVisualizer = candidate;
            int listenerStatus = candidate.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform,
                        int samplingRate) {
                    if (!isCurrentVisualizerCallback(generation, callbackVisualizer)) return;
                    synchronized (visualizerCallbackLock) {
                        if (!isCurrentVisualizerCallback(generation, callbackVisualizer)) return;
                        lastVisualizerCallbackAt = SystemClock.elapsedRealtime();
                        if (waveform == null || waveform.length == 0) return;
                        if (visualizerView != null) visualizerView.submitWaveform(waveform);
                        LockScreenActivity.pushCapturedWaveform(waveform);
                    }
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    if (!isCurrentVisualizerCallback(generation, callbackVisualizer)) return;
                    synchronized (visualizerCallbackLock) {
                        if (!isCurrentVisualizerCallback(generation, callbackVisualizer)) return;
                        lastVisualizerCallbackAt = SystemClock.elapsedRealtime();
                        if (fft == null || fft.length < 4) return;
                        int bins = fft.length / 2;
                        float[] spectrum = fftSpectrum;
                        if (spectrum.length != bins) {
                            spectrum = new float[bins];
                            fftSpectrum = spectrum;
                        }
                        float peak = SharpVisualizerView.fftPeakMagnitude(fft);
                        for (int i = 0; i < bins; i++) {
                            float magnitude = SharpVisualizerView.fftMagnitudeAtBin(fft, i);
                            spectrum[i] = magnitude;
                        }
                        if (peak >= FFT_ACTIVITY_THRESHOLD) {
                            lastAudioAt = SystemClock.elapsedRealtime();
                        }
                        if (visualizerView != null) {
                            visualizerView.submitSpectrum(spectrum, samplingRate);
                        }
                        LockScreenActivity.pushSpectrum(spectrum, samplingRate);
                    }
                }
            }, rate, true, true);
            if (listenerStatus != Visualizer.SUCCESS) {
                throw new IllegalStateException("Visualizer listener setup failed");
            }
            if (candidate.setEnabled(true) != Visualizer.SUCCESS) {
                throw new IllegalStateException("Visualizer enable failed");
            }
            lastVisualizerCallbackAt = SystemClock.elapsedRealtime();
            resetVisualizerRetry();
        } catch (Exception e) {
            stopVisualizer();
            scheduleVisualizerRetry(SystemClock.elapsedRealtime());
        }
    }

    static boolean acceptsVisualizerCallback(long callbackGeneration, long activeGeneration,
            boolean sameVisualizer, boolean serviceDestroyed) {
        return !serviceDestroyed && callbackGeneration == activeGeneration && sameVisualizer;
    }

    private boolean isCurrentVisualizerCallback(long generation, Visualizer candidate) {
        return acceptsVisualizerCallback(generation, visualizerGeneration,
                visualizer == candidate, destroyed);
    }

    private void stopVisualizer() {
        Visualizer current = visualizer;
        if (current == null) {
            lastAudioAt = 0L;
            lastVisualizerCallbackAt = 0L;
            if (!previewActive) {
                clearVisualizerResponse();
            }
            return;
        }
        ++visualizerGeneration;
        visualizer = null;
        synchronized (visualizerCallbackLock) {
            // Wait for a callback that passed the first generation check before
            // invalidating the native listener.
        }
        try { current.setDataCaptureListener(null, 0, false, false); }
        catch (Exception ignored) {}
        try { current.setEnabled(false); } catch (Exception ignored) {}
        try { current.release(); } catch (Exception ignored) {}
        fftSpectrum = new float[0];
        lastAudioAt = 0L;
        lastVisualizerCallbackAt = 0L;
        clearVisualizerResponse();
    }

    private void clearVisualizerResponse() {
        if (visualizerView != null) visualizerView.clearSpectrum();
        LockScreenActivity.clearSpectrum();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "PulseWave service", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps the user-enabled top visualizer ready for selected media apps.");
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("PulseWave")
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
        destroyed = true;
        boolean wasActiveInstance = activeInstance == this;
        if (wasActiveInstance) {
            activeInstance = null;
            running = false;
        }
        handler.removeCallbacks(endPreview);
        handler.removeCallbacks(previewTicker);
        handler.removeCallbacks(usagePoller);
        handler.removeCallbacks(lockHostCheck);
        if (wasActiveInstance) closeLockScreenHost();
        stopVisualizer();
        hideOverlay();
        if (wasActiveInstance) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
