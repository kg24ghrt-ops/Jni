#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <algorithm>

// Ink simulation – blends an ink bitmap onto paper, mimicking real ink behaviour
extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_simulateInk(
        JNIEnv *env,
        jobject /* this */,
        jobject paperBitmap,
        jobject inkBitmap,
        jint offsetX,
        jint offsetY) {

    AndroidBitmapInfo paperInfo, inkInfo;
    void *paperPixels, *inkPixels;

    if (AndroidBitmap_getInfo(env, paperBitmap, &paperInfo) < 0) return;
    if (AndroidBitmap_getInfo(env, inkBitmap, &inkInfo) < 0) return;
    if (paperInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (inkInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;

    if (AndroidBitmap_lockPixels(env, paperBitmap, &paperPixels) < 0) return;
    if (AndroidBitmap_lockPixels(env, inkBitmap, &inkPixels) < 0) {
        AndroidBitmap_unlockPixels(env, paperBitmap);
        return;
    }

    auto *paper = static_cast<uint32_t *>(paperPixels);
    auto *ink = static_cast<uint32_t *>(inkPixels);

    const int inkW = inkInfo.width;
    const int inkH = inkInfo.height;
    const int paperW = paperInfo.width;
    const int paperH = paperInfo.height;

    // Simple noise function for ink density variation
    auto hash = [](int x, int y) -> float {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >> 13)) * 1274126177;
        return (n & 0x7fffffff) / 2147483648.0f;  // 0..1
    };

    for (int y = 0; y < inkH; ++y) {
        int py = y + offsetY;
        if (py < 0 || py >= paperH) continue;

        for (int x = 0; x < inkW; ++x) {
            int px = x + offsetX;
            if (px < 0 || px >= paperW) continue;

            uint32_t inkPixel = ink[y * inkW + x];
            uint8_t a = (inkPixel >> 24) & 0xFF;
            if (a == 0) continue;

            // Paper pixel
            uint32_t paperPixel = paper[py * paperW + px];
            uint8_t paperR = (paperPixel >> 16) & 0xFF;
            uint8_t paperG = (paperPixel >> 8) & 0xFF;
            uint8_t paperB = paperPixel & 0xFF;

            // Ink colour (pre‑rendered)
            uint8_t inkR = (inkPixel >> 16) & 0xFF;
            uint8_t inkG = (inkPixel >> 8) & 0xFF;
            uint8_t inkB = inkPixel & 0xFF;

            // Paper brightness influences absorption
            float brightness = (0.299f * paperR + 0.587f * paperG + 0.114f * paperB) / 255.0f;
            float absorb = 0.85f + 0.3f * (1.0f - brightness);   // 0.85 … 1.15

            // Per‑pixel ink density noise
            float noise = hash(px, py) * 0.2f - 0.1f;
            float inkAmount = (a / 255.0f) * absorb + noise;
            inkAmount = std::min(1.0f, std::max(0.0f, inkAmount));

            // Blend
            uint8_t outR = static_cast<uint8_t>(paperR + (inkR - paperR) * inkAmount);
            uint8_t outG = static_cast<uint8_t>(paperG + (inkG - paperG) * inkAmount);
            uint8_t outB = static_cast<uint8_t>(paperB + (inkB - paperB) * inkAmount);

            paper[py * paperW + px] = (0xFF << 24) | (outR << 16) | (outG << 8) | outB;
        }
    }

    AndroidBitmap_unlockPixels(env, paperBitmap);
    AndroidBitmap_unlockPixels(env, inkBitmap);
}