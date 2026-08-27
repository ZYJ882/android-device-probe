# Android 广色域、HDR 与 10-bit 能力检测

**检索日期：2026-08-28**

Android 8.0 / API 26 引入了广色域（Wide Color Gamut）相关的色彩管理能力。对于普通应用，`Display.isWideColorGamut()` 是系统对当前显示是否支持广色域的公开能力判断；它反映 Android 显示栈所报告的支持状态，不是色度计对面板色域覆盖率的实测。

`Display.getHdrCapabilities()` 和支持 HDR 类型的 API 在 API 24 起公开。HDR 类型可用于显示系统报告的 HDR10、HLG、HDR10+、Dolby Vision 等能力。HDR、广色域和 10-bit 是有关联但不等价的概念：系统报告 HDR 支持或某种 HDR 类型，并不单独证明面板在所有模式下均以原生 10-bit 输出。

## 应用实现

| 检测项 | API 与版本 | 结果用语 |
|---|---|---|
| 广色域 | `Display.isWideColorGamut()`，API 26+；用反射调用以兼容 API 21 | “系统报告支持/不支持广色域”；API 26 前显示“系统未提供公开检测” |
| HDR | `Display.getHdrCapabilities()`，API 24+；现有反射兼容调用 | “系统报告支持的 HDR 类型”；API 24 前显示“系统未提供公开检测” |
| 10-bit | 不以 HDR、广色域或颜色测试图案直接推断 | “没有普通应用通用的面板原生位深公开 API”；如系统公开特定 HDR 类型则并列显示，但不声称已测得面板 10-bit |
| 色域视觉测试 | 当前 ScreenTestActivity 颜色参考/色域图案 | “视觉参考，不替代色度计实测” |

## 参考

1. [Android Developers: Enhance graphics with wide color content](https://developer.android.com/training/wide-color-gamut)：Android 8.0（API 26）引入额外色彩空间的色彩管理；说明 `isWideColorGamut()` 和窗口色彩模式的使用及限制。
2. [Android Developers: Display API reference](https://developer.android.com/reference/android/view/Display)：公开 Display 的 HDR capability、显示状态和广色域相关 API。
3. [AOSP: HDR video playback](https://source.android.com/docs/core/display/hdr)：Android 显示管线中 HDR 格式和显示模式的实现背景。
