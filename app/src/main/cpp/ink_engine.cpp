#include <jni.h>
#include <android/bitmap.h>
#include <EGL/egl.h>
#include <GLES3/gl31.h>
#include <cstring>
#include <ctime>

// ---------- Constants ----------
static const float SIMULATION_SCALE = 0.5f; // 50% resolution

// ---------- GPU Resources ----------
static GLuint physicsProgram = 0;
static GLuint compositeProgram = 0;
static GLuint capillaryProgram = 0;

static GLuint paperTexture = 0;
static GLuint capillaryMap = 0;
static GLuint inkTextureA = 0;  // ping
static GLuint inkTextureB = 0;  // pong
static GLuint outputTexture = 0;

static bool useTextureA = true;
static int fullWidth = 0;
static int fullHeight = 0;
static int simWidth = 0;
static int simHeight = 0;

// Dirty rectangle tracking
struct DirtyRect {
    int x, y, w, h;
    bool valid;
} dirtyRect = {0, 0, 0, 0, false};

// ---------- JNI Entry Point ----------
extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_simulateInk(
        JNIEnv *env,
        jobject /* this */,
        jobject paperBitmap,
        jobject inkBitmap,
        jint offsetX,
        jint offsetY) {

    // 1. Lock bitmaps
    AndroidBitmapInfo paperInfo, inkInfo;
    void *paperPixels, *inkPixels;
    if (AndroidBitmap_getInfo(env, paperBitmap, &paperInfo) < 0) return;
    if (AndroidBitmap_getInfo(env, inkBitmap, &inkInfo) < 0) return;
    if (AndroidBitmap_lockPixels(env, paperBitmap, &paperPixels) < 0) return;
    if (AndroidBitmap_lockPixels(env, inkBitmap, &inkPixels) < 0) {
        AndroidBitmap_unlockPixels(env, paperBitmap);
        return;
    }

    // 2. Initialize GL if needed
    if (!initOpenGL()) {
        AndroidBitmap_unlockPixels(env, paperBitmap);
        AndroidBitmap_unlockPixels(env, inkBitmap);
        return;
    }

    // 3. Create/update textures
    fullWidth = paperInfo.width;
    fullHeight = paperInfo.height;
    simWidth = (int)(fullWidth * SIMULATION_SCALE);
    simHeight = (int)(fullHeight * SIMULATION_SCALE);

    ensureTextures();

    // 4. Create stamp texture
    GLuint stampTexture = uploadStampTexture(inkPixels, inkInfo.width, inkInfo.height);

    // 5. Update dirty rectangle
    updateDirtyRect(offsetX, offsetY, inkInfo.width, inkInfo.height);

    // 6. Run physics simulation (at lower resolution)
    runPhysicsSimulation(stampTexture);

    // 7. Composite full resolution
    runComposite();

    // 8. Read back to bitmap
    readBackToBitmap(paperPixels);

    // 9. Cleanup
    glDeleteTextures(1, &stampTexture);
    AndroidBitmap_unlockPixels(env, paperBitmap);
    AndroidBitmap_unlockPixels(env, inkBitmap);
}

// ---------- Helper Functions ----------

void ensureTextures() {
    if (paperTexture == 0) {
        // Create paper texture
        glGenTextures(1, &paperTexture);
        glBindTexture(GL_TEXTURE_2D, paperTexture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, fullWidth, fullHeight);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Generate capillary map (once)
        generateCapillaryMap();

        // Create ink textures (simulation resolution)
        glGenTextures(1, &inkTextureA);
        glGenTextures(1, &inkTextureB);
        glBindTexture(GL_TEXTURE_2D, inkTextureA);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, simWidth, simHeight);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        glBindTexture(GL_TEXTURE_2D, inkTextureB);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, simWidth, simHeight);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Clear both to transparent
        uint32_t* clearPixels = new uint32_t[simWidth * simHeight];
        memset(clearPixels, 0, simWidth * simHeight * sizeof(uint32_t));
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, simWidth, simHeight,
                        GL_RGBA, GL_UNSIGNED_BYTE, clearPixels);
        glBindTexture(GL_TEXTURE_2D, inkTextureB);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, simWidth, simHeight,
                        GL_RGBA, GL_UNSIGNED_BYTE, clearPixels);
        delete[] clearPixels;

        // Create output texture (full resolution)
        glGenTextures(1, &outputTexture);
        glBindTexture(GL_TEXTURE_2D, outputTexture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, fullWidth, fullHeight);
    }
}

void generateCapillaryMap() {
    // Compile capillary shader (once)
    if (capillaryProgram == 0) {
        capillaryProgram = createComputeProgram(capillaryShaderSource);
    }

    // Create capillary map texture (RG32F)
    glGenTextures(1, &capillaryMap);
    glBindTexture(GL_TEXTURE_2D, capillaryMap);
    glTexStorage2D(GL_TEXTURE_2D, 1, GL_RG32F, simWidth, simHeight);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

    // Dispatch
    glUseProgram(capillaryProgram);
    glBindImageTexture(0, capillaryMap, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RG32F);
    glUniform2f(glGetUniformLocation(capillaryProgram, "uMapSize"), simWidth, simHeight);
    glUniform1ui(glGetUniformLocation(capillaryProgram, "uSeed"), (uint32_t)time(nullptr));

    int workX = (simWidth + 15) / 16;
    int workY = (simHeight + 15) / 16;
    glDispatchCompute(workX, workY, 1);
    glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
}

void updateDirtyRect(int offsetX, int offsetY, int stampW, int stampH) {
    // Convert to simulation coordinates
    int simX = (int)(offsetX * SIMULATION_SCALE);
    int simY = (int)(offsetY * SIMULATION_SCALE);
    int simW = (int)(stampW * SIMULATION_SCALE) + 4; // padding
    int simH = (int)(stampH * SIMULATION_SCALE) + 4;

    if (!dirtyRect.valid) {
        dirtyRect = {simX, simY, simW, simH, true};
    } else {
        // Expand dirty rectangle to include new stamp
        int x1 = std::min(dirtyRect.x, simX);
        int y1 = std::min(dirtyRect.y, simY);
        int x2 = std::max(dirtyRect.x + dirtyRect.w, simX + simW);
        int y2 = std::max(dirtyRect.y + dirtyRect.h, simY + simH);
        dirtyRect = {x1, y1, x2 - x1, y2 - y1, true};
    }

    // Clamp to simulation size
    dirtyRect.x = std::max(0, dirtyRect.x);
    dirtyRect.y = std::max(0, dirtyRect.y);
    dirtyRect.w = std::min(simWidth - dirtyRect.x, dirtyRect.w);
    dirtyRect.h = std::min(simHeight - dirtyRect.y, dirtyRect.h);
}

void runPhysicsSimulation(GLuint stampTexture) {
    if (physicsProgram == 0) {
        physicsProgram = createComputeProgram(inkPhysicsShaderSource);
    }

    GLuint inputTex = useTextureA ? inkTextureA : inkTextureB;
    GLuint outputTex = useTextureA ? inkTextureB : inkTextureA;

    glUseProgram(physicsProgram);

    // Bind textures
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, stampTexture);
    glUniform1i(glGetUniformLocation(physicsProgram, "uStampTexture"), 0);

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, capillaryMap);
    glUniform1i(glGetUniformLocation(physicsProgram, "uCapillaryMap"), 1);

    // Bind ink textures
    glBindImageTexture(0, inputTex, 0, GL_FALSE, 0, GL_READ_ONLY, GL_RGBA8);
    glBindImageTexture(1, outputTex, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA8);

    // Set uniforms
    auto setFloat = [&](const char* name, float value) {
        GLint loc = glGetUniformLocation(physicsProgram, name);
        if (loc >= 0) glUniform1f(loc, value);
    };
    auto setVec2 = [&](const char* name, float x, float y) {
        GLint loc = glGetUniformLocation(physicsProgram, name);
        if (loc >= 0) glUniform2f(loc, x, y);
    };
    auto setVec4 = [&](const char* name, float x, float y, float z, float w) {
        GLint loc = glGetUniformLocation(physicsProgram, name);
        if (loc >= 0) glUniform4f(loc, x, y, z, w);
    };

    setVec2("uSimSize", simWidth, simHeight);
    setVec2("uFullSize", fullWidth, fullHeight);
    setVec2("uStampSize", (float)(fullWidth * SIMULATION_SCALE), (float)(fullHeight * SIMULATION_SCALE));
    setVec2("uStampOffset", 0.0f, 0.0f);
    setVec4("uDirtyRect", dirtyRect.x, dirtyRect.y, dirtyRect.w, dirtyRect.h);
    setFloat("uDiffusionRate", 0.08f);
    setFloat("uCapillaryStrength", 0.3f);
    setFloat("uAbsorption", 0.02f);
    setFloat("uEvaporation", 0.001f);
    setFloat("uTimeStep", 0.05f);

    // Dispatch only the dirty region (or the whole simulation if dirty rect is large)
    int workX = (simWidth + 15) / 16;
    int workY = (simHeight + 15) / 16;
    glDispatchCompute(workX, workY, 1);
    glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

    // Swap for next iteration
    useTextureA = !useTextureA;
}

void runComposite() {
    if (compositeProgram == 0) {
        compositeProgram = createComputeProgram(inkCompositeShaderSource);
    }

    GLuint inkTex = useTextureA ? inkTextureA : inkTextureB;

    glUseProgram(compositeProgram);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, paperTexture);
    glUniform1i(glGetUniformLocation(compositeProgram, "uPaperTexture"), 0);

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, inkTex);
    glUniform1i(glGetUniformLocation(compositeProgram, "uInkTexture"), 1);

    glBindImageTexture(0, outputTexture, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA8);

    glUniform2f(glGetUniformLocation(compositeProgram, "uFullSize"), fullWidth, fullHeight);
    glUniform2f(glGetUniformLocation(compositeProgram, "uSimSize"), simWidth, simHeight);

    int workX = (fullWidth + 15) / 16;
    int workY = (fullHeight + 15) / 16;
    glDispatchCompute(workX, workY, 1);
    glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
}

void readBackToBitmap(void* paperPixels) {
    GLuint fbo;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, outputTexture, 0);
    glReadPixels(0, 0, fullWidth, fullHeight, GL_RGBA, GL_UNSIGNED_BYTE, paperPixels);
    glDeleteFramebuffers(1, &fbo);
}