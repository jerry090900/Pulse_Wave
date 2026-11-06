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

    StudioPreviewView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("PulseWave scene preview, generated demonstration");
    }

    void setScene(StudioPreset newPreset) {
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
            case StudioPreset.SPIKES: drawBars(canvas, w, h, t, true); break;
            case StudioPreset.WAVE: drawWave(canvas, w, h, t); break;
            default: drawBars(canvas, w, h, t, false);
        }
        if (animated && isShown() && isAttachedToWindow()) postInvalidateDelayed(45);
    }

    private void drawBars(Canvas canvas, int w, int h, float t, boolean spikes) {
        float bar = Math.max(dp(2), dp(preset.width));
        float gap = dp(preset.gap);
        int count = Math.max(1, Math.min(190, (int) Math.ceil((w + gap) / (bar + gap))));
        float step = (w + gap) / count;
        bar = Math.max(1f, step - gap);
        float baseline = h * .68f;
        float maxHeight = Math.min(h * .57f, dp(preset.height));
        for (int i = 0; i < count; i++) {
            float envelope = .10f + .90f * Math.abs((float) Math.sin(i * .29f + t * .7f)
                    * (float) Math.cos(i * .13f - t * .4f));
            float level = StudioPreset.clamp(preset.sensitivity, 60, 260) / 160f;
            float bh = Math.max(dp(2), Math.min(maxHeight, maxHeight * envelope * level));
            float x = i * step;
            float right = Math.min(w, x + bar);
            float top = baseline - bh;
            if (spikes) {
                path.reset();
                path.moveTo(x, baseline);
                path.lineTo((x + right) / 2f, top);
                path.lineTo(right, baseline);
                path.close();
                paint.setColor(alphaColor(preset.accent));
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(path, paint);
                canvas.save();
                canvas.clipRect(x, top, right, top + bh * .48f);
                paint.setColor(alphaColor(preset.primary));
                canvas.drawPath(path, paint);
                canvas.restore();
            } else {
                float seam = top + bh * .48f;
                paint.setColor(alphaColor(preset.primary));
                canvas.drawRect(x, top, right, seam, paint);
                paint.setColor(alphaColor(preset.accent));
                canvas.drawRect(x, seam, right, baseline, paint);
            }
        }
    }

    private void drawWave(Canvas canvas, int w, int h, float t) {
        path.reset();
        float mid = h * .50f;
        float amp = Math.min(h * .37f, dp(preset.height) * .65f)
                * preset.sensitivity / 160f;
        for (int x = 0; x <= w; x += 3) {
            float y = mid + (float) Math.sin(x * .039f + t) * amp
                    * (float) Math.sin(x * .009f + t * .45f);
            if (x == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(Math.max(2, preset.width / 2)));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(alphaColor(preset.accent));
        canvas.drawPath(path, paint);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(alphaColor(preset.primary));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
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
