package com.maxximum.kairos.ui.navigation

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
import com.maxximum.kairos.app.AuthUiState
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
import com.maxximum.kairos.ui.add.AddTodoScreen
import com.maxximum.kairos.ui.detail.TodoDetailScreen
import com.maxximum.kairos.ui.list.TodoListScreen
import com.maxximum.kairos.ui.notes.NotesScreen
import com.maxximum.kairos.ui.settings.SettingsScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoNavHost(
    viewModel: TodoViewModel,
    initialTodoId: Int = -1,
    authState: AuthUiState,
    onLogin: (String, String) -> Unit = { _, _ -> },
    onRegister: (String, String) -> Unit = { _, _ -> },
    onLogout: () -> Unit = {},
    onServerChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedTodoId by remember { mutableStateOf(if (initialTodoId > 0) initialTodoId else -1) }
    var allowDetailFromClick by remember { mutableStateOf(initialTodoId > 0) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var settingsReturnPage by remember { mutableIntStateOf(if (initialTodoId > 0) 3 else 0) }
    val syncState by viewModel.syncState.collectAsState()
    val syncConflicts by viewModel.syncConflicts.collectAsState(initial = emptyList())
    val mainPageCount = if (selectedTodoId > 0 && allowDetailFromClick) 4 else 3
    val settingsPage = mainPageCount

    val pagerState = rememberPagerState(
        initialPage = if (initialTodoId > 0) 3 else 0,
        pageCount = {
            mainPageCount + if (showSettingsScreen) 1 else 0
        }
    )

    LaunchedEffect(initialTodoId) {
        if (initialTodoId > 0) {
            selectedTodoId = initialTodoId
            allowDetailFromClick = true
            if (pagerState.currentPage != 3) {
                pagerState.scrollToPage(3)
            }
        }
    }

    LaunchedEffect(showSettingsScreen, settingsPage) {
        if (showSettingsScreen && pagerState.currentPage != settingsPage) {
            pagerState.animateScrollToPage(settingsPage)
        }
    }

    LaunchedEffect(selectedTodoId, allowDetailFromClick) {
        if (selectedTodoId > 0 && allowDetailFromClick && pagerState.currentPage == 1) {
            pagerState.animateScrollToPage(3)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 0) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    BackHandler(enabled = showSettingsScreen && pagerState.currentPage == settingsPage) {
        showSettingsScreen = false
        scope.launch { pagerState.animateScrollToPage(settingsReturnPage.coerceIn(0, mainPageCount - 1)) }
    }

    BackHandler(enabled = !showSettingsScreen && pagerState.currentPage == 3) {
        scope.launch { pagerState.animateScrollToPage(1) }
    }

    BackHandler(enabled = !showSettingsScreen && pagerState.currentPage == 2) {
        scope.launch { pagerState.animateScrollToPage(1) }
    }

    BackHandler(enabled = !showSettingsScreen && pagerState.currentPage == 1) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (showSettingsScreen && pagerState.settledPage != settingsPage) {
            showSettingsScreen = false
        }
        if (!showSettingsScreen && pagerState.settledPage == 3 && !allowDetailFromClick) {
            scope.launch { pagerState.animateScrollToPage(1) }
        }
        if (!showSettingsScreen && pagerState.settledPage != 3) {
            allowDetailFromClick = false
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = true,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when {
            page == settingsPage && showSettingsScreen -> {
                SettingsScreen(
                    onBack = {
                        showSettingsScreen = false
                        scope.launch { pagerState.animateScrollToPage(settingsReturnPage.coerceIn(0, mainPageCount - 1)) }
                    },
                    authState = authState,
                    onLogin = onLogin,
                    onRegister = onRegister,
                    onLogout = onLogout,
                    onServerChanged = onServerChanged,
                    onExportBackup = { uri ->
                        viewModel.exportBackup(uri) { _, message ->
                            ToastUtils.show(context, message)
                        }
                    },
                    syncState = syncState,
                    onSyncNow = viewModel::syncNow,
                    syncConflicts = syncConflicts,
                    onKeepMineConflict = viewModel::keepMineForConflict,
                    onUseServerConflict = viewModel::useServerForConflict
                )
            }

            page == 0 -> AddTodoScreen(
                isActive = pagerState.currentPage == 0,
                onSettings = {
                    settingsReturnPage = pagerState.currentPage
                    showSettingsScreen = true
                },
                onSave = { todo ->
                    viewModel.insert(todo) { id ->
                        val savedTodo = todo.copy(id = id.toInt())
                        AlarmScheduler.schedule(context, savedTodo)
                    }
                },
                onViewAll = { scope.launch { pagerState.animateScrollToPage(1) } }
            )

            page == 1 -> TodoListScreen(
                viewModel = viewModel,
                onTodoClick = { todoId ->
                    selectedTodoId = todoId
                    allowDetailFromClick = true
                },
                onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                authState = authState,
                onLogin = onLogin,
                onRegister = onRegister,
                onLogout = onLogout,
                onServerChanged = onServerChanged,
                onSettings = {
                    settingsReturnPage = pagerState.currentPage
                    showSettingsScreen = true
                },
                onNotes = { scope.launch { pagerState.animateScrollToPage(2) } }
            )

            page == 2 -> {
                NotesScreen(
                    viewModel = viewModel,
                    onBack = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
            }

            page == 3 -> {
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
