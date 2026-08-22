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
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private Prefs p;
    private TextView tvStatus, tvDetail, tvHomeBreaks, tvHomeAlerts, tvHomeHint;
    private Button btnStart;
    private ProgressBar progress;
    private BroadcastReceiver r;
    private boolean pendingStart;
    private boolean moving;
    private boolean sleeping;

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
        btnStart = findViewById(R.id.btnStart);
        new SideNav(this);

        btnStart.setOnClickListener(v -> {
            if (MonitorService.running) stopMonitoring();
            else startMonitoring();
        });
        findViewById(R.id.btnTest).setOnClickListener(v -> testAlert());

        r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Prefs.ACTION_BREAK.equals(intent.getAction())) {
                    toast("已记录有效活动，计时清零");
                }
                moving = intent.getBooleanExtra("moving", moving);
                sleeping = intent.getBooleanExtra("sleeping", sleeping);
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

        int brown = getColor(R.color.accent_brown);
        int orange = getColor(R.color.accent_orange);
        int orangeDeep = getColor(R.color.accent_orange_deep);
        int dark = getColor(R.color.text_title);
        int progressColor = brown;
        int statColor = dark;

        String detail;
        int prog;
        btnStart.setText(running ? "停止监测" : "开始监测");
        if (!running) {
            tvStatus.setText("未开始");
            tvStatus.setTextColor(dark);
            detail = "开启后开始累计久坐时间";
            tvDetail.setTextColor(dark);
            tvDetail.setText(detail);
            prog = 0;
        } else if (sleeping) {
            tvStatus.setText("睡眠中");
            tvStatus.setTextColor(dark);
            detail = "检测到你在休息，久坐计时与提醒已暂停";
            tvDetail.setTextColor(dark);
            tvDetail.setText(detail);
            prog = 0;
            statColor = brown;
        } else if (moving) {
            tvStatus.setText("正在活动");
            tvStatus.setTextColor(getColor(R.color.accent_soft_green));
            detail = "检测到你在走动，久坐计时顺延中";
            tvDetail.setTextColor(dark);
            tvDetail.setText(detail);
            prog = 0;
            statColor = brown;
        } else {
            float ratio = sitMs > 0 ? (float) el / (float) sitMs : 0f;
            boolean near = ratio >= 0.8f;
            int keyColor = near ? orange : brown;
            statColor = keyColor;
            progressColor = keyColor;

            if (p.lastAlert() > 0) {
                long sinceAlert = Math.max(0, System.currentTimeMillis() - p.lastAlert());
                String wait = (int) (sinceAlert / 60000L) + " 分 "
                        + (int) ((sinceAlert / 1000L) % 60L) + " 秒";
                tvStatus.setText("提醒已发出");
                tvStatus.setTextColor(orangeDeep);
                tvDetail.setTextColor(orangeDeep);
                detail = "等待起身 " + wait;
                renderDetail(detail, wait, orangeDeep);
                prog = 1000;
                progressColor = orangeDeep;
                statColor = orange;
            } else if (el >= sitMs) {
                String over = mm + " 分钟";
                tvStatus.setText("已超时");
                tvStatus.setTextColor(orangeDeep);
                tvDetail.setTextColor(orangeDeep);
                detail = "已超时 " + over + "，起来活动一下";
                renderDetail(detail, over, orangeDeep);
                prog = 1000;
                progressColor = orangeDeep;
                statColor = orange;
            } else {
                String elapsed = mm + " 分 " + ss + " 秒";
                tvStatus.setText("正在监测");
                tvStatus.setTextColor(near ? orange : dark);
                tvDetail.setTextColor(dark);
                detail = "已静坐 " + elapsed + "，目标 " + p.effectiveSitMinutes() + " 分钟";
                renderDetail(detail, elapsed, keyColor);
                prog = (int) Math.min(1000, el * 1000 / Math.max(1, sitMs));
            }
        }
        progress.setMax(1000);
        setProgressColor(progressColor);
        progress.setProgress(prog);
        tvHomeBreaks.setText(running ? String.valueOf(p.today("breaks")) : "—");
        tvHomeAlerts.setText(running ? String.valueOf(p.today("alerts")) : "—");
        tvHomeBreaks.setTextColor(statColor);
        tvHomeAlerts.setTextColor(statColor);
        boolean changed = p.autoAdaptive()
                && (p.effectiveSitMinutes() != p.sitMinutes()
                || p.effectiveWinSteps() != p.winSteps()
                || p.effectiveWinMinutes() != p.winMinutes());
        String adaptive = changed ? "（当前标准已自动调整）" : "";
        tvHomeHint.setText("有效活动：" + p.effectiveWinMinutes() + " 分钟内累计 "
                + p.effectiveWinSteps() + " 步" + adaptive + "。拿手机、短距离走动不计入。");
    }

    private void setProgressColor(int color) {
        if (!(progress.getProgressDrawable() instanceof LayerDrawable)) return;
        LayerDrawable layers = (LayerDrawable) progress.getProgressDrawable();
        ClipDrawable clip = (ClipDrawable) layers.findDrawableByLayerId(android.R.id.progress);
        if (clip != null && clip.getDrawable() instanceof GradientDrawable) {
            ((GradientDrawable) clip.getDrawable()).setColor(color);
        }
        GradientDrawable track =
                (GradientDrawable) layers.findDrawableByLayerId(android.R.id.background);
        if (track != null) {
            track.setColor(getColor(R.color.progress_track));
        }
    }

    private void renderDetail(String text, String hot, int color) {
        SpannableString s = new SpannableString(text);
        int start = text.indexOf(hot);
        if (start >= 0) {
            s.setSpan(new ForegroundColorSpan(color), start, start + hot.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new StyleSpan(Typeface.BOLD), start, start + hot.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvDetail.setText(s);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
