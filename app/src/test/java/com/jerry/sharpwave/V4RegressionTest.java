package com.jerry.sharpwave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public class V4RegressionTest {
    @Test
    public void selectedSourceRequiresGlobalAudioOrExistingHold() {
        assertEquals("music.app", OverlayService.resolveSelectedSource(
                "music.app", false, "music.app", true, false, true));
        assertEquals("music.app", OverlayService.resolveSelectedSource(
                "music.app", false, "music.app", false, true, true));
        assertNull(OverlayService.resolveSelectedSource(
                "music.app", false, "music.app", false, false, false));
        assertEquals("music.app", OverlayService.resolveSelectedSource(
                "music.app", false, "music.app", true, true, false));
    }

    @Test
    public void eligibilityDoesNotInventPerAppAttribution() {
        assertEquals("selected.music", OverlayService.resolveSelectedSource(
                "selected.music", false, "selected.music", true, false, false));
        assertFalse(OverlayService.isAudioActivityPresent(0L, 3000L, 2500L, false));
        assertTrue(OverlayService.isAudioActivityPresent(1000L, 3000L, 2500L, false));
        assertTrue(OverlayService.isAudioActivityPresent(0L, 3000L, 2500L, true));
    }

    @Test
    public void stopAndInactivityClearRememberedSelection() {
        assertNull(OverlayService.nextActiveSource(null, null, false));
        assertNull(OverlayService.nextActiveSource(null, null, true));
        assertEquals("music.app", OverlayService.nextActiveSource(
                "music.app", "music.app", false));
        assertNull(OverlayService.nextActiveSource(null, "stale.music", false));
    }

    @Test
    public void foregroundSourceMustStillBeSelected() {
        assertTrue(OverlayService.isSelectedForeground("music.app",
                Collections.singleton("music.app")));
        assertFalse(OverlayService.isSelectedForeground("music.app",
                Collections.singleton("other.app")));
        assertFalse(OverlayService.isSelectedForeground(null,
                Collections.singleton("music.app")));
    }

    @Test
    public void keyguardCannotTreatStaleAppAsForeground() {
        assertFalse(OverlayService.isSelectedForeground("music.app",
                Collections.singleton("music.app"), true));
    }

    @Test
    public void lockScreenHostOnlyUsesLiveActivityInstance() {
        assertTrue(LockScreenActivity.isUsableInstance(true, false, false));
        assertFalse(LockScreenActivity.isUsableInstance(true, true, false));
        assertFalse(LockScreenActivity.isUsableInstance(true, false, true));
        assertFalse(LockScreenActivity.isUsableInstance(false, false, false));
    }

    @Test
    public void revokingSelectedAppDropsRememberedSource() {
        assertEquals("music.app", OverlayService.retainSelectedSource("music.app",
                Collections.singleton("music.app")));
        assertNull(OverlayService.retainSelectedSource("music.app",
                Collections.singleton("other.app")));
        assertFalse(OverlayService.isCurrentSelectedEvent("music.app",
                Collections.singleton("music.app"), 999L,
                Collections.singletonMap("music.app", 1000L)));
        assertTrue(OverlayService.isCurrentSelectedEvent("music.app",
                Collections.singleton("music.app"), 1000L,
                Collections.singletonMap("music.app", 1000L)));
    }

    @Test
    public void backgroundSourceSurvivesScreenAndKeyguardTransitionOnlyWhileAudioContinues() {
        assertEquals("music.app", OverlayService.retainSelectedSourceDuringTransition(
                "music.app", Collections.singleton("music.app"), false, true, true, false));
        assertEquals("music.app", OverlayService.retainSelectedSourceDuringTransition(
                "music.app", Collections.singleton("music.app"), true, true, true, true));
        assertNull(OverlayService.retainSelectedSourceDuringTransition(
                "music.app", Collections.singleton("music.app"), true, false, true, true));
        assertNull(OverlayService.retainSelectedSourceDuringTransition(
                "music.app", Collections.singleton("music.app"), false, true, false, true));
        assertNull(OverlayService.retainSelectedSourceDuringTransition(
                "music.app", Collections.singleton("other.app"), false, true, true, true));
        assertNull(OverlayService.retainSelectedSourceDuringTransition(
                "music.app", Collections.singleton("music.app"), true, true, true, false));
    }

    @Test
    public void previewOnlyServiceStopsAfterPreviewAndMediaEnd() {
        assertTrue(OverlayService.shouldStopPreviewOnly(true, false, false));
        assertFalse(OverlayService.shouldStopPreviewOnly(true, true, false));
        assertFalse(OverlayService.shouldStopPreviewOnly(true, false, true));
        assertFalse(OverlayService.shouldStopPreviewOnly(false, false, false));
    }

    @Test
    public void previewEndClearsSyntheticResponseButPreservesLiveMediaResponse() {
        assertTrue(OverlayService.shouldClearPreviewResponse(false));
        assertFalse(OverlayService.shouldClearPreviewResponse(true));
    }

    @Test
    public void smoothingUsesElapsedTimeWithAttackAndReleaseRates() {
        float fastAttack = SharpVisualizerView.smoothLevel(0f, 1f, 35L, 35f);
        float slowAttack = SharpVisualizerView.smoothLevel(0f, 1f, 70L, 35f);
        float release = SharpVisualizerView.smoothLevel(1f, 0f, 110L, 110f);
        assertTrue(slowAttack > fastAttack);
        assertEquals((float) Math.exp(-1d), release, 0.0001f);
        assertEquals(1f, SharpVisualizerView.smoothLevel(0f, 1f, 1000L, 35f), 0.0001f);
    }

    @Test
    public void spectrumFallsBackWhenStale() {
        assertTrue(SharpVisualizerView.isSpectrumFresh(true, 1700L, 1000L, 700L));
        assertFalse(SharpVisualizerView.isSpectrumFresh(true, 1701L, 1000L, 700L));
        assertFalse(SharpVisualizerView.isSpectrumFresh(false, 1000L, 1000L, 700L));
        assertTrue(SharpVisualizerView.isSpectrumFresh(true, 900L, 1000L, 700L));
    }

    @Test
    public void lifecycleResetClearsAllResponseLayers() {
        float[] smoothed = {0.9f, 0.6f, 0.3f};
        float[] rawTargets = {1f, 0.7f, 0.4f};
        float[] blurredTargets = {0.8f, 0.5f, 0.2f};

        SharpVisualizerView.resetResponseLayers(smoothed, rawTargets, blurredTargets);

        for (float value : smoothed) assertEquals(0f, value, 0.0001f);
        for (float value : rawTargets) assertEquals(0f, value, 0.0001f);
        for (float value : blurredTargets) assertEquals(0f, value, 0.0001f);
    }

    @Test
    public void fftUsesAndroidDcNyquistLayout() {
        byte[] fft = {3, 4, 5, 12, 0, 0, 0, 0};
        assertEquals(3f, SharpVisualizerView.fftMagnitudeAtBin(fft, 0), 0.0001f);
        assertEquals(13f, SharpVisualizerView.fftMagnitudeAtBin(fft, 1), 0.0001f);
        assertEquals(4f, SharpVisualizerView.fftNyquistMagnitude(fft), 0.0001f);
        assertEquals(13f, SharpVisualizerView.fftPeakMagnitude(fft), 0.0001f);
        assertEquals(4f, SharpVisualizerView.fftPeakMagnitude(
                new byte[] {100, 4, 0, 0, 0, 0, 0, 0}), 0.0001f);
    }

    @Test
    public void visualizerSamplingRateIsMappedToHertz() {
        assertEquals(44100, SharpVisualizerView.samplingRateHz(44100000));
        assertEquals(48000, SharpVisualizerView.samplingRateHz(48000));
        assertEquals(44100, SharpVisualizerView.samplingRateHz(0));
    }

    @Test
    public void visualizerCaptureRequiresInteractiveSelectedSourceOrActiveMedia() {
        assertFalse(OverlayService.shouldCaptureVisualizer(false, true, true));
        assertFalse(OverlayService.shouldCaptureVisualizer(true, false, false));
        // A selected foreground app keeps capture available to discover output audio.
        assertTrue(OverlayService.shouldCaptureVisualizer(true, true, false));
        // Background playback keeps capture alive after the selected app leaves foreground.
        assertTrue(OverlayService.shouldCaptureVisualizer(true, false, true));
        assertFalse(OverlayService.shouldCaptureVisualizer(true, true, true, false, true));
        assertFalse(OverlayService.shouldCaptureVisualizer(true, true, true, true, false));
    }

    @Test
    public void staleVisualizerCallbacksAreRejected() {
        assertTrue(OverlayService.acceptsVisualizerCallback(4L, 4L, true, false));
        assertFalse(OverlayService.acceptsVisualizerCallback(3L, 4L, true, false));
        assertFalse(OverlayService.acceptsVisualizerCallback(4L, 4L, false, false));
        assertFalse(OverlayService.acceptsVisualizerCallback(4L, 4L, true, true));
    }

    @Test
    public void visualizerRetryBackoffIsBoundedAndDueCheckIsDeterministic() {
        assertEquals(1000L, OverlayService.nextVisualizerRetryDelay(
                0L, 1000L, 30000L));
        assertEquals(2000L, OverlayService.nextVisualizerRetryDelay(
                1000L, 1000L, 30000L));
        assertEquals(30000L, OverlayService.nextVisualizerRetryDelay(
                16000L, 1000L, 30000L));
        assertEquals(30000L, OverlayService.nextVisualizerRetryDelay(
                30000L, 1000L, 30000L));
        assertFalse(OverlayService.isVisualizerRetryDue(999L, 1000L));
        assertTrue(OverlayService.isVisualizerRetryDue(1000L, 1000L));
        assertTrue(OverlayService.isVisualizerRetryDue(0L, 0L));
    }

    @Test
    public void visualizerCallbackStallRequiresARealElapsedTimeout() {
        assertFalse(OverlayService.isVisualizerCallbackStalled(0L, 5000L, 3000L));
        assertFalse(OverlayService.isVisualizerCallbackStalled(1000L, 4000L, 3000L));
        assertTrue(OverlayService.isVisualizerCallbackStalled(1000L, 4001L, 3000L));
        assertFalse(OverlayService.isVisualizerCallbackStalled(5000L, 4000L, 3000L));
    }

    @Test
    public void oneSignaturePaletteRemainsStableAndOldIndexFallsBack() {
        assertEquals(1, Prefs.PALETTE_COUNT);
        assertEquals(1, Prefs.PALETTE_NAMES.length);
        assertEquals("DHH Signature", Prefs.PALETTE_NAMES[0]);
        assertEquals(0, SharpVisualizerView.paletteIndex(-1));
        assertEquals(0, SharpVisualizerView.paletteIndex(0));
        assertEquals(0, SharpVisualizerView.paletteIndex(9));
        assertEquals(0xFFFFFFFF, SharpVisualizerView.paletteAnchor(0, 0));
        assertEquals(0xFF9F000E, SharpVisualizerView.paletteAnchor(0, 1));
        assertEquals(0xFFB8B8B8, SharpVisualizerView.paletteAnchor(0, 2));
    }}
