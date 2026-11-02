package com.deviceprobe.inspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Display-ready structured data shared by the dashboard, the section list and the
 * exporters. Sections contain groups, groups contain rows; every row carries its
 * source so the UI and JSON export never have to guess provenance again.
 *
 * <p>Since schema_version 2 every row also carries a stable English {@code key}.
 * The key is the JSON field name; {@code label} is presentation only. Renaming a
 * label therefore never changes the exported document, and duplicate labels can no
 * longer collide on a {@code _2} fallback suffix.</p>
 */
final class InfoModel {
    private InfoModel() { }

    /** Where a fact came from; drives the color dot, the source dialog and JSON fields. */
    enum Source {
        SYSTEM("系统报告", "Android Framework", "reported"),
        DRIVER("驱动报告", "GPU / Vulkan driver", "driver_reported"),
        KERNEL("内核公开值", "/proc or /sys public node", "kernel_reported"),
        CAMERA("Camera2 metadata", "Camera2", "camera2_reported"),
        ESTIMATED("估算值", "calculated from public values", "estimated"),
        UNKNOWN("无法确认", "not published to ordinary apps", "unknown");

        final String label;
        final String jsonSource;
        final String jsonConfidence;

        Source(String label, String jsonSource, String jsonConfidence) {
            this.label = label;
            this.jsonSource = jsonSource;
            this.jsonConfidence = jsonConfidence;
        }
    }

    /**
     * One keyed fact with its display label, source and optional presentation hints.
     *
     * <p>{@code uiHidden} rows stay in the model so the JSON exporter keeps the same
     * keys, but they are skipped by the section UI, the search index and the
     * plain-text report.</p>
     *
     * <p>{@code number} / {@code unit} are set only where the value has a single
     * machine-usable quantity (bytes, mAh, MHz, °C). They spare downstream parsers
     * from re-reading "5.55 GiB"; rows whose value is a range or a list leave both
     * null rather than inventing a number.</p>
     */
    static final class Row {
        final String key;
        final String label;
        final String value;
        final Source source;
        final String sourceNote;
        final boolean prominent;
        final int progress; // 0..100, or -1 when the row has no usage bar
        final boolean uiHidden;
        final Double number; // nullable, raw quantity in {@link #unit}
        final String unit;   // nullable, unit of {@link #number}

        Row(String key, String label, String value, Source source, String sourceNote, boolean prominent,
            int progress, boolean uiHidden, Double number, String unit) {
            this.key = key;
            this.label = label;
            this.value = value;
            this.source = source;
            this.sourceNote = sourceNote;
            this.prominent = prominent;
            this.progress = progress;
            this.uiHidden = uiHidden;
            this.number = number;
            this.unit = unit;
        }
    }

    /** A titled block of rows inside a section. */
    static final class Group {
        final String title; // null keeps the rows without a subheader
        final List<Row> rows = new ArrayList<>();

        Group(String title) { this.title = title; }

        Group row(String key, String label, String value, Source source) {
            rows.add(new Row(key, label, value, source, null, false, -1, false, null, null));
            return this;
        }

        Group row(String key, String label, String value, Source source, String note) {
            rows.add(new Row(key, label, value, source, note, false, -1, false, null, null));
            return this;
        }

        Group prominent(String key, String label, String value, Source source) {
            rows.add(new Row(key, label, value, source, null, true, -1, false, null, null));
            return this;
        }

        Group prominent(String key, String label, String value, Source source, String note) {
            rows.add(new Row(key, label, value, source, note, true, -1, false, null, null));
            return this;
        }

        /** Usage bar row (memory/storage/battery levels). */
        Group meter(String key, String label, String value, int percent, Source source) {
            rows.add(new Row(key, label, value, source, null, true, percent, false, null, null));
            return this;
        }

        /** Row whose value also exposes a single machine-usable quantity. */
        Group qty(String key, String label, Double number, String unit, String value, Source source, String note) {
            rows.add(new Row(key, label, value, source, note, false, -1, false, number, unit));
            return this;
        }

        Group qtyProminent(String key, String label, Double number, String unit, String value, Source source) {
            rows.add(new Row(key, label, value, source, null, true, -1, false, number, unit));
            return this;
        }

        Group qtyProminent(String key, String label, Double number, String unit, String value, Source source,
                           String note) {
            rows.add(new Row(key, label, value, source, note, true, -1, false, number, unit));
            return this;
        }

        /**
         * Keeps the row in the exported report but hides it from the section list, the
         * search index and the plain-text report. Used when a value is no longer worth
         * a visible line but must stay in the JSON schema.
         */
        Group hiddenRow(String key, String label, String value, Source source) {
            rows.add(new Row(key, label, value, source, null, false, -1, true, null, null));
            return this;
        }

        Group hiddenRow(String key, String label, String value, Source source, String note) {
            rows.add(new Row(key, label, value, source, note, false, -1, true, null, null));
            return this;
        }

        boolean hasVisibleRows() {
            for (Row row : rows) {
                if (!row.uiHidden) return true;
            }
            return false;
        }

        Row findByKey(String key) {
            for (Row row : rows) {
                if (row.key.equals(key)) return row;
            }
            return null;
        }
    }

    /** An expandable card of related groups plus a footnote and optional actions. */
    static final class Section {
        static final int ACTION_CAMERA_DETAILS = 1;
        static final int ACTION_SENSOR_LIST = 2;

        final String title;
        final String subtitle;
        final List<Group> groups = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        String footnote;
        String rawBody; // when set, the body renders as selectable monospace text

        Section(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        Group group(String title) {
            Group group = new Group(title);
            groups.add(group);
            return group;
        }

        Section footnote(String value) {
            this.footnote = value;
            return this;
        }

        Section action(int actionId) {
            actions.add(actionId);
            return this;
        }

        Row findByKey(String key) {
            for (Group group : groups) {
                Row row = group.findByKey(key);
                if (row != null) return row;
            }
            return null;
        }

        String value(String key, String fallback) {
            Row row = findByKey(key);
            return row == null ? fallback : row.value;
        }

        /** Everything visible in this section, used by search and the text report. */
        String searchableText() {
            StringBuilder out = new StringBuilder(title).append('\n').append(subtitle).append('\n');
            for (Group group : groups) {
                if (group.title != null) out.append(group.title).append('\n');
                for (Row row : group.rows) {
                    if (row.uiHidden) continue;
                    out.append(row.label).append(": ").append(row.value).append('\n');
                }
            }
            if (rawBody != null) out.append(rawBody).append('\n');
            if (footnote != null) out.append(footnote).append('\n');
            return out.toString();
        }
    }

    /**
     * Renders row values for the text report: "label: value" per line. Hidden rows are
     * skipped so the TXT report reads like the screen; the JSON report keeps them.
     */
    static String toPlainText(Section section) {
        if (section.rawBody != null) return section.rawBody;
        StringBuilder out = new StringBuilder();
        for (Group group : section.groups) {
            for (Row row : group.rows) {
                if (row.uiHidden) continue;
                out.append(row.label).append(": ").append(row.value).append('\n');
            }
        }
        return out.toString();
    }
}
