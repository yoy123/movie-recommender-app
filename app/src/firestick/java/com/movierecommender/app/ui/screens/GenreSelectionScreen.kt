@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.movierecommender.app.ui.screens.firestick

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.ContentMode
import com.movierecommender.app.ui.leanback.LeanbackActionButton
import com.movierecommender.app.ui.leanback.LeanbackPanel
import com.movierecommender.app.ui.leanback.LeanbackTextButton
import com.movierecommender.app.ui.leanback.LeanbackTopBar
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModel

@Composable
fun GenreSelectionScreen(
    viewModel: MovieViewModel,
    onGenreSelected: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Determine which tab is selected based on content mode
    val selectedTabIndex = when (uiState.contentMode) {
        ContentMode.MOVIES -> 0
        ContentMode.TV_SHOWS -> 1
    }
    
    Scaffold(
        topBar = {
            LeanbackTopBar(
                title = if (uiState.contentMode == ContentMode.MOVIES) "Movie Genres" else "TV Show Genres",
                subtitle = if (uiState.selectedMovies.isNotEmpty()) {
                    "${uiState.selectedMovies.size} movie picks ready"
                } else {
                    "Browse categories with the remote"
                },
                actions = {
                    if (uiState.selectedMovies.isNotEmpty()) {
                        LeanbackActionButton(
                            icon = Icons.Default.DeleteSweep,
                            label = "Clear",
                            onClick = { viewModel.clearSelections() }
                        )
                    }

                    LeanbackActionButton(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = { showSettingsDialog = true }
                    )
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), MaterialTheme.shapes.large)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Movies Tab
                    val moviesInteraction = remember { MutableInteractionSource() }
                    val moviesFocused by moviesInteraction.collectIsFocusedAsState()
                    
                    Surface(
                        onClick = { viewModel.setContentMode(ContentMode.MOVIES) },
                        modifier = Modifier
                            .weight(1f)
                            .focusable(interactionSource = moviesInteraction),
                        shape = MaterialTheme.shapes.medium,
                        color = if (selectedTabIndex == 0) 
                            MaterialTheme.colorScheme.primary 
                        else if (moviesFocused) 
                            MaterialTheme.colorScheme.secondaryContainer
                        else 
                            MaterialTheme.colorScheme.surface,
                        border = if (moviesFocused) 
                            BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimaryContainer) 
                        else null,
                        tonalElevation = if (moviesFocused) 8.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = "Movies",
                                modifier = Modifier.size(if (moviesFocused) 28.dp else 24.dp),
                                tint = if (selectedTabIndex == 0) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Movies",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selectedTabIndex == 0) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // TV Shows Tab
                    val tvInteraction = remember { MutableInteractionSource() }
                    val tvFocused by tvInteraction.collectIsFocusedAsState()
                    
                    Surface(
                        onClick = { viewModel.setContentMode(ContentMode.TV_SHOWS) },
                        modifier = Modifier
                            .weight(1f)
                            .focusable(interactionSource = tvInteraction),
                        shape = MaterialTheme.shapes.medium,
                        color = if (selectedTabIndex == 1) 
                            MaterialTheme.colorScheme.primary 
                        else if (tvFocused) 
                            MaterialTheme.colorScheme.secondaryContainer
                        else 
                            MaterialTheme.colorScheme.surface,
                        border = if (tvFocused) 
                            BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimaryContainer) 
                        else null,
                        tonalElevation = if (tvFocused) 8.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Tv,
                                contentDescription = "TV Shows",
                                modifier = Modifier.size(if (tvFocused) 28.dp else 24.dp),
                                tint = if (selectedTabIndex == 1) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "TV Shows",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selectedTabIndex == 1) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Get the current genres list based on content mode
            val currentGenres = when (uiState.contentMode) {
                ContentMode.MOVIES -> uiState.genres
                ContentMode.TV_SHOWS -> uiState.tvGenres
            }
            
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
                        Button(onClick = { 
                            when (uiState.contentMode) {
                                ContentMode.MOVIES -> viewModel.loadGenres()
                                ContentMode.TV_SHOWS -> viewModel.loadTvGenres()
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
                currentGenres.isEmpty() -> {
                    Text(
                        text = "No genres available",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = 48.dp,
                            end = 48.dp,
                            top = 24.dp,
                            bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Add Favorites as first item (Movies only for now)
                        if (uiState.contentMode == ContentMode.MOVIES) {
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
                        }
                        
                        // Genre cards
                        items(currentGenres) { genre ->
                            GenreCard(
                                name = genre.name,
                                icon = if (uiState.contentMode == ContentMode.MOVIES) 
                                    Icons.Default.Movie else Icons.Default.Tv,
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

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        LeanbackPanel(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .wrapContentHeight()
        ) {
            Text(
                "Welcome to Movie Recommender!",
                style = MaterialTheme.typography.headlineSmall
            )

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
                        text = "Your personalized favorites will be called \"${nameText.trim()}'s Favorites\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LeanbackTextButton(
                    label = "Get Started",
                    onClick = {
                        if (nameText.trim().isNotEmpty()) {
                            onNameEntered(nameText.trim())
                        }
                    },
                    emphasized = true,
                    enabled = nameText.trim().isNotEmpty()
                )
            }
        }
    }
}

@Composable
fun FocusableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        border = if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
        ),
        content = content
    )
}

// Arrow key controllable slider for Fire TV D-pad
@Composable
fun ArrowKeySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 1f,
    step: Float = 0.05f
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Surface(
        modifier = modifier
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            val newValue = (value - step).coerceAtLeast(minValue)
                            onValueChange(newValue)
                            true
                        }
                        Key.DirectionRight -> {
                            val newValue = (value + step).coerceAtMost(maxValue)
                            onValueChange(newValue)
                            true
                        }
                        else -> false
                    }
                } else false
            },
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◄",
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            
            LinearProgressIndicator(
                progress = (value - minValue) / (maxValue - minValue),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .height(8.dp),
            )
            
            Text(
                text = "►",
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
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
    releaseYearEnd: Float = 2026f,
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        LeanbackPanel(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.9f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                LeanbackTextButton(
                    label = "Done",
                    onClick = onDismiss,
                    emphasized = true
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // Name field - TV friendly
                Text(
                    text = "Your Name",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                var isNameEditing by remember { mutableStateOf(false) }
                
                TvTextField(
                    value = nameText,
                    onValueChange = { 
                        nameText = it
                        onUserNameChange(it)
                    },
                    isActive = isNameEditing,
                    onActiveChange = { isNameEditing = it },
                    placeholder = "Enter your first name",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
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
                    val switchInteraction = remember { MutableInteractionSource() }
                    val switchFocused by switchInteraction.collectIsFocusedAsState()
                    
                    Surface(
                        modifier = Modifier.padding(4.dp),
                        shape = MaterialTheme.shapes.small,
                        border = if (switchFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                        color = if (switchFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = onDarkModeChange,
                            interactionSource = switchInteraction,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LeanbackTextButton(
                    label = "Done",
                    onClick = onDismiss,
                    emphasized = true
                )
            }
        }
    }
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
            
            // D-pad arrow key controllable slider
            ArrowKeySlider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "${(value * 100).toInt()}% - Use ◄► arrows to adjust",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
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
                    text = "2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Start year slider
            Text(
                text = "From: ${startYear.toInt()} - Use ◄► arrows",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            ArrowKeySlider(
                value = startYear,
                onValueChange = { newYear ->
                    if (newYear < endYear) onStartYearChange(newYear)
                },
                modifier = Modifier.fillMaxWidth(),
                minValue = 1950f,
                maxValue = 2024f,
                step = 5f
            )
            
            // End year slider
            Text(
                text = "To: ${endYear.toInt()} - Use ◄► arrows",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            ArrowKeySlider(
                value = endYear,
                onValueChange = { newYear ->
                    if (newYear > startYear) onEndYearChange(newYear)
                },
                modifier = Modifier.fillMaxWidth(),
                minValue = 1951f,
                maxValue = 2026f,
                step = 5f
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
        
        // D-pad friendly buttons + progress bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FocusableButton(
                onClick = { if (value > 0f) onValueChange((value - 0.05f).coerceAtLeast(0f)) },
                modifier = Modifier.width(48.dp).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("◄", style = MaterialTheme.typography.titleMedium)
            }
            
            LinearProgressIndicator(
                progress = value,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .height(8.dp),
            )
            
            FocusableButton(
                onClick = { if (value < 1f) onValueChange((value + 0.05f).coerceAtMost(1f)) },
                modifier = Modifier.width(48.dp).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("►", style = MaterialTheme.typography.titleMedium)
            }
        }
        
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GenreCard(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Movie,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 4.dp
        )
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
                    modifier = Modifier.size(if (isFocused) 36.dp else 32.dp),
                    tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
            }
        }
    }
}

// TV-friendly text field that only opens keyboard on Enter press
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isActive: Boolean,
    onActiveChange: (Boolean) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    if (isActive) {
        // Active mode - show actual text field for input
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                onActiveChange(false)
                                keyboardController?.hide()
                                true
                            }
                            Key.Escape, Key.Back -> {
                                onActiveChange(false)
                                keyboardController?.hide()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            placeholder = { Text(placeholder) },
            singleLine = true
        )
        
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    } else {
        // Inactive mode - show focusable surface that opens keyboard on Enter
        Surface(
            modifier = modifier
                .focusable(interactionSource = interactionSource)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && 
                        (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)) {
                        onActiveChange(true)
                        true
                    } else false
                },
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            ),
            color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (value.isEmpty()) "Press Enter to edit: $placeholder" else value,
                    color = if (value.isEmpty()) {
                        if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer 
                        else MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
