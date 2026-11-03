package com.deviceprobe.inspector;

import android.app.AlertDialog;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * All dialogs of the main screen: search, the report menu, per-row source
 * explanations and the long enumerations (camera cards, sensor list).
 */
final class DialogsUi {
    interface Host {
        void onSearchSubmitted(String query);

        void onCopyReport();

        void onShareReport();

        void onExportText();

        void onExportJson();

        void onClearSearchHighlight();
    }

    private final Ui ui;
    private final Host host;

    DialogsUi(Ui ui, Host host) {
        this.ui = ui;
        this.host = host;
    }

    // ---------------------------------------------------------------- search & report

    void showSearch() {
        final EditText input = new EditText(ui.context());
        input.setSingleLine(true);
        input.setHint("例如：GPU、Vulkan、DDR、电池、温度、摄像头、传感器");
        input.setTextColor(Ui.TEXT_PRIMARY);
        input.setHintTextColor(Ui.TEXT_SECONDARY);
        input.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(8));
        new AlertDialog.Builder(ui.context())
                .setTitle("搜索设备信息")
                .setMessage("仅搜索本次已经采集的本机数据，不联网、不查询机型资料。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("搜索", (dialog, which) -> host.onSearchSubmitted(input.getText().toString()))
                .show();
    }

    void showReportMenu(boolean reportReady) {
        if (!reportReady) {
            toast("正在读取信息，请稍候");
            return;
        }
        final String[] actions = {"复制全部信息", "分享设备报告", "导出 TXT", "导出结构化 JSON", "清除搜索高亮"};
        new AlertDialog.Builder(ui.context())
                .setTitle("设备报告")
                .setItems(actions, (dialog, index) -> {
                    switch (index) {
                        case 0: host.onCopyReport(); break;
                        case 1: host.onShareReport(); break;
                        case 2: host.onExportText(); break;
                        case 3: host.onExportJson(); break;
                        default: host.onClearSearchHighlight(); break;
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- source explanation

    void showRowSource(InfoModel.Row row) {
        String detail = row.sourceNote != null ? row.sourceNote : genericSourceNote(row.source);
        StringBuilder message = new StringBuilder();
        message.append("当前值：\n").append(row.value)
                .append("\n\n来源：\n").append(sourceLongName(row.source))
                .append("\n\n可信度：\n").append(row.source.label);
        if (row.source == InfoModel.Source.ESTIMATED || row.source == InfoModel.Source.UNKNOWN) {
            message.append("\n\n边界：\n").append(detail);
        } else {
            message.append("\n\n说明：\n").append(detail);
        }
        new AlertDialog.Builder(ui.context())
                .setTitle("数据来源 · " + row.label)
                .setMessage(message.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    private static String sourceLongName(InfoModel.Source source) {
        switch (source) {
            case DRIVER: return "当前图形驱动（EGL/OpenGL ES 上下文或 Vulkan loader）直接报告";
            case KERNEL: return "C/NDK 读取的普通应用可访问 /proc、/sys 或 POSIX 接口";
            case CAMERA: return "Camera2 CameraCharacteristics 公开 metadata";
            case ESTIMATED: return "由明确公式从公开值计算，不等同于硬件实测";
            case UNKNOWN: return "系统未公开，或不能被普通应用可靠推断";
            default: return "Android framework 公开返回的运行时状态或兼容性声明";
        }
    }

    private static String genericSourceNote(InfoModel.Source source) {
        switch (source) {
            case DRIVER: return "来自实际创建的离屏 EGL/OpenGL ES 上下文或动态加载的 libvulkan.so；若为软件渲染器也会如实显示。";
            case KERNEL: return "内核公开节点原文与读取失败原因位于「技术原始数据」。";
            case CAMERA: return "应用不打开相机，也不申请 CAMERA 权限。";
            case ESTIMATED: return "估算公式与输入值在对应说明中给出，不等同于仪器测量。";
            case UNKNOWN: return "应用不依据机型数据库补全未知字段。";
            default: return "来自 Android framework 的当前公开信息；不会根据机型数据库补全未知字段。";
        }
    }

    // ---------------------------------------------------------------- long lists

    void showCameraDetails(CapabilityModel capability) {
        if (capability == null) {
            showLongText("Camera2 公开信息", "尚未完成采集，请等待刷新结束后重试。");
            return;
        }
        if (capability.cameraError != null) {
            showLongText("Camera2 公开信息", "摄像头枚举不可用: " + capability.cameraError);
            return;
        }
        if (capability.cameras.isEmpty()) {
            showLongText("Camera2 公开信息", "系统未报告可枚举的摄像头；功能特性见「硬件能力」一节。");
            return;
        }
        ScrollView scroll = new ScrollView(ui.context());
        LinearLayout list = new LinearLayout(ui.context());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(14));
        for (CapabilityModel.Camera camera : capability.cameras) addCameraLayer(list, camera);
        scroll.addView(list);
        new AlertDialog.Builder(ui.context())
                .setTitle("Camera2 详细参数")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void addCameraLayer(LinearLayout list, CapabilityModel.Camera camera) {
        String title = "cameraId " + camera.id + " · " + camera.facingLabel;
        List<String[]> basic = new ArrayList<>();
        basic.add(new String[]{"方向", camera.facingLabel + "（Camera2 LENS_FACING）"});
        if (camera.flash != null) basic.add(new String[]{"闪光灯", camera.flashText()});
        if (camera.hardwareLevel != null) basic.add(new String[]{"Camera2 硬件级别", camera.hardwareLevel});
        if (camera.error != null) basic.add(new String[]{"读取结果", "metadata 不可读取（" + camera.error + "）"});
        addCameraPanel(list, "基本信息 · " + title, basic);
        addCameraFieldPanel(list, "光学参数", camera.optics);
        addCameraFieldPanel(list, "拍摄能力", camera.capture);
        addCameraFieldPanel(list, "Camera2 硬件能力", camera.hardware);
    }

    private void addCameraFieldPanel(LinearLayout list, String title, List<CapabilityModel.Field> fields) {
        if (fields.isEmpty()) return;
        List<String[]> rows = new ArrayList<>();
        for (CapabilityModel.Field field : fields) rows.add(new String[]{field.label, field.value});
        addCameraPanel(list, title, rows);
    }

    private void addCameraPanel(LinearLayout list, String title, List<String[]> rows) {
        if (rows.isEmpty()) return;
        LinearLayout panel = new LinearLayout(ui.context());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ui.dp(13), ui.dp(12), ui.dp(13), ui.dp(12));
        panel.setBackground(ui.card(Ui.SURFACE, ui.dp(14)));
        TextView heading = ui.text(title, 14, Ui.TEXT_PRIMARY, true);
        panel.addView(heading, ui.margins(Ui.matchParent(), Ui.wrapContent(), 0, 0, 0, ui.dp(7)));
        addCameraRows(panel, rows);
        list.addView(panel, ui.margins(Ui.matchParent(), Ui.wrapContent(), 0, 0, 0, ui.dp(10)));
    }

    private void addCameraRows(LinearLayout panel, List<String[]> rows) {
        for (String[] item : rows) {
            LinearLayout row = new LinearLayout(ui.context());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, ui.dp(2), 0, ui.dp(2));
            panel.addView(row, ui.match());
            TextView label = ui.text(item[0], 12, Ui.TEXT_SECONDARY, false);
            TextView value = ui.text(item[1], 12, Ui.TEXT_PRIMARY, false);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.addView(label, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 0.9f));
            value.setGravity(Gravity.RIGHT);
            row.addView(value, new LinearLayout.LayoutParams(0, Ui.wrapContent(), 1.1f));
        }
    }

    void showSensorList(CapabilityModel capability) {
        if (capability == null) {
            showLongText("传感器公开清单", "尚未完成采集，请等待刷新结束后重试。");
            return;
        }
        if (capability.sensorError != null) {
            showLongText("传感器公开清单", capability.sensorError);
            return;
        }
        StringBuilder out = new StringBuilder();
        out.append("系统枚举的传感器数量: ").append(capability.sensorTotal).append('\n');
        if (capability.sensors.isEmpty()) {
            out.append("系统未报告静态传感器。\n");
        } else {
            for (CapabilityModel.Sensor sensor : capability.sensors) {
                out.append('\n').append(sensor.renderText());
            }
        }
        if (capability.dynamicError != null) {
            out.append("\n动态传感器: 不可读取（").append(capability.dynamicError).append("）");
        } else if (capability.dynamicSupported) {
            out.append("\n动态传感器（API 24+ 回调窗口）: ").append(capability.dynamicSensors.size()).append(" 个");
            for (CapabilityModel.Sensor sensor : capability.dynamicSensors) {
                out.append("\n- ").append(sensor.type).append(" / ").append(sensor.name)
                        .append(" / ").append(sensor.vendor);
            }
        } else {
            out.append("\n动态传感器: 需要 API 24+ 公开回调");
        }
        showLongText("传感器公开清单", out.toString());
    }

    void showLongText(String title, String text) {
        ScrollView scroll = new ScrollView(ui.context());
        TextView body = new TextView(ui.context());
        body.setText(TextUtils.isEmpty(text) ? "尚未完成采集" : text);
        body.setTextColor(Ui.TEXT_PRIMARY);
        body.setTextSize(12);
        body.setLineSpacing(ui.dp(2), 1.08f);
        body.setTextIsSelectable(true);
        body.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(18));
        scroll.addView(body);
        new AlertDialog.Builder(ui.context())
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    void showDeviceModeCorrection(final DeviceProfile profile) {
        final String[] modes = {"自动识别（推荐）", "按手机界面", "按平板界面", "按电视界面", "按手表界面"};
        new AlertDialog.Builder(ui.context())
                .setTitle("纠正设备类型")
                .setMessage("仅保存本应用的界面偏好，不会修改系统。自动结果和判定依据会保留在设备详情中。")
                .setSingleChoiceItems(modes, profile.selectedModeIndex(), (dialog, selected) -> {
                    DeviceProfile.Kind override = null;
                    if (selected == 1) override = DeviceProfile.Kind.PHONE;
                    else if (selected == 2) override = DeviceProfile.Kind.TABLET;
                    else if (selected == 3) override = DeviceProfile.Kind.TELEVISION;
                    else if (selected == 4) override = DeviceProfile.Kind.WATCH;
                    DeviceProfile.saveOverride(ui.context(), override);
                    dialog.dismiss();
                    onDeviceModeChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void onDeviceModeChanged() {
        if (ui.context() instanceof MainActivity) {
            ((MainActivity) ui.context()).recreate();
        }
    }

    // ---------------------------------------------------------------- misc

    void toast(String message) {
        Toast.makeText(ui.context(), message, Toast.LENGTH_SHORT).show();
    }

    void toastLong(String message) {
        Toast.makeText(ui.context(), message, Toast.LENGTH_LONG).show();
    }
}
