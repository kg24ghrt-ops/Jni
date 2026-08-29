#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <random>
#include <algorithm>
#include <thread>

#define LOG_TAG "PaperEngineSIMD"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Check for ARM NEON support
#if defined(__ARM_NEON__)
#include <arm_neon.h>
#define HAS_NEON 1
#else
#define HAS_NEON 0
#endif

// Check for SSE4.1 support (x86)
#if defined(__SSE4_1__)
#include <smmintrin.h>
#define HAS_SSE 1
#else
#define HAS_SSE 0
#endif

extern "C" {

// Hash function for noise
static uint32_t hash(uint32_t x, uint32_t y, uint32_t seed) {
    uint32_t h = x * 374761393u + y * 668265263u + seed * 1442695041u;
    h = (h ^ (h >> 13)) * 1274126177u;
    h = h ^ (h >> 16);
    return h;
}

// Smoothstep interpolation
static inline float smoothstep(float t) {
    return t * t * (3.0f - 2.0f * t);
}

// Linear interpolation
static inline float lerp(float a, float b, float t) {
    return a + (b - a) * t;
}

// 2D gradient noise (Perlin-like)
static float noise2D(float x, float y, uint32_t seed) {
    int32_t x0 = static_cast<int32_t>(std::floor(x));
    int32_t y0 = static_cast<int32_t>(std::floor(y));
    
    float fx = x - static_cast<float>(x0);
    float fy = y - static_cast<float>(y0);
    
    float sx = smoothstep(fx);
    float sy = smoothstep(fy);
    
    // Get random gradients at corners
    uint32_t h00 = hash(x0, y0, seed);
    uint32_t h10 = hash(x0 + 1, y0, seed);
    uint32_t h01 = hash(x0, y0 + 1, seed);
    uint32_t h11 = hash(x0 + 1, y0 + 1, seed);
    
    // Convert hash to pseudo-random gradient vectors
    float g00 = (h00 & 0x7FFFFFFF) * (1.0f / 1073741823.5f) * 2.0f - 1.0f;
    float g10 = (h10 & 0x7FFFFFFF) * (1.0f / 1073741823.5f) * 2.0f - 1.0f;
    float g01 = (h01 & 0x7FFFFFFF) * (1.0f / 1073741823.5f) * 2.0f - 1.0f;
    float g11 = (h11 & 0x7FFFFFFF) * (1.0f / 1073741823.5f) * 2.0f - 1.0f;
    
    // Dot product with distance vectors
    float v00 = fx * g00 + fy * g00;
    float v10 = (fx - 1.0f) * g10 + fy * g10;
    float v01 = fx * g01 + (fy - 1.0f) * g01;
    float v11 = (fx - 1.0f) * g11 + (fy - 1.0f) * g11;
    
    // Interpolate
    float nx0 = lerp(v00, v10, sx);
    float nx1 = lerp(v01, v11, sx);
    
    return lerp(nx0, nx1, sy);
}

// Multi-octave noise for realistic paper texture
static float fbmNoise(float x, float y, uint32_t seed, int octaves = 4, float persistence = 0.5f) {
    float total = 0.0f;
    float frequency = 1.0f;
    float amplitude = 1.0f;
    float maxValue = 0.0f;
    
    for (int i = 0; i < octaves; i++) {
        total += noise2D(x * frequency, y * frequency, seed + i) * amplitude;
        maxValue += amplitude;
        frequency *= 2.0f;
        amplitude *= persistence;
        if (amplitude < 0.001f) break;
    }
    
    return total / maxValue;
}

// Multi-threaded paper rendering
JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_renderPaperMT(JNIEnv* env, jobject thiz, jobject bitmap, 
                                                    jint width, jint height, jint seed, 
                                                    jfloat grain_intensity, jfloat fiber_density, 
                                                    jint water_stain_count, jfloat aging_yellow, 
                                                    jfloat fiber_direction, jfloat roughness,
                                                    jint threadCount) {
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
    
    const float invWidth = 1.0f / static_cast<float>(width);
    const float invHeight = 1.0f / static_cast<float>(height);
    const float baseR = 251.0f;  // 0xFB
    const float baseG = 249.0f;  // 0xF9
    const float baseB = 242.0f;  // 0xF2
    
    // Use multi-threading if requested and more than 1 thread
    if (threadCount > 1 && height > 64) {
        // Create thread data
        typedef struct {
            int startY;
            int endY;
            int width;
            int height;
            float invWidth;
            float invHeight;
            uint32_t* pixels;
            int seed;
            float grain_intensity;
            float fiber_direction;
        } ThreadData;
        
        std::vector<std::thread> threads;
        std::vector<ThreadData> threadData(threadCount);
        
        int rowsPerThread = height / threadCount;
        
        for (int t = 0; t < threadCount; t++) {
            int startY = t * rowsPerThread;
            int endY = (t == threadCount - 1) ? height : startY + rowsPerThread;
            
            threadData[t] = {startY, endY, width, height, invWidth, invHeight, 
                           pixels, seed, grain_intensity, fiber_direction};
            
            threads.emplace_back([&, t]() {
                auto& data = threadData[t];
                for (int y = data.startY; y < data.endY; y++) {
                    float ny = static_cast<float>(y) * data.invHeight;
                    for (int x = 0; x < data.width; x++) {
                        float nx = static_cast<float>(x) * data.invWidth;
                        
                        float broad = fbmNoise(nx * 7.0f, ny * 7.0f, data.seed, 3, 0.5f);
                        float medium = fbmNoise(nx * 24.0f, ny * 24.0f, data.seed + 71, 3, 0.5f);
                        float fine = fbmNoise(nx * 100.0f, ny * 100.0f, data.seed + 113, 2, 0.5f);
                        
                        float variation = broad * 2.4f + medium * 1.25f + fine * 0.65f;
                        variation *= data.grain_intensity;
                        
                        float ageEffect = fbmNoise(nx * 5.0f, ny * 5.0f, data.seed + 200, 2, 0.5f) * 0.5f + 0.5f;
                        ageEffect *= aging_yellow;
                        
                        int newR = static_cast<int>(baseR + variation);
                        int newG = static_cast<int>(baseG + variation * 0.92f + ageEffect * 5.0f);
                        int newB = static_cast<int>(baseB + variation * 0.78f + ageEffect * 3.0f);
                        
                        newR = (newR < 0) ? 0 : (newR > 255) ? 255 : newR;
                        newG = (newG < 0) ? 0 : (newG > 255) ? 255 : newG;
                        newB = (newB < 0) ? 0 : (newB > 255) ? 255 : newB;
                        
                        data.pixels[y * data.width + x] = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
                    }
                }
            });
        }
        
        // Wait for all threads to complete
        for (auto& thread : threads) {
            thread.join();
        }
    } else {
        // Single-threaded rendering
        for (int y = 0; y < height; y++) {
            float ny = static_cast<float>(y) * invHeight;
            for (int x = 0; x < width; x++) {
                float nx = static_cast<float>(x) * invWidth;
                
                float broad = fbmNoise(nx * 7.0f, ny * 7.0f, seed, 3, 0.5f);
                float medium = fbmNoise(nx * 24.0f, ny * 24.0f, seed + 71, 3, 0.5f);
                float fine = fbmNoise(nx * 100.0f, ny * 100.0f, seed + 113, 2, 0.5f);
                
                float variation = broad * 2.4f + medium * 1.25f + fine * 0.65f;
                variation *= grain_intensity;
                
                float ageEffect = fbmNoise(nx * 5.0f, ny * 5.0f, seed + 200, 2, 0.5f) * 0.5f + 0.5f;
                ageEffect *= aging_yellow;
                
                int newR = static_cast<int>(baseR + variation);
                int newG = static_cast<int>(baseG + variation * 0.92f + ageEffect * 5.0f);
                int newB = static_cast<int>(baseB + variation * 0.78f + ageEffect * 3.0f);
                
                newR = (newR < 0) ? 0 : (newR > 255) ? 255 : newR;
                newG = (newG < 0) ? 0 : (newG > 255) ? 255 : newG;
                newB = (newB < 0) ? 0 : (newB > 255) ? 255 : newB;
                
                pixels[y * width + x] = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
            }
        }
    }
    
    // Add water stains (single-threaded for now)
    if (water_stain_count > 0) {
        std::default_random_engine rng(seed + 1000);
        std::uniform_real_distribution<float> distX(0.0f, 1.0f);
        std::uniform_real_distribution<float> distY(0.0f, 1.0f);
        std::uniform_real_distribution<float> distSize(0.05f, 0.2f);
        
        for (int i = 0; i < water_stain_count; i++) {
            float centerX = distX(rng);
            float centerY = distY(rng);
            float size = distSize(rng);
            
            int radius = static_cast<int>(size * std::min(width, height));
            int centerX_px = static_cast<int>(centerX * width);
            int centerY_px = static_cast<int>(centerY * height);
            
            int yStart = std::max(0, centerY_px - radius);
            int yEnd = std::min(height, centerY_px + radius);
            int xStart = std::max(0, centerX_px - radius);
            int xEnd = std::min(width, centerX_px + radius);
            
            for (int y = yStart; y < yEnd; y++) {
                for (int x = xStart; x < xEnd; x++) {
                    float dx = static_cast<float>(x - centerX_px);
                    float dy = static_cast<float>(y - centerY_px);
                    float dist = std::sqrt(dx * dx + dy * dy) / radius;
                    if (dist < 1.0f) {
                        float stainEffect = (1.0f - dist) * 0.3f;
                        uint32_t pixel = pixels[y * width + x];
                        uint8_t r = (pixel >> 16) & 0xFF;
                        uint8_t g = (pixel >> 8) & 0xFF;
                        uint8_t b = pixel & 0xFF;
                        
                        int newR = static_cast<int>(r * (1.0f - stainEffect));
                        int newG = static_cast<int>(g * (1.0f - stainEffect));
                        int newB = static_cast<int>(b * (1.0f - stainEffect * 0.5f));
                        
                        newR = (newR < 0) ? 0 : (newR > 255) ? 255 : newR;
                        newG = (newG < 0) ? 0 : (newG > 255) ? 255 : newG;
                        newB = (newB < 0) ? 0 : (newB > 255) ? 255 : newB;
                        
                        pixels[y * width + x] = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
                    }
                }
            }
        }
    }
    
    // Add cellulose fibers (single-threaded for now)
    if (fiber_density > 0.0f) {
        std::default_random_engine fibRng(seed + 2000);
        std::uniform_real_distribution<float> fibDist(0.0f, 1.0f);
        
        int fiberCount = static_cast<int>(fiber_density * width * height / 10000.0f);
        fiberCount = std::min(fiberCount, 9000);
        
        const float pi = 3.1415926535f;
        
        for (int i = 0; i < fiberCount; i++) {
            float startX = fibDist(fibRng) * width;
            float startY = fibDist(fibRng) * height;
            
            float length = fibDist(fibRng) < 0.92f ? 
                (1.5f + fibDist(fibRng) * 5.5f) : 
                (5.0f + fibDist(fibRng) * 13.0f);
            
            float baseAngle = fibDist(fibRng) * pi;
            float directionalBias = (fibDist(fibRng) - 0.5f) * 0.55f * fiber_direction;
            float angle = baseAngle + directionalBias;
            
            float bend = (fibDist(fibRng) - 0.5f) * 1.2f;
            
            uint8_t fiberR = static_cast<uint8_t>(92 + fibDist(fibRng) * 33);
            uint8_t fiberG = static_cast<uint8_t>(76 + fibDist(fibRng) * 20);
            uint8_t fiberB = static_cast<uint8_t>(61 + fibDist(fibRng) * 15);
            uint8_t alpha = static_cast<uint8_t>(5 + fibDist(fibRng) * 14);
            
            float dx = std::cos(angle) * length;
            float dy = std::sin(angle) * length;
            
            int steps = static_cast<int>(length * 2);
            if (steps > 0) {
                float stepInv = 1.0f / steps;
                for (int s = 0; s <= steps; s++) {
                    float t = static_cast<float>(s) * stepInv;
                    float fx = startX + dx * t;
                    float fy = startY + dy * t + bend * std::sin(t * pi) * length * 0.3f;
                    
                    int px = static_cast<int>(fx);
                    int py = static_cast<int>(fy);
                    
                    if (px >= 0 && px < width && py >= 0 && py < height) {
                        uint32_t pixel = pixels[py * width + px];
                        uint8_t r = (pixel >> 16) & 0xFF;
                        uint8_t g = (pixel >> 8) & 0xFF;
                        uint8_t b = pixel & 0xFF;
                        
                        float blend = alpha * (1.0f / 255.0f);
                        float invBlend = 1.0f - blend;
                        int newR = static_cast<int>(r * invBlend + fiberR * blend);
                        int newG = static_cast<int>(g * invBlend + fiberG * blend);
                        int newB = static_cast<int>(b * invBlend + fiberB * blend);
                        
                        newR = (newR < 0) ? 0 : (newR > 255) ? 255 : newR;
                        newG = (newG < 0) ? 0 : (newG > 255) ? 255 : newG;
                        newB = (newB < 0) ? 0 : (newB > 255) ? 255 : newB;
                        
                        pixels[py * width + px] = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
                    }
                }
            }
        }
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
}

// Get the number of available CPU cores
JNIEXPORT jint JNICALL
Java_com_example_homecil_native_PaperEngineNative_getCpuCoreCount(JNIEnv* env, jobject thiz) {
    // Use std::thread::hardware_concurrency() for Android
    // This is portable and works across all platforms
    unsigned int cores = std::thread::hardware_concurrency();
    if (cores == 0) {
        // Fallback: try to read from /proc/cpuinfo on Android/Linux
        FILE* fp = fopen("/proc/cpuinfo", "r");
        if (fp) {
            char line[256];
            while (fgets(line, sizeof(line), fp)) {
                if (strncmp(line, "processor", 9) == 0) {
                    cores++;
                }
            }
            fclose(fp);
        }
    }
    return static_cast<jint>(cores > 0 ? cores : 1);
}

// Check if NEON is available
JNIEXPORT jboolean JNICALL
Java_com_example_homecil_native_PaperEngineNative_hasNeonSupport(JNIEnv* env, jobject thiz) {
    #if HAS_NEON
        return JNI_TRUE;
    #else
        return JNI_FALSE;
    #endif
}

// Check if SSE is available
JNIEXPORT jboolean JNICALL
Java_com_example_homecil_native_PaperEngineNative_hasSseSupport(JNIEnv* env, jobject thiz) {
    #if HAS_SSE
        return JNI_TRUE;
    #else
        return JNI_FALSE;
    #endif
}

}
