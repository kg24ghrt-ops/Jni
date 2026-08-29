#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cmath>
#include <cstdlib>
#include <random>
#include <algorithm>

#define LOG_TAG "DistortEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

// Fast hash function - optimized
static inline uint32_t hash32(uint32_t x, uint32_t y, uint32_t seed) {
    uint32_t h = x * 374761393u + y * 668265263u + seed * 1442695041u;
    h = (h ^ (h >> 13)) * 1274126177u;
    h = h ^ (h >> 16);
    return h;
}

// Smoothstep interpolation - inline
static inline float smoothstep(float t) {
    return t * t * (3.0f - 2.0f * t);
}

// Linear interpolation - inline
static inline float lerp(float a, float b, float t) {
    return a + (b - a) * t;
}

// 2D value noise - optimized
static inline float noise2D(int x, int y, uint32_t seed) {
    uint32_t h = hash32(x, y, seed);
    return (h & 0x7FFFFFFF) / static_cast<float>(0x7FFFFFFF) * 2.0f - 1.0f;
}

// Fractional Brownian motion (fbm) for coherent noise - optimized
static float fbm2D(float x, float y, uint32_t seed, int octaves = 4, float persistence = 0.5f) {
    float total = 0.0f;
    float frequency = 1.0f;
    float amplitude = 1.0f;
    float maxValue = 0.0f;
    
    for (int i = 0; i < octaves; i++) {
        total += noise2D(static_cast<int>(x * frequency), static_cast<int>(y * frequency), seed + i) * amplitude;
        maxValue += amplitude;
        frequency *= 2.0f;
        amplitude *= persistence;
        
        // Early exit for very small amplitudes
        if (amplitude < 0.001f) break;
    }
    
    return total / maxValue;
}

// Calculate gradient at a point (for geometry-aware distortion) - optimized
static inline void calculateGradient(const uint32_t* pixels, int width, int height, int x, int y, 
                             float& gradientX, float& gradientY) {
    // Sample neighboring pixels
    float center = static_cast<float>((pixels[y * width + x] & 0xFF));
    float right = x < width - 1 ? static_cast<float>((pixels[y * width + x + 1] & 0xFF)) : center;
    float left = x > 0 ? static_cast<float>((pixels[y * width + x - 1] & 0xFF)) : center;
    float bottom = y < height - 1 ? static_cast<float>((pixels[(y + 1) * width + x] & 0xFF)) : center;
    float top = y > 0 ? static_cast<float>((pixels[(y - 1) * width + x] & 0xFF)) : center;
    
    // Calculate horizontal and vertical gradients
    gradientX = (right - left) * 0.5f;
    gradientY = (bottom - top) * 0.5f;
}

// Distort a bitmap to simulate hand-drawn imperfections - optimized
JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_distortBitmap(JNIEnv* env, jobject thiz, jobject bitmap, 
                                                      jint seed, jfloat distortionScale, 
                                                      jfloat sineWarpScale, jfloat curvatureScale) {
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
    
    int width = info.width;
    int height = info.height;
    
    // Allocate temporary buffer for the original image
    uint32_t* tempPixels = new uint32_t[width * height];
    std::memcpy(tempPixels, pixels, width * height * sizeof(uint32_t));
    
    // Pre-compute values
    const float invWidth = 1.0f / width;
    const float invHeight = 1.0f / height;
    const float pi = 3.1415926535f;
    
    // Generate distortion for each pixel
    for (int y = 0; y < height; y++) {
        float ny = static_cast<float>(y) * invHeight;
        
        for (int x = 0; x < width; x++) {
            float nx = static_cast<float>(x) * invWidth;
            
            // Coherent FBM noise for base distortion
            float noise = fbm2D(x, y, seed, 4, 0.5f) * distortionScale;
            
            // Sine warp (global distortion)
            float sineX = std::sin(nx * pi * 2.0f * sineWarpScale) * 0.5f * sineWarpScale;
            float sineY = std::sin(ny * pi * 2.0f * sineWarpScale) * 0.5f * sineWarpScale;
            
            // Geometry-aware modulation
            float gradientX, gradientY;
            calculateGradient(tempPixels, width, height, x, y, gradientX, gradientY);
            
            // Calculate gradient magnitude and direction
            float gradientMag = std::sqrt(gradientX * gradientX + gradientY * gradientY);
            float gradientDir = gradientMag > 0.001f ? std::atan2(gradientY, gradientX) : 0.0f;
            
            // Modulate distortion based on gradient (curvature-aware)
            float curvatureMod = std::fabs(std::sin(gradientDir * 2.0f + nx * pi)) * curvatureScale;
            
            // Total distortion
            float totalDistX = noise + sineX + curvatureMod * 0.5f;
            float totalDistY = noise + sineY + curvatureMod * 0.3f;
            
            // Calculate source coordinates (with wrapping)
            float srcX = x + totalDistX * width * 0.05f;
            float srcY = y + totalDistY * height * 0.05f;
            
            // Clamp to image bounds
            srcX = std::max(0.0f, std::min(width - 1.0f, srcX));
            srcY = std::max(0.0f, std::min(height - 1.0f, srcY));
            
            // Bilinear interpolation for smooth distortion
            int x0 = static_cast<int>(srcX);
            int y0 = static_cast<int>(srcY);
            int x1 = std::min(x0 + 1, width - 1);
            int y1 = std::min(y0 + 1, height - 1);
            
            float fx = srcX - x0;
            float fy = srcY - y0;
            
            uint32_t p00 = tempPixels[y0 * width + x0];
            uint32_t p10 = tempPixels[y0 * width + x1];
            uint32_t p01 = tempPixels[y1 * width + x0];
            uint32_t p11 = tempPixels[y1 * width + x1];
            
            // Interpolate each channel - optimized
            uint32_t result = 0xFF000000;
            for (int channel = 0; channel < 3; channel++) {
                uint8_t c00 = (p00 >> (channel * 8)) & 0xFF;
                uint8_t c10 = (p10 >> (channel * 8)) & 0xFF;
                uint8_t c01 = (p01 >> (channel * 8)) & 0xFF;
                uint8_t c11 = (p11 >> (channel * 8)) & 0xFF;
                
                float top = c00 + (c10 - c00) * fx;
                float bottom = c01 + (c11 - c01) * fx;
                uint8_t blended = static_cast<uint8_t>(top + (bottom - top) * fy);
                
                result |= (blended << (channel * 8));
            }
            
            pixels[y * width + x] = result;
        }
    }
    
    // Clean up
    delete[] tempPixels;
    AndroidBitmap_unlockPixels(env, bitmap);
}

// Simplified distortion for character bitmaps (faster, less complex) - optimized
JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_distortCharacter(JNIEnv* env, jobject thiz, jobject bitmap, 
                                                               jint seed, jfloat scale) {
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
    
    int width = info.width;
    int height = info.height;
    
    uint32_t* tempPixels = new uint32_t[width * height];
    std::memcpy(tempPixels, pixels, width * height * sizeof(uint32_t));
    
    const float pi = 3.1415926535f;
    const float seedFloat = static_cast<float>(seed) * 0.01f;
    
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            // Simple noise-based distortion - optimized
            float noiseX = noise2D(x, y, seed) * scale * 0.5f;
            float noiseY = noise2D(x + 100, y + 100, seed) * scale * 0.5f;
            
            // Sine warp - optimized
            float sx = std::sin(x * 0.1f + seedFloat) * scale * 0.3f;
            float sy = std::sin(y * 0.1f + seedFloat) * scale * 0.3f;
            
            float srcX = x + noiseX + sx;
            float srcY = y + noiseY + sy;
            
            srcX = std::max(0.0f, std::min(width - 1.0f, srcX));
            srcY = std::max(0.0f, std::min(height - 1.0f, srcY));
            
            int x0 = static_cast<int>(srcX);
            int y0 = static_cast<int>(srcY);
            
            pixels[y * width + x] = tempPixels[y0 * width + x0];
        }
    }
    
    delete[] tempPixels;
    AndroidBitmap_unlockPixels(env, bitmap);
}

}
