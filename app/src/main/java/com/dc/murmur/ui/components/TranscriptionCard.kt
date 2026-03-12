package com.dc.murmur.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dc.murmur.data.local.dao.SpeakerSegmentWithProfile
import com.dc.murmur.data.local.entity.TranscriptionEntity
import com.dc.murmur.ui.theme.ClaudePurple
import com.dc.murmur.ui.theme.ClaudePurpleBg
import com.dc.murmur.ui.theme.DarkSurfaceCard
import com.dc.murmur.ui.theme.SentimentAnxious
import com.dc.murmur.ui.theme.SentimentAnxiousBg
import com.dc.murmur.ui.theme.SentimentConfident
import com.dc.murmur.ui.theme.SentimentConfidentBg
import com.dc.murmur.ui.theme.SentimentExcited
import com.dc.murmur.ui.theme.SentimentExcitedBg
import com.dc.murmur.ui.theme.SentimentFrustrated
import com.dc.murmur.ui.theme.SentimentFrustratedBg
import com.dc.murmur.ui.theme.SentimentHesitant
import com.dc.murmur.ui.theme.SentimentHesitantBg
import com.dc.murmur.ui.theme.SentimentNegative
import com.dc.murmur.ui.theme.SentimentNegativeBg
import com.dc.murmur.ui.theme.SentimentNeutral
import com.dc.murmur.ui.theme.SentimentNeutralBg
import com.dc.murmur.ui.theme.SentimentPositive
import com.dc.murmur.ui.theme.SentimentPositiveBg
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptionCard(
    transcription: TranscriptionEntity,
    modifier: Modifier = Modifier,
    speakers: List<SpeakerSegmentWithProfile> = emptyList(),
    playingProfileId: Long? = null,
    onPlaySpeaker: (Long) -> Unit = {},
    onTagSpeaker: (Long) -> Unit = {}
) {
    val summary = parseSummary(transcription.keywords)
    val tags = parseTags(transcription.keywords)
    val topics = parseJsonStringArray(transcription.topicsSummary)
    val behavioralTags = parseJsonStringArray(transcription.behavioralTags)
    val isClaudePowered = transcription.modelUsed.contains("claude") || transcription.modelUsed.contains("bridge")
    var expanded by remember { mutableStateOf(false) }

    val (sentimentColor, sentimentBg) = when (transcription.sentiment) {
        "positive" -> Pair(SentimentPositive, SentimentPositiveBg)
        "negative" -> Pair(SentimentNegative, SentimentNegativeBg)
        "anxious" -> Pair(SentimentAnxious, SentimentAnxiousBg)
        "frustrated" -> Pair(SentimentFrustrated, SentimentFrustratedBg)
        "confident" -> Pair(SentimentConfident, SentimentConfidentBg)
        "hesitant" -> Pair(SentimentHesitant, SentimentHesitantBg)
        "excited" -> Pair(SentimentExcited, SentimentExcitedBg)
        else -> Pair(SentimentNeutral, SentimentNeutralBg)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Column {
            // Sentiment accent bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(sentimentColor, sentimentColor.copy(alpha = 0.3f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .animateContentSize(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Expandable transcript text
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                ) {
                    Text(
                        text = transcription.text.ifBlank { "(No speech detected)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (transcription.text.length > 120) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (expanded) "Show less" else "Show more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Activity type + topics row
                if (transcription.activityType != null || topics.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Activity type chip
                        if (transcription.activityType != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = transcription.activityType.replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Topic chips
                        topics.forEach { topic ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = topic,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Behavioral tags
                if (behavioralTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        behavioralTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Key moment highlight
                if (!transcription.keyMoment.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = transcription.keyMoment,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Tags display (keywords from old format)
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Conversation Analysis section (summary from keywords JSON object format)
                if (summary.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { expanded = !expanded }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isClaudePowered) ClaudePurple
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Conversation Analysis",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isClaudePowered) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "via Claude Code",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = ClaudePurple
                                )
                            }
                        }
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Speakers section
                if (speakers.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Speakers",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        speakers.forEachIndexed { index, speaker ->
                            val isPlayingThis = playingProfileId == speaker.voiceProfileId
                            val displayName = speaker.profileLabel
                                ?: "Speaker ${index + 1}"
                            val durationText = formatSpeakerDuration(speaker.speakingDurationMs)
                            val isUntagged = speaker.profileLabel == null

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Play button
                                if (speaker.voiceProfileId != null) {
                                    IconButton(
                                        onClick = { onPlaySpeaker(speaker.voiceProfileId) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingThis) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlayingThis) "Stop" else "Play $displayName",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isPlayingThis) MaterialTheme.colorScheme.error
                                                   else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(28.dp))
                                }

                                // Name
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                // Duration
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Role chip
                                if (speaker.role != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = speaker.role,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // Edit/tag button
                                if (speaker.voiceProfileId != null) {
                                    IconButton(
                                        onClick = { onTagSpeaker(speaker.voiceProfileId) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = if (isUntagged) "Tag speaker" else "Rename speaker",
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isUntagged) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom metadata row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sentiment score badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sentimentBg
                    ) {
                        Text(
                            text = "${transcription.sentiment} ${"%.0f".format(transcription.sentimentScore * 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = sentimentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Source badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isClaudePowered) ClaudePurpleBg
                               else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (isClaudePowered) "Claude Code" else "On-device",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isClaudePowered) ClaudePurple
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Extracts the Claude summary from either JSON format. Returns "" for old plain-array rows. */
private fun parseSummary(json: String): String {
    if (json.isBlank()) return ""
    return try {
        JSONObject(json).optString("summary", "")
    } catch (e: Exception) {
        ""
    }
}

private fun formatSpeakerDuration(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    return if (m > 0) "%dm %02ds".format(m, s % 60) else "%ds".format(s)
}

/** Extracts tags array from keywords JSON. Handles both {"tags":[...]} and plain ["..."] formats. */
private fun parseTags(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("tags") ?: return emptyList()
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        // Try plain JSON array format: ["tag1","tag2"]
        try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Parse a nullable JSON string array like '["a","b"]' into a list. */
private fun parseJsonStringArray(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}
