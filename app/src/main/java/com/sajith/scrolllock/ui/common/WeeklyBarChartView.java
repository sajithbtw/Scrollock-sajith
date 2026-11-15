package com.sajith.scrolllock.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal rounded vertical bar chart displaying the last 7 days of locally recorded focus minutes.
 */
public class WeeklyBarChartView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final List<String> labels = new ArrayList<>();
    private final List<Integer> values = new ArrayList<>();
    private float density;

    public WeeklyBarChartView(Context context) {
        super(context);
        init(context);
    }

    public WeeklyBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WeeklyBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        barPaint.setColor(ContextCompat.getColor(context, R.color.sl_accent));
        barPaint.setStyle(Paint.Style.FILL);

        trackBarPaint.setColor(ContextCompat.getColor(context, R.color.sl_surface_subtle));
        trackBarPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(ContextCompat.getColor(context, R.color.sl_text_secondary));
        labelPaint.setTextSize(11f * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setColor(ContextCompat.getColor(context, R.color.sl_text_primary));
        valuePaint.setTextSize(10f * density);
        valuePaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(Map<String, Integer> dayMinutesMap) {
        labels.clear();
        values.clear();
        if (dayMinutesMap != null) {
            for (Map.Entry<String, Integer> entry : dayMinutesMap.entrySet()) {
                labels.add(entry.getKey());
                values.add(entry.getValue() != null ? entry.getValue() : 0);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels.isEmpty()) {
            return;
        }

        int count = labels.size();
        float width = getWidth();
        float height = getHeight();

        float topMargin = 20f * density;
        float bottomLabelSpace = 24f * density;
        float usableHeight = Math.max(24f, height - topMargin - bottomLabelSpace);
        float columnWidth = width / count;
        float barWidth = Math.min(24f * density, columnWidth * 0.48f);
        float cornerRadius = barWidth / 2f;

        int maxVal = 30;
        for (int val : values) {
            if (val > maxVal) {
                maxVal = val;
            }
        }

        for (int i = 0; i < count; i++) {
            float centerX = columnWidth * i + columnWidth / 2f;
            float left = centerX - barWidth / 2f;
            float right = centerX + barWidth / 2f;
            float barBottom = height - bottomLabelSpace;

            // Subtle full-height track bar
            rect.set(left, topMargin, right, barBottom);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, trackBarPaint);

            int minutes = values.get(i);
            if (minutes > 0) {
                float fraction = (float) minutes / (float) maxVal;
                float barHeight = Math.max(barWidth, usableHeight * fraction);
                float barTop = barBottom - barHeight;
                rect.set(left, barTop, right, barBottom);
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, barPaint);

                canvas.drawText(String.valueOf(minutes), centerX, barTop - (6f * density), valuePaint);
            }

            canvas.drawText(labels.get(i), centerX, height - (6f * density), labelPaint);
        }
    }
}
