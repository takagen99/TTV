package com.takagen.launcher.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.takagen.launcher.App;
import com.takagen.launcher.bean.FavApp;
import com.takagen.launcher.db.dao.FavAppDao;

@Database(entities = {FavApp.class}, version = FavAppDB.VERSION)
public abstract class FavAppDB extends RoomDatabase {

    public static final int VERSION = 33;
    public static final String NAME = "fav";

    private static volatile FavAppDB instance;

    public static synchronized FavAppDB get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    private static FavAppDB create(Context context) {
        return Room.databaseBuilder(context, FavAppDB.class, NAME)
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build();
    }

    public abstract FavAppDao favAppDao();

}
