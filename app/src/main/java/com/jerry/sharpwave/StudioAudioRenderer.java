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
        return waveY(height, level, null);
    }

    static float waveY(float height, float level, StudioPreset style) {
        float v = Math.max(0f, Math.min(1f, level));
        float baseline = style == null ? .82f
                : StudioPreset.clamp(style.waveBaseline, 10, 95) / 100f;
        float amplitude = .72f * (style == null ? 1f
                : StudioPreset.clamp(style.waveAmplitude, 10, 180) / 100f);
        return Math.max(1f, Math.min(height, height * (baseline - v * amplitude)));
    }

    static float waveStrokeWidth(float density, float barWidth, StudioPreset style) {
        if (style != null && style.waveStroke > 0) {
            return Math.max(1f, density * StudioPreset.clamp(style.waveStroke, 1, 18));
        }
        return Math.max(2f * density, barWidth * .5f);
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

    /**
     * Line identities never switch: left is A, middle B, right C.
     * Music flow only brightens each line very gently; it does not remap hues.
     */
    static int overlaidColor(int layer, int primary, int accent, int third,
            int colorMode, float phase) {
        int base = layer == 0 ? primary : layer == 1 ? accent : third;
        if (colorMode != StudioPreset.COLOR_MUSIC_FLOW) return base;
        float pulse = Math.max(0f, (float) Math.sin(
                2f * Math.PI * MusicColorFlow.wrap(phase + layer / 3f)));
        return MusicColorFlow.blend(base, 0xFFFFFFFF, .12f * pulse);
    }

    /** Internal gap between three neighboring lines within the original bar slot. */
    static float innerGap(float barWidth, float requestedGap) {
        return Math.max(0f, Math.min(Math.max(0f, requestedGap),
                Math.max(0f, barWidth) * .18f));
    }

    static float channelWidth(float barWidth, float innerGap) {
        float w = Math.max(0f, barWidth);
        return Math.max(0f, (w - 2f * innerGap) / 3f);
    }

    /** Each colored channel has its own non-overlapping horizontal slot. */
    static float channelLeft(float groupLeft, float barWidth,
            int layer, float innerGap) {
        int channel = Math.max(0, Math.min(2, layer));
        return groupLeft + channel * (channelWidth(barWidth, innerGap) + innerGap);
    }

    /**
     * Two adjustable section boundaries within ONE bar. Nearby audio bands and
     * the existing music-color phase vary section length, not the audio itself.
     * A, B and C are always visible in order when a sufficiently tall bar rises.
     */
    static void layeredFractions(float base, float right, float left, float phase,
            int aPercent, int bPercent, int movement, float[] out) {
        if (out == null || out.length < 2) {
            throw new IllegalArgumentException("Two output boundaries required");
        }
        float motion = StudioPreset.clamp(movement, 0, 100) / 100f;
        float pulse = (float) Math.sin(
                2.0 * Math.PI * MusicColorFlow.wrap(phase));
        float a = StudioPreset.clamp(aPercent, 5, 70) / 100f
                + motion * (.42f * (right - base) + .13f * pulse);
        float b = StudioPreset.clamp(bPercent, aPercent + 5, 95) / 100f
                + motion * (.33f * (left - base) - .09f * pulse);
        out[0] = Math.max(.07f, Math.min(.83f, a));
        out[1] = Math.max(out[0] + .08f, Math.min(.96f, b));
    }

    /**
     * Shared renderer: original legacy, 3 side-by-side channels, and Muviz-like
     * stacked A/B/C segments are separate modes. FFT/music sync unchanged.
     */
    static void draw(Canvas canvas, Paint paint, Path path, int width, int height,
            float barWidthPx, float gapPx, float density, float[] levels, int mode,
            int primary, int accent, int third, int colorMode, float flowPhase) {
        draw(canvas, paint, path, width, height, barWidthPx, gapPx, density,
                levels, mode, primary, accent, third, colorMode, flowPhase, false, null);
    }

    static void draw(Canvas canvas, Paint paint, Path path, int width, int height,
            float barWidthPx, float gapPx, float density, float[] levels, int mode,
            int primary, int accent, int third, int colorMode, float flowPhase,
            boolean barsDownward) {
        draw(canvas, paint, path, width, height, barWidthPx, gapPx, density,
                levels, mode, primary, accent, third, colorMode, flowPhase,
                barsDownward, null);
    }

    static void draw(Canvas canvas, Paint paint, Path path, int width, int height,
            float barWidthPx, float gapPx, float density, float[] levels, int mode,
            int primary, int accent, int third, int colorMode, float flowPhase,
            boolean barsDownward, StudioPreset style) {
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
                || colorMode == StudioPreset.COLOR_MUSIC_FLOW
                || colorMode == StudioPreset.COLOR_STACKED
                || colorMode == StudioPreset.COLOR_TRIPLE_MOTION;
        boolean stackedColors = mode == StudioPreset.BARS
                && colorMode == StudioPreset.COLOR_STACKED;
        float[] stackBreaks = stackedColors ? new float[2] : null;
        boolean tripleMotion = mode == StudioPreset.BARS
                && colorMode == StudioPreset.COLOR_TRIPLE_MOTION;
        // Reused by all bar groups: no per-bar object allocations.
        float[] tripleReaches = tripleMotion ? new float[3] : null;
        // Reuse one color array and shared per-group geometry. Every channel
        // occupies its own x slot so A/B/C can all be visible independently.
        boolean threeChannels = mode == StudioPreset.BARS && multiColor
                && !stackedColors && !tripleMotion;
        int[] overlaidColors = threeChannels ? new int[] {
                overlaidColor(0, primary, accent, third, colorMode, flowPhase),
                overlaidColor(1, primary, accent, third, colorMode, flowPhase),
                overlaidColor(2, primary, accent, third, colorMode, flowPhase)
        } : null;
        float innerSpacing = threeChannels ? innerGap(barWidth,
                density * (style == null ? 1 : style.lineSpacing)) : 0f;
        float singleWidth = threeChannels ? channelWidth(barWidth, innerSpacing) : 0f;
        float previousWaveX = 0f;
        float previousWaveY = 0f;
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
                float currentY = waveY(height, level, style);
                int smooth = style == null ? 0 : style.waveSmoothness;
                if (i == 0) {
                    path.moveTo(center, currentY);
                } else if (smooth <= 0) {
                    path.lineTo(center, currentY);
                } else {
                    float midX = (previousWaveX + center) * .5f;
                    float midY = (previousWaveY + currentY) * .5f;
                    float t = StudioPreset.clamp(smooth, 0, 100) / 100f;
                    path.quadTo(midX + (previousWaveX - midX) * t,
                            midY + (previousWaveY - midY) * t, midX, midY);
                    if (i == count - 1) path.lineTo(center, currentY);
                }
                previousWaveX = center;
                previousWaveY = currentY;
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
            } else if (tripleMotion) {
                // Reference: three audio-driven colors share ONE exact bar slot.
                // Unlike COLOR_STACKED, any color can nearly disappear, leaving
                // single-color and two-color moments as the music changes.
                if (barHeight <= 0f) continue;
                TripleLayerMotion.reaches(levels, i, flowPhase,
                        style == null ? 100 : style.layerMotion, tripleReaches);
                boolean oldAntialias = paint.isAntiAlias();
                paint.setAntiAlias(true);
                paint.setShader(null);
                // Draw C behind B behind A: A near the shared top edge, then B,
                // then C at the tip when their independently moving heights allow.
                // Each layer is precisely the same x coordinate / bar width.
                for (int layer = 2; layer >= 0; layer--) {
                    float reach = tripleReaches[layer] * height;
                    if (reach <= .001f) continue;
                    float layerTop = down ? 0f : height - reach;
                    float layerBottom = down ? reach : height;
                    float radius = Math.min((right - left) * .50f, reach * .50f);
                    paint.setColor(layer == 0 ? primary : layer == 1 ? accent : third);
                    canvas.drawRoundRect(left, layerTop, right, layerBottom,
                            radius, radius, paint);
                }
                paint.setAntiAlias(oldAntialias);
            } else if (stackedColors) {
                // Muviz video: cyan, white and pink occupy the SAME bar width.
                // Independently changing split lengths reveal all colors from
                // the top toward the tip, without repeating a full gradient.
                if (barHeight <= 0f) continue;
                float nearB = overlaidLevel(levels, i, 1);
                float nearC = overlaidLevel(levels, i, 2);
                layeredFractions(level, nearB, nearC, flowPhase,
                        style == null ? 33 : style.layerASplit,
                        style == null ? 68 : style.layerBSplit,
                        style == null ? 85 : style.layerMotion, stackBreaks);
                float radius = Math.min((right - left) * .5f, barHeight * .5f);
                boolean antialias = paint.isAntiAlias();
                paint.setAntiAlias(true);
                paint.setShader(null);
                // Paint C as the full silhouette, then B and A on top clipped
                // to their dynamic lengths. Outer rounded silhouette is shared.
                paint.setColor(third);
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
                int saveB = canvas.save();
                if (down) {
                    canvas.clipRect(left, top, right,
                            top + barHeight * stackBreaks[1]);
                } else {
                    canvas.clipRect(left, bottom - barHeight * stackBreaks[1],
                            right, bottom);
                }
                paint.setColor(accent);
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
                canvas.restoreToCount(saveB);
                int saveA = canvas.save();
                if (down) {
                    canvas.clipRect(left, top, right,
                            top + barHeight * stackBreaks[0]);
                } else {
                    canvas.clipRect(left, bottom - barHeight * stackBreaks[0],
                            right, bottom);
                }
                paint.setColor(primary);
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
                canvas.restoreToCount(saveA);
                paint.setAntiAlias(antialias);
            } else if (threeChannels) {
                // A, B, C are three NARROW lines, next to each other within
                // the original bar-width slot. No gradient and no occlusion.
                // Their lengths sample the same existing smoothed FFT array.
                boolean antialias = paint.isAntiAlias();
                paint.setAntiAlias(true);
                paint.setShader(null);
                for (int layer = 0; layer < 3; layer++) {
                    float length = overlaidLevel(levels, i, layer) * height;
                    if (length <= 0f) continue;
                    float channelX = channelLeft(left, right - left, layer, innerSpacing);
                    float channelEnd = channelX + singleWidth;
                    float channelTop = down ? 0f : height - length;
                    float channelBottom = down ? length : height;
                    float radius = Math.min(singleWidth * .48f, length * .5f);
                    paint.setColor(overlaidColors[layer]);
                    canvas.drawRoundRect(channelX, channelTop, channelEnd,
                            channelBottom, radius, radius, paint);
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
            float stroke = waveStrokeWidth(density, barWidthPx, style);
            paint.setStrokeWidth(stroke);
            if (multiColor) {
                int[] stops = new int[7];
                for (int i = 0; i < stops.length; i++) {
                    stops[i] = colorForLine(i / (float) (stops.length - 1),
                            primary, accent, third, colorMode, flowPhase);
                }
                paint.setShader(new LinearGradient(0f, 0f, width, 0f,
                        stops, null, Shader.TileMode.CLAMP));
            }
            int opacity = style == null ? 100
                    : StudioPreset.clamp(style.waveOpacity, 10, 100);
            paint.setColor(twoTone ? accent : primary);
            int glow = style == null ? 0 : StudioPreset.clamp(style.waveGlow, 0, 100);
            if (glow > 0) {
                paint.setStrokeWidth(stroke * (1f + glow / 17f));
                paint.setAlpha(Math.max(1, Math.round(255f * opacity / 100f
                        * glow / 100f * .25f)));
                canvas.drawPath(path, paint);
                paint.setStrokeWidth(stroke);
            }
            paint.setAlpha(Math.round(255f * opacity / 100f));
            canvas.drawPath(path, paint);
            paint.setShader(null);
            if (twoTone) {
                paint.setStrokeWidth(1.5f * density);
                paint.setColor(primary);
                paint.setAlpha(Math.round(255f * opacity / 100f));
                canvas.drawPath(path, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
            paint.setAntiAlias(antialias);
        }
    }
}
