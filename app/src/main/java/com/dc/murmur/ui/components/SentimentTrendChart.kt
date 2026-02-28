package com.dc.murmur.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dc.murmur.ui.theme.DarkSurfaceCard
import com.dc.murmur.ui.theme.SentimentPositive
import com.dc.murmur.ui.theme.Teal40

@Composable
fun SentimentTrendChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier
) {
    if (data.size < 2) return

    val lineColor = Teal40
    val avgScore = data.map { it.second }.average().toFloat()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Sentiment Trend (30 days)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Avg: ${"%.0f".format(avgScore * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val width = size.width
                val height = size.height
                val points = data.mapIndexed { index, (_, score) ->
                    val x = if (data.size > 1) index.toFloat() / (data.size - 1) * width else width / 2
                    val y = height - (score * height)
                    Offset(x, y)
                }

                // Draw line
                if (points.size >= 2) {
                    val path = Path()
                    path.moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        path.lineTo(points[i].x, points[i].y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw dots
                    points.forEach { point ->
                        drawCircle(
                            color = lineColor,
                            radius = 3.dp.toPx(),
                            center = point
                        )
                    }
                }

                // Draw average line
                val avgY = height - (avgScore * height)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.4f),
                    start = Offset(0f, avgY),
                    end = Offset(width, avgY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            Spacer(Modifier.height(4.dp))

            // Date range labels
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = data.first().first.takeLast(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = data.last().first.takeLast(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
