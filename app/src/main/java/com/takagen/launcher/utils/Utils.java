package com.takagen.launcher.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
import com.takagen.launcher.db.dao.FavAppDao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    public static void hideSystemUI(Activity activity) {
        int flags = View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    public static Animation getAnim(@AnimRes int resId) {
        return AnimationUtils.loadAnimation(App.get(), resId);
    }

    public static Drawable getDrawable(@DrawableRes int resId) {
        return ContextCompat.getDrawable(App.get(), resId);
    }

    public static void shakeView(View v) {
        Animation shake = getAnim(R.anim.shake);
        v.clearAnimation();
        v.startAnimation(shake);
    }

    /**
     * Returns both rows: pinned apps (in saved order) followed by remaining installed apps
     * (alphabetical). TV apps have a banner resource; mobile apps do not.
     */
    @SuppressLint("QueryPermissionsNeeded")
    public static AppBuckets getApps() {
        PackageManager pm = App.get().getPackageManager();
        String selfPkg = App.get().getPackageName();
        FavAppDao dao = FavAppDB.get().favAppDao();

        Map<String, AppInfo> tvAll = new LinkedHashMap<>();
        Map<String, AppInfo> mobileAll = new LinkedHashMap<>();

        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            if (info.packageName.equals(selfPkg)) continue;
            if (pm.getLaunchIntentForPackage(info.packageName) == null) continue;

            AppInfo item = AppInfo.get(info);
            if (info.banner != 0) tvAll.put(info.packageName, item);
            else mobileAll.put(info.packageName, item);
        }

        return new AppBuckets(orderBucket(dao, tvAll, true), orderBucket(dao, mobileAll, false));
    }

    private static List<AppInfo> orderBucket(FavAppDao dao, Map<String, AppInfo> installed, boolean isTv) {
        List<AppInfo> ordered = new ArrayList<>();
        for (FavApp fav : dao.getFavApps(isTv)) {
            AppInfo a = installed.remove(fav.pack);
            if (a != null) ordered.add(a);
        }
        List<AppInfo> remaining = new ArrayList<>(installed.values());
        AppInfo.Sorter.sort(remaining);
        ordered.addAll(remaining);
        return ordered;
    }

    /** Replace the saved order for one bucket with the given ordered list. */
    public static void saveBucketOrder(List<AppInfo> apps, boolean isTv) {
        FavAppDao dao = FavAppDB.get().favAppDao();
        dao.deleteBucket(isTv);
        for (int i = 0; i < apps.size(); i++) {
            AppInfo a = apps.get(i);
            FavApp f = new FavApp();
            f.name = a.getName();
            f.pack = a.getPack();
            f.isTv = isTv;
            f.sortIndex = i;
            dao.addApp(f);
        }
    }

    public static final class AppBuckets {
        public final List<AppInfo> tv;
        public final List<AppInfo> mobile;

        AppBuckets(List<AppInfo> tv, List<AppInfo> mobile) {
            this.tv = tv;
            this.mobile = mobile;
        }
    }
}
