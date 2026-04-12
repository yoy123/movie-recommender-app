package com.movierecommender.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.TvShow
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.ui.viewmodel.MovieViewModel
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onStartOver: () -> Unit,
    onOpenTrailer: (title: String, youtubeKey: String) -> Unit,
    onWatchNow: (title: String, magnetUrl: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val isTvMode = uiState.contentMode == ContentMode.TV_SHOWS
    val hasSelections = if (isTvMode) uiState.selectedTvShows.isNotEmpty() else uiState.selectedMovies.isNotEmpty()
    val contentLabel = if (isTvMode) "TV shows" else "movies"

    // Auto-generate recommendations on first entry if we have selections and nothing yet
    LaunchedEffect(uiState.selectedMovies, uiState.selectedTvShows, uiState.recommendationText, uiState.isLoading, isTvMode) {
        if (!uiState.isLoading && uiState.recommendationText == null && hasSelections) {
            if (isTvMode) {
                viewModel.generateTvRecommendations()
            } else {
                viewModel.generateRecommendations()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!uiState.isLoading && uiState.recommendationText != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = { 
                                if (isTvMode) viewModel.retryTvRecommendations() 
                                else viewModel.retryRecommendations() 
                            }) {
                                Icon(Icons.Default.Autorenew, "Retry recommendations")
                            }
                            Text(
                                text = "retry",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 8.sp,
                                modifier = Modifier.offset(y = (-8).dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.clearSelections()
                            onStartOver()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, "Start Over")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Analyzing your taste...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                    }
                    uiState.error != null -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = uiState.error ?: "Unknown error",
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { 
                                if (isTvMode) viewModel.generateTvRecommendations() 
                                else viewModel.generateRecommendations() 
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {
                        val displayText = uiState.recommendationText
                        if (displayText == null) {
                            Text(
                                text = "No recommendations yet",
                                modifier = Modifier.align(Alignment.Center)
                            )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp)
                                            ) {
                                                Text(
                                                    text = "Based on your selection:",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                if (isTvMode) {
                                                    uiState.selectedTvShows.forEach { tvShow ->
                                                        Column(modifier = Modifier.fillMaxWidth()) {
                                                            Text(
                                                                text = "• ${tvShow.name}",
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    uiState.selectedMovies.forEach { movie ->
                                                        Column(modifier = Modifier.fillMaxWidth()) {
                                                            Text(
                                                                text = "• ${movie.title}",
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = androidx.compose.ui.graphics.Color.White
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp)
                                            ) {
                                                ParsedRecommendationsList(
                                                    text = displayText,
                                                    viewModel = viewModel,
                                                    isTvMode = isTvMode,
                                                    onOpenTrailer = onOpenTrailer,
                                                    onWatchNow = onWatchNow
                                                )
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendedMovieCard(movie: Movie) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w500${movie.posterPath}",
                contentDescription = movie.title,
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = movie.overview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "★ ${String.format("%.1f", movie.voteAverage)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    movie.releaseDate?.let {
                        Text(
                            text = it.take(4),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FormattedRecommendationText(text: String) {
    // Clean up the text first - remove any JSON/markdown artifacts
    val cleanedText = text
        .replace("```json", "")
        .replace("```", "")
        .replace("**", "")
        .trim()
    
    val annotatedString = buildAnnotatedString {
        val lines = cleanedText.lines()
        var inMovieList = false
        val movieEntryRegex = """^(\d+)[\.)\-:]\s*(.+)$""".toRegex()
        val titleExtractionRegex = """^(.+?(?:\(\d{4}\))?)(?:\s*[-–—:]\s*(.+))?${'$'}""".toRegex()
        
        lines.forEach { line ->
            val trimmedLine = line.trim()
            
            when {
                // Skip empty lines
                trimmedLine.isEmpty() -> {
                    append("\n")
                }
                
                // Detect "Analysis" header
                trimmedLine.uppercase().startsWith("ANALYSIS") -> {
                    val display = trimmedLine.trimEnd(':').trim()
                    withStyle(
                        style = SpanStyle(
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    ) {
                        append("\n$display\n")
                    }
                }
                
                // Detect "RECOMMENDATIONS:" header or similar
                trimmedLine.uppercase().startsWith("RECOMMENDATIONS") -> {
                    inMovieList = true
                    withStyle(
                        style = SpanStyle(
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    ) {
                        val display = trimmedLine.trimEnd(':').trim()
                        append("\n${display.uppercase()}\n")
                    }
                }
                
                // Numbered movie entries like "1. Movie Title (Year)"
                movieEntryRegex.matches(trimmedLine) -> {
                    inMovieList = true
                    val match = movieEntryRegex.find(trimmedLine)
                    if (match != null) {
                        val number = match.groupValues[1]
                        val restOfLine = match.groupValues[2]

                        val titleMatch = titleExtractionRegex.find(restOfLine)
                        val movieTitle = titleMatch?.groupValues?.getOrNull(1)?.trim() ?: restOfLine.trim()
                        val inlineDescription = titleMatch?.groupValues?.getOrNull(2)?.trim().orEmpty()
                        val description = if (inlineDescription.isNotEmpty() && inlineDescription != movieTitle) {
                            inlineDescription
                        } else {
                            restOfLine.removePrefix(movieTitle)
                                .trimStart('-', '–', '—', ':', ' ')
                                .trim()
                        }

                        append("\n")
                        withStyle(
                            style = SpanStyle(
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        ) {
                            append("$number. ")
                        }
                        
                        withStyle(
                            style = SpanStyle(
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        ) {
                            append(movieTitle)
                        }
                        append("\n")

                        if (description.isNotEmpty()) {
                            withStyle(
                                style = SpanStyle(
                                    color = Color(0xFF424242),
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            ) {
                                append(description)
                                append("\n")
                            }
                        }
                    }
                }
                
                // Regular description lines (when in movie list)
                inMovieList && !trimmedLine.startsWith("RECOMMENDATIONS") -> {
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFF424242), // Dark gray
                            fontSize = 15.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    ) {
                        append(trimmedLine)
                        append("\n")
                    }
                }
                
                // Introduction/analysis text (before movie list)
                !inMovieList -> {
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFF616161), // Medium gray
                            fontSize = 15.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append(trimmedLine)
                        append("\n")
                    }
                }
            }
        }
    }
    
    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = 22.sp
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private data class RecItem(
    val number: Int,
    val title: String,
    val year: String?,
    val description: String
)

@Composable
private fun ParsedRecommendationsList(
    text: String,
    viewModel: MovieViewModel,
    isTvMode: Boolean = false,
    onOpenTrailer: (title: String, youtubeKey: String) -> Unit,
    onWatchNow: (title: String, magnetUrl: String) -> Unit
) {
    val cleaned = text
        .replace("```json", "")
        .replace("```", "")
        .replace("**", "")
        .trim()
    val items = parseRecommendationItems(cleaned)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Optional intro: grab any analysis before numbered list
        val intro = extractIntro(cleaned)
        if (intro.isNotBlank()) {
            val introLines = intro.lines().filter { it.isNotBlank() }
            val firstLine = introLines.firstOrNull()?.trim() ?: ""
            val hasAnalysisHeading = firstLine.equals("Analysis", ignoreCase = true) ||
                firstLine.equals("Analysis:", ignoreCase = true)
            val analysisBody = if (hasAnalysisHeading) {
                introLines.drop(1).joinToString("\n").trim()
            } else {
                introLines.joinToString("\n").trim()
            }

            if (hasAnalysisHeading) {
                Text(
                    text = "Analysis",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (analysisBody.isNotBlank()) {
                Text(
                    text = analysisBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF616161)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (items.isEmpty()) {
            // Fallback: formatted text + retry
            FormattedRecommendationText(cleaned)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "If this looks off, tap retry to fetch again.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF777777)
            )
        } else {
            Text(
                text = "RECOMMENDATIONS",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { item ->
                RecommendationRow(item = item, viewModel = viewModel, isTvMode = isTvMode, onOpenTrailer = onOpenTrailer, onWatchNow = onWatchNow)
            }
        }
    }
}

@Composable
private fun RecommendationRow(
    item: RecItem,
    viewModel: MovieViewModel,
    isTvMode: Boolean = false,
    onOpenTrailer: (title: String, youtubeKey: String) -> Unit,
    onWatchNow: (title: String, magnetUrl: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val rating by produceState<String?>(initialValue = null, key1 = item.title, key2 = item.year) {
        value = try { viewModel.getTmdbRatingByTitleYear(item.title, item.year) } catch (e: Exception) { null }
    }
    val trailerUrl by produceState<String?>(initialValue = null, key1 = item.title, key2 = item.year) {
        value = try { viewModel.getTrailerUrlByTitle(item.title, item.year, isTvMode) } catch (e: Exception) { null }
    }

    // Watch options dialog state
    var showWatchOptions by remember { mutableStateOf(false) }
    var watchOptions by remember { mutableStateOf<List<com.movierecommender.app.data.model.WatchOption>>(emptyList()) }
    var isLoadingWatchOptions by remember { mutableStateOf(false) }
    var resolvedTmdbId by remember { mutableStateOf<Int?>(null) }

    // TV show episode picker state
    var showEpisodePicker by remember { mutableStateOf(false) }
    var isLoadingSeasons by remember { mutableStateOf(false) }
    var availableSeasons by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf(1) }
    var resolvedImdbId by remember { mutableStateOf<String?>(null) }
    Column {
        val displayTitle = if (!item.year.isNullOrBlank()) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }
        val titleStyle = MaterialTheme.typography.titleMedium.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        val ratingText = rating?.takeIf { it.isNotBlank() }
        Text(
            text = buildAnnotatedString {
                append("${item.number}. $displayTitle")
                if (ratingText != null) {
                    append(" ")
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF0F172A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append("• TMDB $ratingText")
                    }
                }
            },
            style = titleStyle,
            color = Color.Black
        )
        if (!item.description.isBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF424242)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val url = trailerUrl
                android.util.Log.d("RecommendationsScreen", "Trailer button clicked: title=${item.title}, url=$url")
                if (url != null && url.isNotBlank()) {
                    android.util.Log.d("RecommendationsScreen", "Calling onOpenTrailer with URL length: ${url.length}")
                    onOpenTrailer(item.title, url)
                    android.util.Log.d("RecommendationsScreen", "onOpenTrailer called successfully")
                } else {
                    android.util.Log.w("RecommendationsScreen", "No trailer URL available")
                    Toast.makeText(context, if (isTvMode) "No trailer available for this TV show" else "No trailer available for this movie", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Watch Trailer")
            }
            
            TextButton(onClick = {
                if (!isLoadingWatchOptions) {
                    isLoadingWatchOptions = true
                    showWatchOptions = true
                    coroutineScope.launch {
                        try {
                            val tmdbId = resolvedTmdbId ?: run {
                                val id = viewModel.searchTmdbIdByTitle(item.title, item.year, isTvMode)
                                resolvedTmdbId = id
                                id
                            }
                            if (tmdbId != null) {
                                val options = if (isTvMode) {
                                    viewModel.getTvShowWatchOptions(tmdbId, item.title, item.year)
                                } else {
                                    viewModel.getMovieWatchOptions(tmdbId, item.title, item.year)
                                }
                                watchOptions = options
                            } else {
                                // Fallback: try torrent only for movies
                                if (!isTvMode) {
                                    val torrentResult = try {
                                        viewModel.getTorrentMagnetUrlForContent(item.title, item.year, false)
                                    } catch (e: Exception) { null }
                                    watchOptions = if (torrentResult != null) {
                                        listOf(com.movierecommender.app.data.model.WatchOption(
                                            name = "Torrent: ${torrentResult.second}",
                                            type = com.movierecommender.app.data.model.WatchOptionType.TORRENT,
                                            logoPath = null,
                                            packageName = null,
                                            deepLinkUrl = null,
                                            justWatchLink = null,
                                            magnetUrl = torrentResult.first,
                                            quality = torrentResult.second,
                                            seeds = null,
                                            provider = null
                                        ))
                                    } else {
                                        emptyList()
                                    }
                                } else {
                                    watchOptions = emptyList()
                                }
                            }
                        } catch (e: Exception) {
                            watchOptions = emptyList()
                        } finally {
                            isLoadingWatchOptions = false
                        }
                    }
                }
            }, enabled = !isLoadingWatchOptions) {
                Text(if (isLoadingWatchOptions) "Finding..." else "Watch Options")
            }
        }

        // Watch Options Dialog
        if (showWatchOptions) {
            WatchOptionsDialog(
                title = item.title,
                options = watchOptions,
                isLoading = isLoadingWatchOptions,
                onDismiss = {
                    showWatchOptions = false
                    watchOptions = emptyList()
                },
                onTorrentSelected = { magnetUrl ->
                    showWatchOptions = false
                    watchOptions = emptyList()
                    onWatchNow(item.title, magnetUrl)
                },
                onBrowseEpisodes = if (isTvMode) {
                    {
                        showWatchOptions = false
                        watchOptions = emptyList()
                        isLoadingSeasons = true
                        coroutineScope.launch {
                            try {
                                val result = viewModel.getSeasonsForTvShowByTitle(item.title, item.year)
                                if (result != null) {
                                    resolvedImdbId = result.first
                                    availableSeasons = result.second
                                    selectedSeason = result.second.first()
                                    showEpisodePicker = true
                                } else {
                                    Toast.makeText(context, "No episodes available", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error finding show", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoadingSeasons = false
                            }
                        }
                    }
                } else null
            )
        }

        // Episode Picker Dialog for TV shows
        if (showEpisodePicker && resolvedImdbId != null) {
            EpisodePickerDialog(
                showName = item.title,
                seasons = availableSeasons,
                selectedSeason = selectedSeason,
                onSeasonSelected = { selectedSeason = it },
                onEpisodeSelected = { episode ->
                    showEpisodePicker = false
                    coroutineScope.launch {
                        try {
                            val magnet = viewModel.getTvEpisodeMagnetUrl(
                                showTitle = item.title,
                                imdbId = resolvedImdbId,
                                season = selectedSeason,
                                episode = episode
                            )
                            if (magnet != null) {
                                onWatchNow("${item.title} S${selectedSeason}E${episode}", magnet)
                            } else {
                                Toast.makeText(context, "No streaming source found", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error finding source", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { showEpisodePicker = false },
                viewModel = viewModel,
                preResolvedImdbId = resolvedImdbId
            )
        }
    }
}

private fun parseRecommendationItems(text: String): List<RecItem> {
    val lines = text.lines()
    // Accept formats like: "1. Title", "1 .Title", "1) Title", "1 - Title", "1:Title"
    val numbered = Regex("^\\s*(\\d{1,2})\\s*[\\.)\\-:]\\s*(.+)$")
    val yearRegex = Regex("\\((\\d{4})\\)")
    val inlineSplitRegex = Regex("^(.+?(?:\\(\\d{4}\\))?)(?:\\s*[-–—:]\\s*(.+))?$")
    val items = mutableListOf<RecItem>()
    var i = 0
    while (i < lines.size && items.size < 15) {
        val line = lines[i].trim()
        val m = numbered.find(line)
        if (m != null) {
            val num = m.groupValues[1].toInt()
            val titleRaw = m.groupValues[2].trim()
            val splitMatch = inlineSplitRegex.find(titleRaw)
            val baseSegment = splitMatch?.groupValues?.getOrNull(1)?.trim() ?: titleRaw
            val inlineDesc = splitMatch?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }

            val year = yearRegex.find(baseSegment)?.groupValues?.getOrNull(1)
            val title = baseSegment.replace(yearRegex, "").trim().trimEnd('-', '–', '—', ':')

            var desc = inlineDesc ?: ""
            var j = i + 1
            if (desc.isBlank()) {
                while (j < lines.size) {
                    val l = lines[j].trim()
                    if (l.isEmpty()) { j++; continue }
                    if (numbered.containsMatchIn(l)) break
                    desc = l
                    j++
                    break
                }
            }
            items.add(RecItem(num, title, year, desc.trim()))
            i = j
        } else {
            i++
        }
    }
    // Ensure max 15
    return items.take(15)
}

private fun extractIntro(text: String): String {
    val lines = text.lines()
    val numbered = Regex("^\\s*(\\d{1,2})\\s*[\\.)\\-:]\\s*")
    val firstNumberIndex = lines.indexOfFirst { numbered.containsMatchIn(it) }.takeIf { it >= 0 }
    val recommendationsIndex = lines.indexOfFirst { line ->
        line.trim().uppercase().startsWith("RECOMMENDATIONS")
    }.takeIf { it >= 0 }
    val cutoff = listOfNotNull(firstNumberIndex, recommendationsIndex).minOrNull()
    if (cutoff == null || cutoff <= 0) return ""
    val introLines = lines.take(cutoff).dropLastWhile { it.isBlank() }
    return introLines.joinToString("\n").trim()
}
