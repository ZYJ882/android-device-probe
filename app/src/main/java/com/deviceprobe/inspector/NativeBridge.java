package com.deviceprobe.inspector;

/** JNI bridge for data collected by the native C component. */
final class NativeBridge {
    static {
        System.loadLibrary("deviceprobe");
    }

    private NativeBridge() { }

    static native String collectNativeData();

    static native String collectVulkanData();

    /** Returns the pointer width of the running application process, or 0 if unavailable. */
    static native int processBitness();
}
