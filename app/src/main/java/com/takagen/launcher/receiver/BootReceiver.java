package com.takagen.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.takagen.launcher.Setting;
import com.takagen.launcher.service.BootForegroundService;
import com.takagen.launcher.ui.MainActivity;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);

        if (!isBoot) return;

        // If TTV is set as the default HOME launcher, the system launches it
        // automatically on boot — no action needed here.
        // This path is only for the "auto-start" override when TTV is NOT the default.
        if (!Setting.getAutoStart()) return;

        // On Android 10+ (API 29+) we can start an Activity directly from a
        // BroadcastReceiver when the device has just booted — this is one of the
        // explicit exemptions to the background activity start restriction.
        // Skipping the service entirely removes ~1-3 seconds of latency.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(launch);
        } else {
            // Pre-Q: must go through a foreground service to start an activity
            // from the background. Use startForegroundService on O+.
            Intent serviceIntent = new Intent(context, BootForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}