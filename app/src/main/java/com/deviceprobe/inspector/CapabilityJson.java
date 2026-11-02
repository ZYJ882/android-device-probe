package com.deviceprobe.inspector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Maps {@link CapabilityModel} to the {@code camera} / {@code sensors} JSON groups.
 *
 * <p>{@code schema_version} 3 keeps the same top-level and item keys as before; the
 * values now come straight from the probe objects instead of a text parser. Fields
 * that the previous version had to drop (anything not in the parser's label table)
 * are included, which only adds keys.</p>
 *
 * <p>No Android imports: the mapping is exercised by a plain JVM unit test.</p>
 */
final class CapabilityJson {
    private CapabilityJson() { }

    static JSONObject camera(CapabilityModel model) throws JSONException {
        if (model == null) return new JSONObject();
        JSONObject out = new JSONObject();
        out.put("count", model.cameras.size());
        out.put("back", model.countFacing("back"));
        out.put("front", model.countFacing("front"));
        out.put("external", model.countFacing("external"));
        out.put("unknown_facing", model.countFacing("unknown"));
        JSONArray items = new JSONArray();
        for (CapabilityModel.Camera camera : model.cameras) items.put(cameraItem(camera));
        out.put("items", items);
        return out;
    }

    static JSONObject sensors(CapabilityModel model) throws JSONException {
        if (model == null) return new JSONObject();
        JSONObject out = new JSONObject();
        out.put("count", model.sensorTotal);
        if (model.dynamicSupported) out.put("dynamic_count", model.dynamicSensors.size());
        JSONArray items = new JSONArray();
        for (CapabilityModel.Sensor sensor : model.sensors) items.put(sensorItem(sensor));
        out.put("items", items);
        return out;
    }

    private static JSONObject cameraItem(CapabilityModel.Camera camera) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("id", camera.id);
        item.put("facing", camera.facing);
        item.put("flash", camera.flashValue());
        if (camera.hardwareLevel != null) item.put("hardware_level", camera.hardwareLevel);
        if (camera.error != null) item.put("error", camera.error);
        if (!camera.readable()) return item;

        if (camera.megaPixels != null) {
            JSONObject optics = new JSONObject();
            optics.put("max_photo_width", camera.photoWidth);
            optics.put("max_photo_height", camera.photoHeight);
            optics.put("max_photo_mp", camera.megaPixels.doubleValue());
            optics.put("aspect_ratio", camera.aspectRatio);
            putFields(optics, camera.optics);
            item.put("optics", optics);
        } else {
            JSONObject optics = new JSONObject();
            putFields(optics, camera.optics);
            if (optics.length() > 0) item.put("optics", optics);
        }
        putBlock(item, "capture", camera.capture);
        putBlock(item, "hardware", camera.hardware);
        return item;
    }

    private static void putBlock(JSONObject item, String key, java.util.List<CapabilityModel.Field> fields)
            throws JSONException {
        JSONObject block = new JSONObject();
        putFields(block, fields);
        if (block.length() > 0) item.put(key, block);
    }

    private static void putFields(JSONObject target, java.util.List<CapabilityModel.Field> fields)
            throws JSONException {
        for (CapabilityModel.Field field : fields) {
            target.put(field.key, field.unknown ? JSONObject.NULL : field.value);
        }
    }

    private static JSONObject sensorItem(CapabilityModel.Sensor sensor) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("index", sensor.index);
        item.put("type", sensor.type);
        item.put("name", sensor.name);
        item.put("vendor", sensor.vendor);
        if (sensor.version != null) item.put("version", sensor.version.intValue());
        if (sensor.maxRange != null) item.put("max_range", sensor.maxRange.doubleValue());
        if (sensor.resolution != null) item.put("resolution", sensor.resolution.doubleValue());
        if (sensor.minDelayUs != null) item.put("min_delay_us", sensor.minDelayUs.intValue());
        return item;
    }
}
