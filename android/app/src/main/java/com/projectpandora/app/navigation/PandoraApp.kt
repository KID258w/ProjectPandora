package com.projectpandora.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.projectpandora.app.BuildConfig
import com.projectpandora.app.core.network.ApiClient
import com.projectpandora.app.feature.account.*
import com.projectpandora.app.feature.ai.AiMapScreen
import com.projectpandora.app.feature.home.HomeScreen
import com.projectpandora.app.feature.logs.LogsScreen
import com.projectpandora.app.feature.profile.*
import com.projectpandora.app.feature.tasks.TaskCenterScreen
import com.projectpandora.app.feature.views.ViewsScreen
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C
import kotlinx.coroutines.delay

private object AccountRoute {
    const val LAUNCH = "launch"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val INFO = "personal-info"
    const val NOTIFICATIONS = "notifications"
    const val HELP = "help"
}

@Composable
fun PandoraApp(
    startupDelayMillis: Long = 1000L,
    accountRepository: AccountRepository? = null,
) {
    val context = LocalContext.current.applicationContext
    val tokenStorage = remember(context) { AndroidTokenStorage(context) }
    val preferences = remember(context) { AccountPreferences(context) }
    val repository = remember(accountRepository, tokenStorage) {
        accountRepository ?: ApiAccountRepository(ApiClient.createAppAccountApi(tokenStorage), tokenStorage)
    }
    val requiresLocalNetworkPermission = remember(accountRepository) {
        accountRepository == null && LocalNetworkPermission.isRequired(BuildConfig.API_BASE_URL)
    }
    val factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(repository) as T
        }
    }
    val model: AccountViewModel = viewModel(factory = factory)
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: AccountRoute.LAUNCH
    val selectedTab = MainTab.entries.find { it.route == route }
    val user = model.user
    fun resetTo(destination: String) {
        nav.navigate(destination) { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
    }
    fun selectTab(tab: MainTab) {
        nav.navigate(tab.route) {
            popUpTo(MainTab.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(user, route) {
        if (user != null && route == AccountRoute.LOGIN) resetTo(MainTab.HOME.route)
    }
    LaunchedEffect(route, model.initializing, model.startupDestination) {
        if (route == AccountRoute.LAUNCH && !model.initializing) {
            // Wait for NavHost to install its graph before resetting the initial route.
            withFrameNanos { }
            delay(startupDelayMillis)
            val destination = when (model.startupDestination) {
                AccountStartupDestination.WELCOME -> AccountRoute.WELCOME
                AccountStartupDestination.LOGIN -> AccountRoute.LOGIN
                AccountStartupDestination.HOME -> MainTab.HOME.route
            }
            resetTo(destination)
        }
    }
    Box(Modifier.fillMaxSize().background(if (route == AccountRoute.LAUNCH) C.Amber else Color.White)
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Scaffold(containerColor = Color.White, contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { if (selectedTab != null && user != null) PandoraBottomBar(selectedTab, ::selectTab) }) { padding ->
            NavHost(nav, startDestination = AccountRoute.LAUNCH, Modifier.padding(padding)) {
                composable(AccountRoute.LAUNCH) {
                    LaunchScreen()
                }
                composable(AccountRoute.WELCOME) { WelcomeScreen { nav.navigate(AccountRoute.LOGIN) } }
                composable(AccountRoute.LOGIN) { LoginScreen(model, requiresLocalNetworkPermission) }
                composable(MainTab.HOME.route) { user?.let { HomeScreen(it, { selectTab(MainTab.VIEWS) }, { selectTab(MainTab.LOGS) }) } }
                composable(MainTab.VIEWS.route) { ViewsScreen { nav.navigate(AppRoute.TASK_CENTER) } }
                composable(MainTab.LOGS.route) { LogsScreen() }
                composable(MainTab.AI_MAP.route) { AiMapScreen() }
                composable(MainTab.PROFILE.route) {
                    user?.let {
                        ProfileScreen(it, { nav.navigate(AccountRoute.INFO) }, { nav.navigate(AccountRoute.NOTIFICATIONS) },
                            { nav.navigate(AccountRoute.HELP) }, { model.logout(); resetTo(AccountRoute.LOGIN) })
                    }
                }
                composable(AccountRoute.INFO) { user?.let { PersonalInfoScreen(it) { nav.popBackStack() } } }
                composable(AccountRoute.NOTIFICATIONS) { NotificationScreen(preferences) { nav.popBackStack() } }
                composable(AccountRoute.HELP) { HelpScreen { nav.popBackStack() } }
                composable(AppRoute.TASK_CENTER) { Column { BackButton { nav.popBackStack() }; TaskCenterScreen() } }
            }
        }
    }
}
