package com.projectpandora.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import com.projectpandora.app.R
import com.projectpandora.app.core.network.*
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

private const val ROOT = "api/v1/app/"

@Composable
fun HomeScreen(user: BusinessUser, api: BusinessApi, onTask: (Long) -> Unit, onLog: (String) -> Unit, onApprovalTask: (Long) -> Unit = onTask, notificationRefreshKey: Int = 0) {
    var page by remember { mutableStateOf("home") }
    var reload by remember { mutableIntStateOf(0) }
    var unreadNotificationCount by remember { mutableLongStateOf(0L) }
    var homeData by remember { mutableStateOf<JsonObject?>(null) }
    var homeLoading by remember { mutableStateOf(true) }
    var homeError by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(page, lifecycleOwner) {
        if (page == "home") {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(30_000)
                    try { unreadNotificationCount = api.get(ROOT + "notifications/unread-count/").data().long("unread_count") }
                    catch (_: Exception) { /* Keep the last known count during a transient connection failure. */ }
                }
            }
        }
    }
    LaunchedEffect(page, reload, notificationRefreshKey) {
        if (page == "home") {
            homeLoading = homeData == null
            homeError = ""
            try {
                val payload = api.get(ROOT + "home/").data()
                homeData = payload
                unreadNotificationCount = payload.long("unread_notification_count")
            } catch (e: Exception) {
                homeError = e.businessMessage()
            } finally {
                homeLoading = false
            }
        }
    }
    BackHandler(enabled = page in setOf("company-items", "company-top-tasks", "personal-focus")) {
        page = "home"
        reload++
    }
    if (page == "notifications") {
        TaskNotificationsScreen(api, onBack = { page = "home"; reload++ }, onTask = { id, approval -> if (approval) onApprovalTask(id) else onTask(id) }, reloadKey = reload + notificationRefreshKey)
        return
    }
    if (page == "company-items") {
        CompanyItemsScreen(api, onBack = { page = "home"; reload++ }, reloadKey = reload)
        return
    }
    if (page == "company-top-tasks") {
        CompanyTopTasksScreen(
            api = api,
            onBack = { page = "home"; reload++ },
            onTask = onTask,
            reloadKey = reload + notificationRefreshKey,
        )
        return
    }
    if (page == "personal-focus") {
        FocusScreen(api, onBack = { page = "home"; reload++ }, onTask = onTask, reloadKey = reload + notificationRefreshKey)
        return
    }
    val date = remember { SimpleDateFormat("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE).format(Date()) }
    val companyItemRows = homeData?.array("company_items").orEmpty().take(5).map { item ->
        DashboardRow(item.string("title"))
    }
    val companyTaskRows = homeData?.array("company_top_tasks").orEmpty().take(5).map { task ->
        DashboardRow(taskPreview(task)) { onTask(task.long("id")) }
    }
    val personalFocusRows = homeData?.array("personal_focus_tasks").orEmpty().take(5).map { task ->
        DashboardRow(taskPreview(task)) { onTask(task.long("id")) }
    }
    val recentLogRows = homeData?.array("recent_logs").orEmpty().take(5).map { log ->
        val logDate = log.string("log_date")
        DashboardRow("${displayDashboardDate(logDate)} · ${log.string("summary")}") { onLog(logDate) }
    }
    val previewRowCount = listOf(
        companyItemRows.size,
        companyTaskRows.size,
        personalFocusRows.size,
        recentLogRows.size,
    ).maxOrNull()?.coerceAtLeast(1) ?: 1
    val contentBasedCardHeight = (70 + previewRowCount * 24).dp
    val cardHeight = contentBasedCardHeight.coerceAtLeast(112.dp)
    val retryHome = { reload++ }
    PageColumn {
        BrandHeader()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PageTitle("你好，${user.name}", small = true)
            Box(Modifier.size(48.dp)) {
                IconButton(
                    onClick = { page = "notifications" },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center).testTag("task_notifications").semantics {
                        contentDescription = if (unreadNotificationCount > 0) "任务通知，${unreadNotificationCount}条未读" else "任务通知"
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.setting_notification),
                        contentDescription = null,
                        modifier = Modifier.size(25.dp),
                        tint = C.Blue,
                    )
                }
                if (unreadNotificationCount > 0) {
                    Box(
                        Modifier.align(androidx.compose.ui.Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp)
                            .size(10.dp).background(C.Error, CircleShape).border(1.dp, Color.White, CircleShape),
                    )
                }
            }
        }
        Text(user.organization + "  " + date, fontSize = 12.sp, color = C.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = "事件纪", rows = companyItemRows, loading = homeLoading, error = homeError,
                emptyText = "暂无已发布事项", pale = C.AmberPale, accent = C.Orange,
                action = "查看全部 ›", modifier = Modifier.weight(1f), cardHeight = cardHeight,
                onClick = { if (homeError.isNotBlank()) retryHome() else page = "company-items" },
            )
            DashboardCard(
                title = "任务看版", rows = companyTaskRows, loading = homeLoading, error = homeError,
                emptyText = "暂无符合条件的任务", pale = C.BluePale, accent = C.Blue,
                action = "查看全部 ›", modifier = Modifier.weight(1f), cardHeight = cardHeight,
                onClick = { if (homeError.isNotBlank()) retryHome() else page = "company-top-tasks" },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = "个人重点工作", rows = personalFocusRows, loading = homeLoading, error = homeError,
                emptyText = "暂无个人重点任务", pale = C.TealPale, accent = C.Teal,
                action = "调整重点 ›", modifier = Modifier.weight(1f), cardHeight = cardHeight,
                onClick = { if (homeError.isNotBlank()) retryHome() else page = "personal-focus" },
            )
            DashboardCard(
                title = "最近日志", rows = recentLogRows, loading = homeLoading, error = homeError,
                emptyText = "暂无已提交日志", pale = C.VioletPale, accent = C.Violet,
                action = "填写今日日志 ›", modifier = Modifier.weight(1f), cardHeight = cardHeight,
                onClick = { onLog("") },
            )
        }
        Note("今天也把重要的工作，稳稳向前推进。")
    }
}

@Composable
private fun DashboardCard(
    title: String,
    rows: List<DashboardRow>,
    loading: Boolean,
    error: String,
    emptyText: String,
    pale: Color,
    accent: Color,
    action: String,
    modifier: Modifier,
    cardHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier.height(cardHeight)
            .background(pale, RoundedCornerShape(20.dp))
            .border(1.dp, accent.copy(alpha = 0.13f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            when {
                loading && rows.isEmpty() -> Text("正在加载…", fontSize = 11.sp, color = C.Muted)
                error.isNotEmpty() && rows.isEmpty() -> Text("加载失败，点按重试", fontSize = 11.sp, color = C.Muted)
                rows.isEmpty() -> Text(emptyText, fontSize = 11.sp, color = C.Muted)
                else -> rows.forEach { row ->
                    Text(
                        "• ${row.label}",
                        Modifier.fillMaxWidth().then(if (row.onClick == null) Modifier else Modifier.clickable(onClick = row.onClick)),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = C.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(action, fontSize = 11.sp, color = accent)
    }
}

private data class DashboardRow(val label: String, val onClick: (() -> Unit)? = null)

private fun taskPreview(task: JsonObject): String {
    val status = if (task.string("completion_status") == "completed") "已完成" else "未完成"
    val due = displayDashboardDate(task.string("due_at"))
    return listOf(task.string("title"), status, due).filter(String::isNotBlank).joinToString(" · ")
}

private fun displayDashboardDate(value: String): String = value.take(10).replace('-', '/')

@Composable
internal fun FocusScreen(api: BusinessApi, onBack: () -> Unit, onTask: (Long) -> Unit, reloadKey: Int = 0) {
    var selected by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var candidates by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var draggedTaskId by remember { mutableStateOf<Long?>(null) }
    var dragRemainder by remember { mutableFloatStateOf(0f) }
    var previousTaskIndexes by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var reorderAnimationToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swapDistancePx = with(density) { 78.dp.toPx() }

    fun id(row: JsonObject) = row.long("task_id", row.long("id"))

    LaunchedEffect(reload, reloadKey) {
        busy = true
        loaded = false
        error = ""
        try {
            selected = api.get(ROOT + "home/personal-focus/").data().array("tasks")
            candidates = api.all(ROOT + "home/personal-focus/candidates/")
            loaded = true
        } catch (failure: Exception) {
            error = failure.businessMessage()
        } finally {
            busy = false
        }
    }

    fun moveTask(taskId: Long, direction: Int): Boolean {
        val index = selected.indexOfFirst { id(it) == taskId }
        val target = index + direction
        if (index < 0 || target !in selected.indices) return false
        previousTaskIndexes = selected.mapIndexed { itemIndex, item -> id(item) to itemIndex }.toMap()
        val reordered = selected.toMutableList()
        val task = reordered.removeAt(index)
        reordered.add(target, task)
        selected = reordered
        reorderAnimationToken++
        val currentToken = reorderAnimationToken
        scope.launch {
            delay(380)
            if (reorderAnimationToken == currentToken) previousTaskIndexes = emptyMap()
        }
        return true
    }

    val latestMoveTask = rememberUpdatedState<(Long, Int) -> Boolean> { taskId, direction ->
        moveTask(taskId, direction)
    }

    fun beginDrag(taskId: Long) {
        draggedTaskId = taskId
        dragRemainder = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun updateDrag(taskId: Long, deltaY: Float): Float {
        if (draggedTaskId != taskId) return 0f
        dragRemainder += deltaY
        while (dragRemainder >= swapDistancePx) {
            if (!latestMoveTask.value(taskId, 1)) {
                dragRemainder = swapDistancePx * 0.8f
                break
            }
            dragRemainder -= swapDistancePx
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        while (dragRemainder <= -swapDistancePx) {
            if (!latestMoveTask.value(taskId, -1)) {
                dragRemainder = -swapDistancePx * 0.8f
                break
            }
            dragRemainder += swapDistancePx
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        return dragRemainder
    }

    fun endDrag(taskId: Long) {
        if (draggedTaskId == taskId) {
            draggedTaskId = null
            dragRemainder = 0f
        }
    }

    PageColumn {
        BackButton(onBack)
        PageTitle("个人十大重点工作", small = true)
        Text("已选 ${selected.size} / 10 · 长按抓手拖动排序，调整完成后点击底部保存")
        if (busy) CircularProgressIndicator()
        if (error.isNotEmpty()) {
            Note(error)
            TextButton(onClick = { reload++ }) { Text("重试") }
        }

        if (selected.isEmpty() && loaded) {
            Note("还没有个人重点任务，可以从下方候选任务中添加。")
        } else if (selected.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = (selected.size * 78).coerceAtMost(360).dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(selected, key = { id(it) }) { row ->
                    val taskId = id(row)
                    val index = selected.indexOfFirst { id(it) == taskId }
                    FocusTaskRow(
                        row = row,
                        taskId = taskId,
                        index = index,
                        previousIndex = previousTaskIndexes[taskId] ?: index,
                        reorderAnimationToken = reorderAnimationToken,
                        isDragging = draggedTaskId == taskId,
                        dragEnabled = loaded && !busy,
                        onDragStart = { beginDrag(taskId) },
                        onDrag = { deltaY -> updateDrag(taskId, deltaY) },
                        onDragEnd = { endDrag(taskId) },
                        onTask = { onTask(taskId) },
                        onMoveUp = { moveTask(taskId, -1) },
                        onRemove = { selected = selected.filterNot { id(it) == taskId } },
                    )
                }
            }
        }

        OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), label = { Text("搜索可选任务") })
        candidates.filter { it.string("title").contains(keyword, true) && selected.none { s -> id(s) == id(it) } }
            .forEach { row ->
                TextButton(
                    onClick = {
                        if (selected.size < 10) selected = selected + row else error = "最多选择 10 项。"
                    },
                ) { Text("＋ ${row.string("title")}") }
            }
        PrimaryButton(
            "保存顺序",
            {
                scope.launch {
                    busy = true
                    try {
                        api.put(ROOT + "home/personal-focus/", json { add("task_ids", ids(selected.map(::id))) })
                        onBack()
                    } catch (failure: Exception) {
                        error = failure.businessMessage()
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && loaded,
        )
    }
}

@Composable
private fun FocusTaskRow(
    row: JsonObject,
    taskId: Long,
    index: Int,
    previousIndex: Int,
    reorderAnimationToken: Int,
    isDragging: Boolean,
    dragEnabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Float,
    onDragEnd: () -> Unit,
    onTask: () -> Unit,
    onMoveUp: () -> Unit,
    onRemove: () -> Unit,
) {
    var pointerDragging by remember(taskId) { mutableStateOf(false) }
    var pointerOffset by remember(taskId) { mutableFloatStateOf(0f) }
    val placementOffset = remember(taskId) { Animatable(0f) }
    val density = LocalDensity.current
    val rowStepPx = with(density) { 78.dp.toPx() }
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDrag = rememberUpdatedState(onDrag)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.035f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "focus task drag scale",
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "focus task drag elevation",
    )
    val settleOffset by animateFloatAsState(
        targetValue = if (pointerDragging) pointerOffset else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "focus task drag settle",
    )

    LaunchedEffect(index, reorderAnimationToken) {
        if (isDragging) {
            placementOffset.snapTo(0f)
        } else {
            val initialOffset = (previousIndex - index) * rowStepPx
            if (initialOffset != 0f) {
                placementOffset.snapTo(initialOffset)
                placementOffset.animateTo(
                    0f,
                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                )
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .graphicsLayer {
                translationY = (if (pointerDragging) pointerOffset else settleOffset) + placementOffset.value
                scaleX = scale
                scaleY = scale
            }
            .zIndex(if (isDragging) 1f else 0f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isDragging) C.Blue.copy(alpha = 0.45f) else C.Blue.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = if (isDragging) C.BluePale else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                "⠿",
                Modifier
                    .pointerInput(taskId, dragEnabled) {
                        if (dragEnabled) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    pointerDragging = true
                                    pointerOffset = 0f
                                    latestOnDragStart.value()
                                },
                                onDragEnd = {
                                    pointerDragging = false
                                    latestOnDragEnd.value()
                                },
                                onDragCancel = {
                                    pointerDragging = false
                                    latestOnDragEnd.value()
                                },
                            ) { change, amount ->
                                change.consume()
                                pointerDragging = true
                                pointerOffset = latestOnDrag.value(amount.y)
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                fontSize = 18.sp,
                color = if (isDragging) C.Blue else C.Muted,
            )
            Text(
                "${index + 1}",
                Modifier.padding(end = 10.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDragging) C.Blue else C.Muted,
            )
            Text(
                row.string("title"),
                Modifier.weight(1f).clickable(onClick = onTask).padding(vertical = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = if (isDragging) FontWeight.SemiBold else FontWeight.Normal,
                color = C.Ink,
            )
            TextButton(onClick = onMoveUp, enabled = index > 0 && !isDragging) { Text("上移") }
            TextButton(onClick = onRemove, enabled = !isDragging) { Text("移除") }
        }
    }
}
