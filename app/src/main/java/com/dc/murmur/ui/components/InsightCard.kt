package com.dc.murmur.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dc.murmur.data.local.entity.TranscriptionWithChunk
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
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightCard(
    transcription: TranscriptionWithChunk,
    formattedTime: String,
    formattedDuration: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isV2 = transcription.analysisVersion >= 2
    val summary = parseSummary(transcription.keywords)
    val tags = parseTags(transcription.keywords)
    val topics = parseTopics(transcription.topicsSummary)
    val behavioralTags = parseBehavioralTags(transcription.behavioralTags)

    val (sentimentColor, sentimentBg) = sentimentColors(transcription.sentiment)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Text content
            Text(
                text = transcription.text.ifBlank { "(No speech detected)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            // Key moment (v2 only)
            if (isV2 && transcription.keyMoment != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = transcription.keyMoment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Summary
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                    Text(formattedTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                    Text(formattedDuration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (transcription.speakerCount != null && transcription.speakerCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text("${transcription.speakerCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Badges row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Sentiment badge
                Surface(shape = RoundedCornerShape(6.dp), color = sentimentBg) {
                    Text(
                        text = "${transcription.sentiment} ${"%.0f".format(transcription.sentimentScore * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = sentimentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Activity badge (v2 only)
                if (isV2 && transcription.activityType != null) {
                    val (_, actColor) = activityIconAndColor(transcription.activityType)
                    Surface(shape = RoundedCornerShape(6.dp), color = actColor.copy(alpha = 0.15f)) {
                        Text(
                            text = transcription.activityType.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = actColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Source badge
                val isClaudePowered = transcription.modelUsed.contains("claude-code")
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isClaudePowered) ClaudePurpleBg else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isClaudePowered) "Claude" else "On-device",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isClaudePowered) ClaudePurple else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Topics (v2)
                topics.take(3).forEach { topic ->
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)) {
                        Text(
                            text = topic,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Tags
                tags.take(3).forEach { tag ->
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Expanded: show behavioral tags
            if (expanded && behavioralTags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Behavioral patterns",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    behavioralTags.forEach { tag ->
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sentimentColors(sentiment: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (sentiment) {
        "positive" -> SentimentPositive to SentimentPositiveBg
        "negative" -> SentimentNegative to SentimentNegativeBg
        "anxious" -> SentimentAnxious to SentimentAnxiousBg
        "frustrated" -> SentimentFrustrated to SentimentFrustratedBg
        "confident" -> SentimentConfident to SentimentConfidentBg
        "hesitant" -> SentimentHesitant to SentimentHesitantBg
        "excited" -> SentimentExcited to SentimentExcitedBg
        else -> SentimentNeutral to SentimentNeutralBg
    }
}

private fun parseSummary(json: String): String {
    if (json.isBlank()) return ""
    return try { JSONObject(json).optString("summary", "") } catch (_: Exception) { "" }
}

private fun parseTags(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val arr = JSONObject(json).optJSONArray("tags") ?: return emptyList()
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) { emptyList() }
}

private fun parseTopics(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) { emptyList() }
}

private fun parseBehavioralTags(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) { emptyList() }
}
