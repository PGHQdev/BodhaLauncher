package com.bodhalauncher.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * One on-device event (#25, ADR 0009): a type, a timestamp, at most one duration.
 * The schema mirrors the engine's LoggedEvent and stays flush-capable — a later
 * explicit opt-in reads these same rows; nothing here ever transmits.
 */
@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val atEpochMillis: Long,
    val valueMillis: Long?,
)

@Dao
interface EventLogDao {
    @Insert
    suspend fun insert(event: EventLogEntity)

    @Query("DELETE FROM event_log WHERE atEpochMillis < :cutoffMillis")
    suspend fun deleteBefore(cutoffMillis: Long)

    @Query("DELETE FROM event_log")
    suspend fun deleteAll()

    @Query(
        "SELECT * FROM event_log WHERE atEpochMillis >= :fromMillis AND atEpochMillis < :toMillis ORDER BY atEpochMillis"
    )
    suspend fun between(fromMillis: Long, toMillis: Long): List<EventLogEntity>

    @Query("SELECT COUNT(*) FROM event_log")
    suspend fun count(): Int
}

/**
 * The local relational store (#19). Behavioral tables land with the feature that
 * first needs them; the event log arrives first (#25). Device-local only.
 */
@Database(entities = [EventLogEntity::class], version = 1, exportSchema = true)
abstract class BodhaDatabase : RoomDatabase() {

    abstract fun eventLog(): EventLogDao

    companion object {
        @Volatile
        private var instance: BodhaDatabase? = null

        fun get(context: Context): BodhaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, BodhaDatabase::class.java, "bodha.db"
            ).build().also { instance = it }
        }
    }
}
