#include <jni.h>
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstring>
#include <ctime>
#include "shader_loader.h"
#include "vulkan_compute_engine.h"

// --------------------------------------------------------------
// JNI entry point for paper generation (Vulkan compute)
// --------------------------------------------------------------
extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_renderPaper(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap,
        jobject surface,
        jint width,
        jint height,
        jint lineSpacing,
        jint marginLeft,
        jint lineColor,
        jint marginColor,
        jint lineThickness) {

    // 1. Lock the Android bitmap (we'll write back to it)
    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    // 2. Get Vulkan engine and initialize with the surface
    auto& engine = VulkanComputeEngine::getInstance();
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    if (!engine.initialize(window)) {
        ANativeWindow_release(window);
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // 3. Set Vulkan device in shader loader (so it can create shader modules)
    setVulkanDevice(engine.getDevice());

    // 4. Get the paper generation shader module
    VkShaderModule shaderModule = getPaperShaderModule();
    if (!shaderModule) {
        ANativeWindow_release(window);
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // 5. Create output image (storage) at full resolution
    VkImage outputImage = engine.createImage(width, height, VK_FORMAT_R8G8B8A8_UNORM,
                                             VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
    if (!outputImage) {
        ANativeWindow_release(window);
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }
    if (!engine.allocateImageMemory(outputImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
        engine.destroyImage(outputImage);
        ANativeWindow_release(window);
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    VkImageView outputView = engine.createImageView(outputImage, VK_FORMAT_R8G8B8A8_UNORM);
    if (!outputView) {
        engine.destroyImage(outputImage);
        ANativeWindow_release(window);
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // 6. Create descriptor set for the output image (binding 0)
    VkDescriptorSet descSet = engine.createStorageImageDescriptorSet(outputView);
    if (!descSet) {
        engine.destroyImageView(outputView);
        engine.destroyImage(outputImage);
        ANativeWindow_release(window);
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // 7. Prepare push constants (all uniforms)
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
    } pc;

    pc.imageSize[0] = static_cast<float>(width);
    pc.imageSize[1] = static_cast<float>(height);
    pc.lineSpacing = static_cast<float>(lineSpacing);
    pc.marginLeft = static_cast<float>(marginLeft);
    pc.lineThickness = static_cast<float>(lineThickness);
    pc.seed = static_cast<uint32_t>(time(nullptr)) ^ static_cast<uint32_t>(clock());

    // Helper to unpack ARGB color to float3
    auto unpackColor = [](int color, float* out) {
        out[0] = ((color >> 16) & 0xFF) / 255.0f;
        out[1] = ((color >> 8) & 0xFF) / 255.0f;
        out[2] = (color & 0xFF) / 255.0f;
    };
    unpackColor(lineColor, pc.lineColor);
    unpackColor(marginColor, pc.marginColor);
    pc.baseColor[0] = 0.99f;
    pc.baseColor[1] = 0.99f;
    pc.baseColor[2] = 0.98f;

    // Default tunable parameters
    pc.fiberScale = 0.2f;
    pc.grainStrength = 0.3f;
    pc.bleedAmount = 0.25f;
    pc.waveAmplitude = 0.8f;
    pc.inclineSlope = 0.002f;
    pc.spotThreshold = 0.97f;
    pc.vignetteStrength = 0.3f;
    pc.paperAge = 0.5f;
    pc.lineWarp = 0.3f;

    // 8. Dispatch the compute shader
    uint32_t workX = (width + 15) / 16;
    uint32_t workY = (height + 15) / 16;
    bool dispatched = engine.dispatchCompute(shaderModule, {descSet}, &pc, sizeof(pc), workX, workY, 1);
    if (!dispatched) {
        // cleanup and return
        engine.destroyImageView(outputView);
        engine.destroyImage(outputImage);
        ANativeWindow_release(window);
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // 9. Read back the output image to the bitmap pixels
    bool copied = engine.copyImageToHost(outputImage, pixels, width, height, VK_FORMAT_R8G8B8A8_UNORM);
    if (!copied) {
        // fallback: leave bitmap as is (or fill with error color)
    }

    // 10. Cleanup Vulkan resources (per dispatch)
    engine.destroyImageView(outputView);
    engine.destroyImage(outputImage);

    // 11. Release the window and unlock the bitmap
    ANativeWindow_release(window);
    AndroidBitmap_unlockPixels(env, bitmap);
}