# DEPLOYMENT.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Overview

Complete guide for building, releasing, and deploying the OpenStream+ Android/Fire TV app to various distribution channels.

---

## Prerequisites

### Required Tools

- **JDK:** 17 or higher
- **Android Studio:** Hedgehog (2023.1.1) or newer
- **Gradle:** 8.2+ (via wrapper)
- **Git:** For version control
- **Android SDK:** API 24-34

### Required API Keys

Create `local.properties` in project root:

```properties
# TMDB API Key (required)
TMDB_API_KEY=your_tmdb_api_key_here

# OpenAI API Key (required)
OPENAI_API_KEY=sk-proj-your_openai_key_here

# Optional (currently unused)
OMDB_API_KEY=your_omdb_key_here
```

**Never commit `local.properties` to version control.**

---

## Build Variants

### Product Flavors

**Mobile:** Standard Android phone/tablet UI
```bash
./gradlew assembleMobileDebug      # Debug build
./gradlew assembleMobileRelease    # Release build
```

**Firestick:** Fire TV optimized UI
```bash
./gradlew assembleFirestickDebug   # Debug build
./gradlew assembleFirestickRelease # Release build
```

### Build Types

**Debug:**
- Debuggable: true
- Minify: false
- Shrink resources: false
- SSL: Insecure (⚠️ see KNOWN_ISSUES.md #3)

**Release:**
- Debuggable: false
- Minify: true (ProGuard)
- Shrink resources: true
- SSL: Secure (must verify)
- Signed with release keystore

---

## Building APKs

### Debug Builds (Development)

**Mobile Debug:**
```bash
./gradlew :app:assembleMobileDebug
```
Output: `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk`

**Firestick Debug:**
```bash
./gradlew :app:assembleFirestickDebug
```
Output: `app/build/outputs/apk/firestick/debug/app-firestick-debug.apk`

**Install to Device:**
```bash
adb install app/build/outputs/apk/mobile/debug/app-mobile-debug.apk
```

---

### Release Builds (Production)

**Prerequisites:**
1. Generate release keystore
2. Configure signing in `app/build.gradle.kts`
3. Store keystore credentials securely

**Generate Keystore (first time only):**
```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias movie-recommender \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

**Store password securely:**
Add to `local.properties` (NOT in version control):
```properties
KEYSTORE_FILE=../release.keystore
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=movie-recommender
KEY_PASSWORD=your_key_password
```

**Build Release APK:**
```bash
./gradlew :app:assembleMobileRelease
```
Output: `app/build/outputs/apk/mobile/release/app-mobile-release.apk`

**Verify Signing:**
```bash
jarsigner -verify -verbose -certs app/build/outputs/apk/mobile/release/app-mobile-release.apk
```

---

## Building App Bundles (Google Play)

**Generate AAB:**
```bash
./gradlew :app:bundleMobileRelease
```
Output: `app/build/outputs/bundle/mobileRelease/app-mobile-release.aab`

**Why AAB?**
- Smaller downloads (Play Store optimizes per device)
- Required for Google Play submissions
- Not compatible with sideloading (use APK for direct install)

---

## Version Management

### Update Version

Edit `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 2        // Integer (increment for each release)
    versionName = "1.1.0"  // Semantic version (user-facing)
}
```

**Version Naming Convention:**
- Major: Breaking changes (1.0.0 → 2.0.0)
- Minor: New features (1.0.0 → 1.1.0)
- Patch: Bug fixes (1.0.0 → 1.0.1)

---

## ProGuard Configuration

### Current ProGuard Rules

**Location:** `app/proguard-rules.pro`

**Minimal Rules (Needs Enhancement):**
```proguard
# Keep Retrofit interfaces
-keep interface com.movierecommender.app.data.remote.** { *; }

# Keep Room entities
-keep class com.movierecommender.app.data.model.** { *; }

# Keep data classes (JSON parsing)
-keepclassmembers class com.movierecommender.app.data.model.** {
    <fields>;
}
```

**⚠️ Warning:** See [KNOWN_ISSUES.md #15](KNOWN_ISSUES.md#15) - ProGuard rules incomplete.

**Recommended Additions:**
```proguard
# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }

# Retrofit
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
```

---

## Testing Release Builds

### Pre-Release Checklist

**Functional Tests:**
- [ ] Fresh install (not upgrade)
- [ ] Onboarding flow
- [ ] API calls work (TMDB + OpenAI)
- [ ] Recommendations generate
- [ ] Favorites persist
- [ ] Trailer playback
- [ ] Torrent streaming
- [ ] Dark mode toggle
- [ ] No crashes

**ProGuard Tests:**
- [ ] API calls succeed (Retrofit not stripped)
- [ ] Room queries work (DAO methods not stripped)
- [ ] JSON parsing works (Gson not stripped)
- [ ] No `ClassNotFoundException` in logs

**Performance Tests:**
- [ ] APK size < 50 MB (target)
- [ ] Cold start < 2 seconds
- [ ] Memory usage < 300 MB

**Security Tests:**
- [ ] SSL certificate validation enabled (not insecure)
- [ ] API keys not leaked in APK
- [ ] No debug logs in production

---

## Distribution Channels

### 1. Google Play Store (Mobile Only)

**Status:** ⚠️ **Not Recommended** - Torrent streaming violates Play Store policies

**If submitting (without torrent feature):**

1. **Create App in Play Console**
   - Go to https://play.google.com/console
   - Create new app
   - Fill app details

2. **Upload AAB**
   - Production → Create release
   - Upload `app-mobile-release.aab`

3. **Complete Store Listing**
   - Screenshots (phone, tablet, 7-inch, 10-inch)
   - Feature graphic (1024×500)
   - App icon (512×512)
   - Description
   - Privacy policy (required if collecting data)

4. **Content Rating**
   - Complete questionnaire
   - Likely rating: 13+ or 17+ (depends on content)

5. **Submit for Review**
   - Review time: 3-7 days
   - May be rejected due to torrent feature

**Compliance Issues:**
- Torrent streaming: Policy violation
- OpenAI data sharing: Needs user consent (see KNOWN_ISSUES.md #19)

---

### 2. Amazon Appstore (Fire TV)

**Status:** ⚠️ **Torrent feature may cause rejection**

**If submitting:**

1. **Create Account**
   - Go to https://developer.amazon.com/apps-and-games
   - Register as developer

2. **Upload APK**
   - Submit `app-firestick-release.apk`
   - Fill app details

3. **Fire TV Certification**
   - Leanback launcher: ✅
   - Banner image (320×180): ✅
   - DPAD navigation: ✅
   - Focus indicators: ⚠️ Needs enhancement (KNOWN_ISSUES.md #16)

4. **Content Rating**
   - Complete questionnaire

5. **Submit**
   - Review time: 5-10 days

---

### 3. F-Droid (Recommended)

**Status:** ✅ **Best Option** - Open-source friendly, allows torrent feature

**Prerequisites:**
- App must be 100% open source
- No proprietary libraries
- No tracking/analytics

**Submission:**
1. Fork F-Droid data repo: https://gitlab.com/fdroid/fdroiddata
2. Add app metadata
3. Submit merge request
4. Wait for approval

**Pros:**
- No restrictions on torrent streaming
- Privacy-focused user base
- No developer fees

**Cons:**
- Smaller user base
- Manual review process

---

### 4. Direct APK Distribution

**Status:** ✅ **Easiest** - No store policies, full control

**Methods:**

**Self-Hosted:**
1. Upload APK to your server
2. Create download page
3. Users enable "Install from unknown sources"
4. Users download and install

**GitHub Releases:**
1. Push code to GitHub
2. Create release tag
3. Attach APK to release
4. Users download from GitHub

**Example Release:**
```bash
git tag -a v1.0.0 -m "Initial release"
git push origin v1.0.0

# Attach APK manually in GitHub UI
```

---

## CI/CD Automation

### GitHub Actions Workflow

**Location:** `.github/workflows/build.yml`

```yaml
name: Build APKs

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          
      - name: Create local.properties
        run: |
          echo "TMDB_API_KEY=${{ secrets.TMDB_API_KEY }}" >> local.properties
          echo "OPENAI_API_KEY=${{ secrets.OPENAI_API_KEY }}" >> local.properties
          
      - name: Build Mobile Release
        run: ./gradlew assembleMobileRelease
        
      - name: Build Firestick Release
        run: ./gradlew assembleFirestickRelease
        
      - name: Upload APKs
        uses: actions/upload-artifact@v3
        with:
          name: apks
          path: |
            app/build/outputs/apk/mobile/release/*.apk
            app/build/outputs/apk/firestick/release/*.apk
```

**Secrets to Configure:**
- `TMDB_API_KEY`
- `OPENAI_API_KEY`
- `KEYSTORE_PASSWORD` (if signing)
- `KEY_PASSWORD`

---

## Release Process

### Step-by-Step Release

**1. Code Freeze**
- Merge all feature branches
- Fix all critical bugs
- Update documentation

**2. Version Bump**
```bash
# Edit app/build.gradle.kts
versionCode = X + 1
versionName = "X.Y.Z"

git add app/build.gradle.kts
git commit -m "Bump version to X.Y.Z"
```

**3. Update Changelog**
```bash
# Edit CHANGELOG.md (create if doesn't exist)
# Add release notes
```

**4. Build Release**
```bash
./gradlew clean
./gradlew assembleMobileRelease
./gradlew assembleFirestickRelease
```

**5. Test Release**
- Install on clean device
- Run full test checklist
- Verify no crashes

**6. Create Git Tag**
```bash
git tag -a vX.Y.Z -m "Release X.Y.Z"
git push origin vX.Y.Z
```

**7. Upload to Distribution Channel**
- Google Play (if applicable)
- Amazon Appstore (if applicable)
- GitHub Releases
- F-Droid

**8. Announce Release**
- Update README.md
- Post on social media
- Notify users

---

## Rollback Procedure

**If release has critical bug:**

**1. Immediate Mitigation**
- Remove APK from download links
- Pause Play Store/Amazon rollout (if applicable)

**2. Identify Issue**
- Check crash reports
- Review user feedback
- Reproduce bug

**3. Fix**
- Create hotfix branch
- Fix bug
- Test thoroughly

**4. Hotfix Release**
```bash
# Bump patch version
versionCode = X + 1
versionName = "X.Y.Z+1"

./gradlew assembleMobileRelease
./gradlew assembleFirestickRelease

git tag -a vX.Y.Z+1 -m "Hotfix: <bug description>"
git push origin vX.Y.Z+1
```

**5. Deploy Hotfix**
- Upload to distribution channels
- Announce fix

---

## Monitoring

### Post-Release Monitoring

**Metrics to Track:**
- Crash rate (target: < 1%)
- ANR rate (target: < 0.5%)
- API failure rate (TMDB, OpenAI)
- User ratings/reviews
- Download/install count

**Tools:**
- Firebase Crashlytics (recommended)
- Google Play Console (if available)
- Manual user feedback

---

## Security Considerations

### Before Release

**1. Remove Debug Code**
- No `Log.d()` with sensitive data
- No hardcoded API keys
- No debug SSL (KNOWN_ISSUES.md #3)

**2. API Key Protection**
- Keys in BuildConfig (not in code)
- ProGuard obfuscation enabled
- Consider key rotation post-release

**3. Code Obfuscation**
- ProGuard enabled
- Mapping file backed up (for crash deobfuscation)

**4. Permissions Audit**
- Only request necessary permissions
- Explain permissions in-app

---

## Troubleshooting

### Build Fails

**Error:** `TMDB_API_KEY not found`
- **Fix:** Create `local.properties` with API keys

**Error:** `Execution failed for task ':app:lintVitalRelease'`
- **Fix:** `./gradlew assembleMobileRelease --stacktrace`
- Check lint errors, fix or suppress

**Error:** `Failed to sign APK`
- **Fix:** Verify keystore path and passwords in `local.properties`

### Release APK Crashes

**ProGuard Issue:**
- **Fix:** Check `app/build/outputs/mapping/` for obfuscation mapping
- Add missing ProGuard rules
- Test again

**Missing Dependency:**
- **Fix:** Check `build.gradle.kts` for `releaseImplementation` vs `debugImplementation`

---

## Best Practices

1. **Always test release builds** on clean device (not upgrade)
2. **Keep keystore secure** (backup in 3 locations)
3. **Document every release** (changelog, tag, commit message)
4. **Gradual rollout** (10% → 50% → 100%) if possible
5. **Monitor for 48 hours** after release before full rollout

---

**Next Steps:** 
1. Generate release keystore
2. Configure signing
3. Build and test release APK
4. Choose distribution channel
5. Submit for review
