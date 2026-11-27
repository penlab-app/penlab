# PenLab Tablet QA & UI Stress-Audit Tools

Self-contained helpers for installing, capturing, and geometry-auditing `dev.penlab.app` on Android tablets over ADB.

## Safety Rules (Mandatory)
- Only ever interact with `dev.penlab.app`. Never launch, inspect, kill, clear, or uninstall `dev.akram.graphite`.
- Never run `adb uninstall dev.penlab.app` or `adb shell pm clear dev.penlab.app`. Always use in-place `adb install -r` via `tablet-install.sh`.
- `tablet-cap.sh` aborts unless `dev.penlab.app` is the focused window across all displays.

## Files
- `PenLabCap.java` / `penlab-cap.jar`: On-device `app_process` JPEG downscaler (`1280px` default width) so ADB screenshot pulls stay lightweight (~100 KB).
- `tablet-cap.sh`: Focus-guarded screenshot helper (`PENLAB_ADB_SERIAL` supported, auto-pushes `penlab-cap.jar` to `/data/local/tmp/penlab-cap.jar` if absent).
- `tablet-install.sh`: Verifies `dev.penlab.app` package badging and certificate via `aapt2` + `apksigner`, then installs in place via `adb install -r`.
- `ui_stress_audit.py`: Pure Python 3 stdlib UI/UX stress driver (`uiautomator dump`, `logcat`, `gfxinfo`, and `tablet-cap.sh`).

## Quick Usage

1. **Offline self-test (no device required):**
   ```bash
   python3 -m py_compile tools/qa/ui_stress_audit.py
   python3 tools/qa/ui_stress_audit.py --self-test
   ```

2. **Install a built APK safely:**
   ```bash
   PENLAB_ADB_SERIAL=<serial> ./tools/qa/tablet-install.sh app/build/outputs/apk/release/app-release.apk
   ```

3. **Capture a focus-guarded screenshot:**
   ```bash
   PENLAB_ADB_SERIAL=<serial> ./tools/qa/tablet-cap.sh /tmp/penlab_screen.jpg
   ```

4. **Run the live UI stress audit:**
   ```bash
   python3 tools/qa/ui_stress_audit.py --serial <serial> --output-dir /tmp/penlab-qa-audit
   ```
   Outputs:
   - `/tmp/penlab-qa-audit/audit-report.md` (human-readable summary + defect list)
   - `/tmp/penlab-qa-audit/audit-report.json` (structured counts, bounds, and telemetry)
   - `/tmp/penlab-qa-audit/screenshots/*.jpg` and `dumps/*.xml`

## Defect Codes Reported
- `ZERO_SIZE` / `OUT_OF_BOUNDS`: Clickable controls with zero area or extending beyond screen bounds.
- `OVERLAP_COLLISION`: Unrelated clickable controls overlapping >25% of the smaller target.
- `SMALL_TOUCH_TARGET`: Clickable targets smaller than `44dp` scaled to display DPI.
- `UNLABELED_CONTROL`: Clickable controls lacking both `text` and `content-desc` (including descendants).
- `TOP_CHROME_SHIFT`: Horizontal shift >2px on `FileChromePill` or `PageConfigChromePill` across dock modes (`Corner`, `Top`, `Bottom`, `Left`, `Right`, `Float`).
- `RUNTIME_ERROR`: Fatal `logcat` errors (`dev.penlab.app`).
