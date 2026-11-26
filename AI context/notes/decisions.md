# PenLab decisions made autonomously (26 Nov night) — review and reverse freely
- Wheel: ring cell 50dp / radius 155 / disc 190 / hub 72 (≈25% bigger than Graphite). Root cause of the
  two-tries gesture: MainActivity read dock.wheelOpen in composition → hub pointerInput key changed mid-gesture.
- Graphite has the same latent key bug in ToolDock (masked because its MainActivity doesn't recompose on open);
  not touched in Graphite tonight.
- Stage 6: "Bug / idea" moved from library header into Settings → Help & feedback; "Save as PDF" renamed "Export PDF".
- Stage 6 polish: Continue strip hidden when the library has ≤4 notebooks; section header not repeated when it equals
  the page title; empty-folder delete gets an Undo snackbar (no confirm dialog); selected chips/swatches get a check
  or ring, not fill only; all dialogs on PenLabDialog with one pill primary; empty state = 1 primary + 1 secondary;
  library search also matches notebook titles; search field loses focus on return.
- Status-bar icon colour follows the paper while a notebook is open (follows app theme in the library).
- Present mode: leaving restores the zoom/position from before entering.
- Wheel: HOLD_TO_MOVE raise 450→650 ms so a slow pull never turns into toolbar-move (QA: 150 ms slack).
- Graphite has the same reportSelection RectF.isEmpty bug (InkCanvasView ~L3543): selection buttons placed against
  the bottom-left corner + knob instead of the whole box. Not touched in Graphite tonight; one-line port when the owner wants.
- Rotation: views tilted ≤1° are straightened on resize so fit/clamp rules apply (a slight tilt used to skip them).
