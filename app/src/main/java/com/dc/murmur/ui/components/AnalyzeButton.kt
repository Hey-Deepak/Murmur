package com.dc.murmur.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dc.murmur.ai.AnalysisStatus
import com.dc.murmur.ai.AnalysisUiState

@Composable
fun AnalyzeNowCard(
    state: AnalysisUiState,
    unprocessedCount: Int,
    onAnalyzeClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state.status) {
                AnalysisStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                AnalysisStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "AI Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            AnimatedContent(targetState = state.status, label = "analysis_status") { status ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (status) {
                        AnalysisStatus.IDLE -> {
                            if (unprocessedCount > 0) {
                                Text(
                                    text = "$unprocessedCount recording${if (unprocessedCount != 1) "s" else ""} waiting for analysis",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onAnalyzeClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Filled.Analytics,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Analyze Now")
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "All recordings analyzed",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        AnalysisStatus.DOWNLOADING_MODELS -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Downloading AI models...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        AnalysisStatus.RUNNING -> {
                            Text(
                                text = "Analyzing ${state.progress} / ${state.total}...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (state.total > 0) {
                                LinearProgressIndicator(
                                    progress = { state.progress.toFloat() / state.total },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = onCancelClick,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Cancel")
                            }
                        }

                        AnalysisStatus.COMPLETED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Analysis complete! Insights are ready.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (unprocessedCount > 0) {
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = onAnalyzeClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Filled.Analytics,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Analyze $unprocessedCount New")
                                }
                            }
                        }

                        AnalysisStatus.ERROR -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = state.errorMessage ?: "Analysis failed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Button(
                                onClick = onAnalyzeClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}
