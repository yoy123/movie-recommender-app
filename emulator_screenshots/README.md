# Play Store Assets Checklist

This directory contains screenshots and assets for the Google Play Store listing.

## Current Screenshots ✓

We have the following screenshots ready for Google Play Store:

**User Journey Screens:**
1. ✅ `Customize.png` - Welcome/Customization screen
2. ✅ `GenreSelection.png` - Genre selection screen
3. ✅ `MovieSelectionPlusAddtoFavorites.png` - Movie browsing and selection
4. ✅ `CustomizedFavorites.png` - Favorites collection
5. ✅ `preferences1.png` - App preferences (Release Preference)
6. ✅ `preferences2.png` - App preferences (Recommendation Style)
7. ✅ `analyzingtaste.png` - AI analyzing user taste
8. ✅ `recommendations1.png` - AI recommendations with analysis
9. ✅ `recommendations2.png` - Recommendations list
10. ✅ `trailer.png` - Movie trailer screen
11. ✅ `trailer2.png` - Trailer playing
12. ✅ `trailer3.png` - Trailer with details

## Required for Play Store

### Screenshots (✓ Complete)
- **Phone screenshots**: Need minimum 2 (we have 12!) ✓
- Format: PNG or JPEG ✓
- Dimensions: 320-3840px on longest side ✓
- Aspect ratio: 16:9 or 9:16 ✓

**Recommended order for Play Store (choose best 8):**

**Option 1: Full Journey (8 screenshots)**
1. `Customize.png` - Personalized welcome screen
2. `GenreSelection.png` - Browse by genre
3. `MovieSelectionPlusAddtoFavorites.png` - Select movies & add to favorites
4. `CustomizedFavorites.png` - Your personal collection
5. `preferences1.png` OR `preferences2.png` - Customize preferences
6. `recommendations1.png` - AI analysis and recommendations
7. `recommendations2.png` - More recommendations
8. `trailer2.png` - Watch trailers in-app

**Option 2: Focus on Key Features (8 screenshots)**
1. `Customize.png` - Welcome screen
2. `GenreSelection.png` - Choose your genre
3. `MovieSelectionPlusAddtoFavorites.png` - Select movies
4. `analyzingtaste.png` - AI analyzing your taste
5. `recommendations1.png` - Personalized recommendations with analysis
6. `recommendations2.png` - Recommendation list
7. `CustomizedFavorites.png` - Save favorites
8. `trailer2.png` - Watch trailers

### Feature Graphic (⚠️ TODO)
- **Status**: NEEDS TO BE CREATED
- Size: 1024x500 pixels
- Format: PNG or JPEG (no transparency)
- This is the banner at the top of your Play Store listing
- Should showcase key features and branding

**Suggested Content:**
- App name: "Movie Recommender"
- Tagline: "AI-Powered Movie Suggestions"
- Visual: Movie posters or AI brain icon with film reels
- Colors: Match app theme

### App Icon (✓ Complete)
- ✅ Already have: `app/src/main/ic_launcher-playstore.png` (512x512)
- This will be used as the Play Store icon

### Promo Video (Optional)
- **Status**: Not created (optional)
- Length: 30 seconds to 2 minutes
- Shows app in action
- Can significantly increase conversions

## Creating Missing Assets

### Feature Graphic (1024x500)

You can create this using:
1. **Canva** (free templates available)
2. **GIMP** (free, open-source)
3. **Photoshop** / **Figma** (professional tools)
4. **Online tools**: remove.bg, photopea.com

**Quick steps:**
1. Create 1024x500 canvas
2. Add app name "Movie Recommender"
3. Add tagline "AI-Powered Movie Suggestions"
4. Include 2-3 movie poster thumbnails
5. Use gradient background matching app colors
6. Export as high-quality PNG/JPEG

### Optional: Promo Graphic (180x120)
- Smaller promotional image
- Same branding as feature graphic

### Optional: TV Banner (1280x720)
- Only needed if supporting Android TV
- Our app currently doesn't target TV

## Screenshot Guidelines

### Best Practices (Already followed ✓)
- ✅ Show actual app interface
- ✅ Use high-quality images
- ✅ Show key features progression
- ✅ Clean, uncluttered views
- ✅ Good lighting/contrast

### What to Avoid
- ❌ No device frames needed (Play Store adds them)
- ❌ Don't include promotional text on screenshots
- ❌ No fake reviews or ratings
- ❌ No mention of other platforms

## Uploading to Play Store

1. Go to Google Play Console
2. Select your app → Store presence → Main store listing
3. Scroll to "Graphics" section
4. Upload:
   - App icon (512x512) - we have this ✓
   - Feature graphic (1024x500) - CREATE THIS
   - Phone screenshots (minimum 2) - we have 6 ✓
5. Save and continue with other sections

## Testing Screenshots

Before uploading, verify:
- [ ] All images are clear and high resolution
- [ ] No personal/sensitive information visible
- [ ] Text is readable at thumbnail size
- [ ] Images follow Google Play policies
- [ ] Consistent branding across all assets

## Tools for Resizing/Editing

If you need to adjust images:
```bash
# Install ImageMagick (if not installed)
sudo apt install imagemagick

# Resize maintaining aspect ratio
convert input.png -resize 1080x1920 output.png

# Add border if needed
convert input.png -bordercolor white -border 10x10 output.png

# Convert format
convert input.png output.jpg
```

## Next Steps

1. ✅ Screenshots ready - 6 high-quality images
2. ⚠️ Create feature graphic (1024x500)
3. ✅ Privacy policy written (PRIVACY_POLICY.md)
4. ✅ Store description ready (PLAY_STORE_GUIDE.md)
5. ✅ AAB built and signed
6. 📝 Upload to Play Console

See PLAY_STORE_GUIDE.md for complete submission instructions.
