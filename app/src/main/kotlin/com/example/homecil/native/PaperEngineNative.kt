package com.example.homecil.native

import android.graphics.Bitmap

/**
 * Native JNI bridge for the paper rendering engine.
 * This class provides direct access to the C++ native functions and Vulkan GPU rendering.
 */
object PaperEngineNative {

    // Load the native library
    init {
        System.loadLibrary("native-lib")
    }

    /**
     * Render realistic paper texture into a Bitmap (CPU).
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
     * Render paper texture with ruling lines (for export/printing).
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
     * @param lineSpacing Spacing between horizontal lines in pixels
     * @param marginX Left margin position in pixels
     * @param lineColor Color of the ruling lines (ARGB)
     * @param showMarginLine Whether to show the vertical margin line
     * @param showHeaderSpace Whether to reserve header space at the top
     * @param headerHeight Height of the header space in pixels
     * @param lineWidth Width of the ruling lines in pixels
     * @param showVerticalLines Whether to show vertical lines (for graph paper)
     * @param verticalLineSpacing Spacing between vertical lines in pixels
     */
    external fun renderPaperWithRuling(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        seed: Int,
        grainIntensity: Float,
        fiberDensity: Float,
        waterStainCount: Int,
        agingYellow: Float,
        fiberDirection: Float,
        roughness: Float,
        lineSpacing: Float,
        marginX: Float,
        lineColor: Int,
        showMarginLine: Boolean,
        showHeaderSpace: Boolean,
        headerHeight: Float,
        lineWidth: Float,
        showVerticalLines: Boolean,
        verticalLineSpacing: Float
    )

    /**
     * Render paper texture using multi-threading (CPU).
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
     * @param threadCount Number of threads to use (1+)
     */
    external fun renderPaperMT(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        seed: Int,
        grainIntensity: Float,
        fiberDensity: Float,
        waterStainCount: Int,
        agingYellow: Float,
        fiberDirection: Float,
        roughness: Float,
        threadCount: Int
    )

    /**
     * Render paper texture using Vulkan GPU compute shader.
     * This provides the best performance on devices with Vulkan support.
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
    external fun renderPaperVulkan(
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
     * Simulate ink absorption and bleeding on paper (CPU).
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
     * Simulate ink using Vulkan GPU compute shader.
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
    external fun simulateInkVulkan(
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
     * Simplified ink simulation for direct color stamping (CPU).
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
     * Distort a bitmap to simulate hand-drawn imperfections (CPU).
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
     * Distort a bitmap using Vulkan GPU compute shader.
     *
     * @param bitmap The bitmap to distort
     * @param seed Random seed for reproducible distortion
     * @param distortionScale Overall scale of distortion
     * @param sineWarpScale Scale of sinusoidal warp
     * @param curvatureScale Scale of geometry-aware curvature modulation
     */
    external fun distortBitmapVulkan(
        bitmap: Bitmap,
        seed: Int,
        distortionScale: Float,
        sineWarpScale: Float,
        curvatureScale: Float
    )

    /**
     * Fast distortion for character bitmaps (CPU).
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

    /**
     * Get the number of available CPU cores.
     *
     * @return Number of CPU cores
     */
    external fun getCpuCoreCount(): Int

    /**
     * Check if NEON SIMD is available on this device.
     *
     * @return true if NEON is available
     */
    external fun hasNeonSupport(): Boolean

    /**
     * Check if SSE SIMD is available on this device.
     *
     * @return true if SSE is available
     */
    external fun hasSseSupport(): Boolean

    /**
     * Check if Vulkan is supported on this device.
     *
     * @return true if Vulkan is available
     */
    external fun hasVulkanSupport(): Boolean

    /**
     * Initialize Vulkan context for GPU rendering.
     *
     * @param useComputeOnly If true, only initialize compute queues (no graphics)
     * @return true if initialization succeeded
     */
    external fun initVulkan(useComputeOnly: Boolean): Boolean

    /**
     * Shutdown Vulkan context.
     */
    external fun shutdownVulkan()

    /**
     * Get Vulkan device information.
     *
     * @return String containing device info
     */
    external fun getVulkanDeviceInfo(): String

    /**
     * Get device capability information as a formatted string.
     */
    fun getCapabilities(): String {
        val vulkanSupported = hasVulkanSupport()
        val neonSupported = hasNeonSupport()
        val sseSupported = hasSseSupport()
        val cores = getCpuCoreCount()
        
        return """
        | Capability | Available |
        |------------|----------|
        | CPU Cores | $cores |
        | NEON Support | $neonSupported |
        | SSE Support | $sseSupported |
        | Vulkan Support | $vulkanSupported |
        | Recommended Threads | ${recommendedThreadCount()} |
        ${if (vulkanSupported) "\n" + getVulkanDeviceInfo() else ""}
        """.trimMargin()
    }

    /**
     * Get the recommended number of threads for rendering.
     * Uses CPU core count but caps it for optimal performance.
     */
    fun recommendedThreadCount(): Int {
        val cores = getCpuCoreCount()
        // For small bitmaps, single thread is faster due to overhead
        // For large bitmaps, use up to 4 threads (diminishing returns after that)
        return when {
            cores <= 2 -> cores
            cores <= 4 -> cores
            else -> 4  // Cap at 4 for most mobile devices
        }
    }

    /**
     * Determine the best rendering backend for the current device.
     */
    fun getBestRenderingBackend(): RenderingBackend {
        return if (hasVulkanSupport()) {
            RenderingBackend.VULKAN
        } else if (getCpuCoreCount() >= 4) {
            RenderingBackend.MULTI_THREADED
        } else {
            RenderingBackend.SINGLE_THREADED
        }
    }

    /**
     * Rendering backend types
     */
    enum class RenderingBackend {
        /** Single-threaded CPU rendering */
        SINGLE_THREADED,
        /** Multi-threaded CPU rendering */
        MULTI_THREADED,
        /** GPU-accelerated Vulkan rendering */
        VULKAN
    }
}
