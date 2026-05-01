# ========================================
# 通用保留
# ========================================
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions

# ========================================
# SQLCipher
# ========================================
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }

# ========================================
# UPnP (weupnp)
# ========================================
-keep class org.bitlet.weupnp.** { *; }
-keepclassmembers class org.bitlet.weupnp.** { *; }
-dontwarn org.bitlet.weupnp.**

# ========================================
# 项目工具类
# ========================================
-keep class com.example.dualmapper.util.AesUtils { *; }
-keep class com.example.dualmapper.util.ErrorHandler { *; }
-keep class com.example.dualmapper.util.LogExporter { *; }
-keep class com.example.dualmapper.util.KeyExchangeHelper { *; }

# ========================================
# Room（精确保留实体与 DAO）
# ========================================
-keep class com.example.dualmapper.data.KeyMappingEntity { *; }
-keep class com.example.dualmapper.data.PresetLayoutEntity { *; }
-keep class com.example.dualmapper.data.KeyCodeLibraryEntity { *; }
-keep class com.example.dualmapper.data.AppDatabase { *; }
-keep class com.example.dualmapper.data.KeyMappingDao { *; }
-keep class com.example.dualmapper.data.PresetLayoutDao { *; }
-keep class com.example.dualmapper.data.KeyCodeLibraryDao { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ========================================
# Hilt / Dagger
# ========================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}
-keep class com.example.dualmapper.DualMappingApp_GeneratedInjector { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }

# ========================================
# Glide
# ========================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl

# ========================================
# Kotlin Coroutines
# ========================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ========================================
# Bouncy Castle (加密)
# ========================================
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ========================================
# Compose
# ========================================
-keep class androidx.compose.ui.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class androidx.compose.runtime.** { *; }

# ========================================
# Kotlin 序列化 (JSON / Parcelable)
# ========================================
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keep class kotlinx.serialization.** { *; }

# ========================================
# AndroidX / Lifecycle / ViewModel
# ========================================
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# ========================================
# 枚举
# ========================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========================================
# 避免警告的规则
# ========================================
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.**