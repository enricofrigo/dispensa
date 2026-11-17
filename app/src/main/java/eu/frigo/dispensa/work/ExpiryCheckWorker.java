package eu.frigo.dispensa.work;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.activity.MainActivity;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.product.ProductDao;
import eu.frigo.dispensa.ui.SettingsFragment;

public class ExpiryCheckWorker extends Worker {

    private static final String TAG = "ExpiryCheckWorker";
    public static final String CHANNEL_ID = "EXPIRY_NOTIFICATION_CHANNEL";
    public static final String PREF_NOTIFIED_EXPIRED_KEYS = "notified_expired_product_keys";
    private static final int NOTIFICATION_ID = 1;
    private static final int DAYS_BEFORE_EXPIRY_WARNING = 3;

    public ExpiryCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String daysBeforeStr = prefs.getString(SettingsFragment.KEY_EXPIRY_DAYS_BEFORE, "3");
        int daysBeforeExpiryWarning;
        try {
            daysBeforeExpiryWarning = Integer.parseInt(daysBeforeStr);
        } catch (NumberFormatException e) {
            daysBeforeExpiryWarning = DAYS_BEFORE_EXPIRY_WARNING;
        }

        Calendar startOfToday = Calendar.getInstance();
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);

        Calendar expiryWarningCalendar = Calendar.getInstance();
        expiryWarningCalendar.add(Calendar.DAY_OF_YEAR, daysBeforeExpiryWarning);
        expiryWarningCalendar.set(Calendar.HOUR_OF_DAY, 23);
        expiryWarningCalendar.set(Calendar.MINUTE, 59);
        expiryWarningCalendar.set(Calendar.SECOND, 59);

        ProductDao productDao = AppDatabase.getDatabase(context).productDao();
        List<Product> allProducts = productDao.getAllProductsListStatic();

        Set<String> previouslyNotifiedExpired = new HashSet<>(
                prefs.getStringSet(PREF_NOTIFIED_EXPIRED_KEYS, Collections.emptySet())
        );
        Set<String> updatedNotifiedExpired = new HashSet<>();

        StringBuilder expiringProductsMessage = new StringBuilder();
        int expiringCount = 0;

        for (Product product : allProducts) {
            if (product.getExpiryDate() == null) {
                continue;
            }
            Date productExpiryDate = new Date(product.getExpiryDate());
            if (!productExpiryDate.after(expiryWarningCalendar.getTime())) {
                String expiredKey = product.getId() + ":" + product.getExpiryDate();
                boolean isAlreadyPastToday = productExpiryDate.before(startOfToday.getTime());
                if (isAlreadyPastToday) {
                    updatedNotifiedExpired.add(expiredKey);
                    if (previouslyNotifiedExpired.contains(expiredKey)) {
                        // Già notificato in precedenza come scaduto: evita di ripetere ogni giorno all'infinito
                        continue;
                    }
                }
                expiringCount++;
                String productName = (product.getProductName() != null && !product.getProductName().isEmpty())
                        ? product.getProductName() : product.getBarcode();
                String localizedExpiry = context.getString(R.string.exp_date, product.getExpiryDateString());
                expiringProductsMessage.append("- ").append(productName)
                        .append(" (").append(localizedExpiry).append(")\n");
            }
        }

        prefs.edit().putStringSet(PREF_NOTIFIED_EXPIRED_KEYS, updatedNotifiedExpired).apply();

        if (expiringCount > 0) {
            String notificationTitle = context.getString(R.string.expiring_products_notification_title, expiringCount);
            String notificationText = expiringProductsMessage.toString().trim();
            sendNotification(context, notificationTitle, notificationText);
        }

        return Result.success();
    }

    private void sendNotification(Context context, String title, String message) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_fridge)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w(TAG, "Permesso POST_NOTIFICATIONS non concesso. Impossibile inviare notifica.");
                return;
            }
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
