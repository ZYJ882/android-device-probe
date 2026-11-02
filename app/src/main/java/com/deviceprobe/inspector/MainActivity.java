package com.deviceprobe.inspector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main screen. This class only wires lifecycle, threading and the UI pieces
 * together; probes live in the probe classes, parsing and grouping in
 * {@link SectionBuilder}, layout in the UI classes and export in {@link ExportUi}.
 */
public final class MainActivity extends Activity
        implements DashboardUi.Host, SectionUi.Host, DialogsUi.Host {
    private static final String PRIVACY_NOTE =
            "真实性优先：不联网、不申请运行时权限；无法可靠读取的数据会显示“设备未公开”或“无法确认”，不会依据机型猜测。";

    private DeviceProfile profile;
    private Ui ui;
    private DashboardUi dashboard;
    private SectionUi sections;
    private DialogsUi dialogs;
    private ExportUi exporter;
    private ScrollView rootScroll;
    private LinearLayout sectionContainer;

    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean refreshing;
    private volatile boolean destroyed;
    private volatile ProbeSnapshot snapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        profile = DeviceProfile.detect(this);
        ui = new Ui(this, profile);
        dashboard = new DashboardUi(ui, this, profile);
        dialogs = new DialogsUi(ui, this);
        exporter = new ExportUi(ui, probeExecutor);
        setContentView(createContentView());
        sections = new SectionUi(ui, this, rootScroll, sectionContainer);
        refreshProbe();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        uiHandler.removeCallbacksAndMessages(null);
        probeExecutor.shutdownNow();
        exporter.destroy();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Ui.BACKGROUND);
        window.setNavigationBarColor(Ui.BACKGROUND);
    }

    // ---------------------------------------------------------------- layout

    private View createContentView() {
        ScrollView scroll = new ScrollView(this);
        rootScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int horizontalInset = ui.watch() ? ui.dp(10) : (ui.tv() ? ui.dp(38) : ui.dp(18));
        int verticalInset = ui.watch() ? ui.dp(10) : ui.dp(20);
        int bottomInset = ui.watch() ? ui.dp(12) : ui.dp(28);
        root.setPadding(horizontalInset, verticalInset, horizontalInset, bottomInset);
        scroll.addView(root, new ScrollView.LayoutParams(Ui.matchParent(), Ui.wrapContent()));
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(horizontalInset + bars.left, verticalInset + bars.top,
                    horizontalInset + bars.right, bottomInset + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(scroll);

        root.addView(dashboard.buildTopBar());
        root.addView(dashboard.buildHeroCard(), ui.margins(Ui.matchParent(), Ui.wrapContent(),
                0, ui.watch() ? ui.dp(10) : ui.dp(16), 0, 0));
        root.addView(sectionTitle("核心硬件", "最常用信息优先；数值来自本机公开接口"),
                ui.margins(Ui.matchParent(), Ui.wrapContent(), 0, ui.dp(22), 0, ui.dp(10)));
        root.addView(dashboard.buildOverviewGrid());
        root.addView(sectionTitle("快速检测", "直接进入常用项目"),
                ui.margins(Ui.matchParent(), Ui.wrapContent(), 0, ui.dp(22), 0, ui.dp(10)));
        root.addView(dashboard.buildQuickActions());
        if (profile.isSuspectedWatch()) {
            root.addView(dashboard.buildDeviceModeButton(profile),
                    ui.margins(Ui.matchParent(), ui.dp(44), 0, ui.dp(10), 0, 0));
        }
        root.addView(sectionTitle("详细信息", "点击展开；行首色点代表数据来源，点击行查看说明"),
                ui.margins(Ui.matchParent(), Ui.wrapContent(), 0, ui.dp(24), 0, ui.dp(10)));
        sectionContainer = new LinearLayout(this);
        sectionContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(sectionContainer, ui.match());

        TextView privacy = ui.text(PRIVACY_NOTE, 12, Ui.TEXT_SECONDARY, false);
        privacy.setLineSpacing(ui.dp(2), 1.12f);
        privacy.setPadding(ui.dp(2), ui.dp(16), ui.dp(2), 0);
        root.addView(privacy);
        return scroll;
    }

    private View sectionTitle(String titleText, String detailText) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(ui.text(titleText, 17, Ui.TEXT_PRIMARY, true));
        TextView detail = ui.text(detailText, 12, Ui.TEXT_SECONDARY, false);
        detail.setPadding(0, ui.dp(3), 0, 0);
        holder.addView(detail);
        return holder;
    }

    // ---------------------------------------------------------------- probing

    private void refreshProbe() {
        if (destroyed || refreshing) return;
        refreshing = true;
        dashboard.setRefreshing(true);
        probeExecutor.execute(() -> {
            try {
                ProbeSnapshot collected = ProbeSnapshot.collect(MainActivity.this, profile);
                uiHandler.post(() -> applySnapshot(collected));
            } catch (final Exception error) {
                uiHandler.post(() -> finishRefreshWithError(error));
            }
        });
    }

    private void applySnapshot(ProbeSnapshot collected) {
        if (destroyed || isFinishing()) return;
        snapshot = collected;
        dashboard.applySnapshot(collected);
        sections.render(collected);
        refreshing = false;
        dashboard.setRefreshing(false);
        dashboard.setStatus("刚刚更新 · " + DateFormat.getTimeInstance(DateFormat.MEDIUM,
                Locale.getDefault()).format(new Date(collected.collectedAtMillis)));
    }

    private void finishRefreshWithError(Throwable error) {
        if (destroyed || isFinishing()) return;
        refreshing = false;
        dashboard.setRefreshing(false);
        dashboard.setStatus("检测未完成 · " + error.getClass().getSimpleName());
        dialogs.toastLong("设备信息读取未完成，请稍后重试");
    }

    // ---------------------------------------------------------------- DashboardUi.Host

    @Override
    public void onRefreshRequested() {
        refreshProbe();
    }

    @Override
    public void onSearchDialogRequested() {
        dialogs.showSearch();
    }

    @Override
    public void onReportMenuRequested() {
        dialogs.showReportMenu(snapshot != null);
    }

    @Override
    public void onNavigateToSection(String title) {
        sections.navigateTo(title);
    }

    @Override
    public void onCameraDetailsRequested() {
        dialogs.showCameraDetails(snapshot == null ? null : snapshot.capability);
    }

    @Override
    public void onSensorListRequested() {
        dialogs.showSensorList(snapshot == null ? null : snapshot.capability);
    }

    @Override
    public void onDeviceModeCorrectionRequested() {
        dialogs.showDeviceModeCorrection(profile);
    }

    // ---------------------------------------------------------------- SectionUi.Host

    @Override
    public void onRowSourceRequested(InfoModel.Row row) {
        dialogs.showRowSource(row);
    }

    @Override
    public void onSectionAction(int actionId) {
        if (actionId == InfoModel.Section.ACTION_CAMERA_DETAILS) {
            onCameraDetailsRequested();
        } else if (actionId == InfoModel.Section.ACTION_SENSOR_LIST) {
            onSensorListRequested();
        }
    }

    // ---------------------------------------------------------------- DialogsUi.Host

    @Override
    public void onSearchSubmitted(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            dialogs.toast("请输入要搜索的内容");
            return;
        }
        int count = sections.applySearch(normalized);
        if (count == 0) {
            dialogs.toastLong("未在当前已采集数据中找到“" + normalized + "”");
            return;
        }
        dialogs.toast("找到 " + count + " 个相关模块；已展开第一个结果");
    }

    @Override
    public void onCopyReport() {
        if (snapshot != null) exporter.copy(snapshot.textReport);
    }

    @Override
    public void onShareReport() {
        if (snapshot != null) exporter.share(snapshot.textReport);
    }

    @Override
    public void onExportText() {
        startActivityForResult(exporter.createExportIntent(ExportUi.EXPORT_TEXT), ExportUi.EXPORT_TEXT);
    }

    @Override
    public void onExportJson() {
        startActivityForResult(exporter.createExportIntent(ExportUi.EXPORT_JSON), ExportUi.EXPORT_JSON);
    }

    @Override
    public void onClearSearchHighlight() {
        sections.clearHighlight();
    }

    // ---------------------------------------------------------------- export results

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (destroyed || snapshot == null) return;
        if ((requestCode != ExportUi.EXPORT_TEXT && requestCode != ExportUi.EXPORT_JSON)
                || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        exporter.handleExportResult(requestCode, data.getData(), snapshot);
    }
}
