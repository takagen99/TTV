package com.takagen.launcher.adapter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.takagen.launcher.R;
import com.takagen.launcher.Utils;
import com.takagen.launcher.bean.AppInfo;
import com.takagen.launcher.databinding.AdapterBannerBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdapterBanner extends RecyclerView.Adapter<AdapterBanner.Holder> {

	private OnItemClickListener mItemClickListener;
	private final ExecutorService executor;
	private final List<AppInfo> mItems;
	private final Handler handler;

	public AdapterBanner() {
		this.mItems = new ArrayList<>();
		this.executor = Executors.newSingleThreadExecutor();
		this.handler = new Handler(Looper.getMainLooper());
	}

	public interface OnItemClickListener {

		void onItemClick(AppInfo item);
	}

	public void setOnItemClickListener(OnItemClickListener itemClickListener) {
		this.mItemClickListener = itemClickListener;
	}

	class Holder extends RecyclerView.ViewHolder implements View.OnClickListener {

		private final AdapterBannerBinding bannerBinding;

		public Holder(AdapterBannerBinding binding) {
			super(binding.getRoot());
			this.bannerBinding = binding;
			binding.getRoot().setOnClickListener(this);
		}

		@Override
		public void onClick(View view) {
			mItemClickListener.onItemClick(mItems.get(getLayoutPosition()));
		}
	}

	public void getApp(int type) {
		executor.execute(() -> {
			addAll(Utils.getApps(type));
			handler.post(this::notifyDataSetChanged);
		});
	}

	private void addAll(List<AppInfo> items) {
		mItems.clear();
		mItems.addAll(items);
		AppInfo.Sorter.sort(mItems);
	}

	@Override
	public int getItemCount() {
		return mItems.size();
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new Holder(AdapterBannerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull Holder holder, int position) {
		AppInfo item = mItems.get(position);
		setFocusListener(holder.bannerBinding);

		holder.bannerBinding.name.setText(item.getName());
		holder.bannerBinding.icon.setImageDrawable(item.getBanner());

	}

	private void setFocusListener(AdapterBannerBinding binding) {
		binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
			if (v.hasFocus()) {
				v.setAlpha(1f);
				v.setElevation(10);
				v.animate().scaleX(1.25f).scaleY(1.25f)
						.setDuration(50)
						.start();
				v.setSelected(true);
			} else {
				v.animate().scaleX(1.0f).scaleY(1.0f)
						.setDuration(50)
						.start();
				v.setSelected(false);
				v.setElevation(0);
				v.setAlpha(0.9f);
			}
		});

	}
}
