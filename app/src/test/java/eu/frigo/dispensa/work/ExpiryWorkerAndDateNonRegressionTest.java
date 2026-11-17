package eu.frigo.dispensa.work;

import android.app.Application;
import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.util.DateConverter;

/**
 * Fase 0 Baseline Non-Regression Test Suite for DateConverter and ExpiryCheckWorker.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public class ExpiryWorkerAndDateNonRegressionTest {

    private AppDatabase db;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        AppDatabase.setTestInstance(db);
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
        AppDatabase.setTestInstance(null);
    }

    @Test
    public void testDateConverterSupportedFormatsAndRoundTrip() {
        Long tsFull = DateConverter.parseDisplayDateToTimestampMs("15/08/2027");
        Assert.assertNotNull(tsFull);
        Assert.assertEquals("15/08/2027", DateConverter.formatTimestampToDisplayDate(tsFull));

        Long tsShortYear = DateConverter.parseDisplayDateToTimestampMs("15/08/27");
        Assert.assertNotNull(tsShortYear);
        Assert.assertEquals("15/08/2027", DateConverter.formatTimestampToDisplayDate(tsShortYear));

        // Month/Year sets day to end of month (February 2026 -> 28/02/2026)
        Long tsMonthYear = DateConverter.parseDisplayDateToTimestampMs("02/2026");
        Assert.assertNotNull(tsMonthYear);
        Assert.assertEquals("28/02/2026", DateConverter.formatTimestampToDisplayDate(tsMonthYear));

        // Null and invalid inputs
        Assert.assertEquals("", DateConverter.formatTimestampToDisplayDate(null));
        Assert.assertNull(DateConverter.parseDisplayDateToTimestampMs(null));
        Assert.assertNull(DateConverter.parseDisplayDateToTimestampMs("   "));
        Assert.assertNull(DateConverter.parseDisplayDateToTimestampMs("not-a-date"));
    }

    @Test
    public void testExpiryCheckWorkerExecutesSuccessfullyWithProducts() throws Exception {
        Dispensa disp = new Dispensa("Dispensa", true);
        int dispId = (int) db.dispensaDao().insert(disp);

        long now = System.currentTimeMillis();
        // 1. Expiring tomorrow
        Product expSoon = new Product("8001", 1, now + 86400000L, "Ricotta", null, "fridge", 0L, -1);
        expSoon.dispensaId = dispId;
        db.productDao().insert(expSoon);

        // 2. Expiring in 30 days
        Product validProd = new Product("8002", 2, now + 86400000L * 30, "Biscotti", null, "pantry", 0L, -1);
        validProd.dispensaId = dispId;
        db.productDao().insert(validProd);

        // 3. Product without expiry date
        Product noExpProd = new Product("8003", 1, null, "Sale", null, "pantry", 0L, -1);
        noExpProd.dispensaId = dispId;
        db.productDao().insert(noExpProd);

        java.lang.reflect.Constructor<?> ctor = WorkerParameters.class.getConstructors()[0];
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> pt = paramTypes[i];
            if (pt == int.class) args[i] = 0;
            else if (pt == java.util.UUID.class) args[i] = java.util.UUID.randomUUID();
            else if (pt == androidx.work.Data.class) args[i] = androidx.work.Data.EMPTY;
            else if (pt == java.util.Collection.class) args[i] = java.util.Collections.emptyList();
            else args[i] = null;
        }
        WorkerParameters mockParams = (WorkerParameters) ctor.newInstance(args);
        ExpiryCheckWorker worker = new ExpiryCheckWorker(context, mockParams);

        ListenableWorker.Result result = worker.doWork();
        Assert.assertEquals(ListenableWorker.Result.success(), result);
    }

    @Test
    public void testDateConverterConcurrentMultiThreadSafety() throws Exception {
        int threads = 8;
        int iterationsPerThread = 50;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.List<java.util.concurrent.Future<Boolean>> futures = new java.util.ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < iterationsPerThread; i++) {
                    String dateStr = String.format(java.util.Locale.US, "%02d/%02d/2027", (i % 28) + 1, (threadIdx % 12) + 1);
                    Long ts = DateConverter.parseDisplayDateToTimestampMs(dateStr);
                    if (ts == null) return false;
                    String formatted = DateConverter.formatTimestampToDisplayDate(ts);
                    if (!dateStr.equals(formatted)) return false;
                }
                return true;
            }));
        }

        pool.shutdown();
        for (java.util.concurrent.Future<Boolean> f : futures) {
            Assert.assertTrue("Concurrent DateConverter formatting/parsing must be thread-safe", f.get());
        }
    }

    @Test
    public void testAlreadyExpiredProductIsTrackedToAvoidInfiniteDailyRepeat() throws Exception {
        Dispensa disp = new Dispensa("Dispensa", true);
        int dispId = (int) db.dispensaDao().insert(disp);

        long pastExpiry = System.currentTimeMillis() - 86400000L * 5; // Expired 5 days ago
        Product expiredProd = new Product("800999", 1, pastExpiry, "Yogurt Scaduto", null, "fridge", 0L, -1);
        expiredProd.dispensaId = dispId;
        int prodId = (int) db.productDao().insert(expiredProd);

        java.lang.reflect.Constructor<?> ctor = WorkerParameters.class.getConstructors()[0];
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> pt = paramTypes[i];
            if (pt == int.class) args[i] = 0;
            else if (pt == java.util.UUID.class) args[i] = java.util.UUID.randomUUID();
            else if (pt == androidx.work.Data.class) args[i] = androidx.work.Data.EMPTY;
            else if (pt == java.util.Collection.class) args[i] = java.util.Collections.emptyList();
            else args[i] = null;
        }
        WorkerParameters mockParams = (WorkerParameters) ctor.newInstance(args);
        ExpiryCheckWorker worker = new ExpiryCheckWorker(context, mockParams);

        Assert.assertEquals(ListenableWorker.Result.success(), worker.doWork());

        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        java.util.Set<String> notifiedKeys = prefs.getStringSet(ExpiryCheckWorker.PREF_NOTIFIED_EXPIRED_KEYS, java.util.Collections.emptySet());
        Assert.assertTrue("Expired product key must be recorded after first notification",
                notifiedKeys.contains(prodId + ":" + pastExpiry));
    }
}
