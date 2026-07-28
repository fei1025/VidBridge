package com.vidbridge

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vidbridge.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as VidBridgeApplication).container
        setContent {
            VidBridgeTheme { VidBridgeNav(container) }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VidBridgeNav(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "sources") {
        composable("sources") {
            SourcesScreen(
                container = container,
                onAdd = { nav.navigate("sources/add") },
                onEdit = { nav.navigate("sources/edit/$it") },
                onSettings = { nav.navigate("settings") },
                onLibrary = { nav.navigate("library") },
                onBrowse = { nav.navigate("browser/$it") },
            )
        }
        composable("settings") {
            SettingsScreen(container) { nav.popBackStack() }
        }
        composable("library") {
            LibraryScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpen = { sourceId, path, isDirectory ->
                    if (isDirectory) {
                        nav.navigate("browser/$sourceId?path=${Uri.encode(path)}")
                    } else {
                        nav.navigate("player/$sourceId?path=${Uri.encode(path)}")
                    }
                },
            )
        }
        composable("sources/add") {
            AddSourceScreen(container) { nav.popBackStack() }
        }
        composable(
            route = "sources/edit/{sourceId}",
            arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
        ) { backStack ->
            AddSourceScreen(
                container = container,
                sourceId = requireNotNull(backStack.arguments?.getString("sourceId")),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "browser/{sourceId}?path={path}",
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStack ->
            val sourceId = requireNotNull(backStack.arguments?.getString("sourceId"))
            BrowserScreen(
                container = container,
                sourceId = sourceId,
                initialPath = backStack.arguments?.getString("path"),
                onBack = { nav.popBackStack() },
                onPlay = { entry ->
                    nav.navigate("player/$sourceId?path=${Uri.encode(entry.path.value)}")
                },
            )
        }
        composable(
            route = "player/{sourceId}?path={path}",
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType },
            ),
        ) { backStack ->
            PlayerScreen(
                container = container,
                sourceId = requireNotNull(backStack.arguments?.getString("sourceId")),
                path = requireNotNull(backStack.arguments?.getString("path")),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
