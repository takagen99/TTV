package com.takagen.launcher.utils;

import com.takagen.launcher.App;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//import okhttp3.Cache;
//import okhttp3.Interceptor;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.Response;

public class TMDB {


//    public static void getBackdrops2(String id) {
//        ArrayList<String> covers = new ArrayList<String>();
//        String baseUrl = "https://image.tmdb.org/t/p/original/";
//        String apiKey = "f3d2f6963f370a24758969434c14cef0";
//
//        try {
//            JSONObject jObject = getJSONObject("https://api.themoviedb.org/3/tv/" + id + "/images" + "?api_key=" + apiKey + "&include_image_language=en%2Cnull");
//            JSONArray jArray = jObject.getJSONArray("backdrops");
//            for (int i = 0; i < jArray.length(); i++) {
//                covers.add(baseUrl + getStringFromJSONObject(jArray.getJSONObject(i), "file_path", ""));
//                Notify.show(covers.get(0) + " 111");
//            }
//        } catch (JSONException e) {
//            Notify.show("error backdrop");
//        }
//
//    }
//
//    public static List<String> getBackdrops(String id) {
//        ArrayList<String> covers = new ArrayList<String>();
//        String baseUrl = "https://image.tmdb.org/t/p/original/";
//        String apiKey = "f3d2f6963f370a24758969434c14cef0";
//
//        try {
//            JSONObject jObject = getJSONObject("https://api.themoviedb.org/3/tv/" + id + "/images" + "?api_key=" + apiKey + "&include_image_language=en%2Cnull");
//            JSONArray jArray = jObject.getJSONArray("backdrops");
//            for (int i = 0; i < jArray.length(); i++) {
//                covers.add(baseUrl + getStringFromJSONObject(jArray.getJSONObject(i), "file_path", ""));
//            }
//        } catch (JSONException e) {}
//
//        return covers;
//    }
//
//    public static JSONObject getJSONObject(String url) {
//
//        File httpCacheDirectory = new File(App.get().getCacheDir(), "responses");
//        int cacheSize = 10*1024*1024;
//        Cache cache = new Cache(httpCacheDirectory, cacheSize);
//
//        OkHttpClient client = new OkHttpClient.Builder()
//                .addNetworkInterceptor(REWRITE_CACHE_CONTROL_INTERCEPTOR)
//                .cache(cache)
//                .build();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .build();
//
//        try {
//            Response response = client.newCall(request).execute();
//
//            if (response.code() >= 429) {
//                // HTTP error 429 and above means that we've exceeded the query limit
//                // for TMDb. Sleep for 5 seconds and try again.
//                Thread.sleep(5000);
//                response = client.newCall(request).execute();
//            }
//            return new JSONObject(response.body().string());
//        } catch (Exception e) { // IOException and JSONException
//            return new JSONObject();
//        }
//    }
//
//    public static String getStringFromJSONObject(JSONObject json, String name, String fallback) {
//        try {
//            String s = json.getString(name);
//            if (s.equals("null"))
//                return fallback;
//            return s;
//        } catch (Exception e) {
//            return fallback;
//        }
//    }
//
//    private static final Interceptor REWRITE_CACHE_CONTROL_INTERCEPTOR = new Interceptor() {
//        @Override public Response intercept(Interceptor.Chain chain) throws IOException {
//            Response originalResponse = chain.proceed(chain.request());
//            return originalResponse.newBuilder()
//                    .header("Cache-Control", "max-age=60")
//                    .build();
//        }
//    };

}
