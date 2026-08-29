package com.example.homecil.native

import android.graphics.Bitmap

/**
 * Native JNI bridge for the paper rendering engine.
 * This class provides direct access to the C++ native functions.
 */
object PaperEngineNative {

    // Load the native library
    init {
        System.loadLibrary("native-lib")
    }

    /**
     * Render realistic paper texture into a Bitmap.
     *
     * @param bitmap The target Bitmap to render into
     * @param width Width of the bitmap
     * @param height Height of the bitmap
     * @param seed Random seed for reproducible textures
     * @param grainIntensity Intensity of paper grain (0-1)
     * @param fiberDensity Density of cellulose fibers (0-1)
     * @param waterStainCount Number of water stains to add
     * @param agingYellow Amount of yellowing/aging effect (0-1)
     * @param fiberDirection Directional bias for fibers (-1 to 1)
     * @param roughness Overall paper roughness (0-1)
     */
    external fun renderPaper(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        seed: Int,
        grainIntensity: Float,
        fiberDensity: Float,
        waterStainCount: Int,
        agingYellow: Float,
        fiberDirection: Float,
        roughness: Float
    )

    /**
     * Simulate ink absorption and bleeding on paper.
     *
     * @param bitmap The target paper bitmap
     * @param inkBitmap The ink stamp bitmap to apply
     * @param x X position to apply ink
     * @param y Y position to apply ink
     * @param inkColorR Red component of ink color (0-1)
     * @param inkColorG Green component of ink color (0-1)
     * @param inkColorB Blue component of ink color (0-1)
     * @param absorption How much the paper absorbs ink (0-1)
     * @param noiseIntensity Variation in ink density (0-1)
     * @param seed Random seed for reproducible results
     */
    external fun simulateInk(
        bitmap: Bitmap,
        inkBitmap: Bitmap,
        x: Int,
        y: Int,
        inkColorR: Float,
        inkColorG: Float,
        inkColorB: Float,
        absorption: Float,
        noiseIntensity: Float,
        seed: Int
    )

    /**
     * Simplified ink simulation for direct color stamping.
     *
     * @param bitmap The target paper bitmap
     * @param x X position
     * @param y Y position
     * @param width Width of area to fill
     * @param height Height of area to fill
     * @param inkColorR Red component (0-1)
     * @param inkColorG Green component (0-1)
     * @param inkColorB Blue component (0-1)
     * @param opacity Opacity of ink (0-1)
     */
    external fun simulateInkSimple(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        inkColorR: Float,
        inkColorG: Float,
        inkColorB: Float,
        opacity: Float
    )

    /**
     * Distort a bitmap to simulate hand-drawn imperfections.
     *
     * @param bitmap The bitmap to distort
     * @param seed Random seed for reproducible distortion
     * @param distortionScale Overall scale of distortion
     * @param sineWarpScale Scale of sinusoidal warp
     * @param curvatureScale Scale of geometry-aware curvature modulation
     */
    external fun distortBitmap(
        bitmap: Bitmap,
        seed: Int,
        distortionScale: Float,
        sineWarpScale: Float,
        curvatureScale: Float
    )

    /**
     * Fast distortion for character bitmaps.
     *
     * @param bitmap The character bitmap to distort
     * @param seed Random seed for reproducible distortion
     * @param scale Overall scale of distortion
     */
    external fun distortCharacter(
        bitmap: Bitmap,
        seed: Int,
        scale: Float
    )
}
