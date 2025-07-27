package eu.frigo.dispensa.utils;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateConverter {

    private static final String DISPLAY_DATE_FORMAT_PATTERN = "dd/MM/yyyy";

    // Formatter per la visualizzazione. Locale.getDefault() usa le impostazioni regionali dell'utente.
    private static final SimpleDateFormat displayFormatter =
            new SimpleDateFormat(DISPLAY_DATE_FORMAT_PATTERN, Locale.getDefault());


    /**
     * Converte un timestamp Long (millisecondi, che rappresenta una data specifica
     * con l'ora normalizzata) in una stringa di data formattata per la visualizzazione.
     *
     * @param timestampMs Il timestamp in millisecondi. Se null, restituisce una stringa vuota o un placeholder.
     * @return La data formattata come stringa (es. "25/12/2024"), o "" se il timestamp è null.
     */
    @NonNull
    public static String formatTimestampToDisplayDate(@Nullable Long timestampMs) {
        if (timestampMs == null) {
            return ""; // O potresti restituire "N/D", o null se il chiamante lo gestisce
        }
        // Il timestamp dovrebbe già rappresentare una data con ora normalizzata (es. mezzogiorno UTC).
        // Quando lo formattiamo, SimpleDateFormat userà la timezone di default del dispositivo
        // per interpretare quel momento nel tempo.
        return displayFormatter.format(new Date(timestampMs));
    }

    /**
     * Converte una stringa di data (dal formato di visualizzazione) in un timestamp Long (millisecondi).
     * La data risultante avrà l'ora normalizzata (es. a mezzogiorno della timezone di default)
     * per rappresentare l'intera giornata e facilitare i confronti di sole date.
     *
     * @param dateString La stringa della data da parsare (es. "25/12/2024").
     * @return Il timestamp in millisecondi, o null se la stringa non è valida o è vuota.
     */
    @Nullable
    public static Long parseDisplayDateToTimestampMs(@Nullable String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        try {
            // Per il parsing, SimpleDateFormat userà la timezone di default del dispositivo.
            displayFormatter.setLenient(false); // Per un parsing più stretto del formato atteso.
            Date parsedDate = displayFormatter.parse(dateString.trim());

            if (parsedDate != null) {
                Calendar calendar = Calendar.getInstance(); // Ottiene un calendario con la timezone di default
                calendar.setTime(parsedDate);
                normalizeTime(calendar);
                return calendar.getTimeInMillis();
            }
        } catch (ParseException e) {
            Log.e("DateConverter", "Errore durante il parsing della data: " + e.getMessage());
            return null; // Indica che il parsing è fallito
        }
        return null;
    }

    /**
     * Ottiene il timestamp per "oggi" con l'ora normalizzata (es. a mezzogiorno)
     * nella timezone di default del dispositivo. Utile per confronti "data contro data".
     *
     * @return Il timestamp di oggi a mezzogiorno in millisecondi.
     */
    public static long getTodayNormalizedTimestamp() {
        Calendar calendar = Calendar.getInstance(); // Ottiene un calendario con data/ora correnti e timezone di default
        normalizeTime(calendar);
        return calendar.getTimeInMillis();
    }

    /**
     * Ottiene il timestamp per una data specifica (anno, mese, giorno) con l'ora normalizzata
     * (es. a mezzogiorno) nella timezone di default del dispositivo.
     *
     * @param year L'anno (es. 2024).
     * @param month Il mese, 0-indexed (Gennaio = 0, Febbraio = 1, ...).
     * @param dayOfMonth Il giorno del mese (1-31).
     * @return Il timestamp per quella data a mezzogiorno in millisecondi.
     */
    public static Long getTimestampForDate(int year, int month, int dayOfMonth) {
        Calendar calendar = Calendar.getInstance(); // Ottiene un calendario con la timezone di default
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month); // Il mese per Calendar è 0-indexed
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        normalizeTime(calendar);
        return calendar.getTimeInMillis();
    }

    /**
     * Helper privato per normalizzare l'ora di un oggetto Calendar a un punto fisso della giornata
     * (es. mezzogiorno), azzerando minuti, secondi e millisecondi.
     * Questo aiuta a evitare problemi dovuti a differenze di ora quando si confrontano solo le date.
     *
     * @param calendar L'oggetto Calendar da normalizzare.
     */
    private static void normalizeTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 12); // Mezzogiorno (24h format)
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    /**
     * Ottiene il timestamp per "adesso", utile per popolare campi "data di creazione/modifica".
     * @return Il timestamp corrente in millisecondi.
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
}
