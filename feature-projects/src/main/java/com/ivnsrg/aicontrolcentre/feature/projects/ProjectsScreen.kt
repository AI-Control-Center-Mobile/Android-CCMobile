package com.ivnsrg.aicontrolcentre.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.ivnsrg.aicontrolcentre.core.model.Project
import com.ivnsrg.aicontrolcentre.core.model.ProjectsRepository
import com.ivnsrg.aicontrolcentre.core.model.Thread
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppCard
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AppTextField
import com.ivnsrg.aicontrolcentre.core.ui.components.AssistantMarkdownPreview
import com.ivnsrg.aicontrolcentre.core.ui.components.CompactActionButton
import com.ivnsrg.aicontrolcentre.core.ui.components.EmptyState
import com.ivnsrg.aicontrolcentre.core.ui.components.KeyValueRow
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionHeader
import com.ivnsrg.aicontrolcentre.core.ui.theme.LocalSpacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProjectCardUiModel(
    val id: Long,
    val title: String,
    val lastActivityLabel: String,
    val updatedAt: Long,
)

data class ThreadRowUiModel(
    val id: Long,
    val title: String,
    val preview: String,
    val lastActivityLabel: String,
    val messageCount: Int,
    val totalCost: Double,
    val updatedAt: Long,
)

data class ProjectHeaderUiModel(
    val messageCount: Int = 0,
    val totalSpend: Double = 0.0,
    val lastActivityLabel: String = "No activity yet",
)

data class ProjectsUiState(
    val draftTitle: String = "",
    val isCreating: Boolean = false,
)

data class ProjectsScreenState(
    val projects: List<ProjectCardUiModel> = emptyList(),
    val isLoading: Boolean = true,
)

data class ProjectDetailUiState(
    val title: String = "Project",
    val isLoading: Boolean = true,
    val summary: ProjectHeaderUiModel = ProjectHeaderUiModel(),
    val threads: List<ThreadRowUiModel> = emptyList(),
)

class ProjectsViewModel(
    private val projectsRepository: ProjectsRepository,
) : ViewModel() {
    val screenState: StateFlow<ProjectsScreenState> = projectsRepository.observeProjects()
        .map { projects ->
            ProjectsScreenState(
                projects = projects.map(::toProjectCard).sortedByDescending(ProjectCardUiModel::updatedAt),
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsScreenState())

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    fun updateDraftTitle(title: String) {
        _uiState.value = _uiState.value.copy(draftTitle = title)
    }

    fun createProject(onCreated: (Long) -> Unit) {
        val title = _uiState.value.draftTitle.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            val id = projectsRepository.createProject(title)
            _uiState.value = ProjectsUiState()
            onCreated(id)
        }
    }
}

class ProjectsViewModelFactory(
    private val projectsRepository: ProjectsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProjectsViewModel(projectsRepository) as T
    }
}

@Composable
fun ProjectsRoute(
    projectsRepository: ProjectsRepository,
    onProjectClick: (Long) -> Unit,
) {
    val viewModel: ProjectsViewModel = viewModel(
        factory = ProjectsViewModelFactory(projectsRepository),
    )
    val screenState by viewModel.screenState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    ProjectsScreen(
        screenState = screenState,
        draftTitle = uiState.draftTitle,
        isCreating = uiState.isCreating,
        onDraftTitleChange = viewModel::updateDraftTitle,
        onCreateProject = { viewModel.createProject(onProjectClick) },
        onProjectClick = onProjectClick,
    )
}

class ProjectDetailViewModel(
    private val projectId: Long,
    private val projectsRepository: ProjectsRepository,
    private val threadsRepository: ThreadsRepository,
) : ViewModel() {
    val uiState: StateFlow<ProjectDetailUiState> = combine(
        projectsRepository.observeProjects().map { projects -> projects.firstOrNull { it.id == projectId } },
        observeThreadRows(projectId, threadsRepository),
    ) { project, threadRows ->
        val totalSpend = threadRows.sumOf { it.totalCost }
        val messageCount = threadRows.sumOf { it.messageCount }
        val latestUpdatedAt = listOfNotNull(project?.updatedAt, threadRows.maxOfOrNull { it.updatedAt }).maxOrNull()
        ProjectDetailUiState(
            title = project?.title ?: "Project",
            isLoading = project == null,
            summary = ProjectHeaderUiModel(
                messageCount = messageCount,
                totalSpend = totalSpend,
                lastActivityLabel = latestUpdatedAt?.let(::formatAbsoluteDateTime) ?: "No activity yet",
            ),
            threads = threadRows.sortedByDescending(ThreadRowUiModel::updatedAt),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailUiState())

    fun createThread(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val threadId = threadsRepository.createThread(projectId)
            onCreated(threadId)
        }
    }
}

class ProjectDetailViewModelFactory(
    private val projectId: Long,
    private val projectsRepository: ProjectsRepository,
    private val threadsRepository: ThreadsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProjectDetailViewModel(projectId, projectsRepository, threadsRepository) as T
    }
}

@Composable
fun ProjectDetailRoute(
    projectId: Long,
    projectsRepository: ProjectsRepository,
    threadsRepository: ThreadsRepository,
    onBack: () -> Unit,
    onThreadClick: (Long) -> Unit,
) {
    val viewModel: ProjectDetailViewModel = viewModel(
        factory = ProjectDetailViewModelFactory(projectId, projectsRepository, threadsRepository),
    )
    val uiState by viewModel.uiState.collectAsState()

    ProjectDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onNewThreadClick = { viewModel.createThread(onThreadClick) },
        onThreadClick = onThreadClick,
    )
}

@Composable
fun ProjectsScreen(
    screenState: ProjectsScreenState,
    draftTitle: String,
    isCreating: Boolean,
    onDraftTitleChange: (String) -> Unit,
    onCreateProject: () -> Unit,
    onProjectClick: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current

    AppScreenScaffold(
        title = "Projects",
        subtitle = "Project-centric local workspaces for AI threads.",
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            AppCard {
                SectionHeader(title = "CREATE WORKSPACE")
                AppTextField(
                    value = draftTitle,
                    onValueChange = onDraftTitleChange,
                    label = "Project title",
                    placeholder = "UI System Refactor",
                    enabled = !isCreating,
                )
                PrimaryButton(
                    text = if (isCreating) "Creating..." else "Create Project",
                    onClick = onCreateProject,
                    enabled = !isCreating,
                )
            }

            SectionHeader(title = "ACTIVE WORKSPACES")

            if (screenState.projects.isEmpty()) {
                EmptyState(
                    title = "No projects yet",
                    subtitle = "Create your first project to organize threads and local chat history.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(screenState.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectClick(project.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectDetailScreen(
    uiState: ProjectDetailUiState,
    onBack: () -> Unit,
    onNewThreadClick: () -> Unit,
    onThreadClick: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current

    AppScreenScaffold(
        title = uiState.title,
        subtitle = "Review recent threads and start a new conversation.",
        topBar = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                CompactActionButton(text = "Back", onClick = onBack)
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Review recent threads and start a new conversation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        KeyValueRow(label = "Messages", value = uiState.summary.messageCount.toString())
                        KeyValueRow(label = "Total Spend", value = formatCost(uiState.summary.totalSpend))
                    }
                }
                Text(
                    text = "Last Activity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = uiState.summary.lastActivityLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            PrimaryButton(text = "Start New Thread", onClick = onNewThreadClick)
            SectionHeader(title = "RECENT THREADS")

            if (uiState.threads.isEmpty()) {
                EmptyState(
                    title = "No threads in this project",
                    subtitle = "Start a thread here, then your teammate can wire in the chat flow.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(uiState.threads, key = { it.id }) { thread ->
                        ThreadRowCard(thread = thread, onClick = { onThreadClick(thread.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectCardUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(project.title, style = MaterialTheme.typography.titleLarge)
            MetadataChip(text = "LOCAL WORKSPACE")
            Text(
                text = project.lastActivityLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThreadRowCard(
    thread: ThreadRowUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(thread.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = thread.lastActivityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistantMarkdownPreview(content = thread.preview, maxLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataChip(text = "${thread.messageCount} MSG")
                MetadataChip(text = formatCost(thread.totalCost))
            }
        }
    }
}

private fun toProjectCard(project: Project): ProjectCardUiModel = ProjectCardUiModel(
    id = project.id,
    title = project.title,
    lastActivityLabel = formatRelativeDateTime(project.updatedAt),
    updatedAt = project.updatedAt,
)

@OptIn(ExperimentalCoroutinesApi::class)
private fun observeThreadRows(
    projectId: Long,
    threadsRepository: ThreadsRepository,
): Flow<List<ThreadRowUiModel>> {
    return threadsRepository.observeThreads(projectId).flatMapLatest { threads ->
        if (threads.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(threads.map { thread -> observeThreadRow(thread, threadsRepository) }) { rows ->
                rows.toList()
            }
        }
    }
}

private fun observeThreadRow(
    thread: Thread,
    threadsRepository: ThreadsRepository,
): Flow<ThreadRowUiModel> {
    return threadsRepository.observeMessages(thread.id).map { messages ->
        buildThreadRow(thread, messages)
    }
}

private fun buildThreadRow(
    thread: Thread,
    messages: List<Message>,
): ThreadRowUiModel {
    val lastMessage = messages.lastOrNull()
    val updatedAt = maxOf(thread.updatedAt, lastMessage?.createdAt ?: 0L)
    return ThreadRowUiModel(
        id = thread.id,
        title = thread.title,
        preview = lastMessage?.content?.trim().orEmpty().ifBlank { "No messages yet. Use this thread to continue the chat flow." },
        lastActivityLabel = if (updatedAt > 0L) formatRelativeDateTime(updatedAt) else "Idle",
        messageCount = messages.size,
        totalCost = messages.sumOf { it.estimatedCost ?: 0.0 },
        updatedAt = updatedAt.takeIf { it > 0L } ?: thread.updatedAt,
    )
}

private fun formatCost(value: Double): String {
    val formatter = DecimalFormat("$0.0000")
    return formatter.format(value)
}

private fun formatRelativeDateTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour

    return when {
        diff < minute -> "Just now"
        diff < hour -> "${diff / minute} min ago"
        diff < day -> "${diff / hour} h ago"
        diff < day * 2 -> "Yesterday"
        else -> SimpleDateFormat("dd MMM", Locale.US).format(Date(timestamp))
    }
}

private fun formatAbsoluteDateTime(timestamp: Long): String {
    return SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(Date(timestamp))
}
