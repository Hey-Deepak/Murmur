package com.dc.murmur.core.util

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dc.murmur.MainActivity
import com.dc.murmur.R
import com.dc.murmur.core.constants.AppConstants

class NotificationUtil(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun launchIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun buildRecordingNotification(elapsedSeconds: Long = 0): Notification {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        val timer = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        return NotificationCompat.Builder(context, AppConstants.RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Murmur is recording")
            .setContentText("Recording in progress · $timer")
            .setContentIntent(launchIntent())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    fun updateRecordingNotification(elapsedSeconds: Long) {
        nm.notify(AppConstants.RECORDING_NOTIFICATION_ID, buildRecordingNotification(elapsedSeconds))
    }

    fun showAnalysisProgress(current: Int, total: Int) {
        val notification = NotificationCompat.Builder(context, AppConstants.ANALYSIS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Analyzing recordings")
            .setContentText("Processing $current / $total chunks")
            .setContentIntent(launchIntent())
            .setProgress(total, current, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        nm.notify(AppConstants.ANALYSIS_NOTIFICATION_ID, notification)
    }

    fun showInsightsReady() {
        val notification = NotificationCompat.Builder(context, AppConstants.INSIGHTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Your daily insights are ready")
            .setContentText("Tap to see today's summary")
            .setContentIntent(launchIntent())
            .setAutoCancel(true)
            .build()
        nm.notify(AppConstants.INSIGHTS_NOTIFICATION_ID, notification)
    }

    fun showAnalysisSkipped(reason: String) {
        val notification = NotificationCompat.Builder(context, AppConstants.ANALYSIS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Analysis skipped")
            .setContentText(reason)
            .setContentIntent(launchIntent())
            .setAutoCancel(true)
            .build()
        nm.notify(AppConstants.ANALYSIS_NOTIFICATION_ID, notification)
    }

    fun cancelAnalysis() = nm.cancel(AppConstants.ANALYSIS_NOTIFICATION_ID)

    fun showPrediction(message: String) {
        val notification = NotificationCompat.Builder(context, AppConstants.PREDICTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Prediction")
            .setContentText(message)
            .setContentIntent(launchIntent())
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()
        nm.notify(AppConstants.PREDICTION_NOTIFICATION_ID, notification)
    }
}
