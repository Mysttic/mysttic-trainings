package pl.mysttic.trainings.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import pl.mysttic.trainings.data.share.IncomingPlanFile
import pl.mysttic.trainings.ui.adaptive.AppLayout
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.plans.PlansScreen
import pl.mysttic.trainings.ui.planeditor.PlanEditorScreen
import pl.mysttic.trainings.ui.share.PlanImportScreen
import pl.mysttic.trainings.ui.share.PlanShareScreen
import pl.mysttic.trainings.ui.stats.PlanStatsScreen
import pl.mysttic.trainings.ui.editor.WorkoutEditorScreen
import pl.mysttic.trainings.ui.history.HistoryScreen
import pl.mysttic.trainings.ui.history.SessionDetailScreen
import pl.mysttic.trainings.ui.library.ExerciseDetailScreen
import pl.mysttic.trainings.ui.library.LibraryScreen
import pl.mysttic.trainings.ui.plan.PlanScreen
import pl.mysttic.trainings.ui.plan.PlanWizardScreen
import pl.mysttic.trainings.ui.player.PlayerScreen
import pl.mysttic.trainings.ui.presets.PresetsScreen
import pl.mysttic.trainings.ui.settings.SettingsScreen
import pl.mysttic.trainings.ui.theme.LocalAppBackground
import pl.mysttic.trainings.ui.theme.appBackground
import pl.mysttic.trainings.ui.today.TodayScreen
import pl.mysttic.trainings.ui.workout.WorkoutDetailScreen

private class TopLevelDestination(
    val label: String,
    val matches: (NavDestination) -> Boolean,
    val navigate: (NavController) -> Unit,
)

private fun NavController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val topLevel = listOf(
    TopLevelDestination("Dziś", { it.hasRoute<TodayRoute>() }, { it.navigateTopLevel(TodayRoute) }),
    TopLevelDestination("Ćwiczenia", { it.hasRoute<LibraryRoute>() }, { it.navigateTopLevel(LibraryRoute) }),
    TopLevelDestination("Historia", { it.hasRoute<HistoryRoute>() }, { it.navigateTopLevel(HistoryRoute) }),
    TopLevelDestination("Ustawienia", { it.hasRoute<SettingsRoute>() }, { it.navigateTopLevel(SettingsRoute) }),
)

/**
 * Dolny pasek bez ikon (D-32): sama etykieta, a nad aktywną wskaźnik 34 x 4 dp w kolorze ognia.
 * Player i kreator zakrywają nawigację, więc pasek pokazujemy tylko na czterech ekranach głównych.
 * Bez własnego wypełnienia: leży na tle ekranów (D-50), od treści oddziela go linia włosowa.
 */
@Composable
private fun BottomNav(currentDestination: NavDestination?, navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Hairline()
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
            topLevel.forEach { dest ->
                val selected = currentDestination != null && dest.matches(currentDestination)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { dest.navigate(navController) }
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Wcięcia, które szyna bierze na siebie: paski systemu i otwór aparatu (w poziomie bywa po stronie szyny).
 * Te same, z których Scaffold liczy odstęp treści, więc treść obok szyny zjada tylko ich stronę początkową.
 */
private val railInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/**
 * Szyna nawigacji na tablecie i w poziomie (D-42): ten sam język co dolny pasek — etykiety bez ikon (D-32),
 * wskaźnik 34 x 4 dp nad aktywną — tylko w pionie, z linią włosową oddzielającą ją od treści.
 * Wcięcie od strony szyny (otwór aparatu, pasek nawigacji) poszerza szynę, a etykiety zostają poza nim.
 * Jak dolny pasek leży na tle ekranów (D-50), bez własnego wypełnienia.
 */
@Composable
private fun NavRail(currentDestination: NavDestination?, navController: NavController) {
    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(railInsets.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .width(112.dp)
                .padding(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            topLevel.forEach { dest ->
                val selected = currentDestination != null && dest.matches(currentDestination)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dest.navigate(navController) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/**
 * `openPlayer`: id treningu z intentu powiadomienia (MainActivity), otwiera player nad bieżącym ekranem.
 * `openPlanFile`: plik planu z intencji albo błąd jego odbioru; oba otwierają podgląd importu.
 */
@Composable
fun TrainingsRoot(openPlayer: Flow<String> = emptyFlow(), openPlanFile: Flow<IncomingPlanFile> = emptyFlow()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = AppLayout(maxWidth.value.toInt(), maxHeight.value.toInt())
        CompositionLocalProvider(LocalAppLayout provides layout) {
            TrainingsNav(layout, openPlayer, openPlanFile)
        }
    }
}

@Composable
private fun TrainingsNav(layout: AppLayout, openPlayer: Flow<String>, openPlanFile: Flow<IncomingPlanFile>) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showNav = currentDestination != null && topLevel.any { it.matches(currentDestination) }
    val rail = showNav && layout.navRail

    LaunchedEffect(openPlayer) {
        openPlayer.collect { workoutId ->
            navController.navigate(PlayerRoute(workoutId)) { launchSingleTop = true }
        }
    }
    LaunchedEffect(openPlanFile) {
        openPlanFile.collect { incoming ->
            val route = when (incoming) {
                is IncomingPlanFile.Staged -> PlanImportRoute(stagedKey = incoming.key)
                is IncomingPlanFile.Failed -> PlanImportRoute(error = incoming.message)
            }
            // Każdy plik dostaje świeży podgląd: otwarty podgląd poprzedniego schodzi ze stosu. `launchSingleTop`
            // zostawiłby stary wpis z jego ViewModelem, który dalej pokazywałby poprzedni plik.
            navController.navigate(route) { popUpTo<PlanImportRoute> { inclusive = true } }
        }
    }

    // Tło ekranów (D-50) maluje tylko ten wiersz, pod szyną, paskami systemu i treścią; wszystko nad nim
    // jest przezroczyste, więc gradient nie przesuwa się przy przewijaniu ani przy przejściu między ekranami.
    val background = LocalAppBackground.current
    Row(modifier = Modifier.fillMaxSize().appBackground(background, MaterialTheme.colorScheme.surface)) {
    if (rail) NavRail(currentDestination, navController)
    // Wcięcie od strony szyny już wzięła szyna: treść obok niej nie dolicza go drugi raz (Scaffold i paski ekranów).
    val railSide = if (rail) Modifier.consumeWindowInsets(railInsets.only(WindowInsetsSides.Start)) else Modifier
    Scaffold(
        modifier = Modifier.weight(1f).then(railSide),
        bottomBar = { if (showNav && !rail) BottomNav(currentDestination, navController) },
        // Przezroczysty kontener nie ma pary w schemacie, więc kolor treści trzeba podać wprost:
        // `contentColorFor(Color.Transparent)` oddałby domyślną czerń.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            // consumeWindowInsets: ekrany z własnym Scaffold/TopAppBar nie dokładają drugi raz insetu status bara.
            // imePadding (L-7, O-6): przy klawiaturze treść się kurczy zamiast przesuwać całe okno z szyną pod pasek stanu
            // (adjustResize w manifeście); przewijany ekran sam przesuwa aktywne pole nad klawiaturę.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).imePadding(),
        ) {
            composable<TodayRoute> {
                TodayScreen(
                    onOpenWorkout = { id -> navController.navigate(WorkoutDetailRoute(id)) },
                    onStartWorkout = { id -> navController.navigate(PlayerRoute(id)) },
                    onOpenPlan = { navController.navigate(PlanRoute()) },
                    onNewPlan = { step -> navController.navigate(PlanWizardRoute(step)) },
                    onPresets = { navController.navigate(PresetsRoute) },
                    onPlans = { navController.navigate(PlansRoute) },
                )
            }
            composable<PlanRoute> {
                PlanScreen(
                    onBack = { navController.popBackStack() },
                    onStartWorkout = { id -> navController.navigate(PlayerRoute(id)) },
                    onOpenWorkout = { id -> navController.navigate(WorkoutDetailRoute(id)) },
                    onNewCycle = { navController.navigate(PlanWizardRoute()) },
                    onPresets = { navController.navigate(PresetsRoute) },
                    onPlans = { navController.navigate(PlansRoute) { launchSingleTop = true } },
                    onOpenPlan = { id -> navController.navigate(PlanRoute(id)) },
                    onEditPlan = { id -> navController.navigate(PlanEditorRoute(id)) },
                    onExport = { id -> navController.navigate(PlanShareRoute(id)) },
                    onStats = { id -> navController.navigate(PlanStatsRoute(id)) },
                )
            }
            composable<PlansRoute> {
                PlansScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlan = { id -> navController.navigate(PlanRoute(id)) },
                    onWizard = { navController.navigate(PlanWizardRoute()) },
                    onPresets = { navController.navigate(PresetsRoute) },
                    onNewPlan = { navController.navigate(PlanEditorRoute()) },
                    onImport = { navController.navigate(PlanImportRoute()) },
                )
            }
            composable<PresetsRoute> {
                PresetsScreen(
                    onBack = { navController.popBackStack() },
                    onWizard = { navController.navigate(PlanWizardRoute()) },
                    onStarted = {
                        navController.navigate(PlanRoute()) { popUpTo<PresetsRoute> { inclusive = true } }
                    },
                )
            }
            composable<PlanWizardRoute> {
                PlanWizardScreen(
                    onBack = { navController.popBackStack() },
                    onGenerated = {
                        navController.navigate(PlanRoute()) { popUpTo<PlanWizardRoute> { inclusive = true } }
                    },
                )
            }
            composable<LibraryRoute> {
                LibraryScreen(onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) })
            }
            composable<HistoryRoute> {
                HistoryScreen(onOpenSession = { id -> navController.navigate(SessionDetailRoute(id)) })
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
            composable<ExerciseDetailRoute> {
                ExerciseDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                )
            }
            composable<WorkoutDetailRoute> {
                WorkoutDetailScreen(
                    onBack = { navController.popBackStack() },
                    onStart = { id -> navController.navigate(PlayerRoute(id)) },
                    onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                    onEdit = { id -> navController.navigate(WorkoutEditorRoute(id)) },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable<PlanShareRoute> {
                PlanShareScreen(onBack = { navController.popBackStack() })
            }
            composable<PlanImportRoute> {
                PlanImportScreen(
                    onBack = { navController.popBackStack() },
                    onImported = { id ->
                        // Bez launchSingleTop: pod importem z intencji może leżeć podgląd innego planu, a singleTop
                        // wskrzesiłby go razem ze starym planem zamiast pokazać zaimportowany.
                        navController.navigate(PlanRoute(id)) { popUpTo<PlanImportRoute> { inclusive = true } }
                    },
                )
            }
            composable<PlanStatsRoute> {
                PlanStatsScreen(onBack = { navController.popBackStack() })
            }
            composable<PlanEditorRoute> {
                PlanEditorScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(PlanRoute(id)) {
                            popUpTo<PlanEditorRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onEditWorkout = { workoutId, programId, _, literal ->
                        navController.navigate(WorkoutEditorRoute(workoutId, programId, literal))
                    },
                )
            }
            composable<WorkoutEditorRoute> {
                WorkoutEditorScreen(onBack = { navController.popBackStack() })
            }
            composable<PlayerRoute> {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onExit = {
                        navController.navigate(TodayRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<SessionDetailRoute> {
                SessionDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
    }
}
