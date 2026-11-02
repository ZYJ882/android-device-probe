package com.deviceprobe.inspector;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * Turns raw probe output into the structured {@link InfoModel} sections shown on the
 * dashboard. Responsibilities kept deliberately narrow: parse once, deduplicate,
 * group related facts, move caveats from values into footnotes and keep the source
 * of every row explicit. Long per-core or per-node details stay in the raw section
 * instead of flooding the overview.
 *
 * <p>Rows that were removed from the visible list are kept as hidden rows through
 * {@code Group.hiddenRow}; hiding is a presentation decision, so the exported keys
 * stay identical no matter what the screen shows.</p>
 */
final class SectionBuilder {
    static final String SECTION_DEVICE = "设备与系统";
    static final String SECTION_CPU = "CPU 与架构";
    static final String SECTION_GRAPHICS = "图形 API";
    static final String SECTION_MEMORY = "内存与存储";
    static final String SECTION_BATTERY = "电池";
    static final String SECTION_DISPLAY = "屏幕";
    static final String SECTION_CAMERA = "相机与传感器";
    static final String SECTION_HARDWARE = "硬件能力";
    static final String SECTION_SECURITY = "安全与运行状态";
    static final String SECTION_RAW = "技术原始数据";

    private final Context context;
    private final DeviceProfile profile;

    private SectionBuilder(Context context, DeviceProfile profile) {
        this.context = context;
        this.profile = profile;
    }

    static List<InfoModel.Section> buildAll(Context context, DeviceProfile profile,
                                            String nativeResult, String gpuResult,
                                            String vulkanResult, CapabilityModel capability) {
        SectionBuilder builder = new SectionBuilder(context, profile);
        List<InfoModel.Section> sections = new ArrayList<>();
        sections.add(builder.buildDeviceSection(nativeResult));
        sections.add(builder.buildCpuSection(nativeResult));
        sections.add(builder.buildGraphicsSection(gpuResult, vulkanResult));
        sections.add(builder.buildMemorySection(nativeResult));
        sections.add(builder.buildBatterySection(nativeResult));
        sections.add(builder.buildDisplaySection(nativeResult));
        sections.add(builder.buildCameraSection(capability));
        sections.add(builder.buildHardwareSection());
        sections.add(builder.buildSecuritySection(nativeResult));
        sections.add(buildRawSection(nativeResult, gpuResult, vulkanResult, capability));
        return sections;
    }

    // ==================================================================== sections

    private InfoModel.Section buildDeviceSection(String nativeResult) {
        InfoModel.Section section = new InfoModel.Section(SECTION_DEVICE, "型号、芯片、系统版本与构建标识");
        InfoModel.Group identity = section.group("身份");
        identity.prominent("device_name", "设备", safe(Build.MANUFACTURER) + " " + safe(Build.MODEL), InfoModel.Source.SYSTEM);
        identity.row("soc_model", "SoC", socModel(nativeResult), InfoModel.Source.SYSTEM,
                "优先读取 Android 12 / API 31 起的 Build.SOC_MODEL 与 Build.SOC_MANUFACTURER；"
                        + "不可用时回退 /proc/cpuinfo 的 Hardware 与 model name 字段（内核导出）。");
        identity.row("android_version", "系统", "Android " + safe(Build.VERSION.RELEASE) + "（API " + Build.VERSION.SDK_INT + "）",
                InfoModel.Source.SYSTEM);
        identity.row("rom_version", "ROM 版本", buildValue(Build.VERSION.INCREMENTAL), InfoModel.Source.SYSTEM,
                "Build.VERSION.INCREMENTAL：厂商自定义的版本串，通常与系统更新包一一对应。");
        if (Build.VERSION.SDK_INT >= 23) {
            identity.row("base_os", "基础版本", buildValue(Build.VERSION.BASE_OS), InfoModel.Source.SYSTEM,
                    "Build.VERSION.BASE_OS：非空时说明该设备基于某个出厂基线做增量更新。");
        }
        identity.row("security_patch", "安全补丁", Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : "当前系统未提供",
                Build.VERSION.SDK_INT >= 23 ? InfoModel.Source.SYSTEM : InfoModel.Source.UNKNOWN);
        identity.row("device_codename", "设备代号", buildValue(Build.DEVICE), InfoModel.Source.SYSTEM);
        identity.row("board_model", "板级型号", boardValue(), InfoModel.Source.SYSTEM,
                "Build.BOARD 与 Build.HARDWARE；刷机、售后与固件匹配时使用这两个字段。");

        InfoModel.Group rom = section.group("构建标识");
        rom.row("build_type", "构建类型", buildValue(Build.TYPE), InfoModel.Source.SYSTEM,
                "user 为正式版；userdebug / eng 通常为工程或调试版本。");
        rom.row("build_id", "构建 ID", buildValue(Build.ID), InfoModel.Source.SYSTEM);
        rom.row("build_fingerprint", "构建指纹", buildValue(Build.FINGERPRINT), InfoModel.Source.SYSTEM,
                "Build.FINGERPRINT：系统用来校验固件一致性的完整字符串。");
        rom.hiddenRow("build_user", "构建者", buildValue(Build.USER), InfoModel.Source.SYSTEM);
        rom.hiddenRow("build_host", "构建主机", buildValue(Build.HOST), InfoModel.Source.SYSTEM);

        InfoModel.Group kind = section.group("设备类型");
        kind.row("device_form_factor", "设备类型", profile.label(), InfoModel.Source.SYSTEM);
        if (profile.isSuspectedWatch() || profile.manualOverride) {
            kind.row("form_factor_evidence", "判定依据", profile.evidence, InfoModel.Source.SYSTEM,
                    "自动判定仅使用系统公开的分辨率、DPI、比例与特性声明，不依据品牌或机型。");
        }
        return section;
    }

    private InfoModel.Section buildCpuSection(String nativeResult) {
        InfoModel.Section section = new InfoModel.Section(SECTION_CPU, "架构、核心结构、频率与 ABI");
        InfoModel.Group basics = section.group("基础");
        basics.row("system_arch", "系统架构", systemArchitecture(), InfoModel.Source.SYSTEM,
                "显示系统首选运行 ABI 对应的架构；设备支持 ABI 是独立列表，两者含义不同。");
        basics.row("kernel_arch", "内核架构", nativeValue(nativeResult, "uname.machine"), InfoModel.Source.KERNEL,
                "来自 C/NDK uname() 的内核报告值。");
        basics.row("kernel_version", "内核版本", nativeValue(nativeResult, "uname.release"), InfoModel.Source.KERNEL,
                "来自 uname().release；完整内核构建串见「技术原始数据」中的 /proc/version。");
        basics.prominent("logical_cores", "逻辑核心", nativeValue(nativeResult, "POSIX 在线逻辑 CPU") + " 个", InfoModel.Source.KERNEL);

        String cpuRaw = nativeSection(nativeResult, "[CPU 深度原始节点]", "[内核导出原文");
        String structure = coreStructure(cpuRaw);
        if (structure != null) {
            basics.row("core_layout", "核心结构", structure, InfoModel.Source.KERNEL,
                    "按 /sys/devices/system/cpu/cpuN/topology 的 cluster_id 分组统计；"
                            + "无该节点时回退 core_siblings_list。");
        }
        String online = nativeValue(nativeResult, "/sys CPU online");
        if (!"不可读取".equals(online)) {
            basics.row("online_cores", "在线核心", onlineText(online), InfoModel.Source.KERNEL,
                    "标签为在线核心数量，括号内保留 /sys/devices/system/cpu/online 的原始 CPU 号列表。");
        }

        InfoModel.Group frequencies = section.group("频率（系统公开值）");
        String table = frequencyTable(cpuRaw);
        if (table != null) {
            frequencies.row("freq_distribution", "频率分布", table, InfoModel.Source.KERNEL,
                    "按每核 cpuinfo_max_freq 分组；频率相同的核心归为一组，不等同于物理簇划分。");
        }
        String current = frequencyRange(cpuRaw, "scaling_cur_freq_khz");
        String maximum = frequencyRange(cpuRaw, "cpuinfo_max_freq_khz");
        frequencies.row("freq_current", "当前频率", current == null ? "设备未公开或当前应用不可读" : current, InfoModel.Source.KERNEL,
                "各核心瞬时值可能不同；此处显示公开范围内的最小–最大值，逐核心原始值见「技术原始数据」。");
        frequencies.row("freq_max", "最高频率", maximum == null ? "设备未公开或当前应用不可读" : maximum, InfoModel.Source.KERNEL,
                "cpuinfo_max_freq 公开上限；逐核心原始值见「技术原始数据」。");
        String minimum = frequencyRange(cpuRaw, "scaling_min_freq_khz");
        if (minimum != null) {
            frequencies.row("freq_min", "最低频率", minimum, InfoModel.Source.KERNEL,
                    "scaling_min_freq 公开下限；被厂商抬高时通常意味着该核被人为锁频。");
        }
        String governor = distinctValues(cpuRaw, "scaling_governor");
        if (governor != null) {
            frequencies.row("cpufreq_governor", "调频策略", governor, InfoModel.Source.KERNEL,
                    "scaling_governor：performance / schedutil / powersave 等，直接决定升降频策略。");
        }
        String driver = distinctValues(cpuRaw, "scaling_driver");
        if (driver != null) {
            frequencies.row("cpufreq_driver", "调频驱动", driver, InfoModel.Source.KERNEL,
                    "scaling_driver：具体实现调频的驱动名（如 schedutil、msm、mtk_cpufreq）。");
        }

        InfoModel.Group abi = section.group("位数与 ABI");
        abi.row("system_bitness", "系统位数", androidSystemBitness(), InfoModel.Source.SYSTEM,
                "优先读取 Android 运行时（VMRuntime），不可用时按系统文件与 ABI 回退判断；回退依据保留在值中。");
        abi.row("binder_bitness", "Binder 位数", binderBitness(), InfoModel.Source.KERNEL,
                "通过只读 Binder 驱动的 BINDER_VERSION ioctl 判断（协议 7/8）；节点不可读时显示无法确认。");
        abi.row("process_bitness", "进程位数", processBitness(), InfoModel.Source.KERNEL, "由 NDK sizeof(void*) 报告。");
        abi.row("process_abi", "进程 ABI", nativeValue(nativeResult, "native ABI"), InfoModel.Source.KERNEL,
                "本 APK 实际加载的 NDK 库 ABI。");
        abi.row("supported_abis", "设备支持 ABI", join(Build.SUPPORTED_ABIS), InfoModel.Source.SYSTEM);
        abi.hiddenRow("primary_abi", "设备首选 ABI", firstAbi(Build.SUPPORTED_ABIS), InfoModel.Source.SYSTEM);
        abi.hiddenRow("supported_abis_32", "支持的 32 位 ABI", joinOrUnavailable(Build.SUPPORTED_32_BIT_ABIS), InfoModel.Source.SYSTEM);
        abi.hiddenRow("supported_abis_64", "支持的 64 位 ABI", joinOrUnavailable(Build.SUPPORTED_64_BIT_ABIS), InfoModel.Source.SYSTEM);
        abi.hiddenRow("recommended_abi", "安装推荐 ABI", recommendedAbi(), InfoModel.Source.SYSTEM);

        section.footnote("频率为系统公开的瞬时值或上下限，随负载变化；本 APK 打包 ABI 为 armeabi-v7a、arm64-v8a、x86_64。"
                + "ABI 分项（首选 / 32 位 / 64 位 / 安装推荐）只保留在导出的 JSON 报告中，界面不再逐行展示。"
                + "完整驱动与原始节点见「技术原始数据」。");
        return section;
    }

    private InfoModel.Section buildGraphicsSection(String gpuResult, String vulkanResult) {
        InfoModel.Section section = new InfoModel.Section(SECTION_GRAPHICS, "OpenGL ES 与 Vulkan 驱动实际报告");
        InfoModel.Group gl = section.group("OpenGL ES");
        gl.prominent("gl_renderer", "渲染器", gpuValue(gpuResult, "GL_RENDERER"), InfoModel.Source.DRIVER);
        gl.row("gl_vendor", "厂商", gpuValue(gpuResult, "GL_VENDOR"), InfoModel.Source.DRIVER);
        gl.row("gl_version", "版本", gpuValue(gpuResult, "GL_VERSION"), InfoModel.Source.DRIVER);
        gl.row("glsl_version", "GLSL 版本", gpuValue(gpuResult, "GLSL 版本"), InfoModel.Source.DRIVER);
        gl.row("gl_texture_compression", "压缩纹理支持", gpuValue(gpuResult, "GL 纹理压缩"), InfoModel.Source.DRIVER,
                "由驱动报告的实际扩展列表判断；ASTC / ETC2 / S3TC 决定能直接加载哪些压缩纹理资源。");
        gl.row("gl_extension_count", "扩展数量", gpuValue(gpuResult, "GL 扩展数量"), InfoModel.Source.DRIVER);
        gl.row("gl_key_extensions", "主要扩展", gpuValue(gpuResult, "GL 扩展示例"), InfoModel.Source.DRIVER,
                "扩展数量本身不能说明兼容性，此处列出驱动报告的前若干项，完整列表见「技术原始数据」。");

        String vulkan = safe(vulkanResult);
        InfoModel.Group vk = section.group("Vulkan");
        String deviceName = firstLineValue(vulkan, "设备0 名称:");
        vk.row("vulkan_system_features", "系统特性", orUnavailable(firstLineValue(vulkan, "系统 Vulkan 特性:")), InfoModel.Source.SYSTEM,
                "Android framework 的 Vulkan feature 声明，属于兼容性信号。");
        vk.row("vulkan_instance_api", "实例 API 版本", orUnavailable(firstLineValue(vulkan, "Vulkan API 版本:")), InfoModel.Source.DRIVER,
                "由 loader 通过 vkEnumerateInstanceVersion 报告；低于 1.1 表示不支持 Vulkan 1.1 特性。");
        vk.row("vulkan_physical_devices", "物理设备", orUnavailable(firstLineValue(vulkan, "Vulkan 物理设备数量:")), InfoModel.Source.DRIVER);
        vk.row("vulkan_device_name", "设备名称", deviceName == null ? "未枚举到物理设备" : deviceName, InfoModel.Source.DRIVER);
        vk.row("vulkan_device_type", "设备类型", orUnavailable(firstLineValue(vulkan, "设备0 类型:")), InfoModel.Source.DRIVER,
                "集成 GPU 与独立 GPU 的区分来自驱动自报，不代表性能等级。");
        vk.row("vulkan_vendor_device_id", "vendorID / deviceID", orUnavailable(firstLineValue(vulkan, "设备0 vendorID/deviceID:")),
                InfoModel.Source.DRIVER);
        vk.row("vulkan_driver_version", "API / 驱动版本", orUnavailable(firstLineValue(vulkan, "设备0 API/驱动版本:")), InfoModel.Source.DRIVER);
        vk.row("vulkan_queue_families", "队列族数量", orUnavailable(firstLineValue(vulkan, "设备0 队列族数量:")), InfoModel.Source.DRIVER);
        vk.row("vk_extension_count", "扩展数量", orUnavailable(firstLineValue(vulkan, "设备0 扩展数量:")), InfoModel.Source.DRIVER);

        section.footnote("OpenGL ES 与 Vulkan 是两套独立图形 API；以上结果均来自当前驱动或系统特性实际报告，"
                + "不代表所有游戏接口一定可用。Android 7.0 / API 24 之前没有公开 Vulkan 支持基线。");
        return section;
    }

    private InfoModel.Section buildMemorySection(String nativeResult) {
        InfoModel.Section section = new InfoModel.Section(SECTION_MEMORY, "内存、存储与公开的内存类型");
        InfoModel.Group memory = section.group("内存");
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            memory.qtyProminent("ram_total", "总内存", (double) info.totalMem, "B", bytes(info.totalMem),
                    InfoModel.Source.SYSTEM);
            memory.qty("ram_available", "可用内存", (double) info.availMem, "B", bytes(info.availMem),
                    InfoModel.Source.SYSTEM, null);
            memory.meter("ram_usage", "内存使用", percent(info.totalMem - info.availMem, info.totalMem) + "%",
                    percent(info.totalMem - info.availMem, info.totalMem), InfoModel.Source.SYSTEM);
            memory.row("memory_pressure", "当前状态", info.lowMemory ? "内存偏紧" : "正常", InfoModel.Source.SYSTEM);
            memory.row("swap_total", "Swap / ZRAM", swapText(nativeResult), InfoModel.Source.KERNEL,
                    "来自 /proc/meminfo 的 SwapTotal；ZRAM 以 swap 形式统计，直接决定可驻留的后台数量。");
        } else {
            memory.row("ram_total", "系统内存", "不可读取", InfoModel.Source.UNKNOWN);
        }
        String pss = processPss();
        if (pss != null) {
            memory.row("app_pss", "本应用内存占用", pss, InfoModel.Source.SYSTEM,
                    "Debug.MemoryInfo 的 TotalPss；只反映本应用自身，不代表整机占用。");
        }

        InfoModel.Group storage = section.group("存储");
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = fs.getTotalBytes();
            long available = fs.getAvailableBytes();
            storage.qtyProminent("storage_total", "存储总量", (double) total, "B", bytes(total),
                    InfoModel.Source.SYSTEM, "StatFs 数据分区口径。");
            storage.qty("storage_available", "可用空间", (double) available, "B", bytes(available),
                    InfoModel.Source.SYSTEM, "StatFs 数据分区口径。");
            storage.meter("storage_usage", "存储使用", percent(total - available, total) + "%",
                    percent(total - available, total), InfoModel.Source.SYSTEM);
            storage.qty("storage_block_size", "文件系统块大小", (double) fs.getBlockSizeLong(), "B",
                    bytes(fs.getBlockSizeLong()), InfoModel.Source.SYSTEM,
                    "StatFs.getBlockSizeLong()；影响大文件写入的碎片表现。");
        } catch (Throwable error) {
            storage.row("storage_total", "存储空间", "不可读取", InfoModel.Source.UNKNOWN);
        }

        InfoModel.Group memoryType = section.group("内存类型");
        String ddrRaw = nativeSection(nativeResult, "[DDR 内存类型候选]", "[电池 power_supply 原始节点]");
        memoryType.row("ram_type", "DDR 内存类型", ddrConclusion(ddrRaw), InfoModel.Source.KERNEL,
                "仅在公开字段明确包含 DDR、LPDDR 或 HBM 时确认；候选原始节点见「技术原始数据」。");
        String memorySection = nativeSection(nativeResult, "[内存硬件厂商候选]", "[DDR 内存类型候选]");
        String memoryDevice = firstLineValue(memorySection, "/phys_device:");
        if (memoryDevice != null) {
            memoryType.row("ram_device_node", "内存硬件名称（候选）", memoryDevice, InfoModel.Source.KERNEL,
                    "来自 /sys/devices/system/memory/memoryN/phys_device 公开节点。");
        }
        memoryType.hiddenRow("ram_vendor", "内存颗粒厂商", "普通应用无法可靠确认", InfoModel.Source.UNKNOWN);

        section.footnote("DDR 类型与颗粒厂商只显示设备公开结果，不依据机型数据库推测；完整内核字段见「技术原始数据」。"
                + "DRAM 颗粒厂商没有适用于普通应用的公开接口，该行仅保留在导出报告中。");
        return section;
    }

    private InfoModel.Section buildBatterySection(String nativeResult) {
        InfoModel.Section section = new InfoModel.Section(SECTION_BATTERY, "电量、容量、健康度与公开的电池数据");
        Intent intent = batteryIntent();
        if (intent == null) {
            section.group("状态").row("battery_available", "电池信息", "不可读取", InfoModel.Source.UNKNOWN);
            return section;
        }
        InfoModel.Group status = section.group("状态");
        String charge = batteryPercent(intent);
        status.meter("battery_level", "当前电量", charge, progressFromPercentText(charge), InfoModel.Source.SYSTEM);
        status.row("battery_status", "当前状态", batteryStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)), InfoModel.Source.SYSTEM);
        status.row("battery_health", "健康状态", batteryHealth(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)), InfoModel.Source.SYSTEM,
                "BatteryManager 的健康枚举，属于系统判定结果，不是容量百分比。");
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        status.qty("battery_temperature", "温度",
                temperature != Integer.MIN_VALUE ? temperature / 10.0 : null, "°C",
                temperature != Integer.MIN_VALUE
                        ? String.format(Locale.US, "%.1f °C", temperature / 10.0) : "不可读取",
                InfoModel.Source.SYSTEM, null);
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        status.qty("battery_voltage", "电压", voltage >= 0 ? (double) voltage : null, "mV",
                voltage >= 0 ? voltage + " mV" : "不可读取", InfoModel.Source.SYSTEM, null);
        status.row("battery_power_source", "外接电源", pluggedName(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)), InfoModel.Source.SYSTEM);

        String batterySection = nativeSection(nativeResult, "[电池 power_supply 原始节点]", "[数据边界]");
        String design = capacityText(keyValue(batterySection, "charge_full_design"));
        String full = capacityText(keyValue(batterySection, "charge_full"));

        InfoModel.Group capacity = section.group("容量与健康");
        capacity.qty("battery_design_capacity", "设计容量", capacityMah(keyValue(batterySection, "charge_full_design")),
                "mAh", design == null ? "设备未公开" : design,
                design == null ? InfoModel.Source.UNKNOWN : InfoModel.Source.KERNEL,
                "来自 /sys/class/power_supply/*/charge_full_design；按 µAh 口径换算为 mAh。");
        capacity.qty("battery_full_capacity", "当前满充容量", capacityMah(keyValue(batterySection, "charge_full")),
                "mAh", full == null ? "设备未公开" : full,
                full == null ? InfoModel.Source.UNKNOWN : InfoModel.Source.KERNEL,
                "来自 /sys/class/power_supply/*/charge_full；随使用衰减，低于设计容量属正常现象。");
        capacity.row("battery_health_percent", "健康度（电量计估算）", batterySoh(batterySection), InfoModel.Source.ESTIMATED,
                "仅在驱动同时公开 charge_full 与 charge_full_design 时按电量计计算，不是实验室测量值。");
        capacity.row("battery_cycle_count", "循环次数", batteryCycles(batterySection, intent), InfoModel.Source.SYSTEM,
                "优先读取 BatteryManager 的循环次数（Android 14 / API 34 起）；否则回退 power_supply 的 cycle_count。");

        InfoModel.Group hardware = section.group("电池硬件");
        String manufacturer = keyValue(batterySection, "manufacturer");
        String model = keyValue(batterySection, "model_name");
        hardware.row("battery_manufacturer", "电池厂商", manufacturer == null ? "设备未公开" : manufacturer,
                manufacturer == null ? InfoModel.Source.UNKNOWN : InfoModel.Source.KERNEL,
                "来自 /sys/class/power_supply 原始节点。");
        hardware.row("battery_model", "电池型号", model == null ? "设备未公开" : model,
                model == null ? InfoModel.Source.UNKNOWN : InfoModel.Source.KERNEL,
                "来自 /sys/class/power_supply 原始节点。");
        String technology = keyValue(batterySection, "technology");
        hardware.row("battery_technology", "电池技术", technology == null ? "设备未公开" : technology,
                technology == null ? InfoModel.Source.UNKNOWN : InfoModel.Source.KERNEL);
        String current = currentText(batterySection);
        if (current != null) {
            hardware.row("battery_current", "实时电流", current, InfoModel.Source.KERNEL,
                    "power_supply 的 current_now 换算值；正负方向由厂商驱动定义，仅作参考。");
        }
        String remaining = timeToFullText(batterySection);
        if (remaining != null) {
            hardware.row("battery_time_to_full", "充满剩余时间", remaining, InfoModel.Source.KERNEL,
                    "time_to_full_now 的驱动估算值，随温度与充电策略变化。");
        }

        section.footnote("数据来自 ACTION_BATTERY_CHANGED、BatteryManager 与 /sys/class/power_supply 公开节点。"
                + "容量按 µAh 除以 1000 换算为 mAh；驱动直接以 mAh 上报时按原值显示。");
        return section;
    }

    @SuppressWarnings("deprecation")
    private InfoModel.Section buildDisplaySection(String nativeResult) {
        InfoModel.Section section = new InfoModel.Section(SECTION_DISPLAY, "分辨率、刷新率模式与系统报告的能力");
        Display display = currentDisplay();
        if (display == null) {
            section.group("显示").row("display_available", "屏幕信息", "不可读取", InfoModel.Source.UNKNOWN);
            appendPanelGroup(section, nativeResult);
            return section;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        InfoModel.Group panel = section.group("显示");
        panel.prominent("display_resolution", "屏幕分辨率", metrics.widthPixels + " × " + metrics.heightPixels + " 像素", InfoModel.Source.SYSTEM);
        panel.qty("display_refresh_rate", "当前刷新率",
                Math.round(display.getRefreshRate() * 100.0) / 100.0, "Hz",
                String.format(Locale.US, "%.2f Hz", display.getRefreshRate()), InfoModel.Source.SYSTEM, null);
        String modes = refreshModes(display);
        if (modes != null) {
            panel.row("display_modes", "支持的显示模式", modes, InfoModel.Source.SYSTEM,
                    "Display.getSupportedModes() 公开返回的分辨率与刷新率组合；用于确认高刷与降分辨率档位。");
        }
        panel.row("display_density_dpi", "屏幕密度", metrics.densityDpi + " dpi（系统显示密度）", InfoModel.Source.SYSTEM,
                "系统显示密度可与物理像素密度不同，厂商常按省电或显示效果调整。");
        panel.row("display_physical_dpi", "物理像素密度", physicalDensity(metrics), InfoModel.Source.SYSTEM,
                "DisplayMetrics.xdpi / ydpi，由系统按面板参数上报；部分设备上报值不准确。");
        panel.row("display_diagonal_inch", "尺寸估算", estimatedDiagonal(metrics), InfoModel.Source.ESTIMATED,
                "按系统 xdpi/ydpi 推算的物理对角线，不是面板实测尺寸；xdpi 失真时该值同样失真。");
        panel.row("display_is_round", "屏幕形状", profile.screenRound ? "圆形 / 近圆形（系统报告）" : "方形（系统报告）",
                InfoModel.Source.SYSTEM, "Configuration.isScreenRound()（API 23 起）。");
        String cutout = displayCutoutText(display);
        if (cutout != null) {
            panel.row("display_cutout", "刘海 / 挖孔", cutout, InfoModel.Source.SYSTEM,
                    "Display.getCutout()（API 29 起）报告的安全区与挖孔矩形；无挖孔或系统未报告时显示「无」。");
        }

        InfoModel.Group capability = section.group("系统能力");
        capability.row("display_wide_color", "广色域（系统检测）", wideColorGamutCompat(display), InfoModel.Source.SYSTEM);
        capability.row("display_hdr", "HDR（系统检测）", hdrTypesCompat(display), InfoModel.Source.SYSTEM);
        String luminanceText = maxLuminance(display);
        float luminanceValue = desiredMaxLuminance(display);
        if (luminanceText != null) {
            capability.qty("display_max_luminance", "系统期望峰值亮度",
                    Math.round(luminanceValue * 10.0) / 10.0, "nits",
                    luminanceText, InfoModel.Source.SYSTEM,
                    "Display.HdrCapabilities.getDesiredMaxLuminance()，是系统对面板的期望值，不是仪器实测亮度。");
        }
        String colorModeList = colorModes(display);
        if (colorModeList != null) {
            capability.row("display_color_modes", "颜色模式", colorModeList, InfoModel.Source.SYSTEM,
                    "Display.getSupportedColorModes()（API 26 起）公开返回的色彩模式列表。");
        }
        capability.hiddenRow("display_10bit", "10-bit 显示", tenBitCapabilityCompat(display), InfoModel.Source.UNKNOWN);

        appendPanelGroup(section, nativeResult);

        section.footnote("广色域与 HDR 是系统报告能力，不等同于面板物理参数；屏幕测试图案仅供观察，不能替代仪器实测。"
                + "面板标识仅在 EDID 节点可读且校验通过时解码，多数设备该节点对普通应用封闭。");
        return section;
    }

    /** Cutout (notch / punch-hole) safe insets and bounding rects; API 29+. */
    private static String displayCutoutText(Display display) {
        if (Build.VERSION.SDK_INT < 29) return null;
        DisplayCutout cutout = display.getCutout();
        if (cutout == null) return "无（系统未报告挖孔）";
        StringBuilder out = new StringBuilder();
        out.append("安全区 上 ").append(cutout.getSafeInsetTop())
                .append(" / 下 ").append(cutout.getSafeInsetBottom())
                .append(" / 左 ").append(cutout.getSafeInsetLeft())
                .append(" / 右 ").append(cutout.getSafeInsetRight()).append(" px");
        List<Rect> rects = cutout.getBoundingRects();
        if (!rects.isEmpty()) {
            out.append("；挖孔 ").append(rects.size()).append(" 处");
            Rect first = rects.get(0);
            out.append("（首处 ").append(first.width()).append(" × ").append(first.height()).append("）");
        }
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets waterfall = cutout.getWaterfallInsets();
            if (waterfall.left != 0 || waterfall.top != 0 || waterfall.right != 0 || waterfall.bottom != 0) {
                out.append("；瀑布屏内缩 上 ").append(waterfall.top).append(" / 下 ").append(waterfall.bottom)
                        .append(" / 左 ").append(waterfall.left).append(" / 右 ").append(waterfall.right).append(" px");
            }
        }
        return out.toString();
    }

    private InfoModel.Section buildCameraSection(CapabilityModel capability) {
        InfoModel.Section section = new InfoModel.Section(SECTION_CAMERA, "Camera2 公开枚举与传感器清单");
        PackageManager pm = context.getPackageManager();
        InfoModel.Group cameras = section.group("摄像头");
        cameras.prominent("camera_count", "系统枚举摄像头", cameraCountText(capability), InfoModel.Source.CAMERA);
        if (capability != null && capability.cameraError == null && !capability.cameras.isEmpty()) {
            cameras.row("camera_back_count", "后置摄像头", facingCountText(capability.countFacing("back")),
                    InfoModel.Source.CAMERA);
            cameras.row("camera_front_count", "前置摄像头", facingCountText(capability.countFacing("front")),
                    InfoModel.Source.CAMERA);
            cameras.row("camera_external_count", "外置摄像头", facingCountText(capability.countFacing("external")),
                    InfoModel.Source.CAMERA);
            int unknown = capability.countFacing("unknown");
            cameras.row("camera_unknown_facing_count", "方向未知", facingCountText(unknown),
                    InfoModel.Source.CAMERA);
            cameras.hiddenRow("camera_external_unknown_count", "外置 / 方向未知",
                    facingCountText(capability.countFacing("external") + unknown), InfoModel.Source.CAMERA);
            String megapixels = capability.megapixelSummary();
            if (megapixels != null) {
                cameras.row("camera_max_megapixels", "最大像素摘要", megapixels, InfoModel.Source.CAMERA,
                        "按 Camera2 最大 JPEG 输出尺寸计算，不是厂商宣传的传感器理论像素；完整列表见相机详情。");
            }
        }
        cameras.row("camera_flash", "闪光灯", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)), InfoModel.Source.SYSTEM,
                "PackageManager 的 feature 声明，属于系统级兼容性信号。");
        cameras.row("camera_autofocus", "自动对焦", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)),
                InfoModel.Source.SYSTEM, "系统声明存在自动对焦；具体对焦方式见相机详情。");

        InfoModel.Group sensors = section.group("传感器");
        sensors.prominent("sensor_count", "系统枚举传感器", sensorCountText(capability), InfoModel.Source.SYSTEM,
                "来自 SensorManager 静态枚举与 API 24+ 动态回调窗口。");

        section.action(InfoModel.Section.ACTION_CAMERA_DETAILS);
        section.action(InfoModel.Section.ACTION_SENSOR_LIST);
        section.footnote("方向按 Camera2 LENS_FACING 实际枚举，不把 FEATURE_CAMERA_ANY 当作后置；不打开相机、不申请 CAMERA 权限。"
                + "相机模组供应商与镜头型号等未公开字段不作猜测。");
        return section;
    }

    private static String cameraCountText(CapabilityModel capability) {
        if (capability == null) return "尚未完成采集";
        if (capability.cameraError != null) return "枚举失败";
        return capability.cameras.size() + " 个";
    }

    private static String sensorCountText(CapabilityModel capability) {
        if (capability == null) return "尚未完成采集";
        if (capability.sensorError != null) return "不可读取";
        return capability.sensorTotal + " 个";
    }

    private static String facingCountText(int count) {
        return count > 0 ? count + " 个" : "系统未枚举到";
    }

    private InfoModel.Section buildHardwareSection() {
        InfoModel.Section section = new InfoModel.Section(SECTION_HARDWARE, "系统声明的连接、生物识别与外设能力");
        PackageManager pm = context.getPackageManager();
        InfoModel.Group connectivity = section.group("连接与外设");
        connectivity.row("feature_gps", "GPS 定位", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)), InfoModel.Source.SYSTEM);
        connectivity.row("feature_network_location", "网络定位", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK)), InfoModel.Source.SYSTEM);
        connectivity.row("feature_wifi", "Wi-Fi", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_WIFI)), InfoModel.Source.SYSTEM);
        connectivity.row("feature_bluetooth", "蓝牙", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)), InfoModel.Source.SYSTEM);
        connectivity.row("feature_ble", "低功耗蓝牙", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)), InfoModel.Source.SYSTEM);
        connectivity.row("feature_nfc", "NFC", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_NFC)), InfoModel.Source.SYSTEM);
        connectivity.row("feature_usb_host", "USB Host", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)), InfoModel.Source.SYSTEM);
        connectivity.row("feature_ir", "红外遥控", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)), InfoModel.Source.SYSTEM);
        // FEATURE_FM_RADIO is not exposed by every compile SDK's PackageManager stub;
        // the Android feature name is stable and can be queried without a dangerous permission.
        connectivity.row("feature_fm_radio", "FM 收音机", yesNo(pm.hasSystemFeature("android.hardware.fmradio")), InfoModel.Source.SYSTEM);

        InfoModel.Group biometrics = section.group("生物识别");
        biometrics.row("feature_fingerprint", "指纹", Build.VERSION.SDK_INT >= 23
                        ? yesNo(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                        : "系统未提供公开检测（需 API 23+）",
                Build.VERSION.SDK_INT >= 23 ? InfoModel.Source.SYSTEM : InfoModel.Source.UNKNOWN);
        biometrics.row("feature_face", "人脸", Build.VERSION.SDK_INT >= 29
                        ? yesNo(pm.hasSystemFeature(PackageManager.FEATURE_FACE))
                        : "系统未提供公开检测（需 API 29+）",
                Build.VERSION.SDK_INT >= 29 ? InfoModel.Source.SYSTEM : InfoModel.Source.UNKNOWN);
        biometrics.row("feature_iris", "虹膜", Build.VERSION.SDK_INT >= 29
                        ? yesNo(pm.hasSystemFeature(PackageManager.FEATURE_IRIS))
                        : "系统未提供公开检测（需 API 29+）",
                Build.VERSION.SDK_INT >= 29 ? InfoModel.Source.SYSTEM : InfoModel.Source.UNKNOWN);

        InfoModel.Group input = section.group("触控与反馈");
        input.row("touch_points", "多点触控", touchCapability(pm), InfoModel.Source.SYSTEM);
        input.row("haptic_amplitude", "振动幅度控制", vibratorText(), InfoModel.Source.SYSTEM,
                "Vibrator.hasAmplitudeControl()（API 26 起）；支持时通常为线性马达，可用于区分转子马达。");

        InfoModel.Group audio = section.group("音频");
        audio.row("feature_audio_pro", "专业音频", Build.VERSION.SDK_INT >= 23
                        ? yesNo(pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO))
                        : "系统未提供公开检测（需 API 23+）",
                Build.VERSION.SDK_INT >= 23 ? InfoModel.Source.SYSTEM : InfoModel.Source.UNKNOWN);
        audio.row("feature_audio_low_latency", "低延迟音频", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)), InfoModel.Source.SYSTEM);

        InfoModel.Group adaptation = section.group("界面适配");
        adaptation.hiddenRow("ui_rule_tv", "电视遥控器规则", yesNo(profile.isTelevision()), InfoModel.Source.SYSTEM);
        adaptation.hiddenRow("ui_rule_watch", "手表小屏规则", yesNo(profile.isWatch()), InfoModel.Source.SYSTEM);

        section.footnote("以上能力来自 Android 系统 feature flag 的公开声明；“支持”表示系统已声明，"
                + "并不等于当前场景下功能必定可用。界面适配规则属于应用内部逻辑，只保留在导出报告中。");
        return section;
    }

    private InfoModel.Section buildSecuritySection(String nativeResult) {
        InfoModel.Section section = new InfoModel.Section(SECTION_SECURITY, "系统完整性、运行时长与运行时参数");
        InfoModel.Group integrity = section.group("完整性");
        integrity.row("selinux_mode", "SELinux", nativeValue(nativeResult, "SELinux enforce"), InfoModel.Source.KERNEL,
                "/sys/fs/selinux/enforce：enforcing 表示强制模式，permissive 表示仅记录不拦截。普通应用无法修改。");
        integrity.row("bootloader", "引导程序版本", buildValue(Build.BOOTLOADER), InfoModel.Source.SYSTEM);
        integrity.row("low_ram_device", "低内存设备标记", lowRamText(), InfoModel.Source.SYSTEM,
                "ActivityManager.isLowRamDevice()，由系统在出厂配置中声明；Go 版与低配机型常见为「是」。");

        InfoModel.Group runtime = section.group("运行时");
        runtime.row("uptime", "系统运行时长", uptimeText(), InfoModel.Source.SYSTEM,
                "SystemClock.elapsedRealtime()，包含深度休眠时间，等于本次开机以来的时长。");
        runtime.row("timezone", "时区", TimeZone.getDefault().getID(), InfoModel.Source.SYSTEM);
        runtime.row("locale", "语言", Locale.getDefault().toString(), InfoModel.Source.SYSTEM);
        runtime.row("font_scale", "字体缩放", fontScaleText(), InfoModel.Source.SYSTEM,
                "Configuration.fontScale，1.00 为标准值；非 1.00 时说明用户调整过字体大小。");

        section.footnote("本节只读取系统公开字段与只读内核节点，不涉及 root、不修改 SELinux、不申请任何权限。");
        return section;
    }

    private static InfoModel.Section buildRawSection(String nativeResult, String gpuResult,
                                                      String vulkanResult, CapabilityModel capability) {
        InfoModel.Section section = new InfoModel.Section(SECTION_RAW,
                "NDK、内核节点、驱动与枚举原文；仅供核验，默认收起");
        section.rawBody = "[GPU / OpenGL ES 原始报告]\n" + safe(gpuResult)
                + "\n\n[Vulkan 原生报告]\n" + safe(vulkanResult)
                + "\n\n[Camera2 / 传感器公开枚举]\n" + safe(capability == null ? "" : capability.renderText())
                + "\n\n[NDK / 内核公开节点]\n" + safe(nativeResult)
                + "\n\n[构建字段（界面不再逐行展示）]\n"
                + "构建者: " + safe(Build.USER) + "\n"
                + "构建主机: " + safe(Build.HOST) + "\n"
                + "构建指纹: " + safe(Build.FINGERPRINT) + "\n"
                + "引导程序: " + safe(Build.BOOTLOADER) + "\n"
                + "\n\n[说明]\n这些是用于核验的原始输出。节点不可读、字段缺失或值属于厂商实现时，"
                + "应用不会用数据库或机型资料补写。";
        return section;
    }

    // ==================================================================== helpers: CPU

    /** "806 – 2116 MHz（8 核心）" style summary, or null when nothing is readable. */
    private static String frequencyRange(String cpuRaw, String field) {
        if (TextUtils.isEmpty(cpuRaw) || cpuRaw.startsWith("未找到") || cpuRaw.startsWith("该采集段")) return null;
        String marker = "/" + field + ":";
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        int cores = 0;
        for (String line : cpuRaw.split("\\n")) {
            String trimmed = line.trim();
            int markerIndex = trimmed.indexOf(marker);
            if (markerIndex < 0) continue;
            Long kHz = leadingLong(trimmed.substring(markerIndex + marker.length()).trim());
            if (kHz == null || kHz <= 0) continue;
            cores++;
            min = Math.min(min, kHz);
            max = Math.max(max, kHz);
        }
        if (cores == 0) return null;
        if (min == max) return String.format(Locale.US, "%.0f MHz（%d 核心一致）", min / 1000.0, cores);
        return String.format(Locale.US, "%.0f – %.0f MHz（%d 核心范围）", min / 1000.0, max / 1000.0, cores);
    }

    /**
     * Parses the "[CPU 深度原始节点]" block into field -> (cpu index -> value).
     * Source lines look like {@code cpu0/cpufreq/scaling_governor: schedutil}.
     */
    private static Map<String, Map<Integer, String>> parseCpuNodes(String cpuRaw) {
        Map<String, Map<Integer, String>> table = new LinkedHashMap<>();
        if (TextUtils.isEmpty(cpuRaw) || cpuRaw.startsWith("未找到") || cpuRaw.startsWith("该采集段")) return table;
        for (String line : cpuRaw.split("\\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("cpu")) continue;
            int slash = trimmed.indexOf('/');
            int colon = trimmed.indexOf(':', slash + 1);
            if (slash <= 3 || colon <= slash) continue;
            Integer cpu = parseIntSafe(trimmed.substring(3, slash));
            if (cpu == null) continue;
            String field = trimmed.substring(slash + 1, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if (field.isEmpty() || value.isEmpty()) continue;
            Map<Integer, String> column = table.get(field);
            if (column == null) {
                column = new TreeMap<>();
                table.put(field, column);
            }
            column.put(cpu, value);
        }
        return table;
    }

    /** "4 + 4（2 簇，共 8 核）" from cluster_id, or null when the node is unavailable. */
    private static String coreStructure(String cpuRaw) {
        Map<String, Map<Integer, String>> table = parseCpuNodes(cpuRaw);
        Map<Integer, String> column = table.get("cluster_id");
        if (column == null || column.isEmpty()) column = table.get("core_siblings_list");
        if (column == null || column.isEmpty()) return null;
        Map<String, Integer> counts = new TreeMap<>();
        for (String cluster : column.values()) {
            Integer count = counts.get(cluster);
            counts.put(cluster, count == null ? 1 : count + 1);
        }
        if (counts.size() < 2) return null;
        StringBuilder out = new StringBuilder();
        int total = 0;
        for (Integer count : counts.values()) {
            if (out.length() > 0) out.append(" + ");
            out.append(count);
            total += count;
        }
        out.append("（").append(counts.size()).append(" 簇，共 ").append(total).append(" 核）");
        return out.toString();
    }

    /** "4 × 1.80 GHz + 4 × 2.40 GHz（共 8 核）", grouped by cpuinfo_max_freq. */
    private static String frequencyTable(String cpuRaw) {
        Map<Integer, String> column = parseCpuNodes(cpuRaw).get("cpuinfo_max_freq_khz");
        if (column == null || column.isEmpty()) return null;
        Map<Long, Integer> counts = new TreeMap<>();
        for (String raw : column.values()) {
            Long kHz = leadingLong(raw);
            if (kHz == null || kHz <= 0) continue;
            Integer count = counts.get(kHz);
            counts.put(kHz, count == null ? 1 : count + 1);
        }
        if (counts.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        int total = 0;
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            if (out.length() > 0) out.append(" + ");
            out.append(entry.getValue()).append(" × ")
                    .append(String.format(Locale.US, "%.2f GHz", entry.getKey() / 1000000.0));
            total += entry.getValue();
        }
        out.append("（共 ").append(total).append(" 核）");
        return out.toString();
    }

    /** Unique values of one per-core field, e.g. the governor list across clusters. */
    private static String distinctValues(String cpuRaw, String field) {
        Map<Integer, String> column = parseCpuNodes(cpuRaw).get(field);
        if (column == null || column.isEmpty()) return null;
        List<String> unique = new ArrayList<>();
        for (String value : column.values()) {
            if (!unique.contains(value)) unique.add(value);
        }
        if (unique.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String value : unique) {
            if (out.length() > 0) out.append(", ");
            out.append(value);
        }
        return out.toString();
    }

    /** "8 个（CPU 0-7）" from the raw /sys/devices/system/cpu/online value. */
    private static String onlineText(String raw) {
        int count = cpuListCount(raw);
        return count > 0 ? count + " 个（CPU " + raw + "）" : "CPU " + raw;
    }

    private static int cpuListCount(String raw) {
        if (raw == null) return 0;
        int total = 0;
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            int dash = trimmed.indexOf('-');
            if (dash > 0) {
                Integer start = parseIntSafe(trimmed.substring(0, dash));
                Integer end = parseIntSafe(trimmed.substring(dash + 1));
                if (start != null && end != null && end >= start) total += end - start + 1;
            } else if (parseIntSafe(trimmed) != null) {
                total++;
            }
        }
        return total;
    }

    private static Integer parseIntSafe(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 6) return null;
        for (int index = 0; index < trimmed.length(); index++) {
            if (!Character.isDigit(trimmed.charAt(index))) return null;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String systemArchitecture() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0 || TextUtils.isEmpty(abis[0])) return "系统未公开";
        return architectureName(abis[0]) + "（首选 ABI " + abis[0] + "）";
    }

    private static String architectureName(String abi) {
        if ("arm64-v8a".equals(abi)) return "64 位 ARM";
        if ("armeabi-v7a".equals(abi) || "armeabi".equals(abi)) return "32 位 ARM";
        if ("x86_64".equals(abi)) return "64 位 x86";
        if ("x86".equals(abi)) return "32 位 x86";
        return abi;
    }

    private static String binderBitness() {
        try {
            int protocol = NativeBridge.binderProtocolVersion();
            if (protocol == 7) return "32 位（协议 7）";
            if (protocol == 8) return "64 位（协议 8）";
            if (protocol < 0) return "无法确认（Binder 驱动不可读）";
            return "无法确认（协议版本 " + protocol + "）";
        } catch (UnsatisfiedLinkError ignored) {
            return "无法确认（NDK 接口不可用）";
        }
    }

    private String processBitness() {
        try {
            int bits = NativeBridge.processBitness();
            if (bits == 32 || bits == 64) return bits + " 位";
        } catch (UnsatisfiedLinkError ignored) {
            // Keep the result explicit when the native library is unavailable.
        }
        return "无法确认";
    }

    private static String androidSystemBitness() {
        try {
            Class<?> runtimeClass = Class.forName("dalvik.system.VMRuntime");
            Object runtime = runtimeClass.getDeclaredMethod("getRuntime").invoke(null);
            Object value = runtimeClass.getDeclaredMethod("is64Bit").invoke(runtime);
            if (value instanceof Boolean) return ((Boolean) value) ? "64 位" : "32 位";
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Hidden API access may be restricted on newer Android versions; fall back below.
        }
        if (new java.io.File("/system/bin/app_process64").exists()
                || new java.io.File("/system/bin/linker64").exists()) {
            return "64 位（按系统文件回退判断）";
        }
        if (Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length > 0
                && (Build.SUPPORTED_32_BIT_ABIS == null || Build.SUPPORTED_32_BIT_ABIS.length == 0)) {
            return "64 位（按系统 ABI 回退判断）";
        }
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                && Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length == 0
                && Build.SUPPORTED_32_BIT_ABIS != null && Build.SUPPORTED_32_BIT_ABIS.length > 0) {
            return "32 位（按系统 ABI 回退判断）";
        }
        return "无法确认（系统未公开统一字段）";
    }

    private String recommendedAbi() {
        if (Build.SUPPORTED_ABIS != null) {
            for (String systemAbi : Build.SUPPORTED_ABIS) {
                if (isAppSupportedAbi(systemAbi)) return systemAbi;
            }
        }
        return "无匹配 ABI（请使用通用 APK 或检查设备兼容性）";
    }

    private static boolean isAppSupportedAbi(String abi) {
        return "arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi) || "x86_64".equals(abi);
    }

    // ==================================================================== helpers: device identity

    /** Build.SOC_MODEL on API 31+, falling back to the kernel-exported /proc/cpuinfo fields. */
    private static String socModel(String nativeResult) {
        if (Build.VERSION.SDK_INT >= 31) {
            String model = trimToNull(Build.SOC_MODEL);
            String vendor = trimToNull(Build.SOC_MANUFACTURER);
            if (model != null && !"unknown".equalsIgnoreCase(model)) {
                if (vendor == null || "unknown".equalsIgnoreCase(vendor)) return model;
                return vendor + " " + model;
            }
        }
        String hardware = cpuinfoField(nativeResult, "Hardware");
        if (hardware != null) return hardware;
        String modelName = cpuinfoField(nativeResult, "model name");
        if (modelName != null) return modelName;
        return "设备未公开";
    }

    private static String boardValue() {
        String board = trimToNull(Build.BOARD);
        String hardware = trimToNull(Build.HARDWARE);
        if (board == null && hardware == null) return "设备未公开";
        if (board == null) return hardware;
        if (hardware == null) return board;
        return board + " / " + hardware;
    }

    // ==================================================================== helpers: battery / display / hardware

    private Intent batteryIntent() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        return ContextCompat.registerReceiver(context, null, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private static String batteryPercent(Intent intent) {
        if (intent == null) return "不可读取";
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level >= 0 && scale > 0 ? (level * 100 / scale) + "%" : "未知";
    }

    private static int progressFromPercentText(String value) {
        Long number = leadingLong(value);
        if (number == null) return -1;
        return (int) Math.max(0, Math.min(100, number));
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

    private static String batterySoh(String batterySection) {
        Long full = leadingLong(keyValue(batterySection, "charge_full"));
        Long design = leadingLong(keyValue(batterySection, "charge_full_design"));
        if (full == null || design == null || full <= 0 || design <= 0) {
            return "无法计算：设备未同时公开 charge_full 与 charge_full_design";
        }
        return String.format(Locale.US, "%.1f%%（%s / %s）", full * 100.0 / design,
                milliAmpHours(full), milliAmpHours(design));
    }

    /** power_supply reports charge in µAh on most devices and mAh on a few; normalize to mAh. */
    private static String capacityText(String raw) {
        Long value = leadingLong(raw == null ? "" : raw);
        if (value == null || value <= 0) return null;
        return milliAmpHours(value);
    }

    private static String milliAmpHours(long rawValue) {
        double mAh = rawValue > 100000L ? rawValue / 1000.0 : rawValue;
        return String.format(Locale.US, "%.0f mAh", mAh);
    }

    private static String batteryCycles(String batterySection, Intent intent) {
        if (Build.VERSION.SDK_INT >= 34 && intent != null) {
            int cycles = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
            if (cycles >= 0) return cycles + " 次（系统报告）";
        }
        Long value = leadingLong(keyValue(batterySection, "cycle_count"));
        if (value != null && value > 0) return value + " 次（power_supply）";
        return "设备未公开";
    }

    private static String currentText(String batterySection) {
        Long micro = leadingLong(keyValue(batterySection, "current_now"));
        if (micro == null) return null;
        double mA = Math.abs(micro) > 10000L ? micro / 1000.0 : micro;
        return String.format(Locale.US, "%.0f mA", mA);
    }

    private static String timeToFullText(String batterySection) {
        Long seconds = leadingLong(keyValue(batterySection, "time_to_full_now"));
        if (seconds == null || seconds <= 0) return null;
        long minutes = seconds / 60L;
        if (minutes < 1) return "不足 1 分钟";
        if (minutes < 60) return "约 " + minutes + " 分钟";
        return "约 " + (minutes / 60) + " 小时 " + (minutes % 60) + " 分";
    }

    private Display currentDisplay() {
        try {
            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            return displayManager == null ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** "1080 × 2400 @ 60/90/120 Hz；1440 × 3200 @ 60/120 Hz" from Display.getSupportedModes(). */
    private static String refreshModes(Display display) {
        if (display == null || Build.VERSION.SDK_INT < 23) return null;
        Display.Mode[] modes;
        try {
            modes = display.getSupportedModes();
        } catch (RuntimeException ignored) {
            return null;
        }
        if (modes == null || modes.length == 0) return null;
        Map<String, List<String>> byResolution = new LinkedHashMap<>();
        for (Display.Mode mode : modes) {
            if (mode == null) continue;
            String key = mode.getPhysicalWidth() + " × " + mode.getPhysicalHeight();
            List<String> rates = byResolution.get(key);
            if (rates == null) {
                rates = new ArrayList<>();
                byResolution.put(key, rates);
            }
            addRefreshRate(rates, mode.getRefreshRate());
            if (Build.VERSION.SDK_INT >= 31) {
                float[] alternatives = mode.getAlternativeRefreshRates();
                if (alternatives != null) {
                    for (float rate : alternatives) addRefreshRate(rates, rate);
                }
            }
        }
        if (byResolution.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        int printed = 0;
        for (Map.Entry<String, List<String>> entry : byResolution.entrySet()) {
            if (printed >= 3) break;
            if (printed > 0) out.append("；");
            out.append(entry.getKey()).append(" @ ");
            List<String> rates = entry.getValue();
            for (int index = 0; index < rates.size(); index++) {
                if (index > 0) out.append("/");
                out.append(rates.get(index));
            }
            out.append(" Hz");
            printed++;
        }
        if (byResolution.size() > printed) {
            out.append("；另有 ").append(byResolution.size() - printed).append(" 组分辨率");
        }
        return out.toString();
    }

    private static void addRefreshRate(List<String> rates, float rate) {
        if (rate <= 0) return;
        String text = String.format(Locale.US, "%.0f", rate);
        if (!rates.contains(text)) rates.add(text);
    }

    private static String physicalDensity(DisplayMetrics metrics) {
        if (metrics.xdpi <= 0 || metrics.ydpi <= 0) return "系统未公开";
        return String.format(Locale.US, "%.1f × %.1f dpi（xdpi × ydpi）", metrics.xdpi, metrics.ydpi);
    }

    private static String estimatedDiagonal(DisplayMetrics metrics) {
        if (metrics.xdpi <= 0 || metrics.ydpi <= 0) return "不可估算";
        double widthInches = metrics.widthPixels / metrics.xdpi;
        double heightInches = metrics.heightPixels / metrics.ydpi;
        return String.format(Locale.US, "%.2f 英寸", Math.sqrt(widthInches * widthInches + heightInches * heightInches));
    }

    private static String wideColorGamutCompat(Display display) {
        if (Build.VERSION.SDK_INT < 26) {
            return "系统未提供公开检测（需 API 26+；当前 API " + Build.VERSION.SDK_INT + "）";
        }
        return display != null && display.isWideColorGamut() ? "系统报告支持广色域" : "系统报告不支持广色域";
    }

    @SuppressWarnings("deprecation")
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

    /** Same value as {@link #maxLuminance(Display)}, as a number for the JSON report. */
    private static float desiredMaxLuminance(Display display) {
        if (display == null || Build.VERSION.SDK_INT < 24) return -1f;
        android.view.Display.HdrCapabilities capabilities = display.getHdrCapabilities();
        if (capabilities == null) return -1f;
        return capabilities.getDesiredMaxLuminance();
    }

    /** System-desired peak luminance from the HDR capabilities; not a measured value. */
    private static String maxLuminance(Display display) {
        if (display == null || Build.VERSION.SDK_INT < 24) return null;
        android.view.Display.HdrCapabilities capabilities = display.getHdrCapabilities();
        if (capabilities == null) return null;
        float max = capabilities.getDesiredMaxLuminance();
        if (max <= 0) return null;
        return String.format(Locale.US, "%.0f nits（系统期望值）", max);
    }

    private static String colorModes(Display display) {
        if (display == null || Build.VERSION.SDK_INT < 26) return null;
        boolean wideColor;
        try {
            wideColor = display.isWideColorGamut();
        } catch (RuntimeException ignored) {
            return null;
        }
        if (!wideColor) return "sRGB/标准色域";
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                android.graphics.ColorSpace preferred = display.getPreferredWideGamutColorSpace();
                if (preferred != null && preferred.getName() != null) {
                    return "标准色域 + 广色域（" + preferred.getName() + "）";
                }
            } catch (RuntimeException ignored) {
                // Keep the reliable wide-color boolean when the preferred space is unavailable.
            }
        }
        return "标准色域 + 广色域";
    }

    private static String tenBitCapabilityCompat(Display display) {
        String hdr = hdrTypesCompat(display);
        if (Build.VERSION.SDK_INT < 26) return "系统未提供公开检测；无面板原生 10-bit 通用 API";
        if (hdr.startsWith("系统未报告")) return "没有可用的系统 HDR 报告；无面板原生 10-bit 通用 API";
        return "系统报告 HDR（" + hdr + "）；Android 无普通应用通用的面板原生 10-bit API";
    }

    private static String touchCapability(PackageManager pm) {
        if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND)) return "支持 5 点以上";
        if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)) return "支持区分多点触控";
        if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)) return "支持多点触控";
        if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) return "支持单点触控";
        return "系统未声明触控能力";
    }

    private String vibratorText() {
        if (Build.VERSION.SDK_INT < 26) return "系统未提供公开检测（需 API 26+）";
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return "系统未声明振动马达";
            return vibrator.hasAmplitudeControl() ? "支持（可调振幅）" : "支持振动，不支持振幅控制";
        } catch (RuntimeException ignored) {
            return "不可读取";
        }
    }

    private static String yesNo(boolean value) { return value ? "支持" : "未声明"; }

    // ==================================================================== helpers: security / runtime

    private String lowRamText() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return "不可读取";
        try {
            return manager.isLowRamDevice() ? "是（系统声明为低内存设备）" : "否";
        } catch (RuntimeException ignored) {
            return "不可读取";
        }
    }

    private static String processPss() {
        try {
            Debug.MemoryInfo info = new Debug.MemoryInfo();
            Debug.getMemoryInfo(info);
            int pssKb = info.getTotalPss();
            if (pssKb <= 0) return null;
            return String.format(Locale.US, "%.0f MiB（PSS）", pssKb / 1024.0);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String uptimeText() {
        long totalMinutes = SystemClock.elapsedRealtime() / 60000L;
        long days = totalMinutes / (60 * 24);
        long hours = (totalMinutes / 60) % 24;
        long minutes = totalMinutes % 60;
        if (days > 0) return days + " 天 " + hours + " 小时";
        if (hours > 0) return hours + " 小时 " + minutes + " 分";
        return minutes + " 分钟";
    }

    private String fontScaleText() {
        float scale = context.getResources().getConfiguration().fontScale;
        if (Math.abs(scale - 1f) < 0.01f) return String.format(Locale.US, "%.2f（标准）", scale);
        return String.format(Locale.US, "%.2f（用户已调整）", scale);
    }

    // ==================================================================== helpers: raw text parsing

    private static String gpuValue(String gpuResult, String key) {
        String value = keyValue(gpuResult, key);
        return value == null ? "GPU 驱动未提供或上下文创建失败" : value;
    }

    private static String ddrConclusion(String ddrRaw) {
        String value = keyValue(ddrRaw, "DDR 结论");
        return value == null ? "无法从普通应用可靠确认" : value;
    }

    private static String orUnavailable(String value) {
        return value == null ? "未公开或不可用" : value;
    }

    private static String nativeValue(String nativeResult, String key) {
        String value = keyValue(nativeResult, key);
        return value == null ? "不可读取" : value;
    }

    private static String nativeSection(String nativeResult, String startMarker, String endMarker) {
        if (nativeResult == null) return "未找到该采集段。";
        int start = nativeResult.indexOf(startMarker);
        if (start < 0) return "未找到该采集段。";
        int contentStart = nativeResult.indexOf('\n', start);
        if (contentStart < 0) return "该采集段没有可显示内容。";
        int end = nativeResult.indexOf(endMarker, contentStart + 1);
        return nativeResult.substring(contentStart + 1, end >= 0 ? end : nativeResult.length()).trim();
    }

    private static String keyValue(String text, String key) {
        if (TextUtils.isEmpty(text)) return null;
        String prefix = key + ":";
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).trim();
                return TextUtils.isEmpty(value) || value.startsWith("不可读:") ? null : value;
            }
        }
        return null;
    }

    /** Reads one field out of a "-- /proc/<name> --" block exported by the native layer. */
    private static String procFileField(String nativeResult, String fileName, String field) {
        if (nativeResult == null || TextUtils.isEmpty(field)) return null;
        String marker = "-- " + fileName + " --";
        int start = nativeResult.indexOf(marker);
        if (start < 0) return null;
        int contentStart = nativeResult.indexOf('\n', start);
        if (contentStart < 0) return null;
        int end = nativeResult.indexOf("\n-- ", contentStart + 1);
        String block = nativeResult.substring(contentStart + 1, end > 0 ? end : nativeResult.length());
        for (String line : block.split("\\n")) {
            String trimmed = line.trim();
            int colon = trimmed.indexOf(':');
            if (colon <= 0) continue;
            if (trimmed.substring(0, colon).trim().equalsIgnoreCase(field)) {
                String value = trimmed.substring(colon + 1).trim();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    private static String cpuinfoField(String nativeResult, String field) {
        return procFileField(nativeResult, "/proc/cpuinfo", field);
    }

    /** "2.00 GiB" style swap total from /proc/meminfo. */
    private static String swapText(String nativeResult) {
        String raw = procFileField(nativeResult, "/proc/meminfo", "SwapTotal");
        if (raw == null) return "设备未公开";
        Long kib = leadingLong(raw);
        if (kib == null || kib < 0) return "设备未公开";
        if (kib == 0L) return "0（未启用 swap / ZRAM）";
        return bytes(kib * 1024L);
    }

    /** Counts "cameraId N: 前置…" blocks per facing name shown in the capability text. */
    static String firstLineValue(String text, String marker) {
        if (TextUtils.isEmpty(text)) return null;
        for (String line : text.split("\\n")) {
            int position = line.indexOf(marker);
            if (position >= 0) {
                String value = line.substring(position + marker.length()).trim();
                if (!TextUtils.isEmpty(value) && !value.startsWith("不可读:")) return value;
            }
        }
        return null;
    }

    static Long leadingLong(String text) {
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

    private static String firstAbi(String[] abis) {
        return abis != null && abis.length > 0 && !TextUtils.isEmpty(abis[0]) ? abis[0] : "系统未公开";
    }

    private static String joinOrUnavailable(String[] abis) {
        if (abis == null || abis.length == 0) return "不支持或系统未公开";
        return join(abis);
    }

    private static String join(String[] values) {
        return values == null || values.length == 0 ? "未知" : TextUtils.join(", ", values);
    }

    private static String buildValue(String value) {
        return TextUtils.isEmpty(value) ? "设备未公开" : value;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "未知" : value.replace('\n', ' ').replace('\r', ' ');
    }

    /**
     * Human-readable byte size. KiB/MiB keep no decimals for large values, but GiB and TiB
     * always keep two decimals so a nominal 12 GB device is never rounded up to "12 GiB".
     */
    static String bytes(long value) {
        if (value < 0) return "未知";
        final String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double result = value;
        int unit = 0;
        while (result >= 1024.0 && unit < units.length - 1) {
            result /= 1024.0;
            unit++;
        }
        if (unit <= 2) return String.format(Locale.US, "%.0f %s", result, units[unit]);
        return String.format(Locale.US, "%.2f %s", result, units[unit]);
    }

    static int percent(long part, long total) {
        if (total <= 0) return 0;
        return (int) Math.max(0, Math.min(100, part * 100 / total));
    }

    // ==================================================================== helpers: units

    /**
     * Battery capacity in mAh. Drivers report either µAh (large numbers) or mAh; the
     * same threshold as {@link #milliAmpHours(long)} is used so the printed value and
     * the machine-readable number can never disagree.
     */
    private static Double capacityMah(String raw) {
        Long value = leadingLong(raw == null ? "" : raw);
        if (value == null || value <= 0) return null;
        return value > 100000L ? value / 1000.0 : (double) value;
    }

    // ==================================================================== helpers: panel EDID

    private static final Map<String, String> PANEL_VENDORS = new LinkedHashMap<>();

    static {
        PANEL_VENDORS.put("SDC", "Samsung Display");
        PANEL_VENDORS.put("BOE", "BOE Technology");
        PANEL_VENDORS.put("AUO", "AU Optronics");
        PANEL_VENDORS.put("LGD", "LG Display");
        PANEL_VENDORS.put("CSO", "China Star Optoelectronics");
        PANEL_VENDORS.put("TMA", "Tianma Microelectronics");
        PANEL_VENDORS.put("SHP", "Sharp");
        PANEL_VENDORS.put("JDI", "Japan Display");
        PANEL_VENDORS.put("INX", "Innolux");
        PANEL_VENDORS.put("CPT", "Chunghwa Picture Tubes");
    }

    /**
     * Adds the EDID-derived panel identity. The hex dump is produced by
     * {@code ProbeNative} from /sys/class/drm/card0-DSI-1/edid; decoding it is a local
     * parse of bytes the device itself publishes, not a lookup in a model database.
     *
     * <p>Most retail devices deny read access to that node, and some vendors ship a
     * template EDID. Both cases end up as "设备未公开" rather than a guessed panel;
     * rows stay in the JSON so the schema does not change between devices.</p>
     */
    private static void appendPanelGroup(InfoModel.Section section, String nativeResult) {
        InfoModel.Group group = section.group("面板（EDID 解码）");
        EdidInfo info = EdidInfo.fromNativeResult(nativeResult);
        if (info == null) {
            group.hiddenRow("panel_id", "面板标识", "设备未公开", InfoModel.Source.UNKNOWN,
                    "EDID 节点不可读或校验未通过；普通应用没有其它公开路径确认面板供应商。");
            group.hiddenRow("panel_product_code", "面板产品代码", "设备未公开", InfoModel.Source.UNKNOWN);
            group.hiddenRow("panel_mfg_date", "面板生产日期", "设备未公开", InfoModel.Source.UNKNOWN);
            group.hiddenRow("panel_native_resolution", "面板原生分辨率", "设备未公开", InfoModel.Source.UNKNOWN);
            group.hiddenRow("panel_physical_size", "面板物理尺寸", "设备未公开", InfoModel.Source.UNKNOWN);
            return;
        }
        group.row("panel_id", "面板标识", info.vendorText(), InfoModel.Source.KERNEL,
                "由 EDID 的制造商 PNP ID 解码（EDID 1.4 第 8–9 字节）；部分厂商写入通用模板值，"
                        + "不能据此断定实际模组供应商。");
        group.row("panel_product_code", "面板产品代码", info.productText(), InfoModel.Source.KERNEL,
                "EDID 第 10–11 字节的产品代码，由面板厂自行分配，没有公开的型号对照表。");
        String date = info.manufactureText();
        if (date != null) {
            group.row("panel_mfg_date", "面板生产日期", date, InfoModel.Source.KERNEL,
                    "EDID 第 16–17 字节：生产周与年份（年份 = 值 + 1990）。");
        }
        String resolution = info.nativeResolutionText();
        if (resolution != null) {
            group.row("panel_native_resolution", "面板原生分辨率", resolution, InfoModel.Source.KERNEL,
                    "EDID 首个详细时序描述符（第 54–71 字节）；与系统当前分辨率不同时说明正在降分辨率运行。");
        }
        String size = info.physicalSizeText();
        if (size != null) {
            group.row("panel_physical_size", "面板物理尺寸", size, InfoModel.Source.KERNEL,
                    "EDID 第 21–22 字节的厘米尺寸，按对角线换算为英寸；与「尺寸估算」的 dpi 推算互相独立。");
        }
    }

    /** Minimal EDID 1.x decoder: identity, manufacture date, first detailed timing. */
    private static final class EdidInfo {
        private final String manufacturerId;
        private final int productCode;
        private final int week;
        private final int year;
        private final int widthCm;
        private final int heightCm;
        private final int horizontalPixels;
        private final int verticalPixels;

        private EdidInfo(String manufacturerId, int productCode, int week, int year, int widthCm, int heightCm,
                         int horizontalPixels, int verticalPixels) {
            this.manufacturerId = manufacturerId;
            this.productCode = productCode;
            this.week = week;
            this.year = year;
            this.widthCm = widthCm;
            this.heightCm = heightCm;
            this.horizontalPixels = horizontalPixels;
            this.verticalPixels = verticalPixels;
        }

        static EdidInfo fromNativeResult(String nativeResult) {
            byte[] data = edidBytes(nativeResult);
            return data == null ? null : parse(data);
        }

        /** Returns null when the block is truncated, not an EDID, or fails its checksum. */
        static EdidInfo parse(byte[] data) {
            if (data == null || data.length < 128) return null;
            if ((data[0] & 0xFF) != 0x00 || (data[7] & 0xFF) != 0x00) return null;
            for (int index = 1; index <= 6; index++) {
                if ((data[index] & 0xFF) != 0xFF) return null;
            }
            int checksum = 0;
            for (int index = 0; index < 128; index++) checksum += data[index] & 0xFF;
            if ((checksum & 0xFF) != 0) return null;

            int packed = ((data[8] & 0xFF) << 8) | (data[9] & 0xFF);
            int first = (packed >> 10) & 0x1F;
            int second = (packed >> 5) & 0x1F;
            int third = packed & 0x1F;
            String manufacturer = null;
            if (first >= 1 && first <= 26 && second >= 1 && second <= 26 && third >= 1 && third <= 26) {
                manufacturer = new String(new char[]{
                        (char) ('A' + first - 1), (char) ('A' + second - 1), (char) ('A' + third - 1)});
            }
            int productCode = (data[10] & 0xFF) | ((data[11] & 0xFF) << 8);
            int week = data[16] & 0xFF;
            int year = (data[17] & 0xFF) + 1990;
            int widthCm = data[21] & 0xFF;
            int heightCm = data[22] & 0xFF;

            int horizontal = 0;
            int vertical = 0;
            int pixelClock = (data[54] & 0xFF) | ((data[55] & 0xFF) << 8);
            if (pixelClock > 0) {
                horizontal = (data[56] & 0xFF) | ((data[58] & 0xF0) << 4);
                vertical = (data[59] & 0xFF) | ((data[61] & 0xF0) << 4);
            }
            return new EdidInfo(manufacturer, productCode, week, year, widthCm, heightCm, horizontal, vertical);
        }

        String vendorText() {
            if (manufacturerId == null) return "无法解码";
            String vendor = PANEL_VENDORS.get(manufacturerId);
            return vendor == null ? manufacturerId : manufacturerId + "（" + vendor + "）";
        }

        String productText() {
            if (productCode <= 0) return "设备未公开";
            return String.format(Locale.US, "0x%04X（十进制 %d）", productCode, productCode);
        }

        /** Null when the year byte is implausible; week 0 means "year only" per the spec. */
        String manufactureText() {
            if (year < 1990 || year > 2100) return null;
            if (week == 0) return year + " 年（未标周次）";
            if (week > 53) return year + " 年（周次字段异常）";
            return year + " 年第 " + week + " 周";
        }

        String nativeResolutionText() {
            if (horizontalPixels <= 0 || verticalPixels <= 0) return null;
            return horizontalPixels + " × " + verticalPixels + " 像素";
        }

        String physicalSizeText() {
            if (widthCm <= 0 || heightCm <= 0) return null;
            double diagonalCm = Math.sqrt((double) widthCm * widthCm + (double) heightCm * heightCm);
            return String.format(Locale.US, "%.1f × %.1f cm（约 %.2f 英寸）",
                    (double) widthCm, (double) heightCm, diagonalCm / 2.54);
        }
    }

    /**
     * Collects the hex bytes that follow the {@code -- .../edid（二进制前 N 字节，十六进制） --}
     * header emitted by ProbeNative. Returns null when the node was unreadable.
     */
    private static byte[] edidBytes(String nativeResult) {
        if (TextUtils.isEmpty(nativeResult)) return null;
        String marker = "/edid（二进制前";
        int header = nativeResult.indexOf(marker);
        if (header < 0) return null;
        int position = nativeResult.indexOf('\n', header);
        if (position < 0) return null;
        position++;
        byte[] buffer = new byte[256];
        int count = 0;
        while (position < nativeResult.length() && count < buffer.length) {
            int end = nativeResult.indexOf('\n', position);
            if (end < 0) end = nativeResult.length();
            String line = nativeResult.substring(position, end).trim();
            position = end + 1;
            if (line.isEmpty()) break;
            String[] tokens = line.split(" ");
            if (tokens.length == 0) break;
            boolean valid = true;
            int[] parsed = new int[tokens.length];
            for (int index = 0; index < tokens.length; index++) {
                String token = tokens[index];
                if (token.length() != 2) {
                    valid = false;
                    break;
                }
                int high = Character.digit(token.charAt(0), 16);
                int low = Character.digit(token.charAt(1), 16);
                if (high < 0 || low < 0) {
                    valid = false;
                    break;
                }
                parsed[index] = (high << 4) | low;
            }
            if (!valid) break;
            for (int value : parsed) {
                if (count >= buffer.length) break;
                buffer[count++] = (byte) value;
            }
        }
        if (count < 128) return null;
        byte[] result = new byte[count];
        System.arraycopy(buffer, 0, result, 0, count);
        return result;
    }
}
