package com.maxximum.kairos.ui.detail

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.maxximum.kairos.ui.components.AttachmentItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailScreen(todoId: Int, viewModel: TodoViewModel, onBack: () -> Unit) {
    val todo by viewModel.observeTodoById(todoId).collectAsState(initial = null)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = remember(todoId) { androidx.compose.foundation.ScrollState(0) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDailyTimeDialog by remember { mutableStateOf(false) }
    var showWeeklyDialog by remember { mutableStateOf(false) }
    var selectedWeeklyDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) }
    var titleDraft by remember { mutableStateOf("") }
    var descriptionDraft by remember { mutableStateOf("") }
    var canUseFullScreenIntent by remember { mutableStateOf(canUseFullScreenIntentPermission(context)) }

    BackHandler(onBack = onBack)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        todo?.let { currentTodo ->
            val updatedAttachments = currentTodo.attachments + uris.map { it.toString() }
            val updatedTodo = currentTodo.copy(attachments = updatedAttachments)
            viewModel.update(updatedTodo)
            if (uris.isNotEmpty()) {
                ToastUtils.show(context, "${uris.size} attachment(s) added")
            }
        }
    }

    LaunchedEffect(todo?.id) {
        scrollState.scrollTo(0)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        titleDraft = todo?.title.orEmpty()
        descriptionDraft = todo?.description.orEmpty()
        canUseFullScreenIntent = canUseFullScreenIntentPermission(context)
        selectedWeeklyDay = (todo?.reminderTime?.let {
            Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_WEEK)
        } ?: Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    }

    LaunchedEffect(titleDraft, todo?.id) {
        val currentTodo = todo ?: return@LaunchedEffect
        if (titleDraft == currentTodo.title) return@LaunchedEffect
        delay(450)
        val latest = todo ?: return@LaunchedEffect
        if (titleDraft != latest.title && titleDraft.isNotBlank()) {
            val updated = latest.copy(title = titleDraft)
            viewModel.update(updated)
            ToastUtils.show(context, "Title updated")
        }
    }

    LaunchedEffect(descriptionDraft, todo?.id) {
        val currentTodo = todo ?: return@LaunchedEffect
        if (descriptionDraft == currentTodo.description) return@LaunchedEffect
        delay(450)
        val latest = todo ?: return@LaunchedEffect
        if (descriptionDraft != latest.description) {
            val updated = latest.copy(description = descriptionDraft)
            viewModel.update(updated)
            ToastUtils.show(context, "Description saved")
        }
    }

    todo?.let { currentTodo ->
        Scaffold(
            bottomBar = {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                    BottomAppBar(modifier = Modifier.navigationBarsPadding()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            "Details",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showTimePickerDialog = true }) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Set reminder")
                        }
                        IconButton(onClick = {
                            viewModel.toggleComplete(currentTodo, !currentTodo.isCompleted) { result ->
                                ToastUtils.show(context, result.message)
                                if (result.deleted) onBack()
                            }
                        }) {
                            Icon(
                                if (currentTodo.isCompleted) Icons.Default.RadioButtonUnchecked else Icons.Default.CheckCircle,
                                contentDescription = if (currentTodo.isCompleted) "Mark pending" else "Mark complete"
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val now = System.currentTimeMillis()
            val scheduledTime = currentTodo.reminderTime
            val scheduledText = scheduledTime?.let {
                SimpleDateFormat("EEEE, MMM dd yyyy, HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "Not scheduled"
            val isOverdueReminder = scheduledTime != null && !currentTodo.isCompleted && scheduledTime < now
            val addedDate = SimpleDateFormat("EEEE, MMM dd yyyy, HH:mm", Locale.getDefault()).format(Date(currentTodo.timestamp))
            val switchColors = detailSwitchColors()

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp, max = 180.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                            }
                        },
                    label = { Text("Title") },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = descriptionDraft,
                    onValueChange = { descriptionDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    minLines = 3
                )

                DetailSectionLabel("Actions")
                Button(
                    onClick = {
                        viewModel.toggleComplete(currentTodo, !currentTodo.isCompleted) { result ->
                            ToastUtils.show(context, result.message)
                            if (result.deleted) onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (currentTodo.isCompleted) Icons.Default.RadioButtonUnchecked else Icons.Default.CheckCircle,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (currentTodo.isCompleted) "Mark pending" else "Complete task", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        val updated = currentTodo.copy(isArchived = !currentTodo.isArchived)
                        viewModel.update(updated)
                        ToastUtils.show(context, if (updated.isArchived) "Archived" else "Unarchived")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (currentTodo.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (currentTodo.isArchived) "Unarchive task" else "Archive task", maxLines = 1)
                }

                DetailSectionLabel("Schedule")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOverdueReminder) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (isOverdueReminder) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scheduled for", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = if (isOverdueReminder) "Overdue: $scheduledText" else scheduledText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isOverdueReminder) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (currentTodo.recurrenceType() == RecurrenceType.NONE) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            showTimePickerDialog = true
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Schedule, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Today", maxLines = 1)
                        }

                        FilledTonalButton(onClick = {
                            showDarkDateTimePicker(context) {
                                val updated = currentTodo.copy(reminderTime = it)
                                viewModel.update(updated)
                                AlarmScheduler.schedule(context, updated)
                                ToastUtils.show(context, "Reminder set")
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.EditCalendar, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Custom", maxLines = 1)
                        }
                    }
                }

                if (currentTodo.reminderTime != null) {
                    TextButton(
                        onClick = {
                            AlarmScheduler.cancel(context, currentTodo)
                            viewModel.update(currentTodo.copy(reminderTime = null))
                            ToastUtils.show(context, "Reminder cleared")
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Close, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear reminder")
                    }
                }

                DetailSectionLabel("Repeat")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val recurrenceType = currentTodo.recurrenceType()
                    FilterChip(
                        selected = recurrenceType == RecurrenceType.NONE,
                        onClick = {
                            val updated = currentTodo.copy(recurrence = RecurrenceType.NONE.name)
                            viewModel.update(updated)
                        },
                        label = { Text("Off") }
                    )
                    FilterChip(
                        selected = recurrenceType == RecurrenceType.DAILY,
                        onClick = { showDailyTimeDialog = true },
                        label = { Text("Daily") }
                    )
                    FilterChip(
                        selected = recurrenceType == RecurrenceType.WEEKLY,
                        onClick = { showWeeklyDialog = true },
                        label = { Text("Weekly") }
                    )
                }

                DetailSectionLabel("Reminder behavior")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Switch(
                            checked = currentTodo.isHighPriority,
                            onCheckedChange = {
                                val updated = currentTodo.copy(isHighPriority = it)
                                viewModel.update(updated)
                                ToastUtils.show(context, if (it) "High priority enabled" else "Low priority")
                            },
                            enabled = !currentTodo.isFullScreenReminder,
                            colors = switchColors
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "High priority",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (currentTodo.isFullScreenReminder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            if (currentTodo.isFullScreenReminder) {
                                Text(
                                    "Auto on with full-screen reminders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Switch(
                            checked = currentTodo.isFullScreenReminder,
                            onCheckedChange = { enabled ->
                                if (enabled && !canUseFullScreenIntentPermission(context)) {
                                    openFullScreenIntentSettings(context)
                                    canUseFullScreenIntent = false
                                    ToastUtils.show(context, "Allow full-screen reminders in system settings")
                                } else {
                                    canUseFullScreenIntent = canUseFullScreenIntentPermission(context)
                                    val updated = currentTodo.copy(isFullScreenReminder = enabled)
                                    viewModel.update(updated)
                                    ToastUtils.show(context, if (enabled) "Full-screen reminder enabled" else "Full-screen reminder disabled")
                                }
                            },
                            colors = switchColors
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Full-screen reminder", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (canUseFullScreenIntent) "Shows over lock screen when reminder triggers."
                                else "Permission needed on this Android version.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!canUseFullScreenIntent) {
                        TextButton(onClick = {
                            openFullScreenIntentSettings(context)
                            canUseFullScreenIntent = canUseFullScreenIntentPermission(context)
                        }) {
                            Text("Open full-screen permission")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Switch(
                            checked = currentTodo.isOneOffTask,
                            onCheckedChange = {
                                val updated = currentTodo.copy(isOneOffTask = it)
                                viewModel.update(updated)
                                ToastUtils.show(context, if (it) "Auto-delete enabled" else "Auto-delete disabled")
                            },
                            colors = switchColors
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-delete when done", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Applies to non-recurring tasks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                DetailSectionLabel("Attachments")
                if (currentTodo.attachments.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(currentTodo.attachments) { uriStr ->
                            AttachmentItem(uriStr = uriStr, onRemove = {
                                val updated = currentTodo.copy(attachments = currentTodo.attachments - uriStr)
                                viewModel.update(updated)
                                ToastUtils.show(context, "Attachment removed")
                            })
                        }
                    }
                    OutlinedButton(onClick = { launcher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add files")
                    }
                } else {
                    OutlinedButton(onClick = { launcher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Upload files")
                    }
                }

                DetailSectionLabel("Metadata")
                Text("Added on $addedDate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete task", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (showDailyTimeDialog) {
                    val cal = Calendar.getInstance().apply {
                        currentTodo.reminderTime?.let { timeInMillis = it }
                    }
                    val timePickerState = rememberTimePickerState(
                        initialHour = cal.get(Calendar.HOUR_OF_DAY),
                        initialMinute = cal.get(Calendar.MINUTE),
                        is24Hour = true
                    )

                    AlertDialog(
                        onDismissRequest = { showDailyTimeDialog = false },
                        title = { Text("Daily reminder time") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Choose the time for this daily reminder.")
                                TimePicker(state = timePickerState)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDailyTimeDialog = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val nextTime = nextDailyOccurrence(timePickerState.hour, timePickerState.minute)
                                val updated = currentTodo.copy(
                                    recurrence = RecurrenceType.DAILY.name,
                                    reminderTime = nextTime,
                                    isCompleted = false
                                )
                                viewModel.update(updated)
                                AlarmScheduler.schedule(context, updated)
                                ToastUtils.show(context, "Daily reminder set")
                                showDailyTimeDialog = false
                            }) {
                                Text("Save")
                            }
                        }
                    )
                }

                if (showWeeklyDialog) {
                    val cal = Calendar.getInstance().apply {
                        currentTodo.reminderTime?.let { timeInMillis = it }
                    }
                    val timePickerState = rememberTimePickerState(
                        initialHour = cal.get(Calendar.HOUR_OF_DAY),
                        initialMinute = cal.get(Calendar.MINUTE),
                        is24Hour = true
                    )

                    AlertDialog(
                        onDismissRequest = { showWeeklyDialog = false },
                        title = { Text("Weekly reminder schedule") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Pick day of week and time.")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(weekDays()) { day ->
                                        FilterChip(
                                            selected = selectedWeeklyDay == day,
                                            onClick = { selectedWeeklyDay = day },
                                            label = { Text(shortWeekDayLabel(day)) }
                                        )
                                    }
                                }
                                TimePicker(state = timePickerState)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWeeklyDialog = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val nextTime = nextWeeklyOccurrence(
                                    dayOfWeek = selectedWeeklyDay,
                                    hour = timePickerState.hour,
                                    minute = timePickerState.minute
                                )
                                val updated = currentTodo.copy(
                                    recurrence = RecurrenceType.WEEKLY.name,
                                    reminderTime = nextTime,
                                    isCompleted = false
                                )
                                viewModel.update(updated)
                                AlarmScheduler.schedule(context, updated)
                                ToastUtils.show(context, "Weekly reminder set")
                                showWeeklyDialog = false
                            }) {
                                Text("Save")
                            }
                        }
                    )
                }

                if (showTimePickerDialog) {
                    val now = Calendar.getInstance()
                    val timePickerState = rememberTimePickerState(
                        initialHour = now.get(Calendar.HOUR_OF_DAY),
                        initialMinute = now.get(Calendar.MINUTE),
                        is24Hour = true
                    )

                    AlertDialog(
                        onDismissRequest = { showTimePickerDialog = false },
                        title = { Text("Select time") },
                        text = { TimePicker(state = timePickerState) },
                        dismissButton = {
                            TextButton(
                                onClick = { showTimePickerDialog = false },
                                modifier = Modifier.widthIn(min = 96.dp)
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val updatedTime = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                        set(Calendar.MINUTE, timePickerState.minute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    val updated = currentTodo.copy(reminderTime = updatedTime)
                                    viewModel.update(updated)
                                    AlarmScheduler.schedule(context, updated)
                                    ToastUtils.show(context, "Reminder set")
                                    showTimePickerDialog = false
                                },
                                modifier = Modifier.widthIn(min = 72.dp)
                            ) {
                                Text("OK")
                            }
                        }
                    )
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete task") },
                        text = { Text("Are you sure you want to delete this task?") },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteConfirm = false
                                viewModel.delete(currentTodo)
                                ToastUtils.show(context, "Task deleted")
                                onBack()
                            }) {
                                Text("Delete")
                            }
                        }
                    )
                }

            }
        }
    }
}

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun detailSwitchColors(): SwitchColors {
    return SwitchDefaults.colors(
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        disabledUncheckedThumbColor = MaterialTheme.colorScheme.outline,
        disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline,
        disabledUncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
