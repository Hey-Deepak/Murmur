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
import androidx.compose.ui.text.style.TextAlign
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
fun ActivityTrendChart(
    weeklyData: Map<String, List<ActivityTimeBreakdown>>,
    modifier: Modifier = Modifier
) {
    if (weeklyData.isEmpty()) return

    val allActivityTypes = weeklyData.values.flatMap { day -> day.map { it.activityType } }.distinct()
    val maxDayMs = weeklyData.values.maxOfOrNull { day -> day.sumOf { it.totalMs } } ?: 1L

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Activity Trends (7 days)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(12.dp))

            // Stacked columns
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyData.entries.sortedBy { it.key }.forEach { (date, breakdown) ->
                    val dayTotal = breakdown.sumOf { it.totalMs }.coerceAtLeast(1)
                    val barHeight = (dayTotal.toFloat() / maxDayMs * 100).dp

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(32.dp)
                    ) {
                        // Stacked bar
                        Column(
                            modifier = Modifier
                                .width(20.dp)
                                .height(barHeight.coerceAtMost(100.dp))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        ) {
                            breakdown.sortedByDescending { it.totalMs }.forEach { item ->
                                val fraction = item.totalMs.toFloat() / dayTotal
                                if (fraction > 0.05f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(fraction)
                                            .background(activityTypeColor(item.activityType))
                                    )
                                }
                            }
                        }

                        // Date label
                        Text(
                            text = date.takeLast(2),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                allActivityTypes.take(4).forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(activityTypeColor(type))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = type.replace("_", " ").take(8),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun activityTypeColor(type: String): Color {
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
