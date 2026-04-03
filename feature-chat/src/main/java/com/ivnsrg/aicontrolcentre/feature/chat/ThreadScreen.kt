package com.ivnsrg.aicontrolcentre.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.EmptyState
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class ThreadViewModel(
    threadId: Long,
    threadsRepository: ThreadsRepository,
) : ViewModel() {
    val messages = threadsRepository.observeMessages(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class ThreadViewModelFactory(
    private val threadId: Long,
    private val threadsRepository: ThreadsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ThreadViewModel(threadId, threadsRepository) as T
    }
}

@Composable
fun ThreadRoute(
    threadId: Long,
    threadsRepository: ThreadsRepository,
    onCompareClick: (Long) -> Unit,
) {
    val viewModel: ThreadViewModel = viewModel(factory = ThreadViewModelFactory(threadId, threadsRepository))
    val messages by viewModel.messages.collectAsState()

    ThreadScreen(
        threadId = threadId,
        messages = messages,
        onCompareClick = { onCompareClick(threadId) },
    )
}

@Composable
fun ThreadScreen(
    threadId: Long,
    messages: List<Message>,
    onCompareClick: () -> Unit,
) {
    AppScreenScaffold(title = "Thread #$threadId") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrimaryButton(text = "Open Compare", onClick = onCompareClick)
            if (messages.isEmpty()) {
                EmptyState(
                    title = "Сообщений пока нет",
                    subtitle = "Chat flow будет подключён следующим этапом.",
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(messages, key = { it.id }) { message ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(message.role.name, style = MaterialTheme.typography.labelLarge)
                            Text(message.content, style = MaterialTheme.typography.bodyLarge)
                            message.model?.let { MetadataChip("Model: $it") }
                        }
                    }
                }
            }
        }
    }
}
