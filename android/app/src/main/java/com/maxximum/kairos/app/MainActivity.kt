package com.maxximum.kairos.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maxximum.kairos.notifications.NotificationPreferences
import com.maxximum.kairos.notifications.OverdueNotificationWorker
import com.maxximum.kairos.ui.navigation.TodoNavHost
import com.maxximum.kairos.ui.theme.KairosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KairosTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val context = LocalContext.current
                    val viewModel: TodoViewModel = viewModel()
                    val authViewModel: AuthViewModel = viewModel()
                    val authState by authViewModel.uiState.collectAsState()
                    val todoIdFromIntent = intent.getIntExtra("todo_id", -1)
                    val permissionsToRequest = mutableListOf<String>()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        if (permissionsToRequest.isNotEmpty()) {
                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        }
                        checkExactAlarmPermission(context)
                        val hours = NotificationPreferences.getOverdueIntervalHours(context)
                        OverdueNotificationWorker.schedule(context, hours)
                    }

                    TodoNavHost(
                        viewModel = viewModel,
                        initialTodoId = todoIdFromIntent,
                        authState = authState,
                        onLogin = authViewModel::login,
                        onRegister = authViewModel::register,
                        onLogout = authViewModel::logout,
                        onServerChanged = authViewModel::setApiBaseUrl
                    )
                }
            }
        }
    }

    private fun checkExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
    }
}
