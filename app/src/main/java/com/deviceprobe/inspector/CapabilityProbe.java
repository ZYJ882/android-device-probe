package com.deviceprobe.inspector;

import android.content.Context;
import android.graphics.ImageFormat;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.text.TextUtils;
import android.util.Range;
import android.util.Size;

import java.util.List;

/**
 * Enumerates only Android-public device features, Camera2 metadata and sensors.
 * It deliberately does not infer unavailable hardware from a product model or online database.
 */
public final class CapabilityProbe {
    private CapabilityProbe() { }

    public static String collect(Context context, DeviceProfile profile) {
        StringBuilder out = new StringBuilder();
        PackageManager pm = context.getPackageManager();
        out.append("[硬件能力总览]\n");
        append(out, "摄像头存在", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)));
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
        append(out, "电视遥控器规则", yesNo(profile.isTelevision()));
        append(out, "手表小屏规则", yesNo(profile.isWatch()));

        appendCameraSection(out, context);
        appendSensorSection(out, context);
        out.append("\n真实性说明: 上述能力来自 Android 系统 feature flag、Camera2 和 SensorManager 的实际公开枚举；“支持”表示系统已声明或已枚举，并不等于当前场景下功能必定可用。应用不打开相机、不申请 CAMERA 权限；相机模组、指纹/基带芯片具体型号不会仅凭设备型号猜测。\n");
        return out.toString();
    }

    private static void appendCameraSection(StringBuilder out, Context context) {
        out.append("\n[摄像头公开枚举]\n");
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] ids = manager == null ? null : manager.getCameraIdList();
            if (ids == null || ids.length == 0) {
                out.append("系统未报告可枚举的摄像头；功能特性见上方。\n");
                return;
            }
            int front = 0;
            int back = 0;
            int external = 0;
            int unknown = 0;
            StringBuilder details = new StringBuilder();
            for (String id : ids) {
                try {
                    CameraCharacteristics c = manager.getCameraCharacteristics(id);
                    Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                    String facingName = facingName(facing);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) front++;
                    else if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) back++;
                    else if (Build.VERSION.SDK_INT >= 23 && facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) external++;
                    else unknown++;
                    details.append("cameraId ").append(id).append(": ").append(facingName)
                            .append("（Camera2 LENS_FACING）\n");
                    Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null) details.append("  闪光灯: ").append(flash ? "系统报告可用" : "系统报告不可用").append("\n");
                    Integer hardwareLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    if (hardwareLevel != null) details.append("  Camera2 硬件级别: ").append(cameraLevel(hardwareLevel)).append("\n");
                    appendCameraMetadata(details, c);
                } catch (Throwable error) {
                    unknown++;
                    details.append("cameraId ").append(id).append(": metadata 不可读取（")
                            .append(error.getClass().getSimpleName()).append("）\n");
                }
            }
            append(out, "系统枚举摄像头", ids.length + " 个");
            append(out, "后置摄像头", back > 0 ? back + " 个（Camera2 实际枚举）" : "系统未公开或未枚举到");
            append(out, "前置摄像头", front > 0 ? front + " 个（Camera2 实际枚举）" : "系统未公开或未枚举到");
            append(out, "外置摄像头", external > 0 ? external + " 个（Camera2 实际枚举）" : "系统未公开或未枚举到");
            append(out, "方向未知摄像头", unknown > 0 ? unknown + " 个（metadata 未公开或不可读）" : "无");
            out.append(details);
        } catch (Throwable error) {
            out.append("摄像头枚举不可用: ").append(error.getClass().getSimpleName()).append("\n");
        }
    }

    private static void appendSensorSection(StringBuilder out, Context context) {
        out.append("\n[传感器清单]\n");
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            out.append("传感器服务不可用。\n");
            return;
        }
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        out.append("系统枚举的传感器数量: ").append(sensors.size()).append("\n");
        if (sensors.isEmpty()) {
            out.append("系统未报告传感器。\n");
            return;
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
    }

    private static void appendCameraMetadata(StringBuilder details, CameraCharacteristics c) {
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map != null) {
            Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
            Size largest = largestSize(sizes);
            details.append("  最大 JPEG 分辨率: ").append(largest == null ? "系统未公开" : largest.getWidth() + " × " + largest.getHeight()).append("\n");
        } else {
            details.append("  JPEG 输出分辨率: 系统未公开\n");
        }
        Range<Integer>[] ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        details.append("  目标 FPS 范围: ").append(formatFpsRanges(ranges)).append("\n");
        int[] afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        details.append("  自动对焦: ").append(hasAutofocus(afModes) ? "Camera2 metadata 报告支持" : "系统未公开或不支持").append("\n");
        int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        details.append("  RAW: ").append(hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) ? "Camera2 metadata 报告支持" : "系统未公开或不支持").append("\n");
        Integer logicalMulti = reflectedLogicalMultiCameraCapability();
        details.append("  逻辑多摄像头: ").append(logicalMulti == null ? "需要 API 28+ 公开字段" : (hasCapability(caps, logicalMulti) ? "Camera2 metadata 报告支持" : "系统未公开或不支持")).append("\n");
    }

    private static Size largestSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return null;
        Size largest = null;
        long area = -1;
        for (Size size : sizes) {
            if (size == null) continue;
            long candidate = (long) size.getWidth() * size.getHeight();
            if (candidate > area) { area = candidate; largest = size; }
        }
        return largest;
    }

    private static String formatFpsRanges(Range<Integer>[] ranges) {
        if (ranges == null || ranges.length == 0) return "系统未公开";
        StringBuilder out = new StringBuilder();
        int limit = Math.min(ranges.length, 4);
        for (int index = 0; index < limit; index++) {
            if (index > 0) out.append("、");
            Range<Integer> range = ranges[index];
            out.append(range.getLower()).append("–").append(range.getUpper()).append(" fps");
        }
        if (ranges.length > limit) out.append("（另有 ").append(ranges.length - limit).append(" 项）");
        return out.toString();
    }

    private static boolean hasAutofocus(int[] modes) {
        if (modes == null) return false;
        for (int mode : modes) {
            if (mode != CameraCharacteristics.CONTROL_AF_MODE_OFF) return true;
        }
        return false;
    }

    private static boolean hasCapability(int[] capabilities, int target) {
        if (capabilities == null) return false;
        for (int capability : capabilities) if (capability == target) return true;
        return false;
    }

    private static Integer reflectedLogicalMultiCameraCapability() {
        if (Build.VERSION.SDK_INT < 28) return null;
        try {
            return (Integer) CameraCharacteristics.class
                    .getField("REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA").get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String facingName(Integer facing) {
        if (facing == null) return "方向未公开";
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) return "前置";
        if (facing == CameraCharacteristics.LENS_FACING_BACK) return "后置";
        if (Build.VERSION.SDK_INT >= 23 && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) return "外置";
        return "未知方向（值 " + facing + "）";
    }

    private static String cameraLevel(int level) {
        switch (level) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: return "LEGACY";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: return "LIMITED";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL: return "FULL";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3: return "LEVEL_3";
            default: return "值 " + level;
        }
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
