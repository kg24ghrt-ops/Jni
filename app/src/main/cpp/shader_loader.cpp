#include "shader_loader.h"
#include <android/asset_manager.h>
#include <cstring>

static AAssetManager* g_assetManager = nullptr;
static VkDevice g_device = VK_NULL_HANDLE;

void initShaderLoader(AAssetManager* mgr) {
    g_assetManager = mgr;
}

void setVulkanDevice(VkDevice device) {
    g_device = device;
}

static VkShaderModule loadShaderModuleFromAsset(const char* assetPath) {
    if (!g_assetManager || !g_device) return VK_NULL_HANDLE;

    AAsset* asset = AAssetManager_open(g_assetManager, assetPath, AASSET_MODE_BUFFER);
    if (!asset) return VK_NULL_HANDLE;

    size_t size = AAsset_getLength(asset);
    const uint32_t* data = (const uint32_t*)AAsset_getBuffer(asset);
    if (!data) {
        AAsset_close(asset);
        return VK_NULL_HANDLE;
    }

    VkShaderModuleCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    createInfo.codeSize = size;
    createInfo.pCode = data;

    VkShaderModule module = VK_NULL_HANDLE;
    VkResult result = vkCreateShaderModule(g_device, &createInfo, nullptr, &module);
    AAsset_close(asset);

    return (result == VK_SUCCESS) ? module : VK_NULL_HANDLE;
}

// ---- Cached shader modules ----

VkShaderModule getPaperShaderModule() {
    static VkShaderModule module = VK_NULL_HANDLE;
    if (!module) module = loadShaderModuleFromAsset("shaders/paper_generation.spv");
    return module;
}

VkShaderModule getCapillaryShaderModule() {
    static VkShaderModule module = VK_NULL_HANDLE;
    if (!module) module = loadShaderModuleFromAsset("shaders/capillary_map.spv");
    return module;
}

VkShaderModule getPhysicsShaderModule() {
    static VkShaderModule module = VK_NULL_HANDLE;
    if (!module) module = loadShaderModuleFromAsset("shaders/ink_physics.spv");
    return module;
}

VkShaderModule getCompositeShaderModule() {
    static VkShaderModule module = VK_NULL_HANDLE;
    if (!module) module = loadShaderModuleFromAsset("shaders/ink_composite.spv");
    return module;
}