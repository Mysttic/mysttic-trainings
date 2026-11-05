package pl.mysttic.trainings.ui.navigation

import kotlinx.serialization.Serializable

/** Trasy typowane (Navigation Compose 2.8+). Obiekty = ekrany bez argumentów, klasy = z argumentami. */
@Serializable
object TodayRoute

@Serializable
object LibraryRoute

@Serializable
object HistoryRoute

@Serializable
object SettingsRoute

@Serializable
data class ExerciseDetailRoute(val exerciseId: String)

@Serializable
data class WorkoutDetailRoute(val workoutId: String)

@Serializable
data class PlayerRoute(val workoutId: String)

/** Podgląd planu; `programId == null` = plan aktywny (wejście z „Dziś”). */
@Serializable
data class PlanRoute(val programId: String? = null)

/** Lista planów (D-42): aktywny, wstrzymane, nierozpoczęte, zakończone. */
@Serializable
object PlansRoute

@Serializable
data class PlanWizardRoute(
    /** Ocena zamkniętego cyklu z ekranu „Dziś” (nazwa `ProgressionStep`); null = kreator sam zaproponuje. */
    val step: String? = null,
)

/** Gotowe programy (D-37): druga, obok kreatora, droga do cyklu. Bez argumentów, wybór dzieje się na ekranie. */
@Serializable
object PresetsRoute

/**
 * `workoutId == null` = nowy trening. Wbudowanych nie edytujemy — najpierw kopia (D-19).
 * `programId`: nowy trening należy do tego planu (id z prefiksem planu, D-42); `fixed` — plan dosłowny.
 */
@Serializable
data class WorkoutEditorRoute(val workoutId: String? = null, val programId: String? = null, val fixed: Boolean = false)

/** Edytor planu (D-42); `programId == null` = nowy plan własny. */
@Serializable
data class PlanEditorRoute(val programId: String? = null)

/** Wysłanie planu do pliku (D-42). */
@Serializable
data class PlanShareRoute(val programId: String)

/**
 * Podgląd planu z pliku przed importem; `stagedKey == null` = najpierw wybór pliku. `error`: pliku z intencji
 * nie dało się odebrać (za duży, bez dostępu), ekran pokazuje komunikat zamiast wyboru pliku.
 */
@Serializable
data class PlanImportRoute(val stagedKey: String? = null, val error: String? = null)

/** Statystyki planu i raport PDF (D-42). */
@Serializable
data class PlanStatsRoute(val programId: String)

@Serializable
data class SessionDetailRoute(val sessionId: String)
