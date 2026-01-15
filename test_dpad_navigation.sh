#!/bin/bash
# Fire TV Stick D-pad Navigation Test Script

# Prefer the SDK adb when available to avoid "no devices" issues caused by mismatched adb installs.
SDK_ADB="$HOME/Android/Sdk/platform-tools/adb"
if [[ -x "$SDK_ADB" ]]; then
    ADB="$SDK_ADB"
else
    ADB="adb"
fi

echo "🎮 Fire TV Stick D-pad Navigation Test"
echo "========================================"
echo ""

# Check if emulator is running
if ! "$ADB" devices | grep -q "^emulator-"; then
    echo "❌ No emulator detected. Please start the Android TV emulator first."
    echo "   Tip: ./run_tv_emulator.sh FlickPick_TV_New"
    exit 1
fi

echo "✓ Emulator detected"
echo ""

# Function to send key and wait
send_key() {
    local key=$1
    local description=$2
    echo "$description"
    "$ADB" shell input keyevent $key
    sleep 0.8
}

# Launch the app
echo "📱 Launching FlickPick..."
"$ADB" shell am start -n com.movierecommender.app.firestick/com.movierecommender.app.MainActivity
sleep 3

echo ""
echo "🎯 Testing Navigation Flow:"
echo "----------------------------"

# Navigate to a genre
send_key "KEYCODE_DPAD_DOWN" "1️⃣  Moving down to genre card"
send_key "KEYCODE_DPAD_CENTER" "2️⃣  Selecting genre (Action)"
sleep 2

# Select 5 movies
send_key "KEYCODE_DPAD_CENTER" "3️⃣  Selecting movie #1"
send_key "KEYCODE_DPAD_RIGHT" "4️⃣  Moving to movie #2"
send_key "KEYCODE_DPAD_CENTER" "5️⃣  Selecting movie #2"
send_key "KEYCODE_DPAD_RIGHT" "6️⃣  Moving to movie #3"
send_key "KEYCODE_DPAD_CENTER" "7️⃣  Selecting movie #3"
send_key "KEYCODE_DPAD_DOWN" "8️⃣  Moving down to next row"
send_key "KEYCODE_DPAD_CENTER" "9️⃣  Selecting movie #4"
send_key "KEYCODE_DPAD_RIGHT" "🔟 Moving to movie #5"
send_key "KEYCODE_DPAD_CENTER" "1️⃣1️⃣  Selecting movie #5"

echo ""
echo "📍 Navigating to FAB..."
send_key "KEYCODE_DPAD_DOWN" "   ↓ Down"
send_key "KEYCODE_DPAD_DOWN" "   ↓ Down"  
send_key "KEYCODE_DPAD_DOWN" "   ↓ Down"
send_key "KEYCODE_DPAD_CENTER" "   ✓ Clicking 'Get Recommendations'"

echo ""
echo "⏳ Waiting for recommendations (10s)..."
sleep 10

# Take screenshot
echo "📸 Taking screenshot..."
"$ADB" shell screencap -p > /tmp/firestick_recommendations.png

echo ""
echo "✅ Navigation test complete!"
echo "Screenshot saved to: /tmp/firestick_recommendations.png"
echo ""
echo "📊 Quick Stats:"
"$ADB" shell dumpsys activity activities | grep -A 5 "movierecommender" | head -10

echo ""
echo "💡 Manual Controls:"
echo "  Arrow Keys = D-pad navigation"
echo "  Enter = Select (KEYCODE_DPAD_CENTER)"
echo "  Esc = Back (KEYCODE_BACK)"
