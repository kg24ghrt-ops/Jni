#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <cstdlib>
#include <algorithm>

// Simple 2D noise for paper texture (value noise with smoothing)
static float noise(int x, int y, int seed) {
    int n = x + y * 57 + seed * 131;
    n = (n << 13) ^ n;
    return (1.0f - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0f);
}

static float smoothNoise(float x, float y, int seed) {
    int ix = (int)floor(x);
    int iy = (int)floor(y);
    float fx = x - ix;
    float fy = y - iy;

    // Smooth step
    float sx = fx * fx * (3.0f - 2.0f * fx);
    float sy = fy * fy * (3.0f - 2.0f * fy);

    float n00 = noise(ix,     iy,     seed);
    float n10 = noise(ix + 1, iy,     seed);
    float n01 = noise(ix,     iy + 1, seed);
    float n11 = noise(ix + 1, iy + 1, seed);

    float nx0 = n00 + (n10 - n00) * sx;
    float nx1 = n01 + (n11 - n01) * sx;
    return nx0 + (nx1 - nx0) * sy;
}

// Multi-octave noise for paper grain
static float fbm(float x, float y, int seed) {
    float value = 0.0f;
    float amplitude = 0.5f;
    float frequency = 0.01f;
    float persistence = 0.6f;
    int octaves = 4;

    for (int i = 0; i < octaves; ++i) {
        value += amplitude * smoothNoise(x * frequency, y * frequency, seed + i);
        amplitude *= persistence;
        frequency *= 2.0f;
    }
    return value;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_renderPaper(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap,
        jint width,
        jint height) {

    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    auto* ptr = static_cast<uint32_t*>(pixels);
    int seed = 42; // Fixed seed for consistent texture

    // Base paper color (warm off-white)
    const uint8_t baseR = 0xF5;
    const uint8_t baseG = 0xF0;
    const uint8_t baseB = 0xE6;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float noiseVal = fbm(static_cast<float>(x), static_cast<float>(y), seed);
            // Map noise from roughly -0.5..0.5 to brightness variation
            float brightness = 0.95f + noiseVal * 0.1f;
            brightness = std::min(1.0f, std::max(0.0f, brightness));

            uint8_t r = static_cast<uint8_t>(baseR * brightness);
            uint8_t g = static_cast<uint8_t>(baseG * brightness);
            uint8_t b = static_cast<uint8_t>(baseB * brightness);

            ptr[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}