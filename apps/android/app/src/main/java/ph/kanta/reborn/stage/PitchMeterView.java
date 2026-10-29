package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import ph.kanta.reborn.core.PitchData;

/**
 * Custom view for displaying real-time pitch feedback as a visual meter.
 * Used by the Pitch Meter Game feature.
 */
public class PitchMeterView extends View {

    private static final int METER_WIDTH = 600;
    private static final int METER_HEIGHT = 120;
    private static final int TARGET_ZONE_WIDTH = 80;
    private static final int INDICATOR_RADIUS = 12;

    private Paint backgroundPaint;
    private Paint targetZonePaint;
    private Paint indicatorPaint;
    private Paint textPaint;
    private Paint accuracyPaint;
    private Paint trailPaint;
    private Path indicatorPath;
    private RectF targetRect;

    private PitchData currentPitchData;
    private float targetPitchHz = 440f;
    private float accuracy = 0f;
    private int score = 0;
    private int streak = 0;
    private int maxStreak = 0;

    // Animation state
    private float animatedAccuracy = 0f;
    private long lastAnimationTime = 0;
    private boolean showSuccess = false;
    private long successStartTime = 0;

    public PitchMeterView(Context context) {
        this(context, null);
    }

    public PitchMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PitchMeterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.parseColor("#1A1A2E"));
        backgroundPaint.setStyle(Paint.Style.FILL);

        targetZonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetZonePaint.setColor(Color.parseColor("#00E676"));
        targetZonePaint.setStyle(Paint.Style.FILL);
        targetZonePaint.setAlpha(80);

        indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint.setColor(Color.parseColor("#FFFFFF"));
        indicatorPaint.setStyle(Paint.Style.FILL);
        indicatorPaint.setShadowLayer(8, 0, 0, Color.parseColor("#00E676"));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28);
        textPaint.setTextAlign(Paint.Align.CENTER);

        accuracyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        accuracyPaint.setColor(Color.parseColor("#FFD600"));
        accuracyPaint.setTextSize(20);
        accuracyPaint.setTextAlign(Paint.Align.CENTER);

        trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trailPaint.setColor(Color.parseColor("#FFD600"));
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeWidth(3);
        trailPaint.setAlpha(100);

        indicatorPath = new Path();
        targetRect = new RectF();

        // Initialize with default pitch
        currentPitchData = new PitchData(targetPitchHz, targetPitchHz, 1.0f, System.currentTimeMillis());
    }

    public void setTargetPitch(float targetPitchHz) {
        this.targetPitchHz = targetPitchHz;
    }

    public void updatePitch(PitchData pitchData) {
        this.currentPitchData = pitchData;
        this.accuracy = pitchData.accuracy;
        postInvalidate();
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setStreak(int streak) {
        this.streak = streak;
        this.maxStreak = Math.max(maxStreak, streak);
    }

    public void triggerSuccess() {
        showSuccess = true;
        successStartTime = System.currentTimeMillis();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        // Animate accuracy
        long now = System.currentTimeMillis();
        float dt = (now - lastAnimationTime) / 1000f;
        lastAnimationTime = now;
        animatedAccuracy += (accuracy - animatedAccuracy) * Math.min(dt * 8f, 1f);

        // Draw background
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        // Draw target zone (center area)
        float targetLeft = centerX - TARGET_ZONE_WIDTH / 2f;
        float targetRight = centerX + TARGET_ZONE_WIDTH / 2f;
        targetRect.set(targetLeft, centerY - 40, targetRight, centerY + 40);
        canvas.drawRoundRect(targetRect, 20, 20, targetZonePaint);

        // Draw center line
        Paint centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerLinePaint.setColor(Color.parseColor("#00E676"));
        centerLinePaint.setStrokeWidth(2);
        centerLinePaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(centerX, centerY - 50, centerX, centerY + 50, centerLinePaint);

        // Calculate indicator position based on pitch deviation
        float pitchRatio = currentPitchData != null && currentPitchData.targetPitchHz > 0
                ? currentPitchData.currentPitchHz / currentPitchData.targetPitchHz
                : 1f;
        float cents = 1200f * (float) Math.log(pitchRatio);
        float maxCents = 100f; // ±100 cents range
        float normalizedDeviation = Math.max(-1f, Math.min(1f, cents / maxCents));
        float indicatorX = centerX + normalizedDeviation * (width / 2f - 60);

        // Draw trail
        if (Math.abs(cents) > 5) {
            canvas.drawLine(centerX, centerY, indicatorX, centerY, trailPaint);
        }

        // Draw indicator (diamond shape)
        indicatorPath.reset();
        indicatorPath.moveTo(indicatorX, centerY - INDICATOR_RADIUS);
        indicatorPath.lineTo(indicatorX + INDICATOR_RADIUS, centerY);
        indicatorPath.lineTo(indicatorX, centerY + INDICATOR_RADIUS);
        indicatorPath.lineTo(indicatorX - INDICATOR_RADIUS, centerY);
        indicatorPath.close();

        // Color indicator based on accuracy
        if (animatedAccuracy >= 0.9f) {
            indicatorPaint.setColor(Color.parseColor("#00E676")); // Green - perfect
        } else if (animatedAccuracy >= 0.7f) {
            indicatorPaint.setColor(Color.parseColor("#FFD600")); // Yellow - good
        } else if (animatedAccuracy >= 0.4f) {
            indicatorPaint.setColor(Color.parseColor("#FF9100")); // Orange - close
        } else {
            indicatorPaint.setColor(Color.parseColor("#FF3D00")); // Red - off
        }

        canvas.drawPath(indicatorPath, indicatorPaint);

        // Draw success animation
        if (showSuccess) {
            long elapsed = now - successStartTime;
            float progress = Math.min(elapsed / 500f, 1f);
            int alpha = (int) (255 * (1 - progress));
            float scale = 1f + progress * 1.5f;

            Paint successPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            successPaint.setColor(Color.parseColor("#00E676"));
            successPaint.setAlpha(alpha);
            successPaint.setStyle(Paint.Style.STROKE);
            successPaint.setStrokeWidth(4);

            float radius = 50 * scale;
            canvas.drawCircle(centerX, centerY, radius, successPaint);

            if (progress >= 1f) {
                showSuccess = false;
            } else {
                postInvalidateOnAnimation();
            }
        }

        // Draw labels
        String targetNote = currentPitchData != null ? currentPitchData.noteName : "—";
        canvas.drawText("TARGET: " + targetNote, centerX, 40, textPaint);

        // Draw current pitch note
        String currentNote = currentPitchData != null ? currentPitchData.noteName : "—";
        canvas.drawText("YOU: " + currentNote, centerX, height - 20, textPaint);

        // Draw accuracy percentage
        String accuracyText = String.format("%.0f%%", animatedAccuracy * 100);
        canvas.drawText(accuracyText, centerX, centerY + 5, accuracyPaint);

        // Draw cents offset
        if (currentPitchData != null && Math.abs(currentPitchData.centsOffset) > 0) {
            String centsText = String.format("%+d¢", currentPitchData.centsOffset);
            canvas.drawText(centsText, centerX, centerY - 55, textPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(Math.max(width, METER_WIDTH), Math.max(height, METER_HEIGHT));
    }
}
