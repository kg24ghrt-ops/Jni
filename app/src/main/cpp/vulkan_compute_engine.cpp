#include "vulkan_compute_engine.h"
#include <android/native_window.h>
#include <cstring>
#include <iostream>
#include <vector>

#define VK_CHECK(result) if ((result) != VK_SUCCESS) { return false; }

// --------------------------------------------------------------
// Singleton
// --------------------------------------------------------------
VulkanComputeEngine& VulkanComputeEngine::getInstance() {
    static VulkanComputeEngine instance;
    return instance;
}

// --------------------------------------------------------------
// Initialization / Shutdown
// --------------------------------------------------------------
bool VulkanComputeEngine::initialize(ANativeWindow* window) {
    if (initialized) return true;

    if (!createInstance()) return false;
    if (!pickPhysicalDevice()) return false;
    if (!createDevice()) return false;
    if (!createCommandPool()) return false;
    if (!createDescriptorPool()) return false;
    if (!createDescriptorSetLayout()) return false;
    if (!createSampler()) return false;

    initialized = true;
    return true;
}

void VulkanComputeEngine::shutdown() {
    if (device != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(device);
        if (defaultSampler != VK_NULL_HANDLE) {
            vkDestroySampler(device, defaultSampler, nullptr);
            defaultSampler = VK_NULL_HANDLE;
        }
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, nullptr);
            descriptorSetLayout = VK_NULL_HANDLE;
        }
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, nullptr);
            descriptorPool = VK_NULL_HANDLE;
        }
        if (commandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(device, commandPool, nullptr);
            commandPool = VK_NULL_HANDLE;
        }
        vkDestroyDevice(device, nullptr);
        device = VK_NULL_HANDLE;
    }
    if (instance != VK_NULL_HANDLE) {
        vkDestroyInstance(instance, nullptr);
        instance = VK_NULL_HANDLE;
    }
    initialized = false;
}

// --------------------------------------------------------------
// Instance Creation
// --------------------------------------------------------------
bool VulkanComputeEngine::createInstance() {
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "HomeCil";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "No Engine";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_2;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    // Required extensions for Android
    std::vector<const char*> extensions = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
        VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
    };
    createInfo.enabledExtensionCount = extensions.size();
    createInfo.ppEnabledExtensionNames = extensions.data();

    VK_CHECK(vkCreateInstance(&createInfo, nullptr, &instance));
    return true;
}

// --------------------------------------------------------------
// Physical Device Selection
// --------------------------------------------------------------
bool VulkanComputeEngine::pickPhysicalDevice() {
    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (deviceCount == 0) return false;

    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());

    for (auto dev : devices) {
        VkPhysicalDeviceProperties props;
        vkGetPhysicalDeviceProperties(dev, &props);

        // Check for compute queue
        uint32_t queueFamilyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(dev, &queueFamilyCount, nullptr);
        std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(dev, &queueFamilyCount, queueFamilies.data());

        for (uint32_t i = 0; i < queueFamilyCount; i++) {
            if (queueFamilies[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
                physicalDevice = dev;
                queueFamilyIndex = i;
                return true;
            }
        }
    }
    return false;
}

// --------------------------------------------------------------
// Device Creation
// --------------------------------------------------------------
bool VulkanComputeEngine::createDevice() {
    float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo queueCreateInfo{};
    queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueCreateInfo.queueFamilyIndex = queueFamilyIndex;
    queueCreateInfo.queueCount = 1;
    queueCreateInfo.pQueuePriorities = &queuePriority;

    // Enable required features (none needed for basic compute)
    VkPhysicalDeviceFeatures deviceFeatures{};

    // Enable extension for storage buffer / image readback? We'll use transfer queues.
    std::vector<const char*> deviceExtensions = {};

    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.pQueueCreateInfos = &queueCreateInfo;
    createInfo.queueCreateInfoCount = 1;
    createInfo.pEnabledFeatures = &deviceFeatures;
    createInfo.enabledExtensionCount = deviceExtensions.size();
    createInfo.ppEnabledExtensionNames = deviceExtensions.data();

    VK_CHECK(vkCreateDevice(physicalDevice, &createInfo, nullptr, &device));

    vkGetDeviceQueue(device, queueFamilyIndex, 0, &computeQueue);
    return true;
}

// --------------------------------------------------------------
// Command Pool
// --------------------------------------------------------------
bool VulkanComputeEngine::createCommandPool() {
    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.queueFamilyIndex = queueFamilyIndex;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;

    VK_CHECK(vkCreateCommandPool(device, &poolInfo, nullptr, &commandPool));
    return true;
}

// --------------------------------------------------------------
// Descriptor Pool
// --------------------------------------------------------------
bool VulkanComputeEngine::createDescriptorPool() {
    // We need: storage image (4) + combined image sampler (4)
    std::vector<VkDescriptorPoolSize> poolSizes = {
        {VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 8},
        {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 8}
    };

    VkDescriptorPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    poolInfo.poolSizeCount = poolSizes.size();
    poolInfo.pPoolSizes = poolSizes.data();
    poolInfo.maxSets = 8; // enough for our use

    VK_CHECK(vkCreateDescriptorPool(device, &poolInfo, nullptr, &descriptorPool));
    return true;
}

// --------------------------------------------------------------
// Descriptor Set Layout (matching the shader bindings)
// --------------------------------------------------------------
bool VulkanComputeEngine::createDescriptorSetLayout() {
    std::vector<VkDescriptorSetLayoutBinding> bindings;

    // For storage images (binding 0 in shader)
    VkDescriptorSetLayoutBinding storageBinding{};
    storageBinding.binding = 0;
    storageBinding.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    storageBinding.descriptorCount = 1;
    storageBinding.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    bindings.push_back(storageBinding);

    // For combined image samplers (binding 1, 2, etc.)
    VkDescriptorSetLayoutBinding samplerBinding{};
    samplerBinding.binding = 1;
    samplerBinding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    samplerBinding.descriptorCount = 1;
    samplerBinding.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    bindings.push_back(samplerBinding);

    // Actually, we need different shaders have different bindings.
    // But we can reuse the same layout for all: we'll just make it flexible.
    // For simplicity, we'll create a layout with two bindings: storage image (binding 0) and sampler (binding 1).
    // If a shader needs more, we can extend later.

    // For paper generation: only storage image (binding 0) – no sampler.
    // For capillary map: only storage image (binding 0).
    // For physics: storage images (bindings 2 and 3) and samplers (bindings 0 and 1).
    // For composite: samplers (bindings 0 and 1) and storage image (binding 2).
    // So we need a more flexible layout. We'll create a layout with all possible bindings (0-3) for both types.

    VkDescriptorSetLayoutBinding allBindings[4];
    // Binding 0: storage image
    allBindings[0].binding = 0;
    allBindings[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    allBindings[0].descriptorCount = 1;
    allBindings[0].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    // Binding 1: combined image sampler
    allBindings[1].binding = 1;
    allBindings[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    allBindings[1].descriptorCount = 1;
    allBindings[1].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    // Binding 2: storage image (for physics input/output)
    allBindings[2].binding = 2;
    allBindings[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    allBindings[2].descriptorCount = 1;
    allBindings[2].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    // Binding 3: storage image (for physics output)
    allBindings[3].binding = 3;
    allBindings[3].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    allBindings[3].descriptorCount = 1;
    allBindings[3].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    VkDescriptorSetLayoutCreateInfo layoutInfo{};
    layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    layoutInfo.bindingCount = 4;
    layoutInfo.pBindings = allBindings;

    VK_CHECK(vkCreateDescriptorSetLayout(device, &layoutInfo, nullptr, &descriptorSetLayout));
    return true;
}

// --------------------------------------------------------------
// Default Sampler
// --------------------------------------------------------------
bool VulkanComputeEngine::createSampler() {
    VkSamplerCreateInfo samplerInfo{};
    samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    samplerInfo.magFilter = VK_FILTER_LINEAR;
    samplerInfo.minFilter = VK_FILTER_LINEAR;
    samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.anisotropyEnable = VK_FALSE;
    samplerInfo.maxAnisotropy = 1.0f;
    samplerInfo.borderColor = VK_BORDER_COLOR_INT_OPAQUE_BLACK;
    samplerInfo.unnormalizedCoordinates = VK_FALSE;
    samplerInfo.compareEnable = VK_FALSE;
    samplerInfo.compareOp = VK_COMPARE_OP_ALWAYS;
    samplerInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;
    samplerInfo.mipLodBias = 0.0f;
    samplerInfo.minLod = 0.0f;
    samplerInfo.maxLod = 0.0f;

    VK_CHECK(vkCreateSampler(device, &samplerInfo, nullptr, &defaultSampler));
    return true;
}

// --------------------------------------------------------------
// Image & Memory Creation
// --------------------------------------------------------------
VkImage VulkanComputeEngine::createImage(uint32_t width, uint32_t height, VkFormat format, VkImageUsageFlags usage) {
    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.extent.width = width;
    imageInfo.extent.height = height;
    imageInfo.extent.depth = 1;
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.format = format;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    imageInfo.usage = usage;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    VkImage image;
    if (vkCreateImage(device, &imageInfo, nullptr, &image) != VK_SUCCESS) {
        return VK_NULL_HANDLE;
    }
    return image;
}

VkImageView VulkanComputeEngine::createImageView(VkImage image, VkFormat format) {
    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = image;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = format;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;

    VkImageView imageView;
    if (vkCreateImageView(device, &viewInfo, nullptr, &imageView) != VK_SUCCESS) {
        return VK_NULL_HANDLE;
    }
    return imageView;
}

bool VulkanComputeEngine::allocateImageMemory(VkImage image, VkMemoryPropertyFlags properties) {
    VkMemoryRequirements memRequirements;
    vkGetImageMemoryRequirements(device, image, &memRequirements);

    VkPhysicalDeviceMemoryProperties memProperties;
    vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memProperties);

    uint32_t memoryTypeIndex = 0;
    for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++) {
        if ((memRequirements.memoryTypeBits & (1 << i)) &&
            (memProperties.memoryTypes[i].propertyFlags & properties) == properties) {
            memoryTypeIndex = i;
            break;
        }
    }
    if (memoryTypeIndex == 0 && properties != 0) {
        // fallback: try again without properties
        for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++) {
            if (memRequirements.memoryTypeBits & (1 << i)) {
                memoryTypeIndex = i;
                break;
            }
        }
    }

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memRequirements.size;
    allocInfo.memoryTypeIndex = memoryTypeIndex;

    VkDeviceMemory memory;
    if (vkAllocateMemory(device, &allocInfo, nullptr, &memory) != VK_SUCCESS) {
        return false;
    }

    if (vkBindImageMemory(device, image, memory, 0) != VK_SUCCESS) {
        vkFreeMemory(device, memory, nullptr);
        return false;
    }
    // Store memory in a map? For simplicity, we'll just assume the caller manages it.
    // We could store it, but for this engine we'll let the caller handle cleanup.
    // To avoid leaks, we'll maintain a map of image->memory.
    // For now, we'll keep a static map.
    static std::unordered_map<VkImage, VkDeviceMemory> memoryMap;
    memoryMap[image] = memory;
    return true;
}

void VulkanComputeEngine::destroyImage(VkImage image) {
    // Free associated memory if any
    static std::unordered_map<VkImage, VkDeviceMemory> memoryMap;
    auto it = memoryMap.find(image);
    if (it != memoryMap.end()) {
        vkFreeMemory(device, it->second, nullptr);
        memoryMap.erase(it);
    }
    vkDestroyImage(device, image, nullptr);
}

void VulkanComputeEngine::destroyImageView(VkImageView imageView) {
    vkDestroyImageView(device, imageView, nullptr);
}

// --------------------------------------------------------------
// Descriptor Set Creation
// --------------------------------------------------------------
VkDescriptorSet VulkanComputeEngine::createStorageImageDescriptorSet(VkImageView imageView) {
    VkDescriptorSetAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    allocInfo.descriptorPool = descriptorPool;
    allocInfo.descriptorSetCount = 1;
    allocInfo.pSetLayouts = &descriptorSetLayout;

    VkDescriptorSet descriptorSet;
    if (vkAllocateDescriptorSets(device, &allocInfo, &descriptorSet) != VK_SUCCESS) {
        return VK_NULL_HANDLE;
    }

    VkDescriptorImageInfo imageInfo{};
    imageInfo.imageView = imageView;
    imageInfo.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

    VkWriteDescriptorSet write{};
    write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    write.dstSet = descriptorSet;
    write.dstBinding = 0; // binding 0 = storage image
    write.dstArrayElement = 0;
    write.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    write.descriptorCount = 1;
    write.pImageInfo = &imageInfo;

    vkUpdateDescriptorSets(device, 1, &write, 0, nullptr);
    return descriptorSet;
}

VkDescriptorSet VulkanComputeEngine::createCombinedImageDescriptorSet(VkImageView imageView, VkSampler sampler) {
    VkDescriptorSetAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    allocInfo.descriptorPool = descriptorPool;
    allocInfo.descriptorSetCount = 1;
    allocInfo.pSetLayouts = &descriptorSetLayout;

    VkDescriptorSet descriptorSet;
    if (vkAllocateDescriptorSets(device, &allocInfo, &descriptorSet) != VK_SUCCESS) {
        return VK_NULL_HANDLE;
    }

    VkDescriptorImageInfo imageInfo{};
    imageInfo.imageView = imageView;
    imageInfo.sampler = (sampler != VK_NULL_HANDLE) ? sampler : defaultSampler;
    imageInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    VkWriteDescriptorSet write{};
    write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    write.dstSet = descriptorSet;
    write.dstBinding = 1; // binding 1 = combined image sampler
    write.dstArrayElement = 0;
    write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    write.descriptorCount = 1;
    write.pImageInfo = &imageInfo;

    vkUpdateDescriptorSets(device, 1, &write, 0, nullptr);
    return descriptorSet;
}

VkSampler VulkanComputeEngine::createSampler() {
    return defaultSampler; // already created
}

// --------------------------------------------------------------
// Dispatch
// --------------------------------------------------------------
bool VulkanComputeEngine::dispatchCompute(
    VkShaderModule shaderModule,
    const std::vector<VkDescriptorSet>& descriptorSets,
    const void* pushConstants,
    size_t pushConstantsSize,
    uint32_t workX, uint32_t workY, uint32_t workZ) {

    if (!device || !commandPool || !shaderModule) return false;

    // Create pipeline layout
    VkPushConstantRange pushRange{};
    pushRange.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    pushRange.offset = 0;
    pushRange.size = pushConstantsSize;

    VkPipelineLayoutCreateInfo layoutInfo{};
    layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    layoutInfo.pushConstantRangeCount = (pushConstantsSize > 0) ? 1 : 0;
    layoutInfo.pPushConstantRanges = &pushRange;
    layoutInfo.setLayoutCount = 1;
    layoutInfo.pSetLayouts = &descriptorSetLayout;

    VkPipelineLayout pipelineLayout;
    if (vkCreatePipelineLayout(device, &layoutInfo, nullptr, &pipelineLayout) != VK_SUCCESS) {
        return false;
    }

    // Create compute pipeline
    VkComputePipelineCreateInfo pipelineInfo{};
    pipelineInfo.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
    pipelineInfo.stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    pipelineInfo.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    pipelineInfo.stage.module = shaderModule;
    pipelineInfo.stage.pName = "main";
    pipelineInfo.layout = pipelineLayout;

    VkPipeline pipeline;
    if (vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &pipeline) != VK_SUCCESS) {
        vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
        return false;
    }

    // Allocate command buffer
    VkCommandBufferAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = commandPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = 1;

    VkCommandBuffer cmd;
    if (vkAllocateCommandBuffers(device, &allocInfo,