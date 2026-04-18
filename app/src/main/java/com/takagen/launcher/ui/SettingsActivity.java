package com.takagen.launcher.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.takagen.launcher.BuildConfig;
import com.takagen.launcher.R;
import com.takagen.launcher.Setting;
import com.takagen.launcher.databinding.ActivitySettingsBinding;
import com.takagen.launcher.utils.Notify;
import com.takagen.launcher.utils.ResUtil;
import com.takagen.launcher.utils.Utils;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    private String[] autoWall;
    private String[] autoStart;
    private String[] wall;
    private String[] intercept;

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
        binding.wallText.setText((wall = ResUtil.getStringArray(R.array.select_wall))[Setting.getWall()]);
        binding.wallAutoText.setText((autoWall = ResUtil.getStringArray(R.array.on_off))[Setting.getAutoWall()]);
        binding.autoStartText.setText((autoStart = ResUtil.getStringArray(R.array.on_off))[Setting.getAutoStart()]);
        binding.interceptText.setText((intercept = ResUtil.getStringArray(R.array.on_off))[Setting.getIntercept()]);
        binding.tmdbKeyText.setText(Setting.getTMDBKey());
        binding.tvRowHeightText.setText(Setting.getTvRowDp() + "dp");
        binding.mobileRowHeightText.setText(Setting.getMobileRowDp() + "dp");
        binding.appVersion.setText("Version " + BuildConfig.VERSION_CODE + " - " + BuildConfig.VERSION_NAME);
        binding.tmdbKey.requestFocus();
    }

    private void initEvent() {
        binding.wall.setOnClickListener(this::setWall);
        binding.wallAuto.setOnClickListener(this::setAutoWall);
        binding.autoStart.setOnClickListener(this::setAutoStart);
        binding.intercept.setOnClickListener(this::setIntercept);
        binding.tmdbKey.setOnClickListener(this::ontmdbKey);
        binding.tvRowHeight.setOnClickListener(this::setTvRowHeight);
        binding.mobileRowHeight.setOnClickListener(this::setMobileRowHeight);
        binding.appSetting.setOnClickListener(this::goAppSetting);
        binding.systemSetting.setOnClickListener(this::goSystemSetting);
        binding.systemAccessibiliy.setOnClickListener(this::goSystemAccess);

        binding.tmdbKeyText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                savetmdbKey();
                return true;
            }
            return false;
        });
    }

    private void ontmdbKey(View view) {
        binding.tmdbKeyText.requestFocus();
    }

    private void savetmdbKey() {
        Setting.putTMDBKey(String.valueOf(binding.tmdbKeyText.getText()));
    }

    private void setTvRowHeight(View view) {
        int index = Setting.getTvRowIndex();
        index = (index + 1) % Setting.TV_ROW_HEIGHTS.length;
        Setting.putTvRowIndex(index);
        binding.tvRowHeightText.setText(Setting.TV_ROW_HEIGHTS[index] + "dp");
    }

    private void setMobileRowHeight(View view) {
        int index = Setting.getMobileRowIndex();
        index = (index + 1) % Setting.MOB_ROW_HEIGHTS.length;
        Setting.putMobileRowIndex(index);
        binding.mobileRowHeightText.setText(Setting.MOB_ROW_HEIGHTS[index] + "dp");
    }

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
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void setWall(View view) {
        int index = Setting.getWall();
        Setting.putWall(index = index == wall.length - 1 ? 0 : ++index);
        binding.wallText.setText(wall[index]);
    }

    private void setAutoWall(View view) {
        int index = Setting.getAutoWall();
        Setting.putAutoWall(index = index == autoWall.length - 1 ? 0 : ++index);
        binding.wallAutoText.setText(autoWall[index]);
    }

    private void setAutoStart(View view) {
        int index = Setting.getAutoStart();
        Setting.putAutoStart(index = index == autoStart.length - 1 ? 0 : ++index);
        binding.autoStartText.setText(autoStart[index]);
    }

    private void setIntercept(View view) {
        int index = Setting.getIntercept();
        Setting.putIntercept(index = index == intercept.length - 1 ? 0 : ++index);
        binding.interceptText.setText(intercept[index]);
        if (index == 0) {
            Notify.show("Please grant Accessibility");
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
