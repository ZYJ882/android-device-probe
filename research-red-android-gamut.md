# 红色安卓机器人广色域图研究笔记

## 官方 Android Developers Blog

来源：https://android-developers.googleblog.com/2019/05/wide-color-photos-are-coming-to-android.html

官方文章发表于 2019-05-20，明确说明：有一张用于测试应用是否具备广色域显示能力的图片；如果应用和设备正确支持广色域，图中会出现红色 Android logo。文章要求在支持广色域的设备上测试，例如 Pixel 3 或 Samsung Galaxy S10。该图的文件链接标记为 `android_p3.png`，即带 Display P3 色彩空间的图像，而不是普通 sRGB 红色图。

文章同时区分“wide color gamut ready”（正确管理图片颜色空间，避免色彩失真）与“wide color gamut capable”（能够显示超出 sRGB 的颜色）。红色机器人图主要用于后者，并且是应用、图片 ICC/Profile、系统色彩管理和广色域显示链路共同作用的测试，不是单靠 RGB(255,0,0) 绘制即可复现的结果。

## Android 官方广色域文档

来源：https://developer.android.com/training/wide-color-gamut

Android 8.0 / API 26 引入了额外色彩空间的系统色彩管理。应用需要请求 wide color gamut color mode，并加载带有广色域 profile 的图片，或通过 OpenGL/Vulkan 使用相应色彩空间。仅调用普通整数颜色绘制红色，不等价于渲染 Display P3 广色域内容，因为普通颜色会经过系统显示色域映射。

官方文档说明：Display P3 和 scRGB 可用于广色域内容；OpenGL 需要相应 EGL 色彩空间扩展；Vulkan 需要 `VK_EXT_swapchain_colorspace`，并检查 Display P3 色彩空间与对应 surface format 是否可用。

## 独立测试页

来源：https://www.wide-gamut.com/test

该测试页区分 ICC profile、广色域、HDR 与浏览器图片解释。其广色域测试采用带色彩信息的内容，而不是仅靠普通 CSS 或未标记的纯红色元素。页面强调看到不同颜色表现可以作为视觉判断，但不能替代仪器测量。

## 初步结论

用户所说的“红色安卓机器人图”基本就是 Android Developers 官方文章中的 `android_p3.png`。此前应用里用 Canvas 绘制的高饱和红色机器人是视觉参考图案，但严格来说不是官方 Display P3 图片测试，因为 Canvas 普通 int 颜色没有携带 P3 ICC profile，也没有请求 Android wide color mode。若要高真实性复现官方测试，应将官方 `android_p3.png` 作为资源，给屏幕测试 Activity 在 API 26+ 请求 `COLOR_MODE_WIDE_COLOR_GAMUT`，并显示带 Display P3 profile 的 PNG；API 21-25 则如实显示系统不具备公开 wide color managed mode 的限制。
