# LLM-Powered Movie Recommendations

## Overview
The app now uses **OpenAI's GPT-4o-mini** to generate intelligent, personalized movie recommendations instead of a basic algorithm!

## How It Works

### 1. **User Flow** (Unchanged)
- Select a genre
- Pick 5 movies you enjoyed
- Click "Get Recommendations"

### 2. **Behind the Scenes** (NEW!)
When you click "Get Recommendations":

1. **App sends to GPT**:
   - Your 5 movie titles
   - The genre you selected

2. **GPT analyzes and responds**:
   - Analyzes themes, mood, storytelling style
   - Writes a natural, conversational response
   - Recommends 10-15 movies with explanations
   - Explains why each fits your taste
   - Mix of popular and hidden gems

3. **App displays**:
   - The full text response from GPT
   - Easy to read format
   - Personal, like talking to a friend about movies!

## Setup Requirements

### Get an OpenAI API Key

1. Visit https://platform.openai.com/signup
2. Create a free account
3. Go to https://platform.openai.com/api-keys
4. Click "Create new secret key"
5. Copy your API key

### Add API Key to Project

Open `app/build.gradle.kts` and replace:
```kotlin
buildConfigField("String", "OPENAI_API_KEY", "\"YOUR_OPENAI_API_KEY_HERE\"")
```

With your actual key:
```kotlin
buildConfigField("String", "OPENAI_API_KEY", "\"sk-proj-abc123...\"")
```

### Cost

OpenAI pricing (as of 2025):
- **GPT-4o-mini**: ~$0.15 per million input tokens, $0.60 per million output tokens
- **Per recommendation**: ~$0.0002 - $0.0005 (less than a cent!)
- **1000 recommendations**: ~$0.20 - $0.50

Very affordable for personal use!

## Example

**If you select:**
1. Blade Runner 2049 (2017)
2. Ex Machina (2014)
3. Her (2013)
4. Moon (2009)
5. Arrival (2016)

**GPT might respond:**

```json
[
  {
    "title": "Annihilation",
    "year": 2018,
    "reasoning": "Shares the same contemplative, philosophical approach to sci-fi with stunning visuals and questions about identity."
  },
  {
    "title": "Under the Skin",
    "year": 2013,
    "reasoning": "Atmospheric exploration of alienation and humanity through a non-human perspective, similar to Ex Machina's themes."
  },
  {
    "title": "Solaris",
    "year": 1972,
    "reasoning": "Classic slow-burn sci-fi focusing on memory, love, and what it means to be human - matches your taste for thoughtful narratives."
  }
]
```

## Advantages Over Algorithm

### Old Algorithm:
- ❌ Just counted mentions in TMDB data
- ❌ No understanding of themes
- ❌ Biased toward popular movies
- ❌ Couldn't explain recommendations

### New LLM Approach:
- ✅ **Understands storytelling** - Recognizes themes, mood, narrative style
- ✅ **Finds hidden gems** - Not limited to what TMDB's algorithm suggests
- ✅ **Explains reasoning** - Tells you WHY each movie matches
- ✅ **More accurate** - Uses vast movie knowledge
- ✅ **Personalized** - Analyzes YOUR specific taste

## Technical Details

### Model: GPT-4o-mini
- Fast (~5-10 seconds for recommendations)
- Cost-effective
- Excellent movie knowledge
- Good at pattern recognition

### Prompt Engineering
The app uses a carefully crafted prompt that:
- Provides context about selected movies
- Requests specific format (JSON)
- Emphasizes themes and style over popularity
- Asks for hidden gems alongside known films

### Error Handling
- Falls back gracefully if LLM fails
- Retries search if exact movie not found on TMDB
- Handles partial results (shows what it found)

## Privacy & Data

- **No user data stored** by OpenAI (per their API policy)
- **Only movie titles sent** - nothing personal
- **Recommendations not logged** or used for training
- **Local processing** except for API calls

## Future Enhancements

Potential improvements:
- [ ] Use GPT-4 for even better recommendations (slightly more expensive)
- [ ] Add explanation display in UI
- [ ] Allow user to rate recommendations (feedback loop)
- [ ] Support for "more like this specific movie"
- [ ] Combine with user's watch history
- [ ] Multi-genre blending
- [ ] Director/actor style preferences

## Switching to Other LLMs

The code is designed to be modular. You can easily switch to:

### Gemini (Google):
- Free tier available
- Edit `LlmRecommendationService.kt`
- Change endpoint to Gemini API

### Claude (Anthropic):
- Excellent at reasoning
- Similar integration process

### Local LLM (Ollama):
- Run on your own hardware
- 100% private, no API costs
- Requires more powerful device

## Troubleshooting

**"LLM request failed"**
- Check your API key is correct
- Verify you have OpenAI credits
- Check internet connection

**"Could not find recommended movies on TMDB"**
- LLM may have suggested obscure titles
- Try different movies
- Check if movie titles are spelled correctly

**Takes too long**
- Normal: 10-20 seconds (LLM + TMDB searches)
- If over 30 seconds, check network

## Cost Monitoring

Track your usage at: https://platform.openai.com/usage

Set spending limits in your OpenAI account to avoid surprises!
