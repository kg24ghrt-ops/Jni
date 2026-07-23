#include "shader_loader.h"
#include <android/asset_manager.h>
#include <vector>
#include <cstring>

static AAssetManager* g_assetManager = nullptr;

void initShaderLoader(AAssetManager* mgr) {
    g_assetManager = mgr;
}

// --------------------------------------------------------------
// Load a shader from an asset path (e.g., "shaders/paper_generation.spv")
// --------------------------------------------------------------
static GLuint loadShaderFromAsset(const char* assetPath) {
    if (!g_assetManager) return 0;

    AAsset* asset = AAssetManager_open(g_assetManager, assetPath, AASSET_MODE_BUFFER);
    if (!asset) return 0;

    size_t size = AAsset_getLength(asset);
    const void* data = AAsset_getBuffer(asset);
    if (!data) {
        AAsset_close(asset);
        return 0;
    }

    GLuint shader = glCreateShader(GL_COMPUTE_SHADER);
    if (!shader) {
        AAsset_close(asset);
        return 0;
    }

    // Load SPIR‑V binary (OpenGL ES 3.1+ supports this)
    glShaderBinary(1, &shader, GL_SHADER_BINARY_FORMAT_SPIR_V, data, size);
    if (glGetError() != GL_NO_ERROR) {
        glDeleteShader(shader);
        AAsset_close(asset);
        return 0;
    }

    // Specialize the shader (entry point "main")
    glSpecializeShader(shader, "main", 0, nullptr, nullptr);
    if (glGetError() != GL_NO_ERROR) {
        glDeleteShader(shader);
        AAsset_close(asset);
        return 0;
    }

    AAsset_close(asset);
    return shader;
}

// --------------------------------------------------------------
// Create a program from a shader asset
// --------------------------------------------------------------
static GLuint createProgramFromAsset(const char* assetPath) {
    GLuint shader = loadShaderFromAsset(assetPath);
    if (!shader) return 0;

    GLuint program = glCreateProgram();
    glAttachShader(program, shader);
    glLinkProgram(program);

    GLint status;
    glGetProgramiv(program, GL_LINK_STATUS, &status);
    if (!status) {
        char log[512];
        glGetProgramInfoLog(program, sizeof(log), nullptr, log);
        glDeleteProgram(program);
        glDeleteShader(shader);
        return 0;
    }
    glDeleteShader(shader);
    return program;
}

// --------------------------------------------------------------
// Cached programs (load once and reuse)
// --------------------------------------------------------------

GLuint getCapillaryProgram() {
    static GLuint program = 0;
    if (!program) {
        program = createProgramFromAsset("shaders/capillary_map.spv");
    }
    return program;
}

GLuint getPhysicsProgram() {
    static GLuint program = 0;
    if (!program) {
        program = createProgramFromAsset("shaders/ink_physics.spv");
    }
    return program;
}

GLuint getCompositeProgram() {
    static GLuint program = 0;
    if (!program) {
        program = createProgramFromAsset("shaders/ink_composite.spv");
    }
    return program;
}

GLuint getPaperProgram() {
    static GLuint program = 0;
    if (!program) {
        program = createProgramFromAsset("shaders/paper_generation.spv");
    }
    return program;
}