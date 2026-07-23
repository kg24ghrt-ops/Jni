#include "vulkan_compute_engine.h"
#include <cstring>
#include <unordered_map>
#include <utility>   // for std::pair

#define VK_CHECK(cmd) if ((cmd) != VK_SUCCESS) return false

// --------------------------------------------------------------
// Singleton
// --------------------------------------------------------------
VulkanComputeEngine& VulkanComputeEngine::getInstance() {
    static VulkanComputeEngine instance;
    return instance;
}

// --------------------------------------------------------------
// Init / Shutdown
// --------------------------------------------------------------
bool VulkanComputeEngine::initialize() {
    if (initialized) return true;
    VK_CHECK(createInstance());
    VK_CHECK(pickPhysicalDevice());
    VK_CHECK(createDevice());
    VK_CHECK(createCommandPool());
    VK_CHECK(createDescriptorPool());
    VK_CHECK(createDescriptorSetLayout());

    // Allocate a single reusable command buffer for short‑lived operations.
    VkCommandBufferAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = commandPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = 1;
    VK_CHECK(vkAllocateCommandBuffers(device, &allocInfo, &transientCmdBuffer));

    initialized = true;
    return true;
}

void VulkanComputeEngine::shutdown() {
    if (device) {
        vkDeviceWaitIdle(device);

        // Free cached pipelines & layouts
        for (auto& [key, pipeline] : pipelineCache)
            vkDestroyPipeline(device, pipeline, nullptr);
        pipelineCache.clear();

        for (auto& [pushSize, layout] : layoutCache)
            vkDestroyPipelineLayout(device, layout, nullptr);
        layoutCache.clear();

        // Free transient command buffer
        if (transientCmdBuffer)
            vkFreeCommandBuffers(device, commandPool, 1, &transientCmdBuffer);

        // Free all tracked images & their memory
        for (auto& [img, mem] : imageMemoryMap) {
            vkFreeMemory(device, mem, nullptr);
            vkDestroyImage(device, img, nullptr);
        }
        imageMemoryMap.clear();

        vkDestroyDescriptorSetLayout(device, descriptorSetLayout, nullptr);
        vkDestroyDescriptorPool(device, descriptorPool, nullptr);
        vkDestroyCommandPool(device, commandPool, nullptr);
        vkDestroyDevice(device, nullptr);
        device = VK_NULL_HANDLE;
    }
    if (instance) {
        vkDestroyInstance(instance, nullptr);
        instance = VK_NULL_HANDLE;
    }
    initialized = false;
}

// --------------------------------------------------------------
// Instance
// --------------------------------------------------------------
bool VulkanComputeEngine::createInstance() {
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "HomeCil";
    app.apiVersion = VK_API_VERSION_1_2;

    VkInstanceCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    info.pApplicationInfo = &app;
    info.enabledExtensionCount = 0;
    VK_CHECK(vkCreateInstance(&info, nullptr, &instance));
    return true;
}

// --------------------------------------------------------------
// Physical Device
// --------------------------------------------------------------
bool VulkanComputeEngine::pickPhysicalDevice() {
    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (!count) return false;

    std::vector<VkPhysicalDevice> devices(count);
    vkEnumeratePhysicalDevices(instance, &count, devices.data());

    for (auto dev : devices) {
        uint32_t qCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(dev, &qCount, nullptr);
        std::vector<VkQueueFamilyProperties> queues(qCount);
        vkGetPhysicalDeviceQueueFamilyProperties(dev, &qCount, queues.data());

        for (uint32_t i = 0; i < qCount; i++) {
            if (queues[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
                physicalDevice = dev;
                queueFamilyIndex = i;
                return true;
            }
        }
    }
    return false;
}

// --------------------------------------------------------------
// Device
// --------------------------------------------------------------
bool VulkanComputeEngine::createDevice() {
    float priority = 1.0f;
    VkDeviceQueueCreateInfo qInfo{};
    qInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    qInfo.queueFamilyIndex = queueFamilyIndex;
    qInfo.queueCount = 1;
    qInfo.pQueuePriorities = &priority;

    VkDeviceCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    info.pQueueCreateInfos = &qInfo;
    info.queueCreateInfoCount = 1;

    VK_CHECK(vkCreateDevice(physicalDevice, &info, nullptr, &device));
    vkGetDeviceQueue(device, queueFamilyIndex, 0, &computeQueue);
    return true;
}

// --------------------------------------------------------------
// Command Pool
// --------------------------------------------------------------
bool VulkanComputeEngine::createCommandPool() {
    VkCommandPoolCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    info.queueFamilyIndex = queueFamilyIndex;
    VK_CHECK(vkCreateCommandPool(device, &info, nullptr, &commandPool));
    return true;
}

// --------------------------------------------------------------
// Descriptor Pool
// --------------------------------------------------------------
bool VulkanComputeEngine::createDescriptorPool() {
    VkDescriptorPoolSize size{};
    size.type = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    size.descriptorCount = 8;

    VkDescriptorPoolCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    info.poolSizeCount = 1;
    info.pPoolSizes = &size;
    info.maxSets = 8;
    VK_CHECK(vkCreateDescriptorPool(device, &info, nullptr, &descriptorPool));
    return true;
}

// --------------------------------------------------------------
// Descriptor Set Layout (only storage images, binding 0)
// --------------------------------------------------------------
bool VulkanComputeEngine::createDescriptorSetLayout() {
    VkDescriptorSetLayoutBinding bind{};
    bind.binding = 0;
    bind.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    bind.descriptorCount = 1;
    bind.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    VkDescriptorSetLayoutCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    info.bindingCount = 1;
    info.pBindings = &bind;
    VK_CHECK(vkCreateDescriptorSetLayout(device, &info, nullptr, &descriptorSetLayout));
    return true;
}

// --------------------------------------------------------------
// Memory type helper
// --------------------------------------------------------------
uint32_t VulkanComputeEngine::findMemoryType(uint32_t typeBits,
                                             VkMemoryPropertyFlags properties) const {
    VkPhysicalDeviceMemoryProperties memProps;
    vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memProps);
    for (uint32_t i = 0; i < memProps.memoryTypeCount; i++) {
        if ((typeBits & (1u << i)) &&
            (memProps.memoryTypes[i].propertyFlags & properties) == properties)
            return i;
    }
    return ~0u; // shouldn't happen for valid requirements
}

// --------------------------------------------------------------
// Image Creation
// --------------------------------------------------------------
VkImage VulkanComputeEngine::createImage(uint32_t w, uint32_t h,
                                         VkFormat fmt, VkImageUsageFlags usage) {
    VkImageCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    info.imageType = VK_IMAGE_TYPE_2D;
    info.extent = {w, h, 1};
    info.mipLevels = 1;
    info.arrayLayers = 1;
    info.format = fmt;
    info.tiling = VK_IMAGE_TILING_OPTIMAL;
    info.usage = usage;
    info.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    info.samples = VK_SAMPLE_COUNT_1_BIT;

    VkImage image;
    if (vkCreateImage(device, &info, nullptr, &image) != VK_SUCCESS)
        return VK_NULL_HANDLE;
    return image;
}

VkImageView VulkanComputeEngine::createImageView(VkImage image, VkFormat format) {
    VkImageViewCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    info.image = image;
    info.viewType = VK_IMAGE_VIEW_TYPE_2D;
    info.format = format;
    info.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    VkImageView view;
    if (vkCreateImageView(device, &info, nullptr, &view) != VK_SUCCESS)
        return VK_NULL_HANDLE;
    return view;
}

bool VulkanComputeEngine::allocateImageMemory(VkImage image,
                                              VkMemoryPropertyFlags props) {
    VkMemoryRequirements req;
    vkGetImageMemoryRequirements(device, image, &req);

    uint32_t type = findMemoryType(req.memoryTypeBits, props);
    if (type == ~0u) return false;

    VkMemoryAllocateInfo alloc{};
    alloc.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    alloc.allocationSize = req.size;
    alloc.memoryTypeIndex = type;

    VkDeviceMemory memory;
    if (vkAllocateMemory(device, &alloc, nullptr, &memory) != VK_SUCCESS)
        return false;

    if (vkBindImageMemory(device, image, memory, 0) != VK_SUCCESS) {
        vkFreeMemory(device, memory, nullptr);
        return false;
    }

    imageMemoryMap.emplace_back(image, memory);
    return true;
}

void VulkanComputeEngine::destroyImage(VkImage image) {
    for (auto it = imageMemoryMap.begin(); it != imageMemoryMap.end(); ++it) {
        if (it->first == image) {
            vkFreeMemory(device, it->second, nullptr);
            imageMemoryMap.erase(it);
            break;
        }
    }
    vkDestroyImage(device, image, nullptr);
}

void VulkanComputeEngine::destroyImageView(VkImageView view) {
    vkDestroyImageView(device, view, nullptr);
}

void VulkanComputeEngine::destroyDescriptorSet(VkDescriptorSet set) {
    vkFreeDescriptorSets(device, descriptorPool, 1, &set);
}

// --------------------------------------------------------------
// Upload Image Data
// --------------------------------------------------------------
bool VulkanComputeEngine::uploadImageData(VkImage image, void* data,
                                          uint32_t w, uint32_t h) {
    VkDeviceSize size = w * h * 4;

    // Staging buffer
    VkBufferCreateInfo bufInfo{};
    bufInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufInfo.size = size;
    bufInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

    VkBuffer staging;
    if (vkCreateBuffer(device, &bufInfo, nullptr, &staging) != VK_SUCCESS)
        return false;

    VkMemoryRequirements req;
    vkGetBufferMemoryRequirements(device, staging, &req);

    uint32_t type = findMemoryType(req.memoryTypeBits,
                                   VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                                   VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    if (type == ~0u) {
        vkDestroyBuffer(device, staging, nullptr);
        return false;
    }

    VkMemoryAllocateInfo alloc{};
    alloc.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    alloc.allocationSize = req.size;
    alloc.memoryTypeIndex = type;

    VkDeviceMemory stagingMem;
    if (vkAllocateMemory(device, &alloc, nullptr, &stagingMem) != VK_SUCCESS) {
        vkDestroyBuffer(device, staging, nullptr);
        return false;
    }
    vkBindBufferMemory(device, staging, stagingMem, 0);

    // Copy data to staging
    void* mapped;
    vkMapMemory(device, stagingMem, 0, size, 0, &mapped);
    memcpy(mapped, data, size);
    vkUnmapMemory(device, stagingMem);

    // Reuse transient command buffer (safe because we wait idle below)
    VkCommandBuffer cmd = transientCmdBuffer;
    vkResetCommandBuffer(cmd, 0);

    VkCommandBufferBeginInfo begin{};
    begin.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmd, &begin);

    VkImageMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image;
    barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    barrier.srcAccessMask = 0;
    barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    vkCmdPipelineBarrier(cmd,
                         VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                         VK_PIPELINE_STAGE_TRANSFER_BIT,
                         0, 0, nullptr, 0, nullptr, 1, &barrier);

    VkBufferImageCopy region{};
    region.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    region.imageExtent = {w, h, 1};
    vkCmdCopyBufferToImage(cmd, staging, image,
                           VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    // Transition to GENERAL for compute access
    barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    vkCmdPipelineBarrier(cmd,
                         VK_PIPELINE_STAGE_TRANSFER_BIT,
                         VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                         0, 0, nullptr, 0, nullptr, 1, &barrier);

    vkEndCommandBuffer(cmd);

    VkSubmitInfo submit{};
    submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &cmd;
    vkQueueSubmit(computeQueue, 1, &submit, VK_NULL_HANDLE);
    vkQueueWaitIdle(computeQueue);

    // Staging buffer no longer needed
    vkDestroyBuffer(device, staging, nullptr);
    vkFreeMemory(device, stagingMem, nullptr);

    return true;
}

// --------------------------------------------------------------
// Descriptor Set
// --------------------------------------------------------------
VkDescriptorSet VulkanComputeEngine::createStorageImageDescriptorSet(
    VkImageView view) {
    VkDescriptorSetAllocateInfo alloc{};
    alloc.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    alloc.descriptorPool = descriptorPool;
    alloc.descriptorSetCount = 1;
    alloc.pSetLayouts = &descriptorSetLayout;

    VkDescriptorSet set;
    if (vkAllocateDescriptorSets(device, &alloc, &set) != VK_SUCCESS)
        return VK_NULL_HANDLE;

    VkDescriptorImageInfo img{};
    img.imageView = view;
    img.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

    VkWriteDescriptorSet write{};
    write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    write.dstSet = set;
    write.dstBinding = 0;
    write.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    write.descriptorCount = 1;
    write.pImageInfo = &img;

    vkUpdateDescriptorSets(device, 1, &write, 0, nullptr);
    return set;
}

// --------------------------------------------------------------
// Dispatch (now with pipeline/layout caching)
// --------------------------------------------------------------
bool VulkanComputeEngine::dispatchCompute(
    VkShaderModule shader,
    const std::vector<VkDescriptorSet>& sets,
    const void* push,
    size_t pushSize,
    uint32_t wx, uint32_t wy, uint32_t wz) {

    if (!device || !shader) return false;

    // Cache key = (shader, pushSize)
    auto key = std::make_pair(shader, (uint32_t)pushSize);

    // Get or create pipeline layout for this push size
    VkPipelineLayout layout = VK_NULL_HANDLE;
    auto layoutIt = layoutCache.find(pushSize);
    if (layoutIt != layoutCache.end()) {
        layout = layoutIt->second;
    } else {
        VkPushConstantRange range{};
        range.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        range.size = static_cast<uint32_t>(pushSize);

        VkPipelineLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        layoutInfo.setLayoutCount = 1;
        layoutInfo.pSetLayouts = &descriptorSetLayout;
        if (pushSize > 0) {
            layoutInfo.pushConstantRangeCount = 1;
            layoutInfo.pPushConstantRanges = &range;
        } // else nullptr

        if (vkCreatePipelineLayout(device, &layoutInfo, nullptr, &layout) != VK_SUCCESS)
            return false;
        layoutCache[pushSize] = layout;
    }

    // Get or create compute pipeline for (shader, pushSize)
    VkPipeline pipeline = VK_NULL_HANDLE;
    auto pipeIt = pipelineCache.find(key);
    if (pipeIt != pipelineCache.end()) {
        pipeline = pipeIt->second;
    } else {
        VkComputePipelineCreateInfo pipeInfo{};
        pipeInfo.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
        pipeInfo.stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        pipeInfo.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
        pipeInfo.stage.module = shader;
        pipeInfo.stage.pName = "main";
        pipeInfo.layout = layout;

        if (vkCreateComputePipelines(device, VK_NULL_HANDLE, 1,
                                     &pipeInfo, nullptr, &pipeline) != VK_SUCCESS)
            return false;
        pipelineCache[key] = pipeline;
    }

    // Reuse transient command buffer
    VkCommandBuffer cmd = transientCmdBuffer;
    vkResetCommandBuffer(cmd, 0);

    VkCommandBufferBeginInfo begin{};
    begin.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmd, &begin);

    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);

    if (!sets.empty()) {
        vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE,
                                layout, 0, (uint32_t)sets.size(),
                                sets.data(), 0, nullptr);
    }

    if (push && pushSize > 0) {
        vkCmdPushConstants(cmd, layout, VK_SHADER_STAGE_COMPUTE_BIT,
                           0, (uint32_t)pushSize, push);
    }

    vkCmdDispatch(cmd, wx, wy, wz);
    vkEndCommandBuffer(cmd);

    VkSubmitInfo submit{};
    submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &cmd;
    vkQueueSubmit(computeQueue, 1, &submit, VK_NULL_HANDLE);
    vkQueueWaitIdle(computeQueue);

    // Pipelines and layouts are cached – do NOT destroy them here!
    return true;
}

// --------------------------------------------------------------
// Readback
// --------------------------------------------------------------
bool VulkanComputeEngine::copyImageToHost(VkImage image, void* dst,
                                          uint32_t w, uint32_t h) {
    VkDeviceSize size = w * h * 4;

    // Staging buffer
    VkBufferCreateInfo bufInfo{};
    bufInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufInfo.size = size;
    bufInfo.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;

    VkBuffer staging;
    if (vkCreateBuffer(device, &bufInfo, nullptr, &staging) != VK_SUCCESS)
        return false;

    VkMemoryRequirements req;
    vkGetBufferMemoryRequirements(device, staging, &req);

    uint32_t type = findMemoryType(req.memoryTypeBits,
                                   VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                                   VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    if (type == ~0u) {
        vkDestroyBuffer(device, staging, nullptr);
        return false;
    }

    VkMemoryAllocateInfo alloc{};
    alloc.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    alloc.allocationSize = req.size;
    alloc.memoryTypeIndex = type;

    VkDeviceMemory stagingMem;
    if (vkAllocateMemory(device, &alloc, nullptr, &stagingMem) != VK_SUCCESS) {
        vkDestroyBuffer(device, staging, nullptr);
        return false;
    }
    vkBindBufferMemory(device, staging, stagingMem, 0);

    // Reuse transient command buffer
    VkCommandBuffer cmd = transientCmdBuffer;
    vkResetCommandBuffer(cmd, 0);

    VkCommandBufferBeginInfo begin{};
    begin.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmd, &begin);

    VkImageMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image;
    barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    barrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    vkCmdPipelineBarrier(cmd,
                         VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                         VK_PIPELINE_STAGE_TRANSFER_BIT,
                         0, 0, nullptr, 0, nullptr, 1, &barrier);

    VkBufferImageCopy region{};
    region.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    region.imageExtent = {w, h, 1};
    vkCmdCopyImageToBuffer(cmd, image,
                           VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                           staging, 1, &region);

    // Transition back to GENERAL
    barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    barrier.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    vkCmdPipelineBarrier(cmd,
                         VK_PIPELINE_STAGE_TRANSFER_BIT,
                         VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                         0, 0, nullptr, 0, nullptr, 1, &barrier);

    vkEndCommandBuffer(cmd);

    VkSubmitInfo submit{};
    submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &cmd;
    vkQueueSubmit(computeQueue, 1, &submit, VK_NULL_HANDLE);
    vkQueueWaitIdle(computeQueue);

    // Copy data from staging
    void* mapped;
    vkMapMemory(device, stagingMem, 0, size, 0, &mapped);
    memcpy(dst, mapped, size);
    vkUnmapMemory(device, stagingMem);

    vkDestroyBuffer(device, staging, nullptr);
    vkFreeMemory(device, stagingMem, nullptr);

    return true;
}