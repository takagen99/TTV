package com.takagen.launcher;

import com.takagen.launcher.utils.Prefers;

public class Setting {

    // Preset dp values for row height cycling
    public static final int[] TV_ROW_HEIGHTS  = {110, 120, 130, 145, 160, 180};
    public static final int[] MOB_ROW_HEIGHTS = {100, 110, 120, 130, 145, 160};
    private static final int TV_ROW_DEFAULT   = 2; // 130dp
    private static final int MOB_ROW_DEFAULT  = 2; // 120dp

    // 1. TMDB API Key
    public static String getTMDBKey() {
        return Prefers.getString("TMDBkey");
    }

    public static void putTMDBKey(String TMDBkey) {
        Prefers.put("TMDBkey", TMDBkey);
    }

    // 2. Wallpaper categories — bitmask (see WALL_* bit indices below)
    //    Defaults to just "Anime" to match the legacy single-category behaviour.
    public static final int WALL_TRENDING  = 1 << 0; // select_wall[0]
    public static final int WALL_ANIME     = 1 << 1; // select_wall[1]
    public static final int WALL_MOVIES    = 1 << 2; // select_wall[2]
    public static final int WALL_TV_SERIES = 1 << 3; // select_wall[3]
    public static final int WALL_ALL_MASK  =
            WALL_TRENDING | WALL_ANIME | WALL_MOVIES | WALL_TV_SERIES;
    private static final int WALL_DEFAULT_MASK = WALL_ANIME;

    /**
     * Returns the enabled wallpaper categories as a bitmask. Migrates the legacy
     * single-int "wall" pref (0..3 index) on first read. Always returns a non-zero,
     * sanitised mask — if the user somehow clears everything we fall back to the
     * default so the launcher still has artwork.
     */
    public static int getWallMask() {
        int stored = Prefers.getInt("wallMask", -1);
        if (stored == -1) {
            int legacy = Prefers.getInt("wall", 1);
            int mask = (legacy >= 0 && legacy < 4) ? (1 << legacy) : WALL_DEFAULT_MASK;
            Prefers.put("wallMask", mask);
            return mask;
        }
        int sanitized = stored & WALL_ALL_MASK;
        return sanitized == 0 ? WALL_DEFAULT_MASK : sanitized;
    }

    public static void putWallMask(int mask) {
        Prefers.put("wallMask", mask & WALL_ALL_MASK);
    }

    // 3. Auto-Cycle Wallpaper
    public static int getAutoWall() {
        return Prefers.getInt("autoWall", 1);
    }

    public static void putAutoWall(int autoWall) {
        Prefers.put("autoWall", autoWall);
    }

    // 4. TV app row height (index into TV_ROW_HEIGHTS)
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

    // 5. Mobile app row height (index into MOB_ROW_HEIGHTS)
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

    // 6. Auto-start on boot
    //    0 = Off (default), 1 = On
    public static boolean getAutoStart() {
        return Prefers.getInt("autoStart", 0) == 1;
    }

    public static void putAutoStart(boolean enabled) {
        Prefers.put("autoStart", enabled ? 1 : 0);
    }
}
