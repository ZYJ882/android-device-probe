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
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

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
    private TextView deviceValue;
    private TextView systemValue;
    private TextView cpuValue;
    private TextView memoryValue;
    private TextView storageValue;
    private TextView batteryValue;
    private TextView memoryProgressText;
    private TextView storageProgressText;
    private TextView batteryProgressText;
    private ProgressBar memoryProgress;
    private ProgressBar storageProgress;
    private ProgressBar batteryProgress;
    private ScrollView rootScroll;
    private LinearLayout sectionContainer;
    private DeviceProfile deviceProfile;
    private String fullReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        deviceProfile = DeviceProfile.detect(this);
        setContentView(createContentView());
        refreshProbe();
    }

    private void configureSystemBars() {
        Window window = getWindow();
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
        int horizontalInset = deviceProfile.isWatch() ? dp(10) : (deviceProfile.isTelevision() ? dp(38) : dp(20));
        int verticalInset = deviceProfile.isWatch() ? dp(10) : dp(22);
        root.setPadding(horizontalInset, verticalInset, horizontalInset, deviceProfile.isWatch() ? dp(12) : dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(match(), wrap()));

        root.addView(createTopBar());
        root.addView(createHeroCard(), marginParams(match(), wrap(), 0, deviceProfile.isWatch() ? dp(10) : dp(18), 0, 0));

        root.addView(sectionTitle("设备概览", "常用状态一览 · " + deviceProfile.label()),
                marginParams(match(), wrap(), 0, dp(20), 0, dp(10)));
        root.addView(createOverviewGrid());
        root.addView(createActionRow(), marginParams(match(), wrap(), 0, dp(14), 0, 0));
        if (deviceProfile.isSuspectedWatch()) {
            root.addView(createDeviceModeButton(), marginParams(match(), dp(42), 0, dp(9), 0, 0));
        }

        root.addView(sectionTitle("详细信息", "常用结论在前；技术原文统一收起"),
                marginParams(match(), wrap(), 0, dp(24), 0, dp(10)));
        sectionContainer = new LinearLayout(this);
        sectionContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(sectionContainer, matchParams());

        TextView privacy = new TextView(this);
        privacy.setText("本应用不联网、不申请运行时权限；不能可靠读取的硬件信息会明确标为未知，不依据机型猜测。");
        privacy.setTextColor(TEXT_SECONDARY);
        privacy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        privacy.setLineSpacing(dp(2), 1.12f);
        privacy.setPadding(0, dp(17), 0, 0);
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
        icon.setContentDescription("设备信息探查器图标");
        icon.setBackground(roundedDrawable(SURFACE_SOFT, dp(17)));
        icon.setPadding(dp(2), dp(2), dp(2), dp(2));
        bar.addView(icon, fixedParams(dp(52), dp(52)));

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setPadding(dp(12), 0, 0, 0);
        bar.addView(textBlock, new LinearLayout.LayoutParams(0, wrap(), 1f));

        TextView eyebrow = label("DEVICE PROBE", MINT, 11);
        eyebrow.setLetterSpacing(0.12f);
        textBlock.addView(eyebrow);

        TextView title = new TextView(this);
        title.setText("设备信息探查");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(1), 0, 0);
        textBlock.addView(title);

        TextView version = new TextView(this);
        version.setText("本机公开数据 · v1.14");
        version.setTextColor(TEXT_SECONDARY);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textBlock.addView(version);
        return bar;
    }

    private View createHeroCard() {
        LinearLayout hero = card();
        hero.setPadding(dp(18), dp(17), dp(18), dp(17));
        hero.setBackground(strokedDrawable(SURFACE, BORDER, dp(20), 1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        hero.addView(top, matchParams());

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        top.addView(text, new LinearLayout.LayoutParams(0, wrap(), 1f));

        TextView headline = new TextView(this);
        headline.setText("本机设备状态");
        headline.setTextColor(TEXT_PRIMARY);
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.addView(headline);

        TextView description = new TextView(this);
        description.setText("核心参数、硬件能力与屏幕测试。\n" + deviceProfile.screenRule());
        description.setTextColor(TEXT_SECONDARY);
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        description.setLineSpacing(dp(2), 1.1f);
        description.setPadding(0, dp(5), 0, 0);
        text.addView(description);

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        hero.addView(divider, marginParams(match(), dp(1), 0, dp(16), 0, dp(12)));

        statusView = new TextView(this);
        statusView.setTextColor(MINT);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusView.setText("正在刷新…");
        hero.addView(statusView);
        return hero;
    }

    private View createOverviewGrid() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        if (deviceProfile.isWatch()) {
            deviceValue = overviewCardFull(grid, "设备", "读取中…", 0);
            systemValue = overviewCardFull(grid, "系统", "读取中…", dp(8));
            cpuValue = overviewCardFull(grid, "处理器", "读取中…", dp(8));
            memoryValue = overviewCardFull(grid, "可用内存", "读取中…", dp(8));
            storageValue = overviewCardFull(grid, "可用空间", "读取中…", dp(8));
            batteryValue = overviewCardFull(grid, "电池", "读取中…", dp(8));
            return grid;
        }

        LinearLayout rowOne = overviewRow(grid, 0);
        deviceValue = overviewCard(rowOne, "设备", "读取中…", false);
        systemValue = overviewCard(rowOne, "系统", "读取中…", true);

        LinearLayout rowTwo = overviewRow(grid, dp(10));
        cpuValue = overviewCard(rowTwo, "处理器", "读取中…", false);
        memoryValue = overviewCard(rowTwo, "可用内存", "读取中…", true);

        LinearLayout rowThree = overviewRow(grid, dp(10));
        storageValue = overviewCard(rowThree, "可用空间", "读取中…", false);
        batteryValue = overviewCard(rowThree, "电池", "读取中…", true);
        return grid;
    }

    private TextView overviewCardFull(LinearLayout parent, String labelText, String initialValue, int topMargin) {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(11), dp(14), dp(12));
        card.setBackground(strokedDrawable(SURFACE, BORDER, dp(17), 1));
        parent.addView(card, marginParams(match(), dp(70), 0, topMargin, 0, 0));
        TextView label = label(labelText, TEXT_SECONDARY, 11);
        card.addView(label);
        TextView value = new TextView(this);
        value.setText(initialValue);
        value.setTextColor(TEXT_PRIMARY);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setGravity(Gravity.BOTTOM);
        card.addView(value, new LinearLayout.LayoutParams(match(), 0, 1f));
        return value;
    }

    private LinearLayout overviewRow(LinearLayout parent, int topMargin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, marginParams(match(), wrap(), 0, topMargin, 0, 0));
        return row;
    }

    private View createActionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(deviceProfile.isWatch() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        TextView refresh = actionButton("刷新信息", true);
        refresh.setOnClickListener(v -> refreshProbe());
        row.addView(refresh, deviceProfile.isWatch() ? marginParams(match(), dp(44), 0, 0, 0, dp(7)) : new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView screenTest = actionButton("屏幕测试", false);
        screenTest.setOnClickListener(v -> startActivity(new Intent(this, ScreenTestActivity.class)));
        if (!deviceProfile.isWatch()) {
            View gap = new View(this);
            row.addView(gap, fixedParams(dp(10), dp(1)));
        }
        row.addView(screenTest, deviceProfile.isWatch() ? fixedParams(match(), dp(44)) : new LinearLayout.LayoutParams(0, dp(48), 1f));
        return row;
    }

    private View createCategoryNavigation() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(wrap(), wrap()));
        String[] labels = {"概览", "设备", "芯片", "存储", "电池", "屏幕", "能力", "原始"};
        for (String label : labels) {
            TextView chip = navigationChip(label);
            chip.setOnClickListener(view -> navigateTo(label));
            row.addView(chip, marginParams(wrap(), dp(34), 0, 0, dp(8), 0));
        }
        return scroll;
    }

    private TextView navigationChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(text.equals("概览") ? BACKGROUND : TEXT_SECONDARY);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setPadding(dp(13), 0, dp(13), 0);
        chip.setBackground(text.equals("概览") ? roundedDrawable(MINT, dp(13)) : focusDrawable(SURFACE_SOFT, Color.rgb(39, 71, 94), dp(13)));
        return chip;
    }

    private void navigateTo(String category) {
        if (rootScroll == null) return;
        if ("概览".equals(category)) {
            rootScroll.smoothScrollTo(0, 0);
            return;
        }
        String title;
        switch (category) {
            case "设备": title = "设备与系统"; break;
            case "芯片": title = "性能硬件详情"; break;
            case "存储": title = "内存与存储"; break;
            case "电池": title = "电池"; break;
            case "屏幕": title = "屏幕"; break;
            case "能力": title = "硬件能力"; break;
            default: title = "技术详情"; break;
        }
        for (int index = 0; index < sectionContainer.getChildCount(); index++) {
            View candidate = sectionContainer.getChildAt(index);
            if (!title.equals(candidate.getTag())) continue;
            candidate.post(() -> {
                int[] target = new int[2];
                int[] origin = new int[2];
                candidate.getLocationOnScreen(target);
                rootScroll.getLocationOnScreen(origin);
                rootScroll.smoothScrollBy(0, target[1] - origin[1] - dp(12));
            });
            return;
        }
    }

    private View createStatusDashboard() {
        LinearLayout card = card();
        card.setPadding(dp(15), dp(13), dp(15), dp(14));
        card.setBackground(strokedDrawable(SURFACE, BORDER, dp(18), 1));
        card.addView(sectionTitle("即时状态", "已用与剩余空间，点击分类可查看详情"));
        memoryProgressText = statusMetric(card, "内存", "读取中…", true);
        storageProgressText = statusMetric(card, "内部存储", "读取中…", true);
        batteryProgressText = statusMetric(card, "电池", "读取中…", false);
        return card;
    }

    private TextView statusMetric(LinearLayout parent, String labelText, String initialText, boolean hasCapacity) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, 0);
        TextView name = label(labelText, TEXT_PRIMARY, 13);
        row.addView(name, new LinearLayout.LayoutParams(0, wrap(), .8f));
        TextView value = new TextView(this);
        value.setText(initialText);
        value.setTextColor(TEXT_SECONDARY);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        value.setGravity(Gravity.RIGHT);
        row.addView(value, new LinearLayout.LayoutParams(0, wrap(), 1.2f));
        parent.addView(row, matchParams());
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.setProgressTintList(ColorStateList.valueOf(hasCapacity ? MINT : Color.rgb(102, 175, 229)));
        bar.setBackgroundTintList(ColorStateList.valueOf(BORDER));
        parent.addView(bar, marginParams(match(), dp(5), 0, dp(6), 0, 0));
        if ("内存".equals(labelText)) memoryProgress = bar;
        else if ("内部存储".equals(labelText)) storageProgress = bar;
        else batteryProgress = bar;
        return value;
    }

    private TextView createDeviceModeButton() {
        TextView button = actionButton("纠正设备类型 · 当前：" + deviceProfile.label(), false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, deviceProfile.isWatch() ? 11 : 12);
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
        if (primary) {
            button.setTextColor(BACKGROUND);
            button.setBackground(deviceProfile.isTelevision()
                    ? focusDrawable(MINT, Color.rgb(136, 245, 228), dp(15))
                    : rippleDrawable(MINT, Color.rgb(125, 236, 220), dp(15)));
        } else {
            button.setTextColor(TEXT_PRIMARY);
            button.setBackground(deviceProfile.isTelevision()
                    ? focusDrawable(SURFACE_SOFT, Color.rgb(45, 83, 111), dp(15))
                    : rippleDrawable(SURFACE_SOFT, Color.rgb(48, 69, 101), dp(15)));
        }
        return button;
    }

    private TextView overviewCard(LinearLayout parent, String labelText, String initialValue, boolean second) {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(13), dp(14), dp(14));
        card.setBackground(strokedDrawable(SURFACE, BORDER, dp(17), 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(91), 1f);
        if (second) params.setMargins(dp(10), 0, 0, 0);
        parent.addView(card, params);

        TextView label = label(labelText, TEXT_SECONDARY, 11);
        label.setLetterSpacing(0.05f);
        card.addView(label);

        TextView value = new TextView(this);
        value.setText(initialValue);
        value.setTextColor(TEXT_PRIMARY);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setMaxLines(2);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(Gravity.BOTTOM);
        card.addView(value, new LinearLayout.LayoutParams(match(), 0, 1f));
        return value;
    }

    private void renderSections(String nativeResult, String gpuResult, String vulkanResult) {
        sectionContainer.removeAllViews();
        // 常用信息每类只保留一个入口，避免同一结论出现在多个板块。
        addExpandableSection("设备与系统", "型号、系统版本和设备类型", buildDeviceSummary(), true, false);
        addExpandableSection("性能与芯片", "CPU、GPU、ABI 和 DDR 内存类型", buildPerformanceHardwareSummary(nativeResult, gpuResult), false, false);
        addExpandableSection("图形 API", "OpenGL ES 与 Vulkan 驱动支持", buildGraphicsApiSummary(gpuResult, vulkanResult), false, false);
        addExpandableSection("内存与存储", "总内存、当前可用内存和可用空间", buildMemorySummary(nativeResult), false, false);
        addExpandableSection("电池", "电量、充电状态、温度和电压", buildBatterySummary(nativeResult), false, false);
        addExpandableSection("屏幕", "分辨率、刷新率、广色域和 HDR", buildDisplaySummary(), false, false);
        addExpandableSection("硬件能力", "传感器、相机、触控、定位和连接能力", CapabilityProbe.collect(this), false, false);

        // 原始信息统一收起，只在排障或核验时展开。
        addExpandableSection("技术原始数据", "CPU、GPU、内存、屏幕、DDR 和电池的原始读取结果", buildTechnicalRaw(nativeResult, gpuResult, vulkanResult), false, true);
    }

    private void addExpandableSection(String titleText, String subtitleText, String bodyText,
                                      boolean expandedInitially, boolean monospace) {
        final LinearLayout section = card();
        section.setPadding(dp(15), dp(13), dp(15), dp(13));
        section.setBackground(strokedDrawable(SURFACE, BORDER, dp(18), 1));

        final LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setClickable(true);
        header.setFocusable(true);
        header.setBackground(deviceProfile.isTelevision()
                ? focusDrawable(SURFACE, Color.rgb(35, 73, 96), dp(13))
                : rippleDrawable(SURFACE, Color.rgb(37, 58, 88), dp(13)));
        header.setPadding(dp(4), dp(3), dp(3), dp(3));
        section.addView(header, matchParams());

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        header.addView(labels, new LinearLayout.LayoutParams(0, wrap(), 1f));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
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
        toggle.setText(expandedInitially ? "收起  −" : "展开  +");
        toggle.setTextColor(MINT);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        toggle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(toggle, fixedParams(dp(60), dp(42)));

        final TextView body = new TextView(this);
        body.setText(bodyText);
        body.setTextColor(Color.rgb(214, 227, 242));
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        body.setLineSpacing(dp(2), 1.1f);
        body.setTextIsSelectable(true);
        body.setPadding(dp(4), dp(14), dp(4), dp(4));
        if (monospace) body.setTypeface(Typeface.MONOSPACE);
        body.setVisibility(expandedInitially ? View.VISIBLE : View.GONE);
        section.addView(body, matchParams());

        final boolean[] expanded = {expandedInitially};
        header.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            body.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            toggle.setText(expanded[0] ? "收起  −" : "展开  +");
        });
        section.setTag(titleText);
        sectionContainer.addView(section, marginParams(match(), wrap(), 0, 0, 0, dp(10)));
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
        statusView.setText("正在读取本机公开系统信息…");
        String nativeResult;
        try {
            nativeResult = NativeBridge.collectNativeData();
        } catch (Throwable error) {
            nativeResult = "[NDK / 内核公开接口]\n采集失败: "
                    + error.getClass().getSimpleName() + ": " + safe(error.getMessage()) + "\n";
        }
        String gpuResult = GpuProbe.collect();
        String vulkanResult = VulkanProbe.collect(this);
        fullReport = buildCopyReport(nativeResult, gpuResult, vulkanResult);
        updateOverview();
        renderSections(nativeResult, gpuResult, vulkanResult);
        statusView.setText("已完成 · " + DateFormat.getTimeInstance(DateFormat.MEDIUM,
                Locale.getDefault()).format(new Date()));
    }

    private String buildDeviceSummary() {
        StringBuilder out = new StringBuilder();
        append(out, "设备", safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
        append(out, "系统", "Android " + safe(Build.VERSION.RELEASE) + "（API " + Build.VERSION.SDK_INT + "）");
        append(out, "安全补丁", Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : "当前系统未提供");
        append(out, "设备类型", deviceProfile.label());
        append(out, "设备代号", Build.DEVICE);
        if (deviceProfile.isSuspectedWatch() || deviceProfile.manualOverride) {
            append(out, "判定依据", deviceProfile.evidence);
            append(out, "手动纠正", "首页已提供纠正按钮，仅影响本应用布局。");
        }
        return out.toString();
    }

    private String buildAbiSummary(String nativeResult) {
        StringBuilder out = new StringBuilder();
        append(out, "处理器架构", nativeValue(nativeResult, "uname.machine"));
        append(out, "系统首选 ABI", safe(Build.CPU_ABI));
        append(out, "设备支持 ABI", join(Build.SUPPORTED_ABIS));
        append(out, "本应用支持 ABI", appSupportedAbis());
        append(out, "当前运行的原生库", nativeValue(nativeResult, "native ABI"));
        append(out, "推荐安装 ABI", recommendedAbi());
        append(out, "简单解释", "选择与设备首选 ABI 相同的 64 位或 32 位版本；本应用是通用包，无需手动选择。");
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
        append(out, "当前刷新率", String.format(Locale.US, "%.2f Hz", display.getRefreshRate()));
        append(out, "广色域（系统检测）", wideColorGamutCompat(display));
        append(out, "HDR（系统检测）", hdrTypesCompat(display));
        append(out, "10-bit 显示", tenBitCapabilityCompat(display));
        append(out, "说明", "广色域和 HDR 为系统报告能力；色域图案仅供观察，不能替代仪器实测。面板厂商与型号未公开时不会推测。");
        return out.toString();
    }

    private String buildPerformanceHardwareSummary(String nativeResult, String gpuResult) {
        StringBuilder out = new StringBuilder();
        append(out, "CPU 架构", nativeValue(nativeResult, "uname.machine"));
        append(out, "逻辑核心", nativeValue(nativeResult, "POSIX 在线逻辑 CPU") + " 个");
        String cpuRaw = nativeSection(nativeResult, "[CPU 深度原始节点]", "[内核导出原文");
        append(out, "当前频率（候选）", cpuKHz(indexedCpuValue(cpuRaw, "scaling_cur_freq_khz")));
        append(out, "最高频率（候选）", cpuKHz(indexedCpuValue(cpuRaw, "cpuinfo_max_freq_khz")));
        append(out, "GPU", gpuValue(gpuResult, "GL_RENDERER"));
        append(out, "GPU 厂商", gpuValue(gpuResult, "GL_VENDOR"));
        append(out, "图形接口", gpuValue(gpuResult, "GL_VERSION"));
        append(out, "设备首选 ABI", safe(Build.CPU_ABI));
        append(out, "应用正在使用", nativeValue(nativeResult, "native ABI"));
        append(out, "说明", "CPU 频率只显示系统实际公开结果；DDR 内存类型请在“内存与存储”中查看。完整驱动/原始数据请展开“技术原始数据”。");
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
            append(out, "当前状态", info.lowMemory ? "内存偏紧" : "正常");
        } else {
            append(out, "系统内存", "不可读取");
        }
        String ddrRaw = nativeSection(nativeResult, "[DDR 内存类型候选]", "[电池 power_supply 原始节点]");
        append(out, "DDR 内存类型", ddrConclusion(ddrRaw));
        String memorySection = nativeSection(nativeResult, "[内存硬件厂商候选]", "[DDR 内存类型候选]");
        String memoryDevice = firstLineValue(memorySection, "/phys_device:");
        append(out, "内存颗粒厂商", "普通应用未能确认");
        if (memoryDevice != null) append(out, "内存硬件名称（候选）", memoryDevice);
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            append(out, "存储总量", bytes(fs.getTotalBytes()));
            append(out, "可用空间", bytes(fs.getAvailableBytes()));
        } catch (Throwable error) {
            append(out, "存储空间", "不可读取");
        }
        append(out, "说明", "DDR 和内存厂商只显示设备公开结果，不依据机型推测。完整内核字段见“技术原始数据”。");
        return out.toString();
    }

    private String buildHardwareVendorSummary(String nativeResult) {
        StringBuilder out = new StringBuilder();
        append(out, "读取时间", now());
        String memorySection = nativeSection(nativeResult, "[内存硬件厂商候选]", "[DDR 内存类型候选]");
        String memoryDevice = firstLineValue(memorySection, "/phys_device:");
        append(out, "内存厂商", "未公开或无法确认");
        append(out, "内存硬件名称（内核候选）", memoryDevice == null ? "设备未提供" : memoryDevice + "（原始值）");
        append(out, "内存说明", "Android 手机通常不会向普通应用公开 DRAM 颗粒厂商；不会依据手机型号猜测。");

        String batterySection = nativeSection(nativeResult, "[电池 power_supply 原始节点]", "[数据边界]");
        String manufacturer = keyValue(batterySection, "manufacturer");
        String modelName = keyValue(batterySection, "model_name");
        String model = keyValue(batterySection, "model");
        append(out, "电池厂商", manufacturer == null ? "设备未公开或当前应用不可读" : manufacturer + "（power_supply 原始值）");
        append(out, "电池型号", modelName == null ? (model == null ? "设备未公开或当前应用不可读" : model + "（power_supply 原始值）") : modelName + "（power_supply 原始值）");
        append(out, "电量计型号/厂商", "若驱动未公开独立字段，则无法从普通应用可靠确认；原始节点见“电池原始数据”。");
        return out.toString();
    }

    private String buildBatterySummary(String nativeResult) {
        StringBuilder out = new StringBuilder();
        Intent intent = batteryIntent();
        if (intent == null) {
            append(out, "电池信息", "不可读取");
            return out.toString();
        }
        append(out, "当前电量", currentBattery());
        append(out, "当前状态", currentBatteryStatus());
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        append(out, "温度", temperature != Integer.MIN_VALUE ? String.format(Locale.US, "%.1f °C", temperature / 10.0) : "不可读取");
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        append(out, "电压", voltage >= 0 ? voltage + " mV" : "不可读取");
        append(out, "健康状态", batteryHealth(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)));
        String batterySection = nativeSection(nativeResult, "[电池 power_supply 原始节点]", "[数据边界]");
        String manufacturer = keyValue(batterySection, "manufacturer");
        String modelName = keyValue(batterySection, "model_name");
        append(out, "电池厂商", manufacturer == null ? "设备未公开" : manufacturer);
        append(out, "电池型号", modelName == null ? "设备未公开" : modelName);
        append(out, "说明", "健康状态不等于电池健康百分比；更详细电量计字段见“技术原始数据”。");
        return out.toString();
    }

    private String buildDeviceSystemSection() {
        StringBuilder out = new StringBuilder();
        append(out, "系统版本", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        append(out, "安全补丁", Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : "API 23 前无公开字段");
        append(out, "制造商", Build.MANUFACTURER);
        append(out, "品牌", Build.BRAND);
        append(out, "型号", Build.MODEL);
        append(out, "设备代号", Build.DEVICE);
        append(out, "产品代号", Build.PRODUCT);
        append(out, "主板", Build.BOARD);
        append(out, "硬件", Build.HARDWARE);
        append(out, "构建 ID", Build.ID);
        append(out, "构建指纹", Build.FINGERPRINT);
        append(out, "构建时间", formatBuildTime(Build.TIME));
        return out.toString();
    }

    private String buildAbiArchitectureSection(String nativeResult) {
        StringBuilder out = new StringBuilder();
        append(out, "系统 ABI（主）", safe(Build.CPU_ABI));
        append(out, "系统 ABI（次）", safe(Build.CPU_ABI2));
        append(out, "系统支持的 ABI", join(Build.SUPPORTED_ABIS));
        append(out, "应用支持的 ABI", appSupportedAbis());
        append(out, "当前 native ABI", nativeValue(nativeResult, "native ABI"));
        append(out, "CPU 架构（uname.machine）", nativeValue(nativeResult, "uname.machine"));
        append(out, "推荐 ABI", recommendedAbi());
        append(out, "推荐说明", "若使用按 ABI 拆分的 APK，请优先选择上述推荐 ABI；本安装包为多 ABI 通用包，无需手动选择。");
        return out.toString();
    }

    private String appSupportedAbis() {
        return "arm64-v8a, armeabi-v7a, x86, x86_64";
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

    private boolean isAppSupportedAbi(String abi) {
        return "arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)
                || "x86".equals(abi) || "x86_64".equals(abi);
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

    private String buildMemoryStorageSection(String nativeResult) {
        StringBuilder out = new StringBuilder();
        append(out, "读取时间", now());
        append(out, "Android 口径", "ActivityManager.MemoryInfo");
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            append(out, "系统总内存（totalMem）", bytes(info.totalMem));
            append(out, "系统可用内存估计（availMem）", bytes(info.availMem));
            append(out, "低内存状态（lowMemory）", info.lowMemory ? "是" : "否");
            append(out, "低内存阈值（threshold）", bytes(info.threshold));
        } else {
            append(out, "内存服务", "不可用");
        }
        append(out, "内核口径", "/proc/meminfo；保留原始字段，可能与系统口径不同");
        append(out, "内核 MemTotal", kernelMemValue(nativeResult, "MemTotal"));
        append(out, "内核 MemAvailable", kernelMemValue(nativeResult, "MemAvailable"));
        append(out, "内核 MemFree", kernelMemValue(nativeResult, "MemFree"));
        append(out, "内核 Buffers", kernelMemValue(nativeResult, "Buffers"));
        append(out, "内核 Cached", kernelMemValue(nativeResult, "Cached"));
        append(out, "内核 SReclaimable", kernelMemValue(nativeResult, "SReclaimable"));
        append(out, "内核 SwapTotal", kernelMemValue(nativeResult, "SwapTotal"));
        append(out, "内核 SwapFree", kernelMemValue(nativeResult, "SwapFree"));
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            append(out, "数据分区总空间", bytes(fs.getTotalBytes()));
            append(out, "数据分区可用空间", bytes(fs.getAvailableBytes()));
        } catch (Throwable error) {
            append(out, "数据分区", "不可读取: " + error.getClass().getSimpleName());
        }
        return out.toString();
    }

    private String buildDisplayDeepSection(String nativeResult) {
        StringBuilder out = new StringBuilder();
        append(out, "读取时间", now());
        append(out, "数据来源", "Android Display/DisplayManager；显示的是系统逻辑显示状态");
        Display display = currentDisplay();
        if (display == null) {
            append(out, "逻辑显示", "不可读取");
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            append(out, "显示名称", safe(display.getName()));
            append(out, "显示 ID", String.valueOf(display.getDisplayId()));
            append(out, "逻辑分辨率", metrics.widthPixels + " × " + metrics.heightPixels + " px");
            append(out, "逻辑密度", String.format(Locale.US, "%.2f（%d dpi）", metrics.density, metrics.densityDpi));
            append(out, "系统 xdpi / ydpi", String.format(Locale.US, "%.2f / %.2f", metrics.xdpi, metrics.ydpi));
            append(out, "当前刷新率", String.format(Locale.US, "%.2f Hz", display.getRefreshRate()));
            append(out, "显示状态", displayStateName(Build.VERSION.SDK_INT >= 20 ? display.getState() : -1));
            append(out, "按系统 dpi 估算对角线", estimatedDiagonal(metrics));
            if (Build.VERSION.SDK_INT >= 23) {
                append(out, "系统支持模式", displayModesCompat(display));
            } else {
                append(out, "系统支持模式", "需要 API 23+；当前 API " + Build.VERSION.SDK_INT + " 未提供公开字段");
            }
            append(out, "广色域（系统检测）", wideColorGamutCompat(display));
            append(out, "HDR 支持类型", hdrTypesCompat(display));
            append(out, "10-bit 显示", tenBitCapabilityCompat(display));
        }
        append(out, "高色域测试说明", "系统检测表示 Android 显示栈报告的广色域能力；“屏幕测试”中的色域图案仅用于视觉参考，实际覆盖率/色准需色度计。 ");
        append(out, "面板供应商/型号", "普通 Android API 未公开；仅在下方候选内核节点实际可读时显示原始结果");
        append(out, "真实性说明", "分辨率、密度和刷新率是当前逻辑显示状态；估算对角线不等于物理面板实测尺寸。广色域、HDR 与原生 10-bit 不是同一个结论。");
        append(out, "候选内核节点", nativeSectionHasReadableData(nativeResult, "[显示内核候选节点]") ? "存在可读或错误原文，见“屏幕内核候选数据”" : "未发现");
        return out.toString();
    }

    private String buildBatteryDeepSection(String nativeResult) {
        StringBuilder out = new StringBuilder();
        append(out, "读取时间", now());
        append(out, "Android 来源", "ACTION_BATTERY_CHANGED / BatteryManager");
        Intent intent = batteryIntent();
        if (intent != null) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            append(out, "电量（level/scale）", level >= 0 && scale > 0 ? level + " / " + scale + "（" + (level * 100 / scale) + "%）" : "不可读取");
            append(out, "充电状态", batteryStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)));
            append(out, "健康状态（枚举）", batteryHealth(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)));
            append(out, "外接电源", pluggedName(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)));
            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            append(out, "电压（广播）", voltage >= 0 ? voltage + " mV" : "不可读取");
            int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            append(out, "温度（广播）", temperature != Integer.MIN_VALUE ? String.format(Locale.US, "%.1f °C（原始 %d，单位 0.1 °C）", temperature / 10.0, temperature) : "不可读取");
            append(out, "电池技术", safe(intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)));
        } else {
            append(out, "电池广播", "不可读取");
        }
        BatteryManager manager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        append(out, "BatteryManager 属性", "API 21+；不可用时显示实际状态");
        append(out, "电池容量属性", batteryIntProperty(manager, BatteryManager.BATTERY_PROPERTY_CAPACITY, "%"));
        append(out, "即时电流属性", batteryIntProperty(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, "µA"));
        append(out, "平均电流属性", batteryIntProperty(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE, "µA"));
        append(out, "充电计数属性", batteryIntProperty(manager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, "µAh"));
        append(out, "能量计数属性", batteryLongProperty(manager, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER, "nWh"));
        append(out, "内核来源", "/sys/class/power_supply/*；实际节点和错误见“电池 power_supply 原文”");
        append(out, "内核 capacity", nativeBatteryValue(nativeResult, "capacity"));
        append(out, "内核 voltage_now", nativeBatteryValue(nativeResult, "voltage_now"));
        append(out, "内核 current_now", nativeBatteryValue(nativeResult, "current_now"));
        append(out, "内核 temp", nativeBatteryValue(nativeResult, "temp"));
        append(out, "内核 charge_counter", nativeBatteryValue(nativeResult, "charge_counter"));
        append(out, "内核 cycle_count", nativeBatteryValue(nativeResult, "cycle_count"));
        append(out, "健康容量（SOH）", batterySoh(nativeResult));
        append(out, "真实性说明", "当前电量不是健康百分比；只有设备同时公开 charge_full 与 charge_full_design 时才计算设备电量计估算值。");
        return out.toString();
    }

    private String buildCopyReport(String nativeResult, String gpuResult, String vulkanResult) {
        StringBuilder out = new StringBuilder();
        out.append("[设备与系统]\n").append(buildDeviceSummary());
        out.append("\n[性能与芯片]\n").append(buildPerformanceHardwareSummary(nativeResult, gpuResult));
        out.append("\n[图形 API]\n").append(buildGraphicsApiSummary(gpuResult, vulkanResult));
        out.append("\n[内存与存储]\n").append(buildMemorySummary(nativeResult));
        out.append("\n[电池]\n").append(buildBatterySummary(nativeResult));
        out.append("\n[屏幕]\n").append(buildDisplaySummary());
        out.append("\n[硬件能力]\n").append(CapabilityProbe.collect(this));
        out.append("\n[技术原始数据]\n").append(buildTechnicalRaw(nativeResult, gpuResult, vulkanResult));
        return out.toString();
    }

    private String buildTechnicalRaw(String nativeResult, String gpuResult, String vulkanResult) {
        return "[GPU / OpenGL ES 原始报告]\n" + safe(gpuResult)
                + "\n\n[Vulkan 原生报告]\n" + safe(vulkanResult)
                + "\n\n[NDK / 内核公开节点]\n" + safe(nativeResult)
                + "\n\n[说明]\n这些是用于核验的原始输出。节点不可读、字段缺失或值为厂商实现时，应用不会用数据库或机型资料补写。";
    }

    private String buildFrameworkData(String nativeResult) {
        StringBuilder out = new StringBuilder(5200);
        out.append("[Android Framework API]\n");
        append(out, "应用最低 API", "21（Android 5.0）");
        out.append(buildDeviceSystemSection());
        out.append(buildAbiArchitectureSection(nativeResult));
        append(out, "Java 可用处理器", String.valueOf(Runtime.getRuntime().availableProcessors()));
        out.append(buildDisplayDeepSection(nativeResult));
        out.append(buildMemoryStorageSection(nativeResult));
        out.append(buildBatteryDeepSection(nativeResult));
        append(out, "开发者选项 ADB", isAdbEnabled() ? "已启用" : "未启用或不可读取");
        return out.toString();
    }

    private String nativeSummary(String nativeResult) {
        int rawStart = nativeResult.indexOf("[内核导出原文");
        return rawStart >= 0 ? nativeResult.substring(0, rawStart).trim() : nativeResult.trim();
    }

    private String nativeBlock(String nativeResult, String path) {
        String marker = "-- " + path;
        int markerStart = nativeResult.indexOf(marker);
        if (markerStart < 0) return "未找到该内核导出项。";
        int dataStart = nativeResult.indexOf('\n', markerStart);
        if (dataStart < 0) return "该内核导出项没有可显示内容。";
        dataStart++;
        int nextBlock = nativeResult.indexOf("\n-- ", dataStart);
        int boundary = nativeResult.indexOf("\n[数据边界]", dataStart);
        int dataEnd = nativeResult.length();
        if (nextBlock >= 0) dataEnd = nextBlock;
        if (boundary >= 0 && boundary < dataEnd) dataEnd = boundary;
        return nativeResult.substring(dataStart, dataEnd).trim();
    }

    private String nativeSection(String nativeResult, String startMarker, String endMarker) {
        int start = nativeResult.indexOf(startMarker);
        if (start < 0) return "未找到该采集段。";
        int contentStart = nativeResult.indexOf('\n', start);
        if (contentStart < 0) return "该采集段没有可显示内容。";
        int end = nativeResult.indexOf(endMarker, contentStart + 1);
        return nativeResult.substring(contentStart + 1, end >= 0 ? end : nativeResult.length()).trim();
    }

    private boolean nativeSectionHasReadableData(String nativeResult, String marker) {
        String section = nativeSection(nativeResult, marker, "[数据边界]");
        return !section.startsWith("未找到") && !section.startsWith("该采集段没有");
    }

    private String kernelMemValue(String nativeResult, String key) {
        String raw = keyValue(nativeBlock(nativeResult, "/proc/meminfo"), key);
        return raw == null ? "内核未提供或不可读" : raw + "（/proc/meminfo 原始值）";
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
        } catch (Throwable ignored) {
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
        try {
            Object supported = Display.class.getMethod("isWideColorGamut").invoke(display);
            return Boolean.TRUE.equals(supported) ? "系统报告支持广色域（Wide Color Gamut）" : "系统报告不支持广色域";
        } catch (Throwable error) {
            return "不可读取: " + error.getClass().getSimpleName();
        }
    }

    private static String tenBitCapabilityCompat(Display display) {
        String hdr = hdrTypesCompat(display);
        if (Build.VERSION.SDK_INT < 24) {
            return "系统未提供 HDR 类型公开检测；无面板原生 10-bit 通用 API";
        }
        if (hdr.startsWith("不可读取") || hdr.startsWith("系统未报告")) {
            return "没有可用的系统 HDR 类型报告；无面板原生 10-bit 通用 API";
        }
        return "系统报告 HDR 能力（" + hdr + "）；但 Android 没有普通应用通用的面板原生 10-bit 确认 API";
    }

    private static String hdrTypesCompat(Display display) {
        try {
            Object capabilities = Display.class.getMethod("getHdrCapabilities").invoke(display);
            if (capabilities == null) return "系统未报告 HDR 支持";
            int[] types = (int[]) capabilities.getClass().getMethod("getSupportedHdrTypes").invoke(capabilities);
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
        } catch (Throwable error) {
            return "不可读取: " + error.getClass().getSimpleName();
        }
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

    private String batteryIntProperty(BatteryManager manager, int property, String unit) {
        if (manager == null) return "电池服务不可用";
        try {
            int value = manager.getIntProperty(property);
            return value == Integer.MIN_VALUE ? "设备未提供" : value + (TextUtils.isEmpty(unit) ? "" : " " + unit) + "（原始值）";
        } catch (Throwable error) {
            return "不可读取: " + error.getClass().getSimpleName();
        }
    }

    private String batteryLongProperty(BatteryManager manager, int property, String unit) {
        if (manager == null) return "电池服务不可用";
        if (Build.VERSION.SDK_INT < 28) return "需要 API 28+；当前 API " + Build.VERSION.SDK_INT;
        try {
            long value = (Long) BatteryManager.class.getMethod("getLongProperty", int.class).invoke(manager, property);
            return value == Long.MIN_VALUE ? "设备未提供" : value + (TextUtils.isEmpty(unit) ? "" : " " + unit) + "（原始值）";
        } catch (Throwable error) {
            return "不可读取: " + error.getClass().getSimpleName();
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

    private String nativeBoundary(String nativeResult) {
        int start = nativeResult.indexOf("[数据边界]");
        if (start >= 0) return nativeResult.substring(start).trim();
        return "本应用只读取普通应用权限下公开可读的数据；无法读取的节点会报告实际错误。";
    }

    private void updateOverview() {
        deviceValue.setText(safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
        systemValue.setText("Android " + safe(Build.VERSION.RELEASE) + " · API " + Build.VERSION.SDK_INT);
        cpuValue.setText(Runtime.getRuntime().availableProcessors() + " 个逻辑核心");
        memoryValue.setText(availableMemory());
        storageValue.setText(availableStorage());
        batteryValue.setText(currentBattery() + " · " + currentBatteryStatus());
        updateStatusDashboard();
    }

    private void updateStatusDashboard() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(info);
                int used = percent(info.totalMem - info.availMem, info.totalMem);
                if (memoryProgress != null) memoryProgress.setProgress(used);
                if (memoryProgressText != null) memoryProgressText.setText("已用 " + used + "% · 可用 " + bytes(info.availMem));
            }
        } catch (Throwable ignored) { if (memoryProgressText != null) memoryProgressText.setText("系统未提供内存状态"); }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = fs.getTotalBytes();
            long available = fs.getAvailableBytes();
            int used = percent(total - available, total);
            if (storageProgress != null) storageProgress.setProgress(used);
            if (storageProgressText != null) storageProgressText.setText("已用 " + used + "% · 可用 " + bytes(available));
        } catch (Throwable ignored) { if (storageProgressText != null) storageProgressText.setText("系统未提供存储状态"); }
        try {
            Intent intent = batteryIntent();
            int level = intent == null ? -1 : intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent == null ? -1 : intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int charge = level >= 0 && scale > 0 ? level * 100 / scale : 0;
            if (batteryProgress != null) batteryProgress.setProgress(charge);
            if (batteryProgressText != null) batteryProgressText.setText(level >= 0 ? charge + "% · " + currentBatteryStatus() : "系统未提供电量状态");
        } catch (Throwable ignored) { if (batteryProgressText != null) batteryProgressText.setText("系统未提供电量状态"); }
    }

    private static int percent(long part, long total) {
        if (total <= 0) return 0;
        return (int) Math.max(0, Math.min(100, part * 100 / total));
    }

    private String buildFunctionTestSummary() {
        Display display = currentDisplay();
        String colorCapability = display == null ? "系统未提供可检测的默认显示" : wideColorGamutCompat(display);
        return "已提供 13 项屏幕与触控测试入口。\n\n"
                + "系统广色域检测：" + colorCapability + "。\n\n"
                + "可直接开始：坏点/亮点/暗点、灰阶、对比度、饱和度、触控轨迹、几何边缘、RGB 渐变、PWM 闪烁辅助、响应拖影、残影老化、色域参考、视角与色域图案。\n\n"
                + "点击首页“屏幕测试”进入。坏点、PWM、拖影、残影、视角与色域属于标准图案和人工/仪器观察项目；应用不会把观察结果伪装成自动硬件诊断。";
    }

    private String availableMemory() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(info);
                return bytes(info.availMem);
            }
        } catch (Throwable ignored) {
            // Detailed section holds the actual unavailable state.
        }
        return "不可读取";
    }

    private String availableStorage() {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            return bytes(fs.getAvailableBytes());
        } catch (Throwable ignored) {
            return "不可读取";
        }
    }

    private Intent batteryIntent() {
        return registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private String currentBattery() {
        try {
            Intent intent = batteryIntent();
            if (intent == null) return "不可读取";
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return level >= 0 && scale > 0 ? (level * 100 / scale) + "%" : "未知";
        } catch (Throwable ignored) {
            return "不可读取";
        }
    }

    private String currentBatteryStatus() {
        try {
            Intent intent = batteryIntent();
            return intent == null ? "不可读取" : batteryStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
        } catch (Throwable ignored) {
            return "不可读取";
        }
    }

    private String firstAbi() {
        return Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "未知 ABI";
    }

    private boolean isAdbEnabled() {
        try {
            return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void copyReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Device Probe Report", fullReport));
            Toast.makeText(this, "全部报告已复制到剪贴板", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "剪贴板服务不可用", Toast.LENGTH_SHORT).show();
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

    private static String formatBuildTime(long time) {
        return time > 0 ? DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault()).format(new Date(time)) : "未知";
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
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
