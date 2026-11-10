package com.jerry.sharpwave;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TripleLayerMotionTest {
    private static final float EPS = .0001f;

    @Test public void dominantColorChangesAcrossMusicCycleIncludingSingleColorMoments() {
        float[] levels = {.8f, .8f, .8f, .8f, .8f, .8f, .8f};
        float[] c = new float[3];

        TripleLayerMotion.reaches(levels, 3, 0f, 100, c);
        assertEquals(.8f, c[0], EPS); // cyan
        assertTrue(c[1] < .09f && c[2] < .09f);

        TripleLayerMotion.reaches(levels, 3, 1f / 3f, 100, c);
        assertEquals(.8f, c[1], EPS); // white
        assertTrue(c[0] < .09f && c[2] < .09f);

        TripleLayerMotion.reaches(levels, 3, 2f / 3f, 100, c);
        assertEquals(.8f, c[2], EPS); // pink
        assertTrue(c[0] < .09f && c[1] < .09f);
    }

    @Test public void greatestLayerHeightAlwaysMatchesOriginalAudioBarHeight() {
        float[] levels = {.2f, .5f, .9f, .7f, .3f, .1f, .4f};
        float[] reaches = new float[3];
        for (int bar = 0; bar < levels.length; bar++) {
            for (int i = 0; i <= 99; i += 3) {
                TripleLayerMotion.reaches(levels, bar, i / 100f, 100, reaches);
                float tallest = Math.max(reaches[0], Math.max(reaches[1], reaches[2]));
                assertEquals(levels[bar], tallest, EPS);
            }
        }
        // Original top-down orientation and Wave remain unaffected.
        assertEquals(90f, StudioAudioRenderer.barBottomY(100f, .9f, true), EPS);
        assertEquals(10f, StudioAudioRenderer.waveY(100f, 1f), EPS);
    }

    @Test public void zeroMovementKeepsClassicThreeSectionLengths() {
        float[] levels = {.2f, .8f, .2f};
        float[] reaches = new float[3];
        TripleLayerMotion.reaches(levels, 1, .12f, 0, reaches);
        assertEquals(.8f * .33f, reaches[0], EPS);
        assertEquals(.8f * .68f, reaches[1], EPS);
        assertEquals(.8f, reaches[2], EPS);
        float[] another = new float[3];
        TripleLayerMotion.reaches(levels, 1, .95f, 0, another);
        assertArrayEquals(reaches, another, EPS);
    }

    @Test public void musicFlowLoopsContinuouslyAndIsAlwaysBounded() {
        float[] levels = {.3f, .5f, .9f, .6f, .4f};
        float[] a = new float[3];
        float[] b = new float[3];
        for (float phase = 0f; phase <= 1f; phase += .04f) {
            TripleLayerMotion.reaches(levels, 2, phase, 100, a);
            for (float reach : a) {
                assertTrue(reach >= 0f && reach <= .9f + EPS);
            }
        }
        TripleLayerMotion.reaches(levels, 2, .0f, 100, a);
        TripleLayerMotion.reaches(levels, 2, 1f, 100, b);
        assertArrayEquals(a, b, EPS);
    }

    @Test public void silenceAndMissingLevelsDoNotCreateFakeBars() {
        float[] reaches = {1f, 1f, 1f};
        TripleLayerMotion.reaches(new float[]{0f, 0f}, 1, .45f, 100, reaches);
        assertArrayEquals(new float[]{0f, 0f, 0f}, reaches, EPS);
        TripleLayerMotion.reaches(null, 0, .5f, 100, reaches);
        assertArrayEquals(new float[]{0f, 0f, 0f}, reaches, EPS);
    }

    @Test public void adjacentFrequenciesChangeCompositionWithoutMutatingSpectrum() {
        float[] levels = {.2f, .8f, .5f, .3f, .2f};
        float[] sourceCopy = levels.clone();
        float[] first = new float[3];
        float[] second = new float[3];
        TripleLayerMotion.reaches(levels, 2, .19f, 100, first);
        levels[1] = .1f;
        levels[3] = .9f;
        TripleLayerMotion.reaches(levels, 2, .19f, 100, second);
        // Neighboring frequencies affect the color layers, not the bar's
        // dominant height (still based on the original center frequency).
        assertTrue(Math.abs(first[0] - second[0])
                + Math.abs(first[1] - second[1])
                + Math.abs(first[2] - second[2]) > .001f);
        assertEquals(sourceCopy[2], Math.max(second[0],
                Math.max(second[1], second[2])), EPS);
        assertEquals(.5f, levels[2], EPS);
    }

    @Test public void v2IsOptInAndCopiesWithoutRewritingPreviousPresets() {
        StudioPreset existing = StudioPreset.template("old", "Old Bars", StudioPreset.BARS);
        assertEquals(StudioPreset.COLOR_TWO, existing.effectiveColorMode());
        StudioPreset copy = existing.copy();
        copy.setColorMode(StudioPreset.COLOR_TRIPLE_MOTION);
        copy.layerMotion = 100;
        copy.normalize();
        assertEquals(StudioPreset.COLOR_TRIPLE_MOTION, copy.effectiveColorMode());
        assertEquals(StudioPreset.COLOR_TWO, existing.effectiveColorMode());
        assertEquals(100, copy.layerMotion);
        existing.setColorMode(StudioPreset.COLOR_STACKED);
        assertEquals(StudioPreset.COLOR_STACKED, existing.effectiveColorMode());
    }
}
