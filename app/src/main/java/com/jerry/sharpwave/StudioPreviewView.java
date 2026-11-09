package com.jerry.sharpwave;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Preview uses generated demo motion. It does not capture or store the user's audio. */
final class StudioPreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private StudioPreset preset = StudioPreset.template("demo", "Demo", StudioPreset.BARS);
    private Bitmap photo;
    private String loadedUri = "";
    private boolean animated;
    private final MusicColorFlow demoColorFlow = new MusicColorFlow();

    StudioPreviewView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("PulseWave scene preview, generated demonstration");
    }

    void setScene(StudioPreset newPreset) {
        if (preset.type != newPreset.type || preset.effectiveColorMode()
                != newPreset.effectiveColorMode()) demoColorFlow.reset();
        preset = newPreset.copy();
        preset.normalize();
        if (!loadedUri.equals(preset.photoUri)) loadPhoto(preset.photoUri);
        invalidate();
    }

    void setAnimated(boolean enabled) {
        animated = enabled;
        invalidate();
    }

    private void loadPhoto(String uriString) {
        loadedUri = uriString;
        if (photo != null) photo.recycle();
        photo = null;
        if (uriString.isEmpty()) return;
        Uri uri = Uri.parse(uriString);
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
                if (in == null) return;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > 1200
                    || bounds.outHeight / options.inSampleSize > 1200) {
                options.inSampleSize *= 2;
            }
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
                if (in != null) photo = BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception ignored) {
            photo = null; // Permission revoked, missing content, or invalid image: show placeholder.
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        canvas.drawColor(Color.BLACK);
        float t = animated ? SystemClock.uptimeMillis() / 520f : 1.5f;
        switch (preset.type) {
            case StudioPreset.PHOTO: drawPhotoClock(canvas, w, h); break;
            case StudioPreset.RING: drawRing(canvas, w, h, t); break;
            case StudioPreset.SPIKES:
            case StudioPreset.WAVE:
            case StudioPreset.BARS:
            default:
                drawSharedAudioPreview(canvas, w, h, t);
                break;
        }
        if (animated && isShown() && isAttachedToWindow()) postInvalidateDelayed(45);
    }

    /**
     * Draw the identical audio geometry as the live overlay, with synthesized demo
     * amplitudes. The preview deliberately does NOT claim to be real audio capture.
     */
    private void drawSharedAudioPreview(Canvas canvas, int width, int height, float time) {
        int bandHeight = Math.max(1, Math.min(height, Math.round(dp(preset.height))));
        int placement = OverlayService.overlayPositionForScene(
                Prefs.overlayPosition(getContext()), preset);
        int y = placement == Prefs.POSITION_TOP ? 0
                : placement == Prefs.POSITION_BOTTOM ? height - bandHeight
                : (height - bandHeight) / 2;
        boolean barsDownward = preset.type == StudioPreset.BARS
                && placement == Prefs.POSITION_TOP;
        float widthPx = Math.max(1f, dp(preset.width));
        float gapPx = Math.max(0f, dp(preset.gap));
        int count = StudioAudioRenderer.barCount(width, widthPx, gapPx);
        if (count <= 0) return;
        float[] levels = new float[count];
        for (int i = 0; i < count; i++) {
            // Mirrored center-bass pattern; demo signal only, not microphone input.
            int distance = SharpVisualizerView.mirroredBandIndex(i, count);
            float bandEnvelope = 1f - 0.60f * distance / Math.max(1, count - 1);
            float beat = 0.15f + .65f * Math.abs((float) Math.sin(
                    time * .73f + distance * .22f))
                    * Math.abs((float) Math.cos(time * .47f - distance * .13f));
            float level = beat * bandEnvelope * preset.sensitivity / 160f;
            levels[i] = Math.min(1f, Math.max(0f, level));
        }
        int save = canvas.save();
        canvas.translate(0, y);
        int colorMode = preset.effectiveColorMode();
        float colorPhase = (colorMode == StudioPreset.COLOR_MUSIC_FLOW
                || colorMode == StudioPreset.COLOR_STACKED) && animated
                ? demoColorFlow.update(levels, SystemClock.uptimeMillis(),
                        preset.flowStrength) : 0f;
        StudioAudioRenderer.draw(canvas, paint, path, width, bandHeight,
                widthPx, gapPx, getResources().getDisplayMetrics().density,
                levels, preset.type, alphaColor(preset.primary),
                alphaColor(preset.accent), alphaColor(preset.third), colorMode, colorPhase,
                barsDownward, preset);
        canvas.restoreToCount(save);
    }

    private void drawRing(Canvas canvas, int w, int h, float t) {
        float x = w * .5f;
        float y = h * (preset.offsetY / 100f);
        float radius = Math.min(Math.min(w * .35f, h * .34f), dp(preset.ringSize) * .5f);
        float beat = animated ? (float) Math.sin(t * 1.7f) : .35f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(alphaColor(preset.accent));
        canvas.drawCircle(x, y, radius + beat * dp(4), paint);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(alphaColor(preset.primary));
        canvas.drawCircle(x, y, radius * .78f, paint);
        for (int i = 0; i < 36; i++) {
            double a = Math.PI * 2 * i / 36;
            float inside = radius * 1.10f;
            float outside = inside + dp(5) + dp(11)
                    * Math.abs((float) Math.sin(t + i * .32f));
            paint.setStrokeWidth(dp(2));
            canvas.drawLine(x + (float) Math.cos(a) * inside,
                    y + (float) Math.sin(a) * inside,
                    x + (float) Math.cos(a) * outside,
                    y + (float) Math.sin(a) * outside, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        drawCentered(canvas, "DECORATIVE SENSOR RING", x, Math.min(h - dp(12), y + radius + dp(35)),
                dp(9), 0xFFAAAAAA);
    }

    private void drawPhotoClock(Canvas canvas, int w, int h) {
        RectF frame = new RectF(w * .18f, h * .11f, w * .82f, h * .84f);
        if (photo != null && !photo.isRecycled()) {
            Rect src = centerCrop(photo.getWidth(), photo.getHeight(),
                    frame.width() / frame.height());
            canvas.save();
            path.reset();
            path.addRoundRect(frame, dp(22), dp(22), Path.Direction.CW);
            canvas.clipPath(path);
            paint.setColor(Color.WHITE);
            paint.setAlpha(StudioPreset.clamp(preset.opacity, 15, 100) * 255 / 100);
            canvas.drawBitmap(photo, src, frame, paint);
            paint.setAlpha(255);
            canvas.restore();
        } else {
            paint.setColor(0xFF202024);
            canvas.drawRoundRect(frame, dp(22), dp(22), paint);
            drawCentered(canvas, "CHOOSE YOUR PHOTO", w / 2f, h * .46f,
                    dp(10), 0xFFAAAAAA);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x95000000);
        canvas.drawRect(0, h * .70f, w, h, paint);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        drawCentered(canvas, time, w / 2f, h * .83f, dp(34), alphaColor(preset.primary));
        drawCentered(canvas, "PHOTO AOD CONCEPT · PREVIEW", w / 2f, h * .95f,
                dp(9), 0xFFBBBBBB);
    }

    private static Rect centerCrop(int sw, int sh, float targetRatio) {
        if ((float) sw / sh > targetRatio) {
            int cw = Math.max(1, Math.round(sh * targetRatio));
            return new Rect((sw - cw) / 2, 0, (sw + cw) / 2, sh);
        }
        int ch = Math.max(1, Math.round(sw / targetRatio));
        return new Rect(0, (sh - ch) / 2, sw, (sh + ch) / 2);
    }

    private void drawCentered(Canvas canvas, String text, float x, float baseline,
            float size, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size);
        paint.setColor(color);
        canvas.drawText(text, x, baseline, paint);
    }

    private int alphaColor(int color) {
        int a = StudioPreset.clamp(preset.opacity, 15, 100) * 255 / 100;
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animated = false;
        if (photo != null) photo.recycle();
        photo = null;
        loadedUri = "";
    }
}
