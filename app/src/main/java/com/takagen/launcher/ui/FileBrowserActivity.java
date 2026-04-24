package com.takagen.launcher.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.takagen.launcher.databinding.ActivityFileBrowserBinding;
import com.takagen.launcher.databinding.ItemFileRowBinding;
import com.takagen.launcher.utils.Utils;
import com.takagen.launcher.utils.Wallpaper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * General-purpose file browser for navigating the local filesystem.
 * Displays folders and media files (images, videos, APKs). Tapping a
 * folder navigates into it; tapping a file opens it with the appropriate
 * system app via {@link Intent#ACTION_VIEW}.
 */
public class FileBrowserActivity extends AppCompatActivity {

    private static final int REQ_READ_STORAGE = 0x7002;

    private ActivityFileBrowserBinding binding;
    private FileAdapter adapter;
    private File current;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, FileBrowserActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFileBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);
        Wallpaper.applyBackdrop(this, binding.wallpaperBG);

        adapter = new FileAdapter();
        binding.fileList.setLayoutManager(new LinearLayoutManager(this));
        binding.fileList.setAdapter(adapter);
        binding.fileList.setItemAnimator(null);

        binding.btnUp.setOnClickListener(v -> goUp());
        binding.btnClose.setOnClickListener(v -> finish());

        File start = Environment.getExternalStorageDirectory();
        if (start == null || !start.exists()) start = new File("/");
        show(start);

        ensureReadPermission();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void show(File dir) {
        current = dir;
        binding.currentPath.setText(dir.getAbsolutePath());
        binding.btnUp.setEnabled(dir.getParentFile() != null);

        List<Entry> entries = listEntries(dir);
        adapter.setEntries(entries);

        boolean empty = entries.isEmpty();
        binding.emptyMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.fileList.setVisibility(empty ? View.GONE : View.VISIBLE);

        binding.fileList.post(() -> {
            if (!empty) {
                RecyclerView.ViewHolder vh = binding.fileList.findViewHolderForAdapterPosition(0);
                if (vh != null) vh.itemView.requestFocus();
            } else {
                binding.btnClose.requestFocus();
            }
        });
    }

    private void goUp() {
        if (current == null) return;
        File parent = current.getParentFile();
        if (parent != null) show(parent);
    }

    private void openFile(File file) {
        String mime = getMimeType(file);
        if (mime == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(file);
            }
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) { }
    }

    // ── Filesystem listing ────────────────────────────────────────────────────

    private List<Entry> listEntries(File dir) {
        File[] children = null;
        try {
            children = dir.listFiles();
        } catch (SecurityException ignored) { }
        if (children == null) return new ArrayList<>(0);

        // Directories first, then files; each group sorted alphabetically.
        Arrays.sort(children, (a, b) -> {
            boolean aDir = a.isDirectory(), bDir = b.isDirectory();
            if (aDir != bDir) return aDir ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        List<Entry> out = new ArrayList<>();
        for (File f : children) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                out.add(new Entry(f, EntryType.DIR));
            } else {
                EntryType type = typeOf(f);
                if (type != null) out.add(new Entry(f, type));
            }
        }
        return out;
    }

    private EntryType typeOf(File file) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".apk")) return EntryType.APK;
        if (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi")
                || n.endsWith(".mov") || n.endsWith(".wmv") || n.endsWith(".3gp")
                || n.endsWith(".m4v") || n.endsWith(".ts") || n.endsWith(".webm")) {
            return EntryType.VIDEO;
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".webp") || n.endsWith(".bmp") || n.endsWith(".gif")) {
            return EntryType.IMAGE;
        }
        return null;
    }

    private String getMimeType(File file) {
        EntryType type = typeOf(file);
        if (type == null) return null;
        switch (type) {
            case IMAGE: return "image/*";
            case VIDEO: return "video/*";
            case APK:   return "application/vnd.android.package-archive";
            default:    return null;
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private void ensureReadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            List<String> needed = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (!needed.isEmpty()) {
                requestPermissions(needed.toArray(new String[0]), REQ_READ_STORAGE);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQ_READ_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_STORAGE && current != null) show(current);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (current != null && current.getParentFile() != null) {
            goUp();
        } else {
            super.onBackPressed();
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

    // ── Data model ────────────────────────────────────────────────────────────

    private enum EntryType { DIR, IMAGE, VIDEO, APK }

    private static final class Entry {
        final File file;
        final EntryType type;
        Entry(File file, EntryType type) { this.file = file; this.type = type; }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private final List<Entry> entries = new ArrayList<>();

        void setEntries(List<Entry> newEntries) {
            entries.clear();
            entries.addAll(newEntries);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemFileRowBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Entry e = entries.get(position);
            holder.binding.fileName.setText(e.file.getName());
            switch (e.type) {
                case DIR:
                    holder.binding.fileTypeLabel.setText("DIR");
                    int count = countChildren(e.file);
                    holder.binding.fileMeta.setText(
                            count > 0 ? count + " item" + (count == 1 ? "" : "s") : "");
                    break;
                case IMAGE:
                    holder.binding.fileTypeLabel.setText("IMG");
                    holder.binding.fileMeta.setText(humanSize(e.file.length()));
                    break;
                case VIDEO:
                    holder.binding.fileTypeLabel.setText("VID");
                    holder.binding.fileMeta.setText(humanSize(e.file.length()));
                    break;
                case APK:
                    holder.binding.fileTypeLabel.setText("APK");
                    holder.binding.fileMeta.setText(humanSize(e.file.length()));
                    break;
            }
            holder.itemView.setOnClickListener(v -> {
                if (e.type == EntryType.DIR) show(e.file);
                else openFile(e.file);
            });
        }

        @Override public int getItemCount() { return entries.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ItemFileRowBinding binding;
            VH(ItemFileRowBinding b) { super(b.getRoot()); this.binding = b; }
        }
    }

    private int countChildren(File dir) {
        try {
            File[] kids = dir.listFiles();
            return kids == null ? 0 : kids.length;
        } catch (SecurityException e) {
            return 0;
        }
    }

    private String humanSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB"};
        int i = 0;
        double v = bytes;
        while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return i == 0
                ? String.format(Locale.getDefault(), "%d %s", (long) v, units[i])
                : String.format(Locale.getDefault(), "%.1f %s", v, units[i]);
    }
}
