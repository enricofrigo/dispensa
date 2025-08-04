package eu.frigo.dispensa.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.R;

public class ThemeHelper {

    public static String LIGHT_MODE;
    public static String DARK_MODE;
    public static String SYSTEM_DEFAULT_MODE;
    public static String PREF_KEY_THEME;

    public static void applyTheme(Context context) {
        LIGHT_MODE = String.valueOf(R.string.pref_theme_light);
        DARK_MODE = String.valueOf(R.string.pref_theme_dark);
        SYSTEM_DEFAULT_MODE = String.valueOf(R.string.pref_theme_system);
        PREF_KEY_THEME = String.valueOf(R.string.pref_key_theme);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String themePreference = sharedPreferences.getString(PREF_KEY_THEME, SYSTEM_DEFAULT_MODE);
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