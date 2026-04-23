package com.takagen.launcher.utils;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.widget.TextView;

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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns the TMDB backdrop fetch + cycling behaviour previously inlined in MainActivity.
 *
 * <p>One public instance drives a single host activity, but the backing item list and
 * the "currently displayed backdrop URL" are kept in static fields so that other
 * screens (e.g. {@link com.takagen.launcher.ui.SettingsActivity}) can piggy-back on
 * the same artwork without re-fetching.
 */
public class Wallpaper {

    // TMDB
    private static final String TMDB_BASE      = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE     = "https://image.tmdb.org/t/p/original";
    private static final int    PAGES_PER_TYPE = 3;
    private static final int    CONNECT_TIMEOUT_MS = 8_000;
    private static final int    READ_TIMEOUT_MS    = 10_000;

    // Auto-cycle period when Setting.getAutoWall() == 0 (On).
    private static final long   CYCLE_MS       = 30_000L;

    // ── Cross-activity shared state ──
    // Cached items keyed by wallpaper type, so switching activities doesn't refetch.
    private static final List<Item> ITEMS = Collections.synchronizedList(new ArrayList<>());
    private static volatile int     sCachedType = -1;
    private static volatile String  sCurrentUrl;

    /** URL of the backdrop currently displayed on the launcher, or null if none yet. */
    @Nullable
    public static String getCurrentUrl() {
        return sCurrentUrl;
    }

    // ── Per-instance state (bound to one Activity) ──
    private final Activity activity;
    private final View     frame;
    private final View     background;
    private final TextView title;
    private final Animation enterAnim;

    private ExecutorService executor;
    private int wallType = -1;

    private final Runnable cycle = new Runnable() {
        @Override public void run() {
            show();
            if (Setting.getAutoWall() == 0) App.post(this, CYCLE_MS);
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

    /** Kick off fetch if TMDB key is configured. */
    public void load() {
        if (TextUtils.isEmpty(Setting.getTMDBKey())) return;
        wallType = Setting.getWall();

        // Already have items for this type — render immediately, skip the network.
        if (sCachedType == wallType && !ITEMS.isEmpty()) {
            show();
            return;
        }

        ITEMS.clear();
        sCachedType = wallType;

        if (executor != null) executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();
        final int type = wallType;
        executor.execute(() -> {
            for (int page = 1; page <= PAGES_PER_TYPE && !Thread.currentThread().isInterrupted(); page++) {
                fetchPage(type, page);
            }
            App.post(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) show();
            });
        });
    }

    /** Call in onResume — refetches if wallpaper type changed, (re)starts auto-cycle. */
    public void onResume() {
        if (wallType != Setting.getWall()) {
            load();
        }
        App.removeCallbacks(cycle);
        if (Setting.getAutoWall() == 0) App.post(cycle, CYCLE_MS);
    }

    public void onPause() {
        App.removeCallbacks(cycle);
    }

    public void onDestroy() {
        App.removeCallbacks(cycle);
        if (executor != null) executor.shutdownNow();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /** Pick a random backdrop and render it. Safe to call from the UI thread. */
    public void show() {
        if (ITEMS.isEmpty()) return;
        final Item pick;
        synchronized (ITEMS) {
            pick = ITEMS.get(ThreadLocalRandom.current().nextInt(ITEMS.size()));
        }
        sCurrentUrl = IMAGE_BASE + pick.path;

        Glide.with(activity).load(sCurrentUrl).into(new CustomTarget<Drawable>() {
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

    // ── TMDB fetch ────────────────────────────────────────────────────────────

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
                    Item item = parseItem(el.getAsJsonObject());
                    if (item != null) pageItems.add(item);
                }
                ITEMS.addAll(pageItems);
            }
        } catch (Exception ignored) {
            // Silent — next page / next load will try again.
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Nullable
    private static Item parseItem(JsonObject obj) {
        if (!obj.has("backdrop_path") || obj.get("backdrop_path").isJsonNull()) return null;
        String path = obj.get("backdrop_path").getAsString();
        String name = firstString(obj, "original_name", "title", "name");
        return new Item(path, name == null ? "" : name);
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

    // ── Small value type ──────────────────────────────────────────────────────
    private static final class Item {
        final String path;
        final String name;
        Item(String path, String name) { this.path = path; this.name = name; }
    }
}
