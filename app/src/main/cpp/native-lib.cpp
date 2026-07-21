#include <jni.h>
#include <android/bitmap.h>
#include <EGL/egl.h>
#include <GLES3/gl31.h>
#include <cstring>
#include <ctime>

// ---------- Compute shader source (embedded) ----------
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

// ---------- Hash & noise ----------
uint hash(uint x, uint y, uint seed) {
    uint n = x ^ (y * 0x9e3779b9u) ^ seed;
    n = n ^ (n << 13);
    n = n ^ (n >> 17);
    n = n ^ (n << 5);
    return n;
}

float randHash(uint x, uint y, uint seed) {
    return float(hash(x, y, seed)) / 4294967296.0;
}

float noise(uint x, uint y, uint seed) {
    return randHash(x, y, seed);
}

float smoothNoise(float x, float y, uint seed) {
    uint ix = uint(floor(x));
    uint iy = uint(floor(y));
    float fx = x - float(ix);
    float fy = y - float(iy);
    float sx = fx * fx * (3.0 - 2.0 * fx);
    float sy = fy * fy * (3.0 - 2.0 * fy);

    float n00 = noise(ix,     iy,     seed);
    float n10 = noise(ix + 1u, iy,     seed);
    float n01 = noise(ix,     iy + 1u, seed);
    float n11 = noise(ix + 1u, iy + 1u, seed);

    float nx0 = mix(n00, n10, sx);
    float nx1 = mix(n01, n11, sx);
    return mix(nx0, nx1, sy);
}

float fbm(float x, float y, uint seed) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 0.01;
    float persistence = 0.6;
    for (uint i = 0u; i < 4u; i++) {
        value += amplitude * smoothNoise(x * frequency, y * frequency, seed + i);
        amplitude *= persistence;
        frequency *= 2.0;
    }
    return value;
}

// ---------- Main ----------
void main() {
    ivec2 pos = ivec2(gl_GlobalInvocationID.xy);
    if (pos.x >= int(uImageSize.x) || pos.y >= int(uImageSize.y))
        return;

    float fx = float(pos.x);
    float fy = float(pos.y);

    float halfThick = uLineThickness * 0.5;
    bool isLine = false;
    if (uLineSpacing > 0.0) {
        float rem = mod(fy, uLineSpacing);
        isLine = (rem <= halfThick || rem >= uLineSpacing - halfThick);
    }

    bool hasMargin = (uMarginLeft > 0.0);
    bool isMargin = hasMargin && (fx >= uMarginLeft - halfThick && 
                                  fx <= uMarginLeft + halfThick);

    vec3 color;
    if (isMargin) {
        color = uMarginColor;
    } else if (isLine) {
        color = uLineColor;
    } else {
        float n = fbm(fx, fy, uSeed);
        float brightness = 0.975 + n * 0.05;
        brightness = clamp(brightness, 0.9, 1.0);
        color = uBaseColor * brightness;
    }
    imageStore(outputImage, pos, vec4(color, 1.0));
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

    // Initialize EGL/GL
    if (!initOpenGL()) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // Create compute program
    GLuint program = createComputeProgram();
    if (!program) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // Create texture
    GLuint texture;
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, width, height);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glBindImageTexture(0, texture, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA8);

    glUseProgram(program);

    // Set uniforms
    auto setVec3 = [&](const char* name, int color) {
        GLint loc = glGetUniformLocation(program, name);
        if (loc >= 0) {
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            glUniform3f(loc, r, g, b);
        }
    };

    glUniform2f(glGetUniformLocation(program, "uImageSize"), width, height);
    glUniform1f(glGetUniformLocation(program, "uLineSpacing"), lineSpacing);
    glUniform1f(glGetUniformLocation(program, "uMarginLeft"), marginLeft);
    glUniform1f(glGetUniformLocation(program, "uLineThickness"), lineThickness);
    glUniform1ui(glGetUniformLocation(program, "uSeed"), (uint32_t)time(nullptr) ^ (uint32_t)clock());
    setVec3("uLineColor", lineColor);
    setVec3("uMarginColor", marginColor);
    setVec3("uBaseColor", 0xFDFDFB);

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