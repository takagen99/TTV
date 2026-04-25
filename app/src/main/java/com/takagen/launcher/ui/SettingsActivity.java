package com.takagen.launcher.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.takagen.launcher.BuildConfig;
import com.takagen.launcher.R;
import com.takagen.launcher.Setting;
import com.takagen.launcher.databinding.ActivitySettingsBinding;
import com.takagen.launcher.databinding.ItemAboutRowBinding;
import com.takagen.launcher.utils.LocaleHelper;
import com.takagen.launcher.utils.ResUtil;
import com.takagen.launcher.utils.Utils;
import com.takagen.launcher.utils.Wallpaper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Full settings page — reachable via "All settings" in {@link QuickSettingsOverlay}.
 *
 * The left panel has three sections:
 *   - General: TMDB key, wallpaper options.
 *   - System:  Show/Hide Apps, File Browser.
 *   - About:   read-only device / build information.
 *
 * Runtime toggles (Default TV Launcher, Auto Start on Boot, Row Heights) live in
 * {@link QuickSettingsOverlay}, not here.
 */
public class SettingsActivity extends AppCompatActivity {

    /** Built-in folder-browser request code. */
    private static final int REQ_PICK_LOCAL_FOLDER_FALLBACK = 0x5A20;

    private ActivitySettingsBinding binding;

    private String[] autoWall;
    private String[] wall;
    private String[] onOff;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingsActivity.class));
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);
        Wallpaper.applyBackdrop(this, binding.wallpaperBG);
        initView();
        initEvent();
        populateAbout();
    }

    private void initView() {
        onOff = ResUtil.getStringArray(R.array.on_off);
        wall = ResUtil.getStringArray(R.array.select_wall);
        autoWall = onOff;

        binding.wallText.setText(formatWallMask(Setting.getWallMask()));
        binding.wallAutoText.setText(autoWall[Setting.getAutoWall()]);
        binding.tmdbKeyText.setText(Setting.getTMDBKey());
        binding.wallSourceText.setText(formatWallSource(Setting.getWallSource()));
        binding.wallLocalFolderText.setText(formatLocalFolder(Setting.getWallLocalFolder()));
        binding.wallCycleIntervalText.setText(formatCycleSeconds(Setting.getWallCycleSeconds()));
        binding.appVersion.setText(getString(R.string.settings_version_format,
                BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME));
        binding.languageText.setText(getString(R.string.settings_language_value));

        // Source-dependent row visibility (TMDB vs Local branches of Wallpapers Settings).
        applySourceVisibility(Setting.getWallSource());

        // Start on General section, focus first item in right panel
        showSection(0);
        binding.sectionMain.requestFocus();
    }

    private void initEvent() {
        // Left panel section selectors — switch right panel on focus
        binding.sectionMain.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showSection(0);
        });
        binding.sectionSystem.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showSection(1);
        });
        binding.sectionAbout.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showSection(2);
        });
        binding.sectionMain.setOnClickListener(v -> {
            showSection(0);
            binding.wallSource.requestFocus();
        });
        binding.sectionSystem.setOnClickListener(v -> {
            showSection(1);
            binding.showHideApps.requestFocus();
        });
        binding.sectionAbout.setOnClickListener(v -> showSection(2));

        // System panel
        binding.showHideApps.setOnClickListener(v -> AppsHideActivity.start(this));
        binding.fileBrowser.setOnClickListener(v -> FileBrowserActivity.start(this));
        binding.selectLanguage.setOnClickListener(v -> showLanguageDialog());

        // General panel
        binding.wallSource.setOnClickListener(this::toggleWallSource);
        binding.wall.setOnClickListener(this::setWall);
        binding.wallAuto.setOnClickListener(this::setAutoWall);
        binding.wallCycleInterval.setOnClickListener(this::setCycleInterval);
        binding.wallLocalFolder.setOnClickListener(v -> pickLocalFolder());
        binding.tmdbKey.setOnClickListener(v -> binding.tmdbKeyText.requestFocus());

        binding.tmdbKeyText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Setting.putTMDBKey(String.valueOf(binding.tmdbKeyText.getText()));
                return true;
            }
            return false;
        });
    }

    // ── Section switching ──────────────────────────────────────────────────────

    /** @param section 0 = General, 1 = System, 2 = About */
    private void showSection(int section) {
        binding.panelMain.setVisibility(section == 0 ? View.VISIBLE : View.GONE);
        binding.panelSystem.setVisibility(section == 1 ? View.VISIBLE : View.GONE);
        binding.panelAbout.setVisibility(section == 2 ? View.VISIBLE : View.GONE);
    }

    // ── General settings handlers ──────────────────────────────────────────────

    /**
     * Opens a multi-choice dialog so the user can enable any combination of
     * wallpaper categories. The Wallpaper class picks up the new mask on the
     * next onResume and refetches only if the selection actually changed.
     */
    private void setWall(View v) {
        final int initialMask = Setting.getWallMask();
        final boolean[] checked = new boolean[wall.length];
        for (int i = 0; i < wall.length; i++) {
            checked[i] = (initialMask & (1 << i)) != 0;
        }
        new MaterialAlertDialogBuilder(this, R.style.DarkDialogAlert)
                .setTitle(R.string.settings_wall_categories_title)
                .setMultiChoiceItems(wall, checked,
                        (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    int newMask = 0;
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) newMask |= (1 << i);
                    }
                    // Fall back to Anime if the user unchecked everything, otherwise
                    // the launcher would have no backdrops to show.
                    if (newMask == 0) newMask = Setting.WALL_ANIME;
                    Setting.putWallMask(newMask);
                    binding.wallText.setText(formatWallMask(newMask));
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private String formatWallMask(int mask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wall.length; i++) {
            if ((mask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(wall[i]);
            }
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private void setAutoWall(View v) {
        int index = Setting.getAutoWall();
        Setting.putAutoWall(index = index == autoWall.length - 1 ? 0 : ++index);
        binding.wallAutoText.setText(autoWall[index]);
    }

    // ── Wallpaper source (TMDB ↔ Local) ───────────────────────────────────────

    /**
     * Flip the wallpaper source on click. Only two options so we skip the
     * chooser dialog in favour of a cycling toggle — consistent with the
     * "Auto Cycle" row's On/Off behaviour.
     */
    private void toggleWallSource(View v) {
        int next = Setting.getWallSource() == Setting.WALL_SRC_TMDB
                ? Setting.WALL_SRC_LOCAL
                : Setting.WALL_SRC_TMDB;
        Setting.putWallSource(next);
        binding.wallSourceText.setText(formatWallSource(next));
        applySourceVisibility(next);
    }

    /** Show only the rows that are relevant for the current source. */
    private void applySourceVisibility(int source) {
        boolean tmdb = source == Setting.WALL_SRC_TMDB;
        binding.tmdbKey.setVisibility(tmdb ? View.VISIBLE : View.GONE);
        binding.wall.setVisibility(tmdb ? View.VISIBLE : View.GONE);
        binding.wallLocalFolder.setVisibility(tmdb ? View.GONE : View.VISIBLE);
    }

    private String formatWallSource(int source) {
        return source == Setting.WALL_SRC_LOCAL
                ? getString(R.string.settings_wall_source_local)
                : getString(R.string.settings_wall_source_tmdb);
    }

    // ── Local folder picker (Storage Access Framework) ────────────────────────

    /**
     * Launch the folder picker.
     *
     * <p>We always use the built-in {@link FolderBrowserActivity} rather than
     * {@code ACTION_OPEN_DOCUMENT_TREE}, because Android TV ROMs are wildly
     * inconsistent here:
     * <ul>
     *   <li>Many slim TV builds ship without DocumentsUI entirely — the SAF
     *       intent errors out with "You don't have an app that can do this".
     *   <li>Worse, some TVs return a non-null resolver (a system stub) from
     *       {@code resolveActivity}, then show the same system error only
     *       after {@code startActivity} — so we can't reliably detect failure
     *       up-front via a try/catch either.
     * </ul>
     * The built-in browser is always available and uses plain
     * {@link java.io.File} access, which works on every Android version we
     * support. Users with SAF who previously picked a {@code content://} tree
     * URI still get their wallpapers enumerated (see {@code Wallpaper.loadLocal()})
     * for backward-compat with earlier versions of this app.
     */
    private void pickLocalFolder() {
        FolderBrowserActivity.start(this, REQ_PICK_LOCAL_FOLDER_FALLBACK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_LOCAL_FOLDER_FALLBACK) return;
        if (resultCode != RESULT_OK || data == null) return;

        String path = data.getStringExtra(FolderBrowserActivity.RESULT_DATA_PATH);
        if (TextUtils.isEmpty(path)) return;
        // Plain absolute path. Wallpaper.java distinguishes this from any
        // legacy-stored content:// SAF URI by the leading slash.
        Setting.putWallLocalFolder(path);
        binding.wallLocalFolderText.setText(formatLocalFolder(path));
    }

    /**
     * Formats the stored wallpaper-folder reference for display. Two possible
     * shapes are supported:
     * <ul>
     *   <li>SAF tree URI like
     *       {@code content://com.android.externalstorage.documents/tree/primary%3APictures%2FBackgrounds}
     *       → shown as {@code Pictures/Backgrounds}.
     *   <li>Plain absolute path from the built-in browser
     *       like {@code /storage/emulated/0/Pictures/Backgrounds} → shown as
     *       {@code Pictures/Backgrounds} (strips the common storage-root prefix).
     * </ul>
     */
    private String formatLocalFolder(String uriStr) {
        if (TextUtils.isEmpty(uriStr)) return getString(R.string.settings_local_folder_not_set);
        // SAF content URI?
        if (uriStr.startsWith("content://")) {
            try {
                Uri u = Uri.parse(uriStr);
                String docId = android.provider.DocumentsContract.getTreeDocumentId(u);
                if (docId != null) {
                    int colon = docId.indexOf(':');
                    String path = colon >= 0 ? docId.substring(colon + 1) : docId;
                    return path.isEmpty() ? docId : path;
                }
            } catch (Exception ignored) { }
            return uriStr;
        }
        // Plain filesystem path — strip the usual storage root prefix for readability.
        final String[] stripPrefixes = {
                "/storage/emulated/0/",
                "/sdcard/",
                "/storage/self/primary/",
        };
        for (String p : stripPrefixes) {
            if (uriStr.startsWith(p)) {
                String stripped = uriStr.substring(p.length());
                return stripped.isEmpty()
                        ? getString(R.string.settings_local_folder_internal_storage)
                        : stripped;
            }
        }
        return uriStr;
    }

    // ── Cycle interval ────────────────────────────────────────────────────────

    /**
     * Prompt for a custom auto-cycle interval in seconds. A numeric EditText
     * inside the dialog keeps the D-pad IME workflow simple, and the saved
     * value is clamped by Setting.putWallCycleSeconds().
     */
    private void setCycleInterval(View v) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(Setting.WALL_CYCLE_MIN_SEC + "–" + Setting.WALL_CYCLE_MAX_SEC + " sec");
        input.setText(String.valueOf(Setting.getWallCycleSeconds()));
        input.setSelection(input.getText().length());

        new MaterialAlertDialogBuilder(this, R.style.DarkDialogAlert)
                .setTitle(R.string.settings_cycle_interval_title)
                .setView(input)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    int seconds;
                    try {
                        seconds = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        seconds = Setting.WALL_CYCLE_DEFAULT_SEC;
                    }
                    Setting.putWallCycleSeconds(seconds);
                    binding.wallCycleIntervalText.setText(
                            formatCycleSeconds(Setting.getWallCycleSeconds()));
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private String formatCycleSeconds(int seconds) {
        return getString(R.string.settings_cycle_seconds_format, seconds);
    }

    // ── About panel ───────────────────────────────────────────────────────────

    private void populateAbout() {
        bindAbout(binding.aboutAndroidVersion,  getString(R.string.about_android_version),
                Build.VERSION.RELEASE + "  (API " + Build.VERSION.SDK_INT + ")");
        bindAbout(binding.aboutBuildNumber,     getString(R.string.about_build_number),    Build.DISPLAY);
        bindAbout(binding.aboutBuildDate,       getString(R.string.about_build_date),      formatBuildTime(Build.TIME));
        bindAbout(binding.aboutSecurityPatch,   getString(R.string.about_security_patch),  safeSecurityPatch());
        bindAbout(binding.aboutKernel,          getString(R.string.about_kernel_version),  safeProp("os.version"));

        bindAbout(binding.aboutBrand,           getString(R.string.about_brand),           Build.BRAND);
        bindAbout(binding.aboutManufacturer,    getString(R.string.about_manufacturer),    Build.MANUFACTURER);
        bindAbout(binding.aboutModel,           getString(R.string.about_model),           Build.MODEL);
        bindAbout(binding.aboutDevice,          getString(R.string.about_device),          Build.DEVICE);
        bindAbout(binding.aboutProduct,         getString(R.string.about_product),         Build.PRODUCT);
        bindAbout(binding.aboutBoard,           getString(R.string.about_board),           Build.BOARD);
        bindAbout(binding.aboutHardware,        getString(R.string.about_hardware),        Build.HARDWARE);

        bindAbout(binding.aboutProcessor,       getString(R.string.about_processor),       readCpuModel());
        bindAbout(binding.aboutCpuArch,         getString(R.string.about_cpu_architecture), safeProp("os.arch"));
        bindAbout(binding.aboutAbis,            getString(R.string.about_supported_abis),  joinAbis());

        bindAbout(binding.aboutResolution,      getString(R.string.about_resolution),      formatResolution());
        bindAbout(binding.aboutDensity,         getString(R.string.about_density),         formatDensity());
        bindAbout(binding.aboutRefresh,         getString(R.string.about_refresh_rate),    formatRefresh());

        bindAbout(binding.aboutRam,             getString(R.string.about_ram),             formatRam());
        bindAbout(binding.aboutStorage,         getString(R.string.about_internal_storage), formatStorage());
        bindAbout(binding.aboutBootloader,      getString(R.string.about_bootloader),      Build.BOOTLOADER);

        bindAbout(binding.aboutAppVersion,      getString(R.string.about_app_version),
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

    // ── Language ──────────────────────────────────────────────────────────────

    private void showLanguageDialog() {
        final String[] langCodes  = {"en", "zh"};
        final String[] langLabels = {"English", "中文"};
        int current = "zh".equals(Setting.getLanguage()) ? 1 : 0;
        new MaterialAlertDialogBuilder(this, R.style.DarkDialogAlert)
                .setTitle(R.string.settings_language_dialog_title)
                .setSingleChoiceItems(langLabels, current, (dialog, which) -> {
                    dialog.dismiss();
                    if (langCodes[which].equals(Setting.getLanguage())) return;
                    Setting.putLanguage(langCodes[which]);
                    Intent restart = new Intent(this, MainActivity.class);
                    restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(restart);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
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
