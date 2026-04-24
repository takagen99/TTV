package com.takagen.launcher.utils;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takagen.launcher.App;
import com.takagen.launcher.R;
import com.takagen.launcher.Setting;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns the launcher backdrop — fetch, cache, and cycling.
 *
 * <p>Two sources are supported and picked via {@link Setting#getWallSource()}:
 * <ul>
 *   <li><b>TMDB</b> (default) — hits themoviedb.org with the user's API key and
 *       a bitmask of enabled categories (Trending / Anime / Movies / TV Series).
 *   <li><b>Local</b> — enumerates image files under a Storage Access Framework
 *       tree URI that the user picked in Settings.
 * </ul>
 *
 * <p>One public instance drives a single host activity, but the backing item
 * list and the "currently displayed backdrop URL" are kept in static fields so
 * that other screens (e.g. {@link com.takagen.launcher.ui.SettingsActivity})
 * can piggy-back on the same artwork without re-fetching.
 */
public class Wallpaper {

    // TMDB
    private static final String TMDB_BASE          = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE         = "https://image.tmdb.org/t/p/original";
    private static final int    PAGES_PER_TYPE     = 2; // lower than single-category era to cap fan-out
    private static final int    CONNECT_TIMEOUT_MS = 8_000;
    private static final int    READ_TIMEOUT_MS    = 10_000;

    // ── Cross-activity shared state ──
    // Cached items keyed by a compact "source:config" string so switching
    // activities doesn't refetch when nothing relevant changed.
    private static final List<Item> ITEMS       = Collections.synchronizedList(new ArrayList<>());
    private static volatile String  sCachedKey  = null;
    private static volatile String  sCurrentUrl;

    /** URL/URI of the backdrop currently displayed on the launcher, or null if none yet. */
    @Nullable
    public static String getCurrentUrl() {
        return sCurrentUrl;
    }

    /**
     * Paint the currently-displayed launcher backdrop into {@code target}, blurred
     * on API 31+. Caller is responsible for placing {@code target} behind a dim View
     * (typically {@code #B3000000}) so text stays legible — the same pattern used by
     * Settings / Show-Hide / Quick Settings.
     *
     * <p>No-ops gracefully if no backdrop has been loaded yet; in that case only
     * the blur is applied so the underlying activity background still gets softened.
     */
    public static void applyBackdrop(@NonNull Context ctx, @NonNull ImageView target) {
        String url = sCurrentUrl;
        if (!TextUtils.isEmpty(url)) {
            loadInto(Glide.with(ctx), url, target);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.setRenderEffect(
                    RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP));
        }
    }

    /**
     * Centralised Glide load dispatcher. We accept three flavours of
     * {@code loadable} strings from the rest of the code:
     * <ul>
     *   <li><b>http:// / https://</b> — TMDB URLs, loaded as-is.
     *   <li><b>content://</b> — SAF tree child URIs, loaded as-is.
     *   <li><b>Absolute filesystem path</b> ({@code /storage/emulated/0/…}) from
     *       the built-in folder browser. Passed to Glide as a {@link File}
     *       object rather than a {@code file://} URI, because on some Android
     *       TV builds the {@code file://} scheme is blocked under scoped storage
     *       while direct {@link File} access still works via the app's granted
     *       storage-read permissions.
     * </ul>
     */
    private static void loadInto(com.bumptech.glide.RequestManager glide,
                                 @NonNull String loadable,
                                 @NonNull ImageView target) {
        if (isLocalPath(loadable)) {
            glide.load(new File(loadable)).into(target);
        } else {
            glide.load(loadable).into(target);
        }
    }

    private static <T> com.bumptech.glide.RequestBuilder<T> buildLoad(
            @NonNull com.bumptech.glide.RequestManager glide,
            @NonNull Class<T> clazz,
            @NonNull String loadable) {
        com.bumptech.glide.RequestBuilder<T> b = glide.as(clazz);
        return isLocalPath(loadable)
                ? b.load(new File(loadable))
                : b.load(loadable);
    }

    /** True if {@code loadable} is a plain absolute filesystem path. */
    private static boolean isLocalPath(@NonNull String loadable) {
        return loadable.startsWith("/");
    }

    // ── Per-instance state (bound to one Activity) ──
    private final Activity activity;
    private final View     frame;
    private final View     background;
    private final TextView title;
    private final Animation enterAnim;

    private ExecutorService executor;
    private String cachedKey;

    private final Runnable cycle = new Runnable() {
        @Override public void run() {
            show();
            if (Setting.getAutoWall() == 0) App.post(this, cycleIntervalMs());
        }
    };

    public Wallpaper(Activity activity, View frame, View background,
                     TextView title, Animation enterAnim) {
        this.activity   = activity;
        this.frame      = frame;
        this.background = background;
        this.title      = title;
        this.enterAnim  = enterAnim;
    }

    // ── Public lifecycle hooks ────────────────────────────────────────────────

    /** Kick off fetch/enumeration for the current wallpaper source. */
    public void load() {
        final int source = Setting.getWallSource();
        final String key = buildCacheKey(source);
        cachedKey = key;

        // Already have items for this selection — render immediately, skip work.
        if (Objects.equals(sCachedKey, key) && !ITEMS.isEmpty()) {
            show();
            return;
        }

        if (source == Setting.WALL_SRC_LOCAL) {
            loadLocal(key);
        } else {
            loadTmdb(key);
        }
    }

    /** Call in onResume — refetches if selection changed, (re)starts auto-cycle. */
    public void onResume() {
        String currentKey = buildCacheKey(Setting.getWallSource());
        if (!Objects.equals(cachedKey, currentKey)) {
            load();
        } else if (!ITEMS.isEmpty()) {
            // Source unchanged but Auto Cycle or cycle interval may have just
            // flipped in Settings. Re-render now so the "cycle off = first
            // image" semantic takes effect without waiting for a fetch.
            show();
        }
        App.removeCallbacks(cycle);
        if (Setting.getAutoWall() == 0) App.post(cycle, cycleIntervalMs());
    }

    public void onPause() {
        App.removeCallbacks(cycle);
    }

    public void onDestroy() {
        App.removeCallbacks(cycle);
        if (executor != null) executor.shutdownNow();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Render a backdrop. Safe to call from the UI thread.
     *
     * <p>When Auto Cycle is <b>On</b> (pref value 0) we pick a random item each
     * tick so the display rotates. When Auto Cycle is <b>Off</b> we stick with
     * the first item in the list — that's the user's de-facto "static wallpaper".
     */
    public void show() {
        if (ITEMS.isEmpty()) return;
        final Item pick;
        synchronized (ITEMS) {
            boolean cycleOn = Setting.getAutoWall() == 0;
            pick = cycleOn
                    ? ITEMS.get(ThreadLocalRandom.current().nextInt(ITEMS.size()))
                    : ITEMS.get(0);
        }
        sCurrentUrl = pick.loadable;

        // Dispatch via buildLoad so absolute filesystem paths go through Glide's
        // File-based decoder rather than the file:// URI pipeline (see loadInto()).
        buildLoad(Glide.with(activity), Drawable.class, pick.loadable)
                .into(new CustomTarget<Drawable>() {
                    @Override public void onResourceReady(@NonNull Drawable res,
                                                          @Nullable Transition<? super Drawable> t) {
                        if (frame != null) {
                            frame.setBackgroundColor(activity.getResources().getColor(R.color.grey_900));
                        }
                        background.setBackground(res);
                        if (enterAnim != null) background.startAnimation(enterAnim);
                        if (title != null) title.setText(pick.name);
                    }
                    @Override public void onLoadCleared(@Nullable Drawable placeholder) { }
                });
    }

    // ── Cache key ─────────────────────────────────────────────────────────────

    /**
     * Encode everything that affects the <i>contents</i> of the backdrop pool
     * (not the cycle speed, not which image is picked) into a single comparable
     * string. When the key changes we must drop the old pool and re-populate.
     */
    private static String buildCacheKey(int source) {
        if (source == Setting.WALL_SRC_LOCAL) {
            return "local:" + Setting.getWallLocalFolder();
        }
        return "tmdb:" + Setting.getWallMask();
    }

    private static long cycleIntervalMs() {
        return Setting.getWallCycleSeconds() * 1000L;
    }

    // ── TMDB loader ───────────────────────────────────────────────────────────

    private void loadTmdb(String key) {
        if (TextUtils.isEmpty(Setting.getTMDBKey())) {
            // No API key yet — drop whatever was cached so we don't display
            // stale local-folder images under TMDB source.
            ITEMS.clear();
            sCachedKey = key;
            return;
        }

        ITEMS.clear();
        sCachedKey = key;
        if (executor != null) executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();

        final int mask = Setting.getWallMask();
        executor.execute(() -> {
            // Fetch each enabled category in turn. fetchPage() dedups by backdrop
            // path so overlaps between e.g. Trending and Movies don't pile up.
            for (int bit = 0; bit < 4 && !Thread.currentThread().isInterrupted(); bit++) {
                if ((mask & (1 << bit)) == 0) continue;
                for (int page = 1; page <= PAGES_PER_TYPE
                        && !Thread.currentThread().isInterrupted(); page++) {
                    fetchPage(bit, page);
                }
            }
            App.post(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) show();
            });
        });
    }

    private void fetchPage(int type, int page) {
        String url = buildUrl(type, page);
        if (url == null) return;

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return;

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 InputStreamReader reader = new InputStreamReader(in)) {
                JsonElement root = JsonParser.parseReader(reader);
                JsonArray results = root.getAsJsonObject().get("results").getAsJsonArray();

                List<Item> pageItems = new ArrayList<>(results.size());
                for (JsonElement el : results) {
                    Item item = parseTmdbItem(el.getAsJsonObject());
                    if (item != null && !containsLoadable(item.loadable)) pageItems.add(item);
                }
                ITEMS.addAll(pageItems);
            }
        } catch (Exception ignored) {
            // Silent — next page / next load will try again.
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean containsLoadable(String loadable) {
        synchronized (ITEMS) {
            for (Item i : ITEMS) if (i.loadable.equals(loadable)) return true;
        }
        return false;
    }

    @Nullable
    private static Item parseTmdbItem(JsonObject obj) {
        if (!obj.has("backdrop_path") || obj.get("backdrop_path").isJsonNull()) return null;
        String path = obj.get("backdrop_path").getAsString();
        String name = firstString(obj, "original_name", "title", "name");
        return new Item(IMAGE_BASE + path, name == null ? "" : name);
    }

    @Nullable
    private static String firstString(JsonObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.get(k).isJsonNull()) return obj.get(k).getAsString();
        }
        return null;
    }

    @Nullable
    private static String buildUrl(int type, int page) {
        String key = Setting.getTMDBKey();
        if (TextUtils.isEmpty(key)) return null;
        String endpoint;
        String extra = "";
        switch (type) {
            case 0: endpoint = "/trending/all/week";  break;
            case 2: endpoint = "/discover/movie";     extra = "&sort_by=popularity.desc"; break;
            case 3: endpoint = "/trending/tv/week";   break;
            case 1:
            default:
                endpoint = "/discover/tv";
                extra    = "&with_genres=16&with_keywords=210024";
                break;
        }
        return String.format(Locale.ROOT,
                "%s%s?api_key=%s&language=en-US&page=%d%s",
                TMDB_BASE, endpoint, key, page, extra);
    }

    // ── Local folder loader ───────────────────────────────────────────────────

    /**
     * Enumerate image files under the user's picked folder. Two source shapes
     * are supported based on how the folder was originally picked in Settings:
     * <ul>
     *   <li>SAF content URI ({@code content://…/tree/…}) — iterated via
     *       {@link DocumentsContract}. Persistable read permission must have
     *       been taken at pick-time for this to work across launches.
     *   <li>Plain absolute filesystem path (starts with {@code /}) — iterated
     *       via {@link java.io.File#listFiles()}. Used by the built-in
     *       {@link com.takagen.launcher.ui.FolderBrowserActivity} fallback on
     *       TV ROMs that don't ship DocumentsUI.
     * </ul>
     */
    private void loadLocal(String key) {
        final String folderRef = Setting.getWallLocalFolder();
        if (TextUtils.isEmpty(folderRef)) {
            ITEMS.clear();
            sCachedKey = key;
            return;
        }

        ITEMS.clear();
        sCachedKey = key;
        if (executor != null) executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();

        final Context appContext = activity.getApplicationContext();
        executor.execute(() -> {
            String diagnostic = null;
            try {
                if (folderRef.startsWith("content://")) {
                    enumerateSafFolder(appContext, folderRef);
                } else {
                    diagnostic = enumerateFilePath(appContext, folderRef);
                }
            } catch (Exception e) {
                // Permission revoked, URI stale, or enumeration failed. ITEMS
                // stays empty and show() will be a no-op until the user picks
                // a new folder.
                diagnostic = "Couldn't read folder (" + e.getClass().getSimpleName() + ")";
            }

            final String diag = ITEMS.isEmpty() ? diagnostic : null;
            App.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                show();
                if (diag != null) {
                    Toast.makeText(activity, diag, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /** SAF / DocumentsContract enumeration — works on the tree URI you persisted. */
    private static void enumerateSafFolder(Context ctx, String treeUriStr) {
        Uri treeUri = Uri.parse(treeUriStr);
        String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);

        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor c = ctx.getContentResolver()
                .query(childrenUri, projection, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext() && !Thread.currentThread().isInterrupted()) {
                String mime = c.getString(2);
                if (mime == null || !mime.startsWith("image/")) continue;
                String docId = c.getString(0);
                String name  = c.getString(1);
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                ITEMS.add(new Item(childUri.toString(),
                        name == null ? "" : stripExtension(name)));
            }
        }
    }

    /**
     * Enumerate image files in a user-picked folder. Tries two channels in
     * sequence so we survive Android 11+ scoped-storage restrictions:
     *
     * <ol>
     *   <li><b>Plain filesystem</b> via {@link File#listFiles()}. Fastest on
     *       pre-Android-11 devices and on devices with All-Files access.
     *   <li><b>MediaStore</b> via {@link MediaStore.Images.Media}. On Android
     *       11+ without All-Files access, {@code listFiles()} silently returns
     *       an <i>empty array</i> (not null, not a SecurityException) for many
     *       folders — the folder is visible but not enumerable. MediaStore
     *       indexes images in Pictures/, DCIM/, Download/, and most user-
     *       sideloaded media, and works under {@code READ_MEDIA_IMAGES} alone,
     *       so we fall back to it and load via {@code content://} URIs.
     * </ol>
     *
     * <p>Returns {@code null} on success or a user-facing diagnostic message
     * when the folder truly yields no images through either channel.
     */
    @Nullable
    private static String enumerateFilePath(Context ctx, String absPath) {
        File dir = new File(absPath);
        if (!dir.exists()) {
            return "Wallpaper folder no longer exists:\n" + absPath;
        }
        if (!dir.isDirectory()) {
            return "Wallpaper folder is not a directory:\n" + absPath;
        }

        // Attempt 1 — plain filesystem.
        int fsAdded = addFromFilesystem(dir);

        // Attempt 2 — MediaStore (covers the scoped-storage empty-array case).
        int msAdded = addFromMediaStore(ctx, absPath);

        if (fsAdded + msAdded > 0) return null;

        return "No images found in this folder.\n"
                + "On Android 11+, TTV can only see images that MediaStore\n"
                + "has indexed (Pictures/, DCIM/, Download/ — and custom\n"
                + "folders after a media rescan). To use a folder outside\n"
                + "those, grant \"All files access\" to TTV in system Settings\n"
                + "and re-pick the folder.";
    }

    /** Shallow File-API pass. Returns the number of items added. */
    private static int addFromFilesystem(File dir) {
        File[] kids;
        try {
            kids = dir.listFiles();
        } catch (SecurityException ignored) {
            return 0;
        }
        if (kids == null || kids.length == 0) return 0;

        java.util.Arrays.sort(kids, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        int added = 0;
        for (File f : kids) {
            if (Thread.currentThread().isInterrupted()) return added;
            if (!f.isFile() || !isImageFileName(f.getName())) continue;
            ITEMS.add(new Item(f.getAbsolutePath(), stripExtension(f.getName())));
            added++;
        }
        return added;
    }

    /**
     * MediaStore-backed enumeration of images whose on-disk path is a direct
     * child of {@code absPath}. Results are stored as {@code content://} URIs
     * which Glide loads via the ContentResolver pipeline — no direct file
     * access required, so this works under scoped storage where plain
     * {@link File#listFiles()} returns an empty array.
     *
     * <p>Only direct children (not nested sub-folders) are kept to mirror the
     * filesystem pass's semantics.
     */
    private static int addFromMediaStore(Context ctx, String absPath) {
        String normalized = absPath.endsWith("/") ? absPath : absPath + "/";
        int added = 0;
        String[] projection = new String[] {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
        };
        // DATA is the absolute file path. Still readable (not deprecated for
        // reads) on API 30+. We filter to direct children of absPath in the
        // cursor loop to avoid a SQL LIKE-with-escape dance.
        String selection = MediaStore.Images.Media.DATA + " LIKE ?";
        String[] selArgs = new String[] { normalized + "%" };

        try (Cursor c = ctx.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selArgs,
                MediaStore.Images.Media.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (c == null) return 0;
            while (c.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) return added;
                long   id       = c.getLong(0);
                String filePath = c.getString(1);
                String name     = c.getString(2);

                // Direct-child check: must start with normalized, and have no
                // further slashes after the prefix.
                if (filePath == null || !filePath.startsWith(normalized)) continue;
                String rest = filePath.substring(normalized.length());
                if (rest.isEmpty() || rest.contains("/")) continue;

                // Drop if we already got this path from the filesystem pass —
                // loadable forms would differ (absolute path vs content URI),
                // so dedup on filePath.
                if (alreadyHavePath(filePath)) continue;

                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                ITEMS.add(new Item(uri.toString(),
                        name == null ? "" : stripExtension(name)));
                added++;
            }
        } catch (Exception ignored) {
            // Non-fatal — caller will emit a generic diagnostic if nothing got added.
        }
        return added;
    }

    /**
     * True if {@link #ITEMS} already contains an entry whose loadable either
     * equals the given filesystem path (filesystem pass) or whose underlying
     * content URI corresponds to it (theoretical race — kept for safety).
     */
    private static boolean alreadyHavePath(@NonNull String filePath) {
        synchronized (ITEMS) {
            for (Item i : ITEMS) {
                if (i.loadable.equals(filePath)) return true;
            }
        }
        return false;
    }

    private static boolean isImageFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")  || lower.endsWith(".jpeg")
            || lower.endsWith(".png")  || lower.endsWith(".webp")
            || lower.endsWith(".bmp")  || lower.endsWith(".gif")
            || lower.endsWith(".heic") || lower.endsWith(".heif");
    }

    /** Strip "foo.jpg" → "foo" for a friendlier title display. */
    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    // ── Small value type ──────────────────────────────────────────────────────

    /** One renderable wallpaper. {@code loadable} is ready for {@code Glide.load}. */
    private static final class Item {
        final String loadable;
        final String name;
        Item(String loadable, String name) {
            this.loadable = loadable;
            this.name     = name;
        }
    }
}
