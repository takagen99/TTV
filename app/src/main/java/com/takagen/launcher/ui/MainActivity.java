package com.takagen.launcher.ui;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.takagen.launcher.Utils;
import com.takagen.launcher.adapter.AdapterBanner;
import com.takagen.launcher.adapter.AdapterIcon;
import com.takagen.launcher.bean.AppInfo;
import com.takagen.launcher.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

	private ActivityMainBinding binding;
	private AdapterIcon mAdapter;
	private AdapterBanner mAdapterB;

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
		mAdapter.getApp(1);	// Get only with Icons, no banners
		mAdapterB.getApp(2);
	}

	private void initEvent() {
		mAdapter.setOnItemClickListener(this::openApp);
		mAdapterB.setOnItemClickListener(this::openApp);
	}

	private void setRecyclerView() {
		binding.recyclerIcon.setHasFixedSize(true);
		binding.recyclerIcon.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL, false));
		binding.recyclerIcon.setAdapter(mAdapter = new AdapterIcon());


		binding.recyclerBanner.setHasFixedSize(true);
		binding.recyclerBanner.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL, false));
		binding.recyclerBanner.setAdapter(mAdapterB = new AdapterBanner());
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
}
