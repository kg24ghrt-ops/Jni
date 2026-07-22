#pragma once

#include <vulkan/vulkan.h>
#include <android/asset_manager.h>

// Call this once from Java with the AssetManager
void initShaderLoader(AAssetManager* mgr);

// Call this once with the Vulkan device
void setVulkanDevice(VkDevice device);

// Get cached shader modules (load from assets/shaders/)
VkShaderModule getCapillaryShaderModule();
VkShaderModule getPhysicsShaderModule();
VkShaderModule getCompositeShaderModule();
VkShaderModule getPaperShaderModule();