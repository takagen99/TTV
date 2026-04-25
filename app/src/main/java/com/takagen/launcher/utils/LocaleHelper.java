package com.takagen.launcher.utils;

import android.content.Context;
import android.content.res.Configuration;

import androidx.preference.PreferenceManager;

import java.util.Locale;

public final class LocaleHelper {

    private static final String PREF_LANGUAGE = "language";
    private static final String DEFAULT_LANG   = "en";

    private LocaleHelper() {}

    /**
     * Wraps {@code base} with the saved app locale. Safe to call from both
     * {@code Application.attachBaseContext} (before {@link com.takagen.launcher.Init} is
     * set) and {@code Activity.attachBaseContext}, because it reads the preference
     * directly via {@link PreferenceManager} rather than through
     * {@link Prefers} / {@link com.takagen.launcher.Init}.
     */
    public static Context wrap(Context base) {
        String lang = PreferenceManager.getDefaultSharedPreferences(base)
                .getString(PREF_LANGUAGE, DEFAULT_LANG);
        if (DEFAULT_LANG.equals(lang)) return base;
        return applyLocale(base, lang);
    }

    public static Context applyLocale(Context base, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
