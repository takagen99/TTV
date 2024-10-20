package com.takagen.launcher.bean;

import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;

import com.takagen.launcher.App;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppInfo {

    private final Drawable icon;
    private final Drawable banner;
    private final String name;
    private final String pack;

    public AppInfo(String name, String pack, Drawable icon, Drawable banner) {
        this.name = name;
        this.pack = pack;
        this.icon = icon;
        this.banner = banner;
    }

    public static AppInfo get(ApplicationInfo info) {
        Drawable icon = info.loadIcon(App.get().getPackageManager());
        Drawable banner = info.loadBanner(App.get().getPackageManager());
        String name = info.loadLabel(App.get().getPackageManager()).toString();
        String pack = info.packageName;
        return new AppInfo(name, pack, icon, banner);
    }

    public String getName() {
        return name;
    }

    public String getPack() {
        return pack;
    }

    public Drawable getIcon() {
        return icon;
    }

    public Drawable getBanner() {
        return banner;
    }

    public static class Sorter implements Comparator<AppInfo> {

        public static void sort(List<AppInfo> items) {
            Collections.sort(items, new Sorter());
        }

        @Override
        public int compare(AppInfo info1, AppInfo info2) {
            return info1.getName().compareToIgnoreCase(info2.getName());
        }
    }
}
