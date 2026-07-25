package eu.frigo.dispensa.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.R;

public class ThemeHelper {

    public static final String LIGHT_MODE = "light";
    public static final String DARK_MODE = "dark";
    public static final String SYSTEM_DEFAULT_MODE = "system";

    public static void applyTheme(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String themePreferenceKey = context.getString(R.string.pref_key_theme);
        String themePreference = sharedPreferences.getString(themePreferenceKey, SYSTEM_DEFAULT_MODE);
        applyThemePreference(themePreference);
    }

    public static void applyThemePreference(String themePreference) {
        if (LIGHT_MODE.equals(themePreference)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }else if (DARK_MODE.equals(themePreference)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }else{
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}