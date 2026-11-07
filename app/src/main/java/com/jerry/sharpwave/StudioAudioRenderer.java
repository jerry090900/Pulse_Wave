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
 * and on-screen position. The editor supplies simulated levels to this
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

    /** Top-anchored old bars grow down; WAVE preserves its original geometry. */
    static float barTopY(float height, float level, boolean downward) {
        float size = Math.max(0f, Math.min(1f, level)) * height;
        return downward ? 0f : height - size;
    }

    static float barBottomY(float height, float level, boolean downward) {
        float size = Math.max(0f, Math.min(1f, level)) * height;
        return downward ? size : height;
    }

    /**
     * Three AUDIO-DRIVEN lengths at the same x coordinate, based solely on the
     * existing smoothed spectrum. No new capture, timers or smoothing are added.
     *
     * Offset sampling lets the three colors respond differently without altering
     * the original FFT-to-bar response or primary level. On narrow displays,
     * clamping keeps each sample inside the spectrum.
     */
    static int overlaidSampleIndex(int index, int count, int layer) {
        if (count <= 1) return 0;
        int center = Math.max(0, Math.min(count - 1, index));
        int offset = Math.max(2, count / 12);
        if (layer == 1) return Math.min(count - 1, center + offset);
        if (layer == 2) return Math.max(0, center - offset);
        return center;
    }

    static float overlaidLevel(float[] smoothedLevels, int index, int layer) {
        if (smoothedLevels == null || smoothedLevels.length == 0) return 0f;
        float level = smoothedLevels[
                overlaidSampleIndex(index, smoothedLevels.length, layer)];
        return Math.max(0f, Math.min(1f, level));
    }

    /** Original three swatches stay exact in static mode; music flow shifts hues. */
    static int overlaidColor(int layer, int primary, int accent, int third,
            int colorMode, float phase) {
        if (colorMode == StudioPreset.COLOR_MUSIC_FLOW) {
            return MusicColorFlow.flowingColorAt(
                    layer / 3f, phase, primary, accent, third);
        }
        return layer == 0 ? primary : layer == 1 ? accent : third;
    }

    /** Back-to-front: draw longer layers first so every shorter layer stays visible. */
    static void layerDrawOrder(float[] heights, int[] order) {
        if (heights == null || order == null || heights.length != 3
                || order.length != 3) {
            throw new IllegalArgumentException("Exactly three overlaid lines required");
        }
        order[0] = 0;
        order[1] = 1;
        order[2] = 2;
        for (int i = 0; i < 2; i++) {
            for (int j = i + 1; j < 3; j++) {
                if (heights[order[i]] < heights[order[j]]) {
                    int previous = order[i];
                    order[i] = order[j];
                    order[j] = previous;
                }
            }
        }
    }

    /**
     * One shared renderer: static 1/2/3 colors and FFT-driven three-color flow.
     * The optional direction changes only Bars, never FFT processing or Wave.
     */
    static void draw(Canvas canvas, Paint paint, Path path, int width, int height,
            float barWidthPx, float gapPx, float density, float[] levels, int mode,
            int primary, int accent, int third, int colorMode, float flowPhase) {
        draw(canvas, paint, path, width, height, barWidthPx, gapPx, density,
                levels, mode, primary, accent, third, colorMode, flowPhase, false);
    }

    static void draw(Canvas canvas, Paint paint, Path path, int width, int height,
            float barWidthPx, float gapPx, float density, float[] levels, int mode,
            int primary, int accent, int third, int colorMode, float flowPhase,
            boolean barsDownward) {
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
        // Reuse these buffers for all bars; never allocate per FFT band.
        float[] overlaidHeights = mode == StudioPreset.BARS && multiColor
                ? new float[3] : null;
        int[] overlaidOrder = overlaidHeights != null ? new int[3] : null;
        int[] overlaidColors = overlaidHeights != null ? new int[] {
                overlaidColor(0, primary, accent, third, colorMode, flowPhase),
                overlaidColor(1, primary, accent, third, colorMode, flowPhase),
                overlaidColor(2, primary, accent, third, colorMode, flowPhase)
        } : null;
        path.reset();
        for (int i = 0; i < count; i++) {
            float level = Math.max(0f, Math.min(1f, levels[i]));
            float left = i * step;
            float right = i == count - 1 ? width : left + barWidth;
            if (right <= left) continue;
            float barHeight = level * height;
            boolean down = barsDownward && mode == StudioPreset.BARS;
            float top = barTopY(height, level, down);
            float bottom = barBottomY(height, level, down);
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
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
                int saved = canvas.save();
                canvas.clipRect(left, top, right, top + seam);
                paint.setColor(primary);
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
                canvas.restoreToCount(saved);
                paint.setAntiAlias(antialias);
            } else if (overlaidHeights != null) {
                // Three DIFFERENT line lengths occupy the exact same bar slot,
                // each from an adjacent smoothed FFT frequency. The longer color
                // forms the tip; shorter colors cover only their own segments.
                // This is not a gradient and doesn't change response calculations.
                for (int layer = 0; layer < 3; layer++) {
                    overlaidHeights[layer] =
                            overlaidLevel(levels, i, layer) * height;
                }
                layerDrawOrder(overlaidHeights, overlaidOrder);
                boolean antialias = paint.isAntiAlias();
                paint.setAntiAlias(true);
                paint.setShader(null);
                for (int layerIndex = 0; layerIndex < 3; layerIndex++) {
                    int layer = overlaidOrder[layerIndex];
                    float length = overlaidHeights[layer];
                    if (length <= 0f) continue;
                    float layerTop = down ? 0f : height - length;
                    float layerBottom = down ? length : height;
                    float radius = Math.min((right - left) * 0.45f, length * 0.5f);
                    paint.setColor(overlaidColors[layer]);
                    canvas.drawRoundRect(left, layerTop, right, layerBottom,
                            radius, radius, paint);
                }
                paint.setAntiAlias(antialias);
            } else {
                paint.setColor(lineColor);
                canvas.drawRect(left, top, right, bottom, paint);
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
