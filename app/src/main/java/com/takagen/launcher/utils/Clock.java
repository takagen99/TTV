package com.takagen.launcher.utils;

import android.widget.TextView;

import com.takagen.launcher.App;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class Clock {

    private final Date date;
    private SimpleDateFormat format;
    private Callback callback;
    private TextView view;
    private Timer timer;

    public Clock() {
        this.date = new Date();
    }

    public static Clock create() {
        return new Clock();
    }

    public static Clock create(TextView view) {
        return new Clock().view(view).format("HH:mm:ss");
    }

    // takagen99 : new Clock format
    public static Clock create(TextView view, Locale locale) {
        return new Clock().view(view).format("EEE MMM dd, hh:mm aa", locale);
    }

    public Clock view(TextView view) {
        this.view = view;
        return this;
    }

    public Clock format(String format) {
        this.format = new SimpleDateFormat(format, Locale.getDefault());
        return this;
    }

    public Clock format(String format, Locale locale) {
        this.format = new SimpleDateFormat(format, locale);
        return this;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void start() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                App.post(() -> doJob());
            }
        }, 0, 1000);
    }

    private void doJob() {
        try {
            date.setTime(System.currentTimeMillis());
            if (callback != null) callback.onTimeChanged();
            if (view != null) view.setText(format.format(date));
        } catch (Exception ignored) {
        }
    }

    public Clock stop() {
        if (timer != null) timer.cancel();
        return this;
    }

    public void release() {
        if (timer != null) timer.cancel();
        if (callback != null) callback = null;
    }

    public interface Callback {

        void onTimeChanged();
    }
}
