# These are the rules suggested in the best practices document.
# This ensures Jetpack Compose functions correctly after obfuscation.
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.* <fields>;
    @androidx.compose.runtime.* <methods>;
}

# Keep any class annotated with @Keep, which is a general good practice.
-keep @androidx.annotation.Keep class *

# If you add libraries like Retrofit or Gson in the future, they will require
# their own specific ProGuard rules, which you will add here.
# For example, for a data class 'com.example.User' used by Gson:
# -keep class com.example.User { *; }

# The default rules below are good for debugging if you need them.
# Uncomment to preserve line number information for debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# --- Vosk speech recognition (JNA) ---
# JNA's native dispatcher looks up these classes and members by name via JNI.
# Renaming them crashes the app with UnsatisfiedLinkError when a model loads.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**

# --- Gson-serialized models ---
# Song and Folder are written to JSON for Bluetooth transfer and library export.
# Their field names are part of that format and must not be renamed.
-keepattributes Signature, *Annotation*
-keep class dev.sriramcodessw.versesync.data.Song { *; }
-keep class dev.sriramcodessw.versesync.data.Folder { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
