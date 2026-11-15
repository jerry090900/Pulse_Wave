package com.jerry.sharpwave;

/**
 * Three genuinely independent color reaches inside the SAME bar slot.
 *
 * The reference video shows cyan-only and pink-only moments, as well as bars
 * with all three segments. Earlier stacked-bars code forced all three segments
 * to remain visible; this helper permits a layer to almost disappear.
 *
 * Render-only math: reads existing smoothed FFT levels and beat-driven color
 * phase without changing any audio callback, capture timing, FFT mapping,
 * smoothing, or visualizer geometry. Pure Java for deterministic unit tests.
 */
final class TripleLayerMotion {
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    private TripleLayerMotion() {}

    static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * @param levels existing smoothed bar levels; never modified
     * @param barIndex horizontal bar position
     * @param phase already computed MusicColorFlow phase [0, 1)
     * @param movement 0 preserves a static three-section layout; 100 enables
     *                 full independent length changes / single-color moments
     * @param reaches output array [A = cyan, B = white, C = pink] in [0, 1]
     *                expressed as a fraction of FULL overlay height.
     *
     * The maximum reach is always the original bar amplitude. Thus the
     * equalizer silhouette and top-edge music reaction remain unchanged.
     */
    static void reaches(float[] levels, int barIndex, float phase,
            int movement, float[] reaches) {
        if (reaches == null || reaches.length < 3) {
            throw new IllegalArgumentException("Three color outputs required");
        }
        if (levels == null || levels.length == 0) {
            reaches[0] = reaches[1] = reaches[2] = 0f;
            return;
        }
        int i = Math.max(0, Math.min(levels.length - 1, barIndex));
        float base = clamp01(levels[i]);
        if (base <= 0f) {
            reaches[0] = reaches[1] = reaches[2] = 0f;
            return;
        }
        int offset = Math.max(1, levels.length / 36);
        float left = clamp01(levels[Math.max(0, i - offset)]);
        float right = clamp01(levels[Math.min(levels.length - 1, i + offset)]);

        // A gentle spatial shift means neighboring bars do not change colors
        // in lockstep, but do remain visually coherent as a single spectrum.
        float position = (i + .5f) / levels.length;
        float wheel = MusicColorFlow.wrap(phase
                + (position - .5f) * .26f
                + (right - left) * .11f);

        // Three phase-offset color layers. Each layer independently grows or
        // shrinks as color phase advances with existing music energy/onsets.
        // The squared cosine creates brief single-color moments seen in Muviz.
        float a = reachWeight(wheel, 0, base, left);
        float b = reachWeight(wheel, 1, base, (left + right) * .5f);
        float c = reachWeight(wheel, 2, base, right);
        float peak = Math.max(.00001f, Math.max(a, Math.max(b, c)));
        float motion = StudioPreset.clamp(movement, 0, 100) / 100f;
        float still = 1f - motion;
        reaches[0] = clamp01(base * (still * .33f + motion * a / peak));
        reaches[1] = clamp01(base * (still * .68f + motion * b / peak));
        reaches[2] = clamp01(base * (still * 1f + motion * c / peak));
    }

    private static float reachWeight(float wheel, int layer,
            float base, float neighbor) {
        float angle = TWO_PI * MusicColorFlow.wrap(wheel - layer / 3f);
        float raisedCosine = .5f + .5f * (float) Math.cos(angle);
        float independentFrequency = .82f + .18f * clamp01(
                base * .65f + neighbor * .35f);
        return raisedCosine * raisedCosine * independentFrequency;
    }
}
