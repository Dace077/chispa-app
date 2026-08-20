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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chispa.ingles.ui.grammar.GrammarScreen
import com.chispa.ingles.ui.grammar.GrammarTopicScreen
import com.chispa.ingles.ui.home.HomeScreen
import com.chispa.ingles.ui.kids.KidsScreen
import com.chispa.ingles.ui.lesson.LessonScreen
import com.chispa.ingles.ui.lesson.SessionMode
import com.chispa.ingles.ui.onboarding.OnboardingScreen
import com.chispa.ingles.ui.onboarding.PlacementScreen
import com.chispa.ingles.ui.profile.AchievementsScreen
import com.chispa.ingles.ui.profile.AvatarPickerScreen
import com.chispa.ingles.ui.profile.CertificatesScreen
import com.chispa.ingles.ui.profile.ProfileScreen
import com.chispa.ingles.ui.profile.StatsScreen
import com.chispa.ingles.ui.profile.StudentDataScreen
import com.chispa.ingles.ui.profile.VocabularyScreen
import com.chispa.ingles.ui.reader.LibraryScreen
import com.chispa.ingles.ui.reader.ReaderScreen
import com.chispa.ingles.ui.review.ReviewScreen
import com.chispa.ingles.ui.settings.SettingsScreen
import com.chispa.ingles.ui.toefl.ExamScreen
import com.chispa.ingles.ui.toefl.ToeflModuleScreen
import com.chispa.ingles.ui.toefl.ToeflScreen
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

    const val GRAMMAR = "grammar"
    const val GRAMMAR_TOPIC = "grammar/{topicId}"
    fun grammarTopic(topicId: String) = "grammar/$topicId"

    const val SETTINGS = "settings"
    const val ACHIEVEMENTS = "achievements"
    const val VOCABULARY = "vocabulary"
    const val STUDENT_DATA = "student_data"
    const val CERTIFICATES = "certificates"
    const val AVATAR = "avatar"
    const val STATS = "stats"

    const val KIDS = "kids"

    const val TOEFL = "toefl"
    const val TOEFL_MODULE = "toefl/{moduleId}"
    fun toeflModule(moduleId: String) = "toefl/$moduleId"
    const val TOEFL_EXAM = "toefl_exam/{examId}"
    fun toeflExam(examId: String) = "toefl_exam/$examId"

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

/** Quién está usando la app en este momento. */
private enum class ModoChispa { SIN_ELEGIR, ADULTO, NINO }

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
        AppStage.ONBOARDING -> OnboardingScreen(onFinished = { appViewModel.refresh() })
        // El permiso de notificaciones se pide aquí y no al cerrar el
        // onboarding: allí el diálogo del sistema caía justo encima de la
        // primera pregunta del test de nivel, tapándola. Al salir del test el
        // usuario ya está en la pantalla principal y ya ha visto para qué
        // sirve la app, que es cuando la pregunta tiene sentido.
        AppStage.PLACEMENT -> PlacementScreen(
            onFinished = {
                onRequestNotificationPermission()
                appViewModel.refresh()
            }
        )
        AppStage.READY -> {
            // Qué modo se está usando ahora. Vive en memoria y no en disco a
            // propósito: al abrir la app siempre se vuelve a preguntar, porque
            // el teléfono es del adulto pero quien lo agarra a veces es el niño.
            var modo by rememberSaveable { mutableStateOf(ModoChispa.SIN_ELEGIR) }

            when (modo) {
                ModoChispa.SIN_ELEGIR -> ModePickerScreen(
                    nombre = state.nombre,
                    onNormal = { modo = ModoChispa.ADULTO },
                    onKids = { modo = ModoChispa.NINO }
                )

                ModoChispa.ADULTO -> MainScaffold(navController = navController)

                // La X de Chispa Kids devuelve a la puerta, no al curso de
                // adultos: si el niño la toca sin querer, no acaba en el examen
                // TOEFL de su papá.
                ModoChispa.NINO -> KidsScreen(onExit = { modo = ModoChispa.SIN_ELEGIR })
            }
        }
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
                // La barra es una altura fija con cinco huecos: no puede crecer.
                // Con la fuente del sistema al 150% «Aprender» se partía en dos
                // líneas y se salía por abajo. Aquí, y SOLO aquí, se limita el
                // escalado; el contenido de las pantallas sigue creciendo entero,
                // que es lo que de verdad necesita quien agranda la letra.
                val densidad = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = densidad.density,
                        fontScale = densidad.fontScale.coerceAtMost(1.15f)
                    )
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
                            label = {
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
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
                        onOpenReading = { navController.navigate(Routes.reader(it)) },
                        onOpenGrammar = { navController.navigate(Routes.GRAMMAR) }
                    )
                }

                composable(
                    route = Routes.GRAMMAR,
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { it } + fadeOut() }
                ) {
                    GrammarScreen(
                        onOpenTopic = { navController.navigate(Routes.grammarTopic(it)) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.GRAMMAR_TOPIC,
                    arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { it } + fadeOut() }
                ) { entry ->
                    val topicId = entry.arguments?.getString("topicId").orEmpty()
                    GrammarTopicScreen(
                        topicId = topicId,
                        // Saltar a un tema relacionado apila, para poder volver
                        // sobre tus pasos igual que en una enciclopedia.
                        onOpenTopic = { navController.navigate(Routes.grammarTopic(it)) },
                        onBack = { navController.popBackStack() }
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
                        onOpenVocabulary = { navController.navigate(Routes.VOCABULARY) },
                        onOpenStudentData = { navController.navigate(Routes.STUDENT_DATA) },
                        onOpenCertificates = { navController.navigate(Routes.CERTIFICATES) },
                        onOpenAvatar = { navController.navigate(Routes.AVATAR) },
                        onOpenStats = { navController.navigate(Routes.STATS) },
                        onOpenToefl = { navController.navigate(Routes.TOEFL) },
                        onOpenKids = { navController.navigate(Routes.KIDS) }
                    )
                }

                // Chispa Kids va a pantalla completa, sin la barra de abajo:
                // un niño de tres años no debe poder saltar por accidente al
                // curso de adultos ni a los ajustes.
                composable(Routes.KIDS) {
                    KidsScreen(onExit = { navController.popBackStack() })
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onRetakePlacement = { navController.navigate(Routes.PLACEMENT) }
                    )
                }

                composable(Routes.ACHIEVEMENTS) {
                    AchievementsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.VOCABULARY) {
                    VocabularyScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.STUDENT_DATA) {
                    StudentDataScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.CERTIFICATES) {
                    CertificatesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenStudentData = { navController.navigate(Routes.STUDENT_DATA) }
                    )
                }

                composable(Routes.AVATAR) {
                    AvatarPickerScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.TOEFL) {
                    ToeflScreen(
                        onBack = { navController.popBackStack() },
                        onOpenModule = { navController.navigate(Routes.toeflModule(it)) },
                        onStartExam = { navController.navigate(Routes.toeflExam(it)) }
                    )
                }

                composable(
                    route = Routes.TOEFL_EXAM,
                    arguments = listOf(navArgument("examId") { type = NavType.StringType })
                ) { entry ->
                    ExamScreen(
                        examId = entry.arguments?.getString("examId").orEmpty(),
                        onExit = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.TOEFL_MODULE,
                    arguments = listOf(navArgument("moduleId") { type = NavType.StringType })
                ) { entry ->
                    ToeflModuleScreen(
                        moduleId = entry.arguments?.getString("moduleId").orEmpty(),
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.STATS) {
                    StatsScreen(
                        onBack = { navController.popBackStack() },
                        // El repaso ya prioriza lo que más se resiste, así que
                        // "repasar mis fallos" es exactamente esa sesión.
                        onPractice = { navController.navigate(Routes.REVIEW_SESSION) }
                    )
                }

                // Repetición del test de nivel. El de la primera vez no pasa por
                // aquí: ese lo sirve AppStage.PLACEMENT, fuera del NavHost.
                composable(Routes.PLACEMENT) {
                    PlacementScreen(
                        isRetake = true,
                        onFinished = { navController.popBackStack() }
                    )
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
                        onExit = { navController.popBackStack() },
                        onOpenGrammar = { navController.navigate(Routes.grammarTopic(it)) },
                        onOpenCertificates = {
                            // Se sale de la lección y se entra a certificados:
                            // volver atrás desde ahí lleva al camino, no a un
                            // resultado de sesión ya consumido.
                            navController.popBackStack()
                            navController.navigate(Routes.CERTIFICATES)
                        }
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
