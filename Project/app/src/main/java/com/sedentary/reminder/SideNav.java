package com.sedentary.reminder;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SideNav {
    private final Activity a;
    private final View drawer;
    private final View scrim;
    private final float widthPx;
    private boolean open;

    public SideNav(Activity activity) {
        a = activity;
        drawer = activity.findViewById(R.id.drawer);
        scrim = activity.findViewById(R.id.scrim);
        widthPx = activity.getResources().getDisplayMetrics().density * 300f;
        if (drawer != null) drawer.setTranslationX(-widthPx);
        View menu = activity.findViewById(R.id.btnMenu);
        if (menu != null) menu.setOnClickListener(v -> toggle());
        if (scrim != null) scrim.setOnClickListener(v -> close());
        TextView version = activity.findViewById(R.id.tvVersion);
        if (version != null) {
            try {
                PackageInfo pi = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0);
                version.setText("v" + pi.versionName);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        hook(R.id.navHome, MainActivity.class);
        hook(R.id.navStats, StatsActivity.class);
        hook(R.id.navSettings, SettingsActivity.class);
    }

    private void hook(int id, Class<? extends Activity> target) {
        Button b = a.findViewById(id);
        if (b == null) return;
        if (a.getClass().equals(target)) {
            b.setTextColor(0xFF8B6F5B);
            b.setBackgroundResource(R.drawable.panel_tint);
        }
        b.setOnClickListener(v -> {
            close();
            if (!a.getClass().equals(target)) {
                a.startActivity(new Intent(a, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                a.overridePendingTransition(0, 0);
            }
        });
    }

    private void toggle() {
        if (open) close(); else open();
    }

    private void open() {
        open = true;
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        drawer.animate().translationX(0f).setDuration(220).start();
    }

    public void close() {
        open = false;
        scrim.animate().alpha(0f).setDuration(180)
                .withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        drawer.animate().translationX(-widthPx).setDuration(200).start();
    }
}
