# Android Device Probe — 技术设计

## 目标

应用的最低兼容版本是 Android 5.0（API 21）。在不请求危险权限、不尝试 root、不规避 SELinux 或 Android 沙箱的前提下，优先由 C/NDK 读取内核向普通应用公开的只读数据，再用 Android 公开 API 补充框架侧设备属性。

## 数据源分级

| 等级 | 来源 | 计划采集的内容 | 真实性与限制 |
|---|---|---|---|
| 原生 / 内核公开 | C11 POSIX、`/proc`、`/sys` | `uname`、内核版本、CPU 信息、内存信息、CPU online/present、页大小、进程 UID | 尽可能直接，但文件是否可读、字段是否存在由 ROM、内核配置、SELinux 与应用沙箱决定。读取失败必须如实显示。 |
| Android 框架公开 API | `android.os.Build`、`ActivityManager`、`StatFs`、`DisplayMetrics`、电池粘性广播 | 厂商、机型、硬件、ABI、系统版本、内存/存储、屏幕、电池 | 是应用支持的公开系统视图；这些字段与内核态数据不互相替代。 |

## 实现结构

* `MainActivity.java` 仅负责展示、刷新、复制；保持 Java 代码适配 API 21。
* `ProbeNative.c` 通过 JNI 汇总原生探查结果；为每个 `/proc` 或 `/sys` 路径保留路径标签和不可读状态，避免把缺失/拒绝访问误报为设备不存在。
* 输出使用纯文本键值格式并区分“NDK / 内核公开接口”和“Android Framework API”两个部分。
* 不采集电话号、通讯录、定位、媒体、账户、广告标识符、IMEI/序列号等敏感身份标识；`AndroidManifest.xml` 不声明任何权限。
* 为保证覆盖常见 Android 5.0 设备，生成 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 四种 ABI 的 native library。

## 证据依据

Android 官方文档说明 NDK 可以使用标准 C11 库，并且原生 API 必须与应用的 `minSdkVersion` 相匹配；因此项目将 `minSdkVersion` 固定为 21，native 部分只使用在该级别可用的 C/POSIX 机制。[1]

Android 官方 `Build` API 将其描述为“从系统属性提取的当前构建信息”，因此该应用把 `Build` 字段作为框架侧信息展示，而不会把它宣称为直接硬件寄存器读数。[2]

Android 的 SELinux 用于限制系统服务和应用对资源的访问。因此 native C 代码不会天然获得系统或 root 权限；不可读取的节点将明确报告为拒绝/不可用。[3]

## 参考资料

[1]: https://developer.android.com/ndk/guides/stable_apis "Native APIs | Android NDK"
[2]: https://developer.android.com/reference/android/os/Build "Build | Android API Reference"
[3]: https://source.android.com/docs/security/features/selinux "Security-Enhanced Linux in Android | AOSP"

---

生成日期：2026-08-27
作者：Manus AI

