package com.takagen.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.takagen.launcher.App;
import com.takagen.launcher.ui.MainActivity;

public class AccessibilityKeyDetector extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {

    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    App.post(() -> startApp(), 50);
                }
                // Consume both ACTION_DOWN and ACTION_UP so Google TV
                // launcher never gets a chance to handle the Home key.
                return true;
            }
        } catch (Exception e) {
            // ignored
        }
        return super.onKeyEvent(event);
    }

    private void startApp() {
        Intent i = new Intent(getBaseContext(), MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        startActivity(i);
    }

}
