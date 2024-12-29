package com.takagen.launcher;


import com.takagen.launcher.utils.Prefers;


public class Setting {

    // 1. TMDB API Key
    public static String getTMDBKey() {
        return Prefers.getString("TMDBkey");
    }

    public static void putTMDBKey(String TMDBkey) {
        Prefers.put("TMDBkey", TMDBkey);
    }

    // 2. Wallpaper Type
    public static int getWall() {
        return Prefers.getInt("wall", 1);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", wall);
    }

    // 3. Auto-Cycle Wallpaper
    public static int getAuto() {
        return Prefers.getInt("auto", 1);
    }

    public static void putAuto(int wall) {
        Prefers.put("auto", wall);
    }

    // 4. Intercepting HOME button
    public static int getIntercept() {
        return Prefers.getInt("intercept", 1);
    }

    public static void putIntercept(int wall) {
        Prefers.put("intercept", wall);
    }

}
