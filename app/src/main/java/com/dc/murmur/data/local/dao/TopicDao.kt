package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dc.murmur.data.local.entity.ChunkTopicEntity
import com.dc.murmur.data.local.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(topic: TopicEntity): Long

    @Update
    suspend fun update(topic: TopicEntity)

    @Query("SELECT * FROM topics WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TopicEntity?

    @Query("SELECT * FROM topics ORDER BY totalMentions DESC LIMIT :limit")
    fun getTopTopics(limit: Int): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics ORDER BY lastMentionedAt DESC LIMIT :limit")
    fun getRecentTopics(limit: Int): Flow<List<TopicEntity>>

    @Query("""
        SELECT t.* FROM topics t
        INNER JOIN chunk_topics ct ON t.id = ct.topicId
        WHERE ct.chunkId = :chunkId
        ORDER BY ct.relevance DESC
    """)
    suspend fun getByChunk(chunkId: Long): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE category = :category ORDER BY totalMentions DESC")
    fun getByCategory(category: String): Flow<List<TopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunkTopic(chunkTopic: ChunkTopicEntity)

    @Query("DELETE FROM topics WHERE id = :id")
    suspend fun deleteById(id: Long)
}
