# 网络检索笔记：屏幕与内存

## 官方来源

1. Android DisplayManager API: https://developer.android.com/reference/android/hardware/display/DisplayManager
   - DisplayManager 管理附着显示器；可通过 getDisplay/getDisplays 获取逻辑显示对象。
   - Display 对象可提供当前显示刷新率等公开字段；DisplayManager 也区分内置/投影等显示类别。
   - 现代 HDR 转换、亮度事件等字段有较高 API 级别要求，不能直接作为 Android 5.0 的兼容基础。
   - 这些 API 是框架/逻辑显示视图，不等同于面板供应商或物理硬件 EEPROM 信息。

2. Android ActivityManager.MemoryInfo: https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo
   - availMem：系统可用内存估计，不应视为绝对值；内核回收能力和运行策略会影响它。
   - threshold：系统认为低内存并开始回收/杀后台服务的阈值。
   - lowMemory：系统当前是否认为处于低内存状态。
   - totalMem（API 16）：内核可访问的总内存；基本对应 RAM，但不包括内核以下固定分配，例如 DMA 缓冲区、基带 CPU 使用的 RAM 等。
   - advertisedMem（API 34）：面向零售展示的标称内存，可能与 totalMem 不同，不能用于 Android 5.0 兼容主路径。

## 初步结论

屏幕真实性需分层：框架公开的分辨率/刷新率/色彩/HDR/显示模式是真实的系统逻辑显示状态；面板厂商、面板型号、真实物理尺寸、原生时序和硬件校准值通常必须依赖厂商 display HAL、sysfs 或 root/工程接口，普通应用不能保证可读。内存应并列展示 ActivityManager.MemoryInfo 与 C/NDK 读取的 /proc/meminfo；二者口径不同，不能互相伪造替代。
