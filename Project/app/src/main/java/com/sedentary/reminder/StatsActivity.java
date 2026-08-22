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
    private TextView tvRangeTitle, tvLegend;
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

        int tb = p.today("breaks");
        int ta = p.today("alerts");
        ((TextView) findViewById(R.id.tvTodayBreaks)).setText(String.valueOf(tb));
        ((TextView) findViewById(R.id.tvTodayAlerts)).setText(String.valueOf(ta));

        int wb = p.sumLast("breaks", 7);
        int wa = p.sumLast("alerts", 7);
        int sitMin = p.sumLast("sitSumSec", 7) / 60;
        int avg = p.avgSitMinutesLast7();
        int streak = p.streakDays();
        ((TextView) findViewById(R.id.tvWeekBreaks)).setText(wb + " 次");
        ((TextView) findViewById(R.id.tvWeekAlerts)).setText(wa + " 次");
        ((TextView) findViewById(R.id.tvWeekStreak)).setText(streak + " 天");
        ((TextView) findViewById(R.id.tvWeekSummary2))
                .setText("累计久坐 " + sitMin + " 分钟 · 平均每次 "
                        + (avg > 0 ? avg + " 分钟" : "数据积累中"));

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
        findViewById(R.id.btnShare).setOnClickListener(v -> shareReport(wb, wa, sitMin, avg, streak));
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
        if (r == 0) showDay();
        else if (r == 1) showWeek();
        else if (r == 2) showMonth();
        else showYear();
    }

    private void showDay() {
        tvRangeTitle.setText("今日 24 小时");
        tvLegend.setText("深色 = 各小时响应评分（0-10，5 为中性）");
        String[] labels = new String[24];
        int[] score = new int[24];
        int[] zero = new int[24];
        for (int i = 0; i < 24; i++) {
            labels[i] = (i % 2 == 0) ? i + "时" : "";
            score[i] = p.hourScore(i);
        }
        chart.setData(labels, score, zero);
        box.removeAllViews();
        String[] names = {"夜间 0-5", "上午 6-11", "下午 12-17", "晚上 18-23"};
        for (int i = 0; i < 4; i++) {
            addRow(names[i], p.daypartScore(i), -1);
        }
    }

    private void showWeek() {
        tvRangeTitle.setText("近 7 天");
        tvLegend.setText("深灰 = 提醒 · 摩卡 = 有效活动");
        String[] labels = new String[7];
        int[] br = new int[7];
        int[] al = new int[7];
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            String key = Prefs.keyFor(now - (6 - i) * 86400000L);
            labels[i] = key.substring(4, 6) + "/" + key.substring(6, 8);
            br[i] = p.statForDate(key, "breaks");
            al[i] = p.statForDate(key, "alerts");
        }
        chart.setData(labels, al, br);
        fillRows(labels, al, br, 7);
    }

    private void showMonth() {
        tvRangeTitle.setText("近 30 天");
        tvLegend.setText("深灰 = 提醒 · 摩卡 = 有效活动");
        int n = 30;
        String[] labels = new String[n];
        int[] br = new int[n];
        int[] al = new int[n];
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            String key = Prefs.keyFor(now - (n - 1 - i) * 86400000L);
            labels[i] = key.substring(4, 6) + "/" + key.substring(6, 8);
            br[i] = p.statForDate(key, "breaks");
            al[i] = p.statForDate(key, "alerts");
        }
        chart.setData(labels, al, br);
        fillRows(labels, al, br, n);
    }

    private void showYear() {
        tvRangeTitle.setText("近 12 个月");
        tvLegend.setText("深灰 = 提醒 · 摩卡 = 有效活动");
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
        for (int i = 0; i < n; i++) {
            labels[i] = keys.get(i).substring(5) + "月";
            br[i] = brMap.get(keys.get(i));
            al[i] = alMap.get(keys.get(i));
        }
        chart.setData(labels, al, br);
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

    private void shareReport(int wb, int wa, int sitMin, int avg, int streak) {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("久坐提醒 · 本周报告\n");
        sb.append("有效活动 ").append(wb).append(" 次，提醒 ").append(wa)
          .append(" 次，连续达标 ").append(streak).append(" 天\n");
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
        startActivity(Intent.createChooser(send, "分享本周报告"));
    }
}