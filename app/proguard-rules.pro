# ==========================================
# BiliMusic — R8 Rules
#
# Strategy: shrink + optimize enabled, obfuscation disabled.
# This is an open-source project; renaming classes provides no
# security benefit and makes crash logs unreadable.
# ==========================================

#-------------------------------------------
# Global
#-------------------------------------------
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-keepattributes SourceFile,LineNumberTable

#-------------------------------------------
# Kotlin
#-------------------------------------------
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep data class components for copy() / toString() / destructuring
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

#-------------------------------------------
# 应用数据模型 — Gson + Retrofit 序列化
# 所有这些 data class 都在运行时通过反射填充
#-------------------------------------------

# 通用网络模型
-keep class com.thehbc.bilimusic.data.network.model.** { <fields>; }
-keep class com.thehbc.bilimusic.data.model.** { <fields>; }

# Gson @SerializedName 注解的字段
-keepclassmembers class com.thehbc.bilimusic.data.network.model.* {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit 接口（方法不能删除）
-keep,allowobfuscation interface com.thehbc.bilimusic.data.network.api.** { *; }

# 自定义 @WbiSign 注解（运行时通过反射检查）
-keep @interface com.thehbc.bilimusic.data.network.api.WbiSign
-keep @com.thehbc.bilimusic.data.network.api.WbiSign class * { *; }
-keepclassmembers class * {
    @com.thehbc.bilimusic.data.network.api.WbiSign *;
}

#-------------------------------------------
# Gson
#-------------------------------------------
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

#-------------------------------------------
# Retrofit / OkHttp / Okio
#-------------------------------------------
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

#-------------------------------------------
# Room
#-------------------------------------------
-keep class com.thehbc.bilimusic.data.local.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

#-------------------------------------------
# Compose
#-------------------------------------------
-keep class androidx.compose.** { *; }
# 保留 Composable 函数签名，避免运行时崩溃
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn androidx.compose.**

#-------------------------------------------
# Coil 3.x
#-------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**

#-------------------------------------------
# Media3 / ExoPlayer
#-------------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

#-------------------------------------------
# DataStore
#-------------------------------------------
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

#-------------------------------------------
# Lifecycle / ViewModel
#-------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class androidx.lifecycle.** { *; }

#-------------------------------------------
# Navigation Compose
#-------------------------------------------
-keep class androidx.navigation.** { *; }

#-------------------------------------------
# ZXing
#-------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

#-------------------------------------------
# Reorderable (sh.calvin.reorderable)
#-------------------------------------------
-keep class sh.calvin.reorderable.** { *; }

#-------------------------------------------
# AndroidX / Support
#-------------------------------------------
-keep class androidx.core.** { *; }
-keep class androidx.activity.** { *; }
-dontwarn androidx.**
