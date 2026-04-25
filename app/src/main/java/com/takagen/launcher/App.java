package com.takagen.launcher;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.os.HandlerCompat;

import com.takagen.launcher.utils.LocaleHelper;

public class App extends Application {

    private static App instance;
    private final Handler handler;

    public App() {
        instance = this;
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return App.instance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        Context wrapped = LocaleHelper.wrap(base);
        super.attachBaseContext(wrapped);
        Init.set(wrapped);
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

}
