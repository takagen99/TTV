package com.takagen.launcher.ui;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.takagen.launcher.BuildConfig;
import com.takagen.launcher.R;
import com.takagen.launcher.Setting;
import com.takagen.launcher.databinding.ActivitySettingsBinding;
import com.takagen.launcher.utils.ResUtil;
import com.takagen.launcher.utils.Utils;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_DEFAULT_LAUNCHER = 100;

    private ActivitySettingsBinding binding;

    private String[] autoWall;
    private String[] wall;
    private String[] onOff;

    // Which section is currently active in the left panel
    private boolean isShortcutsSection = false;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);
        initView();
        initEvent();
    }

    private void initView() {
        onOff = ResUtil.getStringArray(R.array.on_off);
        binding.wallText.setText((wall = ResUtil.getStringArray(R.array.select_wall))[Setting.getWall()]);
        binding.wallAutoText.setText((autoWall = onOff)[Setting.getAutoWall()]);
        binding.tmdbKeyText.setText(Setting.getTMDBKey());
        binding.tvRowHeightText.setText(Setting.getTvRowDp() + "dp");
        binding.mobileRowHeightText.setText(Setting.getMobileRowDp() + "dp");
        binding.appVersion.setText("Version " + BuildConfig.VERSION_CODE + " - " + BuildConfig.VERSION_NAME);
        updateDefaultLauncherStatus();
        updateAutoStartStatus();

        // Start on General section, focus first item in right panel
        showSection(false);
        binding.sectionMain.requestFocus();
    }

    private void initEvent() {
        // Left panel section selectors — switch right panel on focus
        binding.sectionMain.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showSection(false);
        });
        binding.sectionShortcuts.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showSection(true);
        });
        binding.sectionMain.setOnClickListener(v -> {
            showSection(false);
            binding.tmdbKey.requestFocus();
        });
        binding.sectionShortcuts.setOnClickListener(v -> {
            showSection(true);
            binding.systemAccessibiliy.requestFocus();
        });

        // General panel
        binding.wall.setOnClickListener(this::setWall);
        binding.wallAuto.setOnClickListener(this::setAutoWall);
        binding.tmdbKey.setOnClickListener(v -> binding.tmdbKeyText.requestFocus());
        binding.defaultLauncher.setOnClickListener(this::requestDefaultLauncher);
        binding.autoStart.setOnClickListener(this::toggleAutoStart);
        binding.showHideApps.setOnClickListener(v -> AppsHideActivity.start(this));
        binding.tvRowHeight.setOnClickListener(this::setTvRowHeight);
        binding.mobileRowHeight.setOnClickListener(this::setMobileRowHeight);

        binding.tmdbKeyText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Setting.putTMDBKey(String.valueOf(binding.tmdbKeyText.getText()));
                return true;
            }
            return false;
        });

        // Shortcuts panel
        binding.appSetting.setOnClickListener(this::goAppSetting);
        binding.systemSetting.setOnClickListener(this::goSystemSetting);
        binding.systemAccessibiliy.setOnClickListener(this::goSystemAccess);
    }

    // ── Section switching ──────────────────────────────────────────────────────

    private void showSection(boolean shortcuts) {
        isShortcutsSection = shortcuts;
        binding.panelMain.setVisibility(shortcuts ? View.GONE : View.VISIBLE);
        binding.panelShortcuts.setVisibility(shortcuts ? View.VISIBLE : View.GONE);
    }

    // ── General settings handlers ──────────────────────────────────────────────

    private void setWall(View v) {
        int index = Setting.getWall();
        Setting.putWall(index = index == wall.length - 1 ? 0 : ++index);
        binding.wallText.setText(wall[index]);
    }

    private void setAutoWall(View v) {
        int index = Setting.getAutoWall();
        Setting.putAutoWall(index = index == autoWall.length - 1 ? 0 : ++index);
        binding.wallAutoText.setText(autoWall[index]);
    }

    private void setTvRowHeight(View v) {
        int index = (Setting.getTvRowIndex() + 1) % Setting.TV_ROW_HEIGHTS.length;
        Setting.putTvRowIndex(index);
        binding.tvRowHeightText.setText(Setting.TV_ROW_HEIGHTS[index] + "dp");
    }

    private void setMobileRowHeight(View v) {
        int index = (Setting.getMobileRowIndex() + 1) % Setting.MOB_ROW_HEIGHTS.length;
        Setting.putMobileRowIndex(index);
        binding.mobileRowHeightText.setText(Setting.MOB_ROW_HEIGHTS[index] + "dp");
    }

    // ── Default TV Launcher ───────────────────────────────────────────────────

    private void requestDefaultLauncher(View v) {
        if (isDefaultLauncher()) {
            // Already default — offer to clear it by opening the system home-app picker.
            // Android provides no programmatic API to release the HOME role, so we send
            // the user to the system UI where they can choose a different launcher.
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
        // Fallback for older APIs: trigger the system home-app picker
        openHomePicker();
    }

    /**
     * Opens the system UI that lets the user choose or change the default home app.
     * This is the only way to release the HOME role — Android has no programmatic
     * "release role" API for HOME.
     */
    private void openHomePicker() {
        // Try the dedicated home-settings screen first (works on most Android TV builds)
        try {
            Intent homeSettings = new Intent("android.settings.HOME_SETTINGS");
            homeSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeSettings);
            return;
        } catch (Exception ignored) { }

        // Fallback: clear the default for this package then fire a HOME intent,
        // which forces the resolver to appear again.
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
        if (isDefaultLauncher()) {
            binding.defaultLauncherText.setText("Active — tap to change");
        } else {
            binding.defaultLauncherText.setText("Set as Default");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEFAULT_LAUNCHER) {
            updateDefaultLauncherStatus();
            if (isDefaultLauncher()) {
                Toast.makeText(this,
                        "TTV is now the default launcher! Enable Accessibility for full Home-key control.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ── Auto-start on Boot ────────────────────────────────────────────────────

    private void toggleAutoStart(View v) {
        boolean current = Setting.getAutoStart();
        Setting.putAutoStart(!current);
        updateAutoStartStatus();
        Toast.makeText(this,
                Setting.getAutoStart() ? "TTV will launch automatically on boot" : "Auto-start disabled",
                Toast.LENGTH_SHORT).show();
    }

    private void updateAutoStartStatus() {
        binding.autoStartText.setText(Setting.getAutoStart() ? "On" : "Off");
    }

    // ── Shortcut handlers ─────────────────────────────────────────────────────

    private void goAppSetting(View v) {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    private void goSystemSetting(View v) {
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    private void goSystemAccess(View v) {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        updateDefaultLauncherStatus();
        updateAutoStartStatus();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Utils.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Utils.hideSystemUI(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
