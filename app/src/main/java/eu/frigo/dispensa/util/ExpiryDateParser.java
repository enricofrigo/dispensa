package eu.frigo.dispensa.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpiryDateParser {

    public static String parseExpiryDate(String text) {
        String normalizedText = text.toLowerCase().replace("\n", " ");
        String[] keywords = { "data di scadenza", "da consumarsi preferibilmente", "da consumarsi", "best before", "bb",
                "scadenza", "scad", "lotto" };

        // Cerca prima vicino alle parole chiave, poi ovunque
        String result = findDateNearKeywords(normalizedText, keywords, false);
        if (result != null) return result;
        return findAnyDate(normalizedText, false);
    }

    /**
     * Versione "full-only" del parser: durante il rescan preferisce date complete
     * (gg/MM/aaaa). Se non ne trova, accetta anche qualsiasi formato mese/anno.
     */
    public static String parseExpiryDateFullOnly(String text) {
        String normalizedText = text.toLowerCase().replace("\n", " ");
        String[] keywords = { "data di scadenza", "da consumarsi preferibilmente", "da consumarsi", "best before", "bb",
                "scadenza", "scad", "lotto" };

        // Prima cerca data completa vicino a parole chiave
        String result = findDateNearKeywords(normalizedText, keywords, true);
        if (result != null) return result;
        // Poi qualsiasi data completa
        result = findAnyDate(normalizedText, true);
        if (result != null) return result;
        // Fallback: qualsiasi data (incluso mese/anno) vicino a parole chiave
        result = findDateNearKeywords(normalizedText, keywords, false);
        if (result != null) return result;
        // Ultimo fallback: qualsiasi data mese/anno
        return findAnyDate(normalizedText, false);
    }

    /**
     * Cerca una data nel testo solo nelle vicinanze di parole chiave.
     * @param fullOnly se true cerca solo date complete (gg/MM/aaaa)
     */
    private static String findDateNearKeywords(String text, String[] keywords, boolean fullOnly) {
        for (Object[] entry : buildPatterns(fullOnly)) {
            Pattern p = (Pattern) entry[0];
            boolean reversed = (boolean) entry[1];
            Matcher m = p.matcher(text);
            while (m.find()) {
                int idx = m.start();
                String before = text.substring(Math.max(0, idx - 40), idx);
                for (String kw : keywords) {
                    if (before.contains(kw)) {
                        return normalizeMatch(m, reversed);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Cerca qualsiasi data nel testo (senza vincolo di parole chiave).
     * @param fullOnly se true cerca solo date complete (gg/MM/aaaa)
     */
    private static String findAnyDate(String text, boolean fullOnly) {
        for (Object[] entry : buildPatterns(fullOnly)) {
            Pattern p = (Pattern) entry[0];
            boolean reversed = (boolean) entry[1];
            Matcher m = p.matcher(text);
            if (m.find()) {
                return normalizeMatch(m, reversed);
            }
        }
        return null;
    }

    /**
     * Costruisce la lista ordinata di pattern da provare.
     * Ogni entry è {Pattern, isReversed}.
     * Ordine: data completa → mese/anno (4 cifre) → anno/mese (4 cifre) → mese/anno (2 cifre) → anno/mese (2 cifre)
     */
    private static List<Object[]> buildPatterns(boolean fullOnly) {
        String sep = "[\\-/.\\s]+";
        List<Object[]> list = new ArrayList<>();
        // Data completa: gg/MM/aaaa (anno 4 cifre)
        list.add(new Object[]{ Pattern.compile(
                "\\b(0[1-9]|[12][0-9]|3[01])" + sep + "(0[1-9]|1[012])" + sep + "((?:19|20)\\d\\d)\\b"), false });
        // Data completa: gg/MM/aa (anno 2 cifre, es. 21.12.25)
        list.add(new Object[]{ Pattern.compile(
                "\\b(0[1-9]|[12][0-9]|3[01])" + sep + "(0[1-9]|1[012])" + sep + "(\\d{2})\\b"), false });
        if (!fullOnly) {
            // MM/yyyy
            list.add(new Object[]{ Pattern.compile(
                    "\\b(0[1-9]|1[012])" + sep + "((?:19|20)\\d\\d)\\b"), false });
            // yyyy/MM  (reversed)
            list.add(new Object[]{ Pattern.compile(
                    "\\b((?:19|20)\\d\\d)" + sep + "(0[1-9]|1[012])\\b"), true });
            // MM/yy
            list.add(new Object[]{ Pattern.compile(
                    "\\b(0[1-9]|1[012])" + sep + "(\\d{2})\\b"), false });
            // yy/MM  (reversed)
            list.add(new Object[]{ Pattern.compile(
                    "\\b(\\d{2})" + sep + "(0[1-9]|1[012])\\b"), true });
        }
        return list;
    }

    /**
     * Estrae la stringa normalizzata dal matcher.
     * Se reversed=true, scambia gruppo 1 e gruppo 2 (es. yyyy/MM → MM/yyyy).
     */
    private static String normalizeMatch(Matcher m, boolean reversed) {
        if (reversed && m.groupCount() >= 2) {
            String g1 = m.group(1); // es. "2026"
            String g2 = m.group(2); // es. "05"
            return g2 + "/" + g1;   // restituisce "05/2026"
        }
        return m.group().replaceAll("[\\-/.\\s]+", "/");
    }
}
