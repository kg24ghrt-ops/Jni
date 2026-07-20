#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <algorithm>
#include <cstdlib>

// ---------- helper: clamp ----------
static inline float clamp(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

// ---------- coherent noise (same as before) ----------
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

// ---------- deterministic random from seed ----------
static float randFromSeed(int seed, int offset) {
    int n = seed * 131 + offset * 374761393;
    n = (n ^ (n >> 13)) * 1274126177;
    return (n & 0x7fffffff) / 2147483648.0f;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_distortBitmap(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap,
        jfloat strength,
        jint seed) {

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

    // ---------- Step 1: Extract ink alpha and compute gradients ----------
    uint8_t* alpha = new uint8_t[w * h]();   // 0 for background, non-zero for ink
    for (int i = 0; i < w * h; ++i) {
        alpha[i] = (copy[i] >> 24) & 0xFF;
    }

    // Sobel gradients (on alpha) to get stroke orientation
    float* gradX = new float[w * h]();
    float* gradY = new float[w * h]();
    for (int y = 1; y < h - 1; ++y) {
        for (int x = 1; x < w - 1; ++x) {
            int idx = y * w + x;
            float gx = -alpha[idx - w - 1] + alpha[idx - w + 1]
                      -2*alpha[idx - 1]     + 2*alpha[idx + 1]
                      -alpha[idx + w - 1]   + alpha[idx + w + 1];
            float gy = -alpha[idx - w - 1] -2*alpha[idx - w] - alpha[idx - w + 1]
                      + alpha[idx + w - 1] +2*alpha[idx + w] + alpha[idx + w + 1];
            gradX[idx] = gx;
            gradY[idx] = gy;
        }
    }

    // ---------- Step 2: Compute orientation and curvature ----------
    float* orientation = new float[w * h]();
    float* curvature = new float[w * h]();
    for (int y = 2; y < h - 2; ++y) {
        for (int x = 2; x < w - 2; ++x) {
            int idx = y * w + x;
            if (alpha[idx] == 0) continue;
            float gx = gradX[idx];
            float gy = gradY[idx];
            float mag = sqrtf(gx * gx + gy * gy);
            if (mag < 1e-3f) continue;
            orientation[idx] = atan2f(gy, gx);   // direction of gradient (perpendicular to stroke)

            // Approximate curvature by change in orientation (averaged over 2-pixel radius)
            float avgDiff = 0.0f;
            int count = 0;
            for (int dy = -2; dy <= 2; ++dy) {
                for (int dx = -2; dx <= 2; ++dx) {
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                        int nidx = ny * w + nx;
                        if (alpha[nidx] > 0 && (dx != 0 || dy != 0)) {
                            float diff = orientation[idx] - orientation[nidx];
                            // Normalize angle difference to [-PI, PI]
                            if (diff > M_PI) diff -= 2 * M_PI;
                            if (diff < -M_PI) diff += 2 * M_PI;
                            avgDiff += fabsf(diff);
                            count++;
                        }
                    }
                }
            }
            if (count > 0) curvature[idx] = avgDiff / count;
        }
    }

    // ---------- Step 3: Displacement with geometry‑aware modulation ----------
    int seedX = seed * 2;
    int seedY = seed * 2 + 1;
    float fbmScale = 0.08f;
    float sinAmplitude = strength * 0.4f;   // reduced global sine warp
    float phaseX = randFromSeed(seed, 0) * 2.0f * M_PI;
    float phaseY = randFromSeed(seed, 1) * 2.0f * M_PI;
    float freqX = 0.02f + randFromSeed(seed, 2) * 0.04f;
    float freqY = 0.02f + randFromSeed(seed, 3) * 0.04f;

    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            int idx = y * w + x;
            if (alpha[idx] == 0) continue;   // skip background

            // Base noise displacement
            float dx1 = fbm((float)x, (float)y, seedX, fbmScale) * 2.0f - 1.0f;
            float dy1 = fbm((float)x + 1000, (float)y + 1000, seedY, fbmScale) * 2.0f - 1.0f;

            // Sine warp
            float dx2 = sinAmplitude * sinf(freqY * y + phaseX);
            float dy2 = sinAmplitude * sinf(freqX * x + phaseY);

            float baseDx = dx1 + dx2;
            float baseDy = dy1 + dy2;

            // Geometry‑aware modulation
            float curv = curvature[idx];
            float orient = orientation[idx];

            // Compute perpendicular direction to stroke (gradient direction is normal to stroke)
            float perpX = cosf(orient);   // gradient unit vector
            float perpY = sinf(orient);

            // Stroke direction is perpendicular to gradient
            float strokeX = -perpY;
            float strokeY = perpX;

            // Curvature factor: high curvature (corners) -> reduce displacement
            float curveFactor = 1.0f - clamp(curv * 2.0f, 0.0f, 0.9f);   // 0.1 to 1.0

            // Anisotropy: more displacement perpendicular to stroke than parallel
            float perpStrength = 1.0f;
            float parStrength = 0.3f;

            float dx = (strokeX * baseDx * parStrength + perpX * baseDx * perpStrength) * curveFactor;
            float dy = (strokeY * baseDy * parStrength + perpY * baseDy * perpStrength) * curveFactor;

            float tx = x + dx * strength;
            float ty = y + dy * strength;

            int sx = (int)roundf(tx);
            int sy = (int)roundf(ty);
            if (sx < 0) sx = 0;
            if (sx >= w) sx = w - 1;
            if (sy < 0) sy = 0;
            if (sy >= h) sy = h - 1;

            src[idx] = copy[sy * w + sx];
        }
    }

    // cleanup
    delete[] copy;
    delete[] alpha;
    delete[] gradX;
    delete[] gradY;
    delete[] orientation;
    delete[] curvature;
    AndroidBitmap_unlockPixels(env, bitmap);
}