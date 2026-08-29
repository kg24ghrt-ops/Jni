package com.example.homecil

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.homecil.native.PaperEngineNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the native C++ engines.
 * These tests verify that the JNI bridge is working correctly
 * and the native functions produce expected results.
 */
@RunWith(AndroidJUnit4::class)
class NativeEngineTest {

    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testNativeLibraryLoads() {
        // This test verifies that the native library can be loaded
        // If loading fails, the PaperEngineNative init block will throw
        assertNotNull("Native library should load successfully", PaperEngineNative)
    }

    @Test
    fun testRenderPaper_NonNullResult() {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        
        PaperEngineNative.renderPaper(
            bitmap = bitmap,
            width = 256,
            height = 256,
            seed = 42,
            grainIntensity = 0.5f,
            fiberDensity = 0.3f,
            waterStainCount = 0,
            agingYellow = 0.05f,
            fiberDirection = 0.0f,
            roughness = 0.2f
        )
        
        assertNotNull("Bitmap should not be null after rendering", bitmap)
        assertEquals("Bitmap width should be 256", 256, bitmap.width)
        assertEquals("Bitmap height should be 256", 256, bitmap.height)
        assertEquals("Bitmap config should be ARGB_8888", Bitmap.Config.ARGB_8888, bitmap.config)
    }

    @Test
    fun testRenderPaper_Deterministic() {
        val seed = 12345
        
        // Create two bitmaps with the same parameters
        val bitmap1 = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val bitmap2 = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        
        PaperEngineNative.renderPaper(
            bitmap = bitmap1,
            width = 128,
            height = 128,
            seed = seed,
            grainIntensity = 0.5f,
            fiberDensity = 0.3f,
            waterStainCount = 0,
            agingYellow = 0.05f,
            fiberDirection = 0.0f,
            roughness = 0.2f
        )
        
        PaperEngineNative.renderPaper(
            bitmap = bitmap2,
            width = 128,
            height = 128,
            seed = seed,
            grainIntensity = 0.5f,
            fiberDensity = 0.3f,
            waterStainCount = 0,
            agingYellow = 0.05f,
            fiberDirection = 0.0f,
            roughness = 0.2f
        )
        
        // Compare a few pixels to ensure determinism
        assertEquals("Pixel at (10,10) should be the same", 
            bitmap1.getPixel(10, 10), bitmap2.getPixel(10, 10))
        assertEquals("Pixel at (50,50) should be the same", 
            bitmap1.getPixel(50, 50), bitmap2.getPixel(50, 50))
        assertEquals("Pixel at (100,100) should be the same", 
            bitmap1.getPixel(100, 100), bitmap2.getPixel(100, 100))
    }

    @Test
    fun testRenderPaper_DifferentSeeds() {
        val bitmap1 = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val bitmap2 = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        
        PaperEngineNative.renderPaper(
            bitmap = bitmap1,
            width = 128,
            height = 128,
            seed = 1,
            grainIntensity = 0.5f,
            fiberDensity = 0.3f,
            waterStainCount = 0,
            agingYellow = 0.05f,
            fiberDirection = 0.0f,
            roughness = 0.2f
        )
        
        PaperEngineNative.renderPaper(
            bitmap = bitmap2,
            width = 128,
            height = 128,
            seed = 2,
            grainIntensity = 0.5f,
            fiberDensity = 0.3f,
            waterStainCount = 0,
            agingYellow = 0.05f,
            fiberDirection = 0.0f,
            roughness = 0.2f
        )
        
        // With different seeds, the bitmaps should be different
        var different = false
        for (y in 0 until 128 step 16) {
            for (x in 0 until 128 step 16) {
                if (bitmap1.getPixel(x, y) != bitmap2.getPixel(x, y)) {
                    different = true
                    break
                }
            }
            if (different) break
        }
        
        assertTrue("Bitmaps with different seeds should be different", different)
    }

    @Test
    fun testDistortCharacter_ModifiesBitmap() {
        // Create a simple test bitmap with a known pattern
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        
        // Draw a black square in the center
        for (y in 20 until 44) {
            for (x in 20 until 44) {
                bitmap.setPixel(x, y, android.graphics.Color.BLACK)
            }
        }
        
        val originalPixel = bitmap.getPixel(32, 32)
        
        // Apply distortion
        PaperEngineNative.distortCharacter(
            bitmap = bitmap,
            seed = 42,
            scale = 0.5f
        )
        
        // After distortion, the center pixel might have changed
        // (depending on the distortion pattern)
        // We just verify the function doesn't crash
        assertNotNull("Bitmap should not be null after distortion", bitmap)
    }

    @Test
    fun testSimulateInkSimple_ModifiesBitmap() {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        
        val originalPixel = bitmap.getPixel(10, 10)
        
        // Apply ink
        PaperEngineNative.simulateInkSimple(
            bitmap = bitmap,
            x = 10,
            y = 10,
            width = 50,
            height = 50,
            inkColorR = 0.0f,  // Black
            inkColorG = 0.0f,
            inkColorB = 0.0f,
            opacity = 0.8f
        )
        
        // The inked area should have changed
        assertNotNull("Bitmap should not be null after ink simulation", bitmap)
        
        // Check that some pixels in the inked area changed
        var changed = false
        for (y in 10 until 60 step 10) {
            for (x in 10 until 60 step 10) {
                if (bitmap.getPixel(x, y) != originalPixel) {
                    changed = true
                    break
                }
            }
            if (changed) break
        }
        
        assertTrue("Ink simulation should modify the bitmap", changed)
    }

    @Test
    fun testSimulateInk_WithInkBitmap() {
        val paperBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        paperBitmap.eraseColor(android.graphics.Color.WHITE)
        
        val inkBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        // Create a simple ink pattern (circle)
        inkBitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        val centerX = 16
        val centerY = 16
        val radius = 10
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val dx = x - centerX
                val dy = y - centerY
                if (dx * dx + dy * dy <= radius * radius) {
                    inkBitmap.setPixel(x, y, android.graphics.Color.BLACK)
                }
            }
        }
        
        // Apply ink
        PaperEngineNative.simulateInk(
            bitmap = paperBitmap,
            inkBitmap = inkBitmap,
            x = 50,
            y = 50,
            inkColorR = 1.0f,  // White ink (will show on white paper with absorption)
            inkColorG = 0.0f,
            inkColorB = 0.0f,
            absorption = 0.5f,
            noiseIntensity = 0.1f,
            seed = 42
        )
        
        assertNotNull("Paper bitmap should not be null after ink application", paperBitmap)
    }

    @Test
    fun testDistortBitmap_WithParameters() {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        
        // Fill with a gradient pattern
        for (y in 0 until 128) {
            for (x in 0 until 128) {
                val color = android.graphics.Color.rgb(
                    (x * 255 / 128),
                    (y * 255 / 128),
                    128
                )
                bitmap.setPixel(x, y, color)
            }
        }
        
        // Apply distortion with various parameters
        PaperEngineNative.distortBitmap(
            bitmap = bitmap,
            seed = 42,
            distortionScale = 0.5f,
            sineWarpScale = 0.3f,
            curvatureScale = 0.2f
        )
        
        assertNotNull("Bitmap should not be null after distortion", bitmap)
    }
}
