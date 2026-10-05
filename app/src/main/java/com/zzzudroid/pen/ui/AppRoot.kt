package com.zzzudroid.pen.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zzzudroid.pen.diag.DiagnosticsViewModel

// Маршруты навигации
object Routes {
    const val HOME = "home"
    const val CANVAS = "canvas"
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"
}

@Composable
fun AppRoot(diagnostics: DiagnosticsViewModel) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
    else dynamicLightColorScheme(context)

    MaterialTheme(colorScheme = colors) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(onOpen = { route -> nav.navigate(route) })
            }
            composable(Routes.CANVAS) {
                CanvasScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(diagnostics, onBack = { nav.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
