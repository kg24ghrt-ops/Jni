#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cmath>
#include <cstdlib>
#include <random>
#include <algorithm>

#define LOG_TAG "InkEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

// Fast pseudo-random number generator - PCG variant
static inline uint32_t pcg_hash(uint32_t seed) {
    uint32_t state = seed * 747796405u + 2891336453u;
    uint32_t word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    word = (word >> 22u) ^ word;
    return word;
}

// Fast noise for ink density variation - optimized
static inline float inkNoise(int x, int y, uint32_t seed) {
    uint32_t h = pcg_hash(x * 374761393u + y * 668265263u + seed * 1442695041u);
    return (h & 0xFFFF) / 32767.5f - 1.0f;
}

// Simulate ink bleed and absorption on paper - optimized
JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_simulateInk(JNIEnv* env, jobject thiz, jobject bitmap, 
                                                   jobject inkBitmap, jint x, jint y, 
                                                   jfloat inkColorR, jfloat inkColorG, jfloat inkColorB,
                                                   jfloat absorption, jfloat noiseIntensity, 
                                                   jint seed) {
    AndroidBitmapInfo info;
    uint32_t* pixels;
    uint32_t* inkPixels;
    
    // Lock the target bitmap
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGD("Failed to get bitmap info");
        return;
    }
    
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGD("Bitmap format is not RGBA_8888");
        return;
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, (void**)&pixels) < 0) {
        LOGD("Failed to lock pixels");
        return;
    }
    
    // Lock the ink bitmap
    AndroidBitmapInfo inkInfo;
    if (AndroidBitmap_getInfo(env, inkBitmap, &inkInfo) < 0) {
        LOGD("Failed to get ink bitmap info");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }
    
    if (inkInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGD("Ink bitmap format is not RGBA_8888");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }
    
    if (AndroidBitmap_lockPixels(env, inkBitmap, (void**)&inkPixels) < 0) {
        LOGD("Failed to lock ink pixels");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }
    
    int width = info.width;
    int height = info.height;
    int inkWidth = inkInfo.width;
    int inkHeight = inkInfo.height;
    
    // Convert ink color to integers (0-255)
    uint8_t inkR = static_cast<uint8_t>(inkColorR * 255.0f);
    uint8_t inkG = static_cast<uint8_t>(inkColorG * 255.0f);
    uint8_t inkB = static_cast<uint8_t>(inkColorB * 255.0f);
    
    // Pre-compute inverse of 255 for faster division
    const float inv255 = 1.0f / 255.0f;
    
    // Iterate over the ink bitmap
    for (int iy = 0; iy < inkHeight; iy++) {
        int targetY = y + iy;
        if (targetY < 0 || targetY >= height) continue;
        
        for (int ix = 0; ix < inkWidth; ix++) {
            int targetX = x + ix;
            if (targetX < 0 || targetX >= width) continue;
            
            uint32_t inkPixel = inkPixels[iy * inkWidth + ix];
            uint8_t inkAlpha = (inkPixel >> 24) & 0xFF;
            
            if (inkAlpha == 0) continue; // Skip transparent pixels
            
            // Get paper pixel
            uint32_t paperPixel = pixels[targetY * width + targetX];
            uint8_t paperR = (paperPixel >> 16) & 0xFF;
            uint8_t paperG = (paperPixel >> 8) & 0xFF;
            uint8_t paperB = paperPixel & 0xFF;
            
            // Calculate paper brightness (for absorption effect)
            float paperBrightness = (paperR * 0.299f + paperG * 0.587f + paperB * 0.114f) * inv255;
            
            // Adjust ink color based on paper brightness
            float brightnessFactor = 1.0f - (1.0f - paperBrightness) * absorption;
            
            // Add noise for ink density variation
            float noise = inkNoise(ix, iy, seed) * noiseIntensity;
            float inkFactor = (inkAlpha * inv255) * (1.0f + noise * 0.3f);
            
            // Blend ink with paper
            float blend = inkFactor * brightnessFactor;
            float invBlend = 1.0f - blend;
            
            uint8_t newR = static_cast<uint8_t>(paperR * invBlend + inkR * blend);
            uint8_t newG = static_cast<uint8_t>(paperG * invBlend + inkG * blend);
            uint8_t newB = static_cast<uint8_t>(paperB * invBlend + inkB * blend);
            
            // Apply slight darkening for ink absorption
            float darken = blend * 0.15f;
            newR = static_cast<uint8_t>(newR * (1.0f - darken));
            newG = static_cast<uint8_t>(newG * (1.0f - darken));
            newB = static_cast<uint8_t>(newB * (1.0f - darken * 0.7f));
            
            pixels[targetY * width + targetX] = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
        }
    }
    
    // Unlock bitmaps
    AndroidBitmap_unlockPixels(env, inkBitmap);
    AndroidBitmap_unlockPixels(env, bitmap);
}

// Simplified version for direct color stamping - optimized
JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_simulateInkSimple(JNIEnv* env, jobject thiz, jobject bitmap, 
                                                               jint x, jint y, jint width, jint height,
                                                               jfloat inkColorR, jfloat inkColorG, jfloat inkColorB,
                                                               jfloat opacity) {
    AndroidBitmapInfo info;
    uint32_t* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGD("Failed to get bitmap info");
        return;
    }
    
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGD("Bitmap format is not RGBA_8888");
        return;
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, (void**)&pixels) < 0) {
        LOGD("Failed to lock pixels");
        return;
    }
    
    uint8_t inkR = static_cast<uint8_t>(inkColorR * 255.0f);
    uint8_t inkG = static_cast<uint8_t>(inkColorG * 255.0f);
    uint8_t inkB = static_cast<uint8_t>(inkColorB * 255.0f);
    uint8_t alpha = static_cast<uint8_t>(opacity * 255.0f);
    
    const float inv255 = 1.0f / 255.0f;
    float blend = alpha * inv255;
    float invBlend = 1.0f - blend;
    
    for (int dy = 0; dy < height; dy++) {
        int targetY = y + dy;
        if (targetY < 0 || targetY >= info.height) continue;
        
        for (int dx = 0; dx < width; dx++) {
            int targetX = x + dx;
            if (targetX < 0 || targetX >= info.width) continue;
            
            uint32_t paperPixel = pixels[targetY * info.width + targetX];
            uint8_t paperR = (paperPixel >> 16) & 0xFF;
            uint8_t paperG = (paperPixel >> 8) & 0xFF;
            uint8_t paperB = paperPixel & 0xFF;
            
            // Alpha blend - optimized
            uint8_t newR = static_cast<uint8_t>(paperR * invBlend + inkR * blend);
            uint8_t newG = static_cast<uint8_t>(paperG * invBlend + inkG * blend);
            uint8_t newB = static_cast<uint8_t>(paperB * invBlend + inkB * blend);
            
            pixels[targetY * info.width + targetX] = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
        }
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
}

}
