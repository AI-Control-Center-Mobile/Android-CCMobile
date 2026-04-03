package com.ivnsrg.aicontrolcentre.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.ivnsrg.aicontrolcentre.core.model.Project
import com.ivnsrg.aicontrolcentre.core.model.ProjectsRepository
import com.ivnsrg.aicontrolcentre.core.model.Thread
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.AppTextField
import com.ivnsrg.aicontrolcentre.core.ui.components.EmptyState
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
}

class ProjectsViewModelFactory(
    private val projectsRepository: ProjectsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProjectsViewModel(projectsRepository) as T
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
        onProjectClick = onProjectClick,
    )
}

@Composable
fun ProjectsScreen(
    projects: List<Project>,
    draftTitle: String,
    onDraftTitleChange: (String) -> Unit,
    onCreateProject: () -> Unit,
    onProjectClick: (Long) -> Unit,
) {
    AppScreenScaffold(title = "Projects") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppTextField(
                value = draftTitle,
                onValueChange = onDraftTitleChange,
                label = "Название проекта",
            )
            PrimaryButton(text = "Создать проект", onClick = onCreateProject)

            if (projects.isEmpty()) {
                EmptyState(
                    title = "Пока нет проектов",
                    subtitle = "Создай первый project и начни хранить диалоги локально.",
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(projects, key = { it.id }) { project ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onProjectClick(project.id) },
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(project.title, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "updatedAt: ${project.updatedAt}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ProjectDetailUiState(
    val project: Project? = null,
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
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProjectDetailViewModel(projectId, projectsRepository, threadsRepository) as T
    }
}

@Composable
fun ProjectDetailRoute(
    projectId: Long,
    projectsRepository: ProjectsRepository,
    threadsRepository: ThreadsRepository,
    onThreadClick: (Long) -> Unit,
) {
    val viewModel: ProjectDetailViewModel = viewModel(
        factory = ProjectDetailViewModelFactory(projectId, projectsRepository, threadsRepository),
    )
    val uiState by viewModel.uiState.collectAsState()
    val threads by viewModel.threads.collectAsState()

    ProjectDetailScreen(
        project = uiState.project,
        threads = threads,
        onNewThreadClick = { viewModel.createThread(onThreadClick) },
        onThreadClick = onThreadClick,
    )
}

@Composable
fun ProjectDetailScreen(
    project: Project?,
    threads: List<Thread>,
    onNewThreadClick: () -> Unit,
    onThreadClick: (Long) -> Unit,
) {
    AppScreenScaffold(title = project?.title ?: "Project") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrimaryButton(text = "New Thread", onClick = onNewThreadClick)
            if (threads.isEmpty()) {
                EmptyState(
                    title = "Нет тредов",
                    subtitle = "Создай первый thread для этого проекта.",
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(threads, key = { it.id }) { thread ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThreadClick(thread.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(thread.title, style = MaterialTheme.typography.titleLarge)
                                    Text("updatedAt: ${thread.updatedAt}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
