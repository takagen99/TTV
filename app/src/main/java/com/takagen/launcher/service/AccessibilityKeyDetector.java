package com.takagen.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.takagen.launcher.App;
import com.takagen.launcher.ui.MainActivity;

/**
 * AccessibilityKeyDetector — Home-key guardian for Android 11+
 *
 * Strategy:
 * 1. onKeyEvent: on Android <= 10, consume KEYCODE_HOME directly. On 11+, the
 *    system acts on Home before we see it, so we call performGlobalAction(HOME)
 *    immediately after — this re-triggers HOME role resolution which points to TTV.
 *
 * 2. onAccessibilityEvent: watch for any non-TTV launcher window appearing in the
 *    foreground (TYPE_WINDOW_STATE_CHANGED). When detected, bring TTV back via
 *    startActivity. This covers cases where the accessibility service isn't fast
 *    enough in onKeyEvent.
 *
 * Google TV compatibility requirements (enforced in xml/accessibility_service.xml):
 * - No empty packageNames or settingsActivity attributes
 * - feedbackGeneric (not feedbackAllMask alone)
 * - No flagReportViewIds
 * - No canRetrieveWindowContent
 * - Non-empty android:description in manifest
 */
public class AccessibilityKeyDetector extends AccessibilityService {

    private static final String OWN_PACKAGE = "com.takagen.launcher";

    // Cooldown to avoid spamming bring-to-front calls
    private long lastBringForward = 0;
    private static final long COOLDOWN_MS = 600;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String packageName = pkg.toString();

        // Ignore our own windows
        if (OWN_PACKAGE.equals(packageName)) return;

        // Check if the package that just came to foreground is a launcher/home app
        if (isForeignLauncher(packageName)) {
            long now = System.currentTimeMillis();
            if (now - lastBringForward > COOLDOWN_MS) {
                lastBringForward = now;
                App.post(this::bringToFront, 60);
            }
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) {
            return super.onKeyEvent(event);
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: system already acted on Home. Calling performGlobalAction(HOME)
                // re-fires the HOME intent which resolves to TTV (if set as default).
                // We also schedule a direct bringToFront as a belt-and-suspenders fallback.
                performGlobalAction(GLOBAL_ACTION_HOME);
                App.post(this::bringToFront, 150);
            } else {
                // Android <= 10: we consumed the key (return true below) so the system
                // never fires. Launch directly.
                App.post(this::bringToFront, 50);
            }
        }

        // On Android <= 10, returning true actually prevents the system from acting on Home.
        // On Android 11+ returning true is a no-op for Home, but harmless.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || super.onKeyEvent(event);
    }

    /**
     * Returns true if the given package is a launcher/home app that is NOT TTV.
     * Rather than maintaining a hardcoded list, we ask the PackageManager whether
     * the package holds a HOME intent-filter — this catches OEM launchers and
     * future Google TV launcher package renames automatically.
     */
    private boolean isForeignLauncher(String pkg) {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            PackageManager pm = getPackageManager();
            // resolveActivity gives us whoever would handle HOME right now
            android.content.pm.ResolveInfo ri =
                    pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && pkg.equals(ri.activityInfo.packageName)
                    && !OWN_PACKAGE.equals(pkg)) {
                return true;
            }
            // Also check if the package declares HOME at all (it may not be the current
            // default but still be a launcher trying to surface)
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(Intent.CATEGORY_HOME);
            query.setPackage(pkg);
            return !pm.queryIntentActivities(query, 0).isEmpty();
        } catch (Exception e) {
            // Fall back to known package names if PackageManager call fails
            return pkg.startsWith("com.google.android.tvlauncher")
                    || pkg.startsWith("com.google.android.leanbacklauncher")
                    || pkg.startsWith("com.android.launcher");
        }
    }

    private void bringToFront() {
        try {
            Intent i = new Intent(getBaseContext(), MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) { }
    }
}
