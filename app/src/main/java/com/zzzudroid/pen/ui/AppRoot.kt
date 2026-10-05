package com.zzzudroid.pen.ui

import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zzzudroid.pen.diag.DiagnosticsViewModel
import com.zzzudroid.pen.notes.DocKind

// Маршруты навигации
object Routes {
    const val HOME = "home"
    const val LIST = "list/{kind}"

    /** Полный список документов одного вида: `notes` — тетради, `boards` — доски. */
    fun list(kind: DocKind) = "list/" + if (kind == DocKind.BOARD) "boards" else "notes"
    const val NOTE = "note/{id}?page={page}&bm={bm}&view={view}&focus={focus}"
    const val SEARCH = "search"

    /** Редактор тетради; [page] — индекс страницы с нуля, на которой открыть. */
    fun note(
        id: String,
        page: Int = 0,
        bookmarkId: String? = null,
        view: com.zzzudroid.pen.canvas.ViewState? = null,
        focus: Pair<Float, Float>? = null,
    ): String = buildString {
        append("note/").append(Uri.encode(id)).append("?page=").append(page)
        // Место, куда прийти: закладка доски, прямой вид «масштаб,сдвигX,сдвигY» или точка «x,y» в центр экрана
        if (bookmarkId != null) append("&bm=").append(Uri.encode(bookmarkId))
        if (view != null) append("&view=").append(Uri.encode("${view.scale},${view.tx},${view.ty}"))
        if (focus != null) append("&focus=").append(Uri.encode("${focus.first},${focus.second}"))
    }
    const val CANVAS = "canvas"
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
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
                HomeScreen(
                    onNavigate = { route -> nav.navigate(route) },
                    onOpenList = { kind -> nav.navigate(Routes.list(kind)) },
                    onOpenDocument = { id -> nav.navigate(Routes.note(id)) },
                )
            }
            composable(Routes.LIST, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
                val kind = if (entry.arguments?.getString("kind") == "boards") DocKind.BOARD else DocKind.NOTEBOOK
                NotesListScreen(kind = kind, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate(Routes.note(id)) })
            }
            composable(
                Routes.NOTE,
                arguments = listOf(
                    navArgument("page") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("bm") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("view") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                NoteEditorScreen(onBack = { nav.popBackStack() }, onNavigate = { route -> nav.navigate(route) })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onOpen = { id, page -> nav.navigate(Routes.note(id, page)) },
                )
            }
            composable(Routes.CANVAS) {
                CanvasScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(diagnostics, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
