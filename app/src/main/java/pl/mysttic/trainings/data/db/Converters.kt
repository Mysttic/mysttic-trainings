package pl.mysttic.trainings.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.MuscleGroup

/** Listy i zbiory przechowywane jako JSON w kolumnach tekstowych. Enumy Room mapuje sam (po nazwie). */
class Converters {
    private val json = Json

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else json.decodeFromString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun fromMuscles(value: List<MuscleGroup>): String = fromStringList(value.map { it.name })

    @TypeConverter
    fun toMuscles(value: String): List<MuscleGroup> = toStringList(value).mapNotNull { name -> MuscleGroup.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun fromEquipment(value: Set<Equipment>): String = fromStringList(value.map { it.name }.sorted())

    @TypeConverter
    fun toEquipment(value: String): Set<Equipment> = toStringList(value).mapNotNull { name -> Equipment.entries.firstOrNull { it.name == name } }.toSet()
}
