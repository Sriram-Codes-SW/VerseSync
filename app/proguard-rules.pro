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
