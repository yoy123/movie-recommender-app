#!/usr/bin/env bash
set -euo pipefail

# Launch/stop the Android TV emulator with sane defaults on Linux.
# Fixes common "can't control emulator" issues caused by missing ANDROID_SDK_ROOT / mismatched adb.

CMD="${1:-start}"
AVD_NAME="${2:-FlickPick_TV_New}"

SDK_ROOT_DEFAULT="$HOME/Android/Sdk"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$SDK_ROOT_DEFAULT}"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR_BIN="$ANDROID_SDK_ROOT/emulator/emulator"

if [[ ! -x "$ADB" ]]; then
  echo "ERROR: adb not found at $ADB"
  echo "Set ANDROID_SDK_ROOT (or install platform-tools)"
  exit 1
fi

if [[ ! -x "$EMULATOR_BIN" ]]; then
  echo "ERROR: emulator not found at $EMULATOR_BIN"
  echo "Set ANDROID_SDK_ROOT (or install emulator)"
  exit 1
fi

export ANDROID_SDK_ROOT ANDROID_HOME

usage() {
  echo "Usage:"
  echo "  $0 start [AVD_NAME]   # default AVD_NAME=FlickPick_TV_New"
  echo "  $0 stop               # stops the running emulator (adb emu kill)"
}

case "$CMD" in
  start)
    # If an emulator is already running, don't start a second one.
    if "$ADB" devices | grep -q "^emulator-"; then
      echo "Emulator already running (per adb)."
    else
      echo "Starting AVD: $AVD_NAME"

      # -no-snapshot: avoids snapshot corruption/input weirdness
      # -gpu host: best responsiveness when host GPU works
      # -noaudio: less overhead
      # Redirect output so the terminal stays usable.
      "$EMULATOR_BIN" \
        -avd "$AVD_NAME" \
        -no-snapshot \
        -no-boot-anim \
        -gpu host \
        -noaudio \
        >"/tmp/${AVD_NAME}_emulator.log" 2>&1 &

      echo "Emulator PID: $!"
      echo "Emulator log: /tmp/${AVD_NAME}_emulator.log"
    fi

    echo "Waiting for device to be online..."
    "$ADB" wait-for-device

    # Some boots briefly appear as 'offline'. Poll until 'device'.
    for _ in {1..60}; do
      state=$("$ADB" get-state 2>/dev/null || true)
      if [[ "$state" == "device" ]]; then
        break
      fi
      sleep 1
    done

    echo "adb state: $("$ADB" get-state 2>/dev/null || echo unknown)"
    echo
    echo "Control tip (keyboard/mouse):"
    echo "  ./run_tv_control.sh"
    ;;

  stop)
    serial=$("$ADB" devices | awk '/^emulator-/{print $1; exit 0}')
    if [[ -z "${serial:-}" ]]; then
      echo "No running emulator detected via adb."
      exit 0
    fi

    echo "Stopping emulator: $serial"
    # This stops the emulator cleanly without killing adb server.
    "$ADB" -s "$serial" emu kill || true
    echo "Done. If it doesn't exit within ~20s, you can run: pkill -f \"emulator.*$AVD_NAME\""
    ;;

  *)
    usage
    exit 2
    ;;
esac
