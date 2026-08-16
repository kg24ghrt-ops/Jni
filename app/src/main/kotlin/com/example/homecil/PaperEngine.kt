package com.example.homecil

import java.nio.ByteBuffer

object PaperEngine {
    init { 
        // Loads the C++ JNI bridge (which is linked to the Rust engine via CMake)
        System.loadLibrary("paper_jni") 
    }

    external fun createHeadless(width: Int, height: Int): Long
    external fun destroy(engineHandle: Long)
    external fun generate(handle: Long, width: Int, height: Int, seed: Int, grain: Float, fiber: Float, water: Int, aging: Float, direction: Float, roughness: Float): Int
    external fun readPixels(handle: Long, buffer: ByteBuffer, bufferSize: Int): Int
}