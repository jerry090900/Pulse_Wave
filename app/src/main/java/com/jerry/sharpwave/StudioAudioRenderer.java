package com.jerry.sharpwave;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/**
 * Shared drawing stage for Studio audio styles.
 *
 * The live overlay still owns its existing FFT, mapping, smoothing, capture timing,
 * and centered on-screen position. The editor supplies simulated levels to this
 * exact same geometry and paint code; it never pretends to capture music.
 */
final class StudioAudioRenderer {
    private StudioAudioRenderer() {}

    static int barCount(float screenWidth, float widthPx, float gapPx) {
        if (screenWidth <= 0f) return 0;
        float bw = Math.max(1f, widthPx);
        float gap = Math.max(0f, gapPx);
        int count = Math.max(1, (int) Math.ceil((screenWidth + gap) / (bw + gap)));
        if (gap > 0f) {
            count = Math.min(count, Math.max(1, (int) Math.floor((screenWidth + gap) / gap)));
        }
        return count;
    }

    /** A saved Studio preset owns its own color mode; old home controls remain for legacy style. */
    static boolean useTwoColors(boolean studioSelected, boolean presetTwoTone,
            boolean legacySolidBars) {
        return studioSelected ? presetTwoTone : !legacySolidBars;
    }

    static float waveY(float height, float level) {
        float v = Math.max(0f, Math.min(1f, level));
        return Math.max(1f, height * (0.82f - v * 0.72f));
    }

    /** The original two-color API remains for old styles and regression tests. */
    static void draw(Canvas canvas, Paint paint, Path path, int width, int height,
            float barWidthPx, float gapPx, float density, float[] levels, int mode,
            int primary, int accent, boolean twoTone) {
        draw(canvas, paint, path, width, height, barWidthPx, gapPx, density,
                levels, mode, primary, accent, 0xFF19CFFA,
                twoTone ? StudioPreset.COLOR_TWO : StudioPreset.COLOR_SOLID, 0f);
    }

    static int colorForLine(float position, int first, int second, int third,
            int colorMode, float phase) {
        if (colorMode == StudioPreset.COLOR_MUSIC_FLOW) {
            return MusicColorFlow.flowingColorAt(position, phase, first, second, third);
        }
        return MusicColorFlow.threeColorAt(position, first, second, third);
    }

    /**
     * One shared renderer: static 1/2/3 colors and FFT-driven three-color flow.
     * Moving colors never changes line shape, audio data, update cadence or placement.
     */
    static void draw(Canvas canvas, Paint paint, Path path, int width, int height,
            float barWidthPx, float gapPx, float density, float[] levels, int mode,
            int primary, int accent, int third, int colorMode, float flowPhase) {
        if (width <= 0 || height <= 0 || levels == null || levels.length == 0) return;
        int count = barCount(width, barWidthPx, gapPx);
        if (count != levels.length) {
            throw new IllegalArgumentException("Each rendered band needs an amplitude");
        }
        float gap = Math.max(0f, gapPx);
        float step = (width + gap) / count;
        float barWidth = Math.max(1f, step - gap);
        boolean twoTone = colorMode == StudioPreset.COLOR_TWO;
        boolean multiColor = colorMode == StudioPreset.COLOR_THREE
                || colorMode == StudioPreset.COLOR_MUSIC_FLOW;
        path.reset();
        for (int i = 0; i < count; i++) {
            float level = Math.max(0f, Math.min(1f, levels[i]));
            float left = i * step;
            float right = i == count - 1 ? width : left + barWidth;
            if (right <= left) continue;
            float barHeight = level * height;
            float top = height - barHeight;
            float position = count <= 1 ? .5f : i / (float) (count - 1);
            int lineColor = multiColor
                    ? colorForLine(position, primary, accent, third, colorMode, flowPhase)
                    : primary;

            if (mode == StudioPreset.WAVE) {
                float center = (left + right) * 0.5f;
                if (i == 0) path.moveTo(center, waveY(height, level));
                else path.lineTo(center, waveY(height, level));
            } else if (mode == StudioPreset.SPIKES) {
                if (barHeight <= 0f) continue;
                path.reset();
                path.moveTo(left, height);
                path.lineTo((left + right) * 0.5f, top);
                path.lineTo(right, height);
                path.close();
                paint.setColor(multiColor ? lineColor : twoTone ? accent : primary);
                canvas.drawPath(path, paint);
                if (twoTone) {
                    float seam = top + barHeight * (.40f + .20f * (1f - level));
                    int saved = canvas.save();
                    canvas.clipRect(left, top, right, seam);
                    paint.setColor(primary);
                    canvas.drawPath(path, paint);
                    canvas.restoreToCount(saved);
                }
            } else if (twoTone) {
                if (barHeight <= 0f) continue;
                float radius = Math.min((right - left) * .5f, barHeight * .5f);
                float redFraction = .35f + (.70f - .35f) * level;
                float minimum = Math.min(1f, barHeight * .5f);
                float redHeight = Math.max(minimum, Math.min(barHeight - minimum,
                        barHeight * redFraction));
                float seam = barHeight - redHeight;
                boolean antialias = paint.isAntiAlias();
                paint.setAntiAlias(true);
                paint.setColor(accent);
                canvas.drawRoundRect(left, top, right, height, radius, radius, paint);
                int saved = canvas.save();
                canvas.clipRect(left, top, right, top + seam);
                paint.setColor(primary);
                canvas.drawRoundRect(left, top, right, height, radius, radius, paint);
                canvas.restoreToCount(saved);
                paint.setAntiAlias(antialias);
            } else {
                paint.setColor(lineColor);
                canvas.drawRect(left, top, right, height, paint);
            }
        }

        if (mode == StudioPreset.WAVE) {
            // Keep the same FFT-driven wave geometry, stroke width and dual stroke from
            // the verified new-video live overlay. Only demo input differs in Studio.
            boolean antialias = paint.isAntiAlias();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(2f * density, barWidthPx * .5f));
            if (multiColor) {
                int[] stops = new int[7];
                for (int i = 0; i < stops.length; i++) {
                    stops[i] = colorForLine(i / (float) (stops.length - 1),
                            primary, accent, third, colorMode, flowPhase);
                }
                paint.setShader(new LinearGradient(0f, 0f, width, 0f,
                        stops, null, Shader.TileMode.CLAMP));
            }
            paint.setColor(twoTone ? accent : primary);
            canvas.drawPath(path, paint);
            paint.setShader(null);
            if (twoTone) {
                paint.setStrokeWidth(1.5f * density);
                paint.setColor(primary);
                canvas.drawPath(path, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setAntiAlias(antialias);
        }
    }
}
