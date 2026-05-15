package com.maxximum.kairos.ui.add

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maxximum.kairos.app.TodoViewModel
import com.maxximum.kairos.app.UndoEvent
import com.maxximum.kairos.domain.model.*
import com.maxximum.kairos.domain.logic.*
import com.maxximum.kairos.notifications.AlarmScheduler
import com.maxximum.kairos.platform.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.maxximum.kairos.ui.components.DebugBuildBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTodoScreen(
    isActive: Boolean,
    onSettings: () -> Unit,
    onSave: (Todo) -> Unit,
    onViewAll: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf<Long?>(null) }
    var isHighPriority by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var isOneOffTask by remember { mutableStateOf(false) }
    var showReminderPopup by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDebugBuild = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Kairos", fontWeight = FontWeight.Bold)
                        if (isDebugBuild) {
                            DebugBuildBadge()
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onViewAll) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "View All")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.imePadding().navigationBarsPadding()
            ) {
                Column {
                    if (showReminderPopup) {
                        ReminderQuickActions(
                            onTimeSelected = { 
                                reminderTime = it
                                showReminderPopup = false
                            },
                            onCustomClick = {
                                showReminderPopup = false
                                showDarkDateTimePicker(context) {
                                    reminderTime = it
                                    ToastUtils.show(context, "Reminder set")
                                }
                            }
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { showReminderPopup = !showReminderPopup }) {
                            Icon(
                                Icons.Default.NotificationsActive, 
                                contentDescription = "Reminder",
                                tint = if (reminderTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = isHighPriority,
                                onClick = { isHighPriority = !isHighPriority },
                                label = { Text("High") },
                                leadingIcon = {
                                    Icon(Icons.Default.PriorityHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            FilterChip(
                                selected = isOneOffTask,
                                onClick = { isOneOffTask = !isOneOffTask },
                                label = { Text("Auto-delete") },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            if (reminderTime != null) {
                                FilterChip(
                                    selected = isFullScreen,
                                    onClick = { isFullScreen = !isFullScreen },
                                    label = { Text("Full-screen") },
                                    leadingIcon = {
                                        Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                        
                        FilledIconButton(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onSave(
                                        Todo(
                                            title = title,
                                            description = description,
                                            reminderTime = reminderTime,
                                            isHighPriority = isHighPriority,
                                            isFullScreenReminder = isFullScreen,
                                            isOneOffTask = isOneOffTask
                                        )
                                    )
                                    title = ""
                                    description = ""
                                    reminderTime = null
                                    isHighPriority = false
                                    isFullScreen = false
                                    isOneOffTask = false
                                    ToastUtils.show(context, "Task Saved!")
                                }
                            },
                            enabled = title.isNotBlank()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("Title", fontSize = 24.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
            
            TextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add description...", fontSize = 16.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
            
            reminderTime?.let {
                val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(it))
                AssistChip(
                    onClick = {
                        reminderTime = null
                        isHighPriority = false
                    },
                    label = { Text("Reminder: $date${if (isHighPriority) " • High" else " • Low"}") },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

        }
    }
}

@Composable
fun ReminderQuickActions(onTimeSelected: (Long) -> Unit, onCustomClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Set Reminder", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                QuickActionButton("15m") { onTimeSelected(System.currentTimeMillis() + 15 * 60 * 1000) }
                QuickActionButton("1h") { onTimeSelected(System.currentTimeMillis() + 60 * 60 * 1000) }
                QuickActionButton("Tonight") { 
                    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0) }
                    onTimeSelected(cal.timeInMillis)
                }
                QuickActionButton("Tomorrow") { 
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }
                    onTimeSelected(cal.timeInMillis)
                }
                IconButton(onClick = onCustomClick) { Icon(Icons.Default.EditCalendar, null) }
            }
        }
    }
}

@Composable
fun QuickActionButton(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) })
}
