package com.example.homecil

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.homecil.native.PaperEngineNative

/**
 * Benchmark utility for measuring native C++ engine performance.
 * 
 * Usage:
 * ```kotlin
 * val benchmark = NativeBenchmark()
 * benchmark.runAllBenchmarks()
 * ```
 * 
 * Results are logged with tag "NativeBenchmark".
 */
object NativeBenchmark {

    private const val TAG = "NativeBenchmark"
    private const val WARMUP_ITERATIONS = 5
    private const val BENCHMARK_ITERATIONS = 20

    /**
     * Run all benchmarks and log results.
     */
    fun runAllBenchmarks() {
        Log.d(TAG, "=== Native C++ Engine Benchmarks ===")
        Log.d(TAG, "Warmup: $WARMUP_ITERATIONS, Benchmark: $BENCHMARK_ITERATIONS")
        Log.d(TAG, "Device: ${android.os.Build.DEVICE}, Model: ${android.os.Build.MODEL}")
        Log.d(TAG, "")
        
        benchmarkPaperRendering()
        benchmarkInkSimulation()
        benchmarkDistortion()
        benchmarkCombinedOperations()
        
        Log.d(TAG, "=== Benchmarks Complete ===")
    }

    /**
     * Benchmark paper rendering at various sizes.
     */
    fun benchmarkPaperRendering() {
        Log.d(TAG, "--- Paper Rendering Benchmark ---")
        
        val sizes = listOf(128, 256, 512, 1024)
        
        for (size in sizes) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            // Warmup
            for (i in 0 until WARMUP_ITERATIONS) {
                PaperEngineNative.renderPaper(
                    bitmap = bitmap,
                    width = size,
                    height = size,
                    seed = i,
                    grainIntensity = 0.5f,
                    fiberDensity = 0.3f,
                    waterStainCount = 2,
                    agingYellow = 0.05f,
                    fiberDirection = 0.0f,
                    roughness = 0.2f
                )
            }
            
            // Benchmark
            val start = SystemClock.elapsedRealtimeNanos()
            for (i in 0 until BENCHMARK_ITERATIONS) {
                PaperEngineNative.renderPaper(
                    bitmap = bitmap,
                    width = size,
                    height = size,
                    seed = i * 100,
                    grainIntensity = 0.5f,
                    fiberDensity = 0.3f,
                    waterStainCount = 2,
                    agingYellow = 0.05f,
                    fiberDirection = 0.0f,
                    roughness = 0.2f
                )
            }
            val end = SystemClock.elapsedRealtimeNanos()
            
            val avgTime = (end - start).toDouble() / BENCHMARK_ITERATIONS / 1_000_000.0
            val memoryMB = (size * size * 4).toDouble() / (1024 * 1024)
            
            Log.d(TAG, String.format("renderPaper %dx%d: %.2fms (avg), %.2fMB", 
                size, size, avgTime, memoryMB))
            
            bitmap.recycle()
        }
        Log.d(TAG, "")
    }

    /**
     * Benchmark multi-threaded paper rendering.
     */
    fun benchmarkMultiThreadedRendering() {
        Log.d(TAG, "--- Multi-Threaded Paper Rendering Benchmark ---")
        
        val size = 1024
        val threadCounts = listOf(1, 2, 4)
        
        for (threadCount in threadCounts) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            // Warmup
            for (i in 0 until WARMUP_ITERATIONS) {
                PaperEngineNative.renderPaperMT(
                    bitmap = bitmap,
                    width = size,
                    height = size,
                    seed = i,
                    grainIntensity = 0.5f,
                    fiberDensity = 0.3f,
                    waterStainCount = 0,
                    agingYellow = 0.0f,
                    fiberDirection = 0.0f,
                    roughness = 0.0f,
                    threadCount = threadCount
                )
            }
            
            // Benchmark
            val start = SystemClock.elapsedRealtimeNanos()
            for (i in 0 until BENCHMARK_ITERATIONS) {
                PaperEngineNative.renderPaperMT(
                    bitmap = bitmap,
                    width = size,
                    height = size,
                    seed = i * 100,
                    grainIntensity = 0.5f,
                    fiberDensity = 0.3f,
                    waterStainCount = 0,
                    agingYellow = 0.0f,
                    fiberDirection = 0.0f,
                    roughness = 0.0f,
                    threadCount = threadCount
                )
            }
            val end = SystemClock.elapsedRealtimeNanos()
            
            val avgTime = (end - start).toDouble() / BENCHMARK_ITERATIONS / 1_000_000.0
            
            Log.d(TAG, String.format("renderPaperMT %dx%d, threads=%d: %.2fms (avg)", 
                size, size, threadCount, avgTime))
            
            bitmap.recycle()
        }
        Log.d(TAG, "")
    }

    /**
     * Benchmark ink simulation at various sizes.
     */
    fun benchmarkInkSimulation() {
        Log.d(TAG, "--- Ink Simulation Benchmark ---")
        
        val paperSize = 512
        val paper = Bitmap.createBitmap(paperSize, paperSize, Bitmap.Config.ARGB_8888)
        
        // Pre-render paper
        PaperEngineNative.renderPaper(
            bitmap = paper,
            width = paperSize,
            height = paperSize,
            seed = 42,
            grainIntensity = 0.5f,
            fiberDensity = 0.3f,
            waterStainCount = 0,
            agingYellow = 0.0f,
            fiberDirection = 0.0f,
            roughness = 0.0f
        )
        
        val inkSizes = listOf(32, 64, 128, 256)
        
        for (size in inkSizes) {
            val ink = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            // Create a simple ink pattern
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val dx = x - size / 2
                    val dy = y - size / 2
                    val dist = Math.sqrt(dx * dx + dy * dy.toDouble())
                    if (dist < size / 2.0) {
                        ink.setPixel(x, y, android.graphics.Color.BLACK)
                    } else {
                        ink.setPixel(x, y, android.graphics.Color.TRANSPARENT)
                    }
                }
            }
            
            // Warmup
            for (i in 0 until WARMUP_ITERATIONS) {
                PaperEngineNative.simulateInk(
                    bitmap = paper,
                    inkBitmap = ink,
                    x = (i * 10) % (paperSize - size),
                    y = (i * 15) % (paperSize - size),
                    inkColorR = 0.0f,
                    inkColorG = 0.0f,
                    inkColorB = 0.0f,
                    absorption = 0.3f,
                    noiseIntensity = 0.1f,
                    seed = i
                )
            }
            
            // Benchmark
            val start = SystemClock.elapsedRealtimeNanos()
            for (i in 0 until BENCHMARK_ITERATIONS) {
                PaperEngineNative.simulateInk(
                    bitmap = paper,
                    inkBitmap = ink,
                    x = (i * 10) % (paperSize - size),
                    y = (i * 15) % (paperSize - size),
                    inkColorR = 0.0f,
                    inkColorG = 0.0f,
                    inkColorB = 0.0f,
                    absorption = 0.3f,
                    noiseIntensity = 0.1f,
                    seed = i * 100
                )
            }
            val end = SystemClock.elapsedRealtimeNanos()
            
            val avgTime = (end - start).toDouble() / BENCHMARK_ITERATIONS / 1_000_000.0
            
            Log.d(TAG, String.format("simulateInk %dx%d on %dx%d: %.2fms (avg)", 
                size, size, paperSize, paperSize, avgTime))
            
            ink.recycle()
        }
        
        paper.recycle()
        Log.d(TAG, "")
    }

    /**
     * Benchmark distortion at various sizes.
     */
    fun benchmarkDistortion() {
        Log.d(TAG, "--- Distortion Benchmark ---")
        
        val sizes = listOf(64, 128, 256, 512)
        
        for (size in sizes) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            // Fill with a pattern
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val color = android.graphics.Color.rgb(
                        (x * 255 / size),
                        (y * 255 / size),
                        128
                    )
                    bitmap.setPixel(x, y, color)
                }
            }
            
            // Warmup
            for (i in 0 until WARMUP_ITERATIONS) {
                PaperEngineNative.distortCharacter(
                    bitmap = bitmap,
                    seed = i,
                    scale = 0.5f
                )
            }
            
            // Benchmark
            val start = SystemClock.elapsedRealtimeNanos()
            for (i in 0 until BENCHMARK_ITERATIONS) {
                PaperEngineNative.distortCharacter(
                    bitmap = bitmap,
                    seed = i * 100,
                    scale = 0.5f
                )
            }
            val end = SystemClock.elapsedRealtimeNanos()
            
            val avgTime = (end - start).toDouble() / BENCHMARK_ITERATIONS / 1_000_000.0
            val memoryKB = (size * size * 4).toDouble() / 1024
            
            Log.d(TAG, String.format("distortCharacter %dx%d: %.2fms (avg), %.2fKB", 
                size, size, avgTime, memoryKB))
            
            bitmap.recycle()
        }
        Log.d(TAG, "")
    }

    /**
     * Benchmark combined operations (render + distort + ink).
     */
    fun benchmarkCombinedOperations() {
        Log.d(TAG, "--- Combined Operations Benchmark ---")
        
        val size = 512
        
        // Warmup
        for (i in 0 until WARMUP_ITERATIONS) {
            val paper = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val ink = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            
            PaperEngineNative.renderPaper(
                bitmap = paper,
                width = size,
                height = size,
                seed = i,
                grainIntensity = 0.5f,
                fiberDensity = 0.3f,
                waterStainCount = 0,
                agingYellow = 0.0f,
                fiberDirection = 0.0f,
                roughness = 0.0f
            )
            
            PaperEngineNative.distortCharacter(
                bitmap = paper,
                seed = i,
                scale = 0.3f
            )
            
            ink.eraseColor(android.graphics.Color.BLACK)
            PaperEngineNative.simulateInkSimple(
                bitmap = paper,
                x = 100,
                y = 100,
                width = 100,
                height = 100,
                inkColorR = 0.0f,
                inkColorG = 0.0f,
                inkColorB = 0.0f,
                opacity = 0.8f
            )
            
            paper.recycle()
            ink.recycle()
        }
        
        // Benchmark
        val start = SystemClock.elapsedRealtimeNanos()
        for (i in 0 until BENCHMARK_ITERATIONS) {
            val paper = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val ink = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            
            PaperEngineNative.renderPaper(
                bitmap = paper,
                width = size,
                height = size,
                seed = i * 100,
                grainIntensity = 0.5f,
                fiberDensity = 0.3f,
                waterStainCount = 0,
                agingYellow = 0.0f,
                fiberDirection = 0.0f,
                roughness = 0.0f
            )
            
            PaperEngineNative.distortCharacter(
                bitmap = paper,
                seed = i * 100 + 1,
                scale = 0.3f
            )
            
            ink.eraseColor(android.graphics.Color.BLACK)
            PaperEngineNative.simulateInkSimple(
                bitmap = paper,
                x = 100,
                y = 100,
                width = 100,
                height = 100,
                inkColorR = 0.0f,
                inkColorG = 0.0f,
                inkColorB = 0.0f,
                opacity = 0.8f
            )
            
            paper.recycle()
            ink.recycle()
        }
        val end = SystemClock.elapsedRealtimeNanos()
        
        val avgTime = (end - start).toDouble() / BENCHMARK_ITERATIONS / 1_000_000.0
        
        Log.d(TAG, String.format("Combined (render + distort + ink) %dx%d: %.2fms (avg)", 
            size, size, avgTime))
        Log.d(TAG, "")
    }

    /**
     * Get device information for benchmark context.
     */
    fun getDeviceInfo(): String {
        return """
        | Device Info | Value |
        |-------------|-------|
        | Manufacturer | ${android.os.Build.MANUFACTURER} |
        | Model | ${android.os.Build.MODEL} |
        | Device | ${android.os.Build.DEVICE} |
        | Product | ${android.os.Build.PRODUCT} |
        | CPU ABI | ${android.os.Build.CPU_ABI} |
        | CPU ABI2 | ${android.os.Build.CPU_ABI2} |
        | SDK | ${android.os.Build.VERSION.SDK_INT} |
        | Release | ${android.os.Build.VERSION.RELEASE} |
        | Available Processors | ${Runtime.getRuntime().availableProcessors()} |
        | Max Memory | ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB |
        | Total Memory | ${Runtime.getRuntime().totalMemory() / (1024 * 1024)} MB |
        | Free Memory | ${Runtime.getRuntime().freeMemory() / (1024 * 1024)} MB |
        """.trimMargin()
    }

    /**
     * Get native engine capabilities.
     */
    fun getCapabilities(): String {
        return PaperEngineNative.getCapabilities()
    }
}
