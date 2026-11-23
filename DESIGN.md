# PenLab design notes

How the UI is meant to look and behave, and why. Read this before changing the toolbar.
Decided from the maintainers' choices, a review of other pen apps' manuals and user
feedback, and a critique against usability heuristics, accessibility, edge cases and motion.

## Principles

1. The page is the product. UI stays at the edge, small, and never moves while the pen writes.
2. Nothing in the UI may cost a frame of ink. Toolbar state changes on page changes, never per
   pen sample.
3. Few choices, visible at once: no scrolling inside the toolbar; sliders and the colour wheel live in panels opened by a second tap.
4. Every action can be undone, so nothing needs a confirmation dialog.
5. Nothing moves on its own while the pen is writing. Assists act only after the pen pauses
   (hold to snap) or lifts. A straight-writing guide line in another app was reported as
   "fighting you ... faster than you". A guide that reacts ahead of the hand
   feels like being chased; never build one. (If the ink itself ever feels like it leads the
   pen, the motion prediction is the first thing to turn down.)

## Decisions

- Toolbar: vertical pill, starting on the left edge; movable. It docks to the
  left or right edge and keeps the height it was dropped at; panels open towards the page.
- Pen types: Pen (even), Pressure, Fountain (slanted nib).
- Hold to perfect shapes: hold 500 ms at the end of a stroke; line, arc,
  triangle, rectangle/square, ellipse/circle. Overshoot past the start is trimmed and rounded
  corners are merged, because triangles were finicky. A haptic tick confirms; one undo removes it.
  Strokes under 48 dp are ignored so pausing inside a letter never turns it into a shape.
- Starting pens: black, blue, red.
- Dark page that is display-only: ink is stored in paper colours and shown lightness-inverted,
  so exports are ink on white.
- The eraser covers an area (a circle), and paper can be blank, lined or dotted.

- Lasso: select, move, resize (four corners keep proportions, four edge
  midpoints stretch one way), rotate (handle on a stem above the box, any angle, no snapping:
  a free range was requested), cut, copy, duplicate, mirror, delete. The box is drawn on the
  page, so it turns with the page view. A selection can be dragged
  with any tool; the clipboard lasts across tools until cleared.
- Paste opens from holding the pen still on the page (500 ms, only when the clipboard has
  something), not from a button that sits on screen. The selection's buttons float above its box; a lone Paste button
  shows at the top while the lasso is picked and something has been copied.

- Page view readout: top-right pill "zoom% angle°", tap for
  editable presets and zoom/rotation locks. No snapping of the page angle while turning;
  presets give exact values.

- Looks (the UI felt too bland): two switchable looks, **Blueprint** (blue) and **Copper**, in the
  zoom readout panel under Animations. Each tints the neutrals slightly and owns one accent, used only for
  meaning (picked tool, acting buttons, switches, progress). Tokens:
  `AccentTheme` in `ui/theme/Color.kt`; Material's primary is the accent.
- Titles use Bricolage Grotesque (variable, OFL, `res/font/bricolage.ttf`); body, labels and controls stay
  on the system face. Licence in `licenses/`.
- Full screen is a button on the top bar, not in the ••• menu.
- Pictures that act as ink (equations, diagrams, fills, covers) are shown on the dark page lightness-inverted
  with the hue kept (RGB invert + 180° hue turn), never a plain RGB inversion (that turned red cyan).

## The toolbar

| Part | Spec |
|---|---|
| Pill | 56 dp wide, fully rounded, 12 dp from the edge, 1 dp hairline, no shadow; a 26 dp grip at the top drags it |
| Cells | 48 x 48 dp touch targets; marks inside are 22 dp (26 dp when selected) |
| Tools | pens are dots, the highlighter is a square chip, the eraser is an icon: shape as well as colour |
| Selected tool | 2 dp ring at 36 dp plus the larger mark |
| Groups | tools / undo, redo / paper, clear, separated by short hairlines |
| Second tap on the selected tool | opens a panel beside that cell: pen type (pens only), size slider in mm with editable favourites (no fixed set of values), 8 pen or 4 highlighter colours |
| Panels | 16 dp radius, same surface as the pill; a pick applies and closes; pen-down on the page closes |
| Clear | set apart from undo/redo; shows "Page cleared · Undo" for a few seconds |
| Short windows | undo/redo hide first (the 2- and 3-finger taps cover them) |
| Palm | fingers on the toolbar are ignored while the pen is writing and for 250 ms after |

Motion: tool switch 120 ms ease-out, press scales to 0.94 on a stiff spring, panels fade and
grow from the strip's edge in 140 ms and fade out in 90 ms. Undo, redo and gestures do not
animate. Animated values are read only in the draw phase (`graphicsLayer`), so an animation
frame never re-runs the toolbar's composition.

The strip and its panel are placed by one layout (`ToolbarWithPanel`), not by position
callbacks. The chosen tool is sent to the canvas directly from the tap that changes it.

## Colours

UI colours are in `ui/theme/Color.kt`; ink and page colours in `ink/InkTool.kt` and
`ink/InkColors.kt`. Measured contrast on the toolbar (#2A2C2F): icons 11.1:1, disabled icons
2.8:1, blue pen 4.2:1, red pen 2.7:1 (hence the thin edge on every swatch).

## Left out of the first version on purpose

The lasso, shapes, layers, colour wheel and size slider were added later, each behind a
second tap or a panel so the default view stays small. The rest is still left out:

auto-hiding toolbar, tooltips, onboarding. Each adds a mode or a control; the kill criterion is
"too complicated or bloated".

## Toolbar that changes shape

While dragged, the toolbar shrinks into a ball. Dropped on the left or right edge it becomes
a vertical bar, on the top or bottom a horizontal bar, in a corner the tool wheel.

## Open

- Highlighter over coloured ink on the dark page: the band is faded to 70% there; judge on
  the tablet with real writing.
- Left-handed use: the pill's side is a single alignment in `MainActivity.kt`, not a setting.
