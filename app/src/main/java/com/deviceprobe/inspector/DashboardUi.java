package com.deviceprobe.inspector;

import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The upper part of the main screen: top bar, device hero, six core metric cards
 * and the quick-action shortcuts. Values are updated from finished snapshots only,
 * so the dashboard never shows half-collected data.
 */
final class DashboardUi {
    interface Host {
        void onRefreshRequested();

        void onSearchDialogRequested();

        void onReportMenuRequested();

        void onNavigateToSection(String title);

        void onCameraDetailsRequested();

        void onSensorListRequested();

        void onDeviceModeCorrectionRequested();
    }

    private static final String[] METRIC_TITLES = {"CPU", "GPU", "RAM", "存储", "屏幕", "电池"};

    private final Ui ui;
    private final Host host;
    private final String deviceLabel;
    private TextView statusView;
    private TextView heroNameView;
    private TextView heroDetailView;
    private TextView refreshButton;
    private final MetricCard[] metrics = new MetricCard[METRIC_TITLES.length];

    private static final class MetricCard {
        final TextView value;
        final TextView detail;

        MetricCard(TextView value, TextView detail) {
            this.value = value;
            this.detail = detail;
        }
    }

    DashboardUi(Ui ui, Host host, DeviceProfile profile) {
        this.ui = ui;
        this.host = host;
        this.deviceLabel = profile.label();
    }

    // ---------------------------------------------------------------- build

    View buildTopBar() {
        LinearLayout bar = new LinearLayout(ui.context());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        ImageView icon = new ImageView(ui.context());
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setContentDescription("设备探查器图标");
        icon.setBackground(ui.stroked(Ui.SURFACE_SOFT, Ui.HAIRLINE, ui.dp(15), ui.dp(1)));
        icon.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        int iconSize = ui.watch() ? ui.dp(40) : ui.dp(48);
        bar.addView(icon, ui.fixed(iconSize, iconSize));

        LinearLayout textBlock = new LinearLayout(ui.context());
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setPadding(ui.dp(11), 0, ui.dp(4), 0);
        bar.addView(textBlock, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 1f));
        textBlock.addView(ui.text("设备探查器", ui.watch() ? 18 : 21, Ui.TEXT_PRIMARY, true));
        TextView subtitle = ui.text("本机公开硬件信息 · v" + BuildConfig.VERSION_NAME, ui.watch() ? 10 : 12, Ui.TEXT_SECONDARY, false);
        subtitle.setPadding(0, ui.dp(2), 0, 0);
        textBlock.addView(subtitle);

        LinearLayout tools = new LinearLayout(ui.context());
        tools.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(tools, ui.fixed(Ui.wrapContent(), Ui.wrapContent()));
        int toolWidth = ui.watch() ? ui.dp(46) : ui.dp(54);
        int toolGap = ui.watch() ? ui.dp(4) : ui.dp(8);

        TextView search = compactAction("搜索");
        search.setOnClickListener(v -> {
            Motion.tap(v);
            host.onSearchDialogRequested();
        });
        tools.addView(search, ui.fixed(toolWidth, ui.dp(40)));

        refreshButton = compactAction("刷新");
        refreshButton.setOnClickListener(v -> {
            Motion.tap(v);
            host.onRefreshRequested();
        });
        LinearLayout.LayoutParams refreshParams = ui.fixed(toolWidth, ui.dp(40));
        refreshParams.setMargins(toolGap, 0, 0, 0);
        tools.addView(refreshButton, refreshParams);

        TextView report = compactAction("报告");
        report.setOnClickListener(v -> {
            Motion.tap(v);
            host.onReportMenuRequested();
        });
        LinearLayout.LayoutParams reportParams = ui.fixed(toolWidth, ui.dp(40));
        reportParams.setMargins(toolGap, 0, 0, 0);
        tools.addView(report, reportParams);
        return bar;
    }

    private TextView compactAction(String text) {
        TextView button = ui.text(text, ui.watch() ? 10 : 12, Ui.MINT, true);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setFocusableInTouchMode(ui.tv());
        button.setContentDescription(text + "设备信息");
        button.setBackground(ui.interactiveCard(Ui.SURFACE_SOFT, Ui.SURFACE_FOCUS, ui.dp(12)));
        return button;
    }

    View buildHeroCard() {
        LinearLayout hero = new LinearLayout(ui.context());
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(ui.dp(18), ui.dp(17), ui.dp(18), ui.dp(16));
        hero.setBackground(ui.gradient(Ui.SURFACE_HIGH, Ui.SURFACE, ui.dp(22)));
        hero.setElevation(ui.dp(2));

        TextView label = ui.text("当前设备", 11, Ui.MINT, true);
        label.setLetterSpacing(0.08f);
        label.setBackground(ui.rounded(Ui.MINT_TINT, ui.dp(8)));
        label.setPadding(ui.dp(9), ui.dp(3), ui.dp(9), ui.dp(3));
        hero.addView(label, ui.fixed(Ui.wrapContent(), Ui.wrapContent()));

        heroNameView = ui.text("正在读取设备信息…", ui.watch() ? 19 : 25, Ui.TEXT_PRIMARY, true);
        heroNameView.setMaxLines(2);
        heroNameView.setEllipsize(TextUtils.TruncateAt.END);
        heroNameView.setPadding(0, ui.dp(6), 0, 0);
        hero.addView(heroNameView);

        heroDetailView = ui.text("", ui.watch() ? 11 : 13, Ui.TEXT_SECONDARY, false);
        heroDetailView.setLineSpacing(ui.dp(2), 1.1f);
        heroDetailView.setPadding(0, ui.dp(5), 0, 0);
        hero.addView(heroDetailView);

        statusView = ui.text("正在检测…", 12, Ui.MINT, true);
        statusView.setPadding(0, ui.dp(12), 0, 0);
        hero.addView(statusView);
        return hero;
    }

    View buildOverviewGrid() {
        LinearLayout grid = new LinearLayout(ui.context());
        grid.setOrientation(LinearLayout.VERTICAL);
        String[] details = {"逻辑核心", "驱动报告", "系统 API", "数据分区", "逻辑显示", "系统广播"};
        if (ui.watch()) {
            for (int index = 0; index < METRIC_TITLES.length; index++) {
                metrics[index] = fullMetricCard(grid, METRIC_TITLES[index], details[index], index == 0 ? 0 : ui.dp(8));
            }
            return grid;
        }
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(ui.context());
            line.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(line, ui.margins(Ui.matchParent(), Ui.wrapContent(), 0, row == 0 ? 0 : ui.dp(10), 0, 0));
            metrics[row * 2] = metricCard(line, METRIC_TITLES[row * 2], details[row * 2], false);
            metrics[row * 2 + 1] = metricCard(line, METRIC_TITLES[row * 2 + 1], details[row * 2 + 1], true);
        }
        return grid;
    }

    private MetricCard metricCard(LinearLayout parent, String title, String detailText, boolean second) {
        LinearLayout tile = new LinearLayout(ui.context());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(ui.dp(14), ui.dp(13), ui.dp(14), ui.dp(13));
        tile.setBackground(ui.card(Ui.SURFACE, ui.dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(112), 1f);
        if (second) params.setMargins(ui.dp(10), 0, 0, 0);
        parent.addView(tile, params);

        TextView label = ui.text(title, 11, Ui.TEXT_SECONDARY, true);
        label.setLetterSpacing(0.05f);
        tile.addView(label);

        TextView value = ui.text("读取中…", 21, Ui.TEXT_PRIMARY, true);
        value.setMaxLines(2);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(Gravity.BOTTOM);
        tile.addView(value, new LinearLayout.LayoutParams(Ui.matchParent(), 0, 1f));

        TextView detail = ui.text(detailText, 12, Ui.TEXT_SECONDARY, false);
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setPadding(0, ui.dp(4), 0, 0);
        tile.addView(detail);
        return new MetricCard(value, detail);
    }

    private MetricCard fullMetricCard(LinearLayout parent, String title, String detailText, int topMargin) {
        LinearLayout tile = new LinearLayout(ui.context());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(ui.dp(14), ui.dp(11), ui.dp(14), ui.dp(11));
        tile.setBackground(ui.card(Ui.SURFACE, ui.dp(17)));
        parent.addView(tile, ui.margins(Ui.matchParent(), ui.dp(86), 0, topMargin, 0, 0));

        tile.addView(ui.text(title, 11, Ui.TEXT_SECONDARY, true));
        LinearLayout row = new LinearLayout(ui.context());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        tile.addView(row, new LinearLayout.LayoutParams(Ui.matchParent(), 0, 1f));

        TextView value = ui.text("读取中…", 18, Ui.TEXT_PRIMARY, true);
        value.setMaxLines(1);
        value.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(value, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 1f));
        TextView detail = ui.text(detailText, 11, Ui.TEXT_SECONDARY, false);
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        row.addView(detail, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 1f));
        return new MetricCard(value, detail);
    }

    View buildQuickActions() {
        LinearLayout grid = new LinearLayout(ui.context());
        grid.setOrientation(LinearLayout.VERTICAL);
        if (ui.watch()) {
            grid.addView(quickAction("屏幕测试", "显示与触控", v ->
                            ui.context().startActivity(new Intent(ui.context(), ScreenTestActivity.class))),
                    ui.fixed(Ui.matchParent(), ui.dp(54)));
            grid.addView(quickAction("图形 API", "OpenGL ES / Vulkan", v ->
                            host.onNavigateToSection(SectionBuilder.SECTION_GRAPHICS)),
                    ui.margins(Ui.matchParent(), ui.dp(54), 0, ui.dp(8), 0, 0));
            grid.addView(quickAction("硬件能力", "连接与外设声明", v ->
                            host.onNavigateToSection(SectionBuilder.SECTION_HARDWARE)),
                    ui.margins(Ui.matchParent(), ui.dp(54), 0, ui.dp(8), 0, 0));
            return grid;
        }
        addQuickActionRow(grid,
                "屏幕测试", "显示与触控", v ->
                        ui.context().startActivity(new Intent(ui.context(), ScreenTestActivity.class)),
                "图形 API", "OpenGL / Vulkan", v ->
                        host.onNavigateToSection(SectionBuilder.SECTION_GRAPHICS));
        addQuickActionRow(grid,
                "电池", "状态与健康度", v ->
                        host.onNavigateToSection(SectionBuilder.SECTION_BATTERY),
                "传感器", "系统枚举清单", v ->
                        host.onSensorListRequested());
        addQuickActionRow(grid,
                "相机", "Camera2 元数据", v ->
                        host.onCameraDetailsRequested(),
                "硬件能力", "连接与触控", v ->
                        host.onNavigateToSection(SectionBuilder.SECTION_HARDWARE));
        return grid;
    }

    private void addQuickActionRow(LinearLayout parent, String leftTitle, String leftDetail,
                                   View.OnClickListener leftClick, String rightTitle, String rightDetail,
                                   View.OnClickListener rightClick) {
        LinearLayout row = new LinearLayout(ui.context());
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, ui.margins(Ui.matchParent(), ui.dp(62),
                0, parent.getChildCount() == 0 ? 0 : ui.dp(9), 0, 0));
        row.addView(quickAction(leftTitle, leftDetail, leftClick),
                new LinearLayout.LayoutParams(0, Ui.matchParent(), 1f));
        row.addView(new View(ui.context()), ui.fixed(ui.dp(10), ui.dp(1)));
        row.addView(quickAction(rightTitle, rightDetail, rightClick),
                new LinearLayout.LayoutParams(0, Ui.matchParent(), 1f));
    }

    private View quickAction(String title, String detail, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(ui.context());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ui.dp(14), ui.dp(7), ui.dp(12), ui.dp(7));
        item.setClickable(true);
        item.setFocusable(true);
        item.setFocusableInTouchMode(ui.tv());
        item.setBackground(ui.interactiveCard(Ui.SURFACE_SOFT, Ui.SURFACE_PRESSED, ui.dp(16)));
        item.setOnClickListener(v -> {
            Motion.tap(v);
            listener.onClick(v);
        });

        LinearLayout texts = new LinearLayout(ui.context());
        texts.setOrientation(LinearLayout.VERTICAL);
        item.addView(texts, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 1f));
        texts.addView(ui.text(title, 13, Ui.TEXT_PRIMARY, true));
        TextView detailView = ui.text(detail, 11, Ui.TEXT_SECONDARY, false);
        detailView.setPadding(0, ui.dp(2), 0, 0);
        texts.addView(detailView);

        TextView chevron = ui.text("›", 16, Ui.MINT, true);
        chevron.setPadding(ui.dp(6), 0, 0, 0);
        item.addView(chevron, ui.fixed(Ui.wrapContent(), Ui.wrapContent()));
        return item;
    }

    View buildDeviceModeButton(DeviceProfile profile) {
        TextView button = ui.text("纠正设备类型 · 当前：" + profile.label(), 11, Ui.TEXT_PRIMARY, true);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setFocusableInTouchMode(ui.tv());
        button.setBackground(ui.interactiveCard(Ui.SURFACE_SOFT, Ui.SURFACE_PRESSED, ui.dp(15)));
        button.setOnClickListener(v -> {
            Motion.tap(v);
            host.onDeviceModeCorrectionRequested();
        });
        return button;
    }

    // ---------------------------------------------------------------- update

    /** Toggles the refresh button and, when starting, the status line. */
    void setRefreshing(boolean busy) {
        if (refreshButton != null) {
            refreshButton.setEnabled(!busy);
            refreshButton.setText(busy ? "检测中" : "刷新");
            refreshButton.setAlpha(busy ? 0.6f : 1f);
        }
        if (busy && statusView != null) {
            statusView.setText("正在后台读取本机公开信息…");
            Motion.pulse(statusView);
        }
    }

    void applySnapshot(ProbeSnapshot snapshot) {
        heroNameView.setText(safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
        heroDetailView.setText("Android " + safe(Build.VERSION.RELEASE) + " · API " + Build.VERSION.SDK_INT
                + "\n" + deviceLabel + " · " + devicePatchText());

        String cores = snapshot.value(SectionBuilder.SECTION_CPU, "logical_cores", "不可读取");
        metrics[0].value.setText(shortValue(cores.replace(" 个", " 核"), 18));
        metrics[0].detail.setText(shortValue(snapshot.value(SectionBuilder.SECTION_DEVICE, "soc_model", "不可读取"), 22));

        metrics[1].value.setText(shortValue(snapshot.value(SectionBuilder.SECTION_GRAPHICS, "gl_renderer", "不可读取"), 22));
        metrics[1].detail.setText("OpenGL ES 驱动报告");

        metrics[2].value.setText(shortValue(snapshot.value(SectionBuilder.SECTION_MEMORY, "ram_total", "不可读取"), 18));
        String available = snapshot.value(SectionBuilder.SECTION_MEMORY, "ram_available", "不可读取");
        String used = snapshot.value(SectionBuilder.SECTION_MEMORY, "ram_usage", "不可读取");
        metrics[2].detail.setText("可用 " + shortValue(available, 12) + " · 已用 " + shortValue(used, 10));

        metrics[3].value.setText(shortValue(snapshot.value(SectionBuilder.SECTION_MEMORY, "storage_total", "不可读取"), 18));
        metrics[3].detail.setText("可用 " + shortValue(snapshot.value(SectionBuilder.SECTION_MEMORY, "storage_available", "不可读取"), 12));

        metrics[4].value.setText(shortValue(snapshot.value(SectionBuilder.SECTION_DISPLAY, "display_resolution", "不可读取"), 20));
        metrics[4].detail.setText(shortValue(snapshot.value(SectionBuilder.SECTION_DISPLAY, "display_refresh_rate", "不可读取"), 20));

        String charge = snapshot.value(SectionBuilder.SECTION_BATTERY, "battery_level", "不可读取");
        String status = snapshot.value(SectionBuilder.SECTION_BATTERY, "battery_status", "不可读取");
        metrics[5].value.setText(charge);
        metrics[5].detail.setText(shortValue(status, 18));
        metrics[5].value.setTextColor(batteryColor(charge, status));

        Motion.pulse(heroNameView);
        for (MetricCard metric : metrics) {
            if (metric != null) Motion.pulse(metric.value);
        }
    }

    private String devicePatchText() {
        return Build.VERSION.SDK_INT >= 23 ? "安全补丁 " + Build.VERSION.SECURITY_PATCH
                : "系统未公开安全补丁";
    }

    void setStatus(String status) {
        if (statusView != null) {
            statusView.setText(status);
            Motion.pulse(statusView);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "未知" : value;
    }

    private static String shortValue(String value, int max) {
        if (value == null || value.isEmpty()) return "不可读取";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static int batteryColor(String charge, String status) {
        if (status != null && (status.contains("过热") || status.contains("失效"))) return Ui.RED;
        Long value = SectionBuilder.leadingLong(charge);
        if (value == null) return Ui.TEXT_SECONDARY;
        if (value <= 15) return Ui.RED;
        if (value <= 35) return Ui.AMBER;
        return Ui.MINT;
    }
}
