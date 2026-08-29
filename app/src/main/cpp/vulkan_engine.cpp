#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <vector>
#include <cstring>
#include <cmath>

#define LOG_TAG "VulkanEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Vulkan headers
#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

// For Android hardware buffer support
#include <android/hardware_buffer.h>

// Structure to hold Vulkan resources
typedef struct {
    VkInstance instance;
    VkPhysicalDevice physicalDevice;
    VkDevice device;
    VkQueue queue;
    uint32_t queueFamilyIndex;
    VkCommandPool commandPool;
    VkCommandBuffer commandBuffer;
    VkPipelineCache pipelineCache;
    VkDescriptorPool descriptorPool;
    VkPipelineLayout pipelineLayout;
    VkPipeline computePipeline;
    VkDescriptorSetLayout descriptorSetLayout;
    VkDescriptorSet descriptorSet;
    VkShaderModule shaderModule;
    VkBuffer storageBuffer;
    VkDeviceMemory storageBufferMemory;
    VkBuffer uniformBuffer;
    VkDeviceMemory uniformBufferMemory;
    VkFence fence;
    VkSemaphore semaphore;
    
    // For texture operations
    VkImage textureImage;
    VkDeviceMemory textureMemory;
    VkImageView textureView;
    VkSampler textureSampler;
    
    // For Android surface
    ANativeWindow* window;
    VkSurfaceKHR surface;
    VkSwapchainKHR swapchain;
    std::vector<VkImage> swapchainImages;
    std::vector<VkImageView> swapchainImageViews;
    
    bool initialized;
    bool useCompute;
} VulkanContext;

// Global context
static VulkanContext g_vulkanContext = {0};

// Forward declarations for helper functions
static VkShaderModule createShaderModuleInternal(VkDevice device, const uint32_t* code, size_t size);
static VkResult createPaperComputePipelineInternal(VkDevice device, VkPhysicalDevice physicalDevice);
static VkResult createStorageBufferInternal(VkDevice device, VkPhysicalDevice physicalDevice, VkDeviceSize size, void* initialData);

// Helper function to check Vulkan support on Android
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_homecil_native_PaperEngineNative_hasVulkanSupport(JNIEnv* env, jobject thiz) {
    // Try to create a Vulkan instance to check support
    VkInstance instance;
    VkApplicationInfo appInfo = {};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "HomeCil";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "VulkanEngine";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_0;
    
    const char* extensions[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
        VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
    };
    
    VkInstanceCreateInfo createInfo = {};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = 3;
    createInfo.ppEnabledExtensionNames = extensions;
    
    VkResult result = vkCreateInstance(&createInfo, nullptr, &instance);
    if (result == VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

// Initialize Vulkan context
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_homecil_native_PaperEngineNative_initVulkan(JNIEnv* env, jobject thiz, jboolean useComputeOnly) {
    if (g_vulkanContext.initialized) {
        LOGD("Vulkan already initialized");
        return JNI_TRUE;
    }
    
    VkResult result;
    
    // Create Vulkan instance
    VkApplicationInfo appInfo = {};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "HomeCil";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "VulkanEngine";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_0;
    
    const char* extensions[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
        VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
        VK_EXT_DEBUG_UTILS_EXTENSION_NAME
    };
    
    VkInstanceCreateInfo instanceCreateInfo = {};
    instanceCreateInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceCreateInfo.pApplicationInfo = &appInfo;
    instanceCreateInfo.enabledExtensionCount = 4;
    instanceCreateInfo.ppEnabledExtensionNames = extensions;
    
    result = vkCreateInstance(&instanceCreateInfo, nullptr, &g_vulkanContext.instance);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create Vulkan instance: %d", result);
        return JNI_FALSE;
    }
    LOGD("Vulkan instance created");
    
    // Enumerate physical devices
    uint32_t deviceCount = 0;
    result = vkEnumeratePhysicalDevices(g_vulkanContext.instance, &deviceCount, nullptr);
    if (result != VK_SUCCESS || deviceCount == 0) {
        LOGE("No Vulkan physical devices found");
        vkDestroyInstance(g_vulkanContext.instance, nullptr);
        return JNI_FALSE;
    }
    
    std::vector<VkPhysicalDevice> devices(deviceCount);
    result = vkEnumeratePhysicalDevices(g_vulkanContext.instance, &deviceCount, devices.data());
    if (result != VK_SUCCESS) {
        LOGE("Failed to enumerate physical devices");
        vkDestroyInstance(g_vulkanContext.instance, nullptr);
        return JNI_FALSE;
    }
    
    // Select the first suitable device
    g_vulkanContext.physicalDevice = devices[0];
    LOGD("Selected physical device");
    
    // Find compute queue family
    uint32_t queueFamilyCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(g_vulkanContext.physicalDevice, &queueFamilyCount, nullptr);
    
    bool foundComputeQueue = false;
    
    if (queueFamilyCount > 0) {
        std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(
            g_vulkanContext.physicalDevice, 
            &queueFamilyCount, 
            queueFamilies.data()
        );
        
        for (uint32_t i = 0; i < queueFamilyCount; i++) {
            if (queueFamilies[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
                g_vulkanContext.queueFamilyIndex = i;
                foundComputeQueue = true;
                break;
            }
        }
    }
    
    if (!foundComputeQueue) {
        LOGE("No compute queue family found");
        vkDestroyInstance(g_vulkanContext.instance, nullptr);
        return JNI_FALSE;
    }
    
    // Create device
    float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo deviceQueueCreateInfo = {};
    deviceQueueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    deviceQueueCreateInfo.queueFamilyIndex = g_vulkanContext.queueFamilyIndex;
    deviceQueueCreateInfo.queueCount = 1;
    deviceQueueCreateInfo.pQueuePriorities = &queuePriority;
    
    VkPhysicalDeviceFeatures deviceFeatures = {};
    deviceFeatures.shaderStorageImageExtendedFormats = VK_TRUE;
    deviceFeatures.shaderStorageImageMultisample = VK_TRUE;
    
    const char* deviceExtensions[] = {
        VK_KHR_STORAGE_BUFFER_STORAGE_CLASS_EXTENSION_NAME,
        VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME
    };
    
    VkDeviceCreateInfo deviceCreateInfo = {};
    deviceCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceCreateInfo.queueCreateInfoCount = 1;
    deviceCreateInfo.pQueueCreateInfos = &deviceQueueCreateInfo;
    deviceCreateInfo.enabledExtensionCount = 2;
    deviceCreateInfo.ppEnabledExtensionNames = deviceExtensions;
    deviceCreateInfo.pEnabledFeatures = &deviceFeatures;
    
    result = vkCreateDevice(g_vulkanContext.physicalDevice, &deviceCreateInfo, nullptr, &g_vulkanContext.device);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create Vulkan device: %d", result);
        vkDestroyInstance(g_vulkanContext.instance, nullptr);
        return JNI_FALSE;
    }
    LOGD("Vulkan device created");
    
    // Get compute queue
    vkGetDeviceQueue(g_vulkanContext.device, g_vulkanContext.queueFamilyIndex, 0, &g_vulkanContext.queue);
    LOGD("Compute queue obtained");
    
    // Create command pool
    VkCommandPoolCreateInfo cmdPoolCreateInfo = {};
    cmdPoolCreateInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    cmdPoolCreateInfo.queueFamilyIndex = g_vulkanContext.queueFamilyIndex;
    cmdPoolCreateInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    
    result = vkCreateCommandPool(g_vulkanContext.device, &cmdPoolCreateInfo, nullptr, &g_vulkanContext.commandPool);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create command pool");
        vkDestroyDevice(g_vulkanContext.device, nullptr);
        vkDestroyInstance(g_vulkanContext.instance, nullptr);
        return JNI_FALSE;
    }
    
    // Create command buffer
    VkCommandBufferAllocateInfo cmdBufAllocateInfo = {};
    cmdBufAllocateInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cmdBufAllocateInfo.commandPool = g_vulkanContext.commandPool;
    cmdBufAllocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cmdBufAllocateInfo.commandBufferCount = 1;
    
    result = vkAllocateCommandBuffers(g_vulkanContext.device, &cmdBufAllocateInfo, &g_vulkanContext.commandBuffer);
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate command buffer");
        vkDestroyCommandPool(g_vulkanContext.device, g_vulkanContext.commandPool, nullptr);
        vkDestroyDevice(g_vulkanContext.device, nullptr);
        vkDestroyInstance(g_vulkanContext.instance, nullptr);
        return JNI_FALSE;
    }
    
    // Create fence
    VkFenceCreateInfo fenceInfo = {};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    
    result = vkCreateFence(g_vulkanContext.device, &fenceInfo, nullptr, &g_vulkanContext.fence);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create fence");
        return JNI_FALSE;
    }
    
    // Create descriptor pool
    VkDescriptorPoolSize poolSizes[] = {
        {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 4},
        {VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 4},
        {VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 4},
        {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 4}
    };
    
    VkDescriptorPoolCreateInfo descPoolCreateInfo = {};
    descPoolCreateInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    descPoolCreateInfo.maxSets = 4;
    descPoolCreateInfo.poolSizeCount = 4;
    descPoolCreateInfo.pPoolSizes = poolSizes;
    
    result = vkCreateDescriptorPool(g_vulkanContext.device, &descPoolCreateInfo, nullptr, &g_vulkanContext.descriptorPool);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create descriptor pool");
        return JNI_FALSE;
    }
    
    // Create pipeline cache
    VkPipelineCacheCreateInfo cacheInfo = {};
    cacheInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO;
    result = vkCreatePipelineCache(g_vulkanContext.device, &cacheInfo, nullptr, &g_vulkanContext.pipelineCache);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create pipeline cache");
        return JNI_FALSE;
    }
    
    g_vulkanContext.initialized = true;
    g_vulkanContext.useCompute = useComputeOnly;
    
    LOGD("Vulkan initialized successfully");
    return JNI_TRUE;
}

// Create a shader module from SPIR-V binary
static VkShaderModule createShaderModuleInternal(VkDevice device, const uint32_t* code, size_t size) {
    VkShaderModule shaderModule;
    VkShaderModuleCreateInfo createInfo = {};
    createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    createInfo.codeSize = size;
    createInfo.pCode = code;
    
    VkResult result = vkCreateShaderModule(device, &createInfo, nullptr, &shaderModule);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create shader module: %d", result);
        return VK_NULL_HANDLE;
    }
    return shaderModule;
}

// Create compute pipeline for paper rendering
static VkResult createPaperComputePipelineInternal(VkDevice device, VkPhysicalDevice physicalDevice) {
    // Placeholder - in a real implementation, we would:
    // 1. Load SPIR-V shader code
    // 2. Create shader module
    // 3. Create descriptor set layout
    // 4. Create pipeline layout
    // 5. Create compute pipeline
    
    // For now, create a simple pipeline that we can use
    // In production, this would use actual compiled shaders
    
    // Create descriptor set layout
    VkDescriptorSetLayoutBinding bindings[2] = {
        {
            0, // binding
            VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, // descriptorType
            1, // descriptorCount
            VK_SHADER_STAGE_COMPUTE_BIT, // stageFlags
            nullptr // pImmutableSamplers
        },
        {
            1, // binding
            VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, // descriptorType
            1, // descriptorCount
            VK_SHADER_STAGE_COMPUTE_BIT, // stageFlags
            nullptr // pImmutableSamplers
        }
    };
    
    VkDescriptorSetLayoutCreateInfo layoutInfo = {};
    layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    layoutInfo.bindingCount = 2;
    layoutInfo.pBindings = bindings;
    
    VkResult result = vkCreateDescriptorSetLayout(
        device, 
        &layoutInfo, 
        nullptr, 
        &g_vulkanContext.descriptorSetLayout
    );
    if (result != VK_SUCCESS) {
        LOGE("Failed to create descriptor set layout");
        return result;
    }
    
    // Create pipeline layout
    VkPipelineLayoutCreateInfo pipelineLayoutInfo = {};
    pipelineLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    pipelineLayoutInfo.setLayoutCount = 1;
    pipelineLayoutInfo.pSetLayouts = &g_vulkanContext.descriptorSetLayout;
    
    result = vkCreatePipelineLayout(
        device,
        &pipelineLayoutInfo,
        nullptr,
        &g_vulkanContext.pipelineLayout
    );
    if (result != VK_SUCCESS) {
        LOGE("Failed to create pipeline layout");
        return result;
    }
    
    // For now, we'll skip the actual shader creation since we don't have
    // the SPIR-V binaries embedded. In production, you would:
    // 1. Embed the SPIR-V as a uint32_t array
    // 2. Call createShaderModuleInternal with that array
    // 3. Create the compute pipeline with the shader stage
    
    // Create a dummy shader module (will fail, but we handle it)
    // In real code, replace this with actual shader creation
    uint32_t dummyShader[] = {0x07230203, 0x00010000, 0x00080001};
    g_vulkanContext.shaderModule = createShaderModuleInternal(device, dummyShader, sizeof(dummyShader));
    if (g_vulkanContext.shaderModule == VK_NULL_HANDLE) {
        LOGD("Note: Using dummy shader - embed real SPIR-V for production");
        // Continue anyway - the pipeline will be created but won't work properly
    }
    
    VkPipelineShaderStageCreateInfo shaderStageInfo = {};
    shaderStageInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    shaderStageInfo.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    shaderStageInfo.module = g_vulkanContext.shaderModule;
    shaderStageInfo.pName = "main";
    
    // Create compute pipeline
    VkComputePipelineCreateInfo pipelineInfo = {};
    pipelineInfo.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
    pipelineInfo.stage = shaderStageInfo;
    pipelineInfo.layout = g_vulkanContext.pipelineLayout;
    pipelineInfo.basePipelineHandle = VK_NULL_HANDLE;
    pipelineInfo.basePipelineIndex = -1;
    
    result = vkCreateComputePipelines(
        device,
        g_vulkanContext.pipelineCache,
        1,
        &pipelineInfo,
        nullptr,
        &g_vulkanContext.computePipeline
    );
    if (result != VK_SUCCESS) {
        LOGE("Failed to create compute pipeline: %d (expected with dummy shader)", result);
        // This is expected with dummy shader - in production, use real SPIR-V
    }
    
    return result;
}

// Create storage buffer for bitmap data
static VkResult createStorageBufferInternal(VkDevice device, VkPhysicalDevice physicalDevice, VkDeviceSize size, void* initialData) {
    VkBufferCreateInfo bufferInfo = {};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = size;
    bufferInfo.usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    
    VkResult result = vkCreateBuffer(device, &bufferInfo, nullptr, &g_vulkanContext.storageBuffer);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create buffer");
        return result;
    }
    
    VkMemoryRequirements memRequirements;
    vkGetBufferMemoryRequirements(device, g_vulkanContext.storageBuffer, &memRequirements);
    
    // Find memory type
    VkPhysicalDeviceMemoryProperties memProperties;
    vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memProperties);
    
    uint32_t memoryTypeIndex = 0;
    bool found = false;
    for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++) {
        if ((memRequirements.memoryTypeBits & (1 << i)) &&
            (memProperties.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
            memoryTypeIndex = i;
            found = true;
            break;
        }
    }
    
    if (!found) {
        LOGE("Failed to find suitable memory type");
        vkDestroyBuffer(device, g_vulkanContext.storageBuffer, nullptr);
        return VK_ERROR_MEMORY_MAP_FAILED;
    }
    
    VkMemoryAllocateInfo allocInfo = {};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memRequirements.size;
    allocInfo.memoryTypeIndex = memoryTypeIndex;
    
    result = vkAllocateMemory(device, &allocInfo, nullptr, &g_vulkanContext.storageBufferMemory);
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate memory");
        vkDestroyBuffer(device, g_vulkanContext.storageBuffer, nullptr);
        return result;
    }
    
    if (initialData) {
        void* mappedMemory;
        result = vkMapMemory(device, g_vulkanContext.storageBufferMemory, 0, size, 0, &mappedMemory);
        if (result == VK_SUCCESS) {
            memcpy(mappedMemory, initialData, size);
            vkUnmapMemory(device, g_vulkanContext.storageBufferMemory);
        }
    }
    
    result = vkBindBufferMemory(device, g_vulkanContext.storageBuffer, g_vulkanContext.storageBufferMemory, 0);
    if (result != VK_SUCCESS) {
        LOGE("Failed to bind buffer memory");
        vkFreeMemory(device, g_vulkanContext.storageBufferMemory, nullptr);
        vkDestroyBuffer(device, g_vulkanContext.storageBuffer, nullptr);
        return result;
    }
    
    return VK_SUCCESS;
}

// Render paper texture using Vulkan compute shader
// Note: This is a simplified version that falls back to CPU rendering
// In production, you would need to:
// 1. Compile shaders to SPIR-V
// 2. Embed SPIR-V in the code
// 3. Properly set up the compute pipeline
// 4. Map and read back the results
extern "C" JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_renderPaperVulkan(JNIEnv* env, jobject thiz, jobject bitmap, 
                                                    jint width, jint height, jint seed, 
                                                    jfloat grainIntensity, jfloat fiberDensity, 
                                                    jint waterStainCount, jfloat agingYellow, 
                                                    jfloat fiberDirection, jfloat roughness) {
    LOGD("renderPaperVulkan called - Vulkan GPU rendering");
    
    // For now, fall back to CPU rendering since we don't have
    // the actual SPIR-V shaders embedded
    // In production, this would use the Vulkan compute pipeline
    
    // Check if Vulkan is initialized
    if (!g_vulkanContext.initialized) {
        if (!Java_com_example_homecil_native_PaperEngineNative_initVulkan(env, thiz, JNI_TRUE)) {
            LOGE("Failed to initialize Vulkan, falling back to CPU");
            // Fall through to CPU rendering
        }
    }
    
    // If Vulkan is available but we don't have proper shaders,
    // log a message and fall back to CPU
    if (g_vulkanContext.initialized) {
        LOGD("Vulkan initialized but using CPU fallback (shaders not embedded)");
        LOGD("To enable GPU rendering: compile shaders to SPIR-V and embed in vulkan_engine.cpp");
    }
    
    // Fall back to CPU rendering by calling the existing function
    // This ensures the code compiles and works, even without GPU acceleration
    AndroidBitmapInfo info;
    uint32_t* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return;
    }
    
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888");
        return;
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, (void**)&pixels) < 0) {
        LOGE("Failed to lock pixels");
        return;
    }
    
    // Simple CPU-based paper rendering as fallback
    const float baseR = 251.0f;
    const float baseG = 249.0f;
    const float baseB = 242.0f;
    
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            // Simple gradient for now
            float nx = static_cast<float>(x) / width;
            float ny = static_cast<float>(y) / height;
            
            // Add some variation
            float variation = ((x * 7 + y * 13 + seed) % 255) / 255.0f * grainIntensity * 20.0f;
            variation -= 10.0f;
            
            int newR = static_cast<int>(baseR + variation);
            int newG = static_cast<int>(baseG + variation * 0.92f);
            int newB = static_cast<int>(baseB + variation * 0.78f + agingYellow * 50.0f);
            
            newR = (newR < 0) ? 0 : (newR > 255) ? 255 : newR;
            newG = (newG < 0) ? 0 : (newG > 255) ? 255 : newG;
            newB = (newB < 0) ? 0 : (newB > 255) ? 255 : newB;
            
            pixels[y * width + x] = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
        }
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
    LOGD("renderPaperVulkan completed (CPU fallback)");
}

// Simulate ink using Vulkan (fallback to CPU)
extern "C" JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_simulateInkVulkan(JNIEnv* env, jobject thiz, jobject bitmap, 
                                                    jobject inkBitmap, jint x, jint y, 
                                                    jfloat inkColorR, jfloat inkColorG, jfloat inkColorB,
                                                    jfloat absorption, jfloat noiseIntensity, jint seed) {
    LOGD("simulateInkVulkan called - using CPU fallback");
    
    // Fall back to CPU implementation
    AndroidBitmapInfo info;
    uint32_t* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return;
    }
    
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888");
        return;
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, (void**)&pixels) < 0) {
        LOGE("Failed to lock pixels");
        return;
    }
    
    AndroidBitmapInfo inkInfo;
    uint32_t* inkPixels;
    
    if (AndroidBitmap_getInfo(env, inkBitmap, &inkInfo) < 0) {
        LOGE("Failed to get ink bitmap info");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }
    
    if (inkInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Ink bitmap format is not RGBA_8888");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }
    
    if (AndroidBitmap_lockPixels(env, inkBitmap, (void**)&inkPixels) < 0) {
        LOGE("Failed to lock ink pixels");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }
    
    // Simple ink application
    int inkWidth = inkInfo.width;
    int inkHeight = inkInfo.height;
    
    for (int iy = 0; iy < inkHeight; iy++) {
        for (int ix = 0; ix < inkWidth; ix++) {
            int px = x + ix;
            int py = y + iy;
            
            if (px >= 0 && px < info.width && py >= 0 && py < info.height) {
                uint32_t inkPixel = inkPixels[iy * inkWidth + ix];
                uint8_t inkAlpha = (inkPixel >> 24) & 0xFF;
                
                if (inkAlpha > 0) {
                    uint32_t paperPixel = pixels[py * info.width + px];
                    uint8_t r = (paperPixel >> 16) & 0xFF;
                    uint8_t g = (paperPixel >> 8) & 0xFF;
                    uint8_t b = paperPixel & 0xFF;
                    
                    float alpha = inkAlpha / 255.0f * absorption;
                    float invAlpha = 1.0f - alpha;
                    
                    int newR = static_cast<int>(r * invAlpha + inkColorR * 255.0f * alpha);
                    int newG = static_cast<int>(g * invAlpha + inkColorG * 255.0f * alpha);
                    int newB = static_cast<int>(b * invAlpha + inkColorB * 255.0f * alpha);
                    
                    newR = (newR < 0) ? 0 : (newR > 255) ? 255 : newR;
                    newG = (newG < 0) ? 0 : (newG > 255) ? 255 : newG;
                    newB = (newB < 0) ? 0 : (newB > 255) ? 255 : newB;
                    
                    pixels[py * info.width + px] = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
                }
            }
        }
    }
    
    AndroidBitmap_unlockPixels(env, inkBitmap);
    AndroidBitmap_unlockPixels(env, bitmap);
    LOGD("simulateInkVulkan completed (CPU fallback)");
}

// Distort bitmap using Vulkan (fallback to CPU)
extern "C" JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_distortBitmapVulkan(JNIEnv* env, jobject thiz, jobject bitmap, 
                                                    jint seed, jfloat distortionScale, jfloat sineWarpScale, jfloat curvatureScale) {
    LOGD("distortBitmapVulkan called - using CPU fallback");
    
    // Fall back to CPU implementation
    AndroidBitmapInfo info;
    uint32_t* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return;
    }
    
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888");
        return;
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, (void**)&pixels) < 0) {
        LOGE("Failed to lock pixels");
        return;
    }
    
    int width = info.width;
    int height = info.height;
    
    // Create a temporary buffer for distortion
    uint32_t* tempPixels = new uint32_t[width * height];
    memcpy(tempPixels, pixels, width * height * sizeof(uint32_t));
    
    // Simple distortion
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float nx = static_cast<float>(x);
            float ny = static_cast<float>(y);
            
            // Add distortion
            float dx = distortionScale * 0.1f * sin(nx * 0.1f + seed);
            float dy = distortionScale * 0.1f * cos(ny * 0.1f + seed);
            
            // Add sine warp
            dx += sineWarpScale * 0.05f * sin(nx * 0.05f);
            dy += sineWarpScale * 0.05f * cos(ny * 0.05f);
            
            int srcX = static_cast<int>(nx + dx);
            int srcY = static_cast<int>(ny + dy);
            
            if (srcX >= 0 && srcX < width && srcY >= 0 && srcY < height) {
                pixels[y * width + x] = tempPixels[srcY * width + srcX];
            }
        }
    }
    
    delete[] tempPixels;
    AndroidBitmap_unlockPixels(env, bitmap);
    LOGD("distortBitmapVulkan completed (CPU fallback)");
}

// Shutdown Vulkan
extern "C" JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_shutdownVulkan(JNIEnv* env, jobject thiz) {
    if (!g_vulkanContext.initialized) {
        return;
    }
    
    vkDeviceWaitIdle(g_vulkanContext.device);
    
    if (g_vulkanContext.shaderModule != VK_NULL_HANDLE) {
        vkDestroyShaderModule(g_vulkanContext.device, g_vulkanContext.shaderModule, nullptr);
    }
    if (g_vulkanContext.computePipeline != VK_NULL_HANDLE) {
        vkDestroyPipeline(g_vulkanContext.device, g_vulkanContext.computePipeline, nullptr);
    }
    if (g_vulkanContext.pipelineLayout != VK_NULL_HANDLE) {
        vkDestroyPipelineLayout(g_vulkanContext.device, g_vulkanContext.pipelineLayout, nullptr);
    }
    if (g_vulkanContext.descriptorSetLayout != VK_NULL_HANDLE) {
        vkDestroyDescriptorSetLayout(g_vulkanContext.device, g_vulkanContext.descriptorSetLayout, nullptr);
    }
    if (g_vulkanContext.descriptorPool != VK_NULL_HANDLE) {
        vkDestroyDescriptorPool(g_vulkanContext.device, g_vulkanContext.descriptorPool, nullptr);
    }
    if (g_vulkanContext.pipelineCache != VK_NULL_HANDLE) {
        vkDestroyPipelineCache(g_vulkanContext.device, g_vulkanContext.pipelineCache, nullptr);
    }
    if (g_vulkanContext.fence != VK_NULL_HANDLE) {
        vkDestroyFence(g_vulkanContext.device, g_vulkanContext.fence, nullptr);
    }
    if (g_vulkanContext.commandBuffer != VK_NULL_HANDLE) {
        vkFreeCommandBuffers(g_vulkanContext.device, g_vulkanContext.commandPool, 1, &g_vulkanContext.commandBuffer);
    }
    if (g_vulkanContext.commandPool != VK_NULL_HANDLE) {
        vkDestroyCommandPool(g_vulkanContext.device, g_vulkanContext.commandPool, nullptr);
    }
    if (g_vulkanContext.storageBuffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(g_vulkanContext.device, g_vulkanContext.storageBuffer, nullptr);
    }
    if (g_vulkanContext.storageBufferMemory != VK_NULL_HANDLE) {
        vkFreeMemory(g_vulkanContext.device, g_vulkanContext.storageBufferMemory, nullptr);
    }
    if (g_vulkanContext.device != VK_NULL_HANDLE) {
        vkDestroyDevice(g_vulkanContext.device, nullptr);
    }
    if (g_vulkanContext.instance != VK_NULL_HANDLE) {
        vkDestroyInstance(g_vulkanContext.instance, nullptr);
    }
    
    memset(&g_vulkanContext, 0, sizeof(g_vulkanContext));
    LOGD("Vulkan shutdown complete");
}

// Get Vulkan device information
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_homecil_native_PaperEngineNative_getVulkanDeviceInfo(JNIEnv* env, jobject thiz) {
    if (!g_vulkanContext.initialized) {
        // Try to initialize first
        if (!Java_com_example_homecil_native_PaperEngineNative_initVulkan(env, thiz, JNI_TRUE)) {
            return env->NewStringUTF("Vulkan not available");
        }
    }
    
    VkPhysicalDeviceProperties props;
    vkGetPhysicalDeviceProperties(g_vulkanContext.physicalDevice, &props);
    
    char info[512];
    snprintf(info, sizeof(info), 
             "Device: %s\nAPI Version: %d.%d.%d\nDriver Version: %d\nVendor ID: 0x%04X\nDevice ID: 0x%04X",
             props.deviceName,
             VK_VERSION_MAJOR(props.apiVersion),
             VK_VERSION_MINOR(props.apiVersion),
             VK_VERSION_PATCH(props.apiVersion),
             props.driverVersion,
             props.vendorID,
             props.deviceID);
    
    return env->NewStringUTF(info);
}
