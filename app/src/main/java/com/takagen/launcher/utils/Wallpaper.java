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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the TMDB backdrop fetch + cycling behaviour previously inlined in MainActivity.
 */
public class Wallpaper {

    private static final String BASE = "https://image.tmdb.org/t/p/original";
    private static final long CYCLE_MS = 30_000L;

    private final Activity activity;
    private final View frame;
    private final View background;
    private final TextView title;
    private final Animation enterAnim;
    private final List<String[]> items = new CopyOnWriteArrayList<>();

    private final Runnable cycle = new Runnable() {
        @Override
        public void run() {
            show();
            if (Setting.getAutoWall() == 0) App.post(this, CYCLE_MS);
        }
    };

    private int wallType = -1;

    public Wallpaper(Activity activity, View frame, View background, TextView title, Animation enterAnim) {
        this.activity = activity;
        this.frame = frame;
        this.background = background;
        this.title = title;
        this.enterAnim = enterAnim;
    }

    /** Kick off fetch if TMDB key is configured. */
    public void load() {
        if (TextUtils.isEmpty(Setting.getTMDBKey())) return;
        wallType = Setting.getWall();
        new Thread(() -> {
            for (int page = 1; page <= 3; page++) fetchPage(wallType, page);
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) show();
            });
        }).start();
    }

    /** Call in onResume — refetches if wallpaper type changed, starts auto-cycle if enabled. */
    public void onResume() {
        if (wallType != Setting.getWall()) {
            items.clear();
            load();
        }
        App.removeCallbacks(cycle);
        if (Setting.getAutoWall() == 0) App.post(cycle, CYCLE_MS);
    }

    public void onPause() {
        App.removeCallbacks(cycle);
    }

    /** Pick a random backdrop and render it. */
    public void show() {
        if (items.isEmpty()) return;
        String[] pick = items.get((int) (Math.random() * items.size()));
        try {
            Glide.with(activity).load(BASE + pick[0]).into(new CustomTarget<Drawable>() {
                @Override
                public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                    if (frame != null) {
                        frame.setBackgroundColor(activity.getResources().getColor(R.color.grey_900));
                    }
                    background.setBackground(resource);
                    background.startAnimation(enterAnim);
                    if (title != null) title.setText(pick[1]);
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                }
            });
        } catch (Exception e) {
            App.removeCallbacks(cycle);
        }
    }

    private void fetchPage(int type, int page) {
        String apiKey = Setting.getTMDBKey();
        String url;
        switch (type) {
            case 0: url = "https://api.themoviedb.org/3/trending/all/week?api_key=" + apiKey + "&language=en-US&page=" + page; break;
            case 2: url = "https://api.themoviedb.org/3/discover/movie?api_key=" + apiKey + "&language=en-US&sort_by=popularity.desc&page=" + page; break;
            case 3: url = "https://api.themoviedb.org/3/trending/tv/week?api_key=" + apiKey + "&language=en-US&page=" + page; break;
            case 1:
            default: url = "https://api.themoviedb.org/3/discover/tv?api_key=" + apiKey + "&language=en-US&with_genres=16&with_keywords=210024&page=" + page; break;
        }

        try {
            URLConnection conn = new URL(url).openConnection();
            conn.connect();
            JsonElement root = JsonParser.parseReader(new InputStreamReader((InputStream) conn.getContent()));
            JsonArray results = root.getAsJsonObject().get("results").getAsJsonArray();

            List<String[]> page_items = new ArrayList<>();
            for (JsonElement el : results) {
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("backdrop_path") || obj.get("backdrop_path").isJsonNull()) continue;

                String path = obj.get("backdrop_path").getAsString();
                String name = "";
                if (obj.has("original_name")) name = obj.get("original_name").getAsString();
                else if (obj.has("title")) name = obj.get("title").getAsString();
                else if (obj.has("name")) name = obj.get("name").getAsString();
                page_items.add(new String[]{path, name});
            }
            items.addAll(page_items);
        } catch (Exception ignored) {
        }
    }
}
