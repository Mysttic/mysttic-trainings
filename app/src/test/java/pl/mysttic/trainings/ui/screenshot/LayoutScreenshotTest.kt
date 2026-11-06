package pl.mysttic.trainings.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.Coil
import coil.ImageLoader
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.mysttic.trainings.data.settings.UserSettings
import pl.mysttic.trainings.domain.engine.Phase
import pl.mysttic.trainings.domain.engine.PlayerSnapshot
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.library.LibraryFacets
import pl.mysttic.trainings.domain.library.LibraryFilter
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.plan.PlanListRow
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.PlanStatus
import pl.mysttic.trainings.domain.plan.TargetSuggestion
import pl.mysttic.trainings.ui.adaptive.AppLayout
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.adaptive.TwoPane
import pl.mysttic.trainings.ui.common.VerticalHairline
import pl.mysttic.trainings.ui.library.ExerciseDetailContent
import pl.mysttic.trainings.ui.library.ExerciseDetailUiState
import pl.mysttic.trainings.ui.library.LibraryActions
import pl.mysttic.trainings.ui.library.LibraryContent
import pl.mysttic.trainings.ui.library.LibraryUiState
import pl.mysttic.trainings.ui.navigation.BottomNav
import pl.mysttic.trainings.ui.navigation.NavRail
import pl.mysttic.trainings.ui.player.PlayerActions
import pl.mysttic.trainings.ui.plans.PlansActions
import pl.mysttic.trainings.ui.plans.PlansContent
import pl.mysttic.trainings.ui.plans.PlansUiState
import pl.mysttic.trainings.ui.player.PlayerContent
import pl.mysttic.trainings.ui.player.PlayerUiState
import pl.mysttic.trainings.domain.update.ReleaseInfo
import pl.mysttic.trainings.ui.settings.BackupStatus
import pl.mysttic.trainings.ui.settings.SettingsActions
import pl.mysttic.trainings.ui.settings.SettingsContent
import pl.mysttic.trainings.ui.settings.SettingsSection
import pl.mysttic.trainings.ui.settings.SettingsState
import pl.mysttic.trainings.ui.settings.UpdateUiState
import pl.mysttic.trainings.ui.theme.AppTheme
import pl.mysttic.trainings.ui.theme.LocalAppBackground
import pl.mysttic.trainings.ui.theme.TrainingsTheme
import pl.mysttic.trainings.ui.theme.appBackground
import pl.mysttic.trainings.ui.today.ActiveCard
import pl.mysttic.trainings.ui.today.PlanCard
import pl.mysttic.trainings.ui.today.PlanCardState
import pl.mysttic.trainings.ui.today.StreakCard
import pl.mysttic.trainings.ui.today.TodayActions
import pl.mysttic.trainings.ui.today.TodayContent
import pl.mysttic.trainings.ui.today.TodayUiState
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.plan.DraftDay
import pl.mysttic.trainings.domain.plan.Goal
import pl.mysttic.trainings.domain.plan.PlanDay
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile
import pl.mysttic.trainings.domain.stats.DayHeat
import pl.mysttic.trainings.domain.stats.ExerciseDelta
import pl.mysttic.trainings.domain.stats.HistoryPeriod
import pl.mysttic.trainings.domain.stats.PeriodStats
import pl.mysttic.trainings.domain.stats.WeekVolume
import pl.mysttic.trainings.ui.common.MenuAction
import pl.mysttic.trainings.ui.editor.EditorUiState
import pl.mysttic.trainings.ui.editor.WorkoutEditorActions
import pl.mysttic.trainings.ui.editor.WorkoutEditorContent
import pl.mysttic.trainings.ui.history.HistoryActions
import pl.mysttic.trainings.ui.history.HistoryContent
import pl.mysttic.trainings.ui.history.HistoryUiState
import pl.mysttic.trainings.ui.history.SessionDetailActions
import pl.mysttic.trainings.ui.history.SessionDetailContent
import pl.mysttic.trainings.ui.history.SessionDetailUiState
import pl.mysttic.trainings.ui.plan.PlanActions
import pl.mysttic.trainings.ui.plan.PlanContent
import pl.mysttic.trainings.ui.plan.PlanDayRow
import pl.mysttic.trainings.ui.plan.PlanUiState
import pl.mysttic.trainings.ui.plan.PlanWizardContent
import pl.mysttic.trainings.ui.plan.WizardActions
import pl.mysttic.trainings.ui.plan.WizardUiState
import pl.mysttic.trainings.ui.planeditor.PlanDraft
import pl.mysttic.trainings.ui.planeditor.PlanEditorActions
import pl.mysttic.trainings.ui.planeditor.PlanEditorContent
import pl.mysttic.trainings.ui.planeditor.PlanEditorUiState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Zrzuty układu (D-52, zapowiedziane w D-42): bezstanowe treści głównych ekranów z fiksturami, na telefonie w pionie
 * i tablecie w poziomie, z paskiem nawigacji albo szyną jak w aplikacji. Tak jak `ThemeScreenshotTest` bez flag
 * tylko rysują (łapią wywrotkę układu), `-Proborazzi.record=true` zapisuje wzorce do `src/test/screenshots/uklad`.
 * Nie zastępują emulatora: brak paska stanu, czcionki systemowej i prawdziwych danych.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LayoutScreenshotTest {

    @Before
    fun synchronousImages() {
        // Ilustracje z assetów mają być na zrzucie: ładowanie na wątku głównym i bez bitmap sprzętowych (Robolectric).
        Coil.setImageLoader(
            ImageLoader.Builder(ApplicationProvider.getApplicationContext())
                .dispatcher(Dispatchers.Main.immediate)
                .allowHardware(false)
                .crossfade(false)
                .build(),
        )
    }

    @Test
    fun `dzis pierwsze uruchomienie`() = phone("dzis-start", nav = 0) {
        TodayContent(
            date = DATE,
            today = TodayUiState(loading = false),
            active = null,
            plan = PlanCardState(loading = false, card = null),
            streak = StreakCard(0, 0),
            welcomeImages = listOf("images/pushups.jpg", "images/high-knees.jpg", "images/plank.jpg"),
            actions = TodayActions(),
        )
    }

    @Test
    fun `dzis z planem`() = phone("dzis-plan", nav = 0) { TodayWithPlan() }

    @Test
    fun `dzis z planem na tablecie`() = tablet("dzis-plan-tablet", nav = 0) { TodayWithPlan() }

    @Test
    fun `dzis trening w toku`() = phone("dzis-w-toku", nav = 0) {
        TodayContent(
            date = DATE,
            today = TodayUiState(loading = false, lastSession = lastSession, doneThisWeek = 2),
            active = ActiveCard("w-gora-a", "Góra A", workNumber = 14, workTotal = 56, paused = true),
            plan = PlanCardState(loading = false, card = planCard),
            streak = StreakCard(4, 9),
            welcomeImages = emptyList(),
            actions = TodayActions(),
        )
    }

    @Test
    fun `plany`() = phone("plany", nav = 1) {
        PlansContent(
            state = PlansUiState(
                loading = false,
                active = listOf(PlanListRow("p1", "Redukcja · 30 dni", PlanSource.WIZARD, "", PlanStatus.ACTIVE, 2, 12, null)),
                waiting = listOf(
                    PlanListRow("p2", "Tabata · 4 tygodnie", PlanSource.PRESET, "", PlanStatus.PAUSED, 5, 12, null),
                    PlanListRow("p3", "Siła dla Oli", PlanSource.IMPORTED, "Marta", PlanStatus.NOT_STARTED, 0, 24, null),
                ),
                finished = listOf(PlanListRow("p0", "Start · 14 dni", PlanSource.WIZARD, "", PlanStatus.COMPLETE, 8, 8, null)),
            ),
            actions = PlansActions(),
        )
    }

    @Test
    fun `plany bez zadnego planu`() = phone("plany-puste", nav = 1) {
        PlansContent(state = PlansUiState(loading = false), actions = PlansActions())
    }

    @Test
    fun `biblioteka`() = phone("cwiczenia", nav = 2) {
        LibraryContent(state = library(), actions = LibraryActions(), onOpenExercise = {}, columns = 2)
    }

    @Test
    fun `biblioteka z panelem filtrow`() = phone("cwiczenia-filtry", nav = 2) {
        val filter = LibraryFilter(onlyOwned = true, noEquipment = true, equipment = setOf(Equipment.DUMBBELLS))
        LibraryContent(
            state = library(filter = filter, expanded = true, owned = setOf(Equipment.DUMBBELLS, Equipment.PULL_UP_BAR)),
            actions = LibraryActions(),
            onOpenExercise = {},
            columns = 2,
        )
    }

    @Test
    fun `biblioteka na tablecie, lista i szczegol`() = tablet("cwiczenia-tablet", nav = 2) {
        TwoPane(
            listWidth = 460.dp,
            list = {
                LibraryContent(state = library(), actions = LibraryActions(), onOpenExercise = {}, columns = 2, selectedId = "pushups")
            },
            detail = {
                ExerciseDetailContent(
                    state = pushupsDetail,
                    onBack = null,
                    onOpenExercise = {},
                    onExcludedChange = {},
                    onResetTuning = {},
                    wide = true,
                )
            },
        )
    }

    @Test
    fun `szczegol cwiczenia`() = phone("cwiczenie", nav = null) {
        ExerciseDetailContent(state = pushupsDetail, onBack = {}, onOpenExercise = {}, onExcludedChange = {}, onResetTuning = {})
    }

    @Test
    fun `ustawienia`() = phone("ustawienia", nav = 4) { SettingsContent(state = settingsState, actions = SettingsActions()) }

    @Test
    fun `ustawienia trening`() = phone("ustawienia-trening", nav = 4) {
        SettingsContent(state = settingsState, actions = SettingsActions(), initialSection = SettingsSection.TRAINING)
    }

    /** „O mnie” (D-57): imię, płeć, wzrost i waga, a pod imieniem klucz podpisu planów (D-58). */
    @Test
    fun `ustawienia o mnie`() = phone("ustawienia-o-mnie", nav = 4) {
        SettingsContent(
            state = settingsState.copy(
                settings = settingsState.settings.copy(authorName = "Marta", gender = "kobieta", heightCm = 168, weightKg = 62.5),
            ),
            actions = SettingsActions(),
            initialSection = SettingsSection.ABOUT_ME,
        )
    }

    @Test
    fun `ustawienia dane i kopia`() = phone("ustawienia-dane", nav = 4) {
        SettingsContent(
            state = settingsState.copy(backupStatus = BackupStatus("Zapisano 42 treningi i 3 plany.")),
            actions = SettingsActions(),
            initialSection = SettingsSection.DATA,
        )
    }

    @Test
    fun `ustawienia aktualizacje z nowa wersja`() = phone("ustawienia-aktualizacje", nav = 4) {
        val release = ReleaseInfo(
            version = "1.2.0",
            notes = "Nowy wygląd: kategorie w Ustawieniach, prostszy player, lista i szczegół obok siebie na tablecie.",
            pageUrl = "https://github.com/Mysttic/Mysttic",
            assetUrl = "https://example.invalid/mysttic-trainings-1.2.0-android.apk",
            assetSize = 22_000_000,
        )
        SettingsContent(
            state = settingsState.copy(update = UpdateUiState(release = release)),
            actions = SettingsActions(),
            initialSection = SettingsSection.UPDATES,
        )
    }

    @Test
    fun `ustawienia z nowa wersja na liscie`() = phone("ustawienia-nowa-wersja", nav = 4) {
        val release = ReleaseInfo("1.2.0", "", "https://github.com/Mysttic/Mysttic", null, 0)
        SettingsContent(state = settingsState.copy(update = UpdateUiState(release = release)), actions = SettingsActions())
    }

    @Test
    fun `ustawienia na tablecie`() = tablet("ustawienia-tablet", nav = 4) {
        SettingsContent(state = settingsState, actions = SettingsActions(), initialSection = SettingsSection.TRAINING)
    }

    @Test
    fun `player seria na powtorzenia`() = phone("player", nav = null) {
        PlayerContent(state = playerState(workStep), actions = PlayerActions(), onAbort = {}, onMenu = {})
    }

    @Test
    fun `player przerwa`() = phone("player-przerwa", nav = null) {
        val rest = Step.Rest(seconds = 45, kind = pl.mysttic.trainings.domain.model.RestKind.BETWEEN_ITEMS)
        PlayerContent(
            state = playerState(rest, remaining = 32, next = workStep.copy(exerciseId = "bent-over-dumbbell-row", exerciseName = "Wiosłowanie hantlami w opadzie")),
            actions = PlayerActions(),
            onAbort = {},
            onMenu = {},
        )
    }

    @Test
    fun `player na tablecie`() = tablet("player-tablet", nav = null) {
        PlayerContent(state = playerState(workStep), actions = PlayerActions(), onAbort = {}, onMenu = {})
    }

    // --- D-54: Historia, podgląd planu, kreator i edytory w języku D-52 ------------------------------------------------

    @Test
    fun `historia`() = phone("historia", nav = 3) {
        HistoryContent(state = historyState, actions = HistoryActions(), onOpenSession = {})
    }

    @Test
    fun `historia bez treningow`() = phone("historia-pusta", nav = 3) {
        HistoryContent(state = HistoryUiState(loading = false), actions = HistoryActions(), onOpenSession = {})
    }

    @Test
    fun `historia na tablecie, lista i sesja`() = tablet("historia-tablet", nav = 3) {
        TwoPane(
            listWidth = 460.dp,
            list = { HistoryContent(state = historyState, actions = HistoryActions(), onOpenSession = {}, selectedId = "s2") },
            detail = {
                SessionDetailContent(state = sessionDetail, rpe = 7, note = "", actions = SessionDetailActions(), onBack = null)
            },
        )
    }

    @Test
    fun `szczegoly sesji`() = phone("sesja", nav = null) {
        SessionDetailContent(
            state = sessionDetail,
            rpe = 7,
            note = "Ostatnia runda ciężko, ale bez przerw.",
            actions = SessionDetailActions(),
            onBack = {},
        )
    }

    @Test
    fun `podglad planu`() = phone("plan", nav = null) {
        PlanContent(state = planState, actions = PlanActions(menu = listOf(MenuAction("Usuń plan", {}, destructive = true))))
    }

    @Test
    fun `podglad planu na tablecie`() = tablet("plan-tablet", nav = null) {
        PlanContent(state = planState, actions = PlanActions())
    }

    @Test
    fun `kreator, pierwszy krok`() = phone("kreator-1", nav = null) {
        PlanWizardContent(state = wizardState, step = 1, onStep = {}, actions = WizardActions(), onClose = {})
    }

    @Test
    fun `kreator, sprzet i streszczenie`() = phone("kreator-4", nav = null) {
        PlanWizardContent(state = wizardState, step = 4, onStep = {}, actions = WizardActions(), onClose = {})
    }

    @Test
    fun `kreator na tablecie`() = tablet("kreator-tablet", nav = null) {
        PlanWizardContent(state = wizardState, step = 4, onStep = {}, actions = WizardActions(), onClose = {})
    }

    @Test
    fun `edytor planu`() = phone("edytor-planu", nav = null) {
        PlanEditorContent(state = planEditorState, actions = PlanEditorActions(), onBack = {})
    }

    @Test
    fun `edytor planu na tablecie`() = tablet("edytor-planu-tablet", nav = null) {
        PlanEditorContent(state = planEditorState, actions = PlanEditorActions(), onBack = {})
    }

    @Test
    fun `edytor treningu`() = phone("edytor-treningu", nav = null) {
        WorkoutEditorContent(state = workoutEditorState, estimatedMinutes = 38, actions = WorkoutEditorActions(), onBack = {})
    }

    // --- ramy -------------------------------------------------------------------------------------------------------

    private fun phone(name: String, nav: Int?, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-port-xhdpi")
        capture(name, AppLayout(411, 891)) {
            Column(modifier = Modifier.fillMaxSize().appBackground(LocalAppBackground.current)) {
                Box(modifier = Modifier.weight(1f).padding(top = 24.dp)) { content() }
                if (nav != null) BottomNav(selectedIndex = nav, onSelect = {})
            }
        }
    }

    private fun tablet(name: String, nav: Int?, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("w1280dp-h800dp-land-mdpi")
        capture(name, AppLayout(1280, 800)) {
            Row(modifier = Modifier.fillMaxSize().appBackground(LocalAppBackground.current)) {
                if (nav != null) {
                    NavRail(selectedIndex = nav, onSelect = {})
                } else {
                    VerticalHairline(modifier = Modifier.padding(0.dp))
                }
                Box(modifier = Modifier.weight(1f).padding(top = 16.dp)) { content() }
            }
        }
    }

    private fun capture(name: String, layout: AppLayout, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "src/test/screenshots/uklad/$name.png") {
            TrainingsTheme(theme = AppTheme.FIRE) {
                // Jak `Scaffold` w aplikacji: tło i kolor treści (onSurface) dla tekstów bez własnego koloru.
                Surface(color = MaterialTheme.colorScheme.surface) {
                    CompositionLocalProvider(LocalAppLayout provides layout) { content() }
                }
            }
        }
    }

    // --- fikstury ---------------------------------------------------------------------------------------------------

    @Composable
    private fun TodayWithPlan() {
        TodayContent(
            date = DATE,
            today = TodayUiState(loading = false, lastSession = lastSession, doneThisWeek = 2),
            active = null,
            plan = PlanCardState(loading = false, card = planCard),
            streak = StreakCard(4, 9),
            welcomeImages = emptyList(),
            actions = TodayActions(),
        )
    }

    private fun library(
        filter: LibraryFilter = LibraryFilter(),
        expanded: Boolean = false,
        owned: Set<Equipment> = emptySet(),
    ) = LibraryUiState(
        loading = false,
        filter = filter,
        panelExpanded = expanded,
        tiles = true,
        facets = LibraryFacets.of(exercises),
        ownedEquipment = owned,
        exercises = filter.apply(exercises, owned),
        totalCount = 283,
    )

    private fun playerState(step: Step, remaining: Int? = null, next: Step.Work? = null) = PlayerUiState(
        loading = false,
        workoutName = "Góra A",
        difficulty = Difficulty.INTERMEDIATE,
        snapshot = PlayerSnapshot(
            phase = Phase.RUNNING,
            step = step,
            stepIndex = 27,
            totalSteps = 112,
            workNumber = 14,
            workTotal = 56,
            remainingSec = remaining,
            elapsedInPhaseSec = 7,
            totalElapsedSec = 724,
            paused = false,
            nextWork = next,
        ),
        currentExercise = byId.getValue("pushups"),
        nextExercise = next?.let { byId[it.exerciseId] },
    )

    private companion object {
        const val DATE = "czwartek, 5 listopada"

        fun ex(
            id: String,
            name: String,
            muscle: MuscleGroup,
            level: Difficulty = Difficulty.BEGINNER,
            equipment: Set<Equipment> = emptySet(),
            category: ExerciseCategory = ExerciseCategory.STRENGTH,
            secondary: List<MuscleGroup> = emptyList(),
        ) = Exercise(
            id = id,
            nameEn = id,
            namePl = name,
            kind = ExerciseKind.REPS,
            level = level,
            category = category,
            equipment = equipment,
            primaryMuscles = listOf(muscle),
            secondaryMuscles = secondary,
            instructionsPl = listOf(
                "Dłonie nieco szerzej niż barki, ciało w linii prostej od głowy do pięt, brzuch napięty.",
                "Zginając łokcie, opuść klatkę kilka centymetrów nad podłogę.",
                "Wypchnij się do pełnego wyprostu, nie zapadając bioder.",
            ),
            images = listOf("images/$id.jpg"),
        )

        val exercises = listOf(
            ex("high-knees", "Bieg w miejscu z wysokimi kolanami", MuscleGroup.QUADS, category = ExerciseCategory.CARDIO),
            ex("bird-dog", "Bird dog", MuscleGroup.LOWER_BACK),
            ex("shadow-boxing", "Boks z cieniem", MuscleGroup.SHOULDERS, category = ExerciseCategory.CARDIO),
            ex("crunches", "Brzuszki (spięcia)", MuscleGroup.ABS),
            ex("sit-ups", "Brzuszki pełne (sit-up)", MuscleGroup.ABS),
            ex("hollow-rock", "Bujanie w łódce", MuscleGroup.ABS, level = Difficulty.ADVANCED),
            ex("burpees", "Burpee", MuscleGroup.FULL_BODY, level = Difficulty.INTERMEDIATE, category = ExerciseCategory.PLYOMETRICS),
            ex("plank", "Deska (plank)", MuscleGroup.ABS),
            ex("pushups", "Pompki", MuscleGroup.CHEST, secondary = listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
            ex("bent-over-dumbbell-row", "Wiosłowanie hantlami w opadzie", MuscleGroup.UPPER_BACK, equipment = setOf(Equipment.DUMBBELLS)),
            ex("dumbbell-shoulder-press", "Wyciskanie hantli nad głowę", MuscleGroup.SHOULDERS, equipment = setOf(Equipment.DUMBBELLS)),
            ex("bicep-curls", "Uginanie ramion z hantlami", MuscleGroup.BICEPS, equipment = setOf(Equipment.DUMBBELLS)),
        )
        val byId = exercises.associateBy { it.id }

        val pushupsDetail = ExerciseDetailUiState(
            loading = false,
            exercise = byId.getValue("pushups"),
            easier = byId.getValue("plank"),
            harder = byId.getValue("burpees"),
            ownedEquipment = setOf(Equipment.DUMBBELLS),
        )

        val lastSession = Session(
            id = "s1",
            workoutId = "w-dol-a",
            workoutName = "Dół A",
            difficulty = Difficulty.INTERMEDIATE,
            startedAt = 0L,
            endedAt = 45_000L,
            status = SessionStatus.DONE,
        )

        val planCard = PlanCard(
            programName = "Redukcja · 30 dni",
            nextWorkoutId = "w-gora-a",
            nextWorkoutName = "Góra A",
            nextWorkoutMinutes = 46,
            nextWorkoutExercises = 8,
            doneCount = 2,
            totalCount = 18,
            isComplete = false,
            doneToday = false,
            weekDone = 2,
            weekTotal = 4,
            previewImages = listOf(
                "images/pushups.jpg",
                "images/bent-over-dumbbell-row.jpg",
                "images/dumbbell-shoulder-press.jpg",
                "images/bicep-curls.jpg",
            ),
            previewCount = 8,
            suggestions = listOf(
                TargetSuggestion(
                    blockIndex = 0,
                    itemIndex = 0,
                    exerciseId = "pushups",
                    exerciseName = "Pompki",
                    targetKind = TargetKind.REPS,
                    from = 8,
                    to = 9,
                ),
            ),
        )

        val settingsState = SettingsState(
            settings = UserSettings(
                difficulty = Difficulty.INTERMEDIATE,
                ownedEquipment = setOf(Equipment.DUMBBELLS, Equipment.PULL_UP_BAR, Equipment.JUMP_ROPE),
                countdownSec = 5,
                vibrationEnabled = false,
                volumeKeyDone = false,
                seedVersion = 10,
                reminderEnabled = true,
            ),
            healthAvailable = true,
            versionName = "1.1.0",
            signingKey = "3F2A 9C11 7B04 E2D5 0A1C",
        )

        val workStep = Step.Work(
            blockIndex = 0,
            itemIndex = 0,
            round = 2,
            setIndex = 1,
            setsInItem = 1,
            exerciseId = "pushups",
            exerciseName = "Pompki",
            targetKind = TargetKind.REPS,
            targetValue = 8,
            roundsInBlock = 7,
        )

        // --- D-54 -------------------------------------------------------------------------------------------------

        /** Czas w strefie maszyny: zrzut pokazuje te same godziny na każdym komputerze. */
        fun at(month: Int, day: Int, hour: Int, minute: Int): Long =
            LocalDateTime.of(2026, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val historySessions = listOf(
            Session("s3", "w-gora-a", "Góra A", Difficulty.INTERMEDIATE, at(11, 5, 18, 4), at(11, 5, 18, 49), SessionStatus.DONE, rpe = 7),
            Session("s2", "w-dol-a", "Dół A", Difficulty.INTERMEDIATE, at(11, 3, 7, 30), at(11, 3, 8, 12), SessionStatus.DONE, rpe = 8),
            Session("s1", "w-gora-a", "Góra A", Difficulty.BEGINNER, at(11, 1, 19, 0), at(11, 1, 19, 14), SessionStatus.ABANDONED),
            Session("s0", "w-dol-a", "Dół A", Difficulty.INTERMEDIATE, at(10, 29, 17, 45), at(10, 29, 18, 30), SessionStatus.DONE),
        )

        val historyState = HistoryUiState(
            loading = false,
            sessions = historySessions,
            period = HistoryPeriod.MONTH,
            stats = PeriodStats(sessions = 9, durationSec = 6 * 3600 + 25 * 60, sets = 182, reps = 1460, workSec = 2340),
            totalCount = 14,
            streak = 2,
            bestStreak = 5,
            weeks = (0 until 8).map { WeekVolume(LocalDate.of(2026, 9, 14).plusWeeks(it.toLong()), sessions = it % 3 + 1, reps = 300 + it * 45, workSec = 600) },
            heatmap = (0 until 84).map { DayHeat(LocalDate.of(2026, 8, 14).plusDays(it.toLong()), if (it % 3 == 0) 1 else 0) },
        )

        private fun log(exerciseId: String, name: String, round: Int, actual: Int, target: Int, kind: TargetKind = TargetKind.REPS) = SetLog(
            sessionId = "s2",
            exerciseId = exerciseId,
            exerciseName = name,
            blockIndex = 0,
            itemIndex = 0,
            round = round,
            setIndex = 1,
            targetKind = kind,
            targetValue = target,
            actualValue = actual,
            completedAt = 0L,
        )

        val sessionDetail = SessionDetailUiState(
            loading = false,
            session = historySessions[1],
            logs = (1..3).flatMap { round ->
                listOf(
                    log("pushups", "Pompki", round, actual = if (round == 3) 9 else 10, target = 10),
                    log("plank", "Deska (plank)", round, actual = 40, target = 40, kind = TargetKind.SECONDS),
                )
            },
            deltas = listOf(
                ExerciseDelta("pushups", "Pompki", TargetKind.REPS, sets = 3, total = 29, previousTotal = 26),
                ExerciseDelta("plank", "Deska (plank)", TargetKind.SECONDS, sets = 3, total = 120, previousTotal = 135),
                ExerciseDelta("bird-dog", "Bird dog", TargetKind.REPS, sets = 3, total = 30),
            ),
        )

        private val planRows: List<PlanDayRow> = (1..30).map { day ->
            val inWeek = (day - 1) % 7 + 1
            val workout = when (inWeek) {
                1, 5 -> "w-gora-a" to "Góra A"
                3 -> "w-dol-a" to "Dół A"
                else -> null
            }
            PlanDayRow(
                dayIndex = day,
                workoutId = workout?.first,
                workoutName = workout?.second,
                minutes = if (workout == null) 0 else 46,
                isNext = day == 8,
                done = workout != null && day < 8,
                doneAt = if (workout != null && day < 8) at(11, day, 18, 0) else null,
            )
        }

        val planState = PlanUiState(
            loading = false,
            program = Program(
                id = "p1",
                name = "Redukcja · 30 dni",
                profile = TrainingProfile(goal = Goal.FAT_LOSS),
                startedAt = at(11, 1, 8, 0),
                cycleDays = 30,
                createdAt = at(11, 1, 8, 0),
                active = true,
                days = planRows.map { PlanDay(it.dayIndex, it.workoutId) },
            ),
            days = planRows,
            doneCount = 3,
            totalCount = planRows.count { !it.isRest },
            isActive = true,
            subtitle = "Cykl 30 dni · redukcja · 3 razy w tygodniu · 45 min",
        )

        val wizardState = WizardUiState(
            loading = false,
            profile = TrainingProfile(
                heightCm = 178,
                weightKg = 82.0,
                goal = Goal.CONDITION,
                daysPerWeek = 3,
                sessionMinutes = 30,
                cycleDays = 30,
                equipment = setOf(Equipment.DUMBBELLS, Equipment.PULL_UP_BAR),
            ),
        )

        private val blockA = Block(
            kind = BlockKind.SETS,
            items = listOf(
                BlockItem("pushups", TargetKind.REPS, 10),
                BlockItem("bent-over-dumbbell-row", TargetKind.REPS, 12, weightKg = 10.0),
            ),
            fixed = true,
        )
        private val blockB = Block(
            kind = BlockKind.CIRCUIT,
            rounds = 3,
            restBetweenRoundsSec = 75,
            label = "Brzuch",
            items = listOf(
                BlockItem("plank", TargetKind.SECONDS, 40, restAfterItemSec = 20),
                BlockItem("crunches", TargetKind.REPS, 15, restAfterItemSec = 20),
                BlockItem("bird-dog", TargetKind.REPS, 10, restAfterItemSec = 20, note = "Powoli, bez skrętu bioder"),
            ),
            fixed = true,
        )

        val planEditorState = PlanEditorUiState(
            loading = false,
            draft = PlanDraft(
                programId = "p9",
                isNew = false,
                name = "Siła dla Oli",
                description = "Trzy razy w tygodniu, A i B na zmianę. Między treningami co najmniej dzień przerwy.",
                literal = true,
                days = (1..10).map { DraftDay("d$it", if (it % 2 == 1) (if (it % 4 == 1) "p9-a" else "p9-b") else null) },
            ),
            workouts = listOf(
                Workout(id = "p9-a", name = "A: siła", blocks = listOf(blockA), createdAt = 0L, updatedAt = 0L),
                Workout(id = "p9-b", name = "B: obwód", blocks = listOf(blockB), createdAt = 0L, updatedAt = 0L),
            ),
            lockedKeys = setOf("d1", "d3"),
        )

        val workoutEditorState = EditorUiState(
            loading = false,
            isNew = false,
            name = "Góra A",
            blocks = listOf(blockA, blockB),
            exercises = byId,
        )
    }
}
