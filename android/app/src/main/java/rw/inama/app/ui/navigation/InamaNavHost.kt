package rw.inama.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import rw.inama.app.domain.model.AppSettings
import rw.inama.app.feature.ask.AnswerDetailScreen
import rw.inama.app.feature.ask.AskScreen
import rw.inama.app.feature.farm.FarmScreen
import rw.inama.app.feature.farm.FieldDetailScreen
import rw.inama.app.feature.farm.FieldEditorScreen
import rw.inama.app.feature.history.HistoryScreen
import rw.inama.app.feature.home.HomeScreen
import rw.inama.app.feature.learn.LearnScreen
import rw.inama.app.feature.learn.LessonScreen
import rw.inama.app.feature.onboarding.IntroScreen
import rw.inama.app.feature.onboarding.LanguageScreen
import rw.inama.app.feature.onboarding.OnboardingViewModel
import rw.inama.app.feature.onboarding.PhoneScreen
import rw.inama.app.feature.onboarding.PrivacyScreen
import rw.inama.app.feature.onboarding.ProfileSetupScreen
import rw.inama.app.feature.onboarding.SetupDoneRoute
import rw.inama.app.feature.onboarding.SplashScreen
import rw.inama.app.feature.onboarding.VerifyScreen
import rw.inama.app.feature.profile.HelpScreen
import rw.inama.app.feature.profile.MeScreen
import rw.inama.app.feature.profile.SettingsScreen
import rw.inama.app.feature.scan.AnalysingScreen
import rw.inama.app.feature.scan.DiagnosisScreen
import rw.inama.app.feature.scan.ExpertCaseScreen
import rw.inama.app.feature.scan.PhotoReviewScreen
import rw.inama.app.feature.scan.ScanScreen
import rw.inama.app.feature.scan.ScanViewModel
import rw.inama.app.feature.tasks.TasksScreen
import rw.inama.app.feature.weather.WeatherScreen
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.components.InamaBottomBar
import rw.inama.app.ui.components.Tab
import rw.inama.app.ui.theme.Inama

/** Every destination in the app. Arguments are ids only — screens load their data from repositories. */
object Routes {
    const val SPLASH = "splash"
    const val LANGUAGE = "language"
    const val INTRO = "intro"
    const val PRIVACY = "privacy"
    const val PHONE = "phone"
    const val VERIFY = "verify"
    const val PROFILE = "profile_setup"
    const val PROFILE_EDIT = "profile_edit"
    const val FIRST_FIELD = "first_field"
    const val SETUP_DONE = "setup_done"

    const val HOME = "home"
    const val FARM = "farm"
    const val ASK = "ask"
    const val TASKS = "tasks"
    const val ME = "me"

    const val SCAN = "scan?fieldId={fieldId}"
    fun scan(fieldId: String? = null) = if (fieldId == null) "scan" else "scan?fieldId=${Uri.encode(fieldId)}"
    const val PHOTO_REVIEW = "photo_review"
    const val ANALYSING = "analysing"
    const val DIAGNOSIS = "diagnosis/{id}"
    fun diagnosis(id: String) = "diagnosis/${Uri.encode(id)}"
    const val EXPERT = "expert/{caseId}"
    fun expert(caseId: String) = "expert/${Uri.encode(caseId)}"

    const val ANSWER = "answer/{id}"
    fun answer(id: String) = "answer/${Uri.encode(id)}"
    const val FIELD = "field/{id}"
    fun field(id: String) = "field/${Uri.encode(id)}"
    const val FIELD_EDIT = "field_edit?id={id}"
    fun fieldEdit(id: String? = null) = if (id == null) "field_edit" else "field_edit?id=${Uri.encode(id)}"
    const val WEATHER = "weather"
    const val LEARN = "learn"
    const val LESSON = "lesson/{id}"
    fun lesson(id: String) = "lesson/${Uri.encode(id)}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val HELP = "help"

    val tabs = mapOf(HOME to Tab.HOME, FARM to Tab.FARM, TASKS to Tab.TASKS, ME to Tab.ME)
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.startFresh(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}

@Composable
fun InamaNavHost() {
    val container = LocalAppContainer.current
    val nav = rememberNavController()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
    // Activity-scoped: shared by the onboarding screens and by the photo-check flow.
    val onboarding: OnboardingViewModel = viewModel {
        OnboardingViewModel(container.settingsRepository, container.sessionRepository, container.profileRepository, container.catalogRepository)
    }
    val scan: ScanViewModel = viewModel {
        ScanViewModel(
            container.catalogRepository, container.fieldRepository, container.settingsRepository, container.imageTools, container.config,
            container.diagnoseCrop, container.onDeviceEngine::assessQuality,
        )
    }
    val backStack by nav.currentBackStackEntryAsState()
    val currentTab = Routes.tabs[backStack?.destination?.route]

    Box(Modifier.fillMaxSize().background(Inama.colors.ground)) {
        NavHost(navController = nav, startDestination = Routes.SPLASH, modifier = Modifier.fillMaxSize()) {
            // ---------------------------------------------------------------- onboarding
            composable(Routes.SPLASH) {
                SplashScreen(
                    returning = settings?.onboardingComplete,
                    onStart = { nav.navigate(Routes.LANGUAGE) },
                    onContinue = { nav.startFresh(Routes.HOME) },
                )
            }
            composable(Routes.LANGUAGE) { LanguageScreen(onboarding) { nav.navigate(Routes.INTRO) } }
            composable(Routes.INTRO) { IntroScreen { nav.navigate(Routes.PRIVACY) } }
            composable(Routes.PRIVACY) { PrivacyScreen(onboarding, onBack = { nav.popBackStack() }, onAgree = { nav.navigate(Routes.PHONE) }) }
            composable(Routes.PHONE) { PhoneScreen(onboarding, onBack = { nav.popBackStack() }, onCodeSent = { nav.navigate(Routes.VERIFY) }) }
            composable(Routes.VERIFY) { VerifyScreen(onboarding, onBack = { nav.popBackStack() }, onVerified = { nav.navigate(Routes.PROFILE) }) }
            composable(Routes.PROFILE) { ProfileSetupScreen(onboarding, onBack = { nav.popBackStack() }, onSaved = { nav.navigate(Routes.FIRST_FIELD) }) }
            composable(Routes.FIRST_FIELD) {
                FieldEditorScreen(fieldId = null, firstRun = true, onBack = { nav.popBackStack() }, onSaved = { nav.navigate(Routes.SETUP_DONE) })
            }
            composable(Routes.SETUP_DONE) {
                SetupDoneRoute(onGoHome = { onboarding.finishOnboarding { nav.startFresh(Routes.HOME) } })
            }

            // ---------------------------------------------------------------- tabs
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenDiagnosis = { nav.navigate(Routes.diagnosis(it)) },
                    onScan = { fieldId -> scan.start(fieldId); nav.navigate(Routes.scan(fieldId)) },
                    onAsk = { nav.navigate(Routes.ASK) },
                    onWeather = { nav.navigate(Routes.WEATHER) },
                    onField = { nav.navigate(Routes.field(it)) },
                    onAddField = { nav.navigate(Routes.fieldEdit()) },
                    onLearn = { nav.navigate(Routes.LEARN) },
                    onHistory = { nav.navigate(Routes.HISTORY) },
                    onTasks = { nav.goTab(Routes.TASKS) },
                    onProfile = { nav.goTab(Routes.ME) },
                    onFarm = { nav.goTab(Routes.FARM) },
                )
            }
            composable(Routes.FARM) {
                FarmScreen(onField = { nav.navigate(Routes.field(it)) }, onAddField = { nav.navigate(Routes.fieldEdit()) })
            }
            composable(Routes.TASKS) { TasksScreen() }
            composable(Routes.ME) {
                MeScreen(
                    onHistory = { nav.navigate(Routes.HISTORY) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onHelp = { nav.navigate(Routes.HELP) },
                    onEditProfile = { onboarding.loadForEdit(); nav.navigate(Routes.PROFILE_EDIT) },
                    onFarm = { nav.goTab(Routes.FARM) },
                )
            }

            // ---------------------------------------------------------------- ask
            composable(Routes.ASK) {
                AskScreen(onBack = { nav.popBackStack() }, onScan = { scan.start(null); nav.navigate(Routes.scan()) })
            }
            composable(Routes.ANSWER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                AnswerDetailScreen(id = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }

            // ---------------------------------------------------------------- photo check
            composable(
                Routes.SCAN,
                arguments = listOf(navArgument("fieldId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                ScanScreen(vm = scan, onClose = { nav.popBackStack() }, onPhotoReady = { nav.navigate(Routes.PHOTO_REVIEW) })
            }
            composable(Routes.PHOTO_REVIEW) {
                PhotoReviewScreen(vm = scan, onBack = { nav.popBackStack() }, onRetake = { nav.popBackStack() }, onAnalyse = { nav.navigate(Routes.ANALYSING) })
            }
            composable(Routes.ANALYSING) {
                AnalysingScreen(
                    vm = scan,
                    onDone = { id ->
                        nav.navigate(Routes.diagnosis(id)) { popUpTo(Routes.SCAN) { inclusive = true } }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.DIAGNOSIS, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                DiagnosisScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onExpertCase = { nav.navigate(Routes.expert(it)) },
                    onRetake = { fieldId -> scan.start(fieldId); nav.navigate(Routes.scan(fieldId)) { popUpTo(Routes.DIAGNOSIS) { inclusive = true } } },
                    onTasks = { nav.goTab(Routes.TASKS) },
                )
            }
            composable(Routes.EXPERT, arguments = listOf(navArgument("caseId") { type = NavType.StringType })) { entry ->
                ExpertCaseScreen(caseId = entry.arguments?.getString("caseId").orEmpty(), onBack = { nav.popBackStack() }, onHome = { nav.goTab(Routes.HOME) })
            }

            // ---------------------------------------------------------------- farm & info
            composable(Routes.FIELD, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                FieldDetailScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.fieldEdit(it)) },
                    onScan = { fieldId -> scan.start(fieldId); nav.navigate(Routes.scan(fieldId)) },
                    onDiagnosis = { nav.navigate(Routes.diagnosis(it)) },
                    onTasks = { nav.goTab(Routes.TASKS) },
                    onDeleted = { nav.popBackStack() },
                )
            }
            composable(Routes.FIELD_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                FieldEditorScreen(fieldId = entry.arguments?.getString("id"), firstRun = false, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
            }
            composable(Routes.WEATHER) { WeatherScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.LEARN) { LearnScreen(onBack = { nav.popBackStack() }, onLesson = { nav.navigate(Routes.lesson(it)) }) }
            composable(Routes.LESSON, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                LessonScreen(id = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onAsk = { nav.navigate(Routes.ASK) })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onBack = { nav.popBackStack() }, onDiagnosis = { nav.navigate(Routes.diagnosis(it)) }, onAnswer = { nav.navigate(Routes.answer(it)) }, onScan = {
                    scan.start(null); nav.navigate(Routes.scan())
                })
            }
            composable(Routes.PROFILE_EDIT) {
                ProfileSetupScreen(onboarding, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() }, editing = true)
            }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onReset = { nav.startFresh(Routes.SPLASH) }) }
            composable(Routes.HELP) { HelpScreen(onBack = { nav.popBackStack() }) }
        }

        if (currentTab != null) {
            InamaBottomBar(
                current = currentTab,
                onSelect = { tab ->
                    when (tab) {
                        Tab.HOME -> nav.goTab(Routes.HOME)
                        Tab.FARM -> nav.goTab(Routes.FARM)
                        Tab.ASK -> nav.navigate(Routes.ASK)
                        Tab.TASKS -> nav.goTab(Routes.TASKS)
                        Tab.ME -> nav.goTab(Routes.ME)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
