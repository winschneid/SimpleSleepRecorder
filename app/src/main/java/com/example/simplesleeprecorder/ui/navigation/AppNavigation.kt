package com.example.simplesleeprecorder.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.simplesleeprecorder.SimpleSleepRecorderApp
import com.example.simplesleeprecorder.ui.history.HistoryScreen
import com.example.simplesleeprecorder.ui.history.HistoryViewModel
import com.example.simplesleeprecorder.ui.home.HomeScreen
import com.example.simplesleeprecorder.ui.home.HomeViewModel
import com.example.simplesleeprecorder.ui.result.ResultScreen
import com.example.simplesleeprecorder.ui.result.ResultViewModel

sealed class Screen(val route: String, val label: String) {
    data object Home : Screen("home", "計測")
    data object History : Screen("history", "履歴")
    data object Result : Screen("result/{sessionId}", "結果") {
        fun createRoute(sessionId: Long) = "result/$sessionId"
    }
}

private val bottomNavItems = listOf(Screen.Home, Screen.History)

@Composable
fun AppNavigation(app: SimpleSleepRecorderApp) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = when (screen) {
                                        Screen.Home -> Icons.Default.Nightlight
                                        Screen.History -> Icons.Default.History
                                        else -> Icons.Default.Nightlight
                                    },
                                    contentDescription = screen.label,
                                )
                            },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                val vm: HomeViewModel = viewModel(factory = app.viewModelFactory)
                HomeScreen(
                    viewModel = vm,
                    onSessionEnded = { sessionId ->
                        navController.navigate(Screen.Result.createRoute(sessionId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Screen.History.route) {
                val vm: HistoryViewModel = viewModel(factory = app.viewModelFactory)
                HistoryScreen(viewModel = vm)
            }
            composable(
                route = Screen.Result.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                val vm: ResultViewModel = viewModel(factory = app.viewModelFactory)
                ResultScreen(
                    sessionId = sessionId,
                    viewModel = vm,
                    onDone = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
