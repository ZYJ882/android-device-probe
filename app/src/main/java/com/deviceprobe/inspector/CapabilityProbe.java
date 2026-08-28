package com.deviceprobe.inspector;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.MediaRecorder;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.text.TextUtils;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        append(out, "摄像头存在", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)));
        append(out, "闪光灯", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)));
        append(out, "自动对焦", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)));
        append(out, "多点触控", touchCapability(pm));
        append(out, "GPS 定位", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)));
        append(out, "网络定位", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK)));
        append(out, "Wi-Fi", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_WIFI)));
        append(out, "蓝牙", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)));
        append(out, "低功耗蓝牙", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)));
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
        details.append("  【光学参数】\n");
        float[] focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        float[] apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
        Size[] jpegSizes = null;
        Size[] videoSizes = null;
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map != null) {
            jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
            videoSizes = map.getOutputSizes(MediaRecorder.class);
        }
        Size largestJpeg = largestSize(jpegSizes);
        String maxPhoto = largestJpeg == null ? "系统未公开" : formatSize(largestJpeg);
        details.append("  最大照片分辨率: ").append(maxPhoto);
        if (largestJpeg != null) {
            double mp = (double) largestJpeg.getWidth() * largestJpeg.getHeight() / 1000000.0;
            details.append("；有效像素约 ").append(String.format(Locale.US, "%.1f MP", mp));
            details.append("；宽高比 ").append(aspectRatio(largestJpeg));
        }
        details.append("（Camera2 JPEG 输出，非厂商宣传值）\n");
        details.append("  支持的照片分辨率: ").append(formatSizes(jpegSizes, 8)).append("\n");
        details.append("  类型: ").append(inferLensType(focalLengths, largestJpeg)).append("（仅为 Camera2 可见字段推断）\n");
        details.append("  真实光学焦距: ").append(formatFloatArray(focalLengths, "mm", "系统未公开")).append("\n");
        SizeF sensorSize = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        details.append("  传感器物理尺寸: ").append(formatSensorSize(sensorSize)).append("（Camera2 原始/未公开）\n");
        details.append("  传感器像素阵列: ").append(formatSize(c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE))).append("（Camera2 原始/未公开）\n");
        details.append("  有效感光区域: ").append(formatRect(c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE))).append("（Camera2 原始/未公开）\n");
        details.append("  35mm 等效焦距: ").append(formatEquivalentFocal(focalLengths, sensorSize)).append("（计算值，不是真实焦距）\n");
        details.append("  超焦距: ").append(formatDistance(c.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE), "m", "系统未公开")).append("\n");
        details.append("  对焦距离校准: ").append(formatFocusCalibration(c.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION))).append("\n");
        details.append("  滤镜密度: ").append(formatFloatArray(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES), "", "系统未公开")).append("（Camera2 原始）\n");
        details.append("  光圈值: ").append(formatFloatArray(apertures, "f", "系统未公开")).append("\n");
        Float minFocusDiopter = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        details.append("  最小对焦距离: ").append(formatMinimumFocus(minFocusDiopter)).append("\n");
        details.append("  对焦方式: ").append(formatFocusModes(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES))).append("\n");
        details.append("  OIS 光学防抖: ").append(formatOis(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION))).append("\n");
        details.append("  EIS 电子防抖: ").append(formatEis(c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES))).append("\n");

        details.append("  【拍摄能力】\n");
        Size largestVideo = largestSize(videoSizes);
        details.append("  最大视频分辨率: ").append(largestVideo == null ? "系统未公开" : formatSize(largestVideo)).append("\n");
        details.append("  支持的视频尺寸: ").append(formatSizes(videoSizes, 8)).append("\n");
        details.append("  最大视频帧率: ").append(formatFpsRanges(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))).append("（Camera2 目标范围，未绑定具体分辨率）\n");
        details.append("  最大数字变焦: ").append(formatZoom(c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))).append("\n");
        details.append("  JPEG 缩略图尺寸: ").append(formatSizes(c.get(CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES), 6)).append("\n");
        Range<Integer> isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        details.append("  ISO 范围: ").append(isoRange == null ? "系统未公开" : isoRange.getLower() + "–" + isoRange.getUpper()).append("（Camera2 原始）\n");
        Range<Long> exposureRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        details.append("  曝光时间范围: ").append(formatExposureRange(exposureRange)).append("\n");
        int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        details.append("  RAW / DNG: ").append(hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) ? "支持（Camera2 capability）" : "系统未公开或不支持").append("\n");
        details.append("  闪光灯: ").append(formatBoolean(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))).append("\n");
        details.append("  HDR: ").append(formatHdr(c)).append("（硬件/逻辑能力，不代表夜景模式）\n");
        details.append("  人脸检测: ").append(formatFaceDetect(c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES))).append("\n");
        details.append("  最大人脸数量: ").append(formatInteger(c.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT))).append("\n");
        details.append("  AE 区域数: ").append(formatInteger(c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE))).append("\n");
        details.append("  AWB 区域数: ").append(formatInteger(c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB))).append("\n");
        details.append("  AF 区域数: ").append(formatInteger(c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF))).append("\n");
        details.append("  自动曝光 AE: ").append(formatModeSupport(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES), CameraMetadata.CONTROL_AE_MODE_OFF)).append("\n");
        details.append("  自动白平衡 AWB: ").append(formatModeSupport(c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES), CameraMetadata.CONTROL_AWB_MODE_OFF)).append("\n");

        details.append("  【Camera2 硬件能力】\n");
        Integer hardwareLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        details.append("  硬件级别: ").append(hardwareLevel == null ? "系统未公开" : cameraLevel(hardwareLevel)).append("\n");
        details.append("  Manual Sensor: ").append(hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) ? "支持" : "系统未公开或不支持").append("\n");
        details.append("  Manual Exposure: ").append(hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) ? "支持（由 Manual Sensor capability 报告）" : "系统未公开或不支持").append("\n");
        details.append("  Manual Focus: ").append(hasManualFocus(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)) ? "支持" : "系统未公开或不支持").append("\n");
        details.append("  Burst Capture: ").append(hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE) ? "支持" : "系统未公开或不支持").append("\n");
        Integer logicalMulti = reflectedLogicalMultiCameraCapability();
        boolean logical = logicalMulti != null && hasCapability(caps, logicalMulti);
        details.append("  Logical Camera: ").append(logicalMulti == null ? "需要 API 28+ 公开字段" : logical ? "是（Camera2 capability）" : "系统未公开或不是逻辑多摄像头").append("\n");
        details.append("  Physical Camera ID: ").append(formatPhysicalIds(c)).append("\n");
        details.append("  夜景: 系统没有通用 Camera2 夜景硬件字段，不作厂商模式猜测\n");
    }

    private static String formatSize(Size size) {
        return size.getWidth() + " × " + size.getHeight();
    }

    private static String aspectRatio(Size size) {
        int divisor = gcd(size.getWidth(), size.getHeight());
        return (size.getWidth() / divisor) + ":" + (size.getHeight() / divisor);
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int t = a % b; a = b; b = t; }
        return Math.max(1, Math.abs(a));
    }

    private static String formatSizes(Size[] sizes, int limit) {
        if (sizes == null || sizes.length == 0) return "系统未公开";
        List<Size> ordered = new ArrayList<>();
        Collections.addAll(ordered, sizes);
        Collections.sort(ordered, new Comparator<Size>() {
            @Override public int compare(Size a, Size b) {
                long areaA = (long) a.getWidth() * a.getHeight();
                long areaB = (long) b.getWidth() * b.getHeight();
                return areaA == areaB ? 0 : (areaA < areaB ? 1 : -1);
            }
        });
        StringBuilder out = new StringBuilder();
        int count = Math.min(limit, ordered.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append("、");
            Size size = ordered.get(i);
            out.append(formatSize(size)).append("（").append(aspectRatio(size)).append("）");
        }
        if (ordered.size() > count) out.append("；另有 ").append(ordered.size() - count).append(" 项");
        return out.toString();
    }

    private static String inferLensType(float[] focalLengths, Size largestJpeg) {
        if (focalLengths == null || focalLengths.length == 0) return "系统未公开";
        if (focalLengths.length > 1) return "多焦段/组合（由多个焦距推断）";
        float focal = focalLengths[0];
        if (focal <= 3.5f) return "超广角（由焦距推断）";
        if (focal >= 7.0f) return "长焦倾向（由焦距推断）";
        return "广角倾向（由焦距推断）";
    }

    private static String formatFloatArray(float[] values, String unit, String unknown) {
        if (values == null || values.length == 0) return unknown;
        StringBuilder out = new StringBuilder();
        int limit = Math.min(values.length, 4);
        for (int i = 0; i < limit; i++) {
            if (i > 0) out.append("、");
            if ("f".equals(unit)) out.append("f/");
            out.append(String.format(Locale.US, "%.2f", values[i])).append("f".equals(unit) ? "" : unit);
        }
        if (values.length > limit) out.append("（另有 ").append(values.length - limit).append(" 项）");
        return out.toString();
    }

    private static String formatRect(Rect rect) {
        if (rect == null) return "系统未公开";
        return rect.width() + " × " + rect.height() + " px";
    }

    private static String formatSensorSize(SizeF sensorSize) {
        if (sensorSize == null) return "系统未公开";
        return String.format(Locale.US, "%.2f × %.2f mm", sensorSize.getWidth(), sensorSize.getHeight());
    }

    private static String formatEquivalentFocal(float[] focalLengths, SizeF sensorSize) {
        if (focalLengths == null || focalLengths.length == 0 || sensorSize == null) return "无法计算（缺少焦距或传感器尺寸）";
        double sensorDiagonal = Math.sqrt(sensorSize.getWidth() * sensorSize.getWidth() + sensorSize.getHeight() * sensorSize.getHeight());
        double equivalent = focalLengths[0] * 43.266 / sensorDiagonal;
        return String.format(Locale.US, "约 %.1f mm", equivalent);
    }

    private static String formatDistance(Float value, String unit, String unknown) {
        if (value == null || value <= 0) return unknown;
        return String.format(Locale.US, "%.2f %s（Camera2 原始）", value, unit);
    }

    private static String formatFocusCalibration(Integer calibration) {
        if (calibration == null) return "系统未公开";
        switch (calibration) {
            case CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED: return "未校准";
            case CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE: return "近似校准";
            case CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED: return "已校准";
            default: return "模式 " + calibration;
        }
    }

    private static String formatInteger(Integer value) {
        return value == null ? "系统未公开" : String.valueOf(value);
    }

    private static String formatZoom(Float value) {
        if (value == null || value <= 0) return "系统未公开";
        return String.format(Locale.US, "%.2f×（Camera2 原始）", value);
    }

    private static String formatMinimumFocus(Float diopter) {
        if (diopter == null) return "系统未公开";
        if (diopter <= 0) return "固定焦点/无近距离对焦（Camera2 原始）";
        return String.format(Locale.US, "约 %.1f cm（由最小对焦屈光度计算）", 100.0 / diopter);
    }

    private static String formatFocusModes(int[] modes) {
        if (modes == null || modes.length == 0) return "系统未公开";
        StringBuilder out = new StringBuilder();
        for (int mode : modes) {
            if (out.length() > 0) out.append("、");
            switch (mode) {
                case CameraMetadata.CONTROL_AF_MODE_OFF: out.append("Fixed/Manual"); break;
                case CameraMetadata.CONTROL_AF_MODE_AUTO: out.append("AF"); break;
                case CameraMetadata.CONTROL_AF_MODE_MACRO: out.append("Macro AF"); break;
                case CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO: out.append("CAF Video"); break;
                case CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE: out.append("CAF Picture"); break;
                case CameraMetadata.CONTROL_AF_MODE_EDOF: out.append("EDOF"); break;
                default: out.append("模式 ").append(mode);
            }
        }
        return out.toString();
    }

    private static boolean hasManualFocus(int[] modes) {
        if (modes == null) return false;
        for (int mode : modes) if (mode == CameraMetadata.CONTROL_AF_MODE_OFF) return true;
        return false;
    }

    private static String formatOis(int[] modes) {
        if (modes == null || modes.length == 0) return "系统未公开或不支持";
        for (int mode : modes) if (mode != CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF) return "支持（Camera2 metadata）";
        return "系统未公开或不支持";
    }

    private static String formatEis(int[] modes) {
        if (modes == null || modes.length == 0) return "系统未公开或不支持";
        for (int mode : modes) if (mode != CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF) return "支持（Camera2 metadata）";
        return "系统未公开或不支持";
    }

    private static String formatBoolean(Boolean value) {
        return value == null ? "系统未公开" : (value ? "有（Camera2 原始）" : "无（Camera2 原始）");
    }

    private static String formatHdr(CameraCharacteristics c) {
        int[] modes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        if (modes != null) {
            for (int mode : modes) if (mode == CameraMetadata.CONTROL_SCENE_MODE_HDR) return "支持 HDR 场景模式";
        }
        return "系统未公开通用 HDR capability";
    }

    private static String formatFaceDetect(int[] modes) {
        if (modes == null || modes.length == 0) return "系统未公开或不支持";
        StringBuilder out = new StringBuilder();
        for (int mode : modes) {
            if (out.length() > 0) out.append("、");
            if (mode == CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE) out.append("SIMPLE");
            else if (mode == CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL) out.append("FULL");
            else out.append("OFF");
        }
        return out.toString();
    }

    private static String formatModeSupport(int[] modes, int offMode) {
        if (modes == null || modes.length == 0) return "系统未公开";
        for (int mode : modes) if (mode != offMode) return "支持（Camera2 metadata）";
        return "系统未公开或不支持";
    }

    private static String formatExposureRange(Range<Long> range) {
        if (range == null) return "系统未公开";
        return formatExposure(range.getLower()) + " ~ " + formatExposure(range.getUpper());
    }

    private static String formatExposure(Long nanos) {
        if (nanos == null || nanos <= 0) return "系统未公开";
        double seconds = nanos / 1000000000.0;
        if (seconds >= 1.0) return String.format(Locale.US, "%.1fs", seconds);
        return String.format(Locale.US, "1/%.0fs", 1.0 / seconds);
    }

    private static String formatPhysicalIds(CameraCharacteristics c) {
        if (Build.VERSION.SDK_INT < 28) return "需要 API 28+ 公开字段";
        try {
            Object value = CameraCharacteristics.class.getMethod("getPhysicalCameraIds").invoke(c);
            if (!(value instanceof Set)) return "系统未公开";
            Set<?> ids = (Set<?>) value;
            if (ids.isEmpty()) return "无（不是逻辑多摄像头或未公开）";
            return ids.toString();
        } catch (Throwable ignored) {
            return "系统未公开或不可读取";
        }
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
