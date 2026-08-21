package com.sedentary.reminder;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private Prefs p;
    private SeekBar sbSit, sbWin, sbSteps;
    private EditText etSit, etWin, etSteps, etQuietStart, etQuietEnd;
    private TextView tvSitL, tvWinL, tvStepsL, tvCheck, tvAdaptive, tvQuietSuggest, tvProfile;
    private CheckBox cbQuiet, cbAdaptive;
    private Button btnUseSuggest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        p = new Prefs(this);
        new SideNav(this);
        ((TextView) findViewById(R.id.tvTitle)).setText("设置");

        sbSit = findViewById(R.id.sbSit);
        sbWin = findViewById(R.id.sbWin);
        sbSteps = findViewById(R.id.sbSteps);
        etSit = findViewById(R.id.etSit);
        etWin = findViewById(R.id.etWin);
        etSteps = findViewById(R.id.etSteps);
        tvSitL = findViewById(R.id.tvSitL);
        tvWinL = findViewById(R.id.tvWinL);
        tvStepsL = findViewById(R.id.tvStepsL);
        tvCheck = findViewById(R.id.tvCheck);
        cbQuiet = findViewById(R.id.cbQuiet);
        etQuietStart = findViewById(R.id.etQuietStart);
        etQuietEnd = findViewById(R.id.etQuietEnd);
        cbAdaptive = findViewById(R.id.cbAdaptive);
        tvAdaptive = findViewById(R.id.tvAdaptive);
        tvQuietSuggest = findViewById(R.id.tvQuietSuggest);
        btnUseSuggest = findViewById(R.id.btnUseSuggest);
        tvProfile = findViewById(R.id.tvProfile);

        cbQuiet.setChecked(p.quietEnabled());
        etQuietStart.setText(String.valueOf(p.quietStart()));
        etQuietEnd.setText(String.valueOf(p.quietEnd()));
        cbAdaptive.setChecked(p.autoAdaptive());
        cbAdaptive.setOnCheckedChangeListener((btn, checked) -> {
            p.setAutoAdaptive(checked);
            restartService();
            updateUi();
        });

        sbSit.setProgress(p.sitMinutes() - 15);
        sbWin.setProgress(p.winMinutes() - 1);
        sbSteps.setProgress(p.winSteps() - 20);
        refreshInputs();

        sbSit.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar sk, int v, boolean fromUser) {
                tvSitL.setText((v + 15) + " 分钟");
            }
            @Override public void onStopTrackingTouch(SeekBar sk) {
                p.setSitMinutes(sk.getProgress() + 15);
                refreshInputs();
                restartService();
                updateUi();
            }
        });
        sbWin.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar sk, int v, boolean fromUser) {
                tvWinL.setText((v + 1) + " 分钟");
            }
            @Override public void onStopTrackingTouch(SeekBar sk) {
                p.setWinMinutes(sk.getProgress() + 1);
                refreshInputs();
                updateUi();
            }
        });
        sbSteps.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar sk, int v, boolean fromUser) {
                tvStepsL.setText((v + 20) + " 步");
            }
            @Override public void onStopTrackingTouch(SeekBar sk) {
                p.setWinSteps(sk.getProgress() + 20);
                refreshInputs();
                updateUi();
            }
        });

        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivity(new Intent(this, OnboardingActivity.class)));
        findViewById(R.id.btnApply).setOnClickListener(v -> applyManualValues());
        findViewById(R.id.btnBattery).setOnClickListener(v -> requestIgnoreBattery());
        findViewById(R.id.btnAuto).setOnClickListener(v -> openAutoStart());
        findViewById(R.id.btnNotif).setOnClickListener(v -> openNotifSettings());
        findViewById(R.id.btnOverlay).setOnClickListener(v -> openOverlaySettings());
        findViewById(R.id.btnQuiet).setOnClickListener(v -> applyQuiet());
        btnUseSuggest.setOnClickListener(v -> {
            int[] r = p.suggestQuietHours();
            if (r == null) return;
            p.setQuietStart(r[0]);
            p.setQuietEnd((r[1] + 1) % 24);
            p.setQuietEnabled(true);
            cbQuiet.setChecked(true);
            etQuietStart.setText(String.valueOf(p.quietStart()));
            etQuietEnd.setText(String.valueOf(p.quietEnd()));
            updateUi();
            toast("已采用建议时段");
        });
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }

    private void refreshInputs() {
        etSit.setText(String.valueOf(p.sitMinutes()));
        etWin.setText(String.valueOf(p.winMinutes()));
        etSteps.setText(String.valueOf(p.winSteps()));
    }

    private void applyManualValues() {
        Integer sit = parseInRange(etSit, 15, 240, "久坐时长");
        Integer win = parseInRange(etWin, 1, 15, "活动窗口");
        Integer steps = parseInRange(etSteps, 20, 1000, "步数");
        if (sit == null || win == null || steps == null) return;
        p.setSitMinutes(sit);
        p.setWinMinutes(win);
        p.setWinSteps(steps);
        sbSit.setProgress(sit - 15);
        sbWin.setProgress(win - 1);
        sbSteps.setProgress(steps - 20);
        restartService();
        updateUi();
        toast("已应用");
    }

    private Integer parseInRange(EditText et, int lo, int hi, String name) {
        String s = et.getText().toString().trim();
        if (s.isEmpty()) {
            toast("请填写" + name + "（" + lo + "-" + hi + "）");
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            if (v < lo || v > hi) {
                toast(name + "超出范围：" + lo + "-" + hi);
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            toast(name + "必须是整数");
            return null;
        }
    }

    private void restartService() {
        if (MonitorService.running && p.enabled()) {
            try {
                MonitorService.start(this);
            } catch (Exception ignored) {
            }
        }
    }

    private void applyQuiet() {
        Integer s = parseInRange(etQuietStart, 0, 23, "开始时间");
        Integer e = parseInRange(etQuietEnd, 0, 23, "结束时间");
        if (s == null || e == null) return;
        p.setQuietStart(s);
        p.setQuietEnd(e);
        p.setQuietEnabled(cbQuiet.isChecked());
        updateUi();
        toast("免打扰时段已保存");
    }

    private void requestIgnoreBattery() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            toast("已忽略电池优化");
            updateUi();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e2) {
                toast("请在系统设置中搜索“电池优化”");
            }
        }
    }

    private void openAutoStart() {
        String[][] comps = {
                {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"}
        };
        for (String[] a : comps) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(a[0], a[1]));
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetails();
        toast("未找到荣耀启动管理页，请在应用详情开启自启动/后台活动");
    }

    private void openNotifSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            i.putExtra(Settings.EXTRA_CHANNEL_ID, MonitorService.CH_ALERT);
            startActivity(i);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
        }
    }

    private void updateUi() {
        double bmi = p.bmi();
        tvProfile.setText("年龄 " + p.age() + " · BMI "
                + (Math.round(bmi * 10) / 10.0) + "（" + p.bmiLabel() + "）\n"
                + "职业类型 " + new String[]{"久坐办公", "经常走动", "体力活动"}[p.occupation()]
                + " · 推荐基准：每 " + p.recommendedSitMinutes() + " 分钟提醒，"
                + p.recommendedWinMinutes() + " 分钟内 " + p.recommendedWinSteps() + " 步");
        tvSitL.setText(p.sitMinutes() + " 分钟（基准）");
        tvWinL.setText(p.winMinutes() + " 分钟（基准）");
        tvStepsL.setText(p.winSteps() + " 步（基准）");
        if (p.autoAdaptive()) {
            int avg = p.avgSitMinutesLast7();
            String hist = avg > 0 ? "近 7 天你平均坐 " + avg + " 分钟会起来活动，建议目标 "
                    + p.suggestedSitMinutes() + " 分钟。" : "数据积累后，会给出目标建议。";
            tvAdaptive.setText("当前自动生效：坐 " + p.effectiveSitMinutes() + " 分钟提醒 · "
                    + p.effectiveWinMinutes() + " 分钟内 " + p.effectiveWinSteps() + " 步 · 重复 "
                    + p.effectiveRepeatMinutes() + " 分钟\n" + hist);
        } else {
            tvAdaptive.setText("自动调整已关闭，所有数值按你手动设定执行。");
        }
        int[] r = p.suggestQuietHours();
        if (r != null) {
            tvQuietSuggest.setText("系统发现你长期在 " + r[0] + ":00 - "
                    + ((r[1] + 1) % 24) + ":00 对提醒几乎无响应，建议设为免打扰时段。");
            btnUseSuggest.setVisibility(android.view.View.VISIBLE);
        } else {
            tvQuietSuggest.setText("数据足够后，这里会自动建议适合你的免打扰时段。");
            btnUseSuggest.setVisibility(android.view.View.GONE);
        }
        tvCheck.setText(buildCheck());
    }

    private String buildCheck() {
        String notif = "✓";
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notif = "✗";
        }
        String act = "✓";
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            act = "✗";
        }
        String bat = "✓";
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null || !pm.isIgnoringBatteryOptimizations(getPackageName())) bat = "✗";
        String sensor = "✓";
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm == null || sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) == null) sensor = "✗";

        String chn = "✓";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = nm == null ? null
                : nm.getNotificationChannel(MonitorService.CH_ALERT);
        if (ch == null || ch.getImportance() == NotificationManager.IMPORTANCE_NONE) chn = "✗";

        String mode = "未知";
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            int rm = am.getRingerMode();
            mode = rm == AudioManager.RINGER_MODE_NORMAL ? "响铃"
                    : rm == AudioManager.RINGER_MODE_VIBRATE ? "震动" : "静音";
        }
        String overlay = Settings.canDrawOverlays(this) ? "✓" : "✗";
        int c = p.last7CompliancePct();
        String comp = c >= 0 ? c + "%" : "样本不足";
        return "通知 " + notif + " · 活动识别 " + act + " · 电池优化 " + bat
                + " · 计步传感器 " + sensor + "\n提醒声道 " + chn + " · 系统模式 " + mode
                + " · 悬浮提醒 " + overlay
                + "\n自适应：总体响应 " + p.pressure() + "/10 · 本时段响应 "
                + p.daypartScore(p.currentDaypart()) + "/10 · 7天响应率 " + comp
                + " · 实际目标 " + p.effectiveSitMinutes() + " 分钟";
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {
        }
    }
}
