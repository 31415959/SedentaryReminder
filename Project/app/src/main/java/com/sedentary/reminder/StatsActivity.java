package com.sedentary.reminder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsActivity extends Activity {
    private Prefs p;
    private StatsChartView chart;
    private TrendChartView trend;
    private LinearLayout box;
    private TextView tvRangeTitle, tvLegend, tvSummaryTitle;
    private TextView tvWeekBreaks, tvWeekAlerts, tvWeekStreak, tvWeekSummary2;
    private int range = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        new SideNav(this);
        ((TextView) findViewById(R.id.tvTitle)).setText("统计");
        p = new Prefs(this);
        chart = findViewById(R.id.chart);
        trend = findViewById(R.id.trendChart);
        box = findViewById(R.id.llDays);
        tvRangeTitle = findViewById(R.id.tvRangeTitle);
        tvLegend = findViewById(R.id.tvLegend);
        tvSummaryTitle = findViewById(R.id.tvSummaryTitle);
        tvWeekBreaks = findViewById(R.id.tvWeekBreaks);
        tvWeekAlerts = findViewById(R.id.tvWeekAlerts);
        tvWeekStreak = findViewById(R.id.tvWeekStreak);
        tvWeekSummary2 = findViewById(R.id.tvWeekSummary2);

        ((TextView) findViewById(R.id.tvTodayBreaks)).setText(String.valueOf(p.today("breaks")));
        ((TextView) findViewById(R.id.tvTodayAlerts)).setText(String.valueOf(p.today("alerts")));

        showTrend();

        findViewById(R.id.btnRangeDay).setOnClickListener(v -> setRange(0));
        findViewById(R.id.btnRangeWeek).setOnClickListener(v -> setRange(1));
        findViewById(R.id.btnRangeMonth).setOnClickListener(v -> setRange(2));
        findViewById(R.id.btnRangeYear).setOnClickListener(v -> setRange(3));
        setRange(1);

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        findViewById(R.id.btnShare).setOnClickListener(v -> shareReport());
    }

    private void setRange(int r) {
        range = r;
        int[] ids = {R.id.btnRangeDay, R.id.btnRangeWeek, R.id.btnRangeMonth, R.id.btnRangeYear};
        for (int i = 0; i < ids.length; i++) {
            Button b = findViewById(ids[i]);
            if (i == r) {
                b.setBackgroundResource(R.drawable.btn_primary);
                b.setTextColor(0xFFFFFFFF);
            } else {
                b.setBackgroundResource(R.drawable.btn_secondary);
                b.setTextColor(0xFF1C1F1D);
            }
        }
        updateSummary(r);
        if (r == 0) showDay();
        else if (r == 1) showWeek();
        else if (r == 2) showMonth();
        else showYear();
    }

    /** 顶部摘要随周期联动：数值按当前范围聚合，标题随视图切换。 */
    private void updateSummary(int r) {
        long now = System.currentTimeMillis();
        int days;
        String title;
        switch (r) {
            case 0: days = 1; title = "今日概览"; break;
            case 1: days = 7; title = "近 7 天概览"; break;
            case 2: days = 30; title = "近 30 天概览"; break;
            default: days = 365; title = "近 12 个月概览"; break;
        }
        tvSummaryTitle.setText(title);
        tvWeekBreaks.setText(p.sumLast("breaks", days) + " 次");
        tvWeekAlerts.setText(p.sumLast("alerts", days) + " 次");
        tvWeekStreak.setText(p.streakDays() + " 天");
        int sitMin = p.sumLast("sitSumSec", days) / 60;
        int avg = avgFor(days);
        tvWeekSummary2.setText("累计久坐 " + sitMin + " 分钟 · 平均每次 "
                + (avg > 0 ? avg + " 分钟" : "数据积累中"));
    }

    private int avgFor(int days) {
        int sec = p.sumLast("sitSumSec", days);
        int cnt = p.sumLast("sitCount", days);
        if (cnt <= 0 || sec <= 0) return 0;
        return sec / cnt / 60;
    }

    private void showDay() {
        tvRangeTitle.setText("今日 24 小时");
        tvLegend.setText("橙虚线 = 均值 · 深色 = 各小时响应评分（0-10，5 为中性）");
        String[] labels = new String[24];
        int[] score = new int[24];
        int[] zero = new int[24];
        float sum = 0;
        for (int i = 0; i < 24; i++) {
            labels[i] = i + "时";
            score[i] = p.hourScore(i);
            sum += score[i];
        }
        chart.setData(labels, score, zero, null, sum / 24f);
        box.removeAllViews();
        String[] names = {"夜间 0-5", "上午 6-11", "下午 12-17", "晚上 18-23"};
        for (int i = 0; i < 4; i++) {
            addRow(names[i], p.daypartScore(i), -1);
        }
    }

    private void showWeek() {
        tvRangeTitle.setText("近 7 天");
        tvLegend.setText("橙虚线 = 均值 · 深灰 = 提醒 · 摩卡 = 有效活动");
        String[] labels = new String[7];
        int[] br = new int[7];
        int[] al = new int[7];
        long now = System.currentTimeMillis();
        float sumBr = 0;
        for (int i = 0; i < 7; i++) {
            String key = Prefs.keyFor(now - (6 - i) * 86400000L);
            labels[i] = key.substring(4, 6) + "/" + key.substring(6, 8);
            br[i] = p.statForDate(key, "breaks");
            al[i] = p.statForDate(key, "alerts");
            sumBr += br[i];
        }
        chart.setData(labels, al, br, null, sumBr / 7f);
        fillRows(labels, al, br, 7);
    }

    private void showMonth() {
        int n = 30;
        long now = System.currentTimeMillis();
        String k0 = Prefs.keyFor(now - (n - 1) * 86400000L);
        String k1 = Prefs.keyFor(now);
        tvRangeTitle.setText("近 30 天 · " + Integer.parseInt(k0.substring(4, 6)) + "/"
                + Integer.parseInt(k0.substring(6, 8)) + " - "
                + Integer.parseInt(k1.substring(4, 6)) + "/"
                + Integer.parseInt(k1.substring(6, 8)));
        tvLegend.setText("橙虚线 = 均值 · 深灰 = 提醒 · 摩卡 = 有效活动");
        String[] labels = new String[n];
        int[] br = new int[n];
        int[] al = new int[n];
        float sumBr = 0;
        for (int i = 0; i < n; i++) {
            String key = Prefs.keyFor(now - (n - 1 - i) * 86400000L);
            labels[i] = Integer.parseInt(key.substring(4, 6)) + "/"
                    + Integer.parseInt(key.substring(6, 8));
            br[i] = p.statForDate(key, "breaks");
            al[i] = p.statForDate(key, "alerts");
            sumBr += br[i];
        }
        chart.setData(labels, al, br, null, sumBr / n);
        fillRows(labels, al, br, n);
    }

    private void showYear() {
        tvRangeTitle.setText("近 12 个月");
        tvLegend.setText("橙虚线 = 均值 · 深灰 = 提醒 · 摩卡 = 有效活动（次/天）");
        long now = System.currentTimeMillis();
        List<String> keys = new ArrayList<>();
        Map<String, Integer> brMap = new HashMap<>();
        Map<String, Integer> alMap = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM", Locale.US);
        for (int i = 364; i >= 0; i--) {
            String dk = Prefs.keyFor(now - i * 86400000L);
            cal.setTime(new Date(now - i * 86400000L));
            String mk = f.format(cal.getTime());
            if (!brMap.containsKey(mk)) {
                brMap.put(mk, 0);
                alMap.put(mk, 0);
                keys.add(mk);
            }
            brMap.put(mk, brMap.get(mk) + p.statForDate(dk, "breaks"));
            alMap.put(mk, alMap.get(mk) + p.statForDate(dk, "alerts"));
        }
        while (keys.size() > 12) keys.remove(0);
        int n = keys.size();
        String[] labels = new String[n];
        int[] br = new int[n];
        int[] al = new int[n];
        float[] per = new float[n];
        float sumDaily = 0;
        for (int i = 0; i < n; i++) {
            int month = Integer.parseInt(keys.get(i).substring(5));
            labels[i] = String.valueOf(month);
            br[i] = brMap.get(keys.get(i));
            al[i] = alMap.get(keys.get(i));
            boolean isCurrent = keys.get(i).equals(
                    f.format(new Date(now)));
            int dim;
            if (isCurrent) {
                cal.setTime(new Date(now));
                dim = cal.get(Calendar.DAY_OF_MONTH);
            } else {
                cal.setTime(new Date(now));
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MONTH, 1);
                cal.add(Calendar.DAY_OF_YEAR, -1);
                dim = cal.get(Calendar.DAY_OF_MONTH);
            }
            per[i] = 1f / dim;   // 月日均（当前月按已过天数）
            sumDaily += br[i] * per[i];
        }
        chart.setData(labels, al, br, per, sumDaily / Math.max(1, n));
        fillRows(labels, al, br, n);
    }

    private void fillRows(String[] labels, int[] al, int[] br, int n) {
        box.removeAllViews();
        for (int i = n - 1; i >= 0; i--) {
            addRow(labels[i], al[i], br[i]);
        }
    }

    private void addRow(String label, int alerts, int breaks) {
        TextView row = new TextView(this);
        if (breaks < 0) row.setText(label + "   响应评分 " + alerts + "/10");
        else row.setText(label + "   提醒 " + alerts + " 次 · 有效活动 " + breaks + " 次");
        row.setTextSize(14);
        row.setTextColor(0xFF4A4038);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density),
                0, (int) (10 * getResources().getDisplayMetrics().density));
        box.addView(row);
    }

    private void showTrend() {
        long now = System.currentTimeMillis();
        String[] labels = new String[7];
        int[] targets = new int[7];
        int[] scores = new int[7];
        for (int i = 0; i < 7; i++) {
            String key = Prefs.keyFor(now - (6 - i) * 86400000L);
            labels[i] = key.substring(4, 6) + "/" + key.substring(6, 8);
            targets[i] = p.adaptiveTargetForDate(key);
            scores[i] = p.adaptiveScoreForDate(key);
        }
        trend.setData(labels, targets, scores);
    }

    private void shareReport() {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("久坐提醒 · 数据报告\n");
        int wb = p.sumLast("breaks", 7);
        int wa = p.sumLast("alerts", 7);
        int sitMin = p.sumLast("sitSumSec", 7) / 60;
        int avg = avgFor(7);
        sb.append("近 7 天：有效活动 ").append(wb).append(" 次，提醒 ").append(wa)
          .append(" 次，连续达标 ").append(p.streakDays()).append(" 天\n");
        sb.append("累计久坐 ").append(sitMin).append(" 分钟");
        if (avg > 0) sb.append("，平均每次 ").append(avg).append(" 分钟");
        sb.append("\n");
        for (int i = 6; i >= 0; i--) {
            String key = Prefs.keyFor(now - i * 86400000L);
            sb.append(key.substring(4, 6)).append("/").append(key.substring(6, 8))
              .append("  活动 ").append(p.statForDate(key, "breaks"))
              .append(" 次 · 提醒 ").append(p.statForDate(key, "alerts")).append(" 次\n");
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "分享报告"));
    }
}
