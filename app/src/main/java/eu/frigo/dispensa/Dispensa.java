package eu.frigo.dispensa;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import eu.frigo.dispensa.work.ExpiryCheckWorker; // Importa il tuo worker
import eu.frigo.dispensa.work.ExpiryCheckWorkerScheduler;

public class Dispensa extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        ExpiryCheckWorkerScheduler.scheduleWorker(this); // Schedula all'avvio dell'app
    }

    private void createNotificationChannel() {
        CharSequence name = getString(R.string.expiry_notification_channel_name);
        String description = getString(R.string.expiry_notification_channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(ExpiryCheckWorker.CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void scheduleExpiryCheckWorker() {
        // Crea una richiesta di lavoro periodica (es. una volta al giorno)
        PeriodicWorkRequest expiryCheckRequest =
                new PeriodicWorkRequest.Builder(ExpiryCheckWorker.class, 1, TimeUnit.DAYS)
                        // Puoi aggiungere vincoli qui, es: .setConstraints(Constraints.Builder()...build())
                        .build();

        // Schedula il lavoro, assicurandoti che ce ne sia solo uno attivo con lo stesso nome
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "expiryCheckWork", // Un nome univoco per questo lavoro
                ExistingPeriodicWorkPolicy.KEEP, // Mantieni il lavoro esistente se già schedulato
                // Oppure usa REPLACE se vuoi che ogni nuova schedulazione sovrascriva la precedente
                expiryCheckRequest);
    }
}