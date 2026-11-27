#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <apk-path>" >&2
  exit 1
fi

APK="$1"
if [[ ! -f "$APK" ]]; then
  echo "Error: APK not found: $APK" >&2
  exit 1
fi

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT_DIR="$(ls -1d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -n 1 || true)"
APKSIGNER="${BT_DIR}/apksigner"
AAPT2="${BT_DIR}/aapt2"
if [[ ! -x "$APKSIGNER" || ! -x "$AAPT2" ]]; then
  echo "Error: apksigner/aapt2 not found under ${ANDROID_HOME}/build-tools" >&2
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

BADGING="$("$AAPT2" dump badging "$APK" | grep -E "^(package:|native-code:|testOnly)" || true)"
PKG_NAME="$(printf '%s\n' "$BADGING" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
if [[ "$PKG_NAME" != "dev.penlab.app" ]]; then
  echo "Error: refusing to install package '${PKG_NAME:-<unknown>}' (expected dev.penlab.app)." >&2
  exit 1
fi
if printf '%s\n' "$BADGING" | grep -q "testOnly"; then
  echo "Warning: APK has testOnly flag set." >&2
fi

SIZE_BYTES="$(wc -c < "$APK")"
SIZE_MB="$(awk "BEGIN { printf \"%.2f\", ${SIZE_BYTES} / (1024 * 1024) }")"
echo "APK: $APK (${SIZE_MB} MB)"
printf '%s\n' "$BADGING"

"$APKSIGNER" verify --print-certs "$APK"

adb_install() {
  "$ADB_BIN" -s "$SERIAL" "$@" </dev/null
}

set +e
INSTALL_OUT="$(adb_install install -r --fastdeploy "$APK" 2>&1)"
STATUS=$?
set -e
echo "$INSTALL_OUT"

if [[ $STATUS -eq 0 ]]; then
  exit 0
fi

if [[ "$INSTALL_OUT" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* ]]; then
  echo "Error: INSTALL_FAILED_UPDATE_INCOMPATIBLE — signing certificate mismatch; refusing to uninstall or clear app data." >&2
  exit 1
fi

if [[ "$INSTALL_OUT" == *"--fastdeploy"* ]] || [[ "$INSTALL_OUT" == *"unknown option"* ]] || [[ "$INSTALL_OUT" == *"unrecognized option"* ]] || [[ "$INSTALL_OUT" == *"fastdeploy"* && "$INSTALL_OUT" == *"unsupported"* ]]; then
  echo "Falling back to standard adb install -r (fastdeploy unsupported)..."
  set +e
  FALLBACK_OUT="$(adb_install install -r "$APK" 2>&1)"
  FALLBACK_STATUS=$?
  set -e
  echo "$FALLBACK_OUT"
  if [[ "$FALLBACK_OUT" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* ]]; then
    echo "Error: INSTALL_FAILED_UPDATE_INCOMPATIBLE — signing certificate mismatch; refusing to uninstall or clear app data." >&2
    exit 1
  fi
  exit $FALLBACK_STATUS
fi

exit $STATUS
