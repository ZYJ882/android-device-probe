# JNI 入口与本地桥接
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.deviceprobe.inspector.NativeBridge { *; }

# Activity 入口和版本信息
-keep public class com.deviceprobe.inspector.MainActivity { *; }
-keep public class com.deviceprobe.inspector.ScreenTestActivity { *; }
-keep class com.deviceprobe.inspector.BuildConfig { *; }

# AndroidX 兼容窗口 API 的防御性保留
-dontwarn androidx.core.**
-keep class androidx.core.view.WindowCompat { *; }
-keep class androidx.core.view.WindowInsetsControllerCompat { *; }
-keep class androidx.core.view.WindowInsetsCompat { *; }

# Android framework 的兼容 API 反射/系统实现可能产生的非致命警告
-dontwarn android.hardware.camera2.**
-dontwarn android.hardware.display.**
-dontpreverify
-ignorewarnings
