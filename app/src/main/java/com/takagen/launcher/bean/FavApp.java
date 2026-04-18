package com.takagen.launcher.bean;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class FavApp {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "pack")
    public String pack;

    // True = TV row (banner), false = Mobile row (icon). Separate custom sort per bucket.
    @ColumnInfo(name = "isTv", defaultValue = "0")
    public boolean isTv;

    // Position within its bucket; lower values appear first.
    @ColumnInfo(name = "sortIndex", defaultValue = "0")
    public int sortIndex;
}
