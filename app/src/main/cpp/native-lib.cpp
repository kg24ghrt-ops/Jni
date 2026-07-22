#include <jni.h>
#include <android/bitmap.h>
#include <EGL/egl.h>
#include <GLES3/gl31.h>
#include <cstring>
#include <ctime>
#include "shader_loader.h"   // provides getPaperProgram()

// ---------- EGL / GL state ----------
static EGLDisplay eglDisplay = EGL_NO_DISPLAY;
static EGLContext eglContext = EGL_NO_CONTEXT;
static EGLSurface eglSurface = EGL_NO_SURFACE;
static bool glInitialized = false;

// ---------- GL initialization (shared with ink_engine.cpp) ----------
bool initOpenGL() {
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

    // Get the pre‑compiled paper program
    GLuint program = getPaperProgram();
    if (!program) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // Create output texture
    GLuint texture;
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, width, height);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glBindImageTexture(0, texture, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA8);

    glUseProgram(program);

    // Helper lambdas for setting uniforms
    auto setVec3 = [&](const char* name, int color) {
        GLint loc = glGetUniformLocation(program, name);
        if (loc >= 0) {
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            glUniform3f(loc, r, g, b);
        }
    };
    auto setFloat = [&](const char* name, float value) {
        GLint loc = glGetUniformLocation(program, name);
        if (loc >= 0) glUniform1f(loc, value);
    };
    auto setUInt = [&](const char* name, uint32_t value) {
        GLint loc = glGetUniformLocation(program, name);
        if (loc >= 0) glUniform1ui(loc, value);
    };

    // Set uniforms – exact same as before
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

    // Read back to bitmap
    // Bind the texture to a framebuffer and read pixels
    GLuint fbo;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glDeleteFramebuffers(1, &fbo);

    // Cleanup
    glDeleteTextures(1, &texture);
    AndroidBitmap_unlockPixels(env, bitmap);
}