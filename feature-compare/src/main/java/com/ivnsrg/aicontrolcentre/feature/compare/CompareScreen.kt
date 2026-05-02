package com.ivnsrg.aicontrolcentre.feature.compare

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivnsrg.aicontrolcentre.core.model.AssistantMessageDraft
import com.ivnsrg.aicontrolcentre.core.model.AssistantStreamEvent
import com.ivnsrg.aicontrolcentre.core.model.CompareRepository
import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelPickerMode
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.ModelsRepository
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.core.model.UiError
import com.ivnsrg.aicontrolcentre.core.model.UiException
import com.ivnsrg.aicontrolcentre.core.model.toReadableMessage
import com.ivnsrg.aicontrolcentre.core.model.toUiError
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AssistantMarkdownContent
import com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone
import com.ivnsrg.aicontrolcentre.core.ui.components.CardTone
import com.ivnsrg.aicontrolcentre.core.ui.components.CompactActionButton
import com.ivnsrg.aicontrolcentre.core.ui.components.EmptyState
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.OperationalCard
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionLabel
import com.ivnsrg.aicontrolcentre.core.ui.components.formatLatencySeconds
import com.ivnsrg.aicontrolcentre.core.ui.components.formatRoundedCost
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

private enum class CompareSlot {
    FIRST,
    SECOND,
}

enum class CompareCandidateStatus {
    Idle,
    Waiting,
    Streaming,
    Completed,
    Error,
}

data class CompareCandidateUiState(
    val selectedModelId: String? = null,
    val provider: ModelProvider? = null,
    val model: String? = null,
    val content: String = "",
    val latencyMs: Long? = null,
    val estimatedCost: Double? = null,
    val status: CompareCandidateStatus = CompareCandidateStatus.Idle,
    val error: UiError = UiError.None,
    val draft: AssistantMessageDraft? = null,
)

data class CompareUiState(
    val promptDraft: String = "",
    val comparedPrompt: String? = null,
    val messages: List<Message> = emptyList(),
    val models: List<ModelCatalogEntry> = emptyList(),
    val selectedModelA: String? = null,
    val selectedModelB: String? = null,
    val firstCandidate: CompareCandidateUiState = CompareCandidateUiState(),
    val secondCandidate: CompareCandidateUiState = CompareCandidateUiState(),
    val isLoadingModels: Boolean = true,
    val isComparing: Boolean = false,
    val isPersistingWinner: Boolean = false,
    val error: UiError = UiError.None,
)

class CompareViewModel(
    private val threadId: Long,
    private val initialSelectedModelId: String?,
    private val threadsRepository: ThreadsRepository,
    private val modelsRepository: ModelsRepository,
    private val settingsRepository: SettingsRepository,
    private val compareRepository: CompareRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            threadsRepository.observeMessages(threadId).collect { messages ->
                val latestPrompt = messages.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
                _uiState.update { state ->
                    state.copy(
                        messages = messages,
                        promptDraft = if (state.promptDraft.isBlank()) latestPrompt else state.promptDraft,
                    )
                }
            }
        }
        refreshModels()
    }

    fun updatePrompt(value: String) {
        _uiState.update { it.copy(promptDraft = value, error = UiError.None) }
    }

    fun selectModelA(modelId: String) {
        _uiState.update {
            it.copy(
                selectedModelA = modelId,
                error = UiError.None,
                firstCandidate = CompareCandidateUiState(),
                secondCandidate = CompareCandidateUiState(),
                comparedPrompt = null,
            )
        }
    }

    fun selectModelB(modelId: String) {
        _uiState.update {
            it.copy(
                selectedModelB = modelId,
                error = UiError.None,
                firstCandidate = CompareCandidateUiState(),
                secondCandidate = CompareCandidateUiState(),
                comparedPrompt = null,
            )
        }
    }

    fun refreshModels(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true, error = UiError.None) }

            if (!settingsRepository.hasAnyApiKeys()) {
                _uiState.update { it.copy(isLoadingModels = false, error = UiError.MissingApiKey) }
                return@launch
            }

            try {
                val remoteModels = modelsRepository.refreshModels(forceRefresh = forceRefresh).filter { it.supportsCompare }
                val models = if (remoteModels.isNotEmpty()) {
                    remoteModels
                } else {
                    modelsRepository.getCachedModels().filter { it.supportsCompare }
                }

                if (models.size < 2) {
                    _uiState.update {
                        it.copy(
                            isLoadingModels = false,
                            error = UiError.Provider("Для compare нужно минимум две модели"),
                        )
                    }
                    return@launch
                }

                _uiState.update { state ->
                    val primary = resolvePrimaryModel(state.selectedModelA, initialSelectedModelId, models)
                    state.copy(
                        models = models,
                        selectedModelA = primary,
                        selectedModelB = resolveSecondaryModel(state.selectedModelB, primary, models),
                        isLoadingModels = false,
                    )
                }
            } catch (throwable: Throwable) {
                val fallbackModels = runCatching {
                    modelsRepository.getCachedModels().filter { it.supportsCompare }
                }.getOrDefault(emptyList())

                if (fallbackModels.size >= 2) {
                    _uiState.update { state ->
                        val primary = resolvePrimaryModel(state.selectedModelA, initialSelectedModelId, fallbackModels)
                        state.copy(
                            models = fallbackModels,
                            selectedModelA = primary,
                            selectedModelB = resolveSecondaryModel(state.selectedModelB, primary, fallbackModels),
                            isLoadingModels = false,
                            error = UiError.None,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingModels = false, error = throwable.toUiError()) }
                }
            }
        }
    }

    fun compare() {
        val state = _uiState.value
        val prompt = state.promptDraft.trim()
        val modelA = state.selectedModelA?.let { state.models.firstOrNull { model -> model.id == it } }
        val modelB = state.selectedModelB?.let { state.models.firstOrNull { model -> model.id == it } }

        when {
            prompt.isBlank() -> {
                _uiState.update { it.copy(error = UiError.Validation("Введите prompt для compare")) }
                return
            }

            modelA == null || modelB == null -> {
                _uiState.update { it.copy(error = UiError.Validation("Выбери две модели")) }
                return
            }

            modelA.id == modelB.id -> {
                _uiState.update { it.copy(error = UiError.Validation("Для compare нужны разные модели")) }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isComparing = true,
                    error = UiError.None,
                    comparedPrompt = prompt,
                    firstCandidate = CompareCandidateUiState(
                        selectedModelId = modelA.id,
                        provider = modelA.provider,
                        model = modelA.model,
                        status = CompareCandidateStatus.Waiting,
                    ),
                    secondCandidate = CompareCandidateUiState(
                        selectedModelId = modelB.id,
                        provider = modelB.provider,
                        model = modelB.model,
                        status = CompareCandidateStatus.Waiting,
                    ),
                )
            }

            runCompareSlots(
                prompt = prompt,
                history = state.messages,
                requests = listOf(
                    CompareSlot.FIRST to modelA,
                    CompareSlot.SECOND to modelB,
                ),
            )
        }
    }

    fun retryFirstCandidate() {
        retryCandidate(CompareSlot.FIRST)
    }

    fun retrySecondCandidate() {
        retryCandidate(CompareSlot.SECOND)
    }

    private fun retryCandidate(slot: CompareSlot) {
        val state = _uiState.value
        if (state.isComparing || state.isPersistingWinner) return

        val prompt = state.comparedPrompt?.trim().orEmpty().ifBlank { state.promptDraft.trim() }
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = UiError.Validation("Нет prompt для повторного compare")) }
            return
        }

        val modelEntry = when (slot) {
            CompareSlot.FIRST -> resolveModelEntry(
                selectedModelId = state.firstCandidate.selectedModelId ?: state.selectedModelA,
                models = state.models,
            )

            CompareSlot.SECOND -> resolveModelEntry(
                selectedModelId = state.secondCandidate.selectedModelId ?: state.selectedModelB,
                models = state.models,
            )
        }

        if (modelEntry == null) {
            _uiState.update { it.copy(error = UiError.Validation("Нет модели для повторной попытки")) }
            return
        }

        _uiState.update {
            val updatedCandidate = when (slot) {
                CompareSlot.FIRST -> it.firstCandidate.copy(
                    selectedModelId = modelEntry.id,
                    provider = modelEntry.provider,
                    model = modelEntry.model,
                    status = CompareCandidateStatus.Waiting,
                    error = UiError.None,
                    draft = null,
                    latencyMs = null,
                    estimatedCost = null,
                )

                CompareSlot.SECOND -> it.secondCandidate.copy(
                    selectedModelId = modelEntry.id,
                    provider = modelEntry.provider,
                    model = modelEntry.model,
                    status = CompareCandidateStatus.Waiting,
                    error = UiError.None,
                    draft = null,
                    latencyMs = null,
                    estimatedCost = null,
                )
            }

            when (slot) {
                CompareSlot.FIRST -> it.copy(
                    isComparing = true,
                    error = UiError.None,
                    comparedPrompt = prompt,
                    firstCandidate = updatedCandidate,
                )

                CompareSlot.SECOND -> it.copy(
                    isComparing = true,
                    error = UiError.None,
                    comparedPrompt = prompt,
                    secondCandidate = updatedCandidate,
                )
            }
        }

        viewModelScope.launch {
            runCompareSlots(
                prompt = prompt,
                history = state.messages,
                requests = listOf(slot to modelEntry),
            )
        }
    }

    fun continueWithWinner(selectedModelId: String, onDone: (String) -> Unit) {
        val state = _uiState.value
        if (state.isPersistingWinner) return

        val prompt = state.comparedPrompt?.trim().orEmpty()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = UiError.Validation("Нет prompt для сохранения результата")) }
            return
        }

        val candidate = when (selectedModelId) {
            state.firstCandidate.selectedModelId -> state.firstCandidate
            state.secondCandidate.selectedModelId -> state.secondCandidate
            else -> null
        }

        val draft = candidate?.draft
        if (draft == null) {
            _uiState.update { it.copy(error = UiError.Validation("Результат победителя ещё не готов")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPersistingWinner = true, error = UiError.None) }

            try {
                val latestUserPrompt = state.messages
                    .asReversed()
                    .firstOrNull { it.role == MessageRole.USER }
                    ?.content
                    ?.trim()
                if (latestUserPrompt != prompt) {
                    threadsRepository.insertUserMessage(
                        threadId = threadId,
                        content = prompt,
                        targetModel = selectedModelId,
                        targetProvider = draft.provider,
                    )
                }
                threadsRepository.insertAssistantMessage(
                    threadId = threadId,
                    content = draft.content,
                    provider = draft.provider,
                    model = draft.model,
                    latencyMs = draft.latencyMs,
                    estimatedCost = draft.estimatedCost,
                )
                _uiState.update { it.copy(isPersistingWinner = false) }
                onDone(selectedModelId)
            } catch (throwable: Throwable) {
                _uiState.update { it.copy(isPersistingWinner = false, error = throwable.toUiError()) }
            }
        }
    }

    private suspend fun collectCandidateStream(
        slot: CompareSlot,
        model: ModelCatalogEntry,
        prompt: String,
        history: List<Message>,
    ) {
        try {
            compareRepository.streamModelResponse(
                threadId = threadId,
                provider = model.provider,
                modelId = model.model,
                prompt = prompt,
                history = history,
            ).collectLatest { event ->
                when (event) {
                    is AssistantStreamEvent.Streaming -> updateCandidate(slot) { current ->
                        current.copy(
                            selectedModelId = model.id,
                            provider = model.provider,
                            model = model.model,
                            content = event.accumulatedContent,
                            status = when {
                                event.accumulatedContent.isBlank() -> CompareCandidateStatus.Waiting
                                else -> CompareCandidateStatus.Streaming
                            },
                            error = UiError.None,
                        )
                    }

                    is AssistantStreamEvent.Completed -> updateCandidate(slot) { current ->
                        current.copy(
                            selectedModelId = model.id,
                            provider = event.draft.provider,
                            model = event.draft.model,
                            content = event.draft.content,
                            latencyMs = event.draft.latencyMs,
                            estimatedCost = event.draft.estimatedCost,
                            status = CompareCandidateStatus.Completed,
                            error = UiError.None,
                            draft = event.draft,
                        )
                    }
                }
            }
        } catch (throwable: Throwable) {
            updateCandidate(slot) { current ->
                current.copy(
                    selectedModelId = model.id,
                    provider = model.provider,
                    model = model.model,
                    status = CompareCandidateStatus.Error,
                    error = throwable.toUiError(),
                )
            }
        }
    }

    private suspend fun runCompareSlots(
        prompt: String,
        history: List<Message>,
        requests: List<Pair<CompareSlot, ModelCatalogEntry>>,
    ) {
        supervisorScope {
            requests.map { (slot, model) ->
                launch { collectCandidateStream(slot, model, prompt, history) }
            }.joinAll()
        }

        _uiState.update { it.copy(isComparing = false) }
    }

    private fun updateCandidate(
        slot: CompareSlot,
        transform: (CompareCandidateUiState) -> CompareCandidateUiState,
    ) {
        _uiState.update { state ->
            when (slot) {
                CompareSlot.FIRST -> state.copy(firstCandidate = transform(state.firstCandidate))
                CompareSlot.SECOND -> state.copy(secondCandidate = transform(state.secondCandidate))
            }
        }
    }
}

class CompareViewModelFactory(
    private val threadId: Long,
    private val initialSelectedModelId: String?,
    private val threadsRepository: ThreadsRepository,
    private val modelsRepository: ModelsRepository,
    private val settingsRepository: SettingsRepository,
    private val compareRepository: CompareRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CompareViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CompareViewModel(
                threadId = threadId,
                initialSelectedModelId = initialSelectedModelId,
                threadsRepository = threadsRepository,
                modelsRepository = modelsRepository,
                settingsRepository = settingsRepository,
                compareRepository = compareRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun CompareRoute(
    threadId: Long,
    initialSelectedModelId: String?,
    threadsRepository: ThreadsRepository,
    modelsRepository: ModelsRepository,
    settingsRepository: SettingsRepository,
    compareRepository: CompareRepository,
    pickedModelAId: String?,
    pickedModelBId: String?,
    onPickedModelAConsumed: () -> Unit,
    onPickedModelBConsumed: () -> Unit,
    onModelChosen: (String) -> Unit,
    onOpenModelPicker: (ModelPickerMode, String?) -> Unit,
    onDone: () -> Unit,
) {
    val viewModel: CompareViewModel = viewModel(
        factory = CompareViewModelFactory(
            threadId = threadId,
            initialSelectedModelId = initialSelectedModelId,
            threadsRepository = threadsRepository,
            modelsRepository = modelsRepository,
            settingsRepository = settingsRepository,
            compareRepository = compareRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(pickedModelAId) {
        if (!pickedModelAId.isNullOrBlank()) {
            viewModel.selectModelA(pickedModelAId)
            onPickedModelAConsumed()
        }
    }

    LaunchedEffect(pickedModelBId) {
        if (!pickedModelBId.isNullOrBlank()) {
            viewModel.selectModelB(pickedModelBId)
            onPickedModelBConsumed()
        }
    }

    CompareScreen(
        threadId = threadId,
        uiState = uiState,
        onPromptChange = viewModel::updatePrompt,
        onOpenModelAPicker = { onOpenModelPicker(ModelPickerMode.COMPARE_A, uiState.selectedModelA) },
        onOpenModelBPicker = { onOpenModelPicker(ModelPickerMode.COMPARE_B, uiState.selectedModelB) },
        onCompare = viewModel::compare,
        onRetryFirstCandidate = viewModel::retryFirstCandidate,
        onRetrySecondCandidate = viewModel::retrySecondCandidate,
        onRetry = { viewModel.refreshModels(forceRefresh = true) },
        onWinnerSelected = { modelId -> viewModel.continueWithWinner(modelId, onModelChosen) },
        onDone = onDone,
    )
}

@Composable
private fun CompareScreen(
    threadId: Long,
    uiState: CompareUiState,
    onPromptChange: (String) -> Unit,
    onOpenModelAPicker: () -> Unit,
    onOpenModelBPicker: () -> Unit,
    onCompare: () -> Unit,
    onRetryFirstCandidate: () -> Unit,
    onRetrySecondCandidate: () -> Unit,
    onRetry: () -> Unit,
    onWinnerSelected: (String) -> Unit,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val modelA = uiState.models.firstOrNull { it.id == uiState.selectedModelA }
    val modelB = uiState.models.firstOrNull { it.id == uiState.selectedModelB }
    val hasCandidateContent = remember(uiState.firstCandidate, uiState.secondCandidate) {
        uiState.firstCandidate.status != CompareCandidateStatus.Idle ||
            uiState.secondCandidate.status != CompareCandidateStatus.Idle
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
                    CompactActionButton(text = "Back", onClick = onDone)
                    MetadataChip(text = "Compare A/B", tone = BadgeTone.Info)
                }
                Text(
                    text = "Prompt duel inside thread #$threadId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                OperationalCard(tone = CardTone.Surface2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Prompt")
                        OutlinedTextField(
                            value = uiState.promptDraft,
                            onValueChange = onPromptChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Prompt for compare") },
                            enabled = !uiState.isComparing && !uiState.isPersistingWinner,
                            maxLines = 5,
                        )
                        ModelPickers(
                            models = uiState.models,
                            selectedModelA = uiState.selectedModelA,
                            selectedModelB = uiState.selectedModelB,
                            onOpenModelAPicker = onOpenModelAPicker,
                            onOpenModelBPicker = onOpenModelBPicker,
                        )
                        PrimaryButton(
                            text = if (uiState.isComparing) "Comparing…" else "Run Compare",
                            onClick = onCompare,
                            enabled = !uiState.isComparing &&
                                !uiState.isPersistingWinner &&
                                uiState.models.size >= 2,
                        )
                    }
                }
            }

            if (uiState.error != UiError.None) {
                item {
                    CompareErrorCard(error = uiState.error, onRetry = onRetry)
                }
            }

            when {
                uiState.isLoadingModels && uiState.models.isEmpty() -> {
                    item {
                        OperationalCard(
                            tone = CardTone.Surface1,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(color = colors.accentPrimary)
                                Text(
                                    text = "Loading compare-capable models…",
                                    modifier = Modifier.padding(top = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }

                !hasCandidateContent -> {
                    item {
                        EmptyState(
                            title = "Compare is ready",
                            subtitle = "Pick two models and run a side-by-side response comparison.",
                        )
                    }
                }

                else -> {
                    item {
                        SectionLabel(
                            text = "Prompt: ${uiState.comparedPrompt ?: uiState.promptDraft}",
                            tone = BadgeTone.Info,
                        )
                    }
                    item {
                        CompareResultCard(
                            marker = "A",
                            model = modelA,
                            candidate = uiState.firstCandidate,
                            isPrimary = true,
                            isComparing = uiState.isComparing,
                            isPersistingWinner = uiState.isPersistingWinner,
                            onRetry = onRetryFirstCandidate,
                            onWinnerSelected = onWinnerSelected,
                        )
                    }
                    item {
                        CompareResultCard(
                            marker = "B",
                            model = modelB,
                            candidate = uiState.secondCandidate,
                            isPrimary = false,
                            isComparing = uiState.isComparing,
                            isPersistingWinner = uiState.isPersistingWinner,
                            onRetry = onRetrySecondCandidate,
                            onWinnerSelected = onWinnerSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPickers(
    models: List<ModelCatalogEntry>,
    selectedModelA: String?,
    selectedModelB: String?,
    onOpenModelAPicker: () -> Unit,
    onOpenModelBPicker: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompareModelButton(
            modifier = Modifier.weight(1f),
            title = "Model A",
            selectedModelId = selectedModelA,
            models = models,
            onClick = onOpenModelAPicker,
            tone = BadgeTone.Primary,
        )
        CompareModelButton(
            modifier = Modifier.weight(1f),
            title = "Model B",
            selectedModelId = selectedModelB,
            models = models,
            onClick = onOpenModelBPicker,
            tone = BadgeTone.Warning,
        )
    }
}

@Composable
private fun CompareModelButton(
    title: String,
    selectedModelId: String?,
    models: List<ModelCatalogEntry>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BadgeTone,
) {
    val colors = MaterialTheme.appColors
    val selectedLabel = models.firstOrNull { it.id == selectedModelId }?.label ?: title

    OperationalCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tone = if (tone == BadgeTone.Primary) CardTone.Surface2 else CardTone.Surface3,
        padding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel(text = title, tone = tone)
            Text(
                text = selectedLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (tone == BadgeTone.Primary) colors.accentPrimary else colors.accentWarning,
            )
        }
    }
}

@Composable
private fun CompareResultCard(
    marker: String,
    model: ModelCatalogEntry?,
    candidate: CompareCandidateUiState,
    isPrimary: Boolean,
    isComparing: Boolean,
    isPersistingWinner: Boolean,
    onRetry: () -> Unit,
    onWinnerSelected: (String) -> Unit,
) {
    val colors = MaterialTheme.appColors
    val selectionId = candidate.selectedModelId ?: model?.id
    val canContinue = selectionId != null &&
        candidate.status == CompareCandidateStatus.Completed &&
        candidate.draft != null &&
        !isPersistingWinner
    val canRetry = selectionId != null &&
        candidate.status == CompareCandidateStatus.Error &&
        !isComparing &&
        !isPersistingWinner

    OperationalCard(
        tone = if (isPrimary) CardTone.Surface1 else CardTone.Surface2,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetadataChip(
                        text = marker,
                        tone = if (isPrimary) BadgeTone.Primary else BadgeTone.Warning,
                    )
                    Text(
                        text = model?.label ?: candidate.model ?: "Unknown model",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    candidate.provider?.let {
                        MetadataChip(text = it.displayName, tone = BadgeTone.Neutral)
                    }
                    candidate.model?.let {
                        MetadataChip(text = it.substringAfterLast('/'), tone = BadgeTone.Primary)
                    }
                    candidate.estimatedCost?.let {
                        MetadataChip(text = formatRoundedCost(it), tone = BadgeTone.Info)
                    }
                    candidate.latencyMs?.let {
                        MetadataChip(text = formatLatencySeconds(it), tone = BadgeTone.Primary)
                    }
                }
            }

            CompareCandidateBody(candidate = candidate)

            if (canRetry) {
                CompactActionButton(
                    text = "Retry model",
                    onClick = onRetry,
                    tone = if (isPrimary) BadgeTone.Primary else BadgeTone.Warning,
                )
            }

            PrimaryButton(
                text = if (isPersistingWinner) "Saving…" else "Continue with winner",
                onClick = { selectionId?.let(onWinnerSelected) },
                enabled = canContinue,
            )
        }
    }
}

private fun resolveModelEntry(
    selectedModelId: String?,
    models: List<ModelCatalogEntry>,
): ModelCatalogEntry? = selectedModelId?.let { selectionId ->
    models.firstOrNull { it.id == selectionId }
}

@Composable
private fun CompareCandidateBody(candidate: CompareCandidateUiState) {
    when (candidate.status) {
        CompareCandidateStatus.Idle -> {
            Text(
                text = "Waiting to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary,
            )
        }

        CompareCandidateStatus.Waiting -> {
            StreamingPlaceholder(
                label = "Provider processing",
                showMarkdown = candidate.content.isNotBlank(),
                content = candidate.content,
            )
        }

        CompareCandidateStatus.Streaming -> {
            StreamingPlaceholder(
                label = "Generating response",
                showMarkdown = true,
                content = candidate.content,
            )
        }

        CompareCandidateStatus.Completed -> {
            AssistantMarkdownContent(content = candidate.content)
        }

        CompareCandidateStatus.Error -> {
            OperationalCard(tone = CardTone.Danger) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Model error", tone = BadgeTone.Danger)
                    Text(
                        text = candidate.error.toReadableMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingPlaceholder(
    label: String,
    showMarkdown: Boolean,
    content: String,
) {
    val colors = MaterialTheme.appColors
    val pulse = rememberInfiniteTransition(label = "stream-placeholder")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stream-placeholder-alpha",
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showMarkdown && content.isNotBlank()) {
            AssistantMarkdownContent(content = content)
        } else {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 2) 0.62f else 1f)
                        .height(14.dp)
                        .alpha(alpha)
                        .background(
                            color = colors.surface3,
                            shape = MaterialTheme.shapes.medium,
                        ),
                )
            }
        }
        AnimatedDotsLabel(label = label)
    }
}

@Composable
private fun AnimatedDotsLabel(label: String) {
    val dotsTransition = rememberInfiniteTransition(label = "processing-dots")
    val alpha by dotsTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "processing-dots-alpha",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(6.dp)
                    .alpha(alpha)
                    .background(
                        color = MaterialTheme.appColors.accentPrimary,
                        shape = MaterialTheme.shapes.small,
                    ),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textSecondary,
        )
    }
}

@Composable
private fun CompareErrorCard(
    error: UiError,
    onRetry: () -> Unit,
) {
    OperationalCard(
        tone = CardTone.Danger,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Compare issue", tone = BadgeTone.Danger)
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

private fun resolvePrimaryModel(
    current: String?,
    initial: String?,
    models: List<ModelCatalogEntry>,
): String? = when {
    current != null && models.any { it.id == current } -> current
    initial != null && models.any { it.id == initial } -> initial
    else -> models.firstOrNull()?.id
}

private fun resolveSecondaryModel(
    current: String?,
    primary: String?,
    models: List<ModelCatalogEntry>,
): String? = when {
    current != null && current != primary && models.any { it.id == current } -> current
    else -> models.firstOrNull { it.id != primary }?.id
}
