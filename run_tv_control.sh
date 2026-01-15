#!/usr/bin/env bash
set -euo pipefail

# Reliable control window for the Android TV emulator.
# Run this in the foreground. To exit: close the scrcpy window OR press Ctrl+C in this terminal.

SDK_ADB="$HOME/Android/Sdk/platform-tools/adb"
if [[ -x "$SDK_ADB" ]]; then
  ADB="$SDK_ADB"
else
  ADB="adb"
fi

if ! "$ADB" devices | grep -q "^emulator-"; then
  echo "No emulator detected via adb. Start it first:"
  echo "  ./run_tv_emulator.sh start FlickPick_TV_New"
  exit 1
fi

if ! command -v scrcpy >/dev/null 2>&1; then
  echo "scrcpy not found. Install it or use adb keyevents (test_dpad_navigation.sh)."
  exit 1
fi

echo "Starting scrcpy (no audio) with HID keyboard/mouse injection..."
echo "Exit tips:"
echo "  - Close the scrcpy window, OR"
echo "  - Press Ctrl+C in this terminal"
echo

# --no-audio avoids ALSA issues on headless/ssh boxes.
# --keyboard=uhid/--mouse=uhid gives much better control on Android 13+.
scrcpy \
  --no-audio \
  --keyboard=uhid \
  --mouse=uhid \
  --stay-awake \
  --window-title "FlickPick TV (scrcpy)"
