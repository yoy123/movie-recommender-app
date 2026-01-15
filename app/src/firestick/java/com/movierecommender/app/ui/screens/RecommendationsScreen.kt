package com.movierecommender.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    // Auto-generate recommendations on first entry if we have selections and nothing yet
    LaunchedEffect(uiState.selectedMovies, uiState.recommendationText, uiState.isLoading) {
        if (!uiState.isLoading && uiState.recommendationText == null && uiState.selectedMovies.isNotEmpty()) {
            viewModel.generateRecommendations()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Analysis",
                        modifier = Modifier.padding(start = 16.dp)
                    ) 
                },
                navigationIcon = {
                    // TV-friendly back button with focus indicator
                    val backInteraction = remember { MutableInteractionSource() }
                    val backFocused by backInteraction.collectIsFocusedAsState()
                    
                    Surface(
                        modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (backFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (backFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary) else null
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            interactionSource = backInteraction
                        ) {
                            Icon(
                                Icons.Default.ArrowBack, 
                                "Back",
                                tint = if (backFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(if (backFocused) 32.dp else 24.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Retry button (only when recommendations exist)
                    if (!uiState.isLoading && uiState.recommendationText != null) {
                        val retryInteraction = remember { MutableInteractionSource() }
                        val retryFocused by retryInteraction.collectIsFocusedAsState()

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (retryFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                border = if (retryFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary) else null
                            ) {
                                IconButton(
                                    onClick = { viewModel.retryRecommendations() },
                                    interactionSource = retryInteraction
                                ) {
                                    Icon(
                                        Icons.Default.Autorenew,
                                        contentDescription = "Retry recommendations",
                                        tint = if (retryFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(if (retryFocused) 32.dp else 24.dp)
                                    )
                                }
                            }
                            Text(
                                text = "retry",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 8.sp,
                                modifier = Modifier.offset(y = (-8).dp)
                            )
                        }
                    }

                    // TV-friendly start over button with focus indicator
                    val refreshInteraction = remember { MutableInteractionSource() }
                    val refreshFocused by refreshInteraction.collectIsFocusedAsState()
                    
                    Surface(
                        modifier = Modifier.padding(end = 32.dp, top = 4.dp, bottom = 4.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (refreshFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (refreshFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary) else null
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.clearSelections()
                                onStartOver()
                            },
                            interactionSource = refreshInteraction
                        ) {
                            Icon(
                                Icons.Default.Refresh, 
                                "Start Over",
                                tint = if (refreshFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(if (refreshFocused) 32.dp else 24.dp)
                            )
                        }
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
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 96.dp),
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
                                .align(Alignment.CenterHorizontally)
                                .padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.error ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Focusable retry button
                            val retryInteraction = remember { MutableInteractionSource() }
                            val retryFocused by retryInteraction.collectIsFocusedAsState()

                            Button(
                                onClick = { viewModel.generateRecommendations() },
                                interactionSource = retryInteraction,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (retryFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                                ),
                                border = if (retryFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Text(
                                    "Retry",
                                    color = if (retryFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    else -> {
                        val displayText = uiState.recommendationText
                        if (displayText == null) {
                            Text(
                                text = "No recommendations yet",
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 96.dp)
                            )
                        } else {
                            val cleaned = displayText
                                .replace("```json", "")
                                .replace("```", "")
                                .replace("**", "")
                                .trim()
                            val items = parseRecommendationItems(cleaned)
                            val intro = extractIntro(cleaned)

                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = 64.dp,
                                    end = 64.dp,
                                    top = 24.dp,
                                    bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Selected movies card - focusable
                                item {
                                    FocusableCard(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Column(modifier = Modifier.padding(20.dp)) {
                                            Text(
                                                text = "Based on your selection:",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontSize = 20.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            uiState.selectedMovies.forEach { movie ->
                                                Text(
                                                    text = "• ${movie.title}",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontSize = 18.sp,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Analysis section - focusable
                                if (intro.isNotBlank()) {
                                    item {
                                        FocusableCard(containerColor = Color.White) {
                                            Column(modifier = Modifier.padding(20.dp)) {
                                                Text(
                                                    text = "Analysis",
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 26.sp,
                                                    color = Color.Black
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = intro.lines()
                                                        .filter { it.isNotBlank() }
                                                        .dropWhile { it.trim().equals("Analysis", ignoreCase = true) || it.trim().equals("Analysis:", ignoreCase = true) }
                                                        .joinToString("\n"),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontSize = 18.sp,
                                                    lineHeight = 28.sp,
                                                    color = Color(0xFF333333)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (items.isNotEmpty()) {
                                    item {
                                        FocusableCard(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text(
                                                text = "RECOMMENDATIONS",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 24.sp,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                                            )
                                        }
                                    }
                                }

                                items.forEach { item ->
                                    item {
                                        RecommendationCard(
                                            item = item,
                                            viewModel = viewModel,
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
                            fontSize = 18.sp,
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
                            color = Color(0xFF424242), // Darker gray for better readability
                            fontSize = 20.sp,
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
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 30.sp
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
private fun FocusableCard(
    containerColor: Color,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusable(interactionSource = interactionSource),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp
        )
    ) {
        content()
    }
}

@Composable
private fun RecommendationCard(
    item: RecItem,
    viewModel: MovieViewModel,
    onOpenTrailer: (title: String, youtubeKey: String) -> Unit,
    onWatchNow: (title: String, magnetUrl: String) -> Unit
) {
    val context = LocalContext.current
    var magnetUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingMagnet by remember { mutableStateOf(false) }
    val cardInteraction = remember { MutableInteractionSource() }
    val cardFocused by cardInteraction.collectIsFocusedAsState()
    
    val rating by produceState<String?>(initialValue = null, key1 = item.title, key2 = item.year) {
        value = try { viewModel.getTmdbRatingByTitleYear(item.title, item.year) } catch (e: Exception) { null }
    }
    val trailerUrl by produceState<String?>(initialValue = null, key1 = item.title, key2 = item.year) {
        value = try { viewModel.getImdbTrailerUrlByTitle(item.title, item.year) } catch (e: Exception) { null }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusable(interactionSource = cardInteraction),
        colors = CardDefaults.cardColors(
            containerColor = if (cardFocused) MaterialTheme.colorScheme.primaryContainer else Color.White
        ),
        border = if (cardFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (cardFocused) 8.dp else 2.dp
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val displayTitle = if (!item.year.isNullOrBlank()) {
                "${item.title} (${item.year})"
            } else {
                item.title
            }
            val ratingText = rating?.takeIf { it.isNotBlank() }
            
            // Movie number and title
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                        append("${item.number}. ")
                    }
                    append(displayTitle)
                    if (ratingText != null) {
                        append("  ")
                        withStyle(SpanStyle(color = Color(0xFF666666), fontSize = 16.sp)) {
                            append("★ $ratingText")
                        }
                    }
                },
                style = MaterialTheme.typography.titleLarge,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (cardFocused) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black
            )
            
            // Description
            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    color = if (cardFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else Color(0xFF444444)
                )
            }
            
            // Watch Trailer button
            Spacer(modifier = Modifier.height(12.dp))
            val trailerInteraction = remember { MutableInteractionSource() }
            val trailerFocused by trailerInteraction.collectIsFocusedAsState()
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val url = trailerUrl
                        if (url != null && url.isNotBlank()) {
                            onOpenTrailer(item.title, url)
                        } else {
                            Toast.makeText(context, "No trailer available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    interactionSource = trailerInteraction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (trailerFocused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    ),
                    border = if (trailerFocused) BorderStroke(2.dp, Color.White) else null,
                    modifier = Modifier.focusable(interactionSource = trailerInteraction)
                ) {
                    Text(
                        "Watch Trailer",
                        fontSize = 16.sp
                    )
                }
                
                // Watch Now button
                val watchNowInteraction = remember { MutableInteractionSource() }
                val watchNowFocused by watchNowInteraction.collectIsFocusedAsState()
                val coroutineScope = rememberCoroutineScope()
                
                Button(
                    onClick = {
                        if (magnetUrl != null) {
                            onWatchNow(item.title, magnetUrl!!)
                        } else if (!isLoadingMagnet) {
                            isLoadingMagnet = true
                            coroutineScope.launch {
                                try {
                                    val displayTitle = if (!item.year.isNullOrBlank()) {
                                        "${item.title} (${item.year})"
                                    } else {
                                        item.title
                                    }
                                    val magnet = viewModel.getTorrentMagnetUrl(item.title, item.year)
                                    if (magnet != null) {
                                        magnetUrl = magnet
                                        onWatchNow(item.title, magnet)
                                    } else {
                                        Toast.makeText(context, "No streaming source found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error finding source", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoadingMagnet = false
                                }
                            }
                        }
                    },
                    interactionSource = watchNowInteraction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (watchNowFocused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                    ),
                    border = if (watchNowFocused) BorderStroke(2.dp, Color.White) else null,
                    modifier = Modifier.focusable(interactionSource = watchNowInteraction),
                    enabled = !isLoadingMagnet
                ) {
                    if (isLoadingMagnet) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (isLoadingMagnet) "Finding..." else "Watch Now",
                        fontSize = 16.sp
                    )
                }
            }
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
