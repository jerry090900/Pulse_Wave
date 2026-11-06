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

    @Test public void staticColorsTravelAcrossAdjacentSpectrumLines() {
        int cyan = 0xFF00DDFF;
        int white = 0xFFFFFFFF;
        int pink = 0xFFFF55A2;
        int[] stops = StudioAudioRenderer.spectrumStops(
                cyan, white, pink, StudioPreset.COLOR_THREE, 0f, 0f);
        assertEquals(5, stops.length);
        assertEquals(cyan, stops[0]);
        assertEquals(white, stops[2]);
        assertEquals(pink, stops[4]);
        // Different bars on the same row do not repeat identical stacked colors.
        assertNotEquals(stops[0], stops[2]);
        assertNotEquals(stops[2], stops[4]);
    }

    @Test public void colorFlowMovesAcrossLinesWithoutChangingHeights() {
        int cyan = 0xFF00DDFF;
        int white = 0xFFFFFFFF;
        int pink = 0xFFFF55A2;
        int[] before = StudioAudioRenderer.spectrumStops(
                cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, 0f, 0f);
        int[] after = StudioAudioRenderer.spectrumStops(
                cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, .2f, 0f);
        assertEquals(13, before.length);
        assertEquals(13, after.length);
        assertEquals(cyan, before[0]);
        assertEquals(white, before[4]);
        assertEquals(pink, before[8]);
        assertNotEquals(before[0], after[0]);
        // A moved color phase never changes audio amplitude or bar geometry.
        assertEquals(25f, StudioAudioRenderer.barBottomY(100f, .25f, true), .00001f);
        assertEquals(75f, StudioAudioRenderer.barTopY(100f, .25f, false), .00001f);
        assertTrue(MusicColorFlow.flowingColorAt(0f, .2f, cyan, white, pink)
                == after[0]);
    }

    @Test public void colorAccentAppearsOnlyAtMovingBarEndpoints() {
        assertEquals(6f, StudioAudioRenderer.endpointCapHeight(100f, 1f), .00001f);
        assertEquals(3.6f, StudioAudioRenderer.endpointCapHeight(20f, 1f), .00001f);
        assertEquals(0f, StudioAudioRenderer.endpointCapHeight(0f, 1f), .00001f);
        assertEquals(12f, StudioAudioRenderer.endpointCapHeight(100f, 2f), .00001f);
        int[] body = StudioAudioRenderer.spectrumStops(
                0xFF00DDFF, 0xFFFFFFFF, 0xFFFF55A2,
                StudioPreset.COLOR_MUSIC_FLOW, .2f, 0f);
        int[] tips = StudioAudioRenderer.spectrumStops(
                0xFF00DDFF, 0xFFFFFFFF, 0xFFFF55A2,
                StudioPreset.COLOR_MUSIC_FLOW, .2f, .15f);
        assertNotEquals(body[0], tips[0]);
    }
}
