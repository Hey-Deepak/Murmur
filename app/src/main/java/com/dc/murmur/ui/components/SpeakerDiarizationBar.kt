package com.dc.murmur.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dc.murmur.ui.theme.SpeakerColors

data class SpeakerSegmentUi(
    val label: String,
    val ratio: Float,
    val durationMs: Long,
    val turnCount: Int,
    val role: String? = null,
    val emotionalState: String? = null,
    val isIdentified: Boolean = false
)

/**
 * Compact inline badge showing speaker count with colored dots.
 */
@Composable
fun SpeakerCountBadge(
    speakerCount: Int,
    modifier: Modifier = Modifier
) {
    if (speakerCount <= 0) return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$speakerCount voice${if (speakerCount > 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Horizontal stacked bar showing speaking ratio per speaker.
 * Each segment is colored differently. Shows who spoke what fraction.
 */
@Composable
fun SpeakerRatioBar(
    speakers: List<SpeakerSegmentUi>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 6.dp
) {
    if (speakers.isEmpty()) return

    val totalRatio = speakers.sumOf { it.ratio.toDouble() }.toFloat().coerceAtLeast(0.01f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.matchParentSize()) {
            speakers.forEachIndexed { index, speaker ->
                val fraction = (speaker.ratio / totalRatio).coerceIn(0f, 1f)
                val color = SpeakerColors[index % SpeakerColors.size]
                Box(
                    modifier = Modifier
                        .weight(fraction.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}

/**
 * Speaker legend with colored dots and labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakerLegend(
    speakers: List<SpeakerSegmentUi>,
    modifier: Modifier = Modifier
) {
    if (speakers.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        speakers.forEachIndexed { index, speaker ->
            val color = SpeakerColors[index % SpeakerColors.size]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = speaker.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (speaker.isIdentified) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (speaker.isIdentified) FontWeight.Medium else FontWeight.Normal
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "${(speaker.ratio * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Full speaker diarization visualization: ratio bar + legend + optional details.
 */
@Composable
fun SpeakerDiarizationCard(
    speakers: List<SpeakerSegmentUi>,
    modifier: Modifier = Modifier,
    showDetails: Boolean = false
) {
    if (speakers.isEmpty()) return

    Column(modifier = modifier) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${speakers.size} speaker${if (speakers.size > 1) "s" else ""} detected",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(6.dp))

        // Ratio bar
        SpeakerRatioBar(speakers = speakers)

        Spacer(Modifier.height(6.dp))

        // Legend
        SpeakerLegend(speakers = speakers)

        // Detailed breakdown (expanded view)
        if (showDetails) {
            Spacer(Modifier.height(8.dp))
            speakers.forEachIndexed { index, speaker ->
                val color = SpeakerColors[index % SpeakerColors.size]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = speaker.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (speaker.isIdentified) color else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (speaker.isIdentified) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatSpeakerDuration(speaker.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${speaker.turnCount} turn${if (speaker.turnCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (speaker.role != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = color.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = speaker.role,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (speaker.emotionalState != null) {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = speaker.emotionalState,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSpeakerDuration(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    return if (m > 0) "${m}m ${s % 60}s" else "${s}s"
}
