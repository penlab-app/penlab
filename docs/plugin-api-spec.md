# PenLab plugin API — v1 spec (draft for approval, 23 Nov 2026)

Status: **draft**, waiting for approval by the maintainers. No code yet. Decisions already taken: notes in a normal
folder (option A, "All files access"), public GitHub repo, GPL-3.0.

## Goal

One open-source base app, **PenLab** (pen, pages, PDF, present mode, export). Everything else is a plugin:
Optional extras (AI, diagrams, audio, competitive-programming helpers, laptop sync, Samsung import), an offline LaTeX recogniser,
later community work. The base must stay fast and simple with zero plugins installed.

## Two kinds of plugin

| Kind | How it ships | Can do | Used for |
|---|---|---|---|
| **Built-in module** (v1) | Gradle module in the build, depends only on `:plugin-api` | everything in the API, incl. Compose UI | optional extras, Gemini, Samsung import |
| **External APK** (v2, later) | separate app, found by intent action `dev.penlab.PLUGIN`, bound service + AIDL | data in → data out only (Recogniser, Importer/Exporter); PenLab draws all UI | offline LaTeX, community tools |

v1 builds only the first kind, but every interface uses plain data (below), so v2 is an adapter,
not a rewrite. Dynamic feature modules: no (need Play delivery).

## Plain data types (the contract)

`Stroke` (points x/y/pressure/time in page units, tool, colour, width) · `PageRef` (notebook id,
page index, size mm) · `Selection` (strokes + items + bounds + a rendered `Bitmap`) ·
`PageItem` (type id string, bounds, `ByteArray` payload) · `Result` = items to place | text |
message | nothing.

## Extension points (v1)

1. **PageAction** — a button on the toolbar or the selection menu. `label, icon, enabledWhen(selection
   empty / not empty)`, `suspend run(Selection, PageRef): Result`. PenLab places returned items.
2. **PageItemType** — a new page object. `typeId, draw(Canvas, item), hitTest`. **Unknown types are
   kept byte-for-byte and drawn as a grey box with the plugin name**, so a notebook opens in any
   build and loses nothing.
3. **Recogniser** — `strokes or bitmap → LaTeX | text | SVG`. PenLab shows the result and places it.
4. **Importer / Exporter** — `mime types`, `import(file) → pages + items` / `export(notebook) → file`.
   PDF is the first one, written against this API inside the base to prove it.
5. **SettingsEntry** — one screen per plugin in Settings (keys stored encrypted by PenLab).
6. **Panel** (built-in only) — a side sheet with Compose content (AI chat, problem-solving panel, recordings).
7. **Stroke events** (built-in only) — `onStrokesCommitted(strokes, time)` after the pen lifts
   (audio marks, timers).

## Rules

- **Nothing on the ink path.** No plugin code runs while the pen is down; callbacks run after
  pen-up, off the main thread, with a time budget. Priority 1 (ink speed) can't be broken by a plugin.
- **File format is the base's.** Plugins store data only as `PageItem` payloads or their own file
  inside the notebook folder (`plugins/<id>/`). The base never deletes what it doesn't understand.
- **A crashing plugin is switched off,** not the app: errors are caught, the plugin is disabled,
  the user sees one message.
- **Permissions:** a plugin declares what it needs (network, microphone); the base shows it in
  Settings. The base itself needs no network.
- **Versioning:** `apiVersion` integer; the base loads plugins with the same major version.

## Not in v1

External APK loading, plugin store/discovery, plugins drawing on the live canvas, theming plugins.

## Decided

- **Samsung .sdocx import: plugin** (an Importer).
- **Handwriting search (ML Kit, on-device): base.**
- **Name: PenLab** for the public base app and repo (no existing app found by that name; "Graphite"
  is crowded in trademarks).
- **App id `dev.penlab.app`** (neutral). The first choice was `io.github.penlab.penlab`
  with a GitHub org "penlab", but that name is taken (an empty user account since 2013), so the agreed
  fallback applies. Plugin API package `dev.penlab.plugin`, external plugin intent action
  `dev.penlab.PLUGIN`. The app id can never change after the first public release (updates are
  matched by it). GitHub org: **penlab-app** (github.com/penlab-app, owned by the maintainers). The app id stays `dev.penlab.app`.
