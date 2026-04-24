package com.takagen.launcher.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
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
import com.takagen.launcher.service.AccessibilityKeyDetector;
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
    private QuickSettingsOverlay quickSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            AccessibilityKeyDetector.noteUserDepartedTTV();
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

        timeClock = Clock.create(binding.tvTime).format("h:mm");
        dateClock = Clock.create(binding.tvDate).format("dd MMMM\nEEEE");

        binding.tvPosterTitle.setOnClickListener(this::onMenu);
        binding.tvRefresh.setOnClickListener(this::onMenu);
        binding.tvClear.setOnClickListener(this::onMenu);
        binding.tvMenu.setOnClickListener(this::onMenu);
        binding.tvMenu.setOnLongClickListener(v -> {
            AccessibilityKeyDetector.noteUserDepartedTTV();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
            return true;
        });

        wallpaper = new Wallpaper(this, binding.frameBG, binding.mainBG, binding.tvPosterTitle,
                ResUtil.getAnim(R.anim.enter_anim));
        wallpaper.load();

        // Quick-settings card — hosted inside this activity so we can slide only
        // the right-side panel in, leaving the launcher visible behind a soft dim.
        quickSettings = new QuickSettingsOverlay(this, binding.qsOverlay);
        // Trap D-pad focus inside the card while it's visible so arrow keys
        // don't leak down into the app rows behind the dim.
        quickSettings.setBackgroundContainer(binding.mainContent);
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
        // Don't steal focus from the quick-settings card when it's visible.
        if (quickSettings != null && quickSettings.isShowing()) return;
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
                // If the user picked an app that happens to be another launcher,
                // respect their choice — tell the accessibility Home-guardian to
                // stand down for a moment.
                AccessibilityKeyDetector.noteUserDepartedTTV();
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
        if (v == binding.tvPosterTitle) {
            wallpaper.show();
        } else if (v == binding.tvRefresh) {
            loadApps();
        } else if (v == binding.tvClear) {
            // Build, attach OnShowListener, THEN show. Previously .setOnShowListener
            // was chained AFTER .show(), so the show event had already fired and
            // the listener never ran — meaning "No" was never focused by default.
            AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.DarkDialogAlert)
                    .setTitle("Reset Custom Sort")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Yes", (d, w) -> {
                        db.favAppDao().deleteAllFavApp();
                        loadApps();
                    })
                    .create();
            dialog.setOnShowListener(d -> {
                Button no = ((AlertDialog) d).getButton(DialogInterface.BUTTON_NEGATIVE);
                if (no != null) no.requestFocus();
            });
            dialog.show();
        } else if (v == binding.tvMenu) {
            // Show the Google-TV-style quick-settings card; its "All settings"
            // header then forwards to the full Settings page.
            quickSettings.show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // When the system delivers the HOME intent to an already-running singleTask
        // launcher, onNewIntent fires instead of onCreate. Make sure we're fully visible.
        Utils.hideSystemUI(this);
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
        // Quick-settings card takes priority — dismiss it first.
        if (quickSettings != null && quickSettings.isShowing()) {
            quickSettings.hide();
            return;
        }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (quickSettings != null) quickSettings.onActivityResult(requestCode);
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
        if (wallpaper != null) wallpaper.onDestroy();
        if (quickSettings != null) quickSettings.release();
        executor.shutdownNow();
        binding = null;
    }
}
