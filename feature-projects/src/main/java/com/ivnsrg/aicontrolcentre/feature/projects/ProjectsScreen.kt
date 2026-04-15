package com.ivnsrg.aicontrolcentre.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ivnsrg.aicontrolcentre.core.model.Project
import com.ivnsrg.aicontrolcentre.core.model.ProjectsRepository
import com.ivnsrg.aicontrolcentre.core.model.Thread
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AppTextField
import com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone
import com.ivnsrg.aicontrolcentre.core.ui.components.CardTone
import com.ivnsrg.aicontrolcentre.core.ui.components.CompactActionButton
import com.ivnsrg.aicontrolcentre.core.ui.components.ConfirmDialog
import com.ivnsrg.aicontrolcentre.core.ui.components.EmptyState
import com.ivnsrg.aicontrolcentre.core.ui.components.HeaderDensity
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.MetricTile
import com.ivnsrg.aicontrolcentre.core.ui.components.OperationalCard
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionLabel
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProjectsUiState(
    val draftTitle: String = "",
)

class ProjectsViewModel(
    private val projectsRepository: ProjectsRepository,
) : ViewModel() {
    val projects = projectsRepository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    fun updateDraftTitle(title: String) {
        _uiState.value = _uiState.value.copy(draftTitle = title)
    }

    fun createProject(onCreated: (Long) -> Unit) {
        val title = _uiState.value.draftTitle.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = projectsRepository.createProject(title)
            _uiState.value = ProjectsUiState()
            onCreated(id)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            projectsRepository.deleteProject(projectId)
        }
    }
}

class ProjectsViewModelFactory(
    private val projectsRepository: ProjectsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProjectsViewModel(projectsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun ProjectsRoute(
    projectsRepository: ProjectsRepository,
    onProjectClick: (Long) -> Unit,
) {
    val viewModel: ProjectsViewModel = viewModel(factory = ProjectsViewModelFactory(projectsRepository))
    val projects by viewModel.projects.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    ProjectsScreen(
        projects = projects,
        draftTitle = uiState.draftTitle,
        onDraftTitleChange = viewModel::updateDraftTitle,
        onCreateProject = { viewModel.createProject(onProjectClick) },
        onDeleteProject = viewModel::deleteProject,
        onProjectClick = onProjectClick,
    )
}

@Composable
fun ProjectsScreen(
    projects: List<Project>,
    draftTitle: String,
    onDraftTitleChange: (String) -> Unit,
    onCreateProject: () -> Unit,
    onDeleteProject: (Long) -> Unit,
    onProjectClick: (Long) -> Unit,
) {
    var pendingDeleteProjectId by remember { mutableStateOf<Long?>(null) }
    val pendingProject = projects.firstOrNull { it.id == pendingDeleteProjectId }

    if (pendingProject != null) {
        ConfirmDialog(
            title = "Удалить проект?",
            message = "Проект \"${pendingProject.title}\" будет удалён вместе со всеми тредами и сообщениями.",
            confirmText = "Удалить",
            onConfirm = {
                onDeleteProject(pendingProject.id)
                pendingDeleteProjectId = null
            },
            onDismiss = { pendingDeleteProjectId = null },
        )
    }

    AppScreenScaffold(
        title = "Projects",
        subtitle = "Local-first operational controller",
        headerDensity = HeaderDensity.Compact,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            item {
                OperationalCard(
                    tone = CardTone.Surface2,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Create workspace", tone = com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone.Primary)
                        AppTextField(
                            value = draftTitle,
                            onValueChange = onDraftTitleChange,
                            label = "Project title",
                        )
                        PrimaryButton(text = "Create project", onClick = onCreateProject)
                    }
                }
            }

            item {
                SectionLabel("Active workspaces")
            }

            if (projects.isEmpty()) {
                item {
                    EmptyState(
                        title = "No workspaces yet",
                        subtitle = "Create the first workspace to start storing local AI threads and comparisons.",
                    )
                }
            } else {
                itemsIndexed(projects, key = { _, project -> project.id }) { index, project ->
                    ProjectListItem(
                        project = project,
                        highlight = index == 0,
                        onClick = { onProjectClick(project.id) },
                        onLongClick = { pendingDeleteProjectId = project.id },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectListItem(
    project: Project,
    highlight: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    OperationalCard(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        tone = CardTone.Surface1,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataChip(text = if (highlight) "ACTIVE" else "LOCAL")
                    MetadataChip(
                        text = formatTimestamp(project.updatedAt),
                        tone = com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone.Info,
                    )
                }
            }
            Text(
                text = ">",
                style = MaterialTheme.typography.titleLarge,
                color = if (highlight) colors.accentPrimary else colors.textMuted,
            )
        }
    }
}

data class ProjectDetailUiState(
    val project: Project? = null,
    val createThreadError: String? = null,
)

class ProjectDetailViewModel(
    private val projectId: Long,
    private val projectsRepository: ProjectsRepository,
    private val threadsRepository: ThreadsRepository,
) : ViewModel() {
    val threads = threadsRepository.observeThreads(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = ProjectDetailUiState(projectsRepository.getProject(projectId))
        }
    }

    fun createThread(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(createThreadError = null)
            val project = projectsRepository.getProject(projectId)
            if (project == null) {
                _uiState.value = _uiState.value.copy(
                    project = null,
                    createThreadError = "Project was removed. Go back and choose another workspace.",
                )
                return@launch
            }

            runCatching {
                threadsRepository.createThread(projectId)
            }.onSuccess { threadId ->
                onCreated(threadId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    createThreadError = "Could not start a thread for this project. Refresh Projects and try again.",
                )
            }
        }
    }

    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            threadsRepository.deleteThread(threadId)
        }
    }
}

class ProjectDetailViewModelFactory(
    private val projectId: Long,
    private val projectsRepository: ProjectsRepository,
    private val threadsRepository: ThreadsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProjectDetailViewModel(projectId, projectsRepository, threadsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
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
    val threads by viewModel.threads.collectAsState()

    ProjectDetailScreen(
        project = uiState.project,
        createThreadError = uiState.createThreadError,
        threads = threads,
        onBack = onBack,
        onNewThreadClick = { viewModel.createThread(onThreadClick) },
        onDeleteThread = viewModel::deleteThread,
        onThreadClick = onThreadClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectDetailScreen(
    project: Project?,
    createThreadError: String?,
    threads: List<Thread>,
    onBack: () -> Unit,
    onNewThreadClick: () -> Unit,
    onDeleteThread: (Long) -> Unit,
    onThreadClick: (Long) -> Unit,
) {
    val colors = MaterialTheme.appColors
    var pendingDeleteThreadId by remember { mutableStateOf<Long?>(null) }
    val pendingThread = threads.firstOrNull { it.id == pendingDeleteThreadId }

    if (pendingThread != null) {
        ConfirmDialog(
            title = "Удалить тред?",
            message = "Тред \"${pendingThread.title}\" будет удалён вместе со всеми сообщениями.",
            confirmText = "Удалить",
            onConfirm = {
                onDeleteThread(pendingThread.id)
                pendingDeleteThreadId = null
            },
            onDismiss = { pendingDeleteThreadId = null },
        )
    }

    AppScreenScaffold(
        title = "",
        headerDensity = HeaderDensity.Compact,
        topBar = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactActionButton(text = "Back", onClick = onBack)
                    MetadataChip(text = "PROJECT", tone = BadgeTone.Info)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = project?.title ?: "Workspace",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Project-centric thread workspace",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                OperationalCard(
                    tone = CardTone.Surface3,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            MetricTile(
                                label = "Threads",
                                value = threads.size.toString(),
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "Updated",
                                value = project?.updatedAt?.let(::formatShortDate) ?: "n/a",
                                modifier = Modifier.weight(1f),
                                tone = com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone.Info,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel("Last activity")
                            Text(
                                text = project?.updatedAt?.let(::formatTimestamp) ?: "No activity yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
            createThreadError?.let { message ->
                item {
                    OperationalCard(tone = CardTone.Danger) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("Thread issue", tone = BadgeTone.Danger)
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
            item {
                SectionLabel("Recent threads")
            }

            if (threads.isEmpty()) {
                item {
                    EmptyState(
                        title = "No threads in this workspace",
                        subtitle = "Create the first thread and continue inside the project flow.",
                    )
                }
            } else {
                itemsIndexed(threads, key = { _, thread -> thread.id }) { index, thread ->
                    OperationalCard(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onThreadClick(thread.id) },
                                onLongClick = { pendingDeleteThreadId = thread.id },
                            ),
                        tone = CardTone.Surface1,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = thread.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetadataChip(
                                    text = formatTimestamp(thread.updatedAt),
                                    tone = com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone.Info,
                                )
                                if (index == 0) {
                                    MetadataChip(
                                        text = "Recent",
                                        tone = com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone.Primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                PrimaryButton(
                    text = "Start New Thread",
                    onClick = onNewThreadClick,
                    enabled = project != null,
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("dd MMM • HH:mm", Locale.US).format(Date(timestamp))

private fun formatShortDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM", Locale.US).format(Date(timestamp))
