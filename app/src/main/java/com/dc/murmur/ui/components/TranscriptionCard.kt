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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dc.murmur.data.local.entity.TranscriptionEntity
import com.dc.murmur.ui.theme.ClaudePurple
import com.dc.murmur.ui.theme.ClaudePurpleBg
import com.dc.murmur.ui.theme.DarkSurfaceCard
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
    modifier: Modifier = Modifier
) {
    val summary = parseSummary(transcription.keywords)
    val tags = parseTags(transcription.keywords)
    val isClaudePowered = transcription.modelUsed.contains("claude-code")
    var expanded by remember { mutableStateOf(false) }

    val (sentimentColor, sentimentBg) = when (transcription.sentiment) {
        "positive" -> Pair(SentimentPositive, SentimentPositiveBg)
        "negative" -> Pair(SentimentNegative, SentimentNegativeBg)
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

                // Tags display
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

                // Conversation Analysis section
                if (summary.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
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

/** Extracts tags array from keywords JSON. Returns empty list if missing or invalid. */
private fun parseTags(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("tags") ?: return emptyList()
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}
