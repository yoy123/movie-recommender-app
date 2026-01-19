# TROUBLESHOOTING.md

**Last Updated:** 2026-01-19  
**Status:** SOURCE-OF-TRUTH MEMORY

## Overview

Common problems, error messages, and solutions for the Movie Recommender app. Organized by symptom → diagnosis → fix.

---

## Quick Diagnosis

| Symptom | Likely Cause | See Section |
|---------|--------------|-------------|
| App crashes on launch | Missing API keys | [#1](#1-app-crashes-on-launch) |
| "No recommendations found" | LLM API failure | [#2](#2-recommendations-fail) |
| Movies not loading | TMDB API issue | [#3](#3-movies-dont-load) |
| Favorites disappeared | Database migration | [#4](#4-favorites-disappeared-after-update) |
| Streaming won't start | No seeders / network | [#5](#5-streaming-wont-start) |
| DPAD navigation broken | Focus lost | [#6](#6-fire-tv-dpad-not-working) |
| App very slow | Cache full | [#7](#7-app-is-very-slow) |
| "429 Too Many Requests" | TMDB rate limit | [#8](#8-http-429-too-many-requests) |

---

## Common Issues

### 1. App Crashes on Launch

**Symptoms:**
- App opens briefly, then crashes
- Black screen, then closes
- "Unfortunately, Movie Recommender has stopped"

**Error in Logcat:**
```
java.lang.RuntimeException: Unable to create application
Caused by: java.lang.IllegalStateException: TMDB_API_KEY not found
```

**Diagnosis:** Missing API keys in build configuration.

**Fix:**

1. **Create `local.properties` file** in project root:
   ```properties
   TMDB_API_KEY=your_tmdb_api_key_here
   OPENAI_API_KEY=sk-proj-your_openai_key_here
   ```

2. **Get TMDB API Key:**
   - Go to https://www.themoviedb.org/settings/api
   - Sign up for free account
   - Request API key
   - Copy "API Key (v3 auth)"

3. **Get OpenAI API Key:**
   - Go to https://platform.openai.com/api-keys
   - Create account
   - Create new API key
   - Copy key (starts with `sk-proj-`)

4. **Rebuild:**
   ```bash
   ./gradlew clean
   ./gradlew assembleMobileDebug
   adb install -r app/build/outputs/apk/mobile/debug/app-mobile-debug.apk
   ```

---

### 2. Recommendations Fail

**Symptoms:**
- "Unable to generate recommendations" error message
- Loading spinner runs forever
- Empty recommendations screen

**Possible Causes:**

#### 2a. OpenAI API Failure

**Check Logcat:**
```
LlmRecommendationService: OpenAI API call failed: 401 Unauthorized
```

**Fix:**
- Verify `OPENAI_API_KEY` is correct in `local.properties`
- Check OpenAI account has available credits: https://platform.openai.com/usage
- Verify key has not expired

#### 2b. Network Connectivity

**Check Logcat:**
```
IOException: Unable to resolve host "api.openai.com"
```

**Fix:**
- Enable Wi-Fi/mobile data
- Verify firewall not blocking app
- Test with `curl https://api.openai.com` on same network

#### 2c. LLM Response Parse Error

**Check Logcat:**
```
LlmRecommendationService: Failed to parse recommendations from response
```

**Fix:**
- This is an app bug (LLM returned unexpected format)
- Retry recommendations (button on screen)
- Fallback algorithm should activate automatically
- Report bug with logcat output

---

### 3. Movies Don't Load

**Symptoms:**
- Empty genre selection screen
- No movies in movie selection
- "Unable to load movies" error

**Possible Causes:**

#### 3a. TMDB API Key Invalid

**Check Logcat:**
```
TmdbApiService: HTTP 401 Unauthorized
```

**Fix:**
- Verify `TMDB_API_KEY` in `local.properties`
- Test key: `curl "https://api.themoviedb.org/3/genre/movie/list?api_key=YOUR_KEY"`

#### 3b. Network Timeout

**Check Logcat:**
```
SocketTimeoutException: timeout
```

**Fix:**
- Check network speed (TMDB requires ~1 Mbps minimum)
- Retry (pull to refresh)

#### 3c. TMDB Service Down

**Check Status:**
- Visit https://www.themoviedb.org/
- Check https://twitter.com/themoviedb for outage announcements

**Fix:**
- Wait for service to restore
- No user action possible

---

### 4. Favorites Disappeared After Update

**Symptoms:**
- All favorites gone after app update
- Database empty
- "[Name]'s Favorites" screen empty

**Diagnosis:** Destructive migration triggered (see [KNOWN_ISSUES.md #1](KNOWN_ISSUES.md#1))

**Why This Happens:**
- Room database version changed (schema update)
- `fallbackToDestructiveMigration()` drops all tables
- All data lost permanently

**Fix:**
- **No recovery possible** (data not backed up)
- Re-add favorites manually

**Prevention (For Developers):**
- Implement proper Room migrations (see [DATA_STORAGE.md §6.3](DATA_STORAGE.md#issue-3-destructive-migration))
- Users: Avoid updating app during development phase

---

### 5. Streaming Won't Start

**Symptoms:**
- "Downloading movie..." notification never progresses
- "Unable to connect" error
- Notification disappears after 30 seconds

**Possible Causes:**

#### 5a. No Seeders Available

**Check Logcat:**
```
TorrentStreamService: No seeders found for torrent
```

**Fix:**
- Movie not available for streaming (rare/obscure title)
- Try different movie
- Use "Watch Trailer" instead

#### 5b. Network Firewall

**Diagnosis:**
- ISP blocks torrent traffic
- Corporate/school network restricts P2P

**Fix:**
- Switch to different network
- Use VPN (if allowed)
- Try at home network

#### 5c. Torrent API Down

**Check Logcat:**
```
YtsApiService: HTTP 503 Service Unavailable
```

**Fix:**
- Wait and retry
- YTS/Popcorn API may be down temporarily

---

### 6. Fire TV DPAD Not Working

**Symptoms:**
- Remote buttons don't respond
- Focus stuck on one element
- Can't navigate with D-pad

**Possible Causes:**

#### 6a. Focus Lost

**Diagnosis:** UI state changed, focus reset

**Fix:**
- Press Home button → relaunch app
- Navigate using touch (if Fire TV supports touch)

#### 6b. Focusable Elements Missing

**Diagnosis:** Screen loaded without focusable components

**Fix (Developers):**
- Add `Modifier.focusable()` to all interactive elements
- Use `FocusRequester` to set initial focus

**Fix (Users):**
- Restart app
- Report bug with screen name

---

### 7. App Is Very Slow

**Symptoms:**
- UI lags
- Scrolling choppy
- High battery drain

**Possible Causes:**

#### 7a. Cache Full

**Check:** Settings → Storage → App info → Cache

**Fix:**
```bash
adb shell pm clear com.movierecommender.app
```
Or in app: Settings → Clear cache

#### 7b. Too Many Favorites/Movies in DB

**Diagnosis:** 10,000+ movies in database

**Fix (Developers):**
- Implement cleanup (see [KNOWN_ISSUES.md #6](KNOWN_ISSUES.md#6))

**Fix (Users):**
- Clear app data: Settings → Apps → Movie Recommender → Clear data
- Warning: Loses all favorites

#### 7c. Background Processes

**Check Logcat:**
```
TorrentStreamService: Running in background
```

**Fix:**
- Swipe away notification to stop download
- Force stop app in Settings

---

### 8. HTTP 429 Too Many Requests

**Symptoms:**
- "Too many requests" error
- Recommendations fail after multiple attempts
- Movies stop loading

**Diagnosis:** TMDB rate limit exceeded (40 requests / 10 seconds)

**Current Behavior:**
- App has **no retry logic** (see [KNOWN_ISSUES.md #2](KNOWN_ISSUES.md#2))
- Fails immediately

**Fix:**
- Wait 10 seconds, retry
- Avoid rapid-fire recommendation retries
- Restart app to reset

**For Developers:**
- Implement exponential backoff
- Add request queue with delays

---

## Error Messages Explained

### "No internet connection"

**Meaning:** Device offline or network unreachable

**Fix:**
- Enable Wi-Fi or mobile data
- Check airplane mode off
- Restart device

---

### "Invalid API key"

**Meaning:** TMDB_API_KEY or OPENAI_API_KEY wrong/expired

**Fix:**
- Regenerate keys
- Update `local.properties`
- Rebuild app

---

### "Unable to parse response"

**Meaning:** API returned unexpected format

**Likely Cause:**
- TMDB changed response structure (rare)
- LLM returned malformed response

**Fix:**
- Retry request
- Update app (if patch released)

---

### "Movie not found"

**Meaning:** TMDB search returned no results

**Possible Reasons:**
- Typo in movie title
- Movie not in TMDB database
- LLM hallucinated title (doesn't exist)

**Fix:**
- Manual search for similar title
- Report issue if repeated

---

### "Torrent not available"

**Meaning:** No torrent found on YTS/Popcorn APIs

**Possible Reasons:**
- Movie too new (not pirated yet)
- Movie too obscure (no uploads)
- APIs down

**Fix:**
- Use "Watch Trailer" instead
- Try different movie

---

## Performance Issues

### Slow Startup

**Expected:** 1-2 seconds cold start  
**If slower:** Check background apps, restart device

---

### High Memory Usage

**Expected:** 100-200 MB during normal use  
**If higher:** Clear cache, restart app

**Check Memory:**
```bash
adb shell dumpsys meminfo com.movierecommender.app
```

---

### Battery Drain

**Expected:** ~5% per hour (idle), ~30% per hour (streaming)  
**If higher:**
- Torrent download running in background
- Close app completely (swipe from recents)

---

## Debugging Tools

### Enable Verbose Logging

**ADB Command:**
```bash
adb shell setprop log.tag.MovieRecommender VERBOSE
adb logcat -s MovieRecommender:V
```

**Filter Logs:**
```bash
# LLM service
adb logcat -s LlmRecommendation:V

# TMDB API
adb logcat -s TmdbApi:V

# Torrent streaming
adb logcat -s TorrentStream:V
```

---

### Check Network Requests

**Use Charles Proxy / Wireshark:**
1. Set proxy on device
2. Monitor HTTPS traffic
3. Verify API calls succeed

---

### Inspect Database

**ADB Pull Database:**
```bash
adb root
adb pull /data/data/com.movierecommender.app/databases/movie_recommender_database .
sqlite3 movie_recommender_database

# Inspect tables
.tables
SELECT * FROM movies LIMIT 10;
SELECT COUNT(*) FROM movies WHERE isFavorite = 1;
```

---

## Getting Help

### Before Reporting Bugs

1. **Check this document** for known issues
2. **Check [KNOWN_ISSUES.md](KNOWN_ISSUES.md)** for documented bugs
3. **Collect logcat output:**
   ```bash
   adb logcat > logcat.txt
   ```
4. **Note exact steps to reproduce**

### Reporting Bugs

**Include:**
- Device model (e.g., Samsung Galaxy S21, Fire TV Stick 4K)
- Android version
- App version (Settings → About)
- Exact error message
- Steps to reproduce
- Logcat output (if available)

**Where to Report:**
- GitHub Issues: https://github.com/yoy123/movie-recommender-app/issues
- Email: (if provided)

---

## FAQ

**Q: Why does the app request so many permissions?**  
A: Internet (API calls), storage (cache), notifications (download progress).

**Q: Is my data sent to OpenAI?**  
A: Yes, selected movie titles + preferences sent to generate recommendations. No personal info sent. See privacy policy.

**Q: Why are some movies not available for streaming?**  
A: No seeders found on torrent networks. Try different movie or use trailer preview.

**Q: Can I use the app offline?**  
A: No, all features require internet. Future versions may add offline mode for browsing favorites.

**Q: Why did my favorites disappear after updating?**  
A: Database migration bug. Will be fixed in future update. For now, re-add favorites manually.

**Q: Is torrenting legal?**  
A: Depends on content and jurisdiction. App is neutral technology. User responsible for compliance with local laws.

---

**Still having issues?** Check [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for complete list of documented problems and fixes.
