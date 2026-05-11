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
import com.maxximum.kairos.ui.add.AddTodoScreen
import com.maxximum.kairos.ui.detail.TodoDetailScreen
import com.maxximum.kairos.ui.list.TodoListScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoNavHost(viewModel: TodoViewModel, initialTodoId: Int = -1) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
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
        if (pagerState.currentPage != 0) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
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
                isActive = pagerState.currentPage == 0,
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
