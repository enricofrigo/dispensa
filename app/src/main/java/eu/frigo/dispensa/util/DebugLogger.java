package eu.frigo.dispensa.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * File-based logging utility for troubleshooting synchronization and application issues.
 * Outputs to both logcat and a persistent file in the app's internal storage.
 */
public class DebugLogger {
    private static final String LOG_FILE_NAME = "dispensa_debug.log";
    private static final String BACKUP_FILE_NAME = "dispensa_debug.log.1";
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1 MB
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static DebugLogger instance;
    private File logFile;
    private final Object lock = new Object();

    private DebugLogger(Context context) {
        logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new DebugLogger(context.getApplicationContext());
        }
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        logToFile("D", tag, msg, null);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        logToFile("I", tag, msg, null);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        logToFile("W", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(tag, msg, t);
        logToFile("E", tag, msg, t);
    }

    private static void logToFile(String level, String tag, String msg, Throwable t) {
        if (instance == null) return;
        instance.doLogToFile(level, tag, msg, t);
    }

    private void doLogToFile(String level, String tag, String msg, Throwable t) {
        synchronized (lock) {
            try {
                checkRotation();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                    String timestamp = DATE_FORMAT.format(new Date());
                    String threadName = Thread.currentThread().getName();
                    String line = String.format("%s [%s] %s/%s: %s\n", timestamp, threadName, level, tag, msg);
                    writer.write(line);
                    if (t != null) {
                        writer.write(Log.getStackTraceString(t));
                        writer.write("\n");
                    }
                }
            } catch (IOException e) {
                // Fail silently to avoid app crashes
            }
        }
    }

    private void checkRotation() {
        if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
            File backup = new File(logFile.getParent(), BACKUP_FILE_NAME);
            if (backup.exists()) {
                backup.delete();
            }
            logFile.renameTo(backup);
            logFile = new File(logFile.getParent(), LOG_FILE_NAME);
        }
    }

    public static File exportLog(Context context) {
        if (instance == null) init(context);
        return instance.logFile;
    }

    public static void clearLog() {
        if (instance == null) return;
        synchronized (instance.lock) {
            if (instance.logFile.exists()) {
                instance.logFile.delete();
            }
            File backup = new File(instance.logFile.getParent(), BACKUP_FILE_NAME);
            if (backup.exists()) {
                backup.delete();
            }
        }
    }
}
