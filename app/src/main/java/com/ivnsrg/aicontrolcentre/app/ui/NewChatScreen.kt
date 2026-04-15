package com.ivnsrg.aicontrolcentre.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivnsrg.aicontrolcentre.core.model.ProjectsRepository
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.BadgeTone
import com.ivnsrg.aicontrolcentre.core.ui.components.CardTone
import com.ivnsrg.aicontrolcentre.core.ui.components.EmptyState
import com.ivnsrg.aicontrolcentre.core.ui.components.HeaderDensity
import com.ivnsrg.aicontrolcentre.core.ui.components.MetadataChip
import com.ivnsrg.aicontrolcentre.core.ui.components.OperationalCard
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton
import com.ivnsrg.aicontrolcentre.core.ui.components.SectionLabel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import kotlinx.coroutines.launch

@Composable
fun NewChatRoute(
    projectsRepository: ProjectsRepository,
    threadsRepository: ThreadsRepository,
    onThreadCreated: (Long) -> Unit,
    onCreateProjectClick: () -> Unit,
) {
    val projects by projectsRepository.observeProjects().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val targetProject = projects.firstOrNull()
    var createError by remember { mutableStateOf<String?>(null) }

    AppScreenScaffold(
        title = "New Chat",
        subtitle = "Start a new thread in your latest project",
        headerDensity = HeaderDensity.Compact,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (targetProject == null) {
                EmptyState(
                    title = "Create a project first",
                    subtitle = "Threads live inside projects so local history stays organized.",
                    action = {
                        PrimaryButton(
                            text = "Go to Projects",
                            onClick = onCreateProjectClick,
                        )
                    },
                )
            } else {
                OperationalCard(
                    tone = CardTone.Surface2,
                    padding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel("Target project", tone = BadgeTone.Info)
                        MetadataChip(text = targetProject.title, tone = BadgeTone.Primary)
                        createError?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textSecondary,
                            )
                        }
                        PrimaryButton(
                            text = "Start new thread",
                            onClick = {
                                coroutineScope.launch {
                                    createError = null
                                    val freshProject = projectsRepository.getProject(targetProject.id)
                                    if (freshProject == null) {
                                        createError = "Project was removed. Go back to Projects and choose another workspace."
                                        return@launch
                                    }

                                    runCatching {
                                        threadsRepository.createThread(freshProject.id)
                                    }.onSuccess { threadId ->
                                        onThreadCreated(threadId)
                                    }.onFailure {
                                        createError = "Could not start a thread for this project. Refresh Projects and try again."
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
