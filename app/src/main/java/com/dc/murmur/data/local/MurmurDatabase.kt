package com.dc.murmur.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dc.murmur.data.local.dao.BatteryLogDao
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.local.dao.SessionDao
import com.dc.murmur.data.local.dao.TranscriptionDao
import com.dc.murmur.data.local.entity.BatteryLogEntity
import com.dc.murmur.data.local.entity.RecordingChunkEntity
import com.dc.murmur.data.local.entity.SessionEntity
import com.dc.murmur.data.local.entity.TranscriptionEntity

@Database(
    entities = [
        RecordingChunkEntity::class,
        SessionEntity::class,
        BatteryLogEntity::class,
        TranscriptionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MurmurDatabase : RoomDatabase() {

    abstract fun recordingChunkDao(): RecordingChunkDao
    abstract fun sessionDao(): SessionDao
    abstract fun batteryLogDao(): BatteryLogDao
    abstract fun transcriptionDao(): TranscriptionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transcriptions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chunkId INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        language TEXT NOT NULL DEFAULT 'en',
                        sentiment TEXT NOT NULL,
                        sentimentScore REAL NOT NULL,
                        keywords TEXT NOT NULL,
                        processedAt INTEGER NOT NULL,
                        modelUsed TEXT NOT NULL,
                        FOREIGN KEY (chunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transcriptions_chunkId ON transcriptions(chunkId)")
            }
        }

        fun create(context: Context): MurmurDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MurmurDatabase::class.java,
                "murmur_db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
