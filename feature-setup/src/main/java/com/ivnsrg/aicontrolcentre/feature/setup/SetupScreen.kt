package com.ivnsrg.aicontrolcentre.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppCard
import com.ivnsrg.aicontrolcentre.core.ui.components.AppDropdownField
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AppTextField
import com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone
import com.ivnsrg.aicontrolcentre.core.ui.components.HeaderDensity
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SecondaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionLabel
import com.ivnsrg.aicontrolcentre.core.ui.theme.LocalSpacing
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val selectedProvider: ModelProvider = ModelProvider.OPEN_ROUTER,
    val keyDraft: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveStatus: SetupSaveStatus = SetupSaveStatus.Idle,
    val saveMessage: String? = null,
    val configuredProviders: Set<ModelProvider> = emptySet(),
)

enum class SetupSaveStatus {
    Idle,
    Saving,
    Saved,
    Failed,
}

class SetupViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        refreshConfiguredProviders()
    }

    fun selectProvider(provider: ModelProvider) {
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                error = null,
                saveStatus = SetupSaveStatus.Idle,
                saveMessage = null,
            )
        }
    }

    fun updateApiKey(value: String) {
        _uiState.update {
            it.copy(
                keyDraft = value,
                error = null,
                saveStatus = SetupSaveStatus.Idle,
                saveMessage = null,
            )
        }
    }

    fun saveApiKey() {
        val state = _uiState.value
        val trimmed = state.keyDraft.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(error = "Enter a provider key") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    error = null,
                    saveStatus = SetupSaveStatus.Saving,
                    saveMessage = null,
                )
            }
            settingsRepository.saveApiKey(state.selectedProvider, trimmed)
            val persisted = settingsRepository.getApiKey(state.selectedProvider)
            val configured = settingsRepository.getProviderKeys().mapTo(linkedSetOf()) { it.provider }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    keyDraft = if (persisted != null) "" else trimmed,
                    configuredProviders = configured,
                    saveStatus = if (persisted != null) SetupSaveStatus.Saved else SetupSaveStatus.Failed,
                    saveMessage = if (persisted != null) {
                        "${state.selectedProvider.displayName} key saved locally."
                    } else {
                        "Could not confirm the saved key."
                    },
                )
            }
        }
    }

    private fun refreshConfiguredProviders() {
        viewModelScope.launch {
            val configured = settingsRepository.getProviderKeys().mapTo(linkedSetOf()) { it.provider }
            _uiState.update { it.copy(configuredProviders = configured) }
        }
    }
}

class SetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SetupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SetupViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun SetupRoute(
    settingsRepository: SettingsRepository,
    onCompleted: () -> Unit,
) {
    val viewModel: SetupViewModel = viewModel(factory = SetupViewModelFactory(settingsRepository))
    val uiState by viewModel.uiState.collectAsState()

    SetupScreen(
        uiState = uiState,
        onProviderSelected = viewModel::selectProvider,
        onApiKeyChange = viewModel::updateApiKey,
        onSaveClick = viewModel::saveApiKey,
        onContinueClick = onCompleted,
    )
}

@Composable
fun SetupScreen(
    uiState: SetupUiState,
    onProviderSelected: (ModelProvider) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val spacing = LocalSpacing.current

    AppScreenScaffold(
        title = "Initialize",
        subtitle = "Connect providers for your local workspace.",
        headerDensity = HeaderDensity.Compact,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SectionLabel("Provider access")
                        Text(
                            text = uiState.selectedProvider.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Store one key at a time, switch providers, and save additional access later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        AppDropdownField(
                            label = "Provider",
                            selectedText = uiState.selectedProvider.displayName,
                            options = ModelProvider.entries.map { it.displayName },
                            onOptionSelected = { index -> onProviderSelected(ModelProvider.entries[index]) },
                            enabled = !uiState.isSaving,
                        )
                        AppTextField(
                            value = uiState.keyDraft,
                            onValueChange = onApiKeyChange,
                            label = uiState.selectedProvider.keyLabel,
                            placeholder = uiState.selectedProvider.keyPlaceholder,
                            enabled = !uiState.isSaving,
                            isSecret = true,
                        )
                        uiState.error?.let { message ->
                            MetadataChip(text = message, tone = BadgeTone.Danger)
                        }
                        uiState.saveMessage?.let { message ->
                            MetadataChip(
                                text = message,
                                tone = when (uiState.saveStatus) {
                                    SetupSaveStatus.Saved -> BadgeTone.Primary
                                    SetupSaveStatus.Failed -> BadgeTone.Danger
                                    SetupSaveStatus.Saving -> BadgeTone.Info
                                    SetupSaveStatus.Idle -> BadgeTone.Neutral
                                },
                            )
                        }
                    }
                }

                if (uiState.configuredProviders.isNotEmpty()) {
                    AppCard {
                        SectionLabel("Configured providers", tone = BadgeTone.Info)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            uiState.configuredProviders.forEach { provider ->
                                MetadataChip(
                                    text = provider.displayName,
                                    tone = BadgeTone.Primary,
                                )
                            }
                        }
                    }
                }

                AppCard {
                    MetadataChip(text = "LOCAL SECURITY")
                    Text(
                        text = "Keys are encrypted and stored only on the device. The app uses them directly with the selected provider.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }

                AppCard {
                    Text(
                        text = "Save at least one provider key to unlock projects, threads and compare flows.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        PrimaryButton(
                            text = if (uiState.isSaving) "Saving…" else "Save key",
                            onClick = onSaveClick,
                            enabled = !uiState.isSaving,
                        )
                        if (uiState.saveStatus == SetupSaveStatus.Saved || uiState.configuredProviders.isNotEmpty()) {
                            SecondaryButton(
                                text = "Continue to Projects",
                                onClick = onContinueClick,
                            )
                        }
                    }
                }
            }
        }
    }
}
