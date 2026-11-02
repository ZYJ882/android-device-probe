package com.deviceprobe.inspector;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the detail sections. Each section is one card; inside, rows are grouped
 * under small subtitles and shown as a clean two-column list (label left, value
 * right) instead of a wall of tiles. Source provenance stays visible as a small
 * colored dot per row; tapping a row explains its origin in a dialog.
 */
final class SectionUi {
    interface Host {
        void onRowSourceRequested(InfoModel.Row row);

        void onSectionAction(int actionId);
    }

    private static final class SectionCard {
        final String title;
        final String searchableText;
        final View root;
        final View content;
        final TextView toggle;
        boolean expanded;

        SectionCard(String title, String searchableText, View root, View content, TextView toggle, boolean expanded) {
            this.title = title;
            this.searchableText = searchableText;
            this.root = root;
            this.content = content;
            this.toggle = toggle;
            this.expanded = expanded;
        }
    }

    /** Long values switch to a stacked layout; this keeps rows scannable. */
    private static final int INLINE_VALUE_LIMIT = 18;

    private final Ui ui;
    private final Host host;
    private final ScrollView scroll;
    private final LinearLayout container;
    private final List<SectionCard> cards = new ArrayList<>();

    SectionUi(Ui ui, Host host, ScrollView scroll, LinearLayout container) {
        this.ui = ui;
        this.host = host;
        this.scroll = scroll;
        this.container = container;
    }

    // ---------------------------------------------------------------- render

    void render(ProbeSnapshot snapshot) {
        container.removeAllViews();
        cards.clear();
        boolean first = true;
        for (InfoModel.Section section : snapshot.sections) {
            addSection(section, first);
            first = false;
        }
    }

    private void addSection(InfoModel.Section section, boolean expandedInitially) {
        LinearLayout card = new LinearLayout(ui.context());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(16), ui.dp(13), ui.dp(16), ui.dp(13));
        card.setBackground(ui.rounded(Ui.SURFACE, ui.dp(20)));
        card.setElevation(ui.dp(1));

        LinearLayout header = new LinearLayout(ui.context());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setClickable(true);
        header.setFocusable(true);
        header.setFocusableInTouchMode(ui.tv());
        header.setPadding(ui.dp(3), ui.dp(2), ui.dp(2), ui.dp(4));
        header.setBackground(ui.interactive(Ui.SURFACE, Ui.SURFACE_SOFT, ui.dp(14)));
        card.addView(header, ui.match());

        LinearLayout labels = new LinearLayout(ui.context());
        labels.setOrientation(LinearLayout.VERTICAL);
        header.addView(labels, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 1f));
        TextView title = ui.text(section.title, 17, Ui.TEXT_PRIMARY, true);
        labels.addView(title);
        TextView subtitle = ui.text(section.subtitle, 12, Ui.TEXT_SECONDARY, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, ui.dp(3), 0, 0);
        labels.addView(subtitle);

        TextView toggle = ui.text(expandedInitially ? "收起  −" : "查看  ›", 12, Ui.MINT, true);
        toggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(toggle, ui.fixed(ui.dp(58), ui.dp(42)));

        LinearLayout content = new LinearLayout(ui.context());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(2), ui.dp(11), ui.dp(2), 0);
        content.setVisibility(expandedInitially ? View.VISIBLE : View.GONE);

        if (section.rawBody != null) {
            content.addView(rawBody(section.rawBody));
        } else {
            appendGroups(content, section);
            appendActions(content, section);
        }
        if (section.footnote != null) content.addView(footnote(section.footnote));
        card.addView(content, ui.match());

        SectionCard sectionCard = new SectionCard(section.title, section.searchableText(),
                card, content, toggle, expandedInitially);
        cards.add(sectionCard);
        header.setOnClickListener(v -> setExpanded(sectionCard, !sectionCard.expanded));
        container.addView(card, ui.margins(Ui.matchParent(), Ui.wrapContent(), 0, 0, 0, ui.dp(11)));
    }

    private void appendGroups(LinearLayout content, InfoModel.Section section) {
        boolean firstGroup = true;
        for (InfoModel.Group group : section.groups) {
            // Groups whose rows are all hidden must not leave an empty subtitle behind.
            if (!group.hasVisibleRows()) continue;
            if (group.title != null) {
                content.addView(groupTitle(group.title), ui.margins(Ui.wrapContent(), Ui.wrapContent(),
                        0, firstGroup ? 0 : ui.dp(12), 0, ui.dp(6)));
            } else if (!firstGroup) {
                content.addView(divider(), ui.margins(Ui.matchParent(), ui.dp(1), 0, ui.dp(12), 0, 0));
            }
            for (InfoModel.Row row : group.rows) {
                if (row.uiHidden) continue;
                content.addView(rowView(row));
            }
            firstGroup = false;
        }
    }

    private void appendActions(LinearLayout content, InfoModel.Section section) {
        for (Integer actionId : section.actions) {
            String label = actionId == InfoModel.Section.ACTION_CAMERA_DETAILS
                    ? "查看 Camera2 详细参数"
                    : "查看传感器完整清单";
            TextView action = ui.text(label + "  ›", 12, Ui.MINT, true);
            action.setGravity(Gravity.CENTER);
            action.setClickable(true);
            action.setFocusable(true);
            action.setFocusableInTouchMode(ui.tv());
            action.setPadding(ui.dp(12), 0, ui.dp(12), 0);
            action.setBackground(ui.interactive(Ui.SURFACE_SOFT, Ui.SURFACE_PRESSED, ui.dp(13)));
            action.setOnClickListener(v -> host.onSectionAction(actionId));
            content.addView(action, ui.margins(Ui.matchParent(), ui.dp(38), 0, ui.dp(12), 0, 0));
        }
    }

    private TextView groupTitle(String title) {
        TextView view = ui.text(title, 10, Ui.MINT, true);
        view.setLetterSpacing(0.06f);
        return view;
    }

    private View divider() {
        View line = new View(ui.context());
        line.setBackgroundColor(Ui.SURFACE_SOFT);
        return line;
    }

    private View rowView(final InfoModel.Row row) {
        LinearLayout rowLayout = new LinearLayout(ui.context());
        rowLayout.setOrientation(LinearLayout.VERTICAL);
        rowLayout.setPadding(0, ui.dp(5), 0, ui.dp(5));
        rowLayout.setClickable(true);
        rowLayout.setFocusable(true);
        rowLayout.setFocusableInTouchMode(ui.tv());
        rowLayout.setBackground(ui.tv() ? ui.interactive(Ui.SURFACE, Ui.SURFACE_SOFT, ui.dp(10)) : null);
        rowLayout.setOnClickListener(v -> host.onRowSourceRequested(row));

        boolean inline = row.value.length() <= INLINE_VALUE_LIMIT;
        if (inline) {
            LinearLayout line = new LinearLayout(ui.context());
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            rowLayout.addView(line, ui.match());
            line.addView(sourceDot(row.source), ui.fixed(ui.dp(7), ui.dp(7)));
            TextView label = ui.text(row.label, 13, Ui.TEXT_SECONDARY, false);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(ui.dp(8), 0, 0, 0);
            line.addView(label, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 1f));
            TextView value = ui.text(row.value, row.prominent ? 16 : 14, Ui.TEXT_PRIMARY, true);
            value.setGravity(Gravity.RIGHT);
            value.setMaxLines(2);
            value.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(value, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 1.3f));
        } else {
            LinearLayout line = new LinearLayout(ui.context());
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            rowLayout.addView(line, ui.match());
            line.addView(sourceDot(row.source), ui.fixed(ui.dp(7), ui.dp(7)));
            TextView label = ui.text(row.label, 13, Ui.TEXT_SECONDARY, false);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(ui.dp(8), 0, 0, 0);
            line.addView(label, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 1f));
            rowLayout.addView(stackedValue(row));
        }

        if (row.progress >= 0) rowLayout.addView(progressBar(row));
        return rowLayout;
    }

    private TextView stackedValue(InfoModel.Row row) {
        TextView value = ui.text(row.value, row.prominent ? 16 : 14, Ui.TEXT_PRIMARY, true);
        value.setPadding(ui.dp(15), ui.dp(3), 0, 0);
        value.setLineSpacing(ui.dp(1), 1.06f);
        return value;
    }

    private View sourceDot(InfoModel.Source source) {
        View dot = new View(ui.context());
        dot.setBackground(ui.rounded(sourceColor(source), ui.dp(4)));
        return dot;
    }

    private TextView rawBody(String text) {
        TextView raw = new TextView(ui.context());
        raw.setText(text);
        raw.setTextColor(Color.rgb(206, 221, 238));
        raw.setTextSize(12);
        raw.setLineSpacing(ui.dp(2), 1.08f);
        raw.setTextIsSelectable(true);
        raw.setTypeface(Typeface.MONOSPACE);
        raw.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        return raw;
    }

    private TextView footnote(String text) {
        TextView note = ui.text("ⓘ " + text, 11, Ui.TEXT_SECONDARY, false);
        note.setLineSpacing(ui.dp(2), 1.15f);
        note.setPadding(0, ui.dp(10), 0, 0);
        return note;
    }

    private View progressBar(InfoModel.Row row) {
        LinearLayout track = new LinearLayout(ui.context());
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(ui.rounded(Ui.TRACK, ui.dp(5)));
        int percent = Math.max(0, Math.min(100, row.progress));
        View fill = new View(ui.context());
        fill.setBackground(ui.rounded(progressColor(percent), ui.dp(5)));
        track.addView(fill, new LinearLayout.LayoutParams(0, ui.dp(7), Math.max(1, percent)));
        View remaining = new View(ui.context());
        track.addView(remaining, new LinearLayout.LayoutParams(0, ui.dp(7), Math.max(1, 100 - percent)));
        track.setLayoutParams(ui.margins(Ui.matchParent(), ui.dp(7), ui.dp(15), ui.dp(5), 0, 0));
        return track;
    }

    private static int progressColor(int percent) {
        if (percent >= 95) return Ui.RED;
        if (percent >= 85) return Ui.AMBER;
        return Ui.MINT;
    }

    private static int sourceColor(InfoModel.Source source) {
        switch (source) {
            case DRIVER: return Color.rgb(90, 174, 218);
            case KERNEL: return Color.rgb(75, 174, 152);
            case CAMERA: return Color.rgb(96, 162, 213);
            case ESTIMATED: return Color.rgb(205, 164, 82);
            case UNKNOWN: return Color.rgb(120, 134, 153);
            default: return Color.rgb(89, 162, 141);
        }
    }

    // ---------------------------------------------------------------- behaviour

    private void setExpanded(SectionCard card, boolean expanded) {
        card.expanded = expanded;
        card.content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        card.toggle.setText(expanded ? "收起  −" : "查看  ›");
    }

    void navigateTo(String title) {
        for (SectionCard card : cards) {
            if (!title.equals(card.title)) continue;
            setExpanded(card, true);
            card.root.post(() -> {
                int[] target = new int[2];
                int[] origin = new int[2];
                card.root.getLocationOnScreen(target);
                scroll.getLocationOnScreen(origin);
                scroll.smoothScrollBy(0, target[1] - origin[1] - ui.dp(12));
            });
            return;
        }
    }

    /** Highlights matching sections, expands them and scrolls to the first hit. */
    int applySearch(String query) {
        String normalized = query.trim().toLowerCase(Locale.US);
        SectionCard first = null;
        int count = 0;
        for (SectionCard card : cards) {
            boolean match = card.searchableText.toLowerCase(Locale.US).contains(normalized);
            card.root.setAlpha(match ? 1f : 0.46f);
            if (!match) continue;
            setExpanded(card, true);
            count++;
            if (first == null) first = card;
        }
        if (first != null) {
            final SectionCard target = first;
            target.root.post(() -> {
                int[] targetLocation = new int[2];
                int[] origin = new int[2];
                target.root.getLocationOnScreen(targetLocation);
                scroll.getLocationOnScreen(origin);
                scroll.smoothScrollBy(0, targetLocation[1] - origin[1] - ui.dp(12));
            });
        }
        return count;
    }

    void clearHighlight() {
        for (SectionCard card : cards) card.root.setAlpha(1f);
    }
}
