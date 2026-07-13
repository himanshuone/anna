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
    val currentSessionEndTime: Long = 0L,
    val openCount: Int = 0,
    val deferralCount: Int = 0,
    val totalAccessCount: Int = 0,
    val lastInteractedTime: Long = 0L,
    val isHidden: Boolean = false,
    val folderName: String? = null
)

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey val id: String = "global",
    val dailyStreak: Int = 0,
    val lastActiveDate: String = ""
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
}

@Dao
interface UserStatsDao {
    @Query("SELECT * FROM user_stats WHERE id = 'global' LIMIT 1")
    suspend fun getUserStats(): UserStats?

    @Query("SELECT * FROM user_stats WHERE id = 'global' LIMIT 1")
    fun getUserStatsFlow(): Flow<UserStats?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserStats(userStats: UserStats)
}

// --- DATABASE ---

@Database(
    entities = [AppState::class, UserStats::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun userStatsDao(): UserStatsDao

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
}
