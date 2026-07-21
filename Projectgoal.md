## Remedia Project – Developer Handoff Document

### 1. Project Goal
Build an Android app (`com.example.homecil`) that renders **realistic paper** (plain, lined notebook, textured) and **realistic handwriting** (including Burmese and math) using a JNI‑powered rendering engine. The app must respect the system language, support pinch‑to‑zoom/pan, and be developed feature‑by‑feature in Kotlin + C++.

### 2. Constraints & Architecture
- **Language:** Kotlin for Android layer, C++17 for native engine (JNI).
- **Native code:** Two shared libraries? Actually one library `native-lib` with multiple `.cpp` files (`native-lib.cpp`, `ink_engine.cpp`, `distort_engine.cpp`). All linked via CMake.
- **Package:** `com.example.homecil`
- **Min SDK:** 24
- **Build system:** Gradle with Kotlin DSL, CMake for native.
- **UI:** Single `Activity` with a custom `PaperView` that holds the current paper bitmap and handles touch gestures. A transparent `EditText` provides keyboard input. FloatingActionButtons toggle paper style, text‑writing mode, and math mode.
- **No external database or network – everything is local.**

### 3. File Inventory & What Each File Does

#### 3.1 Project Configuration
- `build.gradle.kts` (project) – plugin declarations.
- `settings.gradle.kts` – repository config, includes `:app`.
- `gradle.properties` – standard Android/Kotlin properties.
- `app/build.gradle.kts` – app module build config, CMake integration, dependencies (AppCompat, Material, JLaTeXMath `ru.noties:jlatexmath-android:0.2.0`).
- `app/src/main/AndroidManifest.xml` – single activity, no special permissions.

#### 3.2 Native Code (`app/src/main/cpp/`)
- **`CMakeLists.txt`** – builds `native-lib` from `native-lib.cpp`, `ink_engine.cpp`, `distort_engine.cpp`. Links `android`, `log`, `jnigraphics`.
- **`native-lib.cpp`** – `renderPaper`: Fills a mutable `Bitmap` with a realistic paper texture using multi‑octave Perlin‑like noise (warm off‑white base).
- **`ink_engine.cpp`** – `simulateInk`: Blends an ink stamp onto the paper bitmap. Takes into account paper brightness for absorption, adds per‑pixel noise for ink density variation. Uses alpha blending.
- **`distort_engine.cpp`** – `distortBitmap`: Warps a character/contour bitmap to destroy perfect geometry. Implements coherent FBM noise + sinusoidal global warp, with geometry‑aware modulation (gradient orientation & curvature) to mimic hand tremor. Accepts a `seed` for per‑glyph uniqueness.

#### 3.3 Kotlin Sources (`app/src/main/kotlin/com/example/homecil/`)
- **`PaperRenderer.kt`** – JNI bridge object. Declares `renderPaper`, `simulateInk`, `distortBitmap`. Loads `native-lib`.
- **`InkStroke.kt`** – Data class holding a character (or label), a small Bitmap, and x/y position on the paper.
- **`PaperView.kt`** – Custom `View` that manages a paper bitmap (`paperBitmap`), a list of ink strokes (`inkStrokes`), and a clean base (`basePaperBitmap`). Supports three `PaperStyle` enums: `REALISTIC`, `PLAIN`, `LINED`. Handles pinch‑zoom & pan via `Matrix`. Methods: `addInkStroke`, `removeLastStroke`, `clearAllInk`, `snapToLine` (for lined paper). When style changes or view resizes, regenerates base and reapplies strokes.
- **`HandwritingRenderer.kt`** – Takes a `text` string, renders it monolithically onto a full‑line bitmap (for correct script shaping), then uses `BreakIterator` to split into grapheme clusters. Measures exact cluster offsets with `paint.measureText(text, 0, endIndex)` (avoids floating‑point accumulation). Extracts each cluster’s sub‑bitmap, distorts it with a unique seed, applies tiny jitter, and returns a list of `InkStroke`. Supports all scripts including Burmese.
- **`MathRenderer.kt`** – Uses `jlatexmath-android` to render a LaTeX string into a `TeXIcon`, draws it onto a Bitmap via `AndroidGraphics2D`, recolours it to gel‑pen blue, distorts, and returns an `InkStroke`.
- **`MainActivity.kt`** – Activity logic. Toggles between paper styles, text‑writing mode (with `EditText` listener that calls `HandwritingRenderer` → `PaperView`), and math mode (calls `MathRenderer`). Handles keyboard show/hide. Clears and rebuilds the current line on every text change.

#### 3.4 Resources
- `res/layout/activity_main.xml` – Contains the `PaperView`, an invisible `EditText` (for cursor & keyboard), a welcome `TextView`, and three `FloatingActionButton`s (paper style, text mode, math mode) stacked at bottom‑right.
- `res/values/strings.xml` – App name, welcome text, content descriptions for FABs.
- `res/values-my/strings.xml` – Burmese translations.

### 4. What Has Already Been Implemented

1. **Realistic paper background** – textured (JNI noise), plain off‑white, and lined notebook (blue lines + red margin on top of texture). Toggled via first FAB.
2. **Zoom & pan** – Pinch‑to‑zoom (0.5x–5x) and drag to scroll work on all paper styles.
3. **Realistic handwriting input** – Tap the pen FAB, type anything (Burmese included). Each grapheme cluster is extracted from a monolithic shaped rendering, individually distorted with geometry‑aware wobble, and stamped onto the paper with ink‑bleed simulation.
4. **Math (LaTeX) input** – Tap the math FAB, type LaTeX (e.g. `E=mc^2`, `\frac{a}{b}`). Rendered with jlatexmath, recoloured, distorted, and stamped.
5. **Line snapping** – When on lined paper, writing baseline snaps to the nearest blue line.
6. **Backspace / delete** – The entire line is rebuilt on every keystroke; deleting a character removes the corresponding cluster stroke. No partial deletion errors.
7. **Burmese support** – Full complex‑script rendering via the system font and grapheme‑cluster slicing.
8. **Human‑like imperfections** – Coherent noise + sine warp + curvature‑dependent modulation. Perfect circles and straight lines are destroyed.

### 5. Known Pitfalls & Key Lessons
- **View constructors:** Custom views must declare all three constructors (`Context`, `Context+AttributeSet`, `Context+AttributeSet+Int`) to be inflatable from XML.
- **Float accumulation:** Use `measureText(text, 0, end)` for absolute positions, never sum `getTextWidths` floats.
- **Contour/hole bug:** Do not split a path by contours – holes in glyphs would be filled. Use monolithic rendering + cluster slicing instead.
- **AWT classes on Android:** JLaTeXMath’s AWT compatibility is limited. Use `TeXIcon` + `AndroidGraphics2D`, not `createBufferedImage`.
- **Seed‑based distortion:** The native `distortBitmap` expects a `jint seed`; keep the signature consistent.
- **Paper regeneration:** `PaperView.onSizeChanged` must reapply saved ink strokes, otherwise keyboard appearance would wipe writing.

### 6. Future Steps / Roadmap

#### 6.1 High Priority
- **Persistent ink across paper style changes** – currently switching style clears all strokes. Store strokes globally and re‑stamp when style changes, or allow mixed paper?
- **Multi‑line text** – When `EditText` wraps or user presses Enter, move to next line (snapping to next notebook line).
- **Touch‑based cursor placement** – Let the user tap on the paper to set writing position instead of fixed (200,400).
- **Save / Load** – Save the `paperBitmap` (PNG) or the list of `InkStroke` to disk, reload later.

#### 6.2 Medium Priority
- **More ink colours / pen types** – Gel, ballpoint, pencil. Parameterize `INK_COLOR` and absorption in `simulateInk`.
- **Stroke undo/redo** – Instead of full line rebuild, maintain an undo stack of strokes.
- **Math keyboard shortcuts** – Add a small LaTeX snippet bar for common symbols.
- **Improved notebook paper** – Variable line spacing, page margins, hole punches?

#### 6.3 Low Priority / Ideas
- **Tilt / pressure simulation** – Use touch pressure (if available) to vary ink width.
- **Real‑time handwriting** – Instead of typing, draw strokes directly on the paper with a stylus.
- **Page‑curl animation** – Visual flair when switching paper styles.
- **Export to PDF** – Save the paper with ink as a vector or high‑res PDF.

### 7. Build & Run Instructions
1. Open the project root in Android Studio.
2. Sync Gradle (downloads NDK, JLaTeXMath).
3. Ensure CMake 3.18.1+ and NDK are installed.
4. Run on device/emulator with API ≥ 24.
5. Tap FABs to cycle paper, enter text or math mode, type via the on‑screen keyboard.

### 8. Contact / Notes
This document describes the state as of the last feature push. All files are self‑contained; there is no server, no database. The native code is in `app/src/main/cpp/`. The Kotlin sources are in `app/src/main/kotlin/com/example/homecil/`. Any future developer can pick up from here by following the “Future Steps” list.

---

**End of Handoff Document**