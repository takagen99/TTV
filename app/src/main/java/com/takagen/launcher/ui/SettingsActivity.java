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
import com.takagen.launcher.utils.ResUtil;
import com.takagen.launcher.utils.Utils;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    private String[] auto;
    private String[] wall;

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
        binding.wallAutoText.setText((auto = ResUtil.getStringArray(R.array.on_off))[Setting.getAuto()]);
        binding.tmdbKeyText.setText(Setting.getTMDBKey());
        binding.appVersion.setText("Version " + BuildConfig.VERSION_CODE + " - " + BuildConfig.VERSION_NAME);
        binding.tmdbKey.requestFocus();
    }

    private void initEvent() {
        binding.wall.setOnClickListener(this::setWall);
        binding.wallAuto.setOnClickListener(this::setAuto);
        binding.tmdbKey.setOnClickListener(this::ontmdbKey);
        binding.appSetting.setOnClickListener(this::goAppSetting);
        binding.systemSetting.setOnClickListener(this::goSystemSetting);

        binding.tmdbKeyText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    savetmdbKey();      //save tmdbKey on Enter
                    return true;
                }
                return false;
            }
        });
    }

    private void ontmdbKey(View view) {
        binding.tmdbKeyText.requestFocus();
    }

    private void savetmdbKey() {
        Setting.putTMDBKey(String.valueOf(binding.tmdbKeyText.getText()));
    }

    private void goAppSetting(View v) {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
    }

    private void goSystemSetting(View v) {
        startActivity(new Intent(Settings.ACTION_SETTINGS));
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
//        Notify.show("index " + index);
        binding.wallText.setText(wall[index]);
    }

    private void setAuto(View view) {
        int index = Setting.getAuto();
        Setting.putAuto(index = index == auto.length - 1 ? 0 : ++index);
        binding.wallAutoText.setText(auto[index]);
    }

    @Override
    public void onBackPressed() {
//        Setting.putTMDBKey(String.valueOf(binding.tmdbKeyText.getText()));
        super.onBackPressed();
    }

}