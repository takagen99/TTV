package com.takagen.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
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

    /**
     * Timestamp (elapsed-realtime ms) of the last time the user intentionally
     * launched a non-launcher activity from inside TTV — e.g. tapping the
     * "System Settings" tile. While this timestamp is "fresh", the accessibility
     * service must NOT try to drag TTV back to the foreground, because doing so
     * would immediately close the screen the user just chose to open.
     *
     * <p>Call {@link #noteUserDepartedTTV()} before launching any settings /
     * deep-link intent from the launcher UI.
     */
    private static volatile long userDepartedAt = 0;
    private static final long USER_DEPART_GRACE_MS = 5_000;

    /** Mark the start of an intentional foreground hand-off away from TTV. */
    public static void noteUserDepartedTTV() {
        userDepartedAt = System.currentTimeMillis();
    }

    private static boolean withinUserDepartGrace() {
        long t = userDepartedAt;
        return t != 0 && (System.currentTimeMillis() - t) < USER_DEPART_GRACE_MS;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String packageName = pkg.toString();

        // Ignore our own windows
        if (OWN_PACKAGE.equals(packageName)) return;

        // The user just tapped a tile that launches a non-launcher activity
        // (Settings, Wi-Fi, etc.). Don't fight their intent.
        if (withinUserDepartGrace()) return;

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
     * Known launcher package prefixes. Kept deliberately narrow — an earlier
     * revision used {@code pm.queryIntentActivities(HOME)} to "auto-discover"
     * launchers, but on some Android TV builds non-launcher system components
     * (e.g. Settings wrapper activities) also declare a {@code CATEGORY_HOME}
     * filter, which caused this service to slam TTV back to the foreground
     * whenever the user opened Settings. Only match packages we know are
     * actually TV launchers.
     */
    private static final String[] KNOWN_LAUNCHER_PREFIXES = {
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.android.launcher",
            "com.android.launcher3",
    };

    /**
     * Returns true if the given package is a launcher/home app that is NOT TTV.
     *
     * <p>The check is two-pronged:
     * <ol>
     *   <li>If the system currently resolves {@code ACTION_MAIN + CATEGORY_HOME}
     *       to this package (i.e. it's the active default HOME) and it's not us,
     *       treat it as foreign. This catches the case where some other launcher
     *       has just stolen the HOME role.
     *   <li>Otherwise, only treat it as a launcher if its package name matches
     *       a known TV-launcher prefix. We deliberately do NOT use a generic
     *       {@code queryIntentActivities(HOME)} check here — see comment on
     *       {@link #KNOWN_LAUNCHER_PREFIXES}.
     * </ol>
     */
    private boolean isForeignLauncher(String pkg) {
        if (OWN_PACKAGE.equals(pkg)) return false;
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            PackageManager pm = getPackageManager();
            android.content.pm.ResolveInfo ri =
                    pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && pkg.equals(ri.activityInfo.packageName)) {
                return true;
            }
        } catch (Exception ignored) { }
        for (String prefix : KNOWN_LAUNCHER_PREFIXES) {
            if (pkg.startsWith(prefix)) return true;
        }
        return false;
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
