#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 4 ]]; then
  echo "Usage: $0 <output.jpg> [max_width=1280] [quality=78] [display_id]" >&2
  exit 1
fi

OUT="$1"
MAX_W="${2:-1280}"
QUALITY="${3:-78}"
DISPLAY_ID="${4:-}"

if ! [[ "$MAX_W" =~ ^[0-9]+$ ]] || [[ "$MAX_W" -le 0 ]]; then
  echo "Error: max_width must be a positive integer (got: $MAX_W)" >&2
  exit 1
fi

if ! [[ "$QUALITY" =~ ^[0-9]+$ ]] || [[ "$QUALITY" -lt 0 || "$QUALITY" -gt 100 ]]; then
  echo "Error: quality must be an integer 0..100 (got: $QUALITY)" >&2
  exit 1
fi

if [[ -n "$DISPLAY_ID" ]] && ! [[ "$DISPLAY_ID" =~ ^[0-9]+$ ]]; then
  echo "Error: display_id must be a non-negative integer (got: $DISPLAY_ID)" >&2
  exit 1
fi

ADB_BIN="${ADB_BIN:-adb}"
SERIAL="${PENLAB_ADB_SERIAL:-}"
if [[ -z "$SERIAL" ]]; then
  SERIAL="$("$ADB_BIN" devices </dev/null | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
if [[ -z "$SERIAL" ]]; then
  echo "Error: no connected ADB device found (set PENLAB_ADB_SERIAL or connect a device)." >&2
  exit 1
fi

adb_cmd() {
  "$ADB_BIN" -s "$SERIAL" "$@" </dev/null
}

# Privacy guard: only capture when dev.penlab.app is the active focused/resumed app
# (Compose DropdownMenu opens Android "Pop-up window" owned by the resumed app).
FOCUS_LINES="$(adb_cmd shell 'dumpsys window displays | grep "mCurrentFocus="' 2>/dev/null || true)"
NON_NULL_FOCUS="$(printf '%s\n' "$FOCUS_LINES" | grep -E 'mCurrentFocus=Window\{' || true)"
OTHER_FOCUS="$(printf '%s\n' "$NON_NULL_FOCUS" | grep -vE 'dev\.penlab\.app|u0 Pop-up window\}' || true)"

RESUMED_LINES="$(adb_cmd shell 'dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity"' 2>/dev/null || true)"
ACTIVE_RESUMED="$(printf '%s\n' "$RESUMED_LINES" | grep 'ActivityRecord\{' || true)"
PENLAB_RESUMED="$(printf '%s\n' "$ACTIVE_RESUMED" | grep 'dev\.penlab\.app' || true)"
OTHER_RESUMED="$(printf '%s\n' "$ACTIVE_RESUMED" | grep -v 'dev\.penlab\.app' || true)"

if [[ -z "$NON_NULL_FOCUS" ]] || [[ -n "$OTHER_FOCUS" ]] || [[ -z "$PENLAB_RESUMED" ]] || [[ -n "$OTHER_RESUMED" ]]; then
  echo "Privacy guard aborted: dev.penlab.app is not the focused/resumed app (mCurrentFocus: ${FOCUS_LINES:-<empty>}; resumed: ${ACTIVE_RESUMED:-<empty>})" >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_JAR="/data/local/tmp/penlab-cap.jar"
LOCAL_JAR="${SCRIPT_DIR}/penlab-cap.jar"

if ! adb_cmd shell "test -s ${REMOTE_JAR}" 2>/dev/null; then
  if [[ ! -s "$LOCAL_JAR" ]]; then
    ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
    PLATFORM_JAR="$(ls -1 "$ANDROID_HOME"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -n 1 || true)"
    D8_BIN="$(ls -1 "$ANDROID_HOME"/build-tools/*/d8 2>/dev/null | sort -V | tail -n 1 || true)"
    if [[ -z "$PLATFORM_JAR" || -z "$D8_BIN" ]]; then
      echo "Error: penlab-cap.jar missing and Android SDK javac/d8 unavailable to build it." >&2
      exit 1
    fi
    BUILD_DIR="$(mktemp -d)"
    javac -cp "$PLATFORM_JAR" -d "$BUILD_DIR" "${SCRIPT_DIR}/PenLabCap.java"
    "$D8_BIN" --lib "$PLATFORM_JAR" --output "$LOCAL_JAR" "$BUILD_DIR"/PenLabCap.class
    rm -rf "$BUILD_DIR"
  fi
  adb_cmd push "$LOCAL_JAR" "$REMOTE_JAR" >/dev/null
fi

SCREENCAP_CMD="screencap -p"
if [[ -n "$DISPLAY_ID" ]]; then
  SCREENCAP_CMD="screencap -d ${DISPLAY_ID} -p"
fi

mkdir -p "$(dirname "$OUT")"
TMP_OUT="${OUT}.tmp.$$"
trap 'rm -f "$TMP_OUT"' EXIT

adb_cmd exec-out "${SCREENCAP_CMD} | CLASSPATH=${REMOTE_JAR} app_process /system/bin PenLabCap ${MAX_W} ${QUALITY}" > "$TMP_OUT"

if [[ ! -s "$TMP_OUT" ]]; then
  echo "Error: captured file is empty" >&2
  exit 1
fi

MAGIC="$(od -An -tx1 -N3 "$TMP_OUT" | tr -d ' \n')"
if [[ "$MAGIC" != "ffd8ff" ]]; then
  echo "Error: captured output is not a valid JPEG (magic: ${MAGIC:-<empty>})" >&2
  exit 1
fi

mv -f "$TMP_OUT" "$OUT"
SIZE_BYTES="$(wc -c < "$OUT")"
SIZE_KB="$(awk "BEGIN { printf \"%.1f\", ${SIZE_BYTES} / 1024 }")"
echo "Saved ${OUT} (${SIZE_KB} KB)"
