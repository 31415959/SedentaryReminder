package com.sedentary.reminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class Prefs {
    public static final String ACTION_STATE = "com.sedentary.reminder.STATE";
    public static final String ACTION_ALERT = "com.sedentary.reminder.ALERT";
    public static final String ACTION_BREAK = "com.sedentary.reminder.BREAK";
    public static final String ACTION_STOP = "com.sedentary.reminder.STOP";

    private final SharedPreferences sp;
    private final String day;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("sedentary", Context.MODE_PRIVATE);
        day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (!sp.getBoolean("algoV2", false)) {
            // v2 算法升级：旧压力值语义不同，迁移为中性起点
            sp.edit().putBoolean("algoV2", true).putInt("pressure", 5).apply();
        }
    }

    public int sitMinutes() { return clamp(sp.getInt("sit", 45), 15, 240); }
    public void setSitMinutes(int v) { sp.edit().putInt("sit", clamp(v, 15, 240)).apply(); }

    public int winMinutes() { return clamp(sp.getInt("win", 2), 1, 15); }
    public void setWinMinutes(int v) { sp.edit().putInt("win", clamp(v, 1, 15)).apply(); }

    public int winSteps() { return clamp(sp.getInt("steps", 120), 20, 1000); }
    public void setWinSteps(int v) { sp.edit().putInt("steps", clamp(v, 20, 1000)).apply(); }

    public int repeatMinutes() { return clamp(sp.getInt("repeat", 5), 1, 60); }

    public boolean enabled() { return sp.getBoolean("enabled", true); }
    public void setEnabled(boolean v) { sp.edit().putBoolean("enabled", v).apply(); }

    public boolean autoAdaptive() { return sp.getBoolean("autoAdaptive", true); }
    public void setAutoAdaptive(boolean v) { sp.edit().putBoolean("autoAdaptive", v).apply(); }

    public boolean onboarded() { return sp.getBoolean("onboarded", false); }
    public void setOnboarded(boolean v) { sp.edit().putBoolean("onboarded", v).apply(); }

    public int age() { return clamp(sp.getInt("age", 30), 10, 100); }
    public void setAge(int v) { sp.edit().putInt("age", clamp(v, 10, 100)).apply(); }

    public int gender() { return clamp(sp.getInt("gender", 0), 0, 2); }
    public void setGender(int v) { sp.edit().putInt("gender", clamp(v, 0, 2)).apply(); }

    public int heightCm() { return clamp(sp.getInt("height", 170), 100, 250); }
    public void setHeightCm(int v) { sp.edit().putInt("height", clamp(v, 100, 250)).apply(); }

    public int weightKg() { return clamp(sp.getInt("weight", 65), 30, 300); }
    public void setWeightKg(int v) { sp.edit().putInt("weight", clamp(v, 30, 300)).apply(); }

    public int bodyFat() { return clamp(sp.getInt("bodyFat", 0), 0, 60); }
    public void setBodyFat(int v) { sp.edit().putInt("bodyFat", clamp(v, 0, 60)).apply(); }

    public int occupation() { return clamp(sp.getInt("job", 0), 0, 2); }
    public void setOccupation(int v) { sp.edit().putInt("job", clamp(v, 0, 2)).apply(); }

    public boolean healthFlag() { return sp.getBoolean("healthFlag", false); }
    public void setHealthFlag(boolean v) { sp.edit().putBoolean("healthFlag", v).apply(); }

    public double bmi() {
        double h = heightCm() / 100.0;
        if (h <= 0) return 0;
        return weightKg() / (h * h);
    }

    public String bmiLabel() {
        double b = bmi();
        if (b < 18.5) return "偏瘦";
        if (b < 24) return "正常";
        if (b < 28) return "超重";
        return "肥胖";
    }

    public boolean highBodyFat() {
        int bf = bodyFat();
        if (bf <= 0) return false;
        if (gender() == 2) return bf >= 35;
        if (gender() == 1) return bf >= 25;
        return bf >= 30;
    }

    public int recommendedSitMinutes() {
        double b = bmi();
        int base = 45;
        if (b >= 28 || age() >= 60 || highBodyFat() || healthFlag()) base = 30;
        else if (b >= 24 || age() >= 50) base = 40;
        else if (b < 18.5) base = 50;
        if (occupation() == 0) base -= 5;
        if (occupation() == 2) base += 10;
        return clamp(base, 30, 60);
    }

    public int recommendedWinSteps() {
        if (age() >= 60 || bmi() >= 28 || highBodyFat()) return 80;
        if (age() >= 50 || bmi() >= 24) return 100;
        if (occupation() == 2) return 150;
        return 120;
    }

    public int recommendedWinMinutes() {
        return (age() >= 60 || bmi() >= 28) ? 3 : 2;
    }

    public void applyRecommendedProfile() {
        setSitMinutes(recommendedSitMinutes());
        setWinMinutes(recommendedWinMinutes());
        setWinSteps(recommendedWinSteps());
        setAutoAdaptive(true);
        setQuietEnabled(true);
        setOnboarded(true);
    }

    public long lastBreak() { return sp.getLong("lastBreak", System.currentTimeMillis()); }
    public void setLastBreak(long v) { sp.edit().putLong("lastBreak", v).apply(); }

    public long lastAlert() { return sp.getLong("lastAlert", 0); }
    public void setLastAlert(long v) { sp.edit().putLong("lastAlert", v).apply(); }

    public int today(String key) { return sp.getInt("t_" + day + "_" + key, 0); }
    public void incToday(String key) { sp.edit().putInt("t_" + day + "_" + key, today(key) + 1).apply(); }
    public void addToday(String key, int amount) {
        sp.edit().putInt("t_" + day + "_" + key, today(key) + amount).apply();
    }

    public int statForDate(String date, String key) { return sp.getInt("t_" + date + "_" + key, 0); }

    /** 记录一次当天自适应快照（多次调用会更新为当天最新值）。 */
    public void snapshotAdaptive() {
        String d = keyFor(System.currentTimeMillis());
        sp.edit()
                .putInt("adp_" + d + "_target", effectiveSitMinutes())
                .putInt("adp_" + d + "_score", pressure())
                .putInt("adp_" + d + "_steps", effectiveWinSteps())
                .apply();
    }

    public int adaptiveTargetForDate(String date) { return sp.getInt("adp_" + date + "_target", 0); }
    public int adaptiveScoreForDate(String date) { return sp.getInt("adp_" + date + "_score", 0); }
    public int adaptiveStepsForDate(String date) { return sp.getInt("adp_" + date + "_steps", 0); }

    public int sumLast(String key, int days) {
        long now = System.currentTimeMillis();
        int s = 0;
        for (int i = 0; i < days; i++) s += statForDate(keyFor(now - i * 86400000L), key);
        return s;
    }

    public static String keyFor(long time) {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(time));
    }

    public int alertLevel() { return Math.max(0, sp.getInt("alertLevel", 0)); }
    public void setAlertLevel(int v) { sp.edit().putInt("alertLevel", Math.max(0, v)).apply(); }

    public long lastPreAlert() { return sp.getLong("lastPreAlert", 0); }
    public void setLastPreAlert(long v) { sp.edit().putLong("lastPreAlert", v).apply(); }

    public boolean quietEnabled() { return sp.getBoolean("quietEnabled", false); }
    public void setQuietEnabled(boolean v) { sp.edit().putBoolean("quietEnabled", v).apply(); }

    public int quietStart() { return clamp(sp.getInt("quietStart", 23), 0, 23); }
    public void setQuietStart(int v) { sp.edit().putInt("quietStart", clamp(v, 0, 23)).apply(); }

    public int quietEnd() { return clamp(sp.getInt("quietEnd", 8), 0, 23); }
    public void setQuietEnd(int v) { sp.edit().putInt("quietEnd", clamp(v, 0, 23)).apply(); }

    public boolean inQuietHours() {
        if (!quietEnabled()) return false;
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        int s = quietStart();
        int e = quietEnd();
        if (s == e) return false;
        if (s < e) return h >= s && h < e;
        return h >= s || h < e;
    }

    public int streakDays() {
        int streak = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 365; i++) {
            if (statForDate(keyFor(now - i * 86400000L), "breaks") > 0) streak++;
            else break;
        }
        return streak;
    }

    /** 自适应压力值 0-10：EMA 平滑后的总体响应水平，越高表示近期响应越好。 */
    public int pressure() { return clamp(sp.getInt("pressure", 5), 0, 10); }

    public int recordMiss() {
        int v = (int) Math.round(pressure() * 0.65); // 向 0 平滑
        sp.edit().putInt("pressure", clamp(v, 0, 10)).apply();
        return v;
    }

    public int recordSuccess() {
        int v = (int) Math.round(pressure() + (10 - pressure()) * 0.35); // 向 10 平滑
        sp.edit().putInt("pressure", clamp(v, 0, 10)).apply();
        return v;
    }

    /** 昼夜节律四时段：0=夜间 0-5，1=上午 6-11，2=下午 12-17，3=晚上 18-23。 */
    public int daypartOfHour(int hour) {
        if (hour < 6) return 0;
        if (hour < 12) return 1;
        if (hour < 18) return 2;
        return 3;
    }

    public int currentDaypart() {
        return daypartOfHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    public int daypartScore(int daypart) {
        return clamp(sp.getInt("dscore_" + daypart, 5), 0, 10);
    }

    public void adjustDaypart(int hour, double target, double alpha) {
        int dp = daypartOfHour(hour);
        int cur = daypartScore(dp);
        int nv = (int) Math.round(cur + (target - cur) * alpha);
        sp.edit().putInt("dscore_" + dp, clamp(nv, 0, 10)).apply();
    }

    /** 近 7 天提醒响应率（百分比）；样本少于 5 次返回 -1。 */
    public int last7CompliancePct() {
        int ok = sumLast("respOk", 7);
        int miss = sumLast("respMiss", 7);
        int n = ok + miss;
        if (n < 5) return -1;
        return (int) Math.round(ok * 100.0 / n);
    }

    /** 某小时时段的历史配合度 0-10，初始 5 分（保留用于免打扰建议）。 */
    public int hourScore(int hour) { return clamp(sp.getInt("hscore_" + hour, 5), 0, 10); }

    public void adjustHourScore(int hour, int delta) {
        sp.edit().putInt("hscore_" + hour, clamp(hourScore(hour) + delta, 0, 10)).apply();
    }

    public int currentHourScore() {
        return hourScore(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    public int lastAlertHour() { return clamp(sp.getInt("lastAlertHour", -1), -1, 23); }
    public void setLastAlertHour(int h) { sp.edit().putInt("lastAlertHour", h).apply(); }

    private int globalAdjust() {
        int c = last7CompliancePct();
        if (c >= 0) {
            if (c < 50) return -5;
            if (c > 85) return 5;
            return 0;
        }
        if (pressure() <= 3) return 5;
        if (pressure() >= 7) return -10;
        return 0;
    }

    private int daypartAdjust() {
        int ds = daypartScore(currentDaypart());
        if (ds <= 3) return -5;
        if (ds >= 7) return 5;
        return 0;
    }

    /** 实际久坐时长：以指南推荐的 30-90 分钟为界，按总体响应率 + 当前时段响应水平调整。 */
    public int effectiveSitMinutes() {
        if (!autoAdaptive()) return sitMinutes();
        int base = clamp(sitMinutes(), 30, 90);
        return clamp(base + globalAdjust() + daypartAdjust(), 30, 90);
    }

    /** 实际活动窗口：表现差时放宽到 3 分钟，降低完成难度。 */
    public int effectiveWinMinutes() {
        if (!autoAdaptive()) return winMinutes();
        boolean hard = pressure() <= 3 || daypartScore(currentDaypart()) <= 3;
        return hard ? Math.max(3, winMinutes()) : winMinutes();
    }

    /** 实际最低步数：表现差时降低 20 步；表现好时提高 10 步，保持轻体力活动水平。 */
    public int effectiveWinSteps() {
        if (!autoAdaptive()) return winSteps();
        boolean hard = pressure() <= 3 || daypartScore(currentDaypart()) <= 3;
        boolean easy = pressure() >= 7 && daypartScore(currentDaypart()) >= 7;
        if (hard) return clamp(winSteps() - 20, 60, 1000);
        if (easy) return clamp(winSteps() + 10, 60, 300);
        return winSteps();
    }

    /** 实际重复提醒间隔：3/5/8 分钟三档。 */
    public int effectiveRepeatMinutes() {
        if (!autoAdaptive()) return repeatMinutes();
        if (pressure() <= 3) return Math.max(3, repeatMinutes() - 2);
        if (pressure() >= 7) return 3;
        return Math.max(3, repeatMinutes());
    }

    /** 提前预警量：5/10/15 分钟三档。 */
    public int preLeadMinutes() {
        if (!autoAdaptive()) return 10;
        if (pressure() <= 3) return 5;
        if (pressure() >= 7 || daypartScore(currentDaypart()) <= 3) return 15;
        return 10;
    }

    /** 扫描 24 小时历史评分，找到连续低响应时段（长度至少 3 小时）。返回 {start,end}，无则 null。 */
    public int[] suggestQuietHours() {
        int[] score = new int[48];
        for (int i = 0; i < 48; i++) score[i] = hourScore(i % 24);
        int bestLen = 0, bestStart = -1, bestEnd = -1;
        int i = 0;
        while (i < 48) {
            if (score[i] <= 3) {
                int s = i;
                while (i < 48 && score[i] <= 3) i++;
                int e = i - 1;
                int len = e - s + 1;
                if (len >= 3 && len <= 12 && len > bestLen) {
                    bestLen = len;
                    bestStart = s % 24;
                    bestEnd = e % 24;
                }
            } else {
                i++;
            }
        }
        if (bestStart < 0) return null;
        return new int[]{bestStart, bestEnd};
    }

    /** 近 7 天平均一次“坐多久后起来活动”。 */
    public int avgSitMinutesLast7() {
        int sec = sumLast("sitSumSec", 7);
        int cnt = sumLast("sitCount", 7);
        if (cnt <= 0 || sec <= 0) return 0;
        return (int) (sec / cnt / 60);
    }

    /** 根据历史坐姿时长给出的建议目标。 */
    public int suggestedSitMinutes() {
        int avg = avgSitMinutesLast7();
        if (avg <= 0) return sitMinutes();
        return clamp(avg + 5, 20, 120);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
