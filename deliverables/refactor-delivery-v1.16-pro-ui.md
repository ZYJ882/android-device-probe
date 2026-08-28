# v1.16.0 专业信息界面改造交付说明

## 概览

本次改造依据用户提供的审查文件完成。目标是保留现有 C/NDK、OpenGL ES、Vulkan、DDR、电池与屏幕测试的真实性优先原则，同时将信息密集的日志式页面重构为首屏可读、详细信息可追溯的硬件探查器。应用版本仍为 `1.16.0`（versionCode `17`），最低支持 Android 5.0 / API 21。

> 本版不会依据机型、品牌、SoC 名称或在线数据库补全硬件字段。系统未公开、普通应用权限下不可读或不能可靠判断的值，会显示“设备未公开”“无法确认”或“估算”。

## 1. 已修改的文件

| 文件 | 本次修改 |
|---|---|
| `app/src/main/java/com/deviceprobe/inspector/MainActivity.java` | 完整重构首页和详细模块；后台刷新；本地搜索；复制、分享、TXT/JSON 导出；来源/可信度标签；清理旧版 UI 方法。 |
| `app/src/main/java/com/deviceprobe/inspector/CapabilityProbe.java` | 修复相机方向判断；通过 Camera2 枚举前/后/外置相机和公开元数据。 |
| `app/src/main/cpp/ProbeNative.c` | 将 Vulkan `VkApplicationInfo` 的应用版本标识由遗留 `1.14.0` 统一为 `1.16.0`。 |
| `app/build.gradle` | 启用 `BuildConfig` 生成，让界面、导出文件名和 JSON 报告使用 Gradle 的实际版本号。 |
| `app/src/main/AndroidManifest.xml` | 增加电视 Banner，声明触控对电视可选；去掉会限制通用 APK 的 Wear feature 声明。 |
| `app/src/main/res/drawable-nodpi/tv_banner.png` | 新增 320×180 电视 Leanback 启动器 Banner。 |
| `app/src/main/res/values/styles.xml` | 移除基础主题中 API 23+ 才有的状态栏属性，保持 API 21 安全加载。 |
| `README.md` | 更新 UI、导出、Camera2、后台采集、电视 Banner 与最新 APK 验证说明。 |
| `refactor-audit-v1.16.md` | 新增改造前问题与真实性边界审查记录。 |

## 2. 新增和改进的功能

| 功能 | 实现与真实性边界 |
|---|---|
| 首屏 Hero 与硬件概览 | 首屏突出设备型号、Android/API、设备类型、安全补丁，以及 CPU、GPU、RAM、存储、屏幕、电池六项。数值来自当前设备公开信息。 |
| 快速检测入口 | 提供屏幕测试、图形 API、电池、传感器、相机和硬件能力入口；屏幕测试原有 14 项未删除。 |
| 详情键值网格 | 普通信息不再连续堆叠为文本墙，改为两列紧凑网格；技术原文继续以等宽文本集中保留。 |
| 数据来源/可信度标签 | 以系统报告、驱动报告、内核公开值、估算、无法确认标记重要数值。标签来自数据路径和字段性质，不把不确定信息标为已确认。 |
| 后台采集 | 使用单线程 `ExecutorService` 采集 NDK、EGL/OpenGL ES、Vulkan、Camera2 和传感器；界面线程仅应用快照。刷新期间防止重复点击。 |
| 本地搜索 | 搜索仅针对本轮已采集的数据，自动展开并定位匹配模块；支持 GPU、Vulkan、DDR、电池、温度、摄像头、传感器等关键词。 |
| 报告操作 | 顶部报告菜单支持复制全部信息、系统分享、通过系统文件选择器导出 TXT，以及结构化 JSON。 |
| 结构化 JSON | 分为 `device`、`cpu`、`gpu`、`vulkan`、`display`、`memory`、`battery`、`hardware` 与 `raw`；每个事实字段包含 `value`、`source`、`confidence`，未知值为 JSON `null`。 |
| Camera2 相机修复 | `FEATURE_CAMERA_ANY` 只表示系统声明存在任意相机；前置/后置/外置方向由 `CameraCharacteristics.LENS_FACING` 实际枚举。完整清单还能显示最大 JPEG 分辨率、目标 FPS、自动对焦、RAW、API 28+ 逻辑多摄像头公开能力。 |
| TV / Watch 兼容性 | 保留原有手机、平板、电视、手表识别和手表手动纠正；新增 TV Banner，触控明确为可选，电视焦点和屏幕测试遥控器控制未改动。 |

## 3. 已清理的旧代码

已从 `MainActivity` 删除未接入首页的 `createCategoryNavigation()`、`createStatusDashboard()` 及关联进度条状态逻辑；删除重复的旧深度文本构建、未使用的 ABI/框架/电池辅助方法和无用导入。新版首页不再保留旧版硬编码 `v1.14` 字样。

## 4. 已修复的问题

| 问题 | 修复 |
|---|---|
| 顶部版本显示为 `v1.14` | 改为 `BuildConfig.VERSION_NAME`；导出文件名与 JSON `app_version` 使用同一真实版本来源。 |
| 刷新阻塞界面线程 | 采集移入单线程后台执行并以主线程快照更新。 |
| `FEATURE_CAMERA_ANY` 被称为后置摄像头 | 更正为“摄像头存在”；以 Camera2 `LENS_FACING` 确认相机方向。 |
| CPU 每核心频率挤在单行 | 拆分为 `CPU 0 当前频率`、`CPU 1 当前频率` 等独立可读条目；不在缺少可靠拓扑时猜测大/中/小核。 |
| TV Lint / 清单兼容错误 | 增加 Leanback Banner、触控可选声明，移除无效 Wear feature；Lint 已无错误。 |
| API 21 样式风险 | 移除基础 `values` 中 API 23 才引入的 `windowLightStatusBar` 属性。 |
| Vulkan 应用版本遗留硬编码 | 由 `1.14.0` 更新为 `1.16.0`。 |

## 5. 仍无法由普通应用可靠读取的信息

| 项目 | 原因与应用行为 |
|---|---|
| DRAM 颗粒厂商与多数 DDR 代际 | Android 通常不向普通应用公开；只有 `/sys`、设备树或 debug 节点明确提供 `DDR`/`LPDDR`/`HBM` 字段才确认，否则显示无法可靠确认。 |
| 面板物理型号、供应商、原生 10-bit | 不存在通用普通应用 API；HDR 或 Wide Color Gamut 系统声明不会被当作原生 10-bit 或 P3 覆盖率证明。 |
| 真实色域覆盖率、色准、亮度 | Display P3 红色机器人和其他图案仅用于视觉观察；精确测量需要色度计等仪器。 |
| 电池真实化学健康度与实验室容量 | 仅在驱动同时公开 `charge_full` 与 `charge_full_design` 时显示“电量计估算”；不宣称为实验室测量。 |
| 相机模组、基带、指纹芯片型号 | 不会根据设备型号猜测；仅显示 Camera2、PackageManager 和系统公开元数据。 |
| 受 SELinux / 厂商限制的 `/proc`、`/sys` 节点 | 读取失败时保留实际不可读状态或原始错误；不使用 root 或提权。 |

## 6. 构建与验证结果

| 项目 | 结果 |
|---|---|
| 构建命令 | `./gradlew clean assembleDebug lintDebug` |
| 构建结果 | 成功；`lintDebug` 完成且无错误。 |
| 应用 ID / 版本 | `com.deviceprobe.inspector` / `1.16.0`（versionCode `17`） |
| SDK | minSdk `21`，targetSdk `35`，compileSdk `35` |
| 权限 | 无 `uses-permission` 条目。 |
| ABI | `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 均包含 `libdeviceprobe.so`。 |
| 签名 | APK Signature Scheme v1 与 v2 均验证通过。 |
| APK | `device-info-probe-v1.16.0-pro-ui-debug.apk`，201,464 bytes。 |
| APK SHA-256 | `01278bce91879fa22d57a236138f583c9eb128e222a90e6d33c0ef7774160e2c` |

完整命令输出见 `deliverables/build-verification-v1.16-pro-ui.txt`。

## 7. 后续版本建议

后续可考虑在不破坏真实性的前提下，增加按数据来源筛选、导出 JSON Schema 文档、完整传感器搜索页、Camera2 输出格式/逻辑摄像头详情页，以及在真实设备上的性能与显示链路回归测试。若公开发布，应使用用户自己的发布密钥生成 Release APK，而不是将 Debug 签名作为生产签名。

## 仓库

公共仓库已更新至提交 [`7cc6564`](https://github.com/ZYJ882/android-device-probe/commit/7cc65647189dd8a86bfa9e3448038b0cc9603b44)：[ZYJ882/android-device-probe](https://github.com/ZYJ882/android-device-probe)。
