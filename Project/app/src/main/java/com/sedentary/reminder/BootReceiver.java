package com.sedentary.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Prefs p = new Prefs(context);
        if (!p.enabled()) return;
        try {
            MonitorService.start(context);
        } catch (Exception ignored) {
        }
        WatchdogReceiver.schedule(context);
    }
}
