package com.jerry.sharpwave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Audio synchronization, FFT mapping, smoothing and Wave geometry are never modified.
 * These tests cover only the old bars' drawing orientation and three-color paint inputs.
 */
public final class LegacyBarDirectionAndColorTest {
    @Test public void topBarsStartAtTopAndExtendDownTowardBottom() {
        assertEquals(0f, StudioAudioRenderer.barTopY(100f, .25f, true), .00001f);
        assertEquals(25f, StudioAudioRenderer.barBottomY(100f, .25f, true), .00001f);
        assertEquals(0f, StudioAudioRenderer.barTopY(100f, 1f, true), .00001f);
        assertEquals(100f, StudioAudioRenderer.barBottomY(100f, 1f, true), .00001f);
    }

    @Test public void bottomAndMiddleBarsPreserveTheirOriginalUpwardGrowth() {
        assertEquals(75f, StudioAudioRenderer.barTopY(100f, .25f, false), .00001f);
        assertEquals(100f, StudioAudioRenderer.barBottomY(100f, .25f, false), .00001f);
        assertEquals(100f, StudioAudioRenderer.barTopY(100f, 0f, false), .00001f);
        assertEquals(0f, StudioAudioRenderer.barBottomY(100f, 0f, true), .00001f);
    }

    @Test public void waveStaysInMiddleIndependentOfLegacyBarsSetting() {
        StudioPreset wave = StudioPreset.template("wave", "Wave", StudioPreset.WAVE);
        StudioPreset bars = StudioPreset.template("bars", "Bars", StudioPreset.BARS);
        for (int requested = Prefs.POSITION_TOP; requested <= Prefs.POSITION_BOTTOM;
                requested++) {
            assertEquals(Prefs.POSITION_CENTER,
                    OverlayService.overlayPositionForScene(requested, wave));
            assertEquals(requested,
                    OverlayService.overlayPositionForScene(requested, bars));
            assertEquals(requested,
                    OverlayService.overlayPositionForScene(requested, null));
        }
        // The Wave amplitude map stays the exact same as before direction work.
        assertEquals(82f, StudioAudioRenderer.waveY(100f, 0f), .00001f);
        assertEquals(10f, StudioAudioRenderer.waveY(100f, 1f), .00001f);
    }

    @Test public void staticThreeColorGradientContainsAllThreeColorsInEveryBar() {
        int cyan = 0xFF00DDFF;
        int white = 0xFFFFFFFF;
        int pink = 0xFFFF55A2;
        int[] stops = StudioAudioRenderer.verticalStops(
                cyan, white, pink, StudioPreset.COLOR_THREE, .8f);
        assertEquals(3, stops.length);
        assertEquals(cyan, stops[0]);
        assertEquals(white, stops[1]);
        assertEquals(pink, stops[2]);
    }

    @Test public void musicFlowChangesColorGradientWithoutChangingBarHeights() {
        int cyan = 0xFF00DDFF;
        int white = 0xFFFFFFFF;
        int pink = 0xFFFF55A2;
        int[] a = StudioAudioRenderer.verticalStops(
                cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, 0f);
        int[] b = StudioAudioRenderer.verticalStops(
                cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, .2f);
        assertEquals(7, a.length);
        assertEquals(7, b.length);
        assertEquals(cyan, a[0]);
        assertEquals(white, a[2]);
        assertEquals(pink, a[4]);
        assertNotEquals(a[0], b[0]);
        assertEquals(25f, StudioAudioRenderer.barBottomY(100f, .25f, true), .00001f);
        assertTrue(MusicColorFlow.flowingColorAt(0f, .2f, cyan, white, pink)
                == b[0]);
    }
}
