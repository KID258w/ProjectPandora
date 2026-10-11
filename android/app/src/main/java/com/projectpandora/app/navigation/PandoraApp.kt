package com.projectpandora.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.projectpandora.app.feature.ai.AiMapScreen
import com.projectpandora.app.feature.home.HomeScreen
import com.projectpandora.app.feature.logs.LogsScreen
import com.projectpandora.app.feature.profile.ProfileScreen
import com.projectpandora.app.feature.tasks.TaskCenterScreen
import com.projectpandora.app.feature.views.ViewsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PandoraApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val selectedTab = MainTab.entries.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        topBar = {
            if (currentRoute == AppRoute.TASK_CENTER) {
                CenterAlignedTopAppBar(
                    title = { Text("任务中心") },
                    navigationIcon = {
                        TextButton(onClick = { navController.popBackStack() }) {
                            Text("返回")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selectedTab != null) {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.label.take(1)) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.HOME.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(MainTab.HOME.route) { HomeScreen() }
            composable(MainTab.VIEWS.route) {
                ViewsScreen(onOpenTaskCenter = { navController.navigate(AppRoute.TASK_CENTER) })
            }
            composable(MainTab.LOGS.route) { LogsScreen() }
            composable(MainTab.AI_MAP.route) { AiMapScreen() }
            composable(MainTab.PROFILE.route) { ProfileScreen() }
            composable(AppRoute.TASK_CENTER) { TaskCenterScreen() }
        }
    }
}
