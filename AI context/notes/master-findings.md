# Master review findings (Opus, 26 Nov night) — fed to fixers after Stage 6 re-QA
## Touch targets < 48 dp (rubric T1)
- CanvasTopChrome: ChromePillIconButton widths 42 (Back, File ⋮ L112/184), 40 (L522/586), 36 (L532/576 — zoom − / +?) in a 48 dp tall pill → hit width < 48.
- SearchUi StepperIconButton 36 dp clickable (L159).
- PagesPanel bookmark toggle 44 dp (L351).
## Typography
- CanvasTopChrome PDF badge 9.5 sp / 11 sp line / 0.4 sp tracking literal (L217) — below 11 sp floor, not a token.
- ToolPanels line-type labels 10 sp literal (L295) — token.
## Token discipline
- ~600 literal dp values outside ui/theme (ToolPanels 168, ToolDock 99, ColorWheel 58, CanvasTopChrome 57). Spacing values should map to PenLabSpacing; component sizes to PenLabLayout.
## Fixed by Master
- CRASH (StackOverflowError) from the Stage-6 polish search-focus hack: clearFocus inside onFocusChanged looped with
  Android re-granting focus. Now focusProperties { canFocus = touched }. Verified: 3× open/close notebook, tap+type search.
## Visual notes (Master, library screenshot)
- Search results: summary "1 notebook title" + section header "Notebook titles" says the same thing twice.
- Long title breaks mid-word ("MoM-Assignmen / t2-…"): prefer break at '-' / allow hyphen break opportunities.
- Landscape covers are shorter than portrait in the same row → title/meta baselines differ across a row.
## From r3 re-QA (for Stage 7)
- Library card meta: first glyph of "4 pages · Yesterday" clipped at the left edge (test1 card only).
- Light/Copper: secondary header chips (Import, new folder) pale grey on cream; PDF badge small copper on peach — measure contrast, raise to ≥3:1 for UI.
- Search: after tapping X then BACK, field keeps focus and cursor (persists after Settings). Clear focus when the IME hides / on BACK (NOT from onFocusChanged).
## Master visual walk, notebook screen (Dark/Blueprint, build 2328dee)
- V1 Page edge (accent) runs under the status bar and crosses the clock/date text when the page is near the top.
  Fit/scroll clamp should keep the page top below the status bar, or draw a status-bar scrim over the canvas.
- V2/V16 Zoom "−" renders as a filled double bar (Duotone minus fill) and "+" is heavier than the other icons;
  same heavy "+" on the width/eraser "add favourite" tiles and view presets. Use Regular/Bold weight for −/+.
- V8 File quick menu: icon sizes/weights differ between "Recent files" rows and "Current file" rows; text start x differs by ~2 dp.
- V9 Selected recent file shows fill highlight AND a square checkbox-like check; elsewhere selection = bare check. Use one.
- V10 "Sync to Google Drive" is not sync (it saves a PDF via the picker). Honest label: "Save to Google Drive".
- V11 Naming: options popover "Report bugs" vs Settings/library "Bug / idea". Use "Bug / idea" everywhere.
- V27 More menu groups "Highlighter" under Canvas (it is a pen tool). Move to its own row under a "Pens" group or drop
  (highlighter is reachable via pen slots?) — check product intent; at least don't call it Canvas.
- V30 View presets popover: "Settings…" has both an ellipsis and a chevron (pick chevron); "Draw with finger" row has
  no icon so its label starts left of the others; padlock icons next to Zoom/Rotation have no visible meaning/label.
- Dead legacy UI (Worker 8): TopIconButton, ShareButton, ExportButton, BackToLibrary, NotebookTab, PageCounter,
  TopMoreMenu (+ ViewReadout?) have no live callers — delete (repo will be public; dead code reads as vibe-coded).
- "Clear page": verify it offers Undo (rubric F: undo over confirm).
## Master visual walk part 2 (build ca34cb2)
- V32 Lasso: rotate handle above a selection near the top sits under the floating toolbar (unreachable). Flip the
  rotate knob below the box (or inside) when it would collide with top chrome / screen top.
- V33 Selection bar isn't centred on the selection (bar centre ≈ 315 px vs selection centre ≈ 415 at 1280 scale)
  though there is room; centre it, clamped to screen.
- V34 "Dupl." → "Duplicate" (there is room; rubric C8 no abbreviations).
- V35 Mirror H / Mirror V are icon-only while every other action has a label: add "Flip ↔" / "Flip ↕" style labels
  ("Flip H", "Flip V") or make all icon+label consistent.
- V37 "Deselect" uses a filled checkbox icon (reads as "selected"); use an X / select-none icon.
- V38 Lasso More → "Plugins: Convert to LaTeX / Clean up diagram" are shown with no plugin installed (dead entries
  ending in a toast). Decision: show the Plugins section only when a registered plugin provides actions.
- V42 Pages tab "Bookmarked" pill: unclear whether filter or action; make it a clear toggle ("Bookmarks only").
- V45 Layers: "New layer" button uses the same tinted fill as the selected layer row; use a tonal/outlined button.
- V47 Accent look card (Settings) uses the square checkbox-style check again (see V9); use the bare check everywhere.
- V49 Sentence case: "Writing & Input" → "Writing & input"; UK spelling: "Customize toolbar items" → "Customise…".
- V50 Settings toggle rows' text starts ~4 px right of the other labels in the same section.
## Pen-speed check (Master, S9, build ca34cb2, gfxinfo)
- Writing 10 strokes: p50 5 ms, p90 7, p95 8, p99 8 ms, legacy jank 0 → within 8.3 ms (120 Hz). Live ink is front-buffered.
- Pen switching (8 taps): p50 5, p99 14 ms, 8/397 slow-UI frames (2%). First tap after reset showed ~75 ms frames
  (cold composition); re-measure after fixes; acceptable but watch.
## From review-1 regression QA (for next fixer)
- R1 (medium) BACK with any popover/menu open (title quick menu confirmed; check File ⋮, More, panels, wheel,
  page config, view presets, search) must close the overlay first, not leave the notebook.
- R2 (medium) Pages tab thumbnails of A4-landscape notebooks are portrait-shaped and crop the page: use the page's aspect.
- R3 Library long titles break mid-word ("MoM-Assignmen/t2-…"): allow breaks at '-', '_' (insert zero-width break
  opportunities after them) before falling back to mid-word; meta line needs a little more space under a 2-line title.
- R4 Search results: summary "1 notebook title" + header "Notebook titles" duplicate: drop the summary line when there
  is only one section, or drop section headers when only one section has results.
- R5 View presets: "Draw with finger" label uses a larger text style than "Fit page to width"/"Settings": same style.
- R6 Settings: "Customise toolbar items" row inset ~48 px from the other labels in its section: align.
- R7 Rotate knob flip threshold: flip when the knob CIRCLE (centre − radius − margin) would touch the top chrome,
  not when the centre crosses it (stayed above at 17 px clearance).
- R8 Library grid: a landscape cover beside portrait covers makes title/meta baselines differ across the row;
  give every cover the same box height (letterbox landscape inside it) so rows line up.
## Master walk part 3
- W1 Colour wheel (Markers) fans out from the left edge underneath the File pill and toolbar and under the status bar:
  swatches behind the chrome are visible but untappable. Hide/fade top chrome while the colour wheel is open (or
  start the fan below the chrome) so every visible swatch is reachable.
- W2 With the craft pass the right pill grew, so the centre toolbar island centres in the remaining gap (≈555 px of
  1280) rather than the screen centre (640). Decide: centre on screen when it fits without overlap.
## From final coverage QA (Worker 13)
- F1 (med) Left/Right rail grip sits in the side gesture zone: dragging it inward fires system Back and leaves the
  notebook. Apply systemGestureExclusionRects to the rail grip/rail (≤200 dp tall per edge), like the wheel.
- F2 (med) Pull-to-add-page hint/progress not reset: after rotation a "Release to add a page" ghost with full progress
  shows with no touch; idle notebook shows "Pull to add a page" with ~70% progress. Reset on rotation/fit/open.
- F3 (med) Highlighter active (More → Pens → Highlighter): no toolbar cell shows a selected state. Show an active
  highlighter cell (temporary slot or the More button tinted with a highlighter badge) so the active tool is visible.
- F4 (low-med) Corner docking: drop detection uses the finger, but the ball trails it → drops land as rail/top bar;
  saved side and shown side disagreed once. Use the ball's position for the corner test and save what's shown.
- F5 (low) Page config popover width changes per tab → tab strip jumps. Fixed width across tabs.
- F6 (low) Pen panel grows a third swatch row when a custom colour is added → More colours/Pick jump 56 px. Reserve
  the row or put custom colours in the existing grid (replace oldest).
- F7 (low) RGB tab: slider fills stop ~9 px short of the thumb; value numbers dim. Fill to thumb centre; onSurface numbers.
- F8 (low) Light theme + Dark paper: bright pills on near-black page; copper page edge almost invisible on dark paper;
  File menu has no visible edge on light paper. Check page-edge accent contrast on dark paper (≥3:1) and popover outline.
- F9 (low) Bottom bar overlaps the page's bottom edge (~40 px); add bottom clearance when docked bottom (like top).
- F10 (minor) Export look dialog preview always shows lined paper; use the notebook's paper style.
## From final-2 QA (observations to fix)
- G1 Pull-to-add triggers too easily: rapid finger swipes at the end of the notebook added page 2. For a TA scrolling
  fast in class this makes stray pages. Require a deliberate pull: higher threshold and/or a short hold at "ready"
  before release adds, and ignore flings (velocity) as pulls.
- G2 After rotation the view isn't re-clamped (scrolled past the page end with a large empty area). Re-apply the
  clamp/fit on size change.
- G3 Accessibility audit not done yet (rubric A1–A4): TalkBack labels on icon-only buttons, font scale 1.3.
## From final-3 QA
- H1 (med) G2 rotation on a tall multi-page notebook ("test1", 4 portrait pages, 75%, scrolled to page 4 end): after
  rotating to portrait the page bottom sits at ~59% of the screen and the page is shifted ~410 px right (cut off), though
  it's wider than the view. Rotating back restores. settleAfterResize/clamp must apply BOTH axes and the normal clamp rules
  (x ≤ 0 when wider than the view, centred when narrower). Possibly introduced by the G2 change — check against HEAD~1.
- H2 (low) Settings → Animations only reflects ANIMATOR_DURATION_SCALE after a relaunch; re-read on resume really
  (QA saw it stale in both directions).
- H3 (low) Disabled "Off" segment: blue text on blue pill, unreadable. Disabled segmented state must keep contrast.
- H4 (low, unconfirmed) With animator scale 0, first tap on the active pen after relaunch didn't open the pen panel.
  Check the panel-open path with MotionSpeed.OFF (a snap/0-duration animation never firing its end/visibility).
- H5 Back with a lasso selection active should clear the selection first (add to backAction order: overlay →
  selection → Present → fullscreen → leave).
## Pen-speed, controlled (Master, 26 Nov ~06:00; same 10 strokes in QA merge, 3 runs each, undone after)
- HEAD c903ad7: p50 7–8 ms, p99 15–19 ms, legacy jank 17–23%, slow-UI 0–3.
- ca34cb2 (before tonight's review fixes): p50 7–8 ms, p99 18–22 ms, legacy jank 33–45%, slow-UI 7–11.
→ No regression; HEAD is slightly better. Earlier "p99 8 ms" was measured in a run that also counted many cheap
  animation frames (99 frames) — not comparable. The slow frames are the committed-stroke redraw at pen-up (live ink
  is front-buffered, so the pen itself is unaffected). Worth a profiling pass later (chunk record at commit).
- Cover: photos (isInk=false) drawn lightness-inverted on dark-paper covers; covers must not invert photos.
