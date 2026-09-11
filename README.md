# Android 设备信息探查器

> 面向 Android 5.0 及以上设备的本地硬件与系统信息查看工具。它优先显示**当前设备确实向普通应用公开的数据**，而不是根据型号或在线数据库猜测参数。

应用以 Java 界面和 C/NDK 探测层组成，支持手机、平板、电视与手表布局。窗口兼容逻辑使用 AndroidX Core 1.13.1，避免 Android 5.0–9 在验证 API 30 窗口符号时发生 VerifyError。除用户主动使用系统分享或导出功能外，应用不访问网络、不上传探查结果，也不请求危险权限。

## 设计原则

| 原则 | 实现方式 |
|---|---|
| 真实性优先 | C/NDK 优先读取普通应用可访问的 `/proc`、`/sys` 与 POSIX 信息；Android framework、EGL/OpenGL ES、Camera2 与传感器数据作为明确标识来源的补充。 |
| 不猜测 | 未公开、受 SELinux 限制、无法在普通应用权限下确认的信息，会显示“设备未公开”“不可读取”或“无法可靠确认”。 |
| 数据可追溯 | 常规信息标记为系统报告、驱动报告、内核公开值、估算或无法确认；完整原文集中于“技术原始数据”。 |
| 本地优先 | 不使用云端机型数据库，不采集账户、通讯录、位置、序列号、IMEI、广告标识符或外部存储内容。 |
| 兼容优先 | 最低 API 21。对 API 24+ 动态传感器、API 26+ 广色域、API 28+ 逻辑多摄像头和 API 30+ 边到边窗口使用运行时 SDK 守卫，旧系统只显示实际不可用状态。 |

> C/NDK 能读取的是 Android 向沙箱内普通应用暴露的内核接口；它不获得 root 权限，也不能绕过 SELinux。因此，“真实”来自明确的数据来源和权限边界，而不只是使用 C 语言。[1] [2]

## 主要功能

| 模块 | 采集内容 | 主要来源 |
|---|---|---|
| 设备与系统 | 厂商、型号、设备代号、Android/API、安全补丁、设备类型与手动纠正 | `Build`、`Configuration`、`PackageManager` |
| 小屏 Android 手表 | 保留 Wear OS 适配，同时识别非 Wear OS 的华强北/小手机式手表；读取分辨率、逻辑 DPI、xdpi、ydpi、物理对角线和屏幕比例并自动调整 UI dp | `DisplayMetrics`、`Configuration`、`PackageManager` |
| CPU 与 ABI | 系统架构、CPU 架构、CPU ABI、在线逻辑核心、在线 CPU 列表、每核心当前/最大频率候选、调速器、设备首选 ABI、设备支持 ABI、应用实际打包 ABI 与安装推荐 ABI | C/NDK、`uname`、`/sys/devices/system/cpu`、`Build`、Gradle ABI filters |
| GPU 与图形 API | OpenGL ES 厂商、渲染器、版本、GLSL、扩展数量；Vulkan loader、实例 API、物理设备、驱动和扩展 | 实际 EGL/OpenGL ES 上下文、C/NDK 动态加载 Vulkan |
| 内存与存储 | 总内存、可用内存、使用比例、存储总量/可用量、DDR 公开字段与内存硬件名称候选 | `ActivityManager`、`StatFs`、`/proc/meminfo`、公开 `/sys` |
| 电池 | 电量、状态、健康枚举、温度、电压、外接电源、公开的厂商/型号与条件式健康度估算 | `ACTION_BATTERY_CHANGED`、`BatteryManager`、`/sys/class/power_supply` |
| 屏幕 | 分辨率、密度、尺寸估算、刷新率、系统广色域/HDR 报告、10-bit 公开边界、EDID 可读前 256 字节 | `Display`、`DisplayManager`、公开显示节点、DRM/sysfs |
| 硬件能力 | 摄像头存在声明、Camera2 前/后/外置方向、四层摄像头详情、照片/视频尺寸、MP、焦距、光圈、对焦、ISO、曝光、OIS/EIS、RAW、HDR、逻辑/物理摄像头与静态/动态传感器能力 | `PackageManager`、Camera2、`SensorManager` |
| 屏幕测试 | 14 项纯色、灰阶、对比度、饱和度、触控、几何、渐变、闪烁辅助、拖影、残影、色域、视角等测试，含官方 Display P3 红色机器人图 | Android Canvas、MotionEvent、官方 P3 PNG |

## 界面与操作

首页使用设备 Hero 和 CPU、GPU、RAM、存储、屏幕、电池六项核心指标卡，避免将原始日志直接铺满首屏。常用操作包括：

- **搜索**：在当前已采集的本地数据中查找关键词，自动展开并定位对应模块；可搜索 GPU、Vulkan、DDR、电池、相机和传感器字段。
- **刷新**：在单线程后台重新读取 NDK、EGL/OpenGL ES、Vulkan、Camera2、静态/动态传感器和 framework 数据；界面线程只更新采集完成后的不可变快照，并防止重复刷新；Activity 销毁后丢弃待回调结果。
- **报告**：复制全部信息、调用系统分享、通过系统文件选择器导出 TXT，或导出包含 `device`、`system`、`cpu`、`gpu`、`display`、`memory`、`storage`、`battery`、`sensors`、`camera`、`vulkan` 与 `hardware` 分组的 JSON；每个事实字段包含 `value`、`source`、`confidence`。
- **详细信息**：常规模块默认使用键值网格；内存/存储使用率和电池电量按本轮实际采集数值绘制轻量进度条，不生成性能评分；“技术原始数据”默认收起，集中保留内核节点、驱动原文和探测失败原因。
- **来源说明**：点击任意来源/可信度标签，可查看该字段对应的 Android API、Camera2 metadata、OpenGL ES/Vulkan 驱动或 `/proc`、`/sys` 公开节点类别，以及相应的真实性边界。
- **屏幕测试**：进入全屏测试；电视可使用遥控器方向键切换项目，手表使用小屏布局与双箭头控制。

## 数据来源与可信度

应用不把不同层级的数据混为一谈。每个可结构化展示的项目会按下列方式解释来源与可靠性。

| 标签 | 含义 | 示例 |
|---|---|---|
| 系统报告 | Android framework 公开返回的运行时状态或兼容性声明 | 分辨率、当前刷新率、内存、`Display.isWideColorGamut()` |
| 驱动报告 | 当前图形驱动或 Camera2 metadata 直接返回 | `GL_RENDERER`、Vulkan 物理设备、相机 `LENS_FACING` |
| 内核公开值 | 当前应用能够读取的 `/proc` 或 `/sys` 值 | CPUFreq、`power_supply` 电池厂商、DDR 原始候选字段 |
| 估算 | 由明确公式从公开值计算，不等同于硬件实测 | 屏幕对角线估算、电量计的健康度估算 |
| 无法确认 | 系统未公开或信息不能被普通应用可靠推断 | DRAM 颗粒厂商、面板物理型号、原生 10-bit 色深 |

## 相机信息的判定方式

`FEATURE_CAMERA_ANY` 仅代表系统声明存在任意摄像头，**不能**当作“后置摄像头”。应用会使用 Camera2 的 `CameraCharacteristics.LENS_FACING` 实际枚举前置、后置、外置与方向未知的 Camera ID。点击“相机”快速入口后，每个 Camera ID 会以四层卡片显示：基本信息、光学参数、拍摄能力和 Camera2 硬件能力。

详细字段包括最大照片分辨率、按宽 × 高计算的有效像素 MP 与宽高比、主要照片尺寸、焦段推断、真实光学焦距、传感器物理尺寸、由传感器尺寸计算的 35mm 等效焦距、光圈、最小对焦距离、AF/CAF/Fixed Focus、OIS/EIS、最大视频输出尺寸、Camera2 目标 FPS 范围、ISO、曝光时间、RAW/DNG、闪光灯、HDR 场景能力、人脸检测、AE/AWB、`LIMITED/FULL/LEVEL_3/LEGACY` 硬件级别、Manual Sensor/Exposure/Focus、Burst Capture、Logical Camera 与 Physical Camera ID。每个字段都明确区分 Camera2 原始值、计算值、推断值或未公开值；“夜景”不作为通用硬件参数猜测。

其中“有效像素”明确按 Camera2 最大 JPEG 输出尺寸计算，例如 `8000 × 6000 = 48,000,000 ≈ 48.0 MP`；它表示系统可输出的最大照片分辨率，不等同于厂商宣传的传感器理论像素。真实光学焦距与 35mm 等效焦距分开显示，等效焦距带有“计算值”说明。

上述采集不打开相机、不预览、不拍照，也不请求 `CAMERA` 权限。相机模组供应商、镜头传感器型号等未被系统公开的字段不会被机型数据库补全。[3]

## 广色域与屏幕测试说明

“官方 Display P3 红色机器人”使用 Android Developers 文章中提供的原始 `android_p3.png`。进入该项目时，应用在 Android 8.0/API 26 及以上请求广色域窗口模式，并显示系统报告的广色域状态。Android 5.0–7.x 仍可打开原图，但会明确说明系统没有公开的广色域窗口模式。

如果应用、系统色彩管理和广色域显示链路均正常，红色背景中的机器人应当可见。该图用于观察图片的显示链路，**不是**色域覆盖率、色准、亮度、面板供应商或原生色深的仪器测量。此类指标仍需色度计等专业设备。[4] [5]

## 兼容性

| 项目 | 支持范围 |
|---|---|
| 最低系统版本 | Android 5.0 / API 21；界面同时显示当前系统 API level |
| 构建 SDK | compileSdk 35，targetSdk 35；AndroidX Core 1.13.1 |
| 系统架构与原生 ABI | 界面显示 32/64 位及 ARM/x86 系统架构；APK 原生 ABI 为 `armeabi-v7a`、`arm64-v8a`、`x86_64`，已移除已废弃的 32 位 x86 |
| 图形 API | OpenGL ES 实际上下文检测；Vulkan 通过动态 `libvulkan.so` 加载，旧系统无 loader 时安全显示不可用。 |
| 设备形态 | 手机、平板、Android TV/Leanback、Wear OS，以及不声明 Wear feature、但具备小屏/近方形/低分辨率组合线索的华强北式 Android 手表。两套手表规则并存，不相互覆盖。 |
| 权限 | 无 `uses-permission` 条目。 |

电视启动器提供 16:9 Banner，并显式声明触控为可选；这使同一个 APK 不会因默认触控要求而被电视设备过滤。Wear OS 设备继续优先使用系统手表规则。对于华强北式小手机 Android 手表，应用不会依据品牌或“和顺成”等名称断言，而是使用真实分辨率、逻辑 DPI、xdpi/ydpi、物理对角线和近方形比例的组合线索；命中后启用独立的小屏 Android 自适应 dp 规则，并保留用户手动纠正选项。[6] [7]

## 已知边界

| 无法可靠直接读取的项目 | 原因与应用行为 |
|---|---|
| DDR 代际与 DRAM 颗粒厂商 | 仅在公开字段明确包含 `DDR`、`LPDDR` 或 `HBM` 时确认；动态频率、机型、SoC 名称和容量不用于推断。 |
| 面板型号、供应商和原生 10-bit | Android 没有适用于普通应用的统一公开接口；HDR/广色域声明不被当作面板物理参数证明。 |
| 色域覆盖率、色准、亮度、PWM 频率 | 屏幕图案为视觉观察工具；精确测量需要外部相机、示波器或色度计。 |
| 电池真实化学健康度 | 仅在驱动同时公开 `charge_full` 和 `charge_full_design` 时计算电量计估算值，不宣称实验室测量结果。 |
| 受权限限制的内核节点 | 保留实际不可读状态或错误原因；应用不尝试 root、`su` 或修改 SELinux。 |

## 安装

请从仓库的 **[Releases](https://github.com/ZYJ882/android-device-probe/releases)** 页面下载最新 APK。Release 页面提供经过 R8 压缩并签名的安装包；Android 5.0 通常需要允许“未知来源”，较新 Android 版本需要允许用于安装的文件管理器或浏览器“安装未知应用”。

也可在已开启 USB 调试的设备上使用：

```bash
adb install -r <downloaded-apk>.apk
```

> 仓库不包含任何私钥。若要长期维护或发布到应用商店，请使用自己妥善保管的发布密钥，并通过 Gradle 属性 `DEVICE_PROBE_RELEASE_STORE_FILE`、`DEVICE_PROBE_RELEASE_STORE_PASSWORD`、`DEVICE_PROBE_RELEASE_KEY_ALIAS` 和 `DEVICE_PROBE_RELEASE_KEY_PASSWORD` 配置 release 签名。未配置时构建脚本仅回退到 debug 签名，便于本地验证。

## 从源码构建

项目使用 Gradle、Java、CMake 和 Android NDK。准备 Android SDK Platform 35、Build Tools 35.0.0、NDK 27.2.12479018 与 CMake 3.22.1 后，在项目根目录创建 `local.properties` 并设置 SDK 路径：

```properties
sdk.dir=/path/to/Android/Sdk
```

然后执行：

```bash
./gradlew assembleDebug
```

首次构建需要可访问 Gradle/Maven 依赖源，以下载 `androidx.core:core:1.13.1`。

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

建议在提交前执行完整验证：

```bash
./gradlew clean assembleDebug assembleRelease lintDebug lintRelease
```

## 工程安全与性能

原生报告缓冲区使用堆内存并在 JNI 返回后释放；`/proc`、`/sys` 和 EDID 读取处理 EINTR/短读，并在报告达到上限时明确标记截断。屏幕测试使用 AndroidX `WindowCompat`、`WindowInsetsCompat` 和 `WindowInsetsControllerCompat`，手表提供长按退出，触摸轨迹按 pointer ID 处理；导出使用 try-with-resources，Activity 销毁后不会继续更新已分离的 View。

Release 默认启用 R8 和资源压缩。签名密钥只从仓库外的 Gradle 属性读取，避免私钥进入源码或版本历史。

## 项目结构

```text
app/
├── src/main/cpp/                 # C/NDK：/proc、/sys、CPU、内存、电池、动态 Vulkan 探测
├── src/main/java/.../            # Java：界面、EGL/OpenGL ES、Camera2、设备类型与屏幕测试
├── src/main/res/raw/android_p3.png   # 官方 Display P3 测试图
├── src/main/res/drawable-nodpi/  # TV Leanback Banner
└── build.gradle                  # SDK、ABI、CMake 与版本配置
deliverables/                     # 当前构建验证记录与交付说明
```

## 版本与发布

仓库主页只维护稳定的功能、边界、构建和使用说明。每次版本的新增功能、修复、兼容性变化、APK 校验值与下载附件均发布在 **[GitHub Releases](https://github.com/ZYJ882/android-device-probe/releases)**，不在本 README 中累积版本流水账。

## 参考资料

[1]: https://developer.android.com/ndk/guides/stable_apis "Native APIs | Android NDK"
[2]: https://source.android.com/docs/security/features/selinux "Security-Enhanced Linux in Android | AOSP"
[3]: https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics "CameraCharacteristics | Android Developers"
[4]: https://android-developers.googleblog.com/2019/05/wide-color-photos-are-coming-to-android.html "Wide Color Photos Are Coming to Android | Android Developers Blog"
[5]: https://developer.android.com/training/wide-color-gamut "Enhance graphics with wide color content | Android Developers"
[6]: https://developer.android.com/training/tv/start/start.html "Build TV apps | Android Developers"
[7]: https://developer.android.com/guide/topics/manifest/uses-feature-element.html "<uses-feature> | Android Developers"
