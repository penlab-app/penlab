#!/usr/bin/env python3
"""
Automated UI/UX, geometry, and layout stress-audit driver for PenLab (dev.penlab.app).

Uses Python 3 stdlib only (`adb`, `uiautomator dump`, `logcat`, `dumpsys gfxinfo`,
and `tools/qa/tablet-cap.sh`). Never touches `dev.akram.graphite` and never calls
`adb uninstall` or `pm clear`.

Offline detector verification:
    python3 tools/qa/ui_stress_audit.py --self-test
"""

from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass, field
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import unittest
import xml.etree.ElementTree as ET


BOUNDS_RE = re.compile(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")
PENLAB_PKG = "dev.penlab.app"


@dataclass(frozen=True)
class Bounds:
    left: int
    top: int
    right: int
    bottom: int

    @property
    def width(self) -> int:
        return self.right - self.left

    @property
    def height(self) -> int:
        return self.bottom - self.top

    @property
    def area(self) -> int:
        return max(0, self.width) * max(0, self.height)

    @property
    def center(self) -> tuple[int, int]:
        return ((self.left + self.right) // 2, (self.top + self.bottom) // 2)

    def contains(self, other: "Bounds") -> bool:
        return (
            self.left <= other.left
            and self.top <= other.top
            and self.right >= other.right
            and self.bottom >= other.bottom
        )

    def intersection(self, other: "Bounds") -> tuple[int, int]:
        iw = min(self.right, other.right) - max(self.left, other.left)
        ih = min(self.bottom, other.bottom) - max(self.top, other.top)
        return (max(0, iw), max(0, ih))


@dataclass
class UiNode:
    node_id: int
    parent_id: int | None
    depth: int
    package: str
    class_name: str
    text: str
    content_desc: str
    resource_id: str
    clickable: bool
    enabled: bool
    checkable: bool
    checked: bool
    scrollable: bool
    bounds: Bounds
    descendant_labels: list[str] = field(default_factory=list)

    @property
    def effective_label(self) -> str:
        if self.content_desc.strip():
            return self.content_desc.strip()
        if self.text.strip():
            return self.text.strip()
        for label in self.descendant_labels:
            if label.strip():
                return label.strip()
        return ""


@dataclass(frozen=True)
class AuditFinding:
    screen: str
    severity: str  # ERROR, WARNING, INFO
    code: str      # ZERO_SIZE, OUT_OF_BOUNDS, OVERLAP_COLLISION, SMALL_TOUCH_TARGET, UNLABELED_CONTROL, TRUNCATED_TEXT, TOP_CHROME_SHIFT, RUNTIME_ERROR
    message: str
    details: dict[str, object] = field(default_factory=dict)


def parse_bounds(raw: str) -> Bounds | None:
    """Parses Android UIAutomator `[l,t][r,b]` bounds strings."""
    m = BOUNDS_RE.match((raw or "").strip())
    if not m:
        return None
    return Bounds(int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)))


def parse_ui_xml(xml_text: str) -> list[UiNode]:
    """Parses UIAutomator XML into flat UiNode records with descendant labels."""
    root = ET.fromstring(xml_text)
    nodes: list[UiNode] = []

    def walk(elem: ET.Element, parent_id: int | None, depth: int) -> list[str]:
        if elem.tag == "node":
            bounds = parse_bounds(elem.attrib.get("bounds", "")) or Bounds(0, 0, 0, 0)
            node_id = len(nodes)
            node = UiNode(
                node_id=node_id,
                parent_id=parent_id,
                depth=depth,
                package=elem.attrib.get("package", ""),
                class_name=elem.attrib.get("class", ""),
                text=elem.attrib.get("text", ""),
                content_desc=elem.attrib.get("content-desc", ""),
                resource_id=elem.attrib.get("resource-id", ""),
                clickable=elem.attrib.get("clickable", "false") == "true",
                enabled=elem.attrib.get("enabled", "true") == "true",
                checkable=elem.attrib.get("checkable", "false") == "true",
                checked=elem.attrib.get("checked", "false") == "true",
                scrollable=elem.attrib.get("scrollable", "false") == "true",
                bounds=bounds,
            )
            nodes.append(node)
            child_labels: list[str] = []
            for child in elem:
                child_labels.extend(walk(child, node_id, depth + 1))
            node.descendant_labels = child_labels
            own = []
            if node.content_desc.strip():
                own.append(node.content_desc.strip())
            if node.text.strip():
                own.append(node.text.strip())
            return own + child_labels
        collected: list[str] = []
        for child in elem:
            collected.extend(walk(child, parent_id, depth))
        return collected

    walk(root, None, 0)
    return nodes


def is_ancestor(nodes_by_id: dict[int, UiNode], maybe_ancestor_id: int, node_id: int) -> bool:
    cur = nodes_by_id[node_id].parent_id
    while cur is not None:
        if cur == maybe_ancestor_id:
            return True
        cur = nodes_by_id[cur].parent_id
    return False


def min_touch_target_px(dpi: int, min_dp: float = 44.0) -> int:
    """Minimum recommended touch-target extent in device pixels (`44dp` threshold)."""
    return max(1, int(min_dp * dpi / 160.0))


def audit_nodes(
    nodes: list[UiNode],
    screen_w: int,
    screen_h: int,
    dpi: int,
    screen_name: str,
    edge_tolerance_px: int = 2,
) -> list[AuditFinding]:
    """Runs pure geometry, touch-target, overlap, and labeling checks on UI nodes."""
    findings: list[AuditFinding] = []
    nodes_by_id = {n.node_id: n for n in nodes}
    target_px = min_touch_target_px(dpi, 44.0)

    app_interactive = [
        n for n in nodes
        if n.package == PENLAB_PKG and (n.clickable or n.checkable) and n.enabled
    ]
    zero_ids: set[int] = set()

    for n in app_interactive:
        b = n.bounds
        label = n.effective_label or f"node#{n.node_id}"
        if b.width <= 0 or b.height <= 0:
            zero_ids.add(n.node_id)
            findings.append(
                AuditFinding(
                    screen=screen_name,
                    severity="ERROR",
                    code="ZERO_SIZE",
                    message=f"Zero/negative bounds on clickable control '{label}': {b}",
                    details={"label": label, "bounds": asdict(b)},
                )
            )
            continue

        if (
            b.left < -edge_tolerance_px
            or b.top < -edge_tolerance_px
            or b.right > screen_w + edge_tolerance_px
            or b.bottom > screen_h + edge_tolerance_px
        ):
            findings.append(
                AuditFinding(
                    screen=screen_name,
                    severity="ERROR",
                    code="OUT_OF_BOUNDS",
                    message=f"Control '{label}' extends outside {screen_w}x{screen_h}: {b}",
                    details={"label": label, "bounds": asdict(b), "screen": [screen_w, screen_h]},
                )
            )

        if n.clickable and (b.width < target_px or b.height < target_px):
            findings.append(
                AuditFinding(
                    screen=screen_name,
                    severity="WARNING",
                    code="SMALL_TOUCH_TARGET",
                    message=(
                        f"Touch target '{label}' is {b.width}x{b.height}px "
                        f"(< {target_px}px / 44dp @ {dpi}dpi)"
                    ),
                    details={"label": label, "bounds": asdict(b), "min_px": target_px},
                )
            )

        if n.clickable and not n.effective_label:
            findings.append(
                AuditFinding(
                    screen=screen_name,
                    severity="WARNING",
                    code="UNLABELED_CONTROL",
                    message=f"Clickable control at {b} lacks text/content-desc",
                    details={"bounds": asdict(b), "class": n.class_name, "resource_id": n.resource_id},
                )
            )

    # Pairwise clickable overlap check (unrelated siblings only).
    clickable = [n for n in app_interactive if n.clickable and n.node_id not in zero_ids]
    for i, a in enumerate(clickable):
        for b in clickable[i + 1 :]:
            if is_ancestor(nodes_by_id, a.node_id, b.node_id) or is_ancestor(nodes_by_id, b.node_id, a.node_id):
                continue
            iw, ih = a.bounds.intersection(b.bounds)
            if iw <= 6 or ih <= 6:
                continue
            overlap = iw * ih
            min_area = min(a.bounds.area, b.bounds.area)
            if min_area > 0 and overlap > min_area * 0.25:
                findings.append(
                    AuditFinding(
                        screen=screen_name,
                        severity="ERROR",
                        code="OVERLAP_COLLISION",
                        message=(
                            f"Overlapping controls '{a.effective_label or a.node_id}' {a.bounds} "
                            f"and '{b.effective_label or b.node_id}' {b.bounds} ({iw}x{ih}px)"
                        ),
                        details={
                            "first": a.effective_label or a.node_id,
                            "second": b.effective_label or b.node_id,
                            "intersection": [iw, ih],
                        },
                    )
                )

    for n in nodes:
        if n.package != PENLAB_PKG:
            continue
        t = n.text.strip()
        if t and (t.endswith("\u2026") or t.endswith("...")) and len(t) <= 18:
            findings.append(
                AuditFinding(
                    screen=screen_name,
                    severity="WARNING",
                    code="TRUNCATED_TEXT",
                    message=f"Short label appears truncated: '{t}' at {n.bounds}",
                    details={"text": t, "bounds": asdict(n.bounds)},
                )
            )

    return findings


def find_node(
    nodes: list[UiNode],
    *,
    content_desc: str | None = None,
    content_desc_contains: str | None = None,
    text: str | None = None,
    text_contains: str | None = None,
    class_name: str | None = None,
    clickable_only: bool = False,
) -> UiNode | None:
    """Returns the first node matching exact or substring filters."""
    for n in nodes:
        if n.package and n.package != PENLAB_PKG:
            continue
        if clickable_only and not n.clickable:
            continue
        if class_name is not None and n.class_name != class_name:
            continue
        if content_desc is not None and n.content_desc != content_desc:
            continue
        if content_desc_contains is not None and content_desc_contains not in n.content_desc:
            continue
        if text is not None and n.text != text:
            continue
        if text_contains is not None and text_contains not in n.text:
            continue
        return n
    return None


def parse_display_size_from_dumpsys(dumpsys_text: str) -> tuple[int, int] | None:
    """Extracts current display dimensions from `dumpsys window displays` (`cur=WxH`)."""
    m = re.search(r"\bcur=(\d+)x(\d+)\b", dumpsys_text or "")
    if not m:
        return None
    w, h = int(m.group(1)), int(m.group(2))
    return (w, h) if w > 0 and h > 0 else None


def resolve_screen_size(nodes: list[UiNode], fallback: tuple[int, int]) -> tuple[int, int]:
    """
    Resolves active display size from full-screen root `<node>` bounds (`[0,0][W,H]`)
    so portrait physical `wm size` never triggers landscape false positives.
    """
    candidates = [
        (n.bounds.width, n.bounds.height)
        for n in nodes
        if n.depth == 0 and n.bounds.left == 0 and n.bounds.top == 0 and n.bounds.width >= 600 and n.bounds.height >= 400
    ]
    if candidates:
        return max(candidates, key=lambda wh: wh[0] * wh[1])
    return fallback


def check_top_chrome_symmetry(
    mode_to_nodes: dict[str, list[UiNode]],
    tolerance_px: int = 2,
) -> list[AuditFinding]:
    """
    Verifies top-row chrome pills (`Back to library` on left, `Full screen` on right)
    keep identical horizontal anchor coordinates across Corner/Top/Bottom/Left/Right/Float.
    """
    findings: list[AuditFinding] = []
    left_xs: dict[str, int] = {}
    right_xs: dict[str, int] = {}
    for mode, nodes in mode_to_nodes.items():
        back_btn = find_node(nodes, content_desc="Back to library")
        fs_btn = find_node(nodes, content_desc="Full screen")
        if back_btn:
            left_xs[mode] = back_btn.bounds.left
        if fs_btn:
            right_xs[mode] = fs_btn.bounds.right

    if len(left_xs) >= 2:
        span = max(left_xs.values()) - min(left_xs.values())
        if span > tolerance_px:
            findings.append(
                AuditFinding(
                    screen="top_chrome_symmetry",
                    severity="ERROR",
                    code="TOP_CHROME_SHIFT",
                    message=f"FileChromePill left edge shifted by {span}px across dock modes: {left_xs}",
                    details={"left_edges": left_xs, "span_px": span},
                )
            )
    if len(right_xs) >= 2:
        span = max(right_xs.values()) - min(right_xs.values())
        if span > tolerance_px:
            findings.append(
                AuditFinding(
                    screen="top_chrome_symmetry",
                    severity="ERROR",
                    code="TOP_CHROME_SHIFT",
                    message=f"PageConfigChromePill right edge shifted by {span}px across dock modes: {right_xs}",
                    details={"right_edges": right_xs, "span_px": span},
                )
            )
    return findings


def verify_collapsed_toolbar(nodes: list[UiNode]) -> list[AuditFinding]:
    """Checks that collapsed toolbar exposes Expand toolbar and hides non-pen/eraser utilities."""
    findings: list[AuditFinding] = []
    if not find_node(nodes, content_desc="Expand toolbar"):
        findings.append(
            AuditFinding(
                screen="collapsed_toolbar",
                severity="ERROR",
                code="COLLAPSE_MISSING_EXPAND",
                message="Collapsed toolbar did not show 'Expand toolbar' toggle",
            )
        )
    for forbidden in ("Undo", "Redo", "Paper style", "Tools: ruler, protractor, shape snap", "More tools", "Clear page", "Layers"):
        if find_node(nodes, content_desc=forbidden):
            findings.append(
                AuditFinding(
                    screen="collapsed_toolbar",
                    severity="ERROR",
                    code="COLLAPSE_EXTRA_ITEM",
                    message=f"Collapsed toolbar unexpectedly still shows '{forbidden}'",
                    details={"item": forbidden},
                )
            )
    return findings


def detect_adb_serial(explicit_serial: str | None, adb_bin: str = "adb") -> str:
    if explicit_serial:
        return explicit_serial
    env_serial = os.environ.get("PENLAB_ADB_SERIAL", "").strip()
    if env_serial:
        return env_serial
    out = subprocess.check_output([adb_bin, "devices"], stdin=subprocess.DEVNULL, text=True)
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            return parts[0]
    raise RuntimeError("No connected ADB device found (set --serial or PENLAB_ADB_SERIAL).")


class LiveAuditor:
    """Drives PenLab over ADB cleanly and captures screenshots + XML dumps + runtime telemetry."""

    def __init__(self, serial: str, output_dir: Path, dpi_override: int | None = None, adb_bin: str = "adb") -> None:
        self.serial = serial
        self.output_dir = output_dir
        self.screenshots_dir = output_dir / "screenshots"
        self.dumps_dir = output_dir / "dumps"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        self.dumps_dir.mkdir(parents=True, exist_ok=True)
        self.adb_bin = adb_bin
        self.cap_script = Path(__file__).resolve().parent / "tablet-cap.sh"
        self.findings: list[AuditFinding] = []
        self.screen_summaries: list[dict[str, object]] = []
        self.screen_w, self.screen_h = self._query_screen_size()
        self.dpi = dpi_override or self._query_density()
        self.step_counter = 0
        self.created_notebook_uuids: list[str] = []

    def _adb(self, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [self.adb_bin, "-s", self.serial, *args],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=check,
        )

    def _query_screen_size(self) -> tuple[int, int]:
        disp_out = self._adb("shell", "dumpsys", "window", "displays", check=False).stdout
        parsed = parse_display_size_from_dumpsys(disp_out)
        if parsed:
            return parsed
        out = self._adb("shell", "wm", "size", check=False).stdout
        m = re.search(r"Override size:\s*(\d+)x(\d+)", out) or re.search(r"Physical size:\s*(\d+)x(\d+)", out)
        return (int(m.group(1)), int(m.group(2))) if m else (2560, 1600)

    def _query_density(self) -> int:
        out = self._adb("shell", "wm", "density", check=False).stdout
        m = re.search(r"Override density:\s*(\d+)", out) or re.search(r"Physical density:\s*(\d+)", out)
        return int(m.group(1)) if m else 340

    def _is_penlab_foreground(self) -> bool:
        out = self._adb("shell", "dumpsys window displays | grep 'mCurrentFocus='", check=False).stdout
        focus_lines = [ln for ln in out.splitlines() if "mCurrentFocus=Window{" in ln]
        if not focus_lines:
            return False
        if any(PENLAB_PKG not in ln and "u0 Pop-up window}" not in ln for ln in focus_lines):
            return False
        resumed = self._adb("shell", 'dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity"', check=False).stdout
        active_records = [ln for ln in resumed.splitlines() if "ActivityRecord{" in ln]
        return bool(active_records) and all(PENLAB_PKG in ln for ln in active_records)

    def ensure_penlab_foreground(self) -> None:
        if not self._is_penlab_foreground():
            self._adb("shell", "am", "start", "-n", f"{PENLAB_PKG}/.MainActivity")
            time.sleep(1.8)
            if not self._is_penlab_foreground():
                raise RuntimeError(f"Privacy guard: {PENLAB_PKG} is not the active focused/resumed app")

    def _dismiss_ime_if_open(self) -> None:
        ime_out = self._adb("shell", "dumpsys input_method | grep 'mInputShown='", check=False).stdout
        if "mInputShown=true" in ime_out:
            self._adb("shell", "input", "keyevent", "4", check=False)
            time.sleep(0.4)

    def _list_library_uuids(self) -> set[str]:
        out = self._adb("shell", "ls -1 /sdcard/Documents/PenLab 2>/dev/null || true", check=False).stdout
        return {ln.strip() for ln in out.splitlines() if ln.strip() and not ln.strip().startswith(".")}

    def dump_ui(self, settle_s: float = 0.5) -> tuple[list[UiNode], str]:
        time.sleep(settle_s)
        self.ensure_penlab_foreground()
        self._adb("shell", "uiautomator", "dump", "/sdcard/penlab_audit_ui.xml")
        xml_text = self._adb("exec-out", "cat", "/sdcard/penlab_audit_ui.xml").stdout
        return parse_ui_xml(xml_text), xml_text

    def inspect_screen(self, name: str, capture_shot: bool = True) -> list[UiNode]:
        self.step_counter += 1
        slug = f"{self.step_counter:02d}_{re.sub(r'[^a-zA-Z0-9_-]+', '_', name)}"
        nodes, xml_text = self.dump_ui()
        self.screen_w, self.screen_h = resolve_screen_size(nodes, (self.screen_w, self.screen_h))
        (self.dumps_dir / f"{slug}.xml").write_text(xml_text, encoding="utf-8")
        shot_path = None
        if capture_shot and self.cap_script.exists():
            shot_file = self.screenshots_dir / f"{slug}.jpg"
            env = os.environ.copy()
            env["PENLAB_ADB_SERIAL"] = self.serial
            env["ADB_BIN"] = self.adb_bin
            subprocess.run([str(self.cap_script), str(shot_file)], env=env, check=False)
            if shot_file.exists():
                shot_path = str(shot_file)
        hits = audit_nodes(nodes, self.screen_w, self.screen_h, self.dpi, name)
        self.findings.extend(hits)
        self.screen_summaries.append(
            {
                "screen": name,
                "nodes": len(nodes),
                "errors": sum(1 for h in hits if h.severity == "ERROR"),
                "warnings": sum(1 for h in hits if h.severity == "WARNING"),
                "screenshot": shot_path,
            }
        )
        return nodes

    def tap_node(self, nodes: list[UiNode], **filters: object) -> bool:
        node = find_node(nodes, **filters)  # type: ignore[arg-type]
        if not node:
            self.findings.append(
                AuditFinding(
                    screen="navigation",
                    severity="WARNING",
                    code="NODE_NOT_FOUND",
                    message=f"Could not find UI node matching {filters}",
                )
            )
            return False
        cx, cy = node.bounds.center
        self._adb("shell", "input", "tap", str(cx), str(cy))
        return True

    def clear_logs_and_gfxinfo(self) -> None:
        self._adb("logcat", "-c", check=False)
        self._adb("shell", "dumpsys", "gfxinfo", PENLAB_PKG, "reset", check=False)

    def collect_runtime_telemetry(self) -> dict[str, object]:
        log_out = self._adb("logcat", "-d", "-v", "brief", "AndroidRuntime:E", "Choreographer:W", "*:S", check=False).stdout
        fatal_lines = [ln for ln in log_out.splitlines() if PENLAB_PKG in ln or "FATAL EXCEPTION" in ln]
        skipped_frames = [ln for ln in log_out.splitlines() if "Skipped " in ln]
        for ln in fatal_lines:
            self.findings.append(
                AuditFinding(
                    screen="runtime",
                    severity="ERROR",
                    code="RUNTIME_ERROR",
                    message=ln,
                )
            )
        gfx = self._adb("shell", "dumpsys", "gfxinfo", PENLAB_PKG, check=False).stdout
        gfx_lines = [
            ln.strip()
            for ln in gfx.splitlines()
            if any(k in ln for k in ("Total frames rendered", "Janky frames:", "50th percentile", "90th percentile", "95th percentile", "99th percentile"))
        ]
        return {
            "fatal_logcat_lines": fatal_lines,
            "choreographer_skipped_count": len(skipped_frames),
            "gfxinfo_summary": gfx_lines,
        }

    def run(self) -> dict[str, object]:
        self.clear_logs_and_gfxinfo()
        self.ensure_penlab_foreground()

        # Snapshot initial Settings before changing anything, and always restore in finally.
        saved_theme = "System"
        saved_accent = "Blueprint"
        saved_dock = "Corner"
        temp_created = False

        try:
            lib_nodes = self.inspect_screen("library_home")

            # Settings audit from Library
            if self.tap_node(lib_nodes, content_desc="Settings"):
                s_nodes = self.inspect_screen("settings_top")
                for label in ("System", "Light", "Dark"):
                    n = find_node(s_nodes, text=label)
                    if n and n.checked:
                        saved_theme = label
                for label in ("Blueprint", "Copper"):
                    n = find_node(s_nodes, content_desc_contains=f"{label} accent look")
                    if n and n.checked:
                        saved_accent = label
                dock_node = self._scroll_settings_to_find(text="Dock position")
                if dock_node:
                    dock_screen, _ = self.dump_ui(0.3)
                    for label in ("Top", "Bottom", "Left", "Right", "Float", "Corner"):
                        n = find_node(dock_screen, text=label)
                        if n and n.checked:
                            saved_dock = label
                cust_node = self._scroll_settings_to_find(text="Customise toolbar items")
                if cust_node:
                    cx, cy = cust_node.bounds.center
                    self._adb("shell", "input", "tap", str(cx), str(cy))
                    self.inspect_screen("settings_toolbar_customizer")
                self._adb("shell", "input", "keyevent", "4")
                time.sleep(0.4)

            # New notebook dialog validation & temporary "QA Audit" creation (track exact UUID)
            lib_nodes = self.inspect_screen("library_before_new", capture_shot=False)
            before_uuids = self._list_library_uuids()
            if self.tap_node(lib_nodes, text="New notebook"):
                dlg = self.inspect_screen("new_notebook_dialog")
                title_box = find_node(dlg, class_name="android.widget.EditText")
                if title_box:
                    cx, cy = title_box.bounds.center
                    self._adb("shell", "input", "tap", str(cx), str(cy))
                    self._adb("shell", "input", "keyevent", "123")
                    for _ in range(32):
                        self._adb("shell", "input", "keyevent", "67")
                    self._adb("shell", "input", "text", "QA%sAudit")
                    self._dismiss_ime_if_open()
                dlg2, _ = self.dump_ui()
                if self.tap_node(dlg2, text="Create"):
                    temp_created = True
                    time.sleep(1.0)
                    after_uuids = self._list_library_uuids()
                    self.created_notebook_uuids = sorted(after_uuids - before_uuids)

            # Canvas audit across dock positions + top chrome symmetry
            dock_dumps: dict[str, list[UiNode]] = {}
            for dock_label in ("Corner", "Top", "Bottom", "Left", "Right", "Float"):
                self._switch_dock_from_canvas(dock_label)
                dock_dumps[dock_label] = self.inspect_screen(f"canvas_dock_{dock_label.lower()}")
                if dock_label in ("Left", "Float"):
                    nodes_here = dock_dumps[dock_label]
                    if self.tap_node(nodes_here, content_desc="Collapse toolbar"):
                        collapsed_nodes = self.inspect_screen(f"canvas_{dock_label.lower()}_collapsed")
                        self.findings.extend(verify_collapsed_toolbar(collapsed_nodes))
                        self.tap_node(collapsed_nodes, content_desc="Expand toolbar")
                        time.sleep(0.4)

            self.findings.extend(check_top_chrome_symmetry(dock_dumps))

            # Popover & mode sweep on canvas
            canvas_nodes = self.inspect_screen("canvas_popovers_base", capture_shot=False)
            for label in ("Tools: ruler, protractor, shape snap", "More tools", "File options"):
                if self.tap_node(canvas_nodes, content_desc=label):
                    self.inspect_screen(f"popover_{label.split(':')[0].lower()}")
                    self._adb("shell", "input", "keyevent", "4")
                    time.sleep(0.4)

            if self.tap_node(canvas_nodes, content_desc="Full screen"):
                fs_nodes = self.inspect_screen("canvas_fullscreen")
                self.tap_node(fs_nodes, content_desc="Leave full screen")
                time.sleep(0.4)

            # Multi-theme sweep
            self._set_theme_from_canvas("Light", "Copper", "Left")
            self.inspect_screen("theme_light_copper_left")
            self._set_theme_from_canvas("Dark", "Blueprint", "Float")
            self.inspect_screen("theme_dark_blueprint_float")

        finally:
            try:
                self._set_theme_from_canvas(saved_theme, saved_accent, saved_dock)
            except Exception:
                pass
            if temp_created:
                self._cleanup_temp_notebook()

        telemetry = self.collect_runtime_telemetry()
        return {
            "device": self.serial,
            "screen": {"width": self.screen_w, "height": self.screen_h, "dpi": self.dpi},
            "screens_audited": self.screen_summaries,
            "finding_counts": {
                "ERROR": sum(1 for f in self.findings if f.severity == "ERROR"),
                "WARNING": sum(1 for f in self.findings if f.severity == "WARNING"),
                "INFO": sum(1 for f in self.findings if f.severity == "INFO"),
            },
            "findings": [asdict(f) for f in self.findings],
            "telemetry": telemetry,
        }

    def _scroll_settings_to_find(self, max_swipes: int = 4, **filters: object) -> UiNode | None:
        for step in range(max_swipes + 1):
            nodes, _ = self.dump_ui(0.3)
            hit = find_node(nodes, **filters)  # type: ignore[arg-type]
            if hit:
                return hit
            if step < max_swipes:
                cx = self.screen_w // 2
                y1 = int(self.screen_h * 0.65)
                y2 = int(self.screen_h * 0.30)
                self._adb("shell", "input", "swipe", str(cx), str(y1), str(cx), str(y2), "280")
        return None

    def _switch_dock_from_canvas(self, dock_label: str) -> None:
        nodes, _ = self.dump_ui(0.3)
        if not self.tap_node(nodes, content_desc="File options"):
            return
        menu, _ = self.dump_ui(0.4)
        if not self.tap_node(menu, text="Settings"):
            return
        hit = self._scroll_settings_to_find(text=dock_label)
        if hit:
            cx, cy = hit.bounds.center
            self._adb("shell", "input", "tap", str(cx), str(cy))
        self._adb("shell", "input", "keyevent", "4")
        time.sleep(0.4)

    def _set_theme_from_canvas(self, theme: str, accent: str, dock_label: str) -> None:
        nodes, _ = self.dump_ui(0.3)
        if not self.tap_node(nodes, content_desc="File options"):
            return
        menu, _ = self.dump_ui(0.4)
        if not self.tap_node(menu, text="Settings"):
            return
        settings, _ = self.dump_ui(0.5)
        self.tap_node(settings, text=theme)
        self.tap_node(settings, content_desc_contains=f"{accent} accent look")
        hit = self._scroll_settings_to_find(text=dock_label)
        if hit:
            cx, cy = hit.bounds.center
            self._adb("shell", "input", "tap", str(cx), str(cy))
        self._adb("shell", "input", "keyevent", "4")
        time.sleep(0.4)

    def _cleanup_temp_notebook(self) -> None:
        nodes, _ = self.dump_ui(0.3)
        if find_node(nodes, content_desc="Back to library"):
            self.tap_node(nodes, content_desc="Back to library")
            time.sleep(0.8)
        # First delete the temporary "QA Audit" notebook card cleanly via UI if visible.
        lib_nodes, _ = self.dump_ui(0.6)
        qa_card = find_node(lib_nodes, text="QA Audit")
        if qa_card:
            cx, cy = qa_card.bounds.center
            self._adb("shell", "input", "swipe", str(cx), str(cy), str(cx), str(cy), "900")
            menu, _ = self.dump_ui(0.5)
            self.tap_node(menu, text="Delete")
            time.sleep(0.5)
        # Remove ONLY the exact tracked UUID directories created during this audit run
        # from both Library and .trash/, never touching any pre-existing user notebooks.
        for uuid in self.created_notebook_uuids:
            if not re.fullmatch(r"[A-Za-z0-9_.-]+", uuid):
                continue
            self._adb("shell", "rm", "-rf", f"/sdcard/Documents/PenLab/{uuid}", check=False)
            self._adb("shell", "rm", "-rf", f"/sdcard/Documents/PenLab/.trash/{uuid}", check=False)


def write_reports(report: dict[str, object], output_dir: Path) -> None:
    json_path = output_dir / "audit-report.json"
    md_path = output_dir / "audit-report.md"
    json_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    counts = report["finding_counts"]
    lines = [
        "# PenLab UI Stress-Audit Report",
        "",
        f"- **Device:** `{report['device']}`",
        f"- **Display:** `{report['screen']['width']}x{report['screen']['height']}` @ `{report['screen']['dpi']} dpi`",
        f"- **Summary:** `{counts['ERROR']} errors`, `{counts['WARNING']} warnings`, `{counts['INFO']} info`",
        "",
        "## Screens Audited",
        "| Screen | Nodes | Errors | Warnings | Screenshot |",
        "|---|---:|---:|---:|---|",
    ]
    for s in report["screens_audited"]:  # type: ignore[attr-defined]
        shot = s["screenshot"] or "-"
        lines.append(f"| `{s['screen']}` | {s['nodes']} | {s['errors']} | {s['warnings']} | `{shot}` |")

    lines.extend(["", "## Findings"])
    findings = report["findings"]
    if not findings:
        lines.append("- None! Zero geometry, overlap, target-size, labeling, symmetry, or runtime issues detected.")
    else:
        for f in findings:  # type: ignore[attr-defined]
            lines.append(f"- **[{f['severity']}] `{f['code']}` ({f['screen']}):** {f['message']}")

    tel = report["telemetry"]
    lines.extend([
        "",
        "## Runtime Telemetry",
        f"- **Choreographer skipped-frame messages:** `{tel['choreographer_skipped_count']}`",
        f"- **Fatal logcat lines (`dev.penlab.app`):** `{len(tel['fatal_logcat_lines'])}`",
    ])
    for g in tel["gfxinfo_summary"]:  # type: ignore[attr-defined]
        lines.append(f"- `gfxinfo`: `{g}`")

    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


class AuditSelfTest(unittest.TestCase):
    def _wrap_xml(self, body: str) -> str:
        return f'<hierarchy rotation="0">{body}</hierarchy>'

    def test_detects_out_of_bounds_and_zero_size(self) -> None:
        xml = self._wrap_xml(
            f'<node package="{PENLAB_PKG}" class="android.view.View" text="" content-desc="Offscreen" '
            'clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[1200,10][1320,120]" />'
            f'<node package="{PENLAB_PKG}" class="android.view.View" text="" content-desc="Zero" '
            'clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[50,50][50,100]" />'
        )
        hits = audit_nodes(parse_ui_xml(xml), 1280, 800, 160, "test")
        codes = {h.code for h in hits}
        self.assertIn("OUT_OF_BOUNDS", codes)
        self.assertIn("ZERO_SIZE", codes)

    def test_detects_sibling_overlap_but_ignores_parent_child(self) -> None:
        xml = self._wrap_xml(
            f'<node package="{PENLAB_PKG}" class="android.view.ViewGroup" text="" content-desc="Container" '
            'clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[100,100][300,200]">'
            f'  <node package="{PENLAB_PKG}" class="android.view.View" text="Inner" content-desc="" '
            '  clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            '  bounds="[110,110][200,190]" />'
            '</node>'
            f'<node package="{PENLAB_PKG}" class="android.view.View" text="Sibling" content-desc="" '
            'clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[140,110][280,190]" />'
        )
        hits = audit_nodes(parse_ui_xml(xml), 1280, 800, 160, "overlap")
        overlaps = [h for h in hits if h.code == "OVERLAP_COLLISION"]
        self.assertTrue(any("Sibling" in h.message for h in overlaps))
        self.assertFalse(any("Container" in h.message and "Inner" in h.message for h in overlaps))

    def test_detects_small_touch_target_and_unlabeled_control(self) -> None:
        # At 340 dpi, 44dp = 93px; 80x80 fails while 102x102 (~48dp) passes.
        xml = self._wrap_xml(
            f'<node package="{PENLAB_PKG}" class="android.view.View" text="" content-desc="" '
            'clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[100,100][180,180]" />'
            f'<node package="{PENLAB_PKG}" class="android.view.View" text="OK" content-desc="" '
            'clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[300,100][402,202]" />'
        )
        hits = audit_nodes(parse_ui_xml(xml), 2560, 1600, 340, "density")
        codes = {h.code for h in hits}
        self.assertIn("SMALL_TOUCH_TARGET", codes)
        self.assertIn("UNLABELED_CONTROL", codes)
        self.assertEqual(1, sum(1 for h in hits if h.code == "SMALL_TOUCH_TARGET"))

    def test_detects_top_chrome_horizontal_shift(self) -> None:
        corner = parse_ui_xml(self._wrap_xml(
            f'<node package="{PENLAB_PKG}" class="android.view.View" text="" content-desc="Back to library" '
            'clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[24,48][120,144]" />'
        ))
        left_shifted = parse_ui_xml(self._wrap_xml(
            f'<node package="{PENLAB_PKG}" class="android.view.View" text="" content-desc="Back to library" '
            'clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[168,48][264,144]" />'
        ))
        left_symmetric = parse_ui_xml(self._wrap_xml(
            f'<node package="{PENLAB_PKG}" class="android.view.View" text="" content-desc="Back to library" '
            'clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[24,48][120,144]" />'
        ))
        self.assertEqual(1, len(check_top_chrome_symmetry({"Corner": corner, "Left": left_shifted})))
        self.assertEqual(0, len(check_top_chrome_symmetry({"Corner": corner, "Left": left_symmetric})))

    def test_resolve_screen_size_uses_landscape_root_bounds(self) -> None:
        xml = self._wrap_xml(
            f'<node package="{PENLAB_PKG}" class="android.widget.FrameLayout" text="" content-desc="" '
            'clickable="false" enabled="true" checkable="false" checked="false" scrollable="false" '
            'bounds="[0,0][2560,1600]">'
            f'  <node package="{PENLAB_PKG}" class="android.view.View" text="RightButton" content-desc="" '
            '  clickable="true" enabled="true" checkable="false" checked="false" scrollable="false" '
            '  bounds="[2300,48][2460,152]" />'
            '</node>'
        )
        nodes = parse_ui_xml(xml)
        w, h = resolve_screen_size(nodes, (1600, 2560))
        self.assertEqual((2560, 1600), (w, h))
        hits = audit_nodes(nodes, w, h, 340, "landscape_root")
        self.assertFalse(any(hit.code == "OUT_OF_BOUNDS" for hit in hits))

    def test_parse_display_size_from_dumpsys(self) -> None:
        sample = "  mDisplayId=0 init=1600x2560 340dpi cur=2560x1600 app=2560x1600 rng=1600x1600-2560x2560"
        self.assertEqual((2560, 1600), parse_display_size_from_dumpsys(sample))
        self.assertIsNone(parse_display_size_from_dumpsys("unrelated output"))


def run_self_test() -> int:
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(AuditSelfTest)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    return 0 if result.wasSuccessful() else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="PenLab automated UI/UX & layout stress audit")
    parser.add_argument("--self-test", action="store_true", help="Run offline pure-Python detector self-tests")
    parser.add_argument("--serial", default=None, help="ADB device serial (or PENLAB_ADB_SERIAL)")
    parser.add_argument("--output-dir", default="build/qa-audit", help="Directory for reports and screenshots")
    parser.add_argument("--dpi", type=int, default=None, help="Override display DPI for touch-target checks")
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    serial = detect_adb_serial(args.serial)
    out_dir = Path(args.output_dir)
    auditor = LiveAuditor(serial=serial, output_dir=out_dir, dpi_override=args.dpi)
    report = auditor.run()
    write_reports(report, out_dir)
    counts = report["finding_counts"]
    print(f"Audit complete: {counts['ERROR']} errors, {counts['WARNING']} warnings -> {out_dir}")
    return 1 if counts["ERROR"] > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
