package com.takagen.launcher.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takagen.launcher.App;
import com.takagen.launcher.R;
import com.takagen.launcher.adapter.AdapterBanner;
import com.takagen.launcher.adapter.AdapterIcon;
import com.takagen.launcher.bean.AppInfo;
import com.takagen.launcher.bean.FavApp;
import com.takagen.launcher.databinding.ActivityMainBinding;
import com.takagen.launcher.db.FavAppDB;
import com.takagen.launcher.ui.dialog.InputDialog;
import com.takagen.launcher.utils.Clock;
import com.takagen.launcher.utils.Notify;
import com.takagen.launcher.utils.ResUtil;
import com.takagen.launcher.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AdapterBanner.OnClickListener {

    FavAppDB db = FavAppDB.get();
    ArrayList<String> backdropPath = new ArrayList<String>();

    ArrayList<List<String>> backdropPathL = new ArrayList<>();
    private ActivityMainBinding binding;
    private AdapterIcon mAdapter;
    private AdapterBanner mAdapterB;
    private Clock mClock;
    private Clock mClockDate;
    private Animation aEnter;
    private Animation aExit;

    private Uri mUri;
    String id_0 = "com.google.android.videos/.tv.usecase.tvinput.playback.TvInputService"; // Tuner
    String id_1 = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW4";     // HDMI 1
    String id_2 = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5";     // HDMI 2
    String id_3 = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW6";     // HDMI 3
    String id_av = "com.mediatek.tvinput/.composite.CompositeInputService/HW1";     // AV Composite


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Utils.hideSystemUI(this);
        initView();
        initEvent();
    }

    private void initView() {

        this.aEnter = ResUtil.getAnim(R.anim.enter_anim);
        this.aExit = ResUtil.getAnim(R.anim.exit_anim);

        setRecyclerView();
        mAdapter.getApp(1);    // Get only with Icons, no banners
        mAdapterB.getApp(2);

        mClock = Clock.create(binding.tvTime).format("HH:mm");
        mClockDate = Clock.create(binding.tvDate).format("dd MMMM\nEEEE");

        binding.tvRefresh.setOnClickListener(this::onMenuClick);
        binding.tvInput.setOnClickListener(this::onMenuClick);
        binding.tvClear.setOnClickListener(this::onMenuClick);
        binding.tvWifi.setOnClickListener(this::onMenuClick);
        binding.tvMenu.setOnClickListener(this::onMenuClick);
        binding.tvMenu.setOnLongClickListener(this::onLongMenu);

        setIcon();
        setWall();

        // For Testing only - Show Pinned
        binding.testGet.setOnClickListener(this::getFavApp);
//        binding.testGet.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                getFavApp();
//            }
//        });
        // For Testing only - Trigger HDMI
        binding.testHDMI.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callHDMI();
            }
        });
    }

    private void callHDMI() {
        mUri = TvContract.buildChannelUriForPassthroughInput(id_2);
        startActivity(new Intent(Intent.ACTION_VIEW, mUri), null);
    }

    private void getFavApp(View v) {
        FavAppDB db = FavAppDB.get();
        List<FavApp> FavList = db.favAppDao().getAllFavApp();
        Collections.reverse(FavList);

//        StringBuilder sb = new StringBuilder();
//        for (FavApp f : FavList) {
//            sb.append(f.id + " " + f.name + " " + f.pack);
//            sb.append("\n");
//        }
//        String finalData = sb.toString();
//        Notify.show(finalData);

        TvInputManager mTvInputManager = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mTvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        }
        List<TvInputInfo> inputs = mTvInputManager.getTvInputList();
        StringBuilder sb = new StringBuilder();
        for (TvInputInfo f : inputs) {
            sb.append(f.getId() + " " + f.getType() + " " + f.isPassthroughInput());
            sb.append("\n");
        }
        String finalData = sb.toString();
        Notify.show(finalData);

    }

    private void initEvent() {
        mAdapter.setOnItemClickListener(this::openApp);

        // Fix to focus on HorizontalGridView by focusing on Menu button first
        binding.tvMenu.requestFocus();
        App.post(() -> binding.recyclerBanner.requestFocus(), 500);
    }

    private void setRecyclerView() {
//		binding.recyclerIcon.setHasFixedSize(true);
//		binding.recyclerIcon.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerIcon.setAdapter(mAdapter = new AdapterIcon());
//        binding.recyclerIcon.setWindowAlignmentOffsetPercent(10);       // to set focus at 1st item

//		binding.recyclerBanner.setHasFixedSize(true);
//		binding.recyclerBanner.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerBanner.setAdapter(mAdapterB = new AdapterBanner(this));
//        binding.recyclerBanner.setWindowAlignmentOffsetPercent(10);
    }

    private void openApp(AppInfo item) {
        try {
            startActivity(getPackageManager().getLaunchIntentForPackage(item.getPack()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Utils.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Utils.hideSystemUI(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        mClockDate.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
        mClockDate.stop();
    }

    private void onMenuClick(View v) {
        if (v == binding.tvRefresh) {
            setWallFinish();
        } else if (v == binding.tvInput) {
            selectInputDialog();
        } else if (v == binding.tvClear) {
            MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this, R.style.DarkDialogAlert)
                    .setTitle("Reset Pinned Apps")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Yes", (dialog, which) -> clearPinApp());
            AlertDialog alertDialog = dialogBuilder.create();
            alertDialog.setOnShowListener(dialog -> {
                Button negativeButton = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                if (negativeButton != null) {
                    negativeButton.requestFocus();
                }
            });
            alertDialog.show();
        } else if (v == binding.tvMenu) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } else if (v == binding.tvWifi) {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        }
    }

    private boolean onLongMenu(View view) {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
        return true;
    }

    private void clearPinApp() {
        db.favAppDao().deleteAllFavApp();
        mAdapterB.getApp(2);
    }

    boolean isNetworkAvailable() {
        ConnectivityManager cm
                = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    private void setIcon() {
        if (isNetworkAvailable()) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI) {
                binding.tvWifi.setImageDrawable(Utils.getDrawable(R.drawable.ic_home_network_wifi));
            } else if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_MOBILE) {
                binding.tvWifi.setImageDrawable(Utils.getDrawable(R.drawable.ic_home_network_mobile));
            } else if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_ETHERNET) {
                binding.tvWifi.setImageDrawable(Utils.getDrawable(R.drawable.ic_home_network_lan));
            }
        }
    }

    @Override
    public void onItemClick(AppInfo item) {
        try {
            startActivity(getPackageManager().getLaunchIntentForPackage(item.getPack()));
            overridePendingTransition(R.anim.zoom_enter, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onLongClick(AppInfo item) {
        try {
            ApplicationInfo app = getPackageManager().getApplicationInfo(item.getPack(), 0);
            // delete existing index first
            FavAppDB db = FavAppDB.get();
            db.favAppDao().deletePack(app.packageName);

            // put into database
            FavApp favApp = new FavApp();
            favApp.name = app.name;
            favApp.pack = app.packageName;
            Notify.show("Pinned - " + app.packageName);
            db.favAppDao().addApp(favApp);

            // get App again & refresh
            mAdapterB.getApp(2);
            binding.recyclerBanner.scrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    public void setWall() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                openURL(1);
                openURL(2);
                openURL(3);

                runOnUiThread(new Runnable() {
                    public void run() {
                        // do onPostExecute stuff
                        setWallFinish();
                    }
                });
            }
        }).start();

    }

    public void openURL(Integer page) {
        String apiKey = "5622cafbfe8f8cfe358a29c53e19bba0";  //  f3d2f6963f370a24758969434c14cef0

        String specific = "https://api.themoviedb.org/3/tv/" + "95479" + "/images" + "?api_key=" + apiKey + "&include_image_language=en%2Cnull";
        String trending = "https://api.themoviedb.org/3/trending/all/week?api_key=" + apiKey + "&language=en-US&page=" + page;

        try {
            URL url = new URL(trending);
            URLConnection request = url.openConnection();
            request.connect();

            JsonParser jp = new JsonParser(); //from gson
            JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent())); //Convert the input stream to a json element
            JsonObject rootobj = root.getAsJsonObject();

            // For TV Show specific with ID -----------------------
//                    JsonArray items = rootobj.get("backdrops").getAsJsonArray();
//                    for (int i = 0; i < items.size(); i++) {
//                        JsonObject item = items.get(i).getAsJsonObject();
//                        if (i == 3) {
//                            filePath[0] = item.get("file_path").getAsString();
//                        }
//                    }

            // For Trending Movie & TV Shows of the week  --------------
            JsonArray items = rootobj.get("results").getAsJsonArray();
            for (int i = 0; i < items.size(); i++) {
                String t_path, t_name = "";

                JsonObject item = items.get(i).getAsJsonObject();
                List<String> row = new ArrayList<>();

                t_path = item.get("backdrop_path").getAsString();
                row.add(t_path);                                       // Column 1 : Backdrop path
                if (item.has("name")) {
                    t_name = item.get("name").getAsString();
                    row.add(t_name);                                   // Column 2 : Name, or
                } else if (item.has("title")) {
                    t_name = item.get("title").getAsString();
                    row.add(t_name);                                   // Column 2 : Title
                }

                backdropPathL.add(row);
                System.out.println("backdrop_pathL: " + t_path + " - " + t_name + "\n");
            }
        } catch (IOException e) {
            System.out.println("TMDB open Error");
        }
    }

    public void setWallFinish() {

        if (backdropPathL.isEmpty()) return;

        // Get random number between 0 - 39
        int n = 60;
        int rand = (int) (Math.random() * n);

        String baseUrl = "https://image.tmdb.org/t/p/original";
//        String fullPath = baseUrl + backdropPath.get(rand);

        String fullPath = baseUrl + backdropPathL.get(rand).get(0);
//        System.out.println("takagen " + backdropPathL.get(rand).get(0));
//        System.out.println("takagen " + backdropPathL.get(rand).get(1));

        // Update Background Resource
        try {
            Glide.with(this)
                    .load(fullPath)
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            binding.frameBG.setBackgroundColor(getResources().getColor(R.color.grey_900));
                            binding.mainBG.setBackground(resource);
                            binding.mainBG.startAnimation(aEnter);
                            binding.tvPosterTitle.setText(backdropPathL.get(rand).get(1));
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } catch (Exception e) {
            Notify.show("Set Wallpaper Failed");
        }

    }

    public void selectInputDialog() {
        InputDialog.create(this).show();
    }

//    @Override
//    public boolean dispatchKeyEvent(KeyEvent event) {
//        int action = event.getAction();
//        int keyCode = event.getKeyCode();
//
//        // takagen99 : to set focus back to tvVod when press up
//        if (binding.recyclerBanner.isFocused()) {
//            Notify.show("Focus test tset " + getCurrentFocus());
//        } else if (binding.tvWifi.isFocused()) {
//            Notify.show("Wifi Focus test tset " + getCurrentFocus());
//        } else {
//            Notify.show("not Focus test tset " + getCurrentFocus());
//        }
//
//        return super.dispatchKeyEvent(event);
//    }

}