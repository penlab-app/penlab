# User UI Sketches Specification

Transcribed verbatim from the two hand-drawn sketches provided by the user (drawn inside PenLab on the Galaxy Tab S9).

---

## Sketch 1 — Canvas Top Floating Chrome & Customizable Toolbar (Implemented in Stages 3–4)

Top floating chrome split into **3 floating islands** along the top of the canvas:

1. **Left Island (`FileChromePill`):**
   - Layout: `[ < | file name (folder if exists) | ⋮ ]`
   - Tapping the filename area opens a dialog/popover giving access to **recent files** and **current file controls** (renaming, moving to another folder, etc.).
   - Tapping `⋮` (`options`) opens:
     - `export`
     - `share`
     - `sync to GD` *(wired honestly to SAF PDF export where Drive is selectable, since OAuth Drive sync is not in backend)*
     - `settings`
     - `report bugs`

2. **Center Island (`DockedToolbar`):**
   - Layout: `[ || | Pen 1 | Pen 2 | Pen 3 | Pen 4 | Erase | Select | Fill | Tools | More (▾) | Undo | Redo | || ]`
   - `||` on left = **handle**; `||` on right = **another handle** (drag to move/dock).
   - **4 pens** ("customizable").
   - **Utilities:**
     - `erase` → `complete stroke` / `partial stroke`
     - `select` (lasso)
     - `fill`
     - `tools` → `ruler`, `protractor`
     - `more` → `add from pdf`, `add image` *(plus unpinned tools: Highlighter, Text box, Layers, Clear page)*
   - `Undo` / `Redo`.
   - Bottom note: **"the whole bar can be customizable from the settings"** *(implemented via `ToolbarConfig` + Settings → Toolbar)*.

3. **Right Island (`PageConfigChromePill`):**
   - Layout: `[ Search | + 100% - | Fullscreen | Page config ]`
   - `Search` (magnifying glass)
   - `Zoom` (`− 100% +` inline stepper; tap `%` for presets & locks)
   - `Fullscreen` (expand corners)
   - `Page config` (stacked pages icon → unified Pages / Paper / Layers popover)

---

## Sketch 2 — Lasso Select Dialog & Iconography (Implemented in Stage 5)

Floating **Select dialog** shown beside an active lasso selection:

- Layout: `[ copy | cut | paste | dupl. | delete | mirror-H | mirror-V | more ]`
  - Two icons between `delete` and `more`: **mirror icons** (horizontal & vertical flip).
  - `more` → `latex`, `diagrams`, `anything else` *(extensible `SELECTION_NOT_EMPTY` plugin actions + Fill + Deselect)*.
- Bottom note: **"do you know the phosphor icon pack?"** → Entire app standardized on **Phosphor Duotone** (`com.adamglin:phosphor-icon:1.0.0`).
