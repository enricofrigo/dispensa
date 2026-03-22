package eu.frigo.dispensa.util;

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

    // Formatter per la visualizzazione. Locale.getDefault() usa le impostazioni
    // regionali dell'utente.
    private static final SimpleDateFormat displayFormatter = new SimpleDateFormat(DISPLAY_DATE_FORMAT_PATTERN,
            Locale.getDefault());

    /**
     * Converte un timestamp Long (millisecondi, che rappresenta una data specifica
     * con l'ora normalizzata) in una stringa di data formattata per la
     * visualizzazione.
     *
     * @param timestampMs Il timestamp in millisecondi. Se null, restituisce una
     *                    stringa vuota o un placeholder.
     * @return La data formattata come stringa (es. "25/12/2024"), o "" se il
     *         timestamp è null.
     */
    @NonNull
    public static String formatTimestampToDisplayDate(@Nullable Long timestampMs) {
        if (timestampMs == null) {
            return "";
        }
        return displayFormatter.format(new Date(timestampMs));
    }

    /**
     * Converte una stringa di data (dal formato di visualizzazione) in un timestamp
     * Long (millisecondi).
     * La data risultante avrà l'ora normalizzata (es. a mezzogiorno della timezone
     * di default)
     * per rappresentare l'intera giornata e facilitare i confronti di sole date.
     *
     * @param dateString La stringa della data da parsare (es. "25/12/2024").
     * @return Il timestamp in millisecondi, o null se la stringa non è valida o è
     *         vuota.
     */
    @Nullable
    public static Long parseDisplayDateToTimestampMs(@Nullable String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        String trimmed = dateString.trim();
        String formatPattern = null;

        if (trimmed.matches("\\d{2}/\\d{4}")) {
            formatPattern = "MM/yyyy";
        } else if (trimmed.matches("\\d{2}/\\d{2}/\\d{4}")) {
            formatPattern = "dd/MM/yyyy";
        } else if (trimmed.matches("\\d{2}/\\d{2}/\\d{2}")) {
            formatPattern = "dd/MM/yy";
        } else if (trimmed.matches("\\d{2}/\\d{2}")) {
            formatPattern = "MM/yy";
        } else {
            // Tentativo finale con formati addizionali se sfuggita alla normalizzazione (es. dd-MM-yyyy)
            String[] fallbackFormats = { "dd/MM/yyyy", "dd.MM.yyyy", "dd-MM-yyyy" };
            for (String format : fallbackFormats) {
                try {
                    SimpleDateFormat formatter = new SimpleDateFormat(format, Locale.getDefault());
                    formatter.setLenient(false);
                    Date parsedDate = formatter.parse(trimmed);
                    if (parsedDate != null) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(parsedDate);
                        normalizeTime(calendar);
                        return calendar.getTimeInMillis();
                    }
                } catch (ParseException ignored) {}
            }
        }

        if (formatPattern != null) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(formatPattern, Locale.getDefault());
                formatter.setLenient(false);
                Date parsedDate = formatter.parse(trimmed);

                if (parsedDate != null) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(parsedDate);

                    if (formatPattern.equals("MM/yyyy") || formatPattern.equals("MM/yy")) {
                        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                    }

                    normalizeTime(calendar);
                    return calendar.getTimeInMillis();
                }
            } catch (ParseException e) {
                // Procedi all'errore
            }
        }

        Log.e("DateConverter", "Errore durante il parsing della data: Unparseable date: \"" + dateString + "\"");
        return null;
    }

    @Nullable
    public static Date parseDisplayDateToDate(@Nullable String dateString) {
        Long l = parseDisplayDateToTimestampMs(dateString);
        if (l != null) {
            return new Date(l);
        }
        return null;
    }

    /**
     * Ottiene il timestamp per "oggi" con l'ora normalizzata (es. a mezzogiorno)
     * nella timezone di default del dispositivo. Utile per confronti "data contro
     * data".
     *
     * @return Il timestamp di oggi a mezzogiorno in millisecondi.
     */
    public static long getTodayNormalizedTimestamp() {
        Calendar calendar = Calendar.getInstance(); // Ottiene un calendario con data/ora correnti e timezone di default
        normalizeTime(calendar);
        return calendar.getTimeInMillis();
    }

    /**
     * Ottiene il timestamp per una data specifica (anno, mese, giorno) con l'ora
     * normalizzata
     * (es. a mezzogiorno) nella timezone di default del dispositivo.
     *
     * @param year       L'anno (es. 2024).
     * @param month      Il mese, 0-indexed (Gennaio = 0, Febbraio = 1, ...).
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
     * Helper privato per normalizzare l'ora di un oggetto Calendar a un punto fisso
     * della giornata
     * (es. mezzogiorno), azzerando minuti, secondi e millisecondi.
     * Questo aiuta a evitare problemi dovuti a differenze di ora quando si
     * confrontano solo le date.
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
     * Ottiene il timestamp per "adesso", utile per popolare campi "data di
     * creazione/modifica".
     * 
     * @return Il timestamp corrente in millisecondi.
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
}
