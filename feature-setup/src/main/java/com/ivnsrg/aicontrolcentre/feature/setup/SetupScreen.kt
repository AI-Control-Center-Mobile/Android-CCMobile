package com.ivnsrg.aicontrolcentre.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ivnsrg.aicontrolcentre.core.ui.components.HeaderDensity
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.OperationalCard
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SecondaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionLabel
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

data class SetupUiState(
    val apiKey: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveStatus: SetupSaveStatus = SetupSaveStatus.Idle,
    val saveMessage: String? = null,
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

    fun updateApiKey(value: String) {
        _uiState.value = _uiState.value.copy(
            apiKey = value,
            error = null,
            saveStatus = SetupSaveStatus.Idle,
            saveMessage = null,
        )
    }

    fun saveApiKey() {
        val trimmed = _uiState.value.apiKey.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Введите OpenRouter API key")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                error = null,
                saveStatus = SetupSaveStatus.Saving,
                saveMessage = null,
            )
            settingsRepository.saveApiKey(trimmed)
            val persistedKeys = settingsRepository.getApiKeys()
            val persisted = trimmed in persistedKeys
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                saveStatus = if (persisted) SetupSaveStatus.Saved else SetupSaveStatus.Failed,
                saveMessage = if (persisted) {
                    "Ключ сохранён: ${persistedKeys.firstOrNull()?.let(::maskPersistedKey) ?: maskPersistedKey(trimmed)}"
                } else {
                    "Не удалось подтвердить сохранение ключа"
                },
            )
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
        onApiKeyChange = viewModel::updateApiKey,
        onSaveClick = viewModel::saveApiKey,
        onContinueClick = onCompleted,
    )
}

@Composable
fun SetupScreen(
    uiState: SetupUiState,
    onApiKeyChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    AppScreenScaffold(
        title = "Initialize",
        subtitle = "Connect your OpenRouter workspace key.",
        headerDensity = HeaderDensity.Compact,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OperationalCard(
                tone = CardTone.Surface2,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("API Provider")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "OpenRouter.ai",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Local-only operational routing",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    AppTextField(
                        value = uiState.apiKey,
                        onValueChange = onApiKeyChange,
                        label = "OpenRouter API key",
                        enabled = !uiState.isSaving,
                    )
                    uiState.error?.let { message ->
                        MetadataChip(
                            text = message,
                            tone = BadgeTone.Danger,
                        )
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

            AppInfoCallout(
                title = "Local security",
                body = "Your API key is encrypted and stored locally on device. The app does not relay keys through its own backend.",
            )

            OperationalCard(
                tone = CardTone.Surface1,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "This app works as a local-first AI control workspace. Configure the key once, then move into projects, threads and compare flows.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    PrimaryButton(
                        text = if (uiState.isSaving) "Saving…" else "Save key",
                        onClick = onSaveClick,
                        enabled = !uiState.isSaving,
                    )
                    if (uiState.saveStatus == SetupSaveStatus.Saved) {
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

private fun maskPersistedKey(value: String): String =
    if (value.length <= 10) value else "${value.take(8)}…${value.takeLast(4)}"
