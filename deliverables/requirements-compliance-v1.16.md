# v1.16.0 最新验收文件逐项符合性说明

## 结论

已按用户提供的最新验收文件逐项复核并补齐实现。现有工程保留 C/NDK 探测、OpenGL ES/Vulkan、DDR、电池 `power_supply`、官方 Display P3 图、14 项屏幕测试、电视遥控器控制与手表小屏适配；本轮没有依据设备型号、网络数据库或 SoC 名称补猜硬件参数。

> 编译、Lint、APK 结构、签名、ABI、资源、代码入口与 API 21 配置已经验证。界面的真实滚动流畅度、厂商是否公开某个内核节点、广色域图在特定屏幕上的显示效果，以及相机/传感器实际列表，仍必须以目标设备运行结果为准，不能由构建环境伪造。

## 验收清单

| 验收项 | 状态 | 实现证据 |
|---|---|---|
| 首屏不再是日志墙 | 已完成 | `MainActivity` 首屏为设备 Hero、CPU/GPU/RAM/存储/屏幕/电池六张指标卡、六个快速入口和默认收起的详细模块。 |
| CPU、GPU、RAM、存储、屏幕、电池突出 | 已完成 | 两列硬件概览；重要数字使用 21sp，辅助说明使用 12sp。 |
| 长文本可读 | 已完成 | 常规模块转换为两列键值网格；内核、驱动和 Vulkan 原文只在“技术原始数据”中以等宽文本保留。 |
| CPU 每核心频率可读 | 已完成 | `appendCpuFrequencyRows()` 拆分 `CPU 0 当前频率`、`CPU 1 当前频率` 等独立条目；未可靠读取拓扑时不分大小核。 |
| GPU / OpenGL / Vulkan 可读 | 已完成 | 图形摘要单独显示 OpenGL ES 渲染器、厂商、版本、GLSL 及 Vulkan 系统特性、物理设备、API/驱动和扩展数量；原始字段可展开核验。 |
| HDR、广色域、10-bit 表述严谨 | 已完成 | 广色域与 HDR 标记为系统报告；10-bit 明确写为无普通应用通用原生面板确认 API，不把 HDR 等同 10-bit。 |
| DDR 不猜测 | 已完成 | 仅明确原始值含 `DDR`、`LPDDR` 或 `HBM` 时确认；否则显示“无法从普通应用可靠确认”。 |
| 电池直观展示 | 已完成 | 电量、状态、温度、电压、健康状态、厂商/型号、条件式电量计健康度；电池电量使用本轮实际采集值绘制进度条。 |
| 内存与存储直观展示 | 已完成 | 总/可用内存、ActivityManager 口径使用率、存储总/可用量和 StatFs 口径使用率；内存与存储使用率绘制进度条。 |
| 相机前后置判断正确 | 已完成 | `FEATURE_CAMERA_ANY` 仅称为“摄像头存在”；Camera2 `LENS_FACING` 枚举前置、后置、外置和未知方向。 |
| 相机 metadata | 已完成 | Camera ID、方向、闪光灯、硬件级别、最大 JPEG 分辨率、目标 FPS、自动对焦、RAW、API 28+ 逻辑多摄像头公开能力。 |
| 传感器列表可用 | 已完成 | 传感器数量与名称、厂商、版本、最大量程、分辨率、最小延迟保留在独立完整清单，最多显示系统实际枚举的前 64 项。 |
| 搜索详细信息 | 已完成 | 本地搜索当前采集的模块内容；命中后自动展开、滚动定位；硬件模块索引包含隐藏的 Camera2/传感器清单。 |
| 复制、分享、TXT、JSON | 已完成 | 顶部报告菜单支持复制、系统分享、系统文件选择器 TXT/JSON 导出。 |
| JSON 结构与来源 | 已完成 | JSON 提供 `device`、`system`、`cpu`、`gpu`、`display`、`memory`、`storage`、`battery`、`sensors`、`camera`、`vulkan`、`hardware` 和 `raw`；事实字段具有 `value`、`source`、`confidence`。 |
| 数据来源可查看 | 已完成 | 字段下的来源/可信度标签可点击，显示 API、Camera2、GPU/Vulkan 驱动或 `/proc`、`/sys` 公开节点类别和边界说明。 |
| 刷新不阻塞 UI | 已完成 | `Executors.newSingleThreadExecutor()` 后台采集；UI 线程只接收 `ProbeSnapshot`；刷新期间禁用重复刷新。 |
| 避免重复系统读取 | 已完成 | 电池广播在一次刷新中采集一次并传递至摘要；概览、复制报告和 JSON 使用已采集的文本快照。 |
| 版本号统一 | 已完成 | 主界面、导出文件名和 JSON 使用 `BuildConfig.VERSION_NAME`；Gradle 是 `1.16.0` / versionCode `17`。 |
| 屏幕测试未破坏 | 已完成 | 14 项图案、官方 Display P3 红色机器人、电视方向键、手表长按退出和小屏控制仍保留。 |
| 电视与手表适配未破坏 | 已完成 | `DeviceProfile` 仍保留 PHONE/TABLET/TELEVISION/WATCH、疑似手表和手动纠正；电视 Banner、焦点和遥控器支持保留。 |
| Android 5.0 兼容 | 已完成 | minSdk `21`；新版本 API 使用运行时判断或反射回退；未引入 Material Components 或提高最低 SDK。 |
| 无新敏感权限、无联网/机型库 | 已完成 | APK 清单无 `uses-permission`；代码不含网络访问逻辑，探测以设备公开接口为唯一来源。 |

## 1. 已修改的文件

| 文件 | 本轮最终补齐 |
|---|---|
| `app/src/main/java/com/deviceprobe/inspector/MainActivity.java` | 统一刷新快照，消除概览、复制与 JSON 的重复系统读取；新增电池/内存/存储真实进度条、可点击数据来源说明、相机与传感器直达入口；JSON 增加系统、存储、相机、传感器独立分组。 |
| `app/src/main/java/com/deviceprobe/inspector/CapabilityProbe.java` | 移除 `FEATURE_CAMERA` 后置含义混淆和冗余 API 检查，仅以 `FEATURE_CAMERA_ANY` 表达相机存在，并保留 Camera2 实际枚举。 |
| `README.md` | 更新稳定功能说明，补充快照化刷新、真实进度条、来源点击说明和完整 JSON 分组；不保留版本流水账。 |
| `deliverables/build-verification-v1.16-complete-requirements.txt` | 新增最终构建、签名、ABI、权限和关键功能代码证据。 |

此前已完成且仍保留的相关文件包括 `DeviceProfile.java`、`ScreenTestActivity.java`、`GpuProbe.java`、`VulkanProbe.java`、`NativeBridge.java`、`ProbeNative.c`、`AndroidManifest.xml`、`tv_banner.png` 和官方 `android_p3.png`。

## 2. 增加的功能

本轮最终补齐了按采集快照渲染的概览和导出流程、真实电池/内存/存储使用率进度条、绿色/黄色/红色/灰色状态表达、可点击字段来源说明、相机与传感器的独立快速入口，以及符合要求的 JSON 顶层分组。此前已实现的首页 Hero、六项指标卡、快速检测、Camera2 元数据、搜索、复制/分享/导出、后台采集、来源标签、TV/Watch 和 P3 测试均已重新核对。

## 3. 已删除的旧代码

此前重构已删除旧分类导航、旧即时状态进度条、未接入首页的重复深度文本构建、重复 ABI/框架/电池辅助方法、无用字段和过时版本硬编码。当前本轮继续移除了将 `FEATURE_CAMERA` 混为后置相机的回退条件以及无意义的 API 18 蓝牙条件。

## 4. 已修复的问题

| 问题 | 修复 |
|---|---|
| 概览、复制报告和 JSON 反复读取电池/内存/存储/显示 | 一次刷新产生 `ProbeSnapshot`；后续 UI、TXT 和 JSON 直接使用快照。 |
| 详情来源标签不可追溯 | 每个来源/可信度标签可点击查看来源类别和数据边界。 |
| 内存/存储与电池仅有数字，不够直观 | 增加严格以 ActivityManager、StatFs、ACTION_BATTERY_CHANGED 本轮值计算的进度条。 |
| 相机/传感器快速入口没有直达详细清单 | 分别打开 Camera2 与传感器完整公开枚举。 |
| JSON 缺少用户要求的独立结构分组 | 增加 `system`、`storage`、`camera` 和 `sensors`。 |

## 5. 仍无法可靠读取的硬件信息

普通 Android 应用通常无法可靠获取 DRAM 颗粒厂商和大多数 DDR 代际、屏幕面板物理型号/供应商/原生 10-bit、真实色域覆盖率/色准/亮度/PWM 频率、相机模组与传感器型号、基带/指纹芯片型号，以及实验室意义的电池化学健康度。受 SELinux 或厂商内核限制的 `/proc`、`/sys` 节点也可能不可读。

应用的处理方式是：只显示系统、驱动或内核实际公开的信息；无法确认时展示未知状态并在来源说明中解释原因。电池健康度仅在 `charge_full` 与 `charge_full_design` 均公开时计算“电量计估算”，而非实验室测量。屏幕图案只用于视觉观察，不能伪装成自动仪器检测。

## 6. 编译与产物验证

最终执行：

```bash
./gradlew clean assembleDebug lintDebug
```

结果为 `BUILD SUCCESSFUL`；`lintDebug` 完成且无错误。最终 Debug APK 包名为 `com.deviceprobe.inspector`，versionName 为 `1.16.0`，versionCode 为 `17`，minSdk 为 `21`，targetSdk 为 `35`。APK 不含 `uses-permission`，包含 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 四种 `libdeviceprobe.so`，v1/v2 签名均已通过验证。

当前最终 APK：`device-info-probe-v1.16.0-verified-hardware-debug.apk`，大小 203,619 bytes，SHA-256：

```text
f262ac82ca989081e1acd8d2e7c1c202f29ae65d19ca56b603ac72fbf97ebf95
```

## 7. 下一版本建议

下一版本最有价值的增量是基于真实设备的回归验证，而不是继续堆叠参数。可考虑加入：数据来源筛选、JSON Schema 文档、每个传感器/Camera2 详情的可搜索独立页面、可选的无障碍描述，以及在真实手机、Android TV 和手表上验证滚动、焦点、广色域显示链路与厂商节点可读性。公开发布前还应使用独立的 Release 签名密钥生成正式安装包。
