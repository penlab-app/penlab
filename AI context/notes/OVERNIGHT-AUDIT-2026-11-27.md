# PenLab Overnight UI/UX, Layout & Core Hardening Audit — 27 Nov 2026

> **Branch:** `ui/redesign`  
> **Scope:** Consolidated live-device (`SM-X716B` Galaxy Tab S9, Android 16, `2560 × 1600` @ `340 dpi`) 46-screenshot audit (`Critic A`, `Critic B`, `Critic C`, `penlab_qa`) plus static code/lifecycle audit across all PenLab Kotlin modules.  
> **External System Overlay Note:** Samsung One UI's floating S-Pen Air Command icon visible along the far-right system overlay edge in screenshots is external to PenLab and explicitly excluded from defect tracking.

---

## Stage Overview & Execution Roadmap

| Stage | Scope | Status | Primary Files |
|---|---|---|---|
| **Stage 0** | Fix `Left`/`Right` docked pill top-chrome shift, quiet-hours status-page mute, package `tools/qa/` stress & capture suite | **DONE & QA-VERIFIED** | `PresentMode.kt`, `MainActivity.kt`, `ScreenChromeTest.kt`, `tools/qa/*` |
| **Stage 2** | Canvas Popovers, Color Wheel, Ruler/Protractor, Eyedropper & Top Chrome Collisions (`BUG-01`..`BUG-06`) | **FIXED & Tab S9 Visually Verified** | `MarkerWheel.kt`, `ColorWheel.kt`, `Instruments.kt`, `InkCanvasView.kt`, `ToolDock.kt`, `ToolPanels.kt`, `MenuAndDialogComponents.kt`, `PresentMode.kt`, `MainActivity.kt` |
| **Stage 3** | Toolbar Pill Symmetry, Expand Icon, Selection Bar, Add from PDF & Text Box (`BUG-07`..`BUG-10`) | **FIXED & Tab S9 Visually Verified** | `ToolDock.kt`, `ToolPanels.kt`, `Icons.kt`, `InkCanvasView.kt`, `PdfCropDialog.kt`, `PdfCropGeometry.kt`, `RequestDialog.kt`, `LibraryScreen.kt`, `Instruments.kt`, `MainActivity.kt` |
| **Stage 4** | Settings, Library, Search, Dialogs & Light-Theme Contrast (`BUG-11`..`BUG-13`) | Planned | `SettingsSheet.kt`, `ChromeComponents.kt`, `LibraryScreen.kt`, `LibraryCards.kt`, `SearchUi.kt`, `Covers.kt`, `ToolPanels.kt`, `MainActivity.kt` |
| **Stage 5** | Core Lifecycle, Data-Loss, Undo Hardening & Final Lint (`BUG-14`..`BUG-17`) | Planned | `MainActivity.kt`, `InkCanvasView.kt`, `Covers.kt`, `PageStore.kt`, `FolderRepository.kt`, `FirstLaunchSheet.kt`, `Theme.kt` |
| **Stage 6** | Highest-value bonus feature on `feat/...` off `ui/redesign` | Planned (Post Stages 2–5) | Dedicated feature branch |

---

## Stage 0 — Already Fixed & QA-Verified

| ID | Severity | Finding & Verification | Files |
|---|---|---|---|
| **BUG-00** | `P1` | `LEFT`/`RIGHT` docked toolbar pills shifted top-row `FileChromePill`, `PageConfigChromePill`, and `Leave full screen` inward via `railLeft`/`railRight`, even though Stage 3 side pills float centered below `TOP_BUTTONS_HEIGHT`. Fixed to `topChromeHorizontalPaddingDp(PenLabSpacing.md.value, dock.side).dp` while keeping `railLeft`/`railRight` for selection clearance, `RecordingPill`, and Present pill. Verified `0 px` top-chrome shift on Tab S9 + `ScreenChromeTest` (`10/10`) + reusable `tools/qa/` suite (`PenLabCap.java`, `penlab-cap.jar`, `tablet-cap.sh`, `tablet-install.sh`, `ui_stress_audit.py`). | `PresentMode.kt`, `MainActivity.kt`, `ScreenChromeTest.kt`, `tools/qa/*` |

---

## Stage 2 — Canvas Popovers, Color Wheel, Ruler/Protractor, Eyedropper & Top Chrome Collisions (**FIXED & Tab S9 Visually Verified**)

> **Status (`BUG-01`..`BUG-06`):** **FIXED & Tab S9 Visually Verified** (`56` suites, `511` `:app` unit tests passing, `arm64-v8a` R8 APK verified on Tab S9).

| ID | Sev | Finding (`FIXED & Tab S9 Visually Verified`) | Screenshot / Source Refs | Implemented Fix & Verification |
|---|---|---|---|---|
| **BUG-01** | `P0` | `ColorWheel` clipped ~40% off left screen edge when anchored near edge/corner, underlying toolbar bleeds through center hole, and bottom RGB/opacity pill stretches uncomfortably wide. | `30_color_wheel_overlay.jpg`, `ColorWheel.kt` | Clamp wheel center within `[radius + safeMargin, screenW - radius - safeMargin]`; draw opaque/scrim hub backing so underlying toolbar cannot show through; cap bottom RGB/alpha pill width cleanly (`widthIn(max = ...)`). |
| **BUG-02** | `P0` | Ruler & Protractor spawn on top of each other near the same screen region and underneath the floating toolbar pill. | `32b_ruler_protractor_canvas.jpg`, `Instruments.kt`, `InkCanvasView.kt` | Offset default Ruler vs Protractor spawn coordinates away from each other and outside docked/floating toolbar bounds (`safeCenter` minus active toolbar rect). |
| **BUG-03** | `P0` | Toolbar popovers (`Pen`, `Tools`, `More`) open directly over and cover the docked/floating toolbar pill; `MoreToolsPanel` and `InstrumentsPanel` are unscrollable and can clip on shorter viewports. | `29_*`, `32_*`, `33_*`, `ToolDock.kt` (`PanelBeside`), `ToolPanels.kt` | Place panels strictly clear of `dock.barBounds` (flip below/above/beside based on available viewport height/width rather than overlapping the bar); wrap `MoreToolsPanel` & `InstrumentsPanel` in `verticalScroll` with dynamic viewport `heightIn(max = ...)` cap. |
| **BUG-04** | `P0` | `FileOptionsPopover` (`⋮`) aligns left under `<` back chevron instead of right-aligning under `⋮`; `FileQuickMenu` & `FileOptionsPopover` occlude `RecordingPill` (`🎤`), which straddles top canvas border and collides with row-2 `TOP` bar. | `13_*`, `14_*`, `22_*`, `MainActivity.kt`, `CanvasTopChrome.kt` | Align `FileOptionsPopover` right edge to `⋮` button bounds (`Alignment.End` / measured offset); shift or coordinate `RecordingPill` vertical slot clear of open `FileQuickMenu`/`FileOptionsPopover` and row-2 `TOP` toolbar clearance (`TOP_BUTTONS_HEIGHT + BarHeight + spacing`). |
| **BUG-05** | `P0/P1` | Eraser popover/selection inconsistency when toggling eraser options; stale `ToolDock.cellCenters` / `gripCenters` after collapse or dock switch; open Corner `Wheel` remains open behind `ToolOptionsPanel`; light-theme `hubWellColor` low contrast (`lum > 0.42f`). | `31_*`, `ToolDock.kt`, `MainActivity.kt` | Sync Eraser tool index/state on selection & panel updates; prune stale `cellCenters` keys when `shownItems` changes; fold `dock.wheelOpen = false` whenever opening a panel; adjust `hubWellColor` luminance threshold (`> 0.42f`) for crisp Light-mode stroke preview contrast. |
| **BUG-06** | `P1/P2` | Bright-white Eyedropper banner (`30b`) & Present toast (`21`) clash harshly on Dark UI; missing `BackHandler` to exit Eyedropper mode; Present pill `railRight` shift/overlap (`21`); top-bar `y` offset jump (`26` vs `90`) when exiting Present/Fullscreen; `1px` bright blue page border drawn on canvas (`12`). | `12_*`, `21_*`, `30b_*`, `MainActivity.kt`, `InkCanvasView.kt` | Theme Eyedropper banner & Present toast via `PenLabComponentTokens` dark/light semantic tokens; add `BackHandler(enabled = eyedropperActive)` to exit cleanly; remove stray `railRight` shift when Present hides dock and stabilize status/inset top-bar transition (`y` offset); soften/remove unintentional `1px` bright blue canvas page stroke. |

---

## Stage 3 — Toolbar Pill Symmetry, Expand Icon, Selection Bar, Add from PDF & Text Box (**FIXED & Tab S9 Visually Verified**)

> **Status (`BUG-07`..`BUG-10`):** **FIXED & Tab S9 Visually Verified** (`56` suites, `516` `:app` unit tests passing, `arm64-v8a` R8 APK verified on Tab S9; plus `BUG-02` Protractor top-clearance polish & `tools/qa/tablet-cap.sh` fixed-string `grep -F`).

| ID | Sev | Finding (`FIXED & Tab S9 Visually Verified`) | Screenshot / Source Refs | Implemented Fix & Verification |
|---|---|---|---|---|
| **BUG-07** | `P1/P2` | Expanded `Left`/`Right` vertical `Rail` loses bottom `=` drag grip and stretches into bottom edge on shorter heights; `Right` pill edge inset asymmetry; `BOTTOM` bar sits too close to Android OS gesture bar (`26`); dim `Undo`/`Redo` & low floating pill rim contrast (`25`–`28`); `CollapseCell` uses `FullScreen` `[ ]` expand icon (`23`/`28`); collapsing hides an active non-pen `More` tool without indicator. | `22_*`–`28_*`, `ToolDock.kt`, `Icons.kt` | Pinned top & bottom `Grip` outside scrollable middle rail `Column`; unified `maxOf(insets.bottom, insets.gestureBottom)` + `18.dp`/`20.dp` bottom clearance & `10.dp` capsule elevation; added dedicated `PenLabIcons.ToolbarExpand`/`ToolbarCollapse` (`ArrowsOutSimple`/`ArrowsInSimple`); updated `collapsedToolbarItems` to keep active non-pen tool (or `DockItem.More` fallback) visible; `ToolbarPlacementTest` (13/13) verified. |
| **BUG-08** | `P0/P1` | `SelectionActions` overflows off narrow widths (missing `horizontalScroll`); dashed selection bbox extends off-screen; rotation handle crowds `Delete` action; low contrast on disabled `Paste` & `Delete`. | `34e_*`, `ToolPanels.kt`, `MainActivity.kt` | Wrapped `SelectionActions` in `.horizontalScroll(rememberScrollState())` inside `PenLabChromePill` with `maxWidth` measurement constraint; exported `SELECTION_BAR_GAP_DP = 56f` + `horizontalMarginPx` in `placeSelectionBar` so vertical gap clears the rotation knob without crowding horizontal clamping; strengthened disabled `Paste` (`0.45f` alpha) and dark-mode `Delete` (`#FF7B7B`) foregrounds; `SelectionAndPresentTest` (12/12) verified. |
| **BUG-09** | `P1` | `PdfCropDialog` (`34a`) default crop rect + opaque handles cover corner page text; split pagination footer wastes width & narrow screens overflow; corrupted PDF page render spins infinitely; placed white PDF snippet (`34b`, `37`) conceals white ink written on Dark Paper. | `34a_*`, `34b_*`, `37_*`, `PdfCropDialog.kt`, `PdfCropGeometry.kt`, `InkCanvasView.kt` | Inset `NormalizedCropRect.DEFAULT` (`0.08f, 0.22f, 0.92f, 0.62f`) with translucent handles (`0.36f`/`0.45f` + crisp ring); grouped `< Page X of Y >` pagination in a `FlowRow` footer; added `previewError` + `"Retry"` UI on preview failure; added `defaultDarkPaperCropAdapt(darkPage)` + `"Adapt to dark paper"` `PenLabChoiceChip` wired to `InkCanvasView.addImage(..., adaptToDarkPaper = ...)` (`isInk = adaptToDarkPaper`); `PdfCropGeometryTest` (7/7) verified. |
| **BUG-10** | `P1/P2` | `TextBoxDialog` (`34c`, `34d`) lacks `PenLabDialog` wrapper, `imePadding()`, placeholder, focus border, empty-CTA disable, and a11y label; `"Text box"` placement mode gives no on-canvas `"Tap to place text"` hint. | `34c_*`, `34d_*`, `RequestDialog.kt`, `LibraryScreen.kt`, `MainActivity.kt` | Migrated `TextBoxDialog` to `PenLabDialog` (`imePadding()`, `"Text box content"` label + semantics, `"Type text to place on page…"` placeholder, `canSubmitTextBox` empty-CTA disable); added timed `"Tap anywhere on the page to add a text box"` `ToolbarMessage` when selecting the Text box tool; `ToolPanelHelpersTest` (14/14) & `InstrumentsTest` (2/2, extra Protractor top clearance) verified. |

---

## Stage 4 — Settings, Library, Search, Dialogs & Light-Theme Contrast

| ID | Sev | Finding | Screenshot / Source Refs | Fix Plan |
|---|---|---|---|---|
| **BUG-11** | `P0/P1` | `SettingsSheet` (`08`, `09`, `36`): pitch-black thumb on ON `Switch` toggles across app; scrolled widgets bleed sharply under sticky header & clip against rounded bottom corners; tiny `▲`/`▼` customizer reorder buttons (`<48dp` targets) with bright arrows on disabled rows; 6-item `Dock position` segmented control wraps/clips on narrower widths. | `08_*`, `09_*`, `36_*`, `SettingsSheet.kt`, `ChromeComponents.kt` | Fix Material3 `Switch` `checkedThumbColor` / `checkedTrackColor` contrast across Light & Dark; wrap scrollable Settings body with header divider/top-bottom fade & bottom padding inside rounded sheet; upgrade `▲`/`▼` reorder buttons to `48.dp` targets with muted disabled tint; allow responsive 2x3 wrap or compact width handling for 6-option `Dock position` on narrow screens. |
| **BUG-12** | `P0/P1/P2` | `LibraryScreen` & `LibraryCards` (`01`, `02`, `06`): Search shows `"4 notebook titles"` count mismatch, 2px clipped result row, missing `imePadding()`, and `1.95:1` Light-theme `searchMatch` contrast; jagged bottom-aligned Continue thumbnails & cropped `Amany Test` ink cover; trailing orphan `·` in subtitle; naked `New folder`/`Settings` header buttons vs boxed resting `⋮`; hollow `▷` vs `›`; `Recent files` (`13`) folder dot misaligns doc icon. | `01_*`, `02_*`, `06_*`, `13_*`, `LibraryScreen.kt`, `LibraryCards.kt`, `SearchUi.kt`, `Covers.kt` | Reconcile search result count with rendered list, fix `imePadding()` + row height, lift Light `searchMatch` highlight contrast to `>= 5:1`; standardize top-aligned uniform aspect-ratio Continue cards and fix `CoverMaker` stroke bounds margin; trim empty metadata before joining `"·"`; align header icon button chrome (`PenLabIconButton`) & card `⋮` resting surface cleanly; replace hollow `▷` with `PenLabIcons.ChevronRight`; fix `Recent files` row leading icon alignment. |
| **BUG-13** | `P1/P2` | `NewNotebookDialog` (`04`) slices row-3 paper templates and shows indistinct `Grid`/`Engineering`/`Graph` mini-previews; `NewFolder`/`Rename` dialog allows empty confirm click (`05`); `WelcomeGuide` (`10`) puts pen icon on `Done`, left-aligns CTAs, omits `System` theme option; `Bug/Idea` (`11`) shows unreadable disabled `Save` & dev string `"Saved with: ..."`; `Pages` / `Paper` / `Layers` (`15`–`19`) render `+` chips identically to presets, dim page thumbnails/bookmarks, mismatch `+ New layer` outline vs `+ Add page` fill, and miss Search clear/next buttons. | `04_*`, `05_*`, `10_*`, `11_*`, `15_*`–`19_*`, `MainActivity.kt`, `ToolPanels.kt`, `FirstLaunchSheet.kt` | Fix `NewNotebookDialog` paper grid height/spacing + crisp grid line contrast; disable `Create`/`Rename` when trimmed text is blank; polish `WelcomeGuide` (`System/Light/Dark`, clean `Done` button, trailing CTA alignment); fix `Bug/Idea` disabled button contrast & user-facing save toast; differentiate `+` custom preset chip visually, brighten `Pages`/`Bookmarks` previews, unify `+ New layer` / `+ Add page` button hierarchy, and add Search clear/next controls. |

---

## Stage 5 — Core Lifecycle, Data-Loss, Undo Hardening & Final Lint

| ID | Sev | Finding | Source Refs | Fix Plan |
|---|---|---|---|---|
| **BUG-14** | `P0` | `openNotebook` resource leak & unsaved-stroke drop on concurrent open or `onNewIntent` (opening a second notebook before the first finishes flushing can drop strokes or leak `PdfBackground`/threads). | `MainActivity.kt` | Serialize `openNotebook` transitions cleanly (`Job` cancellation/join + flush pending ink save before switching/closing handles). |
| **BUG-15** | `P0` | Drawing strokes while async `PageStore.load` is still in flight gets wiped once `load` completes and replaces page state. | `MainActivity.kt`, `InkCanvasView.kt` | Gate canvas authoring or merge/queue live strokes safely until `PageStore.load` finishes applying loaded strokes. |
| **BUG-16** | `P0` | `Covers.forget()` mutates Compose `mutableStateMapOf` off the main thread (`Dispatchers.IO` / background executors), risking snapshot crash / race. | `Covers.kt`, `MainActivity.kt` | Dispatch `Covers.forget()` map updates onto Main thread (`withContext(Dispatchers.Main.immediate)` / `runOnUiThread`). |
| **BUG-17** | `P0/P2` | `turnOpenNotebook`/`turnNotebook` race on active notebook metadata; `canClearCurrentPage` ignores items-only pages & blank subpage cases; `Layers` undo drops `hidden` layer state; `pageStyle` desyncs after async `load`; `keepPagesInView` clamps rotated dimensions inaccurately; `MAX_ZOOM` under-scales high-DPI tablets; `createFromImport` misflags non-PDF imports; `Folder` rename ignores duplicate target conflict; `delete()` return values unchecked; `FirstLaunchSheet` lacks double-tap guard; `Theme.kt` side effects mutate window unconditionally. | `MainActivity.kt`, `InkCanvasView.kt`, `PageStore.kt`, `FolderRepository.kt`, `FirstLaunchSheet.kt`, `Theme.kt` | Fix each lifecycle/data invariant with unit tests covering undo `hidden` preservation, `canClearCurrentPage`, rotated clamping, folder rename collision, `turnNotebook` synchronization, and run final `:app:lintRelease` + multi-module `testDebugUnitTest` + Tab S9 end-to-end QA before **Stage 6** bonus branch. |

---

## Overnight Run Summary & Handoff (27 Nov 2026, 01:25–09:35 EET)

### Completed on `ui/redesign`
- **Stage 0 & 1 (`bdb51a8`):** Fixed `Left`/`Right` docked toolbar shifting top chrome pills (`BUG-00`), packaged reusable QA suite in [`tools/qa/`](../../tools/qa/README.md) (`PenLabCap.java`, `penlab-cap.jar`, `tablet-cap.sh`, `tablet-install.sh`, `ui_stress_audit.py`), and executed the 46-screenshot live Tab S9 stress test + static audit creating this defect log.
- **Stage 2 (`f0c6af3`):** Fixed `BUG-01`..`BUG-06` (centered `MarkerWheel`/`ColorWheel`, staggered `Protractor`/`Ruler`, bar-avoidant `PanelBeside` + scrollable panels, overflow-aligned `FileOptionsPopover` + inline `RecordingPill`, dark-themed Eyedropper/Present banners + `BackHandler`). Verified with `:app:testDebugUnitTest` (`56` suites, `511` tests, `0` failures) + Tab S9 visual QA.
- **Stage 3 (`5e16ef3`):** Fixed `BUG-07`..`BUG-10` + `BUG-02` Protractor polish (pinned top/bottom `Rail` grips, `ToolbarExpand`/`ToolbarCollapse` icons, active hidden-tool collapsed fallback, scrollable `SelectionActions` with `56f` rotate clearance, translucent `PdfCropDialog` handles + `FlowRow` pagination + `"Adapt to dark paper"`, `PenLabDialog` `TextBoxDialog` + placement toast). Verified with `:app:testDebugUnitTest` (`56` suites, `516` tests, `0` failures), `arm64-v8a` R8 APK (`CN=PenLab`), and Tab S9 visual QA.

### Why the Run Paused at `03:20 AM EET`
- At `03:20 AM EET` (`5.58%` 5-hour session quota remaining), immediately after Stage 3 passed Tab S9 visual QA and `AI context/` was updated, the turn hit the 5-hour session API cutoff right before committing Stage 3 and scheduling the automatic sleep timer for the reset (`~05:13 AM EET`).
- Upon resuming at `09:28 AM EET` (near the `10:00 AM EET` cutoff), Stage 3 was committed & pushed (`5e16ef3`), this handoff was recorded, and the session wrapped up cleanly without merging to `main`.

### Minor Stage 3 Residuals to Fold Into Stage 4 / 5
1. `TextBoxDialog` displays label `"Text box content"` while focused instead of placeholder `"Type text to place on page…"`; `"Tap anywhere on the page to add a text box"` requires a stylus tap when `drawWithFinger = false`.
2. Protractor top arc outer rim grazes the very top status bar edge (`y=0..30`, numbers legible below it).

### Remaining Stages (Next Agent)
1. **Stage 4:** Fix `BUG-11`..`BUG-13` (`SettingsSheet`, `LibraryScreen`/`LibraryCards`/`SearchUi`/`Covers`, `NewNotebookDialog`/`FirstLaunchSheet`/`ToolPanels`), verify unit tests + Tab S9 visual QA across Light & Dark themes, commit & push `ui/redesign`.
2. **Stage 5:** Fix `BUG-14`..`BUG-17` (core lifecycle, `PageStore.load` stroke safety, `Covers.forget()` main-thread dispatch, undo/zoom/import/folder invariants), run `:app:lintRelease` + multi-module `testDebugUnitTest` + `tools/qa/ui_stress_audit.py` on Tab S9, commit & push `ui/redesign`.
3. **Stage 6 (Bonus Feature Branch):** Branch `feat/library-trash-and-sort` off `ui/redesign` to implement Library Trash Manager (`.trash/` restore / permanent delete / empty) + Sort (`Recent` / `Name` / `Kind`). Do **not** merge to `main` prematurely.

### Resources Used (`01:25 EET` Baseline → `09:30 EET` Final Telemetry)

| Resource | Baseline (`01:25 EET`) | Final (`09:30 EET`) | Used Overnight | Notes / Cap |
|---|---|---|---|---|
| **Omar Network Total** | `1,746.89 MiB` (`1,831,745,403 B`) | `3,088.42 MiB` (`3,238,446,280 B`) | **`+1,341.53 MiB`** (`+1,406.70 MB`) | **`32.75%`** of `+4.0 GiB` (`4,096 MiB`) cap |
| ↳ `agy` (Agy AI Agent) | `1,391,985,431 B` | `2,593,942,592 B` | **`+1,149.02 MiB`** (`+1,204.84 MB`) | Largest network consumer (`85.65%` of delta) |
| ↳ `other_wsl` (Gradle/git/DNS/etc.) | `233,785,295 B` | `339,987,808 B` | `+101.61 MiB` (`+106.55 MB`) | `--no-daemon`, `--offline`/cached deps |
| ↳ `adb_out` (to Akram) | `141,460,914 B` | `184,674,960 B` | `+41.21 MiB` (`+43.21 MB`) | Compressed `-Ppenlab.abi=arm64-v8a` builds |
| ↳ `adb_in` (from Akram) | `13,789,241 B` | `45,474,062 B` | `+30.22 MiB` (`+31.69 MB`) | Scaled JPEG screenshots (`PenLabCap`) |
| ↳ `tailscale_overhead` | `22,217,273 B` | `37,580,318 B` | `+14.67 MiB` (`+15.38 MB`) | `DERP Relay (par)` overhead |
| ↳ `status_web` (ngrok) | `28,507,249 B` | `33,541,010 B` | `+4.80 MiB` (`+5.03 MB`) | Live status telemetry & thumbnails |
| **Akram Network Total** | `196.43 MiB` (`205,974,677 B`) | `287.33 MiB` (`301,291,806 B`) | **`+90.90 MiB`** (`+95.32 MB`) | Inbound APK + outbound QA screenshots/VPN |
| **Gemini 5-Hour Session Quota** | `69.65%` remaining | `99.73%` remaining *(post-reset; hit `5.58%` at `03:20 EET`)* | ~`64.07%` + reset | Reset while paused overnight |
| **Gemini Weekly Quota** | `51.92%` remaining | `34.34%` remaining | **`-17.58%`** | `34.34%` remaining for Stage 4–6 |
