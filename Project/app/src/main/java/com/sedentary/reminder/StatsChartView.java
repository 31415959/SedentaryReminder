package com.sedentary.reminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * 统计柱状图：双系列（提醒 / 有效活动）并排。
 * 柱高支持按槽换算（如年视图的"月日均"），并绘制一条均值参考虚线（主系列）。
 */
public class StatsChartView extends View {
    private String[] labels = new String[0];
    private int[] alerts = new int[0];
    private int[] breaks = new int[0];
    private float[] per = new float[0];   // 每槽换算系数（月日均=1/当月天数；其余=1）
    private float avgLine = 0;            // 均值参考线（0=不画）
    private final Paint barP = new Paint();
    private final Paint barG = new Paint();
    private final Paint nilP = new Paint();
    private final Paint txtP = new Paint();
    private final Paint smallP = new Paint();
    private final Paint avgP = new Paint();
    private final Paint slashP = new Paint();
    private float dp;

    public StatsChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        barP.setColor(0xFF3F362E);
        barP.setAntiAlias(true);
        barG.setColor(0xFF8B6F5B);
        barG.setAntiAlias(true);
        nilP.setColor(0xFFDACDB8);
        nilP.setAntiAlias(true);
        txtP.setColor(0xFF8A7F73);
        txtP.setTextSize(11 * dp);
        txtP.setTextAlign(Paint.Align.CENTER);
        txtP.setAntiAlias(true);
        smallP.setColor(0xFFA0968A);
        smallP.setTextSize(9 * dp);
        smallP.setTextAlign(Paint.Align.CENTER);
        smallP.setAntiAlias(true);
        avgP.setColor(0xFFC56F2B);
        avgP.setStrokeWidth(1.2f * dp);
        avgP.setStyle(Paint.Style.STROKE);
        avgP.setPathEffect(new DashPathEffect(new float[]{6 * dp, 6 * dp}, 0));
        avgP.setAntiAlias(true);
        slashP.setColor(0xFF8A7F73);
        slashP.setStrokeWidth(1.0f * dp);
        slashP.setStyle(Paint.Style.STROKE);
        slashP.setAntiAlias(true);
    }

    public void setData(String[] l, int[] a, int[] b) {
        setData(l, a, b, null, 0);
    }

    public void setData(String[] l, int[] a, int[] b, float[] perSlot, float avg) {
        labels = l;
        alerts = a;
        breaks = b;
        per = new float[l.length];
        for (int i = 0; i < per.length; i++) per[i] = perSlot == null ? 1f : perSlot[i];
        avgLine = avg;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels.length == 0) return;
        float w = getWidth();
        float h = getHeight();
        // 月视图使用“月在上、日期在下”的两行标签，并把所有日期都显示出来，避免横排日期重叠。
        boolean stackedLabels = labels.length >= 20 && labels[0].contains("/");
        float top = 26 * dp;
        float bottom = h - (stackedLabels ? 64 * dp : 30 * dp);
        float slot = w / labels.length;
        boolean dense = labels.length >= 20;
        float bw = Math.min(dense ? 5 * dp : 12 * dp, slot * 0.32f);
        // 同一天的两根柱子靠在一起，不同天之间留出明显间距。
        float pairGap = dense ? 1.0f * dp : 1.5f * dp;
        float halfPair = bw + pairGap / 2f;
        float edge = 4 * dp;

        // 换算后柱高与 y 轴最大值
        float[] av = new float[labels.length];
        float[] bv = new float[labels.length];
        float maxv = 1f;
        float maxBreak = 0;
        for (int i = 0; i < labels.length; i++) {
            av[i] = alerts[i] * per[i];
            bv[i] = breaks[i] * per[i];
            maxv = Math.max(maxv, Math.max(av[i], bv[i]));
            maxBreak = Math.max(maxBreak, bv[i]);
        }
        boolean singleSeries = maxBreak <= 0.05f;   // 单系列（如日视图）数值全量标注
        float usable = bottom - top;

        // 标签步进：月视图两行标签全显；其他密集视图按 3 格一标；宽裕视图全显。
        int step;
        if (stackedLabels) step = 1;
        else if (labels.length >= 20) step = 3;
        else step = 1;

        // 均值参考线（主系列=有效活动）
        if (avgLine > 0) {
            float ay = bottom - avgLine * usable / maxv;
            if (ay > top + 8 * dp && ay < bottom - 8 * dp) {
                canvas.drawLine(edge, ay, w - edge, ay, avgP);
            }
        }

        for (int i = 0; i < labels.length; i++) {
            float cx = slot * i + slot / 2f;
            boolean showLabel = (i % step == 0);

            if (singleSeries) {
                // 单系列（日视图）柱体和数值都居中，保证文字和柱子一一对应。
                if (av[i] > 0.05f) {
                    float ha = av[i] * usable / maxv;
                    canvas.drawRoundRect(cx - bw / 2f, bottom - ha, cx + bw / 2f, bottom,
                            4 * dp, 4 * dp, barP);
                    float vx = cx;
                    float vh = smallP.measureText(fmt(av[i])) / 2f;
                    if (vx - vh < edge) vx = edge + vh;
                    else if (vx + vh > w - edge) vx = w - edge - vh;
                    canvas.drawText(fmt(av[i]), vx, bottom - ha - 5 * dp, smallP);
                } else {
                    canvas.drawRoundRect(cx - bw / 2f, bottom - 2 * dp, cx + bw / 2f, bottom,
                            2 * dp, 2 * dp, nilP);
                }
            } else {
                // 双系列：同一天两根柱子紧挨，不同天之间由 slot 自然分隔。
                if (av[i] > 0.05f) {
                    float ha = av[i] * usable / maxv;
                    canvas.drawRoundRect(cx - halfPair, bottom - ha, cx - pairGap / 2f, bottom,
                            4 * dp, 4 * dp, barP);
                }
                if (bv[i] > 0.05f) {
                    float hb = bv[i] * usable / maxv;
                    canvas.drawRoundRect(cx + pairGap / 2f, bottom - hb, cx + halfPair, bottom,
                            4 * dp, 4 * dp, barG);
                }
                if (av[i] <= 0.05f && bv[i] <= 0.05f) {
                    // 无记录占位：浅色短座，明示"当日无数据"而非绘制缺口
                    canvas.drawRoundRect(cx - halfPair, bottom - 2 * dp, cx - pairGap / 2f, bottom,
                            2 * dp, 2 * dp, nilP);
                    canvas.drawRoundRect(cx + pairGap / 2f, bottom - 2 * dp, cx + halfPair, bottom,
                            2 * dp, 2 * dp, nilP);
                }
                if (av[i] > 0.05f) {
                    canvas.drawText(fmt(av[i]), cx - pairGap / 2f - bw / 2f,
                            bottom - av[i] * usable / maxv - 5 * dp, smallP);
                }
                if (bv[i] > 0.05f) {
                    canvas.drawText(fmt(bv[i]), cx + pairGap / 2f + bw / 2f,
                            bottom - bv[i] * usable / maxv - 5 * dp, smallP);
                }
            }

            if (showLabel) {
                String monthText = null;
                String dayText = null;
                float half;
                if (stackedLabels) {
                    String[] parts = labels[i].split("/");
                    monthText = parts.length > 0 ? parts[0] : labels[i];
                    dayText = parts.length > 1 ? parts[1] : "";
                    // 日期是竖排的，所以横向宽度只看“月份字符串”和“单个日期数字”的最大宽度，
                    // 不要用整串“7/20”的宽度，否则首尾会被推歪并压到相邻日期。
                    float maxW = txtP.measureText(monthText);
                    for (int k = 0; k < dayText.length(); k++) {
                        maxW = Math.max(maxW, txtP.measureText(String.valueOf(dayText.charAt(k))));
                    }
                    half = maxW / 2f;
                } else {
                    half = txtP.measureText(labels[i]) / 2f;
                }
                // 标签夹紧到图表内，但只保留很小的边距，避免首尾标签被推得太靠里。
                float labelEdge = 1 * dp;
                float lx = cx;
                if (cx - half < labelEdge) lx = labelEdge + half;
                else if (cx + half > w - labelEdge) lx = w - labelEdge - half;
                if (stackedLabels) {
                    // 日期竖向：一个数字一行。
                    // 月份靠近斜杠约半个字符高度；日期离斜杠稍远一点。
                    float slashTopY = h - 50 * dp;
                    float slashBottomY = h - 45 * dp;
                    float monthGap = 3 * dp;
                    float dateGap = 10 * dp;
                    canvas.drawText(monthText, lx, slashTopY - monthGap, txtP);
                    canvas.drawLine(lx + 4 * dp, slashTopY, lx - 4 * dp, slashBottomY, slashP);
                    float dayStartY = slashBottomY + dateGap;
                    for (int k = 0; k < dayText.length(); k++) {
                        canvas.drawText(String.valueOf(dayText.charAt(k)), lx,
                                dayStartY + k * 10 * dp, txtP);
                    }
                } else {
                    canvas.drawText(labels[i], lx, h - 8 * dp, txtP);
                }
            }
        }
    }

    private String fmt(float v) {
        float r = Math.round(v * 10f) / 10f;
        if (Math.abs(r - Math.round(r)) < 0.05f) return String.valueOf((int) Math.round(r));
        return String.format(Locale.US, "%.1f", r);
    }
}
