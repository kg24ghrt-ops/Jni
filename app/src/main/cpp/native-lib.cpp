#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <ctime>
#include <memory>
#include "shader_loader.h"
#include "vulkan_compute_engine.h"

// --------------------------------------------------------------
// Small RAII helpers for clean Vulkan resource handling
// --------------------------------------------------------------
struct ImageDeleter {
    void operator()(VkImage img) const { VulkanComputeEngine::getInstance().destroyImage(img); }
};
using UniqueImage = std::unique_ptr<std::remove_pointer_t<VkImage>, ImageDeleter>;

struct ImageViewDeleter {
    void operator()(VkImageView view) const { VulkanComputeEngine::getInstance().destroyImageView(view); }
};
using UniqueImageView = std::unique_ptr<std::remove_pointer_t<VkImageView>, ImageViewDeleter>;

struct DescriptorSetDeleter {
    void operator()(VkDescriptorSet set) const { VulkanComputeEngine::getInstance().destroyDescriptorSet(set); }
};
using UniqueDescriptorSet = std::unique_ptr<std::remove_pointer_t<VkDescriptorSet>, DescriptorSetDeleter>;

// RAII wrapper for AndroidBitmap_lockPixels / unlockPixels
class ScopedBitmapLock {
public:
    ScopedBitmapLock(JNIEnv* env, jobject bitmap, void*& outPixels)
        : env_(env), bitmap_(bitmap), pixels_(nullptr) {
        if (AndroidBitmap_lockPixels(env_, bitmap_, &pixels_) != 0) {
            pixels_ = nullptr;
        }
        outPixels = pixels_;
    }
    ~ScopedBitmapLock() {
        if (pixels_) {
            AndroidBitmap_unlockPixels(env_, bitmap_);
        }
    }
    bool isValid() const { return pixels_ != nullptr; }
    void* getPixels() const { return pixels_; }

private:
    JNIEnv* env_;
    jobject bitmap_;
    void* pixels_;
};

// --------------------------------------------------------------
// JNI entry point for paper generation (Vulkan compute)
// --------------------------------------------------------------
extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_renderPaper(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap,
        jobject surface,   // ignored – kept for compatibility
        jint width,
        jint height,
        jint lineSpacing,
        jint marginLeft,
        jint lineColor,
        jint marginColor,
        jint lineThickness) {

    // 1. Validate and lock bitmap
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;

    void* pixels = nullptr;
    ScopedBitmapLock lock(env, bitmap, pixels);
    if (!lock.isValid()) return;

    // 2. Initialize Vulkan engine
    auto& engine = VulkanComputeEngine::getInstance();
    if (!engine.initialize()) return;

    // 3. Set Vulkan device for shader loader
    setVulkanDevice(engine.getDevice());
    VkShaderModule shaderModule = getPaperShaderModule();
    if (!shaderModule) return;

    // 4. Create output image + view (RAII wrappers)
    VkImage rawImage = engine.createImage(
        width, height, VK_FORMAT_R8G8B8A8_UNORM,
        VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
    if (!rawImage) return;
    UniqueImage outputImage(rawImage);

    if (!engine.allocateImageMemory(rawImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))
        return;

    VkImageView rawView = engine.createImageView(rawImage, VK_FORMAT_R8G8B8A8_UNORM);
    if (!rawView) return;
    UniqueImageView outputView(rawView);

    // 5. Descriptor set
    VkDescriptorSet rawSet = engine.createStorageImageDescriptorSet(rawView);
    if (!rawSet) return;
    UniqueDescriptorSet descSet(rawSet);

    // 6. Push constants
    struct PaperPushConstants {
        float imageSize[2];
        float lineSpacing;
        float marginLeft;
        float lineThickness;
        uint32_t seed;
        float lineColor[3];
        float marginColor[3];
        float baseColor[3];
        float fiberScale;
        float grainStrength;
        float bleedAmount;
        float waveAmplitude;
        float inclineSlope;
        float spotThreshold;
        float vignetteStrength;
        float paperAge;
        float lineWarp;
    } pc{};

    pc.imageSize[0] = static_cast<float>(width);
    pc.imageSize[1] = static_cast<float>(height);
    pc.lineSpacing = static_cast<float>(lineSpacing);
    pc.marginLeft = static_cast<float>(marginLeft);
    pc.lineThickness = static_cast<float>(lineThickness);
    pc.seed = static_cast<uint32_t>(time(nullptr)) ^ static_cast<uint32_t>(clock());

    // Helper lambda – unpacks ARGB to float3
    auto unpackColor = [](int color, float* out) {
        out[0] = ((color >> 16) & 0xFF) / 255.0f;
        out[1] = ((color >> 8)  & 0xFF) / 255.0f;
        out[2] = ( color        & 0xFF) / 255.0f;
    };
    unpackColor(lineColor,   pc.lineColor);
    unpackColor(marginColor, pc.marginColor);

    pc.baseColor[0] = 0.99f; pc.baseColor[1] = 0.99f; pc.baseColor[2] = 0.98f;

    // Tuning defaults
    pc.fiberScale       = 0.2f;
    pc.grainStrength    = 0.3f;
    pc.bleedAmount      = 0.25f;
    pc.waveAmplitude    = 0.8f;
    pc.inclineSlope     = 0.002f;
    pc.spotThreshold    = 0.97f;
    pc.vignetteStrength = 0.3f;
    pc.paperAge         = 0.5f;
    pc.lineWarp         = 0.3f;

    // 7. Dispatch
    uint32_t workX = (width  + 15) / 16;
    uint32_t workY = (height + 15) / 16;
    bool dispatched = engine.dispatchCompute(
        shaderModule, {rawSet}, &pc, sizeof(pc), workX, workY, 1);
    if (!dispatched) return;   // RAII cleans up everything

    // 8. Copy result back to bitmap (fixed signature)
    bool copied = engine.copyImageToHost(rawImage, pixels, width, height);
    if (!copied) {
        // Fill bitmap with bright red to signal error
        memset(pixels, 0xFF, width * height * 4);
    }
    // RAII unlocks bitmap and destroys Vulkan objects automatically
}