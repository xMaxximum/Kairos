package com.maxximum.kairos.ui.list

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
import com.maxximum.kairos.app.AuthUiState
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
import com.maxximum.kairos.ui.components.TodoSectionHeader
import com.maxximum.kairos.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoListScreen(
    viewModel: TodoViewModel,
    onTodoClick: (Int) -> Unit,
    onBack: () -> Unit,
    authState: AuthUiState,
    onLogin: (String, String) -> Unit = { _, _ -> },
    onRegister: (String, String) -> Unit = { _, _ -> },
    onLogout: () -> Unit = {},
    onServerChanged: (String) -> Unit = {},
    onSettings: () -> Unit = {}
) {
    val context = LocalContext.current
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
                        IconButton(onClick = onSettings) {
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
                                onToggleComplete = {
                                    val markCompleted = !todo.isCompleted
                                    viewModel.toggleComplete(todo, markCompleted) { result ->
                                        ToastUtils.show(context, result.message)
                                    }
                                },
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
                            onToggleComplete = {
                                val markCompleted = !todo.isCompleted
                                viewModel.toggleComplete(todo, markCompleted) { result ->
                                    ToastUtils.show(context, result.message)
                                }
                            },
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
                if (todo.recurrenceType() != RecurrenceType.NONE) {
                    Text("Repeats ${todo.recurrenceType().shortLabel().lowercase(Locale.getDefault())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
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
