package com.dc.murmur.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dc.murmur.data.local.dao.CoSpeakerInfo
import com.dc.murmur.data.local.entity.VoiceProfileEntity
import com.dc.murmur.data.repository.SpeakerMatch
import com.dc.murmur.ui.theme.DarkSurfaceCard
import com.dc.murmur.ui.theme.Teal40

@Composable
fun PersonCard(
    profile: VoiceProfileEntity,
    formattedDuration: String,
    formattedLastSeen: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    matchSuggestion: SpeakerMatch? = null,
    onSuggestionTap: (() -> Unit)? = null,
    coSpeakers: List<CoSpeakerInfo> = emptyList()
) {
    val displayName = profile.label ?: profile.voiceId.take(16)
    val initials = if (profile.label != null) {
        profile.label.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    } else "?"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (profile.label != null) Teal40.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (profile.label != null) Teal40
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    // Embedding quality badge
                    EmbeddingQualityBadge(profile.embeddingSampleCount)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${profile.interactionCount} interactions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Last seen $formattedLastSeen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Match suggestion chip for untagged profiles
                if (matchSuggestion != null) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { onSuggestionTap?.invoke() }
                    ) {
                        Text(
                            text = "Might be ${matchSuggestion.profile.label} (${(matchSuggestion.score * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Co-speaker context
                if (coSpeakers.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    val names = coSpeakers.mapNotNull { it.label }.take(3).joinToString(", ")
                    Text(
                        text = "Often heard with: $names",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmbeddingQualityBadge(sampleCount: Int) {
    if (sampleCount <= 0) return

    val (label, color) = when {
        sampleCount < 3 -> "Weak" to Color(0xFFFF9800) // orange
        sampleCount <= 10 -> "Good" to Color(0xFF42A5F5) // blue
        else -> "Strong" to Color(0xFF66BB6A) // green
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}
