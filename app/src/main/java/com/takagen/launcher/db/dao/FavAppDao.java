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
    public void addApp(FavApp favApp);

    @Update
    public void updateApp(FavApp favApp);

    @Delete
    public void deleteApp(FavApp favApp);

    @Query("select * from FavApp" )
    public List<FavApp> getAllFavApp();

    @Query("DELETE FROM FavApp where name <> 0" )
    public abstract void deleteAllFavApp();

    @Query("DELETE FROM FavApp where pack = :packageName" )
    public void deletePack(String packageName);

}
