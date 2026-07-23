#pragma once

#include <vulkan/vulkan.h>
#include <android/asset_manager.h>
#include <vector>
#include <functional>

class VulkanComputeEngine {
public:
    static VulkanComputeEngine& getInstance();

    bool initialize(ANativeWindow* window);
    void shutdown();

    VkDevice getDevice() const { return device; }
    VkQueue getComputeQueue() const { return computeQueue; }
    VkCommandPool getCommandPool() const { return commandPool; }

    // Create images and image views
    VkImage createImage(uint32_t width, uint32_t height, VkFormat format, VkImageUsageFlags usage);
    VkImageView createImageView(VkImage image, VkFormat format);
    void destroyImage(VkImage image);
    void destroyImageView(VkImageView imageView);

    // Allocate memory for an image and bind it
    bool allocateImageMemory(VkImage image, VkMemoryPropertyFlags properties);

    // Dispatch a compute shader with push constants, descriptor sets, and workgroup dimensions
    bool dispatchCompute(
        VkShaderModule shaderModule,
        const std::vector<VkDescriptorSet>& descriptorSets,
        const void* pushConstants,
        size_t pushConstantsSize,
        uint32_t workX, uint32_t workY, uint32_t workZ
    );

    // Create a descriptor set for a storage image (output)
    VkDescriptorSet createStorageImageDescriptorSet(VkImageView imageView);

    // Create a descriptor set for combined image sampler (input texture)
    VkDescriptorSet createCombinedImageDescriptorSet(VkImageView imageView, VkSampler sampler);

    // Copy an image to host memory (readback)
    bool copyImageToHost(VkImage image, void* dstData, uint32_t width, uint32_t height, VkFormat format);

    // Create a sampler with default settings
    VkSampler createSampler();

private:
    VulkanComputeEngine() = default;
    ~VulkanComputeEngine() = default;

    bool createInstance();
    bool pickPhysicalDevice();
    bool createDevice();
    bool createCommandPool();
    bool createDescriptorPool();
    bool createDescriptorSetLayout();

    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue computeQueue = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
    VkDescriptorSetLayout descriptorSetLayout = VK_NULL_HANDLE;
    VkSampler defaultSampler = VK_NULL_HANDLE;

    uint32_t queueFamilyIndex = 0;
    bool initialized = false;
};