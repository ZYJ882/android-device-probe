# 设备信息探查器（Android 5.0+）

**版本：1.16.0**  
**应用 ID：`com.deviceprobe.inspector`**  
**最低支持：Android 5.0 / API 21**  
**构建类型：已签名的 Debug APK（用于测试安装）**

这是一个本地离线运行的 Android 设备信息查看工具。其原则不是“只要 C 语言就一定最底层”，而是**在普通应用权限边界内，让 C/NDK 优先读取内核向应用公开的真实只读数据**，并把 Android 框架提供的系统属性分开展示。

> C/NDK 能够直接调用 POSIX 接口和读取普通应用可访问的 `/proc`、`/sys` 节点；它不会自动获得 root 或绕过 Android 沙箱、SELinux。数据的真实性取决于来源与权限，而不单取决于编程语言。[1] [2]

## 1.16.0 官方 Display P3 红色机器人测试

本版将第 14 项屏幕测试替换为 Android Developers 官方文章所用的 `android_p3.png`，原始文件以 `res/raw` 资源打包，构建后 APK 内资源 SHA-256 与下载原图均为 `c39618c20ad5dfca7596bc3fbe0e37c7cc3de5cf68b78b3bf088282f2e49ea30`。该文件携带用于显示 Display P3 差异的内容；本应用不再用普通 sRGB Canvas 红色机器人替代它。[10]

进入“官方 Display P3 红色机器人”时，应用会在 API 26+ 通过公开 `Window.setColorMode()` 请求广色域窗口模式，同时读取当前显示器的广色域报告并把结果显示在测试顶部。系统是否最终授予模式取决于 Android 版本、系统色彩管理和实际显示器能力；这不是运行时权限申请。切换到其他测试项目后，窗口恢复默认色彩模式。Android 5.0–7.x 仍可打开并显示原图，但会如实标明没有公开的广色域窗口模式，不能以此作出 P3 显示已生效的结论。[11]

当 P3 图片、应用色彩管理和广色域屏幕链路全部正常时，红色背景中会显现 Android 机器人；如果只看到较均匀的红色，通常表示该图片没有按广色域路径呈现。该图用于验证此图片的显示链路，**不能**测量 DCI-P3 覆盖率、色准、亮度或面板型号，精确测量仍需要色度计。

## 1.15.0 OpenGL ES 与 Vulkan 支持检测

本版新增独立的“**图形 API**”板块。OpenGL ES 继续通过实际 EGL/OpenGL ES 离屏上下文读取 `GL_VENDOR`、`GL_RENDERER`、`GL_VERSION`、GLSL 版本和扩展数量；Vulkan 则在 C/NDK 中使用 `dlopen("libvulkan.so")` 动态加载 loader，枚举实例 API 版本、物理设备数量、设备名称、厂商/设备 ID、设备类型、设备 API/驱动版本、队列族数量和设备扩展数量。动态加载使同一个最低 API 21 安装包能够在 Android 5.0 设备上安全运行：没有 Vulkan loader 的设备只显示不可用，不会因静态链接 Vulkan 导致启动失败。[8] [9]

Vulkan 的“系统特性已声明”与“原生物理设备已枚举”被刻意分开显示。前者是 Android framework 的兼容性声明，后者是当前 loader/驱动的实际探测结果；当厂商实现不公开字段、系统版本低于 API 24 或驱动加载失败时，应用显示实际不可读状态，不用机型数据库猜测。OpenGL ES 与 Vulkan 是两套独立 API，检测到其中一套并不代表另一套必然可用。

## 1.14.0 精简信息架构与去重展示

详细信息分成两层：第一层是普通用户优先的“设备与系统”“性能与芯片”“图形 API”“内存与存储”“电池”“屏幕”“硬件能力”，每个结论只在一个板块出现；第二层是默认收起的“技术原始数据”，集中保留 C/NDK、GPU、CPU、内存、DDR、屏幕和电池的原始内容。

普通用户优先看到的是“总内存、当前可用内存、分辨率、刷新率、电量、健康状态”等易懂名称。处理器 ABI 与 GPU 信息集中于“性能与芯片”；DDR 和内存硬件候选集中于“内存与存储”；电池厂商和型号集中于“电池”。需要排查问题时，再展开单一的“技术原始数据”查看 C/NDK、`/proc`、`/sys` 和驱动原始值。

新增“硬件厂商”板块，优先展示电池 `power_supply` 驱动实际公开的 `manufacturer`、`model_name`/`model`，并尝试读取 Linux memory block 的 `phys_device` 作为内存硬件名称候选。标准 Android 普通应用通常无法可靠获得 DRAM 颗粒厂商和电量计芯片厂商；应用会明确显示“未公开或无法确认”，不会依据手机型号猜测。

新增“屏幕测试”入口，使用 Android 自定义 View/Canvas 绘制全屏测试图案，包含坏点/亮点/暗点纯色、灰阶、对比度、色彩饱和度、触控、几何/边缘、RGB 渐变、闪烁辅助（PWM）、响应拖影、残影老化、色域参考、视角和色域检测共 14 项。新增“官方 Display P3 红色机器人”图案，原图在 API 26+ 请求广色域窗口后显示，并在顶部报告系统的请求与显示器状态。测试页面支持上一项、下一项、退出；纯色测试点击画面切换颜色，触控测试显示手指轨迹。坏点、视角、拖影、残影和 PWM 只能由用户观察或外部相机/仪器辅助判断，软件不会伪造自动诊断结果；实际色域测量需要校色仪。

应用会自动识别设备类型，优先检查 Android 公开系统特性，而不是只按分辨率猜测：`FEATURE_WATCH` 或 `UI_MODE_TYPE_WATCH` 确认为手表；`FEATURE_LEANBACK`、`FEATURE_TELEVISION` 或电视 UI 模式确认为电视；未命中时按 `smallestScreenWidthDp` 辅助区分平板和手机。电视端会显示大屏规则，所有操作按钮和可展开板块均可获得焦点并以薄荷绿描边提示；屏幕测试支持遥控器左/上切换上一项、右/下切换下一项、确认键切换纯色。手表端使用单列紧凑概览、较小边距、两枚箭头控制屏幕测试，并支持长按测试画面退出；测试图案会按实际可用显示区域、手表小屏网格和色阶数量重新绘制。

对于华强北等可能未声明标准 Wear 特性的原生 Android 套壳手表，应用会以“**疑似 Android 手表**”显示，而不是把推断伪装成事实。该判断要求多个公开线索同时满足，例如很小的最小宽度、较短的像素边长、接近方形的显示比例，或 API 23+ 的圆屏线索；设备与系统板块会逐项列出依据。首页新增“**纠正设备类型**”入口，可选自动、手机、平板、电视或手表；选择只保存在本应用中并影响布局规则，不修改系统，且仍保留自动判定供比对。品牌、机型和“和顺成”等方案名称不会用于断言系统类型。

新增“**性能硬件详情**”板块。CPU 由 C/NDK 显示 `uname.machine`、POSIX 在线逻辑核心、`/sys` 在线 CPU 列表，以及设备实际公开的每核心拓扑、调速器、当前/最大频率候选节点；GPU 在本应用创建离屏 EGL/OpenGL ES 上下文后读取 `GL_VENDOR`、`GL_RENDERER`、`GL_VERSION`、GLSL 版本与扩展数量；内存并列显示 Android `MemoryInfo` 的总量/可用量/阈值和 `/proc/meminfo` 的 `MemTotal`、`MemAvailable`、缓存、可回收缓存与 swap。CPU 频率、GPU 字符串和内存字段均来自实际系统/驱动公开值；节点不可读时显示实际状态，不会用 SoC 数据库推测。

内存详情现已增加“**DDR 内存类型**”。C/NDK 会检查设备实际公开的 memory controller、运行时设备树、debugfs 与 devfreq 候选属性。**只有**原始值明确含 `DDR`、`LPDDR` 或 `HBM` 时，才显示为已确认的内存类型并附原始路径；否则会显示“无法从普通应用可靠确认”。动态内存频率、机型、SoC 名称、内存容量和品牌均不会被用于推断 DDR 代际；`devfreq` 节点只作为内存控制器/动态频率信息展示。完整原始结果位于“DDR 内存类型依据”板块。

本应用保留直观概览、硬件能力列表和单项测试入口：概览只显示设备、系统、逻辑核心、可用内存、可用空间与电池六张卡；“硬件能力”通过 Android `PackageManager` 和 `SensorManager` 显示系统公开的摄像头、触控、定位、Wi‑Fi、蓝牙、NFC、USB、电视/手表适配规则和传感器清单；首页“屏幕测试”直接进入 14 项屏幕测试，其中包含官方 Display P3 红色机器人测试。它不会把机型资料、网上参数或 feature flag 伪装成实际芯片或模组信息。

屏幕板块现增加可验证的系统级**广色域（Wide Color Gamut）**、**HDR 类型**与 **10-bit 显示边界**。广色域使用 `Display.isWideColorGamut()` 的系统报告（API 26+），HDR 使用 `Display.getHdrCapabilities()` 的系统报告（API 24+），均以反射方式调用以确保 Android 5.0 兼容。系统报告的广色域/HDR 能力不能单独证明面板原生 10-bit；Android 没有供普通应用统一确认面板原生 10-bit 的公开 API，因此会如实显示这一限制。屏幕测试中的色域图案是视觉参考，色域覆盖率和色准仍需色度计实测。

本版以“**一项结论，一个常用入口**”为原则压缩页面：首页仅保留六张概览卡（设备、系统、逻辑核心、可用内存、可用空间、电池）与“刷新信息、屏幕测试”两项操作；移除了与概览卡重复的即时状态进度条、分类导航和复制按钮。详细信息精简为“设备与系统、性能与芯片、图形 API、内存与存储、电池、屏幕、硬件能力”七组；系统兼容性合并进性能与芯片，内存/电池厂商各自合并进所属板块，所有 NDK、GPU、CPU、DDR、屏幕和电池原始信息合并为一个默认收起的“技术原始数据”入口。手动设备类型纠正只在疑似手表时显示。


应用 ID 为 **`com.deviceprobe.inspector`**，不含 `manus`。新的应用 ID 同时用于 Java 包命名空间与 C/NDK 的 JNI 导出函数名，二者已经同步迁移。

| 展示板块 | 读取来源 | 数据示例 | 默认状态 |
|---|---|---|---|
| 设备与系统 | Android `Build` | 厂商、型号、Android 版本、安全补丁、设备代号 | 展开 |
| 屏幕 | `Display`、`DisplayManager` 与公开 sysfs 候选节点 | 分辨率、密度、刷新率、广色域系统检测、HDR 类型、10-bit 公开边界、面板公开边界 | 收起 |
| 内存与存储 | `ActivityManager.MemoryInfo`、`/proc/meminfo`、`StatFs`、DDR 候选节点 | 总/可用内存、DDR 类型结论、阈值、缓存、swap、分区空间 | 收起 |
| 电池 | 电池粘性广播、`BatteryManager`、`/sys/class/power_supply` | 电量、健康枚举、电压、温度、电流、计数、SOH 条件估算 | 收起 |
| 性能与芯片 | C/NDK、公开 `sysfs`、`Build` | CPU 架构/频率候选、当前 native ABI 与设备首选 ABI | 收起 |
| 图形 API | 实际 EGL/OpenGL ES 上下文、Android framework、C/NDK 动态 Vulkan loader | OpenGL ES 厂商/渲染器/版本、Vulkan 系统特性、设备和驱动版本、扩展数量 | 收起 |
| 硬件能力 | `PackageManager`、`SensorManager`、Camera2 枚举 | 摄像头、触控、定位、Wi-Fi、蓝牙、NFC、USB、传感器清单；仅显示公开声明/枚举结果 | 收起 |
| 屏幕测试 | Android Canvas、MotionEvent、官方 Display P3 PNG、全屏沉浸式 Activity | 14 项显示/触控测试图案；第 14 项请求广色域窗口并显示官方 P3 原图；自动适配电视/手表控制 | 入口 |
| 设备类型与布局 | `PackageManager`、`Configuration.uiMode`、显示指标 | 确认/疑似/未确认的手机、平板、电视、手表识别，判定依据与手动纠正 | 设备与系统 |
| 技术原始数据 | C/NDK、`/proc`、公开 `sysfs`、实际 EGL/OpenGL ES 上下文、动态 Vulkan loader | CPU、OpenGL ES、Vulkan、内存、屏幕、DDR 和电池的全部原始读取结果 | 收起 |
| 数据边界 | C/NDK 采集过程的实际结果 | 可读性、沙箱和 SELinux 限制 | 收起 |

## 功能与隐私边界

本应用不申请任何 Android 权限，不访问网络，不读取账户、联系人、位置、电话号、IMEI、序列号、广告标识符、相册或外部存储内容。它不尝试 root、提权、执行 `su`、修改 SELinux，也不会把不可读取的文件伪造成“设备不存在”。每一个无法读取的内核节点将显示实际失败原因。

界面提供“刷新信息”和“屏幕测试”两项高频操作。应用本身不会上传或保存探查结果。

## 安装方法

将 `device-info-probe-v1.16.0-official-p3-debug.apk` 复制到目标设备后，用系统“文件管理”或“下载”应用打开安装。Android 5.0 通常需要在“设置 → 安全”中临时允许“未知来源”；较新系统会要求为用于安装的应用单独允许“安装未知应用”。也可在已启用开发者选项和 USB 调试的情况下执行：

```bash
adb install -r device-info-probe-v1.16.0-official-p3-debug.apk
```

安装包为**测试签名** Debug APK，已经通过 v1 和 v2 签名验证，其中 v1 签名可支持 Android 5.0 的安装。它适合直接安装验证；若将来要公开发布或持续升级，应使用您自己妥善保管的发布密钥重新签名，切勿把调试签名当作生产签名。

## 构建验证结果

| 项目 | 已验证结果 |
|---|---|
| 应用 ID / 版本 | `com.deviceprobe.inspector` / `1.16.0`（versionCode 17） |
| 最低 SDK | `21`（Android 5.0） |
| 运行权限 | 无 `uses-permission` 条目 |
| 签名 | APK Signature Scheme v1、v2 均已验证 |
| 启动器图标 | `mdpi`、`hdpi`、`xhdpi`、`xxhdpi`、`xxxhdpi` 五种密度资源已打包 |
| 原生 ABI | `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` |
| target SDK | `35`（不影响 `minSdk 21` 的 Android 5.0+ 兼容范围） |
| 图形 API | OpenGL ES 实际 EGL 上下文；Vulkan 动态 `libvulkan.so` 查询，不静态链接 |
| 官方 P3 图片 | `res/raw/android_p3.png` 原图字节已核对；APK 内 SHA-256 一致 |
| APK SHA-256 | `33129034e96e4f1998a725e88fa14807cdabd6bc75aa351c1cbb948da700d0e3` |

完整验证记录见 `deliverables/build-verification-v1.16-official-p3.txt`。

## 从源码重建

项目包含 Gradle wrapper、Java 界面源码、C/NDK 源码与各密度图标资源。安装 Android SDK Platform 35、Build Tools 35.0.0、NDK 27.2.12479018、CMake 3.22.1 后，将 `local.properties` 的 `sdk.dir` 改为您的 Android SDK 路径，再在项目根目录执行：

```bash
./gradlew assembleDebug
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 参考资料

[1]: https://developer.android.com/ndk/guides/stable_apis "Native APIs | Android NDK"
[2]: https://source.android.com/docs/security/features/selinux "Security-Enhanced Linux in Android | AOSP"
[3]: https://developer.android.com/reference/android/os/Build "Build | Android API Reference"
[4]: https://developer.android.com/reference/android/hardware/display/DisplayManager "DisplayManager | Android Developers"
[5]: https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo "ActivityManager.MemoryInfo | Android Developers"
[6]: https://developer.android.com/reference/android/os/BatteryManager "BatteryManager | Android Developers"
[7]: https://source.android.com/docs/core/perf/health/implementation-2-1 "Implement Health 2.1 | Android Open Source Project"
[8]: https://developer.android.com/ndk/guides/graphics "Graphics | Android NDK"
[9]: https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_VULKAN_HARDWARE_LEVEL "PackageManager Vulkan feature constants | Android Developers"
[10]: https://android-developers.googleblog.com/2019/05/wide-color-photos-are-coming-to-android.html "Wide Color Photos Are Coming to Android | Android Developers Blog"
[11]: https://developer.android.com/training/wide-color-gamut "Enhance graphics with wide color content | Android Developers"

---

更新日期：2026-08-28
