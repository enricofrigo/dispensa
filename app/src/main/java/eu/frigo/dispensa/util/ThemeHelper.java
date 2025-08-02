package eu.frigo.dispensa.util; // o il tuo package di utility

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public class ThemeHelper {

    public static final String LIGHT_MODE = "light";
    public static final String DARK_MODE = "dark";
    public static final String SYSTEM_DEFAULT_MODE = "system"; // Deve corrispondere a theme_values

    public static final String PREF_KEY_THEME = "theme_preference";

    // Applica il tema basandosi sulla preferenza salvata
    public static void applyTheme(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String themePreference = sharedPreferences.getString(PREF_KEY_THEME, SYSTEM_DEFAULT_MODE);
        applyThemePreference(themePreference);
    }

    // Imposta la modalità notturna di AppCompat
    public static void applyThemePreference(String themePreference) {
        switch (themePreference) {
            case LIGHT_MODE:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DARK_MODE:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default: // SYSTEM_DEFAULT_MODE o qualsiasi valore non riconosciuto
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}