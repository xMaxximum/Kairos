package com.maxximum.kairos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class OverdueNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(context).todoDao()
        val todos = dao.getAllTodos().first()
        val now = System.currentTimeMillis()

        val overdueCount = todos.count { todo ->
            !todo.isCompleted && !todo.isArchived &&
                todo.reminderTime != null && todo.reminderTime < now
        }

        val pendingCount = todos.count { todo ->
            !todo.isCompleted && !todo.isArchived
        }

        if (pendingCount > 0) {
            showOverdueNotification(context, overdueCount, pendingCount)
        }

        return Result.success()
    }

    private fun showOverdueNotification(context: Context, overdueCount: Int, pendingCount: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "overdue_reminder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pending Task Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic reminders for pending and overdue tasks"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (overdueCount > 0)
            "\u26a0\ufe0f $overdueCount overdue task${if (overdueCount > 1) "s" else ""}"
        else
            "You have $pendingCount pending task${if (pendingCount > 1) "s" else ""}"

        val body = buildString {
            if (overdueCount > 0) append("$overdueCount overdue  \u2022  ")
            append("$pendingCount total pending")
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val NOTIFICATION_ID = 9999
        const val WORK_NAME = "overdue_reminder_work"

        fun schedule(context: Context, intervalHours: Int) {
            cancel(context)
            if (intervalHours <= 0) return

            val request = PeriodicWorkRequestBuilder<OverdueNotificationWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
