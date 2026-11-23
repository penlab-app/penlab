# PenLab

A fast, simple handwriting notebook for Android tablets. It is built around one idea: the pen
should feel instant, and everything else should stay out of its way.

- **Pen and eraser:** pressure-sensitive pens, a highlighter, a whole-stroke eraser and an area
  eraser. Fingers only pan and zoom; two- and three-finger taps undo and redo.
- **Pages:** A4 notebooks (true size) or endless canvases, with blank, lined, dotted, grid,
  engineering, graph, Cornell, isometric and problem-sheet paper. Page grid with bookmarks,
  insert and delete.
- **PDF:** import a PDF and write on it; export any notebook as a PDF with vector ink.
- **Handwriting search:** on-device recognition (ML Kit Digital Ink), no account and no network
  use after the one-time model download.
- **Layers, shapes and lasso:** togglable layers, hold-to-snap shapes, fill, rulers, and a lasso
  that selects, moves, resizes, rotates, copies and deletes.
- **Photos and text boxes** placed on the page, a dark page that stays ink-on-white in exports,
  and a library with folders and a trash.

## Build

Requirements: JDK 17 or newer and the Android SDK with platform 36 (set `sdk.dir` in
`local.properties` or `ANDROID_HOME`). The Gradle wrapper is included.

```sh
./gradlew assembleRelease      # app/build/outputs/apk/release/
./gradlew testDebugUnitTest    # unit tests
./gradlew lintRelease          # Android lint
```

The release build is signed with the debug key so it installs without a keystore. Use your own
signing configuration before distributing it.

## Plugins

PenLab is the open-source base. Optional features (importers, exporters, recognisers, panels)
are planned as plugins on top of a small, versioned API. The draft is in
[docs/plugin-api-spec.md](docs/plugin-api-spec.md).

## Design

UI rules and the reasons behind them are in [DESIGN.md](DESIGN.md).

## Licence

PenLab is free software under the [GNU General Public License v3.0](LICENSE). Third-party
notices (fonts) are in [licenses/](licenses/).
