package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---

@Entity(tableName = "app_states")
data class AppState(
    @PrimaryKey val packageName: String,
    val isLockedOut: Boolean = false,
    val lockOutUntil: Long = 0L,
    val currentSessionEndTime: Long = 0L
)

@Entity(tableName = "weekly_logs")
data class WeeklyLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val durationMinutes: Int,
    val category: String
)

@Entity(tableName = "weekly_reports")
data class WeeklyReport(
    @PrimaryKey val weekIdentifier: String,
    val reportText: String,
    val timestamp: Long
)

// --- DAO ---

@Dao
interface AppDao {
    @Query("SELECT * FROM app_states WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppState(packageName: String): AppState?

    @Query("SELECT * FROM app_states WHERE packageName = :packageName LIMIT 1")
    fun getAppStateFlow(packageName: String): Flow<AppState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppState(appState: AppState)

    @Query("DELETE FROM app_states WHERE packageName = :packageName")
    suspend fun deleteAppState(packageName: String)

    @Query("SELECT * FROM app_states")
    suspend fun getAllAppStates(): List<AppState>

    // Weekly Logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyLog(log: WeeklyLog)

    @Query("SELECT * FROM weekly_logs WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getLogsSince(sinceTimestamp: Long): List<WeeklyLog>

    @Query("SELECT * FROM weekly_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<WeeklyLog>>

    // Weekly Reports
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyReport(report: WeeklyReport)

    @Query("SELECT * FROM weekly_reports ORDER BY timestamp DESC")
    fun getAllWeeklyReportsFlow(): Flow<List<WeeklyReport>>
}

// --- DATABASE ---

@Database(
    entities = [AppState::class, WeeklyLog::class, WeeklyReport::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "intent_gate_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- REPOSITORY ---

class AppRepository(private val appDao: AppDao) {
    suspend fun getAppState(packageName: String): AppState? {
        return appDao.getAppState(packageName)
    }

    fun getAppStateFlow(packageName: String): Flow<AppState?> {
        return appDao.getAppStateFlow(packageName)
    }

    suspend fun insertAppState(appState: AppState) {
        appDao.insertAppState(appState)
    }

    suspend fun insertWeeklyLog(log: WeeklyLog) {
        appDao.insertWeeklyLog(log)
    }

    suspend fun getLogsSince(sinceTimestamp: Long): List<WeeklyLog> {
        return appDao.getLogsSince(sinceTimestamp)
    }

    fun getAllLogsFlow(): Flow<List<WeeklyLog>> {
        return appDao.getAllLogsFlow()
    }

    suspend fun insertWeeklyReport(report: WeeklyReport) {
        appDao.insertWeeklyReport(report)
    }

    fun getAllWeeklyReportsFlow(): Flow<List<WeeklyReport>> {
        return appDao.getAllWeeklyReportsFlow()
    }
}
