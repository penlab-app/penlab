# PenLab UI Redesign — AI Agent Handoff Context

> **Branch:** `ui/redesign` (forked from `main` @ `c0c15ae`)  
> **Status:** **Stages 1–5 COMPLETE, QA-VERIFIED ON TABLET, AND COMMITTED.**  
> **Next Stage:** **Stage 6 — Library Screen Redesign & First Launch Flow** (then **Stage 7 — Final Visual Audit, Impeccable Polish & Regression Pass**).

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
| **Dialogs, Settings & Modes** | `app/src/main/kotlin/dev/penlab/app/ui/{SettingsSheet.kt, PresentMode.kt, ExportLookDialog.kt, RequestDialog.kt, Motion.kt}` |
| **Library (Stage 6 target)** | `app/src/main/kotlin/dev/penlab/app/ui/{LibraryScreen.kt, Covers.kt}` |
| **Activity & Ink Surface** | `app/src/main/kotlin/dev/penlab/app/MainActivity.kt`, `app/src/main/kotlin/dev/penlab/app/ink/{InkCanvasView.kt, InkTool.kt, InkColors.kt, PageBackground.kt, StrokeChunkCache.kt}` |
| **UI Unit Tests** | `app/src/test/kotlin/dev/penlab/app/ui/{ThemeTokensTest.kt, ViewSettingsLogicTest.kt, ToolbarConfigTest.kt, ToolPanelHelpersTest.kt, SelectionAndPresentTest.kt, ScreenChromeTest.kt, FingerDrawDefaultTest.kt}` |
