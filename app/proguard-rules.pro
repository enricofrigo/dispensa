# Ignora le classi mancanti di Google Play Services e del modulo gdrive
-dontwarn com.google.android.gms.**
-dontwarn eu.frigo.dispensa.sync.gdrive.**

# Preservation of data entities for GSON serialization/deserialization (Backup/Restore)
-keep class eu.frigo.dispensa.data.** { *; }
-keep class eu.frigo.dispensa.sync.gdrive.** { *; }

# GSON specific rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.annotations.SerializedName