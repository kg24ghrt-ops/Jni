#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <random>
#include <algorithm>

#define LOG_TAG "PaperEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Perlin noise implementation for paper texture with optimizations
extern "C" {

// Hash function for noise - optimized
static uint32_t hash(uint32_t x, uint32_t y, uint32_t seed) {
    // Faster hash using multiplication and XOR
    uint32_t h = x * 374761393u + y * 668265263u + seed * 1442695041u;
    h = (h ^ (h >> 13)) * 1274126177u;
    h = h ^ (h >> 16);
    return h;
}

// Fast smoothstep approximation (3x^2 - 2x^3)
static inline float fastSmoothstep(float t) {
    return t * t * (3.0f - 2.0f * t);
}

// Linear interpolation - inline for performance
static inline float lerp(float a, float b, float t) {
    return a + (b - a) * t;
}

// 2D gradient noise (Perlin-like) - optimized
static float noise2D(float x, float y, uint32_t seed) {
    int32_t x0 = static_cast<int32_t>(std::floor(x));
    int32_t y0 = static_cast<int32_t>(std::floor(y));
    
    float fx = x - static_cast<float>(x0);
    float fy = y - static_cast<float>(y0);
    
    float sx = fastSmoothstep(fx);
    float sy = fastSmoothstep(fy);
    
    // Get random gradients at corners - use faster hash
    uint32_t h00 = hash(x0, y0, seed);
    uint32_t h10 = hash(x0 + 1, y0, seed);
    uint32_t h01 = hash(x0, y0 + 1, seed);
    uint32_t h11 = hash(x0 + 1, y0 + 1, seed);
    
    // Convert hash to pseudo-random gradient vectors using bit manipulation
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

// Multi-octave noise for realistic paper texture - optimized with early exit
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
        
        // Early exit for very small amplitudes
        if (amplitude < 0.001f) break;
    }
    
    return total / maxValue;
}

// Pre-computed base paper color
static const uint32_t BASE_PAPER_COLOR = 0xFFFBF9F2;

// Render realistic paper texture into a Bitmap with caching and optimizations
JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_renderPaper(JNIEnv* env, jobject thiz, jobject bitmap, 
                                                   jint width, jint height, jint seed, 
                                                   jfloat grain_intensity, jfloat fiber_density, 
                                                   jint water_stain_count, jfloat aging_yellow, 
                                                   jfloat fiber_direction, jfloat roughness) {
    AndroidBitmapInfo info;
    uint32_t* pixels;
    
    // Lock the bitmap
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
    
    // Pre-compute values
    const float invWidth = 1.0f / static_cast<float>(width);
    const float invHeight = 1.0f / static_cast<float>(height);
    const float paperR = (BASE_PAPER_COLOR >> 16) & 0xFF;
    const float paperG = (BASE_PAPER_COLOR >> 8) & 0xFF;
    const float paperB = BASE_PAPER_COLOR & 0xFF;
    
    // Extract RGB components from base color
    const float baseR = static_cast<float>(paperR);
    const float baseG = static_cast<float>(paperG);
    const float baseB = static_cast<float>(paperB);
    
    // Generate paper texture using multi-octave noise
    // Use pointer arithmetic for faster access
    uint32_t* rowPtr = pixels;
    
    for (int y = 0; y < height; y++) {
        float ny = static_cast<float>(y) * invHeight;
        
        for (int x = 0; x < width; x++) {
            float nx = static_cast<float>(x) * invWidth;
            
            // Large scale variation (paper formation)
            float broad = fbmNoise(nx * 7.0f, ny * 7.0f, seed, 3, 0.5f);
            
            // Medium scale variation (density)
            float medium = fbmNoise(nx * 24.0f, ny * 24.0f, seed + 71, 3, 0.5f);
            
            // Fine scale variation (grain)
            float fine = fbmNoise(nx * 100.0f, ny * 100.0f, seed + 113, 2, 0.5f);
            
            // Combine with different amplitudes
            float variation = broad * 2.4f + medium * 1.25f + fine * 0.65f;
            variation *= grain_intensity;
            
            // Apply aging (yellowing effect)
            float ageEffect = fbmNoise(nx * 5.0f, ny * 5.0f, seed + 200, 2, 0.5f) * 0.5f + 0.5f;
            ageEffect *= aging_yellow;
            
            // Apply variation (slightly reduce green and blue for warm paper look)
            int newR = static_cast<int>(baseR + variation);
            int newG = static_cast<int>(baseG + variation * 0.92f + ageEffect * 5.0f);
            int newB = static_cast<int>(baseB + variation * 0.78f + ageEffect * 3.0f);
            
            // Clamp values using min/max (faster than branching for most cases)
            newR = (newR < 0) ? 0 : (newR > 255) ? 255 : newR;
            newG = (newG < 0) ? 0 : (newG > 255) ? 255 : newG;
            newB = (newB < 0) ? 0 : (newB > 255) ? 255 : newB;
            
            // Set pixel directly
            *rowPtr++ = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
        }
    }
    
    // Add water stains if requested
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
                        // Darken the water stain area
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
    
    // Add cellulose fibers - optimized with pre-computed values
    if (fiber_density > 0.0f) {
        std::default_random_engine fibRng(seed + 2000);
        std::uniform_real_distribution<float> fibDist(0.0f, 1.0f);
        
        int fiberCount = static_cast<int>(fiber_density * width * height / 10000.0f);
        fiberCount = std::min(fiberCount, 9000);
        
        const float pi = 3.1415926535f;
        
        for (int i = 0; i < fiberCount; i++) {
            float startX = fibDist(fibRng) * width;
            float startY = fibDist(fibRng) * height;
            
            // Most fibers are short
            float length = fibDist(fibRng) < 0.92f ? 
                (1.5f + fibDist(fibRng) * 5.5f) : 
                (5.0f + fibDist(fibRng) * 13.0f);
            
            // Random orientation with directional bias
            float baseAngle = fibDist(fibRng) * pi;
            float directionalBias = (fibDist(fibRng) - 0.5f) * 0.55f * fiber_direction;
            float angle = baseAngle + directionalBias;
            
            // Curvature
            float bend = (fibDist(fibRng) - 0.5f) * 1.2f;
            
            // Fiber color (brownish) - pre-computed ranges
            uint8_t fiberR = static_cast<uint8_t>(92 + fibDist(fibRng) * 33);
            uint8_t fiberG = static_cast<uint8_t>(76 + fibDist(fibRng) * 20);
            uint8_t fiberB = static_cast<uint8_t>(61 + fibDist(fibRng) * 15);
            
            // Fiber alpha (very subtle)
            uint8_t alpha = static_cast<uint8_t>(5 + fibDist(fibRng) * 14);
            
            float dx = std::cos(angle) * length;
            float dy = std::sin(angle) * length;
            
            // Draw curved fiber
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
                        // Blend fiber with existing pixel
                        uint32_t pixel = pixels[py * width + px];
                        uint8_t r = (pixel >> 16) & 0xFF;
                        uint8_t g = (pixel >> 8) & 0xFF;
                        uint8_t b = pixel & 0xFF;
                        
                        // Alpha blend - optimized
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
    
    // Unlock the bitmap
    AndroidBitmap_unlockPixels(env, bitmap);
}

}
