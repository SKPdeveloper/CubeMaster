package com.example.cubemaster.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.*
import androidx.navigation.compose.*
import com.example.cubemaster.presentation.catalog.CatalogScreen
import com.example.cubemaster.presentation.demolition.DemolitionScreen
import com.example.cubemaster.presentation.documents.ProjectDocumentsScreen
import com.example.cubemaster.presentation.estimate.EstimateScreen
import com.example.cubemaster.presentation.geometry.GeometryScreen
import com.example.cubemaster.presentation.layers.LayersScreen
import com.example.cubemaster.presentation.objectplan.ObjectPlanScreen
import com.example.cubemaster.presentation.profile.ProfileScreen
import com.example.cubemaster.presentation.projects.ProjectsScreen
import com.example.cubemaster.presentation.rooms.RoomsScreen
import com.example.cubemaster.presentation.summary.SummaryScreen

sealed class Screen(val route: String) {
    object Projects : Screen("projects")
    object Rooms : Screen("rooms/{projectId}") {
        fun createRoute(projectId: String) = "rooms/$projectId"
    }
    object ObjectPlan : Screen("object-plan/{projectId}") {
        fun createRoute(projectId: String) = "object-plan/$projectId"
    }
    object Geometry : Screen("geometry/{roomId}") {
        fun createRoute(roomId: String) = "geometry/$roomId"
    }
    object Layers : Screen("layers/{roomId}/{surfaceId}") {
        fun createRoute(roomId: String, surfaceId: String) = "layers/$roomId/$surfaceId"
    }
    object Demolition : Screen("demolition/{roomId}") {
        fun createRoute(roomId: String) = "demolition/$roomId"
    }
    object Summary : Screen("summary/{projectId}") {
        fun createRoute(projectId: String) = "summary/$projectId"
    }
    object Estimate : Screen("estimate/{projectId}") {
        fun createRoute(projectId: String) = "estimate/$projectId"
    }
    object Catalog : Screen("catalog")
    object Profile : Screen("profile")
    object ProjectDocuments : Screen("project-documents/{projectId}") {
        fun createRoute(projectId: String) = "project-documents/$projectId"
    }
}

@Composable
fun AppNavigation(startDestination: String = Screen.Projects.route, pendingShortcutAction: String? = null) {
    val navController = rememberNavController()

    // Ярлики застосунку ("Каталог"/"Профіль") відкривають екран одразу поверх стартового;
    // "Новий проєкт" обробляється всередині ProjectsScreen (діалог створення).
    LaunchedEffect(pendingShortcutAction) {
        when (pendingShortcutAction) {
            "catalog" -> navController.navigate(Screen.Catalog.route)
            "profile" -> navController.navigate(Screen.Profile.route)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Projects.route) {
            ProjectsScreen(
                openCreateDialogOnStart = pendingShortcutAction == "new_project",
                onProjectClick = { projectId ->
                    navController.navigate(Screen.Rooms.createRoute(projectId))
                },
                onSummaryClick = { projectId ->
                    navController.navigate(Screen.Summary.createRoute(projectId))
                },
                onCatalogClick = { navController.navigate(Screen.Catalog.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) }
            )
        }

        composable(
            Screen.Rooms.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStack ->
            val projectId = backStack.arguments!!.getString("projectId")!!
            RoomsScreen(
                projectId = projectId,
                onRoomClick = { roomId ->
                    navController.navigate(Screen.Geometry.createRoute(roomId))
                },
                onObjectPlanClick = {
                    navController.navigate(Screen.ObjectPlan.createRoute(projectId))
                },
                onSummaryClick = {
                    navController.navigate(Screen.Summary.createRoute(projectId))
                },
                onEstimateClick = {
                    navController.navigate(Screen.Estimate.createRoute(projectId))
                },
                onDocumentsClick = {
                    navController.navigate(Screen.ProjectDocuments.createRoute(projectId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.ObjectPlan.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) {
            ObjectPlanScreen(
                onRoomClick = { roomId ->
                    navController.navigate(Screen.Geometry.createRoute(roomId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Geometry.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStack ->
            val roomId = backStack.arguments!!.getString("roomId")!!
            GeometryScreen(
                roomId = roomId,
                onLayersClick = { surfaceId ->
                    navController.navigate(Screen.Layers.createRoute(roomId, surfaceId))
                },
                onDemolitionClick = {
                    navController.navigate(Screen.Demolition.createRoute(roomId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Layers.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("surfaceId") { type = NavType.StringType }
            )
        ) { backStack ->
            val roomId = backStack.arguments!!.getString("roomId")!!
            val surfaceId = backStack.arguments!!.getString("surfaceId")!!
            LayersScreen(
                roomId = roomId,
                surfaceId = surfaceId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Demolition.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStack ->
            DemolitionScreen(
                roomId = backStack.arguments!!.getString("roomId")!!,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Summary.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStack ->
            SummaryScreen(
                projectId = backStack.arguments!!.getString("projectId")!!,
                onEstimateClick = { projectId ->
                    navController.navigate(Screen.Estimate.createRoute(projectId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Estimate.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStack ->
            EstimateScreen(
                projectId = backStack.arguments!!.getString("projectId")!!,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Catalog.route) {
            CatalogScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Profile.route) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.ProjectDocuments.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) {
            ProjectDocumentsScreen(onBack = { navController.popBackStack() })
        }
    }
}
