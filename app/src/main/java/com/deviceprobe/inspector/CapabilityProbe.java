package com.deviceprobe.inspector;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.text.TextUtils;

import java.util.List;

/**
 * Enumerates only Android-public device features and sensors. It deliberately does
 * not infer unavailable hardware from a product model or online database.
 */
public final class CapabilityProbe {
    private CapabilityProbe() { }

    public static String collect(Context context) {
        StringBuilder out = new StringBuilder();
        PackageManager pm = context.getPackageManager();
        out.append("[硬件能力总览]\n");
        append(out, "摄像头", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)));
        append(out, "后置摄像头", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)));
        append(out, "闪光灯", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)));
        append(out, "自动对焦", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)));
        append(out, "多点触控", touchCapability(pm));
        append(out, "GPS 定位", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)));
        append(out, "网络定位", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK)));
        append(out, "Wi-Fi", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_WIFI)));
        append(out, "蓝牙", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)));
        append(out, "低功耗蓝牙", yesNo(Build.VERSION.SDK_INT >= 18 && pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)));
        append(out, "NFC", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_NFC)));
        append(out, "USB Host", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)));
        append(out, "电视遥控器规则", yesNo(DeviceProfile.detect(context).isTelevision()));
        append(out, "手表小屏规则", yesNo(DeviceProfile.detect(context).isWatch()));

        out.append("\n[摄像头公开枚举]\n");
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                String[] ids = manager == null ? null : manager.getCameraIdList();
                if (ids == null || ids.length == 0) {
                    out.append("系统未报告可枚举的摄像头；功能特性见上方。\n");
                } else {
                    out.append("系统报告的摄像头数量: ").append(ids.length).append("\n");
                    for (String id : ids) out.append("cameraId: ").append(id).append("（系统公开 ID，不代表模组型号）\n");
                }
            } catch (Throwable error) {
                out.append("摄像头枚举不可用: ").append(error.getClass().getSimpleName()).append("\n");
            }
        } else {
            out.append("需要 API 21+；当前系统未提供 Camera2 枚举。\n");
        }

        out.append("\n[传感器清单]\n");
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            out.append("传感器服务不可用。\n");
            return out.toString();
        }
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        out.append("系统枚举的传感器数量: ").append(sensors.size()).append("\n");
        if (sensors.isEmpty()) {
            out.append("系统未报告传感器。\n");
            return out.toString();
        }
        int limit = Math.min(sensors.size(), 64);
        for (int index = 0; index < limit; index++) {
            Sensor sensor = sensors.get(index);
            out.append(index + 1).append(". ").append(typeName(sensor.getType())).append("\n");
            out.append("   名称: ").append(safe(sensor.getName())).append("\n");
            out.append("   厂商: ").append(safe(sensor.getVendor())).append("；版本: ").append(sensor.getVersion()).append("\n");
            out.append("   最大量程: ").append(sensor.getMaximumRange())
                    .append("；分辨率: ").append(sensor.getResolution())
                    .append("；最小延迟: ").append(sensor.getMinDelay()).append(" µs\n");
        }
        if (sensors.size() > limit) out.append("另有 ").append(sensors.size() - limit).append(" 项未展开。\n");
        out.append("\n真实性说明: 上述能力来自 Android 系统 feature flag 和 SensorManager 枚举；“支持”表示系统已声明或已枚举，并不等于当前场景下功能必定可用。相机模组、指纹/基带芯片具体型号不会仅凭设备型号猜测。\n");
        return out.toString();
    }

    private static String touchCapability(PackageManager pm) {
        if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND)) return "支持 5 点以上（系统声明）";
        if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)) return "支持区分多点触控（系统声明）";
        if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)) return "支持多点触控（系统声明）";
        if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) return "支持单点触控（系统声明）";
        return "系统未声明触控能力";
    }

    private static String yesNo(boolean value) { return value ? "支持（系统公开声明）" : "系统未声明"; }

    private static void append(StringBuilder out, String key, String value) {
        out.append(key).append(": ").append(value).append('\n');
    }

    private static String safe(String value) { return TextUtils.isEmpty(value) ? "未知" : value.replace('\n', ' ').replace('\r', ' '); }

    private static String typeName(int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER: return "加速度计";
            case Sensor.TYPE_MAGNETIC_FIELD: return "磁力计";
            case Sensor.TYPE_ORIENTATION: return "方向传感器（旧类型）";
            case Sensor.TYPE_GYROSCOPE: return "陀螺仪";
            case Sensor.TYPE_LIGHT: return "光线传感器";
            case Sensor.TYPE_PRESSURE: return "气压计";
            case Sensor.TYPE_TEMPERATURE: return "温度传感器（旧类型）";
            case Sensor.TYPE_PROXIMITY: return "距离传感器";
            case Sensor.TYPE_GRAVITY: return "重力传感器";
            case Sensor.TYPE_LINEAR_ACCELERATION: return "线性加速度计";
            case Sensor.TYPE_ROTATION_VECTOR: return "旋转矢量";
            case Sensor.TYPE_RELATIVE_HUMIDITY: return "湿度传感器";
            case Sensor.TYPE_AMBIENT_TEMPERATURE: return "环境温度";
            case Sensor.TYPE_STEP_COUNTER: return "计步器";
            case Sensor.TYPE_HEART_RATE: return "心率传感器";
            default: return "传感器类型 " + type;
        }
    }
}
