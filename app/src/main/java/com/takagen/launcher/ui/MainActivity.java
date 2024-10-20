package com.takagen.launcher.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.takagen.launcher.App;
import com.takagen.launcher.R;
import com.takagen.launcher.Utils;
import com.takagen.launcher.adapter.AdapterBanner;
import com.takagen.launcher.adapter.AdapterIcon;
import com.takagen.launcher.bean.AppInfo;
import com.takagen.launcher.databinding.ActivityMainBinding;
import com.takagen.launcher.utils.Clock;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AdapterIcon mAdapter;
    private AdapterBanner mAdapterB;

    private Clock mClock;
    private Clock mClockDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);
        initView();
        initEvent();
    }

    private void initView() {
        setRecyclerView();
        mAdapter.getApp(1);    // Get only with Icons, no banners
        mAdapterB.getApp(2);

        mClock = Clock.create(binding.tvTime).format("HH:mm");
        mClockDate = Clock.create(binding.tvDate).format("dd MMMM\nEEEE");

        binding.tvWifi.setOnClickListener(this::onMenuClick);
        binding.tvMenu.setOnClickListener(this::onMenuClick);
        setIcon();
    }

    private void initEvent() {
        mAdapter.setOnItemClickListener(this::openApp);
        mAdapterB.setOnItemClickListener(this::openApp);

        // Fix to focus on HorizontalGridView by focusing on Menu button first
        binding.tvMenu.requestFocus();
        App.post(() -> binding.recyclerBanner.requestFocus(), 500);
    }

    private void setRecyclerView() {
//		binding.recyclerIcon.setHasFixedSize(true);
//		binding.recyclerIcon.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerIcon.setAdapter(mAdapter = new AdapterIcon());
//        binding.recyclerIcon.setWindowAlignmentOffsetPercent(10);       // to set focus at 1st item

//		binding.recyclerBanner.setHasFixedSize(true);
//		binding.recyclerBanner.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerBanner.setAdapter(mAdapterB = new AdapterBanner());
//        binding.recyclerBanner.setWindowAlignmentOffsetPercent(10);
    }

    private void openApp(AppInfo item) {
        try {
            startActivity(getPackageManager().getLaunchIntentForPackage(item.getPack()));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        mClock.start();
        mClockDate.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
        mClockDate.stop();
    }

    private void onMenuClick(View v) {
        if (v == binding.tvMenu) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } else if (v == binding.tvWifi) {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        }
    }

    private boolean onLongMenu(View view) {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
        return true;
    }

    boolean isNetworkAvailable() {
        ConnectivityManager cm
                = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    private void setIcon() {
        if (isNetworkAvailable()) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI) {
                binding.tvWifi.setImageDrawable(Utils.getDrawable(R.drawable.ic_home_network_wifi));
            } else if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_MOBILE) {
                binding.tvWifi.setImageDrawable(Utils.getDrawable(R.drawable.ic_home_network_mobile));
            } else if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_ETHERNET) {
                binding.tvWifi.setImageDrawable(Utils.getDrawable(R.drawable.ic_home_network_lan));
            }
        }
    }

}
