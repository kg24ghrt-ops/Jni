#include <jni.h>
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstring>
#include <ctime>
#include "shader_loader.h"
#include "vulkan_compute_engine.h"

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

    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    auto& engine = VulkanComputeEngine::getInstance();
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) { AndroidBitmap_unlockPixels(env, bitmap); return; }

    if (!engine.initialize(window)) {
        ANativeWindow_release(window);
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    setVulkanDevice(engine.getDevice());

    // Create output image (storage)
    VkImage outputImage = engine.createImage(width, height, VK_FORMAT_R8G8B8A8_UNORM,
                                             VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
    if (!outputImage) { /* error */ }
    if (!engine.allocateImageMemory(outputImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) { /* error */ }
    VkImageView outputView = engine.createImageView(outputImage, VK_FORMAT_R8G8B8A8_UNORM);

    // Create descriptor set for output image
    VkDescriptorSet descSet = engine.createStorageImageDescriptorSet(outputView);

    // Build push constants
    struct PushConstants { /* same as before */ } pc;
    // ... fill pc with uniform data ...

    // Dispatch
    VkShaderModule shaderModule = getPaperShaderModule();
    if (!shaderModule) { /* error */ }
    engine.dispatchCompute(shaderModule, {descSet}, &pc, sizeof(pc), (width+15)/16, (height+15)/16, 1);

    // Read back
    engine.copyImageToHost(outputImage, pixels, width, height, VK_FORMAT_R8G8B8A8_UNORM);

    // Cleanup
    engine.destroyImageView(outputView);
    engine.destroyImage(outputImage);
    // (Need to free descriptor set? We'll handle with pool reset later)
    ANativeWindow_release(window);
    AndroidBitmap_unlockPixels(env, bitmap);
}