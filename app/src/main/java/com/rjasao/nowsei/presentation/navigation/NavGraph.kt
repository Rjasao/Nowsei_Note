package com.rjasao.nowsei.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rjasao.nowsei.presentation.notebook_detail.NotebookDetailScreen
import com.rjasao.nowsei.presentation.notebooks.NotebooksScreen
import com.rjasao.nowsei.presentation.page_detail.PageDetailScreen
import com.rjasao.nowsei.presentation.section_detail.SectionDetailScreen
import com.rjasao.nowsei.presentation.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.NOTEBOOKS
    ) {
        composable(Routes.NOTEBOOKS) {
            NotebooksScreen(navController = navController)
        }

        composable(Routes.SETTINGS) {
            // ✅ SettingsScreen atual não recebe navController
            SettingsScreen()
        }

        composable(
            route = Routes.NOTEBOOK_DETAIL,
            arguments = listOf(
                navArgument("notebookId") { type = NavType.StringType }
            )
        ) {
            NotebookDetailScreen(navController = navController)
        }

        composable(
            route = Routes.SECTION_DETAIL,
            arguments = listOf(
                navArgument("sectionId") { type = NavType.StringType },
                navArgument("sectionTitle") { type = NavType.StringType }
            )
        ) {
            SectionDetailScreen(navController = navController)
        }

        composable(
            route = Routes.PAGE_DETAIL,
            arguments = listOf(
                navArgument("pageId") { type = NavType.StringType }
            )
        ) {
            PageDetailScreen(navController = navController)
        }
    }
}
