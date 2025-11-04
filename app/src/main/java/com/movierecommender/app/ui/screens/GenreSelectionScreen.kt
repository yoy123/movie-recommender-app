@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.movierecommender.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.ui.viewmodel.MovieViewModel

@Composable
fun GenreSelectionScreen(
    viewModel: MovieViewModel,
    onGenreSelected: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select a Genre") },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
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
                        Button(onClick = { viewModel.loadGenres() }) {
                            Text("Retry")
                        }
                    }
                }
                uiState.genres.isEmpty() -> {
                    Text(
                        text = "No genres available",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Add Dee's Favorites as first item
                        item {
                            GenreCard(
                                name = "${uiState.userName}'s Favorites",
                                icon = Icons.Default.Favorite,
                                onClick = {
                                    viewModel.selectGenre(-1, "${uiState.userName}'s Favorites")
                                    onGenreSelected()
                                }
                            )
                        }
                        
                        // Regular genres
                        items(uiState.genres) { genre ->
                            GenreCard(
                                name = genre.name,
                                icon = Icons.Default.Movie,
                                onClick = {
                                    viewModel.selectGenre(genre.id, genre.name)
                                    onGenreSelected()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Welcome Dialog (First Run)
    if (uiState.isFirstRun) {
        WelcomeDialog(
            onNameEntered = { name ->
                viewModel.updateUserName(name)
                viewModel.completeFirstRun()
            }
        )
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        PreferenceSettingsDialog(
            currentPreference = uiState.indiePreference,
            currentUserName = uiState.userName,
            onPreferenceChange = { viewModel.updateIndiePreference(it) },
            onUserNameChange = { viewModel.updateUserName(it) },
            // Toggles and values
            useIndiePreference = uiState.useIndiePreference,
            usePopularityPreference = uiState.usePopularityPreference,
            releaseYearStart = uiState.releaseYearStart,
            releaseYearEnd = uiState.releaseYearEnd,
            useReleaseYearPreference = uiState.useReleaseYearPreference,
            tonePreference = uiState.tonePreference,
            useTonePreference = uiState.useTonePreference,
            internationalPreference = uiState.internationalPreference,
            useInternationalPreference = uiState.useInternationalPreference,
            experimentalPreference = uiState.experimentalPreference,
            useExperimentalPreference = uiState.useExperimentalPreference,
            popularityPreference = uiState.popularityPreference,
            // Callbacks
            onUseIndieChange = { viewModel.updateUseIndiePreference(it) },
            onUsePopularityChange = { viewModel.updateUsePopularityPreference(it) },
            onReleaseYearStartChange = { viewModel.updateReleaseYearStart(it) },
            onReleaseYearEndChange = { viewModel.updateReleaseYearEnd(it) },
            onUseReleaseYearChange = { viewModel.updateUseReleaseYearPreference(it) },
            onToneChange = { viewModel.updateTonePreference(it) },
            onUseToneChange = { viewModel.updateUseTonePreference(it) },
            onInternationalChange = { viewModel.updateInternationalPreference(it) },
            onUseInternationalChange = { viewModel.updateUseInternationalPreference(it) },
            onExperimentalChange = { viewModel.updateExperimentalPreference(it) },
            onUseExperimentalChange = { viewModel.updateUseExperimentalPreference(it) },
            onPopularityChange = { viewModel.updatePopularityPreference(it) },
            // Dark mode
            isDarkMode = uiState.isDarkMode,
            onDarkModeChange = { viewModel.updateDarkMode(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun WelcomeDialog(
    onNameEntered: (String) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = { }, // Prevent dismissing without entering name
        title = { 
            Text(
                "Welcome to Movie Recommender!",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Let's personalize your experience.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "What's your first name?",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter your name") },
                    singleLine = true
                )
                
                if (nameText.isNotEmpty()) {
                    Text(
                        text = "Your personalized favorites will be called \"${nameText.trim()}'s Favorites\" 🎬",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (nameText.trim().isNotEmpty()) {
                        onNameEntered(nameText.trim())
                    }
                },
                enabled = nameText.trim().isNotEmpty()
            ) {
                Text("Get Started")
            }
        }
    )
}

@Composable
fun PreferenceSettingsDialog(
    currentPreference: Float,
    currentUserName: String,
    onPreferenceChange: (Float) -> Unit,
    onUserNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    // All preference values
    useIndiePreference: Boolean = true,
    usePopularityPreference: Boolean = true,
    releaseYearStart: Float = 1980f,
    releaseYearEnd: Float = 2025f,
    useReleaseYearPreference: Boolean = true,
    tonePreference: Float = 0.5f,
    useTonePreference: Boolean = true,
    internationalPreference: Float = 0.5f,
    useInternationalPreference: Boolean = true,
    experimentalPreference: Float = 0.5f,
    useExperimentalPreference: Boolean = true,
    popularityPreference: Float = 0.5f,
    // All callbacks
    onUseIndieChange: (Boolean) -> Unit = {},
    onUsePopularityChange: (Boolean) -> Unit = {},
    onReleaseYearStartChange: (Float) -> Unit = {},
    onReleaseYearEndChange: (Float) -> Unit = {},
    onUseReleaseYearChange: (Boolean) -> Unit = {},
    onToneChange: (Float) -> Unit = {},
    onUseToneChange: (Boolean) -> Unit = {},
    onInternationalChange: (Float) -> Unit = {},
    onUseInternationalChange: (Boolean) -> Unit = {},
    onExperimentalChange: (Float) -> Unit = {},
    onUseExperimentalChange: (Boolean) -> Unit = {},
    onPopularityChange: (Float) -> Unit = {},
    // Dark mode
    isDarkMode: Boolean = true,
    onDarkModeChange: (Boolean) -> Unit = {}
) {
    var nameText by remember { mutableStateOf(currentUserName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Name field
                Text(
                    text = "Your Name",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { 
                        nameText = it
                        onUserNameChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    placeholder = { Text("Enter your first name") },
                    singleLine = true
                )
                
                Text(
                    text = "This will personalize your Favorites section to \"${nameText.ifEmpty { "Your" }}'s Favorites\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Dark/Light Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (isDarkMode) "Dark Mode" else "Light Mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = onDarkModeChange
                    )
                }
                
                // Divider
                Divider(modifier = Modifier.padding(bottom = 16.dp))
                
                Text(
                    text = "Recommendation Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 1. Indie vs Blockbuster
                PreferenceSliderWithToggle(
                    title = "Production Style",
                    leftLabel = "Blockbusters",
                    rightLabel = "Indie Films",
                    value = currentPreference,
                    enabled = useIndiePreference,
                    onValueChange = onPreferenceChange,
                    onEnabledChange = onUseIndieChange
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 2. Popularity Level
                PreferenceSliderWithToggle(
                    title = "Popularity Level",
                    leftLabel = "Cult Classics",
                    rightLabel = "Mainstream",
                    value = popularityPreference,
                    enabled = usePopularityPreference,
                    onValueChange = onPopularityChange,
                    onEnabledChange = onUsePopularityChange
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 3. Release Year Range (Dual Slider)
                ReleaseYearRangeSlider(
                    startYear = releaseYearStart,
                    endYear = releaseYearEnd,
                    enabled = useReleaseYearPreference,
                    onStartYearChange = onReleaseYearStartChange,
                    onEndYearChange = onReleaseYearEndChange,
                    onEnabledChange = onUseReleaseYearChange
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 4. Tone/Mood
                PreferenceSliderWithToggle(
                    title = "Tone/Mood",
                    leftLabel = "Light & Uplifting",
                    rightLabel = "Dark & Serious",
                    value = tonePreference,
                    enabled = useTonePreference,
                    onValueChange = onToneChange,
                    onEnabledChange = onUseToneChange
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 5. International vs Domestic
                PreferenceSliderWithToggle(
                    title = "Geographic Focus",
                    leftLabel = "Domestic",
                    rightLabel = "International",
                    value = internationalPreference,
                    enabled = useInternationalPreference,
                    onValueChange = onInternationalChange,
                    onEnabledChange = onUseInternationalChange
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 6. Experimental vs Traditional
                PreferenceSliderWithToggle(
                    title = "Storytelling Style",
                    leftLabel = "Traditional",
                    rightLabel = "Experimental",
                    value = experimentalPreference,
                    enabled = useExperimentalPreference,
                    onValueChange = onExperimentalChange,
                    onEnabledChange = onUseExperimentalChange
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun PreferenceSliderWithToggle(
    title: String,
    leftLabel: String,
    rightLabel: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
        
        if (enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = leftLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = rightLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
            
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun ReleaseYearRangeSlider(
    startYear: Float,
    endYear: Float,
    enabled: Boolean,
    onStartYearChange: (Float) -> Unit,
    onEndYearChange: (Float) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Release Year Range",
                style = MaterialTheme.typography.titleSmall
            )
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
        
        if (enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "From ${startYear.toInt()} to ${endYear.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1950",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "2025",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Start year slider
            Text(
                text = "Earliest: ${startYear.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = startYear,
                onValueChange = { newStart ->
                    // Ensure start is never after end
                    if (newStart <= endYear) {
                        onStartYearChange(newStart)
                    }
                },
                valueRange = 1950f..2025f,
                steps = 74, // 75 years total
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary
                )
            )
            
            // End year slider
            Text(
                text = "Latest: ${endYear.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = endYear,
                onValueChange = { newEnd ->
                    // Ensure end is never before start
                    if (newEnd >= startYear) {
                        onEndYearChange(newEnd)
                    }
                },
                valueRange = 1950f..2025f,
                steps = 74, // 75 years total
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary
                )
            )
        }
    }
}

@Composable
fun PreferenceSlider(
    title: String,
    leftLabel: String,
    rightLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun GenreCard(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Movie,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
