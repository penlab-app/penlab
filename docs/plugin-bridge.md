# PenLab plugin bridge (v2): plugins as separate apps

A plugin is a small separate Android app. PenLab finds it, asks the user, and talks to it through a
bound service. PenLab is never rebuilt for a plugin. (The in-process v1 API in `plugin-api/` is what
the app itself and the adapters speak; see `plugin-api-spec.md`.)

Modules: `:plugin-sdk` (what a plugin app and PenLab share), `:sample-plugin` (a complete example, applicationId
`dev.penlab.sample`: a "Word count" page action and a "Hello screen" screen).

## How PenLab finds a plugin

The plugin app declares an exported service with the action `dev.penlab.PLUGIN` and a manifest resource:

```xml
<service android:name=".MyService" android:exported="true">
    <intent-filter><action android:name="dev.penlab.PLUGIN" /></intent-filter>
    <meta-data android:name="dev.penlab.plugin.manifest" android:resource="@raw/penlab_plugin" />
</service>
```

`res/raw/penlab_plugin.json`:

```json
{ "id": "dev.example.penlab.thing", "name": "Thing", "apiVersion": 2,
  "capabilities": [
    { "kind": "pageAction", "id": "count", "label": "Count", "icon": "@drawable/ic_count", "selection": "required|none|any" },
    { "kind": "importer",   "id": "fmt",   "label": "My format", "mimeTypes": ["application/x-fmt"], "extensions": ["fmt"] },
    { "kind": "screen",     "id": "chat",  "label": "Chat", "icon": "@drawable/ic_chat", "activity": ".ChatActivity", "placement": "selectionMenu|moreMenu|fileMenu|topBar", "inputs": ["page", "notebook", "items"] },
    { "kind": "recogniser", "id": "r", "label": "..." }, { "kind": "media", "id": "m", "label": "...", "icon": "@drawable/ic_mic" }
  ],
  "permissions": ["network", "microphone"] }
```

- `apiVersion` must be exactly 2. Unknown kinds (and capabilities with missing or unknown fields) are ignored.
  `recogniser` is read but not hosted yet; `media` is hosted (see "Media").
- Where entries appear: pageAction `selection: required` in the lasso's More menu, `none` in the file menu, `any` in both;
  a screen goes where its `placement` says (`moreMenu` = the toolbar's More panel, default; `topBar` = its own icon button in the top-right bar, beside search).
- Optional `"icon"` on `pageAction`, `screen`, and `media`: `@drawable/<name>` (or `<name>`) matching `[a-z][a-z0-9_]{0,127}`, loaded off the main thread from the plugin package and bounded to 96 px (`ExternalPluginIcons`); rendered tinted to the menu icon colour (monochrome 24 dp vector recommended). Invalid or missing icons are dropped silently and fall back to PenLab's built-in slot/default icon.
- Importers are matched by MIME type or by extension (formats Android has no MIME type for). A format that is only known by its
  extension should list `extensions` and leave `mimeTypes` empty: claiming `application/octet-stream` or `application/zip` would route
  every unrelated binary to the plugin. (The library's import picker accepts any file, so such files stay pickable.) A file nobody claims is
  only ever tried with the built-in importers, never sent to a plugin.
- PenLab's manifest has `<queries><intent><action android:name="dev.penlab.PLUGIN"/></intent></queries>` (package
  visibility on Android 11+). Rescans happen when PenLab resumes, when Settings opens, and while it is on screen when a
  package is installed, updated or removed (no manifest receiver: blocked since Android 8).

## Trust

Settings → Plugins lists every plugin with name, package, capabilities and declared permissions. A plugin signed with the
same certificate as PenLab is **Trusted** and on by default; any other is off until the user switches it on, with a
one-time consent dialog. The choice is stored by package name. A plugin that crashes, times out or answers nonsense is
switched off until PenLab restarts, with one message (the same crash isolation as built-in plugins). Settings shows such a
plugin with its switch off and a "Try again" button that clears the crash flag and rescans; the user's saved on/off choice is
never changed by a crash. The service also
checks its caller: only `dev.penlab.app` may call it (`PenLabPluginService`), optionally also with the same signature
(`requireSameSignature`).

## Transport

AIDL `dev.penlab.sdk.IPenLabPlugin`: `int apiVersion()`, `Bundle run(String capabilityId, String requestJson, in Bundle files)`,
`void cancel(String capabilityId)`. Files are `ParcelFileDescriptor`s inside the Bundles (no content URIs between the apps).
PenLab binds the explicit component on a worker thread for one call, waits 60 s (importers 180 s), then calls `cancel`,
unbinds and reports a timeout. While a `pageAction` is running, PenLab's status snackbar offers a **Cancel** action that cancels the coroutine, calls `IPenLabPlugin.cancel(capabilityId)` on a worker thread, unbinds cleanly, and leaves the plugin enabled.

Request JSON (`PluginRequest`): `capabilityId`, `strokesFormat` (the newest strokes-file version PenLab reads, see below; absent or 0 in an older PenLab),
`itemsFormat` (the newest items format, see "Selected items"; absent or 0 in an older PenLab), `page {notebookId, pageIndex, widthMm, heightMm, darkPage}`,
`selection {boundsMm[l,t,r,b], strokeCount, itemCount, imageFile, strokesFile, items[]}`, `source {displayName, mimeType,
suggestedTitle, file}` (importers). Request files: `selection.png` (dark ink on transparent, at most 1200 px),
`strokes.json` (`StrokesJson`), `item-<n>` (original pictures of selected photos, below), `source`. Positions are millimetres of paper.

Result JSON (`ResultCodec`), returned in the Bundle under `result`, files by name next to it:

- `{"type":"items","items":[{"typeId","boundsMm":[l,t,r,b],"text"?,"file"?,"replaces"?,"rotationDeg"?}],"strokesFile"?}`: `penlab.text` (the `text`),
  `penlab.image` (a photo) and `penlab.ink-image` (ink: dark on transparent, shown light on the dark page) with the
  picture as the item's `file`. PenLab saves pictures into the notebook's `images/` and makes real image and text items, so
  they draw and export after the plugin is uninstalled. Any other `typeId` shows as a grey box. `strokesFile` names a
  result file of real strokes (next section). Items and strokes together are one undo step; with nothing selected they are
  centred on the visible page as one group.
  `rotationDeg` and `replaces` are explained in "Selected items" below.
- `{"type":"rename","title"}`: rename the open notebook (a **screen's** answer; PenLab cleans the title: control characters and line breaks become
  spaces, runs of spaces collapse, at most 80 characters, blank = a message instead). An older PenLab does not know the type and treats it as a malformed
  answer (the plugin is switched off), so a screen sends it only when `EXTRA_HOST_FEATURES` contains `rename` (see "Screen inputs").
- `{"type":"text","text"}` (a dialog), `{"type":"message","message"}`, `{"type":"none"}`.
- `{"type":"error","message"}`: the plugin could not do it. Shown as a message; the plugin stays on. (An importer's
  "cannot read this file" is this or `none`.)
- importer: `{"type":"notebook","title","pageWidthMm","pageHeightMm","pdfFile"?,"strokesFile"?,"pages":[{"backgroundPdfPage","items":[...]}]}`.
  It becomes a notebook through the same path as a PDF import. The one `strokesFile` holds the ink of all pages (below).

### Selected items: the original photos in, "replace" out (items format 1)

`selection.png` is only a **render** of the selection (strokes and items, at most 1200 px, transparent paper). A plugin that
works on a selected photo (remove its background, crop, enhance, read its text) needs the real picture, and wants to swap the
photo for the result instead of adding a second copy. Items format 1 does both. It is additive: the bridge version stays 2, an
older plugin ignores the new fields, and `PluginRequest.itemsFormat` / `canReplaceItems` tell a plugin whether the PenLab it
runs in has it (0 / false in a PenLab without it, which sends no items and would ignore `replaces`).

**Request.** Top-level `itemsFormat` (1; `PenLabBridge.ITEMS_FORMAT`) and `selection.items`, one entry per selected page item
(`SelectionItem`; `PluginRequest.selectionItems`):

```json
{ "itemsFormat": 1,
  "selection": { "boundsMm": [..], "strokeCount": 3, "itemCount": 2, "imageFile": "selection.png", "strokesFile": "strokes.json",
    "items": [
      { "id": "9b1c...", "typeId": "penlab.image", "boundsMm": [l,t,r,b], "rotationDeg": 20, "file": "item-0", "mimeType": "image/jpeg" },
      { "id": "4e07...", "typeId": "penlab.text",  "boundsMm": [l,t,r,b], "rotationDeg": 0, "text": "..." } ] } }
```

- `id` is the page item's id, stable for the notebook; a result names it in `replaces`.
- `boundsMm` is the item's **unrotated** box in notebook millimetres: the rectangle before PenLab turns it about its centre by
  `rotationDeg` degrees. (This is the same convention results use, so a plugin can hand the numbers straight back.)
- `penlab.image` and `penlab.ink-image` items carry `file`, the name of a request file (`item-0`, `item-1`, ...; a
  `ParcelFileDescriptor` in the files Bundle like `selection.png`) holding the **original picture** from the notebook, and
  `mimeType` (sniffed from the bytes: `image/png`, `image/jpeg`, ...). A picture whose long side is over **2048 px** is scaled down
  to 2048 (re-encoded as PNG if it was PNG or has transparency, else JPEG, quality 92); a smaller one is sent byte for byte.
  Read it with `PluginCall.itemBytes(item)` / `itemInputStream(item)`.
- Limits: at most 8 picture files per call and 32 MB each; further pictures (or one that cannot be read) are listed without
  `file`, so a plugin falls back to `selection.png`. Text items carry `text`; plugin-owned items carry neither file nor text.
- Items PenLab does not list: items whose layer is hidden are not selected in the first place (same rule as `selection.png`).

**Result.** A result item may carry two more fields:

- `rotationDeg` (default 0): `boundsMm` is the unrotated box, turned about its centre by this angle, as in the request.
- `replaces` (default none): the `id` of a **selected item listed in this call's request**. PenLab removes that item and puts
  the new one at the same place in the stacking order, in the **same single undo step** as everything else in the result (other
  added items, strokes); the new item takes over the old one's place in the selection. An empty box (`right <= left` or
  `bottom <= top`) with `replaces` means "keep the old item's position and turn and fit the picture inside its old box".
  The old picture file stays in `images/` (undo needs it).
- Safety: `replaces` is honoured only for ids this call's request listed, and each id once; anything else (an unknown id, an id from
  another call, a second item naming the same id, an item the user deleted meanwhile, a plugin screen's result) is ignored and the
  item is simply added. A plugin cannot delete things it was not given. `ResultMapper` does this check; `PluginItemMaterialiser.place`
  does the in-place swap.

In the SDK: `ResultItem.image(png, l, t, r, b, ink, rotationDeg, replaces)` and `ResultItem.text(..., rotationDeg, replaces)`.
A plugin that replaces a photo checks `call.request.canReplaceItems` first and otherwise adds its result beside the original:

```kotlin
override fun onRun(call: PluginCall): PluginResult {
  val photo = call.selectionItems.firstOrNull { it.isPicture && it.file != null } ?: return fallBackToSelectionImage(call)
  val bytes = call.itemBytes(photo) ?: return PluginResult.Error("no picture")
  val out = process(bytes)
  val b = photo.boundsMm
  return PluginResult.Items(listOf(ResultItem.image(out, b[0], b[1], b[2], b[3], rotationDeg = photo.rotationDeg, replaces = photo.id)))
}
```

A plugin *screen* gets items only if its capability lists the `items` input (next section); without it a screen's `replaces` is ignored.
Tests: `WireTest` (SDK: request items, result fields, older-PenLab defaults), `RequestItemsTest` (host: files, caps, ids),
`ResultMapperTest` (valid / invalid / missing / duplicate `replaces`), `ItemPicturesTest`, `PluginItemMaterialiserTest` (in-place
swap, empty-box frame, one-undo-step grouping). Device check: photo on the page, lasso it, run a plugin that replaces it; the photo
is swapped in place (rotated photo keeps its angle), one undo brings the photo back, two selected photos are both replaced.

### Strokes (real ink)

A result can carry real strokes: PenLab turns them into the same `InkStroke`s the pen makes, so they are saved in the normal
page file and erase, select, move and are searched like handwriting. (Pictures of ink, `penlab.ink-image`, remain for plugins
that only have a picture.) JSON is far too big for thousands of strokes, so the strokes are a compact **binary file** returned
like any result file (a `ParcelFileDescriptor` in the Bundle, or a granted URI from a screen) and named in the JSON by
`strokesFile`; the SDK writes it for you: `PluginResult.Items(items, strokes)` and `NotebookPage(strokes = ...)`. Plugins use
`SdkStroke` and `StrokeFamily`; the codec is `StrokesBinary` (`encode`, `encodeSections`, `decode`, `decodeSections`; JVM-tested).

**Units: millimetres of paper, not page units** (PenLab multiplies by its own units per millimetre: 10 on A4 notebooks, the
screen's dots per millimetre on an endless canvas, so a plugin never needs to know). In an importer's result they are measured
from the top-left of that page; in an `items` result like the items' `boundsMm` (notebook millimetres, or centred when nothing
was selected). Pen width is millimetres of paper too.

Format version 1, big-endian:

```
4 bytes  magic "PLNK"            u16 format version (1)            u32 section count
per section (a page of an imported notebook, section i = page i; an `items` result has one section):
  u32 stroke count
  per stroke:
    u8  family      0 pressure, 1 ballpoint, 2 fountain, 3 highlighter (PenLab's own pens; unknown reads as pressure)
    u8  flags       bit 0: pressures follow, bit 1: times follow
    i32 argb        colour as PenLab stores it: paper colours, i.e. what the ink looks like on a white page
                    (PenLab shows it lightness-inverted on a dark page). Highlighters: alpha 0x66 like PenLab's;
                    an opaque colour is drawn at that 40 %
    f32 width       pen width, mm
    i32 layer       0-based layer, or -1 = the notebook's current layer (an import makes the layers it needs, at most 16;
                    elsewhere it is clamped to the notebook's layers)
    u32 n           point count
    n x (f32 x, f32 y)      mm
    n x f32 pressure        if flag 0: 0..1, or -1 for none (clamped to 0..1)
    n x u32 time            if flag 1: ms since the stroke began, never decreasing; without it PenLab uses an even pace
```

Pen families: `pressure` (width follows pressure; PenLab's default pen), `ballpoint` (one width), `fountain` (angled flat nib),
`highlighter` (wide, translucent, drawn under the ink of its layer). Points that are not finite numbers are dropped by the
encoder; the decoder refuses a newer version, a bad count or a short file (`WireFormatException`: the plugin's fault, so the
plugin is switched off like for any malformed answer). Limits: 64 MB per strokes file, 2 million strokes, 2 million points per stroke.
Large imports are decoded and written to the notebook on the importer's worker thread, never the main thread.

**Older PenLab.** The bridge version stays 2, so a plugin built with this SDK still loads in an older PenLab, which ignores
`strokesFile` and would place nothing. The request tells you: `PluginRequest.strokesFormat` is 0 there and 1 or more in a PenLab that reads
strokes. A plugin that returns only strokes should answer `PluginResult.Error("Update PenLab ...")` (or fall back to pictures)
when `strokesFormat < 1`.

## Screens

PenLab starts the plugin's exported activity (explicit component) with `startActivityForResult`. Extras are in
`PenLabScreenContract` (notebook id, page index and size, dark page, and with a selection a PNG and the strokes as
content URIs of PenLab's FileProvider, granted for reading through the intent's ClipData). The activity calls
`PenLabScreenContract.isCalledByPenLab(this)` (an exported activity can be started by anyone), reads
`readRequest(intent)`, and answers with `setResult(RESULT_OK, PenLabScreenContract.resultIntent(this, result))`: the result JSON
and its files as granted content URIs served by the SDK's `PenLabResultProvider`. PenLab only follows URIs of a provider owned by
the plugin's own package. Cancelling places nothing.

### Screen inputs (what PenLab prepares for a screen)

Every screen launch also carries `EXTRA_NOTEBOOK_TITLE` / `EXTRA_NOTEBOOK_FOLDER` (strings, "" for none), `EXTRA_SELECTION_BOUNDS_MM` (float[4], notebook
millimetres, only with a selection: a result is placed where its `boundsMm` say, so a screen that puts something beside or under the selection needs
the box, which strokes alone do not give for a selection of photos) and `EXTRA_HOST_FEATURES` (String[] of what this PenLab can do for screens:
`screen-inputs`, `rename`; absent in an older PenLab, so a plugin that needs one checks it and otherwise says "update PenLab").

Rendering is not free, so a screen asks for pictures in its manifest, `"inputs": [...]` (unknown names are ignored):

| Input | Extras | What |
|---|---|---|
| `page` | `EXTRA_PAGE_IMAGE` (Uri) | the current page, 1400 px wide, as it would print (paper colours on white, PDF slide under it), JPEG quality 90. An endless canvas: what is on screen. |
| `notebook` | `EXTRA_NOTEBOOK_IMAGES` (ArrayList of Uri), `EXTRA_NOTEBOOK_PAGES` (int[], 1-based, same order) | the written pages (ink, photo, text or PDF slide; blank pages are skipped), at most 60, 900 px wide, JPEG. Drawn one at a time off the main thread (`InkCanvasView.pagesForBackground`), so the ink never freezes. The page numbers matter: blank pages are skipped, so the position in the list is not the page number. An endless canvas gives one picture. |
| `items` | `EXTRA_SELECTION_ITEMS` (JSON array, the same objects as `selection.items` above), `EXTRA_SELECTION_ITEM_FILES` (Bundle: `item-0`... to Uri) | the selected page items with their original pictures (same limits and scaling as for a page action); the screen's result may then `replaces` those ids (same safety rules: only ids listed here, each once). |

All URIs are PenLab FileProvider URIs granted for reading through the intent's ClipData, and the scratch files are deleted when the screen has answered. Put each
input on the capability that needs it (a `notebook` input costs up to 60 renders at every open): for example a catch-all panel asks for `page`, and a separate
file-menu entry asks for `notebook`. In the SDK: `PenLabBridge.INPUT_*`, `PenLabScreenContract.EXTRA_*`, `ScreenRequest.pageImage / notebookImages / notebookPages / selectionBoundsMm / selectionItemsJson / hostFeatures`.
Tests: `ExternalManifestTest` (inputs), `ResultMapperTest` (rename, `replaces` ids), `WireTest` (rename result, items JSON). Device check: a screen with `notebook` input on a notebook
with blank pages reports page numbers that skip them; a screen with `items` replaces a selected photo in one undo step.

### Theme extras

PenLab also passes its current look so a screen can match it. Read them with `PenLabScreenContract.theme(intent)`, which
returns a `ScreenTheme(darkUi, accentArgb, darkPaper)`; if an older PenLab sent nothing you get a light default.

| Extra | Type | Meaning |
|---|---|---|
| `dev.penlab.extra.THEME_DARK_UI` (`EXTRA_THEME_DARK_UI`) | boolean | PenLab's interface is in its dark theme |
| `dev.penlab.extra.THEME_ACCENT` (`EXTRA_THEME_ACCENT`) | int (ARGB, alpha 0xFF) | the accent colour of that theme (Blueprint, Copper, ...) |
| `dev.penlab.extra.THEME_DARK_PAPER` (`EXTRA_THEME_DARK_PAPER`) | boolean | the open notebook's paper is dark (a separate setting from the UI theme; same value as `DARK_PAGE`) |

The values are those at launch time; a screen is not told if the user changes theme while it is open. Targeting SDK 35+
means the screen draws edge to edge: pad your content with the system-bar insets (see `HelloActivity`, which also sets the
status-bar icon colour from `darkUi`).

## Media (lecture recording)

A plugin app can record a lecture while the user writes: every stroke remembers when it was written, a finger tap on ink plays the
audio from that moment, and a recording pill shows in the top chrome. PenLab only owns the bookkeeping; the plugin owns the
microphone, the file and the player. Without a media plugin switched on, marked ink is plain ink (no seek).

**Manifest.** `{ "kind": "media", "id": "record", "label": "Record lecture", "placement": "topBar|moreMenu|fileMenu", "screen": "<id of a screen capability>", "startActivity": "<activity class>" }`.
`placement` says where the "Record lecture" entry goes (`moreMenu`, the toolbar's More panel, is the default, and so is any unknown value;
`topBar` is a mic button in the top chrome where the pill appears; `fileMenu` is the file options popover). `screen` is optional and only
names a screen capability of the same plugin that lists recordings (that screen appears under its own placement). No entry shows while a
recording is live (the pill has the controls).

**Starting from the foreground (`startActivity`).** Android 12+ refuses a microphone foreground service that is started while the app is in the
background, and a plugin's *bound* service counts as background even when PenLab is in front and binds it with `BIND_INCLUDE_CAPABILITIES`
(device log: "Foreground service started from background can not have location/camera/microphone access"). The fix is the standard one: the
START must come from an activity that is visible. So a media capability may name `startActivity` (a class name, or one starting with "." relative to
the plugin's package; an exported activity). When it is present, "Record lecture" does NOT send `media:command start`: PenLab opens that activity
(explicit intent, started FOR A RESULT so the plugin can check `PenLabScreenContract.isCalledByPenLab`, no animation) with the same extras a screen gets
(`PenLabScreenContract`: capability id, notebook id, page index and size, dark page, theme; no selection). The activity should be transparent: it asks for any
missing permission right there (a dialog needs a visible window, so first run works too), starts its foreground service with
`startForegroundService`, and finishes at once. The result it sets is ignored. If the launch itself throws, or no `startActivity` is declared,
PenLab falls back to the old binder `media:command start` (fine for a plugin whose start does not need a visible app). With no notebook open and a
`startActivity` declared, PenLab shows "Open a notebook first" and starts nothing. Pause, resume and stop always go over the binder.
The plugin must call `mediaStarted` only after its service is in the foreground AND its recorder runs.

**Plugin to PenLab: `IPenLabHost`** (in `:plugin-sdk`; `PenLabHostClient` binds it: `connectBlocking()` on a worker thread, then the calls below, all
safe when PenLab is absent). PenLab's exported service is `dev.penlab.app/.plugins.host.MediaHostService`.

| Call | Meaning |
|---|---|
| `mediaStarted(pluginId, ref, startElapsedRealtimeMs): Int` | A recording started for the open notebook. `ref` is the plugin's opaque reference (a content URI, a path). Returns the recording's index, or -1 (no notebook open, or `ref` refused: blank, over 1024 characters, or any control character, U+001F included). |
| `mediaPaused` / `mediaResumed(pluginId, atElapsedRealtimeMs)` | Pause and resume. |
| `mediaStopped(pluginId)` | The recording ended: the pill goes away. |
| `playbackPosition(pluginId, recordingIndex, positionMs, playing)` | The player's progress; the glow follows. Report the index the seek carried (or `mediaStarted` returned). |
| `level(pluginId, level)` | Microphone level 0..1 for the meter (about 10 a second; faster is dropped). |

All times are `SystemClock.elapsedRealtime()`. A start in the future or over a minute ago is clamped to "now". Only one recording is live at a time;
a second plugin's `mediaStarted` returns -1 (the same plugin starting again replaces its own). Closing PenLab ends PenLab's side of a recording
(the plugin's recorder goes on, unmarked); a plugin that is switched off or crashes loses its live recording the same way.

**Who may call.** The service is exported, so every call is checked: the calling UID's packages must contain the package that owns the `pluginId`
argument, and that plugin must be one PenLab hosts right now (switched on by trust or consent, not crashed, declaring `media`). An app that is not a
plugin, a plugin that is off, and a plugin speaking as another plugin all get a `SecurityException` (`MediaCallerPolicy`). With no PenLab screen
alive every call is a harmless no-op.

**Notebook list.** The notebook keeps its recordings in `notebook.properties` (`recordings`, entries joined by U+001F). PenLab owns it: `mediaStarted`
appends `ref` (or returns the index of an identical existing entry) and writes the file at once. A stroke's mark is `(recordingIndex shl 32) or positionMs`
(Graphite's AudioMark, so Graphite notebooks keep working) and holds an INDEX, so entries are only ever appended, never removed or reordered.

**Failed starts.** The reference is stored when `mediaStarted` arrives, so a plugin that says "started" and then fails would leave a dead entry. As a
safety net (a correct plugin never needs it), `MediaSessions.stopped` takes the reference off again when ALL of these hold: this start appended it (it did not
reuse an old entry), it is still the LAST entry, the plugin sent no `level` and no `playbackPosition`, no stroke was marked with it, and `mediaStopped` came within
10 s of the start. Entries are never removed from the middle, and never when anything refers to them, so indices of marked recordings cannot shift.
(The host interface has no "stopped with an error" call; a quick stop with no report is the signal.)

**Stamping.** While a recording of the open notebook is live, every committed stroke gets its mark at pen-up (never while the pen is down; nothing runs on
the pen's path: one volatile read and arithmetic from a cached session snapshot). The position is where the stroke BEGAN (pen-up time minus the stroke's
duration) on the recording's own clock with pauses left out (`MediaTimeline`). A stroke that began during a pause, or in another notebook, gets no mark; one
that began before a pause keeps its mark even if the pen lifted inside it. Hold-to-snap shapes carry the mark of the line they replace.

**PenLab to plugin: `run` calls** (bound like any capability call, 10 s timeout, crash isolation as usual; bound with `BIND_INCLUDE_CAPABILITIES`; that keeps the plugin's process
alive and PenLab-visible but does NOT let a bound service start a microphone foreground service: see `startActivity` above). The request is a normal request JSON with these top-level keys, built by
`PenLabPluginService.mediaCommandRequest` / `mediaSeekRequest` and read with `PluginCall.mediaCommand`, `mediaSeekRef`, `mediaSeekPositionMs`, `mediaSeekRecordingIndex`, `mediaNotebookId`:

- `run("media:command", {"command": "start"|"pause"|"resume"|"stop", "page": {"notebookId": ...}})`: the pill and the entry. `page` is the open notebook (as for
  page actions); `start` always names the open notebook, the other controls name the live recording's notebook (and carry no `page` if another notebook is open now).
  Answer `none`, or `error` / `message` with text PenLab shows. A failed `stop` ends the pill anyway.
- `run("media:seek", {"ref": "<ref>", "positionMs": n, "recordingIndex": i})`: a finger tap on marked ink. `positionMs` already includes a 2 s lead (the sentence is
  heard from its start). `recordingIndex` is the index in the notebook's list; report it back in `playbackPosition` so the glow follows even when the plugin does not
  recognise `ref` (a notebook migrated from Graphite). PenLab accepts `playbackPosition` only for the live recording, an index in the open notebook's list, or the index of the last seek.

**UI.** The recording pill sits under the left island (full screen keeps it; Present hides it, recording continues): dot, elapsed time, level meter, pause/resume, stop.
It is the same chrome pill as the islands: palm-ignoring, 48 dp targets, registered in the top-bar hit rects so its taps never leave a stray dot. The glow is drawn by the
selection layer (off the ink path), covers ink written from 4 s before to 0.3 s after the play position, follows the ink unless the user pans, and goes if the plugin stops
reporting for 15 s. With several media plugins on, a seek goes to the one that is recording, else the first.

Tests: `MediaTimelineTest` (packing, pauses, stamp rules), `MediaSessionsTest` (start / refs / one at a time / throttling / playback index rules / failed-start removal), `MediaCallerPolicyTest`,
`RecordingRefsTest` (list round trip through Properties, refused refs), `MediaPagesTest`, `MediaStartRouteTest` (start activity or binder), `ExternalManifestTest` (`startActivity`), `ExternalMediaTest` (wire format, bind flags, crash isolation), `WireTest` (SDK:
request keys), `MediaPillTest`. Device check: record 20 s while writing, strokes marked, stop, tap ink, plays from there with the glow; uninstall the plugin, ink still fine.

## Writing a plugin

Copy `sample-plugin/`, depend on `:plugin-sdk`, extend `PenLabPluginService` (`onRun(call)` returns a `PluginResult`),
list the capabilities in `penlab_plugin.json`. Sign it with the shared PenLab key (see `sample-plugin/build.gradle.kts`) to be Trusted.

## Not done yet / test on a device

The recogniser capability (parsed only).
Device checks: install `sample-plugin` (Trusted, on by itself) and see it in Settings → Plugins; select ink, More ▾ → Word count (check `@drawable/ic_wordcount` icon and Cancel snackbar action on slow calls);
toolbar More ▾ → Hello screen → the text box appears (undo removes it); uninstall → entries vanish; kill the plugin process
mid-call → one message, PenLab keeps running.
