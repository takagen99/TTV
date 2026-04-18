package com.takagen.launcher.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.takagen.launcher.bean.FavApp;

import java.util.List;

@Dao
public interface FavAppDao {

    @Insert
    void addApp(FavApp favApp);

    @Update
    void updateApp(FavApp favApp);

    @Delete
    void deleteApp(FavApp favApp);

    @Query("SELECT * FROM FavApp ORDER BY isTv DESC, sortIndex ASC")
    List<FavApp> getAllFavApp();

    @Query("SELECT * FROM FavApp WHERE isTv = :isTv ORDER BY sortIndex ASC")
    List<FavApp> getFavApps(boolean isTv);

    @Query("DELETE FROM FavApp")
    void deleteAllFavApp();

    @Query("DELETE FROM FavApp WHERE isTv = :isTv")
    void deleteBucket(boolean isTv);

    @Query("DELETE FROM FavApp WHERE pack = :packageName")
    void deletePack(String packageName);
}
