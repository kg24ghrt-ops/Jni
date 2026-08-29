package com.example.homecil

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import com.example.homecil.native.PaperEngineNative

/**
 * Test activity to verify native C++ functions work correctly.
 * This activity tests the JNI bridge by rendering paper texture natively.
 */
class NativeTestActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Test the native paper rendering
        val testBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        
        try {
            PaperEngineNative.renderPaper(
                bitmap = testBitmap,
                width = 512,
                height = 512,
                seed = 42,
                grainIntensity = 0.5f,
                fiberDensity = 0.3f,
                waterStainCount = 2,
                agingYellow = 0.1f,
                fiberDirection = 0.5f,
                roughness = 0.3f
            )
            
            // Test distortion
            PaperEngineNative.distortCharacter(
                bitmap = testBitmap,
                seed = 123,
                scale = 0.5f
            )
            
            // Test ink simulation
            val inkBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            inkBitmap.eraseColor(android.graphics.Color.BLACK)
            
            PaperEngineNative.simulateInkSimple(
                bitmap = testBitmap,
                x = 100,
                y = 100,
                width = 100,
                height = 100,
                inkColorR = 0.1f,
                inkColorG = 0.2f,
                inkColorB = 0.8f,
                opacity = 0.8f
            )
            
            setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        android.widget.ImageView(this@NativeTestActivity).apply {
                            setImageBitmap(testBitmap)
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        }
                        Text(
                            text = "Native C++ Engines Working!\nPaper + Ink + Distortion",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
        } catch (e: Exception) {
            setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Native Error: ${e.message}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
