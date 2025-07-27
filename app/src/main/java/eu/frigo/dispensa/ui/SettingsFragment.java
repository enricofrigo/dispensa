package eu.frigo.dispensa.ui; // o un package appropriato per la UI

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.util.Calendar;
import java.util.Locale;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.work.ExpiryCheckWorkerScheduler;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String KEY_EXPIRY_DAYS_BEFORE = "pref_expiry_days_before";
    public static final String KEY_NOTIFICATION_TIME_HOUR = "pref_notification_time_hour";
    public static final String KEY_NOTIFICATION_TIME_MINUTE = "pref_notification_time_minute";

    private Preference notificationTimePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        notificationTimePreference = findPreference("pref_notification_time");
        if (notificationTimePreference != null) {
            updateNotificationTimeSummary();
            notificationTimePreference.setOnPreferenceClickListener(preference -> {
                showTimePickerDialog();
                return true;
            });
        }

        // Valida l'input per i giorni di preavviso (opzionale ma consigliato)
        EditTextPreference daysBeforePref = findPreference(KEY_EXPIRY_DAYS_BEFORE);
        if (daysBeforePref != null) {
            daysBeforePref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int days = Integer.parseInt((String) newValue);
                    if (days >= 0 && days <= 365) { // Esempio di range valido
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // Non è un numero
                }
                // Mostra un Toast o un messaggio di errore se l'input non è valido
                android.widget.Toast.makeText(getContext(), "Inserisci un numero di giorni valido (0-365).", android.widget.Toast.LENGTH_SHORT).show();
                return false; // Impedisce il salvataggio del valore non valido
            });
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
                .setTitleText("Seleziona ora notifica")
                .build();

        timePicker.addOnPositiveButtonClickListener(dialog -> {
            int selectedHour = timePicker.getHour();
            int selectedMinute = timePicker.getMinute();

            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_NOTIFICATION_TIME_HOUR, selectedHour);
            editor.putInt(KEY_NOTIFICATION_TIME_MINUTE, selectedMinute);
            editor.apply();

            updateNotificationTimeSummary();
            // Rischedula il worker perché l'ora è cambiata
            ExpiryCheckWorkerScheduler.scheduleWorker(requireContext());
        });

        timePicker.show(getParentFragmentManager(), "TIME_PICKER_TAG");
    }

    private void updateNotificationTimeSummary() {
        if (notificationTimePreference != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            int hour = prefs.getInt(KEY_NOTIFICATION_TIME_HOUR, 9);
            int minute = prefs.getInt(KEY_NOTIFICATION_TIME_MINUTE, 0);
            // Formatta l'ora per la visualizzazione
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
        updateNotificationTimeSummary(); // Aggiorna anche al resume
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null && key.equals(KEY_EXPIRY_DAYS_BEFORE)) {
            // I giorni sono cambiati, il worker userà il nuovo valore al prossimo avvio.
            // Non è strettamente necessario rischedulare, ma se vuoi che il cambio sia
            // immediato per il calcolo dei giorni nel prossimo run, potresti farlo.
            // Per ora, lasciamo che il worker legga il valore aggiornato al prossimo run.
        }
        // Per KEY_NOTIFICATION_TIME_HOUR e KEY_NOTIFICATION_TIME_MINUTE,
        // la rischedulazione avviene già nel listener del TimePicker.
    }
}
