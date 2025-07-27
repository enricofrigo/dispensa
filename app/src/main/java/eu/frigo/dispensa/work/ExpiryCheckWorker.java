package eu.frigo.dispensa.work; // Crea questo package

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.data.ProductDao;
import android.Manifest; // Per il permesso delle notifiche
import android.app.PendingIntent;
import android.content.Intent;
import eu.frigo.dispensa.MainActivity;
import eu.frigo.dispensa.ui.SettingsFragment;


public class ExpiryCheckWorker extends Worker {

    private static final String TAG = "ExpiryCheckWorker";
    public static final String CHANNEL_ID = "EXPIRY_NOTIFICATION_CHANNEL";
    private static final int NOTIFICATION_ID = 1;
    private static final int DAYS_BEFORE_EXPIRY_WARNING = 3; // Notifica 3 giorni prima

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
            daysBeforeExpiryWarning = DAYS_BEFORE_EXPIRY_WARNING; // Fallback
        }

        Calendar todayCalendar = Calendar.getInstance();
        Calendar expiryWarningCalendar = Calendar.getInstance();
        expiryWarningCalendar.add(Calendar.DAY_OF_YEAR, daysBeforeExpiryWarning);

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayDateString = dateFormat.format(todayCalendar.getTime());
        String expiryWarningDateString = dateFormat.format(expiryWarningCalendar.getTime());

        ProductDao productDao = AppDatabase.getDatabase(context).productDao();
        List<Product> allProducts = productDao.getAllProductsListStatic(); // Assicurati di avere un metodo per ottenere una lista statica
        // o considera di fare questa logica in un thread separato se usi LiveData/Flow qui

        StringBuilder expiringProductsMessage = new StringBuilder();
        int expiringCount = 0;

        for (Product product : allProducts) {
            if (product.getExpiryDate() == null) {
                continue;
            }
            Date productExpiryDate = new Date(product.getExpiryDate());
            // Controlla se il prodotto scade oggi o entro DAYS_BEFORE_EXPIRY_WARNING giorni
            if (!productExpiryDate.after(expiryWarningCalendar.getTime()) && !productExpiryDate.before(todayCalendar.getTime())) {
                // Il prodotto è in scadenza (o scaduto oggi)
                expiringCount++;
                String productName = (product.getProductName() != null && !product.getProductName().isEmpty())
                        ? product.getProductName() : product.getBarcode();
                expiringProductsMessage.append("- ").append(productName)
                        .append(" (Scad. ").append(product.getExpiryDateString()).append(")\n");
            }
        }

        if (expiringCount > 0) {
            String notificationTitle = context.getString(R.string.expiring_products_notification_title, expiringCount);
            String notificationText = expiringProductsMessage.toString().trim();
            sendNotification(context, notificationTitle, notificationText);
        }

        return Result.success();
    }

    private void sendNotification(Context context, String title, String message) {
        // Crea un Intent per aprire MainActivity quando si clicca la notifica
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_expiry_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message)) // Per testo lungo
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent) // Imposta il PendingIntent
                .setAutoCancel(true); // La notifica si chiude quando l'utente la tocca

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // Controlla il permesso per le notifiche (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Se il permesso non è concesso, il worker non dovrebbe provare a inviare la notifica.
                // La richiesta di permesso dovrebbe essere gestita nell'UI dell'app.
                // Questo log è solo per debug.
                android.util.Log.w(TAG, "Permesso POST_NOTIFICATIONS non concesso. Impossibile inviare notifica.");
                return; // Non inviare la notifica
            }
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}

