#include <jni.h>
#include <dirent.h>
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/utsname.h>
#include <sys/types.h>
#include <unistd.h>

#define REPORT_CAPACITY 65536
#define READ_CAPACITY 8192

static void mark_report_truncated(char *report, size_t capacity) {
    static const char marker[] = "\n（报告已截断）\n";
    size_t marker_len = sizeof(marker) - 1;
    if (capacity == 0) return;
    if (marker_len + 1 >= capacity) {
        report[capacity - 1] = '\0';
        return;
    }
    memcpy(report + capacity - marker_len - 1, marker, marker_len);
    report[capacity - 1] = '\0';
}

static void appendf(char *report, size_t capacity, const char *format, ...) {
    size_t used = strlen(report);
    if (capacity == 0 || used >= capacity - 1) {
        mark_report_truncated(report, capacity);
        return;
    }
    va_list args;
    va_start(args, format);
    int written = vsnprintf(report + used, capacity - used, format, args);
    va_end(args);
    if (written < 0 || (size_t) written >= capacity - used) {
        mark_report_truncated(report, capacity);
    }
}

static ssize_t read_full(int fd, void *buffer, size_t capacity) {
    size_t total = 0;
    while (total < capacity) {
        ssize_t count = read(fd, (char *) buffer + total, capacity - total);
        if (count > 0) total += (size_t) count;
        else if (count == 0) break;
        else if (errno == EINTR) continue;
        else return -1;
    }
    return (ssize_t) total;
}

static void append_key_value(char *report, size_t capacity, const char *key, const char *value) {
    appendf(report, capacity, "%s: %s\n", key, value && value[0] ? value : "未知");
}

static void normalize_text(char *text) {
    if (text == NULL) return;
    for (size_t index = 0; text[index] != '\0'; ++index) {
        unsigned char c = (unsigned char) text[index];
        if (c == '\r') text[index] = '\n';
        else if (c != '\n' && c != '\t' && (c < 32 || c == 127)) text[index] = ' ';
    }
    size_t length = strlen(text);
    while (length > 0 && (text[length - 1] == '\n' || text[length - 1] == ' ' || text[length - 1] == '\t')) {
        text[--length] = '\0';
    }
}

static int read_text_file(const char *path, char *buffer, size_t capacity) {
    if (capacity == 0) return -1;
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    ssize_t count = read_full(fd, buffer, capacity - 1);
    int saved_errno = errno;
    close(fd);
    if (count < 0) {
        errno = saved_errno;
        return -1;
    }
    buffer[count] = '\0';
    normalize_text(buffer);
    return 0;
}

static void append_text_source(char *report, size_t capacity, const char *path, size_t read_limit) {
    char data[READ_CAPACITY];
    size_t capacity_to_read = read_limit + 1;
    if (capacity_to_read > sizeof(data)) capacity_to_read = sizeof(data);
    if (read_text_file(path, data, capacity_to_read) == 0) {
        appendf(report, capacity, "\n-- %s%s --\n%s\n", path,
                strlen(data) >= read_limit ? "（内容已截断）" : "", data);
    } else {
        appendf(report, capacity, "\n-- %s --\n不可读: %s\n", path, strerror(errno));
    }
}

static void append_binary_hex_source(char *report, size_t capacity, const char *path, size_t read_limit) {
    unsigned char data[256];
    if (read_limit > sizeof(data)) read_limit = sizeof(data);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        appendf(report, capacity, "\n-- %s --\n不可读: %s\n", path, strerror(errno));
        return;
    }
    ssize_t count = read_full(fd, data, read_limit);
    int saved_errno = errno;
    close(fd);
    if (count < 0) {
        appendf(report, capacity, "\n-- %s --\n不可读: %s\n", path, strerror(saved_errno));
        return;
    }
    appendf(report, capacity, "\n-- %s（二进制前 %zd 字节，十六进制） --\n", path, count);
    for (ssize_t index = 0; index < count; ++index) {
        appendf(report, capacity, "%02X%s", data[index], (index + 1) % 16 == 0 ? "\n" : " ");
    }
    if (count > 0 && count % 16 != 0) appendf(report, capacity, "\n");
}

static void append_power_supply_entry(char *report, size_t capacity, const char *entry_name) {
    static const char *fields[] = {
            "type", "status", "health", "present", "online", "capacity", "capacity_level",
            "technology", "manufacturer", "model_name", "model", "voltage_now", "voltage_max", "current_now", "current_avg",
            "current_max", "charge_counter", "charge_full", "charge_full_design", "cycle_count",
            "temp", "time_to_full_now"
    };
    char type_path[512];
    char value[READ_CAPACITY];
    snprintf(type_path, sizeof(type_path), "/sys/class/power_supply/%s/type", entry_name);
    if (read_text_file(type_path, value, sizeof(value)) != 0 || strcmp(value, "Battery") != 0) {
        return;
    }

    appendf(report, capacity, "\n-- /sys/class/power_supply/%s（type=Battery） --\n", entry_name);
    for (size_t index = 0; index < sizeof(fields) / sizeof(fields[0]); ++index) {
        char path[512];
        snprintf(path, sizeof(path), "/sys/class/power_supply/%s/%s", entry_name, fields[index]);
        if (read_text_file(path, value, sizeof(value)) == 0) {
            appendf(report, capacity, "%s: %s\n", fields[index], value);
        } else {
            appendf(report, capacity, "%s: 不可读: %s\n", fields[index], strerror(errno));
        }
    }
}

static void append_memory_vendor_report(char *report, size_t capacity) {
    appendf(report, capacity, "\n[内存硬件厂商候选]\n");
    appendf(report, capacity, "标准 sysfs 主要描述内存块状态；phys_device 是否包含颗粒厂商由内核/厂商实现决定。\n");
    bool found = false;
    for (int index = 0; index < 8; ++index) {
        char path[512];
        char value[READ_CAPACITY];
        snprintf(path, sizeof(path), "/sys/devices/system/memory/memory%d/phys_device", index);
        if (read_text_file(path, value, sizeof(value)) == 0) {
            found = true;
            appendf(report, capacity, "memory%d/phys_device: %s\n", index, value);
        }
        snprintf(path, sizeof(path), "/sys/devices/system/memory/memory%d/state", index);
        if (read_text_file(path, value, sizeof(value)) == 0) {
            appendf(report, capacity, "memory%d/state: %s\n", index, value);
        }
    }
    if (!found) appendf(report, capacity, "未发现可读的 memory0..memory7/phys_device 节点。\n");
}

static int read_printable_file(const char *path, char *buffer, size_t capacity) {
    if (capacity == 0) return -1;
    unsigned char bytes[512];
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    ssize_t count = read(fd, bytes, sizeof(bytes));
    int saved_errno = errno;
    close(fd);
    if (count < 0) {
        errno = saved_errno;
        return -1;
    }
    size_t used = 0;
    bool previous_space = true;
    for (ssize_t index = 0; index < count && used + 1 < capacity; ++index) {
        unsigned char c = bytes[index];
        bool printable = c >= 32 && c <= 126;
        if (printable) {
            buffer[used++] = (char) c;
            previous_space = false;
        } else if (!previous_space) {
            buffer[used++] = ' ';
            previous_space = true;
        }
    }
    buffer[used] = '\0';
    normalize_text(buffer);
    return 0;
}

static bool contains_memory_type_token(const char *value) {
    if (value == NULL) return false;
    size_t length = strlen(value);
    for (size_t index = 0; index < length; ++index) {
        char c0 = value[index];
        char c1 = index + 1 < length ? value[index + 1] : '\0';
        char c2 = index + 2 < length ? value[index + 2] : '\0';
        char c3 = index + 3 < length ? value[index + 3] : '\0';
        if ((c0 == 'D' || c0 == 'd') && (c1 == 'D' || c1 == 'd') && (c2 == 'R' || c2 == 'r')) return true;
        if ((c0 == 'H' || c0 == 'h') && (c1 == 'B' || c1 == 'b') && (c2 == 'M' || c2 == 'm')) return true;
        if ((c0 == 'L' || c0 == 'l') && (c1 == 'P' || c1 == 'p') && (c2 == 'D' || c2 == 'd') && (c3 == 'D' || c3 == 'd')) return true;
    }
    return false;
}

static void append_devfreq_memory_candidates(char *report, size_t capacity) {
    DIR *directory = opendir("/sys/class/devfreq");
    if (directory == NULL) {
        appendf(report, capacity, "devfreq 目录不可读: %s\n", strerror(errno));
        return;
    }
    bool found = false;
    int checked = 0;
    struct dirent *entry;
    while ((entry = readdir(directory)) != NULL && checked < 16) {
        if (entry->d_name[0] == '.') continue;
        const char *name = entry->d_name;
        bool candidate = strstr(name, "ddr") || strstr(name, "DDR") || strstr(name, "dram") || strstr(name, "DRAM")
                || strstr(name, "mem") || strstr(name, "MEM") || strstr(name, "bw") || strstr(name, "BW");
        if (!candidate) continue;
        ++checked;
        char path[512];
        char value[READ_CAPACITY];
        snprintf(path, sizeof(path), "/sys/class/devfreq/%s/name", name);
        if (read_text_file(path, value, sizeof(value)) == 0) {
            found = true;
            appendf(report, capacity, "devfreq %s/name: %s\n", name, value);
        } else {
            appendf(report, capacity, "devfreq 候选: %s\n", name);
        }
        snprintf(path, sizeof(path), "/sys/class/devfreq/%s/cur_freq", name);
        if (read_text_file(path, value, sizeof(value)) == 0) appendf(report, capacity, "devfreq %s/cur_freq: %s\n", name, value);
        snprintf(path, sizeof(path), "/sys/class/devfreq/%s/available_frequencies", name);
        if (read_text_file(path, value, sizeof(value)) == 0) appendf(report, capacity, "devfreq %s/available_frequencies: %s\n", name, value);
    }
    closedir(directory);
    if (!found) appendf(report, capacity, "未发现当前应用可读的 DDR/DRAM/memory devfreq 候选节点。\n");
}

static void append_ddr_type_report(char *report, size_t capacity) {
    static const char *paths[] = {
            "/sys/firmware/devicetree/base/memory/ddr_type",
            "/sys/firmware/devicetree/base/memory/ddr-type",
            "/sys/firmware/devicetree/base/memory/dram_type",
            "/sys/firmware/devicetree/base/memory/memory_type",
            "/sys/firmware/devicetree/base/chosen/ddr_type",
            "/sys/firmware/devicetree/base/chosen/memory_type",
            "/sys/kernel/debug/ddr/ddr_type",
            "/sys/kernel/debug/dram/dram_type",
            "/sys/class/devfreq/ddr/type",
            "/sys/class/devfreq/dram/type"
    };
    appendf(report, capacity, "\n[DDR 内存类型候选]\n");
    appendf(report, capacity, "只在原始字段明确含 DDR、LPDDR 或 HBM 时确认类型；动态频率不等于 DDR 代际。\n");
    bool readable = false;
    bool confirmed = false;
    for (size_t index = 0; index < sizeof(paths) / sizeof(paths[0]); ++index) {
        char value[READ_CAPACITY];
        if (read_printable_file(paths[index], value, sizeof(value)) == 0) {
            readable = true;
            appendf(report, capacity, "%s: %s\n", paths[index], value[0] ? value : "（空值）");
            if (!confirmed && contains_memory_type_token(value)) {
                appendf(report, capacity, "DDR 结论: %s（设备公开字段：%s）\n", value, paths[index]);
                confirmed = true;
            }
        }
    }
    if (!confirmed) {
        appendf(report, capacity, "DDR 结论: 无法从普通应用可靠确认（未发现明确的 DDR/LPDDR/HBM 原始类型字段）。\n");
    }
    if (!readable) appendf(report, capacity, "DDR 类型候选路径均未公开或当前应用不可读。\n");
    appendf(report, capacity, "内存控制器/devfreq 候选（频率仅作性能信息，不用于推断 DDR 类型）：\n");
    append_devfreq_memory_candidates(report, capacity);
}

static void append_power_supply_report(char *report, size_t capacity) {
    DIR *directory = opendir("/sys/class/power_supply");
    if (directory == NULL) {
        appendf(report, capacity, "目录不可读: /sys/class/power_supply: %s\n", strerror(errno));
        return;
    }

    bool found_battery = false;
    struct dirent *entry;
    while ((entry = readdir(directory)) != NULL) {
        if (entry->d_name[0] == '.') continue;
        char type_path[512];
        char type_value[READ_CAPACITY];
        snprintf(type_path, sizeof(type_path), "/sys/class/power_supply/%s/type", entry->d_name);
        if (read_text_file(type_path, type_value, sizeof(type_value)) == 0 && strcmp(type_value, "Battery") == 0) {
            found_battery = true;
            append_power_supply_entry(report, capacity, entry->d_name);
        }
    }
    closedir(directory);
    if (!found_battery) {
        appendf(report, capacity, "未发现可由当前应用读取的 type=Battery 节点。\n");
    }
}

static bool append_cpu_node(char *report, size_t capacity, int cpu, const char *relative, const char *label) {
    char path[512];
    char value[READ_CAPACITY];
    snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/%s", cpu, relative);
    if (read_text_file(path, value, sizeof(value)) == 0) {
        appendf(report, capacity, "cpu%d/%s: %s\n", cpu, label, value);
        return true;
    }
    return false;
}

static int cpu_limit_from_present(void) {
    char present[256];
    if (read_text_file("/sys/devices/system/cpu/present", present, sizeof(present)) != 0) {
        long configured = sysconf(_SC_NPROCESSORS_CONF);
        return configured > 0 && configured < 1024 ? (int) configured : 16;
    }
    int max_cpu = -1;
    char *save = NULL;
    for (char *token = strtok_r(present, ",", &save); token != NULL; token = strtok_r(NULL, ",", &save)) {
        int start = -1;
        int end = -1;
        if (sscanf(token, "%d-%d", &start, &end) == 2) {
            if (end > max_cpu) max_cpu = end;
        } else if (sscanf(token, "%d", &start) == 1 && start > max_cpu) {
            max_cpu = start;
        }
    }
    return max_cpu >= 0 && max_cpu < 1023 ? max_cpu + 1 : 16;
}

static void append_cpu_detail_report(char *report, size_t capacity) {
    static const char *relatives[] = {
            "online", "topology/physical_package_id", "topology/core_id", "topology/cluster_id",
            "topology/core_siblings_list", "cpufreq/scaling_driver", "cpufreq/scaling_governor",
            "cpufreq/scaling_cur_freq", "cpufreq/scaling_min_freq", "cpufreq/scaling_max_freq",
            "cpufreq/cpuinfo_min_freq", "cpufreq/cpuinfo_max_freq", "cpufreq/related_cpus"
    };
    static const char *labels[] = {
            "online", "physical_package_id", "core_id", "cluster_id", "core_siblings_list",
            "scaling_driver", "scaling_governor", "scaling_cur_freq_khz", "scaling_min_freq_khz",
            "scaling_max_freq_khz", "cpuinfo_min_freq_khz", "cpuinfo_max_freq_khz", "related_cpus"
    };
    appendf(report, capacity, "\n[CPU 深度原始节点]\n");
    appendf(report, capacity, "候选 sysfs 节点由内核/厂商决定；只打印当前应用成功读取的值。频率单位通常为 kHz。\n");
    bool found = false;
    int cpu_limit = cpu_limit_from_present();
    appendf(report, capacity, "CPU present 动态遍历上限: %d\n", cpu_limit);
    for (int cpu = 0; cpu < cpu_limit; ++cpu) {
        char path[256];
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d", cpu);
        if (access(path, F_OK) != 0) continue;
        for (size_t index = 0; index < sizeof(relatives) / sizeof(relatives[0]); ++index) {
            if (append_cpu_node(report, capacity, cpu, relatives[index], labels[index])) found = true;
        }
    }
    if (!found) appendf(report, capacity, "未发现当前应用可读的 CPU 拓扑或 CPUFreq 节点。\n");
}

static const char *vulkan_device_type_name(VkPhysicalDeviceType type) {
    switch (type) {
        case VK_PHYSICAL_DEVICE_TYPE_OTHER: return "其他";
        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: return "集成 GPU";
        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU: return "独立 GPU";
        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU: return "虚拟 GPU";
        case VK_PHYSICAL_DEVICE_TYPE_CPU: return "CPU 软件实现";
        default: return "未知";
    }
}

static void append_vulkan_report(char *report, size_t capacity) {
    appendf(report, capacity, "\n[Vulkan 原生驱动查询]\n");
    /* Load Vulkan dynamically: API 21 devices simply report no loader. */
    void *library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (library == NULL) {
        const char *loader_error = dlerror();
        appendf(report, capacity, "Vulkan loader: 不可用或未提供（%s）\n", loader_error ? loader_error : "未知错误");
        return;
    }
    PFN_vkGetInstanceProcAddr get_instance_proc_addr =
            (PFN_vkGetInstanceProcAddr) dlsym(library, "vkGetInstanceProcAddr");
    if (get_instance_proc_addr == NULL) {
        appendf(report, capacity, "Vulkan loader: 找到，但缺少 vkGetInstanceProcAddr\n");
        dlclose(library);
        return;
    }
    PFN_vkEnumerateInstanceVersion enumerate_version =
            (PFN_vkEnumerateInstanceVersion) get_instance_proc_addr(NULL, "vkEnumerateInstanceVersion");
    uint32_t api_version = VK_API_VERSION_1_0;
    if (enumerate_version != NULL) {
        VkResult version_result = enumerate_version(&api_version);
        appendf(report, capacity, "Vulkan instance API: %s\n", version_result == VK_SUCCESS ? "可枚举" : "枚举失败");
    } else {
        appendf(report, capacity, "Vulkan instance API: 1.0（驱动未提供版本枚举入口）\n");
    }
    appendf(report, capacity, "Vulkan API 版本: %u.%u.%u\n",
            VK_VERSION_MAJOR(api_version), VK_VERSION_MINOR(api_version), VK_VERSION_PATCH(api_version));

    PFN_vkCreateInstance create_instance =
            (PFN_vkCreateInstance) get_instance_proc_addr(NULL, "vkCreateInstance");
    if (create_instance == NULL) {
        appendf(report, capacity, "Vulkan 实例: 无法创建（缺少 vkCreateInstance）\n");
        dlclose(library);
        return;
    }
    VkApplicationInfo app_info = { VK_STRUCTURE_TYPE_APPLICATION_INFO, NULL,
            "DeviceProbe", VK_MAKE_VERSION(1, 16, 0), "DeviceProbe", VK_MAKE_VERSION(1, 0, 0), api_version };
    VkInstanceCreateInfo create_info = { VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, NULL, 0,
            &app_info, 0, NULL, 0, NULL };
    VkInstance instance = VK_NULL_HANDLE;
    VkResult create_result = create_instance(&create_info, NULL, &instance);
    if (create_result != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        appendf(report, capacity, "Vulkan 实例: 创建失败（VkResult=%d）\n", create_result);
        dlclose(library);
        return;
    }
    PFN_vkEnumeratePhysicalDevices enumerate_devices =
            (PFN_vkEnumeratePhysicalDevices) get_instance_proc_addr(instance, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties get_properties =
            (PFN_vkGetPhysicalDeviceProperties) get_instance_proc_addr(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkGetPhysicalDeviceQueueFamilyProperties get_queue_properties =
            (PFN_vkGetPhysicalDeviceQueueFamilyProperties) get_instance_proc_addr(instance, "vkGetPhysicalDeviceQueueFamilyProperties");
    PFN_vkEnumerateDeviceExtensionProperties enumerate_extensions =
            (PFN_vkEnumerateDeviceExtensionProperties) get_instance_proc_addr(instance, "vkEnumerateDeviceExtensionProperties");
    if (enumerate_devices == NULL || get_properties == NULL) {
        appendf(report, capacity, "Vulkan 物理设备: 缺少枚举函数\n");
    } else {
        uint32_t device_count = 0;
        VkResult count_result = enumerate_devices(instance, &device_count, NULL);
        appendf(report, capacity, "Vulkan 物理设备数量: %u（VkResult=%d）\n", device_count, count_result);
        if (count_result == VK_SUCCESS && device_count > 0 && device_count < 16) {
            VkPhysicalDevice devices[16];
            if (enumerate_devices(instance, &device_count, devices) == VK_SUCCESS) {
                for (uint32_t index = 0; index < device_count; ++index) {
                    VkPhysicalDeviceProperties properties;
                    get_properties(devices[index], &properties);
                    appendf(report, capacity, "设备%d 名称: %s\n", index, properties.deviceName);
                    appendf(report, capacity, "设备%d 类型: %s\n", index, vulkan_device_type_name(properties.deviceType));
                    appendf(report, capacity, "设备%d vendorID/deviceID: 0x%04X / 0x%04X\n", index, properties.vendorID, properties.deviceID);
                    appendf(report, capacity, "设备%d API/驱动版本: %u.%u.%u / %u.%u.%u\n", index,
                            VK_VERSION_MAJOR(properties.apiVersion), VK_VERSION_MINOR(properties.apiVersion), VK_VERSION_PATCH(properties.apiVersion),
                            VK_VERSION_MAJOR(properties.driverVersion), VK_VERSION_MINOR(properties.driverVersion), VK_VERSION_PATCH(properties.driverVersion));
                    if (get_queue_properties != NULL) {
                        uint32_t queue_count = 0;
                        get_queue_properties(devices[index], &queue_count, NULL);
                        appendf(report, capacity, "设备%d 队列族数量: %u\n", index, queue_count);
                    }
                    if (enumerate_extensions != NULL) {
                        uint32_t extension_count = 0;
                        VkResult extension_result = enumerate_extensions(devices[index], NULL, &extension_count, NULL);
                        if (extension_result == VK_SUCCESS || extension_result == VK_INCOMPLETE) {
                            appendf(report, capacity, "设备%d 扩展数量: %u（VkResult=%d）\n", index, extension_count, extension_result);
                        } else {
                            appendf(report, capacity, "设备%d 扩展枚举失败（VkResult=%d）\n", index, extension_result);
                        }
                    }
                }
            }
        }
    }
    PFN_vkDestroyInstance destroy_instance =
            (PFN_vkDestroyInstance) get_instance_proc_addr(instance, "vkDestroyInstance");
    if (destroy_instance != NULL) destroy_instance(instance, NULL);
    dlclose(library);
}

static const char *compiled_abi(void) {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#elif defined(__i386__)
    return "x86";
#else
    return "unknown";
#endif
}

JNIEXPORT jstring JNICALL
Java_com_deviceprobe_inspector_NativeBridge_collectVulkanData(JNIEnv *env, jclass clazz) {
    (void) clazz;
    char *report = (char *) calloc(1, REPORT_CAPACITY);
    if (report == NULL) return (*env)->NewStringUTF(env, "Vulkan 原生报告：内存分配失败");
    append_vulkan_report(report, REPORT_CAPACITY);
    jstring result = (*env)->NewStringUTF(env, report);
    free(report);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_deviceprobe_inspector_NativeBridge_collectNativeData(JNIEnv *env, jclass clazz) {
    (void) clazz;
    char *report = (char *) calloc(1, REPORT_CAPACITY);
    if (report == NULL) return (*env)->NewStringUTF(env, "NDK 原生报告：内存分配失败");
    char number[128];
    char value[READ_CAPACITY];
    appendf(report, REPORT_CAPACITY, "[NDK / 内核公开接口]\n");
    append_key_value(report, REPORT_CAPACITY, "native ABI", compiled_abi());

    struct utsname kernel;
    if (uname(&kernel) == 0) {
        append_key_value(report, REPORT_CAPACITY, "uname.sysname", kernel.sysname);
        append_key_value(report, REPORT_CAPACITY, "uname.release", kernel.release);
        append_key_value(report, REPORT_CAPACITY, "uname.version", kernel.version);
        append_key_value(report, REPORT_CAPACITY, "uname.machine", kernel.machine);
    } else {
        appendf(report, REPORT_CAPACITY, "uname: 失败: %s\n", strerror(errno));
    }

    long page_size = sysconf(_SC_PAGESIZE);
    long online_cpus = sysconf(_SC_NPROCESSORS_ONLN);
    long configured_cpus = sysconf(_SC_NPROCESSORS_CONF);
    snprintf(number, sizeof(number), "%ld bytes", page_size);
    append_key_value(report, REPORT_CAPACITY, "POSIX 页大小", number);
    snprintf(number, sizeof(number), "%ld", online_cpus);
    append_key_value(report, REPORT_CAPACITY, "POSIX 在线逻辑 CPU", number);
    snprintf(number, sizeof(number), "%ld", configured_cpus);
    append_key_value(report, REPORT_CAPACITY, "POSIX 配置逻辑 CPU", number);
    snprintf(number, sizeof(number), "%lu", (unsigned long) getuid());
    append_key_value(report, REPORT_CAPACITY, "应用 Linux UID", number);

    if (read_text_file("/sys/devices/system/cpu/present", value, sizeof(value)) == 0) {
        append_key_value(report, REPORT_CAPACITY, "/sys CPU present", value);
    } else {
        appendf(report, REPORT_CAPACITY, "/sys CPU present: 不可读: %s\n", strerror(errno));
    }
    if (read_text_file("/sys/devices/system/cpu/online", value, sizeof(value)) == 0) {
        append_key_value(report, REPORT_CAPACITY, "/sys CPU online", value);
    } else {
        appendf(report, REPORT_CAPACITY, "/sys CPU online: 不可读: %s\n", strerror(errno));
    }
    append_cpu_detail_report(report, REPORT_CAPACITY);

    if (read_text_file("/sys/fs/selinux/enforce", value, sizeof(value)) == 0) {
        append_key_value(report, REPORT_CAPACITY, "SELinux enforce", strcmp(value, "1") == 0 ? "enforcing (1)" : "permissive (0)");
    } else {
        appendf(report, REPORT_CAPACITY, "SELinux enforce: 不可读: %s\n", strerror(errno));
    }

    appendf(report, REPORT_CAPACITY, "\n[内核导出原文；仅展示公开可读内容]\n");
    append_text_source(report, REPORT_CAPACITY, "/proc/version", 2048);
    append_text_source(report, REPORT_CAPACITY, "/proc/cpuinfo", 6144);
    append_text_source(report, REPORT_CAPACITY, "/proc/meminfo", 6144);

    appendf(report, REPORT_CAPACITY, "\n[显示内核候选节点]\n");
    appendf(report, REPORT_CAPACITY, "以下只是常见只读候选路径；面板供应商/型号没有 Android 通用公开路径。\n");
    append_text_source(report, REPORT_CAPACITY, "/sys/class/graphics/fb0/name", 512);
    append_text_source(report, REPORT_CAPACITY, "/sys/class/graphics/fb0/virtual_size", 512);
    append_text_source(report, REPORT_CAPACITY, "/sys/class/graphics/fb0/modes", 1024);
    append_text_source(report, REPORT_CAPACITY, "/sys/class/drm/card0-DSI-1/modes", 1024);
    append_binary_hex_source(report, REPORT_CAPACITY, "/sys/class/drm/card0-DSI-1/edid", 256);

    append_memory_vendor_report(report, REPORT_CAPACITY);
    append_ddr_type_report(report, REPORT_CAPACITY);

    appendf(report, REPORT_CAPACITY, "\n[电池 power_supply 原始节点]\n");
    appendf(report, REPORT_CAPACITY, "字段由电池驱动/fuel-gauge 实际暴露；单位与可读性按原始节点保留。\n");
    append_power_supply_report(report, REPORT_CAPACITY);

    appendf(report, REPORT_CAPACITY, "\n[数据边界]\n"
            "本节由 C/NDK 使用 POSIX 与只读 /proc、/sys 接口获取。"
            "无法读取的节点按实际错误显示；应用没有 root 权限，未执行提权、绕过 SELinux 或访问受限系统服务。"
            "屏幕面板供应商、内存颗粒信息和电池健康百分比若未由设备公开，均不作猜测。\n");
    jstring result = (*env)->NewStringUTF(env, report);
    free(report);
    return result;
}
