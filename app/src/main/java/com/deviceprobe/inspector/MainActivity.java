package com.deviceprobe.inspector;

import android.app.ActivityManager;
import android.hardware.display.DisplayManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * A modern, zero-permission Android 5.0+ device inspection UI.
 * Details are separated into expandable sections so long kernel text does not
 * overwhelm the overview. Native C/NDK results remain distinct from framework data.
 */
public final class MainActivity extends android.app.Activity {
    private static final int BACKGROUND = Color.rgb(9, 16, 29);
    private static final int SURFACE = Color.rgb(17, 28, 47);
    private static final int SURFACE_SOFT = Color.rgb(22, 37, 59);
    private static final int BORDER = Color.rgb(42, 60, 86);
    private static final int TEXT_PRIMARY = Color.rgb(240, 246, 252);
    private static final int TEXT_SECONDARY = Color.rgb(161, 180, 205);
    private static final int MINT = Color.rgb(57, 216, 196);

    private TextView statusView;
    private TextView heroNameView;
    private TextView heroDetailView;
    private TextView refreshButton;
    private MetricCard cpuMetric;
    private MetricCard gpuMetric;
    private MetricCard memoryMetric;
    private MetricCard storageMetric;
    private MetricCard displayMetric;
    private MetricCard batteryMetric;
    private ScrollView rootScroll;
    private LinearLayout sectionContainer;
    private DeviceProfile deviceProfile;
    private final List<SectionCard> sectionCards = new ArrayList<>();
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean refreshing;
    private volatile boolean destroyed;
    private String fullReport = "";
    private String latestNativeResult = "";
    private String latestGpuResult = "";
    private String latestVulkanResult = "";
    private String latestCapabilityResult = "";
    private String latestDeviceSummary = "";
    private String latestPerformanceSummary = "";
    private String latestGraphicsSummary = "";
    private String latestMemorySummary = "";
    private String latestBatterySummary = "";
    private String latestDisplaySummary = "";
    private static final int EXPORT_TEXT = 1;
    private static final int EXPORT_JSON = 2;

    private static final class MetricCard {
        final TextView value;
        final TextView detail;
        MetricCard(TextView value, TextView detail) { this.value = value; this.detail = detail; }
    }

    private static final class ProbeSnapshot {
        final String nativeResult;
        final String gpuResult;
        final String vulkanResult;
        final String capabilityResult;
        final String deviceSummary;
        final String performanceSummary;
        final String graphicsSummary;
        final String memorySummary;
        final String batterySummary;
        final String displaySummary;
        final String technicalRaw;
        final String copyReport;
        ProbeSnapshot(String nativeResult, String gpuResult, String vulkanResult, String capabilityResult,
                      String deviceSummary, String performanceSummary, String graphicsSummary, String memorySummary,
                      String batterySummary, String displaySummary, String technicalRaw, String copyReport) {
            this.nativeResult = nativeResult;
            this.gpuResult = gpuResult;
            this.vulkanResult = vulkanResult;
            this.capabilityResult = capabilityResult;
            this.deviceSummary = deviceSummary;
            this.performanceSummary = performanceSummary;
            this.graphicsSummary = graphicsSummary;
            this.memorySummary = memorySummary;
            this.batterySummary = batterySummary;
            this.displaySummary = displaySummary;
            this.technicalRaw = technicalRaw;
            this.copyReport = copyReport;
        }
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        deviceProfile = DeviceProfile.detect(this);
        setContentView(createContentView());
        refreshProbe();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        uiHandler.removeCallbacksAndMessages(null);
        probeExecutor.shutdownNow();
        super.onDestroy();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
    }

    private View createContentView() {
        ScrollView scroll = new ScrollView(this);
        rootScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int horizontalInset = deviceProfile.isWatch() ? dp(10) : (deviceProfile.isTelevision() ? dp(38) : dp(18));
        int verticalInset = deviceProfile.isWatch() ? dp(10) : dp(20);
        root.setPadding(horizontalInset, verticalInset, horizontalInset, deviceProfile.isWatch() ? dp(12) : dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(match(), wrap()));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(horizontalInset + bars.left, verticalInset + bars.top,
                    horizontalInset + bars.right,
                    (deviceProfile.isWatch() ? dp(12) : dp(28)) + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(scroll);

        root.addView(createTopBar());
        root.addView(createHeroCard(), marginParams(match(), wrap(), 0, deviceProfile.isWatch() ? dp(10) : dp(16), 0, 0));
        root.addView(sectionTitle("核心硬件", "最常用信息优先；数值来自本机公开接口"),
                marginParams(match(), wrap(), 0, dp(22), 0, dp(10)));
        root.addView(createOverviewGrid());
        root.addView(sectionTitle("快速检测", "直接进入常用项目"),
                marginParams(match(), wrap(), 0, dp(22), 0, dp(10)));
        root.addView(createQuickActions());
        if (deviceProfile.isSuspectedWatch()) {
            root.addView(createDeviceModeButton(), marginParams(match(), dp(44), 0, dp(10), 0, 0));
        }

        root.addView(sectionTitle("详细信息", "点击展开；每项保留数据来源与可信度"),
                marginParams(match(), wrap(), 0, dp(24), 0, dp(10)));
        sectionContainer = new LinearLayout(this);
        sectionContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(sectionContainer, matchParams());

        TextView privacy = new TextView(this);
        privacy.setText("真实性优先：不联网、不申请运行时权限；无法可靠读取的数据会显示“设备未公开”或“无法确认”，不会依据机型猜测。");
        privacy.setTextColor(TEXT_SECONDARY);
        privacy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        privacy.setLineSpacing(dp(2), 1.12f);
        privacy.setPadding(dp(2), dp(16), dp(2), 0);
        root.addView(privacy);
        return scroll;
    }

    private View createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setContentDescription("设备探查器图标");
        icon.setBackground(roundedDrawable(SURFACE_SOFT, dp(15)));
        icon.setPadding(dp(3), dp(3), dp(3), dp(3));
        bar.addView(icon, fixedParams(deviceProfile.isWatch() ? dp(40) : dp(48), deviceProfile.isWatch() ? dp(40) : dp(48)));

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setPadding(dp(11), 0, dp(4), 0);
        bar.addView(textBlock, new LinearLayout.LayoutParams(0, wrap(), 1f));
        TextView title = new TextView(this);
        title.setText("设备探查器");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, deviceProfile.isWatch() ? 18 : 21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textBlock.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("本机公开硬件信息 · v" + BuildConfig.VERSION_NAME);
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, deviceProfile.isWatch() ? 10 : 12);
        subtitle.setPadding(0, dp(2), 0, 0);
        textBlock.addView(subtitle);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(tools, new LinearLayout.LayoutParams(wrap(), wrap()));
        TextView search = compactAction("搜索");
        search.setContentDescription("搜索设备信息");
        search.setOnClickListener(v -> showSearchDialog());
        tools.addView(search, fixedParams(deviceProfile.isWatch() ? dp(46) : dp(54), dp(40)));
        refreshButton = compactAction("刷新");
        refreshButton.setContentDescription("刷新设备信息");
        refreshButton.setOnClickListener(v -> refreshProbe());
        tools.addView(refreshButton, fixedParams(deviceProfile.isWatch() ? dp(46) : dp(54), dp(40)));
        TextView report = compactAction("报告");
        report.setContentDescription("复制、分享或导出设备报告");
        report.setOnClickListener(v -> showReportMenu());
        tools.addView(report, fixedParams(deviceProfile.isWatch() ? dp(46) : dp(54), dp(40)));
        return bar;
    }

    private TextView compactAction(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(MINT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, deviceProfile.isWatch() ? 10 : 12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setFocusableInTouchMode(deviceProfile.isTelevision());
        button.setBackground(deviceProfile.isTelevision()
                ? focusDrawable(SURFACE_SOFT, Color.rgb(38, 73, 88), dp(12))
                : rippleDrawable(SURFACE_SOFT, Color.rgb(38, 73, 88), dp(12)));
        return button;
    }

    private View createHeroCard() {
        LinearLayout hero = card();
        hero.setPadding(dp(18), dp(17), dp(18), dp(16));
        hero.setBackground(roundedDrawable(SURFACE, dp(22)));
        TextView label = label("当前设备", MINT, 11);
        label.setLetterSpacing(0.08f);
        hero.addView(label);
        heroNameView = new TextView(this);
        heroNameView.setText("正在读取设备信息…");
        heroNameView.setTextColor(TEXT_PRIMARY);
        heroNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, deviceProfile.isWatch() ? 19 : 25);
        heroNameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heroNameView.setMaxLines(2);
        heroNameView.setEllipsize(TextUtils.TruncateAt.END);
        heroNameView.setPadding(0, dp(6), 0, 0);
        hero.addView(heroNameView);
        heroDetailView = new TextView(this);
        heroDetailView.setTextColor(TEXT_SECONDARY);
        heroDetailView.setTextSize(TypedValue.COMPLEX_UNIT_SP, deviceProfile.isWatch() ? 11 : 13);
        heroDetailView.setLineSpacing(dp(2), 1.1f);
        heroDetailView.setPadding(0, dp(5), 0, 0);
        hero.addView(heroDetailView);
        statusView = new TextView(this);
        statusView.setTextColor(MINT);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusView.setPadding(0, dp(12), 0, 0);
        statusView.setText("正在检测…");
        hero.addView(statusView);
        return hero;
    }

    private View createOverviewGrid() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        if (deviceProfile.isWatch()) {
            cpuMetric = metricCardFull(grid, "CPU", "读取中…", "逻辑核心", 0);
            gpuMetric = metricCardFull(grid, "GPU", "读取中…", "驱动报告", dp(8));
            memoryMetric = metricCardFull(grid, "RAM", "读取中…", "系统 API", dp(8));
            storageMetric = metricCardFull(grid, "存储", "读取中…", "数据分区", dp(8));
            displayMetric = metricCardFull(grid, "屏幕", "读取中…", "逻辑显示", dp(8));
            batteryMetric = metricCardFull(grid, "电池", "读取中…", "系统广播", dp(8));
            return grid;
        }
        LinearLayout rowOne = overviewRow(grid, 0);
        cpuMetric = metricCard(rowOne, "CPU", "读取中…", "逻辑核心", false);
        gpuMetric = metricCard(rowOne, "GPU", "读取中…", "驱动报告", true);
        LinearLayout rowTwo = overviewRow(grid, dp(10));
        memoryMetric = metricCard(rowTwo, "RAM", "读取中…", "系统 API", false);
        storageMetric = metricCard(rowTwo, "存储", "读取中…", "数据分区", true);
        LinearLayout rowThree = overviewRow(grid, dp(10));
        displayMetric = metricCard(rowThree, "屏幕", "读取中…", "逻辑显示", false);
        batteryMetric = metricCard(rowThree, "电池", "读取中…", "系统广播", true);
        return grid;
    }

    private MetricCard metricCard(LinearLayout parent, String labelText, String initialValue, String initialDetail, boolean second) {
        LinearLayout tile = card();
        tile.setPadding(dp(14), dp(13), dp(14), dp(13));
        tile.setBackground(roundedDrawable(SURFACE, dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(112), 1f);
        if (second) params.setMargins(dp(10), 0, 0, 0);
        parent.addView(tile, params);
        TextView label = label(labelText, TEXT_SECONDARY, 11);
        label.setLetterSpacing(0.05f);
        tile.addView(label);
        TextView value = new TextView(this);
        value.setText(initialValue);
        value.setTextColor(TEXT_PRIMARY);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setMaxLines(2);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(Gravity.BOTTOM);
        tile.addView(value, new LinearLayout.LayoutParams(match(), 0, 1f));
        TextView detail = new TextView(this);
        detail.setText(initialDetail);
        detail.setTextColor(TEXT_SECONDARY);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setPadding(0, dp(4), 0, 0);
        tile.addView(detail);
        return new MetricCard(value, detail);
    }

    private MetricCard metricCardFull(LinearLayout parent, String labelText, String initialValue, String initialDetail, int topMargin) {
        LinearLayout tile = card();
        tile.setPadding(dp(14), dp(11), dp(14), dp(11));
        tile.setBackground(roundedDrawable(SURFACE, dp(17)));
        parent.addView(tile, marginParams(match(), dp(86), 0, topMargin, 0, 0));
        TextView label = label(labelText, TEXT_SECONDARY, 11);
        tile.addView(label);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        tile.addView(row, new LinearLayout.LayoutParams(match(), 0, 1f));
        TextView value = new TextView(this);
        value.setText(initialValue);
        value.setTextColor(TEXT_PRIMARY);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setMaxLines(1);
        value.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(value, new LinearLayout.LayoutParams(0, wrap(), 1f));
        TextView detail = new TextView(this);
        detail.setText(initialDetail);
        detail.setTextColor(TEXT_SECONDARY);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        row.addView(detail, new LinearLayout.LayoutParams(0, wrap(), 1f));
        return new MetricCard(value, detail);
    }

    private LinearLayout overviewRow(LinearLayout parent, int topMargin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, marginParams(match(), wrap(), 0, topMargin, 0, 0));
        return row;
    }

    private View createQuickActions() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        if (deviceProfile.isWatch()) {
            grid.addView(quickAction("屏幕测试", "14 项显示与触控图案", v -> startActivity(new Intent(this, ScreenTestActivity.class))),
                    fixedParams(match(), dp(54)));
            grid.addView(quickAction("图形 API", "OpenGL ES 与 Vulkan", v -> navigateToSection("图形 API")),
                    marginParams(match(), dp(54), 0, dp(8), 0, 0));
            grid.addView(quickAction("硬件能力", "传感器与相机公开信息", v -> navigateToSection("硬件能力")),
                    marginParams(match(), dp(54), 0, dp(8), 0, 0));
            return grid;
        }
        addQuickActionRow(grid,
                "屏幕测试", "显示与触控", v -> startActivity(new Intent(this, ScreenTestActivity.class)),
                "图形 API", "OpenGL / Vulkan", v -> navigateToSection("图形 API"));
        addQuickActionRow(grid,
                "电池", "状态与健康度", v -> navigateToSection("电池"),
                "传感器", "系统枚举列表", v -> showSensorDialog());
        addQuickActionRow(grid,
                "相机", "Camera2 元数据", v -> showCameraDialog(),
                "硬件能力", "连接与触控", v -> navigateToSection("硬件能力"));
        return grid;
    }

    private void addQuickActionRow(LinearLayout parent, String leftTitle, String leftDetail, View.OnClickListener leftClick,
                                   String rightTitle, String rightDetail, View.OnClickListener rightClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, marginParams(match(), dp(62), 0, parent.getChildCount() == 0 ? 0 : dp(9), 0, 0));
        TextView left = quickAction(leftTitle, leftDetail, leftClick);
        row.addView(left, new LinearLayout.LayoutParams(0, match(), 1f));
        View gap = new View(this);
        row.addView(gap, fixedParams(dp(10), dp(1)));
        TextView right = quickAction(rightTitle, rightDetail, rightClick);
        row.addView(right, new LinearLayout.LayoutParams(0, match(), 1f));
    }

    private TextView quickAction(String title, String detail, View.OnClickListener listener) {
        TextView item = new TextView(this);
        item.setText(title + "\n" + detail + "  ›");
        item.setTextColor(TEXT_PRIMARY);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        item.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setLineSpacing(dp(2), 1.0f);
        item.setPadding(dp(14), dp(7), dp(12), dp(7));
        item.setClickable(true);
        item.setFocusable(true);
        item.setFocusableInTouchMode(deviceProfile.isTelevision());
        item.setBackground(deviceProfile.isTelevision()
                ? focusDrawable(SURFACE_SOFT, Color.rgb(36, 72, 85), dp(16))
                : rippleDrawable(SURFACE_SOFT, Color.rgb(36, 72, 85), dp(16)));
        item.setOnClickListener(listener);
        return item;
    }

    private TextView createDeviceModeButton() {
        TextView button = actionButton("纠正设备类型 · 当前：" + deviceProfile.label(), false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        button.setOnClickListener(v -> chooseDeviceMode());
        return button;
    }

    private void chooseDeviceMode() {
        final String[] modes = {"自动识别（推荐）", "按手机界面", "按平板界面", "按电视界面", "按手表界面"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("纠正设备类型")
                .setMessage("仅保存本应用的界面偏好，不会修改系统。自动结果和判定依据会保留在设备详情中。")
                .setSingleChoiceItems(modes, deviceProfile.selectedModeIndex(), (dialog, selected) -> {
                    DeviceProfile.Kind override = null;
                    if (selected == 1) override = DeviceProfile.Kind.PHONE;
                    else if (selected == 2) override = DeviceProfile.Kind.TABLET;
                    else if (selected == 3) override = DeviceProfile.Kind.TELEVISION;
                    else if (selected == 4) override = DeviceProfile.Kind.WATCH;
                    DeviceProfile.saveOverride(this, override);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private TextView actionButton(String text, boolean primary) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setFocusableInTouchMode(deviceProfile.isTelevision());
        button.setTextColor(primary ? BACKGROUND : TEXT_PRIMARY);
        button.setBackground(deviceProfile.isTelevision()
                ? focusDrawable(primary ? MINT : SURFACE_SOFT, primary ? Color.rgb(136, 245, 228) : Color.rgb(45, 83, 111), dp(15))
                : rippleDrawable(primary ? MINT : SURFACE_SOFT, primary ? Color.rgb(125, 236, 220) : Color.rgb(48, 69, 101), dp(15)));
        return button;
    }

    private void renderSections(ProbeSnapshot snapshot) {
        sectionContainer.removeAllViews();
        sectionCards.clear();
        addExpandableSection("设备与系统", "型号、系统版本、设备类型与安全补丁", snapshot.deviceSummary, true, false);
        addExpandableSection("性能与芯片", "CPU 核心、频率、ABI 与 GPU 摘要", snapshot.performanceSummary, false, false);
        addExpandableSection("图形 API", "OpenGL ES 与 Vulkan 驱动实际报告", snapshot.graphicsSummary, false, false);
        addExpandableSection("内存与存储", "总内存、可用内存、DDR 依据与数据分区", snapshot.memorySummary, false, false);
        addExpandableSection("电池", "电量、充电、温度、电压和电量计公开值", snapshot.batterySummary, false, false);
        addExpandableSection("屏幕", "分辨率、刷新率、HDR、广色域与测试入口", snapshot.displaySummary, false, false);
        addExpandableSection("硬件能力", "Camera2、传感器、触控、定位和连接能力", buildHardwareOverview(snapshot.capabilityResult), false, false);
        addExpandableSection("技术原始数据", "NDK、内核节点、GPU 与 Vulkan 原文；仅供核验", snapshot.technicalRaw, false, true);
    }

    private void addExpandableSection(String titleText, String subtitleText, String bodyText,
                                      boolean expandedInitially, boolean monospace) {
        final LinearLayout section = card();
        section.setPadding(dp(14), dp(12), dp(14), dp(12));
        section.setBackground(roundedDrawable(SURFACE, dp(20)));
        section.setFocusable(true);
        section.setTag(titleText);

        final LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setClickable(true);
        header.setFocusable(true);
        header.setFocusableInTouchMode(deviceProfile.isTelevision());
        header.setPadding(dp(3), dp(2), dp(2), dp(4));
        header.setBackground(deviceProfile.isTelevision()
                ? focusDrawable(SURFACE, Color.rgb(34, 70, 84), dp(14))
                : rippleDrawable(SURFACE, Color.rgb(34, 70, 84), dp(14)));
        section.addView(header, matchParams());

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        header.addView(labels, new LinearLayout.LayoutParams(0, wrap(), 1f));
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, dp(3), 0, 0);
        labels.addView(subtitle);

        final TextView toggle = new TextView(this);
        toggle.setText(expandedInitially ? "收起  −" : "查看  ›");
        toggle.setTextColor(MINT);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        toggle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(toggle, fixedParams(dp(58), dp(42)));

        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(11), 0, 0);
        content.setVisibility(expandedInitially ? View.VISIBLE : View.GONE);
        if (monospace) {
            TextView raw = new TextView(this);
            raw.setText(bodyText);
            raw.setTextColor(Color.rgb(206, 221, 238));
            raw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            raw.setLineSpacing(dp(2), 1.08f);
            raw.setTextIsSelectable(true);
            raw.setTypeface(Typeface.MONOSPACE);
            raw.setPadding(dp(4), dp(4), dp(4), dp(4));
            content.addView(raw, matchParams());
        } else {
            if ("电池".equals(titleText)) {
                content.addView(createUsageBar("当前电量", firstLineValue(bodyText, "当前电量:")), marginParams(match(), wrap(), 0, 0, 0, dp(10)));
            } else if ("内存与存储".equals(titleText)) {
                content.addView(createUsageBar("内存使用", firstLineValue(bodyText, "内存使用:")), marginParams(match(), wrap(), 0, 0, 0, dp(8)));
                content.addView(createUsageBar("存储使用", firstLineValue(bodyText, "存储使用:")), marginParams(match(), wrap(), 0, 0, 0, dp(10)));
            }
            addKeyValueGrid(content, bodyText);
            if ("硬件能力".equals(titleText)) {
                TextView detailAction = actionButton("查看完整 Camera2 / 传感器清单  ›", false);
                detailAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                detailAction.setOnClickListener(v -> showLongTextDialog("硬件能力原始枚举", latestCapabilityResult));
                content.addView(detailAction, marginParams(match(), dp(42), 0, dp(10), 0, 0));
            }
        }
        section.addView(content, matchParams());

        String searchableText = titleText + "\n" + subtitleText + "\n" + bodyText;
        if ("硬件能力".equals(titleText)) searchableText += "\n" + latestCapabilityResult;
        final SectionCard card = new SectionCard(titleText, searchableText,
                section, content, toggle, expandedInitially);
        sectionCards.add(card);
        header.setOnClickListener(v -> setSectionExpanded(card, !card.expanded));
        sectionContainer.addView(section, marginParams(match(), wrap(), 0, 0, 0, dp(11)));
    }

    private void addKeyValueGrid(LinearLayout parent, String bodyText) {
        List<String[]> pairs = parseKeyValueLines(bodyText);
        if (pairs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("系统未提供可结构化的信息。请查看技术原始数据。\n" + safe(bodyText));
            empty.setTextColor(TEXT_SECONDARY);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            parent.addView(empty);
            return;
        }
        boolean singleColumn = deviceProfile.isWatch();
        for (int index = 0; index < pairs.size(); ) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            parent.addView(row, marginParams(match(), wrap(), 0, index == 0 ? 0 : dp(8), 0, 0));
            String[] first = pairs.get(index++);
            row.addView(keyValueTile(first[0], first[1]), new LinearLayout.LayoutParams(0, wrap(), 1f));
            if (!singleColumn && index < pairs.size()) {
                View gap = new View(this);
                row.addView(gap, fixedParams(dp(8), dp(1)));
                String[] second = pairs.get(index++);
                row.addView(keyValueTile(second[0], second[1]), new LinearLayout.LayoutParams(0, wrap(), 1f));
            }
        }
    }

    private View createUsageBar(String label, String value) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        TextView caption = new TextView(this);
        caption.setText(label + "  " + (value == null ? "不可读取" : value));
        caption.setTextColor(TEXT_SECONDARY);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        holder.addView(caption);
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(roundedDrawable(Color.rgb(31, 47, 70), dp(5)));
        track.setPadding(0, 0, 0, 0);
        Long parsedPercent = leadingLong(value);
        int percent = progressPercent(value);
        View fill = new View(this);
        fill.setBackground(roundedDrawable(progressColor(percent), dp(5)));
        View remaining = new View(this);
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(7), parsedPercent == null ? 0 : Math.max(1, percent)));
        track.addView(remaining, new LinearLayout.LayoutParams(0, dp(7), parsedPercent == null ? 1 : Math.max(1, 100 - percent)));
        holder.addView(track, marginParams(match(), dp(7), 0, dp(6), 0, 0));
        return holder;
    }

    private static int progressPercent(String value) {
        Long number = leadingLong(value);
        if (number == null) return 0;
        return (int) Math.max(0, Math.min(100, number));
    }

    private static int progressColor(int percent) {
        if (percent >= 95) return Color.rgb(214, 101, 101);
        if (percent >= 85) return Color.rgb(205, 164, 82);
        return MINT;
    }

    private List<String[]> parseKeyValueLines(String text) {
        List<String[]> pairs = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return pairs;
        String[] lines = text.split("\\n");
        String pending = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("[")) continue;
            int separator = trimmed.indexOf(": ");
            if (separator <= 0) separator = trimmed.indexOf('：');
            if (separator > 0) {
                if (pending != null) {
                    pairs.add(new String[]{"说明", pending});
                    pending = null;
                }
                pairs.add(new String[]{trimmed.substring(0, separator).trim(), trimmed.substring(separator + (trimmed.charAt(separator) == '：' ? 1 : 2)).trim()});
            } else if (pending == null) {
                pending = trimmed;
            } else {
                pending += " " + trimmed;
            }
        }
        if (pending != null) pairs.add(new String[]{"说明", pending});
        return pairs;
    }

    private View keyValueTile(String key, String value) {
        LinearLayout tile = card();
        tile.setPadding(dp(11), dp(10), dp(11), dp(9));
        tile.setBackground(roundedDrawable(SURFACE_SOFT, dp(14)));
        TextView label = new TextView(this);
        label.setText(key);
        label.setTextColor(TEXT_SECONDARY);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(label);
        TextView main = new TextView(this);
        main.setText(value);
        main.setTextColor(TEXT_PRIMARY);
        main.setTextSize(TypedValue.COMPLEX_UNIT_SP, isPrimaryMetric(key) ? 17 : 14);
        main.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        main.setLineSpacing(dp(1), 1.06f);
        main.setMaxLines(key.equals("说明") ? 4 : 3);
        main.setEllipsize(TextUtils.TruncateAt.END);
        main.setPadding(0, dp(4), 0, 0);
        tile.addView(main);
        TextView badge = sourceBadge(key, value);
        badge.setClickable(true);
        badge.setFocusable(true);
        badge.setOnClickListener(v -> showDataSourceDialog(key, value));
        tile.addView(badge, marginParams(wrap(), wrap(), 0, dp(7), 0, 0));
        return tile;
    }

    private boolean isPrimaryMetric(String key) {
        return key.contains("总内存") || key.contains("可用内存") || key.contains("存储总量")
                || key.contains("可用空间") || key.contains("当前电量") || key.equals("GPU")
                || key.contains("屏幕分辨率") || key.contains("逻辑核心");
    }

    private void showDataSourceDialog(String key, String value) {
        String source = sourceFor(key, value, "Android Framework");
        String confidence = isUnknownValue(value) ? "unknown" : confidenceFor(key, value, "reported");
        String detail;
        if (source.contains("OpenGL")) {
            detail = "实际离屏 EGL/OpenGL ES 上下文：GL_VENDOR、GL_RENDERER、GL_VERSION 或 GLSL 查询结果。";
        } else if (source.contains("Vulkan")) {
            detail = "Android framework Vulkan feature 与 C/NDK 动态 libvulkan.so loader 枚举结果分开采集。";
        } else if (source.contains("Battery")) {
            detail = "电量、状态、温度与电压来自 ACTION_BATTERY_CHANGED / BatteryManager；厂商、型号与电量计字段仅在 /sys/class/power_supply 对普通应用公开时显示。";
        } else if (source.contains("Camera2")) {
            detail = "来自 Camera2 CameraCharacteristics 公开 metadata；应用不打开相机，也不申请 CAMERA 权限。";
        } else if (source.contains("/proc")) {
            detail = "来自 C/NDK 读取的普通应用可访问 /proc 或 /sys 节点；具体原文与读取失败原因位于“技术原始数据”。";
        } else if (source.contains("Display")) {
            detail = "来自 Android Display / DisplayManager 公开 API；广色域与 HDR 是系统报告能力，不等同于面板物理参数。";
        } else {
            detail = "来自 Android framework 的当前公开信息；不会根据机型数据库补全未知字段。";
        }
        showLongTextDialog("数据来源 · " + key,
                "当前值：\n" + safe(value) + "\n\n来源：\n" + source
                        + "\n\n可信度：\n" + confidence + "\n\n说明：\n" + detail);
    }

    private TextView sourceBadge(String key, String value) {
        String tag;
        int color;
        if (value.contains("无法") || value.contains("未公开") || value.contains("不可读取") || value.contains("未提供")) {
            tag = "无法确认";
            color = Color.rgb(120, 134, 153);
        } else if (value.contains("估算") || value.contains("计算值") || value.contains("推断") || key.contains("估算")) {
            tag = value.contains("推断") ? "推断" : "计算值";
            color = Color.rgb(205, 164, 82);
        } else if (key.contains("GPU") || key.contains("OpenGL") || key.contains("Vulkan")) {
            tag = "驱动报告";
            color = Color.rgb(90, 174, 218);
        } else if (key.contains("DDR") || key.contains("内核") || key.contains("频率") || key.contains("厂商")) {
            tag = "内核公开值";
            color = Color.rgb(75, 174, 152);
        } else if (isCameraField(key)) {
            tag = "Camera2 metadata";
            color = Color.rgb(96, 162, 213);
        } else {
            tag = "系统报告";
            color = Color.rgb(89, 162, 141);
        }
        TextView badge = new TextView(this);
        badge.setText(tag);
        badge.setTextColor(color);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setPadding(dp(7), dp(3), dp(7), dp(3));
        badge.setBackground(roundedDrawable(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)), dp(9)));
        return badge;
    }

    private void setSectionExpanded(SectionCard card, boolean expanded) {
        card.expanded = expanded;
        card.content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        card.toggle.setText(expanded ? "收起  −" : "查看  ›");
    }

    private void navigateToSection(String title) {
        for (SectionCard card : sectionCards) {
            if (!title.equals(card.title)) continue;
            setSectionExpanded(card, true);
            card.root.post(() -> {
                int[] target = new int[2];
                int[] origin = new int[2];
                card.root.getLocationOnScreen(target);
                rootScroll.getLocationOnScreen(origin);
                rootScroll.smoothScrollBy(0, target[1] - origin[1] - dp(12));
            });
            return;
        }
    }

    private View sectionTitle(String titleText, String detailText) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        holder.addView(title);
        TextView detail = new TextView(this);
        detail.setText(detailText);
        detail.setTextColor(TEXT_SECONDARY);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setPadding(0, dp(3), 0, 0);
        holder.addView(detail);
        return holder;
    }

    private LinearLayout card() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setElevation(dp(1));
        return result;
    }

    private Drawable focusDrawable(int normalColor, int focusedColor, int radius) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[]{android.R.attr.state_focused}, strokedDrawable(focusedColor, MINT, radius, 2));
        result.addState(new int[]{android.R.attr.state_pressed}, roundedDrawable(focusedColor, radius));
        result.addState(new int[]{}, roundedDrawable(normalColor, radius));
        return result;
    }

    private TextView label(String value, int color, float sizeSp) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextColor(color);
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }

    private void refreshProbe() {
        if (destroyed || refreshing) return;
        refreshing = true;
        if (refreshButton != null) {
            refreshButton.setEnabled(false);
            refreshButton.setText("检测中");
        }
        statusView.setText("正在后台读取本机公开信息…");
        probeExecutor.execute(() -> {
            try {
                String nativeResult;
                try {
                    nativeResult = NativeBridge.collectNativeData();
                } catch (Throwable error) {
                    nativeResult = "[NDK / 内核公开接口]\n采集失败: "
                            + error.getClass().getSimpleName() + ": " + safe(error.getMessage()) + "\n";
                }
                String gpuResult = GpuProbe.collect();
                String vulkanResult = VulkanProbe.collect(this);
                String capabilityResult = CapabilityProbe.collect(this, deviceProfile);
                String deviceSummary = buildDeviceSummary();
                String performanceSummary = buildPerformanceHardwareSummary(nativeResult, gpuResult);
                String graphicsSummary = buildGraphicsApiSummary(gpuResult, vulkanResult);
                String memorySummary = buildMemorySummary(nativeResult);
                Intent batterySnapshot = batteryIntent();
                String batterySummary = buildBatterySummary(nativeResult, batterySnapshot);
                String displaySummary = buildDisplaySummary();
                String technicalRaw = buildTechnicalRaw(nativeResult, gpuResult, vulkanResult);
                String copyReport = buildCopyReport(deviceSummary, performanceSummary, graphicsSummary, memorySummary,
                        batterySummary, displaySummary, capabilityResult, technicalRaw);
                ProbeSnapshot snapshot = new ProbeSnapshot(nativeResult, gpuResult, vulkanResult, capabilityResult,
                        deviceSummary, performanceSummary, graphicsSummary, memorySummary, batterySummary,
                        displaySummary, technicalRaw, copyReport);
                uiHandler.post(() -> applySnapshot(snapshot));
            } catch (Exception error) {
                uiHandler.post(() -> {
                    if (!destroyed) finishRefreshWithError(error);
                });
            }
        });
    }

    private void applySnapshot(ProbeSnapshot snapshot) {
        if (destroyed || isFinishing()) return;
        latestNativeResult = snapshot.nativeResult;
        latestGpuResult = snapshot.gpuResult;
        latestVulkanResult = snapshot.vulkanResult;
        latestCapabilityResult = snapshot.capabilityResult;
        latestDeviceSummary = snapshot.deviceSummary;
        latestPerformanceSummary = snapshot.performanceSummary;
        latestGraphicsSummary = snapshot.graphicsSummary;
        latestMemorySummary = snapshot.memorySummary;
        latestBatterySummary = snapshot.batterySummary;
        latestDisplaySummary = snapshot.displaySummary;
        fullReport = snapshot.copyReport;
        updateOverview(snapshot);
        renderSections(snapshot);
        refreshing = false;
        if (refreshButton != null) {
            refreshButton.setEnabled(true);
            refreshButton.setText("刷新");
        }
        statusView.setText("刚刚更新 · " + DateFormat.getTimeInstance(DateFormat.MEDIUM,
                Locale.getDefault()).format(new Date()));
    }

    private void finishRefreshWithError(Throwable error) {
        if (destroyed || isFinishing()) return;
        refreshing = false;
        if (refreshButton != null) {
            refreshButton.setEnabled(true);
            refreshButton.setText("刷新");
        }
        statusView.setText("检测未完成 · " + error.getClass().getSimpleName());
        Toast.makeText(getApplicationContext(), "设备信息读取未完成，请稍后重试", Toast.LENGTH_SHORT).show();
    }

    private void showSearchDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("例如：GPU、Vulkan、DDR、电池、温度、摄像头、传感器");
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_SECONDARY);
        input.setSelectAllOnFocus(false);
        input.setPadding(dp(18), dp(8), dp(18), dp(8));
        new android.app.AlertDialog.Builder(this)
                .setTitle("搜索设备信息")
                .setMessage("仅搜索本次已经采集的本机数据，不联网、不查询机型资料。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("搜索", (dialog, which) -> searchSections(input.getText().toString()))
                .show();
    }

    private void searchSections(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            Toast.makeText(getApplicationContext(), "请输入要搜索的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        SectionCard first = null;
        int count = 0;
        for (SectionCard card : sectionCards) {
            boolean match = card.searchableText.toLowerCase(Locale.US).contains(normalized);
            card.root.setAlpha(match ? 1f : 0.46f);
            if (!match) continue;
            setSectionExpanded(card, true);
            count++;
            if (first == null) first = card;
        }
        if (first == null) {
            Toast.makeText(getApplicationContext(), "未在当前已采集数据中找到“" + query.trim() + "”", Toast.LENGTH_LONG).show();
            return;
        }
        final SectionCard target = first;
        target.root.post(() -> {
            int[] targetLocation = new int[2];
            int[] origin = new int[2];
            target.root.getLocationOnScreen(targetLocation);
            rootScroll.getLocationOnScreen(origin);
            rootScroll.smoothScrollBy(0, targetLocation[1] - origin[1] - dp(12));
        });
        Toast.makeText(getApplicationContext(), "找到 " + count + " 个相关模块；已展开第一个结果", Toast.LENGTH_SHORT).show();
    }

    private void clearSearchHighlight() {
        for (SectionCard card : sectionCards) card.root.setAlpha(1f);
    }

    private void showReportMenu() {
        if (TextUtils.isEmpty(fullReport)) {
            Toast.makeText(getApplicationContext(), "正在读取信息，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] actions = {"复制全部信息", "分享设备报告", "导出 TXT", "导出结构化 JSON", "清除搜索高亮"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("设备报告")
                .setItems(actions, (dialog, index) -> {
                    switch (index) {
                        case 0: copyReport(); break;
                        case 1: shareReport(); break;
                        case 2: requestExport(EXPORT_TEXT); break;
                        case 3: requestExport(EXPORT_JSON); break;
                        default: clearSearchHighlight(); break;
                    }
                })
                .show();
    }

    private void shareReport() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "设备探查器报告 · " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
        share.putExtra(Intent.EXTRA_TEXT, fullReport);
        startActivity(Intent.createChooser(share, "分享设备报告"));
    }

    private void requestExport(int type) {
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType(type == EXPORT_JSON ? "application/json" : "text/plain");
        create.putExtra(Intent.EXTRA_TITLE, "device-probe-" + BuildConfig.VERSION_NAME + "." + (type == EXPORT_JSON ? "json" : "txt"));
        startActivityForResult(create, type);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (destroyed) return;
        if ((requestCode != EXPORT_TEXT && requestCode != EXPORT_JSON) || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final int exportType = requestCode;
        final android.net.Uri destination = data.getData();
        final String textSnapshot = fullReport;
        probeExecutor.execute(() -> {
            try {
                String payload = exportType == EXPORT_JSON ? buildStructuredJsonReport() : textSnapshot;
                try (OutputStream stream = getContentResolver().openOutputStream(destination)) {
                    if (stream == null) throw new IllegalStateException("输出流不可用");
                    try (Writer writer = new OutputStreamWriter(stream, "UTF-8")) {
                        writer.write(payload);
                    }
                }
                uiHandler.post(() -> {
                    if (!destroyed) Toast.makeText(getApplicationContext(), exportType == EXPORT_JSON ? "JSON 报告已导出" : "TXT 报告已导出", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                uiHandler.post(() -> {
                    if (!destroyed) Toast.makeText(getApplicationContext(), "导出失败：" + error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String buildStructuredJsonReport() {
        try {
            JSONObject root = new JSONObject();
            root.put("schema_version", 1);
            root.put("app_version", BuildConfig.VERSION_NAME);
            root.put("generated_at", now());
            root.put("device", sectionJson(latestDeviceSummary, "Android Framework", "reported"));
            root.put("system", sectionJson(filterKeyValueLines(latestDeviceSummary, "系统", "安全补丁", "设备类型"), "Android Framework", "reported"));
            root.put("cpu", sectionJson(filterKeyValueLines(latestPerformanceSummary, "CPU", "逻辑核心", "ABI"), "/proc and /sys", "reported"));
            root.put("gpu", sectionJson(latestGraphicsSummary, "OpenGL ES Driver", "driver_reported"));
            root.put("vulkan", sectionJson(latestVulkanResult, "Vulkan Driver / Android Framework", "driver_reported"));
            root.put("display", sectionJson(latestDisplaySummary, "Android Display API", "reported"));
            root.put("memory", sectionJson(filterKeyValueLines(latestMemorySummary, "内存", "DDR", "当前状态"), "Android Framework and Linux public nodes", "reported"));
            root.put("storage", sectionJson(filterKeyValueLines(latestMemorySummary, "存储", "可用空间"), "StatFs data partition", "reported"));
            root.put("battery", sectionJson(latestBatterySummary, "Battery broadcast and power_supply", "reported"));
            root.put("camera", sectionJson(extractTextSection(latestCapabilityResult, "[摄像头公开枚举]", "[传感器清单]"), "Camera2", "reported"));
            root.put("sensors", sectionJson(extractTextSection(latestCapabilityResult, "[传感器清单]", "真实性说明:"), "SensorManager", "reported"));
            root.put("hardware", sectionJson(buildHardwareOverview(latestCapabilityResult), "PackageManager, Camera2 and SensorManager", "reported"));
            JSONObject raw = new JSONObject();
            raw.put("ndk_kernel", latestNativeResult);
            raw.put("opengl_es", latestGpuResult);
            raw.put("vulkan", latestVulkanResult);
            root.put("raw", raw);
            root.put("data_boundary", "Only public data readable to an ordinary Android app is included. Unknown values are not inferred from a model database.");
            return root.toString(2);
        } catch (JSONException error) {
            return "{\"error\":\"JSON export failed\"}";
        }
    }

    private static String filterKeyValueLines(String text, String... keyStarts) {
        if (TextUtils.isEmpty(text)) return "";
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            int split = trimmed.indexOf(": ");
            if (split <= 0) split = trimmed.indexOf('：');
            if (split <= 0) continue;
            String key = trimmed.substring(0, split).trim();
            for (String keyStart : keyStarts) {
                if (key.startsWith(keyStart)) {
                    out.append(trimmed).append('\n');
                    break;
                }
            }
        }
        return out.toString();
    }

    private static String extractTextSection(String text, String startMarker, String endMarker) {
        if (TextUtils.isEmpty(text)) return "";
        int start = text.indexOf(startMarker);
        if (start < 0) return "";
        int startLine = text.indexOf('\n', start);
        if (startLine < 0) return "";
        int end = text.indexOf(endMarker, startLine + 1);
        return text.substring(startLine + 1, end >= 0 ? end : text.length()).trim();
    }

    private JSONObject sectionJson(String text, String defaultSource, String defaultConfidence) throws JSONException {
        JSONObject section = new JSONObject();
        if (TextUtils.isEmpty(text)) return section;
        String[] lines = text.split("\\n");
        int duplicate = 2;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("[")) continue;
            int split = trimmed.indexOf(": ");
            if (split <= 0) split = trimmed.indexOf('：');
            if (split <= 0) continue;
            String key = trimmed.substring(0, split).trim();
            String value = trimmed.substring(split + (trimmed.charAt(split) == '：' ? 1 : 2)).trim();
            while (section.has(key)) key = key + "_" + duplicate++;
            JSONObject fact = new JSONObject();
            boolean unknown = isUnknownValue(value);
            fact.put("value", unknown ? JSONObject.NULL : value);
            fact.put("source", sourceFor(key, value, defaultSource));
            fact.put("confidence", unknown ? "unknown" : confidenceFor(key, value, defaultConfidence));
            section.put(key, fact);
        }
        return section;
    }

    private static boolean isUnknownValue(String value) {
        String lower = safe(value).toLowerCase(Locale.US);
        return lower.contains("不可读取") || lower.contains("未公开") || lower.contains("未提供")
                || lower.contains("无法确认") || lower.contains("未知") || lower.contains("不可用");
    }

    private static boolean isCameraField(String key) {
        return key.contains("Camera") || key.contains("camera") || key.contains("摄像头")
                || key.contains("照片") || key.contains("视频") || key.contains("焦距")
                || key.contains("光圈") || key.contains("对焦") || key.contains("传感器物理")
                || key.contains("ISO") || key.contains("曝光") || key.contains("防抖")
                || key.contains("RAW") || key.contains("HDR") || key.contains("人脸")
                || key.contains("Logical") || key.contains("Physical") || key.contains("硬件级别")
                || key.contains("Manual") || key.contains("Burst") || key.contains("自动曝光")
                || key.contains("自动白平衡") || key.contains("闪光灯");
    }

    private static String sourceFor(String key, String value, String fallback) {
        String combined = key + " " + value;
        if ((isCameraField(key) && !key.contains("HDR")) || combined.contains("Camera2") || combined.contains("cameraId")) return "Camera2 metadata";
        if (combined.contains("OpenGL") || combined.contains("GL_")) return "OpenGL ES Driver";
        if (combined.contains("Vulkan")) return "Vulkan Driver / Android Framework";
        if (combined.contains("power_supply") || combined.contains("电量计")) return "Battery power_supply";
        if (combined.contains("DDR") || combined.contains("内核") || combined.contains("CPU ")) return "/proc or /sys public node";
        if (combined.contains("屏幕") || combined.contains("HDR") || combined.contains("广色域")) return "Android Display API";
        return fallback;
    }

    private static String confidenceFor(String key, String value, String fallback) {
        String combined = key + " " + value;
        if (combined.contains("估算") || combined.contains("计算值") || combined.contains("推断") || combined.contains("推算")) return "estimated";
        if (combined.contains("系统报告") || combined.contains("系统声明")) return "system_reported";
        if (combined.contains("驱动")) return "driver_reported";
        if (combined.contains("内核") || combined.contains("power_supply")) return "kernel_reported";
        if ((isCameraField(key) && !key.contains("HDR")) || combined.contains("Camera2")) return "camera2_reported";
        return fallback;
    }

    private String buildHardwareOverview(String capabilityResult) {
        StringBuilder out = new StringBuilder();
        if (TextUtils.isEmpty(capabilityResult)) {
            append(out, "硬件能力", "尚未完成采集");
            return out.toString();
        }
        int cameraSection = capabilityResult.indexOf("[摄像头公开枚举]");
        String overview = cameraSection >= 0 ? capabilityResult.substring(0, cameraSection) : capabilityResult;
        for (String line : overview.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("[")) continue;
            out.append(trimmed).append('\n');
        }
        String cameraCount = firstLineValue(capabilityResult, "系统枚举摄像头:");
        String sensorCount = firstLineValue(capabilityResult, "系统枚举的传感器数量:");
        append(out, "Camera2 已枚举", cameraCount == null ? "系统未公开或不可读" : cameraCount);
        append(out, "传感器", sensorCount == null ? "系统未公开或不可读" : sensorCount + "（点击下方查看完整清单）");
        append(out, "数据来源", "PackageManager、Camera2、SensorManager；完整逐项信息可展开查看");
        return out.toString();
    }

    private void showCameraDialog() {
        String camera = extractTextSection(latestCapabilityResult, "[摄像头公开枚举]", "[传感器清单]");
        if (TextUtils.isEmpty(camera)) {
            showLongTextDialog("Camera2 公开信息", "尚未完成采集，请等待刷新结束后重试。");
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(10), dp(14), dp(14));
        String[] cameras = camera.split("(?=cameraId )");
        for (String block : cameras) {
            if (!TextUtils.isEmpty(block.trim())) addCameraDetailCard(list, block.trim());
        }
        scroll.addView(list);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Camera2 详细参数")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void addCameraDetailCard(LinearLayout list, String block) {
        String[] lines = block.split("\\n");
        String cameraTitle = lines.length == 0 ? "摄像头" : lines[0].trim();
        StringBuilder basic = new StringBuilder();
        List<String> sectionTitles = new ArrayList<>();
        List<StringBuilder> sectionBodies = new ArrayList<>();
        StringBuilder current = basic;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("【") && trimmed.endsWith("】")) {
                String sectionTitle = trimmed.substring(1, trimmed.length() - 1);
                sectionTitles.add(sectionTitle);
                current = new StringBuilder();
                sectionBodies.add(current);
            } else if (!trimmed.isEmpty()) {
                current.append(trimmed).append('\n');
            }
        }
        addCameraLayer(list, "基本信息 · " + cameraTitle, basic.toString());
        for (int index = 0; index < sectionTitles.size(); index++) {
            addCameraLayer(list, sectionTitles.get(index), sectionBodies.get(index).toString());
        }
    }

    private void addCameraLayer(LinearLayout list, String title, String body) {
        LinearLayout panel = card();
        panel.setPadding(dp(13), dp(12), dp(13), dp(12));
        panel.setBackground(roundedDrawable(SURFACE, dp(14)));
        TextView heading = label(title, TEXT_PRIMARY, 14);
        panel.addView(heading, marginParams(match(), wrap(), 0, 0, 0, dp(7)));
        addKeyValueGrid(panel, body);
        list.addView(panel, marginParams(match(), wrap(), 0, 0, 0, dp(10)));
    }

    private void showSensorDialog() {
        String sensors = extractTextSection(latestCapabilityResult, "[传感器清单]", "真实性说明:");
        showLongTextDialog("传感器公开清单", TextUtils.isEmpty(sensors) ? "尚未完成采集，请等待刷新结束后重试。" : sensors);
    }

    private void showLongTextDialog(String title, String text) {
        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(TextUtils.isEmpty(text) ? "尚未完成采集" : text);
        body.setTextColor(TEXT_PRIMARY);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        body.setLineSpacing(dp(2), 1.08f);
        body.setTextIsSelectable(true);
        body.setPadding(dp(18), dp(12), dp(18), dp(18));
        scroll.addView(body);
        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private String buildDeviceSummary() {
        StringBuilder out = new StringBuilder();
        append(out, "设备", safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
        append(out, "系统", "Android " + safe(Build.VERSION.RELEASE) + "（API " + Build.VERSION.SDK_INT + "）");
        append(out, "安全补丁", Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : "当前系统未提供");
        append(out, "ROM 构建者", buildValue(Build.USER));
        append(out, "ROM 构建主机", buildValue(Build.HOST));
        append(out, "ROM 构建类型", buildValue(Build.TYPE));
        append(out, "设备类型", deviceProfile.label());
        append(out, "设备代号", Build.DEVICE);
        if (deviceProfile.isSuspectedWatch() || deviceProfile.manualOverride) {
            append(out, "判定依据", deviceProfile.evidence);
            append(out, "手动纠正", "首页已提供纠正按钮，仅影响本应用布局。");
        }
        return out.toString();
    }

    private String buildDisplaySummary() {
        StringBuilder out = new StringBuilder();
        append(out, "读取时间", now());
        Display display = currentDisplay();
        if (display == null) {
            append(out, "屏幕信息", "不可读取");
            return out.toString();
        }
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        append(out, "屏幕分辨率", metrics.widthPixels + " × " + metrics.heightPixels + " 像素");
        append(out, "屏幕密度", metrics.densityDpi + " dpi（系统显示密度）");
        append(out, "尺寸估算", estimatedDiagonal(metrics));
        append(out, "当前刷新率", String.format(Locale.US, "%.2f Hz", display.getRefreshRate()));
        append(out, "广色域（系统检测）", wideColorGamutCompat(display));
        append(out, "HDR（系统检测）", hdrTypesCompat(display));
        append(out, "10-bit 显示", tenBitCapabilityCompat(display));
        append(out, "说明", "广色域和 HDR 为系统报告能力；色域图案仅供观察，不能替代仪器实测。面板厂商与型号未公开时不会推测。");
        return out.toString();
    }

    private String buildPerformanceHardwareSummary(String nativeResult, String gpuResult) {
        StringBuilder out = new StringBuilder();
        append(out, "Binder 位数", binderBitness());
        append(out, "Android 系统位数", androidSystemBitness());
        append(out, "当前应用进程位数", processBitness());
        append(out, "当前进程架构", processArchitecture());
        append(out, "当前进程 ABI", nativeValue(nativeResult, "native ABI"));
        append(out, "当前系统 ABI", currentSystemAbi());
        append(out, "系统架构", systemArchitecture());
        append(out, "CPU 架构", nativeValue(nativeResult, "uname.machine"));
        append(out, "CPU ABI", currentSystemAbi());
        append(out, "系统 API", "Android API " + Build.VERSION.SDK_INT + "（" + safe(Build.VERSION.RELEASE) + "）");
        append(out, "逻辑核心", nativeValue(nativeResult, "POSIX 在线逻辑 CPU") + " 个");
        String cpuRaw = nativeSection(nativeResult, "[CPU 深度原始节点]", "[内核导出原文");
        appendCpuFrequencyRows(out, "当前频率", cpuKHz(indexedCpuValue(cpuRaw, "scaling_cur_freq_khz")));
        appendCpuFrequencyRows(out, "最高频率", cpuKHz(indexedCpuValue(cpuRaw, "cpuinfo_max_freq_khz")));
        append(out, "GPU", gpuValue(gpuResult, "GL_RENDERER"));
        append(out, "GPU 厂商", gpuValue(gpuResult, "GL_VENDOR"));
        append(out, "图形接口", gpuValue(gpuResult, "GL_VERSION"));
        append(out, "设备首选 ABI", firstAbi(Build.SUPPORTED_ABIS));
        append(out, "设备支持 ABI", join(Build.SUPPORTED_ABIS));
        append(out, "支持的 32 位 CPU ABI", joinOrUnavailable(Build.SUPPORTED_32_BIT_ABIS));
        append(out, "支持的 64 位 CPU ABI", joinOrUnavailable(Build.SUPPORTED_64_BIT_ABIS));
        append(out, "应用支持 ABI", appSupportedAbis());
        append(out, "应用安装推荐 ABI", recommendedAbi());
        append(out, "应用正在使用", nativeValue(nativeResult, "native ABI"));
        append(out, "说明", "系统架构只显示系统首选运行 ABI 对应的架构；设备支持 ABI 是独立列表，不与系统架构合并。CPU 频率只显示系统实际公开结果；DDR 内存类型请在“内存与存储”中查看。完整驱动/原始数据请展开“技术原始数据”。");
        return out.toString();
    }

    private static String indexedCpuValue(String raw, String field) {
        if (TextUtils.isEmpty(raw) || raw.startsWith("未找到") || raw.startsWith("该采集段")) return "设备未公开或当前应用不可读";
        String marker = "/" + field + ":";
        StringBuilder out = new StringBuilder();
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            int markerIndex = trimmed.indexOf(marker);
            if (markerIndex < 0) continue;
            String cpu = trimmed.substring(0, markerIndex);
            String value = trimmed.substring(markerIndex + marker.length()).trim();
            if (TextUtils.isEmpty(value)) continue;
            if (out.length() > 0) out.append("；");
            out.append(cpu).append("=").append(value);
            if (out.length() > 460) {
                out.append("；已截断");
                break;
            }
        }
        return out.length() == 0 ? "设备未公开或当前应用不可读" : out.toString();
    }

    private static void appendCpuFrequencyRows(StringBuilder out, String label, String values) {
        if (TextUtils.isEmpty(values) || values.startsWith("设备未公开") || values.startsWith("不可读取")) {
            append(out, label + "（候选）", values);
            return;
        }
        String[] parts = values.split("；");
        boolean found = false;
        for (String part : parts) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String cpu = part.substring(0, equals).trim().replace("cpu", "CPU ");
            String value = part.substring(equals + 1).trim();
            append(out, cpu + " " + label, value);
            found = true;
        }
        if (!found) append(out, label + "（候选）", values);
    }

    private static String cpuKHz(String raw) {
        if (TextUtils.isEmpty(raw) || raw.startsWith("设备未公开")) return raw;
        StringBuilder out = new StringBuilder();
        String[] parts = raw.split("；");
        for (String part : parts) {
            int equals = part.indexOf('=');
            if (equals < 0) continue;
            String cpu = part.substring(0, equals);
            Long kHz = leadingLong(part.substring(equals + 1).trim());
            if (out.length() > 0) out.append("；");
            out.append(cpu).append("=");
            if (kHz == null) out.append(part.substring(equals + 1).trim()).append(" kHz（原始值）");
            else out.append(String.format(Locale.US, "%.0f MHz", kHz / 1000.0)).append("（原始 kHz）");
        }
        return out.length() == 0 ? raw : out.toString();
    }

    private static String gpuValue(String gpuResult, String key) {
        String value = keyValue(gpuResult, key);
        return value == null ? "GPU 驱动未提供或上下文创建失败" : value;
    }

    private static String ddrConclusion(String ddrRaw) {
        String value = keyValue(ddrRaw, "DDR 结论");
        return value == null ? "无法从普通应用可靠确认" : value;
    }

    private static String devfreqSummary(String ddrRaw) {
        if (TextUtils.isEmpty(ddrRaw)) return "设备未公开或当前应用不可读";
        StringBuilder result = new StringBuilder();
        String[] lines = ddrRaw.split("\\n");
        for (String line : lines) {
            String value = line.trim();
            if (!value.startsWith("devfreq ")) continue;
            if (result.length() > 0) result.append("；");
            result.append(value);
            if (result.length() > 360) {
                result.append("；已截断");
                break;
            }
        }
        return result.length() == 0 ? "设备未公开或当前应用不可读" : result.toString();
    }

    private String buildGraphicsApiSummary(String gpuResult, String vulkanResult) {
        StringBuilder out = new StringBuilder();
        append(out, "OpenGL ES 渲染器", gpuValue(gpuResult, "GL_RENDERER"));
        append(out, "OpenGL ES 厂商", gpuValue(gpuResult, "GL_VENDOR"));
        append(out, "OpenGL ES 版本", gpuValue(gpuResult, "GL_VERSION"));
        append(out, "GLSL 版本", gpuValue(gpuResult, "GLSL 版本"));
        String vulkan = safe(vulkanResult);
        String feature = firstLineValue(vulkan, "系统 Vulkan 特性:");
        String devices = firstLineValue(vulkan, "Vulkan 物理设备数量:");
        String device = firstLineValue(vulkan, "设备0 名称:");
        String versions = firstLineValue(vulkan, "设备0 API/驱动版本:");
        String extensions = firstLineValue(vulkan, "设备0 扩展数量:");
        append(out, "Vulkan 系统特性", feature == null ? "未公开或不可用" : feature);
        append(out, "Vulkan 物理设备", devices == null ? "未枚举到物理设备" : devices);
        append(out, "Vulkan 设备", device == null ? "无" : device);
        append(out, "Vulkan API / 驱动版本", versions == null ? "未公开或不可用" : versions);
        append(out, "Vulkan 扩展数量", extensions == null ? "未公开或不可用" : extensions);
        append(out, "说明", "OpenGL ES 和 Vulkan 是两套独立图形 API；结果来自当前驱动或系统特性，不代表所有游戏接口都一定可用。Android 7.0 前按兼容规则显示 Vulkan 不可用。");
        return out.toString();
    }

    private String buildMemorySummary(String nativeResult) {
        StringBuilder out = new StringBuilder();
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            append(out, "总内存", bytes(info.totalMem));
            append(out, "当前可用内存", bytes(info.availMem));
            append(out, "内存使用", percent(info.totalMem - info.availMem, info.totalMem) + "%（ActivityManager 口径）");
            append(out, "当前状态", info.lowMemory ? "内存偏紧" : "正常");
        } else {
            append(out, "系统内存", "不可读取");
        }
        String ddrRaw = nativeSection(nativeResult, "[DDR 内存类型候选]", "[电池 power_supply 原始节点]");
        append(out, "DDR 内存类型", ddrConclusion(ddrRaw));
        String memorySection = nativeSection(nativeResult, "[内存硬件厂商候选]", "[DDR 内存类型候选]");
        String memoryDevice = firstLineValue(memorySection, "/phys_device:");
        append(out, "内存颗粒厂商", "普通应用无法可靠确认");
        if (memoryDevice != null) append(out, "内存硬件名称（候选）", memoryDevice);
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long storageTotal = fs.getTotalBytes();
            long storageAvailable = fs.getAvailableBytes();
            append(out, "存储总量", bytes(storageTotal));
            append(out, "可用空间", bytes(storageAvailable));
            append(out, "存储使用", percent(storageTotal - storageAvailable, storageTotal) + "%（StatFs 数据分区口径）");
        } catch (Throwable error) {
            append(out, "存储空间", "不可读取");
        }
        append(out, "说明", "DDR 和内存厂商只显示设备公开结果，不依据机型推测。完整内核字段见“技术原始数据”。");
        return out.toString();
    }

    private String buildBatterySummary(String nativeResult, Intent intent) {
        StringBuilder out = new StringBuilder();
        if (intent == null) {
            append(out, "电池信息", "不可读取");
            return out.toString();
        }
        append(out, "当前电量", batteryPercent(intent));
        append(out, "当前状态", batteryStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)));
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        append(out, "温度", temperature != Integer.MIN_VALUE ? String.format(Locale.US, "%.1f °C", temperature / 10.0) : "不可读取");
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        append(out, "电压", voltage >= 0 ? voltage + " mV" : "不可读取");
        append(out, "健康状态", batteryHealth(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)));
        String batterySection = nativeSection(nativeResult, "[电池 power_supply 原始节点]", "[数据边界]");
        String manufacturer = keyValue(batterySection, "manufacturer");
        String modelName = keyValue(batterySection, "model_name");
        append(out, "外接电源", pluggedName(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)));
        append(out, "电池厂商", manufacturer == null ? "设备未公开" : manufacturer + "（power_supply 原始值）");
        append(out, "电池型号", modelName == null ? "设备未公开" : modelName + "（power_supply 原始值）");
        append(out, "健康度（电量计估算）", batterySoh(nativeResult));
        append(out, "数据来源", "ACTION_BATTERY_CHANGED、BatteryManager 和 /sys/class/power_supply；健康度不是实验室测量值。");
        return out.toString();
    }

    private String androidSystemBitness() {
        try {
            Class<?> runtimeClass = Class.forName("dalvik.system.VMRuntime");
            Object runtime = runtimeClass.getDeclaredMethod("getRuntime").invoke(null);
            Object value = runtimeClass.getDeclaredMethod("is64Bit").invoke(runtime);
            if (value instanceof Boolean) return ((Boolean) value) ? "64 位（Android 运行时）" : "32 位（Android 运行时）";
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Hidden API access may be restricted on newer Android versions; use filesystem fallback below.
        }
        if (new java.io.File("/system/bin/app_process64").exists()
                || new java.io.File("/system/bin/linker64").exists()) {
            return "64 位（系统运行时文件回退判断）";
        }
        if (Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length > 0
                && (Build.SUPPORTED_32_BIT_ABIS == null || Build.SUPPORTED_32_BIT_ABIS.length == 0)) {
            return "64 位（系统 ABI 回退判断）";
        }
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                && Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length == 0
                && Build.SUPPORTED_32_BIT_ABIS != null && Build.SUPPORTED_32_BIT_ABIS.length > 0) {
            return "32 位（系统 ABI 回退判断）";
        }
        return "无法确认（系统未公开统一字段）";
    }

    private String processBitness() {
        try {
            int bits = NativeBridge.processBitness();
            if (bits == 32 || bits == 64) return bits + " 位（NDK sizeof(void*)）";
        } catch (UnsatisfiedLinkError ignored) {
            // Native library unavailable; keep the result explicit.
        }
        return "无法确认";
    }

    private String systemArchitecture() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0 || TextUtils.isEmpty(abis[0])) return "系统未公开";
        return architectureName(abis[0]) + "（系统首选运行 ABI：" + abis[0] + "）";
    }

    private static String architectureName(String abi) {
        if ("arm64-v8a".equals(abi)) return "64 位 ARM";
        if ("armeabi-v7a".equals(abi) || "armeabi".equals(abi)) return "32 位 ARM";
        if ("x86_64".equals(abi)) return "64 位 x86";
        if ("x86".equals(abi)) return "32 位 x86";
        return abi;
    }

    private String appSupportedAbis() {
        return "arm64-v8a, armeabi-v7a, x86_64（本 APK 实际打包）";
    }

    private String recommendedAbi() {
        if (Build.SUPPORTED_ABIS != null) {
            for (String systemAbi : Build.SUPPORTED_ABIS) {
                if (isAppSupportedAbi(systemAbi)) {
                    return systemAbi + "（匹配设备首选 ABI）";
                }
            }
        }
        return "无匹配 ABI（请使用通用 APK 或检查设备兼容性）";
    }

    private String binderBitness() {
        try {
            int protocol = NativeBridge.binderProtocolVersion();
            if (protocol == 7) return "32 位（Binder 协议 7）";
            if (protocol == 8) return "64 位（Binder 协议 8）";
            if (protocol < 0) return "无法确认（Binder 驱动不可读，errno=" + (-protocol) + "）";
            return "无法确认（Binder 协议版本 " + protocol + "）";
        } catch (UnsatisfiedLinkError ignored) {
            return "无法确认（NDK 接口不可用）";
        }
    }
    private static String processArchitecture() {
        String value = System.getProperty("os.arch");
        return TextUtils.isEmpty(value) ? "系统未公开" : value;
    }
    @SuppressWarnings("deprecation")
    private static String currentSystemAbi() {
        String abi = Build.CPU_ABI;
        return TextUtils.isEmpty(abi) ? firstAbi(Build.SUPPORTED_ABIS) : abi;
    }
    private static String firstAbi(String[] abis) {
        return abis != null && abis.length > 0 && !TextUtils.isEmpty(abis[0]) ? abis[0] : "系统未公开";
    }
    private static String joinOrUnavailable(String[] abis) {
        if (abis == null || abis.length == 0) return "不支持或系统未公开";
        return join(abis);
    }
    private static String buildValue(String value) {
        return TextUtils.isEmpty(value) ? "设备未公开" : value;
    }
    private boolean isAppSupportedAbi(String abi) {
        return "arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)
                || "x86_64".equals(abi);
    }

    private String nativeValue(String nativeResult, String key) {
        String prefix = key + ":";
        int start = nativeResult.indexOf(prefix);
        if (start < 0) return "不可读取";
        start += prefix.length();
        int end = nativeResult.indexOf('\n', start);
        String value = (end >= 0 ? nativeResult.substring(start, end) : nativeResult.substring(start)).trim();
        return TextUtils.isEmpty(value) ? "不可读取" : value;
    }

    private String buildCopyReport(String deviceSummary, String performanceSummary, String graphicsSummary,
                                   String memorySummary, String batterySummary, String displaySummary,
                                   String capabilityResult, String technicalRaw) {
        StringBuilder out = new StringBuilder();
        out.append("[设备与系统]\n").append(deviceSummary);
        out.append("\n[性能与芯片]\n").append(performanceSummary);
        out.append("\n[图形 API]\n").append(graphicsSummary);
        out.append("\n[内存与存储]\n").append(memorySummary);
        out.append("\n[电池]\n").append(batterySummary);
        out.append("\n[屏幕]\n").append(displaySummary);
        out.append("\n[硬件能力]\n").append(capabilityResult);
        out.append("\n[技术原始数据]\n").append(technicalRaw);
        return out.toString();
    }

    private String buildTechnicalRaw(String nativeResult, String gpuResult, String vulkanResult) {
        return "[GPU / OpenGL ES 原始报告]\n" + safe(gpuResult)
                + "\n\n[Vulkan 原生报告]\n" + safe(vulkanResult)
                + "\n\n[NDK / 内核公开节点]\n" + safe(nativeResult)
                + "\n\n[说明]\n这些是用于核验的原始输出。节点不可读、字段缺失或值为厂商实现时，应用不会用数据库或机型资料补写。";
    }

    private String nativeSection(String nativeResult, String startMarker, String endMarker) {
        int start = nativeResult.indexOf(startMarker);
        if (start < 0) return "未找到该采集段。";
        int contentStart = nativeResult.indexOf('\n', start);
        if (contentStart < 0) return "该采集段没有可显示内容。";
        int end = nativeResult.indexOf(endMarker, contentStart + 1);
        return nativeResult.substring(contentStart + 1, end >= 0 ? end : nativeResult.length()).trim();
    }

    private String nativeBatteryValue(String nativeResult, String key) {
        String section = nativeSection(nativeResult, "[电池 power_supply 原始节点]", "[数据边界]");
        String raw = keyValue(section, key);
        return raw == null ? "设备未公开或当前应用不可读" : raw + "（power_supply 原始值）";
    }

    private String batterySoh(String nativeResult) {
        String section = nativeSection(nativeResult, "[电池 power_supply 原始节点]", "[数据边界]");
        Long full = leadingLong(keyValue(section, "charge_full"));
        Long design = leadingLong(keyValue(section, "charge_full_design"));
        if (full == null || design == null || full <= 0 || design <= 0) {
            return "无法计算：设备未同时公开 charge_full 与 charge_full_design";
        }
        return String.format(Locale.US, "%.1f%%（%d / %d；设备电量计估算）", full * 100.0 / design, full, design);
    }

    private static String keyValue(String text, String key) {
        String prefix = key + ":";
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).trim();
                return TextUtils.isEmpty(value) || value.startsWith("不可读:") ? null : value;
            }
        }
        return null;
    }

    private static String firstLineValue(String text, String marker) {
        if (TextUtils.isEmpty(text)) return null;
        String[] lines = text.split("\\n");
        for (String line : lines) {
            int position = line.indexOf(marker);
            if (position >= 0) {
                String value = line.substring(position + marker.length()).trim();
                if (!TextUtils.isEmpty(value) && !value.startsWith("不可读:")) return value;
            }
        }
        return null;
    }

    private static Long leadingLong(String text) {
        if (TextUtils.isEmpty(text)) return null;
        int end = 0;
        if (text.charAt(0) == '-') end++;
        while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
        if (end == 0 || (end == 1 && text.charAt(0) == '-')) return null;
        try {
            return Long.parseLong(text.substring(0, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Display currentDisplay() {
        try {
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            return displayManager == null ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String estimatedDiagonal(DisplayMetrics metrics) {
        if (metrics.xdpi <= 0 || metrics.ydpi <= 0) return "不可估算";
        double widthInches = metrics.widthPixels / metrics.xdpi;
        double heightInches = metrics.heightPixels / metrics.ydpi;
        return String.format(Locale.US, "%.2f 英寸（按系统 xdpi/ydpi 推算，不是面板实测）", Math.sqrt(widthInches * widthInches + heightInches * heightInches));
    }

    private static String displayModesCompat(Display display) {
        try {
            Object modes = Display.class.getMethod("getSupportedModes").invoke(display);
            int count = java.lang.reflect.Array.getLength(modes);
            if (count == 0) return "系统未报告";
            StringBuilder out = new StringBuilder();
            int limit = Math.min(count, 8);
            for (int index = 0; index < limit; index++) {
                if (index > 0) out.append("；");
                Object mode = java.lang.reflect.Array.get(modes, index);
                Class<?> type = mode.getClass();
                int width = (Integer) type.getMethod("getPhysicalWidth").invoke(mode);
                int height = (Integer) type.getMethod("getPhysicalHeight").invoke(mode);
                float rate = (Float) type.getMethod("getRefreshRate").invoke(mode);
                out.append(width).append("×").append(height).append(" @ ")
                        .append(String.format(Locale.US, "%.2f", rate)).append(" Hz");
            }
            if (count > limit) out.append("；另有 ").append(count - limit).append(" 项");
            return out.toString();
        } catch (Throwable error) {
            return "不可读取: " + error.getClass().getSimpleName();
        }
    }

    private static String wideColorGamutCompat(Display display) {
        if (Build.VERSION.SDK_INT < 26) {
            return "系统未提供公开检测（需 API 26+；当前 API " + Build.VERSION.SDK_INT + "）";
        }
        return display != null && display.isWideColorGamut()
                ? "系统报告支持广色域（Wide Color Gamut）" : "系统报告不支持广色域";
    }

    private static String tenBitCapabilityCompat(Display display) {
        String hdr = hdrTypesCompat(display);
        if (Build.VERSION.SDK_INT < 26) {
            return "系统未提供 HDR 类型公开检测；无面板原生 10-bit 通用 API";
        }
        if (hdr.startsWith("不可读取") || hdr.startsWith("系统未报告")) {
            return "没有可用的系统 HDR 类型报告；无面板原生 10-bit 通用 API";
        }
        return "系统报告 HDR 能力（" + hdr + "）；但 Android 没有普通应用通用的面板原生 10-bit 确认 API";
    }

    private static String hdrTypesCompat(Display display) {
        if (display == null || Build.VERSION.SDK_INT < 26) return "系统未报告 HDR 支持";
        android.view.Display.HdrCapabilities capabilities = display.getHdrCapabilities();
        int[] types = capabilities == null ? null : capabilities.getSupportedHdrTypes();
        if (types == null || types.length == 0) return "系统未报告 HDR 支持";
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < types.length; index++) {
            if (index > 0) out.append(", ");
            switch (types[index]) {
                case 1: out.append("Dolby Vision"); break;
                case 2: out.append("HDR10"); break;
                case 3: out.append("HLG"); break;
                case 4: out.append("HDR10+"); break;
                default: out.append("类型 ").append(types[index]); break;
            }
        }
        return out.toString();
    }

    private static String displayStateName(int state) {
        switch (state) {
            case Display.STATE_OFF: return "关闭";
            case Display.STATE_ON: return "开启";
            case Display.STATE_DOZE: return "低功耗常亮";
            case Display.STATE_DOZE_SUSPEND: return "低功耗挂起";
            case Display.STATE_UNKNOWN: return "未知";
            default: return "不可读取或类型 " + state;
        }
    }

    private static String batteryHealth(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "失效";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "电压过高";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "未指定故障";
            case BatteryManager.BATTERY_HEALTH_COLD: return "温度过低";
            default: return "未知";
        }
    }

    private static String pluggedName(int plugged) {
        if (plugged == 0) return "未连接外部电源";
        StringBuilder out = new StringBuilder();
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) out.append("AC");
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) appendPlug(out, "USB");
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) appendPlug(out, "无线");
        return out.length() == 0 ? "类型 " + plugged : out.toString();
    }

    private static void appendPlug(StringBuilder out, String value) {
        if (out.length() > 0) out.append(" + ");
        out.append(value);
    }

    private String now() {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault()).format(new Date());
    }

    private void updateOverview(ProbeSnapshot snapshot) {
        heroNameView.setText(safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
        heroDetailView.setText("Android " + safe(Build.VERSION.RELEASE) + " · API " + Build.VERSION.SDK_INT
                + "\n" + deviceProfile.label() + " · "
                + (Build.VERSION.SDK_INT >= 23 ? "安全补丁 " + Build.VERSION.SECURITY_PATCH : "系统未公开安全补丁"));
        cpuMetric.value.setText(shortValue(summaryValue(snapshot.performanceSummary, "逻辑核心"), 18));
        cpuMetric.detail.setText(shortValue(summaryValue(snapshot.performanceSummary, "CPU 架构"), 22));
        gpuMetric.value.setText(shortValue(summaryValue(snapshot.performanceSummary, "GPU"), 22));
        gpuMetric.detail.setText("OpenGL ES 驱动报告");

        memoryMetric.value.setText(shortValue(summaryValue(snapshot.memorySummary, "总内存"), 18));
        String memoryAvailable = summaryValue(snapshot.memorySummary, "当前可用内存");
        String memoryUsed = summaryValue(snapshot.memorySummary, "内存使用");
        memoryMetric.detail.setText("可用 " + shortValue(memoryAvailable, 12) + " · 已用 " + shortValue(memoryUsed, 10));

        storageMetric.value.setText(shortValue(summaryValue(snapshot.memorySummary, "存储总量"), 18));
        storageMetric.detail.setText("可用 " + shortValue(summaryValue(snapshot.memorySummary, "可用空间"), 12));

        displayMetric.value.setText(shortValue(summaryValue(snapshot.displaySummary, "屏幕分辨率"), 20));
        displayMetric.detail.setText(shortValue(summaryValue(snapshot.displaySummary, "当前刷新率"), 20) + " · 系统逻辑显示");

        String charge = summaryValue(snapshot.batterySummary, "当前电量");
        batteryMetric.value.setText(charge);
        batteryMetric.detail.setText(shortValue(summaryValue(snapshot.batterySummary, "当前状态"), 18));
        batteryMetric.value.setTextColor(batteryColor(charge, batteryMetric.detail.getText().toString()));
    }

    private static String summaryValue(String summary, String key) {
        String value = firstLineValue(summary, key + ":");
        return value == null ? "不可读取" : value;
    }

    private static int batteryColor(String charge, String status) {
        if (safe(status).contains("过热") || safe(status).contains("失效")) return Color.rgb(214, 101, 101);
        Long value = leadingLong(charge);
        if (value == null) return TEXT_SECONDARY;
        if (value <= 15) return Color.rgb(214, 101, 101);
        if (value <= 35) return Color.rgb(205, 164, 82);
        return MINT;
    }

    private static String shortValue(String value, int max) {
        if (TextUtils.isEmpty(value)) return "不可读取";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static int percent(long part, long total) {
        if (total <= 0) return 0;
        return (int) Math.max(0, Math.min(100, part * 100 / total));
    }

    private Intent batteryIntent() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        return ContextCompat.registerReceiver(this, null, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private static String batteryPercent(Intent intent) {
        if (intent == null) return "不可读取";
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level >= 0 && scale > 0 ? (level * 100 / scale) + "%" : "未知";
    }

    private void copyReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Device Probe Report", fullReport));
            Toast.makeText(getApplicationContext(), "全部报告已复制到剪贴板", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "剪贴板服务不可用", Toast.LENGTH_SHORT).show();
        }
    }

    private static String batteryStatus(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
            case BatteryManager.BATTERY_STATUS_FULL: return "已充满";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "未充电";
            default: return "未知";
        }
    }

    private static String join(String[] values) {
        return values == null || values.length == 0 ? "未知" : TextUtils.join(", ", values);
    }

    private static void append(StringBuilder out, String key, String value) {
        out.append(key).append(": ").append(safe(value)).append('\n');
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "未知" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String bytes(long value) {
        if (value < 0) return "未知";
        final String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double result = value;
        int unit = 0;
        while (result >= 1024.0 && unit < units.length - 1) {
            result /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, result >= 100 || unit == 0 ? "%.0f %s" : "%.2f %s", result, units[unit]);
    }

    private Drawable roundedDrawable(int fillColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private Drawable strokedDrawable(int fillColor, int strokeColor, int radius, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private Drawable rippleDrawable(int fillColor, int pressedColor, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(pressedColor),
                roundedDrawable(fillColor, radius), null);
    }

    private LinearLayout.LayoutParams matchParams() {
        return new LinearLayout.LayoutParams(match(), wrap());
    }

    private LinearLayout.LayoutParams fixedParams(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int match() { return ViewGroup.LayoutParams.MATCH_PARENT; }
    private int wrap() { return ViewGroup.LayoutParams.WRAP_CONTENT; }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (deviceProfile == null ? value * density + 0.5f : deviceProfile.dp(value, density) + 0.5f);
    }
}
