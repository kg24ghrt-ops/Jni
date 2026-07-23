#pragma once

#include <vulkan/vulkan.h>
#include <vector>
#include <unordered_map>

/**
 * VulkanComputeEngine – pure compute engine (no presentation).
 * Manages instance, device, queues, memory, images, descriptor sets,
 * and dispatch for compute shaders.
 */
class VulkanComputeEngine {
public:
    static VulkanComputeEngine& getInstance();

    // Initialize Vulkan (no window needed)
    bool initialize();
    void shutdown();

    // Getters
    VkDevice getDevice() const { return device; }
    VkQueue getComputeQueue() const { return computeQueue; }
    VkCommandPool getCommandPool() const { return commandPool; }
    VkDescriptorPool getDescriptorPool() const { return descriptorPool; }
    VkDescriptorSetLayout getDescriptorSetLayout() const { return descriptorSetLayout; }
    VkSampler getDefaultSampler() const { return defaultSampler; }

    // Image creation / destruction
    VkImage createImage(uint32_t width, uint32_t height, VkFormat format, VkImageUsageFlags usage);
    VkImageView createImageView(VkImage image, VkFormat format);
    bool allocateImageMemory(VkImage image, VkMemoryPropertyFlags properties);
    void destroyImage(VkImage image);
    void destroyImageView(VkImageView imageView);
    void destroyDescriptorSet(VkDescriptorSet descriptorSet);

    // Upload pixel data to image (via staging buffer)
    bool uploadImageData(VkImage image, void* data, uint32_t width, uint32_t height, VkFormat format);

    // Descriptor set creation
    VkDescriptorSet createStorageImageDescriptorSet(VkImageView imageView);
    VkDescriptorSet createCombinedImageDescriptorSet(VkImageView imageView, VkSampler sampler = VK_NULL_HANDLE);

    // Dispatch a compute shader
    bool dispatchCompute(
        VkShaderModule shaderModule,
        const std::vector<VkDescriptorSet>& descriptorSets,
        const void* pushConstants,
        size_t pushConstantsSize,
        uint32_t workX, uint32_t workY, uint32_t workZ
    );

    // Copy image back to host memory (readback)
    bool copyImageToHost(VkImage image, void* dstData, uint32_t width, uint32_t height, VkFormat format);

private:
    VulkanComputeEngine() = default;
    ~VulkanComputeEngine() = default;

    // Internal helpers
    bool createInstance();
    bool pickPhysicalDevice();
    bool createDevice();
    bool createCommandPool();
    bool createDescriptorPool();
    bool createDescriptorSetLayout();
    bool createSampler();

    // Single‑time command buffer helpers (for upload/copy)
    VkCommandBuffer beginSingleTimeCommands();
    void endSingleTimeCommands(VkCommandBuffer commandBuffer);

    // Vulkan handles
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue computeQueue = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
    VkDescriptorSetLayout descriptorSetLayout = VK_NULL_HANDLE;
    VkSampler defaultSampler = VK_NULL_HANDLE;

    // Map image -> device memory for automatic cleanup
    std::unordered_map<VkImage, VkDeviceMemory> imageMemoryMap;

    uint32_t queueFamilyIndex = 0;
    bool initialized = false;
};