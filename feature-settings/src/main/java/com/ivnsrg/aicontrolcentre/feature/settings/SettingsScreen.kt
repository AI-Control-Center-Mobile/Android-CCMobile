package com.ivnsrg.aicontrolcentre.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppInfoCallout
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AppTextField
import com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone
import com.ivnsrg.aicontrolcentre.core.ui.components.CardTone
import com.ivnsrg.aicontrolcentre.core.ui.components.CompactActionButton
import com.ivnsrg.aicontrolcentre.core.ui.components.ConfirmDialog
import com.ivnsrg.aicontrolcentre.core.ui.components.HeaderDensity
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.OperationalCard
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionLabel
import com.ivnsrg.aicontrolcentre.core.ui.components.SecondaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.StatusBadge
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKeys: List<String> = emptyList(),
    val keyDraft: String = "",
    val saveStatus: SettingsSaveStatus = SettingsSaveStatus.Idle,
    val saveMessage: String? = null,
    val isClearing: Boolean = false,
) {
    val hasApiKeys: Boolean
        get() = apiKeys.isNotEmpty()
}

enum class SettingsSaveStatus {
    Idle,
    Saving,
    Saved,
    Failed,
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun updateKeyDraft(value: String) {
        _uiState.value = _uiState.value.copy(
            keyDraft = value,
            saveStatus = SettingsSaveStatus.Idle,
            saveMessage = null,
        )
    }

    fun refresh() {
        viewModelScope.launch {
            val keys = settingsRepository.getApiKeys()
            _uiState.value = _uiState.value.copy(apiKeys = keys)
        }
    }

    fun addKey() {
        val trimmed = _uiState.value.keyDraft.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(
                saveStatus = SettingsSaveStatus.Failed,
                saveMessage = "Введите OpenRouter API key",
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                saveStatus = SettingsSaveStatus.Saving,
                saveMessage = null,
            )
            settingsRepository.addApiKey(trimmed)
            val keys = settingsRepository.getApiKeys()
            val savedKey = settingsRepository.getPrimaryApiKey()
            val wasPersisted = trimmed in keys
            _uiState.value = _uiState.value.copy(
                apiKeys = keys,
                keyDraft = if (wasPersisted) "" else trimmed,
                saveStatus = if (wasPersisted) SettingsSaveStatus.Saved else SettingsSaveStatus.Failed,
                saveMessage = if (wasPersisted) {
                    "Ключ сохранён: ${maskApiKey(savedKey ?: trimmed)}"
                } else {
                    "Не удалось подтвердить сохранение ключа"
                },
            )
        }
    }

    fun removeKey(
        key: String,
        onAllKeysRemoved: () -> Unit,
    ) {
        viewModelScope.launch {
            settingsRepository.removeApiKey(key)
            val keys = settingsRepository.getApiKeys()
            _uiState.value = _uiState.value.copy(
                apiKeys = keys,
                saveStatus = SettingsSaveStatus.Idle,
                saveMessage = null,
            )
            if (keys.isEmpty()) {
                onAllKeysRemoved()
            }
        }
    }

    fun clearKeys(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearApiKey()
            _uiState.value = SettingsUiState()
            onDone()
        }
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearing = true)
            try {
                settingsRepository.clearAllLocalData()
                _uiState.value = SettingsUiState()
                onDone()
            } finally {
                if (_uiState.value != SettingsUiState()) {
                    _uiState.value = _uiState.value.copy(isClearing = false)
                }
            }
        }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun SettingsRoute(
    settingsRepository: SettingsRepository,
    onApiKeyRemoved: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepository))
    val uiState by viewModel.uiState.collectAsState()
    var pendingRemoveKey by remember { mutableStateOf<String?>(null) }
    var showClearKeysDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    pendingRemoveKey?.let { key ->
        ConfirmDialog(
            title = "Remove saved key?",
            message = "This removes the selected API key. If it is the last key, the app will return to setup.",
            confirmText = "Remove",
            onConfirm = {
                pendingRemoveKey = null
                viewModel.removeKey(key, onApiKeyRemoved)
            },
            onDismiss = { pendingRemoveKey = null },
        )
    }

    if (showClearKeysDialog) {
        ConfirmDialog(
            title = "Clear all API keys?",
            message = "This removes saved OpenRouter keys only. Projects, threads and messages stay on device.",
            confirmText = "Clear keys",
            onConfirm = {
                showClearKeysDialog = false
                viewModel.clearKeys(onApiKeyRemoved)
            },
            onDismiss = { showClearKeysDialog = false },
        )
    }

    if (showClearAllDialog) {
        ConfirmDialog(
            title = "Очистить все локальные данные?",
            message = "Будут удалены API key, проекты, треды и сообщения.",
            confirmText = "Очистить",
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
        onAddKey = viewModel::addKey,
        onRemoveKey = { pendingRemoveKey = it },
        onClearKeys = { showClearKeysDialog = true },
        onClearAll = { showClearAllDialog = true },
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onDraftChange: (String) -> Unit,
    onAddKey: () -> Unit,
    onRemoveKey: (String) -> Unit,
    onClearKeys: () -> Unit,
    onClearAll: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    AppScreenScaffold(
        title = "Settings",
        subtitle = "Infrastructure and device-local controls",
        headerDensity = HeaderDensity.Compact,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SectionLabel("Infrastructure")
            }
            item {
                OperationalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "OpenRouter API keys",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Secure local credential storage with automatic key failover",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            StatusBadge(
                                modifier = Modifier.padding(top = 2.dp),
                                text = if (uiState.hasApiKeys) "ACTIVE" else "MISSING",
                                tone = if (uiState.hasApiKeys) BadgeTone.Primary else BadgeTone.Warning,
                            )
                        }

                        AppTextField(
                            value = uiState.keyDraft,
                            onValueChange = onDraftChange,
                            label = "Add OpenRouter API key",
                        )
                        uiState.saveMessage?.let { message ->
                            MetadataChip(
                                text = message,
                                tone = when (uiState.saveStatus) {
                                    SettingsSaveStatus.Saved -> BadgeTone.Primary
                                    SettingsSaveStatus.Failed -> BadgeTone.Danger
                                    SettingsSaveStatus.Saving -> BadgeTone.Info
                                    SettingsSaveStatus.Idle -> BadgeTone.Neutral
                                },
                            )
                        }
                        PrimaryButton(
                            text = when (uiState.saveStatus) {
                                SettingsSaveStatus.Saving -> "Saving…"
                                else -> "Add key"
                            },
                            onClick = onAddKey,
                            enabled = !uiState.isClearing,
                        )
                        SecondaryButton(
                            text = "Clear saved keys",
                            onClick = onClearKeys,
                            enabled = uiState.apiKeys.isNotEmpty() && !uiState.isClearing,
                        )
                    }
                }
            }

            item {
                OperationalCard(
                    tone = CardTone.Surface2,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Saved keys", tone = BadgeTone.Info)
                        if (uiState.apiKeys.isEmpty()) {
                            Text(
                                text = "No saved OpenRouter keys yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                uiState.apiKeys.forEachIndexed { index, key ->
                                    SavedApiKeyRow(
                                        keyValue = key,
                                        isPrimary = index == 0,
                                        onRemove = { onRemoveKey(key) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionLabel("App architecture")
            }
            item {
                AppInfoCallout(
                    title = "Local-first protocol",
                    body = "SQLite persistence, encrypted key storage and no cloud sync. The current app state stays entirely on device.",
                )
            }
            item {
                OperationalCard(
                    tone = CardTone.Surface1,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetadataChip(text = "SQLite • No sync • Zero knowledge", tone = BadgeTone.Info)
                        Text(
                            text = "Model engine wired through OpenRouter repositories. Compare and thread flows reuse the same local-first project context.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            item {
                SectionLabel("Danger zone", tone = BadgeTone.Danger)
            }
            item {
                OperationalCard(
                    tone = CardTone.Danger,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                        text = "Permanently remove all local projects, threads, messages and credentials from this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    SecondaryButton(
                        text = if (uiState.isClearing) "Clearing…" else "Wipe all local data",
                        onClick = onClearAll,
                        enabled = !uiState.isClearing,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SavedApiKeyRow(
    keyValue: String,
    isPrimary: Boolean,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    OperationalCard(
        tone = if (isPrimary) CardTone.Surface1 else CardTone.Surface3,
        padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataChip(
                        text = if (isPrimary) "Primary" else "Fallback",
                        tone = if (isPrimary) BadgeTone.Primary else BadgeTone.Neutral,
                    )
                }
                Text(
                    text = maskApiKey(keyValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }
            CompactActionButton(
                text = "Remove",
                onClick = onRemove,
                tone = BadgeTone.Danger,
            )
        }
    }
}

internal fun maskApiKey(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length <= 10) return trimmed
    return "${trimmed.take(8)}…${trimmed.takeLast(4)}"
}
