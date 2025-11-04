# Google Play Store Submission Guide

This guide walks you through preparing and submitting Movie Recommender App to the Google Play Store.

## Prerequisites

- [x] Google Play Developer account ($25 one-time registration fee)
- [x] Signed release build (AAB file)
- [x] Privacy Policy (see PRIVACY_POLICY.md)
- [ ] App screenshots (required)
- [ ] Feature graphic (required)
- [ ] App icon (required - we have this)

## Step 1: Build the Release Bundle

Build the signed Android App Bundle (AAB):

```bash
./gradlew bundleRelease
```

The AAB file will be located at:
```
app/build/outputs/bundle/release/app-release.aab
```

## Step 2: Prepare Store Assets

### Required Assets

#### 1. Screenshots (REQUIRED)
You need at least 2 screenshots (up to 8) for each form factor:
- **Phone**: 16:9 or 9:16 aspect ratio
  - Min dimension: 320px
  - Max dimension: 3840px
  - Recommended: 1080x1920 or 1080x2340 (portrait)

We have screenshots in `emulator_screenshots/` folder:
- ✓ launch.png
- ✓ step1.png
- ✓ step2.png
- ✓ step3.png
- ✓ recos_after_fix.png
- ✓ recos_ui_verify.png

#### 2. Feature Graphic (REQUIRED)
- Size: 1024x500 pixels
- Format: PNG or JPEG
- No transparency
- This is the banner shown at the top of your store listing

#### 3. App Icon (REQUIRED)
- Size: 512x512 pixels
- Format: PNG (32-bit)
- We already have this: `app/src/main/ic_launcher-playstore.png`

#### 4. Promotional Graphics (Optional but recommended)
- Promo graphic: 180x120 pixels
- TV banner: 1280x720 pixels

## Step 3: Play Store Listing Content

### App Title
**Movie Recommender - AI Movie Suggestions**
(Max 50 characters)

### Short Description
Get personalized movie recommendations powered by AI. Select 5 movies you love, and discover your next favorite film!
(Max 80 characters)

### Full Description

```
🎬 Discover Your Next Favorite Movie with AI!

Movie Recommender uses advanced AI (GPT-4o-mini) to understand your taste and suggest movies you'll love. No more endless scrolling - get smart, personalized recommendations based on movies you've already enjoyed.

✨ KEY FEATURES:

🤖 AI-Powered Recommendations
Select 5 movies from any genre, and let our AI analyze your taste to suggest hidden gems and popular hits tailored just for you.

❤️ Personal Favorites Collection
Save movies from any genre in your custom "Dee's Favorites" list. Build your perfect watchlist across all categories.

🎯 Smart Analysis
Our AI understands themes, mood, and storytelling style - not just popularity. Get recommendations that truly match your preferences.

📱 Beautiful, Intuitive Design
Modern Material Design 3 interface with smooth animations and an easy-to-use experience.

🔍 Extensive Movie Database
Powered by The Movie Database (TMDB), access millions of movies with detailed information, ratings, and posters.

💾 Offline Storage
Your selections and favorites are saved locally - no account required.

🌟 PERFECT FOR:

• Movie enthusiasts looking for new films to watch
• Anyone tired of generic recommendations
• Building a personal movie collection
• Discovering hidden gems in your favorite genres
• Finding movies similar to ones you love

🎭 SUPPORTED GENRES:

Action • Comedy • Drama • Horror • Sci-Fi • Romance • Thriller • Animation • Documentary • Fantasy • Mystery • Crime • Adventure • Family • History • Music • War • Western • and more!

📝 HOW IT WORKS:

1. Choose a genre you're interested in
2. Select 5 movies you've watched and enjoyed
3. Get AI-powered recommendations with explanations
4. Save your favorite movies across all genres
5. Watch trailers and get detailed movie information

🔒 PRIVACY FIRST:

• No personal data collection
• No user accounts required
• All data stored locally on your device
• Secure API communications

Note: This app requires internet connection for movie data and AI recommendations. API keys for TMDB and OpenAI are required (see documentation).

Download now and let AI be your personal movie curator! 🎥✨
```
(Max 4000 characters)

### App Category
**Entertainment** or **Lifestyle**

### Content Rating
Complete the content rating questionnaire. Expected rating: **Everyone** or **Teen**

### Target Audience
- Primary: Ages 13+
- All audiences interested in movies

### Store Listing Contact Details
- Email: [Your support email]
- Website: https://github.com/yoy123/movie-recommender-app
- Privacy Policy URL: [You'll need to host PRIVACY_POLICY.md online]

### Tags (Keywords)
- movie recommendations
- ai movies
- movie finder
- film suggestions
- movie app
- tmdb
- watch movies
- movie database
- cinema
- entertainment

## Step 4: App Content Declaration

You'll need to answer these questions in Google Play Console:

### Privacy Policy
- **Privacy policy URL**: [Host PRIVACY_POLICY.md on GitHub Pages or your website]

### Data Safety Section
- **Does your app collect user data?**: NO
- **Does your app share data with third parties?**: NO
- **Data handling**: All data stored locally on device
- **Security practices**: Data is encrypted in transit (HTTPS)

### Ads
- **Does your app contain ads?**: NO

### App Access
- **Special access requirements**: None
- **Does your app have restricted access?**: NO

### Target Audience
- **Age groups**: 13 and older

### News Apps
- **Is this a news app?**: NO

### COVID-19 Contact Tracing & Status Apps
- **Is this a COVID-19 app?**: NO

### Sensitive Permissions
Your app uses:
- INTERNET: Required for fetching movie data and AI recommendations
- ACCESS_NETWORK_STATE: To check internet connectivity

## Step 5: Testing

### Internal Testing Track (Recommended)
1. Create an internal testing track
2. Upload your AAB
3. Add test users (up to 100)
4. Test thoroughly before production release

### Closed Testing (Alpha/Beta)
1. Create a closed testing track
2. Get feedback from limited audience
3. Iterate and improve

### Open Testing (Optional)
1. Allow public testing with early access
2. Gather broader feedback

## Step 6: Production Release

### Release Checklist
- [ ] Signed AAB built and tested
- [ ] All store assets prepared (screenshots, feature graphic)
- [ ] Privacy policy URL added
- [ ] App description finalized
- [ ] Content rating completed
- [ ] Data safety form completed
- [ ] Pricing set (Free)
- [ ] Countries/regions selected (Worldwide or specific regions)
- [ ] Release notes written

### Release Notes (Version 1.0.0)

```
🎉 Welcome to Movie Recommender v1.0!

✨ Features:
• AI-powered movie recommendations using GPT-4o-mini
• Browse movies by genre
• Select 5 movies and get personalized suggestions
• Save favorite movies across all genres
• Watch trailers directly in the app
• Beautiful Material Design 3 interface
• TMDB ratings and detailed movie information

This is our initial release. We'd love your feedback!
```

## Step 7: Play Console Upload

1. Go to [Google Play Console](https://play.google.com/console/)
2. Click "Create app"
3. Fill in app details (name, language, category)
4. Complete all required sections:
   - Privacy Policy
   - App access
   - Ads
   - Content rating
   - Target audience
   - News apps
   - Data safety
5. Upload AAB to a testing or production track
6. Upload store assets (screenshots, icon, feature graphic)
7. Write store listing (title, description)
8. Set pricing & distribution
9. Submit for review

## Review Process

- **Timeline**: Usually 1-7 days for first review
- **Common reasons for rejection**:
  - Missing privacy policy
  - Insufficient screenshots
  - Content rating issues
  - App crashes or critical bugs
  - Misleading store listing

## Post-Launch

### Version Updates
To release updates:
1. Increment `versionCode` and `versionName` in `build.gradle.kts`
2. Build new AAB: `./gradlew bundleRelease`
3. Upload to Play Console
4. Add release notes
5. Submit for review

### Monitoring
- Check Google Play Console for:
  - Crash reports (Firebase Crashlytics recommended)
  - ANR (Application Not Responding) reports
  - User reviews and ratings
  - Installation statistics

## Important Notes

1. **API Keys**: Users will need their own TMDB and OpenAI API keys. Consider:
   - Adding key setup instructions in the app
   - Or providing backend service with your keys (costs involved)

2. **OpenAI Costs**: Each recommendation costs ~$0.0002-$0.0005
   - If you provide the API key, you pay per request
   - If users provide their own keys, they pay
   - Consider implementing rate limiting

3. **TMDB Attribution**: Required by TMDB ToS
   - ✓ Already included in the app

4. **Updates**: Keep dependencies updated for security and Play Store compliance

## Resources

- [Google Play Console](https://play.google.com/console/)
- [Play Console Help](https://support.google.com/googleplay/android-developer/)
- [Android App Bundle Guide](https://developer.android.com/guide/app-bundle)
- [Store Listing Best Practices](https://developer.android.com/distribute/best-practices/launch/store-listing)

## Troubleshooting

### "App not uploaded"
- Make sure you're uploading AAB (not APK)
- Check signing configuration is correct
- Ensure versionCode is higher than previous uploads

### "Privacy Policy Required"
- Host PRIVACY_POLICY.md on GitHub Pages, your website, or use a free hosting service
- Add the URL in Play Console

### "Screenshots Required"
- Need minimum 2 screenshots per form factor
- Check dimensions and format requirements

## Need Help?

Check out these resources:
- GitHub Issues: https://github.com/yoy123/movie-recommender-app/issues
- Play Console Support: https://support.google.com/googleplay/android-developer/
