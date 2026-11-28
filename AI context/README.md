# PenLab UI Redesign — AI Agent Handoff Context

> **Branch:** `ui/redesign` (forked from `main` @ `c0c15ae`)  
> **Status:** **Stages 0, 2, 3, 4, 5 & 6 COMPLETE, Tab S9 QA-VERIFIED, AND PUSHED on `ui/redesign`** (`BUG-00`..`BUG-17` closed; [`notes/TEST-PLAN-2026-11-27.md`](notes/TEST-PLAN-2026-11-27.md) Parts A, C, D, E verified PASS; all 6 Stage 6 features `6.1`–`6.6` verified PASS).  
> **Next Stage:** **Stage 7 (Production hardening: `:app:lintRelease`, multi-module tests, universal signed release APK & landscape/portrait stress QA)** before merging `ui/redesign` to `main` (plus optional post-release **Stage 8** `feat/library-trash-and-sort`). See [`notes/OVERNIGHT-AUDIT-2026-11-27.md`](notes/OVERNIGHT-AUDIT-2026-11-27.md).

---

## 1. Product & User Context

- **App:** PenLab (`dev.penlab.app`, GPL-3.0) — free, open-source native Android handwriting & note-taking app for tablets.
- **Primary Persona:** University mathematics TA (Calculus 1 & 2, Probability, Differential Equations). Writes worked solutions live with a stylus while mirroring the tablet to a classroom projector, and preps notes / annotates PDFs (past exams, problem sheets) at home. Students also use it.
- **Core UX Principle:** *Operate mode* — speed, reliability, restraint, zero "vibecoded" clutter. Open, pick a pen, write. The page and ink are the product; chrome recedes into crisp floating islands.
- **Target Hardware:** 11-inch Android tablet, landscape (`2560 × 1600 px` @ `340 dpi` ≈ `1205 × 753 dp`; tested on **Samsung Galaxy Tab S9**, `SM-X716B`, Android 16 / API 36).
- **Tech Stack:**
  - Native Android, Kotlin `2.3.20`, AGP `9.0.1`, Gradle `9.1.0`, `compileSdk = 36`, `targetSdk = 36`, `minSdk = 30`, `jvmToolchain(17)`.
  - Jetpack Compose BOM `2026.03.01` + Material 3.
  - Icons: **Phosphor Duotone** (`com.adamglin:phosphor-icon:1.0.0`, `PhosphorIcons.Duotone.*` + `PhosphorIcons.Bold.Check` for bare circular swatch checks + `PhosphorIcons.Fill.BookmarkSimple` for active bookmarks).
  - Title Font: **Bricolage Grotesque** variable (`app/src/main/res/font/bricolage.ttf`, OFL) strictly for Display/Headline/Title; system sans (`FontFamily.Default`) for body/controls/labels; tabular numerals (`fontFeatureSettings = "tnum"`) for zoom %, angle °, page counters, and mm readouts.
  - Ink & Search: Jetpack Ink `1.0.0`, `androidx.input:input-motionprediction:1.0.1`, ML Kit Digital Ink `19.0.0`.
  - No WebViews.

---

## 2. Mandatory Development & Safety Rules

1. **Delegated Subagent Workflow:**
   - The main coordinator **never writes application code directly**—delegate each stage to a dedicated **Builder** subagent.
   - After every stage (or small feature), spawn a **QA** subagent to install the debug APK on the live tablet via Wireless ADB, exercise every feature as a real user, capture and visually inspect screenshots, and check `logcat` for crashes.
   - If QA reports any bugs or visual defects, spawn a **Fixer** subagent to fix them, rebuild/test, and run **Re-QA** until **PASS**.
   - Commit cleanly on `ui/redesign` after each stage passes QA.
2. **ABSOLUTE TABLET SAFETY RULES:**
   - Another app called **`graphite`** is installed on the user's tablet. **NEVER** launch, inspect, kill, clear, uninstall, or touch anything related to `graphite`! Only ever interact with `dev.penlab.app`.
   - **NEVER** run `adb uninstall dev.penlab.app` or `adb shell pm clear dev.penlab.app` — preserve user notebooks in `/sdcard/Documents/PenLab/` (e.g. `bombo`, `test1`). Always update via `adb install -r -d ...`.

---

## 3. Completed Work & Git History (`ui/redesign`)

| Commit | Stage | Summary & Verified Deliverables |
|---|---|---|
| `6077165` | **Stage 1** | 3-layer design token system (`PenLabPrimitives` → `penLabColorScheme` / `LocalPenLabColors` for Light & Dark × Blueprint & Copper → `PenLabComponentTokens`), `PenLabSpacing`, `PenLabShapes`, `PenLabTypography` + `PenLabTextStyles` (`tnum`), `ThemeMode` (`LIGHT`/`DARK`/`SYSTEM`), `PageThemeMode` (`LIGHT`/`DARK`), Phosphor Duotone icon registry (`ui/Icons.kt`), 6 WCAG contrast tests (`ThemeTokensTest.kt`). **Tablet QA: PASS.** |
| `b838acb` | **Stage 2** | Reusable token-driven component library (`ui/components/ChromeComponents.kt`, `MenuAndDialogComponents.kt`), unified `SettingsSheet.kt` (Appearance, Writing & Input, Storage), live `InkCanvasView.setDarkPage(Boolean)` (updates paper & ink live without losing strokes; PDFs stay white), persisted `shapeSnapping`, Light-theme contrast audit across all screens, mutual-exclusion canvas popover dismissal, `ViewSettingsLogicTest.kt`. **Tablet QA → Fixer → Re-QA: PASS.** |
| `1952b30` | **Stage 3** | Sketch 1 **3-island floating top chrome** (`ui/CanvasTopChrome.kt`: Left `FileChromePill` with `FileQuickMenuPopover` [recent files + Rename/Move/Turn] & `FileOptionsPopover` [Export, Share, honest Sync to Drive via SAF, Present, Settings, Report bugs]; Center customizable `DockedToolbar` with `||` drag handles on both ends, **4 customizable pens** [Black/Blue/Red/Forest Green `#1E7E3E`], Eraser, Select, Fill, Tools, More `▾`, Undo/Redo; Right `PageConfigChromePill` [Search, inline `− 100% +` zoom stepper + View presets popover, Fullscreen, unified Page Config]). Full `ToolbarConfig.kt` + Settings Toolbar Customizer (`1..6` pens, dock position, toggle & reorder items, reset). `ToolbarConfigTest.kt` (8 tests). **Tablet QA: PASS.** |
| `80bc38c` | **Stage 4** | Polished **Pen Settings** (Ballpoint/Pressure/Fountain paper-matched live stroke cards, 5 line patterns, stroke width slider + favorites, 4-col swatches with bare bold check `PenLabIcons.CheckBare`, `More colours` + 1-tap `Pick` eyedropper), **Eraser** (Complete vs Partial segmented control + circle radius preview), **Tools** (icons + subtitles), **More** (Insert / Canvas / destructive Clear groups), **Color & Marker Wheel** (safe-zone `computeColorWheelDiameter`, 0px jump bottom pill, Light/Dark RGB picker, dual-stroke reticles), **Unified Page Config** (fixed 2-row Pages grid height, Light/Dark live paper mini-previews + `"PDF pages stay white"` pill, Layers visible Rename/Delete actions). `ToolPanelHelpersTest.kt` (8 tests). **Tablet QA → Fixer → Re-QA: PASS.** |
| `b4b4649` | **Stage 5** | Sketch 2 **Lasso Selection Bar** (`[ Copy \| Cut \| Paste \| Dupl. \| Delete \| Mirror H \| Mirror V \| More ▾ ]` with Phosphor Duotone icons, muted disabled Paste when clipboard empty, `More ▾` popover with Fill/Deselect + Plugins section including `Convert to LaTeX` & `Clean up diagram` extensible slots + honest notice toast), upgraded `PasteMenu` & floating lone Paste pill, compact tonal `PDF` badge in `FileChromePill`, upgraded `ExportLookDialog` (visual Light paper vs Dark page preview cards + vector ink note), whisper-quiet translucent Present Mode corner pill (`PenTools` + `Close`) + entry toast + 4s toolbar peek, translucent Fullscreen exit pill. `SelectionAndPresentTest.kt` (9 tests). **Tablet QA: PASS.** |

### Phase 1 Fixes (26 Nov 2026, QA-Verified on Tab S9)
- **Dark-paper cover photo inversion fixed:** `CoverMaker.make`, `InkCanvasView.pagePicture`, `Covers`, `MainActivity`, `LibraryScreen`, and `LibraryCards` now generate and load `cover-dark.jpg` for non-PDF notebooks on dark paper using `InkColors.onScreen` / `ItemPainter.draw`, keeping `ImageItem(isInk = false)` photos un-inverted while rendering ink, text, and `#18191B` paper properly (falling back to `InvertCover` on `cover.jpg` until `cover-dark.jpg` exists). `CoversTest.kt` (6 tests).
- **`.trash/` library migration fixed:** `LibraryMigration.run` now also migrates private `.trash/` notebooks into `Documents/PenLab/.trash/` with `sameContents` verification and ID-clash handling (`LibraryMigrationTest` expanded to 13 tests).
- **Opt-in single-ABI build filter:** `app/build.gradle.kts` supports `-Ppenlab.abi=arm64-v8a` for compact ~13.3 MiB non-`testOnly` release builds without affecting universal default builds.

### Phase 2 — Projector View Full Content & Committed-Scene Caching (26 Nov 2026, QA-Verified on Tab S9)
- **Projector View full content & caching:** `ProjectorSnapshot` / `ProjectorSurfaceView` / `InkCanvasView.publishProjector` now render PDF pages (`PdfBackground`), `ImageItem` photos, `TextItem` text boxes (using thread-safe `ThreadLocal<TextPaint>` in `TextItems`), and `PluginPageItem` placeholders alongside ruling & committed ink, backed by hybrid `StaticCacheTracker` committed-scene bitmap caching on the projector render thread (`ProjectorLogic.kt`, 11 tests in `ProjectorLogicTest.kt`), QA-verified on Tab S9 with a simulated secondary display.

### Phase 3 — Plugin Bridge Polish (26 Nov 2026, QA-Verified on Tab S9)
- **Cancel button for running plugin calls:** `PluginTransport.cancel` / `BoundServiceTransport` in-flight tracking / `TransportOutcome.Cancelled` → `CancellationException` (no crash-disable) wired through `ExternalPageAction.cancel()` and `MainActivity`'s `"Running…"` snackbar `"Cancel"` button + `closeNotebook`/`onDestroy` cleanup.
- **External `@drawable` menu icons:** `"icon"` manifest parsing in `ExternalManifest.kt`, bounded (`<= 96px`) LRU cross-package loader in `ExternalPluginIcons.kt`, `HasExternalIcon` on `ExternalPageAction`/`ExternalScreen`/`ExternalMedia`, shared `PluginMenuRow` rendering across `MorePanel`/`SelectionActions`/`FileOptionsPopover`, and `sample-plugin` `ic_wordcount` reference example.
- **Tests & device QA:** 495 JVM unit tests passing (`:app` 451 + `:plugin-sdk` 44, including `ExternalPluginIconsTest`, expanded `ExternalManifestTest` & `ExternalPluginTest`), verified on Tab S9 (`arm64-v8a` R8 build).

### Phase 4 — Pen-Up `StrokeChunkCache` Optimization & `lintRelease` Polish (26 Nov 2026, QA-Verified on Tab S9)
- **`StrokeChunkCache` pen-up optimization:** `CHUNK_SIZE` lowered `64 → 16`, `ZOOM_REFRESHES_PER_FRAME` `4 → 8`, `PREPARES_PER_FRAME` `3 → 6`, plus O(1) incremental `bounds.union(...)` on append (`StrokeChunkCache.kt`).
- **`lintRelease` & release polish:** `0` lint errors (`87` warnings, `0` `ContentDescription` / `RtlHardcoded`); fixed `InlinedApi` (`FingerTapDetector.kt`), `UseCompatLoadingForDrawables` (`ExternalPluginIcons.kt`), `LocalContextResourcesRead` & `AutoboxingStateCreation` (`MainActivity.kt`), and steady-state `SelectionView.onDraw` `DrawAllocation` (`InkCanvasView.kt`).
- **Verified:** 495 JVM unit tests passing + `:sample-plugin:compileDebugKotlin`, `arm64-v8a` R8 APK (`13.32 MB`), and 4-theme QA PASS on Tab S9 (`Dark/Light × Blueprint/Copper`, `gfxinfo` `50th: 5ms`, `90th: 8ms`, zero `logcat` errors).

### Stage 1 (Round 2) — S Pen Pressure Sensitivity Calibration (26 Nov 2026, QA-Verified on Tab S9)
- Added `PressureSensitivity` (`Light` / `Balanced` [default] / `Firm`), `PressureCurveSpec`, and calibrated per-style curve tables in `app/src/main/kotlin/dev/penlab/app/ink/PressureCurve.kt`.
- Updated `PenBrushes.kt` with pre-allocated `BrushFamily` variants for Pressure & Fountain pens while keeping `"pressure"` / `"fountain"` `.graphite` serialization IDs 100% backward-compatible.
- Persisted `pressureSensitivity` in `ViewSettings.kt`, exposed `Light` / `Balanced` / `Firm` segmented control under **Settings → Writing & input** (`SettingsSheet.kt`), and wired into `InkCanvasView.kt` / `MainActivity.kt`.
- Verified `:app:testDebugUnitTest` (`54` suites, `491` tests, `0` failures — including `PressureCurveTest` & `ViewSettingsLogicTest`) and Tab S9 R8 release QA (`13.34 MB` `arm64-v8a` APK, `CN=PenLab, O=penlab-app`).

### Stage 2 (Round 2) — "Add from PDF" Page Scroller + Region Crop & Place (26 Nov 2026, QA-Verified on Tab S9)
- Added pure-Kotlin `PdfCropGeometry.kt` (`NormalizedCropRect`, `CropHandle`, `hitTestCropHandle`, `applyCropDrag`, `PixelCropBounds`, `computeHighResCropBounds`).
- Added `PdfCropDialog.kt` with single-thread `PdfCropSession`, bounded thumbnail & page preview `LruCache`s (`16 MB` / `24 MB`), scrollable left page strip (`LazyColumn`), interactive crop overlay (`28.dp` touch handles, outer scrim, thirds grid), footer page navigation / `Full page` / `Reset`, and high-res lossless PNG crop export saved into the open notebook's `images/` directory.
- Clamped tall snippets cleanly in `InkCanvasView.addImage` (`h <= visible.height() * 0.72f`) and rewired `MainActivity.kt` canvas `onAddFromPdf` (`pickPdfFromCanvas`) away from `importPdfAndOpen` while keeping Library PDF import untouched.
- Verified `:app:testDebugUnitTest` (`55` suites, `497` tests, `0` failures — including 6 `PdfCropGeometryTest` tests & `TypographyDisciplineTest`) and Tab S9 end-to-end R8 release QA.

### Stage 3 (Round 2) — Movable, Collapsible Toolbar & Floating Side-Snap Pills (26 Nov 2026, QA-Verified on Tab S9)
- Added `DockSide.FLOATING` (`floatX`/`floatY` persistence) and `DockDrop.resolveTarget` (`EDGE_SNAP_DP = 76f`, corner-first -> edge snap -> interior free-floating drop) in `ToolDock.kt`.
- Added persisted `ToolDock.collapsed` state + `collapsedToolbarItems(items, tools)` keeping only `PEN` slots and `ERASER` plus a `CollapseCell` expand/collapse toggle on `Bar` and `Rail`.
- Converted `LEFT`/`RIGHT` docked `Rail` from full-height bezel-flush slabs into centered, edge-inset floating vertical capsule pills (`RoundedCornerShape(28.dp)`, `floatingChromeSurface`, `EdgeGap = 12.dp`) while preserving `setGestureExclusion`, updated `isOnDock`, and kept Corner Wheel untouched.
- Added `DockPositionOption.FLOATING("Float")` in `SettingsSheet.kt` and updated `MainActivity.kt` selection clearance (`RailWidth + 12.dp`).
- Verified `:app:testDebugUnitTest` (`500` tests, `0` failures — including 9 `ToolbarPlacementTest`, 34 `WheelGeometryTest`, and `TypographyDisciplineTest`) and Tab S9 R8 release QA.

### Stage 4 (Round 2) — Final Audit & Multi-Theme QA Sign-off (26 Nov 2026, QA-Verified on Tab S9)
- Full multi-module unit tests (`./gradlew testDebugUnitTest`): **59 suites, 554 tests, 0 skipped, 0 failures, 0 errors** (`:app` 500, `:plugin-api` 7, `:plugin-sdk` 47, `:sample-plugin` 0).
- `:app:lintRelease`: **0 errors** (`94` pre-existing warnings; `0` warnings in `PressureCurve.kt`, `PenBrushes.kt`, `SettingsSheet.kt`, and `PdfCropGeometry.kt`; remaining hits in `ToolDock.kt`/`InkCanvasView.kt`/`MainActivity.kt`/`PdfCropDialog.kt`/`ViewSettings.kt` are pre-existing style hints such as `UseKtx` / `ModifierParameter` / `VisibleForTests` / `DrawAllocation`).
- Tab S9 hardware multi-theme QA (`Light + Copper` left floating capsule pill and `Dark + Blueprint` collapsed free-floating bar verified read-only on `DSU Sheet`, all original settings restored, `0` logcat errors).

### Overnight Audit & Stage 0 (27 Nov 2026, QA-Verified on Tab S9)
- **Stage 0 fix (`PresentMode.kt`, `MainActivity.kt`, `ScreenChromeTest.kt`):** Removed horizontal `railLeft`/`railRight` shift on top-row floating chrome (`FileChromePill`, `PageConfigChromePill`, `Leave full screen`) via pure-Kotlin `topChromeHorizontalPaddingDp` (`555` unit tests passing including `ScreenChromeTest` 10/10; `0 px` top-chrome shift verified on Tab S9).
- **Reusable QA Suite (`tools/qa/`):** Packaged `PenLabCap.java`, `penlab-cap.jar`, `tablet-cap.sh` (with `dev.penlab.app` focus + `Pop-up window` resumed-app privacy guard), `tablet-install.sh`, `ui_stress_audit.py` (with landscape `uiautomator`/display bounds detection, exact temporary notebook UUID tracking, IME-safe dialog entry, and `--self-test`), and `tools/qa/README.md`.
- **Overnight Audit & Stages 2–5 Fix Plan:** Consolidated all verified live-screenshot findings (`Critic A`/`B`/`C` + `penlab_qa`) and static lifecycle/data-loss findings (`BUG-01`..`BUG-17`, excluding external Samsung S-Pen Air Command overlay) in [`notes/OVERNIGHT-AUDIT-2026-11-27.md`](notes/OVERNIGHT-AUDIT-2026-11-27.md).

### Stage 2 (Overnight Fix Pass — 27 Nov 2026, QA-Verified on Tab S9)
- **Fixed `BUG-01`..`BUG-06`:** Viewport-centered `MarkerWheel` + dimmed modal backdrop + shrink-wrapped `ColorWheel` bottom pill + `HslWheel` zero-span guard (`MarkerWheel.kt`, `ColorWheel.kt`), staggered non-overlapping `Protractor`/`Ruler` spawns + baseline degree margin (`Instruments.kt`, `InkCanvasView.kt`), bar-avoidant `PanelBeside` placement + scrollable `InstrumentsPanel`/`MoreToolsPanel` + slider stop-dot removal (`ToolDock.kt`, `ToolPanels.kt`, `MenuAndDialogComponents.kt`), overflow-aligned `FileOptionsPopover` + inline top-row `RecordingPill` (`MainActivity.kt`, `PresentMode.kt`), pruned `cellCenters` + `togglePanel` wheel fold + `0.42f` Light `hubWellColor`, and Dark-themed Eyedropper/Present banners + Eyedropper `BackHandler` + `statusBarsIgnoringVisibility` + neutral `1px` page border.
- **Verified:** `:app:testDebugUnitTest` (**56 suites, 511 tests, 0 failures** — including `InstrumentsTest`, expanded `ScreenChromeTest`, `ToolPanelHelpersTest`, `ToolbarPlacementTest`) and Tab S9 visual/hardware QA (`arm64-v8a` R8 build).

### Stage 3 (Overnight Fix Pass — 27 Nov 2026, QA-Verified on Tab S9)
- **Fixed `BUG-07`..`BUG-10` (+ `BUG-02` Protractor polish):** Pinned top/bottom `Rail` grips with middle-only scrolling, `maxOf(insets.bottom, insets.gestureBottom)` bottom clearance, dedicated `ToolbarExpand`/`ToolbarCollapse` icons (`Icons.kt`), and active hidden-tool collapsed fallback (`ToolDock.kt`); scrollable `SelectionActions`, `SELECTION_BAR_GAP_DP = 56f` rotate-handle clearance, and stronger disabled `Paste` / dark `Delete` contrast (`ToolPanels.kt`, `MainActivity.kt`); translucent `PdfCropDialog` handles, `FlowRow` grouped pagination, `previewError` Retry state, and `"Adapt to dark paper"` snippet inversion (`PdfCropGeometry.kt`, `PdfCropDialog.kt`, `InkCanvasView.kt`); `PenLabDialog` `TextBoxDialog` with `imePadding()`, placeholder, semantics, `canSubmitTextBox`, and `"Tap anywhere on the page to add a text box"` toast (`LibraryScreen.kt`, `RequestDialog.kt`, `MainActivity.kt`); plus extra Protractor top clearance (`Instruments.kt`).
- **Verified:** `:app:testDebugUnitTest` (**56 suites, 516 tests, 0 failures** — including `ToolbarPlacementTest`, `SelectionAndPresentTest`, `PdfCropGeometryTest`, `ToolPanelHelpersTest`, `InstrumentsTest`, `TypographyDisciplineTest`), `arm64-v8a` R8 APK (`14M`, signed `CN=PenLab, O=penlab-app`), and Tab S9 visual/hardware QA.

### Stage 4 (Production Fix Pass — 28 Nov 2026, QA-Verified on Tab S9)
- **Fixed `BUG-11`..`BUG-13` + Stage 3 Residuals (`a6da2bb`):** Tokenized `penLabSwitchColors()` (`Color.kt`, `ChromeComponents.kt`) fixing pitch-black ON switch thumbs, `clipToBounds()` scroll fades + responsive 2×3 `Dock position` wrapping + `48.dp` customizer targets (`SettingsSheet.kt`, `MenuAndDialogComponents.kt`); Light `AmberLightHighlight` (`0xFF9A4407`, `>= 5:1` WCAG AA), Search `imePadding()`, stale-query/count guard, inline `×` clear, `clampSearchStepperIndex` (`SearchUi.kt`, `LibraryScreen.kt`), top-aligned uniform `Continue` cards & generous `computeEndlessCoverBounds` (`CoverMaker.kt`), trimmed `cardMeta` (`LibraryLogic.kt`), tonal header pills + `PhosphorIcons.Regular.*` `⋮`/`›` + `PaperPreview` `.dp.toPx()` (`Icons.kt`, `LibraryCards.kt`, `CanvasTopChrome.kt`); unclipped `NewNotebookDialog` Row 3 + distinct `Grid`/`Engineering`/`Graph` previews, blank-CTA guards (`canConfirmNamedInput` & `RenameDialog` on `PenLabDialog`), `FirstLaunchSheet` `System` theme & double-tap guard, `RequestDialog` `"Location: ..."` & readable disabled `Save`, dashed `+` width chip, brighter `Pages` thumbnails, primary `+ New layer`, canvas Search prev/next stepping, `TextBoxDialog` focused placeholder (`label = null`), and raised `Protractor` `safeTop` clearance (`Instruments.kt`).
- **Verified:** `:app:testDebugUnitTest` (**56 suites, 522 tests, 0 failures** — including `ThemeTokensTest`, `CoversTest`, `LibraryLogicTest`, `InstrumentsTest`, `ToolPanelHelpersTest`, `TypographyDisciplineTest`), `13.38 MB` `arm64-v8a` R8 APK, and Light/Dark Tab S9 visual QA (`logcat` clean).

### Stage 5 (Production Fix Pass — 28 Nov 2026, QA-Verified on Tab S9)
- **Fixed all `BUG-17` Core Invariants (`e4ba417`, building on `9f1c8e4` `BUG-14`..`16`):** Serialized `PageStore.saveFresh` & `transformAndSave` on `SAVE_THREAD` with `turningIds`/`openGeneration` turn guards (`NotebookTurn.kt`, `PageStore.kt`, `MainActivity.kt`); added `canClearPage` & `InkCanvasView.clear(): Boolean` (`InkCanvasHelpers.kt`) so items-only pages clear cleanly with undo; bumped `HistoryFile.VERSION = 5` (`SavedLayers` preserving `hidden` & `current` across undo/redo/reload); synced `pageStyle` after async load via `onPageStyleChanged`; replaced rotation bounds clamp with `ViewSettle.clampPagesInView` (`PullToAdd.kt`); scaled `minZoomFor`/`maxZoomFor` with `trueSizeZoom`; gated `importedHasPdfBackground` on `any { it.backgroundPdfPage >= 0 }` (`NotebookStore.kt`); wrapped `Accent.syncResolvedDark` in `SideEffect` (`Theme.kt`).
- **Verified:** Multi-module `./gradlew testDebugUnitTest` (**62 suites, 595 tests, 0 failures** — `:app` 541, `:plugin-api` 7, `:plugin-sdk` 47; new `CanvasInvariantsTest` & `NotebookInvariantsTest`), `14.03 MB` `arm64-v8a` R8 APK (`CN=PenLab, O=penlab-app`), and live Tab S9 QA PASS across all `BUG-17` invariants + `TEST-PLAN-2026-11-27.md` Parts **A (`A1–A10`)**, **C (`C1–C2`)**, **D (`D1–D5`)**, and **E (`E1–E7`)** (`0` `logcat` exceptions).

### Stage 6 (User Requests, Tap-to-Select & Rubber-Band Snap Line — 28 Nov 2026, QA-Verified on Tab S9)
- **Implemented Items `6.1`–`6.6` (`InkCanvasHelpers.kt`, `InkCanvasView.kt`, `ViewSettings.kt`, `ToolPanels.kt`, `MainActivity.kt`):**
  - `6.1` **Line-extension axis snapping** (`snapPointToLineRay`, `±6°` / `14.dp` forward ray projection in `moveLineEnd`).
  - `6.2` **Velocity-scaled eraser** (`eraserVelocityScaling` persisted toggle `"Speed-scaled size"`, `computeVelocityScaledFactor` `1.0x`–`2.2x` EMA speed multiplier driving cursor & erase hit-test radius).
  - `6.3` **Plugin diagram placement scale** (`computePluginPlacementTransform` proportional to active selection bbox / `<= 65%` viewport).
  - `6.4` **Canvas panning while plugins run** (audited overlay guards + fixed `handleSelectionFinger` `panZoomDetector.adopt` multi-touch transition).
  - `6.5` **Tap-to-select non-stroke objects** (reverse z-order `hitTestTopItem` for `ImageItem`/`TextItem`/`PluginPageItem` in Select mode and finger-while-writing mode, keeping pen tool active).
  - `6.6` **Live 360° rubber-band hold-to-snap straight line** (`liveRubberLine` preview on `RecognizedShape.Kind.LINE` keeping start anchor fixed until `ACTION_UP`).
- **Verified:** Multi-module `./gradlew testDebugUnitTest` (**62 suites, 603 tests, 0 failures** — `:app` 549, `:plugin-api` 7, `:plugin-sdk` 47) and live Tab S9 QA PASS across all 6 features (`0` `logcat` exceptions, `4.86%` jank, `p50=7ms`).

---

## 4. Critical Architecture & Performance Invariants (DO NOT BREAK)

1. **Zero Compose Recomposition per Ink Sample:** Never read pen/stroke coordinates or mutate Compose state during `ACTION_MOVE` / ink authoring. `InkCanvasView` renders live ink on `InProgressStrokesView` (Jetpack Ink front-buffer). Compose reacts only to discrete callbacks (`onHistoryChanged`, whole-number `onViewChanged`, `onSelectionChanged`, `onCurrentPageChanged`, `onClipboardChanged`).
2. **Imperative Tool Dispatch:** Send tool/eraser changes directly to `InkCanvasView` on user tap (`sendToolsToCanvas()`).
3. **Palm Rejection:** Every floating bar, pill, panel, and overlay button over the canvas MUST apply `Modifier.ignorePalm { canvas.isPenWriting }`. Keep `MainActivity.dispatchTouchEvent` / `dispatchGenericMotionEvent` forwarding to `viewSettings.noteMotionEvent(ev)`.
4. **Tap-Swallow Contract:** Dismissing any open popover/menu/wheel by tapping outside MUST stamp `canvas.closedSomethingAt = event.uptimeMillis` (`pressOnlyClosed` within `2ms`) so the tap never drops a stray ink dot. All floating pills/popovers must register accurate hit rects (`topBarBounds`, `dock.isOnDock`, `pasteMenuBounds`).
5. **Mutual-Exclusion Overlays:** Opening any top-bar popover, menu, dock panel, or Settings sheet MUST dismiss all other open overlays (`closeOverlays(topBarToo = true)`).
6. **Side Insets:** When the toolbar is docked as a `LEFT` or `RIGHT` vertical rail, keep reporting covered width via `canvas.setSideInsets(left, right)` (`0, 0` when `TOP`/`BOTTOM`/corner).
7. **Animation Phase Isolation:** Read animated values in `graphicsLayer { ... }` or `offset { ... }` / layout placement blocks so animation frames skip composition. Route all transitions through `Motion.spring` / `Motion.tween` so `MotionSpeed.OFF` snaps instantly.
8. **Token & Craft Discipline:** Zero hardcoded hex colors in UI chrome (ink/paper previews come from `InkColors`); single depth cue per surface (`floatingChromeSurface` or `flatCardSurface`, never hairline + heavy shadow "ghost card"); `>= 48.dp` touch targets everywhere; `PenLabTextStyles.tabular*` (`tnum`) for dynamic numbers; no nested cards, no kickers/eyebrows above headings, no emoji icons.

---

## 5. Build, Environment & Wireless ADB Setup (WSL2 Debian on Windows)

### Toolchain Paths (already installed on this machine)
- **JDK 17:** `/home/omar/.local/jdks/jdk-17` (`Eclipse Temurin 17`)
- **Android SDK:** `/home/omar/Android/Sdk` (`platforms;android-36`, `build-tools;36.0.0`, `platform-tools`, configured in `local.properties` and `~/.bashrc`)
- **Gradle build & unit test command:**
  ```bash
  export JAVA_HOME="$HOME/.local/jdks/jdk-17" ANDROID_HOME="$HOME/Android/Sdk"
  cd /home/omar/penlab/penlab && ./gradlew testDebugUnitTest assembleDebug --max-workers=2 --no-daemon
  ```
- **Debug APK output:** `/home/omar/penlab/penlab/app/build/outputs/apk/debug/app-debug.apk`
- **Windows UNC path for ADB install:** `'\\wsl.localhost\Debian\home\omar\penlab\penlab\app\build\outputs\apk\debug\app-debug.apk'`

### Wireless ADB (from WSL2 via Windows `adb.exe`)
- Always invoke `/mnt/c/platform-tools/adb.exe` directly with `</dev/null` and `-s <serial>` (never omit `-s`, as mDNS + explicit IP can show two entries for the same tablet).
- If Wi-Fi changed or port rotated:
  ```bash
  /mnt/c/platform-tools/adb.exe mdns services </dev/null
  /mnt/c/platform-tools/adb.exe connect <new-ip>:<new-connect-port> </dev/null
  /mnt/c/platform-tools/adb.exe devices -l </dev/null
  ```
- Install APK without wiping user notebooks:
  ```bash
  /mnt/c/platform-tools/adb.exe -s <serial> install -r -d '\\wsl.localhost\Debian\home\omar\penlab\penlab\app\build\outputs\apk\debug\app-debug.apk' </dev/null
  ```
- Crash check after QA:
  ```bash
  /mnt/c/platform-tools/adb.exe -s <serial> logcat -d -s AndroidRuntime:E </dev/null
  ```

### Installed Agent Skills (local in `.agents/skills/`, excluded via `.git/info/exclude`)
If setting up on a fresh machine, reinstall with:
```bash
for pkg in \
  "pbakaus/impeccable@impeccable" \
  "wshobson/agents@mobile-android-design" \
  "hamen/material-3-skill@material-3" \
  "nextlevelbuilder/ui-ux-pro-max-skill@design-system" \
  "chrisbanes/skills@compose-animations" \
  "chrisbanes/skills@compose-performance" \
  "chrisbanes/skills@compose-ui-testing-patterns" \
  "thebushidocollective/han@android-jetpack-compose" \
  "android/skills@android-profiler"
do
  npx -y skills add "$pkg" -a antigravity -y --copy
done
```

---

## 6. Remaining Work — Detailed Specs for Stages 6 & 7

### Stage 6: Library Screen Redesign & First Launch Flow
**Primary files:** `ui/LibraryScreen.kt`, `ui/Covers.kt`, `ui/SearchUi.kt`, `MainActivity.kt`, new `ui/FirstLaunchSheet.kt`, plus JVM unit tests.

1. **Library Top Header (1280×800 landscape tablet, Light & Dark):**
   - Clean, spacious header bar with generous horizontal padding (`24.dp`) and calm hierarchy:
     - Left: Upgraded `PenLabSearchField` pill (`Search handwriting`, Phosphor Duotone `SearchIcon`, clear button, live indexing progress indicator).
     - Right: Compact Phosphor Duotone icon/tonal actions — `Import` dropdown/menu (combining `Import PDF files` + `Import folder` cleanly instead of two wide text buttons crowding the bar!), `New folder` (`PenLabIcons.FolderPlus`), `Settings` (`PenLabIcons.Settings`), `Bug / idea` (`PenLabIcons.Idea`, or tucked cleanly into Settings/overflow so the header stays calm and uncluttered), and primary pill CTA `New notebook` (`PenLabIcons.FilePlus`).
2. **Breadcrumbs, Continue Strip & Course Shelves:**
   - Crisp breadcrumb trail (`Notebooks › Course › Subfolder`) with Phosphor chevrons (`PenLabIcons.ChevronRight`), clear active folder title in Bricolage Grotesque, and quick folder color indicator.
   - `"Continue"` horizontal recent strip at root (up to 4 recently opened notebooks with crisp cover previews & relative date).
   - Refined `CourseShelf` rows with folder color dot, notebook count, and `"Open"` action.
3. **Refined `NotebookCard` & `FolderCard` (zero nested-card clutter, single depth cue):**
   - `NotebookCard`: crisp aspect-ratio cover thumbnail (`flatCardSurface` with `1.dp` `outlineVariant` hairline, `14.dp` radius), subtle tonal `PDF` badge on PDF notebooks, Bricolage/medium title, tabular page count + date subtitle (`4 pages · 24 Nov 2026`), and a **visible `⋮` overflow button** (`PenLabIcons.MoreVert`, `48.dp` target) opening the action menu (`Rename`, `Move to folder`, `Export PDF`, `Share PDF`, `Turn portrait/landscape`, destructive `Delete` with Undo snackbar) — while keeping long-press working too!
   - `FolderCard`: clean folder silhouette/badge with folder palette tint, Phosphor Duotone `FolderIcon`, notebook count, and visible `⋮` menu (`Rename`, `Colour`, `Move`, `Delete` when empty).
4. **Upgraded `NewNotebookDialog`:**
   - Built on `PenLabDialog` (`widthIn(max = 560.dp)`): notebook title field, clean `PenLabSegmentedControl` for format (`A4 Portrait` / `A4 Landscape` / `Endless`), and a crisp visual mini-preview grid of all 9 paper styles (rendered with active Light/Dark paper tint!) — preserving per-folder defaults in `folder_paper` prefs.
5. **Polished `EmptyLibraryState` & `EmptyFolderState`:**
   - Uses `PenLabEmptyState` with Phosphor Duotone illustration icon, warm helpful copy tailored to notes/PDFs (e.g. `"Start your first notebook or import a PDF exam sheet"`), and 1-tap `New notebook` + `Import PDF` actions.
6. **Lightweight, Skippable First Launch Flow (`FirstLaunchSheet.kt`):**
   - Triggered ONLY on genuine first run (persisted flag `first_launch_done` in `SharedPreferences("look")`; ALSO auto-skipped if the user already has existing notebooks unless launched from a `"Quick setup"` / `"Welcome guide"` option in Settings so QA and returning users can test it cleanly!).
   - Clean centered tablet sheet (`max 560.dp` wide):
     - Welcoming Bricolage heading (`"Welcome to PenLab"`) + 1-line subtitle (`"Pick your look and paper — you can change this anytime in Settings."`).
     - Live interactive selectors: **App theme** (`Light` / `Dark`), **Accent look** (`Blueprint` / `Copper`), **Paper** (`Light paper` / `Dark paper`) — recoloring the app live as they tap!
     - Primary CTA `"Start writing"` (closes & creates/opens a notebook or enters Library) + secondary `"Import a PDF"` + subtle `"Skip"` in top corner.
7. **Stage 6 Tablet QA Checklist:**
   - Test Library in BOTH **Dark** and **Light** themes (and Blueprint + Copper), verify search field & handwriting search results, Import menu, New Folder, New Notebook visual paper dialog, visible `⋮` card menus (Rename/Move/Color/Delete+Undo — clean up any test folder/notebook afterward!), empty folder state, and First Launch sheet (triggerable via Settings or test flag), 0 logcat crashes.

---

### Stage 7: Final Visual Audit, Impeccable Polish & Regression Pass
1. Run `./gradlew testDebugUnitTest lintDebug assembleDebug --max-workers=2 --no-daemon` and fix any new lint warnings or test failures.
2. Full end-to-end Tablet QA walkthrough on the Galaxy Tab S9 across all 4 theme combinations (`Dark Blueprint`, `Light Blueprint`, `Dark Copper`, `Light Copper`) covering:
   - Library (populated + empty folder + search)
   - First Launch sheet
   - Writing Canvas 3-island chrome (including 4 pens, drag-to-dock & back to Top, Toolbar Customizer)
   - Pen Settings, Eraser, Color/Marker Wheel, Tools (Ruler/Protractor), More, Page Config (Pages/Paper/Layers)
   - Lasso Selection Bar (Sketch 2) + More Plugins + Floating Paste pill
   - Export PDF Dialog, Fullscreen, Present Mode (peek + exit)
   - Settings Sheet
3. Verify against `impeccable/reference/craft-floor.md`: contrast ≥4.5:1, single depth cue per surface, ≥48dp touch targets, tabular numerals, zero clipped labels, zero overlap, 0 crashes in logcat, and `graphite` untouched.
4. Commit Stage 7 cleanly on `ui/redesign` and push.

---

## 7. Key File Map

| Area | Files |
|---|---|
| **Design Tokens & Theme** | `app/src/main/kotlin/dev/penlab/app/ui/theme/{Color.kt, Theme.kt, Type.kt, Shape.kt, Spacing.kt}` |
| **Icons (Phosphor Duotone)** | `app/src/main/kotlin/dev/penlab/app/ui/Icons.kt` |
| **Component Library** | `app/src/main/kotlin/dev/penlab/app/ui/components/{ChromeComponents.kt, MenuAndDialogComponents.kt}` |
| **Canvas Chrome & Toolbar** | `app/src/main/kotlin/dev/penlab/app/ui/{CanvasTopChrome.kt, ToolDock.kt, ToolbarConfig.kt, ToolSettings.kt, ToolPanels.kt}` |
| **Popovers & Pickers** | `app/src/main/kotlin/dev/penlab/app/ui/{ColorWheel.kt, MarkerWheel.kt, PagesPanel.kt, ViewReadout.kt, ViewSettings.kt, SearchUi.kt}` |
| **Dialogs, Settings & Modes** | `app/src/main/kotlin/dev/penlab/app/ui/{SettingsSheet.kt, PresentMode.kt, ExportLookDialog.kt, PdfCropDialog.kt, PdfCropGeometry.kt, RequestDialog.kt, Motion.kt}` |
| **Library** | `app/src/main/kotlin/dev/penlab/app/ui/{LibraryScreen.kt, LibraryCards.kt, Covers.kt}`, `app/src/main/kotlin/dev/penlab/app/notes/{CoverMaker.kt, LibraryMigration.kt}` |
| **Activity & Ink Surface** | `app/src/main/kotlin/dev/penlab/app/MainActivity.kt`, `app/src/main/kotlin/dev/penlab/app/ink/{InkCanvasView.kt, InkTool.kt, InkColors.kt, PageBackground.kt, PageItems.kt, PenBrushes.kt, PressureCurve.kt, StrokeChunkCache.kt}` |
| **Projector View** | `app/src/main/kotlin/dev/penlab/app/projector/{ProjectorLogic.kt, ProjectorSurface.kt}`, `app/src/test/kotlin/dev/penlab/app/projector/ProjectorLogicTest.kt` |
| **External Plugin Bridge** | `app/src/main/kotlin/dev/penlab/app/plugins/external/{ExternalManifest.kt, ExternalPlugin.kt, ExternalPluginIcons.kt, ExternalPluginManager.kt, PluginTransport.kt}`, `app/src/test/kotlin/dev/penlab/app/plugins/external/{ExternalManifestTest.kt, ExternalPluginTest.kt, ExternalPluginIconsTest.kt}` |
| **UI Unit Tests** | `app/src/test/kotlin/dev/penlab/app/ui/{ThemeTokensTest.kt, ViewSettingsLogicTest.kt, ToolbarConfigTest.kt, ToolPanelHelpersTest.kt, SelectionAndPresentTest.kt, ScreenChromeTest.kt, FingerDrawDefaultTest.kt, CoversTest.kt, PdfCropGeometryTest.kt}`, `app/src/test/kotlin/dev/penlab/app/ink/PressureCurveTest.kt` |

## Notes from the 25–26 Nov review + plugin work (`AI context/notes/`)
- `REVIEW-2026-11-26.md` — what landed overnight (merge, Stage 6, wheel fix, Stage 7 craft, review fixes, real bugs found).
- `master-findings.md` — every review finding with ID and how it was fixed; pen-speed measurements.
- `ui-ux-rubric.md` — 192-item UI/UX checklist used for the element-by-element review.
- `decisions.md` — decisions taken autonomously (reversible).
- `plugin-bridge-contract.md`, `bridge-proposal-items.md`, `plugin-media-contract.md` — plugin API v2 design (see also docs/plugin-bridge.md).
