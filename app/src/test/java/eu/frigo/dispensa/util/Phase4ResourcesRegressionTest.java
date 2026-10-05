package eu.frigo.dispensa.util;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Non-regression tests for Phase 4: Resource & Translation cleanup.
 * Guarantees XML integrity, string array balance, and absence of dead resources.
 */
public class Phase4ResourcesRegressionTest {

    private static final String RES_DIR = "src/main/res";

    private Document parseXml(File file) throws Exception {
        assertTrue("Resource file must exist: " + file.getPath(), file.exists());
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(file);
    }

    private Map<String, String> extractStrings(File file) throws Exception {
        Document doc = parseXml(file);
        NodeList nodes = doc.getElementsByTagName("string");
        Map<String, String> strings = new HashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String name = el.getAttribute("name");
            String content = el.getTextContent();
            strings.put(name, content);
        }
        return strings;
    }

    @Test
    public void testArraysXmlIntegrity() throws Exception {
        File arraysFile = new File(RES_DIR, "values/arrays.xml");
        Document doc = parseXml(arraysFile);

        NodeList arrayNodes = doc.getElementsByTagName("string-array");
        Map<String, Integer> arrayLengths = new HashMap<>();

        for (int i = 0; i < arrayNodes.getLength(); i++) {
            Element el = (Element) arrayNodes.item(i);
            String name = el.getAttribute("name");
            NodeList items = el.getElementsByTagName("item");
            arrayLengths.put(name, items.getLength());
        }

        // Verify array pairs exist and have matching entry/value sizes
        assertTrue(arrayLengths.containsKey("theme_entries"));
        assertTrue(arrayLengths.containsKey("theme_values"));
        assertEquals("theme_entries and theme_values must have identical sizes",
                arrayLengths.get("theme_entries"), arrayLengths.get("theme_values"));

        assertTrue(arrayLengths.containsKey("language_entries"));
        assertTrue(arrayLengths.containsKey("language_values"));
        assertEquals("language_entries and language_values must have identical sizes",
                arrayLengths.get("language_entries"), arrayLengths.get("language_values"));

        assertTrue(arrayLengths.containsKey("sync_provider_entries"));
        assertTrue(arrayLengths.containsKey("sync_provider_values"));
        assertEquals("sync_provider_entries and sync_provider_values must have identical sizes",
                arrayLengths.get("sync_provider_entries"), arrayLengths.get("sync_provider_values"));

        // Verify obsolete default_locations arrays were removed
        assertFalse("Obsolete default_locations_entries must not exist", arrayLengths.containsKey("default_locations_entries"));
        assertFalse("Obsolete default_locations_values must not exist", arrayLengths.containsKey("default_locations_values"));
    }

    @Test
    public void testEssentialStringsExistInAllLocales() throws Exception {
        String[] locales = {"values", "values-it", "values-de"};
        String[] essentialKeys = {
                "app_name",
                "add_product",
                "edit_product",
                "save_product_button",
                "delete_product_title",
                "tab_title_all_products",
                "pref_key_exp_days",
                "pref_key_exp_time",
                "provider_webdav",
                "provider_local_saf",
                "manage_dispense_title",
                "default_location_fridge_entry",
                "default_location_freezer_entry",
                "default_location_pantry_entry",
                "add_new_location",
                "sync_setup_error"
        };

        for (String locale : locales) {
            File stringsFile = new File(RES_DIR, locale + "/strings.xml");
            Map<String, String> strings = extractStrings(stringsFile);

            for (String key : essentialKeys) {
                assertTrue("Missing essential string '" + key + "' in locale: " + locale, strings.containsKey(key));
                assertNotNull("String content must not be null for '" + key + "' in " + locale, strings.get(key));
                assertFalse("String content must not be empty for '" + key + "' in " + locale, strings.get(key).trim().isEmpty());
            }
        }
    }

    @Test
    public void testDeadStringsRemovedFromAllLocales() throws Exception {
        String[] locales = {"values", "values-it", "values-de"};
        String[] deadKeys = {
                "barcode_mandatory",
                "err_scan_from_image",
                "defaults",
                "new_location",
                "default_icon",
                "hint_new_location",
                "version_format_simple",
                "pref_exp_time_title",
                "pref_cat_locations_title",
                "pref_location_title",
                "pref_location_summary",
                "pref_cat_theme_title",
                "pref_tosano_title",
                "pref_tosano_summary",
                "pref_clean_images_title",
                "pref_clean_images_summary",
                "pref_cat_off_cache_title",
                "shopping_list_badge_description",
                "pref_sync_config_title",
                "share_pantry_dialog_message",
                "share_button",
                "sync_configure_first",
                "pairing_code_title",
                "overwrite",
                "sync_webdav_warn_existing",
                "sync_webdav_warn_existing_desc",
                "sync_setup_err_device_already_present",
                "sync_setup_err",
                "shared_with_provider_format",
                "action_select_provider",
                "default_location_fridge",
                "default_location_freezer",
                "default_location_pantry",
                "pref_key_device_name"
        };

        for (String locale : locales) {
            File stringsFile = new File(RES_DIR, locale + "/strings.xml");
            Map<String, String> strings = extractStrings(stringsFile);

            for (String deadKey : deadKeys) {
                assertFalse("Dead string '" + deadKey + "' should be removed from " + locale, strings.containsKey(deadKey));
            }
        }
    }

    @Test
    public void testDeletedDrawablesAndMenusDoNotExist() {
        String[] deletedFiles = {
                "res/menu/menu_manage_locations.xml",
                "res/menu/menu_product_context.xml",
                "res/drawable/ic_launcher_foreground.xml",
                "res/drawable/viewfinder_border.xml",
                "res/drawable/edit_location_24px.xml",
                "res/drawable/ic_add_24_white.xml",
                "res/drawable/all_asterisk_24_white.xml",
                "res/drawable/list_item_border_expiring_soon.xml",
                "res/drawable/list_item_border_expired.xml",
                "res/drawable/ic_shopping_cart.xml",
                "res/drawable/ic_palceholder_image_background.xml",
                "res/drawable/ic_palceholder_image_foreground.xml"
        };

        for (String relPath : deletedFiles) {
            File f = new File("src/main", relPath);
            assertFalse("Deleted file should no longer exist: " + relPath, f.exists());
        }
    }
}
