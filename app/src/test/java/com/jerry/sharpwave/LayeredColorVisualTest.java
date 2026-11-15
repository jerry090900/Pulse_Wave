package com.jerry.sharpwave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Muviz-inspired composition is three sections of ONE bar, not 3 bars. */
public final class LayeredColorVisualTest {
    @Test public void defaultsCreateThreeOrderedSectionsAtSameBarPosition() {
        float[] breaks = new float[2];
        StudioAudioRenderer.layeredFractions(.5f, .5f, .5f, 0f,
                33, 68, 0, breaks);
        assertEquals(.33f, breaks[0], .00001f);
        assertEquals(.68f, breaks[1], .00001f);
        assertTrue(breaks[0] > 0f);
        assertTrue(breaks[1] > breaks[0]);
        assertTrue(breaks[1] < 1f);
    }

    @Test public void nearbyAudioChangesColorBreaksWithoutChangingBarHeight() {
        float[] original = new float[2];
        float[] reactive = new float[2];
        StudioAudioRenderer.layeredFractions(.5f, .5f, .5f, 0f,
                33, 68, 85, original);
        StudioAudioRenderer.layeredFractions(.5f, .95f, .10f, .25f,
                33, 68, 85, reactive);
        assertNotEquals(original[0], reactive[0], .00001f);
        assertNotEquals(original[1], reactive[1], .00001f);
        assertEquals(50f, StudioAudioRenderer.barBottomY(100f, .5f, true), .00001f);
        assertEquals(50f, StudioAudioRenderer.barTopY(100f, .5f, false), .00001f);
    }

    @Test public void allColorBreaksRemainOrderedForExtremeAudio() {
        float[] breaks = new float[2];
        for (float a : new float[]{0f, .1f, .5f, 1f}) {
            for (float b : new float[]{0f, .1f, .5f, 1f}) {
                StudioAudioRenderer.layeredFractions(a, b, 1f - b, .8f,
                        30, 67, 100, breaks);
                assertTrue(breaks[0] >= .07f);
                assertTrue(breaks[1] > breaks[0]);
                assertTrue(breaks[1] <= .96f);
            }
        }
    }

    @Test public void newModeIsOptInAndPreservesPriorColorModes() {
        StudioPreset older = StudioPreset.template("old", "Old Red White", StudioPreset.BARS);
        assertEquals(StudioPreset.COLOR_TWO, older.effectiveColorMode());
        older.setColorMode(StudioPreset.COLOR_THREE);
        assertEquals(StudioPreset.COLOR_THREE, older.effectiveColorMode());
        older.setColorMode(StudioPreset.COLOR_MUSIC_FLOW);
        assertEquals(StudioPreset.COLOR_MUSIC_FLOW, older.effectiveColorMode());
        older.setColorMode(StudioPreset.COLOR_STACKED);
        assertEquals(StudioPreset.COLOR_STACKED, older.copy().effectiveColorMode());
        older.layerASplit = 29;
        older.layerBSplit = 72;
        older.layerMotion = 95;
        StudioPreset saved = older.copy();
        assertEquals(29, saved.layerASplit);
        assertEquals(72, saved.layerBSplit);
        assertEquals(95, saved.layerMotion);
        saved.layerASplit = 80;
        saved.layerBSplit = 10;
        saved.normalize();
        assertEquals(70, saved.layerASplit);
        assertEquals(75, saved.layerBSplit);
    }
}
