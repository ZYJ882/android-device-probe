# 电视与手表适配依据

**检索日期：2026-08-27**

## 自动设备类型识别

Android 的 `PackageManager.hasSystemFeature()` 可用于查询设备公开特性。实现将按以下优先级识别：

| 类型 | 公开特性 | 处理方式 |
|---|---|---|
| 手表 | `PackageManager.FEATURE_WATCH`（`android.hardware.type.watch`） | 使用小屏幕单列布局与大触控目标 |
| 电视 | `PackageManager.FEATURE_LEANBACK` 或 `PackageManager.FEATURE_TELEVISION` | 使用横向大屏布局、可聚焦控件与遥控器导航 |
| 手机/平板 | 未命中上述特性 | 保持现有触摸优先布局；再以最小宽度判断手机或平板 |

屏幕分辨率、密度、宽高方向仅用于布局和测试图案的辅助调整，**不得替代设备特性作为电视/手表身份判断依据**。

## 电视遥控器

Android TV 官方导航文档说明，电视主要使用带上、下、左、右方向键的 D-pad；方向键在可聚焦对象间移动焦点，Select 键激活当前焦点对象，Back 键返回。框架会基于相对位置自动提供方向焦点，但每个可见控件都必须可达到，且要提供清晰的焦点视觉反馈。

实现决定：电视端所有按钮和可展开板块设置 `focusable`/`focusableInTouchMode`，使用焦点状态背景高亮，并在屏幕测试页面拦截 DPAD_LEFT/RIGHT 与 ENTER/DPAD_CENTER 完成上一个、下一个和纯色切换。启动时将焦点放在主要动作上。

## 手表小屏幕

Wear OS 设备特性命中后，应用使用更紧凑的边距和单列信息卡；按钮保持足够高度，屏幕测试页面提供点击左/右半屏切换项目、长按退出和小屏简化绘制，以避免底部三按钮栏占满显示区域。

## 参考

1. [Android TV navigation](https://developer.android.com/training/tv/get-started/navigation)：D-pad 焦点、Select 和 Back 导航规则。
2. [PackageManager API reference](https://developer.android.com/reference/android/content/pm/PackageManager)：`FEATURE_LEANBACK` 与 `FEATURE_WATCH` 特性常量。
3. [Wear adaptive design overview](https://developer.android.com/design/ui/wear/guides/foundations/adaptive-design)：手表应采用响应式布局以适应不同尺寸显示器。

## 非标准 Android 手表的辅助识别（v1.9.0）

除 `PackageManager.FEATURE_WATCH` 外，应用还读取 `Resources.getConfiguration().uiMode` 的 `UI_MODE_TYPE_WATCH`；二者任一命中即为“已确认手表”。Android `Configuration` 还提供 `isScreenRound()`（API 23+），可作为辅助外形线索，不能独立证明设备类型。

对于未按标准特性声明的 AOSP 套壳手表，应用只在多项非身份线索同时成立时显示“疑似 Android 手表”：例如较小的最小宽度、低逻辑分辨率、低像素尺寸和圆形屏幕线索。该结论会列出触发项目并标为启发式判断；应用不根据品牌、产品名或系统方案名称推断“和顺成”。用户可以保存手动设备模式覆盖（手机/平板/电视/手表/自动），以纠正非标准设备的 UI 分类。

4. [Android Configuration](https://developer.android.com/reference/android/content/res/Configuration)：`uiMode`、`UI_MODE_TYPE_WATCH` 与 `isScreenRound()`。
5. [Android UiModeManager](https://developer.android.com/reference/android/app/UiModeManager)：系统 UI 模式服务说明。
