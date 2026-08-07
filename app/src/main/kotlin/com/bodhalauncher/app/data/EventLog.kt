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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bodhalauncher.app.focus.FocusRecordDao
import com.bodhalauncher.app.focus.FocusRecordEntity
import com.bodhalauncher.app.inbox.NotificationLogDao
import com.bodhalauncher.app.inbox.NotificationRecordEntity
import com.bodhalauncher.app.session.SessionRecordDao
import com.bodhalauncher.app.session.SessionRecordEntity

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
 * first needs them; the event log arrived first (#25), the notification log
 * with the digest (#161). Device-local only.
 */
@Database(
    entities = [
        EventLogEntity::class, NotificationRecordEntity::class,
        SessionRecordEntity::class, FocusRecordEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class BodhaDatabase : RoomDatabase() {

    abstract fun eventLog(): EventLogDao

    abstract fun notificationLog(): NotificationLogDao

    abstract fun sessionRecords(): SessionRecordDao

    abstract fun focusRecords(): FocusRecordDao

    companion object {
        @Volatile
        private var instance: BodhaDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notification_log` (" +
                        "`keyHash` TEXT NOT NULL, `appPackage` TEXT NOT NULL, " +
                        "`section` TEXT NOT NULL, `category` TEXT, " +
                        "`postedAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`keyHash`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_record` (" +
                        "`sessionId` INTEGER NOT NULL, `startMillis` INTEGER NOT NULL, " +
                        "`endMillis` INTEGER, `dayEpochDay` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`sessionId`))"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_record` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`label` TEXT NOT NULL, `startMillis` INTEGER NOT NULL, " +
                        "`endMillis` INTEGER NOT NULL, `reaches` INTEGER NOT NULL, " +
                        "`proceeds` INTEGER NOT NULL, `endedEarly` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): BodhaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, BodhaDatabase::class.java, "bodha.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
