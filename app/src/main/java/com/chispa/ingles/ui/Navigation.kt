package com.chispa.ingles.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chispa.ingles.ui.home.HomeScreen
import com.chispa.ingles.ui.lesson.LessonScreen
import com.chispa.ingles.ui.lesson.SessionMode
import com.chispa.ingles.ui.onboarding.OnboardingScreen
import com.chispa.ingles.ui.onboarding.PlacementScreen
import com.chispa.ingles.ui.profile.AchievementsScreen
import com.chispa.ingles.ui.profile.ProfileScreen
import com.chispa.ingles.ui.profile.VocabularyScreen
import com.chispa.ingles.ui.reader.LibraryScreen
import com.chispa.ingles.ui.reader.ReaderScreen
import com.chispa.ingles.ui.review.ReviewScreen
import com.chispa.ingles.ui.settings.SettingsScreen
import com.chispa.ingles.ui.speaking.SpeakingScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val PLACEMENT = "placement"

    const val HOME = "home"
    const val LIBRARY = "library"
    const val REVIEW = "review"
    const val SPEAKING = "speaking"
    const val PROFILE = "profile"

    const val READER = "reader/{readingId}"
    fun reader(readingId: String) = "reader/$readingId"

    const val SETTINGS = "settings"
    const val ACHIEVEMENTS = "achievements"
    const val VOCABULARY = "vocabulary"

    const val LESSON = "lesson/{lessonId}"
    fun lesson(lessonId: String) = "lesson/$lessonId"

    const val REVIEW_SESSION = "review_session"
    const val SPEAKING_SESSION = "speaking_session"

    val bottomBarRoutes = setOf(HOME, LIBRARY, REVIEW, SPEAKING, PROFILE)
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val TABS = listOf(
    TabItem(Routes.HOME, "Aprender", Icons.Filled.School),
    TabItem(Routes.LIBRARY, "Leer", Icons.AutoMirrored.Filled.MenuBook),
    TabItem(Routes.REVIEW, "Repaso", Icons.Filled.Refresh),
    TabItem(Routes.SPEAKING, "Hablar", Icons.Filled.Mic),
    TabItem(Routes.PROFILE, "Perfil", Icons.Filled.Person)
)

@Composable
fun ChispaAppRoot(
    deepLink: String?,
    onDeepLinkConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = chispaViewModel { AppViewModel(it) }
    val state by appViewModel.state.collectAsState()

    // Deep link desde una notificación: nos lleva directo a la sección pedida.
    LaunchedEffect(deepLink, state.stage) {
        if (deepLink != null && state.stage == AppStage.READY) {
            val route = when (deepLink) {
                "review" -> Routes.REVIEW
                else -> Routes.HOME
            }
            if (route != Routes.HOME) {
                navController.navigate(route) { launchSingleTop = true }
            }
            onDeepLinkConsumed()
        }
    }

    when (state.stage) {
        AppStage.LOADING -> SplashScreen()
        AppStage.ONBOARDING -> OnboardingScreen(
            onFinished = { appViewModel.refresh() },
            onRequestNotificationPermission = onRequestNotificationPermission
        )
        AppStage.PLACEMENT -> PlacementScreen(onFinished = { appViewModel.refresh() })
        AppStage.READY -> MainScaffold(navController = navController)
    }
}

@Composable
private fun MainScaffold(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.bottomBarRoutes

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(180)) }
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenLesson = { navController.navigate(Routes.lesson(it)) },
                        onOpenReview = { navController.navigate(Routes.REVIEW) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onOpenReading = { navController.navigate(Routes.reader(it)) }
                    )
                }

                composable(
                    route = Routes.READER,
                    arguments = listOf(navArgument("readingId") { type = NavType.StringType }),
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { it } + fadeOut() }
                ) { entry ->
                    ReaderScreen(
                        readingId = entry.arguments?.getString("readingId").orEmpty(),
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.REVIEW) {
                    ReviewScreen(
                        onStartReview = { navController.navigate(Routes.REVIEW_SESSION) },
                        onOpenVocabulary = { navController.navigate(Routes.VOCABULARY) }
                    )
                }

                composable(Routes.SPEAKING) {
                    SpeakingScreen(
                        onStartSession = { navController.navigate(Routes.SPEAKING_SESSION) }
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                        onOpenVocabulary = { navController.navigate(Routes.VOCABULARY) }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.ACHIEVEMENTS) {
                    AchievementsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.VOCABULARY) {
                    VocabularyScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Routes.LESSON,
                    arguments = listOf(navArgument("lessonId") { type = NavType.StringType }),
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { it } + fadeOut() }
                ) { entry ->
                    val lessonId = entry.arguments?.getString("lessonId").orEmpty()
                    LessonScreen(
                        lessonId = lessonId,
                        mode = SessionMode.LESSON,
                        onExit = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.REVIEW_SESSION,
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { it } + fadeOut() }
                ) {
                    LessonScreen(
                        lessonId = "",
                        mode = SessionMode.REVIEW,
                        onExit = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.SPEAKING_SESSION,
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { it } + fadeOut() }
                ) {
                    LessonScreen(
                        lessonId = "",
                        mode = SessionMode.SPEAKING,
                        onExit = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
