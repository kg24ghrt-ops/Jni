#include <jni.h>
#include <android/log.h>
#include <stdint.h>

// C-ABI declarations matching your Rust library exactly
extern "C" {
    struct PaperParams {
        uint32_t width;
        uint32_t height;
        uint32_t seed;
        float    grain_intensity;
        float    fiber_density;
        uint32_t water_stain_count;
        float    aging_yellow;
        float    fiber_direction;
        float    roughness;
        float    _pad0;
        float    _pad1;
    };

    void* paper_engine_create_headless(uint32_t width, uint32_t height);
    void paper_engine_destroy(void* engine_handle);
    int paper_engine_generate(void* engine_handle, const struct PaperParams* params);
    int paper_engine_read_pixels(void* engine_handle, uint8_t* out_rgba8, size_t buffer_size);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_homecil_PaperEngine_createHeadless(JNIEnv* env, jobject thiz, jint width, jint height) {
    return (jlong)paper_engine_create_headless(width, height);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperEngine_destroy(JNIEnv* env, jobject thiz, jlong handle) {
    paper_engine_destroy((void*)handle);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_homecil_PaperEngine_generate(JNIEnv* env, jobject thiz, jlong handle, 
                                                jint width, jint height, jint seed,
                                                jfloat grain, jfloat fiber, jint water,
                                                jfloat aging, jfloat direction, jfloat roughness) {
    PaperParams params;
    params.width = width;
    params.height = height;
    params.seed = seed;
    params.grain_intensity = grain;
    params.fiber_density = fiber;
    params.water_stain_count = water;
    params.aging_yellow = aging;
    params.fiber_direction = direction;
    params.roughness = roughness;
    params._pad0 = 0.0f;
    params._pad1 = 0.0f;
    
    return paper_engine_generate((void*)handle, &params);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_homecil_PaperEngine_readPixels(JNIEnv* env, jobject thiz, jlong handle, jobject buffer, jint buffer_size) {
    uint8_t* pixels = (uint8_t*)env->GetDirectBufferAddress(buffer);
    if (!pixels) return -1;
    return paper_engine_read_pixels((void*)handle, pixels, (size_t)buffer_size);
}