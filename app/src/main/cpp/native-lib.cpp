#include <jni.h>
#include <android/bitmap.h>
#include <EGL/egl.h>
#include <GLES3/gl31.h>
#include <cstring>
#include <ctime>

// ---------- Enhanced Realistic Paper Shader ----------
static const char* computeShaderSource = R"(
#version 310 es

layout(local_size_x = 16, local_size_y = 16) in;
layout(rgba8, binding = 0) uniform highp writeonly image2D outputImage;

uniform highp vec2  uImageSize;
uniform highp float uLineSpacing;
uniform highp float uMarginLeft;
uniform highp float uLineThickness;
uniform highp uint  uSeed;
uniform highp vec3  uLineColor;
uniform highp vec3  uMarginColor;
uniform highp vec3  uBaseColor;

// New tunable uniforms
uniform highp float uFiberScale;
uniform highp float uGrainStrength;
uniform highp float uBleedAmount;
uniform highp float uWaveAmplitude;
uniform highp float uInclineSlope;
uniform highp float uSpotThreshold;
uniform highp float uVignetteStrength;
uniform highp float uPaperAge;
uniform highp float uLineWarp;

// ---------- CORE NOISE ----------
uint hash(uint x, uint y, uint seed) {
    uint n = x ^ (y * 0x9e3779b9u) ^ seed;
    n = n ^ (n << 13);
    n = n ^ (n >> 17);
    n = n ^ (n << 5);
    return n;
}

mediump float randHash(uint x, uint y, uint seed) {
    return float(hash(x, y, seed)) / 4294967296.0;
}

mediump float valueNoise(highp float x, highp float y, uint seed) {
    uint ix = uint(floor(x));
    uint iy = uint(floor(y));
    mediump float fx = x - float(ix);
    mediump float fy = y - float(iy);
    mediump float sx = fx * fx * (3.0 - 2.0 * fx);
    mediump float sy = fy * fy * (3.0 - 2.0 * fy);

    mediump float n00 = randHash(ix,     iy,     seed);
    mediump float n10 = randHash(ix + 1u, iy,     seed);
    mediump float n01 = randHash(ix,     iy + 1u, seed);
    mediump float n11 = randHash(ix + 1u, iy + 1u, seed);

    mediump float nx0 = mix(n00, n10, sx);
    mediump float nx1 = mix(n01, n11, sx);
    return mix(nx0, nx1, sy);
}

// ---------- NOISE CACHE ----------
struct NoiseCache {
    mediump float base;
    mediump float detail;
    mediump float warm;
    mediump float wave;
    mediump float imperfection;
    mediump float warp;
    mediump float aging;
};

NoiseCache generateNoiseCache(highp float x, highp float y, uint seed) {
    NoiseCache n;

    n.warp = valueNoise(x * 0.005, y * 0.005, seed + 800u);
    n.aging = valueNoise(x * 0.02, y * 0.02, seed + 900u);

    mediump float grain = 0.0;
    mediump float amp = 0.5;
    mediump float freq = 0.008;
    for (uint i = 0u; i < 4u; i++) {
        grain += amp * valueNoise(x * freq, y * freq, seed + i * 131u);
        amp *= 0.6;
        freq *= 2.0;
    }
    n.base = grain;

    n.detail = randHash(uint(floor(x * 0.5)), uint(floor(y * 0.5)), seed + 500u);
    n.warm = valueNoise(x * 0.03, y * 0.03, seed + 700u);
    n.wave = valueNoise(x * 0.01, float(seed) * 0.1, seed + 1000u);
    n.imperfection = valueNoise(x * 0.6, y * 0.6, seed + 7000u);

    return n;
}

// ---------- VORONOI ----------
mediump float voronoi(highp float x, highp float y, uint seed) {
    highp vec2 p = vec2(floor(x), floor(y));
    mediump float minDistSq = 1.0;

    for (int dy = -1; dy <= 1; dy += 2) {
        for (int dx = -1; dx <= 1; dx += 2) {
            vec2 cell = p + vec2(dx, dy);
            uint h = hash(uint(cell.x), uint(cell.y), seed);
            vec2 offset = vec2(
                float(h & 0xFFFFu) / 65536.0,
                float((h >> 16u) & 0xFFFFu) / 65536.0
            );
            vec2 diff = vec2(
                x - cell.x - offset.x,
                y - cell.y - offset.y
            );
            float d = dot(diff, diff);
            minDistSq = min(minDistSq, d);
        }
    }
    return sqrt(minDistSq);
}

// ---------- PAPER TEXTURE ----------
mediump float paperTexture(highp float x, highp float y, uint seed, NoiseCache n) {
    mediump float v = voronoi(x * uFiberScale, y * uFiberScale, seed + 500u);
    return mix(n.base, v, uGrainStrength);
}

// ---------- INK BLEED ----------
mediump float inkBleed(highp float d, uint seed) {
    mediump float n = valueNoise(d * 8.0, float(seed & 0xFFFFu), seed);
    return clamp(0.4 + 0.6 * n, 0.0, 1.0);
}

// ---------- MAIN ----------
void main() {
    ivec2 pos = ivec2(gl_GlobalInvocationID.xy);
    if (pos.x >= int(uImageSize.x) || pos.y >= int(uImageSize.y))
        return;

    highp float fx = float(pos.x);
    highp float fy = float(pos.y);
    highp float cx = fx / uImageSize.x;
    highp float cy = fy / uImageSize.y;

    NoiseCache n = generateNoiseCache(fx, fy, uSeed);

    // ---------- PAPER COLOR WITH AGING ----------
    vec3 warmBase = vec3(0.95, 0.93, 0.89);
    float aging = n.aging * uPaperAge;
    vec3 agedColor = warmBase + vec3(aging * 0.04, aging * 0.02, -aging * 0.03);
    agedColor = clamp(agedColor, 0.82, 0.98);

    mediump float grain = paperTexture(fx, fy, uSeed, n);
    mediump float brightness = 0.94 + grain * 0.08;
    brightness = clamp(brightness, 0.86, 1.0);
    vec3 paperColor = agedColor * brightness;

    // ---------- VIGNETTE ----------
    vec2 centerVec = vec2(cx - 0.5, cy - 0.5);
    float distFromCenter = length(centerVec);
    float vignette = 1.0 - (distFromCenter * distFromCenter * uVignetteStrength);
    float vignetteNoise = valueNoise(fx * 0.01, fy * 0.01, uSeed + 10000u) * 0.1;
    vignette = clamp(vignette + vignetteNoise, 0.65, 1.0);
    float edgeFalloff = 1.0 - pow(distFromCenter * 1.2, 2.0) * 0.15;
    edgeFalloff = clamp(edgeFalloff, 0.7, 1.0);
    paperColor *= (vignette * edgeFalloff);

    // ---------- LINE WARP ----------
    float halfThick = uLineThickness * 0.5;
    float lineFalloff = 1.5;

    float warpX = n.warp * uLineWarp * 5.0;
    float warpY = valueNoise(fx * 0.008 + 50.0, fy * 0.008 + 50.0, uSeed + 1100u) * uLineWarp * 3.0;

    float waveX = sin(fx * 0.005 + float(uSeed) * 0.1) * uWaveAmplitude;
    float yOffset = waveX + (n.wave * 1.2 - 0.6) + warpY;
    float incline = uInclineSlope * fx;
    highp float effectiveY = fy + yOffset + incline + warpX;

    // ---------- LINE DETECTION ----------
    float lineDist = 9999.0;
    bool isLine = false;
    if (uLineSpacing > 0.0) {
        float rem = mod(effectiveY, uLineSpacing);
        float distToLine = min(rem, uLineSpacing - rem);
        lineDist = distToLine;
        if (distToLine < halfThick + lineFalloff) {
            isLine = true;
        }
    }

    // ---------- MARGIN WITH ROUGH EDGE ----------
    float marginJitter = (n.detail - 0.5) * 1.5;
    float marginWave = sin(fy * 0.003 + float(uSeed) * 0.05) * 0.5;
    float roughEdge = valueNoise(fy * 0.1, float(uSeed) * 0.1, uSeed + 1200u) * 0.8;
    highp float effectiveMarginX = uMarginLeft + marginWave + marginJitter + roughEdge;

    float marginDist = abs(fx - effectiveMarginX);
    bool isMargin = (uMarginLeft > 0.0) && (marginDist < halfThick + lineFalloff);

    // ---------- RENDER ----------
    vec3 finalColor = paperColor;

    if (isMargin) {
        float d = marginDist - halfThick;
        float alpha = 1.0 - smoothstep(0.0, lineFalloff, max(0.0, d));
        float bleed = inkBleed(d, uSeed + 2000u);
        bleed = clamp(bleed * 1.2, 0.0, 1.0);
        float spread = n.warm;
        alpha = clamp(alpha * (0.75 + uBleedAmount * bleed) + (spread - 0.5) * 0.08, 0.0, 1.0);

        vec3 marginInk = uMarginColor;
        float inkVar = (n.detail - 0.5) * 0.05;
        marginInk += inkVar;
        marginInk = clamp(marginInk, 0.0, 1.0);

        finalColor = mix(paperColor, marginInk, alpha);
    } else if (isLine) {
        float d = lineDist - halfThick;
        float alpha = 1.0 - smoothstep(0.0, lineFalloff, max(0.0, d));
        float bleed = inkBleed(d, uSeed + 4000u);
        float density = n.wave;
        alpha = clamp(alpha * (0.85 + 0.15 * density) * (0.75 + 0.25 * bleed), 0.0, 1.0);

        float var = n.warm - 0.5;
        vec3 inkColor = uLineColor + var * 0.04;
        inkColor = clamp(inkColor, 0.0, 1.0);

        finalColor = mix(paperColor, inkColor, alpha);
    }

    // ---------- PAPER IMPERFECTIONS ----------
    float spotEffect = smoothstep(uSpotThreshold, 1.0, n.imperfection);
    finalColor *= (1.0 - spotEffect * 0.06);

    float lightEffect = smoothstep(0.96, 1.0, n.detail);
    finalColor *= (1.0 + lightEffect * 0.04);

    // ---------- OUTPUT ----------
    imageStore(outputImage, pos, vec4(clamp(finalColor, 0.0, 1.0), 1.0));
}
)";

// ---------- EGL / GL state ----------
static EGLDisplay eglDisplay = EGL_NO_DISPLAY;
static EGLContext eglContext = EGL_NO_CONTEXT;
static EGLSurface eglSurface = EGL_NO_SURFACE;
static bool glInitialized = false;

// ---------- Init ----------
static bool initOpenGL() {
    if (glInitialized) return true;

    eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) return false;
    if (!eglInitialize(eglDisplay, nullptr, nullptr)) return false;

    EGLint attribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config;
    EGLint numConfigs;
    if (!eglChooseConfig(eglDisplay, attribs, &config, 1, &numConfigs) || numConfigs == 0)
        return false;

    EGLint pbufferAttribs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    eglSurface = eglCreatePbufferSurface(eglDisplay, config, pbufferAttribs);
    if (eglSurface == EGL_NO_SURFACE) return false;

    EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    eglContext = eglCreateContext(eglDisplay, config, EGL_NO_CONTEXT, ctxAttribs);
    if (eglContext == EGL_NO_CONTEXT) return false;

    if (!eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
        return false;

    glInitialized = true;
    return true;
}

// ---------- Compile shader ----------
static GLuint createComputeProgram() {
    GLuint shader = glCreateShader(GL_COMPUTE_SHADER);
    glShaderSource(shader, 1, &computeShaderSource, nullptr);
    glCompileShader(shader);

    GLint status;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status) return 0;

    GLuint program = glCreateProgram();
    glAttachShader(program, shader);
    glLinkProgram(program);
    glGetProgramiv(program, GL_LINK_STATUS, &status);
    if (!status) return 0;
    glDeleteShader(shader);
    return program;
}

// ---------- JNI entry point ----------
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

    if (!initOpenGL()) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    GLuint program = createComputeProgram();
    if (!program) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    GLuint texture;
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, width, height);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glBindImageTexture(0, texture, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA8);

    glUseProgram(program);

    // Helper for vec3 uniforms from int color
    auto setVec3 = [&](const char* name, int color) {
        GLint loc = glGetUniformLocation(program, name);
        if (loc >= 0) {
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            glUniform3f(loc, r, g, b);
        }
    };

    // Helper for float uniforms
    auto setFloat = [&](const char* name, float value) {
        GLint loc = glGetUniformLocation(program, name);
        if (loc >= 0) glUniform1f(loc, value);
    };

    // Helper for uint uniforms
    auto setUInt = [&](const char* name, uint32_t value) {
        GLint loc = glGetUniformLocation(program, name);
        if (loc >= 0) glUniform1ui(loc, value);
    };

    // Set all uniforms
    glUniform2f(glGetUniformLocation(program, "uImageSize"), width, height);
    glUniform1f(glGetUniformLocation(program, "uLineSpacing"), lineSpacing);
    glUniform1f(glGetUniformLocation(program, "uMarginLeft"), marginLeft);
    glUniform1f(glGetUniformLocation(program, "uLineThickness"), lineThickness);
    setUInt("uSeed", (uint32_t)time(nullptr) ^ (uint32_t)clock());
    setVec3("uLineColor", lineColor);
    setVec3("uMarginColor", marginColor);
    setVec3("uBaseColor", 0xFDFDFB);

    // New tunable parameters (defaults)
    setFloat("uFiberScale", 0.2f);
    setFloat("uGrainStrength", 0.3f);
    setFloat("uBleedAmount", 0.25f);
    setFloat("uWaveAmplitude", 0.8f);
    setFloat("uInclineSlope", 0.002f);
    setFloat("uSpotThreshold", 0.97f);
    setFloat("uVignetteStrength", 0.3f);
    setFloat("uPaperAge", 0.5f);
    setFloat("uLineWarp", 0.3f);

    // Dispatch
    int workX = (width + 15) / 16;
    int workY = (height + 15) / 16;
    glDispatchCompute(workX, workY, 1);
    glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

    // Read back
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

    // Cleanup
    glDeleteProgram(program);
    glDeleteTextures(1, &texture);
    AndroidBitmap_unlockPixels(env, bitmap);
}