-keep class com.yausername.** { *; }
-keep class com.arthenica.** { *; }
-keep class com.mediawave.downloader.data.model.** { *; }
-keep class org.schabi.** { *; }
-dontwarn com.yausername.**
-dontwarn com.arthenica.**

-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
