package pl.mysttic.trainings.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import pl.mysttic.trainings.data.db.dao.CheckpointDao
import pl.mysttic.trainings.data.db.dao.ExerciseDao
import pl.mysttic.trainings.data.db.dao.ProgramDao
import pl.mysttic.trainings.data.db.dao.SessionDao
import pl.mysttic.trainings.data.db.dao.TuningDao
import pl.mysttic.trainings.data.db.dao.WorkoutDao
import pl.mysttic.trainings.data.db.entity.ExerciseEntity
import pl.mysttic.trainings.data.db.entity.ExerciseTuningEntity
import pl.mysttic.trainings.data.db.entity.PlanExerciseNoteEntity
import pl.mysttic.trainings.data.db.entity.PlayerCheckpointEntity
import pl.mysttic.trainings.data.db.entity.ProgramDayEntity
import pl.mysttic.trainings.data.db.entity.ProgramEntity
import pl.mysttic.trainings.data.db.entity.SessionEntity
import pl.mysttic.trainings.data.db.entity.SetLogEntity
import pl.mysttic.trainings.data.db.entity.WorkoutEntity

/**
 * Schemat wersjonowany; eksport schematu do `app/schemas/` (plugin androidx.room).
 * Każda zmiana encji = podbicie `version` + Migration w [AppDatabase.MIGRATIONS] (docs/DECISIONS.md, D-05).
 */
@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutEntity::class,
        SessionEntity::class,
        SetLogEntity::class,
        PlayerCheckpointEntity::class,
        ProgramEntity::class,
        ProgramDayEntity::class,
        ExerciseTuningEntity::class,
        PlanExerciseNoteEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun sessionDao(): SessionDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun programDao(): ProgramDao
    abstract fun tuningDao(): TuningDao

    companion object {
        const val NAME = "trainings.db"

        /** v2: cykle treningowe z kreatora (D-20). Historia i ćwiczenia zostają nietknięte. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `programs` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `profileJson` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `cycleDays` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `program_days` (" +
                        "`programId` TEXT NOT NULL, `dayIndex` INTEGER NOT NULL, `workoutId` TEXT, " +
                        "PRIMARY KEY(`programId`, `dayIndex`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_program_days_programId` ON `program_days` (`programId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_program_days_workoutId` ON `program_days` (`workoutId`)")
            }
        }

        /** v3: pamięć trenera per ćwiczenie (D-35). Nowa tabela, reszta bez zmian. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_tuning` (" +
                        "`exerciseId` TEXT NOT NULL, `repsDelta` INTEGER NOT NULL, `secondsDelta` INTEGER NOT NULL, " +
                        "`excluded` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`exerciseId`))",
                )
            }
        }

        /**
         * v4: lista planów i plan w sesji (D-42). Plan dostaje źródło, opis, autora, pochodzenie, tożsamość do plików
         * i kotwicę rytmu; sesja zapisuje plan i liczbę przewidzianych serii. Stare sesje dostają plan tą samą regułą,
         * którą kolejka liczyła je dotąd (trening z dni planu, start po starcie planu), więc postęp się nie zmienia.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `programs` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'WIZARD'")
                db.execSQL("ALTER TABLE `programs` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `programs` ADD COLUMN `author` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `programs` ADD COLUMN `originId` TEXT")
                db.execSQL("ALTER TABLE `programs` ADD COLUMN `shareUid` TEXT")
                db.execSQL("ALTER TABLE `programs` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `programs` ADD COLUMN `rhythmStart` INTEGER NOT NULL DEFAULT 0")
                // Profil gotowego programu niesie `presetId` (D-37); kreator zapisuje tam null.
                db.execSQL(
                    "UPDATE `programs` SET `source` = 'PRESET' " +
                        "WHERE `profileJson` LIKE '%\"presetId\":\"%'",
                )
                db.execSQL("UPDATE `programs` SET `updatedAt` = `createdAt`")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `programId` TEXT")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `plannedSets` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_programId` ON `sessions` (`programId`)")
                // Najnowszy pasujący plan wygrywa: ten sam trening w dwóch planach dotąd nie występował,
                // ale sortowanie trzyma wynik deterministyczny.
                db.execSQL(
                    "UPDATE `sessions` SET `programId` = (" +
                        "SELECT d.`programId` FROM `program_days` d JOIN `programs` p ON p.`id` = d.`programId` " +
                        "WHERE d.`workoutId` = `sessions`.`workoutId` AND `sessions`.`startedAt` >= p.`startedAt` " +
                        "ORDER BY p.`startedAt` DESC LIMIT 1) " +
                        "WHERE `workoutId` IS NOT NULL",
                )
            }
        }

        /** v5: notatki do ćwiczeń w obrębie planu (D-44). Nowa tabela, reszta bez zmian. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_exercise_notes` (" +
                        "`programId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`programId`, `exerciseId`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_plan_exercise_notes_programId` ON `plan_exercise_notes` (`programId`)",
                )
            }
        }

        /**
         * v6: data wycofania ćwiczenia z bazy (D-47). Nowa kolumna bez wartości, więc każde ćwiczenie zostaje
         * w użyciu, dopóki baza z `retiredAt` nie wejdzie przy imporcie.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `retiredAt` TEXT")
            }
        }

        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}
