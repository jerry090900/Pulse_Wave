package com.jerry.sharpwave;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Deterministic renderer contract shared by preview and live overlay.
 * FFT capture and live smoothing remain separately regression-tested.
 */
public final class StudioAudioRendererTest {
    @Test public void previewAndLiveUseSameNumberOfBars() {
        assertEquals(20, StudioAudioRenderer.barCount(200f, 8f, 2f));
        assertEquals(20, StudioAudioRenderer.barCount(200f, 8f, 2f));
        assertEquals(0, StudioAudioRenderer.barCount(0f, 8f, 2f));
    }

    @Test public void studioOwnsItsColorModeIndependentlyOfLegacySwitch() {
        assertTrue(StudioAudioRenderer.useTwoColors(true, true, true));
        assertFalse(StudioAudioRenderer.useTwoColors(true, false, false));
        assertTrue(StudioAudioRenderer.useTwoColors(false, false, false));
        assertFalse(StudioAudioRenderer.useTwoColors(false, true, true));
    }

    @Test public void waveUsesUnchangedHeightMapping() {
        assertEquals(82f, StudioAudioRenderer.waveY(100f, 0f), .001f);
        assertEquals(10f, StudioAudioRenderer.waveY(100f, 1f), .001f);
        assertEquals(46f, StudioAudioRenderer.waveY(100f, .5f), .001f);
    }

    @Test public void presetCopiesAndDuplicatesPreserveTwoColors() {
        StudioPreset p = StudioPreset.template("test", "Wave", StudioPreset.WAVE);
        assertTrue(p.twoTone);
        p.twoTone = false;
        assertFalse(p.copy().twoTone);
        assertFalse(p.duplicate().twoTone);
    }
}
