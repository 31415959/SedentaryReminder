package com.sedentary.reminder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class AlertActivity extends Activity {
    private BroadcastReceiver r;
    private Prefs p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        p = new Prefs(this);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_alert);

        long el = Math.max(0, System.currentTimeMillis() - p.lastBreak());
        int mm = (int) (el / 60000L);
        TextView tv = findViewById(R.id.tvAlertInfo);
        String sitText = mm == 0 ? "您已静坐不到 1 分钟。" : "您已静坐约 " + mm + " 分钟。";
        tv.setText(sitText + "\n\n当前活动标准：\n"
                + p.effectiveWinMinutes() + " 分钟内累计步行 ≥ " + p.effectiveWinSteps() + " 步。\n\n"
                + "拿手机、翻身等短距离走动不计入；达标后计时自动清零并关闭提醒。");

        Button btn = findViewById(R.id.btnDismiss);
        btn.setText("知道了，" + p.effectiveRepeatMinutes() + " 分钟后再提醒");
        btn.setOnClickListener(v -> {
            p.setLastAlert(System.currentTimeMillis());
            finish();
        });

        r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if (Prefs.ACTION_BREAK.equals(a) || Prefs.ACTION_STOP.equals(a)) {
                    finish();
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Prefs.ACTION_BREAK);
        f.addAction(Prefs.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(r, f);
        }
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(r);
        } catch (Exception ignored) {
        }
        super.onPause();
    }
}
