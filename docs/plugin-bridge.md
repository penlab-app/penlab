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
    { "kind": "pageAction", "id": "count", "label": "Count", "selection": "required|none|any" },
    { "kind": "importer",   "id": "fmt",   "label": "My format", "mimeTypes": ["application/x-fmt"], "extensions": ["fmt"] },
    { "kind": "screen",     "id": "chat",  "label": "Chat", "activity": ".ChatActivity", "placement": "selectionMenu|moreMenu|fileMenu" },
    { "kind": "recogniser", "id": "r", "label": "..." }, { "kind": "media", "id": "m", "label": "..." }
  ],
  "permissions": ["network", "microphone"] }
```

- `apiVersion` must be exactly 2. Unknown kinds (and capabilities with missing or unknown fields) are ignored.
  `recogniser` and `media` are read but not hosted yet.
- Where entries appear: pageAction `selection: required` in the lasso's More menu, `none` in the file menu, `any` in both;
  a screen goes where its `placement` says (`moreMenu` = the toolbar's More panel, default). External entries have no icon.
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
unbinds and reports a timeout.

Request JSON (`PluginRequest`): `capabilityId`, `strokesFormat` (the newest strokes-file version PenLab reads, see below; absent or 0 in an older PenLab), `page {notebookId, pageIndex, widthMm, heightMm, darkPage}`,
`selection {boundsMm[l,t,r,b], strokeCount, itemCount, imageFile, strokesFile}`, `source {displayName, mimeType,
suggestedTitle, file}` (importers). Request files: `selection.png` (dark ink on transparent, at most 1200 px),
`strokes.json` (`StrokesJson`), `source`. Positions are millimetres of paper.

Result JSON (`ResultCodec`), returned in the Bundle under `result`, files by name next to it:

- `{"type":"items","items":[{"typeId","boundsMm":[l,t,r,b],"text"?,"file"?}],"strokesFile"?}`: `penlab.text` (the `text`),
  `penlab.image` (a photo) and `penlab.ink-image` (ink: dark on transparent, shown light on the dark page) with the
  picture as the item's `file`. PenLab saves pictures into the notebook's `images/` and makes real image and text items, so
  they draw and export after the plugin is uninstalled. Any other `typeId` shows as a grey box. `strokesFile` names a
  result file of real strokes (next section). Items and strokes together are one undo step; with nothing selected they are
  centred on the visible page as one group.
- `{"type":"text","text"}` (a dialog), `{"type":"message","message"}`, `{"type":"none"}`.
- `{"type":"error","message"}`: the plugin could not do it. Shown as a message; the plugin stays on. (An importer's
  "cannot read this file" is this or `none`.)
- importer: `{"type":"notebook","title","pageWidthMm","pageHeightMm","pdfFile"?,"strokesFile"?,"pages":[{"backgroundPdfPage","items":[...]}]}`.
  It becomes a notebook through the same path as a PDF import. The one `strokesFile` holds the ink of all pages (below).

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

## Writing a plugin

Copy `sample-plugin/`, depend on `:plugin-sdk`, extend `PenLabPluginService` (`onRun(call)` returns a `PluginResult`),
list the capabilities in `penlab_plugin.json`. Sign it with the shared PenLab key (see `sample-plugin/build.gradle.kts`) to be Trusted.

## Not done yet / test on a device

Recogniser and media capabilities (parsed only), icons for external entries, a Cancel button for a running plugin.
Device checks: install `sample-plugin` (Trusted, on by itself) and see it in Settings → Plugins; select ink, More ▾ → Word count;
toolbar More ▾ → Hello screen → the text box appears (undo removes it); uninstall → entries vanish; kill the plugin process
mid-call → one message, PenLab keeps running.
