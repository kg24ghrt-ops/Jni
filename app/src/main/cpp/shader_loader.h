#pragma once

#include <GLES3/gl31.h>
#include <android/asset_manager.h>

// Call this once from Java with the AssetManager
void initShaderLoader(AAssetManager* mgr);

// Get cached compute programs (load from assets/shaders/)
GLuint getCapillaryProgram();
GLuint getPhysicsProgram();
GLuint getCompositeProgram();
GLuint getPaperProgram();