package com.ivnsrg.aicontrolcentre.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ivnsrg.aicontrolcentre.AiControlCentreApp
import com.ivnsrg.aicontrolcentre.core.model.AppRoute
import com.ivnsrg.aicontrolcentre.core.model.ModelPickerMode
import com.ivnsrg.aicontrolcentre.core.ui.components.FloatingBottomBarContainer
import com.ivnsrg.aicontrolcentre.core.ui.components.LoadingState
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import com.ivnsrg.aicontrolcentre.app.ui.ModelPickerRoute
import com.ivnsrg.aicontrolcentre.app.ui.NewChatRoute
import com.ivnsrg.aicontrolcentre.feature.chat.ThreadRoute
import com.ivnsrg.aicontrolcentre.feature.compare.CompareRoute
import com.ivnsrg.aicontrolcentre.feature.projects.ProjectDetailRoute
import com.ivnsrg.aicontrolcentre.feature.projects.ProjectsRoute
import com.ivnsrg.aicontrolcentre.feature.settings.SettingsRoute
import com.ivnsrg.aicontrolcentre.feature.setup.SetupRoute
import androidx.compose.material3.MaterialTheme

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
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

    NavHost(
        navController = navController,
        startDestination = startDestination!!,
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
            ScreenWithBottomBar(
                navItems = navItems,
                currentRoute = currentDestination?.route,
                isProjectsSelected = true,
                isNewChatSelected = false,
                isSettingsSelected = false,
                onProjectsClick = { navController.navigateToTopLevel(AppRoute.PROJECTS) },
                onNewChatClick = { navController.navigateToTopLevel(AppRoute.NEW_CHAT) },
                onSettingsClick = { navController.navigateToTopLevel(AppRoute.SETTINGS) },
            ) {
                ProjectsRoute(
                    projectsRepository = container.projectsRepository,
                    onProjectClick = { navController.navigate(AppRoute.project(it)) },
                )
            }
        }
        composable(AppRoute.SETTINGS) {
            ScreenWithBottomBar(
                navItems = navItems,
                currentRoute = currentDestination?.route,
                isProjectsSelected = false,
                isNewChatSelected = false,
                isSettingsSelected = true,
                onProjectsClick = { navController.navigateToTopLevel(AppRoute.PROJECTS) },
                onNewChatClick = { navController.navigateToTopLevel(AppRoute.NEW_CHAT) },
                onSettingsClick = { navController.navigateToTopLevel(AppRoute.SETTINGS) },
            ) {
                SettingsRoute(
                    settingsRepository = container.settingsRepository,
                    onApiKeyRemoved = {
                        navController.navigate(AppRoute.SETUP) {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
        composable(AppRoute.NEW_CHAT) {
            ScreenWithBottomBar(
                navItems = navItems,
                currentRoute = currentDestination?.route,
                isProjectsSelected = false,
                isNewChatSelected = true,
                isSettingsSelected = false,
                onProjectsClick = { navController.navigateToTopLevel(AppRoute.PROJECTS) },
                onNewChatClick = { navController.navigateToTopLevel(AppRoute.NEW_CHAT) },
                onSettingsClick = { navController.navigateToTopLevel(AppRoute.SETTINGS) },
            ) {
                NewChatRoute(
                    projectsRepository = container.projectsRepository,
                    threadsRepository = container.threadsRepository,
                    onThreadCreated = { threadId -> navController.navigate(AppRoute.thread(threadId)) },
                    onCreateProjectClick = { navController.navigateToTopLevel(AppRoute.PROJECTS) },
                )
            }
        }
        composable(
            route = AppRoute.PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
        ) { entry ->
            val projectId = requireNotNull(entry.arguments?.getLong("projectId"))
            ScreenWithBottomBar(
                navItems = navItems,
                currentRoute = currentDestination?.route,
                isProjectsSelected = true,
                isNewChatSelected = false,
                isSettingsSelected = false,
                onProjectsClick = { navController.navigateToTopLevel(AppRoute.PROJECTS) },
                onNewChatClick = { navController.navigateToTopLevel(AppRoute.NEW_CHAT) },
                onSettingsClick = { navController.navigateToTopLevel(AppRoute.SETTINGS) },
            ) {
                ProjectDetailRoute(
                    projectId = projectId,
                    projectsRepository = container.projectsRepository,
                    threadsRepository = container.threadsRepository,
                    onBack = { navController.popBackStack() },
                    onThreadClick = { navController.navigate(AppRoute.thread(it)) },
                )
            }
        }
        composable(
            route = AppRoute.THREAD,
            arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
        ) { entry ->
            val threadId = requireNotNull(entry.arguments?.getLong("threadId"))
            val compareSelectedModelId by entry.savedStateHandle
                .getStateFlow<String?>(COMPARE_SELECTED_MODEL_KEY, null)
                .collectAsState()
            val pickedChatModelId by entry.savedStateHandle
                .getStateFlow<String?>(MODEL_PICKER_CHAT_RESULT_KEY, null)
                .collectAsState()
            ThreadRoute(
                threadId = threadId,
                threadsRepository = container.threadsRepository,
                modelsRepository = container.modelsRepository,
                settingsRepository = container.settingsRepository,
                chatRepository = container.chatRepository,
                compareSelectedModelId = compareSelectedModelId,
                pickedModelId = pickedChatModelId,
                onCompareSelectionConsumed = {
                    entry.savedStateHandle[COMPARE_SELECTED_MODEL_KEY] = null
                },
                onPickedModelConsumed = {
                    entry.savedStateHandle[MODEL_PICKER_CHAT_RESULT_KEY] = null
                },
                onCompareClick = { selectedModelId ->
                    entry.savedStateHandle[COMPARE_INITIAL_MODEL_KEY] = selectedModelId
                    navController.navigate(AppRoute.compare(threadId))
                },
                onModelPickerClick = { selectedModelId ->
                    entry.savedStateHandle[MODEL_PICKER_CURRENT_KEY] = selectedModelId
                    navController.navigate(AppRoute.modelPicker(ModelPickerMode.CHAT.name))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoute.COMPARE,
            arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
        ) { entry ->
            val threadId = requireNotNull(entry.arguments?.getLong("threadId"))
            val previousEntry = navController.previousBackStackEntry
            val initialSelectedModel = previousEntry
                ?.savedStateHandle
                ?.get<String?>(COMPARE_INITIAL_MODEL_KEY)
            previousEntry?.savedStateHandle?.set(COMPARE_INITIAL_MODEL_KEY, null)
            val pickedCompareAModelId by entry.savedStateHandle
                .getStateFlow<String?>(MODEL_PICKER_COMPARE_A_RESULT_KEY, null)
                .collectAsState()
            val pickedCompareBModelId by entry.savedStateHandle
                .getStateFlow<String?>(MODEL_PICKER_COMPARE_B_RESULT_KEY, null)
                .collectAsState()
            CompareRoute(
                threadId = threadId,
                initialSelectedModelId = initialSelectedModel,
                threadsRepository = container.threadsRepository,
                modelsRepository = container.modelsRepository,
                settingsRepository = container.settingsRepository,
                compareRepository = container.compareRepository,
                pickedModelAId = pickedCompareAModelId,
                pickedModelBId = pickedCompareBModelId,
                onPickedModelAConsumed = {
                    entry.savedStateHandle[MODEL_PICKER_COMPARE_A_RESULT_KEY] = null
                },
                onPickedModelBConsumed = {
                    entry.savedStateHandle[MODEL_PICKER_COMPARE_B_RESULT_KEY] = null
                },
                onModelChosen = { modelId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(COMPARE_SELECTED_MODEL_KEY, modelId)
                    navController.popBackStack()
                },
                onOpenModelPicker = { mode, selectedModelId ->
                    entry.savedStateHandle[MODEL_PICKER_CURRENT_KEY] = selectedModelId
                    navController.navigate(AppRoute.modelPicker(mode.name))
                },
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoute.MODEL_PICKER,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val modeArg = requireNotNull(entry.arguments?.getString("mode"))
            val mode = ModelPickerMode.valueOf(modeArg)
            val previousEntry = navController.previousBackStackEntry
            val currentSelection = previousEntry
                ?.savedStateHandle
                ?.get<String?>(MODEL_PICKER_CURRENT_KEY)
            previousEntry?.savedStateHandle?.set(MODEL_PICKER_CURRENT_KEY, null)

            ModelPickerRoute(
                mode = mode,
                currentSelection = currentSelection,
                modelsRepository = container.modelsRepository,
                settingsRepository = container.settingsRepository,
                onBack = { navController.popBackStack() },
                onModelSelected = { modelId ->
                    when (mode) {
                        ModelPickerMode.CHAT -> previousEntry?.savedStateHandle?.set(MODEL_PICKER_CHAT_RESULT_KEY, modelId)
                        ModelPickerMode.COMPARE_A -> previousEntry?.savedStateHandle?.set(MODEL_PICKER_COMPARE_A_RESULT_KEY, modelId)
                        ModelPickerMode.COMPARE_B -> previousEntry?.savedStateHandle?.set(MODEL_PICKER_COMPARE_B_RESULT_KEY, modelId)
                    }
                    navController.popBackStack()
                },
            )
        }
    }
}

@Composable
private fun ScreenWithBottomBar(
    navItems: List<NavItem>,
    currentRoute: String?,
    isProjectsSelected: Boolean,
    isNewChatSelected: Boolean,
    isSettingsSelected: Boolean,
    onProjectsClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val bottomBarRoutes = navItems.map { it.route }.toSet() + AppRoute.PROJECT
    androidx.compose.material3.Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                FloatingBottomBarContainer {
                    FloatingNavItem(
                        icon = Icons.Default.Home,
                        label = "Projects",
                        selected = isProjectsSelected,
                        onClick = onProjectsClick,
                    )
                    FloatingNavItem(
                        icon = Icons.Default.Create,
                        label = "New Chat",
                        selected = isNewChatSelected,
                        onClick = onNewChatClick,
                    )
                    FloatingNavItem(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        selected = isSettingsSelected,
                        onClick = onSettingsClick,
                    )
                }
            }
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(innerPadding),
        ) {
            content()
        }
    }
}

@Composable
private fun RowScope.FloatingNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) colors.accentPrimary else colors.textMuted,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.textPrimary else colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun androidx.navigation.NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private const val COMPARE_INITIAL_MODEL_KEY = "compare_initial_model"
private const val COMPARE_SELECTED_MODEL_KEY = "compare_selected_model"
private const val MODEL_PICKER_CURRENT_KEY = "model_picker_current"
private const val MODEL_PICKER_CHAT_RESULT_KEY = "model_picker_chat_result"
private const val MODEL_PICKER_COMPARE_A_RESULT_KEY = "model_picker_compare_a_result"
private const val MODEL_PICKER_COMPARE_B_RESULT_KEY = "model_picker_compare_b_result"
