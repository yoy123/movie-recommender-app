#!/bin/bash
# Screenshot capture script for Movie Recommender App
# This script navigates through the app and captures screenshots

SCREENSHOT_DIR="/run/media/dan/EXTRA/movie app/emulator_screenshots"
DEVICE_PATH="/sdcard"

echo "🎬 Starting screenshot capture for Movie Recommender App"
echo "================================================"

# Function to take screenshot
take_screenshot() {
    local name=$1
    local desc=$2
    echo "📸 Capturing: $desc"
    adb shell screencap -p "$DEVICE_PATH/${name}.png"
    adb pull "$DEVICE_PATH/${name}.png" "$SCREENSHOT_DIR/"
    echo "✓ Saved: ${name}.png"
    echo ""
}

# Screenshot 1: Current screen (should be genre selection or startup)
echo "Step 1: Initial screen"
sleep 2
take_screenshot "01_genre_selection" "Genre Selection Screen"

# Screenshot 2: Open preferences/settings
echo "Step 2: Opening preferences..."
# Tap the settings/preferences icon (usually top right)
# Coordinates may need adjustment based on screen size
adb shell input tap 950 150
sleep 2
take_screenshot "02_preferences" "Preferences Screen"

# Go back to genre screen
adb shell input keyevent 4  # BACK button
sleep 1

# Screenshot 3: Select a genre (tap on Action)
echo "Step 3: Selecting Action genre..."
adb shell input tap 540 400  # Approximate center-left for first genre
sleep 2
take_screenshot "03_movie_selection" "Movie Selection Screen"

# Screenshot 4: Tap on Favorites to show favorites screen
echo "Step 4: Opening Favorites..."
adb shell input keyevent 4  # Back to genre screen
sleep 1
# Look for Dee's Favorites - usually has heart icon
adb shell input tap 540 800  # Adjust based on where favorites is
sleep 2
take_screenshot "04_favorites" "Favorites Screen with Movies"

# Screenshot 5: Go back and select movies to get recommendations
echo "Step 5: Going back to get recommendations..."
adb shell input keyevent 4
sleep 1

# Select Action genre again
adb shell input tap 540 400
sleep 2

# Select 5 movies (tap on different movie posters)
echo "Selecting movies for recommendations..."
for i in {1..5}; do
    # Tap on movie posters in grid (adjust coordinates as needed)
    case $i in
        1) adb shell input tap 270 500 ;;
        2) adb shell input tap 810 500 ;;
        3) adb shell input tap 270 900 ;;
        4) adb shell input tap 810 900 ;;
        5) adb shell input tap 270 1300 ;;
    esac
    sleep 0.5
done

sleep 2
take_screenshot "05_movies_selected" "Movies Selected"

# Tap "Get Recommendations" button (usually at bottom)
echo "Step 6: Getting recommendations..."
adb shell input tap 540 2100  # Bottom center for recommendation button
sleep 10  # Wait for AI to process

take_screenshot "06_recommendations" "Recommendations with Analysis"

# Screenshot 7: Scroll down to see more recommendations
echo "Step 7: Scrolling recommendations..."
adb shell input swipe 540 1500 540 800 500
sleep 1
take_screenshot "07_recommendations_list" "Recommendations List"

# Screenshot 8: Tap on a movie to see trailer
echo "Step 8: Opening trailer..."
adb shell input tap 540 600  # Tap on first recommendation
sleep 3
take_screenshot "08_trailer_screen" "Trailer Screen"

echo ""
echo "================================================"
echo "✅ Screenshot capture complete!"
echo "📁 Screenshots saved to: $SCREENSHOT_DIR"
echo ""
echo "Screenshots captured:"
ls -1 "$SCREENSHOT_DIR"/*.png | tail -8
