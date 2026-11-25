# MUVANTA release hardening & ProGuard/R8 rules

# Strip verbose and debug Android log calls in minified release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Preserve Room database entities & generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
