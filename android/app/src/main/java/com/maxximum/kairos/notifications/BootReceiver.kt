package com.maxximum.kairos.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maxximum.kairos.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val database = AppDatabase.getDatabase(context)
            val dao = database.todoDao()
            
            CoroutineScope(Dispatchers.IO).launch {
                val todos = dao.getAllTodos().first()
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val now = System.currentTimeMillis()
                
                todos.forEach { todo ->
                    todo.reminderTime?.let { time ->
                        if (todo.isArchived || todo.isCompleted) return@let

                        if (time > now) {
                            val reminderIntent = Intent(context, ReminderReceiver::class.java).apply {
                                putExtra("todo_id", todo.id)
                                putExtra("todo_title", todo.title)
                                putExtra("is_high_priority", todo.isHighPriority)
                                putExtra("is_full_screen", todo.isFullScreenReminder)
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context, todo.id, reminderIntent, 
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent)
                        } else {
                            val overdueIntent = Intent(context, ReminderReceiver::class.java).apply {
                                putExtra("todo_id", todo.id)
                                putExtra("todo_title", todo.title)
                                putExtra("is_high_priority", todo.isHighPriority)
                                putExtra("is_full_screen", todo.isFullScreenReminder)
                            }
                            context.sendBroadcast(overdueIntent)
                        }
                    }
                }
            }
        }
    }
}

