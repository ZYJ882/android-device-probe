# Preserve JNI entry points referenced by Android's native bridge.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep Activity entry points and native bridge names stable for reflection/JNI.
-keep public class com.deviceprobe.inspector.NativeBridge { *; }
-keep public class com.deviceprobe.inspector.MainActivity { *; }
-keep public class com.deviceprobe.inspector.ScreenTestActivity { *; }
