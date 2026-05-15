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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val syncState by viewModel.syncState.collectAsState()
    var selectedTodos by remember { mutableStateOf(setOf<Int>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(TodoFilter.ALL) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var wasSyncing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val requestSync: () -> Unit = {
        if (authState.isAuthenticated && !authState.isLoading && !syncState.isSyncing) {
            viewModel.syncNow()
        } else if (!authState.isAuthenticated) {
            ToastUtils.show(context, "Sign in to sync tasks")
        }
    }

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

    LaunchedEffect(syncState.isSyncing, syncState.message) {
        if (wasSyncing && !syncState.isSyncing) {
            ToastUtils.show(context, syncState.message ?: "Sync complete")
        }
        wasSyncing = syncState.isSyncing
    }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            delay(100)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(enabled = isSearchOpen) {
        isSearchOpen = false
        searchQuery = ""
        keyboardController?.hide()
    }

    val filteredTodos = remember(todos, currentFilter, searchQuery) {
        val normalizedQuery = searchQuery.trim().lowercase(Locale.getDefault())
        todos.filter { 
            when (currentFilter) {
                TodoFilter.ALL -> !it.isArchived
                TodoFilter.PENDING -> !it.isArchived && !it.isCompleted
                TodoFilter.COMPLETED -> !it.isArchived && it.isCompleted
                TodoFilter.ARCHIVED -> it.isArchived
            }
        }.filter { todo ->
            normalizedQuery.isBlank() || todo.matchesSearch(normalizedQuery)
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
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.imePadding()
            ) {
                Column {
                    if (isSearchOpen && !isSelectionMode) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .focusRequester(searchFocusRequester),
                            singleLine = true,
                            placeholder = { Text("Search tasks") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    isSearchOpen = false
                                    searchQuery = ""
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            }
                        )
                    }

                    BottomAppBar(modifier = Modifier.navigationBarsPadding()) {
                        IconButton(onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedTodos = emptySet()
                            } else {
                                onBack()
                            }
                        }) {
                            val icon = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack
                            Icon(icon, contentDescription = "Back")
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            if (isSelectionMode) {
                                Text("${selectedTodos.size} selected", style = MaterialTheme.typography.titleMedium)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Your Tasks", style = MaterialTheme.typography.titleMedium)
                                    if (overdueCount > 0) {
                                        Badge(containerColor = Color(0xFFD32F2F)) {
                                            Text("$overdueCount", color = Color.White)
                                        }
                                    }
                                }
                                val subtitle = if (searchQuery.isBlank()) currentFilter.label else "${currentFilter.label} / search"
                                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (isSelectionMode) {
                            IconButton(onClick = {
                                val toArchive = todos.filter { it.id in selectedTodos }
                                viewModel.archiveMultiple(toArchive)
                                isSelectionMode = false
                                selectedTodos = emptySet()
                            }) { Icon(Icons.Default.Archive, contentDescription = "Archive") }

                            IconButton(onClick = {
                                val toDelete = todos.filter { it.id in selectedTodos }
                                viewModel.deleteMultiple(toDelete)
                                isSelectionMode = false
                                selectedTodos = emptySet()
                            }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                        } else {
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                            Box {
                                IconButton(onClick = { filterMenuExpanded = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                                }
                                DropdownMenu(
                                    expanded = filterMenuExpanded,
                                    onDismissRequest = { filterMenuExpanded = false }
                                ) {
                                    TodoFilter.values().forEach { filter ->
                                        DropdownMenuItem(
                                            text = { Text(filter.label) },
                                            onClick = {
                                                currentFilter = filter
                                                filterMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                if (currentFilter == filter) Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        )
                                    }
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    isSearchOpen = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Search")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = syncState.isSyncing,
            onRefresh = requestSync,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (syncState.isSyncing) {
                    item(key = "sync_status") {
                        SyncPullStatus(
                            text = "Syncing tasks...",
                            showProgress = syncState.isSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

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
}

private fun Todo.matchesSearch(query: String): Boolean {
    val reminder = reminderTime?.let {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(it))
    }.orEmpty()
    return listOf(title, description, recurrence, reminder)
        .any { it.lowercase(Locale.getDefault()).contains(query) }
}

@Composable
private fun SyncPullStatus(text: String, showProgress: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
