package com.deviceprobe.inspector;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Copy, share and export flows.
 *
 * <p>schema_version 3 keys every structured fact by its stable English {@code key}
 * instead of its display label, so renaming a label no longer rewrites the document.
 * Each fact keeps the human {@code label} alongside {@code value}, and adds
 * {@code number}/{@code unit} where the value is a single quantity.</p>
 *
 * <p>Rows flagged {@code uiHidden} are still exported: they were removed from the
 * screen, not from the data format. The {@code camera} and {@code sensors} groups come
 * straight from {@link CapabilityModel} through {@link CapabilityJson}: each camera
 * carries {@code id}/{@code facing}/{@code flash}/{@code hardware_level} plus
 * {@code optics}/{@code capture}/{@code hardware} sub-objects; each sensor carries
 * {@code type}/{@code name}/{@code vendor}/{@code version}/{@code max_range}/
 * {@code resolution}/{@code min_delay_us}. No text parsing is involved, so a wording
 * change in the probe can no longer silently reshape the export.</p>
 */
final class ExportUi {
    static final int EXPORT_TEXT = 1;
    static final int EXPORT_JSON = 2;

    private final Ui ui;
    private final ExecutorService executor;
    private final Handler uiHandler;
    private volatile boolean destroyed;

    ExportUi(Ui ui, ExecutorService executor) {
        this.ui = ui;
        this.executor = executor;
        this.uiHandler = new Handler(Looper.getMainLooper());
    }

    void destroy() {
        destroyed = true;
    }

    // ---------------------------------------------------------------- actions

    void copy(String textReport) {
        ClipboardManager clipboard = (ClipboardManager) ui.context().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Device Probe Report", textReport));
            Toast.makeText(ui.context(), "全部报告已复制到剪贴板", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(ui.context(), "剪贴板服务不可用", Toast.LENGTH_SHORT).show();
        }
    }

    void share(String textReport) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "设备探查器报告 · " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
        share.putExtra(Intent.EXTRA_TEXT, textReport);
        ui.context().startActivity(Intent.createChooser(share, "分享设备报告"));
    }

    Intent createExportIntent(int type) {
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType(type == EXPORT_JSON ? "application/json" : "text/plain");
        create.putExtra(Intent.EXTRA_TITLE, "device-probe-" + BuildConfig.VERSION_NAME
                + "." + (type == EXPORT_JSON ? "json" : "txt"));
        return create;
    }

    /** Writes the chosen document on the probe thread; shows a toast when done. */
    void handleExportResult(int exportType, Uri destination, ProbeSnapshot snapshot) {
        executor.execute(() -> {
            try {
                String payload = exportType == EXPORT_JSON ? buildJsonReport(snapshot) : snapshot.textReport;
                try (OutputStream stream = ui.context().getContentResolver().openOutputStream(destination)) {
                    if (stream == null) throw new IllegalStateException("输出流不可用");
                    try (Writer writer = new OutputStreamWriter(stream, "UTF-8")) {
                        writer.write(payload);
                    }
                }
                post(exportType == EXPORT_JSON ? "JSON 报告已导出" : "TXT 报告已导出", true);
            } catch (Exception error) {
                post("导出失败：" + error.getClass().getSimpleName(), false);
            }
        });
    }

    private void post(String message, boolean shortDuration) {
        uiHandler.post(() -> {
            if (destroyed) return;
            Toast.makeText(ui.context(), message, shortDuration ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        });
    }

    // ---------------------------------------------------------------- JSON

    String buildJsonReport(ProbeSnapshot snapshot) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema_version", 3);
            root.put("app_version", BuildConfig.VERSION_NAME);
            root.put("generated_at", now());
            root.put("device", sectionJson(snapshot, SectionBuilder.SECTION_DEVICE));
            root.put("system", systemJson(snapshot));
            root.put("cpu", sectionJson(snapshot, SectionBuilder.SECTION_CPU));
            root.put("gpu", gpuJson(snapshot));
            root.put("vulkan", vulkanJson(snapshot));
            root.put("display", sectionJson(snapshot, SectionBuilder.SECTION_DISPLAY));
            root.put("memory", memoryJson(snapshot, "memory", "内存", "内存类型"));
            root.put("storage", memoryJson(snapshot, "storage", "存储", null));
            root.put("battery", sectionJson(snapshot, SectionBuilder.SECTION_BATTERY));
            root.put("camera", cameraJson(snapshot));
            root.put("sensors", sensorsJson(snapshot));
            root.put("hardware", sectionJson(snapshot, SectionBuilder.SECTION_HARDWARE));
            root.put("security", sectionJson(snapshot, SectionBuilder.SECTION_SECURITY));
            JSONObject raw = new JSONObject();
            raw.put("ndk_kernel", snapshot.nativeResult);
            raw.put("opengl_es", snapshot.gpuResult);
            raw.put("vulkan", snapshot.vulkanResult);
            root.put("raw", raw);
            root.put("data_boundary", "Only public data readable to an ordinary Android app is included. "
                    + "Unknown values are not inferred from a model database.");
            return root.toString(2);
        } catch (JSONException error) {
            return "{\"error\":\"JSON export failed\"}";
        }
    }

    private JSONObject sectionJson(ProbeSnapshot snapshot, String sectionTitle) throws JSONException {
        InfoModel.Section section = snapshot.section(sectionTitle);
        return section == null ? new JSONObject() : sectionJson(section);
    }

    private JSONObject sectionJson(InfoModel.Section section) throws JSONException {
        JSONObject out = new JSONObject();
        for (InfoModel.Group group : section.groups) {
            for (InfoModel.Row row : group.rows) {
                out.put(uniqueKey(out, row.key), fact(row));
            }
        }
        return out;
    }

    /** One exported fact: stable key outside, human label plus optional quantity inside. */
    private static JSONObject fact(InfoModel.Row row) throws JSONException {
        JSONObject fact = new JSONObject();
        boolean unknown = row.source == InfoModel.Source.UNKNOWN;
        fact.put("label", row.label);
        fact.put("value", unknown ? JSONObject.NULL : row.value);
        if (row.number != null && row.unit != null) {
            fact.put("number", row.number.doubleValue());
            fact.put("unit", row.unit);
        }
        fact.put("source", row.source.jsonSource);
        fact.put("confidence", row.source.jsonConfidence);
        return fact;
    }

    /** Keys are unique by construction; the suffix only guards against a future duplicate. */
    private static String uniqueKey(JSONObject out, String key) {
        String candidate = key;
        int suffix = 2;
        while (out.has(candidate)) candidate = key + "_" + suffix++;
        return candidate;
    }

    private JSONObject systemJson(ProbeSnapshot snapshot) throws JSONException {
        InfoModel.Section section = snapshot.section(SectionBuilder.SECTION_DEVICE);
        if (section == null) return new JSONObject();
        JSONObject out = new JSONObject();
        for (InfoModel.Group group : section.groups) {
            if ("身份".equals(group.title)) continue;
            for (InfoModel.Row row : group.rows) {
                out.put(uniqueKey(out, row.key), fact(row));
            }
        }
        return out;
    }

    private JSONObject gpuJson(ProbeSnapshot snapshot) throws JSONException {
        return groupJson(snapshot, SectionBuilder.SECTION_GRAPHICS, "OpenGL ES");
    }

    private JSONObject vulkanJson(ProbeSnapshot snapshot) throws JSONException {
        return groupJson(snapshot, SectionBuilder.SECTION_GRAPHICS, "Vulkan");
    }

    private JSONObject groupJson(ProbeSnapshot snapshot, String sectionTitle, String groupTitle) throws JSONException {
        InfoModel.Section section = snapshot.section(sectionTitle);
        if (section == null) return new JSONObject();
        for (InfoModel.Group group : section.groups) {
            if (groupTitle.equals(group.title)) {
                InfoModel.Section holder = new InfoModel.Section(sectionTitle, "");
                holder.groups.add(group);
                return sectionJson(holder);
            }
        }
        return new JSONObject();
    }

    private JSONObject memoryJson(ProbeSnapshot snapshot, String key, String includeGroup, String secondGroup) throws JSONException {
        InfoModel.Section section = snapshot.section(SectionBuilder.SECTION_MEMORY);
        if (section == null) return new JSONObject();
        InfoModel.Section holder = new InfoModel.Section(key, "");
        for (InfoModel.Group group : section.groups) {
            if (includeGroup.equals(group.title) || (secondGroup != null && secondGroup.equals(group.title))) {
                holder.groups.add(group);
            }
        }
        return sectionJson(holder);
    }

    private JSONObject cameraJson(ProbeSnapshot snapshot) throws JSONException {
        return CapabilityJson.camera(snapshot.capability);
    }

    private JSONObject sensorsJson(ProbeSnapshot snapshot) throws JSONException {
        return CapabilityJson.sensors(snapshot.capability);
    }

    private static String now() {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
                .format(new Date());
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "未知" : value;
    }
}
