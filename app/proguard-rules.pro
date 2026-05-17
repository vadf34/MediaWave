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
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }
-keepclassmembers class com.yausername.** {
    public *;
    private *;
    protected *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}
