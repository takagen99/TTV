package com.takagen.launcher.ui;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.takagen.launcher.R;
import com.takagen.launcher.Setting;
import com.takagen.launcher.databinding.ActivityQuickSettingsBinding;
import com.takagen.launcher.databinding.ItemQuickTileBinding;
import com.takagen.launcher.utils.Clock;

import java.util.List;

/**
 * Google-TV-style quick-settings overlay anchored to the right edge of the screen.
 *
 * Launched when the user taps the gear in {@link MainActivity}'s top bar.
 * "All settings" in the header forwards to the full {@link SettingsActivity}.
 */
public class QuickSettingsActivity extends AppCompatActivity {

    private static final int REQ_DEFAULT_LAUNCHER = 101;

    private ActivityQuickSettingsBinding binding;

    private Clock dateClock;
    private Clock timeClock;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, QuickSettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuickSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Tap outside the panel dismisses the overlay (same as Back).
        binding.scrim.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                finish();
                return true;
            }
            return false;
        });

        // Clocks — date format matches MainActivity ("dd MMMM\nEEEE"), time uses 12-hour.
        dateClock = Clock.create().view(binding.qsDate).format("dd MMMM, EEEE");
        timeClock = Clock.create().view(binding.qsTime).format("h:mm a");

        bindHeader();
        bindTiles();
        bindBottomSettings();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void bindHeader() {
        binding.qsAllSettings.setOnClickListener(v -> {
            SettingsActivity.start(this);
            finish();
        });
    }

    // ── 8 shortcut tiles ──────────────────────────────────────────────────────

    private void bindTiles() {
        bindTile(binding.tileAppSettings,    R.drawable.ic_home_setting,      "App Settings",
                v -> openAppSettings());
        bindTile(binding.tileWifi,           R.drawable.ic_home_network_wifi, "Wi-Fi",
                v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        bindTile(binding.tileSystemSettings, R.drawable.ic_home_setting,      "System Settings",
                v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        bindTile(binding.tileAccessibility,  R.drawable.ic_home_setting,      "Accessibility",
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        bindTile(binding.tileHdmi1,     R.drawable.input_hdmi,      "HDMI 1",    v -> switchTvInput("HDMI 1"));
        bindTile(binding.tileHdmi2,     R.drawable.input_hdmi,      "HDMI 2",    v -> switchTvInput("HDMI 2"));
        bindTile(binding.tileHdmi3,     R.drawable.input_hdmi,      "HDMI 3",    v -> switchTvInput("HDMI 3"));
        bindTile(binding.tileComposite, R.drawable.input_composite, "Composite", v -> switchTvInput("Composite"));

        // Start with focus on the first tile so the overlay is D-pad navigable immediately.
        View first = binding.tileAppSettings.getRoot();
        first.post(first::requestFocus);
    }

    /** Populate one <include>d tile's icon + label and wire its click. */
    private void bindTile(ItemQuickTileBinding tile, @DrawableRes int iconRes,
                          String label, View.OnClickListener onClick) {
        tile.tileIcon.setImageResource(iconRes);
        tile.tileLabel.setText(label);
        tile.getRoot().setOnClickListener(onClick);
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    /**
     * Best-effort switch to a TV passthrough input by label match (e.g. "HDMI 1").
     * Works on stock Android TV builds that expose the input via {@link TvInputManager}.
     * Falls back to a toast on devices that don't expose a matching input.
     */
    private void switchTvInput(String hint) {
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                List<TvInputInfo> list = tim.getTvInputList();
                String needle = hint.toLowerCase().replace(" ", "");
                for (TvInputInfo info : list) {
                    if (!info.isPassthroughInput()) continue;
                    CharSequence label = info.loadLabel(this);
                    if (label == null) continue;
                    String hay = label.toString().toLowerCase().replace(" ", "");
                    if (hay.contains(needle)) {
                        Intent i = new Intent(Intent.ACTION_VIEW,
                                TvContract.buildChannelUriForPassthroughInput(info.getId()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        finish();
                        return;
                    }
                }
            }
        } catch (Exception ignored) { }
        Toast.makeText(this, hint + " input is not available on this device",
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
            Toast.makeText(this,
                    Setting.getAutoStart() ? "TTV will launch automatically on boot" : "Auto-start disabled",
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
        binding.autoStartText.setText(Setting.getAutoStart() ? "On" : "Off");
    }

    // ── Default TV Launcher (same logic as SettingsActivity) ─────────────────

    private void requestDefaultLauncher() {
        if (isDefaultLauncher()) {
            openHomePicker();
        } else {
            setAsDefaultLauncher();
        }
    }

    private void setAsDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                startActivityForResult(
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
            startActivity(homeSettings);
            return;
        } catch (Exception ignored) { }

        try {
            getPackageManager().clearPackagePreferredActivities(getPackageName());
        } catch (Exception ignored) { }

        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    private boolean isDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null) return rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo.packageName.equals(getPackageName());
    }

    private void updateDefaultLauncherStatus() {
        binding.defaultLauncherText.setText(
                isDefaultLauncher() ? "Active — tap to change" : "Set as Default");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEFAULT_LAUNCHER) updateDefaultLauncherStatus();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        if (dateClock != null) dateClock.start();
        if (timeClock != null) timeClock.start();
        updateDefaultLauncherStatus();
        updateAutoStartStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dateClock != null) dateClock.stop();
        if (timeClock != null) timeClock.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dateClock != null) dateClock.release();
        if (timeClock != null) timeClock.release();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
