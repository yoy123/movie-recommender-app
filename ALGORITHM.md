# Improved Recommendation Algorithm

## Overview
The new algorithm focuses on **genre accuracy**, **film style matching**, and **discovering hidden gems** rather than just popular movies.

## How It Works

### 1. **Genre Filtering (Strict)**
- Identifies the primary genre from your 5 selected movies
- **Only recommends movies that match this genre**
- Ensures thematic consistency

### 2. **Keyword/Theme Analysis**
- Fetches detailed metadata for each selected movie
- Extracts keywords (e.g., "dystopia", "revenge", "heist", "coming of age")
- Finds common themes across your selections
- Prioritizes movies with matching keywords

### 3. **Film Style Matching**
- Analyzes similarities in:
  - Story themes
  - Mood and tone
  - Narrative structure
  - Character types
- Uses TMDB's "similar movies" endpoint (better than "recommendations" for style)

### 4. **Quality Over Popularity**
- **Hidden Gems Bonus**: Movies with 100-5,000 votes get extra points
- Rating similarity: Matches the average rating of your selections
- Doesn't require blockbuster status

### 5. **Scoring System**

Each candidate movie earns points for:

| Factor | Points | Purpose |
|--------|--------|---------|
| **Genre Match** | 50 | Must match primary genre |
| **Matching Keywords** | 15 per match | Style/theme similarity |
| **Similar Rating** | 20 | Quality consistency |
| **Hidden Gem** (100-5K votes) | 15 | Discover lesser-known films |
| **Mid-Range** (5K-10K votes) | 10 | Balanced recognition |
| **Well-Known** (10K+ votes) | 5 | Can include hits |
| **High Quality** (7.0+ rating) | 10 | Good films bonus |
| **Recommendation Count** | 10 per occurrence | Multiple sources |

**Minimum Score**: 50 points (ensures relevance)

### 6. **Result Diversity**
- Pulls from multiple pages of similar/recommended movies
- Combines suggestions from all 5 of your selections
- Returns top 25 matches (increased from 20)

## Example

**If you select 5 sci-fi films about AI/dystopia:**
- ✅ Recommends: Indie sci-fi with AI themes, even if lesser-known
- ✅ Includes: Mix of hidden gems AND popular films if they match
- ❌ Excludes: Popular action movies that don't match the theme
- ❌ Excludes: Romantic comedies, even if highly rated

## Key Improvements

### Old Algorithm:
- ❌ Just counted how many times a movie was recommended
- ❌ Favored blockbusters (high popularity scores)
- ❌ Ignored genre boundaries
- ❌ No theme/style analysis

### New Algorithm:
- ✅ Strictly filters by genre
- ✅ Analyzes film themes and keywords
- ✅ Rewards hidden gems
- ✅ Matches your taste profile
- ✅ Balances quality and relevance

## Technical Details

### API Calls Per Recommendation:
- 5 movie details (for keywords)
- 15 similar/recommendation queries (3 per movie)
- ~20-30 candidate detail fetches (for scoring)

### Performance:
- Takes 10-20 seconds (more API calls for better accuracy)
- Caches results in Room database
- Worth the wait for much better recommendations!

## Future Enhancements

Potential improvements:
- [ ] Director/actor similarity
- [ ] Decade/era matching
- [ ] Runtime preferences
- [ ] Language/country origin filters
- [ ] Cinematography style analysis
- [ ] User feedback loop (like/dislike)
