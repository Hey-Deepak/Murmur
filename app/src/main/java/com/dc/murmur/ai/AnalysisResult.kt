package com.dc.murmur.ai

data class DiarizedSpeakerInfo(
    val speakerIndex: Int,
    val totalMs: Long,
    val ratio: Float,
    val matchedProfileId: Long?,
    val matchedProfileName: String?,
    val matchConfidence: Float?,
    val embedding: FloatArray?,
    // Time ranges within the chunk audio where this speaker talks: [[startMs, endMs], ...]
    val timings: List<Pair<Long, Long>> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiarizedSpeakerInfo) return false
        return speakerIndex == other.speakerIndex && matchedProfileId == other.matchedProfileId
    }
    override fun hashCode() = speakerIndex * 31 + (matchedProfileId?.hashCode() ?: 0)
}

data class SpeakerResult(
    val label: String,
    val speakingRatio: Float,
    val turnCount: Int,
    val role: String?,
    val emotionalState: String?,
    val matchedProfileId: Long? = null
)

data class TopicResult(
    val name: String,
    val relevance: Float,
    val category: String?,
    val keyPoints: List<String>
)

data class AnalysisResult(
    val chunkId: Long,
    val text: String,
    val sentiment: String,
    val sentimentScore: Float,
    val keywords: String,
    val modelUsed: String,
    val activityType: String? = null,
    val activityConfidence: Float? = null,
    val activitySubType: String? = null,
    val speakers: List<SpeakerResult> = emptyList(),
    val topics: List<TopicResult> = emptyList(),
    val behavioralTags: List<String> = emptyList(),
    val keyMoment: String? = null,
    val diarizedSpeakers: List<DiarizedSpeakerInfo> = emptyList()
)
