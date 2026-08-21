package com.sedentary.reminder;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private Prefs p;
    private TextView tvStatus, tvDetail, tvHomeBreaks, tvHomeAlerts, tvHomeHint;
    private ProgressBar progress;
    private BroadcastReceiver r;
    private boolean pendingStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        p = new Prefs(this);
        tvStatus = findViewById(R.id.tvStatus);
        tvDetail = findViewById(R.id.tvDetail);
        tvHomeBreaks = findViewById(R.id.tvHomeBreaks);
        tvHomeAlerts = findViewById(R.id.tvHomeAlerts);
        tvHomeHint = findViewById(R.id.tvHomeHint);
        progress = findViewById(R.id.progress);
        new SideNav(this);

        findViewById(R.id.btnStart).setOnClickListener(v -> startMonitoring());
        findViewById(R.id.btnStop).setOnClickListener(v -> stopMonitoring());
        findViewById(R.id.btnTest).setOnClickListener(v -> testAlert());

        r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Prefs.ACTION_BREAK.equals(intent.getAction())) {
                    toast("已记录有效活动，计时清零");
                }
                updateUi();
            }
        };
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!p.onboarded()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            return;
        }
        IntentFilter f = new IntentFilter();
        f.addAction(Prefs.ACTION_STATE);
        f.addAction(Prefs.ACTION_BREAK);
        f.addAction(Prefs.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(r, f);
        }
        if (p.enabled() && !MonitorService.running) {
            try {
                MonitorService.start(this);
                WatchdogReceiver.schedule(this);
            } catch (Exception ignored) {
            }
        }
        updateUi();
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(r);
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    private void startMonitoring() {
        p.setEnabled(true);
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (!need.isEmpty()) {
            pendingStart = true;
            requestPermissions(need.toArray(new String[0]), 1);
            return;
        }
        doStart();
    }

    private void doStart() {
        try {
            MonitorService.start(this);
        } catch (Exception e) {
            toast("启动失败：" + e.getMessage());
            return;
        }
        WatchdogReceiver.schedule(this);
        updateUi();
    }

    private void stopMonitoring() {
        p.setEnabled(false);
        try {
            stopService(new Intent(this, MonitorService.class));
        } catch (Exception ignored) {
        }
        sendBroadcast(new Intent(Prefs.ACTION_STOP).setPackage(getPackageName()));
        updateUi();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean all = grantResults.length > 0;
            for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) all = false;
            if (pendingStart && all) {
                pendingStart = false;
                doStart();
            } else if (pendingStart) {
                pendingStart = false;
                toast("缺少权限，无法开始监测");
            }
        }
        updateUi();
    }

    private void testAlert() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    MonitorService.CH_ALERT, "久坐提醒", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 600, 400, 600});
            nm.createNotificationChannel(ch);

            Intent full = new Intent(this, AlertActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent fpi = PendingIntent.getActivity(this, 300, full,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(this, MonitorService.CH_ALERT)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("久坐提醒：测试提醒")
                    .setContentText("这是一次测试，声音与震动跟随系统设置")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setContentIntent(fpi)
                    .setFullScreenIntent(fpi, true)
                    .build();
            nm.notify(MonitorService.NOTIF_ALERT, n);
        }
        if (MonitorService.running) {
            startService(new Intent(this, MonitorService.class)
                    .setAction(MonitorService.ACTION_TEST_ALERT));
        }
        startActivity(new Intent(this, AlertActivity.class));
    }

    private void updateUi() {
        boolean running = MonitorService.running;
        long sitMs = p.effectiveSitMinutes() * 60000L;
        long el = Math.max(0, System.currentTimeMillis() - p.lastBreak());
        int mm = (int) (el / 60000L);
        int ss = (int) ((el / 1000L) % 60L);

        tvStatus.setText(running ? "正在监测" : "未开始");
        String detail;
        if (!running) {
            detail = "开启后开始累计久坐时间";
        } else if (el >= sitMs) {
            detail = "已超时 " + mm + " 分钟，起来活动一下";
        } else {
            detail = "已静坐 " + mm + " 分 " + ss + " 秒，目标 " + p.effectiveSitMinutes() + " 分钟";
        }
        tvDetail.setText(detail);
        progress.setMax(1000);
        progress.setProgress((int) Math.min(1000, el * 1000 / Math.max(1, sitMs)));
        tvHomeBreaks.setText(running ? String.valueOf(p.today("breaks")) : "—");
        tvHomeAlerts.setText(running ? String.valueOf(p.today("alerts")) : "—");
        boolean changed = p.autoAdaptive()
                && (p.effectiveSitMinutes() != p.sitMinutes()
                || p.effectiveWinSteps() != p.winSteps()
                || p.effectiveWinMinutes() != p.winMinutes());
        String adaptive = changed ? "（当前标准已自动调整）" : "";
        tvHomeHint.setText("有效活动：" + p.effectiveWinMinutes() + " 分钟内累计 "
                + p.effectiveWinSteps() + " 步" + adaptive + "。拿手机、短距离走动不计入。");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
