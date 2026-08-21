package com.sedentary.reminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class TrendChartView extends View {
    private String[] labels = new String[0];
    private int[] targets = new int[0];
    private int[] scores = new int[0];
    private final Paint lineT = new Paint();
    private final Paint lineS = new Paint();
    private final Paint txt = new Paint();
    private final Paint val = new Paint();
    private float dp;

    public TrendChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        lineT.setColor(0xFF1E6B52);
        lineT.setStrokeWidth(2.5f * dp);
        lineT.setStyle(Paint.Style.STROKE);
        lineT.setStrokeCap(Paint.Cap.ROUND);
        lineS.setColor(0xFF9AA7A1);
        lineS.setStrokeWidth(1.5f * dp);
        lineS.setStyle(Paint.Style.STROKE);
        lineS.setStrokeCap(Paint.Cap.ROUND);
        txt.setColor(0xFF6B726E);
        txt.setTextSize(11 * dp);
        txt.setTextAlign(Paint.Align.CENTER);
        val.setColor(0xFF3D4943);
        val.setTextSize(10 * dp);
        val.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(String[] l, int[] t, int[] s) {
        labels = l;
        targets = t;
        scores = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels.length == 0) return;
        float w = getWidth();
        float h = getHeight();
        float top = 16 * dp;
        float bottom = h - 28 * dp;
        float slot = w / labels.length;
        int max = 90;
        float usable = bottom - top;
        float prevX = -1, prevY = -1, prevSX = -1, prevSY = -1;
        for (int i = 0; i < labels.length; i++) {
            float cx = slot * i + slot / 2f;
            if (targets[i] > 0) {
                float y = bottom - targets[i] * usable / max;
                canvas.drawCircle(cx, y, 3 * dp, lineT);
                val.setColor(0xFF1E6B52);
                canvas.drawText(String.valueOf(targets[i]), cx, y - 6 * dp, val);
                if (prevX >= 0) canvas.drawLine(prevX, prevY, cx, y, lineT);
                prevX = cx;
                prevY = y;
            } else {
                prevX = -1;
            }
            if (scores[i] > 0) {
                float sy = bottom - scores[i] * usable / 10f;
                if (prevSX >= 0) canvas.drawLine(prevSX, prevSY, cx, sy, lineS);
                prevSX = cx;
                prevSY = sy;
            } else {
                prevSX = -1;
            }
            canvas.drawText(labels[i], cx, h - 8 * dp, txt);
        }
    }
}
