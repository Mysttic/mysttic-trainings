package pl.mysttic.trainings.domain.plan.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Plik planu do udostępniania (D-42): osobny format, nie wariant kopii zapasowej. Nazwy pól są umową na lata —
 * plik wysłany dziś ma się wczytać w każdej przyszłej wersji — więc każde pole ma stałe `@SerialName`,
 * a enumy jadą jako tekst (nieznana wartość z nowszej aplikacji jest pomijana z ostrzeżeniem, nie wysadza odczytu).
 * W pliku nie ma sylwetki (wzrost, waga), historii ani pamięci trenera — tylko to, co ułożył autor.
 * Od formatu 2 (D-58) plik jedzie w kopercie `PlanEnvelope`: podpisany kluczem telefonu, a na życzenie zamknięty
 * hasłem (`PlanSeal`). Ta klasa to treść koperty; plik formatu 1 to sama treść, bez podpisu.
 */
@Serializable
data class PlanFile(
    /** Znacznik formatu, bez wartości domyślnej: odróżnia plik planu od kopii zapasowej i od byle JSON-a. */
    @SerialName("format") val format: String,
    @SerialName("formatVersion") val formatVersion: Int,
    @SerialName("appVersion") val appVersion: String = "",
    /** Wersja seedu u autora; wyższa niż u odbiorcy = ćwiczenia bez ilustracji do czasu aktualizacji. */
    @SerialName("seedVersion") val seedVersion: Int = 0,
    @SerialName("exportedAt") val exportedAt: Long = 0,
    /**
     * Kto podpisał i wysłał plik (D-58): imię z „O mnie”. Autor planu stoi w `plan.author` i bywa kimś innym,
     * gdy ktoś przekazuje dalej plan od kogoś. Pliki formatu 1 tego pola nie mają.
     */
    @SerialName("signer") val signer: String = "",
    @SerialName("plan") val plan: SharedPlan,
) {
    companion object {
        const val FORMAT = "mysttic-trainings-plan"
        /** 2 od D-58: podpis i hasło w kopercie. Pliki formatu 1 dalej się wczytują, jako niepodpisane. */
        const val VERSION = 2
        const val EXTENSION = ".plan.json"
        const val MIME = "application/json"
        /** Największy plik planu; 120 dni z pełnymi treningami to ułamek tego. */
        const val MAX_BYTES = 1024 * 1024
    }
}

@Serializable
data class SharedPlan(
    /** Tożsamość planu między telefonami: ponowny import tego samego pliku pokazuje „Masz już ten plan”. */
    @SerialName("uid") val uid: String,
    @SerialName("name") val name: String,
    @SerialName("author") val author: String = "",
    @SerialName("description") val description: String = "",
    /** Poziom, dla którego autor pisał wartości; odbiorca dostaje je dosłownie. */
    @SerialName("level") val level: String = "INTERMEDIATE",
    @SerialName("days") val days: List<SharedDay>,
    @SerialName("workouts") val workouts: List<SharedWorkout>,
    /** Migawki ćwiczeń użytych w planie — odbiorca bez tego ćwiczenia w bibliotece dostaje je z pliku. */
    @SerialName("exercises") val exercises: List<SharedExercise> = emptyList(),
    /** Notatki do ćwiczeń w tym planie (D-44), np. ciężary rozpisane przez autora; starsze pliki ich nie mają. */
    @SerialName("notes") val notes: List<SharedNote> = emptyList(),
)

/** Notatka do ćwiczenia w planie (D-44); `exercise` to id ćwiczenia z tego pliku. */
@Serializable
data class SharedNote(
    @SerialName("exercise") val exercise: String,
    @SerialName("note") val note: String,
)

/** Dzień planu; `workout` to klucz treningu z tego pliku („w1”), null = odpoczynek. */
@Serializable
data class SharedDay(
    @SerialName("day") val day: Int,
    @SerialName("workout") val workout: String? = null,
)

@Serializable
data class SharedWorkout(
    @SerialName("key") val key: String,
    @SerialName("name") val name: String,
    @SerialName("note") val note: String = "",
    @SerialName("blocks") val blocks: List<SharedBlock>,
)

@Serializable
data class SharedBlock(
    @SerialName("kind") val kind: String,
    @SerialName("rounds") val rounds: Int = 1,
    @SerialName("restBetweenRoundsSec") val restBetweenRoundsSec: Int = 60,
    @SerialName("restAfterSec") val restAfterSec: Int? = null,
    @SerialName("label") val label: String = "",
    @SerialName("items") val items: List<SharedItem>,
)

@Serializable
data class SharedItem(
    @SerialName("exercise") val exercise: String,
    @SerialName("target") val target: String,
    @SerialName("value") val value: Int,
    @SerialName("sets") val sets: Int = 3,
    @SerialName("restAfterSetSec") val restAfterSetSec: Int = 45,
    @SerialName("restAfterItemSec") val restAfterItemSec: Int = 60,
    @SerialName("weightKg") val weightKg: Double? = null,
    @SerialName("openEnded") val openEnded: Boolean = false,
    @SerialName("cadenceSec") val cadenceSec: Int? = null,
    @SerialName("note") val note: String = "",
    /** Okno EMOM (s, D-43); brak = zwykła przerwa. */
    @SerialName("windowSec") val windowSec: Int? = null,
)

/** Ćwiczenie bez obrazów i wariantów: tyle, żeby player i biblioteka odbiorcy miały co pokazać. */
@Serializable
data class SharedExercise(
    @SerialName("id") val id: String,
    @SerialName("namePl") val namePl: String,
    @SerialName("nameEn") val nameEn: String = "",
    @SerialName("kind") val kind: String,
    @SerialName("level") val level: String = "INTERMEDIATE",
    @SerialName("category") val category: String,
    @SerialName("equipment") val equipment: List<String> = emptyList(),
    @SerialName("primaryMuscles") val primaryMuscles: List<String> = emptyList(),
    @SerialName("secondaryMuscles") val secondaryMuscles: List<String> = emptyList(),
    @SerialName("instructionsPl") val instructionsPl: List<String> = emptyList(),
    @SerialName("defaultReps") val defaultReps: Int = 12,
    @SerialName("defaultSeconds") val defaultSeconds: Int = 30,
)
