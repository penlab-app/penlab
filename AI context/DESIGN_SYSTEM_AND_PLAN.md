# PenLab Design System & 7-Stage UI Plan

> **Target Device:** 11-inch Android Tablet, Landscape (`2560 × 1600 px` @ `340 dpi` ≈ `1205 × 753 dp`, Samsung Galaxy Tab S9 / Android 16 API 36)  
> **Primary User:** University mathematics TA (Calculus 1 & 2, Probability, Differential Equations) writing live worked solutions with a stylus mirrored to a classroom projector, plus home note prep and PDF exam annotation.  
> **Design Mode:** **Operate** — precision, speed, zero-friction legibility, earned familiarity. The page and ink are the product; chrome recedes cleanly into floating islands.

---

## 1. UX Reasoning & Architectural Decisions

1. **Three-Island Floating Top Chrome (Sketch 1):**  
   Replacing the heavy full-width solid header band with three compact, floating pills (`File` | `Customizable Tool Bar` | `Page & View`) reclaims vertical writing space and lets the paper extend edge-to-edge under the status bar. On a 1205×753dp landscape tablet, every primary action sits within a single reach without obscuring the central writing zone.
2. **4 Quick-Switch Customizable Pens + Modular Center Bar:**  
   During a live Calculus tutorial, switching between Black (main derivation), Blue (annotations/theorems), Red (corrections/boundaries), and Green (key results) happens dozens of times per lecture. Putting **4 customizable pen slots** directly in the center bar eliminates menu-diving, while `||` drag handles on both ends preserve PenLab's signature edge-docking (horizontal top/bottom bar, vertical left/right rail, or corner wheel) and full customization in Settings (`1..6` pens, toggle/reorder tools).
3. **Second-Tap Progressive Disclosure:**  
   Single tap activates a tool instantly (0 latency, 0 modal). Second tap on an already-selected pen, eraser, tools, or more slot opens a compact anchored popover right beside that slot. Pen-down on the canvas immediately dismisses any open popover without dropping an ink dot (`closedSomethingAt` swallow contract).
4. **Unified Contextual Selection Bar (Sketch 2):**  
   When the user circles an equation or diagram with Lasso, a crisp horizontal pill floats beside the selection box (`Copy`, `Cut`, `Paste`, `Dupl.`, `Delete`, `Mirror H`, `Mirror V`, `More ▾` → Fill + Deselect + Plugins like LaTeX/Diagrams). Zero permanent screen real estate consumed when not selecting.
5. **True Light & Dark UI + Independent Paper Mode:**  
   Projecting in a bright lecture hall often needs **Light UI + Light Paper**; prepping notes at night benefits from **Dark UI + Dark Paper** (display-only lightness-inverted ink so PDF exports remain crisp ink-on-white). Both Blueprint (cool drafting slate-blue) and Copper (warm architectural clay) accent looks support full Light and Dark Material 3 token ramps.
6. **Zero-Cost Ink Invariants Preserved:**  
   Every toolbar/panel keeps `Modifier.ignorePalm { canvas.isPenWriting }`, imperative tool dispatch on tap, deferred layout/draw-phase animations (`graphicsLayer` / `offset`), and zero per-stroke Compose recompositions.
7. **Phosphor Duotone Iconography:**  
   `com.adamglin:phosphor-icon:1.0.0` (`PhosphorIcons.Duotone.*`) gives a cohesive, tactile, non-generic visual language across every button, menu, panel, and empty state (`PhosphorIcons.Bold.Check` used inside small circular swatches to avoid a faint square box).

---

## 2. Three-Layer Design Token Architecture (Light & Dark)

Implemented in `app/src/main/kotlin/dev/penlab/app/ui/theme/Color.kt`, `Theme.kt`, `Spacing.kt`, `Shape.kt`, `Type.kt`.

### Layer 1 — Primitives (`PenLabPrimitives`)

| Family | Token | Hex | Usage |
|---|---|---|---|
| **Slate Neutral (Blueprint)** | `Slate0` / `Slate50` / `Slate100` / `Slate200` | `#FFFFFF` / `#F6F8FB` / `#ECEFF5` / `#DCE2EC` | Light surfaces & borders |
| | `Slate400` / `Slate500` / `Slate600` | `#8E99A8` / `#5F6B7C` / `#3E4856` | Muted text, icons, hairlines |
| | `Slate800` / `Slate850` / `Slate900` / `Slate950` | `#232830` / `#1C2027` / `#14171C` / `#0D1014` | Dark surfaces & backdrops |
| **Warm Stone Neutral (Copper)** | `Stone0` / `Stone50` / `Stone100` / `Stone200` | `#FFFFFF` / `#FAF7F4` / `#F1ECE6` / `#E2D9D0` | Light warm surfaces & borders |
| | `Stone400` / `Stone500` / `Stone600` | `#9B9189` / `#6F655E` / `#4E4640` | Muted warm text & dividers |
| | `Stone800` / `Stone850` / `Stone900` / `Stone950` | `#2C2825` / `#23201D` / `#191614` / `#12100E` | Dark warm surfaces & backdrops |
| **Blueprint Accent** | `BlueLight` (`500`/`600`/`100`) | `#2563EB` / `#1D4ED8` / `#DBEAFE` | Light primary & container |
| | `BlueDark` (`400`/`900`) | `#5AA2FF` / `#1A3252` | Dark primary & container |
| **Copper Accent** | `CopperLight` (`600`/`700`/`100`) | `#AD4D1B` / `#9E481B` / `#FCE8DC` | Light primary (5.45:1 on white) & container |
| | `CopperDark` (`400`/`900`) | `#E59158` / `#432617` | Dark primary & container |
| **Semantic Status** | `RedLight` / `RedDark` | `#DC2626` / `#F87171` | Destructive actions, errors |
| | `GreenLight` / `GreenDark` | `#15803D` / `#4ADE80` | Active / success confirmation |
| | `AmberLight` / `AmberDark` | `#D97706` / `#FFC34D` | Search match highlights, bookmarks |

### Layer 2 — Semantic Roles (`MaterialTheme.colorScheme` + `LocalPenLabColors`)

| Semantic Role | Light (Blueprint / Copper) | Dark (Blueprint / Copper) | Contrast Target |
|---|---|---|---|
| `background` | `#F6F8FB` / `#FAF7F4` | `#14171C` / `#191614` | — |
| `surface` | `#FFFFFF` / `#FFFFFF` | `#1C2027` / `#23201D` | — |
| `surfaceContainer` (floating chrome) | `#FFFFFF` / `#FFFFFF` | `#232830` / `#2C2825` | Distinct from paper |
| `surfaceContainerHigh` (popovers) | `#F6F8FB` / `#F8F4F0` | `#2B313B` / `#35302C` | Elevated layer |
| `onSurface` | `#14171C` / `#191614` | `#F2F5FA` / `#F7F2ED` | ≥ 12:1 |
| `onSurfaceVariant` | `#525D6D` / `#625851` | `#9CA5B3` / `#AFA59D` | ≥ 5.0:1 |
| `outline` | `#9AA5B5` / `#ABA096` | `#535E6F` / `#645B54` | ≥ 3:1 UI |
| `outlineVariant` | `#DCE2EC` / `#E2D9D0` | `#353D49` / `#403934` | Hairline |
| `primary` / `onPrimary` | `#2563EB`/`#FFF` · `#AD4D1B`/`#FFF` | `#5AA2FF`/`#0B1626` · `#E59158`/`#24130A` | ≥ 5.1:1 |
| `primaryContainer` / `onPrimaryContainer` | `#DBEAFE`/`#1E3A8A` · `#FCE8DC`/`#6B2D0F` | `#1E3554`/`#D6E8FF` · `#4A2B1A`/`#FCE8DC` | Selected states |
| `error` / `errorContainer` | `#DC2626` / `#FEE2E2` | `#F87171` / `#451A1A` | Destructive |

### Layer 3 — Component Aliases (`PenLabComponentTokens`)
- `chromePillBg` → `surfaceContainer`
- `chromePillBorder` → `outlineVariant`
- `toolCellSelectedBg` → `primaryContainer`
- `toolCellSelectedRing` → `primary` (2dp ring)
- `panelBg` → `surfaceContainerHigh`
- `chipIdleBg` → `chipIdle`
- `cardBg` → `surface`, `cardBorder` → `outlineVariant`

---

## 3. Typography, Spacing, Radii, Elevation & Motion

- **Typography (`Type.kt`):** `Bricolage Grotesque` strictly on Display/Headline/Title (`-0.02em` to `-0.03em` tracking); system sans on Body/Label; `PenLabTextStyles.tabularLabel/Medium/Small/Body` (`fontFeatureSettings = "tnum"`) for dynamic numbers.
- **Spacing (`Spacing.kt`):** `xs = 4.dp`, `sm = 8.dp`, `md = 12.dp`, `lg = 16.dp`, `xl = 24.dp`, `xxl = 32.dp`, `touchTarget = 48.dp`, `edgeMargin = 12.dp`.
- **Shapes (`Shape.kt`):** `extraSmall = 6.dp`, `small = 10.dp`, `medium = 14.dp`, `large = 18.dp`, `extraLarge = 24.dp`, `pill = CircleShape`.
- **Elevation (Single Depth Cue Rule):** `flatCardSurface` (1dp hairline, 0 shadow) for cards; `floatingChromeSurface` (soft drop shadow + subtle hairline only where white meets white paper) for floating canvas islands/popovers.
- **Motion (`Motion.kt`):** 120ms tool switch, 0.94f stiff-spring press scale, 140ms/90ms popover enter/exit, 0ms undo/redo/ink. All animations honor `Motion.speed` (`OFF/SLOW/NORMAL/FAST`) and read values in `graphicsLayer`/layout phase.

---

## 4. 7-Stage Roadmap & Live Progress

| Stage | Scope | Status | Commit |
|---|---|---|---|
| **Stage 1** | Design System Foundation (3-layer Light/Dark tokens) & Phosphor Duotone Icons | **DONE (QA PASS)** | `6077165` |
| **Stage 2** | Reusable Component Library & Unified Settings Screen (Light/Dark UI + Live Paper) | **DONE (Re-QA PASS)** | `b838acb` |
| **Stage 3** | Canvas 3-Island Floating Top Chrome, 4 Customizable Pens & Customizable Center Toolbar (Sketch 1) | **DONE (QA PASS)** | `1952b30` |
| **Stage 4** | Pen Settings, Paper Picker, Color/Marker Wheel & Tool Popovers Polish | **DONE (Re-QA PASS)** | `80bc38c` |
| **Stage 5** | Lasso Selection Bar (Sketch 2), PDF Annotation Badge & Export Dialog, Fullscreen & Present Mode | **DONE (QA PASS)** | `b4b4649` |
| **Stage 6** | Library Screen Redesign (1280×800 tablet, Light/Dark, visible card menus, visual New Notebook dialog, Empty states) & First Launch Flow | **NEXT UP** | — |
| **Stage 7** | Final Visual Audit, Impeccable Polish, Android Lint & Full Regression QA Pass | **PENDING** | — |
