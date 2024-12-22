package com.takagen.launcher;


import com.takagen.launcher.utils.Prefers;


public class Setting {

    public static int getWall() {
        return Prefers.getInt("wall",1);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", wall);
    }

    public static int getAuto() {
        return Prefers.getInt("auto",1);
    }

    public static void putAuto(int wall) {
        Prefers.put("auto", wall);
    }

    public static String getTMDBKey() {
        return Prefers.getString("TMDBkey");
    }

    public static void putTMDBKey(String TMDBkey) {
        Prefers.put("TMDBkey", TMDBkey);
    }

}
