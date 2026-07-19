#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <algorithm>

// … noise and fbm functions remain exactly the same …

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

    auto* ptr = static_cast<uint8_t*>(pixels);       // work in bytes
    uint32_t byteStride = info.stride;               // stride is in bytes
    int seed = 42;

    const uint8_t baseR = 0xFD;
    const uint8_t baseG = 0xFD;
    const uint8_t baseB = 0xFB;

    const uint8_t lineR = (lineColor >> 16) & 0xFF;
    const uint8_t lineG = (lineColor >> 8) & 0xFF;
    const uint8_t lineB = lineColor & 0xFF;

    const uint8_t marginR = (marginColor >> 16) & 0xFF;
    const uint8_t marginG = (marginColor >> 8) & 0xFF;
    const uint8_t marginB = marginColor & 0xFF;

    int thick = (lineThickness < 1) ? 1 : lineThickness;
    int halfThick = thick / 2;

    for (int y = 0; y < height; ++y) {
        // TRUE byte‑accurate row pointer:
        uint32_t* rowPtr = reinterpret_cast<uint32_t*>(ptr + y * byteStride);

        bool isLine = false;
        if (lineSpacing > 0) {
            int remainder = y % lineSpacing;
            if (remainder <= halfThick || remainder >= lineSpacing - halfThick) {
                isLine = true;
            }
        }

        bool rowHasMargin = (marginLeft > 0 && thick > 0);
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