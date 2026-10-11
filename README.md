# OpenMathPad 📐✨

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-green.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.05.01-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Adaptive%20Expressive-orange.svg)](https://m3.material.io)
[![Display](https://img.shields.io/badge/Display-144Hz%20Ultra--Low%20Latency-ff69b4.svg)](#-performance--hardware-optimization)
[![Tests](https://img.shields.io/badge/Unit%20Tests-Passing%20(100%25)-success.svg)](#-getting-started--build-instructions)
[![Diagnostics](https://img.shields.io/badge/LeakCanary-2.14%20Integrated-yellow.svg)](#-diagnostics--memory-profiling)

> **Lightweight, high-performance Infinite Canvas Math & Physics Scratchpad for Android tablets.**

---

## 🎯 Mission & Overview

**OpenMathPad** is a 100% local, privacy-first infinite scratchpad designed specifically for solving STEM problems, derivations, and physics diagrams on Android tablets:

- **144Hz Low-Latency Inking**: Ultra-responsive drawing loop profiling with zero per-frame heap allocations inside `DrawScope` for buttery smooth stylus tracking on high-refresh-rate displays.
- **Authentic Uniform Ballpoint Pen**: Crisp, constant-width ballpoint/gel pen strokes (presets: Thin 1.5dp, Normal 2.5dp, Thick 4.0dp) and semi-transparent highlighters (50% alpha). Zero post-stroke morphing or styling deformation upon `ACTION_UP`.
- **Natural Midpoint Quadratic Bezier Smoothing**: Eliminates digitizer quantization jitter while strictly preserving authentic handwriting trajectory, symbol sharpness, and mathematical notation.
- **Classic Squared Paper Grid (Тетрадь в клеточку)**: Standard 5mm school notebook grid lines dynamically scaled and shifted with canvas zoom and pan. Easily toggle between Squared Grid and Blank canvas.
- **GeoGebra-Lite Smart Geometry & Snapping**:
  * **Draw & Hold Line Straightening**: Holding the pen stationary at the end of a stroke for $\ge 400\text{ms}$ automatically straightens it into a crisp line segment.
  * **Endpoint Magnetic Snapping**: Snaps stroke endpoints to nearby vertices ($\le 24\text{px}$) or grid intersections to draw closed polygons, triangles, coordinate axes, and vectors effortlessly.
  * **Cartesian Coordinate Axes (XY)**: Quick action toggle to project an aligned Cartesian coordinate system with directional arrows and origin $(0, 0)$.
  * **Ruler Mode**: Restricts inking strictly to straight line segments.
- **100% Hardware Palm Rejection**: Active stylus (`TOOL_TYPE_STYLUS`, `TOOL_TYPE_ERASER`) draws freely while capacitive palm touches are cleanly rejected. An optional touch-drawing toggle is available for finger sketching.
- **Infinite Canvas Navigation**: Smooth two-finger panning and pinch-to-zoom (0.2x to 5.0x) with coordinate scene transformations.
- **Task & Problem Image Insertion**: Import textbook or assignment screenshots directly onto the canvas, automatically centered in the current viewport, with persistent sandbox storage (`context.filesDir/task_images/`) and interactive 4-corner proportional resize handles.

---

## 🏗️ Architecture Overview

OpenMathPad uses a clean, reactive architecture with Android Jetpack Compose and `StateFlow`:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│   InfiniteCanvas  •  CanvasToolbar  •  CanvasImageCard  • Diagnostics  │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ StateFlow / MotionEvents
┌────────────────────────────────────▼───────────────────────────────────┐
│                      CanvasViewModel State Holder                      │
│   Undo/Redo History • Telemetry • Coordinate Mapping • Hold-to-Straight│
└──────────────┬──────────────────────────────────────────┬──────────────┘
               │ Screen to Scene Transform                │ Persistent Images
┌──────────────▼─────────────┐             ┌──────────────▼──────────────┐
│  Low-Latency Inking Engine │             │    Task Media Management    │
│ • PalmRejectionFilter      │             │ • TaskImageManager          │
│ • GeometrySnapper (Snap/XY)│             │ • Downscale & EXIF rotation │
│ • BezierSmoother (Midpoint)│             │ • Internal Storage Sandbox  │
│ • Cached Path & StrokeStyle│             │ • Proportional 4-Corner Box │
│ • StrokeEraser (Geo Bounds)│             └─────────────────────────────┘
└────────────────────────────┘
```

### 1. Low-Latency Inking & Geometry Engine (`BezierSmoother`, `GeometrySnapper`, `DrawingStroke`)
* **Real-Time Midpoint Quadratic Bezier Interpolation**: Interpolates smooth C1 curves between point midpoints using control points. Smooths high-frequency hardware jitter without deforming mathematical symbols or numbers.
* **Uniform Ballpoint Pen Rendering**: Clean, constant-width lines with `StrokeCap.Round` and `StrokeJoin.Round`. Eliminates erratic width variation for readable mathematical formulas.
* **Smart Line Straightening & Magnetic Snapping**: Real-time draw-and-hold timer converts strokes into straight lines, snapping to vertices and grid intersections.
* **Path & Style Caching**: Finished strokes lazily cache their rendered `androidx.compose.ui.graphics.Path` and `DrawScope.Stroke` style for zero-cost pans and zooms.
* **Zero-Allocation Rendering**: Eliminates per-frame heap allocations inside `DrawScope` on 144Hz displays.

### 2. Infinite Canvas & Squared Paper Grid (`InfiniteCanvas`, `CanvasViewModel`)
* **Coordinate Transformations**: Converts screen pixel coordinates to infinite scene coordinates:
  $$\text{sceneX} = \frac{\text{screenX} - \text{panOffsetX}}{\text{zoomScale}}, \quad \text{sceneY} = \frac{\text{screenY} - \text{panOffsetY}}{\text{zoomScale}}$$
* **Two-Finger Navigation**: Distinguishes single-pointer stylus inking from two-finger gestures (pan and pinch-to-zoom clamped between 0.2x and 5.0x).
* **Infinite Squared Grid**: Renders subtle notebook grid lines in $O(1)$ constant time strictly within the active viewport bounds.
* **Cartesian Axes**: Displays scaled X and Y axes with directional arrows and $(0, 0)$ origin mark.

### 3. Task Image Import & Resizing (`TaskImageManager`, `CanvasImageCard`)
* **Secure Sandbox Storage**: Problem images are copied directly to `context.filesDir/task_images/` to prevent URI permission expiration.
* **Viewport Centered Insertion**: Spawns newly inserted tasks at the center of the user's current view.
* **Memory-Safe Downscaling**: Automatically decodes images with sample sizing to avoid Out-Of-Memory errors with high-resolution photos.
* **Interactive Geometry**: Move images anywhere on the infinite canvas; resize proportionally via 4 circular corner handles.

---

## ⚡ Performance & Hardware Optimization

OpenMathPad is optimized for **modern large-screen tablets (e.g., Xiaomi Pad 7 Pro with 144Hz display, Samsung Galaxy Tab S-series, Lenovo Tab Pen Plus)**:

| Optimization Vector | Implementation Strategy | Impact |
| :--- | :--- | :--- |
| **Hot-Path Allocations** | Completely eliminated polygon meshing, per-segment line loops, and heap allocations inside `Canvas` / `DrawScope`. Cached `Path` and `Stroke` styles. | Eliminates GC churn and frame drops; maintains solid 144 FPS drawing. |
| **Input Latency** | Direct real-time coordinate streaming without pull-string delay. Historical batching via `MotionEvent.getHistoricalX/Y/Pressure`. | Zero pen tip trailing lag. |
| **WYSIWYG Fidelity** | Active stroke committed directly to stroke list upon `ACTION_UP` without post-processing mutations. | Zero visual morphing or jarring deformation upon stylus release. |
| **Grid Rendering** | Dot grid calculated only within visible viewport bounding box. | Constant-time rendering regardless of canvas pan offset. |
| **Memory Safeguards** | Undo snapshot stack capped to 50 levels (`MAX_UNDO_STACK_SIZE`). Image bitmaps decoded with memory downscaling. | Zero memory leaks across extended drawing sessions. |

---

## 📱 Hardware & Stylus Compatibility

- **Active Stylus Protocols**:
  - Xiaomi Smart Pen / Focus Pen (Xiaomi Pad 6 / 7 / Pro series)
  - Samsung S-Pen (Wacom EMR protocol)
  - Lenovo Precision Pen / Tab Pen Plus
  - Universal Stylus Initiative (USI 2.0)
- **Capacitive Styluses & Finger Drawing**: Supported when toggling **Touch Mode** in the toolbar.

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
1. **Java Development Kit (JDK):** Version 17 or 21.
2. **Android SDK:** Compile SDK 37, Min SDK 24 (Android 7.0+).
3. **Android Studio:** Ladybug (2024.2.1+) or Meerkat (2024.3.1+).

### Clone & Build

```bash
# Clone the repository
git clone https://github.com/pochemu-august/OpenMathPad.git
cd OpenMathPad

# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests
./gradlew :app:testDebugUnitTest
```

### Windows One-Click Static Analysis & Build Script

```cmd
run_analysis.bat
```

The batch script runs Gradle verification, executes unit tests, performs lint analysis, and stores reports in `analysis_reports/latest/`.

---

## 🔍 Diagnostics & Memory Profiling

- **In-App Telemetry Badge**: Real-time HUD displaying active tool type (`STYLUS`, `FINGER`, `ERASER`), live hardware pressure, zoom factor, pan coordinates, accepted events, and rejected palm touches.
- **LeakCanary 2.14**: Integrated in `debug` builds to monitor view models, Compose trees, and context leaks.
- **Clean Git Tracking**: `.gitignore` configured to exclude IDE caches, build outputs, `.agent/` directories, and analysis dumps.

---

## 📄 License

This project is open-source and licensed under the **[MIT License](LICENSE)**.

```
Copyright (c) 2026 augustuz / OpenMathPad Contributors
```
