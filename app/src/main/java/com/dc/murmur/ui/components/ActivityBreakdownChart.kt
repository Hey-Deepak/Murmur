package com.dc.murmur.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dc.murmur.data.local.dao.ActivityTimeBreakdown
import com.dc.murmur.ui.theme.ActivityCasualChat
import com.dc.murmur.ui.theme.ActivityCommuting
import com.dc.murmur.ui.theme.ActivityEating
import com.dc.murmur.ui.theme.ActivityIdle
import com.dc.murmur.ui.theme.ActivityMeeting
import com.dc.murmur.ui.theme.ActivityPhoneCall
import com.dc.murmur.ui.theme.ActivitySolo
import com.dc.murmur.ui.theme.ActivityWorking
import com.dc.murmur.ui.theme.DarkSurfaceCard

@Composable
fun ActivityBreakdownChart(
    breakdown: List<ActivityTimeBreakdown>,
    modifier: Modifier = Modifier
) {
    if (breakdown.isEmpty()) return

    val totalMs = breakdown.sumOf { it.totalMs }.coerceAtLeast(1)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Activity Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(12.dp))

            // Stacked bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                breakdown.forEach { item ->
                    val fraction = item.totalMs.toFloat() / totalMs
                    if (fraction > 0.01f) {
                        Box(
                            modifier = Modifier
                                .weight(fraction)
                                .height(24.dp)
                                .background(activityColor(item.activityType))
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Legend
            breakdown.forEach { item ->
                val pct = (item.totalMs.toFloat() / totalMs * 100).toInt()
                val minutes = item.totalMs / 60000
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(activityColor(item.activityType))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.activityType.replace("_", " ")
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${minutes}m ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun activityColor(type: String): Color {
    return when (type) {
        "eating" -> ActivityEating
        "meeting" -> ActivityMeeting
        "working" -> ActivityWorking
        "commuting" -> ActivityCommuting
        "idle" -> ActivityIdle
        "phone_call" -> ActivityPhoneCall
        "casual_chat" -> ActivityCasualChat
        "solo" -> ActivitySolo
        else -> ActivityIdle
    }
}
