package com.ivnsrg.aicontrolcentre.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivnsrg.aicontrolcentre.core.model.ChatRepository
import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
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
import com.ivnsrg.aicontrolcentre.core.ui.components.formatLatencySeconds
import com.ivnsrg.aicontrolcentre.core.ui.components.formatRoundedCost
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.OperationalCard
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionLabel
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ThreadUiState(
    val messages: List<Message> = emptyList(),
    val models: List<ModelCatalogEntry> = emptyList(),
    val selectedModelId: String? = null,
    val promptDraft: String = "",
    val isLoadingModels: Boolean = true,
    val isSending: Boolean = false,
    val error: UiError = UiError.None,
)

class ThreadViewModel(
    private val threadId: Long,
    private val threadsRepository: ThreadsRepository,
    private val modelsRepository: ModelsRepository,
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ThreadUiState())
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            threadsRepository.observeMessages(threadId).collect { messages ->
                _uiState.update { state ->
                    state.copy(
                        messages = messages,
                        selectedModelId = resolveSelectedModel(
                            currentSelection = state.selectedModelId,
                            messages = messages,
                            models = state.models,
                        ),
                    )
                }
            }
        }
        refreshModels()
    }

    fun updatePrompt(value: String) {
        _uiState.update { it.copy(promptDraft = value, error = UiError.None) }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModelId = modelId, error = UiError.None) }
    }

    fun applyCompareSelection(modelId: String) {
        if (modelId.isBlank()) return
        _uiState.update { state ->
            state.copy(
                selectedModelId = modelId,
                error = UiError.None,
            )
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true, error = UiError.None) }

            if (settingsRepository.getApiKey().isNullOrBlank()) {
                _uiState.update { it.copy(isLoadingModels = false, error = UiError.MissingApiKey) }
                return@launch
            }

            try {
                val remoteModels = modelsRepository.refreshModels()
                    .filter { it.supportsChat }
                val models = if (remoteModels.isNotEmpty()) remoteModels else modelsRepository.getCachedModels().filter { it.supportsChat }

                if (models.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoadingModels = false,
                            error = UiError.Provider("Не удалось получить список chat-моделей"),
                        )
                    }
                    return@launch
                }

                _uiState.update { state ->
                    state.copy(
                        models = models,
                        selectedModelId = resolveSelectedModel(
                            currentSelection = state.selectedModelId,
                            messages = state.messages,
                            models = models,
                        ),
                        isLoadingModels = false,
                    )
                }
            } catch (throwable: Throwable) {
                val fallbackModels = runCatching {
                    modelsRepository.getCachedModels().filter { it.supportsChat }
                }.getOrDefault(emptyList())

                if (fallbackModels.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(
                            models = fallbackModels,
                            selectedModelId = resolveSelectedModel(
                                currentSelection = state.selectedModelId,
                                messages = state.messages,
                                models = fallbackModels,
                            ),
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

    fun sendMessage() {
        val state = _uiState.value
        val prompt = state.promptDraft.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = UiError.Validation("Введите сообщение")) }
            return
        }

        val selectedModelId = state.selectedModelId
        if (selectedModelId.isNullOrBlank()) {
            _uiState.update { it.copy(error = UiError.Validation("Сначала выбери модель")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = UiError.None) }

            try {
                val assistantDraft = chatRepository.sendMessage(
                    threadId = threadId,
                    modelId = selectedModelId,
                    prompt = prompt,
                    history = state.messages,
                )
                threadsRepository.insertUserMessage(threadId, prompt, selectedModelId)
                threadsRepository.insertAssistantMessage(
                    threadId = threadId,
                    content = assistantDraft.content,
                    provider = assistantDraft.provider,
                    model = assistantDraft.model,
                    latencyMs = assistantDraft.latencyMs,
                    estimatedCost = assistantDraft.estimatedCost,
                )
                _uiState.update {
                    it.copy(
                        promptDraft = "",
                        isSending = false,
                    )
                }
            } catch (throwable: Throwable) {
                _uiState.update { it.copy(isSending = false, error = throwable.toUiError()) }
            }
        }
    }
}

class ThreadViewModelFactory(
    private val threadId: Long,
    private val threadsRepository: ThreadsRepository,
    private val modelsRepository: ModelsRepository,
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThreadViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ThreadViewModel(
                threadId = threadId,
                threadsRepository = threadsRepository,
                modelsRepository = modelsRepository,
                settingsRepository = settingsRepository,
                chatRepository = chatRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun ThreadRoute(
    threadId: Long,
    threadsRepository: ThreadsRepository,
    modelsRepository: ModelsRepository,
    settingsRepository: SettingsRepository,
    chatRepository: ChatRepository,
    compareSelectedModelId: String?,
    pickedModelId: String?,
    onCompareSelectionConsumed: () -> Unit,
    onPickedModelConsumed: () -> Unit,
    onCompareClick: (String?) -> Unit,
    onModelPickerClick: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ThreadViewModel = viewModel(
        factory = ThreadViewModelFactory(
            threadId = threadId,
            threadsRepository = threadsRepository,
            modelsRepository = modelsRepository,
            settingsRepository = settingsRepository,
            chatRepository = chatRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(compareSelectedModelId) {
        if (!compareSelectedModelId.isNullOrBlank()) {
            viewModel.applyCompareSelection(compareSelectedModelId)
            onCompareSelectionConsumed()
        }
    }

    LaunchedEffect(pickedModelId) {
        if (!pickedModelId.isNullOrBlank()) {
            viewModel.selectModel(pickedModelId)
            onPickedModelConsumed()
        }
    }

    ThreadScreen(
        threadId = threadId,
        uiState = uiState,
        onPromptChange = viewModel::updatePrompt,
        onSendClick = viewModel::sendMessage,
        onRetryModels = viewModel::refreshModels,
        onCompareClick = { onCompareClick(uiState.selectedModelId) },
        onOpenModelPicker = { onModelPickerClick(uiState.selectedModelId) },
        onBack = onBack,
    )
}

@Composable
fun ThreadScreen(
    threadId: Long,
    uiState: ThreadUiState,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onRetryModels: () -> Unit,
    onCompareClick: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    AppScreenScaffold(
        title = "",
        topBar = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactActionButton(
                        text = "Back",
                        onClick = onBack,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactActionButton(
                            text = "Compare",
                            onClick = onCompareClick,
                            tone = BadgeTone.Info,
                        )
                        CompactActionButton(
                            text = "Models",
                            onClick = onOpenModelPicker,
                            tone = BadgeTone.Warning,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Thread #$threadId",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetadataChip(
                            text = uiState.selectedModelId?.substringAfterLast('/') ?: "no model",
                            tone = BadgeTone.Primary,
                        )
                        MetadataChip(
                            text = if (uiState.isLoadingModels) "syncing" else "ready",
                            tone = if (uiState.isLoadingModels) BadgeTone.Warning else BadgeTone.Info,
                        )
                    }
                }
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
            if (uiState.error != UiError.None) {
                ThreadErrorCard(
                    error = uiState.error,
                    onRetry = onRetryModels,
                )
            }

            when {
                uiState.messages.isEmpty() && uiState.isLoadingModels -> {
                    OperationalCard(
                        modifier = Modifier.weight(1f),
                        tone = CardTone.Surface2,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(color = colors.accentPrimary)
                            Text(
                                text = "Syncing model catalog…",
                                modifier = Modifier.padding(top = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                uiState.messages.isEmpty() -> {
                    EmptyState(
                        title = "No messages yet",
                        subtitle = "Choose an active model and send the first prompt to start this thread.",
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(uiState.messages, key = { _, message -> message.id }) { index, message ->
                            val switchNotice = modelSwitchNotice(uiState.messages, index)
                            if (switchNotice != null) {
                                SectionLabel(
                                    text = switchNotice,
                                    modifier = Modifier.padding(start = 4.dp),
                                    tone = BadgeTone.Info,
                                )
                            }
                            MessageBubble(message = message)
                        }
                    }
                }
            }

            ComposerCard(
                promptDraft = uiState.promptDraft,
                onPromptChange = onPromptChange,
                onSendClick = onSendClick,
                isSending = uiState.isSending,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val colors = MaterialTheme.appColors
    val isUser = message.role == MessageRole.USER
    val bubbleColor = if (isUser) colors.surface2 else colors.surface3
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.84f else 0.92f)
                .background(
                    bubbleColor,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        topStart = if (isUser) 20.dp else 8.dp,
                        topEnd = if (isUser) 8.dp else 20.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 20.dp,
                    ),
                )
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isUser) "User" else "Assistant",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) colors.textSecondary else colors.textMuted,
                    )
                    BubbleCopyButton(
                        copied = copied,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            copied = true
                        },
                    )
                }
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                } else {
                    AssistantMarkdownContent(
                        content = message.content,
                    )
                }
                if (!isUser) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            message.provider?.let { MetadataChip(text = it.name, tone = BadgeTone.Neutral) }
                            message.model?.let { MetadataChip(text = it.substringAfterLast('/'), tone = BadgeTone.Primary) }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            message.latencyMs?.let { MetadataChip(text = formatLatencySeconds(it), tone = BadgeTone.Info) }
                            message.estimatedCost?.let { MetadataChip(text = formatRoundedCost(it), tone = BadgeTone.Warning) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleCopyButton(
    copied: Boolean,
    onCopy: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Box(
        modifier = Modifier
            .background(
                color = if (copied) colors.accentPrimary.copy(alpha = 0.18f) else colors.surface1,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onCopy)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (copied) "Copied" else "Copy",
            style = MaterialTheme.typography.labelSmall,
            color = if (copied) colors.accentPrimary else colors.textSecondary,
        )
    }
}

@Composable
private fun ComposerCard(
    promptDraft: String,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSending: Boolean,
) {
    val colors = MaterialTheme.appColors
    OperationalCard(
        tone = CardTone.Surface1,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Compose")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = promptDraft,
                    onValueChange = onPromptChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Claude…", color = colors.textMuted) },
                    enabled = !isSending,
                    maxLines = 3,
                    singleLine = false,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.stroke,
                        unfocusedBorderColor = colors.stroke,
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background,
                        disabledContainerColor = colors.surface2,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentPrimary,
                    ),
                )
                Box(
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .background(
                            color = colors.accentPrimary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        )
                        .clickable(enabled = !isSending, onClick = onSendClick)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isSending) "…" else "Send",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.background,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadErrorCard(
    error: UiError,
    onRetry: () -> Unit,
) {
    OperationalCard(
        tone = CardTone.Danger,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Execution issue", tone = BadgeTone.Danger)
            Text(
                text = error.toReadableMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary,
            )
            CompactActionButton(
                text = "Retry sync",
                onClick = onRetry,
                tone = BadgeTone.Danger,
            )
        }
    }
}

private fun resolveSelectedModel(
    currentSelection: String?,
    messages: List<Message>,
    models: List<ModelCatalogEntry>,
): String? {
    if (models.isEmpty()) return null
    if (currentSelection != null && models.any { it.id == currentSelection }) return currentSelection

    val recentMessageModel = messages
        .asReversed()
        .mapNotNull { it.model }
        .firstOrNull { candidate -> models.any { it.id == candidate } }

    return recentMessageModel ?: models.first().id
}

private fun modelSwitchNotice(messages: List<Message>, index: Int): String? {
    val current = messages.getOrNull(index) ?: return null
    val currentModel = current.model ?: return null
    if (current.role != MessageRole.ASSISTANT) return null

    val previousAssistantModel = messages
        .subList(0, index)
        .asReversed()
        .firstOrNull { it.role == MessageRole.ASSISTANT && it.model != null }
        ?.model

    if (previousAssistantModel == null || previousAssistantModel == currentModel) return null
    return "Model switched to ${currentModel.substringAfterLast('/')}"
}
