package com.takagen.launcher.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.takagen.launcher.App;
import com.takagen.launcher.R;
import com.takagen.launcher.adapter.AdapterMain;
import com.takagen.launcher.adapter.AdapterRow;
import com.takagen.launcher.bean.AppInfo;
import com.takagen.launcher.bean.AppItem;
import com.takagen.launcher.databinding.ActivityMainBinding;
import com.takagen.launcher.db.FavAppDB;
import com.takagen.launcher.ui.dialog.InputDialog;
import com.takagen.launcher.utils.Clock;
import com.takagen.launcher.utils.ResUtil;
import com.takagen.launcher.utils.Utils;
import com.takagen.launcher.utils.Wallpaper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AdapterRow.Listener {

    private static final int REQ_OVERLAY = 2084;

    private final FavAppDB db = FavAppDB.get();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityMainBinding binding;
    private AdapterMain adapter;
    private Clock timeClock;
    private Clock dateClock;
    private Wallpaper wallpaper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
            return;
        }

        initViews();
        loadApps();
    }

    private void initViews() {
        adapter = new AdapterMain(this);
        binding.mainRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.mainRecycler.setAdapter(adapter);
        binding.mainRecycler.setHasFixedSize(false);
        binding.mainRecycler.setItemAnimator(null);

        timeClock = Clock.create(binding.tvTime).format("HH:mm");
        dateClock = Clock.create(binding.tvDate).format("dd MMMM\nEEEE");

        binding.tvPosterTitle.setOnClickListener(this::onMenu);
        binding.tvRefresh.setOnClickListener(this::onMenu);
        binding.tvInput.setOnClickListener(this::onMenu);
        binding.tvClear.setOnClickListener(this::onMenu);
        binding.tvWifi.setOnClickListener(this::onMenu);
        binding.tvMenu.setOnClickListener(this::onMenu);
        binding.tvMenu.setOnLongClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
            return true;
        });

        updateNetworkIcon();

        wallpaper = new Wallpaper(this, binding.frameBG, binding.mainBG, binding.tvPosterTitle,
                ResUtil.getAnim(R.anim.enter_anim));
        wallpaper.load();
        // Initial focus: deferred to focusFirstApp() after apps finish loading.
    }

    private void loadApps() {
        executor.execute(() -> {
            Utils.AppBuckets buckets = Utils.getApps();

            List<AppItem> items = new ArrayList<>();
            if (!buckets.tv.isEmpty()) {
                items.add(new AppItem.Header("TV Apps"));
                items.add(new AppItem.Section(buckets.tv, true));
            }
            if (!buckets.mobile.isEmpty()) {
                items.add(new AppItem.Header("Mobile Apps"));
                items.add(new AppItem.Section(buckets.mobile, false));
            }

            App.post(() -> {
                if (binding != null) {
                    adapter.submit(items);
                    // After layout settles, focus the first app in the first section row.
                    binding.mainRecycler.post(this::focusFirstApp);
                }
            });
        });
    }

    /** Focus the first child of the first section row (first TV app, or mobile if no TV). */
    private void focusFirstApp() {
        if (binding == null) return;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            RecyclerView.ViewHolder vh = binding.mainRecycler.findViewHolderForAdapterPosition(i);
            if (vh != null && vh.itemView instanceof RecyclerView) {
                RecyclerView inner = (RecyclerView) vh.itemView;
                if (inner.getChildCount() > 0) {
                    inner.getChildAt(0).requestFocus();
                    return;
                }
            }
        }
    }

    @Override
    public void onItemClick(AppInfo item) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(item.getPack());
            if (launch != null) {
                startActivity(launch);
                overridePendingTransition(R.anim.zoom_enter, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReorder(List<AppInfo> newOrder, boolean isTv) {
        List<AppInfo> snapshot = new ArrayList<>(newOrder);
        executor.execute(() -> Utils.saveBucketOrder(snapshot, isTv));
    }

    private void onMenu(View v) {
        if (v == binding.tvRefresh || v == binding.tvPosterTitle) {
            wallpaper.show();
        } else if (v == binding.tvInput) {
            InputDialog.create(this).show();
        } else if (v == binding.tvClear) {
            new MaterialAlertDialogBuilder(this, R.style.DarkDialogAlert)
                    .setTitle("Reset Custom Sort")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Yes", (d, w) -> {
                        db.favAppDao().deleteAllFavApp();
                        loadApps();
                    })
                    .show()
                    .setOnShowListener(d -> {
                        Button no = ((AlertDialog) d).getButton(DialogInterface.BUTTON_NEGATIVE);
                        if (no != null) no.requestFocus();
                    });
        } else if (v == binding.tvMenu) {
            SettingsActivity.start(this);
        } else if (v == binding.tvWifi) {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        }
    }

    private void updateNetworkIcon() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;
        NetworkInfo net = cm.getActiveNetworkInfo();
        if (net == null || !net.isConnectedOrConnecting()) return;
        int icon;
        switch (net.getType()) {
            case ConnectivityManager.TYPE_WIFI: icon = R.drawable.ic_home_network_wifi; break;
            case ConnectivityManager.TYPE_MOBILE: icon = R.drawable.ic_home_network_mobile; break;
            case ConnectivityManager.TYPE_ETHERNET: icon = R.drawable.ic_home_network_lan; break;
            default: icon = R.drawable.ic_home_network_no; break;
        }
        binding.tvWifi.setImageDrawable(Utils.getDrawable(icon));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View focus = getCurrentFocus();
            if (focus != null) {
                int kc = event.getKeyCode();
                if ((focus == binding.tvPosterTitle && kc == KeyEvent.KEYCODE_DPAD_LEFT)
                        || (focus == binding.tvMenu && kc == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    Utils.shakeView(focus);
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (binding != null) {
            LinearLayoutManager lm = (LinearLayoutManager) binding.mainRecycler.getLayoutManager();
            if (lm != null && lm.findFirstCompletelyVisibleItemPosition() > 0) {
                binding.mainRecycler.smoothScrollToPosition(0);
                binding.mainRecycler.post(this::focusFirstApp);
                return;
            }
        }
        // Do NOT call super — the launcher must never exit via the Back button.
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
        if (timeClock != null) timeClock.start();
        if (dateClock != null) dateClock.start();
        updateNetworkIcon();
        if (wallpaper != null) wallpaper.onResume();
        // Reload app list on every resume so hiding/showing changes take effect immediately.
        if (adapter != null) loadApps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (timeClock != null) timeClock.stop();
        if (dateClock != null) dateClock.stop();
        if (wallpaper != null) wallpaper.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeClock != null) timeClock.release();
        if (dateClock != null) dateClock.release();
        executor.shutdownNow();
        binding = null;
    }
}
