package eu.frigo.dispensa.data;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.dispensa.DispensaDao;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.product.ProductDao;

@RunWith(RobolectricTestRunner.class)
public class ProductManagementTest {

    private AppDatabase db;
    private ProductDao productDao;
    private DispensaDao dispensaDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        productDao = db.productDao();
        dispensaDao = db.dispensaDao();
    }

    @After
    public void closeDb() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testAddProductAndRetrieve() {
        Dispensa dispensa = new Dispensa("Dispensa Principale", true);
        long dispId = dispensaDao.insert(dispensa);

        Product product = new Product(
                "8001234567890",
                3,
                System.currentTimeMillis() + 86400000L * 7,
                "Latte Intero",
                null,
                "fridge",
                0L,
                -1
        );
        product.dispensaId = (int) dispId;
        product.lastModified = System.currentTimeMillis();

        long prodId = productDao.insert(product);
        Assert.assertTrue(prodId > 0);

        Product retrieved = productDao.getProductByIdSync((int) prodId);
        Assert.assertNotNull(retrieved);
        Assert.assertEquals("8001234567890", retrieved.getBarcode());
        Assert.assertEquals("Latte Intero", retrieved.getProductName());
        Assert.assertEquals(3, retrieved.getQuantity());
        Assert.assertEquals("fridge", retrieved.getStorageLocation());
        Assert.assertEquals((int) dispId, retrieved.dispensaId);
    }

    @Test
    public void testUpdateProduct() {
        Dispensa dispensa = new Dispensa("Cucina", true);
        long dispId = dispensaDao.insert(dispensa);

        Product product = new Product(
                "8009876543210",
                2,
                System.currentTimeMillis() + 86400000L * 10,
                "Pasta Barilla",
                null,
                "pantry",
                0L,
                -1
        );
        product.dispensaId = (int) dispId;
        long prodId = productDao.insert(product);
        product.setId((int) prodId);

        // Update product details
        product.setQuantity(5);
        product.setProductName("Pasta Barilla Integrale");
        product.setStorageLocation("cupboard");
        product.setOpenedDate(System.currentTimeMillis());
        product.setShelfLifeAfterOpeningDays(30);
        product.lastModified = System.currentTimeMillis();

        productDao.update(product);

        Product updated = productDao.getProductByIdSync((int) prodId);
        Assert.assertNotNull(updated);
        Assert.assertEquals(5, updated.getQuantity());
        Assert.assertEquals("Pasta Barilla Integrale", updated.getProductName());
        Assert.assertEquals("cupboard", updated.getStorageLocation());
        Assert.assertTrue(updated.isOpened());
        Assert.assertEquals(30, updated.getShelfLifeAfterOpeningDays());
    }

    @Test
    public void testDeleteProduct() {
        Dispensa dispensa = new Dispensa("Dispensa", true);
        long dispId = dispensaDao.insert(dispensa);

        Product product = new Product("1122334455", 1, System.currentTimeMillis() + 100000L, "Yogurt", null, "fridge", 0L, -1);
        product.dispensaId = (int) dispId;
        long prodId = productDao.insert(product);
        product.setId((int) prodId);

        Assert.assertNotNull(productDao.getProductByIdSync((int) prodId));

        productDao.delete(product);

        Product deleted = productDao.getProductByIdSync((int) prodId);
        Assert.assertNull(deleted);
    }

    @Test
    public void testGetProductByLotKey() {
        Dispensa dispensa = new Dispensa("Dispensa", true);
        long dispId = dispensaDao.insert(dispensa);

        long expiryDate1 = 1700000000000L;
        long expiryDate2 = 1700086400000L;

        Product p1 = new Product("123", 2, expiryDate1, "Succo Arancia", null, "fridge", 0L, -1);
        p1.dispensaId = (int) dispId;
        Product p2 = new Product("123", 4, expiryDate2, "Succo Arancia", null, "fridge", 0L, -1);
        p2.dispensaId = (int) dispId;

        productDao.insert(p1);
        productDao.insert(p2);

        Product match1 = productDao.getProductByLotKeySync("123", expiryDate1, "fridge", (int) dispId);
        Product match2 = productDao.getProductByLotKeySync("123", expiryDate2, "fridge", (int) dispId);

        Assert.assertNotNull(match1);
        Assert.assertNotNull(match2);
        Assert.assertEquals(2, match1.getQuantity());
        Assert.assertEquals(4, match2.getQuantity());
    }

    @Test
    public void testDeleteAllProductsForSpecificDispensa() {
        long disp1 = dispensaDao.insert(new Dispensa("Dispensa 1", true));
        long disp2 = dispensaDao.insert(new Dispensa("Dispensa 2", false));

        Product p1 = new Product("111", 1, 1000L, "Prod 1", null, "loc", 0L, -1);
        p1.dispensaId = (int) disp1;
        Product p2 = new Product("222", 2, 2000L, "Prod 2", null, "loc", 0L, -1);
        p2.dispensaId = (int) disp2;

        productDao.insert(p1);
        productDao.insert(p2);

        // Delete only products of disp1
        productDao.deleteAllProducts((int) disp1);

        List<Product> disp1Products = productDao.getAllProductsListStatic((int) disp1);
        List<Product> disp2Products = productDao.getAllProductsListStatic((int) disp2);

        Assert.assertEquals(0, disp1Products.size());
        Assert.assertEquals(1, disp2Products.size());
        Assert.assertEquals("Prod 2", disp2Products.get(0).getProductName());
    }

    @Test
    public void testUpdateProductLocation() {
        Dispensa dispensa = new Dispensa("Dispensa", true);
        long dispId = dispensaDao.insert(dispensa);

        Product p = new Product("555", 1, 1000L, "Burro", null, "cassetto_1", 0L, -1);
        p.dispensaId = (int) dispId;
        long pId = productDao.insert(p);

        // Update location from cassetto_1 to frigo_default
        productDao.updateProductLocation("cassetto_1", "frigo_default", (int) dispId);

        Product updated = productDao.getProductByIdSync((int) pId);
        Assert.assertNotNull(updated);
        Assert.assertEquals("frigo_default", updated.getStorageLocation());
    }
}
