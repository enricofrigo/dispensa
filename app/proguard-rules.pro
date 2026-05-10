# Preservation of data entities for GSON serialization/deserialization (Backup/Restore)
-keep class eu.frigo.dispensa.data.** { *; }

# GSON specific rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.annotations.SerializedName

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Sync Module Rules
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

# Keep all sync classes
-keep class eu.frigo.dispensa.sync.core.** { *; }
-keep class eu.frigo.dispensa.sync.webdav.** { *; }
-keep class eu.frigo.dispensa.sync.local.** { *; }
-keep class eu.frigo.dispensa.sync.drive.** { *; }

# Keep Gson serializable DTOs
-keepclassmembers class eu.frigo.dispensa.sync.core.model.SyncChange {
    public <fields>;
}
-keepclassmembers class eu.frigo.dispensa.sync.core.SyncManager$SyncBlob {
    public <fields>;
}

# Keep database migrations
-keep class eu.frigo.dispensa.data.AppDatabase { *; }

# Keep WorkManager Worker
-keep class eu.frigo.dispensa.sync.core.engine.SyncWorker { *; }
