# OpenGL ES 与 Vulkan 检测依据

## 官方结论

Android 官方 Vulkan 概览说明 Vulkan 从 Android 7.0（API 24）开始提供；Android 10（API 29）及以上的 64 位设备支持 Vulkan 1.1，但具体设备仍应通过运行时特性与驱动查询确认。OpenGL ES 仍是 Android 支持的图形 API。

OpenGL ES 方面，应用可创建 EGL/OpenGL ES 上下文并通过 glGetString 获取 GL_VENDOR、GL_RENDERER、GL_VERSION、GLSL 版本和 GL_EXTENSIONS。这些值代表当前实际图形驱动上下文的报告，不等于芯片数据库推断。

Vulkan 方面，优先使用 PackageManager 检测 FEATURE_VULKAN_HARDWARE_LEVEL 与 FEATURE_VULKAN_HARDWARE_VERSION（API 24+），再加载 Vulkan loader 并调用 vkEnumerateInstanceVersion、vkEnumeratePhysicalDevices、vkGetPhysicalDeviceProperties、vkGetPhysicalDeviceFeatures、vkGetPhysicalDeviceQueueFamilyProperties 与 vkEnumerateDeviceExtensionProperties。若 loader 或驱动不可用，显示实际错误或不可用原因。

## 兼容性边界

Android 5.0 应用可以继续保留 OpenGL ES 检测；Vulkan 检测必须 API 24+ 运行时降级，不能直接链接高版本 Java API。Vulkan 版本、GPU vendorID/deviceID、设备名称、驱动版本和扩展由驱动实际报告；“支持 Vulkan”只说明驱动可创建/枚举 Vulkan 设备，不代表每个扩展或每个格式都支持。

## 参考来源

1. https://developer.android.com/games/develop/vulkan/overview
2. https://developer.android.com/reference/android/content/pm/PackageManager
3. https://registry.khronos.org/OpenGL-Refpages/es3.0/html/glGetString.xhtml
4. https://docs.vulkan.org/refpages/latest/refpages/source/VkPhysicalDeviceProperties.html

保存时间：2026-08-28
