# DDR 内存类型/代际探查边界

**检索日期：2026-08-28**

Android 和通用 Linux 没有面向普通应用的统一 DDR/LPDDR 类型 API。`/proc/meminfo` 只描述内存统计；Linux Devfreq 是设备动态调频框架，可能由厂商用于 DDR/内存控制器，但频率节点本身不能证明 DDR 类型。

## 真实性优先策略

| 优先级 | 读取对象 | 结果处理 |
|---|---|---|
| 1 | 设备实际公开的内存控制器/DDR sysfs 属性，如 `dram_type`、`ddr_type`、`mem_type`、`memory_type` | 仅当原始值含明确 `DDR`、`LPDDR`、`HBM` 等内存类型字符串时，显示为“设备公开的内存类型”。 |
| 2 | 设备树运行时导出候选属性、内存控制器 compatible/name、devfreq 节点 | 保留为“内存控制器/调频候选信息”；不可将频率或 compatible 名直接翻译为 DDR 代际。 |
| 3 | 不存在明确类型字段 | 显示“无法从普通应用可靠确认”；不得根据 SoC、机型、内存容量、频率或品牌数据库推断。 |

实施中会递归限制性扫描 `/sys/class/devfreq` 与 `/sys/devices` 中名称含 `dram`、`ddr`、`memory`、`memlat` 的候选目录，只读取小文本属性，限制节点数量和输出长度；`debugfs`/设备树路径若受 SELinux 限制则如实显示不可读。与此同时保留 DDR 频率候选字段，但标签为“动态频率候选”，不作为内存类型。

## 参考

1. [Linux Kernel: Device Frequency Scaling](https://docs.kernel.org/driver-api/devfreq.html)：Devfreq 是任意设备的动态电压/频率调节框架，不能作为 DDR 类型的统一识别 API。
2. [AOSP kernel MSM Devfreq Kconfig](https://android.googlesource.com/kernel/msm/+/android-7.1.0_r0.2/drivers/devfreq/Kconfig)：厂商内核可将 devfreq 用于 DDR、cache、CCI 等，因此实现和公开字段具有设备差异。
3. [Linux memory block ABI](https://android.googlesource.com/kernel/common/+/android-trusty-3.10/Documentation/ABI/testing/sysfs-devices-memory)：标准 memory sysfs 描述内存块状态，不定义 DRAM 颗粒厂商或 DDR 类型。
