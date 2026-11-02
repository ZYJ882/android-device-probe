package com.deviceprobe.inspector;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Enumerates only Android-public device features, Camera2 metadata and sensors.
 * It deliberately does not infer unavailable hardware from a product model or online database.
 *
 * <p>Results are returned as a {@link CapabilityModel}, not as text. The text block in
 * 「技术原始数据」is rendered from that model by {@link CapabilityModel#renderText()},
 * so the screen, the dialogs and the JSON export all read the same objects.</p>
 */
public final class CapabilityProbe {
    private CapabilityProbe() { }

    public static CapabilityModel collect(Context context, DeviceProfile profile) {
        CapabilityModel model = new CapabilityModel();
        PackageManager pm = context.getPackageManager();
        model.feature("camera_present", "摄像头存在", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)));
        model.feature("camera_flash", "闪光灯", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)));
        model.feature("camera_autofocus", "自动对焦", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)));
        model.feature("touch", "多点触控", touchCapability(pm));
        model.feature("gps", "GPS 定位", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)));
        model.feature("network_location", "网络定位", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK)));
        model.feature("wifi", "Wi-Fi", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_WIFI)));
        model.feature("bluetooth", "蓝牙", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)));
        model.feature("ble", "低功耗蓝牙", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)));
        model.feature("nfc", "NFC", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_NFC)));
        model.feature("usb_host", "USB Host", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)));
        model.feature("ui_rule_tv", "电视遥控器规则", yesNo(profile.isTelevision()));
        model.feature("ui_rule_watch", "手表小屏规则", yesNo(profile.isWatch()));

        collectCameras(model, context);
        collectSensors(model, context);
        return model;
    }

    // ---------------------------------------------------------------- cameras

    private static void collectCameras(CapabilityModel model, Context context) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] ids = manager == null ? null : manager.getCameraIdList();
            if (ids == null || ids.length == 0) return;
            for (String id : ids) {
                CapabilityModel.Camera camera = model.addCamera(id);
                try {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    camera.facing = facingEnum(facing);
                    camera.facingLabel = facingName(facing);
                    camera.flash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    if (level != null) camera.hardwareLevel = cameraLevel(level);
                    fillCameraMetadata(camera, characteristics);
                } catch (RuntimeException error) {
                    camera.error = error.getClass().getSimpleName();
                    camera.facing = "unknown";
                    camera.facingLabel = "方向未公开";
                    camera.flash = null;
                }
            }
        } catch (Throwable error) {
            model.cameraError = error.getClass().getSimpleName();
        }
    }

    private static void fillCameraMetadata(CapabilityModel.Camera camera, CameraCharacteristics c) {
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
        String maxPhoto;
        if (largestJpeg == null) {
            maxPhoto = "系统未公开";
        } else {
            camera.photoWidth = largestJpeg.getWidth();
            camera.photoHeight = largestJpeg.getHeight();
            double megapixels = (double) largestJpeg.getWidth() * largestJpeg.getHeight() / 1000000.0;
            camera.megaPixels = Math.round(megapixels * 10.0) / 10.0;
            camera.aspectRatio = aspectRatio(largestJpeg);
            maxPhoto = formatSize(largestJpeg)
                    + "；有效像素约 " + String.format(Locale.US, "%.1f MP", megapixels)
                    + "；宽高比 " + camera.aspectRatio
                    + "（Camera2 JPEG 输出，非厂商宣传值）";
        }
        camera.optics.add(CapabilityModel.Field.of("max_photo", "最大照片分辨率", maxPhoto));
        camera.optics.add(CapabilityModel.Field.of("photo_sizes", "支持的照片分辨率", formatSizes(jpegSizes, 8)));
        camera.optics.add(CapabilityModel.Field.of("lens_type", "类型",
                inferLensType(focalLengths, largestJpeg) + "（仅为 Camera2 可见字段推断）"));
        camera.optics.add(CapabilityModel.Field.of("focal_lengths", "真实光学焦距",
                formatFloatArray(focalLengths, "mm", "系统未公开")));
        SizeF sensorSize = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        camera.optics.add(CapabilityModel.Field.of("sensor_physical_size", "传感器物理尺寸",
                formatSensorSize(sensorSize) + "（Camera2 原始/未公开）"));
        camera.optics.add(CapabilityModel.Field.of("pixel_array_size", "传感器像素阵列",
                formatSize(c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)) + "（Camera2 原始/未公开）"));
        camera.optics.add(CapabilityModel.Field.of("active_array_size", "有效感光区域",
                formatRect(c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)) + "（Camera2 原始/未公开）"));
        camera.optics.add(CapabilityModel.Field.of("equivalent_focal", "35mm 等效焦距",
                formatEquivalentFocal(focalLengths, sensorSize) + "（计算值，不是真实焦距）"));
        camera.optics.add(CapabilityModel.Field.of("hyperfocal", "超焦距",
                formatDistance(c.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE), "m", "系统未公开")));
        camera.optics.add(CapabilityModel.Field.of("focus_calibration", "对焦距离校准",
                formatFocusCalibration(c.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION))));
        camera.optics.add(CapabilityModel.Field.of("filter_densities", "滤镜密度",
                formatFloatArray(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES), "", "系统未公开")
                        + "（Camera2 原始）"));
        camera.optics.add(CapabilityModel.Field.of("apertures", "光圈值",
                formatFloatArray(apertures, "f", "系统未公开")));
        camera.optics.add(CapabilityModel.Field.of("min_focus_distance", "最小对焦距离",
                formatMinimumFocus(c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE))));
        camera.optics.add(CapabilityModel.Field.of("focus_modes", "对焦方式",
                formatFocusModes(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES))));
        camera.optics.add(CapabilityModel.Field.of("ois", "OIS 光学防抖",
                formatOis(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION))));
        camera.optics.add(CapabilityModel.Field.of("eis", "EIS 电子防抖",
                formatEis(c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES))));

        Size largestVideo = largestSize(videoSizes);
        camera.capture.add(CapabilityModel.Field.of("max_video", "最大视频分辨率",
                largestVideo == null ? "系统未公开" : formatSize(largestVideo)));
        camera.capture.add(CapabilityModel.Field.of("video_sizes", "支持的视频尺寸", formatSizes(videoSizes, 8)));
        camera.capture.add(CapabilityModel.Field.of("max_fps", "最大视频帧率",
                formatFpsRanges(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))
                        + "（Camera2 目标范围，未绑定具体分辨率）"));
        camera.capture.add(CapabilityModel.Field.of("max_zoom", "最大数字变焦",
                formatZoom(c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))));
        camera.capture.add(CapabilityModel.Field.of("thumbnail_sizes", "JPEG 缩略图尺寸",
                formatSizes(c.get(CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES), 6)));
        Range<Integer> isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        camera.capture.add(CapabilityModel.Field.of("iso_range", "ISO 范围",
                (isoRange == null ? "系统未公开" : isoRange.getLower() + "–" + isoRange.getUpper())
                        + "（Camera2 原始）"));
        camera.capture.add(CapabilityModel.Field.of("exposure_range", "曝光时间范围",
                formatExposureRange(c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE))));
        int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        camera.capture.add(CapabilityModel.Field.of("raw", "RAW / DNG",
                hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                        ? "支持（Camera2 capability）" : "系统未公开或不支持"));
        camera.capture.add(CapabilityModel.Field.of("hdr", "HDR",
                formatHdr(c) + "（硬件/逻辑能力，不代表夜景模式）"));
        camera.capture.add(CapabilityModel.Field.of("face_detect", "人脸检测",
                formatFaceDetect(c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES))));
        camera.capture.add(CapabilityModel.Field.of("max_face_count", "最大人脸数量",
                formatInteger(c.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT))));
        camera.capture.add(CapabilityModel.Field.of("ae_regions", "AE 区域数",
                formatInteger(c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE))));
        camera.capture.add(CapabilityModel.Field.of("awb_regions", "AWB 区域数",
                formatInteger(c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB))));
        camera.capture.add(CapabilityModel.Field.of("af_regions", "AF 区域数",
                formatInteger(c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF))));
        camera.capture.add(CapabilityModel.Field.of("auto_exposure", "自动曝光 AE",
                formatModeSupport(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
                        CameraMetadata.CONTROL_AE_MODE_OFF)));
        camera.capture.add(CapabilityModel.Field.of("auto_white_balance", "自动白平衡 AWB",
                formatModeSupport(c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
                        CameraMetadata.CONTROL_AWB_MODE_OFF)));

        camera.hardware.add(CapabilityModel.Field.of("level", "硬件级别",
                camera.hardwareLevel == null ? "系统未公开" : camera.hardwareLevel));
        camera.hardware.add(CapabilityModel.Field.of("manual_sensor", "Manual Sensor",
                hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
                        ? "支持" : "系统未公开或不支持"));
        camera.hardware.add(CapabilityModel.Field.of("manual_exposure", "Manual Exposure",
                hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
                        ? "支持（由 Manual Sensor capability 报告）" : "系统未公开或不支持"));
        camera.hardware.add(CapabilityModel.Field.of("manual_focus", "Manual Focus",
                hasManualFocus(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES))
                        ? "支持" : "系统未公开或不支持"));
        camera.hardware.add(CapabilityModel.Field.of("burst", "Burst Capture",
                hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)
                        ? "支持" : "系统未公开或不支持"));
        Integer logicalMulti = reflectedLogicalMultiCameraCapability();
        boolean logical = logicalMulti != null && hasCapability(caps, logicalMulti);
        camera.hardware.add(CapabilityModel.Field.of("logical", "Logical Camera",
                logicalMulti == null ? "需要 API 28+ 公开字段"
                        : logical ? "是（Camera2 capability）" : "系统未公开或不是逻辑多摄像头"));
        camera.hardware.add(CapabilityModel.Field.of("physical_ids", "Physical Camera ID",
                formatPhysicalIds(c)));
        camera.notes.add("夜景: 系统没有通用 Camera2 夜景硬件字段，不作厂商模式猜测");
    }

    // ---------------------------------------------------------------- sensors

    private static void collectSensors(CapabilityModel model, Context context) {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) {
            model.sensorError = "传感器服务不可用。";
            return;
        }
        List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_ALL);
        model.sensorTotal = sensors.size();
        for (int index = 0; index < sensors.size(); index++) {
            model.sensors.add(toSensor(sensors.get(index), index + 1));
        }
        collectDynamicSensors(model, manager);
    }

    private static CapabilityModel.Sensor toSensor(Sensor sensor, int index) {
        CapabilityModel.Sensor item = new CapabilityModel.Sensor(index, typeName(sensor.getType()));
        item.name = safe(sensor.getName());
        item.vendor = safe(sensor.getVendor());
        item.version = sensor.getVersion();
        item.maxRange = normalize(sensor.getMaximumRange());
        item.resolution = normalize(sensor.getResolution());
        item.minDelayUs = sensor.getMinDelay();
        return item;
    }

    /** Float.toString keeps the shortest round-tripping form, so 19.6f stays 19.6. */
    private static Double normalize(float value) {
        return Double.valueOf(Float.toString(value));
    }

    private static void collectDynamicSensors(CapabilityModel model, SensorManager manager) {
        if (Build.VERSION.SDK_INT < 24) {
            model.dynamicSupported = false;
            return;
        }
        model.dynamicSupported = true;
        final List<Sensor> found = new ArrayList<>();
        SensorManager.DynamicSensorCallback callback = new SensorManager.DynamicSensorCallback() {
            @Override public void onDynamicSensorConnected(Sensor sensor) {
                if (sensor != null) found.add(sensor);
            }
        };
        try {
            manager.registerDynamicSensorCallback(callback);
            SystemClock.sleep(250L);
            manager.unregisterDynamicSensorCallback(callback);
            for (int index = 0; index < found.size(); index++) {
                model.dynamicSensors.add(toSensor(found.get(index), index + 1));
            }
        } catch (RuntimeException error) {
            try { manager.unregisterDynamicSensorCallback(callback); } catch (RuntimeException ignored) { }
            model.dynamicError = error.getClass().getSimpleName();
        }
    }

    // ---------------------------------------------------------------- formatting

    private static String formatSize(Size size) {
        if (size == null) return "系统未公开";
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
        if (focalLengths == null || focalLengths.length == 0 || sensorSize == null) {
            return "无法计算（缺少焦距或传感器尺寸）";
        }
        double sensorDiagonal = Math.sqrt(sensorSize.getWidth() * sensorSize.getWidth()
                + sensorSize.getHeight() * sensorSize.getHeight());
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
        for (int mode : modes) {
            if (mode != CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF) return "支持（Camera2 metadata）";
        }
        return "系统未公开或不支持";
    }

    private static String formatEis(int[] modes) {
        if (modes == null || modes.length == 0) return "系统未公开或不支持";
        for (int mode : modes) {
            if (mode != CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF) return "支持（Camera2 metadata）";
        }
        return "系统未公开或不支持";
    }

    private static String formatHdr(CameraCharacteristics c) {
        int[] modes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        if (modes != null) {
            for (int mode : modes) {
                if (mode == CameraMetadata.CONTROL_SCENE_MODE_HDR) return "支持 HDR 场景模式";
            }
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
        Set<String> ids = c.getPhysicalCameraIds();
        return ids == null || ids.isEmpty() ? "无（不是逻辑多摄像头）" : ids.toString();
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

    private static boolean hasCapability(int[] capabilities, int target) {
        if (capabilities == null) return false;
        for (int capability : capabilities) if (capability == target) return true;
        return false;
    }

    private static Integer reflectedLogicalMultiCameraCapability() {
        return Build.VERSION.SDK_INT < 28
                ? null : CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA;
    }

    private static String facingEnum(Integer facing) {
        if (facing == null) return "unknown";
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) return "front";
        if (facing == CameraCharacteristics.LENS_FACING_BACK) return "back";
        if (Build.VERSION.SDK_INT >= 23 && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) return "external";
        return "unknown";
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

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "未知" : value.replace('\n', ' ').replace('\r', ' ');
    }

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
