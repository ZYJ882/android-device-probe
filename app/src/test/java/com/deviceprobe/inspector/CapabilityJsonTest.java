package com.deviceprobe.inspector;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the JSON export is a direct projection of {@link CapabilityModel}.
 *
 * <p>Runs on a plain JVM: the model is built by hand, no device or Android SDK needed.</p>
 */
public class CapabilityJsonTest {

    private static CapabilityModel model() {
        CapabilityModel model = new CapabilityModel();

        CapabilityModel.Camera back = model.addCamera("0");
        back.facing = "back";
        back.facingLabel = "后置";
        back.flash = true;
        back.hardwareLevel = "FULL";
        back.photoWidth = 4000;
        back.photoHeight = 3000;
        back.megaPixels = 12.0;
        back.aspectRatio = "4:3";
        back.optics.add(CapabilityModel.Field.of("max_photo", "最大照片分辨率", "4000 × 3000"));
        back.optics.add(CapabilityModel.Field.of("apertures", "光圈值", "f/1.80"));
        back.optics.add(CapabilityModel.Field.of("hyperfocal", "超焦距", "系统未公开"));
        back.capture.add(CapabilityModel.Field.of("raw", "RAW / DNG", "支持（Camera2 capability）"));
        back.hardware.add(CapabilityModel.Field.of("level", "硬件级别", "FULL"));

        CapabilityModel.Camera broken = model.addCamera("3");
        broken.error = "RuntimeException";

        CapabilityModel.Sensor accelerometer = new CapabilityModel.Sensor(1, "加速度计");
        accelerometer.name = "BMI160 accelerometer";
        accelerometer.vendor = "Bosch";
        accelerometer.version = 3;
        accelerometer.maxRange = 19.6;
        accelerometer.resolution = 0.001;
        accelerometer.minDelayUs = 10000;
        model.sensors.add(accelerometer);
        model.sensorTotal = 42;
        model.dynamicSupported = true;
        model.dynamicSensors.add(new CapabilityModel.Sensor(1, "心率传感器"));
        return model;
    }

    @Test
    public void cameraCountsComeFromTheModelNotFromText() throws Exception {
        JSONObject camera = CapabilityJson.camera(model());
        assertEquals(2, camera.getInt("count"));
        assertEquals(1, camera.getInt("back"));
        assertEquals(0, camera.getInt("front"));
        assertEquals(0, camera.getInt("external"));
        assertEquals(1, camera.getInt("unknown_facing"));
    }

    @Test
    public void cameraItemCarriesStableKeysAndSubBlocks() throws Exception {
        JSONObject item = CapabilityJson.camera(model()).getJSONArray("items").getJSONObject(0);
        assertEquals("0", item.getString("id"));
        assertEquals("back", item.getString("facing"));
        assertEquals("available", item.getString("flash"));
        assertEquals("FULL", item.getString("hardware_level"));

        JSONObject optics = item.getJSONObject("optics");
        assertEquals("4000 × 3000", optics.getString("max_photo"));
        assertEquals("f/1.80", optics.getString("apertures"));
        assertEquals(4000, optics.getInt("max_photo_width"));
        assertEquals(3000, optics.getInt("max_photo_height"));
        assertEquals(12.0, optics.getDouble("max_photo_mp"), 0.001);
        assertEquals("4:3", optics.getString("aspect_ratio"));

        assertEquals("支持（Camera2 capability）",
                item.getJSONObject("capture").getString("raw"));
        assertEquals("FULL", item.getJSONObject("hardware").getString("level"));
    }

    @Test
    public void unavailableFieldsExportAsNullInsteadOfProse() throws Exception {
        JSONObject optics = CapabilityJson.camera(model())
                .getJSONArray("items").getJSONObject(0).getJSONObject("optics");
        assertTrue(optics.isNull("hyperfocal"));
    }

    @Test
    public void unreadableCameraKeepsItsIdentityAndError() throws Exception {
        JSONObject item = CapabilityJson.camera(model()).getJSONArray("items").getJSONObject(1);
        assertEquals("3", item.getString("id"));
        assertEquals("unknown", item.getString("facing"));
        assertEquals("unknown", item.getString("flash"));
        assertEquals("RuntimeException", item.getString("error"));
        assertFalse(item.has("optics"));
    }

    @Test
    public void sensorsExportEveryEnumeratedSensorWithTypedValues() throws Exception {
        JSONObject sensors = CapabilityJson.sensors(model());
        assertEquals(42, sensors.getInt("count"));
        assertEquals(1, sensors.getInt("dynamic_count"));
        JSONArray items = sensors.getJSONArray("items");
        assertEquals(1, items.length());
        JSONObject item = items.getJSONObject(0);
        assertEquals(1, item.getInt("index"));
        assertEquals("加速度计", item.getString("type"));
        assertEquals("BMI160 accelerometer", item.getString("name"));
        assertEquals("Bosch", item.getString("vendor"));
        assertEquals(3, item.getInt("version"));
        assertEquals(19.6, item.getDouble("max_range"), 0.0001);
        assertEquals(0.001, item.getDouble("resolution"), 0.0001);
        assertEquals(10000, item.getInt("min_delay_us"));
    }

    @Test
    public void missingModelExportsAnEmptyObject() throws Exception {
        assertEquals(0, CapabilityJson.camera(null).length());
        assertEquals(0, CapabilityJson.sensors(null).length());
    }
}
