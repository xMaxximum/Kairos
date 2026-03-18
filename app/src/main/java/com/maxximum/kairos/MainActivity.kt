package com.maxximum.kairos

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.maxximum.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KairosTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val context = LocalContext.current
                    val viewModel: TodoViewModel = viewModel()
                    
                    val todoIdFromIntent = intent.getIntExtra("todo_id", -1)

                    val permissionsToRequest = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        checkExactAlarmPermission(context)
                        val hours = NotificationPreferences.getOverdueIntervalHours(context)
                        OverdueNotificationWorker.schedule(context, hours)
                    }
                    
                    TodoNavHost(viewModel = viewModel, initialTodoId = todoIdFromIntent)
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

enum class TodoFilter(val label: String) {
    ALL("All Tasks"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    ARCHIVED("Archived")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoNavHost(viewModel: TodoViewModel, initialTodoId: Int = -1) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedTodoId by remember { mutableStateOf(if (initialTodoId > 0) initialTodoId else -1) }
    var allowDetailFromClick by remember { mutableStateOf(initialTodoId > 0) }

    val pagerState = rememberPagerState(
        initialPage = if (initialTodoId > 0) 2 else 0,
        pageCount = { 3 }
    )

    LaunchedEffect(initialTodoId) {
        if (initialTodoId > 0) {
            selectedTodoId = initialTodoId
            allowDetailFromClick = true
            if (pagerState.currentPage != 2) {
                pagerState.scrollToPage(2)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 0) keyboardController?.hide()
    }

    BackHandler(enabled = pagerState.currentPage == 2) {
        scope.launch { pagerState.animateScrollToPage(1) }
    }

    BackHandler(enabled = pagerState.currentPage == 1) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage == 2 && !allowDetailFromClick) {
            scope.launch { pagerState.animateScrollToPage(1) }
        }
        if (pagerState.settledPage != 2) {
            allowDetailFromClick = false
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = true,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> AddTodoScreen(
                onSave = { todo ->
                    viewModel.insert(todo) { id ->
                        val savedTodo = todo.copy(id = id.toInt())
                        AlarmScheduler.schedule(context, savedTodo)
                    }
                },
                onViewAll = { scope.launch { pagerState.animateScrollToPage(1) } }
            )

            1 -> TodoListScreen(
                viewModel = viewModel,
                onTodoClick = { todoId ->
                    selectedTodoId = todoId
                    allowDetailFromClick = true
                    scope.launch { pagerState.animateScrollToPage(2) }
                },
                onBack = { scope.launch { pagerState.animateScrollToPage(0) } }
            )

            2 -> {
                if (selectedTodoId > 0) {
                    TodoDetailScreen(
                        todoId = selectedTodoId,
                        viewModel = viewModel,
                        onBack = { scope.launch { pagerState.animateScrollToPage(1) } }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Task not found")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTodoScreen(onSave: (Todo) -> Unit, onViewAll: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf<Long?>(null) }
    var isHighPriority by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showReminderPopup by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
                        if (BuildConfig.DEBUG) {
                            DebugBuildBadge()
                        }
                    }
                },
                actions = {
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
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showReminderPopup = !showReminderPopup }) {
                            Icon(
                                Icons.Default.NotificationsActive, 
                                contentDescription = "Reminder",
                                tint = if (reminderTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isHighPriority, onCheckedChange = { isHighPriority = it })
                                Text("High Priority", style = MaterialTheme.typography.bodySmall)
                            }

                            if (reminderTime != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isFullScreen, onCheckedChange = { isFullScreen = it })
                                    Text("Full Screen", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onSave(
                                        Todo(
                                            title = title,
                                            description = description,
                                            reminderTime = reminderTime,
                                            isHighPriority = isHighPriority,
                                            isFullScreenReminder = isFullScreen
                                        )
                                    )
                                    title = ""
                                    description = ""
                                    reminderTime = null
                                    isHighPriority = false
                                    isFullScreen = false
                                    ToastUtils.show(context, "Task Saved!")
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save")
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoListScreen(viewModel: TodoViewModel, onTodoClick: (Int) -> Unit, onBack: () -> Unit) {
    val todos by viewModel.allTodos.collectAsState(initial = emptyList())
    var selectedTodos by remember { mutableStateOf(setOf<Int>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(TodoFilter.ALL) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.undoEvents.collectLatest { event ->
            val result = snackbarHostState.showSnackbar(
                message = when(event) {
                    is UndoEvent.Delete -> event.message
                    is UndoEvent.Archive -> event.message
                },
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                when(event) {
                    is UndoEvent.Delete -> viewModel.undoDelete()
                    is UndoEvent.Archive -> viewModel.undoArchive()
                }
            }
        }
    }

    val filteredTodos = remember(todos, currentFilter) {
        todos.filter { 
            when (currentFilter) {
                TodoFilter.ALL -> !it.isArchived
                TodoFilter.PENDING -> !it.isArchived && !it.isCompleted
                TodoFilter.COMPLETED -> !it.isArchived && it.isCompleted
                TodoFilter.ARCHIVED -> it.isArchived
            }
        }.sortedBy { it.reminderTime ?: Long.MAX_VALUE }
    }

    val overdueCount = remember(filteredTodos) {
        val now = System.currentTimeMillis()
        filteredTodos.count { !it.isCompleted && !it.isArchived && it.reminderTime != null && it.reminderTime < now }
    }

    val useGroupedView = currentFilter == TodoFilter.ALL || currentFilter == TodoFilter.PENDING
    val todoSections = remember(filteredTodos, useGroupedView) {
        if (useGroupedView) groupTodosByDate(filteredTodos) else null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            var showSettings by remember { mutableStateOf(false) }
            if (showSettings) {
                SettingsDialog(onDismiss = { showSettings = false })
            }
            TopAppBar(
                title = { 
                    Column {
                        if (isSelectionMode) {
                            Text("${selectedTodos.size} selected")
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Your Tasks")
                                if (overdueCount > 0) {
                                    Badge(containerColor = Color(0xFFD32F2F)) {
                                        Text("$overdueCount", color = Color.White)
                                    }
                                }
                            }
                            Text(currentFilter.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) { isSelectionMode = false; selectedTodos = emptySet() } else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val toArchive = todos.filter { it.id in selectedTodos }
                            viewModel.archiveMultiple(toArchive)
                            isSelectionMode = false; selectedTodos = emptySet()
                        }) { Icon(Icons.Default.Archive, contentDescription = "Archive") }
                        
                        IconButton(onClick = {
                            val toDelete = todos.filter { it.id in selectedTodos }
                            viewModel.deleteMultiple(toDelete)
                            isSelectionMode = false; selectedTodos = emptySet()
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    } else {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.FilterList, null) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            TodoFilter.values().forEach { filter ->
                                DropdownMenuItem(
                                    text = { Text(filter.label) },
                                    onClick = { currentFilter = filter; expanded = false },
                                    leadingIcon = {
                                        if (currentFilter == filter) Icon(Icons.Default.Check, null)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (todoSections != null) {
                todoSections.forEach { section ->
                    stickyHeader(key = "header_${section.label}") {
                        TodoSectionHeader(
                            label = section.label,
                            count = section.todos.size,
                            isOverdue = section.label == "Overdue"
                        )
                    }
                    items(section.todos, key = { it.id }) { todo ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            TodoItem(
                                todo = todo,
                                isSelected = todo.id in selectedTodos,
                                onToggleComplete = { viewModel.update(todo.copy(isCompleted = !todo.isCompleted)) },
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedTodos = if (todo.id in selectedTodos) selectedTodos - todo.id else selectedTodos + todo.id
                                        if (selectedTodos.isEmpty()) isSelectionMode = false
                                    } else onTodoClick(todo.id)
                                },
                                onLongClick = { isSelectionMode = true; selectedTodos = selectedTodos + todo.id }
                            )
                        }
                    }
                }
            } else {
                items(filteredTodos, key = { it.id }) { todo ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        TodoItem(
                            todo = todo,
                            isSelected = todo.id in selectedTodos,
                            onToggleComplete = { viewModel.update(todo.copy(isCompleted = !todo.isCompleted)) },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedTodos = if (todo.id in selectedTodos) selectedTodos - todo.id else selectedTodos + todo.id
                                    if (selectedTodos.isEmpty()) isSelectionMode = false
                                } else onTodoClick(todo.id)
                            },
                            onLongClick = { isSelectionMode = true; selectedTodos = selectedTodos + todo.id }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoItem(todo: Todo, isSelected: Boolean, onToggleComplete: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val isOverdue = !todo.isCompleted && todo.reminderTime != null && todo.reminderTime < now
    val overdueColor = Color(0xFFD32F2F)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(
                if (isOverdue) Modifier.border(1.5.dp, overdueColor, RoundedCornerShape(16.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isOverdue) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .background(overdueColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Checkbox(checked = todo.isCompleted, onCheckedChange = { onToggleComplete() })
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    todo.title,
                    style = MaterialTheme.typography.titleLarge,
                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null
                )
                if (todo.isHighPriority && !todo.isCompleted) {
                    Text("High Priority", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6D00))
                }
                if (todo.reminderTime != null) {
                    val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(todo.reminderTime))
                    Text(
                        if (isOverdue) "Overdue: $date" else "Reminder: $date",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) overdueColor else MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (todo.isArchived) {
                Icon(Icons.Default.Archive, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailScreen(todoId: Int, viewModel: TodoViewModel, onBack: () -> Unit) {
    val todo by viewModel.observeTodoById(todoId).collectAsState(initial = null)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }
    var descriptionDraft by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)
    
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
        titleDraft = todo?.title.orEmpty()
        descriptionDraft = todo?.description.orEmpty()
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
            topBar = {
                TopAppBar(
                    title = { Text("Details") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = {
                            val updated = currentTodo.copy(isArchived = !currentTodo.isArchived)
                            viewModel.update(updated)
                            ToastUtils.show(context, if (updated.isArchived) "Archived" else "Unarchived")
                        }) { Icon(if (currentTodo.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, null) }
                        
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = currentTodo.isCompleted, onCheckedChange = { 
                        val updated = currentTodo.copy(isCompleted = it)
                        viewModel.update(updated)
                        ToastUtils.show(context, if (it) "Task completed" else "Task marked pending")
                    })
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Title") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = currentTodo.isHighPriority,
                        onCheckedChange = {
                            val updated = currentTodo.copy(isHighPriority = it)
                            viewModel.update(updated)
                            ToastUtils.show(context, if (it) "High priority enabled" else "Low priority")
                        }
                    )
                    Text("High Priority", style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedTextField(
                    value = descriptionDraft,
                    onValueChange = { descriptionDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))
                val addedDate = SimpleDateFormat("EEEE, MMM dd yyyy, HH:mm", Locale.getDefault()).format(Date(currentTodo.timestamp))
                Text("Added on $addedDate", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.height(24.dp)); HorizontalDivider(); Spacer(modifier = Modifier.height(24.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Attachments", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { launcher.launch("*/*") }) {
                        Icon(Icons.Default.AddCircleOutline, null)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
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
                } else {
                    OutlinedButton(onClick = { launcher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Upload Files")
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))

                val now = System.currentTimeMillis()
                val scheduledTime = currentTodo.reminderTime
                val scheduledText = scheduledTime?.let {
                    SimpleDateFormat("EEEE, MMM dd yyyy, HH:mm", Locale.getDefault()).format(Date(it))
                } ?: "Not scheduled"
                val isOverdueReminder = scheduledTime != null && !currentTodo.isCompleted && scheduledTime < now

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
                                text = if (isOverdueReminder) "Overdue · $scheduledText" else scheduledText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isOverdueReminder) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        showTimePickerDialog = true
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(4.dp)); Text("Today")
                    }
                    
                    Button(onClick = {
                        showDarkDateTimePicker(context) {
                            val updated = currentTodo.copy(reminderTime = it)
                            viewModel.update(updated)
                            AlarmScheduler.schedule(context, updated)
                            ToastUtils.show(context, "Reminder set")
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.EditCalendar, null); Spacer(Modifier.width(4.dp)); Text("Custom")
                    }

                    OutlinedButton(
                        onClick = {
                            AlarmScheduler.cancel(context, currentTodo)
                            viewModel.update(currentTodo.copy(reminderTime = null))
                            ToastUtils.show(context, "Reminder cleared")
                        },
                        enabled = currentTodo.reminderTime != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
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
fun AttachmentItem(uriStr: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val uri = Uri.parse(uriStr)
    val mimeType = context.contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uriStr))
    val isImage = mimeType?.startsWith("image/") == true

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.size(108.dp)) {
        Card(
            modifier = Modifier.fillMaxSize().clickable { openFile(context, uri, mimeType) },
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isImage) {
                AsyncImage(model = uriStr, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(48.dp))
                    Text(mimeType?.split("/")?.lastOrNull()?.uppercase() ?: "FILE", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp))
                }
            }
        }
        IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
        ) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Remove attachment") },
                text = { Text("Delete this attachment from the task?") },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(android.R.string.cancel)) }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onRemove()
                    }) {
                        Text("Remove")
                    }
                }
            )
        }
    }
}

fun openFile(context: Context, uri: Uri, mimeType: String?) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with..."))
    } catch (e: Exception) {
        ToastUtils.show(context, "Could not open file")
    }
}

fun showDarkDateTimePicker(context: Context, onResult: (Long) -> Unit) {
    val cal = Calendar.getInstance()
    DatePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, y, m, d ->
        android.app.TimePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, h, min ->
            val result = Calendar.getInstance().apply {
                set(y, m, d, h, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            onResult(result)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

object ToastUtils {
    fun show(context: android.content.Context, message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}

// ── Date grouping ─────────────────────────────────────────────────────────────

data class TodoSection(val label: String, val todos: List<Todo>)

fun groupTodosByDate(todos: List<Todo>): List<TodoSection> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfToday = cal.timeInMillis
    val startOfTomorrow = startOfToday + 86_400_000L
    val startOfDayAfterTomorrow = startOfTomorrow + 86_400_000L
    val startOfNextWeek = startOfToday + 7 * 86_400_000L

    val overdue = todos.filter { !it.isCompleted && it.reminderTime != null && it.reminderTime < startOfToday }
    val today = todos.filter { it.reminderTime != null && it.reminderTime in startOfToday until startOfTomorrow }
    val tomorrow = todos.filter { it.reminderTime != null && it.reminderTime in startOfTomorrow until startOfDayAfterTomorrow }
    val thisWeek = todos.filter { it.reminderTime != null && it.reminderTime in startOfDayAfterTomorrow until startOfNextWeek }
    val later = todos.filter { it.reminderTime != null && it.reminderTime >= startOfNextWeek }
    val noDate = todos.filter { it.reminderTime == null }

    return buildList {
        if (overdue.isNotEmpty()) add(TodoSection("Overdue", overdue))
        if (today.isNotEmpty()) add(TodoSection("Today", today))
        if (tomorrow.isNotEmpty()) add(TodoSection("Tomorrow", tomorrow))
        if (thisWeek.isNotEmpty()) add(TodoSection("This Week", thisWeek))
        if (later.isNotEmpty()) add(TodoSection("Later", later))
        if (noDate.isNotEmpty()) add(TodoSection("No Due Date", noDate))
    }
}

@Composable
fun TodoSectionHeader(label: String, count: Int, isOverdue: Boolean) {
    val accentColor = if (isOverdue) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Badge(containerColor = accentColor) {
                Text("$count", color = Color.White)
            }
        }
    }
}

// ── Settings dialog ───────────────────────────────────────────────────────────

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var intervalHours by remember {
        mutableIntStateOf(NotificationPreferences.getOverdueIntervalHours(context))
    }
    val options = listOf(0, 1, 2, 3, 4, 6, 8, 12, 24)
    val optionRows = options.chunked(5)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Notification Settings")
                if (BuildConfig.DEBUG) {
                    DebugBuildBadge()
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Remind me of pending tasks every:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    optionRows.forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                NotificationPreferences.setOverdueIntervalHours(context, intervalHours)
                OverdueNotificationWorker.schedule(context, intervalHours)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DebugBuildBadge() {
    Surface(
        color = Color(0xFF8B0000),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "DEBUG",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
