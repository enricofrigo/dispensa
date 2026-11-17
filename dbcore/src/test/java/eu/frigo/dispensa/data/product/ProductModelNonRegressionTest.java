package eu.frigo.dispensa.data.product;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;

/**
 * Fase 0 Baseline Non-Regression Test Suite for Product model calculations:
 * expiry dates, shelf-life after opening, remaining percentage clamping,
 * custom local image sanitization, and copy helpers.
 */
@RunWith(RobolectricTestRunner.class)
public class ProductModelNonRegressionTest {

    @Test
    public void testExpiryDateFormattingAndActualExpiryWithShelfLife() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.DECEMBER, 25, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long expiryTs = cal.getTimeInMillis();

        Product p = new Product("800111", 1, expiryTs, "Panna Fresca", null, "fridge", 0L, -1);
        Assert.assertFalse(p.isOpened());
        Assert.assertEquals(expiryTs, p.getActualExpiryTimestamp().longValue());
        Assert.assertTrue(p.getExpiryDateString().contains("25/12/2026"));

        // Null or <= 0 expiry date
        Product noExpiry = new Product("800222", 1, null, "Sale", null, "pantry", 0L, -1);
        Assert.assertEquals(0L, noExpiry.getActualExpiryTimestamp().longValue());
        Assert.assertNotNull(noExpiry.getExpiryDateString());
        Assert.assertFalse(noExpiry.getExpiryDateString().isEmpty());
    }

    @Test
    public void testRemainingPercentageClampingAndCopyWithNewQuantity() {
        Product p = new Product("800333", 5, 1750000000000L, "Biscotti", "https://img.example.com/a.jpg", "pantry", 1740000000000L, 14, "Nota test");
        p.id = 42;
        p.dispensaId = 3;
        p.lastModified = 123456789L;

        p.setRemainingPercentage(150);
        Assert.assertEquals(100, p.getRemainingPercentage());

        p.setRemainingPercentage(-20);
        Assert.assertEquals(0, p.getRemainingPercentage());

        p.setRemainingPercentage(75);
        Assert.assertEquals(75, p.getRemainingPercentage());

        Product copy = p.copyWithNewQuantity(2);
        Assert.assertEquals(42, copy.id);
        Assert.assertEquals(3, copy.dispensaId);
        Assert.assertEquals(2, copy.getQuantity());
        Assert.assertEquals(75, copy.getRemainingPercentage());
        Assert.assertEquals("Nota test", copy.getNote());
        Assert.assertEquals(123456789L, copy.lastModified);
    }

    @Test
    public void testCustomLocalImageDetectionAndCreateExportCopy() {
        Product remoteImgProduct = new Product("800444", 1, null, "Pasta", "https://images.openfoodfacts.org/pasta.jpg", "pantry", 0L, -1);
        Assert.assertFalse(remoteImgProduct.hasCustomLocalImage());
        Assert.assertEquals("https://images.openfoodfacts.org/pasta.jpg", remoteImgProduct.createExportCopy().getImageUrl());

        Product localImgProduct = new Product("800555", 1, null, "Torta Fatta in Casa", "file:///data/user/0/eu.frigo.dispensa/files/img.jpg", "fridge", 0L, -1);
        Assert.assertTrue(localImgProduct.hasCustomLocalImage());
        Assert.assertNull("Export copy must strip local file:// image URL", localImgProduct.createExportCopy().getImageUrl());
    }
}
