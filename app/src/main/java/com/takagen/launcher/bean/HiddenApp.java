package com.takagen.launcher.bean;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "hidden_app")
public class HiddenApp {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "pack")
    public String pack = "";
}
