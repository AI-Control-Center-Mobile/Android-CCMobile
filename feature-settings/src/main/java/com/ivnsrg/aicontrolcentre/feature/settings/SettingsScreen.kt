package com.ivnsrg.aicontrolcentre.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ivnsrg.aicontrolcentre.core.ui.components.ConfirmDialog
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SecondaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hasApiKey: Boolean = false,
    val keyDraft: String = "",
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
        _uiState.value = _uiState.value.copy(keyDraft = value)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(hasApiKey = !settingsRepository.getApiKey().isNullOrBlank())
        }
    }

    fun saveKey() {
        val trimmed = _uiState.value.keyDraft.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            settingsRepository.saveApiKey(trimmed)
            _uiState.value = SettingsUiState(hasApiKey = true)
        }
    }

    fun clearKey(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearApiKey()
            refresh()
            onDone()
        }
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearAllLocalData()
            _uiState.value = SettingsUiState()
            onDone()
        }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(settingsRepository) as T
    }
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
            title = "Удалить API key?",
            message = "Ключ будет удалён. На следующем запуске откроется setup screen.",
            confirmText = "Удалить",
            onConfirm = {
                showClearKeyDialog = false
                viewModel.clearKey(onApiKeyRemoved)
            },
            onDismiss = { showClearKeyDialog = false },
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
    AppScreenScaffold(title = "Settings") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetadataChip(if (uiState.hasApiKey) "API key сохранён" else "API key не задан")
            AppTextField(
                value = uiState.keyDraft,
                onValueChange = onDraftChange,
                label = "Новый OpenRouter API key",
            )
            PrimaryButton(text = "Сохранить key", onClick = onSaveKey)
            SecondaryButton(text = "Удалить key", onClick = onClearKey)
            SecondaryButton(text = "Очистить все локальные данные", onClick = onClearAll)
            Text("Bootstrap configuration screen")
        }
    }
}
