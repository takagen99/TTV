package com.takagen.launcher.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.AnimRes;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.takagen.launcher.App;
import com.takagen.launcher.R;
import com.takagen.launcher.bean.AppInfo;
import com.takagen.launcher.bean.FavApp;
import com.takagen.launcher.db.FavAppDB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Utils {

    public static void hideSystemUI(Activity activity) {
        int flags = View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    public static Animation getAnim(@AnimRes int resId) {
        return AnimationUtils.loadAnimation(App.get(), resId);
    }

    public static Drawable getDrawable(@DrawableRes int resId) {
        return ContextCompat.getDrawable(App.get(), resId);
    }

    public static void shakeView(View v) {
        Animation shake = ResUtil.getAnim(R.anim.shake);
        v.clearAnimation();
        v.startAnimation(shake);
    }

    @SuppressLint("QueryPermissionsNeeded")
    public static List<AppInfo> getApps(int type) {
        // Favorite Pinned Apps
        FavAppDB db = FavAppDB.get();
        List<FavApp> FavList = db.favAppDao().getAllFavApp();

        List<AppInfo> items = new ArrayList<>();
        for (ApplicationInfo info : App.get().getPackageManager().getInstalledApplications(0)) {
            if (info.packageName.equals(App.get().getPackageName())) continue;

            String tempPack = null;
            for (FavApp f : FavList) {
                if (info.packageName.contains(f.pack)) {
                    tempPack = f.pack;
                }
            }
            if (info.packageName.equals(tempPack)) continue;
//			if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

            // ignored Launch Intent Apps
            if (App.get().getPackageManager().getLaunchIntentForPackage(info.packageName) == null)
                continue;

            // Type = 1, to get Icons only
            if (type == 1 && info.banner != 0) {
                continue;
                // Type = 2, to get Banners only
            } else if (type == 2 && info.banner == 0) {
                continue;
            }

            items.add(AppInfo.get(info));
        }
        return items;
    }

    @SuppressLint("QueryPermissionsNeeded")
    public static List<AppInfo> getAppsPriority() {
        FavAppDB db = FavAppDB.get();
        List<FavApp> FavList = db.favAppDao().getAllFavApp();
        Collections.reverse(FavList);

        List<AppInfo> items = new ArrayList<>();
        for (FavApp f : FavList) {
            try {
                ApplicationInfo info = App.get().getPackageManager().getApplicationInfo(f.pack, 0);
                items.add(AppInfo.get(info));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return items;
    }

}
