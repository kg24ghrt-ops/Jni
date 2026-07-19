#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <algorithm>

// ---------- noise helpers (coherent, not random per pixel) ----------
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

    float sx = fx * fx * (3.0f - 2.0f * fx);
    float sy = fy * fy * (3.0f - 2.0f * fy);

    float n00 = noise(ix, iy, seed);
    float n10 = noise(ix + 1, iy, seed);
    float n01 = noise(ix, iy + 1, seed);
    float n11 = noise(ix + 1, iy + 1, seed);

    float nx0 = n00 + (n10 - n00) * sx;
    float nx1 = n01 + (n11 - n01) * sx;
    return nx0 + (nx1 - nx0) * sy;
}

// Fractional Brownian motion – smooth, multi‑octave noise
static float fbm(float x, float y, int seed, float scale) {
    float value = 0.0f;
    float amplitude = 0.5f;
    float frequency = scale;
    float persistence = 0.6f;
    int octaves = 3;

    for (int i = 0; i < octaves; ++i) {
        value += amplitude * smoothNoise(x * frequency, y * frequency, seed + i);
        amplitude *= persistence;
        frequency *= 2.0f;
    }
    return value;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_distortBitmap(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap,
        jfloat strength) {

    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    auto* src = static_cast<uint32_t*>(pixels);
    int w = info.width;
    int h = info.height;

    // Copy original
    uint32_t* copy = new uint32_t[w * h];
    std::copy(src, src + w * h, copy);

    // Seed (different for each character if you want, fixed here)
    int seedX = 123;
    int seedY = 456;
    float scale = 0.08f;   // lower → smoother / larger waves

    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            // Smooth displacement (-1..1) from coherent noise
            float dx = fbm((float)x, (float)y, seedX, scale) * 2.0f - 1.0f;
            float dy = fbm((float)x + 1000, (float)y + 1000, seedY, scale) * 2.0f - 1.0f;

            int sx = static_cast<int>(x + dx * strength);
            int sy = static_cast<int>(y + dy * strength);

            if (sx < 0) sx = 0;
            if (sx >= w) sx = w - 1;
            if (sy < 0) sy = 0;
            if (sy >= h) sy = h - 1;

            src[y * w + x] = copy[sy * w + sx];
        }
    }

    delete[] copy;
    AndroidBitmap_unlockPixels(env, bitmap);
}