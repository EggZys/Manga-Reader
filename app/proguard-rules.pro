# ==================== Room ====================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ==================== Coil ====================
-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn coil.**

# ==================== Kotlin Serialization ====================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mangareader.**$$serializer { *; }
-keepclassmembers class com.mangareader.** {
    *** Companion;
}
-keepclasseswithmembers class com.mangareader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==================== Compose ====================
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ==================== Coroutines ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==================== OkHttp (used by Coil) ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ==================== General ====================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
