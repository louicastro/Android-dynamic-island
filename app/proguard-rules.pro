# DynamicIsland Proguard Rules
# Keep data models and serialization
-keep class com.dynamicisland.android.model.** { *; }

# Keep Compose runtime annotations
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Keep Services
-keep class com.dynamicisland.android.service.** { *; }

# Keep Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
