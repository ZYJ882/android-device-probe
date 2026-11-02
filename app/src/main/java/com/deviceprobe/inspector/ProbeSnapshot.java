package com.deviceprobe.inspector;

import android.content.Context;

import java.util.List;

/**
 * An immutable result of one complete probe round. Everything the UI shows is derived
 * from this snapshot, so the dashboard, sections and exporters can never disagree.
 * Collection happens on a single background thread; the UI thread only swaps the
 * finished snapshot in.
 */
final class ProbeSnapshot {
    final String nativeResult;
    final String gpuResult;
    final String vulkanResult;
    /** Structured capability result; the text block is rendered from it on demand. */
    final CapabilityModel capability;
    final List<InfoModel.Section> sections;
    final String textReport;
    final long collectedAtMillis;

    private ProbeSnapshot(String nativeResult, String gpuResult, String vulkanResult,
                          CapabilityModel capability, List<InfoModel.Section> sections,
                          String textReport, long collectedAtMillis) {
        this.nativeResult = nativeResult;
        this.gpuResult = gpuResult;
        this.vulkanResult = vulkanResult;
        this.capability = capability;
        this.sections = sections;
        this.textReport = textReport;
        this.collectedAtMillis = collectedAtMillis;
    }

    static ProbeSnapshot collect(Context context, DeviceProfile profile) {
        String nativeResult;
        try {
            nativeResult = NativeBridge.collectNativeData();
        } catch (Throwable error) {
            nativeResult = "[NDK / 内核公开接口]\n采集失败: "
                    + error.getClass().getSimpleName() + ": " + safe(error.getMessage()) + "\n";
        }
        String gpuResult = GpuProbe.collect();
        String vulkanResult = VulkanProbe.collect(context);
        CapabilityModel capability = CapabilityProbe.collect(context, profile);
        List<InfoModel.Section> sections = SectionBuilder.buildAll(
                context, profile, nativeResult, gpuResult, vulkanResult, capability);
        return new ProbeSnapshot(nativeResult, gpuResult, vulkanResult, capability,
                sections, buildTextReport(sections), System.currentTimeMillis());
    }

    InfoModel.Section section(String title) {
        for (InfoModel.Section section : sections) {
            if (section.title.equals(title)) return section;
        }
        return null;
    }

    /** Looks a row up by its stable key, never by its display label. */
    String value(String sectionTitle, String key, String fallback) {
        InfoModel.Section section = section(sectionTitle);
        return section == null ? fallback : section.value(key, fallback);
    }

    private static String buildTextReport(List<InfoModel.Section> sections) {
        StringBuilder out = new StringBuilder();
        for (InfoModel.Section section : sections) {
            out.append('[').append(section.title).append("]\n")
                    .append(InfoModel.toPlainText(section));
            if (section.footnote != null) out.append("说明: ").append(section.footnote).append('\n');
            out.append('\n');
        }
        return out.toString().trim();
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "未知" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
