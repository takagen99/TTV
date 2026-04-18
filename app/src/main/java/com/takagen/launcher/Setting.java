package com.takagen.launcher;

import com.takagen.launcher.utils.Prefers;

public class Setting {

    // Preset dp values for row height cycling
    public static final int[] TV_ROW_HEIGHTS   = {110, 120, 130, 145, 160, 180};
    public static final int[] MOB_ROW_HEIGHTS  = {100, 110, 120, 130, 145, 160};
    private static final int TV_ROW_DEFAULT    = 2; // 130dp
    private static final int MOB_ROW_DEFAULT   = 2; // 120dp

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
    public static int getAutoWall() {
        return Prefers.getInt("autoWall", 1);
    }

    public static void putAutoWall(int autoWall) {
        Prefers.put("autoWall", autoWall);
    }

    // 4. Auto-Start App
    public static int getAutoStart() {
        return Prefers.getInt("autoStart", 1);
    }

    public static void putAutoStart(int autoStart) {
        Prefers.put("autoStart", autoStart);
    }

    // 5. Intercepting HOME button
    public static int getIntercept() {
        return Prefers.getInt("intercept", 1);
    }

    public static void putIntercept(int wall) {
        Prefers.put("intercept", wall);
    }

    // 6. TV app row height (index into TV_ROW_HEIGHTS)
    public static int getTvRowIndex() {
        return Prefers.getInt("tvRowIdx", TV_ROW_DEFAULT);
    }

    public static void putTvRowIndex(int index) {
        Prefers.put("tvRowIdx", index);
    }

    public static int getTvRowDp() {
        int idx = getTvRowIndex();
        return TV_ROW_HEIGHTS[Math.max(0, Math.min(idx, TV_ROW_HEIGHTS.length - 1))];
    }

    // 7. Mobile app row height (index into MOB_ROW_HEIGHTS)
    public static int getMobileRowIndex() {
        return Prefers.getInt("mobRowIdx", MOB_ROW_DEFAULT);
    }

    public static void putMobileRowIndex(int index) {
        Prefers.put("mobRowIdx", index);
    }

    public static int getMobileRowDp() {
        int idx = getMobileRowIndex();
        return MOB_ROW_HEIGHTS[Math.max(0, Math.min(idx, MOB_ROW_HEIGHTS.length - 1))];
    }
}
