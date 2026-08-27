# 网络检索笔记：Display 与 Battery

## 官方来源

1. AOSP Display.java: https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/view/Display.java
   - Display 提供逻辑显示的尺寸和密度等信息；当前模式、刷新率、HDR 能力等属于系统显示管线公开视图。
   - 因此 Display/DisplayManager 能真实反映系统当前逻辑显示状态，但不自动等于面板供应商、面板型号或硬件 EEPROM 信息。

2. Android BatteryManager API: https://developer.android.com/reference/android/os/BatteryManager
   - BatteryManager 包含 ACTION_BATTERY_CHANGED 广播中电池属性的键和常量，并提供查询电池/充电属性的方法。
   - 实施时应读取 level/scale、status、health、voltage、temperature、current_now、charge_counter 等存在且有值的字段，并同时显示“来源”和“单位”。
   - charge counter 是电量计/系统估计值，不应被改名为“真实剩余容量”；额定容量、设计容量、循环次数和健康度通常依赖 fuel-gauge/HAL/厂商实现。

## 初步结论

屏幕可采用三层数据源：第一层是 API 17+ 的 Display/DisplayManager，读取逻辑分辨率、densityDpi、刷新率、当前模式、HDR 能力和显示状态；第二层是 Android 5.0 可读的系统资源或只读 sysfs，尝试读取 panel/connector 相关节点但必须按路径记录结果，不能假设所有厂商共用路径；第三层是厂商 display HAL、Dumpsys 或 root/工程接口，仅作为可选特权探查，不应伪装成普通应用可获得的信息。供应商/面板型号能否获得取决于 ROM 和权限，不能保证。

电池可用官方 BatteryManager 粘性广播作为主路径，并用 C/NDK 探查 `/sys/class/power_supply/*` 作为“内核导出原文”交叉核对；如果两者不一致，应并列显示而不是挑选一个覆盖另一个。系统 API 应显示健康状态、充电状态、百分比、温度、电压、电流、充电计数和设计容量（若系统返回）；sysfs 应显示真实路径、原始值、单位换算和读取时间。电池“健康百分比”若不是设备/燃料计直接提供，必须标注为估算，不能用当前电量充当健康度。

## AOSP 显示支持补充

3. AOSP Display support: https://source.android.com/docs/core/display/multi_display/displays
   - AOSP 说明 SurfaceFlinger 可从显示硬件的 EDID 中提取制造商或型号信息并生成稳定显示 ID；示例通过 `dumpsys SurfaceFlinger --display-id` 查看 pnpId/displayName。
   - 该文档同时说明物理显示地址、端口等方法是 framework/system_server 内部使用的隐藏能力，不能视为普通第三方应用稳定可用的公开 API。
   - 因此“屏幕供应商/型号”的最真实路径是：厂商 display HAL/HWC → EDID/驱动 → SurfaceFlinger；普通应用最多可靠取得公开逻辑显示信息，尝试读取厂商节点时必须报告路径与权限结果。

## 内存厂商字段补充

4. Android kernel/common `sysfs-devices-memory`: https://android.googlesource.com/kernel/common/+/android-trusty-3.10/Documentation/ABI/testing/sysfs-devices-memory
   - `/sys/devices/system/memory` 描述内核 memory block 的内部状态；`memoryX/state` 是 online/offline 状态，`memoryX/phys_index` 是 section ID。
   - `memoryX/phys_device` 设计为显示物理内存设备名称，但官方文档明确指出当前实现不完整。因此它只能作为“内存硬件名称/厂商候选”直接读取，不能保证包含 DRAM 颗粒厂商；无法读取时必须显示未公开或不可读。

实施决定：应用将尝试读取前 8 个 memory block 的 `phys_device` 和 `state`，同时在电池 `power_supply` 节点中直接读取实际存在的 `manufacturer`、`model_name`、`model` 字段。上述值均保留路径和原始字符串，绝不根据手机型号数据库推测内存或电池厂商。
