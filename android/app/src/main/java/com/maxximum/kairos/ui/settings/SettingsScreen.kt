package com.maxximum.kairos.ui.settings

import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.maxximum.kairos.app.AuthUiState
import com.maxximum.kairos.app.SyncUiState
import com.maxximum.kairos.data.local.SyncConflict
import com.maxximum.kairos.data.sync.TaskSyncSnapshots
import com.maxximum.kairos.notifications.NotificationPreferences
import com.maxximum.kairos.notifications.OverdueNotificationWorker
import com.maxximum.kairos.ui.auth.AccountSettingsSection
import com.maxximum.kairos.ui.components.DebugBuildBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    authState: AuthUiState,
    onLogin: (String, String) -> Unit = { _, _ -> },
    onRegister: (String, String) -> Unit = { _, _ -> },
    onLogout: () -> Unit = {},
    onServerChanged: (String) -> Unit = {},
    onExportBackup: (Uri) -> Unit = {},
    syncState: SyncUiState = SyncUiState(),
    onSyncNow: () -> Unit = {},
    syncConflicts: List<SyncConflict> = emptyList(),
    onKeepMineConflict: (SyncConflict) -> Unit = {},
    onUseServerConflict: (SyncConflict) -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isDebugBuild = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    var intervalHours by remember {
        mutableIntStateOf(NotificationPreferences.getOverdueIntervalHours(context))
    }
    val options = listOf(0, 1, 2, 3, 4, 6, 8, 12, 24)
    val optionRows = options.chunked(3)
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let(onExportBackup) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Settings")
                        if (isDebugBuild) {
                            DebugBuildBadge()
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = {
                        NotificationPreferences.setOverdueIntervalHours(context, intervalHours)
                        OverdueNotificationWorker.schedule(context, intervalHours)
                        onBack()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccountSettingsSection(
                state = authState,
                onLogin = onLogin,
                onRegister = onRegister,
                onLogout = onLogout,
                onServerChanged = onServerChanged
            )
            HorizontalDivider()
            Text(
                "Sync",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                syncStatusText(authState, syncState),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onSyncNow,
                enabled = authState.isAuthenticated && !syncState.isSyncing && !authState.isLoading
            ) {
                if (syncState.isSyncing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp)
                    )
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                }
                Text("Sync now")
            }
            if (syncConflicts.isNotEmpty()) {
                Text(
                    "Conflict inbox",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "These tasks changed on more than one device. Pick the version to keep before they sync again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                syncConflicts.forEach { conflict ->
                    val localSnapshot = remember(conflict.localSnapshotJson) {
                        TaskSyncSnapshots.decode(conflict.localSnapshotJson)
                    }
                    val serverSnapshot = remember(conflict.serverSnapshotJson) {
                        TaskSyncSnapshots.decode(conflict.serverSnapshotJson)
                    }
                    val fields = remember(conflict.conflictedFields) {
                        conflict.conflictedFields.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    }
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                localSnapshot?.title ?: serverSnapshot?.title ?: "Task conflict",
                                style = MaterialTheme.typography.titleSmall
                            )
                            fields.take(5).forEach { field ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        TaskSyncSnapshots.displayName(field),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        "Mine: ${TaskSyncSnapshots.displayValue(localSnapshot, field)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Server: ${TaskSyncSnapshots.displayValue(serverSnapshot, field)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onKeepMineConflict(conflict) }) {
                                    Text("Keep Mine")
                                }
                                OutlinedButton(onClick = { onUseServerConflict(conflict) }) {
                                    Text("Use Server")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        val text = listOfNotNull(
                                            serverSnapshot?.title,
                                            serverSnapshot?.description?.takeIf { it.isNotBlank() }
                                        ).joinToString("\n\n")
                                        clipboard.setText(AnnotatedString(text))
                                    }
                                ) {
                                    Text("Copy Server Text")
                                }
                                TextButton(onClick = {}) {
                                    Text("Resolve Later")
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Text(
                "Data safety",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Export a local JSON backup before trying sync or moving devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    backupLauncher.launch(defaultBackupFileName())
                }
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
                Text("Export backup")
            }
            HorizontalDivider()
            Text(
                "Remind me of pending tasks every:",
                style = MaterialTheme.typography.bodyLarge
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                optionRows.forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { h ->
                            FilterChip(
                                selected = intervalHours == h,
                                onClick = { intervalHours = h },
                                label = {
                                    Text(
                                        when (h) {
                                            0 -> "Off"
                                            1 -> "1 hr"
                                            else -> "$h hrs"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
            Text(
                if (intervalHours == 0)
                    "Periodic notifications are disabled."
                else
                    "You'll be notified every $intervalHours hour${if (intervalHours > 1) "s" else ""} when you have pending tasks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            Text(
                "This setting only affects pending tasks and does not change custom reminder times on individual tasks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun defaultBackupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "kairos-backup-$stamp.json"
}

private fun syncStatusText(authState: AuthUiState, syncState: SyncUiState): String {
    if (!authState.isAuthenticated) return "Sign in to sync with your server. Offline mode still works."
    if (syncState.message != null) return syncState.message
    val lastSync = syncState.lastSyncedAtMillis ?: return "Ready to upload local tasks and import new server tasks."
    val stamp = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(lastSync))
    return "Last synced $stamp"
}
