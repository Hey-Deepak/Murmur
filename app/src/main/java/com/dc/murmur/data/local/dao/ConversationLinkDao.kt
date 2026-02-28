package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dc.murmur.data.local.entity.ConversationLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationLinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: ConversationLinkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<ConversationLinkEntity>)

    @Query("""
        SELECT * FROM conversation_links
        WHERE sourceChunkId = :chunkId OR targetChunkId = :chunkId
        ORDER BY createdAt DESC
    """)
    suspend fun getByChunk(chunkId: Long): List<ConversationLinkEntity>

    @Query("SELECT * FROM conversation_links WHERE linkType = :type ORDER BY createdAt DESC LIMIT :limit")
    fun getByType(type: String, limit: Int): Flow<List<ConversationLinkEntity>>

    @Query("DELETE FROM conversation_links WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
