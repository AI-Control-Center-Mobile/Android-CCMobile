package com.ivnsrg.aicontrolcentre.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppCard
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AppTextField
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.theme.LocalSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val apiKey: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

class SetupViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun updateApiKey(value: String) {
        _uiState.value = _uiState.value.copy(apiKey = value, error = null)
    }

    fun saveApiKey() {
        val trimmed = _uiState.value.apiKey.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter your OpenRouter API key.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            runCatching {
                settingsRepository.saveApiKey(trimmed)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, completed = true)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Failed to store the API key locally. Please try again.",
                )
            }
        }
    }
}

class SetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SetupViewModel(settingsRepository) as T
}

@Composable
fun SetupRoute(
    settingsRepository: SettingsRepository,
    onCompleted: () -> Unit,
) {
    val viewModel: SetupViewModel = viewModel(factory = SetupViewModelFactory(settingsRepository))
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.completed) {
        if (uiState.completed) onCompleted()
    }

    SetupScreen(
        uiState = uiState,
        onApiKeyChange = viewModel::updateApiKey,
        onSaveClick = viewModel::saveApiKey,
    )
}

@Composable
fun SetupScreen(
    uiState: SetupUiState,
    onApiKeyChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    val spacing = LocalSpacing.current

    AppScreenScaffold(
        title = "Initialize",
        subtitle = "Connect your OpenRouter workspace key.",
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                AppCard {
                    MetadataChip(text = "API PROVIDER")
                    Text(
                        text = "OpenRouter.ai (Local-Only)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AppTextField(
                        value = uiState.apiKey,
                        onValueChange = onApiKeyChange,
                        label = "OpenRouter API key",
                        placeholder = "sk-or-v1-...",
                        enabled = !uiState.isSaving,
                        isSecret = true,
                    )
                    uiState.error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                AppCard {
                    MetadataChip(text = "LOCAL SECURITY")
                    Text(
                        text = "Your API key is encrypted on-device and stays out of app-owned servers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                PrimaryButton(
                    text = if (uiState.isSaving) "Saving..." else "Proceed to Projects",
                    onClick = onSaveClick,
                    enabled = !uiState.isSaving,
                )
            }
        }
    }
}
