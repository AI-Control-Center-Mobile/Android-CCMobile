package com.ivnsrg.aicontrolcentre.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.ProviderApiKey
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaRepository
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaSnapshot
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaSource
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaStatus
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppCard
import com.ivnsrg.aicontrolcentre.core.ui.components.AppDropdownField
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
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionHeader
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionLabel
import com.ivnsrg.aicontrolcentre.core.ui.components.SecondaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.StatusBadge
import com.ivnsrg.aicontrolcentre.core.ui.theme.LocalSpacing
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val providerKeys: List<ProviderApiKey> = emptyList(),
    val selectedProvider: ModelProvider = ModelProvider.OPEN_ROUTER,
    val keyDraft: String = "",
    val saveStatus: SettingsSaveStatus = SettingsSaveStatus.Idle,
    val saveMessage: String? = null,
    val isClearing: Boolean = false,
    val quotaSnapshots: List<ProviderQuotaSnapshot> = emptyList(),
    val isLoadingQuota: Boolean = false,
)

enum class SettingsSaveStatus {
    Idle,
    Saving,
    Saved,
    Failed,
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val providerQuotaRepository: ProviderQuotaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectProvider(provider: ModelProvider) {
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                saveStatus = SettingsSaveStatus.Idle,
                saveMessage = null,
            )
        }
    }

    fun updateKeyDraft(value: String) {
        _uiState.update {
            it.copy(
                keyDraft = value,
                saveStatus = SettingsSaveStatus.Idle,
                saveMessage = null,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val keys = settingsRepository.getProviderKeys()
            val quotas = providerQuotaRepository.getQuotaSnapshots()
            _uiState.update {
                it.copy(
                    providerKeys = keys,
                    quotaSnapshots = quotas,
                    isLoadingQuota = false,
                )
            }
        }
    }

    fun saveKey() {
        val state = _uiState.value
        val trimmed = state.keyDraft.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    saveStatus = SettingsSaveStatus.Failed,
                    saveMessage = "Enter a key before saving.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    saveStatus = SettingsSaveStatus.Saving,
                    saveMessage = null,
                )
            }
            settingsRepository.saveApiKey(state.selectedProvider, trimmed)
            val keys = settingsRepository.getProviderKeys()
            val persisted = keys.firstOrNull { it.provider == state.selectedProvider }?.key
            _uiState.update {
                it.copy(
                    providerKeys = keys,
                    keyDraft = if (persisted != null) "" else trimmed,
                    saveStatus = if (persisted != null) SettingsSaveStatus.Saved else SettingsSaveStatus.Failed,
                    saveMessage = if (persisted != null) {
                        "${state.selectedProvider.displayName} key saved locally."
                    } else {
                        "Could not confirm the saved key."
                    },
                )
            }
            refreshQuota()
        }
    }

    fun removeKey(
        provider: ModelProvider,
        onAllKeysRemoved: () -> Unit,
    ) {
        viewModelScope.launch {
            settingsRepository.clearApiKey(provider)
            val keys = settingsRepository.getProviderKeys()
            _uiState.update { it.copy(providerKeys = keys) }
            refreshQuota()
            if (keys.isEmpty()) {
                onAllKeysRemoved()
            }
        }
    }

    fun clearAllKeys(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearAllApiKeys()
            _uiState.value = SettingsUiState()
            onDone()
        }
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            try {
                settingsRepository.clearAllLocalData()
                _uiState.value = SettingsUiState()
                onDone()
            } finally {
                if (_uiState.value != SettingsUiState()) {
                    _uiState.update { it.copy(isClearing = false) }
                }
            }
        }
    }

    fun refreshQuota() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingQuota = true) }
            val quotas = providerQuotaRepository.refreshQuotaSnapshots()
            _uiState.update { it.copy(quotaSnapshots = quotas, isLoadingQuota = false) }
        }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val providerQuotaRepository: ProviderQuotaRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepository, providerQuotaRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun SettingsRoute(
    settingsRepository: SettingsRepository,
    providerQuotaRepository: ProviderQuotaRepository,
    onApiKeyRemoved: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(settingsRepository, providerQuotaRepository),
    )
    val uiState by viewModel.uiState.collectAsState()
    var pendingRemoveProvider by remember { mutableStateOf<ModelProvider?>(null) }
    var showClearKeysDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    pendingRemoveProvider?.let { provider ->
        ConfirmDialog(
            title = "Remove saved key?",
            message = "This removes the selected provider key. If it is the last configured key, the app will return to setup.",
            confirmText = "Remove",
            onConfirm = {
                pendingRemoveProvider = null
                viewModel.removeKey(provider, onApiKeyRemoved)
            },
            onDismiss = { pendingRemoveProvider = null },
        )
    }

    if (showClearKeysDialog) {
        ConfirmDialog(
            title = "Clear all provider keys?",
            message = "This removes saved provider keys only. Projects, threads and messages stay on device.",
            confirmText = "Clear keys",
            onConfirm = {
                showClearKeysDialog = false
                viewModel.clearAllKeys(onApiKeyRemoved)
            },
            onDismiss = { showClearKeysDialog = false },
        )
    }

    if (showClearAllDialog) {
        ConfirmDialog(
            title = "Clear all local data?",
            message = "This removes saved keys, projects, threads and messages.",
            confirmText = "Clear data",
            onConfirm = {
                showClearAllDialog = false
                viewModel.clearAllData(onApiKeyRemoved)
            },
            onDismiss = { showClearAllDialog = false },
        )
    }

    SettingsScreen(
        uiState = uiState,
        onProviderSelected = viewModel::selectProvider,
        onDraftChange = viewModel::updateKeyDraft,
        onSaveKey = viewModel::saveKey,
        onRemoveKey = { pendingRemoveProvider = it },
        onClearKeys = { showClearKeysDialog = true },
        onClearAll = { showClearAllDialog = true },
        onRefreshQuota = viewModel::refreshQuota,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onProviderSelected: (ModelProvider) -> Unit,
    onDraftChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onRemoveKey: (ModelProvider) -> Unit,
    onClearKeys: () -> Unit,
    onClearAll: () -> Unit,
    onRefreshQuota: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val spacing = LocalSpacing.current

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
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            contentPadding = PaddingValues(bottom = spacing.xxl),
        ) {
            item {
                SectionHeader("INFRASTRUCTURE")
            }

            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Provider keys",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Use a single form to select a provider, paste a key and save it locally.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                            StatusBadge(
                                text = if (uiState.providerKeys.isNotEmpty()) "ACTIVE" else "MISSING",
                                tone = if (uiState.providerKeys.isNotEmpty()) BadgeTone.Primary else BadgeTone.Warning,
                            )
                        }
                        AppDropdownField(
                            label = "Provider",
                            selectedText = uiState.selectedProvider.displayName,
                            options = ModelProvider.entries.map { it.displayName },
                            onOptionSelected = { index -> onProviderSelected(ModelProvider.entries[index]) },
                            enabled = !uiState.isClearing && uiState.saveStatus != SettingsSaveStatus.Saving,
                        )
                        AppTextField(
                            value = uiState.keyDraft,
                            onValueChange = onDraftChange,
                            label = uiState.selectedProvider.keyLabel,
                            placeholder = uiState.selectedProvider.keyPlaceholder,
                            isSecret = true,
                            enabled = !uiState.isClearing && uiState.saveStatus != SettingsSaveStatus.Saving,
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
                            text = if (uiState.saveStatus == SettingsSaveStatus.Saving) "Saving…" else "Save key",
                            onClick = onSaveKey,
                            enabled = !uiState.isClearing && uiState.saveStatus != SettingsSaveStatus.Saving,
                        )
                    }
                }
            }

            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SectionLabel("Saved keys", tone = BadgeTone.Info)
                        if (uiState.providerKeys.isEmpty()) {
                            Text(
                                text = "No provider keys saved yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                uiState.providerKeys.forEach { key ->
                                    SavedProviderKeyRow(
                                        provider = key.provider,
                                        keyValue = key.key,
                                        onRemove = { onRemoveKey(key.provider) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Provider quota and diagnostics",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Shows the remaining quota each provider actually exposes: live diagnostics, balances, or last known rate-limit snapshots.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                            StatusBadge(
                                text = if (uiState.quotaSnapshots.any { it.status == ProviderQuotaStatus.AVAILABLE }) "READY" else "WAITING",
                                tone = if (uiState.quotaSnapshots.any { it.status == ProviderQuotaStatus.AVAILABLE }) BadgeTone.Primary else BadgeTone.Warning,
                            )
                        }
                        SecondaryButton(
                            text = if (uiState.isLoadingQuota) "Refreshing…" else "Refresh provider data",
                            onClick = onRefreshQuota,
                            enabled = !uiState.isClearing && !uiState.isLoadingQuota,
                        )
                        if (uiState.quotaSnapshots.isEmpty()) {
                            Text(
                                text = "Configure provider keys to load diagnostics and quota data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                uiState.quotaSnapshots.forEach { snapshot ->
                                    ProviderQuotaCard(snapshot)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SecondaryButton(
                    text = "Clear saved keys",
                    onClick = onClearKeys,
                    enabled = uiState.providerKeys.isNotEmpty() && !uiState.isClearing,
                )
            }

            item {
                SectionHeader("DANGER ZONE")
            }
            item {
                AppCard {
                    MetadataChip(text = "DESTRUCTIVE", tone = BadgeTone.Danger)
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(
                            text = "Permanently remove all local projects, threads, messages and saved keys from this device.",
                            style = MaterialTheme.typography.bodyMedium,
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
private fun SavedProviderKeyRow(
    provider: ModelProvider,
    keyValue: String,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    OperationalCard(
        tone = CardTone.Surface3,
        padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataChip(text = provider.displayName, tone = BadgeTone.Info)
                    MetadataChip(text = "Saved key", tone = BadgeTone.Neutral)
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

@Composable
private fun ProviderQuotaCard(
    snapshot: ProviderQuotaSnapshot,
) {
    val colors = MaterialTheme.appColors
    val tone = when (snapshot.status) {
        ProviderQuotaStatus.AVAILABLE -> CardTone.Surface2
        ProviderQuotaStatus.UNAVAILABLE -> CardTone.Surface3
        ProviderQuotaStatus.ERROR -> CardTone.Danger
    }

    OperationalCard(
        tone = tone,
        padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetadataChip(text = snapshot.provider.displayName, tone = BadgeTone.Info)
                        MetadataChip(
                            text = when (snapshot.source) {
                                ProviderQuotaSource.LIVE -> "LIVE"
                                ProviderQuotaSource.SNAPSHOT -> "SNAPSHOT"
                            },
                            tone = if (snapshot.source == ProviderQuotaSource.LIVE) BadgeTone.Primary else BadgeTone.Warning,
                        )
                    }
                    Text(
                        text = snapshot.headline,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                StatusBadge(
                    text = when (snapshot.status) {
                        ProviderQuotaStatus.AVAILABLE -> "AVAILABLE"
                        ProviderQuotaStatus.UNAVAILABLE -> "NO DATA"
                        ProviderQuotaStatus.ERROR -> "ERROR"
                    },
                    tone = when (snapshot.status) {
                        ProviderQuotaStatus.AVAILABLE -> BadgeTone.Primary
                        ProviderQuotaStatus.UNAVAILABLE -> BadgeTone.Warning
                        ProviderQuotaStatus.ERROR -> BadgeTone.Danger
                    },
                )
            }

            if (snapshot.values.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    snapshot.values.forEach { value ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = value.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Text(
                                text = value.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            snapshot.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            snapshot.updatedAt?.let { updatedAt ->
                Text(
                    text = "Updated ${formatTimestamp(updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

internal fun maskApiKey(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length <= 10) return trimmed
    return "${trimmed.take(8)}…${trimmed.takeLast(4)}"
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(Date(timestamp))
