#pragma once

#include <GLES3/gl31.h>

// Load a shader from embedded SPIR‑V (OpenGL ES)
GLuint loadShaderFromSPIRV(const void* data, size_t size);

// Create compute program from embedded SPIR‑V
GLuint createComputeProgram(const void* data, size_t size);

// Get cached program for each shader
GLuint getCapillaryProgram();
GLuint getPhysicsProgram();
GLuint getCompositeProgram();
GLuint getPaperProgram();