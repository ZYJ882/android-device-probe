# 更新日志（Changelog）

本项目所有对用户可见的变更都记录在本文件中。

格式参考 [Keep a Changelog 1.1.0](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循[语义化版本 2.0.0](https://semver.org/lang/zh-CN/)：

- **Major（X）**：破坏对外接口或数据格式（例如 JSON 报告 schema 不兼容变更）；
- **Minor（Y）**：向后兼容的新功能或改进（本次 1.22.3 → 1.23.0 即属此类）；
- **Patch（Z）**：仅含向后兼容的缺陷修复。

本文件自 1.23.0 起开始规范记录；更早的历史版本未留存逐版变更明细。

## [未发布]

### 仓库规范（不影响应用代码，无 versionCode 变更）

- 对齐 GitHub 开源仓库社区标准：新增根目录 `LICENSE`（MIT）；README 新增「许可证」章节。
- 补齐 Gradle Wrapper 的 Windows 启动脚本 `gradlew.bat`（与 gradle-wrapper.properties 中 8.10.2 版本对应的官方脚本）。
- `.gitignore` 增加构建产物（`*.apk`、`*.aab`、`*.dex`、`*.ap_`、`.externalNativeBuild/`）与 `Thumbs.db` 忽略规则。

## [3.2.0] - 2026-11-03

**主题：方案 B——采集层直接产出结构化对象，删掉文本解析器。**
3.1.0 的 camera / sensors 是「先拼文本、再把文本解析回结构」，解析规则依赖采集层文案，改一句提示语就可能悄悄丢字段。本次让 `CapabilityProbe` 直接返回 `CapabilityModel`，界面、对话框、文本报告和 JSON 导出全部读同一批对象。`schema_version` 保持 `3`（无键删除，只有新增），versionCode 30 → 31。

### 新增

- `CapabilityModel`：纯 Java 模型，含相机字段（稳定 key + 展示标签 + 值 + 未知标记）、传感器数值、动态传感器，以及由模型渲染的原始文本 `renderText()`。
- `CapabilityJson`：`CapabilityModel` → `camera` / `sensors` 两个 JSON 分组的纯映射，零 Android 依赖。
- 相机 `optics` 新增 `max_photo_width`、`max_photo_height`、`max_photo_mp`、`aspect_ratio` 四个结构化字段；不可读相机的 item 新增 `error`（异常类名）。
- 此前解析表未覆盖的相机字段现在一并导出，不再被静默丢弃。

### 变更

- `CapabilityProbe.collect()` 返回 `CapabilityModel`；`ProbeSnapshot.capability` 取代原来的字符串字段。
- 相机详情对话框、传感器清单对话框改为直接渲染模型对象；传感器清单不再受 64 条文本上限约束，可看到全部枚举项。
- `SectionBuilder` 的相机方向计数、最大像素摘要改读模型，不再用正则扫文本。
- 「技术原始数据」中的枚举原文由 `CapabilityModel.renderText()` 生成，与界面/导出同源。

### 移除

- `ProbeTextParser` 及其测试：文本解析路径整体删除，`CapabilityProbe` 的文案不再影响数据结构。

### 修复

- `SENSOR_INFO_PIXEL_ARRAY_SIZE` 为空时 `formatSize(null)` 触发 NPE，导致整台相机被误判为「metadata 不可读取」。
- `Physical Camera ID` 为空集合时文本含「未公开」，旧解析器会把「无」错误导出成 `null`；现在按真实值导出。
- 相机 item 的 `flash` 被 `capture` 块里的「有（Camera2 原始）」覆盖成 `unknown`（3.0.0 起所有摄像头都如此）；现在直接取 `FLASH_INFO_AVAILABLE`，正确输出 `available` / `unavailable`。

### 测试

- JUnit 单测增至 14 条：`CapabilityModelTest`（计数、像素摘要、文本渲染、传感器截断、未知标记约定）与 `CapabilityJsonTest`（键结构、未知值转 null、不可读相机、传感器数值类型）。两者均为纯 JVM 测试。

## [3.1.0] - 2026-11-03

**主题：补上刘海/挖孔信息，并把相机/传感器解析变成可回归的单测对象。**
3.0.0 把 camera/sensors 结构化后，解析逻辑埋在 `ExportUi`（依赖 Android），无法单测；同时屏幕分区缺一项物理特征——刘海/挖孔位置。本次把解析抽成纯类、补 `DisplayCutout`、加 JUnit 单测与 CI。JSON 只新增 `display_cutout`，`schema_version` 保持 `3`。versionCode 29 → 30。

### 新增

- **屏幕新增「刘海 / 挖孔」行**（key `display_cutout`，API 29+）：输出安全区上/下/左/右像素、挖孔数量与首处尺寸；API 30+ 追加瀑布屏内缩。无挖孔时显示「无（系统未报告挖孔）」，API 28 及以下不显示该行。
- **单元测试**：`app/src/test/java/com/deviceprobe/inspector/ProbeTextParserTest.java`，6 条用例覆盖相机计数/方向/闪光灯/子对象、未知值转 `null`、metadata 不可读、传感器复合行拆分、未公开不回填、空文本。
- **CI**：`.github/workflows/android.yml`，推送/PR 时依次执行 `testDebugUnitTest` → `assembleDebug` → `lintDebug`，并上传 lint 报告。

### 变更

- **解析逻辑从 `ExportUi` 抽到 `ProbeTextParser`**：新类不依赖 Android，仅用 `org.json` 与正则，因此可在 JVM 单测里直接运行；`ExportUi` 只保留 JSON 文档组装。
- **修复传感器复合行解析**：`最大量程: 19.6；分辨率: 0.001；最小延迟: 10000 µs` 原先只取到第一个字段，分辨率与最小延迟被丢弃；现按 `；` 切段逐段解析。`厂商: Bosch；版本: 3` 同样改为逐段解析。

### 构建

- `app/build.gradle` 新增 `testImplementation 'junit:junit:4.13.2'` 与 `testImplementation 'org.json:json:20231013'`（后者仅供 JVM 单测，不会打包进 APK）。

## [3.0.0] - 2026-11-03

**主题：相机与传感器 JSON 结构化——移除 2.0.0 的「例外」声明。**
2.0.0 把其它分组都改成了稳定英文 key，但相机和传感器仍由采集层文本块透传、用块内标签作 key，导致三个问题：序号行（如 `1. 加速度计`）被整行丢弃、下标（如 `名称_2`）只代表文本出现顺序而非硬件身份、复合值（如 `厂商: x；版本: 3`）字段边界错乱。本次把这两组也解析成层级化的 `items` 数组 + 稳定英文 key；`schema_version` 升到 `3`。versionCode 28 → 29。

### 破坏性变更（需下游适配）

- **`camera` / `sensors` 两组改为结构化 `items` 数组**：不再用中文标签作 key，每组输出稳定的英文对象数组。
- **`schema_version` 由 `2` 升为 `3`**。
- 移除 2.0.0 对这两组的「例外」声明（见 2.0.0 破坏性变更条目末条），它们现在与其它分组一致。

### 新增

- **`camera` 分组结构**：顶层 `count` / `back` / `front` / `external` / `unknown_facing` 汇总，加 `items` 数组。每个摄像头 item 含 `id`、`facing`（枚举 `front` / `back` / `external` / `unknown`）、`flash`（枚举 `available` / `unavailable` / `unknown`）、`hardware_level`，以及三个子对象 `optics`（光学参数）、`capture`（拍摄能力）、`hardware`（Camera2 硬件能力），子对象字段均已映射为英文 key（如 `max_photo` / `max_video` / `ois` / `raw` / `physical_ids`）。
- **`sensors` 分组结构**：顶层 `count` / `dynamic_count` 加 `items` 数组。每个传感器 item 含 `index`、`type`、`name`、`vendor`，以及数值字段 `version`（整数）、`max_range` / `resolution` / `min_delay_us`（浮点）。

### 变更

- **`ExportUi` 重写**：`cameraJson` / `sensorsJson` 由 `parseCamera` / `parseSensors` 替换，按 `cameraId` 与序号行切分文本块并映射字段。原探测文本始终保留在 `raw` 分组，格式变更不丢数据。
- **已知边界（诚实声明）**：结构化解析依赖 `CapabilityProbe` 的文本格式，`CameraField` 映射表未覆盖的字段不会进入结构化输出（但始终保留于 `raw`）；若将来采集层文案变更，需同步更新 `CameraField`。

## [2.0.0] - 2026-11-03

**主题：JSON 报告改为稳定键 + 数值字段，新增 EDID 面板解码。**
1.x 的 JSON 用中文显示文案直接当字段名，改一次界面措辞就破一次数据格式，重名条目还得靠 `_2` 后缀兜底。本次给每一行加稳定的英文 `key`，`label` 降级为纯展示字段；`schema_version` 升到 `2`。versionCode 27 → 28。

### 破坏性变更（需下游适配）

- **JSON 字段名改为稳定的 snake_case key**：例如 `"总内存"` → `"ram_total"`、`"SoC"` → `"soc_model"`、`"设计容量"` → `"battery_design_capacity"`。字段名不再随界面文案变化。
- **每个事实新增 `label`**：保留原中文名，便于旧解析器映射，也便于阅读原始 JSON。
- **`schema_version` 由 `1` 升为 `2`**。
- **`camera` / `sensors` 两组例外（已在 3.0.0 移除）**：2.0.0 发布时这两组仍由采集层文本块透传、键名保持块内标签（同时新增 `label` 字段），因为当时认为它们的键随枚举内容变化、不属于「界面文案改名」风险面。3.0.0 已将其一并结构化。

### 新增

- **屏幕新增「面板（EDID 解码）」组**：解码 `ProbeNative` 已读取的 `/sys/class/drm/card0-DSI-1/edid`，显示面板标识（PNP ID + 厂商名，如 `SDC（Samsung Display）`）、产品代码、生产周/年、原生分辨率、物理尺寸（cm 与英寸）。
  - 只解析设备自己公开的二进制，不查机型库；EDID 头、6 字节 `FF` 前缀与 128 字节校验和任一不通过即判为「设备未公开」。
  - 节点不可读（多数零售设备的默认状态）时，五个键仍以 `null` + `unknown` 导出，JSON 结构不随设备变化。
- **数值字段 `number` / `unit`**：值本身是单一数量的行，额外导出机器可读数值。目前已覆盖总内存、可用内存、存储总量、可用空间、文件系统块大小、温度、电压、设计容量、满充容量、刷新率、峰值亮度。区间值（如频率 `1000 – 2800 MHz`）与列表值不硬凑数值。

### 变更

- **`InfoModel.Row` 增加 `key` / `number` / `unit`**；`Group` 的 `row` / `prominent` / `meter` / `hiddenRow` 全部要求传 key，新增 `qty` / `qtyProminent` 用于带数值的行。
- **取值改为按 key 查找**：`Section.value(key, fallback)` 与首屏指标卡不再用中文标签匹配。此前同一分区内两个同名条目（如 OpenGL 与 Vulkan 的「扩展数量」）会取到错误的那一行。
- **`ExportUi` 重构**：事实对象由统一的 `fact(row)` 生成，`uniqueKey()` 仅作为将来出现重复 key 的兜底。

## [1.24.0] - 2026-11-03

**主题：条目取舍纠偏——把「构建视角」换回「设备视角」。**
1.23.0 治理了界面排版，但取舍的优先级排反了：保留了 9 行 ABI 构建信息，却缺少 SoC、ROM 版本号、内核版本、电池设计容量、刷新率模式这些用户真正常查的字段。本次不删任何采集代码、不改 JSON 数据格式，只重新分配「哪一行值得显示」。versionCode 26 → 27。

### 新增

- **新分区「安全与运行状态」**：SELinux 模式（此前已由 NDK 采集但从未显示）、引导程序版本、低内存设备标记、系统运行时长、时区、语言、字体缩放。
- **设备与系统**：新增 SoC（`Build.SOC_MODEL`，API 31+；回退 `/proc/cpuinfo` 的 `Hardware` / `model name`）、ROM 版本（`VERSION.INCREMENTAL`）、基础版本（`VERSION.BASE_OS`，API 23+）、板级型号（`BOARD` / `HARDWARE`）、构建 ID、构建指纹。
- **CPU 与架构**：新增内核版本（`uname.release`，此前只显示 `uname.machine`）、核心结构（按 `cluster_id` 统计，如「4 + 4（2 簇，共 8 核）」）、频率分布（按 `cpuinfo_max_freq` 分组，如「4 × 1.80 GHz + 4 × 2.40 GHz」）、最低频率、调频策略（`scaling_governor`）、调频驱动。
- **图形 API**：新增压缩纹理支持（ASTC / ETC2 / S3TC / ATC / ETC1）、主要扩展列表、Vulkan 实例 API 版本、设备类型（集成 / 独立 GPU）、vendorID / deviceID、队列族数量。
- **内存与存储**：新增 Swap / ZRAM（`/proc/meminfo: SwapTotal`）、本应用内存占用（`Debug.MemoryInfo`）、文件系统块大小。
- **电池**：新增设计容量（mAh）、当前满充容量（mAh）、循环次数（API 34+ 或 `cycle_count`）、电池技术、实时电流、充满剩余时间。
- **屏幕**：新增支持的显示模式（`Display.getSupportedModes()` 的分辨率 × 刷新率组合）、物理像素密度（xdpi / ydpi 原值）、屏幕形状（圆 / 方）、系统期望峰值亮度（`getDesiredMaxLuminance()`）、颜色模式列表（`getSupportedColorModes()`）。
- **相机与传感器**：新增最大像素摘要（如「3.1 MP、12.5 MP、50.0 MP」）、闪光灯、自动对焦；「外置」与「方向未知」拆为两行独立计数。
- **硬件能力**：新增红外遥控、FM 收音机、指纹、人脸、虹膜、振动幅度控制、专业音频、低延迟音频。
- **JSON 报告**：新增顶层 `security` 分组；`schema_version` 仍为 `1`（纯增量）。

### 变更

- **首屏 CPU 卡**：副值由「内核架构 `aarch64`」改为「SoC 型号」。`aarch64` 对普通用户没有信息量，SoC 才是第一屏该回答的问题。
- **在线核心**：值由裸 CPU 号列表改为「8 个（CPU 0-7）」，标签与值语义一致。
- **系统位数**：不再截断判定依据，「（按系统文件回退判断）」等回退说明保留在值中。
- **电池健康度**：由「96.4%（4820000 / 5000000）」改为「96.4%（4820 mAh / 5000 mAh）」，单位明确。

### 修复

- **容量换算**：`charge_full` / `charge_full_design` 按 µAh 口径除以 1000 换算为 mAh，不再直接显示七位原始数值。
- **字节格式化**：`bytes()` 此前对 ≥100 的值一律取整，导致 5.55 GiB 显示为「6 GiB」。现在 GiB / TiB 恒定保留两位小数，KiB / MiB 仍取整。
- **文档与实现不一致**：`CHANGELOG` 曾称「电视遥控器规则 / 手表小屏规则已降为脚注」，实际仍是数据行；本次真正移出界面。

### 移除（仅界面，数据与 JSON 键保留）

- **界面不再显示**：ABI 分项 4 行（设备首选 ABI、支持 32 位、支持 64 位、安装推荐 ABI）、构建者、构建主机、内存颗粒厂商、10-bit 显示、电视遥控器规则、手表小屏规则、外置 / 方向未知合并行。
- 上述条目改为 `InfoModel.Group.hiddenRow`：`SectionUi` 不渲染、搜索不命中、TXT 报告不输出，但 **JSON 导出保留原键**。

### 兼容性（本次明确未变）

- **JSON 报告 `schema_version` 仍为 1**：所有既有键均保留，仅新增 `security` 分组与新字段；外部解析脚本无需适配。
- **权限**：仍无任何 `uses-permission`。新增字段全部来自 `Build`、`PackageManager`、`Display`、`BatteryManager`、`Debug`、`Vibrator` 等无权限公开接口。
- **采集层零改动**：`ProbeNative.c`、`NativeBridge`、`VulkanProbe`、`CapabilityProbe`、`DeviceProfile` 未修改；仅 `GpuProbe` 增加一行派生值 `GL 纹理压缩`。
- minSdk 21、targetSdk 35、打包 ABI 不变。
- API 级别守卫：`SOC_MODEL`（31+）、`BASE_OS`（23+）、`EXTRA_CYCLE_COUNT`（34+）、`getSupportedModes` / `isScreenRound`（23+）、`getDesiredMaxLuminance`（24+）、`getSupportedColorModes`（26+）、`FEATURE_FINGERPRINT`（23+）、`FEATURE_FACE` / `FEATURE_IRIS`（29+）、`hasAmplitudeControl`（26+）。

### 未做（留待后续）

- `Row` 增加稳定英文 `key` 与 `numericValue` / `unit`：会改变 JSON 键集合，属破坏性变更，按 SemVer 需并入下一次 Major。
- EDID 与 `fb0/modes` 解析：需先做节点可读性探测，确认收益后再投入。

## [1.23.0] - 2026-10-05

**主题：治理「信息与显示杂乱」——架构重构与界面降噪。**
针对 1.22.3 收到的「信息和显示非常杂乱」反馈做的整体治理。本次未删除任何用户功能，未改变任何采集口径与数据格式（JSON 报告 `schema_version` 仍为 1），对外接口与数据完全向后兼容，按 SemVer 判定为 Minor 升级。versionCode 25 → 26。

### 修复

- **来源徽章噪音**：原先每个数据行都挂一枚彩色「来源徽章」，整屏出现几十枚彩色小标签；现改为行首小色点区分来源类别，轻触数据行即可在弹窗中查看该值的来源、口径与免责说明，默认界面不再出现成片徽章。
- **逐核频率平铺**：「性能与芯片」原先平铺 8 核 × 当前/最高共 16 行碎片化逐核频率；现聚合为一行「806 – 2116 MHz（8 核心范围）」式区间摘要，逐核心原始值仍完整保留在「技术原始数据」分区与导出报告中。
- **重复字段**：CPU ABI、系统 API、应用正在使用 等已在其他分区展示的字段不再重复出现，GPU 信息也不再同时出现在两个分区。
- **值内括号注释**：「（power_supply 原始值）」「（Camera2 JPEG 输出，非厂商宣传值）」等长括号注释从数据值中移除，改放入来源弹窗与分区脚注，数据行只保留值本身。
- **说明行混杂**：教学性长文本（如电视遥控器规则、手表小屏规则）从数据卡片的「一等公民」位置降为分区底部的小字脚注。
- **分区行数**：「性能与芯片」分区由 30+ 行精简至 15 行（基础 4 + 频率 2 + ABI 9），SoC、内存、GPU 等重点信息一眼可见。

### 变更

- **架构重构**：MainActivity 由 1959 行「巨石类」拆分为 15 个职责单一的类——`Ui`（统一色板、间距、圆角与交互背景）、`InfoModel`（分区 → 分组 → 行的结构化模型与来源枚举）、`ProbeSnapshot`（不可变采集快照）、`SectionBuilder`（一次解析、去重、组段）、`DashboardUi`（首页仪表卡）、`SectionUi`（分区卡片渲染）、`DialogsUi`（对话框）、`ExportUi`（复制/分享/TXT/JSON 导出）等，MainActivity 收敛为 280 行纯编排器，界面构建、数据解析、导出彻底解耦。
- **结构化数据流**：新增 `InfoModel` + `ProbeSnapshot` 结构化管线，取代原先「把摘要字符串拼出来再逐行解析回键值对」的脆弱流程；解析只做一次，仪表卡、分区卡与导出共享同一份数据，UI 与数据不再可能不一致。
- **交互统一**：数据行统一为「轻触查看来源与口径」，与搜索、刷新、展开收起等既有手势互不干扰。

### 移除

- 死代码：`DeviceProfile.screenRule` / `detectionDetails`、`CapabilityProbe.hasAutofocus` 等未被界面引用的遗留成员，以及未使用的 import。
- 仓库根目录重复存放的 `gradle-wrapper.jar`（`gradle/wrapper/` 内已有正式副本）。

### 兼容性（本次明确未变）

- 功能全量保留：搜索与高亮清除、刷新、报告菜单、TXT + JSON 导出、Camera2 四层卡片、传感器清单、屏幕测试、设备类型纠正。
- JSON 报告格式 `schema_version 1` 不变，外部解析脚本无需适配。
- 探针层 `DeviceProfile` / `CapabilityProbe` / `GpuProbe` / `VulkanProbe` / `NativeBridge` / `ScreenTestActivity` / `ProbeNative.c` 及其「key: value」数据契约零改动。
- minSdk 21（Android 5.0）、targetSdk 35、打包 ABI（armeabi-v7a、arm64-v8a、x86_64）不变。

## [1.22.3]

记录基线版本（versionCode 25）。应用功能在此版本已完整可用：设备/系统/CPU/GPU/内存/存储/相机/传感器/电池等分区探查、搜索与高亮、TXT + JSON 导出、屏幕测试。本版本存在已知体验问题——界面信息杂乱（来源徽章成片、逐核频率平铺 16 行、字段重复、值内嵌括号注释、说明行与数据行混杂）与 MainActivity 1959 行巨石类带来的可维护性问题，均已在 1.23.0 治理。
