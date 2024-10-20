package com.takagen.launcher.adapter;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.takagen.launcher.Utils;
import com.takagen.launcher.bean.AppInfo;
import com.takagen.launcher.databinding.AdapterIconBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdapterIcon extends RecyclerView.Adapter<AdapterIcon.Holder> {

    private final ExecutorService executor;
    private final List<AppInfo> mItems;
    private final Handler handler;
    private OnItemClickListener mItemClickListener;

    public AdapterIcon() {
        this.mItems = new ArrayList<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void setOnItemClickListener(OnItemClickListener itemClickListener) {
        this.mItemClickListener = itemClickListener;
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
        return new Holder(AdapterIconBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppInfo item = mItems.get(position);
        setFocusListener(holder.iconBinding);

        holder.iconBinding.name.setText(item.getName());

//		if (item.getBanner()==null) {
        holder.iconBinding.icon.setImageDrawable(item.getIcon());
//		} else {
//			holder.iconBinding.icon.setImageDrawable(item.getBanner());
//		}

    }

    private void setFocusListener(AdapterIconBinding binding) {
        binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
            if (v.hasFocus()) {
                v.setAlpha(1f);
                v.setElevation(10);
                v.animate().scaleX(1.25f).scaleY(1.25f)
                        .setDuration(100)
                        .start();
                v.setSelected(true);
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f)
                        .setDuration(100)
                        .start();
                v.setSelected(false);
                v.setElevation(0);
                v.setAlpha(0.9f);
            }
        });

    }

    public interface OnItemClickListener {

        void onItemClick(AppInfo item);
    }

    class Holder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterIconBinding iconBinding;

        public Holder(AdapterIconBinding binding) {
            super(binding.getRoot());
            this.iconBinding = binding;
            binding.getRoot().setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            mItemClickListener.onItemClick(mItems.get(getLayoutPosition()));
        }
    }
}
