package com.deviceprobe.inspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured result of one capability probe round: feature flags, cameras and sensors.
 *
 * <p>This is the single source of truth for the camera/sensor block. The section list,
 * the two detail dialogs, the text report and the JSON export all read these objects,
 * so a change in collection can never make the screen and the export disagree.</p>
 *
 * <p>The class is plain Java (no Android imports) on purpose: {@link #renderText()} and
 * {@link CapabilityJson} are both pure functions over this model, so they can be
 * exercised in a plain JVM unit test without a device or the Android SDK.</p>
 */
final class CapabilityModel {

    static final String TRUTH_STATEMENT =
            "上述能力来自 Android 系统 feature flag、Camera2 和 SensorManager 的实际公开枚举；"
                    + "“支持”表示系统已声明或已枚举，并不等于当前场景下功能必定可用。"
                    + "应用不打开相机、不申请 CAMERA 权限；相机模组、指纹/基带芯片具体型号不会仅凭设备型号猜测。";

    /** The text report prints at most this many sensors; the model keeps all of them. */
    static final int SENSOR_TEXT_LIMIT = 64;

    /** One feature-flag line, e.g. {@code Wi-Fi: 支持（系统公开声明）}. */
    static final class Feature {
        final String key;
        final String label;
        final String value;

        Feature(String key, String label, String value) {
            this.key = key;
            this.label = label;
            this.value = value;
        }
    }

    /**
     * One camera metadata line: stable JSON key, human label and rendered value.
     *
     * <p>{@code unknown} is decided once here instead of being re-sniffed from the
     * string by every consumer. Values that the collector could not obtain start with
     * 「系统未公开」/「无法计算」, which is the only convention this flag relies on.</p>
     */
    static final class Field {
        final String key;
        final String label;
        final String value;
        final boolean unknown;

        Field(String key, String label, String value, boolean unknown) {
            this.key = key;
            this.label = label;
            this.value = value;
            this.unknown = unknown;
        }

        static Field of(String key, String label, String value) {
            String text = value == null || value.isEmpty() ? "系统未公开" : value;
            boolean unknown = text.startsWith("系统未公开") || text.startsWith("无法计算");
            return new Field(key, label, text, unknown);
        }
    }

    /** One enumerated camera. Mutable only while the probe fills it in. */
    static final class Camera {
        final String id;
        String facing = "unknown";
        String facingLabel = "方向未公开";
        Boolean flash;
        String hardwareLevel;
        String error;

        final List<Field> optics = new ArrayList<>();
        final List<Field> capture = new ArrayList<>();
        final List<Field> hardware = new ArrayList<>();
        /** Prose caveats kept for the text report, never exported as data. */
        final List<String> notes = new ArrayList<>();

        Integer photoWidth;
        Integer photoHeight;
        Double megaPixels;
        String aspectRatio;

        Camera(String id) {
            this.id = id;
        }

        boolean readable() {
            return error == null;
        }

        String flashValue() {
            if (flash == null) return "unknown";
            return flash ? "available" : "unavailable";
        }

        String flashText() {
            if (flash == null) return "系统未公开";
            return flash ? "系统报告可用" : "系统报告不可用";
        }

        String renderText() {
            StringBuilder out = new StringBuilder();
            out.append("cameraId ").append(id).append(": ");
            if (error != null) {
                out.append("metadata 不可读取（").append(error).append("）\n");
                return out.toString();
            }
            out.append(facingLabel).append("（Camera2 LENS_FACING）\n");
            if (flash != null) out.append("  闪光灯: ").append(flashText()).append('\n');
            if (hardwareLevel != null) {
                out.append("  Camera2 硬件级别: ").append(hardwareLevel).append('\n');
            }
            appendBlock(out, "光学参数", optics);
            appendBlock(out, "拍摄能力", capture);
            appendBlock(out, "Camera2 硬件能力", hardware);
            for (String note : notes) out.append("  ").append(note).append('\n');
            return out.toString();
        }

        private static void appendBlock(StringBuilder out, String title, List<Field> fields) {
            if (fields.isEmpty()) return;
            out.append("  【").append(title).append("】\n");
            for (Field field : fields) {
                out.append("  ").append(field.label).append(": ").append(field.value).append('\n');
            }
        }
    }

    /** One enumerated sensor (static or dynamic). */
    static final class Sensor {
        final int index;
        final String type;
        String name = "未知";
        String vendor = "未知";
        Integer version;
        Double maxRange;
        Double resolution;
        Integer minDelayUs;

        Sensor(int index, String type) {
            this.index = index;
            this.type = type;
        }

        String renderText() {
            StringBuilder out = new StringBuilder();
            out.append(index).append(". ").append(type).append('\n');
            out.append("   名称: ").append(name).append('\n');
            out.append("   厂商: ").append(vendor).append("；版本: ")
                    .append(version == null ? "未知" : String.valueOf(version)).append('\n');
            out.append("   最大量程: ").append(numberText(maxRange))
                    .append("；分辨率: ").append(numberText(resolution))
                    .append("；最小延迟: ").append(minDelayUs == null ? "未知" : String.valueOf(minDelayUs))
                    .append(" µs\n");
            return out.toString();
        }

        private static String numberText(Double value) {
            return value == null ? "未知" : String.valueOf(value);
        }
    }

    final List<Feature> features = new ArrayList<>();
    final List<Camera> cameras = new ArrayList<>();
    final List<Sensor> sensors = new ArrayList<>();
    final List<Sensor> dynamicSensors = new ArrayList<>();

    /** Non-null when the whole camera enumeration failed, e.g. {@code SecurityException}. */
    String cameraError;
    /** Non-null when the sensor service itself is unavailable. */
    String sensorError;
    /** Reported by SensorManager, including sensors beyond {@link #SENSOR_TEXT_LIMIT}. */
    int sensorTotal;
    /** False when the platform has no dynamic-sensor callback (below API 24). */
    boolean dynamicSupported;
    /** Non-null when registering the dynamic-sensor callback failed. */
    String dynamicError;

    // ---------------------------------------------------------------- accessors

    void feature(String key, String label, String value) {
        features.add(new Feature(key, label, value));
    }

    Camera addCamera(String id) {
        Camera camera = new Camera(id);
        cameras.add(camera);
        return camera;
    }

    int countFacing(String facing) {
        int count = 0;
        for (Camera camera : cameras) if (facing.equals(camera.facing)) count++;
        return count;
    }

    /** "12.5 MP、50.0 MP" from the largest JPEG size of each camera, or null. */
    String megapixelSummary() {
        List<String> values = new ArrayList<>();
        for (Camera camera : cameras) {
            if (camera.megaPixels == null) continue;
            String text = String.format(java.util.Locale.US, "%.1f MP", camera.megaPixels);
            if (!values.contains(text)) values.add(text);
        }
        if (values.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        int limit = Math.min(values.size(), 4);
        for (int index = 0; index < limit; index++) {
            if (index > 0) out.append("、");
            out.append(values.get(index));
        }
        if (values.size() > limit) out.append("；另有 ").append(values.size() - limit).append(" 个镜头");
        return out.toString();
    }

    // ---------------------------------------------------------------- text

    /**
     * The human-readable block kept in 「技术原始数据」. Rendered from the same objects
     * the UI and the JSON use, so it can never drift from them.
     */
    String renderText() {
        StringBuilder out = new StringBuilder();
        out.append("[硬件能力总览]\n");
        for (Feature feature : features) {
            out.append(feature.label).append(": ").append(feature.value).append('\n');
        }

        out.append("\n[摄像头公开枚举]\n");
        if (cameraError != null) {
            out.append("摄像头枚举不可用: ").append(cameraError).append('\n');
        } else if (cameras.isEmpty()) {
            out.append("系统未报告可枚举的摄像头；功能特性见上方。\n");
        } else {
            out.append("系统枚举摄像头: ").append(cameras.size()).append(" 个\n");
            out.append("后置摄像头: ").append(facingCountText("back", "Camera2 实际枚举")).append('\n');
            out.append("前置摄像头: ").append(facingCountText("front", "Camera2 实际枚举")).append('\n');
            out.append("外置摄像头: ").append(facingCountText("external", "Camera2 实际枚举")).append('\n');
            int unknown = countFacing("unknown");
            out.append("方向未知摄像头: ")
                    .append(unknown > 0 ? unknown + " 个（metadata 未公开或不可读）" : "无").append('\n');
            for (Camera camera : cameras) out.append(camera.renderText());
        }

        out.append("\n[传感器清单]\n");
        if (sensorError != null) {
            out.append(sensorError).append('\n');
        } else {
            out.append("系统枚举的传感器数量: ").append(sensorTotal).append('\n');
            if (sensors.isEmpty()) {
                out.append("系统未报告静态传感器。\n");
            } else {
                int limit = Math.min(sensors.size(), SENSOR_TEXT_LIMIT);
                for (int index = 0; index < limit; index++) out.append(sensors.get(index).renderText());
                if (sensors.size() > limit) {
                    out.append("另有 ").append(sensors.size() - limit).append(" 项未展开。\n");
                }
            }
        }
        out.append('\n');
        if (dynamicError != null) {
            out.append("动态传感器: 不可读取（").append(dynamicError).append("）\n");
        } else if (!dynamicSupported) {
            out.append("动态传感器: 需要 API 24+ 公开回调\n");
        } else {
            out.append("动态传感器（API 24+ 回调窗口）: ").append(dynamicSensors.size()).append(" 个\n");
            for (Sensor sensor : dynamicSensors) {
                out.append("- ").append(sensor.type).append(" / ").append(sensor.name)
                        .append(" / ").append(sensor.vendor).append('\n');
            }
        }

        out.append("\n真实性说明: ").append(TRUTH_STATEMENT).append('\n');
        return out.toString();
    }

    private String facingCountText(String facing, String suffix) {
        int count = countFacing(facing);
        return count > 0 ? count + " 个（" + suffix + "）" : "系统未公开或未枚举到";
    }
}
