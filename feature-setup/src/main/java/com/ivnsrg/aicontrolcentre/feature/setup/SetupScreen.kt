package com.ivnsrg.aicontrolcentre.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AppTextField
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
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
            _uiState.value = _uiState.value.copy(error = "Введите OpenRouter API key")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            settingsRepository.saveApiKey(trimmed)
            _uiState.value = _uiState.value.copy(isSaving = false, completed = true)
        }
    }
}

class SetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SetupViewModel(settingsRepository) as T
    }
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
    AppScreenScaffold(title = "Настройка OpenRouter") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppTextField(
                value = uiState.apiKey,
                onValueChange = onApiKeyChange,
                label = "OpenRouter API key",
                enabled = !uiState.isSaving,
            )
            uiState.error?.let {
                androidx.compose.material3.Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }
            PrimaryButton(
                text = if (uiState.isSaving) "Сохраняем…" else "Сохранить key",
                onClick = onSaveClick,
                enabled = !uiState.isSaving,
            )
        }
    }
}
