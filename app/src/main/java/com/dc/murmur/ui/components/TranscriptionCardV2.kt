package com.dc.murmur.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Timer
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
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptionCardV2(
    transcription: TranscriptionWithChunk,
    formattedTime: String,
    formattedDuration: String,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val summary = parseSummaryV2(transcription.keywords)
    val tags = parseTagsV2(transcription.keywords)
    val isClaudePowered = transcription.modelUsed.contains("claude-code")
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
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Transcript body (expandable, text-first)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(
                    text = transcription.text.ifBlank { "(No speech detected)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (transcription.text.length > 150) {
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

            // Metadata row: time, duration, filename, delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        transcription.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Bottom row: sentiment badge + source badge + tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Sentiment badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = sentimentBg
                ) {
                    Text(
                        text = "${transcription.sentiment} ${"%.0f".format(transcription.sentimentScore * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = sentimentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Source badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isClaudePowered) ClaudePurpleBg
                           else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isClaudePowered) "Claude" else "On-device",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isClaudePowered) ClaudePurple
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Tags
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun parseSummaryV2(json: String): String {
    if (json.isBlank()) return ""
    return try {
        JSONObject(json).optString("summary", "")
    } catch (_: Exception) {
        ""
    }
}

private fun parseTagsV2(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("tags") ?: return emptyList()
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}
