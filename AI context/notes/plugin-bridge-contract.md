# PenLab plugin bridge (v2) — contract (Master, 26 Nov 2026)

the owner's choice: plugins are separate small apps (APKs) that PenLab discovers; PenLab is never rebuilt for a plugin.
Public PenLab gets the bridge + a tiny SDK. the owner's plugins live in a private repo (penlab-extras).

## Discovery
- A plugin app declares an exported `<service>` with intent-filter action `dev.penlab.PLUGIN` and
  `<meta-data android:name="dev.penlab.plugin.manifest" android:resource="@raw/penlab_plugin"/>` — a JSON file:
  `{ "id": "dev.akram.penlab.samsung", "name": "Samsung Notes import", "apiVersion": 2,
     "capabilities": [ { "kind": "importer", "id": "sdocx", "label": "Samsung Notes (.sdocx)", "mimeTypes": [...], "extensions": ["sdocx"] },
                       { "kind": "pageAction", "id": "...", "label": "...", "icon": "@drawable/x", "selection": "required|none|any" },
                       { "kind": "screen", "id": "...", "label": "...", "activity": "com.x.ChatActivity", "placement": "selectionMenu|moreMenu|fileMenu" },
                       { "kind": "recogniser", ... }, { "kind": "media", ... } ],
     "permissions": ["network", "microphone"] }`
- PenLab queries `PackageManager.queryIntentServices(Intent("dev.penlab.PLUGIN"), GET_META_DATA)`, parses the JSON,
  and refreshes on package add/remove (broadcast receiver for PACKAGE_ADDED/REMOVED/REPLACED with the plugin action).
- Major `apiVersion` must match (2). Unknown kinds are ignored.

## Trust / consent
- Settings → **Plugins** lists discovered plugins (name, package, capabilities, declared permissions).
- A plugin signed with the same certificate as PenLab is enabled by default ("Trusted"); any other plugin is listed
  disabled until the user turns it on (one-time consent dialog naming what it can access). Choice persisted.
- Disabled plugins contribute nothing to menus.

## Transport (data-in / data-out)
- PenLab binds the plugin service (`bindService` with explicit component, BIND_AUTO_CREATE), calls AIDL:
  ```aidl
  interface IPenLabPlugin {
    int apiVersion();
    // request/response are JSON strings; large payloads are passed as files via content URIs inside the JSON
    String run(String capabilityId, String requestJson);
    void cancel(String capabilityId);
  }
  ```
- Large data: PenLab writes inputs (selection PNG, strokes JSON, the import source copy) into
  `cache/plugin-io/<uuid>/`, exposes them through PenLab's FileProvider with `grantUriPermission` to the plugin package,
  and puts the content URIs in requestJson. The plugin writes outputs into its own FileProvider and returns content URIs;
  PenLab reads them with ContentResolver (plugin must grant read to `dev.penlab.app`).
- Timeouts: run() on a PenLab worker thread with a 60 s default (importers 180 s); binder death / timeout / exception →
  the existing crash isolation (plugin disabled for the session, one message). UI shows the existing busy snackbar.
- JSON results map to the v1 `Result` types: `{"type":"items","items":[{typeId, boundsMm, payloadUri|imageUri}]}`,
  `{"type":"text","text":...}`, `{"type":"message",...}`, `{"type":"none"}`; importer → `{"type":"notebook", pages:[...]}`
  in the same shape as v1 `ImportedNotebook`, with image/PDF parts as URIs.
- External capabilities are adapted into the existing `PluginRegistry` as `Plugin`/`Capability` objects (adapter classes),
  so PDF (built-in) and external plugins share menus, import routing and crash isolation.

## Screens (UI-heavy plugins)
- `screen` capability: PenLab launches the plugin's exported Activity (explicit component) via the Activity Result API
  with extras: notebookId, pageIndex, page size mm, darkPage, optional selection PNG + strokes JSON URIs (granted).
  The plugin returns `RESULT_OK` + a result JSON (same Result shapes) which PenLab places as one undo step.
- Placement controls where the entry appears (selection More menu, toolbar More menu, File ⋮ menu).

## Media marks (for the audio plugin later)
- `media` capability: PenLab offers a "Recording" hook: the plugin's recorder screen/service tells PenLab (via a
  bound-service callback or a broadcast to PenLab with the plugin's id) "session S started at T / stopped"; while a
  session is active PenLab stamps committed strokes with the existing MediaMark field (session index + offset).
  A finger tap on marked ink sends `seek(session, positionMs)` to the plugin. (Design here; built with the audio plugin.)

## SDK (public, module `:plugin-sdk`, Android library)
- `PenLabPluginService` abstract base (implements the AIDL, JSON parsing, URI helpers), data classes mirroring the JSON,
  `PenLabScreenContract` helpers for screen activities, and the JSON schema docs. Plugin repos depend on it via
  Gradle composite build (`includeBuild`) or a published artifact later.
- A sample plugin module `:sample-plugin` (public) with one pageAction ("Word count") proving the round trip.

## Tests
- JVM: manifest JSON parsing (valid, unknown kinds, wrong apiVersion), Result JSON ↔ v1 Result mapping, consent rules.
- Device (QA): install sample plugin APK → appears in Settings → Plugins (trusted) → its action runs from the menu →
  result placed; uninstall → disappears; kill the plugin mid-run → PenLab survives with one message.

## Amendments (advisor review, binding)
1. PenLab manifest MUST declare `<queries><intent><action android:name="dev.penlab.PLUGIN"/></intent></queries>`
   (targetSdk 36 package visibility) — without it discovery, binding and checkSignatures see nothing.
2. No manifest PACKAGE_ADDED receiver (blocked since Android 8): rescan in onResume and when Settings → Plugins opens;
   optionally a runtime-registered receiver while running.
3. SDK security: `PenLabPluginService` verifies the caller (`Binder.getCallingUid()` → package must be dev.penlab.app,
   and signature check for trusted mode) before running anything; reject others.
4. Transport: pass files as `ParcelFileDescriptor` arguments in the AIDL (no cross-app FileProviders):
   `String run(String capabilityId, String requestJson, in ParcelFileDescriptor[] inputs, out-results via returned
   descriptor list)` — e.g. `Bundle run(String capabilityId, String requestJson, in Bundle files)` where files is a
   Bundle of name → ParcelFileDescriptor, and the returned Bundle holds "result" JSON + name → ParcelFileDescriptor.
5. Image results (LaTeX, diagrams, background removal) map onto the existing `ImageItem(isInk = …)` saved in the
   notebook's images/ — never PluginPageItem — so they draw and export after the plugin is uninstalled.
6. Plan item (not bridge): moving the owner's Graphite library + Recordings/Graphite into PenLab (one-off export from
   Graphite; audio plugin reads existing recordings so old MediaMarks still seek).
7. No keys/tokens in any repo; AI key stored encrypted in the plugin's own data; plugin builds use the shared signing
   key from ~/.config/penlab (→ "Trusted" by same certificate).
8. Bridge is built on branch `plugins/bridge` (from ui/redesign); merged after S9 QA.
