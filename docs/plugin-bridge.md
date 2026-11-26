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
- Importers are matched by MIME type or by extension (formats Android has no MIME type for). A file nobody claims is
  only ever tried with the built-in importers, never sent to a plugin.
- PenLab's manifest has `<queries><intent><action android:name="dev.penlab.PLUGIN"/></intent></queries>` (package
  visibility on Android 11+). Rescans happen when PenLab resumes, when Settings opens, and while it is on screen when a
  package is installed, updated or removed (no manifest receiver: blocked since Android 8).

## Trust

Settings → Plugins lists every plugin with name, package, capabilities and declared permissions. A plugin signed with the
same certificate as PenLab is **Trusted** and on by default; any other is off until the user switches it on, with a
one-time consent dialog. The choice is stored by package name. A plugin that crashes, times out or answers nonsense is
switched off until PenLab restarts, with one message (the same crash isolation as built-in plugins). The service also
checks its caller: only `dev.penlab.app` may call it (`PenLabPluginService`), optionally also with the same signature
(`requireSameSignature`).

## Transport

AIDL `dev.penlab.sdk.IPenLabPlugin`: `int apiVersion()`, `Bundle run(String capabilityId, String requestJson, in Bundle files)`,
`void cancel(String capabilityId)`. Files are `ParcelFileDescriptor`s inside the Bundles (no content URIs between the apps).
PenLab binds the explicit component on a worker thread for one call, waits 60 s (importers 180 s), then calls `cancel`,
unbinds and reports a timeout.

Request JSON (`PluginRequest`): `capabilityId`, `page {notebookId, pageIndex, widthMm, heightMm, darkPage}`,
`selection {boundsMm[l,t,r,b], strokeCount, itemCount, imageFile, strokesFile}`, `source {displayName, mimeType,
suggestedTitle, file}` (importers). Request files: `selection.png` (dark ink on transparent, at most 1200 px),
`strokes.json` (`StrokesJson`), `source`. Positions are millimetres of paper.

Result JSON (`ResultCodec`), returned in the Bundle under `result`, files by name next to it:

- `{"type":"items","items":[{"typeId","boundsMm":[l,t,r,b],"text"?,"file"?}]}`: `penlab.text` (the `text`),
  `penlab.image` (a photo) and `penlab.ink-image` (ink: dark on transparent, shown light on the dark page) with the
  picture as the item's `file`. PenLab saves pictures into the notebook's `images/` and makes real image and text items, so
  they draw and export after the plugin is uninstalled. Any other `typeId` shows as a grey box. One undo step.
- `{"type":"text","text"}` (a dialog), `{"type":"message","message"}`, `{"type":"none"}`.
- `{"type":"error","message"}`: the plugin could not do it. Shown as a message; the plugin stays on. (An importer's
  "cannot read this file" is this or `none`.)
- importer: `{"type":"notebook","title","pageWidthMm","pageHeightMm","pdfFile"?,"pages":[{"backgroundPdfPage","items":[...]}]}`.
  It becomes a notebook through the same path as a PDF import.

## Screens

PenLab starts the plugin's exported activity (explicit component) with `startActivityForResult`. Extras are in
`PenLabScreenContract` (notebook id, page index and size, dark page, and with a selection a PNG and the strokes as
content URIs of PenLab's FileProvider, granted for reading through the intent's ClipData). The activity calls
`PenLabScreenContract.isCalledByPenLab(this)` (an exported activity can be started by anyone), reads
`readRequest(intent)`, and answers with `setResult(RESULT_OK, PenLabScreenContract.resultIntent(this, result))`: the result JSON
and its files as granted content URIs served by the SDK's `PenLabResultProvider`. PenLab only follows URIs of a provider owned by
the plugin's own package. Cancelling places nothing.

## Writing a plugin

Copy `sample-plugin/`, depend on `:plugin-sdk`, extend `PenLabPluginService` (`onRun(call)` returns a `PluginResult`),
list the capabilities in `penlab_plugin.json`. Sign it with the shared PenLab key (see `sample-plugin/build.gradle.kts`) to be Trusted.

## Not done yet / test on a device

Recogniser and media capabilities (parsed only), icons for external entries, a Cancel button for a running plugin.
Device checks: install `sample-plugin` (Trusted, on by itself) and see it in Settings → Plugins; select ink, More ▾ → Word count;
toolbar More ▾ → Hello screen → the text box appears (undo removes it); uninstall → entries vanish; kill the plugin process
mid-call → one message, PenLab keeps running.
