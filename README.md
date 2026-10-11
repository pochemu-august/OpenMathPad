# OpenMathPad 📐✨

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-green.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.05.01-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Adaptive%20Expressive-orange.svg)](https://m3.material.io)
[![Display](https://img.shields.io/badge/Display-144Hz%20Ultra--Low%20Latency-ff69b4.svg)](#performance--hardware-optimization)
[![Tests](https://img.shields.io/badge/Unit%20Tests-64%2F64%20Passing%20(100%25)-success.svg)](#running-tests)
[![Diagnostics](https://img.shields.io/badge/LeakCanary-2.14%20Integrated-yellow.svg)](#diagnostics--memory-profiling)

> **Free, open-source, tablet-first alternative to MyScript Math and Nebo for STEM learners, students, educators, and engineers.**

---

## 🎯 Mission Statement

Proprietary digital math notebooks frequently lock users into expensive recurring subscriptions, restrict calculations behind closed-source cloud servers, and fail to take full advantage of high-refresh-rate active digitizers.

**OpenMathPad** is built from the ground up as a **100% local, privacy-first, open-source math workspace**. It turns your Android tablet into an intelligent calculation sheet:
- **Write naturally** with active styluses using hardware-accelerated Bezier curve smoothing and pressure dynamics.
- **Compute instantly** through an offline, high-precision AST-based Math Engine.
- **Recognize formulas** using an on-device ONNX handwriting OCR pipeline with zero cloud roundtrips.
- **Keep complete ownership** of your notes, variables, and math scratchpads.

---

## 🏗️ Architecture Overview

OpenMathPad employs a modular, reactive architecture designed for zero-jitter rendering at high frame rates:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│   StylusCanvas  •  CanvasToolbar  •  FormulaOverlayCard  • Diagnostics │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ StateFlow / MotionEvents
┌────────────────────────────────────▼───────────────────────────────────┐
│                      CanvasViewModel State Holder                      │
│   Undo/Redo History (Capped) • Telemetry Tracking • Math Synchronization│
└──────────────┬──────────────────────────────────────────┬──────────────┘
               │                                          │
┌──────────────▼─────────────┐             ┌──────────────▼──────────────┐
│    Stylus Inking Engine    │             │       Math Engine Core      │
│ • PalmRejectionFilter      │             │ • MathLexer (Tokens)        │
│ • PressureSmoother (LowPass│             │ • MathParser (Pratt AST)    │
│ • BezierSmoother (Midpoint)│             │ • MathEvaluator (Exec Steps)│
│ • StrokeEraser (Geo Bounds)│             │ • LatexSanitizer (Clean up) │
└──────────────┬─────────────┘             └──────────────▲──────────────┘
               │ Render / Points                          │ Normalized LaTeX
┌──────────────▼──────────────────────────────────────────┴──────────────┐
│                  On-Device ONNX OCR Pipeline                           │
│ • CanvasRasterizer (Vector-to-Tensor normalization)                    │
│ • OnnxMathOcrEngine (Microsoft ONNX Runtime / Heuristic fallback)       │
│ • Dynamic Placement Point Calculation (Right of expression)            │
└────────────────────────────────────────────────────────────────────────┘
```

### 1. Native Low-Latency Stylus Inking
* **Hardware Palm Rejection (`PalmRejectionFilter`):** Distinguishes hardware stylus events (`TOOL_TYPE_STYLUS`, `TOOL_TYPE_ERASER`) from resting palms and capacitive touch (`TOOL_TYPE_FINGER`). Allows toggling touch-drawing mode on demand.
* **Pressure Dynamics & Exponential Filtering (`PressureSmoother`):** Applies an exponential low-pass filter to raw stylus pressure without object boxing, scaling stroke line width dynamically from 0.35x to 1.75x.
* **Midpoint Quadratic Bezier Smoothing (`BezierSmoother`):** Batches native `MotionEvent` historical input samples (`event.historySize`) and interpolates smooth curves across midpoints to prevent polyline jaggedness.
* **Stroke Eraser (`StrokeEraser`):** Bounding-box pre-filtering followed by segment-distance geometric hit-testing for instant, lag-free erasing of written math strokes.

### 2. High-Performance Math Engine
* **Lexer & Parser:** Hand-crafted lexer supporting standard arithmetic, implicit multiplication (`2pi`, `3(x+1)`), exponentiation, roots (`sqrt`, `cbrt`), factorials, trigonometric and hyperbolic functions, logarithms, and integration heuristics.
* **Precedence Climbing AST:** Pratt-style precedence climbing parser generating a strongly-typed Abstract Syntax Tree (`ExprNode`).
* **Evaluation Context:** Variable binding (`x = 10`, `y = 2.5`), custom constants, configurable precision, and dynamic angle modes (**Radians** vs **Degrees**).
* **Step-by-Step Breakdown:** Records intermediate evaluation steps (`EvaluationStep`) for transparent mathematical derivation.
* **LaTeX Sanitizer:** Sanitizes LaTeX expressions into canonical machine-readable math notation.

### 3. Modular ONNX OCR Pipeline
* **Canvas Rasterizer (`CanvasRasterizer`):** Transforms vector stroke paths into normalized 512×128 monochrome tensors with bounding-box auto-centering and padding.
* **On-Device Inference (`OnnxMathOcrEngine`):** Integrates Microsoft ONNX Runtime Android SDK (`ai.onnxruntime:onnxruntime-android:1.17.0`) for offline handwritten formula recognition, backed by heuristic fallback for instant out-of-the-box functionality without requiring separate model downloads.
* **Smart Placement Point Calculation:** Automatically positions the evaluated expression result (e.g. `= 31.42`) immediately to the right of the handwritten formula with full undo/redo history support.

---

## ⚡ Performance & Hardware Optimization

OpenMathPad is specifically tailored for **modern large-screen tablets (such as the Xiaomi Pad 7 Pro with 144Hz display, Samsung Galaxy Tab S-series, and Lenovo Tab Pen Plus)**:

| Optimization Vector | Implementation Strategy | Impact |
| :--- | :--- | :--- |
| **Hot-Path Allocations** | Zero object allocations inside `pointerInteropFilter`, `Canvas`, and `drawBehind` loops. Pre-cached `DrawingStroke.isUniformWidth`, cached `DrawScope.Stroke` styles, and cached `TextStyle`. | Eliminates garbage collection churn and dropped frames at 144Hz. |
| **Grid Rendering** | Background dot grid is computed and cached via `Modifier.drawWithCache` using batched `DrawScope.drawPoints`. | Avoids hundreds of redundant `drawCircle` calls per frame; redrawn only on canvas resize. |
| **StateFlow Emissions** | Combined stroke mutation and diagnostics telemetry update into a single atomic `MutableStateFlow.update` call. | Reduces StateFlow notifications and recomposition passes by 50% during stroke gestures. |
| **Historical Batching** | Extracts all high-rate digitized points from `MotionEvent.getHistoricalX/Y/Pressure/EventTime`. | Zero input loss between screen refresh frames. |
| **Memory Safeguards** | Undo snapshot stack capped to 50 levels (`MAX_UNDO_STACK_SIZE`). ViewModel overrides `onCleared()` to free native ONNX sessions and cached paths. LeakCanary integrated in debug builds. | Zero memory leaks or unbounded RAM growth across hours of continuous usage. |

---

## 📱 Device Compatibility & Form Factors

- **Large Tablets (Width ≥ 840dp):** Displays an adaptive side-panel formula overlay card alongside the full-screen stylus canvas.
- **Compact Screens & Split-Screen (Width < 840dp):** Collapses the formula overlay into an animated bottom sheet with expandable detail tabs.
- **Active Stylus Hardware:**
  - Xiaomi Focus Pen / Smart Pen (Xiaomi Pad 6/7/Pro series)
  - Samsung S-Pen (Wacom EMR protocol)
  - Lenovo Precision Pen / Tab Pen Plus
  - Generic USI 2.0 styluses and capacitive pens

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
1. **Java Development Kit (JDK):** Version 17 or Version 21.
2. **Android SDK:** Compile SDK 37, Min SDK 24 (Android 7.0+).
3. **Android Studio:** Ladybug (2024.2.1+) or Meerkat (2024.3.1+).
4. **Git**

### Clone & Build

```bash
# Clone the repository
git clone https://github.com/pochemu-august/OpenMathPad.git
cd OpenMathPad

# Build debug APK
./gradlew assembleDebug

# Run unit tests (64 tests)
./gradlew testDebugUnitTest
```

### Running Tests

To verify that the Math Engine, Canvas Rasterizer, Palm Rejection, and Stroke Smoothing logic are passing with zero regressions:

```bash
# On Linux / macOS
./gradlew :app:testDebugUnitTest

# On Windows PowerShell
.\gradlew.bat :app:testDebugUnitTest
```

### Tablet Debugging Setup (Wi-Fi & USB)

To test low-latency stylus drawing on a physical Android tablet:

1. **Enable Developer Options:**
   - Go to **Settings** > **About Tablet** > Tap **Build Number** 7 times.
2. **Configure Display & Input:**
   - Go to **Settings** > **Display** > Set **Refresh Rate** to **Custom / Maximum (144Hz or 120Hz)**.
   - Go to **Developer Options** > Enable **USB Debugging**.
   - *(Optional for stylus testing)* Enable **Pointer location** to inspect raw touch telemetry.
3. **Wi-Fi Debugging (Android 11+):**
   - Connect tablet and workstation to the same Wi-Fi network.
   - Go to **Developer Options** > Enable **Wireless Debugging**.
   - Tap **Pair device with pairing code**.
   - On workstation:
     ```bash
     adb pair <ip_address>:<port> <pairing_code>
     adb connect <ip_address>:<port>
     ./gradlew installDebug
     ```

---

## 🔍 Diagnostics & Memory Profiling

- **Real-Time Input Diagnostics:** An in-app telemetry HUD at the bottom-left of the canvas displays active tool type (`STYLUS`, `FINGER`, `ERASER`), real-time pressure, historical sample count per event, and accepted vs palm-rejected input counts.
- **LeakCanary 2.14:** Automatically runs in `debug` builds to monitor ViewModels, Compose remember nodes, and Context retainers.
- **Android Lint Baseline:** Strict Lint configuration configured in `app/build.gradle.kts` with baseline tracking and suppression of experimental Compose API warnings.

---

## 🗺️ Project Roadmap

- [x] High-precision Midpoint Bezier smoothing & pressure scaling.
- [x] Hardware palm rejection & real-time telemetry badge.
- [x] Lexer, precedence climbing parser, and step-by-step evaluator.
- [x] On-device ONNX handwriting OCR pipeline with heuristic fallback.
- [x] Tablet-adaptive Material 3 side panel and bottom sheet overlay.
- [x] Hot-path zero-allocation draw optimizations for 144Hz screens.
- [x] LeakCanary 2.14 & Android Lint diagnostic setup.
- [ ] Interactive 2D Function Graphing with gesture zoom & pan.
- [ ] Multi-page notebook document management (Room DB + vector serialization).
- [ ] Export notebooks to PDF, SVG, and LaTeX documents.
- [ ] Extended mathematical symbol recognition (matrices, Greek letters, summations).

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork the repository** and create a feature branch (`git checkout -b feature/amazing-feature`).
2. **Follow Kotlin Coding Conventions** and preserve existing comments and architectural patterns.
3. **Verify Performance:** Do not introduce object allocations inside high-frequency draw loops or `pointerInteropFilter`.
4. **Run Unit Tests:** Ensure all unit tests pass cleanly:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
5. **Open a Pull Request** with a detailed explanation of your changes.

---

## 📄 License

This project is open-source and licensed under the **[MIT License](LICENSE)**.

```
Copyright (c) 2026 augustuz / OpenMathPad Contributors
```
