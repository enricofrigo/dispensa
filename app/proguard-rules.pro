# Preservation of data entities for GSON serialization/deserialization (Backup/Restore)
-keep class eu.frigo.dispensa.data.** { *; }

# GSON specific rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.annotations.SerializedName