package com.maxximum.kairos

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.maxximum.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.launch

class PostponeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val todoId = intent.getIntExtra("todo_id", -1)
        if (todoId == -1) {
            finish()
            return
        }

        setContent {
            KairosTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f))
                        .clickable { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    PostponeDialogContent(
                        onDismiss = { finish() },
                        onPostpone = { time -> postponeTask(todoId, time) },
                        onCustom = {
                            showDarkDateTimePicker(this@PostponeActivity) { selectedTime ->
                                postponeTask(todoId, selectedTime)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun postponeTask(id: Int, time: Long) {
        val viewModel = TodoViewModel(application)
        lifecycleScope.launch {
            viewModel.getTodoById(id)?.let {
                val updated = it.copy(reminderTime = time)
                viewModel.update(updated)
                AlarmScheduler.schedule(this@PostponeActivity, updated)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(id)
                ToastUtils.show(this@PostponeActivity, "Reminder postponed")
            } ?: run {
                ToastUtils.show(this@PostponeActivity, "Task not found")
            }
            finish()
        }
    }
}

@Composable
fun PostponeDialogContent(
    onDismiss: () -> Unit,
    onPostpone: (Long) -> Unit,
    onCustom: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(0.9f)
            .clickable(enabled = false) { },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Postpone Task", style = MaterialTheme.typography.headlineSmall)
            Text("When should we remind you again?", style = MaterialTheme.typography.bodyMedium)
            
            Button(onClick = { onPostpone(System.currentTimeMillis() + 15 * 60 * 1000) }, modifier = Modifier.fillMaxWidth()) { Text("15 Minutes") }
            Button(onClick = { onPostpone(System.currentTimeMillis() + 60 * 60 * 1000) }, modifier = Modifier.fillMaxWidth()) { Text("1 Hour") }
            Button(onClick = { onPostpone(System.currentTimeMillis() + 24 * 60 * 60 * 1000) }, modifier = Modifier.fillMaxWidth()) { Text("Tomorrow") }
            OutlinedButton(onClick = onCustom, modifier = Modifier.fillMaxWidth()) { Text("Custom time") }
            
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}
