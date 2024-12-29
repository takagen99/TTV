package com.takagen.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.takagen.launcher.App;
import com.takagen.launcher.Setting;
import com.takagen.launcher.ui.MainActivity;
import com.takagen.launcher.utils.Notify;

import java.util.List;

public class AccessibilityKeyDetector extends AccessibilityService {
    ActivityManager activityManager = null;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {

    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Exit if not intercepting Home buttont
        if (Setting.getIntercept() == 1) return false;

        try {
            int action = event.getAction();
            int keyCode = event.getKeyCode();

            if (action == KeyEvent.ACTION_UP) {
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    ActivityManager activityManager = (ActivityManager) this.getSystemService( Context.ACTIVITY_SERVICE );
                    List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
                    for(ActivityManager.RunningAppProcessInfo appProcess : appProcesses){
                        if(!(appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)){
//                            Notify.show("not current foreground App, then trigger start activity");
                        } else {
//                            Notify.show("same app");
                            MainActivity.moveToHome();
                        }
                    }
                    App.post(() -> startApp(), 50);
//                    Notify.show("AFTER PRESSEd" + this.getPackageName());
//                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//                        activityManager = (ActivityManager) getApplicationContext()
//                                .getSystemService(Context.ACTIVITY_SERVICE);
//
//                        activityManager.moveTaskToFront(getTaskId(), 0);
//                    }

                }
            }

        } catch (Exception e) {

        }
        return super.onKeyEvent(event);
    }

    private void startApp() {
//        Intent i = this.getPackageManager().
//                getLaunchIntentForPackage(this.getPackageName());
////                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
////                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
////                    i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        Intent i = new Intent(getBaseContext(), MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        startActivity(i);
    }

}
