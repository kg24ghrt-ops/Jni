#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <algorithm>

// ---- noise functions ----

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

    float n00 = noise(ix,     iy,     seed);
    float n10 = noise(ix + 1, iy,     seed);
    float n01 = noise(ix,     iy + 1, seed);
    float n11 = noise(ix + 1, iy + 1, seed);

    float nx0 = n00 + (n10 - n00) * sx;
    float nx1 = n01 + (n11 - n01) * sx;
    return nx0 + (nx1 - nx0) * sy;
}

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

// ---- JNI entry point ----

extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_renderPaper(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap,
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

    uint8_t* ptr = static_cast<uint8_t*>(pixels);      // byte pointer
    uint32_t byteStride = info.stride;                  // stride is in bytes
    int seed = 42;

    // Base paper colour (off‑white)
    const uint8_t baseR = 0xFD;
    const uint8_t baseG = 0xFD;
    const uint8_t baseB = 0xFB;

    // Decompose line colours into R, G, B
    const uint8_t lineR = (lineColor >> 16) & 0xFF;
    const uint8_t lineG = (lineColor >> 8) & 0xFF;
    const uint8_t lineB = lineColor & 0xFF;

    const uint8_t marginR = (marginColor >> 16) & 0xFF;
    const uint8_t marginG = (marginColor >> 8) & 0xFF;
    const uint8_t marginB = marginColor & 0xFF;

    int thick = (lineThickness < 1) ? 1 : lineThickness;
    int halfThick = thick / 2;

    for (int y = 0; y < height; ++y) {
        // Byte‑accurate row pointer
        uint32_t* rowPtr = reinterpret_cast<uint32_t*>(ptr + y * byteStride);

        // Is this row part of a thickened horizontal line?
        bool isLine = false;
        if (lineSpacing > 0) {
            int remainder = y % lineSpacing;
            if (remainder <= halfThick || remainder >= lineSpacing - halfThick) {
                isLine = true;
            }
        }

        bool rowHasMargin = (marginLeft > 0);
        int marginLeftBound = marginLeft - halfThick;
        int marginRightBound = marginLeft + halfThick;

        for (int x = 0; x < width; ++x) {
            uint32_t pixel;

            bool isMargin = rowHasMargin && (x >= marginLeftBound && x <= marginRightBound);

            if (isMargin) {
                pixel = (0xFF << 24) | (marginR << 16) | (marginG << 8) | marginB;
            } else if (isLine) {
                pixel = (0xFF << 24) | (lineR << 16) | (lineG << 8) | lineB;
            } else {
                // Paper grain via fbm noise
                float noiseVal = fbm((float)x, (float)y, seed);
                float brightness = 0.975f + noiseVal * 0.05f;
                brightness = std::min(1.0f, std::max(0.9f, brightness));

                uint8_t r = (uint8_t)(baseR * brightness);
                uint8_t g = (uint8_t)(baseG * brightness);
                uint8_t b = (uint8_t)(baseB * brightness);
                pixel = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }

            rowPtr[x] = pixel;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}
