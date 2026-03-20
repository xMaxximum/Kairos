package com.maxximum.kairos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MARK_DONE = "com.maxximum.kairos.ACTION_MARK_DONE"
        const val ACTION_POSTPONE = "com.maxximum.kairos.ACTION_POSTPONE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getIntExtra("todo_id", -1)
        val title = intent.getStringExtra("todo_title") ?: "Reminder"
        val isHighPriority = intent.getBooleanExtra("is_high_priority", false)
        val isFullScreen = intent.getBooleanExtra("is_full_screen", false)

        when (intent.action) {
            ACTION_MARK_DONE -> {
                markAsDone(context, todoId)
                val taskTitle = intent.getStringExtra("todo_title")?.takeIf { it.isNotBlank() }
                ToastUtils.show(context, if (taskTitle != null) "Completed: $taskTitle" else "Task completed")
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(todoId)
            }
            ACTION_POSTPONE -> {
                // Open the dedicated floating PostponeActivity
                val postponeIntent = Intent(context, PostponeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    putExtra("todo_id", todoId)
                }
                context.startActivity(postponeIntent)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(todoId)
            }
            else -> {
                showNotification(context, todoId, title, isHighPriority, isFullScreen)
            }
        }
    }

    private fun markAsDone(context: Context, todoId: Int) {
        val dao = AppDatabase.getDatabase(context).todoDao()
        CoroutineScope(Dispatchers.IO).launch {
            dao.getTodoById(todoId)?.let {
                val updated = it.applyCompletionChange(markCompleted = true)
                dao.updateTodo(updated)
                if (updated.reminderTime != null && !updated.isCompleted) {
                    AlarmScheduler.schedule(context, updated)
                } else {
                    AlarmScheduler.cancel(context, updated)
                }
            }
        }
    }

    private fun showNotification(
        context: Context,
        todoId: Int,
        title: String,
        isHighPriority: Boolean,
        isFullScreen: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val effectiveHighPriority = isHighPriority || isFullScreen
        val channelId = if (effectiveHighPriority) "todo_reminders_high" else "todo_reminders_low"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (effectiveHighPriority) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, "Todo Reminders", importance).apply {
                description = "Notifications for todo tasks"
                enableVibration(effectiveHighPriority)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("todo_id", todoId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, todoId, activityIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra("todo_id", todoId)
            putExtra("todo_title", title)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context, todoId + 100, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val postponeIntent = Intent(context, PostponeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("todo_id", todoId)
        }
        val postponePendingIntent = PendingIntent.getActivity(
            context, todoId + 200, postponeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (effectiveHighPriority) "High Priority Task" else "Task Reminder")
            .setContentText(title)
            .setPriority(if (effectiveHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(if (effectiveHighPriority) longArrayOf(0, 300, 250, 300) else longArrayOf(0L))
            .addAction(android.R.drawable.ic_menu_edit, "Postpone", postponePendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Done", donePendingIntent)

        if (isFullScreen && canUseFullScreenIntentPermission(context)) {
            val fullScreenIntent = Intent(context, FullScreenReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra("todo_title", title)
                putExtra("todo_id", todoId)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, todoId + 1000, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        notificationManager.notify(todoId, builder.build())
    }
}
