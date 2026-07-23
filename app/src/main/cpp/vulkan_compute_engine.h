#pragma once

#include <vulkan/vulkan.h>
#include <vector>
#include <utility>
#include <unordered_map>

class VulkanComputeEngine {
public:
    static VulkanComputeEngine& getInstance();

    bool initialize();
    void shutdown();

    // Getters
    VkDevice getDevice() const { return device; }
    VkQueue getComputeQueue() const { return computeQueue; }
    VkCommandPool getCommandPool() const { return commandPool; }
    VkDescriptorPool getDescriptorPool() const { return descriptorPool; }
    VkDescriptorSetLayout getDescriptorSetLayout() const { return descriptorSetLayout; }

    // Image management
    VkImage createImage(uint32_t width, uint32_t height, VkFormat format, VkImageUsageFlags usage);
    VkImageView createImageView(VkImage image, VkFormat format);
    bool allocateImageMemory(VkImage image, VkMemoryPropertyFlags properties);
    void destroyImage(VkImage image);
    void destroyImageView(VkImageView imageView);
    void destroyDescriptorSet(VkDescriptorSet descriptorSet);

    // Upload pixel data to image
    bool uploadImageData(VkImage image, void* data, uint32_t width, uint32_t height);

    // Create a descriptor set for a storage image (binding 0)
    VkDescriptorSet createStorageImageDescriptorSet(VkImageView imageView);

    // Dispatch compute shader with caching
    bool dispatchCompute(
        VkShaderModule shaderModule,
        const std::vector<VkDescriptorSet>& descriptorSets,
        const void* pushConstants,
        size_t pushConstantsSize,
        uint32_t workX, uint32_t workY, uint32_t workZ
    );

    // Readback image to host
    bool copyImageToHost(VkImage image, void* dstData, uint32_t width, uint32_t height);

private:
    VulkanComputeEngine() = default;
    ~VulkanComputeEngine() = default;

    bool createInstance();
    bool pickPhysicalDevice();
    bool createDevice();
    bool createCommandPool();
    bool createDescriptorPool();
    bool createDescriptorSetLayout();

    uint32_t findMemoryType(uint32_t typeBits, VkMemoryPropertyFlags properties) const;

    // Vulkan handles
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue computeQueue = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
    VkDescriptorSetLayout descriptorSetLayout = VK_NULL_HANDLE;

    // Reusable command buffer
    VkCommandBuffer transientCmdBuffer = VK_NULL_HANDLE;

    // Custom hash for std::pair<VkShaderModule, uint32_t>
    struct ShaderPushPairHash {
        size_t operator()(const std::pair<VkShaderModule, uint32_t>& p) const noexcept {
            return std::hash<VkShaderModule>()(p.first) ^
                   (std::hash<uint32_t>()(p.second) << 1);
        }
    };

    // Caches for pipelines and layouts
    std::unordered_map<std::pair<VkShaderModule, uint32_t>, VkPipeline, ShaderPushPairHash> pipelineCache;
    std::unordered_map<size_t, VkPipelineLayout> layoutCache;

    // Track image memory for cleanup
    std::vector<std::pair<VkImage, VkDeviceMemory>> imageMemoryMap;

    uint32_t queueFamilyIndex = 0;
    bool initialized = false;
};

// (No longer need the std::hash specialization here – it is replaced by the custom functor above.)