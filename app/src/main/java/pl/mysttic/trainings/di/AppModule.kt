package pl.mysttic.trainings.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.data.db.AppDatabase
import pl.mysttic.trainings.data.db.dao.CheckpointDao
import pl.mysttic.trainings.data.db.dao.ExerciseDao
import pl.mysttic.trainings.data.db.dao.ProgramDao
import pl.mysttic.trainings.data.db.dao.SessionDao
import pl.mysttic.trainings.data.db.dao.TuningDao
import pl.mysttic.trainings.data.db.dao.WorkoutDao
import pl.mysttic.trainings.service.AndroidCues
import pl.mysttic.trainings.service.AndroidMonotonicClock
import pl.mysttic.trainings.service.Cues
import pl.mysttic.trainings.service.MonotonicClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            // Kopia systemowa (res/xml/data_extraction_rules.xml) pomija trainings.db-wal, bo plik WAL bez bazy
            // jest bezużyteczny — ale przy dzienniku WAL ostatnie zapisy siedzą właśnie tam i odtworzona baza
            // cofa się do ostatniego checkpointu. Przy kilku zapisach na trening TRUNCATE nic nie kosztuje,
            // a wszystko ląduje w jednym pliku, który kopia zabiera.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    @Provides
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideCheckpointDao(db: AppDatabase): CheckpointDao = db.checkpointDao()

    @Provides
    fun provideProgramDao(db: AppDatabase): ProgramDao = db.programDao()

    @Provides
    fun provideTuningDao(db: AppDatabase): TuningDao = db.tuningDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideClock(): MonotonicClock = AndroidMonotonicClock

    @Provides
    @Singleton
    fun provideCues(@ApplicationContext context: Context): Cues = AndroidCues(context)
}
