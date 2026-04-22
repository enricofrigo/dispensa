package eu.frigo.dispensa.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.util.LocaleHelper;
import eu.frigo.dispensa.work.ExpiryCheckWorkerScheduler;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static String KEY_EXPIRY_DAYS_BEFORE = "pref_expiry_days_before";
    public static String KEY_LANGUAGE_PREFERENCE = "language_preference";
    public static final String KEY_NOTIFICATION_TIME_HOUR = "pref_notification_time_hour";
    public static final String KEY_NOTIFICATION_TIME_MINUTE = "pref_notification_time_minute";
    public static final String KEY_PREF_ENABLE_OFF_API = "pref_key_enable_off_api";
    public static final String KEY_PREF_DEFAULT_SHELF_LIFE = "pref_key_default_shelf_life";
    public static final String KEY_OFF_CACHE_LIMIT = "pref_off_cache_limit";
    public static final String KEY_OFF_CACHE_TTL_DAYS = "pref_off_cache_ttl_days";
    public static final String KEY_OFF_CACHE_CLEAR = "pref_off_cache_clear";
    public static final String KEY_DEFUALT_ICON = "pref_predefined_tab_icon";

    private Preference notificationTimePreference;
    private ListPreference languagePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        notificationTimePreference = findPreference(getString(R.string.pref_key_exp_time));
        if (notificationTimePreference != null) {
            updateNotificationTimeSummary();
            notificationTimePreference.setOnPreferenceClickListener(preference -> {
                showTimePickerDialog();
                return true;
            });
        }

        EditTextPreference daysBeforePref = findPreference(KEY_EXPIRY_DAYS_BEFORE);
        if (daysBeforePref != null) {
            daysBeforePref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int days = Integer.parseInt((String) newValue);
                    if (days >= 0 && days <= 365) { // Esempio di range valido
                        return true;
                    }
                } catch (NumberFormatException e) {
                    Log.e("SettingsFragment", "Errore durante la conversione a numero: " + e.getMessage());
                }
                android.widget.Toast.makeText(getContext(), getString(R.string.pref_exp_days_error), android.widget.Toast.LENGTH_SHORT).show();
                return false;
            });
        }
        
        EditTextPreference defaultShelfLifePref = findPreference("pref_key_default_shelf_life");
        if (defaultShelfLifePref != null) {
            defaultShelfLifePref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue == null || ((String) newValue).trim().isEmpty()) {
                    return true;
                }
                try {
                    int days = Integer.parseInt((String) newValue);
                    if (days >= 0 && days <= 3650) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    Log.e("SettingsFragment", "Errore conversione default shelf life: " + e.getMessage());
                }
                android.widget.Toast.makeText(getContext(), getString(R.string.pref_exp_days_error), android.widget.Toast.LENGTH_SHORT).show();
                return false;
            });
        }
        languagePreference = findPreference(KEY_LANGUAGE_PREFERENCE);
        if (languagePreference != null) {
            String currentLangValue = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(KEY_LANGUAGE_PREFERENCE, "en");
            Log.d("locale SettingsFragment", "onCreatePreferences - Valore lingua letto (con chiave hardcoded): " + currentLangValue);
            languagePreference.setValue(currentLangValue);
            updateLanguagePreferenceSummary(currentLangValue);
        }

        Preference cleanImagesPref = findPreference("pref_clean_images");
        if (cleanImagesPref != null) {
            cleanImagesPref.setOnPreferenceClickListener(preference -> {
                cleanOrphanImages();
                return true;
            });
        }

        Preference clearCachePref = findPreference(KEY_OFF_CACHE_CLEAR);
        if (clearCachePref != null) {
            clearCachePref.setOnPreferenceClickListener(preference -> {
                clearOpenFoodFactCache();
                return true;
            });
        }
    }

    private void clearOpenFoodFactCache() {
        Context context = requireContext();
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            eu.frigo.dispensa.data.openfoodfacts.OpenFoodFactCacheManager.clearAllCache(
                context, eu.frigo.dispensa.data.AppDatabase.getDatabase(context));
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(context, getString(R.string.notify_cache_cleared), android.widget.Toast.LENGTH_SHORT).show()
            );
        });
    }

    private void cleanOrphanImages() {
        Context context = requireContext();
        eu.frigo.dispensa.data.Repository.cleanOrphanImages(context, count ->
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        android.widget.Toast.makeText(context,
                getString(R.string.notify_clean_images_done, count),
                android.widget.Toast.LENGTH_SHORT).show()));
    }
    private void showTimePickerDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        int currentHour = prefs.getInt(KEY_NOTIFICATION_TIME_HOUR, 9); // Default 9 AM
        int currentMinute = prefs.getInt(KEY_NOTIFICATION_TIME_MINUTE, 0); // Default 00 minutes

        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(getContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(currentHour)
                .setMinute(currentMinute)
                .setTitleText(getString(R.string.pref_key_exp_time_title))
                .build();

        timePicker.addOnPositiveButtonClickListener(dialog -> {
            int selectedHour = timePicker.getHour();
            int selectedMinute = timePicker.getMinute();

            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_NOTIFICATION_TIME_HOUR, selectedHour);
            editor.putInt(KEY_NOTIFICATION_TIME_MINUTE, selectedMinute);
            editor.apply();
            updateNotificationTimeSummary();
            ExpiryCheckWorkerScheduler.scheduleWorker(requireContext());
        });
        timePicker.show(getParentFragmentManager(), "TIME_PICKER_TAG");
    }
    private void updateNotificationTimeSummary() {
        if (notificationTimePreference != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            int hour = prefs.getInt(KEY_NOTIFICATION_TIME_HOUR, 9);
            int minute = prefs.getInt(KEY_NOTIFICATION_TIME_MINUTE, 0);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            notificationTimePreference.setSummary(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).registerOnSharedPreferenceChangeListener(this);
        updateNotificationTimeSummary();
        if (languagePreference != null) {
            String currentLangValue = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(KEY_LANGUAGE_PREFERENCE, "en");
            Log.d("locale SettingsFragment", "onResume - Valore lingua letto (con chiave hardcoded): " + currentLangValue);
            updateLanguagePreferenceSummary(currentLangValue);
        }
    }
    @Override
    public void onPause() {
        super.onPause();
        Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).unregisterOnSharedPreferenceChangeListener(this);
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Context context = getContext();
        if (KEY_LANGUAGE_PREFERENCE.equals(key)) {
            if (context == null) return;
            String langCode = sharedPreferences.getString(key, "en");
            Log.d("locale SettingsFragment", "onSharedPreferenceChanged lingua selezionata: " + langCode + " applicata.");
            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(langCode);
            AppCompatDelegate.setApplicationLocales(appLocale);
            updateLanguagePreferenceSummary(langCode);
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(requireContext()).edit();
            editor.putString(KEY_LANGUAGE_PREFERENCE, langCode);
            editor.commit();
            LocaleHelper.setLocale(context,langCode);
            triggerRebirthWithAlarmManager(context);
        }
    }
    private void updateLanguagePreferenceSummary(String languageValue) {
        if (languagePreference == null) {
            Log.w("locale SettingsFragment", "updateLanguagePreferenceSummary chiamato ma languagePreference è null");
            return;
        }
        if (languageValue == null) {
            Log.w("locale SettingsFragment", "updateLanguagePreferenceSummary chiamato con languageValue null");
            return;
        }

        Log.d("locale SettingsFragment", "Aggiornamento summary per languageValue: " + languageValue);

        CharSequence[] entries = languagePreference.getEntries();
        CharSequence[] entryValues = languagePreference.getEntryValues();

        if (entries == null || entryValues == null || entries.length != entryValues.length) {
            Log.e("locale SettingsFragment", "Entries o EntryValues non validi per languagePreference.");
            languagePreference.setSummary(languageValue); // Fallback al codice lingua
            return;
        }

        boolean found = false;
        for (int i = 0; i < entryValues.length; i++) {
            if (entryValues[i].toString().equals(languageValue)) {
                languagePreference.setSummary(entries[i]);
                Log.d("locale SettingsFragment", "Summary impostato a: " + entries[i]);
                found = true;
                break;
            }
        }

        if (!found) {
            Log.w("locale SettingsFragment", "Nessuna entry corrispondente trovata per languageValue: " + languageValue + ". Uso il valore come summary.");
            languagePreference.setSummary(languageValue);
        }
    }
    public static void triggerRebirthWithAlarmManager(Context context) {
        if (context == null) {return;}
        System.exit(0);
    }
    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() != null && preference.getKey().equals(getString(R.string.pref_key_location))) {
            getParentFragmentManager().beginTransaction()
                .replace(android.R.id.content, new ManageLocationsFragment())
                .addToBackStack(null)
                .commit();
            return false;
        }
        return false;
    }
}
