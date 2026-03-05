package com.dc.murmur.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dc.murmur.data.local.dao.ActivityDao
import com.dc.murmur.data.local.dao.BatteryLogDao
import com.dc.murmur.data.local.dao.ConversationLinkDao
import com.dc.murmur.data.local.dao.DailyInsightDao
import com.dc.murmur.data.local.dao.PredictionDao
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.local.dao.SessionDao
import com.dc.murmur.data.local.dao.SpeakerSegmentDao
import com.dc.murmur.data.local.dao.TopicDao
import com.dc.murmur.data.local.dao.TranscriptionDao
import com.dc.murmur.data.local.dao.VoiceProfileDao
import com.dc.murmur.data.local.entity.ActivityEntity
import com.dc.murmur.data.local.entity.BatteryLogEntity
import com.dc.murmur.data.local.entity.ChunkTopicEntity
import com.dc.murmur.data.local.entity.ConversationLinkEntity
import com.dc.murmur.data.local.entity.DailyInsightEntity
import com.dc.murmur.data.local.entity.PredictionEntity
import com.dc.murmur.data.local.entity.RecordingChunkEntity
import com.dc.murmur.data.local.entity.SessionEntity
import com.dc.murmur.data.local.entity.SpeakerSegmentEntity
import com.dc.murmur.data.local.entity.TopicEntity
import com.dc.murmur.data.local.entity.TranscriptionEntity
import com.dc.murmur.data.local.entity.VoiceProfileEntity

@Database(
    entities = [
        RecordingChunkEntity::class,
        SessionEntity::class,
        BatteryLogEntity::class,
        TranscriptionEntity::class,
        ActivityEntity::class,
        VoiceProfileEntity::class,
        SpeakerSegmentEntity::class,
        TopicEntity::class,
        ChunkTopicEntity::class,
        ConversationLinkEntity::class,
        DailyInsightEntity::class,
        PredictionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class MurmurDatabase : RoomDatabase() {

    abstract fun recordingChunkDao(): RecordingChunkDao
    abstract fun sessionDao(): SessionDao
    abstract fun batteryLogDao(): BatteryLogDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun activityDao(): ActivityDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun speakerSegmentDao(): SpeakerSegmentDao
    abstract fun topicDao(): TopicDao
    abstract fun conversationLinkDao(): ConversationLinkDao
    abstract fun dailyInsightDao(): DailyInsightDao
    abstract fun predictionDao(): PredictionDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // New columns on transcriptions
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN activityType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN speakerCount INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN topicsSummary TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN behavioralTags TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN keyMoment TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN analysisVersion INTEGER NOT NULL DEFAULT 1")

                // activities table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS activities (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chunkId INTEGER NOT NULL,
                        activityType TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        subActivity TEXT DEFAULT NULL,
                        date TEXT NOT NULL,
                        startTime INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        detectedAt INTEGER NOT NULL,
                        FOREIGN KEY (chunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_chunkId ON activities(chunkId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_date ON activities(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_activityType ON activities(activityType)")

                // voice_profiles table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS voice_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        voiceId TEXT NOT NULL,
                        label TEXT DEFAULT NULL,
                        photoUri TEXT DEFAULT NULL,
                        firstSeenAt INTEGER NOT NULL,
                        lastSeenAt INTEGER NOT NULL,
                        totalInteractionMs INTEGER NOT NULL DEFAULT 0,
                        interactionCount INTEGER NOT NULL DEFAULT 0,
                        notes TEXT DEFAULT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voice_profiles_label ON voice_profiles(label)")

                // speaker_segments table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS speaker_segments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chunkId INTEGER NOT NULL,
                        voiceProfileId INTEGER DEFAULT NULL,
                        speakerLabel TEXT NOT NULL,
                        speakingDurationMs INTEGER NOT NULL,
                        turnCount INTEGER NOT NULL,
                        role TEXT DEFAULT NULL,
                        emotionalState TEXT DEFAULT NULL,
                        FOREIGN KEY (chunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE,
                        FOREIGN KEY (voiceProfileId) REFERENCES voice_profiles(id) ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_speaker_segments_chunkId ON speaker_segments(chunkId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_speaker_segments_voiceProfileId ON speaker_segments(voiceProfileId)")

                // topics table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS topics (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        firstMentionedAt INTEGER NOT NULL,
                        lastMentionedAt INTEGER NOT NULL,
                        totalMentions INTEGER NOT NULL DEFAULT 1,
                        totalDurationMs INTEGER NOT NULL DEFAULT 0,
                        category TEXT DEFAULT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_topics_name ON topics(name)")

                // chunk_topics junction
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chunk_topics (
                        chunkId INTEGER NOT NULL,
                        topicId INTEGER NOT NULL,
                        relevance REAL NOT NULL DEFAULT 1.0,
                        keyPoints TEXT DEFAULT NULL,
                        PRIMARY KEY (chunkId, topicId),
                        FOREIGN KEY (chunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE,
                        FOREIGN KEY (topicId) REFERENCES topics(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chunk_topics_chunkId ON chunk_topics(chunkId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chunk_topics_topicId ON chunk_topics(topicId)")

                // conversation_links table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS conversation_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceChunkId INTEGER NOT NULL,
                        targetChunkId INTEGER NOT NULL,
                        linkType TEXT NOT NULL,
                        description TEXT DEFAULT NULL,
                        strength REAL NOT NULL DEFAULT 1.0,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (sourceChunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE,
                        FOREIGN KEY (targetChunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_links_sourceChunkId ON conversation_links(sourceChunkId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_links_targetChunkId ON conversation_links(targetChunkId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_links_linkType ON conversation_links(linkType)")

                // daily_insights table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_insights (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        timelineJson TEXT NOT NULL,
                        timeBreakdownJson TEXT NOT NULL,
                        peopleSummaryJson TEXT NOT NULL,
                        topTopics TEXT NOT NULL,
                        highlight TEXT DEFAULT NULL,
                        overallSentiment TEXT NOT NULL,
                        overallSentimentScore REAL NOT NULL,
                        totalRecordedMs INTEGER NOT NULL,
                        totalAnalyzedChunks INTEGER NOT NULL,
                        generatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_insights_date ON daily_insights(date)")

                // predictions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS predictions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        predictionType TEXT NOT NULL,
                        message TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        basedOnDays INTEGER NOT NULL,
                        triggerTime INTEGER DEFAULT NULL,
                        date TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        wasFulfilled INTEGER DEFAULT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_predictions_date ON predictions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_predictions_isActive ON predictions(isActive)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE voice_profiles ADD COLUMN embedding TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voice_profiles_voiceId ON voice_profiles(voiceId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add segment timings for speaker-specific audio playback
                db.execSQL("ALTER TABLE speaker_segments ADD COLUMN segmentTimings TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE voice_profiles ADD COLUMN embeddingSampleCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE voice_profiles ADD COLUMN embeddingUpdatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): MurmurDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MurmurDatabase::class.java,
                "murmur_db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        }
    }
}
