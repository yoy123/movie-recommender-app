package com.movierecommender.app.ui.screens.firestick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.movierecommender.app.ui.viewmodel.firestick.MovieViewModel

@Composable
fun SettingsScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    PreferenceSettingsDialog(
        currentPreference = uiState.indiePreference,
        currentUserName = uiState.userName,
        onPreferenceChange = { viewModel.updateIndiePreference(it) },
        onUserNameChange = { viewModel.updateUserName(it) },
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
        isDarkMode = uiState.isDarkMode,
        onDarkModeChange = { viewModel.updateDarkMode(it) },
        onDismiss = onBackClick
    )
}
