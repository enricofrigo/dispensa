package eu.frigo.dispensa.ui;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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

import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.util.LocaleHelper;
import eu.frigo.dispensa.util.ThemeHelper;
import eu.frigo.dispensa.work.ExpiryCheckWorkerScheduler;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static String KEY_EXPIRY_DAYS_BEFORE;
    public static String KEY_THEME_PREFERENCE;
    public static String KEY_LANGUAGE_PREFERENCE = "language_preference";
    public static final String KEY_NOTIFICATION_TIME_HOUR = "pref_notification_time_hour";
    public static final String KEY_NOTIFICATION_TIME_MINUTE = "pref_notification_time_minute";

    private Preference notificationTimePreference;
    private ListPreference languagePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        KEY_EXPIRY_DAYS_BEFORE = getString(R.string.pref_key_exp_days);
        KEY_THEME_PREFERENCE = getString(R.string.pref_key_theme);

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
        languagePreference = findPreference(KEY_LANGUAGE_PREFERENCE);
        if (languagePreference != null) {
            String currentLangValue = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(KEY_LANGUAGE_PREFERENCE, getString(R.string.language_system_default));
            Log.d("localeS", "onCreatePreferences - Valore lingua letto (con chiave hardcoded): " + currentLangValue);
            languagePreference.setValue(currentLangValue);
            updateLanguagePreferenceSummary(currentLangValue);
        }

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
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateNotificationTimeSummary();
        if (languagePreference != null) {
            String currentLangValue = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(KEY_LANGUAGE_PREFERENCE, getString(R.string.language_system_default));
            Log.d("localeS", "onResume - Valore lingua letto (con chiave hardcoded): " + currentLangValue);
            updateLanguagePreferenceSummary(currentLangValue);
        }
    }
    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Context context = getContext();
        if (KEY_LANGUAGE_PREFERENCE.equals(key)) {
            if (context == null) return;
            String langCode = sharedPreferences.getString(key, getString(R.string.language_system_default));
            Log.d("localeS", "Lingua selezionata: " + langCode + " applicata.");

            LocaleListCompat appLocale;
            if (LocaleHelper.DEFAULT_LANGUAGE_CODE.equals(langCode) || langCode.equalsIgnoreCase(getString(R.string.language_system_default))) {
                appLocale = LocaleListCompat.getEmptyLocaleList();
            } else {
                appLocale = LocaleListCompat.forLanguageTags(langCode);
            }
            AppCompatDelegate.setApplicationLocales(appLocale);
            updateLanguagePreferenceSummary(langCode);
            // In onSharedPreferenceChanged, dopo che la lingua è cambiata e prima di triggerRebirth
            String savedLangCode = sharedPreferences.getString(key, "NOT_FOUND");
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(requireContext()).edit();
            editor.putString(KEY_LANGUAGE_PREFERENCE, langCode);
            boolean success = editor.commit();
            Log.d("localeS", "Valore SALVATO in SharedPreferences per" + key + ":_"+ savedLangCode+"_");
            triggerRebirthWithAlarmManager(context);
        }
        else if (KEY_EXPIRY_DAYS_BEFORE.equals(key)) {
        }
        else if (KEY_THEME_PREFERENCE.equals(key)) {
            String themeValue = sharedPreferences.getString(key, ThemeHelper.SYSTEM_DEFAULT_MODE);
            ThemeHelper.applyThemePreference(themeValue);
            if (getActivity() != null) {
                getActivity().recreate();
            }
        }
    }
    private void updateLanguagePreferenceSummary(String languageValue) {
        if (languagePreference == null) {
            Log.w("localeS", "updateLanguagePreferenceSummary chiamato ma languagePreference è null");
            return;
        }
        if (languageValue == null) {
            Log.w("localeS", "updateLanguagePreferenceSummary chiamato con languageValue null");
            return;
        }

        Log.d("localeS", "Aggiornamento summary per languageValue: " + languageValue);

        CharSequence[] entries = languagePreference.getEntries();
        CharSequence[] entryValues = languagePreference.getEntryValues();

        if (entries == null || entryValues == null || entries.length != entryValues.length) {
            Log.e("localeS", "Entries o EntryValues non validi per languagePreference.");
            languagePreference.setSummary(languageValue); // Fallback al codice lingua
            return;
        }

        boolean found = false;
        for (int i = 0; i < entryValues.length; i++) {
            if (entryValues[i].toString().equals(languageValue)) {
                languagePreference.setSummary(entries[i]);
                Log.d("localeS", "Summary impostato a: " + entries[i]);
                found = true;
                break;
            }
        }

        if (!found) {
            Log.w("localeS", "Nessuna entry corrispondente trovata per languageValue: " + languageValue + ". Uso il valore come summary.");
            languagePreference.setSummary(languageValue);
        }
        LocaleHelper.setLocale(getContext(),languageValue);
    }
    public static void triggerRebirthWithAlarmManager(Context context) {
        if (context == null) {return;}
        Log.d("localeS", "triggerRebirthWithAlarmManager chiamato");
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
