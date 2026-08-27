# CPU、GPU 与内存硬件详情的数据源

**检索日期：2026-08-28**

## 真实数据优先顺序

| 硬件项 | 首选来源 | 可展示字段 | 真实性边界 |
|---|---|---|---|
| GPU | Android `GLSurfaceView` 创建实际 EGL/OpenGL ES 上下文后调用 `glGetString()` | GL_VENDOR、GL_RENDERER、GL_VERSION、GLSL 版本、扩展数量/节选 | 这是正在使用的图形驱动所报告的真实渲染器信息；不是 SoC 数据库推断。创建上下文失败时应显示失败原因。 |
| CPU | C/NDK `uname()`、`sysconf()`、`/proc/cpuinfo` 与公开 sysfs CPU 节点 | 架构、可用/在线核心、CPU 列表、调速器、当前/最小/最大频率、相关核心 | CPUFreq、cpuidle 和厂商节点可能不可读；每项需要保留路径、原始字符串或读取错误。 |
| 内存 | `ActivityManager.MemoryInfo` 与 `/proc/meminfo`；memory block `phys_device` 仅为候选 | 总/可用内存、低内存阈值、缓存、可回收缓存、swap 与原始内核字段 | `availMem` 与 `MemFree` 并非同一口径；Android 通常没有公开的通用 DRAM 厂商/频率 API。 |

## 实施决定

1. 新建轻量的 `GLSurfaceView`/Renderer，在 GL 上下文回调内读取实际 GPU 字符串。只显示字符串和扩展计数/节选，避免把渲染器名反推为具体芯片型号。
2. C/NDK 扩展采集 `/sys/devices/system/cpu/cpuN/{online,cpufreq/*,cpuidle/*,topology/*}` 与 `/proc/cpuinfo`，限制逐项输出长度，避免大报告失控。
3. 应用 UI 在“性能硬件详情”中总结，并将 CPU/GPU 原始数据单独默认收起。每个条目标记 Android 图形驱动、NDK/POSIX、`/proc` 或 `sysfs` 来源。

## 参考

1. [Khronos: glGetString](https://registry.khronos.org/OpenGL-Refpages/es3.0/html/glGetString.xhtml)：`GL_VENDOR`、`GL_RENDERER`、`GL_VERSION` 与 `GL_EXTENSIONS` 查询。
2. [Android: OpenGL ES](https://developer.android.com/develop/ui/views/graphics/opengl/about-opengl)：Android 对 OpenGL ES 的支持。
3. [Linux Kernel: CPU Performance Scaling](https://docs.kernel.org/admin-guide/pm/cpufreq.html)：CPUFreq 子系统和 policy/sysfs 信息。
4. [Linux Kernel: CPUFreq Stats](https://docs.kernel.org/cpu-freq/cpufreq-stats.html)：CPU 频率统计 sysfs 接口。
