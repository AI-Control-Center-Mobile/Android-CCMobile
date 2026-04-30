package com.ivnsrg.aicontrolcentre.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppCard
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AppTextField
import com.ivnsrg.aicontrolcentre.core.ui.components.ConfirmDialog
import com.ivnsrg.aicontrolcentre.core.ui.components.KeyValueRow
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionHeader
import com.ivnsrg.aicontrolcentre.core.ui.components.SecondaryButton
import com.ivnsrg.aicontrolcentre.core.ui.theme.LocalSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hasApiKey: Boolean = false,
    val keyDraft: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun updateKeyDraft(value: String) {
        _uiState.value = _uiState.value.copy(keyDraft = value, error = null)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                hasApiKey = !settingsRepository.getApiKey().isNullOrBlank(),
                error = null,
            )
        }
    }

    fun saveKey() {
        val trimmed = _uiState.value.keyDraft.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            runCatching {
                settingsRepository.saveApiKey(trimmed)
            }.onSuccess {
                _uiState.value = SettingsUiState(hasApiKey = true)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Failed to store the API key locally. Please try again.",
                )
            }
        }
    }

    fun clearKey(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                settingsRepository.clearApiKey()
            }.onSuccess {
                _uiState.value = _uiState.value.copy(hasApiKey = false, keyDraft = "", error = null)
                onDone()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to clear the API key. Please try again.",
                )
            }
        }
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                settingsRepository.clearAllLocalData()
            }.onSuccess {
                _uiState.value = SettingsUiState()
                onDone()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to wipe local data. Please try again.",
                )
            }
        }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(settingsRepository) as T
}

@Composable
fun SettingsRoute(
    settingsRepository: SettingsRepository,
    onApiKeyRemoved: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepository))
    val uiState by viewModel.uiState.collectAsState()
    var showClearKeyDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearKeyDialog) {
        ConfirmDialog(
            title = "Clear API key?",
            message = "This removes only the OpenRouter key. The setup flow will appear again next time the app needs credentials.",
            confirmText = "Clear Key",
            onConfirm = {
                showClearKeyDialog = false
                viewModel.clearKey(onApiKeyRemoved)
            },
            onDismiss = { showClearKeyDialog = false },
        )
    }

    if (showClearAllDialog) {
        ConfirmDialog(
            title = "Wipe all local data?",
            message = "This removes the API key, projects, threads, messages, and cached local state from this device.",
            confirmText = "Wipe Data",
            onConfirm = {
                showClearAllDialog = false
                viewModel.clearAllData(onApiKeyRemoved)
            },
            onDismiss = { showClearAllDialog = false },
        )
    }

    SettingsScreen(
        uiState = uiState,
        onDraftChange = viewModel::updateKeyDraft,
        onSaveKey = viewModel::saveKey,
        onClearKey = { showClearKeyDialog = true },
        onClearAll = { showClearAllDialog = true },
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onDraftChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onClearKey: () -> Unit,
    onClearAll: () -> Unit,
) {
    val spacing = LocalSpacing.current

    AppScreenScaffold(
        title = "Settings",
        subtitle = "Manage local credentials, storage, and app metadata.",
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            SectionHeader(title = "INFRASTRUCTURE")
            AppCard {
                MetadataChip(text = if (uiState.hasApiKey) "ACTIVE" else "MISSING")
                Text(
                    text = "OpenRouter API",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (uiState.hasApiKey) {
                        "The device has a locally encrypted API key."
                    } else {
                        "No API key is stored yet. Setup will be required before chat usage."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppTextField(
                    value = uiState.keyDraft,
                    onValueChange = onDraftChange,
                    label = "Rotate or replace key",
                    placeholder = "sk-or-v1-...",
                    enabled = !uiState.isSaving,
                    isSecret = true,
                )
                PrimaryButton(
                    text = if (uiState.isSaving) "Saving..." else "Save Key",
                    onClick = onSaveKey,
                    enabled = !uiState.isSaving,
                )
                SecondaryButton(text = "Clear Key", onClick = onClearKey)
                uiState.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SectionHeader(title = "APP ARCHITECTURE")
            AppCard {
                KeyValueRow(label = "Storage", value = "Room / DataStore")
                KeyValueRow(label = "Mode", value = "Local-first")
                KeyValueRow(label = "Credentials", value = "Encrypted")
                KeyValueRow(label = "Version", value = "MVP v2")
            }

            SectionHeader(title = "DANGER ZONE")
            AppCard {
                Text(
                    text = "Permanently remove all local projects, threads, messages, and credentials from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecondaryButton(text = "Wipe All Local Data", onClick = onClearAll)
            }
        }
    }
}
