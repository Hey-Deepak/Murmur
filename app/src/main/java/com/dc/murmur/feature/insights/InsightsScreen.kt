package com.dc.murmur.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dc.murmur.ui.components.ActivityBreakdownChart
import com.dc.murmur.ui.components.InsightCard
import com.dc.murmur.ui.components.PredictionCard
import com.dc.murmur.ui.components.TimelineBlock
import com.dc.murmur.ui.theme.DarkSurfaceCard
import org.json.JSONArray
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = koinViewModel()
) {
    val viewMode by viewModel.viewMode.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val dailyInsight by viewModel.dailyInsight.collectAsState()
    val dailyActivities by viewModel.dailyActivities.collectAsState()
    val timeBreakdown by viewModel.timeBreakdown.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val weeklyInsights by viewModel.weeklyInsights.collectAsState()
    val weeklyTopTopics by viewModel.weeklyTopTopics.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayDateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // View mode tabs
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("daily" to "Daily", "weekly" to "Weekly", "transcripts" to "Transcripts").forEach { (mode, label) ->
                    FilterChip(
                        selected = viewMode == mode,
                        onClick = { viewModel.setViewMode(mode) },
                        label = { Text(label) }
                    )
                }
            }
        }

        when (viewMode) {
            "daily" -> {
                // Date selector
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val cal = Calendar.getInstance()
                            cal.time = dateFormat.parse(selectedDate)!!
                            cal.add(Calendar.DAY_OF_YEAR, -1)
                            viewModel.selectDate(dateFormat.format(cal.time))
                        }) {
                            Icon(Icons.Default.ChevronLeft, "Previous day")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = try {
                                    displayDateFormat.format(dateFormat.parse(selectedDate)!!)
                                } catch (_: Exception) { selectedDate },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(onClick = {
                            val cal = Calendar.getInstance()
                            cal.time = dateFormat.parse(selectedDate)!!
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                            viewModel.selectDate(dateFormat.format(cal.time))
                        }) {
                            Icon(Icons.Default.ChevronRight, "Next day")
                        }
                    }
                }

                // Daily insight highlight
                if (dailyInsight != null) {
                    item {
                        val insight = dailyInsight!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Day Summary",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "${insight.overallSentiment} ${"%.0f".format(insight.overallSentimentScore * 100)}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (insight.highlight != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = insight.highlight,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${viewModel.formatDuration(insight.totalRecordedMs)} recorded · ${insight.totalAnalyzedChunks} chunks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Generate insight button
                    item {
                        OutlinedButton(
                            onClick = { viewModel.generateDailyInsight(selectedDate) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Daily Insight")
                        }
                    }
                }

                // Activity breakdown
                if (timeBreakdown.isNotEmpty()) {
                    item { ActivityBreakdownChart(timeBreakdown) }
                }

                // Timeline
                if (dailyActivities.isNotEmpty()) {
                    item {
                        Text(
                            text = "Timeline",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(dailyActivities, key = { it.id }) { activity ->
                        TimelineBlock(activity = activity)
                    }
                }

                // Predictions
                if (predictions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Predictions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(predictions, key = { it.id }) { prediction ->
                        PredictionCard(
                            prediction = prediction,
                            onDismiss = { viewModel.dismissPrediction(prediction.id) }
                        )
                    }
                }

                // Day's transcriptions
                val dayTranscriptions = viewModel.filteredTranscriptions(selectedDate)
                if (dayTranscriptions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Analysis (${dayTranscriptions.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(dayTranscriptions, key = { it.id }) { t ->
                        InsightCard(
                            transcription = t,
                            formattedTime = viewModel.formatTimestamp(t.startTime),
                            formattedDuration = viewModel.formatDuration(t.durationMs)
                        )
                    }
                }

                if (dailyActivities.isEmpty() && dailyInsight == null && viewModel.filteredTranscriptions(selectedDate).isEmpty()) {
                    item {
                        Text(
                            text = "No data for this date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                }
            }

            "weekly" -> {
                // Weekly overview
                if (weeklyInsights.isNotEmpty()) {
                    item {
                        Text(
                            text = "Past 7 Days",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(weeklyInsights, key = { it.id }) { insight ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = insight.date,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = insight.overallSentiment,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (insight.highlight != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = insight.highlight,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Top topics
                                val topics = try {
                                    val arr = JSONArray(insight.topTopics)
                                    (0 until arr.length()).map { arr.getString(it) }
                                } catch (_: Exception) { emptyList() }
                                if (topics.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        topics.take(5).forEach { topic ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = topic,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Top topics this week
                if (weeklyTopTopics.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Top Topics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            weeklyTopTopics.forEach { topic ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${topic.name} (${topic.totalMentions})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (weeklyInsights.isEmpty() && weeklyTopTopics.isEmpty()) {
                    item {
                        Text(
                            text = "No weekly data yet. Use the app for a few days to see trends.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                }
            }

            "transcripts" -> {
                // Search
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQuery(it) },
                        placeholder = { Text("Search transcriptions...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                items(searchResults, key = { it.id }) { t ->
                    InsightCard(
                        transcription = t,
                        formattedTime = viewModel.formatTimestamp(t.startTime),
                        formattedDuration = viewModel.formatDuration(t.durationMs)
                    )
                }

                if (searchResults.isEmpty()) {
                    item {
                        Text(
                            text = if (searchQuery.isBlank()) "No transcriptions yet" else "No results found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                }
            }
        }
    }
}
