package com.ivnsrg.aicontrolcentre.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import com.ivnsrg.aicontrolcentre.core.model.ModelPickerMode
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelsRepository
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.UiError
import com.ivnsrg.aicontrolcentre.core.model.UiException
import com.ivnsrg.aicontrolcentre.core.model.toReadableMessage
import com.ivnsrg.aicontrolcentre.core.model.toUiError
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AppTextField
import com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone
import com.ivnsrg.aicontrolcentre.core.ui.components.CardTone
import com.ivnsrg.aicontrolcentre.core.ui.components.CompactActionButton
import com.ivnsrg.aicontrolcentre.core.ui.components.EmptyState
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.OperationalCard
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionLabel
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelPickerUiState(
    val query: String = "",
    val models: List<ModelCatalogEntry> = emptyList(),
    val selectedModelId: String? = null,
    val isLoading: Boolean = true,
    val error: UiError = UiError.None,
)

class ModelPickerViewModel(
    private val mode: ModelPickerMode,
    private val currentSelection: String?,
    private val modelsRepository: ModelsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModelPickerUiState(selectedModelId = currentSelection))
    val uiState: StateFlow<ModelPickerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = UiError.None) }
            if (settingsRepository.getApiKey().isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, error = UiError.MissingApiKey) }
                return@launch
            }

            try {
                val remote = modelsRepository.refreshModels()
                val filtered = remote.filterForMode(mode)
                val models = if (filtered.isNotEmpty()) filtered else modelsRepository.getCachedModels().filterForMode(mode)
                _uiState.update {
                    it.copy(
                        models = models.sortedBy { model -> model.label.lowercase() },
                        isLoading = false,
                    )
                }
            } catch (throwable: Throwable) {
                val fallback = runCatching {
                    modelsRepository.getCachedModels().filterForMode(mode)
                }.getOrDefault(emptyList())

                if (fallback.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            models = fallback.sortedBy { model -> model.label.lowercase() },
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = throwable.toUiError()) }
                }
            }
        }
    }
}

class ModelPickerViewModelFactory(
    private val mode: ModelPickerMode,
    private val currentSelection: String?,
    private val modelsRepository: ModelsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelPickerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModelPickerViewModel(
                mode = mode,
                currentSelection = currentSelection,
                modelsRepository = modelsRepository,
                settingsRepository = settingsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun ModelPickerRoute(
    mode: ModelPickerMode,
    currentSelection: String?,
    modelsRepository: ModelsRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onModelSelected: (String) -> Unit,
) {
    val viewModel: ModelPickerViewModel = viewModel(
        factory = ModelPickerViewModelFactory(
            mode = mode,
            currentSelection = currentSelection,
            modelsRepository = modelsRepository,
            settingsRepository = settingsRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()

    ModelPickerScreen(
        mode = mode,
        uiState = uiState,
        onQueryChange = viewModel::updateQuery,
        onRetry = viewModel::refresh,
        onBack = onBack,
        onModelSelected = onModelSelected,
    )
}

@Composable
fun ModelPickerScreen(
    mode: ModelPickerMode,
    uiState: ModelPickerUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onModelSelected: (String) -> Unit,
) {
    val colors = MaterialTheme.appColors
    val visibleModels = uiState.models.filter { model ->
        if (uiState.query.isBlank()) {
            true
        } else {
            val query = uiState.query.trim().lowercase()
            model.label.lowercase().contains(query) ||
                model.id.lowercase().contains(query) ||
                model.model.lowercase().contains(query)
        }
    }

    AppScreenScaffold(
        title = "",
        topBar = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactActionButton(text = "Back", onClick = onBack)
                    MetadataChip(
                        text = when (mode) {
                            ModelPickerMode.CHAT -> "CHAT MODEL"
                            ModelPickerMode.COMPARE_A -> "COMPARE A"
                            ModelPickerMode.COMPARE_B -> "COMPARE B"
                        },
                        tone = BadgeTone.Info,
                    )
                }
                Text(
                    text = "Choose OpenRouter model",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Search by provider, model id or label. The selected model is marked with a check.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OperationalCard(
                tone = CardTone.Surface2,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Search")
                    AppTextField(
                        value = uiState.query,
                        onValueChange = onQueryChange,
                        label = "Search models",
                    )
                }
            }

            when {
                uiState.error != UiError.None -> {
                    PickerErrorCard(
                        error = uiState.error,
                        onRetry = onRetry,
                    )
                }

                uiState.isLoading -> {
                    EmptyState(
                        title = "Loading models",
                        subtitle = "Syncing model catalog from OpenRouter.",
                        modifier = Modifier.weight(1f),
                    )
                }

                visibleModels.isEmpty() -> {
                    EmptyState(
                        title = "No models found",
                        subtitle = if (uiState.query.isBlank()) {
                            "No models are available for this picker mode."
                        } else {
                            "Try another query or refresh the catalog."
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visibleModels, key = { it.id }) { model ->
                            ModelPickerItem(
                                model = model,
                                selected = model.id == uiState.selectedModelId,
                                onClick = { onModelSelected(model.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPickerItem(
    model: ModelCatalogEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    OperationalCard(
        modifier = Modifier.clickable(onClick = onClick),
        tone = if (selected) CardTone.Surface2 else CardTone.Surface1,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = model.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = colors.accentPrimary,
                    )
                }
                MetadataChip(
                    text = if (model.supportsCompare) "COMPARE" else "CHAT",
                    tone = if (selected) BadgeTone.Primary else BadgeTone.Neutral,
                )
            }
        }
    }
}

@Composable
private fun PickerErrorCard(
    error: UiError,
    onRetry: () -> Unit,
) {
    OperationalCard(
        tone = CardTone.Danger,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Picker issue", tone = BadgeTone.Danger)
            Text(
                text = error.toReadableMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary,
            )
            CompactActionButton(
                text = "Retry",
                onClick = onRetry,
                tone = BadgeTone.Danger,
            )
        }
    }
}

private fun List<ModelCatalogEntry>.filterForMode(mode: ModelPickerMode): List<ModelCatalogEntry> =
    filter { model ->
        when (mode) {
            ModelPickerMode.CHAT -> model.supportsChat
            ModelPickerMode.COMPARE_A, ModelPickerMode.COMPARE_B -> model.supportsCompare
        }
    }
