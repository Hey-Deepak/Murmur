package com.dc.murmur.data.repository

import com.dc.murmur.data.local.dao.ActivityDao
import com.dc.murmur.data.local.dao.ActivityTimeBreakdown
import com.dc.murmur.data.local.dao.ConversationLinkDao
import com.dc.murmur.data.local.dao.DailyInsightDao
import com.dc.murmur.data.local.dao.PredictionDao
import com.dc.murmur.data.local.dao.TopicDao
import com.dc.murmur.data.local.entity.ActivityEntity
import com.dc.murmur.data.local.entity.ChunkTopicEntity
import com.dc.murmur.data.local.entity.ConversationLinkEntity
import com.dc.murmur.data.local.entity.DailyInsightEntity
import com.dc.murmur.data.local.entity.PredictionEntity
import com.dc.murmur.data.local.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

class InsightsRepository(
    private val activityDao: ActivityDao,
    private val dailyInsightDao: DailyInsightDao,
    private val topicDao: TopicDao,
    private val conversationLinkDao: ConversationLinkDao,
    private val predictionDao: PredictionDao
) {

    // Activity
    suspend fun saveActivity(activity: ActivityEntity): Long {
        return activityDao.insert(activity)
    }

    fun getActivitiesByDate(date: String): Flow<List<ActivityEntity>> {
        return activityDao.getByDate(date)
    }

    suspend fun getTimeBreakdown(date: String): List<ActivityTimeBreakdown> {
        return activityDao.getTimeBreakdown(date)
    }

    suspend fun getTimeBreakdownRange(startDate: String, endDate: String): List<ActivityTimeBreakdown> {
        return activityDao.getTimeBreakdownRange(startDate, endDate)
    }

    // Daily Insights
    suspend fun saveDailyInsight(insight: DailyInsightEntity): Long {
        return dailyInsightDao.insert(insight)
    }

    suspend fun getDailyInsight(date: String): DailyInsightEntity? {
        return dailyInsightDao.getByDate(date)
    }

    fun getDailyInsightFlow(date: String): Flow<DailyInsightEntity?> {
        return dailyInsightDao.getByDateFlow(date)
    }

    fun getRecentInsights(limit: Int = 7): Flow<List<DailyInsightEntity>> {
        return dailyInsightDao.getRecent(limit)
    }

    suspend fun getInsightsRange(startDate: String, endDate: String): List<DailyInsightEntity> {
        return dailyInsightDao.getRange(startDate, endDate)
    }

    // Topics
    suspend fun getOrCreateTopic(name: String, category: String?, now: Long): TopicEntity {
        val existing = topicDao.getByName(name.lowercase())
        if (existing != null) {
            val updated = existing.copy(
                lastMentionedAt = now,
                totalMentions = existing.totalMentions + 1,
                category = category ?: existing.category
            )
            topicDao.update(updated)
            return updated
        }
        val topic = TopicEntity(
            name = name.lowercase(),
            firstMentionedAt = now,
            lastMentionedAt = now,
            totalMentions = 1,
            category = category
        )
        val id = topicDao.insert(topic)
        return topic.copy(id = id)
    }

    suspend fun linkChunkToTopic(chunkId: Long, topicId: Long, relevance: Float, keyPoints: String?) {
        topicDao.insertChunkTopic(ChunkTopicEntity(
            chunkId = chunkId,
            topicId = topicId,
            relevance = relevance,
            keyPoints = keyPoints
        ))
    }

    fun getTopTopics(limit: Int = 10): Flow<List<TopicEntity>> {
        return topicDao.getTopTopics(limit)
    }

    fun getRecentTopics(limit: Int = 10): Flow<List<TopicEntity>> {
        return topicDao.getRecentTopics(limit)
    }

    // Links
    suspend fun saveLinks(links: List<ConversationLinkEntity>) {
        if (links.isNotEmpty()) {
            conversationLinkDao.insertAll(links)
        }
    }

    suspend fun getLinksForChunk(chunkId: Long): List<ConversationLinkEntity> {
        return conversationLinkDao.getByChunk(chunkId)
    }

    // Predictions
    suspend fun savePrediction(prediction: PredictionEntity): Long {
        return predictionDao.insert(prediction)
    }

    fun getActivePredictions(limit: Int = 10): Flow<List<PredictionEntity>> {
        return predictionDao.getActive(limit)
    }

    suspend fun dismissPrediction(id: Long) {
        predictionDao.dismiss(id)
    }

    suspend fun markPredictionFulfillment(id: Long, fulfilled: Boolean) {
        predictionDao.markFulfillment(id, fulfilled)
    }
}
