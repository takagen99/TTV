package com.takagen.launcher.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.takagen.launcher.App;
import com.takagen.launcher.adapter.AdapterAppsHide;
import com.takagen.launcher.bean.AppHideItem;
import com.takagen.launcher.bean.HiddenApp;
import com.takagen.launcher.databinding.ActivityAppsHideBinding;
import com.takagen.launcher.db.FavAppDB;
import com.takagen.launcher.utils.Utils;
import com.takagen.launcher.utils.Wallpaper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppsHideActivity extends AppCompatActivity implements AdapterAppsHide.Listener {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<AppHideItem> items = new ArrayList<>();

    private ActivityAppsHideBinding binding;
    private AdapterAppsHide adapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, AppsHideActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAppsHideBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);
        Wallpaper.applyBackdrop(this, binding.wallpaperBG);

        adapter = new AdapterAppsHide(items, this);
        binding.appsRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.appsRecycler.setAdapter(adapter);
        binding.appsRecycler.setItemAnimator(null);

        binding.btnShowAll.setOnClickListener(v -> showAll());

        loadApps();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadApps() {
        executor.execute(() -> {
            List<AppHideItem> loaded = Utils.getAllAppsForHide();
            App.post(() -> {
                if (binding == null) return;
                items.clear();
                items.addAll(loaded);
                adapter.notifyDataSetChanged();
                // Focus first item after layout
                binding.appsRecycler.post(() -> {
                    RecyclerView.ViewHolder vh =
                            binding.appsRecycler.findViewHolderForAdapterPosition(0);
                    if (vh != null) vh.itemView.requestFocus();
                });
            });
        });
    }

    // ── Toggle handler ────────────────────────────────────────────────────────

    @Override
    public void onToggle(int position, AppHideItem item) {
        item.hidden = !item.hidden;
        adapter.notifyItemChanged(position);
        final boolean nowHidden = item.hidden;
        final String pack = item.app.getPack();
        executor.execute(() -> {
            if (nowHidden) {
                HiddenApp h = new HiddenApp();
                h.pack = pack;
                FavAppDB.get().hiddenAppDao().hide(h);
            } else {
                FavAppDB.get().hiddenAppDao().show(pack);
            }
        });
    }

    // ── Show All ──────────────────────────────────────────────────────────────

    private void showAll() {
        boolean changed = false;
        for (AppHideItem item : items) {
            if (item.hidden) { item.hidden = false; changed = true; }
        }
        if (changed) {
            adapter.notifyDataSetChanged();
            executor.execute(() -> FavAppDB.get().hiddenAppDao().showAll());
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        binding = null;
    }
}
