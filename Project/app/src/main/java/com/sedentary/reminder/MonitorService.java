package com.sedentary.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Calendar;

public class MonitorService extends Service implements SensorEventListener {
    public static final String ACTION_START = "com.sedentary.reminder.START_SVC";
    public static final String ACTION_STOP = "com.sedentary.reminder.STOP_SVC";
    public static final String ACTION_TEST_ALERT = "com.sedentary.reminder.TEST_ALERT";
    public static final String CH_STATUS = "status";
    public static final String CH_ALERT = "alert_v2";
    public static final String CH_PRE = "pre";
    public static final int NOTIF_STATUS = 1;
    public static final int NOTIF_ALERT = 2;
    public static final int NOTIF_PRE = 3;

    public static volatile boolean running = false;

    private Prefs p;
    private SensorManager sm;
    private Sensor stepSensor;
    private Sensor accSensor;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Long> steps = new ArrayDeque<>();
    private PowerManager.WakeLock wl;
    private NotificationManager nm;
    private long now;
    private long lastStepAt;
    private boolean activePhase;
    private View overlayView;
    private long sessionTargetMs;
    private long lastSnapshotAt;

    public static void start(Context c) {
        Intent i = new Intent(c, MonitorService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    public static void stop(Context c) {
        Intent i = new Intent(c, MonitorService.class).setAction(ACTION_STOP);
        try {
            c.startService(i);
        } catch (Exception ignored) {
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            now = System.currentTimeMillis();
            if (now - lastSnapshotAt >= 5 * 60000L) {
                lastSnapshotAt = now;
                p.snapshotAdaptive();
            }
            checkAlert();
            updateStatus();
            sendStateBroadcast();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        p = new Prefs(this);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) {
            stepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            accSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannels();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sedentary:monitor");
            wl.setReferenceCounted(false);
            if (!wl.isHeld()) wl.acquire(60 * 60 * 1000L);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_TEST_ALERT.equals(intent.getAction())) {
            showOverlay("测试悬浮提醒", "如果你能在其他应用上方看到这一页，说明悬浮提醒已生效。");
            return START_STICKY;
        }
        if (!p.enabled()) {
            stopMonitoring();
            return START_NOT_STICKY;
        }
        startAsForeground();
        if (!running) {
            running = true;
            registerSensors();
            steps.clear();
            if (p.lastBreak() <= 0) p.setLastBreak(System.currentTimeMillis());
            recalcTarget();
        }
        h.removeCallbacks(tick);
        h.post(tick);
        return START_STICKY;
    }

    private void startAsForeground() {
        Notification n = statusNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_STATUS, n);
        }
    }

    private void registerSensors() {
        if (sm == null) return;
        try {
            if (stepSensor != null) sm.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (accSensor != null) sm.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (SecurityException ignored) {
        }
    }

    /** 每轮久坐重新计算目标，并加 ±10% 随机抖动，避免固定间隔造成习惯化。 */
    private void recalcTarget() {
        long base = p.effectiveSitMinutes() * 60000L;
        double jitter = 0.9 + Math.random() * 0.2;
        sessionTargetMs = (long) (base * jitter);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26 || nm == null) return;
        NotificationChannel st = new NotificationChannel(CH_STATUS, "监测状态", NotificationManager.IMPORTANCE_LOW);
        st.setShowBadge(false);
        NotificationChannel al = new NotificationChannel(CH_ALERT, "久坐提醒", NotificationManager.IMPORTANCE_HIGH);
        al.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        al.enableVibration(true);
        al.setVibrationPattern(new long[]{0, 600, 400, 600});
        NotificationChannel pre = new NotificationChannel(CH_PRE, "提前提醒", NotificationManager.IMPORTANCE_DEFAULT);
        pre.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        pre.enableVibration(false);
        nm.createNotificationChannel(st);
        nm.createNotificationChannel(al);
        nm.createNotificationChannel(pre);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_DETECTOR) return;
        long t = System.currentTimeMillis();
        long winMs = p.effectiveWinMinutes() * 60000L;
        steps.addLast(t);
        while (!steps.isEmpty() && t - steps.peekFirst() > winMs) steps.removeFirst();
        if (steps.size() >= p.effectiveWinSteps()) {
            recordBreak(t);
        }
        if (activePhase) {
            // 达标后仍连续走动（90 秒内有下一步）就持续顺延，不开始下一轮久坐计时
            if (t - lastStepAt <= 90 * 1000L) {
                p.setLastBreak(t);
            } else {
                activePhase = false;
            }
        }
        lastStepAt = t;
    }

    private void recordBreak(long t) {
        long since = t - p.lastBreak();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (p.lastAlert() > 0 && t - p.lastAlert() <= 10 * 60000L) {
            // 提醒后 10 分钟内完成有效活动：记为一次成功响应
            p.recordSuccess();
            p.adjustDaypart(p.lastAlertHour(), 10, 0.4);
            p.adjustHourScore(p.lastAlertHour(), 1);
            p.incToday("respOk");
        } else if (p.lastAlert() == 0) {
            // 没有提醒也主动活动：对时段评分给予较弱的正向更新
            p.adjustDaypart(hour, 8, 0.2);
            p.adjustHourScore(hour, 1);
        }
        if (since >= 60000L) {
            int boutSec = (int) Math.min(4 * 3600, since / 1000L);
            p.addToday("sitSumSec", boutSec);
            p.incToday("sitCount");
        }
        activePhase = true;
        lastStepAt = t;
        p.setLastBreak(t);
        recalcTarget();
        p.setLastAlert(0);
        p.setLastPreAlert(0);
        p.setAlertLevel(0);
        steps.clear();
        if (nm != null) {
            nm.cancel(NOTIF_ALERT);
            nm.cancel(NOTIF_PRE);
        }
        dismissOverlay();
        if (since >= 60000L) {
            p.incToday("breaks");
            sendBroadcast(new Intent(Prefs.ACTION_BREAK).setPackage(getPackageName()));
            notifyBreakComplete();
        }
        updateStatus();
        sendStateBroadcast();
    }

    private void notifyBreakComplete() {
        Intent main = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 600, main,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CH_PRE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("有效活动完成")
                .setContentText("久坐计时已重新开始")
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (nm != null) nm.notify(NOTIF_PRE, b.build());
    }

    private void checkAlert() {
        long sitMs = sessionTargetMs > 0 ? sessionTargetMs : p.effectiveSitMinutes() * 60000L;
        long elapsed = now - p.lastBreak();
        if (elapsed >= sitMs) {
            if (p.inQuietHours()) return;
            int level = p.alertLevel();
            if (level > 0 && p.lastAlert() > 0
                    && now - p.lastAlert() >= 10 * 60000L) {
                // 上一次提醒 10 分钟内没有响应：记录为未响应
                p.recordMiss();
                p.adjustDaypart(p.lastAlertHour(), 0, 0.4);
                p.adjustHourScore(p.lastAlertHour(), -1);
                p.incToday("respMiss");
            }
            long repMs = Math.max(2, p.effectiveRepeatMinutes() - level) * 60000L;
            if (p.lastAlert() == 0 || now - p.lastAlert() >= repMs) {
                p.setLastAlert(now);
                sendAlert(elapsed, level);
                p.setAlertLevel(level + 1);
            }
        } else {
            if (p.lastAlert() != 0) {
                p.setLastAlert(0);
                if (nm != null) nm.cancel(NOTIF_ALERT);
            }
            long leadMs = p.preLeadMinutes() * 60000L;
            long preAt = Math.max(sitMs / 2, sitMs - leadMs);
            if (!p.inQuietHours() && elapsed >= preAt
                    && now - p.lastPreAlert() >= 5 * 60000L) {
                p.setLastPreAlert(now);
                sendPreAlert(sitMs - elapsed);
            }
        }
    }

    private void sendPreAlert(long remainMs) {
        long remain = Math.max(1, remainMs / 60000L);
        Intent main = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 500, main,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CH_PRE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("准备起来活动一下")
                .setContentText("距离久坐提醒还有约 " + remain + " 分钟")
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (nm != null) nm.notify(NOTIF_PRE, b.build());
    }

    private void sendAlert(long elapsed, int level) {
        int mins = (int) (elapsed / 60000L);
        String title = level == 0 ? "久坐提醒" : "久坐提醒 · 第 " + (level + 1) + " 次";
        String msg = timeMessage(mins, level);
        p.setLastAlertHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        Intent full = new Intent(this, AlertActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fpi = PendingIntent.getActivity(this, 100, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent snooze = new Intent(this, SnoozeReceiver.class);
        PendingIntent spi = PendingIntent.getBroadcast(this, 400, snooze,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action act = new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat),
                "稍后提醒", spi).build();
        Notification.Builder b = new Notification.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(msg)
                .setStyle(new Notification.BigTextStyle().bigText(msg))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(fpi)
                .setFullScreenIntent(fpi, true)
                .addAction(act);
        if (nm != null) nm.notify(NOTIF_ALERT, b.build());
        showOverlay(title, msg);
        p.incToday("alerts");
        sendBroadcast(new Intent(Prefs.ACTION_ALERT).setPackage(getPackageName()));
    }

    private void showOverlay(String title, String msg) {
        if (Build.VERSION.SDK_INT < 26 || !Settings.canDrawOverlays(this)) return;
        dismissOverlay();
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) return;
            View v = LayoutInflater.from(this).inflate(R.layout.overlay_alert, null);
            ((TextView) v.findViewById(R.id.tvOverlayTitle)).setText(title);
            ((TextView) v.findViewById(R.id.tvOverlayMsg)).setText(msg);
            Button btn = v.findViewById(R.id.btnOverlayDismiss);
            btn.setOnClickListener(view -> {
                p.setLastAlert(System.currentTimeMillis());
                dismissOverlay();
            });
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
            wm.addView(v, lp);
            overlayView = v;
        } catch (Exception ignored) {
        }
    }

    private void dismissOverlay() {
        if (overlayView == null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) wm.removeView(overlayView);
        } catch (Exception ignored) {
        }
        overlayView = null;
    }

    private String timeMessage(int mins, int level) {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        String base;
        if (h >= 5 && h < 11) {
            base = "早上好，站起来接杯水，顺便伸个懒腰";
        } else if (h >= 11 && h < 14) {
            base = "中午了，饭后走动几分钟，别马上坐下";
        } else if (h >= 14 && h < 18) {
            base = "下午容易僵硬，活动一下肩颈，看看远处";
        } else if (h >= 18 && h < 23) {
            base = "晚上好，在家走两圈，放松一下腰背";
        } else {
            base = "夜深了，简单走动一下再继续";
        }
        if (level == 0) return base + "。目标：" + p.effectiveWinMinutes() + " 分钟内 " + p.effectiveWinSteps() + " 步。";
        if (level == 1) return "已经提醒过一次了。你已静坐 " + mins + " 分钟，这次认真起来走走吧。";
        return "第 " + (level + 1) + " 次提醒：你已静坐 " + mins + " 分钟。起来活动片刻："
                + p.effectiveWinMinutes() + " 分钟内累计 " + p.effectiveWinSteps() + " 步。";
    }

    private Notification statusNotification() {
        long t = System.currentTimeMillis();
        long sitMs = p.effectiveSitMinutes() * 60000L;
        long el = Math.max(0, t - p.lastBreak());
        int mm = (int) (el / 60000L);
        int ss = (int) ((el / 1000L) % 60L);
        String text;
        if (isMovingAt(t)) {
            text = "正在活动，久坐计时顺延中";
        } else if (p.lastAlert() > 0) {
            long sinceAlert = t - p.lastAlert();
            text = "提醒已发出，等待起身 "
                    + (int) (sinceAlert / 60000L) + " 分 "
                    + (int) ((sinceAlert / 1000L) % 60L) + " 秒";
        } else if (el >= sitMs && p.inQuietHours()) {
            text = "已超时 " + mm + " 分钟，免打扰时段结束后提醒";
        } else if (el >= sitMs) {
            text = "已超时 " + mm + " 分钟，请起来活动！";
        } else {
            text = "已静坐 " + mm + " 分 " + ss + " 秒 / " + p.effectiveSitMinutes() + " 分钟";
            if (p.effectiveSitMinutes() != p.sitMinutes()) text += "（已自适应提前）";
        }
        Intent main = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 200, main,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("久坐提醒监测中")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi);
        return b.build();
    }

    private void updateStatus() {
        if (nm == null) return;
        nm.notify(NOTIF_STATUS, statusNotification());
    }

    private boolean isMovingAt(long t) {
        return lastStepAt > 0 && t - lastStepAt <= 60 * 1000L;
    }

    private void sendStateBroadcast() {
        Intent i = new Intent(Prefs.ACTION_STATE).setPackage(getPackageName());
        i.putExtra("elapsed", Math.max(0, now - p.lastBreak()));
        i.putExtra("moving", isMovingAt(now));
        sendBroadcast(i);
    }

    private void stopMonitoring() {
        running = false;
        h.removeCallbacks(tick);
        if (sm != null) sm.unregisterListener(this);
        if (wl != null && wl.isHeld()) wl.release();
        if (nm != null) {
            nm.cancel(NOTIF_STATUS);
            nm.cancel(NOTIF_ALERT);
            nm.cancel(NOTIF_PRE);
        }
        dismissOverlay();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
