#pragma once

#include <vulkan/vulkan.h>
#include <android/asset_manager.h>

// Call this once from Java with the AssetManager
void initShaderLoader(AAssetManager* mgr);

// Call this once with the Vulkan device
void setVulkanDevice(VkDevice device);

// Get cached shader modules (load from assets/shaders/)
// These names match what native-lib.cpp and ink_engine.cpp expect
VkShaderModule getPaperProgram();
VkShaderModule getCapillaryProgram();
VkShaderModule getPhysicsProgram();
VkShaderModule getCompositeProgram();