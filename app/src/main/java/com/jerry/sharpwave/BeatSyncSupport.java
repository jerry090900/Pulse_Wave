package com.jerry.sharpwave;

/**
 * Optional paint-only beat emphasis, driven by the EXISTING smoothed FFT values.
 * It never modifies captured audio, band mapping, callback scheduling or the
 * original levels. With enhancement off, the original array is returned exactly.
 */
final class BeatSyncSupport {
    static final int SMOOTH = 0;
    static final int BALANCED = 1;
    static final int PUNCHY = 2;

    private float baselineBass;
    private float beatPulse;
    private long lastFrameMs;
    private float[] visualLevels = new float[0];

    void reset() {
        baselineBass = 0f;
        beatPulse = 0f;
        lastFrameMs = 0L;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Bass is in the center when PulseWave's "Center the bass" is enabled.
     * Otherwise the original frequency mapping begins at the left edge.
     */
    static float bassEnergy(float[] levels, boolean centerBass) {
        if (levels == null || levels.length == 0) return 0f;
        int count = levels.length;
        float total = 0f;
        int samples = 0;
        for (int i = 0; i < count; i++) {
            boolean bassBand = centerBass
                    ? Math.abs(i - (count - 1) * .5f) <= Math.max(1f, count * .12f)
                    : i <= Math.max(1, count / 4);
            if (!bassBand) continue;
            total += clamp(levels[i], 0f, 1f);
            samples++;
        }
        return samples == 0 ? 0f : total / samples;
    }

    float[] enhance(float[] levels, long nowMs, boolean enabled,
            boolean adaptive, int profile, boolean centerBass) {
        if (!enabled || levels == null || levels.length == 0) {
            reset();
            return levels;
        }
        if (visualLevels.length != levels.length) {
            visualLevels = new float[levels.length];
        }
        float bass = bassEnergy(levels, centerBass);
        float average = 0f;
        for (float v : levels) average += clamp(v, 0f, 1f);
        average /= levels.length;

        int mode = Math.max(SMOOTH, Math.min(PUNCHY, profile));
        float boost = mode == SMOOTH ? .13f : mode == BALANCED ? .24f : .40f;
        float releaseMs = mode == SMOOTH ? 220f : mode == BALANCED ? 140f : 85f;
        if (lastFrameMs == 0L || nowMs < lastFrameMs) {
            baselineBass = bass;
            beatPulse = 0f;
        } else {
            float elapsed = Math.max(1f, Math.min(100f, nowMs - lastFrameMs));
            float onset = Math.max(0f, bass - baselineBass);
            beatPulse = Math.max(beatPulse * (float) Math.exp(-elapsed / releaseMs),
                    onset * 2.5f);
            // A slow running baseline tracks song level, not individual kicks.
            baselineBass += (bass - baselineBass) * Math.min(1f, elapsed / 380f);
        }
        lastFrameMs = nowMs;

        float gain = adaptive
                ? clamp(.53f / Math.max(.22f, average + .12f), .8f, 1.45f) : 1f;
        for (int i = 0; i < levels.length; i++) {
            float v = clamp(levels[i], 0f, 1f);
            float distance = centerBass
                    ? Math.abs(i - (levels.length - 1) * .5f)
                            / Math.max(1f, (levels.length - 1) * .5f)
                    : i / (float) Math.max(1, levels.length - 1);
            float bassWeight = 1f - clamp(distance, 0f, 1f);
            visualLevels[i] = clamp(v * gain + beatPulse * boost * bassWeight, 0f, 1f);
        }
        return visualLevels;
    }
}
