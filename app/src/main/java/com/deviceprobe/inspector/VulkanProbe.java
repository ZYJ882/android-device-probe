package com.deviceprobe.inspector;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Reports Vulkan framework features and the actual native loader/device query. */
final class VulkanProbe {
    private VulkanProbe() { }

    static String collect(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("[Vulkan 支持检测]\n");
        if (Build.VERSION.SDK_INT < 24) {
            out.append("系统状态: Android 7.0 / API 24 前没有公开 Vulkan 支持基线\n");
            out.append("native 查询: 未执行（保持 Android 5.0 兼容）\n");
            return out.toString();
        }
        PackageManager pm = context.getPackageManager();
        boolean level = pm.hasSystemFeature("android.hardware.vulkan.level");
        boolean version = pm.hasSystemFeature("android.hardware.vulkan.version");
        out.append("系统 Vulkan 特性: ").append(level || version ? "已声明" : "未声明").append('\n');
        out.append("Vulkan 硬件级别特性: ").append(level ? "已声明" : "未声明").append('\n');
        out.append("Vulkan 硬件版本特性: ").append(version ? "已声明" : "未声明").append('\n');
        try {
            out.append(NativeBridge.collectVulkanData());
        } catch (Throwable error) {
            out.append("native Vulkan 查询: 未完成（")
                    .append(error.getClass().getSimpleName()).append(": ")
                    .append(error.getMessage() == null ? "未知错误" : error.getMessage()).append(")\n");
        }
        out.append("说明: 特性声明是系统兼容性信号；设备名称、驱动版本和扩展以 Vulkan 驱动实际枚举结果为准。\n");
        return out.toString();
    }
}
