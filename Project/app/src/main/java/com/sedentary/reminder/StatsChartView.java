package com.sedentary.reminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class StatsChartView extends View {
    private String[] labels = new String[0];
    private int[] alerts = new int[0];
    private int[] breaks = new int[0];
    private final Paint barP = new Paint();
    private final Paint barG = new Paint();
    private final Paint txtP = new Paint();
    private final Paint smallP = new Paint();
    private float dp;

    public StatsChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        barP.setColor(0xFF3F362E);
        barG.setColor(0xFF8B6F5B);
        txtP.setColor(0xFF8A7F73);
        txtP.setTextSize(11 * dp);
        txtP.setTextAlign(Paint.Align.CENTER);
        smallP.setColor(0xFFA0968A);
        smallP.setTextSize(9 * dp);
        smallP.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(String[] l, int[] a, int[] b) {
        labels = l;
        alerts = a;
        breaks = b;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels.length == 0) return;
        float w = getWidth();
        float h = getHeight();
        float top = 24 * dp;
        float bottom = h - 30 * dp;
        float slot = w / labels.length;
        float bw = Math.min(12 * dp, slot * 0.32f);
        int labelStep = 1;
        if (labels.length >= 30) labelStep = 5;
        else if (labels.length >= 20) labelStep = 4;
        else if (labels.length >= 12) labelStep = 2;
        int max = 1;
        for (int v : alerts) max = Math.max(max, v);
        for (int v : breaks) max = Math.max(max, v);
        float usable = bottom - top;
        for (int i = 0; i < labels.length; i++) {
            boolean showMark = (i % labelStep == 0) || (i == labels.length - 1);
            float cx = slot * i + slot / 2f;
            float ha = alerts[i] * usable / max;
            float hb = breaks[i] * usable / max;
            canvas.drawRoundRect(cx - bw - 2 * dp, bottom - ha, cx - 2 * dp, bottom, 4 * dp, 4 * dp, barP);
            canvas.drawRoundRect(cx + 2 * dp, bottom - hb, cx + bw + 2 * dp, bottom, 4 * dp, 4 * dp, barG);
            if (showMark && alerts[i] > 0) {
                canvas.drawText(String.valueOf(alerts[i]), cx - bw / 2f, bottom - ha - 5 * dp, smallP);
            }
            if (showMark && breaks[i] > 0) {
                canvas.drawText(String.valueOf(breaks[i]), cx + bw / 2f, bottom - hb - 5 * dp, smallP);
            }
            if (showMark) {
                canvas.drawText(labels[i], cx, h - 8 * dp, txtP);
            }
        }
    }
}
