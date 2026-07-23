#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <ctime>
#include <algorithm>
#include <unordered_map>
#include "shader_loader.h"
#include "vulkan_compute_engine.h"

// ---------- Constants ----------
static const float SIMULATION_SCALE = 0.5f; // 50% resolution

// ---------- Vulkan Resources ----------
static VulkanComputeEngine& engine = VulkanComputeEngine::getInstance();

static VkImage paperImage = VK_NULL_HANDLE;
static VkImageView paperView = VK_NULL_HANDLE;
static VkImage capillaryImage = VK_NULL_HANDLE;
static VkImageView capillaryView = VK_NULL_HANDLE;
static VkImage inkImageA = VK_NULL_HANDLE;  // ping
static VkImageView inkViewA = VK_NULL_HANDLE;
static VkImage inkImageB = VK_NULL_HANDLE;  // pong
static VkImageView inkViewB = VK_NULL_HANDLE;
static VkImage outputImage = VK_NULL_HANDLE;
static VkImageView outputView = VK_NULL_HANDLE;

static bool useTextureA = true;
static int fullWidth = 0;
static int fullHeight = 0;
static int simWidth = 0;
static int simHeight = 0;

// Descriptor sets (cached)
static VkDescriptorSet capillaryDescSet = VK_NULL_HANDLE;
static VkDescriptorSet physicsDescSet = VK_NULL_HANDLE;
static VkDescriptorSet compositeDescSet = VK_NULL_HANDLE;

// Dirty rectangle tracking
struct DirtyRect {
    int x, y, w, h;
    bool valid;
} dirtyRect = {0, 0, 0, 0, false};

// ---------- Helper prototypes ----------
bool ensureResources();
void generateCapillaryMap();
void updateDirtyRect(int offsetX, int offsetY, int stampW, int stampH);
void runPhysicsSimulation(VkImage stampImage, VkImageView stampView);
void runComposite();
void readBackToBitmap(void* paperPixels);
VkImage uploadStampTexture(void* pixels, int width, int height, VkImageView& outView);

// ---------- JNI Entry Point ----------
extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_simulateInk(
        JNIEnv *env,
        jobject /* this */,
        jobject paperBitmap,
        jobject inkBitmap,
        jint offsetX,
        jint offsetY) {

    // 1. Lock bitmaps
    AndroidBitmapInfo paperInfo, inkInfo;
    void *paperPixels, *inkPixels;
    if (AndroidBitmap_getInfo(env, paperBitmap, &paperInfo) < 0) return;
    if (AndroidBitmap_getInfo(env, inkBitmap, &inkInfo) < 0) return;
    if (AndroidBitmap_lockPixels(env, paperBitmap, &paperPixels) < 0) return;
    if (AndroidBitmap_lockPixels(env, inkBitmap, &inkPixels) < 0) {
        AndroidBitmap_unlockPixels(env, paperBitmap);
        return;
    }

    // 2. Engine must be already initialized by native-lib.cpp.
    //    We only need the device set in shader loader.
    setVulkanDevice(engine.getDevice());

    // 3. Store dimensions and create resources if first time or size changed
    fullWidth = paperInfo.width;
    fullHeight = paperInfo.height;
    simWidth = (int)(fullWidth * SIMULATION_SCALE);
    simHeight = (int)(fullHeight * SIMULATION_SCALE);

    if (!ensureResources()) {
        AndroidBitmap_unlockPixels(env, paperBitmap);
        AndroidBitmap_unlockPixels(env, inkBitmap);
        return;
    }

    // 4. Upload paper bitmap to Vulkan image (if changed)
    if (!engine.uploadImageData(paperImage, paperPixels, fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM)) {
        AndroidBitmap_unlockPixels(env, paperBitmap);
        AndroidBitmap_unlockPixels(env, inkBitmap);
        return;
    }

    // 5. Upload stamp bitmap to a temporary image
    VkImageView stampView = VK_NULL_HANDLE;
    VkImage stampImage = uploadStampTexture(inkPixels, inkInfo.width, inkInfo.height, stampView);
    if (!stampImage) {
        AndroidBitmap_unlockPixels(env, paperBitmap);
        AndroidBitmap_unlockPixels(env, inkBitmap);
        return;
    }

    // 6. Update dirty rectangle
    updateDirtyRect(offsetX, offsetY, inkInfo.width, inkInfo.height);

    // 7. Run physics simulation (at lower resolution)
    runPhysicsSimulation(stampImage, stampView);

    // 8. Composite at full resolution
    runComposite();

    // 9. Read back to bitmap
    readBackToBitmap(paperPixels);

    // 10. Cleanup stamp
    engine.destroyImage(stampImage);
    engine.destroyImageView(stampView);

    AndroidBitmap_unlockPixels(env, paperBitmap);
    AndroidBitmap_unlockPixels(env, inkBitmap);
}

// ---------- Helper Functions ----------

bool ensureResources() {
    // Create paper image (full resolution)
    if (paperImage == VK_NULL_HANDLE) {
        paperImage = engine.createImage(fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                        VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        if (!paperImage) return false;
        if (!engine.allocateImageMemory(paperImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) return false;
        paperView = engine.createImageView(paperImage, VK_FORMAT_R8G8B8A8_UNORM);
        if (!paperView) return false;

        // Create capillary map (simulation resolution, RG32F)
        capillaryImage = engine.createImage(simWidth, simHeight, VK_FORMAT_R32G32_SFLOAT,
                                            VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
        if (!capillaryImage) return false;
        if (!engine.allocateImageMemory(capillaryImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) return false;
        capillaryView = engine.createImageView(capillaryImage, VK_FORMAT_R32G32_SFLOAT);
        if (!capillaryView) return false;

        // Generate capillary map
        generateCapillaryMap();

        // Create ink textures (simulation resolution, RGBA8) – ping and pong
        inkImageA = engine.createImage(simWidth, simHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                       VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
        if (!inkImageA) return false;
        if (!engine.allocateImageMemory(inkImageA, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) return false;
        inkViewA = engine.createImageView(inkImageA, VK_FORMAT_R8G8B8A8_UNORM);
        if (!inkViewA) return false;

        inkImageB = engine.createImage(simWidth, simHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                       VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
        if (!inkImageB) return false;
        if (!engine.allocateImageMemory(inkImageB, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) return false;
        inkViewB = engine.createImageView(inkImageB, VK_FORMAT_R8G8B8A8_UNORM);
        if (!inkViewB) return false;

        // Clear ink layers to transparent (0,0,0,0)
        uint32_t* clearData = new uint32_t[simWidth * simHeight];
        memset(clearData, 0, simWidth * simHeight * sizeof(uint32_t));
        engine.uploadImageData(inkImageA, clearData, simWidth, simHeight, VK_FORMAT_R8G8B8A8_UNORM);
        engine.uploadImageData(inkImageB, clearData, simWidth, simHeight, VK_FORMAT_R8G8B8A8_UNORM);
        delete[] clearData;

        // Create output image (full resolution)
        outputImage = engine.createImage(fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                         VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
        if (!outputImage) return false;
        if (!engine.allocateImageMemory(outputImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) return false;
        outputView = engine.createImageView(outputImage, VK_FORMAT_R8G8B8A8_UNORM);
        if (!outputView) return false;
    }

    return true;
}

void generateCapillaryMap() {
    // Get the capillary shader module
    VkShaderModule shaderModule = getCapillaryShaderModule();
    if (!shaderModule) return;

    // Create descriptor set for capillary map (storage image)
    capillaryDescSet = engine.createStorageImageDescriptorSet(capillaryView);
    if (!capillaryDescSet) return;

    // Push constants for capillary generation
    struct CapillaryPush {
        float mapSize[2];
        uint32_t seed;
    } pc;
    pc.mapSize[0] = simWidth;
    pc.mapSize[1] = simHeight;
    pc.seed = (uint32_t)time(nullptr);

    // Dispatch
    engine.dispatchCompute(shaderModule, {capillaryDescSet}, &pc, sizeof(pc),
                          (simWidth + 15) / 16, (simHeight + 15) / 16, 1);
}

void updateDirtyRect(int offsetX, int offsetY, int stampW, int stampH) {
    // Convert to simulation coordinates
    int simX = (int)(offsetX * SIMULATION_SCALE);
    int simY = (int)(offsetY * SIMULATION_SCALE);
    int simW = (int)(stampW * SIMULATION_SCALE) + 4;
    int simH = (int)(stampH * SIMULATION_SCALE) + 4;

    if (!dirtyRect.valid) {
        dirtyRect = {simX, simY, simW, simH, true};
    } else {
        int x1 = std::min(dirtyRect.x, simX);
        int y1 = std::min(dirtyRect.y, simY);
        int x2 = std::max(dirtyRect.x + dirtyRect.w, simX + simW);
        int y2 = std::max(dirtyRect.y + dirtyRect.h, simY + simH);
        dirtyRect = {x1, y1, x2 - x1, y2 - y1, true};
    }
    dirtyRect.x = std::max(0, dirtyRect.x);
    dirtyRect.y = std::max(0, dirtyRect.y);
    dirtyRect.w = std::min(simWidth - dirtyRect.x, dirtyRect.w);
    dirtyRect.h = std::min(simHeight - dirtyRect.y, dirtyRect.h);
}

void runPhysicsSimulation(VkImage stampImage, VkImageView stampView) {
    // Get physics shader module
    VkShaderModule shaderModule = getPhysicsShaderModule();
    if (!shaderModule) return;

    // Choose input/output ink images
    VkImageView inputView = useTextureA ? inkViewA : inkViewB;
    VkImageView outputView = useTextureA ? inkViewB : inkViewA;

    // Create descriptor set for physics:
    // Binding 0: stamp sampler (combined image sampler)
    // Binding 1: capillary sampler (combined image sampler)
    // Binding 2: input ink (storage image, readonly)
    // Binding 3: output ink (storage image, writeonly)
    // We'll allocate from the engine's descriptor pool.
    VkDescriptorSet descSet;
    VkDescriptorSetAllocateInfo alloc = {};
    alloc.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    alloc.descriptorPool = engine.getDescriptorPool();
    alloc.descriptorSetCount = 1;
    VkDescriptorSetLayout layout = engine.getDescriptorSetLayout(); // local copy
    alloc.pSetLayouts = &layout;
    if (vkAllocateDescriptorSets(engine.getDevice(), &alloc, &descSet) != VK_SUCCESS) {
        return;
    }

    // Prepare writes
    VkDescriptorImageInfo stampInfo{};
    stampInfo.imageView = stampView;
    stampInfo.sampler = engine.getDefaultSampler();
    stampInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    VkDescriptorImageInfo capillaryInfo{};
    capillaryInfo.imageView = capillaryView;
    capillaryInfo.sampler = engine.getDefaultSampler();
    capillaryInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    VkDescriptorImageInfo inputInfo{};
    inputInfo.imageView = inputView;
    inputInfo.imageLayout = VK_IMAGE_LAYOUT_GENERAL; // storage image

    VkDescriptorImageInfo outputInfo{};
    outputInfo.imageView = outputView;
    outputInfo.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

    std::vector<VkWriteDescriptorSet> writes(4);
    // Binding 0: sampler
    writes[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writes[0].dstSet = descSet;
    writes[0].dstBinding = 0;
    writes[0].descriptorCount = 1;
    writes[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    writes[0].pImageInfo = &stampInfo;

    writes[1].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writes[1].dstSet = descSet;
    writes[1].dstBinding = 1;
    writes[1].descriptorCount = 1;
    writes[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    writes[1].pImageInfo = &capillaryInfo;

    writes[2].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writes[2].dstSet = descSet;
    writes[2].dstBinding = 2;
    writes[2].descriptorCount = 1;
    writes[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    writes[2].pImageInfo = &inputInfo;

    writes[3].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writes[3].dstSet = descSet;
    writes[3].dstBinding = 3;
    writes[3].descriptorCount = 1;
    writes[3].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    writes[3].pImageInfo = &outputInfo;

    vkUpdateDescriptorSets(engine.getDevice(), writes.size(), writes.data(), 0, nullptr);

    // Push constants for physics
    struct PhysicsPush {
        float dirtyRect[4];
        float simSize[2];
        float fullSize[2];
        float stampSize[2];
        float stampOffset[2];
        float diffusionRate;
        float capillaryStrength;
        float absorption;
        float evaporation;
        float timeStep;
        uint32_t iteration;
    } pc;
    pc.dirtyRect[0] = dirtyRect.x;
    pc.dirtyRect[1] = dirtyRect.y;
    pc.dirtyRect[2] = dirtyRect.w;
    pc.dirtyRect[3] = dirtyRect.h;
    pc.simSize[0] = simWidth;
    pc.simSize[1] = simHeight;
    pc.fullSize[0] = fullWidth;
    pc.fullSize[1] = fullHeight;
    pc.stampSize[0] = (float)(fullWidth * SIMULATION_SCALE);
    pc.stampSize[1] = (float)(fullHeight * SIMULATION_SCALE);
    pc.stampOffset[0] = 0.0f;
    pc.stampOffset[1] = 0.0f;
    pc.diffusionRate = 0.08f;
    pc.capillaryStrength = 0.3f;
    pc.absorption = 0.02f;
    pc.evaporation = 0.001f;
    pc.timeStep = 0.05f;
    pc.iteration = 0; // first pass

    // Dispatch
    engine.dispatchCompute(shaderModule, {descSet}, &pc, sizeof(pc),
                          (simWidth + 15) / 16, (simHeight + 15) / 16, 1);

    // Swap for next iteration
    useTextureA = !useTextureA;

    // Clean up previous physics descriptor set if any
    if (physicsDescSet != VK_NULL_HANDLE) {
        vkFreeDescriptorSets(engine.getDevice(), engine.getDescriptorPool(), 1, &physicsDescSet);
    }
    physicsDescSet = descSet; // store for later reuse
}

void runComposite() {
    VkShaderModule shaderModule = getCompositeShaderModule();
    if (!shaderModule) return;

    // Choose current ink texture (the one that was just written)
    VkImageView inkView = useTextureA ? inkViewB : inkViewA; // because we swapped after physics

    // Create descriptor set for composite:
    // binding 0: paper sampler
    // binding 1: ink sampler
    // binding 2: output storage image
    VkDescriptorSet descSet;
    VkDescriptorSetAllocateInfo alloc = {};
    alloc.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    alloc.descriptorPool = engine.getDescriptorPool();
    alloc.descriptorSetCount = 1;
    VkDescriptorSetLayout layout = engine.getDescriptorSetLayout(); // local copy
    alloc.pSetLayouts = &layout;
    if (vkAllocateDescriptorSets(engine.getDevice(), &alloc, &descSet) != VK_SUCCESS) {
        return;
    }

    VkDescriptorImageInfo paperInfo{};
    paperInfo.imageView = paperView;
    paperInfo.sampler = engine.getDefaultSampler();
    paperInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    VkDescriptorImageInfo inkInfo{};
    inkInfo.imageView = inkView;
    inkInfo.sampler = engine.getDefaultSampler();
    inkInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    VkDescriptorImageInfo outputInfo{};
    outputInfo.imageView = outputView;
    outputInfo.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

    std::vector<VkWriteDescriptorSet> writes(3);
    writes[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writes[0].dstSet = descSet;
    writes[0].dstBinding = 0;
    writes[0].descriptorCount = 1;
    writes[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    writes[0].pImageInfo = &paperInfo;

    writes[1].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writes[1].dstSet = descSet;
    writes[1].dstBinding = 1;
    writes[1].descriptorCount = 1;
    writes[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    writes[1].pImageInfo = &inkInfo;

    writes[2].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writes[2].dstSet = descSet;
    writes[2].dstBinding = 2;
    writes[2].descriptorCount = 1;
    writes[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    writes[2].pImageInfo = &outputInfo;

    vkUpdateDescriptorSets(engine.getDevice(), writes.size(), writes.data(), 0, nullptr);

    // Push constants for composite
    struct CompositePush {
        float fullSize[2];
        float simSize[2];
    } pc;
    pc.fullSize[0] = fullWidth;
    pc.fullSize[1] = fullHeight;
    pc.simSize[0] = simWidth;
    pc.simSize[1] = simHeight;

    engine.dispatchCompute(shaderModule, {descSet}, &pc, sizeof(pc),
                          (fullWidth + 15) / 16, (fullHeight + 15) / 16, 1);

    // Cache descriptor set
    if (compositeDescSet != VK_NULL_HANDLE) {
        vkFreeDescriptorSets(engine.getDevice(), engine.getDescriptorPool(), 1, &compositeDescSet);
    }
    compositeDescSet = descSet;
}

void readBackToBitmap(void* paperPixels) {
    engine.copyImageToHost(outputImage, paperPixels, fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM);
}

VkImage uploadStampTexture(void* pixels, int width, int height, VkImageView& outView) {
    VkImage image = engine.createImage(width, height, VK_FORMAT_R8G8B8A8_UNORM,
                                       VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
    if (!image) return VK_NULL_HANDLE;
    if (!engine.allocateImageMemory(image, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
        engine.destroyImage(image);
        return VK_NULL_HANDLE;
    }

    if (!engine.uploadImageData(image, pixels, width, height, VK_FORMAT_R8G8B8A8_UNORM)) {
        engine.destroyImage(image);
        return VK_NULL_HANDLE;
    }

    outView = engine.createImageView(image, VK_FORMAT_R8G8B8A8_UNORM);
    if (!outView) {
        engine.destroyImage(image);
        return VK_NULL_HANDLE;
    }
    return image;
}