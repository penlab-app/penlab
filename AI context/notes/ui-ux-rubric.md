# PenLab UI/UX polish rubric (v1, 26 Nov 2026)

Purpose: one tickable checklist per UI element. Run it per screen x 4 theme combos (Dark/Light x Blueprint/Copper), on the Tab S9 (1205 x 753 dp) and once on the projector view.
Each item: `ID  rule | why | how to check`. Mark each PASS / FAIL / N/A. A FAIL needs a screenshot or adb note.

## Where the numbers come from (read this first)

- **PenLab's own design system wins on look and numbers** (DESIGN_SYSTEM_AND_PLAN.md, README.md section 4). Checkable values below are PenLab's: press scale 0.94, popover 140/90 ms, tool switch 120 ms, undo/ink 0 ms, targets >= 48 dp, radii 6/10/14/18/24 dp, spacing 4/8/12/16/24/32 dp, edge margin 12 dp.
- Where an outside source says something different, it is listed as an **OPEN DECISION** (the owner + Omar choose), not a failure. quiet-shadcn (no pills, Lucide, neutral selection, 6/8 radii) conflicts with the shipped look (pills, Phosphor Duotone, primaryContainer selection); only its *process and principles* are imported here.
- Source tags: **[M3]** developer.android.com Material 3 in Compose page; **[HIG]** developer.apple.com HIG (Feedback, Motion, Undo and redo, Apple Pencil and Scribble pages, fetched); **[Emil]** emilkowal.ski "7 practical animation tips"; **[Jakub]** jakub.kr "Details that make interfaces feel better"; **[Rauno]** rauno.me/craft/interaction-design; **[WIG]** Vercel web-interface-guidelines (web rules translated or dropped); **[NNg]** nngroup 10 heuristics; **[LoUX]** lawsofux.com (laws confirmed via search: Jakob, Fitts, Hick, Doherty 400 ms, Miller, Postel); **[Droid-stylus]** developer.android.com stylus palm-rejection cookbook; **[Droid-LS]** Android large-screen quality tiers; **[Concepts]** concepts.app manual; **[Goodnotes]** support.goodnotes.com gestures article; **[Samsung]** samsung.com S Pen support; **[PenLab]** project docs; **[Proj]** derived by me from the project docs, no external source.
- **(unverified)** = category convention or my reasoning, no fetched source. Not tagged to a source on purpose.
- Not obtained: m3.material.io pages (JavaScript-rendered, fetch returned the wrong page), so exact M3 state-layer percentages are NOT printed as fact. Linear's craft essays: none found; only a talk listing (Saarinen, Stripe Sessions 2024: "quality in the details becomes the differentiation"). Notability, Samsung Notes selection/empty-state specifics: not found in sources.
- Palette/contrast numbers in PenLab docs (e.g. Copper primary 5.45:1 on white) come from its own tests; this rubric asks you to re-measure on rendered screenshots.

---

## 1. Principles (P)

- [ ] P1  **Ink first, chrome second.** Nothing in chrome may compete with the page for contrast or motion while the pen is down. | Priority 1 in PenLab ("page and ink are the product"). | Pen-down: no chrome animation, no layout shift.
- [ ] P2  **Operate mode: one tap does the thing.** Primary actions (pen, eraser, undo, zoom) need no menu; second tap opens options. | Hick: fewer choices, faster decisions [LoUX]. | Count taps for: switch pen, undo, zoom reset, new page. Target 1 each.
- [ ] P3  **Respond within 100 ms, finish within 400 ms.** Every tap shows a visible response in <= 1 frame; any work > 400 ms shows progress. | Doherty threshold [LoUX]; "respond to every action quickly" [Emil]. | 240 fps capture or screen-record frame stepping.
- [ ] P4  **Undo over confirm.** Reversible actions execute immediately with an Undo path; confirmation dialogs only for irreversible loss. | User control and freedom [NNg]; warn only on unexpected irreversible loss [HIG Feedback]. | List every destructive action; each is either undoable or confirmed.
- [ ] P5  **Same thing, same look, same place.** One component per job (one chip, one popover frame, one segmented control). | Consistency and standards [NNg]; Jakob's law [LoUX]. | Screenshot diff of the same component across screens.
- [ ] P6  **Recognition over recall.** Icons that are not universally known carry a label or long-press tooltip; state is visible, not remembered. | Recognition rather than recall [NNg]. | Cover the labels: can a new user name each icon?
- [ ] P7  **Quiet by default, loud only for danger.** One accent colour for selection/primary; red only for destructive/error. | Aesthetic and minimalist design [NNg]; quiet-shadcn "one accent". | Count accent-coloured elements per screen; selection and primary only.
- [ ] P8  **Never lose work.** Autosave, undo across close, no state loss on rotate/resize/dock move. | Error prevention [NNg]; stylus guide: keep action history to roll back palm touches [Droid-stylus]. | Kill app mid-stroke; rotate; resize in desktop mode.
- [ ] P9  **Honest copy.** Say what really happens ("Share PDF", "Sync via Drive picker"), never promise a feature that is a stub. | Match with real world [NNg]; PenLab already does this for Sync. | Read every label against actual behaviour.
- [ ] P10 **Motion explains, never decorates.** Animate to show where something came from/went; skip motion on frequent actions. | "Add motion purposefully", "avoid motion on frequent interactions" [HIG Motion]; [Rauno]. | Each animation has a one-line reason in the PR.
- [ ] P11 **Design for the worst viewer.** The back-row student on a washed-out projector is the real audience. | [Proj] primary persona projects live. | Section 9 checks.

---

## 2. Visual craft (V)

### Spacing and alignment
- [ ] V1  All paddings/gaps are from the scale 4/8/12/16/24/32 dp (`PenLabSpacing`); no literal 5, 6, 10, 14, 18 dp in layout. | One rhythm reads as calm. | grep `\.dp` literals in chrome files; allow only token names and documented exceptions.
- [ ] V2  Floating islands sit `edgeMargin` (12 dp) from every screen edge and from each other; identical across all 3 top islands. | Inconsistent insets are the first thing an eye catches. | Overlay a 12 dp grid on a screenshot.
- [ ] V3  Icon buttons in one row share a centre line and a baseline to within 0 px; text labels sit on a common baseline. | Misalignment of 1 px reads as "cheap". | Draw a horizontal line across the toolbar screenshot.
- [ ] V4  Optical alignment: icons with asymmetrical visual mass (play, chevron, pen) are nudged so they look centred, preferably in the vector, not with padding hacks. | [Jakub] optical alignment. | Zoom 400% on each icon in its button.
- [ ] V5  Visible content is aligned to a shared left edge within a panel (title, rows, section labels). | Ragged edges = unplanned layout. | Ruler line down the panel.
- [ ] V6  Gap between a label and its control is smaller than the gap between groups (proximity). | Grouping without borders. | Measure: intra-group <= half inter-group.
- [ ] V7  No element is clipped, truncated unintentionally, or overlaps another, at font scale 1.0, 1.3 and 1.5. | README Stage 7 gate. | Settings > Font size; screenshot every panel.

### Radius and depth
- [ ] V8  Radius comes only from the shape tokens (6/10/14/18/24 dp, pill); nested shapes are concentric: outer = inner + padding. | Concentric corners look intentional [Jakub]. | Inspect each popover containing buttons/cards.
- [ ] V9  One depth cue per surface: hairline OR shadow, never both stacked ("ghost card"). Floating chrome = soft shadow; flat cards = 1 dp hairline. | PenLab invariant #8; M3 uses tonal + shadow elevation but one language [M3 Elevation]. | Zoom a card corner in each theme.
- [ ] V10 White chrome over white paper (Light/Light) is still distinguishable from the page: measure luminance step between island and paper and confirm a visible edge. | Both are `#FFFFFF`; the hairline is the only separator. | Screenshot, sample pixel colours at the island edge; expect a visible border/shadow, not a 0 delta.
- [ ] V11 Dark theme depth comes from surface lightness steps, not shadows alone; each step is >= 1.1 contrast between adjacent surfaces. | "Grey on grey on grey" is a known trap. | Measure ratio panel vs page vs card.
- [ ] V12 Image/cover thumbnails have a 1 px inner outline at roughly 10% (black in light, white in dark). | Stops covers bleeding into the background [Jakub]. | Library screenshot, Light and Dark.

### Colour and tokens
- [ ] V13 Zero hardcoded hex or `Color(0x...)` in UI code outside theme files and ink/paper previews. | Token discipline (PenLab invariant #8). | `grep -rn "Color(0x" app/src/main/kotlin/dev/penlab/app/ui` minus theme/.
- [ ] V14 Every foreground/background pair uses matching roles (onPrimary on primary, onPrimaryContainer on primaryContainer). | Mismatched roles break contrast [M3 Accessibility]. | Code review + contrast sampling.
- [ ] V15 Text contrast >= 4.5:1; large text and icons/borders that carry meaning >= 3:1; measured on a screenshot, not from tokens, in all 4 theme combos. | WCAG; [M3] a11y. | Colour picker on rendered pixels, including disabled and muted text.
- [ ] V16 Selected state is conveyed by more than colour (ring/fill shape change + weight) so it survives grayscale and bad projectors. | Colour alone fails colour-blind and washed-out displays. | Grayscale screenshot: can you still find the selected tool?
- [ ] V17 Pen swatch colours are true ink colours (from `InkColors`), never tinted by the theme accent. | The TA's pen colours carry meaning (black/blue/red/green). | Compare chip colour to stroke colour.
- [ ] V18 Light and Dark page ink: dark-page ink is lightness-inverted for display only; PDFs stay white; export is always ink-on-white. | PenLab rule. | Export a dark-mode note, open on laptop.

### Typography
- [ ] V19 Bricolage only on Display/Headline/Title; system sans on body/labels/controls. No third family. | Hierarchy via two voices. | Inspect `fontFamily` use.
- [ ] V20 Dynamic numbers (zoom %, page counter, angle, mm, time) use tabular numerals; width does not change when digits change. | No jitter on update [Jakub]. | Hold zoom stepper; the readout must not shift neighbours.
- [ ] V21 Type scale is used by role (Title/Body/Label), not by ad-hoc `fontSize`. M3 default sizes for reference: body 14/20, label 12/16, labelSmall 11/16 [M3]. | Predictable hierarchy. | grep `fontSize =` in ui/.
- [ ] V22 Smallest text on screen >= 12 sp; nothing important at 11 sp. | 11 sp is unreadable from a classroom [Proj]. | List all `labelSmall` uses.
- [ ] V23 Titles wrap balanced, no one-word last lines in 2-line titles; ellipsis is a single "…" and long names truncate in the middle for file names (keep extension/term). | Polished text edges [Jakub]. | Rename a notebook to 80 characters.
- [ ] V24 Sentence case everywhere ("New notebook", "Start writing"), consistent across menus, buttons, dialogs. | PenLab's copy style; Vercel's Title Case rule is deliberately NOT imported. | Read all labels.

### Icons
- [ ] V25 One family (Phosphor Duotone), one visual size per context (e.g. 24 dp in toolbar, 20 in menus), one stroke weight. Bold/Fill variants only where the DS says (check mark, active bookmark). | Mixed icon sets are an AI-slop tell. | Screenshot every icon; compare weights.
- [ ] V26 Duotone secondary layer is legible at projector distance: the icon still reads when the 20-30% tint layer disappears. | Duotone relies on a faint second tone that washes out. | Blur/greyscale the screenshot; each tool still identifiable.
- [ ] V27 Icon-only buttons have a tooltip/long-press label and a `contentDescription` (see A1). | Recognition vs recall; accessibility. | Long-press every icon.
- [ ] V28 Icon meaning follows convention (undo = curved left arrow, not a refresh). | Jakob's law [LoUX]. | Icon audit sheet.

---

## 3. Interaction states (S)

Check every interactive element in all 7 states. M3 uses state layers; exact percentages are theme-dependent and not asserted here.

- [ ] S1  **Rest:** looks tappable (affordance: fill, border, or icon container), not just floating glyphs. | Affordance [Rauno]. | Show to a new user; ask what is tappable.
- [ ] S2  **Pressed:** visible change within 1 frame (ripple or tint, plus 0.94 scale on tool cells per `Motion.kt`). | Instant feedback. | 240 fps recording; response on the same frame as touch-down.
- [ ] S3  **Pressed feedback does not shift layout or neighbours** and releases back on cancel (drag off the element). | Cancelable press. | Press, drag off, release: no action fired, state restored.
- [ ] S4  **Focused (keyboard/stylus-hover/D-pad):** visible focus ring >= 2 dp and >= 3:1 against its background, using the shape of the element (pill ring on pill). | Keyboard users, Bluetooth keyboard on Tab S9. | Connect a keyboard, Tab through every screen.
- [ ] S5  **Hover (S Pen hover / pointer):** a hover state exists for tool cells and menu rows, and S Pen hover on the canvas never triggers a popover. | Hover previews help predict the result [HIG Pencil]. | Hover with S Pen over toolbar and canvas.
- [ ] S6  **Selected:** `primaryContainer` fill + ring (2 dp `primary`), icon colour changes; there is exactly one selected tool at a time. | PenLab DS. | Toggle through all tools.
- [ ] S7  **Disabled:** reduced emphasis but still >= 3:1 for the glyph, explains why on long-press ("Nothing to paste"). Never removed silently. | Show people when a command cannot be carried out and why [HIG Feedback]. | Empty clipboard Paste; no-history Undo.
- [ ] S8  **Loading:** operation > 400 ms shows progress in place (inline spinner or determinate bar), the trigger button is disabled/replaced only after the request starts. | Visibility of system status [NNg]; [WIG]. | Export a 100-page notebook; open a large PDF.
- [ ] S9  **Error:** message states what failed and the next step; inline near the cause; retry exists. | Recover from errors [NNg]. | Force: no write permission, full storage, corrupt page file.
- [ ] S10 **Toggle/segmented/switch** shows both states with text or icon change, not colour alone; the thumb/indicator animates in <= 150 ms. | Clear state. | Settings screens.
- [ ] S11 **Sliders:** value readout while dragging, tabular, positioned so the finger/pen does not cover it; haptic detents at meaningful stops. | Do not mirror obscured controls [Rauno]. | Stroke width slider with a finger.
- [ ] S12 **Long press** has a visible progress/affordance when it triggers an action (pen hold-to-paste ~1.1 s, lasso 0.5 s). | Avoid invisible timers. | Hold on canvas; a visible cue appears before the action fires.

---

## 4. Touch and pen (T)

- [ ] T1  Every tappable element has a >= 48 x 48 dp hit area (visual may be smaller; extend the touch area, do not enlarge the glyph). | [Droid-LS] 48 dp; PenLab invariant. | Layout Inspector bounds / `Modifier.minimumInteractiveComponentSize` audit.
- [ ] T2  Adjacent targets have >= 8 dp between hit areas (or no gap, never overlap). | Fat-finger errors, Fitts [LoUX]. | Bounds overlay.
- [ ] T3  Pen (stylus) taps on toolbar work as reliably as finger taps; tapping with the pen tip is never ignored by palm rule. | Primary user holds the pen. | 20 pen taps on each toolbar button; 20/20.
- [ ] T4  Every floating bar, pill, panel and overlay uses `ignorePalm { canvas.isPenWriting }`. | PenLab invariant #3. | Rest palm on toolbar while pen writes: no button fires.
- [ ] T5  Palm rejection honours `ACTION_CANCEL` and `FLAG_CANCELED` (API 33+): a cancelled touch stops the gesture and rolls back temporary changes (no dot, no zoom jump). | [Droid-stylus]. | Rest a palm, then write; undo stack contains no phantom strokes.
- [ ] T6  Dismiss taps never leave an ink dot: tapping outside any popover/menu/wheel closes it and swallows the tap (`closedSomethingAt` within 2 ms). | PenLab invariant #4. | Open each overlay (20 of them), tap the canvas with pen and finger: stroke count unchanged.
- [ ] T7  Opening any overlay closes all others (one popover at a time), including top-bar popovers and Settings. | PenLab invariant #5; quiet-shadcn. | Open A then B; A must be gone.
- [ ] T8  Pen-down on canvas closes any popover and starts ink with no dropped first segment. | "Operate mode". | Open popover, write quickly; first 50 ms of stroke exists.
- [ ] T9  Finger behaviour is explicit: fingers pan/zoom, never ink, when a stylus is in use; "Draw with finger" is an obvious toggle for users without a stylus (default ON until a stylus is seen). | PLAN.md item 3b. | Test with and without S Pen.
- [ ] T10 Undo/redo gestures (2-finger tap, 3-finger tap) are discoverable once (first-run hint or settings entry), can be turned off, and don't fire on a pinch start. | Goodnotes uses 2-/3-finger double tap [Goodnotes]; HIG warns against redefining standard gestures [HIG Undo]; tap vs pinch confusion (unverified). | 50 pinches: 0 undos.
- [ ] T11 Pen side button = eraser while held, previous tool restored on release. | Same as Samsung Notes [Samsung]. | Hold button, erase, release; tool is restored.
- [ ] T12 Every drag/swipe/pinch/hold gesture has a visible-button alternative. | [WIG]; motor accessibility. | List gestures; each has a button.
- [ ] T13 Dragging the toolbar by its `||` handle starts after a small slop (8 dp) and never on a tap; dock snap zones are shown while dragging. | Accidental moves in class. | Tap handle 20 times; no move.
- [ ] T14 Zoom/pan with two fingers never cancels or lags an in-progress pen stroke. | Priority 1/2. | Pen down + finger pinch simultaneously.
- [ ] T15 Targets near screen edges (side rail, corner wheel) respect gesture-navigation insets so the system back gesture does not steal the tap. | Android 16 gesture nav. | Test rail docked left/right with gesture nav.
- [ ] T16 Wheels/sliders keep tracking if the pen/finger leaves the control's bounds mid-drag. | Do not drop the interaction when the control is obscured [Rauno]. | Drag colour wheel outside its circle.

---

## 5. Motion (M)

Use PenLab numbers: 120 ms tool switch, 0.94 press scale, 140 ms enter / 90 ms exit popovers, 0 ms undo/ink. Everything routes through `Motion.spring/tween`.

- [ ] M1  Enter/exit use ease-out (decelerate in), no ease-in on UI; popovers 140 ms in, 90 ms out. | [Emil] ease-out; exits quieter than enters [Jakub]. | Frame-count a recording.
- [ ] M2  Nothing animates longer than 300 ms except deliberate long moves (dock slide, page add); document each. | UI animations under 300 ms [Emil]. | List every duration in `Motion.kt` + call sites.
- [ ] M3  Popovers scale from the trigger's anchor point, not from centre; start >= 0.9 scale (never 0). | [Emil] origin-aware, no scale(0). | Open a popover from the leftmost and rightmost tool; origin differs.
- [ ] M4  Press scale: PenLab uses 0.94; Emil suggests 0.97. **OPEN DECISION** for the owner/Omar (0.94 on a 48 dp cell is a 3 dp shrink, visible; confirm it does not look "bouncy" on small icons). | [Emil] vs [PenLab]. | Side-by-side video.
- [ ] M5  High-frequency actions are instant: undo, redo, ink, eraser, tool switch visuals <= 120 ms, no bounce. | Avoid motion on frequent actions [HIG Motion, Emil, Rauno]. | Tap undo 20 times quickly; no queued animations.
- [ ] M6  Animations are interruptible: reversing mid-open (tap again, pen-down) retargets from the current value, no snap to end. | Interruptible [Rauno, Jakub]. | Open/close popover repeatedly at 2 Hz.
- [ ] M7  Springs: critically damped for menus/panels (no overshoot); bounce only after a throw gesture (toolbar throw, wheel). | the owner's "Apple feel" brief [PenLab]; physics matches gesture [Rauno]. | Visual check, overshoot < 2% on panels.
- [ ] M8  Animation phase isolation: animated values read inside `graphicsLayer {}` / `offset {}` / layout lambdas; no recomposition per frame. | PenLab invariant #7; compose-performance skill. | Layout Inspector recomposition counts during an animation = 0-1.
- [ ] M9  `MotionSpeed.OFF` snaps everything instantly, including popovers, snackbars, wheel, dock slide, page transitions. | In-app setting. | Set OFF, walk all screens.
- [ ] M10 **System reduced motion**: when Android "Remove animations" (animator duration scale 0) is on, PenLab behaves like OFF even if in-app speed is NORMAL. Not specified in docs, likely untested. | HIG: make motion optional [HIG Motion]; [WIG]. | `adb shell settings put global animator_duration_scale 0`, walk screens.
- [ ] M11 Never animate: ink while writing, undo/redo result, scroll while a pen is down, anything the user does >100 times/day, layout properties (width/height) in lists. | [Rauno, HIG Motion, WIG]. | Code review.
- [ ] M12 Enter animation of a multi-part panel staggers at most 3 groups with <= 40 ms offsets, or is a single fade. | Stagger helps if small [Jakub]; too long hurts speed. | Count stagger steps.
- [ ] M13 Loading indicators do not spin forever without information; after 3 s add text ("Importing page 12 of 40"). | Visibility of status [NNg]. | Import a large PDF.
- [ ] M14 No looping decorative animation anywhere in the app. | [WIG]; projector distraction. | Walk screens idle for 30 s.
- [ ] M15 Page transitions preserve spatial logic: library to canvas expands from the tapped cover; canvas to library collapses to it. | Spatial consistency [Rauno]. | Record both transitions.

---

## 6. Feedback and forgiveness (F)

- [ ] F1  Destructive action pattern: act immediately, show snackbar with **Undo** for >= 6 s (notebook delete to trash, page delete, clear page, folder delete). Dialogs only for irreversible loss. | [NNg] user control; [HIG Feedback]. | Perform each; Undo restores byte-identical content.
- [ ] F2  "Clear page" is covered by undo (one grouped step), and Undo is reachable after the toast fades (stays in toolbar). | Largest accidental loss risk in class. | Clear, wait 10 s, Undo.
- [ ] F3  One notification mechanism at a time: snackbar (action possible) vs transient toast (info only), and the two never stack or overlap; snackbars sit above the toolbar/rail, never under it. | Consistency; M3 snackbar usage (unverified details). | Trigger two events within 1 s.
- [ ] F4  Confirm success only for significant, non-obvious outcomes (export saved, import done, projector connected); never for undo, tool changes, or autosave. | [HIG Feedback]. | List all toasts; justify each.
- [ ] F5  Toast/snackbar copy: <= 8 words, names the object ("Moved 'Calc II' to Exams"), no exclamation marks, no emoji. | Honest, quiet copy. | Read all strings.
- [ ] F6  Undo/redo visibly highlights the result when it affects content off-screen (scroll/zoom to it, flash outline). | [HIG Undo] show the result. | Draw on page 1, scroll to 8, Undo: the view shows what changed.
- [ ] F7  Undo depth is unlimited within a session and persists across close (PenLab keeps last 100 steps; verify limit is stated or sufficient). | [HIG Undo]. | Make 150 strokes, close, reopen, undo 100.
- [ ] F8  Disabled Undo/Redo buttons are greyed when stack is empty (state, not hidden). | Status visibility. | Fresh notebook.
- [ ] F9  Selection actions (cut, delete) are one undo step each; Paste with cascade is one step. | Predictable undo [HIG Undo]. | Undo count per action.
- [ ] F10 Haptics (where available) confirm snap/dock, eraser mode change, selection start; none on ink. | [HIG Feedback] multi-channel feedback. | Feel it on S Pen button toggle and dock snap.
- [ ] F11 Save state is honest: "Saved" indicator or no indicator, but a visible, calm warning on failure with Retry. | Error recovery [NNg]. | Fill storage; cause a failed save.
- [ ] F12 Permissions/system errors in plain language with a deep link to the setting that fixes it ("Allow access to all files"). | [NNg] #9. | Revoke storage permission.

---

## 7. Empty, first-run and error states (E)

- [ ] E1  Every list/grid/panel has an explicit empty state: icon + title + one sentence + at most one primary button (and maybe one secondary). | quiet-shadcn empty-state shape; [WIG] handle empty states. | Empty library, empty folder, no search results, no recent files, no bookmarks, no layers.
- [ ] E2  Empty library: copy names the two real actions ("New notebook" / "Import PDF") and they work in one tap. | Operate mode. | Fresh install screenshot.
- [ ] E3  No-results search says what was searched and offers one fix ("Search all notebooks"). | Recognise and recover. | Search nonsense.
- [ ] E4  First-run sheet is skippable in one tap, takes <= 3 choices, changes nothing irreversible, and never appears again after skip or when notebooks exist. | README Stage 6 spec; [NNg] flexibility. | Fresh install, skip, relaunch.
- [ ] E5  First open of a canvas shows nothing but the page and toolbar; no tutorial overlay blocks writing. Gesture hints are one-time, dismissible by writing. | Priority 1. | Fresh install, open notebook, write immediately.
- [ ] E6  Error states are never raw exceptions or codes; each has a title, cause in plain words, and a next step. | [NNg] #9. | Corrupt PDF import, unreadable page file (kept aside per README).
- [ ] E7  Loading a large notebook shows layout skeleton (page frames, placeholder thumbnails), not a blank screen; media/thumbnails reserve their size so nothing jumps when they arrive. | [WIG] reserve space; skeletons preferred to spinners for lists (unverified). | Open 100-page notebook cold.
- [ ] E8  Offline/permission-lacking features degrade quietly (Drive sync via picker) and explain what is available. | Honest copy. | Airplane mode.
- [ ] E9  Very long names, 1000 notebooks, 1 notebook, 0 pages in a folder: layout holds (clamp, scroll, virtualised). | [WIG] short/average/long content; virtualise 50+ items. | Seed data.
- [ ] E10 Plugin/AI-looking menu entries that are placeholders ("Convert to LaTeX" without a plugin) are either hidden or labelled "needs a plugin" and explain how to get it. | Honest copy; avoid dead buttons. | Tap each plugin slot with no plugins installed.

---

## 8. Consistency across screens (C)

- [ ] C1  Same action = same label, icon, and place everywhere (Rename, Move, Delete, Export). One string/icon constant per concept. | [NNg] consistency. | Compare library card menu vs canvas file menu.
- [ ] C2  One dialog frame (`PenLabDialog`, max width 560 dp), one popover frame, one sheet frame; same corner radius, padding, title style, button order. | Component library rule. | Open all dialogs; overlay.
- [ ] C3  Button order: secondary left, primary right (or the platform convention) in every dialog; destructive is red but same size as Cancel. | Jakob/Platform [LoUX]; quiet-shadcn. | Screenshot all dialogs.
- [ ] C4  Primary action per screen is visibly unique (one filled button). | Hierarchy. | Count filled buttons per screen = 1.
- [ ] C5  Same component identical in Light/Dark and Blueprint/Copper except for token colours (no per-theme layout forks). | Token system. | Diff 4 screenshots.
- [ ] C6  Overflow menus (`⋮`) are always the same icon, same position (trailing), same 48 dp target, same item ordering: non-destructive first, destructive last, separated. | Predictability. | Library card, folder card, file pill.
- [ ] C7  Section labels, empty states, and settings rows use the same typographic roles. | Consistency. | Settings vs Page config.
- [ ] C8  Abbreviations are consistent and unambiguous: avoid "Dupl."; use "Duplicate" (tooltip at minimum). Same for "Mirror H/V". | Match real world words [NNg]; recognition. | Read selection bar at font scale 1.3.
- [ ] C9  Sorting of lists follows one rule everywhere (recent first) and says so. | Predictability. | Library, recent files.
- [ ] C10 Back/close behaviour: system back closes the top-most overlay first, then leaves the screen; never exits the app with an open panel. | Platform convention. | Back with each overlay open.
- [ ] C11 Dates, page counts and sizes formatted by locale APIs; units with non-breaking space ("4 pages", "10 MB"). | [WIG] locale. | Switch device language/region.
- [ ] C12 Spacing, icon size and tap target of the toolbar are identical for horizontal, vertical rail, bar, wheel and ball layouts as far as geometry allows. | One toolbar, many docks. | Cycle all dock modes.

---

## 9. Projector and classroom specifics (R) — derived from project docs [Proj]

- [ ] R1  What the audience sees: while a popover, recent-files menu or settings panel is open, the projector view stays clean (page and ink only) or the audience view clearly shows the same thing deliberately. | Private chrome should not leak into the shared view unless intended. | Open every overlay in Present/Projector mode; look at the projector output.
- [ ] R2  Present mode chrome is near-invisible: the corner pill is translucent, shows for <= 4 s after a touch ("peek"), and exit is always one tap away. | README Stage 5 spec. | Time the peek; confirm exit works while ink is being written.
- [ ] R3  Ink legibility at distance: default stroke width is >= 2.5 mm paper width for black pen; highlighter does not reduce ink contrast below 4.5:1. | Back row readability. | Photograph the projection from 8 m (or view at 50% size).
- [ ] R4  Light UI + Light paper is the projector default and is verified; black ink on `#FFFFFF`/near-white at maximum contrast, no tinted page by default. | Washed-out projectors crush light greys. | Set projector to low contrast / photo mode; write in grey and in black.
- [ ] R5  Hairlines, grid lines and dotted paper stay visible but never dominate: on a projector they should still read (>= 1.5 px at 100% zoom projected) yet be lighter than ink by >= 3:1. | Paper style must not hide equations. | Project lined/grid/dotted.
- [ ] R6  Pen colours are separable in projection: black/blue/red/green remain distinguishable under greyscale (use darker blue and green, check luminance differences >= 20%). | Four colours carry meaning in a derivation. | Greyscale screenshot of 4 strokes.
- [ ] R7  Cursor/laser-style or hover indicators are not mirrored by accident (hover dot, selection handles are intentional). | Audience should see ink, not UI noise. | Hover S Pen in projector mode.
- [ ] R8  Zoom and pan are smooth and **never** leave stale frames on the projector; zoom % readout does not appear on the projector unless chosen. | Doherty threshold [LoUX]; PLAN projector notes. | Pinch zoom while recording both screens.
- [ ] R9  Screen stays on and notifications are suppressed (or advised) during presentation; no system toasts/popups cover the page. | Embarrassing in front of a class. | 10-minute presentation with a notification arriving.
- [ ] R10 Accidental UI cannot appear mid-lecture: no unexpected dialogs, "rate us", update prompts, permission prompts in Present mode. | Operate mode. | Review all prompts.
- [ ] R11 Switching page/slide: transition <= 150 ms or instant, with no flash of white/black on the projector. | Teaching flow. | Page flip stress.
- [ ] R12 Battery and heat: nothing in the chrome polls or animates while idle (idle CPU near 0%). | Long class sessions. | `dumpsys cpuinfo`/Profiler while idle for 2 min.

---

## 10. Performance feel (X)

- [ ] X1  Pen-to-ink latency matches Concepts or better (measured with the 240 fps test). Any UI change near the drawing path re-runs it. | Priority 1; PHASE1 gate. | Frame-count a stroke start.
- [ ] X2  Zero Compose recomposition per ink sample; no state writes in `ACTION_MOVE`. | PenLab invariant #1. | Layout Inspector recomposition counts while writing = 0.
- [ ] X3  Toolbar tap to visible change <= 1 frame; panel open <= 140 ms to first paint. | Doherty. | Screen recording frame stepping.
- [ ] X4  120 Hz holds while panning a 100-page notebook and while a panel animates; no frame > 8.3 ms in 10 s of use (Perfetto/frame stats). | PenLab tested ~2500 strokes at 120 Hz. | `adb shell dumpsys gfxinfo dev.penlab.app framestats`.
- [ ] X5  First launch to usable canvas <= 1 s warm, <= 2.5 s cold; the library shows content progressively. | Doherty (cold start is an exception, give feedback). | `adb shell am start -W`.
- [ ] X6  No jank when opening a panel while a stroke is being committed in the background (save/index thread contention). | Handwriting index and autosave run in the background. | Write continuously while opening panels.
- [ ] X7  Lists over ~50 items are virtualised (`LazyVerticalGrid`); thumbnails decode off the main thread with a placeholder. | [WIG] virtualise; skeletons. | Library with 200 notebooks.
- [ ] X8  Release build (R8) is what is tested for every UI check; debug can hide or add jank. | Known bug only seen on release. | `./gradlew installRelease` before QA.
- [ ] X9  No layout shift when async content arrives (thumbnail, page count, search index status). | Reserve space [WIG]. | Slow-motion recording of library load.

---

## 11. Accessibility (A)

- [ ] A1  Every icon-only control has a meaningful `contentDescription` ("Undo", not "icon"); decorative icons use `null`; state is exposed ("Black pen, selected"). | [Droid a11y semantics]; [WIG]. | TalkBack walk of toolbar, selection bar, all three top islands.
- [ ] A2  TalkBack reading order follows visual order; floating islands read top-left to right, then page controls. | Traversal order. | TalkBack swipe through.
- [ ] A3  Custom controls (color wheel, toolbar customizer, drag handles, sliders) expose roles, values and alternatives (+/- buttons, hex entry). | Custom gestures hide from a11y. | TalkBack on wheel.
- [ ] A4  App works at system font scale 1.3 and 1.5 and Display size "Large": no clipped label, no overlap, toolbars scroll or reflow. | [Droid scalable content]. | Settings > Display; screenshot all screens.
- [ ] A5  Colour contrast >= 4.5:1 text, 3:1 UI, measured in all 4 themes (see V15), including placeholder text and disabled labels where informative. | WCAG. | Measure.
- [ ] A6  No information by colour alone (selection, error, search highlight, pen colour name). | WCAG 1.4.1. | Colour-blind simulation.
- [ ] A7  Works with an external keyboard: Tab order, Enter/Space activate, Esc closes top overlay, Ctrl+Z/Ctrl+Shift+Z undo/redo, Ctrl+N new page (or documented shortcuts). | Desktop-mode/Bluetooth keyboard on Tab S9. | Keyboard-only walkthrough.
- [ ] A8  Reduced motion honoured (see M10). Flashing content never exceeds 3 flashes/s. | WCAG 2.3.1; [HIG Motion]. | System setting.
- [ ] A9  Touch targets >= 48 dp (T1) and a "larger controls" setting or at least respects Display size. | Motor accessibility. | Display size max.
- [ ] A10 Dialogs and sheets trap focus, return focus to the trigger on close, and announce their title. | Standard a11y. | TalkBack open/close.
- [ ] A11 Language/RTL: layout mirrors correctly (Arabic locale), icons that imply direction flip (undo/redo, chevrons) and tabular numerals still align. | User base is Egyptian; likely used with Arabic UI (Proj, unverified). | Switch to Arabic; screenshot.

---

## 12. Small things that make it feel premium (K)

Each is a concrete micro-detail. Tick when present and verified.

- [ ] K1  Pressed state at 0.94 scale **and** returns with a spring, not a snap. | [Emil]. | Slow-mo video.
- [ ] K2  Ripple/pressed tint is clipped to the element's shape (a circle button has a circular ripple, a pill a pill). | Craft. | Press edge of each shape.
- [ ] K3  Hit area extends beyond the glyph (48 dp) but the pressed highlight matches the visible shape, not the huge touch box. | Precision feel. | Press near the edge.
- [ ] K4  Tooltips: first shows after ~500 ms hold; the next one opens instantly when moving to a neighbour, and closes together. | [Emil] tooltip behaviour. | Long-press neighbours.
- [ ] K5  Tabular numerals on every changing number (zoom %, page n/N, mm, degrees, timers). | [Jakub]. | Hold a stepper.
- [ ] K6  Selected tool indicator slides/morphs between cells in 120 ms rather than disappearing/appearing. | Continuity. | Switch pens quickly.
- [ ] K7  Popovers anchor with a small pointer or alignment to their trigger and stay fully on-screen with 12 dp margin, flipping side when needed. | Spatial consistency [Rauno]. | Open from each dock position.
- [ ] K8  Concentric radii: a 6 dp inner swatch inside a 14 dp pill with 8 dp padding. | [Jakub]. | Inspect.
- [ ] K9  A 1 px hairline at 10% on thumbnails and paper previews; no heavy borders. | [Jakub]. | Zoom.
- [ ] K10 Shadows are layered and soft (2 layers, low alpha), never a single dark blur. | [Jakub] layered shadows. | Zoom on shadow edge in Light.
- [ ] K11 Dock snapping has a subtle haptic tick and a 100-150 ms settle with a tiny overshoot only after a throw. | Rauno/HIG physical feedback. | Throw the toolbar.
- [ ] K12 Momentum: throw velocity carries into the settled position; light flick travels farther. | [Rauno] kinetic physics. | Flick slow vs fast.
- [ ] K13 Wheel/slider dragging continues if the finger leaves the control; value updates 1:1 with no lag. | [Rauno]. | Test.
- [ ] K14 Eyedropper/loupe shows a magnified preview above the finger, hidden when released. | [Rauno] loupe. | Eyedropper test.
- [ ] K15 Pen colour/size preview stroke in the panel matches the real stroke exactly (same renderer). | Trust. | Compare preview vs ink.
- [ ] K16 The hover dot/cursor for the S Pen shows the real stroke size/eraser radius before touching. | Hover previews predict the result [HIG Pencil]. | Hover.
- [ ] K17 Zoom readout fades after 1.5 s of inactivity but never while adjusting; double-tap resets to 100%. | Concepts double-tap zoom field to recentre [Concepts]. | Use.
- [ ] K18 Page counter uses a subtle count animation or none; never flashes. | Calm. | Scroll.
- [ ] K19 Snackbars slide from the edge with an 8-12 dp lift above the toolbar and dismiss with a swipe; Undo is a text button >= 48 dp tall. | Polished toasts. | Trigger one.
- [ ] K20 Text selection, rename, and title fields: caret colour is the accent, the field selects all on first tap in rename, keyboard "Done" commits. | Detail polish. | Rename a notebook.
- [ ] K21 Keyboard opening never covers the focused text box on canvas (known open edge case) and the page scrolls to keep it visible. | Known issue in CLAUDE.md. | Insert text near the bottom.
- [ ] K22 Folder/notebook colours are from a curated palette with checked contrast against both themes' surfaces. | Avoids muddy chips. | Library with all colours.
- [ ] K23 Consistent haptic and no sound by default; any sound is optional and off in Present mode. | Classroom etiquette (unverified). | Settings.
- [ ] K24 Cover thumbnails fade in over 150 ms when decoded, never pop; placeholders have the exact same size. | Perceived quality [Emil] blur/fade. | Cold library.
- [ ] K25 Nothing is "almost aligned": every pair of adjacent islands/pills has an identical height (e.g. 48/56 dp) and vertical centre. | Craft. | Ruler on screenshots.
- [ ] K26 Settings opens to the last-used section; changes apply live with no "Apply" button; Reset is per section with Undo. | Fewer decisions. | Walk Settings.
- [ ] K27 Theme/accent/paper switches crossfade in <= 200 ms without flashing white. | Dark room use. | Toggle theme at night.

---

## 13. Anti-patterns and AI-slop tells (Z)

Any hit = FAIL. These are the visible signs of generated, un-curated UI.

- [ ] Z1  Purple/blue gradients, glow, or glassmorphism blur on chrome. | Slop signature. | Visual scan.
- [ ] Z2  Emoji used as icons, status, or bullets in the UI (including empty states). | PenLab rule. | grep emoji; scan.
- [ ] Z3  Kicker/eyebrow text above headings ("SETTINGS" above "Appearance"), decorative section labels in all caps. | PenLab invariant #8. | Visual scan.
- [ ] Z4  Card inside card inside card; hairline + heavy shadow together ("ghost card"). | PenLab invariant #8. | Library, settings.
- [ ] Z5  Three identical feature tiles with icon + title + sentence in a first-run or empty state. | Generic template tell. | First-run sheet.
- [ ] Z6  Generic copy: "Oops!", "Something went wrong", "Let's get started!", "Unleash your creativity", exclamation marks. | Honest copy. | grep strings.
- [ ] Z7  Default Material look leaking: stock M3 `FilledTonalButton` with default purple scheme, default `Switch`, `AlertDialog` styling different from `PenLabDialog`. | Own tokens over Material defaults (PLAN.md). | Open every dialog.
- [ ] Z8  Mixed icon families (Material Icons next to Phosphor); stroke weight differences. | Consistency. | Icon audit.
- [ ] Z9  Everything the same weight and size: no hierarchy (all labels 14 sp medium). | Hierarchy. | Squint test.
- [ ] Z10 Equal emphasis on every button: more than one filled primary button visible at a time. | Hierarchy. | Count.
- [ ] Z11 Overly bouncy spring on everything, scale-in from 0, long (> 300 ms) menu animations. | [Emil] for numbers; [HIG Motion]. | Frame count.
- [ ] Z12 Fake loading (skeleton for something that is instant), fake progress, fake "AI" badges. | Honest copy. | Check each.
- [ ] Z13 Feature creep in chrome: > 12 visible controls in the toolbar at 1205 dp, or 6 pens + 7 tools overflowing without scroll/overflow affordance. | Hick, Miller [LoUX]. | Count at default config and at max (6 pens).
- [ ] Z14 Dead UI: buttons that do nothing, disabled with no reason, settings that don't apply. | Honest behaviour. | Tap everything.
- [ ] Z15 Over-explaining: tooltips on obvious controls, paragraph descriptions in panels. | Minimalism [NNg]. | Read each panel; max 1 short sentence.
- [ ] Z16 Gradient text, rainbow accent rings, decorative blobs/orbs behind content. | Slop tell. | Scan.
- [ ] Z17 Inconsistent corner radius within one row/panel (pill next to 10 dp next to 14 dp without reason). | Consistency. | Scan.

---

## 14. Competitor reference notes (what the category does)

Only what was found in sources; the rest is marked.

- **Concepts:** Tool wheel with eight customisable slots incl. undo/redo; can convert to a toolbar by dragging to an edge; compact mode docks a spinnable wheel in a corner; double-tap the zoom value to recentre [Concepts]. Relevance: PenLab's toolbar/wheel/ball modes and home action match this model; confirm one-tap access to undo in every mode.
- **Goodnotes:** Undo = two-finger double tap, redo = three-finger double tap, lasso actions undoable by the same gestures; Windows 3-finger OS gesture conflicts noted [Goodnotes]. User feedback only: Pencil double-tap always switches to eraser, lasso should not auto-trigger while writing (not official). Relevance: decide tap vs double-tap for 2/3-finger undo (PenLab uses taps) and ensure nothing activates lasso during writing.
- **Samsung Notes / S Pen:** Hold the S Pen button to erase, release to return to the previous tool; area vs stroke eraser; stray dots can appear when the pen is lifted too fast or the palm lands before the tip [Samsung, PhoneArena via search]. Relevance: PenLab's side-button eraser and palm-rule delay are on the right track; test pen-lift followed by immediate finger touch.
- **Apple Pencil (HIG):** Double-tap toggles current tool/eraser by default; respect the user's preference; do not use it for content-changing actions; hover previews; make custom behaviour opt-in and discoverable [HIG Apple Pencil]. Squeeze exists on Pencil Pro only. Relevance (Android analogue): S Pen air actions are optional; do not depend on them.
- **Notability:** pen/palm settings specifics not obtained. Treat all Notability comparisons as (unverified).
- **Empty states / selection UI in these apps:** not found in sources; not asserted.

---

## 15. How to run this rubric

1. Pick a screen (Library, Canvas chrome, Pen panel, Eraser panel, Colour wheel, Tools, More, Page config, Lasso bar, Export dialog, Settings, First launch, Present mode).
2. For each element on that screen tick the relevant IDs. Elements usually need V, S, T, M, A, K, and Z; screens also need E, C, R, X.
3. Take the screenshot first, then measure. Measurements > opinions.
4. Record FAILs as `ID, screen, theme combo, evidence path`. Group fixes by ID so one repair lands in the shared component, not per screen.
5. Re-run the whole rubric on the release build (X8) after fixes; do not tick from memory.

## 16. Open decisions to raise with the owner and Omar

1. Press scale 0.94 vs 0.97 (M4).
2. 2-/3-finger tap vs double-tap for undo/redo (T10): PenLab uses single taps; Goodnotes uses double taps.
3. Whether Light UI + Light paper becomes the default in Present mode (R4).
4. Duotone icons vs projector legibility (V26): consider a "high-contrast icons" setting.
5. Sentence case copy rule is final (V24), contradicting Vercel's Title Case guideline.
6. Whether to honour system "Remove animations" in addition to the in-app speed (M10).
