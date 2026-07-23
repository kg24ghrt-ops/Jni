#pragma once

#include <vulkan/vulkan.h>
#include <android/native_window.h>
#include <android/asset_manager.h>
#include <vector>
#include <unordered_map>

class VulkanComputeEngine {
public:
    static VulkanComputeEngine& getInstance();

    bool initialize(ANativeWindow* window);
    void shutdown();

    // Getters
    VkDevice getDevice() const { return device; }
    VkQueue getComputeQueue() const { return computeQueue; }
    VkCommandPool getCommandPool() const { return commandPool; }
    VkDescriptorPool getDescriptorPool() const { return descriptorPool; }
    VkDescriptorSetLayout getDescriptorSetLayout() const { return descriptorSetLayout; }
    VkSampler getDefaultSampler() const { return defaultSampler; }

    // Image creation and management
    VkImage createImage(uint32_t width, uint32_t height, VkFormat format, VkImageUsageFlags usage);
    VkImageView createImageView(VkImage image, VkFormat format);
    bool allocateImageMemory(VkImage image, VkMemoryPropertyFlags properties);
    void destroyImage(VkImage image);
    void destroyImageView(VkImageView imageView);
    void destroyDescriptorSet(VkDescriptorSet descriptorSet);

    // Upload data to image (staging buffer)
    bool uploadImageData(VkImage image, void* data, uint32_t width, uint32_t height, VkFormat format);

    // Descriptor set creation
    VkDescriptorSet createStorageImageDescriptorSet(VkImageView imageView);
    VkDescriptorSet createCombinedImageDescriptorSet(VkImageView imageView, VkSampler sampler = VK_NULL_HANDLE);

    // Dispatch
    bool dispatchCompute(
        VkShaderModule shaderModule,
        const std::vector<VkDescriptorSet>& descriptorSets,
        const void* pushConstants,
        size_t pushConstantsSize,
        uint32_t workX, uint32_t workY, uint32_t workZ
    );

    // Copy image to host (readback)
    bool copyImageToHost(VkImage image, void* dstData, uint32_t width, uint32_t height, VkFormat format);

private:
    VulkanComputeEngine() = default;
    ~VulkanComputeEngine() = default;

    bool createInstance();
    bool pickPhysicalDevice();
    bool createDevice();
    bool createCommandPool();
    bool createDescriptorPool();
    bool createDescriptorSetLayout();
    bool createSampler();

    // Single-time command buffer helpers
    VkCommandBuffer beginSingleTimeCommands();
    void endSingleTimeCommands(VkCommandBuffer commandBuffer);

    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue computeQueue = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
    VkDescriptorSetLayout descriptorSetLayout = VK_NULL_HANDLE;
    VkSampler defaultSampler = VK_NULL_HANDLE;

    // Map to track image memory for destruction
    std::unordered_map<VkImage, VkDeviceMemory> imageMemoryMap;

    uint32_t queueFamilyIndex = 0;
    bool initialized = false;
};