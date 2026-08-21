package com.sedentary.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class WatchdogReceiver extends BroadcastReceiver {
    private static final long INTERVAL = 15 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        Prefs p = new Prefs(context);
        if (p.enabled()) {
            try {
                if (!MonitorService.running) {
                    MonitorService.start(context);
                } else {
                    long elapsed = System.currentTimeMillis() - p.lastBreak();
                    if (elapsed >= p.sitMinutes() * 60000L) {
                        MonitorService.start(context);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        schedule(context);
    }

    public static void schedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, WatchdogReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 7, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + INTERVAL;
        if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
