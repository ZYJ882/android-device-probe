package com.deviceprobe.inspector;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the model's derived values and the human text block that the section
 * 「技术原始数据」shows. Pure JVM, no Android dependencies.
 */
public class CapabilityModelTest {

    @Test
    public void megapixelSummaryDeduplicatesAndKeepsOrder() {
        CapabilityModel model = new CapabilityModel();
        addCamera(model, "0", 12.0);
        addCamera(model, "1", 50.0);
        addCamera(model, "2", 12.0);
        assertEquals("12.0 MP、50.0 MP", model.megapixelSummary());
    }

    @Test
    public void megapixelSummaryIsNullWithoutUsableSizes() {
        CapabilityModel model = new CapabilityModel();
        model.addCamera("0");
        assertNull(model.megapixelSummary());
    }

    @Test
    public void facingCountsFollowTheEnumNotTheDisplayText() {
        CapabilityModel model = new CapabilityModel();
        model.addCamera("0").facing = "back";
        model.addCamera("1").facing = "back";
        model.addCamera("2").facing = "front";
        CapabilityModel.Camera broken = model.addCamera("3");
        broken.facing = "unknown";
        assertEquals(2, model.countFacing("back"));
        assertEquals(1, model.countFacing("front"));
        assertEquals(1, model.countFacing("unknown"));
        assertEquals(0, model.countFacing("external"));
    }

    @Test
    public void renderTextDescribesCamerasFromTheSameObjects() {
        CapabilityModel model = new CapabilityModel();
        model.feature("wifi", "Wi-Fi", "支持（系统公开声明）");
        CapabilityModel.Camera camera = model.addCamera("0");
        camera.facing = "back";
        camera.facingLabel = "后置";
        camera.flash = true;
        camera.hardwareLevel = "FULL";
        camera.optics.add(CapabilityModel.Field.of("max_photo", "最大照片分辨率", "4000 × 3000"));
        camera.capture.add(CapabilityModel.Field.of("raw", "RAW / DNG", "支持（Camera2 capability）"));
        camera.notes.add("夜景: 系统没有通用 Camera2 夜景硬件字段，不作厂商模式猜测");

        String text = model.renderText();
        assertTrue(text.startsWith("[硬件能力总览]\nWi-Fi: 支持（系统公开声明）"));
        assertTrue(text.contains("[摄像头公开枚举]"));
        assertTrue(text.contains("系统枚举摄像头: 1 个"));
        assertTrue(text.contains("后置摄像头: 1 个（Camera2 实际枚举）"));
        assertTrue(text.contains("方向未知摄像头: 无"));
        assertTrue(text.contains("cameraId 0: 后置（Camera2 LENS_FACING）"));
        assertTrue(text.contains("  闪光灯: 系统报告可用"));
        assertTrue(text.contains("  【光学参数】\n  最大照片分辨率: 4000 × 3000"));
        assertTrue(text.contains("  【拍摄能力】\n  RAW / DNG: 支持（Camera2 capability）"));
        assertTrue(text.contains("  夜景: 系统没有通用 Camera2 夜景硬件字段"));
        assertTrue(text.contains("真实性说明: "));
    }

    @Test
    public void renderTextMarksUnreadableMetadata() {
        CapabilityModel model = new CapabilityModel();
        CapabilityModel.Camera camera = model.addCamera("3");
        camera.error = "RuntimeException";
        String text = model.renderText();
        assertTrue(text.contains("cameraId 3: metadata 不可读取（RuntimeException）"));
        assertTrue(text.contains("方向未知摄像头: 1 个（metadata 未公开或不可读）"));
        assertFalse(text.contains("【光学参数】"));
    }

    @Test
    public void renderTextTruncatesLongSensorListsButTheModelKeepsThem() {
        CapabilityModel model = new CapabilityModel();
        model.sensorTotal = 66;
        model.dynamicSupported = true;
        for (int index = 1; index <= 66; index++) {
            model.sensors.add(new CapabilityModel.Sensor(index, "加速度计"));
        }
        String text = model.renderText();
        assertTrue(text.contains("系统枚举的传感器数量: 66"));
        assertTrue(text.contains("64. 加速度计"));
        assertFalse(text.contains("65. 加速度计"));
        assertTrue(text.contains("另有 2 项未展开。"));
        assertEquals(66, model.sensors.size());
    }

    @Test
    public void sensorTextKeepsTheShortFloatForm() {
        CapabilityModel.Sensor sensor = new CapabilityModel.Sensor(1, "加速度计");
        sensor.name = "BMI160";
        sensor.vendor = "Bosch";
        sensor.version = 3;
        sensor.maxRange = Double.valueOf(Float.toString(19.6f));
        sensor.resolution = Double.valueOf(Float.toString(0.001f));
        sensor.minDelayUs = 10000;
        String text = sensor.renderText();
        assertTrue(text.contains("最大量程: 19.6；分辨率: 0.001；最小延迟: 10000 µs"));
    }

    @Test
    public void unknownFlagIsDecidedOnceByTheCollectorConvention() {
        assertTrue(CapabilityModel.Field.of("k", "l", "系统未公开").unknown);
        assertTrue(CapabilityModel.Field.of("k", "l", "系统未公开或不支持").unknown);
        assertTrue(CapabilityModel.Field.of("k", "l", "无法计算（缺少焦距或传感器尺寸）").unknown);
        assertFalse(CapabilityModel.Field.of("k", "l", "无（不是逻辑多摄像头）").unknown);
        assertFalse(CapabilityModel.Field.of("k", "l", "需要 API 28+ 公开字段").unknown);
    }

    private static void addCamera(CapabilityModel model, String id, double megapixels) {
        CapabilityModel.Camera camera = model.addCamera(id);
        camera.facing = "back";
        camera.megaPixels = megapixels;
    }
}
