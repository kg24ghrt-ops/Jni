# Notebook Paper Design Specification

## Overview

This document outlines the design and implementation plan for creating realistic lined notebook paper in the HomeCil application.

## Research: Real Notebook Paper Characteristics

### Standard Notebook Paper Measurements

| Type | Line Spacing | Margin | Line Color | Line Width | Paper Size |
|------|--------------|--------|------------|------------|------------|
| College Ruled | 9/32" (7.1mm) | 1.25" (31.75mm) | Light blue/gray | 0.5mm | Letter (8.5"×11") |
| Wide Ruled | 11/32" (8.7mm) | 1.25" (31.75mm) | Light blue/gray | 0.5mm | Letter (8.5"×11") |
| Narrow Ruled | 6/32" (4.8mm) | 1.25" (31.75mm) | Light blue/gray | 0.5mm | Letter (8.5"×11") |
| Graph Paper | 1/4" (6.35mm) | 0.5" (12.7mm) | Light gray | 0.3mm | Letter/A4 |
| Legal Ruled | 11/32" (8.7mm) | 1.25" (31.75mm) | Red | 0.5mm | Legal (8.5"×14") |

### A4/A5 Paper Standards (Metric)

| Type | Width | Height | Line Spacing (mm) | Margin (mm) |
|------|-------|--------|-------------------|-------------|
| A4 | 210mm | 297mm | 7mm (college) | 25mm |
| A5 | 148mm | 210mm | 7mm (college) | 20mm |

### Visual Characteristics

1. **Line Color**: Typically light blue (hex: #A9C2D9) or light gray (#E0E0E0)
2. **Line Opacity**: ~30-40% for subtle appearance
3. **Line Width**: 0.3-0.5mm
4. **Margin Line**: Often red (#E57373) or same as ruling lines
5. **Margin Width**: 25-32mm from left edge
6. **Header Space**: Top 25-30mm often left blank
7. **Paper Texture**: Slight off-white (#FBF9F2) with subtle grain

## Implementation Plan

### 1. NotebookGrid Component (Already Implemented)

The `NotebookGrid.kt` component already handles:
- ✅ Horizontal ruling lines
- ✅ Vertical margin line
- ✅ Cursor line highlighting
- ✅ Configurable line spacing
- ✅ Configurable margin

**Current Implementation Status**: Functional but needs enhancement

### 2. PaperEngine Enhancement

#### Current State
- Generates realistic paper texture with grain, fibers, aging
- No ruling lines (lines are drawn separately in NotebookGrid)

#### Required Enhancements
1. **Option to include ruling lines in texture**
   - For export/printing scenarios
   - Baked into the bitmap
2. **Configurable ruling parameters**
   - Line spacing
   - Line color
   - Line width
   - Margin position

### 3. Native C++ Engine Enhancement

#### Current State
- `renderPaper()` - Generates paper texture
- `renderPaperMT()` - Multi-threaded version
- `renderPaperVulkan()` - Vulkan version (CPU fallback)

#### Required Enhancements
1. Add ruling line parameters to all functions
2. Implement ruling line drawing in C++
3. Optimize for performance

## Proposed Architecture

### Layered Approach

```
┌─────────────────────────────────────────┐
│           NotebookPage                    │
│  ┌───────────────────────────────────┐  │
│  │         Paper Texture               │  │
│  │    (from PaperEngine)               │  │
│  │                                       │  │
│  │  ┌─────────────────────────────┐  │  │
│  │  │      NotebookGrid              │  │  │
│  │  │  - Horizontal Ruling Lines     │  │  │
│  │  │  - Vertical Margin Line         │  │  │
│  │  │  - Cursor Highlight             │  │  │
│  │  └─────────────────────────────┘  │  │
│  │                                       │  │
│  │  ┌─────────────────────────────┐  │  │
│  │  │         Text Content            │  │  │
│  │  │    (BasicTextField)             │  │  │
│  │  └─────────────────────────────┘  │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| PaperEngine | Generate paper texture with optional ruling lines |
| NotebookGrid | Draw dynamic ruling lines, margin, cursor highlight |
| NotebookPage | Compose all layers, manage text editing |

## Implementation Details

### NotebookGrid.kt Enhancements

```kotlin
@Composable
internal fun NotebookGrid(
    modifier: Modifier,
    lineSpacing: Dp,
    marginX: Dp,
    layoutResult: TextLayoutResult?,
    activeLine: Int,
    showMargin: Boolean,
    rulingType: RulingType = RulingType.COLLEGE,  // NEW
    showHeaderSpace: Boolean = true,             // NEW
    headerHeight: Dp = 25.dp                      // NEW
) {
    // ... existing code ...
    
    Canvas(modifier = modifier) {
        // Draw header space (blank area at top)
        if (showHeaderSpace) {
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, headerHeight.toPx())
            )
        }
        
        // Draw ruling lines (starting below header)
        val startY = if (showHeaderSpace) headerHeight.toPx() else 0f
        val availableHeight = size.height - startY
        val lineCount = (availableHeight / spacingPx).toInt()
        
        for (i in 0..lineCount) {
            val y = startY + i * spacingPx
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5f
            )
        }
        
        // ... rest of existing code ...
    }
}

enum class RulingType {
    COLLEGE,   // 7mm spacing
    WIDE,      // 8.7mm spacing
    NARROW,    // 4.8mm spacing
    GRAPH,     // 6.35mm spacing with vertical lines
    LEGAL      // 8.7mm spacing, red lines
}
```

### PaperEngine.kt Enhancements

```kotlin
object PaperEngine {
    // ... existing code ...
    
    fun generateTexture(
        paperSize: PaperSize,
        density: Density,
        paperColor: Color,
        rulingType: RulingType? = null,      // NEW: optional ruling
        lineColor: Color = Color(0xFFA9C2D9), // NEW
        lineSpacing: Dp? = null,              // NEW: override spacing
        marginX: Dp = 25.dp,                  // NEW
        showMarginLine: Boolean = true       // NEW
    ): ImageBitmap {
        val bitmap = // ... create bitmap ...
        
        // Generate paper texture
        PaperEngineNative.renderPaper(
            bitmap = bitmap,
            // ... existing parameters ...
        )
        
        // Optionally add ruling lines to texture
        rulingType?.let { type ->
            val spacingPx = with(density) { 
                lineSpacing?.toPx() ?: type.defaultSpacing.toPx() 
            }
            val marginPx = with(density) { marginX.toPx() }
            
            drawRulingLines(
                bitmap = bitmap,
                spacing = spacingPx,
                margin = marginPx,
                lineColor = lineColor,
                showMarginLine = showMarginLine
            )
        }
        
        return bitmap.asImageBitmap()
    }
    
    private fun drawRulingLines(
        bitmap: Bitmap,
        spacing: Float,
        margin: Float,
        lineColor: Color,
        showMarginLine: Boolean
    ) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = lineColor.toArgb()
            strokeWidth = 0.5f
            isAntiAlias = true
        }
        
        // Draw horizontal lines
        for (y in 0 until bitmap.height step spacing.toInt()) {
            canvas.drawLine(0f, y.toFloat(), bitmap.width.toFloat(), y.toFloat(), paint)
        }
        
        // Draw margin line
        if (showMarginLine) {
            paint.color = Color.Red.toArgb()
            canvas.drawLine(margin, 0f, margin, bitmap.height.toFloat(), paint)
        }
    }
}
```

### Native C++ Enhancements

```cpp
// In native-lib.cpp
JNIEXPORT void JNICALL
Java_com_example_homecil_native_PaperEngineNative_renderPaperWithRuling(
    JNIEnv* env, jobject thiz, jobject bitmap,
    jint width, jint height, jint seed,
    jfloat grainIntensity, jfloat fiberDensity,
    jint waterStainCount, jfloat agingYellow,
    jfloat fiberDirection, jfloat roughness,
    // NEW: Ruling parameters
    jfloat lineSpacing, jfloat marginX,
    jint lineColor, jboolean showMarginLine
) {
    // ... existing paper rendering code ...
    
    // After paper texture is generated, add ruling lines
    if (lineSpacing > 0) {
        uint32_t lineColorRGBA = static_cast<uint32_t>(lineColor);
        uint8_t lineR = (lineColorRGBA >> 16) & 0xFF;
        uint8_t lineG = (lineColorRGBA >> 8) & 0xFF;
        uint8_t lineB = lineColorRGBA & 0xFF;
        
        // Draw horizontal ruling lines
        for (int y = 0; y < height; y += static_cast<int>(lineSpacing)) {
            for (int x = 0; x < width; x++) {
                // Skip margin area for margin line
                if (showMarginLine && x < marginX) {
                    continue;
                }
                // Blend line color with existing pixel
                uint32_t pixel = pixels[y * width + x];
                uint8_t r = (pixel >> 16) & 0xFF;
                uint8_t g = (pixel >> 8) & 0xFF;
                uint8_t b = pixel & 0xFF;
                
                // Blend with 30% opacity
                float blend = 0.3f;
                int newR = static_cast<int>(r * (1.0f - blend) + lineR * blend);
                int newG = static_cast<int>(g * (1.0f - blend) + lineG * blend);
                int newB = static_cast<int>(b * (1.0f - blend) + lineB * blend);
                
                pixels[y * width + x] = 0xFF000000 | (newR << 16) | (newG << 8) | newB;
            }
        }
        
        // Draw margin line
        if (showMarginLine) {
            int marginX_int = static_cast<int>(marginX);
            for (int y = 0; y < height; y++) {
                if (marginX_int >= 0 && marginX_int < width) {
                    // Red margin line
                    pixels[y * width + marginX_int] = 0xFFE57373;
                }
            }
        }
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
}
```

## Configuration Options

### Ruling Type Presets

```kotlin
data class RulingConfig(
    val name: String,
    val lineSpacing: Dp,
    val marginX: Dp,
    val lineColor: Color,
    val marginColor: Color,
    val showMarginLine: Boolean,
    val showHeaderSpace: Boolean,
    val headerHeight: Dp
)

val RULING_PRESETS = mapOf(
    "college" to RulingConfig(
        name = "College Ruled",
        lineSpacing = 7.dp,
        marginX = 25.dp,
        lineColor = Color(0xFFA9C2D9),
        marginColor = Color(0xFFE57373),
        showMarginLine = true,
        showHeaderSpace = true,
        headerHeight = 25.dp
    ),
    "wide" to RulingConfig(
        name = "Wide Ruled",
        lineSpacing = 8.7.dp,
        marginX = 25.dp,
        lineColor = Color(0xFFA9C2D9),
        marginColor = Color(0xFFE57373),
        showMarginLine = true,
        showHeaderSpace = true,
        headerHeight = 25.dp
    ),
    "narrow" to RulingConfig(
        name = "Narrow Ruled",
        lineSpacing = 4.8.dp,
        marginX = 25.dp,
        lineColor = Color(0xFFA9C2D9),
        marginColor = Color(0xFFE57373),
        showMarginLine = true,
        showHeaderSpace = true,
        headerHeight = 25.dp
    ),
    "graph" to RulingConfig(
        name = "Graph Paper",
        lineSpacing = 6.35.dp,
        marginX = 12.7.dp,
        lineColor = Color(0xFFE0E0E0),
        marginColor = Color(0xFFE0E0E0),
        showMarginLine = false,
        showHeaderSpace = false,
        headerHeight = 0.dp
    ),
    "plain" to RulingConfig(
        name = "Plain Paper",
        lineSpacing = 0.dp,
        marginX = 0.dp,
        lineColor = Color(0xFFA9C2D9),
        marginColor = Color(0xFFE57373),
        showMarginLine = false,
        showHeaderSpace = false,
        headerHeight = 0.dp
    )
)
```

## Performance Considerations

### 1. Dynamic vs Baked Ruling Lines

| Approach | Pros | Cons |
|----------|------|------|
| **Dynamic (NotebookGrid)** | Scrolls with text, always aligned | Slightly more GPU work |
| **Baked (PaperEngine)** | Single draw call, exported correctly | Doesn't scroll with text |
| **Hybrid** | Best of both worlds | More complex implementation |

**Recommendation**: Use **Dynamic** approach (current NotebookGrid implementation) for interactive use, **Baked** for export/printing.

### 2. Line Drawing Optimization

```kotlin
// Optimized line drawing in NotebookGrid
Canvas(modifier = modifier) {
    val spacingPx = lineSpacing.toPx()
    val marginPx = marginX.toPx()
    
    // Pre-calculate line count
    val startY = if (showHeaderSpace) headerHeight.toPx() else 0f
    val availableHeight = size.height - startY
    val lineCount = (availableHeight / spacingPx).toInt()
    
    // Use drawLine for each line (Compose optimizes this)
    // OR: Use Path for batch drawing
    val path = Path().apply {
        for (i in 0..lineCount) {
            val y = startY + i * spacingPx
            moveTo(0f, y)
            lineTo(size.width, y)
        }
        if (showMargin) {
            moveTo(marginPx, 0f)
            lineTo(marginPx, size.height)
        }
    }
    
    drawPath(path, color = lineColor, style = Stroke(width = 0.5f))
}
```

### 3. Memory Optimization

- **Texture Atlas**: Cache ruling line patterns as textures
- **Reuse Path Objects**: Create Path once, reuse with reset()
- **Lazy Drawing**: Only draw visible lines (for very large pages)

## Testing Strategy

### 1. Visual Tests

```kotlin
@Preview
@Composable
fun NotebookPaperPreview() {
    val rulingTypes = remember { RULING_PRESETS.values.toList() }
    
    Column {
        rulingTypes.forEach { config ->
            NotebookPagePreview(rulingConfig = config)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun NotebookPagePreview(rulingConfig: RulingConfig) {
    val texture = remember { 
        PaperEngine.generateTexture(
            paperSize = PaperSize.A5,
            density = LocalDensity.current,
            paperColor = Color(0xFFFBF9F2)
        )
    }
    
    Box(modifier = Modifier.size(300.dp, 400.dp)) {
        Image(bitmap = texture, contentDescription = null)
        NotebookGrid(
            modifier = Modifier.matchParentSize(),
            lineSpacing = rulingConfig.lineSpacing,
            marginX = rulingConfig.marginX,
            layoutResult = null,
            activeLine = -1,
            showMargin = rulingConfig.showMarginLine,
            showHeaderSpace = rulingConfig.showHeaderSpace,
            headerHeight = rulingConfig.headerHeight
        )
    }
}
```

### 2. Unit Tests

```kotlin
class NotebookGridTest {
    @Test
    fun testCollegeRulingLineCount() {
        val heightDp = 297.dp // A4 height
        val density = Density(1.0f)
        val spacingPx = with(density) { 7.dp.toPx() }
        val headerHeightPx = with(density) { 25.dp.toPx() }
        val availableHeight = with(density) { heightDp.toPx() } - headerHeightPx
        
        val expectedLines = (availableHeight / spacingPx).toInt()
        
        // A4 paper (297mm) with 7mm spacing and 25mm header
        // should have approximately 39-40 lines
        assertTrue(expectedLines in 38..42)
    }
    
    @Test
    fun testMarginPosition() {
        val marginDp = 25.dp
        val density = Density(1.0f)
        val marginPx = with(density) { marginDp.toPx() }
        
        // Margin should be at 25mm from left
        assertEquals(25f, marginPx, 0.1f)
    }
}
```

## Implementation Roadmap

### Phase 1: Quick Wins (Current Sprint)
1. ✅ Fix existing NotebookGrid compilation issues
2. ✅ Ensure basic ruling lines are visible
3. ✅ Add configuration options for line spacing
4. ✅ Add header space support

### Phase 2: Enhancements (Next Sprint)
1. Add ruling type presets
2. Add baked ruling lines option in PaperEngine
3. Add native C++ ruling line support
4. Add graph paper (grid lines) support

### Phase 3: Polish (Future)
1. Add custom ruling configuration UI
2. Add ruler measurement markings
3. Add page numbers
4. Add watermark support

## References

### Real-World Notebook Specifications
- [Moleskine Notebooks](https://www.moleskine.com/en/collections/classic-notebooks)
- [Leuchtturm1917](https://www.leuchtturm1917.com/en/notebooks)
- [Oxford Optik Paper](https://www.oxford.com/en/stationery/optik-paper)

### Design Inspiration
- [Notebook Paper Textures](https://www.textures.com/system/preview/100105/Paper-Notebook-0001-1K-JPG?1460412000)
- [Lined Paper Generator](https://www.printablepaper.net/category/lined)

### Technical References
- [Android Canvas API](https://developer.android.com/reference/android/graphics/Canvas)
- [Compose Canvas API](https://developer.android.com/jetpack/compose/graphics/draw)
- [Compose Text Layout](https://developer.android.com/jetpack/compose/text)

## Conclusion

The current implementation already has the foundation for lined notebook paper. The main work needed is:

1. **Verify existing functionality** - Ensure NotebookGrid is working correctly
2. **Add configuration options** - Expose ruling parameters to users
3. **Add presets** - Common ruling types (college, wide, narrow, graph)
4. **Add export support** - Baked ruling lines for printing

The layered approach (PaperEngine for texture + NotebookGrid for dynamic lines) is the most flexible and performant solution.
