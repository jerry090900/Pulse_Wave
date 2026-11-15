package com.jerry.sharpwave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Only rendering coordinates and colors change; FFT capture, mapping, smoothing,
 * music timing, and the Wave's path geometry stay exactly as before.
 */
public final class LegacyBarDirectionAndColorTest {
    @Test public void topBarsStartAtTopAndExtendDownward() {
        assertEquals(0f, StudioAudioRenderer.barTopY(100f, .25f, true), .00001f);
        assertEquals(25f, StudioAudioRenderer.barBottomY(100f, .25f, true), .00001f);
        assertEquals(0f, StudioAudioRenderer.barTopY(100f, 1f, true), .00001f);
        assertEquals(100f, StudioAudioRenderer.barBottomY(100f, 1f, true), .00001f);
    }

    @Test public void middleAndBottomBarsPreserveUpwardGeometry() {
        assertEquals(75f, StudioAudioRenderer.barTopY(100f, .25f, false), .00001f);
        assertEquals(100f, StudioAudioRenderer.barBottomY(100f, .25f, false), .00001f);
        assertEquals(100f, StudioAudioRenderer.barTopY(100f, 0f, false), .00001f);
        assertEquals(0f, StudioAudioRenderer.barBottomY(100f, 0f, true), .00001f);
    }

    @Test public void waveFollowsChosenScreenPositionButKeepsOriginalShape() {
        StudioPreset wave = StudioPreset.template("wave", "Wave", StudioPreset.WAVE);
        StudioPreset bars = StudioPreset.template("bars", "Bars", StudioPreset.BARS);
        for (int requested = Prefs.POSITION_TOP; requested <= Prefs.POSITION_BOTTOM;
                requested++) {
            assertEquals(requested,
                    OverlayService.overlayPositionForScene(requested, wave));
            assertEquals(requested,
                    OverlayService.overlayPositionForScene(requested, bars));
            assertEquals(requested,
                    OverlayService.overlayPositionForScene(requested, null));
        }
        assertEquals(82f, StudioAudioRenderer.waveY(100f, 0f), .00001f);
        assertEquals(10f, StudioAudioRenderer.waveY(100f, 1f), .00001f);
    }

    @Test public void everyBarRendersThreeIndependentSamplesAtOnePosition() {
        // A 60-bar screen has a +/-5 bar frequency offset for overlaid lines.
        assertEquals(30, StudioAudioRenderer.overlaidSampleIndex(30, 60, 0));
        assertEquals(35, StudioAudioRenderer.overlaidSampleIndex(30, 60, 1));
        assertEquals(25, StudioAudioRenderer.overlaidSampleIndex(30, 60, 2));

        float[] levels = new float[60];
        levels[30] = .6f;
        levels[35] = .3f;
        levels[25] = .9f;
        assertEquals(.6f, StudioAudioRenderer.overlaidLevel(levels, 30, 0), .00001f);
        assertEquals(.3f, StudioAudioRenderer.overlaidLevel(levels, 30, 1), .00001f);
        assertEquals(.9f, StudioAudioRenderer.overlaidLevel(levels, 30, 2), .00001f);
        assertEquals(0, StudioAudioRenderer.overlaidSampleIndex(0, 60, 2));
        assertEquals(59, StudioAudioRenderer.overlaidSampleIndex(59, 60, 1));
        assertEquals(0f, StudioAudioRenderer.overlaidLevel(null, 0, 1), .00001f);
    }

    @Test public void threeLineSlotsStaySeparateAndOrderedAThenBThenC() {
        float gap = StudioAudioRenderer.innerGap(12f, 1f);
        float width = StudioAudioRenderer.channelWidth(12f, gap);
        float leftA = StudioAudioRenderer.channelLeft(10f, 12f, 0, gap);
        float leftB = StudioAudioRenderer.channelLeft(10f, 12f, 1, gap);
        float leftC = StudioAudioRenderer.channelLeft(10f, 12f, 2, gap);
        assertEquals(1f, gap, .00001f);
        assertEquals(10f, leftA, .00001f);
        assertTrue(leftA + width < leftB);
        assertTrue(leftB + width < leftC);
        assertEquals(22f, leftC + width, .00001f);
        // Narrow legacy bars never produce a negative line width.
        assertTrue(StudioAudioRenderer.channelWidth(2f,
                StudioAudioRenderer.innerGap(2f, 10f)) > 0f);
    }

    @Test public void threeColorsUseExactSwatchesInStaticOverlaidMode() {
        int cyan = 0xFF19CFFA;
        int white = 0xFFFFFFFF;
        int pink = 0xFFFF55A2;
        assertEquals(cyan, StudioAudioRenderer.overlaidColor(
                0, cyan, white, pink, StudioPreset.COLOR_THREE, .42f));
        assertEquals(white, StudioAudioRenderer.overlaidColor(
                1, cyan, white, pink, StudioPreset.COLOR_THREE, .42f));
        assertEquals(pink, StudioAudioRenderer.overlaidColor(
                2, cyan, white, pink, StudioPreset.COLOR_THREE, .42f));
    }

    @Test public void beatPulseKeepsPermanentA_B_CColorAssignments() {
        int cyan = 0xFF19CFFA;
        int white = 0xFFFFFFFF;
        int pink = 0xFFFF55A2;
        // A is always cyan-ish, B remains white, C is always pink-ish.
        assertEquals(cyan, StudioAudioRenderer.overlaidColor(
                0, cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, 0f));
        assertNotEquals(cyan, StudioAudioRenderer.overlaidColor(
                0, cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, .1f));
        assertEquals(white, StudioAudioRenderer.overlaidColor(
                1, cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, .1f));
        assertEquals(pink, StudioAudioRenderer.overlaidColor(
                2, cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, 0f));
        for (int layer = 0; layer < 3; layer++) {
            assertEquals(StudioAudioRenderer.overlaidColor(layer,
                    cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, .1f),
                    StudioAudioRenderer.overlaidColor(layer,
                    cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, 1.1f));
        }
        // Bar height/position remains untouched by color movement.
        assertEquals(25f, StudioAudioRenderer.barBottomY(100f, .25f, true), .00001f);
        assertEquals(75f, StudioAudioRenderer.barTopY(100f, .25f, false), .00001f);
        assertTrue(MusicColorFlow.wrap(1f) == 0f);
    }

    @Test public void waveEditorDefaultsAndSettingsNeverMoveOldBars() {
        StudioPreset p = StudioPreset.template("wave", "Wave", StudioPreset.WAVE);
        assertEquals(82f, StudioAudioRenderer.waveY(100f, 0f, p), .00001f);
        assertEquals(10f, StudioAudioRenderer.waveY(100f, 1f, p), .00001f);
        assertEquals(4f, StudioAudioRenderer.waveStrokeWidth(2f, 6f, p), .00001f);
        p.waveBaseline = 50;
        p.waveAmplitude = 50;
        p.waveStroke = 6;
        assertEquals(32f, StudioAudioRenderer.waveY(100f, .5f, p), .00001f);
        assertEquals(12f, StudioAudioRenderer.waveStrokeWidth(2f, 6f, p), .00001f);
        assertEquals(25f, StudioAudioRenderer.barBottomY(100f, .25f, true), .00001f);
    }
}
