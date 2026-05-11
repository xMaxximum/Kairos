package com.maxximum.kairos

import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.maxximum.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FullScreenReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        enableEdgeToEdge()

        val todoTitle = intent.getStringExtra("todo_title") ?: "Reminder"
        val todoId = intent.getIntExtra("todo_id", -1)

        setContent {
            KairosTheme {
                FullScreenReminderContent(
                    title = todoTitle,
                    onDismiss = { finishReminder(todoId) },
                    onMarkDone = { markAsDone(todoId) },
                    onSnoozePreset = { minutes -> postponeReminder(todoId, System.currentTimeMillis() + minutes * 60_000L) },
                    onSnoozeCustomTime = {
                        showDarkDateTimePicker(this@FullScreenReminderActivity) { selectedTime ->
                            postponeReminder(todoId, selectedTime)
                        }
                    }
                )
            }
        }
    }

    private fun finishReminder(todoId: Int) {
        if (todoId > 0) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(todoId)
        }
        finish()
    }

    private fun markAsDone(todoId: Int) {
        if (todoId <= 0) {
            finish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@FullScreenReminderActivity).todoDao()
            val todo = dao.getTodoById(todoId)
            if (todo != null) {
                val updated = todo.applyCompletionChange(markCompleted = true)
                // Auto-delete one-off tasks when completed
                if (todo.isOneOffTask && todo.recurrenceType() == RecurrenceType.NONE) {
                    if (updated.reminderTime != null) {
                        AlarmScheduler.cancel(this@FullScreenReminderActivity, updated)
                    }
                    dao.deleteTodo(updated)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.cancel(todoId)
                    withContext(Dispatchers.Main) {
                        ToastUtils.show(this@FullScreenReminderActivity, "Task auto-deleted")
                        finish()
                    }
                } else {
                    dao.updateTodo(updated)
                    if (updated.reminderTime != null && !updated.isCompleted) {
                        AlarmScheduler.schedule(this@FullScreenReminderActivity, updated)
                    } else {
                        AlarmScheduler.cancel(this@FullScreenReminderActivity, updated)
                    }
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.cancel(todoId)
                    withContext(Dispatchers.Main) {
                        ToastUtils.show(this@FullScreenReminderActivity, "Task completed")
                        finish()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    private fun postponeReminder(todoId: Int, time: Long) {
        if (todoId <= 0) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@FullScreenReminderActivity).todoDao()
            val todo = dao.getTodoById(todoId)
            if (todo != null) {
                val updated = todo.copy(reminderTime = time, isCompleted = false)
                dao.updateTodo(updated)
                AlarmScheduler.schedule(this@FullScreenReminderActivity, updated)
                val manager = getSystemService(NotificationManager::class.java)
                manager.cancel(todoId)
                withContext(Dispatchers.Main) {
                    ToastUtils.show(this@FullScreenReminderActivity, "Reminder snoozed")
                    finish()
                }
            } else {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }
}

@Composable
private fun FullScreenReminderContent(
    title: String,
    onDismiss: () -> Unit,
    onMarkDone: () -> Unit,
    onSnoozePreset: (Int) -> Unit,
    onSnoozeCustomTime: () -> Unit
) {
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                modifier = Modifier.height(110.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Reminder",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showSnoozeDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Snooze", maxLines = 1)
                }

                Button(
                    onClick = onMarkDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Mark as Done", maxLines = 1)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Dismiss", maxLines = 1)
                }
            }
        }
    }

    if (showSnoozeDialog) {
        AlertDialog(
            onDismissRequest = { showSnoozeDialog = false },
            title = { Text("Snooze reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Choose a quick snooze or set your own minutes.")
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quick options:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    showSnoozeDialog = false
                                    onSnoozePreset(5)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("5 min") }
                            Button(
                                onClick = {
                                    showSnoozeDialog = false
                                    onSnoozePreset(15)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("15 min") }
                            Button(
                                onClick = {
                                    showSnoozeDialog = false
                                    onSnoozePreset(60)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("1 hour") }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Custom minutes:", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { customMinutes = it.filter(Char::isDigit) },
                            label = { Text("Minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Or choose date/time:", style = MaterialTheme.typography.labelMedium)
                        TextButton(
                            onClick = {
                                showSnoozeDialog = false
                                onSnoozeCustomTime()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Custom date/time")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSnoozeDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minutes = customMinutes.toIntOrNull()
                        if (minutes != null && minutes > 0) {
                            showSnoozeDialog = false
                            onSnoozePreset(minutes)
                        }
                    },
                    enabled = customMinutes.toIntOrNull()?.let { it > 0 } == true
                ) {
                    Text("Apply")
                }
            }
        )
    }
}
