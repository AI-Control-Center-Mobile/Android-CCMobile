package com.ivnsrg.aicontrolcentre.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ivnsrg.aicontrolcentre.AiControlCentreApp
import com.ivnsrg.aicontrolcentre.core.model.AppRoute
import com.ivnsrg.aicontrolcentre.core.model.ProjectsRepository
import com.ivnsrg.aicontrolcentre.core.ui.components.EmptyState
import com.ivnsrg.aicontrolcentre.core.ui.components.LoadingState
import com.ivnsrg.aicontrolcentre.feature.chat.ThreadRoute
import com.ivnsrg.aicontrolcentre.feature.compare.CompareRoute
import com.ivnsrg.aicontrolcentre.feature.projects.ProjectDetailRoute
import com.ivnsrg.aicontrolcentre.feature.projects.ProjectsRoute
import com.ivnsrg.aicontrolcentre.feature.settings.SettingsRoute
import com.ivnsrg.aicontrolcentre.feature.setup.SetupRoute
import androidx.compose.ui.platform.LocalContext

private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun AiControlCentreNavHost() {
    val context = LocalContext.current.applicationContext as AiControlCentreApp
    val container = context.appContainer
    val navController = rememberNavController()
    val navItems = remember {
        listOf(
            NavItem(AppRoute.PROJECTS, "Projects", Icons.Default.Home),
            NavItem(AppRoute.NEW_CHAT, "New Chat", Icons.Default.Create),
            NavItem(AppRoute.SETTINGS, "Settings", Icons.Default.Settings),
        )
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val topLevelRoutes = navItems.map { it.route }.toSet()

    val startDestination by produceState<String?>(initialValue = null) {
        value = if (container.settingsRepository.getApiKey().isNullOrBlank()) {
            AppRoute.SETUP
        } else {
            AppRoute.PROJECTS
        }
    }

    if (startDestination == null) {
        LoadingState()
        return
    }

    Scaffold(
        bottomBar = {
            val currentRoute = currentDestination?.route
            if (currentRoute in topLevelRoutes) {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppRoute.SETUP) {
                SetupRoute(
                    settingsRepository = container.settingsRepository,
                    onCompleted = {
                        navController.navigate(AppRoute.PROJECTS) {
                            popUpTo(AppRoute.SETUP) { inclusive = true }
                        }
                    },
                )
            }
            composable(AppRoute.PROJECTS) {
                ProjectsRoute(
                    projectsRepository = container.projectsRepository,
                    onProjectClick = { navController.navigate(AppRoute.project(it)) },
                )
            }
            composable(AppRoute.NEW_CHAT) {
                NewChatRoute(
                    projectsRepository = container.projectsRepository,
                    onOpenProjects = { navController.navigate(AppRoute.PROJECTS) },
                    onProjectClick = { navController.navigate(AppRoute.project(it)) },
                )
            }
            composable(AppRoute.SETTINGS) {
                SettingsRoute(
                    settingsRepository = container.settingsRepository,
                    onApiKeyRemoved = {
                        navController.navigate(AppRoute.SETUP) {
                            popUpTo(0)
                        }
                    },
                )
            }
            composable(
                route = AppRoute.PROJECT,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
            ) { entry ->
                val projectId = requireNotNull(entry.arguments?.getLong("projectId"))
                ProjectDetailRoute(
                    projectId = projectId,
                    projectsRepository = container.projectsRepository,
                    threadsRepository = container.threadsRepository,
                    onThreadClick = { navController.navigate(AppRoute.thread(it)) },
                )
            }
            composable(
                route = AppRoute.THREAD,
                arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
            ) { entry ->
                val threadId = requireNotNull(entry.arguments?.getLong("threadId"))
                ThreadRoute(
                    threadId = threadId,
                    threadsRepository = container.threadsRepository,
                    onCompareClick = { navController.navigate(AppRoute.compare(it)) },
                )
            }
            composable(
                route = AppRoute.COMPARE,
                arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
            ) { entry ->
                val threadId = requireNotNull(entry.arguments?.getLong("threadId"))
                CompareRoute(
                    threadId = threadId,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun NewChatRoute(
    projectsRepository: ProjectsRepository,
    onOpenProjects: () -> Unit,
    onProjectClick: (Long) -> Unit,
) {
    val projects by produceState(initialValue = emptyList(), projectsRepository) {
        projectsRepository.observeProjects().collect { value = it }
    }

    if (projects.isEmpty()) {
        EmptyState(
            title = "Сначала нужен project",
            subtitle = "Создай проект в разделе Projects и потом стартуй новый thread.",
        )
    } else {
        EmptyState(
            title = "New Chat entry",
            subtitle = "Bootstrap-заглушка. Выбери project в Projects для запуска нового thread.",
        )
    }
}
