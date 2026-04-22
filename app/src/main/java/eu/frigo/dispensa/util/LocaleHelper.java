package eu.frigo.dispensa.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Locale;

import eu.frigo.dispensa.ui.SettingsFragment;

public class LocaleHelper {

    public static final String DEFAULT_LANGUAGE_CODE = "english";

    public static Context onAttach(Context context) {
        String lang = getPersistedData(context, Locale.getDefault().getLanguage());
        return setLocale(context, lang);
    }

    public static void applyLocaleOnCreate(Context context) {
        String lang = getPersistedData(context, Locale.getDefault().getLanguage());
        setLocale(context, lang);
    }


    public static Context setLocale(Context context, String languageCode) {
        persist(context, languageCode);

        if (languageCode.equals(DEFAULT_LANGUAGE_CODE)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return updateResources(context, Locale.getDefault());
            }
            return updateResourcesLegacy(context, Locale.getDefault());
        }

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return updateResources(context, locale);
        }
        return updateResourcesLegacy(context, locale);
    }

    private static String getPersistedData(Context context, String defaultLanguage) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SettingsFragment.KEY_LANGUAGE_PREFERENCE, defaultLanguage);
    }

    private static void persist(Context context, String language) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SettingsFragment.KEY_LANGUAGE_PREFERENCE, language);
        Log.d("locale","Set locale to "+language);
        editor.commit();
    }

    private static Context updateResources(Context context, Locale locale) {
        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale); // Importante per RTL
        return context.createConfigurationContext(configuration);
    }

    // Per versioni precedenti ad Android N
    @SuppressWarnings("deprecation")
    private static Context updateResourcesLegacy(Context context, Locale locale) {
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLayoutDirection(locale);
        }
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        return context;
    }

    public static void recreateActivity(android.app.Activity activity) {
        activity.recreate();
    }
}
