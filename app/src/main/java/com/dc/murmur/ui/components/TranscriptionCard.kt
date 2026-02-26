package com.dc.murmur.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dc.murmur.data.local.entity.TranscriptionEntity
import org.json.JSONArray

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptionCard(
    transcription: TranscriptionEntity,
    modifier: Modifier = Modifier
) {
    val keywords = parseKeywords(transcription.keywords)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Transcript text (truncated)
            Text(
                text = transcription.text.ifBlank { "(No speech detected)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Sentiment chip
            val (sentimentColor, sentimentBg) = when (transcription.sentiment) {
                "positive" -> Pair(Color(0xFF2E7D32), Color(0xFFE8F5E9))
                "negative" -> Pair(Color(0xFFC62828), Color(0xFFFFEBEE))
                else -> Pair(Color(0xFF616161), Color(0xFFF5F5F5))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "${transcription.sentiment} (${
                                "%.0f".format(transcription.sentimentScore * 100)
                            }%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = sentimentBg,
                        labelColor = sentimentColor
                    )
                )

                // Keyword chips
                keywords.take(5).forEach { keyword ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = keyword,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun parseKeywords(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}
