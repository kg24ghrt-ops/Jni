# Native C++ API Documentation

This document describes the native C++ engines and their JNI bridge for the HomeCil paper rendering application.

## Overview

The native layer provides three core engines implemented in C++ for maximum performance:

1. **Paper Engine** (`native-lib.cpp`) - Realistic paper texture generation
2. **Ink Engine** (`ink_engine.cpp`) - Ink absorption and bleeding simulation  
3. **Distortion Engine** (`distort_engine.cpp`) - Geometry-aware bitmap distortion

All engines are exposed through the `PaperEngineNative` Kotlin object via JNI.

## Quick Start

### 1. Load the Native Library

The native library is automatically loaded when you access `PaperEngineNative`:

```kotlin
import com.example.homecil.native.PaperEngineNative

// Library loads automatically in the init block
val engine = PaperEngineNative
```

### 2. Basic Usage

```kotlin
// Create a bitmap for rendering
val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

// Render paper texture
PaperEngineNative.renderPaper(
    bitmap = bitmap,
    width = 512,
    height = 512,
    seed = 42,
    grainIntensity = 0.5f,
    fiberDensity = 0.3f,
    waterStainCount = 2,
    agingYellow = 0.1f,
    fiberDirection = 0.0f,
    roughness = 0.2f
)

// Apply distortion
PaperEngineNative.distortCharacter(
    bitmap = bitmap,
    seed = 123,
    scale = 0.5f
)

// Apply ink
PaperEngineNative.simulateInkSimple(
    bitmap = bitmap,
    x = 100,
    y = 100,
    width = 50,
    height = 50,
    inkColorR = 0.1f,
    inkColorG = 0.2f,
    inkColorB = 0.8f,
    opacity = 0.8f
)
```

## API Reference

### PaperEngineNative

#### `renderPaper`

Renders realistic paper texture into a Bitmap.

**Signature:**
```kotlin
fun renderPaper(
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
```

**Parameters:**
- `bitmap`: Target Bitmap (must be ARGB_8888 format)
- `width`: Width of the bitmap
- `height`: Height of the bitmap
- `seed`: Random seed for reproducible textures (0+)
- `grainIntensity`: Intensity of paper grain (0.0 - 1.0)
- `fiberDensity`: Density of cellulose fibers (0.0 - 1.0)
- `waterStainCount`: Number of water stains to add (0+)
- `agingYellow`: Amount of yellowing/aging effect (0.0 - 1.0)
- `fiberDirection`: Directional bias for fibers (-1.0 to 1.0)
- `roughness`: Overall paper roughness (0.0 - 1.0)

**Performance:**
- Complexity: O(width × height × octaves)
- Typical time for 512×512: ~15-25ms
- Typical time for 1024×1024: ~50-80ms

**Example:**
```kotlin
val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
PaperEngineNative.renderPaper(
    bitmap = bitmap,
    width = 1024,
    height = 1024,
    seed = System.currentTimeMillis().toInt(),
    grainIntensity = 0.6f,
    fiberDensity = 0.4f,
    waterStainCount = 3,
    agingYellow = 0.08f,
    fiberDirection = 0.2f,
    roughness = 0.3f
)
```

#### `simulateInk`

Simulates ink absorption and bleeding on paper with full control.

**Signature:**
```kotlin
fun simulateInk(
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
```

**Parameters:**
- `bitmap`: Target paper Bitmap
- `inkBitmap`: Ink stamp Bitmap to apply (must be ARGB_8888)
- `x`: X position to apply ink
- `y`: Y position to apply ink
- `inkColorR`: Red component of ink color (0.0 - 1.0)
- `inkColorG`: Green component of ink color (0.0 - 1.0)
- `inkColorB`: Blue component of ink color (0.0 - 1.0)
- `absorption`: How much the paper absorbs ink (0.0 - 1.0)
- `noiseIntensity`: Variation in ink density (0.0 - 1.0)
- `seed`: Random seed for reproducible results

**Performance:**
- Complexity: O(inkWidth × inkHeight)
- Typical time for 100×100 ink on 512×512 paper: ~1-3ms

**Example:**
```kotlin
val paper = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
val inkStamp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
// ... draw ink shape into inkStamp ...

PaperEngineNative.simulateInk(
    bitmap = paper,
    inkBitmap = inkStamp,
    x = 200,
    y = 300,
    inkColorR = 0.0f,  // Black
    inkColorG = 0.0f,
    inkColorB = 0.0f,
    absorption = 0.4f,
    noiseIntensity = 0.15f,
    seed = 42
)
```

#### `simulateInkSimple`

Simplified ink simulation for direct color stamping (faster).

**Signature:**
```kotlin
fun simulateInkSimple(
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
```

**Parameters:**
- `bitmap`: Target Bitmap
- `x`: X position
- `y`: Y position
- `width`: Width of area to fill
- `height`: Height of area to fill
- `inkColorR`: Red component (0.0 - 1.0)
- `inkColorG`: Green component (0.0 - 1.0)
- `inkColorB`: Blue component (0.0 - 1.0)
- `opacity`: Opacity of ink (0.0 - 1.0)

**Performance:**
- Complexity: O(width × height)
- Typical time for 50×50 area: ~0.5-1ms

**Example:**
```kotlin
PaperEngineNative.simulateInkSimple(
    bitmap = paper,
    x = 100,
    y = 100,
    width = 50,
    height = 50,
    inkColorR = 0.1f,
    inkColorG = 0.2f,
    inkColorB = 0.8f,
    opacity = 0.8f
)
```

#### `distortBitmap`

Applies geometry-aware distortion to simulate hand-drawn imperfections.

**Signature:**
```kotlin
fun distortBitmap(
    bitmap: Bitmap,
    seed: Int,
    distortionScale: Float,
    sineWarpScale: Float,
    curvatureScale: Float
)
```

**Parameters:**
- `bitmap`: Bitmap to distort
- `seed`: Random seed for reproducible distortion
- `distortionScale`: Overall scale of FBM noise distortion (0.0 - 1.0)
- `sineWarpScale`: Scale of sinusoidal warp (0.0 - 1.0)
- `curvatureScale`: Scale of geometry-aware curvature modulation (0.0 - 1.0)

**Performance:**
- Complexity: O(width × height × octaves)
- Typical time for 256×256: ~8-15ms

**Example:**
```kotlin
PaperEngineNative.distortBitmap(
    bitmap = characterBitmap,
    seed = characterHashCode,
    distortionScale = 0.4f,
    sineWarpScale = 0.2f,
    curvatureScale = 0.3f
)
```

#### `distortCharacter`

Fast distortion optimized for character bitmaps.

**Signature:**
```kotlin
fun distortCharacter(
    bitmap: Bitmap,
    seed: Int,
    scale: Float
)
```

**Parameters:**
- `bitmap`: Character Bitmap to distort
- `seed`: Random seed for reproducible distortion
- `scale`: Overall scale of distortion (0.0 - 1.0)

**Performance:**
- Complexity: O(width × height)
- Typical time for 64×64: ~1-2ms

**Example:**
```kotlin
PaperEngineNative.distortCharacter(
    bitmap = glyphBitmap,
    seed = glyphId,
    scale = 0.3f
)
```

## Advanced Usage

### PaperEngine Integration

The `PaperEngine` object provides a higher-level interface that uses the native engines:

```kotlin
import com.example.homecil.PaperEngine

// Use native engine (default)
PaperEngine.useNativeEngine = true

val texture = PaperEngine.generateTexture(
    paperSize = PaperSize.A4,
    density = LocalDensity.current,
    paperColor = Color(0xFFFBF9F2)
)

// Apply distortion
PaperEngine.distortCharacter(
    bitmap = characterBitmap,
    seed = 42,
    scale = 0.5f
)

// Apply ink
PaperEngine.simulateInkSimple(
    paperBitmap = paper,
    x = 100,
    y = 100,
    width = 50,
    height = 50,
    inkColor = Color.Black,
    opacity = 0.8f
)
```

### InkEngine Integration

```kotlin
import com.example.homecil.InkEngine

InkEngine.useNativeEngine = true

// Apply ink to bitmap
InkEngine.applyInkSimple(
    paperBitmap = paper,
    x = 100,
    y = 100,
    width = 50,
    height = 50,
    inkColor = PenType.BALLPOINT.baseColor,
    opacity = 0.9f
)
```

### Fallback to Kotlin

For compatibility or debugging, you can disable native rendering:

```kotlin
PaperEngine.useNativeEngine = false
InkEngine.useNativeEngine = false

// Now uses pure Kotlin implementation
val texture = PaperEngine.generateTexture(...)
```

## Performance Tips

### 1. Reuse Bitmaps

Creating Bitmaps is expensive. Reuse them when possible:

```kotlin
// Good: Reuse bitmap
val textureBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

fun updateTexture() {
    PaperEngineNative.renderPaper(textureBitmap, ...)  // Reuse
    imageView.setImageBitmap(textureBitmap)
}
```

### 2. Use Appropriate Sizes

Render at the size you need, not larger:

```kotlin
// Good: Render at display size
val displayWidth = resources.displayMetrics.widthPixels
val displayHeight = resources.displayMetrics.heightPixels
val bitmap = Bitmap.createBitmap(displayWidth, displayHeight, ...)

// Bad: Always render at max size
val bitmap = Bitmap.createBitmap(2048, 2048, ...)  // Wastes memory
```

### 3. Batch Operations

Combine multiple operations into single passes when possible:

```kotlin
// Good: Render paper with all effects at once
PaperEngineNative.renderPaper(
    bitmap = bitmap,
    waterStainCount = 3,  // Include stains in render
    agingYellow = 0.1f      // Include aging in render
)

// Bad: Multiple passes
PaperEngineNative.renderPaper(bitmap, waterStainCount = 0)
// Then add stains separately...
```

### 4. Use Simple Distortion for Characters

For small bitmaps (characters, glyphs), use `distortCharacter` instead of `distortBitmap`:

```kotlin
// Good: Fast distortion for characters
PaperEngineNative.distortCharacter(glyph, seed, scale)  // ~1-2ms

// Bad: Overkill for small bitmaps
PaperEngineNative.distortBitmap(glyph, seed, ...)  // ~8-15ms
```

### 5. Cache Results

Cache rendered textures and distorted bitmaps:

```kotlin
val textureCache = mutableMapOf<String, Bitmap>()

fun getTexture(key: String): Bitmap {
    return textureCache.getOrPut(key) {
        val bitmap = Bitmap.createBitmap(...)
        PaperEngineNative.renderPaper(bitmap, seed = key.hashCode(), ...)
        bitmap
    }
}
```

## Threading

The native functions are **thread-safe** and can be called from any thread:

```kotlin
// Background thread
val bitmap = withContext(Dispatchers.IO) {
    val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    PaperEngineNative.renderPaper(bmp, ...)
    bmp
}

// Main thread
withContext(Dispatchers.Main) {
    imageView.setImageBitmap(bitmap)
}
```

⚠️ **Note:** Bitmap operations should not be performed on the main thread for large bitmaps.

## Memory Management

### Bitmap Recycling

Always recycle Bitmaps when done:

```kotlin
val bitmap = Bitmap.createBitmap(...)
try {
    PaperEngineNative.renderPaper(bitmap, ...)
    // Use bitmap...
} finally {
    if (!bitmap.isRecycled) {
        bitmap.recycle()
    }
}
```

### Memory Usage

Estimated memory usage:

| Bitmap Size | Memory (ARGB_8888) |
|-------------|-------------------|
| 256×256     | ~256 KB           |
| 512×512     | ~1 MB             |
| 1024×1024   | ~4 MB             |
| 2048×2048   | ~16 MB            |

The native engines allocate temporary buffers:
- `renderPaper`: No temporary buffer (in-place)
- `distortBitmap`: 1× temporary buffer (same size as input)
- `simulateInk`: No temporary buffer

## Troubleshooting

### "CMake '3.18.1' was not found"

Ensure CMake is installed and the version in `app/build.gradle.kts` matches:

```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.28.3"  // Must match installed version
    }
}
```

### "Native library not found"

Ensure the library name matches in `System.loadLibrary()`:

```kotlin
// In PaperEngineNative.kt
init {
    System.loadLibrary("native-lib")  // Must match add_library() name
}
```

### "No implementation found for native function"

Check that:
1. JNI function names match exactly (including package)
2. Function signatures match (parameter types and order)
3. The library is properly loaded

Example correct naming:
```cpp
// C++ function name must match Kotlin external declaration
JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_renderPaper(...)
```

### Build fails with "undefined reference"

Ensure all C++ files are listed in `CMakeLists.txt`:

```cmake
add_library(native-lib SHARED 
    native-lib.cpp
    ink_engine.cpp
    distort_engine.cpp
)
```

## Implementation Details

### Noise Algorithms

The engines use **Perlin-like gradient noise** with:
- Multiple octaves (frequency bands)
- Persistence for amplitude falloff
- Smoothstep interpolation

**fbmNoise (Fractal Brownian Motion):**
```
total = 0
frequency = 1
amplitude = 1
for each octave:
    total += noise(x * frequency, y * frequency) * amplitude
    frequency *= 2
    amplitude *= persistence
return total / maxValue
```

### Paper Texture Composition

The paper texture combines:
1. **Broad noise** (7× scale) - Large paper formation
2. **Medium noise** (24× scale) - Subtle density variation
3. **Fine noise** (100× scale) - Microscopic grain

With weights: `broad * 2.4 + medium * 1.25 + fine * 0.65`

### Ink Simulation

Ink absorption uses:
1. **Paper brightness** - Darker paper absorbs more ink
2. **Noise variation** - Per-pixel density variation
3. **Alpha blending** - Standard RGBA blending
4. **Darkening** - Slight darkening for absorption effect

### Distortion

Geometry-aware distortion uses:
1. **FBM noise** - Coherent random distortion
2. **Sine warp** - Global sinusoidal distortion
3. **Curvature modulation** - Gradient-aware distortion that follows image features

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-08-29 | Initial release with all three engines |

## License

The native code is licensed under the same terms as the project (MIT License).

## Contact

For questions or issues, refer to the main project documentation.
