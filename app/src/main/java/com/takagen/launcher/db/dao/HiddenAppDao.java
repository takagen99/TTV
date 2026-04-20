package com.takagen.launcher.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.takagen.launcher.bean.HiddenApp;

import java.util.List;

@Dao
public interface HiddenAppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void hide(HiddenApp app);

    @Query("DELETE FROM hidden_app WHERE pack = :pack")
    void show(String pack);

    @Query("SELECT pack FROM hidden_app")
    List<String> getHiddenPacks();

    @Query("DELETE FROM hidden_app")
    void showAll();
}
