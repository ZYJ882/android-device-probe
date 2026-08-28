# v1.16 源码改造审查

## 已确认问题

1. `MainActivity` 顶栏硬编码 `v1.14`，与 Gradle 的 `1.16.0 / versionCode 17` 不一致。
2. `refreshProbe()` 在 UI 线程同步执行 NDK、EGL/OpenGL、Vulkan、Camera2、SensorManager 和多个 Framework 查询，可能造成卡顿。
3. 首页虽然已有概览卡，数值层级不够明显，详细信息全部以单一 `TextView` 键值文本呈现，信息密度过高。
4. `createCategoryNavigation()`、`createStatusDashboard()` 和相关进度条更新逻辑没有加入当前首页，是可清理的旧 UI 代码。
5. `CapabilityProbe` 把 `FEATURE_CAMERA_ANY` 误称为“后置摄像头”；该 feature 只能说明任意摄像头存在，前后置应来自 Camera2 `LENS_FACING` 实际公开元数据。
6. 报告复制已有私有方法但没有显著入口；没有搜索、分享、TXT 导出和结构化 JSON 导出。
7. `ProbeNative.c` 的 Vulkan `VkApplicationInfo` 仍硬编码为 `1.14.0`。

## 改造原则

- 复用 `ProbeNative.c`、`GpuProbe`、`VulkanProbe` 的真实采集逻辑，不用机型库补写硬件参数。
- 采集移至单线程后台执行，UI 线程只构建/更新视图。
- 首页显示设备 Hero、CPU/GPU/RAM/存储/屏幕/电池六张关键概览卡和快速入口；详细模块继续保留。
- 非原始模块采用紧凑两列事实网格，字段显示来源与可信度标签；原始数据仍以等宽文本保留。
- Camera2 只读取公开 metadata，不打开相机、不申请 CAMERA 权限。
- 导出 JSON 使用结构化对象，每个字段包含 `value`、`source`、`confidence`，未知值为 JSON `null`。
- 维持 minSdk 21、电视焦点和手表紧凑规则；API 26+ 的广色域特性继续通过兼容方式调用。
