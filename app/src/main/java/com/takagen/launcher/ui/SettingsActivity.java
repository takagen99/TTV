package com.takagen.launcher.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.takagen.launcher.BuildConfig;
import com.takagen.launcher.R;
import com.takagen.launcher.Setting;
import com.takagen.launcher.databinding.ActivitySettingsBinding;
import com.takagen.launcher.databinding.ItemAboutRowBinding;
import com.takagen.launcher.utils.ResUtil;
import com.takagen.launcher.utils.Utils;
import com.takagen.launcher.utils.Wallpaper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Full settings page — reachable via "All settings" in {@link QuickSettingsActivity}.
 *
 * The left panel has two sections:
 *   - General: TMDB key, wallpaper options, Show/Hide Apps.
 *   - About:   read-only device / build information.
 *
 * Runtime toggles (Default TV Launcher, Auto Start on Boot, Row Heights) live in
 * {@link QuickSettingsActivity}, not here.
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    private String[] autoWall;
    private String[] wall;
    private String[] onOff;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);
        applyWallpaperBackdrop();
        initView();
        initEvent();
        populateAbout();
    }

    /**
     * Reuse whatever TMDB backdrop is currently showing on the launcher so the
     * Settings page doesn't feel visually disconnected. Applies a RenderEffect
     * blur on API 31+ and falls back to the dim-only overlay on older devices.
     */
    private void applyWallpaperBackdrop() {
        String url = Wallpaper.getCurrentUrl();
        if (!TextUtils.isEmpty(url)) {
            Glide.with(this).load(url).into(binding.wallpaperBG);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.wallpaperBG.setRenderEffect(
                    RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP));
        }
    }

    private void initView() {
        onOff = ResUtil.getStringArray(R.array.on_off);
        binding.wallText.setText((wall = ResUtil.getStringArray(R.array.select_wall))[Setting.getWall()]);
        binding.wallAutoText.setText((autoWall = onOff)[Setting.getAutoWall()]);
        binding.tmdbKeyText.setText(Setting.getTMDBKey());
        binding.appVersion.setText("Version " + BuildConfig.VERSION_CODE + " - " + BuildConfig.VERSION_NAME);

        // Start on General section, focus first item in right panel
        showSection(false);
        binding.sectionMain.requestFocus();
    }

    private void initEvent() {
        // Left panel section selectors — switch right panel on focus
        binding.sectionMain.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showSection(false);
        });
        binding.sectionAbout.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showSection(true);
        });
        binding.sectionMain.setOnClickListener(v -> {
            showSection(false);
            binding.tmdbKey.requestFocus();
        });
        binding.sectionAbout.setOnClickListener(v -> showSection(true));

        // General panel
        binding.wall.setOnClickListener(this::setWall);
        binding.wallAuto.setOnClickListener(this::setAutoWall);
        binding.tmdbKey.setOnClickListener(v -> binding.tmdbKeyText.requestFocus());
        binding.showHideApps.setOnClickListener(v -> AppsHideActivity.start(this));

        binding.tmdbKeyText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Setting.putTMDBKey(String.valueOf(binding.tmdbKeyText.getText()));
                return true;
            }
            return false;
        });
    }

    // ── Section switching ──────────────────────────────────────────────────────

    private void showSection(boolean about) {
        binding.panelMain.setVisibility(about ? View.GONE : View.VISIBLE);
        binding.panelAbout.setVisibility(about ? View.VISIBLE : View.GONE);
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

    // ── About panel ───────────────────────────────────────────────────────────

    private void populateAbout() {
        bindAbout(binding.aboutAndroidVersion,  "Android Version",
                Build.VERSION.RELEASE + "  (API " + Build.VERSION.SDK_INT + ")");
        bindAbout(binding.aboutBuildNumber,     "Build Number",    Build.DISPLAY);
        bindAbout(binding.aboutBuildDate,       "Build Date",      formatBuildTime(Build.TIME));
        bindAbout(binding.aboutSecurityPatch,   "Security Patch",  safeSecurityPatch());
        bindAbout(binding.aboutKernel,          "Kernel Version",  safeProp("os.version"));

        bindAbout(binding.aboutBrand,           "Brand",           Build.BRAND);
        bindAbout(binding.aboutManufacturer,    "Manufacturer",    Build.MANUFACTURER);
        bindAbout(binding.aboutModel,           "Model",           Build.MODEL);
        bindAbout(binding.aboutDevice,          "Device",          Build.DEVICE);
        bindAbout(binding.aboutProduct,         "Product",         Build.PRODUCT);
        bindAbout(binding.aboutBoard,           "Board",           Build.BOARD);
        bindAbout(binding.aboutHardware,        "Hardware",        Build.HARDWARE);

        bindAbout(binding.aboutProcessor,       "Processor",       readCpuModel());
        bindAbout(binding.aboutCpuArch,         "CPU Architecture", safeProp("os.arch"));
        bindAbout(binding.aboutAbis,            "Supported ABIs",  joinAbis());

        bindAbout(binding.aboutResolution,      "Resolution",      formatResolution());
        bindAbout(binding.aboutDensity,         "Density",         formatDensity());
        bindAbout(binding.aboutRefresh,         "Refresh Rate",    formatRefresh());

        bindAbout(binding.aboutRam,             "RAM",             formatRam());
        bindAbout(binding.aboutStorage,         "Internal Storage", formatStorage());
        bindAbout(binding.aboutBootloader,      "Bootloader",      Build.BOOTLOADER);

        bindAbout(binding.aboutAppVersion,      "App Version",
                BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
    }

    private void bindAbout(ItemAboutRowBinding row, String label, String value) {
        row.rowLabel.setText(label);
        row.rowValue.setText(value == null || value.isEmpty() ? "—" : value);
    }

    // ── About value helpers ──────────────────────────────────────────────────

    private String formatBuildTime(long time) {
        try {
            return new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                    .format(new Date(time));
        } catch (Exception e) {
            return String.valueOf(time);
        }
    }

    private String safeSecurityPatch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return Build.VERSION.SECURITY_PATCH;
        return "—";
    }

    private String safeProp(String key) {
        try {
            String v = System.getProperty(key);
            return v == null ? "—" : v;
        } catch (Exception e) {
            return "—";
        }
    }

    private String joinAbis() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0) return "—";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < abis.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(abis[i]);
        }
        return sb.toString();
    }

    /** Reads /proc/cpuinfo to get a friendly processor model name when available. */
    private String readCpuModel() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                // "Hardware", "model name" or "Processor" lines all carry useful info on different SoCs.
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("hardware")
                        || lower.startsWith("model name")
                        || lower.startsWith("processor")) {
                    int colon = line.indexOf(':');
                    if (colon > -1 && colon < line.length() - 1) {
                        String v = line.substring(colon + 1).trim();
                        if (!v.isEmpty() && !v.matches("\\d+")) return v;
                    }
                }
            }
        } catch (Exception ignored) { }
        return Build.HARDWARE;
    }

    private String formatResolution() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.widthPixels + " × " + dm.heightPixels + " px";
    }

    private String formatDensity() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.densityDpi + " dpi  (×" + dm.density + ")";
    }

    private String formatRefresh() {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) {
                return String.format(Locale.getDefault(),
                        "%.1f Hz", wm.getDefaultDisplay().getRefreshRate());
            }
        } catch (Exception ignored) { }
        return "—";
    }

    private String formatRam() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return humanBytes(mi.availMem) + " free / " + humanBytes(mi.totalMem) + " total";
            }
        } catch (Exception ignored) { }
        return "—";
    }

    private String formatStorage() {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long block = fs.getBlockSizeLong();
            long free  = fs.getAvailableBlocksLong() * block;
            long total = fs.getBlockCountLong()    * block;
            return humanBytes(free) + " free / " + humanBytes(total) + " total";
        } catch (Exception ignored) { }
        return "—";
    }

    private String humanBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        double v = bytes;
        while (v >= 1024 && idx < units.length - 1) { v /= 1024; idx++; }
        return String.format(Locale.getDefault(),
                idx == 0 ? "%.0f %s" : "%.2f %s", v, units[idx]);
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
    public void onBackPressed() {
        super.onBackPressed();
    }
}
