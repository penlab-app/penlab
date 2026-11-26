# Bridge proposal: selected items in, "replace" out (items format 1)

Status: PROPOSAL (S3, 26 Nov 2026). Nothing in `penlab-bridge` was edited. The "Remove background" plugin in
`penlab-extras` is already written against this and checks for it at run time (see "Capability check").

## Why

A plugin that works on a selected photo (Remove background first; later crop, enhance, OCR of a photo) cannot do it
properly with today's bridge:

1. `selection.png` is a **render** of the selection (strokes + items, at most 1200 px on the long side, transparent
   paper). The original photo file never leaves PenLab (`PluginConvert.item` gives an `ImageItem` an empty payload;
   the request JSON only carries `itemCount`). A 12 MP photo shown on A4 arrives as about 1200 px, and if the lasso
   also holds ink the ink is in the picture.
2. Results can only **add** items (`PluginResult.Items` -> `changeItems { it += made }`). A cut-out lands next to or
   over the photo and the user deletes the photo by hand.

## 1. Request: the selected items, with their pictures

New optional top-level field `itemsFormat` (int, like `strokesFormat`; absent/0 = an older PenLab) and a new array
`selection.items`:

```json
{ "itemsFormat": 1,
  "selection": { "boundsMm": [..], "strokeCount": 3, "itemCount": 2, "imageFile": "selection.png", "strokesFile": "strokes.json",
    "items": [
      { "id": "9b1c...", "typeId": "penlab.image", "boundsMm": [l,t,r,b], "rotationDeg": 0,
        "file": "item-0", "mimeType": "image/jpeg" },
      { "id": "4e07...", "typeId": "penlab.text", "boundsMm": [l,t,r,b], "rotationDeg": 0, "text": "..." }
    ] } }
```

- `id`: the page item's id (`PageItem.id`), stable for the notebook, used by `replaces` below.
- `boundsMm` is the item's **unrotated** box (the rectangle before it is turned about its centre by `rotationDeg`,
  degrees, as `PageItem.rotation`), in notebook millimetres. (Not `PageItem.bounds()`, which is the axis-aligned box
  of the turned rectangle; `PluginConvert.item` keeps using that for the v1 `PluginItem`.)
- `file`: for `penlab.image` and `penlab.ink-image` items, the name of a request file holding **the original picture
  bytes** from the notebook's `images/` (unchanged, no re-encode). Sent as a `ParcelFileDescriptor` in the files Bundle
  like `selection.png`. Names are `item-<n>`; `mimeType` is sniffed from the bytes (`image/png`, `image/jpeg`, ...).
- Text items carry `text`; plugin-owned items carry no file. Items hidden by a hidden layer are not sent (same rule as
  `pluginSelection()`).
- Limits (host side): at most 8 picture files per call, each at most 32 MB; further pictures are listed without `file`.
- Old plugins ignore the new fields (the SDK reader skips unknown keys), so this is backwards compatible; the bridge
  version stays 2.

Host change: `RequestFiles.writeSelection` copies `images/<fileName>` into the call's scratch folder and lists them;
`Selection` (plugin-api v1) gains `val imageFiles: Map<String, File> = emptyMap()` and `PluginItem` gains
`id: String? = null` and `rotationDeg: Float = 0f` (defaults keep every existing caller compiling).

## 2. Result: an item that replaces a selected item

Two new optional fields on a result item (both in the existing `items` result):

```json
{ "type": "items", "items": [
  { "typeId": "penlab.image", "boundsMm": [l,t,r,b], "rotationDeg": 20, "replaces": "9b1c...", "file": "item-0" } ] }
```

- `rotationDeg` (default 0): as in the request, `boundsMm` is the unrotated box, turned about its centre.
- `replaces` (default none): the `id` of a **selected item sent in this call's request**. The host removes that item
  and adds the new one **in the same undo step** (`history.grouped { changeItems { remove; add } }`), keeps the new
  item selected, and puts it at the old item's z-order position.
- Safety: the host honours `replaces` only for ids it listed in this call's `selection.items`; any other id is
  ignored (the item is just added), so a plugin cannot delete things it was not given. One id may be replaced at most once.
- The original picture file stays in `images/` (undo needs it); a later "tidy images" pass may drop unreferenced files.

SDK change (`:plugin-sdk`, all additive):
- `PenLabBridge.ITEMS_FORMAT = 1`.
- `class SelectionItem(id, typeId, boundsMm, rotationDeg, file, mimeType, text)`; `SelectionInfo.items: List<SelectionItem> = emptyList()`;
  `PluginRequest.itemsFormat: Int = 0`; `PluginCall.itemFile(item): ByteArray? / InputStream?`.
- `ResultItem.replaces: String? = null`, `ResultItem.rotationDeg: Float = 0f`, written and read by `ResultCodec`.
- `ResultItem.image(...)` gets optional `replaces` / `rotationDeg` parameters.

## 3. Capability check (what the plugin does today)

`itemsFormat >= 1` in the request means "this PenLab sends items and honours `replaces`".

| PenLab | What "Remove background" does |
|---|---|
| has the proposal (`itemsFormat >= 1`, a photo item with a `file`) | cuts out the **original** photo (decoded to at most 2048 px), trims the empty margin, returns the cut-out with `replaces` = the photo's id and its box/rotation, so the photo is swapped in one undo step |
| current bridge (no `itemsFormat`) | cuts out `selection.png` (at most 1200 px; ink in the lasso is included), returns it as a **new** `penlab.image` placed beside the original (right if it fits on the page, else below), original untouched |

The plugin never sends `replaces` unless `itemsFormat >= 1`: a current PenLab would ignore it and put the cut-out
exactly over the photo, which looks like nothing happened.

Until the SDK has these types, the plugin carries a small `ProposedBridge.kt` that reads the extra request fields from
the raw request JSON and patches `replaces` / `rotationDeg` into the encoded result JSON. When the SDK adopts the proposal that file is
deleted and the service goes back to extending `PenLabPluginService`.

## 4. Tests the host change needs

- JVM: request JSON with items (and one without the picture), `replaces` honoured only for listed ids, rotation kept,
  undo restores the original photo in one step, a result with `replaces` from an old plugin payload (unknown id) just adds.
- Device: photo on the page -> lasso -> More -> Remove background -> photo is replaced by the cut-out, one undo brings the photo back;
  rotated photo keeps its angle; two photos selected -> both replaced.
