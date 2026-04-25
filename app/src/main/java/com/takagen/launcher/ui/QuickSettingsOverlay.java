package com.takagen.launcher.ui;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.DrawableRes;

import com.takagen.launcher.R;
import com.takagen.launcher.Setting;
import com.takagen.launcher.databinding.ActivityQuickSettingsBinding;
import com.takagen.launcher.databinding.ItemQuickTileBinding;
import com.takagen.launcher.service.AccessibilityKeyDetector;
import com.takagen.launcher.utils.Clock;

import java.util.List;

/**
 * Google-TV-style quick-settings card hosted inside {@link MainActivity}.
 *
 * <p>Rather than launching a separate activity (which dragged the whole screen
 * into an activity transition), the panel lives as an {@code <include>} inside
 * {@code activity_main.xml}. {@link #show()} / {@link #hide()} animate just
 * the right-side panel (slide) and the dim scrim (fade), so the launcher —
 * wallpaper, clock, app rows — stays visible through the dim the entire time.
 *
 * <p>"All settings" in the header forwards to the full {@link SettingsActivity}.
 */
public class QuickSettingsOverlay {

    static final int REQ_DEFAULT_LAUNCHER = 101;

    private static final long ANIM_IN_MS  = 260L;
    private static final long ANIM_OUT_MS = 220L;

    private final Activity host;
    private final ActivityQuickSettingsBinding binding;

    private Clock dateClock;
    private Clock timeClock;
    private boolean shown;
    private View previousFocus;

    /** Main-page container whose descendants should not receive D-pad focus
     *  while the card is open. Set by {@link #setBackgroundContainer(ViewGroup)}. */
    private ViewGroup backgroundContainer;
    private int backgroundFocusabilityOriginal = ViewGroup.FOCUS_AFTER_DESCENDANTS;

    public QuickSettingsOverlay(Activity host, ActivityQuickSettingsBinding binding) {
        this.host    = host;
        this.binding = binding;

        binding.qsRoot.setVisibility(View.GONE);

        // Tap outside the panel dismisses the card.
        binding.qsRoot.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                // Only dismiss when the tap landed outside the panel.
                float x = ev.getX();
                float y = ev.getY();
                int[] loc = new int[2];
                binding.qsPanel.getLocationInWindow(loc);
                boolean insidePanel =
                        x >= loc[0] && x <= loc[0] + binding.qsPanel.getWidth()
                     && y >= loc[1] && y <= loc[1] + binding.qsPanel.getHeight();
                if (!insidePanel) {
                    hide();
                    return true;
                }
            }
            return false;
        });

        // Clocks — date format matches MainActivity ("dd MMMM\nEEEE"), time uses 12-hour.
        android.content.Context ctx = binding.getRoot().getContext();
        dateClock = Clock.create().view(binding.qsDate).format(ctx.getString(R.string.main_date2));
        timeClock = Clock.create().view(binding.qsTime).format("h:mm a");

        bindHeader();
        bindTiles();
        bindBottomSettings();
    }

    // ── show / hide ───────────────────────────────────────────────────────────

    /**
     * Register the main-page container. Its descendant-focusability is flipped
     * to {@link ViewGroup#FOCUS_BLOCK_DESCENDANTS} while the card is visible so
     * D-pad navigation stays inside the panel instead of leaking through to the
     * app rows behind.
     */
    public void setBackgroundContainer(ViewGroup container) {
        this.backgroundContainer = container;
    }

    public boolean isShowing() {
        return shown;
    }

    public void show() {
        if (shown) return;
        shown = true;
        previousFocus = host.getCurrentFocus();

        // Trap D-pad focus inside the card while it's up.
        if (backgroundContainer != null) {
            backgroundFocusabilityOriginal = backgroundContainer.getDescendantFocusability();
            backgroundContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }

        binding.qsRoot.setVisibility(View.VISIBLE);

        // Dim fades in from 0 → 1.
        binding.qsDim.setAlpha(0f);
        binding.qsDim.animate().alpha(1f).setDuration(ANIM_IN_MS).start();

        // Panel slides in from the right edge.
        final View panel = binding.qsPanel;
        panel.setTranslationX(panel.getWidth() > 0 ? panel.getWidth() : 420f * host.getResources().getDisplayMetrics().density);
        panel.animate()
                .translationX(0f)
                .setDuration(ANIM_IN_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withStartAction(() -> {
                    updateDefaultLauncherStatus();
                    updateAutoStartStatus();
                    if (dateClock != null) dateClock.start();
                    if (timeClock != null) timeClock.start();
                    // Initial focus → the "All settings" header button.
                    binding.qsAllSettings.post(binding.qsAllSettings::requestFocus);
                })
                .start();
    }

    public void hide() {
        if (!shown) return;
        shown = false;

        // Release the focus trap so the launcher is navigable again.
        if (backgroundContainer != null) {
            backgroundContainer.setDescendantFocusability(backgroundFocusabilityOriginal);
        }

        binding.qsDim.animate()
                .alpha(0f)
                .setDuration(ANIM_OUT_MS)
                .start();

        final View panel = binding.qsPanel;
        panel.animate()
                .translationX(panel.getWidth())
                .setDuration(ANIM_OUT_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    binding.qsRoot.setVisibility(View.GONE);
                    if (dateClock != null) dateClock.stop();
                    if (timeClock != null) timeClock.stop();
                    if (previousFocus != null) previousFocus.requestFocus();
                })
                .start();
    }

    public void release() {
        if (dateClock != null) { dateClock.release(); dateClock = null; }
        if (timeClock != null) { timeClock.release(); timeClock = null; }
    }

    /** MainActivity forwards the default-launcher activity result back here. */
    public void onActivityResult(int requestCode) {
        if (requestCode == REQ_DEFAULT_LAUNCHER) updateDefaultLauncherStatus();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void bindHeader() {
        binding.qsAllSettings.setOnClickListener(v -> {
            SettingsActivity.start(host);
            hide();
        });
    }

    // ── 8 shortcut tiles ──────────────────────────────────────────────────────

    private void bindTiles() {
        String appSettings  = host.getString(R.string.qs_tile_app_settings);
        String wifi         = host.getString(R.string.qs_tile_wifi);
        String moreSettings = host.getString(R.string.qs_tile_more_settings);
        String accessibility = host.getString(R.string.qs_tile_accessibility);
        String hdmi1        = host.getString(R.string.qs_tile_hdmi1);
        String hdmi2        = host.getString(R.string.qs_tile_hdmi2);
        String hdmi3        = host.getString(R.string.qs_tile_hdmi3);
        String composite    = host.getString(R.string.qs_tile_composite);

        bindTile(binding.tileAppSettings,    R.drawable.ic_home_setting,      appSettings,
                v -> launchSettings(appSettings,
                        new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                        new Intent(Settings.ACTION_APPLICATION_SETTINGS),
                        new Intent(Settings.ACTION_SETTINGS)));

        bindTile(binding.tileWifi,           R.drawable.ic_home_network_wifi, wifi,
                v -> launchSettings(wifi,
                        new Intent(Settings.ACTION_WIFI_SETTINGS),
                        new Intent(Settings.ACTION_WIRELESS_SETTINGS),
                        new Intent(Settings.ACTION_SETTINGS)));

        bindTile(binding.tileSystemSettings, R.drawable.ic_home_setting,      moreSettings,
                v -> launchSettings(moreSettings,
                        new Intent(Settings.ACTION_SETTINGS)));

        bindTile(binding.tileAccessibility,  R.drawable.ic_home_setting,      accessibility,
                v -> launchSettings(accessibility,
                        new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                        new Intent(Settings.ACTION_SETTINGS)));

        bindTile(binding.tileHdmi1,     R.drawable.input_hdmi,      hdmi1,      v -> switchTvInput(hdmi1));
        bindTile(binding.tileHdmi2,     R.drawable.input_hdmi,      hdmi2,      v -> switchTvInput(hdmi2));
        bindTile(binding.tileHdmi3,     R.drawable.input_hdmi,      hdmi3,      v -> switchTvInput(hdmi3));
        bindTile(binding.tileComposite, R.drawable.input_composite, composite,  v -> switchTvInput(composite));
    }

    /** Populate one <include>d tile's icon + label and wire its click. */
    private void bindTile(ItemQuickTileBinding tile, @DrawableRes int iconRes,
                          String label, View.OnClickListener onClick) {
        tile.tileIcon.setImageResource(iconRes);
        tile.tileLabel.setText(label);
        tile.getRoot().setOnClickListener(onClick);
    }

    /**
     * Try a chain of candidate Settings intents, launching the first one the
     * device can actually resolve. Because this launcher is (often) set as the
     * default HOME activity, sub-activities launched without {@code FLAG_ACTIVITY_NEW_TASK}
     * can end up running inside the launcher's task and then "return" straight
     * back here — that's why the user sees Home-app bounce-back. Launching into
     * a fresh task and verifying resolution avoids both problems.
     *
     * @param label     Human-readable name for the toast fallback.
     * @param candidates Ordered intents to try. The first one whose activity
     *                   resolves against the device's PackageManager is launched.
     */
    private void launchSettings(String label, Intent... candidates) {
        PackageManager pm = host.getPackageManager();
        for (Intent intent : candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(pm) != null) {
                try {
                    // Tell the accessibility Home-guardian service that the user
                    // is intentionally navigating away, so it doesn't slam TTV
                    // back to the foreground as soon as Settings opens.
                    AccessibilityKeyDetector.noteUserDepartedTTV();
                    host.startActivity(intent);
                    hide();
                    return;
                } catch (Exception ignored) {
                    // Try the next candidate.
                }
            }
        }
        Toast.makeText(host, host.getString(R.string.qs_not_available, label),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Best-effort switch to a TV passthrough input by label match (e.g. "HDMI 1").
     * Works on stock Android TV builds that expose the input via {@link TvInputManager}.
     * Falls back to a toast on devices that don't expose a matching input.
     */
    private void switchTvInput(String hint) {
        try {
            TvInputManager tim = (TvInputManager) host.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                List<TvInputInfo> list = tim.getTvInputList();
                String needle = hint.toLowerCase().replace(" ", "");
                for (TvInputInfo info : list) {
                    if (!info.isPassthroughInput()) continue;
                    CharSequence label = info.loadLabel(host);
                    if (label == null) continue;
                    String hay = label.toString().toLowerCase().replace(" ", "");
                    if (hay.contains(needle)) {
                        Intent i = new Intent(Intent.ACTION_VIEW,
                                TvContract.buildChannelUriForPassthroughInput(info.getId()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        AccessibilityKeyDetector.noteUserDepartedTTV();
                        host.startActivity(i);
                        hide();
                        return;
                    }
                }
            }
        } catch (Exception ignored) { }
        Toast.makeText(host, host.getString(R.string.qs_input_not_available, hint),
                Toast.LENGTH_SHORT).show();
    }

    // ── Bottom settings (moved from SettingsActivity) ─────────────────────────

    private void bindBottomSettings() {
        updateAutoStartStatus();
        updateDefaultLauncherStatus();
        binding.tvRowHeightText.setText(Setting.getTvRowDp() + "dp");
        binding.mobileRowHeightText.setText(Setting.getMobileRowDp() + "dp");

        binding.autoStart.setOnClickListener(v -> {
            Setting.putAutoStart(!Setting.getAutoStart());
            updateAutoStartStatus();
            Toast.makeText(host,
                    Setting.getAutoStart()
                            ? host.getString(R.string.qs_auto_start_on_toast)
                            : host.getString(R.string.qs_auto_start_off_toast),
                    Toast.LENGTH_SHORT).show();
        });

        binding.defaultLauncher.setOnClickListener(v -> requestDefaultLauncher());

        binding.tvRowHeight.setOnClickListener(v -> {
            int idx = (Setting.getTvRowIndex() + 1) % Setting.TV_ROW_HEIGHTS.length;
            Setting.putTvRowIndex(idx);
            binding.tvRowHeightText.setText(Setting.TV_ROW_HEIGHTS[idx] + "dp");
        });

        binding.mobileRowHeight.setOnClickListener(v -> {
            int idx = (Setting.getMobileRowIndex() + 1) % Setting.MOB_ROW_HEIGHTS.length;
            Setting.putMobileRowIndex(idx);
            binding.mobileRowHeightText.setText(Setting.MOB_ROW_HEIGHTS[idx] + "dp");
        });
    }

    private void updateAutoStartStatus() {
        binding.autoStartText.setText(
                Setting.getAutoStart() ? host.getString(R.string.on) : host.getString(R.string.off));
    }

    // ── Default TV Launcher (unchanged behaviour) ─────────────────────────────

    private void requestDefaultLauncher() {
        if (isDefaultLauncher()) {
            openHomePicker();
        } else {
            setAsDefaultLauncher();
        }
    }

    private void setAsDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = host.getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                AccessibilityKeyDetector.noteUserDepartedTTV();
                host.startActivityForResult(
                        rm.createRequestRoleIntent(RoleManager.ROLE_HOME),
                        REQ_DEFAULT_LAUNCHER);
                return;
            }
        }
        openHomePicker();
    }

    private void openHomePicker() {
        try {
            Intent homeSettings = new Intent("android.settings.HOME_SETTINGS");
            homeSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            AccessibilityKeyDetector.noteUserDepartedTTV();
            host.startActivity(homeSettings);
            return;
        } catch (Exception ignored) { }

        try {
            host.getPackageManager().clearPackagePreferredActivities(host.getPackageName());
        } catch (Exception ignored) { }

        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        host.startActivity(home);
    }

    private boolean isDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = host.getSystemService(RoleManager.class);
            if (rm != null) return rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = host.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo.packageName.equals(host.getPackageName());
    }

    private void updateDefaultLauncherStatus() {
        binding.defaultLauncherText.setText(isDefaultLauncher()
                ? host.getString(R.string.qs_default_launcher_active)
                : host.getString(R.string.qs_default_launcher_set));
    }
}
