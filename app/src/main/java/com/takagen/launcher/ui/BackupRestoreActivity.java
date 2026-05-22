package com.takagen.launcher.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.takagen.launcher.R;
import com.takagen.launcher.databinding.ActivityBackupRestoreBinding;
import com.takagen.launcher.databinding.ItemBackupRowBinding;
import com.takagen.launcher.utils.LocaleHelper;
import com.takagen.launcher.utils.Utils;
import com.takagen.launcher.utils.Wallpaper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Backup and restore all launcher settings to/from a JSON file stored in
 * the public Downloads folder under a "TTV" sub-directory:
 *   /sdcard/Download/TTV/ttv_backup_YYYYMMDD_HHmmss.json
 *
 * Permission notes:
 *   API ≤ 28 — WRITE_EXTERNAL_STORAGE (declared in manifest with maxSdkVersion="28").
 *   API 29   — requestLegacyExternalStorage=true covers it, no extra permission.
 *   API 30+  — MANAGE_EXTERNAL_STORAGE must be granted; if not the user is
 *              directed to the system "All files access" settings page.
 */
public class BackupRestoreActivity extends AppCompatActivity {

    private static final String BACKUP_DIR    = "backups";
    private static final String BACKUP_PREFIX = "ttv_backup_";
    private static final String BACKUP_EXT    = ".json";
    private static final int    BACKUP_VERSION = 1;

    /** SharedPreferences int keys that are backed up. */
    private static final String[] INT_KEYS = {
            "wallMask", "autoWall", "wallSource", "wallCycleSec",
            "tvRowIdx", "mobRowIdx", "autoStart"
    };
    /** SharedPreferences string keys that are backed up. */
    private static final String[] STRING_KEYS = {
            "TMDBkey", "wallLocalFolder", "language"
    };

    private ActivityBackupRestoreBinding binding;
    private final List<File> backups = new ArrayList<>();
    private BackupAdapter adapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, BackupRestoreActivity.class));
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBackupRestoreBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);
        Wallpaper.applyBackdrop(this, binding.wallpaperBG);

        adapter = new BackupAdapter();
        binding.backupList.setLayoutManager(new LinearLayoutManager(this));
        binding.backupList.setAdapter(adapter);
        binding.backupList.setItemAnimator(null);

        binding.btnBackupNow.setOnClickListener(v -> createBackup());

        loadBackups();
    }

    // ── Storage access ────────────────────────────────────────────────────────

    /** Returns true if the app can write to the public Downloads folder. */
    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true; // API ≤ 29: legacy storage / WRITE_EXTERNAL_STORAGE handles it
    }

    /** Shows a toast and opens the system "All files access" settings page. */
    private void requestStorageAccess() {
        Toast.makeText(this, R.string.backup_need_storage_permission, Toast.LENGTH_LONG).show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        }
    }

    // ── Backup directory ──────────────────────────────────────────────────────

    private File getBackupDir() {
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir  = new File(base, "TTV");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ── List existing backups ─────────────────────────────────────────────────

    private void loadBackups() {
        if (!hasStorageAccess()) {
            binding.emptyMessage.setVisibility(View.VISIBLE);
            binding.backupList.setVisibility(View.GONE);
            binding.btnBackupNow.requestFocus();
            return;
        }
        backups.clear();
        File dir = getBackupDir();
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles(f ->
                    f.isFile()
                    && f.getName().startsWith(BACKUP_PREFIX)
                    && f.getName().endsWith(BACKUP_EXT));
            if (files != null) {
                List<File> list = new ArrayList<>(Arrays.asList(files));
                Collections.sort(list, (a, b) ->
                        Long.compare(b.lastModified(), a.lastModified()));
                backups.addAll(list);
            }
        }
        adapter.notifyDataSetChanged();
        boolean empty = backups.isEmpty();
        binding.emptyMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.backupList.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) binding.btnBackupNow.requestFocus();
    }

    // ── Create backup ─────────────────────────────────────────────────────────

    private void createBackup() {
        if (!hasStorageAccess()) { requestStorageAccess(); return; }
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            JSONObject settings = new JSONObject();
            for (String key : INT_KEYS) {
                if (prefs.contains(key)) settings.put(key, prefs.getInt(key, 0));
            }
            for (String key : STRING_KEYS) {
                settings.put(key, prefs.getString(key, ""));
            }

            JSONObject root = new JSONObject();
            root.put("version", BACKUP_VERSION);
            root.put("timestamp", System.currentTimeMillis());
            root.put("settings", settings);

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File file = new File(getBackupDir(), BACKUP_PREFIX + ts + BACKUP_EXT);

            try (FileWriter fw = new FileWriter(file)) {
                fw.write(root.toString(2));
            }

            Toast.makeText(this,
                    getString(R.string.backup_success, file.getName()),
                    Toast.LENGTH_SHORT).show();
            loadBackups();
        } catch (Exception e) {
            Toast.makeText(this, R.string.backup_fail, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Options dialog (Restore / Delete) ────────────────────────────────────

    private void showBackupOptions(File file) {
        String displayName = formatFileName(file.getName());
        new MaterialAlertDialogBuilder(this, R.style.DarkDialogAlert)
                .setTitle(displayName)
                .setItems(new String[]{
                        getString(R.string.backup_btn_restore),
                        getString(R.string.backup_btn_delete)
                }, (dialog, which) -> {
                    if (which == 0) confirmRestore(file);
                    else confirmDelete(file);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    private void confirmRestore(File file) {
        new MaterialAlertDialogBuilder(this, R.style.DarkDialogAlert)
                .setTitle(R.string.backup_confirm_title)
                .setMessage(getString(R.string.backup_confirm_msg, formatFileName(file.getName())))
                .setPositiveButton(R.string.btn_ok, (d, w) -> restoreBackup(file))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void restoreBackup(File file) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            JSONObject root     = new JSONObject(sb.toString());
            JSONObject settings = root.getJSONObject("settings");

            SharedPreferences.Editor editor =
                    PreferenceManager.getDefaultSharedPreferences(this).edit();
            for (String key : INT_KEYS) {
                if (settings.has(key)) editor.putInt(key, settings.getInt(key));
            }
            for (String key : STRING_KEYS) {
                if (settings.has(key)) editor.putString(key, settings.getString(key));
            }
            editor.apply();

            Toast.makeText(this, R.string.backup_restored, Toast.LENGTH_SHORT).show();

            Intent restart = new Intent(this, MainActivity.class);
            restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(restart);
        } catch (Exception e) {
            Toast.makeText(this, R.string.backup_restore_fail, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private void confirmDelete(File file) {
        new MaterialAlertDialogBuilder(this, R.style.DarkDialogAlert)
                .setTitle(R.string.backup_confirm_delete_title)
                .setMessage(getString(R.string.backup_confirm_delete_msg,
                        formatFileName(file.getName())))
                .setPositiveButton(R.string.btn_ok, (d, w) -> deleteBackup(file))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void deleteBackup(File file) {
        if (file.delete()) {
            Toast.makeText(this, R.string.backup_deleted, Toast.LENGTH_SHORT).show();
            loadBackups();
        } else {
            Toast.makeText(this, R.string.backup_fail, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    /** Converts "ttv_backup_20240415_143022.json" to "15 Apr 2024, 14:30:22". */
    private String formatFileName(String name) {
        try {
            String stripped = name
                    .replace(BACKUP_PREFIX, "")
                    .replace(BACKUP_EXT, "");
            Date d = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).parse(stripped);
            if (d != null) {
                return new SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault()).format(d);
            }
        } catch (Exception ignored) { }
        return name;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
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

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class BackupAdapter extends RecyclerView.Adapter<BackupAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemBackupRowBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(backups.get(position));
        }

        @Override
        public int getItemCount() {
            return backups.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            private final ItemBackupRowBinding binding;

            Holder(ItemBackupRowBinding b) {
                super(b.getRoot());
                this.binding = b;

                b.getRoot().setOnFocusChangeListener((v, hasFocus) ->
                        v.animate()
                                .scaleX(hasFocus ? 1.02f : 1.0f)
                                .scaleY(hasFocus ? 1.02f : 1.0f)
                                .setDuration(100)
                                .start());

                b.getRoot().setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) showBackupOptions(backups.get(pos));
                });
            }

            void bind(File file) {
                binding.backupDate.setText(formatFileName(file.getName()));
                binding.backupSize.setText(humanBytes(file.length()));
            }
        }
    }
}
