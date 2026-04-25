package com.takagen.launcher.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.takagen.launcher.R;
import com.takagen.launcher.databinding.ActivityFolderBrowserBinding;
import com.takagen.launcher.databinding.ItemFolderRowBinding;
import com.takagen.launcher.utils.LocaleHelper;
import com.takagen.launcher.utils.Utils;
import com.takagen.launcher.utils.Wallpaper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Offline fallback folder picker. Used when {@link SettingsActivity#pickLocalFolder()}
 * can't launch the system SAF / DocumentsUI picker — common on slim Android TV
 * ROMs that ship without DocumentsUI.
 *
 * <p>Traverses the filesystem using {@link java.io.File}, shows one directory
 * at a time with its sub-folders, and returns the user's pick as an absolute
 * path via {@link Activity#setResult(int, Intent)}. The caller should treat
 * {@link #RESULT_DATA_PATH} in the result intent's data as a plain filesystem
 * path (no {@code file://} scheme) for easy enumeration later.
 *
 * <p>On API 30+ arbitrary folder access beyond MediaStore-visible directories
 * would require {@code MANAGE_EXTERNAL_STORAGE} (granted manually in system
 * Settings). For now we rely on legacy/MediaStore read permission only; if a
 * folder isn't listable it simply appears empty and the user can pick a
 * different one or back out.
 */
public class FolderBrowserActivity extends AppCompatActivity {

    /** Extra on the result intent carrying the chosen absolute path. */
    public static final String RESULT_DATA_PATH = "path";

    private static final int REQ_READ_STORAGE = 0x7001;

    private ActivityFolderBrowserBinding binding;
    private FolderAdapter adapter;

    private File current;

    public static void start(Activity activity, int requestCode) {
        activity.startActivityForResult(
                new Intent(activity, FolderBrowserActivity.class), requestCode);
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFolderBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);
        Wallpaper.applyBackdrop(this, binding.wallpaperBG);

        adapter = new FolderAdapter();
        binding.folderList.setLayoutManager(new LinearLayoutManager(this));
        binding.folderList.setAdapter(adapter);
        binding.folderList.setItemAnimator(null);

        binding.btnUp.setOnClickListener(v -> goUp());
        binding.btnCancel.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        binding.btnSelect.setOnClickListener(v -> returnSelected());

        // Start at the external storage root. This is typically /storage/emulated/0
        // and is the most recognisable starting point for TV users.
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

        List<Entry> entries = listSubfolders(dir);
        adapter.setEntries(entries);

        boolean empty = entries.isEmpty();
        binding.emptyMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.folderList.setVisibility(empty ? View.GONE : View.VISIBLE);

        // Focus the first folder row on arrival so D-pad navigation has somewhere
        // to land; fall back to the Select button for empty folders.
        binding.folderList.post(() -> {
            if (!empty) {
                RecyclerView.ViewHolder vh = binding.folderList.findViewHolderForAdapterPosition(0);
                if (vh != null) vh.itemView.requestFocus();
            } else {
                binding.btnSelect.requestFocus();
            }
        });
    }

    private void goUp() {
        if (current == null) return;
        File parent = current.getParentFile();
        if (parent != null) show(parent);
    }

    private void returnSelected() {
        if (current == null) {
            setResult(RESULT_CANCELED);
        } else {
            Intent data = new Intent();
            data.putExtra(RESULT_DATA_PATH, current.getAbsolutePath());
            setResult(RESULT_OK, data);
        }
        finish();
    }

    // ── Filesystem access ─────────────────────────────────────────────────────

    /**
     * Return the subfolders of {@code dir} (plus image-file counts for context),
     * sorted alphabetically, case-insensitive. Returns empty when the directory
     * is unreadable — caller interprets that as "pick-only, no children".
     */
    private List<Entry> listSubfolders(File dir) {
        File[] children = null;
        try {
            children = dir.listFiles();
        } catch (SecurityException ignored) { }
        if (children == null) return new ArrayList<>(0);

        Arrays.sort(children, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        List<Entry> out = new ArrayList<>();
        for (File f : children) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                out.add(new Entry(f, countImageFiles(f)));
            }
        }
        return out;
    }

    /** Shallow count of image files in {@code dir} for a quick "24 images" hint. */
    private int countImageFiles(File dir) {
        int count = 0;
        try {
            File[] kids = dir.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (k.isFile() && isImageName(k.getName())) count++;
                }
            }
        } catch (SecurityException ignored) { }
        return count;
    }

    private static boolean isImageName(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg")  || lower.endsWith(".jpeg")
            || lower.endsWith(".png")  || lower.endsWith(".webp")
            || lower.endsWith(".bmp")  || lower.endsWith(".gif");
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    /**
     * Request whichever storage-read permission is actually needed for the
     * running Android version. On API 33+ it's {@code READ_MEDIA_IMAGES}; on
     * API 23–32 it's {@code READ_EXTERNAL_STORAGE}; pre-23 nothing is needed
     * past the manifest-declared permissions (they're granted at install time).
     */
    private void ensureReadPermission() {
        String perm;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perm = Manifest.permission.READ_MEDIA_IMAGES;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            perm = Manifest.permission.READ_EXTERNAL_STORAGE;
        } else {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, REQ_READ_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_STORAGE && current != null) {
            // Re-render to pick up any newly-visible folders.
            show(current);
        }
    }

    // ── Lifecycle (mirror SettingsActivity for immersive fullscreen) ──────────

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

    // ── Entries + adapter ─────────────────────────────────────────────────────

    /** A single directory row (folder + shallow image count for context). */
    private static final class Entry {
        final File dir;
        final int  imageCount;
        Entry(File dir, int imageCount) { this.dir = dir; this.imageCount = imageCount; }
    }

    /**
     * Trivial RecyclerView adapter. Kept as an inner class so the whole
     * folder-browser feature is self-contained in one Java file + two XMLs.
     */
    private class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.VH> {
        private final List<Entry> entries = new ArrayList<>();

        void setEntries(List<Entry> newEntries) {
            entries.clear();
            entries.addAll(newEntries);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return new VH(ItemFolderRowBinding.inflate(inflater, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Entry e = entries.get(position);
            holder.binding.folderName.setText(e.dir.getName());
            String meta = e.imageCount > 0
                    ? holder.binding.getRoot().getContext()
                            .getString(R.string.folder_meta_images, e.imageCount)
                    : "";
            holder.binding.folderMeta.setText(meta);

            holder.itemView.setOnClickListener(v -> show(e.dir));
        }

        @Override public int getItemCount() { return entries.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ItemFolderRowBinding binding;
            VH(ItemFolderRowBinding b) { super(b.getRoot()); this.binding = b; }
        }
    }
}
