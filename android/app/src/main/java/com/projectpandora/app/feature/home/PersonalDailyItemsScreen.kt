package com.projectpandora.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.JsonObject
import com.projectpandora.app.R
import com.projectpandora.app.core.network.BusinessApi
import com.projectpandora.app.core.network.array
import com.projectpandora.app.core.network.businessMessage
import com.projectpandora.app.core.network.data
import com.projectpandora.app.core.network.json
import com.projectpandora.app.core.network.long
import com.projectpandora.app.core.network.string
import com.projectpandora.app.feature.tags.TagColorPalette
import com.projectpandora.app.feature.tags.tagColor
import com.projectpandora.app.feature.views.MONTH_WEEKDAYS
import com.projectpandora.app.feature.views.HolidayMarker
import com.projectpandora.app.feature.views.MonthPicker
import com.projectpandora.app.feature.views.PeriodArrow
import com.projectpandora.app.feature.views.businessCalendar
import com.projectpandora.app.feature.views.businessDateFromCalendarPicker
import com.projectpandora.app.feature.views.calendarPickerMillis
import com.projectpandora.app.feature.views.date
import com.projectpandora.app.feature.views.monthCalendarWeeks
import com.projectpandora.app.feature.views.parseBusinessDate
import com.projectpandora.app.ui.BackButton
import com.projectpandora.app.ui.theme.PandoraColors as C
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PERSONAL_ITEMS_API = "api/v1/app/personal-items/"
private const val DAILY_ITEM_VISIBLE_BARS = 3
private const val DAY_MILLIS = 86_400_000L
@Composable
internal fun PersonalDailyItemsScreen(api: BusinessApi, onBack: () -> Unit, embedded: Boolean = false) {
    var selectedMonth by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var reload by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var calendarDays by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var selectedItem by remember { mutableStateOf<JsonObject?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<JsonObject?>(null) }
    var formDate by remember { mutableStateOf(date(businessCalendar())) }
    var deletingItem by remember { mutableStateOf<JsonObject?>(null) }
    val current = businessCalendar(selectedMonth)
    val weeks = remember(selectedMonth) { monthCalendarWeeks(current) }
    val visibleStart = weeks.first().first().let(::date)
    val visibleEnd = weeks.last().last().let(::date)
    val scope = rememberCoroutineScope()

    fun openItem(item: JsonObject) {
        scope.launch {
            detailLoading = true
            error = ""
            try {
                selectedItem = api.get("$PERSONAL_ITEMS_API${item.long("id")}/").data()
            } catch (failure: Exception) {
                error = failure.businessMessage()
                selectedDate = null
            } finally {
                detailLoading = false
            }
        }
    }

    if (!embedded) BackHandler { onBack() }

    LaunchedEffect(visibleStart, visibleEnd, reload) {
        loading = true
        error = ""
        try {
            val payload = api.get(
                PERSONAL_ITEMS_API,
                mapOf("start_date" to visibleStart, "end_date" to visibleEnd),
            ).data()
            items = payload.array("items")
            calendarDays = payload.array("calendar_days")
        } catch (failure: Exception) {
            error = failure.businessMessage()
        } finally {
            loading = false
        }
    }

    if (showMonthPicker) {
        MonthPicker(current, { showMonthPicker = false }) { year, month ->
            val chosen = businessCalendar(selectedMonth).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
            }
            selectedMonth = chosen.timeInMillis
            showMonthPicker = false
        }
    }

    if (showForm) {
        PersonalDailyItemFormDialog(
            api = api,
            item = editingItem,
            initialDate = formDate,
            onDismiss = { showForm = false; editingItem = null },
            onSaved = {
                showForm = false
                editingItem = null
                reload++
            },
        )
    }

    selectedDate?.let { day ->
        PersonalDailyItemDayDialog(
            day = day,
            items = itemsForDate(items, day),
            onDismiss = { selectedDate = null },
            onCreate = {
                formDate = day
                editingItem = null
                showForm = true
            },
            onOpenItem = ::openItem,
        )
    }

    selectedItem?.let { item ->
        PersonalDailyItemDetailDialog(
            item = item,
            onDismiss = { selectedItem = null },
            onEdit = {
                editingItem = item
                formDate = item.string("start_date")
                selectedItem = null
                showForm = true
            },
            onDelete = { deletingItem = item },
        )
    }

    if (detailLoading) {
        Dialog(onDismissRequest = { detailLoading = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = C.Amber, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("正在读取事项…", color = C.Ink, fontSize = 14.sp)
                }
            }
        }
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!busy) deletingItem = null },
            title = { Text("删除每日事项？") },
            text = { Text("“${item.string("title")}”删除后无法恢复。") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        error = ""
                        try {
                            api.delete("$PERSONAL_ITEMS_API${item.long("id")}/")
                            items = items.filterNot { it.long("id") == item.long("id") }
                            selectedItem = null
                            deletingItem = null
                            reload++
                        } catch (failure: Exception) {
                            error = failure.businessMessage()
                            deletingItem = null
                            selectedItem = null
                            selectedDate = null
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("删除", color = C.Error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingItem = null }) { Text("取消") } },
        )
    }

    Column(
        (if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).background(Color.White),
    ) {
        if (embedded) {
            Text(
                "每日事项",
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 4.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = C.Ink,
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onBack)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("每日事项", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    Text("只属于你的备忘日历", fontSize = 12.sp, color = C.Muted)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PeriodArrow(previous = true, description = "上个月") {
                selectedMonth = businessCalendar(selectedMonth).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                }.timeInMillis
            }
            TextButton(
                onClick = { showMonthPicker = true },
                modifier = Modifier.weight(1f).testTag("personal-items-month-picker"),
            ) {
                Text(
                    "${current.get(Calendar.YEAR)}年 ${current.get(Calendar.MONTH) + 1}月 ▾",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.Ink,
                )
            }
            PeriodArrow(previous = false, description = "下个月") {
                selectedMonth = businessCalendar(selectedMonth).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, 1)
                }.timeInMillis
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.weight(1f).height(46.dp).clickable {
                    formDate = selectedDate ?: date(businessCalendar())
                    editingItem = null
                    showForm = true
                }.testTag("create-personal-daily-item"),
                shape = RoundedCornerShape(14.dp),
                color = C.Amber,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("＋ 新建事项", color = C.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            val today = businessCalendar()
            val isCurrentMonth = current.get(Calendar.YEAR) == today.get(Calendar.YEAR) && current.get(Calendar.MONTH) == today.get(Calendar.MONTH)
            if (!isCurrentMonth) {
                Surface(
                    modifier = Modifier.height(46.dp).clickable {
                        selectedMonth = businessCalendar().timeInMillis
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xffF7F8FA),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xffE6E9EF)),
                ) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("本月", color = C.Selected, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp), color = C.Amber)
        if (error.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(error, Modifier.weight(1f), color = C.Error, fontSize = 12.sp)
                TextButton(onClick = { reload++ }) { Text("重试") }
            }
        }
        val calendarModifier = if (embedded) {
            Modifier.fillMaxWidth().height(470.dp)
        } else {
            Modifier.weight(1f).fillMaxWidth()
        }
        Box(calendarModifier) {
            Image(
                painterResource(R.drawable.pandora_footer_art),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(100.dp).align(Alignment.BottomCenter).graphicsLayer { alpha = 0.12f },
            )
            PersonalDailyItemsMonthGrid(
                current = current,
                weeks = weeks,
                items = items,
                calendarDays = calendarDays,
                loading = loading,
                onDate = { selectedDate = it },
                onOpenItem = ::openItem,
                onMonthChange = { delta ->
                    selectedMonth = businessCalendar(selectedMonth).apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        add(Calendar.MONTH, delta)
                    }.timeInMillis
                },
                onMove = { item, sourceDay, targetDay ->
                    val itemId = item.long("id")
                    busy = true
                    error = ""
                    try {
                        val start = parseBusinessDate(item.string("start_date")) ?: return@PersonalDailyItemsMonthGrid false
                        val source = parseBusinessDate(sourceDay) ?: return@PersonalDailyItemsMonthGrid false
                        val target = parseBusinessDate(targetDay) ?: return@PersonalDailyItemsMonthGrid false
                        val shift = ((target - source).toDouble() / DAY_MILLIS).toInt()
                        val targetStart = businessCalendar(start).apply { add(Calendar.DAY_OF_MONTH, shift) }
                        val moved = api.post(
                            "$PERSONAL_ITEMS_API$itemId/move/",
                            json { addProperty("target_start_date", date(targetStart)) },
                        ).data()
                        items = (items.filterNot { it.long("id") == itemId } + moved)
                            .sortedWith(compareBy<JsonObject> { it.string("start_date") }.thenBy { it.long("id") })
                        reload++
                        true
                    } catch (failure: Exception) {
                        error = failure.businessMessage()
                        false
                    } finally {
                        busy = false
                    }
                },
            )
        }
    }
}

private data class PersonalItemSegment(
    val item: JsonObject,
    val start: Int,
    var end: Int,
    var lane: Int = 0,
)

private data class PersonalItemBarKey(val itemId: Long, val weekStart: String, val startColumn: Int)

private fun itemsForDate(items: List<JsonObject>, day: String): List<JsonObject> = items
    .filter { it.string("start_date") <= day && it.string("end_date") >= day }
    .sortedWith(compareBy<JsonObject> { it.string("start_date") }.thenBy { it.string("end_date") }.thenBy { it.long("id") })

private fun personalItemSegments(week: List<Calendar>, items: List<JsonObject>): Pair<List<PersonalItemSegment>, List<Int>> {
    val daily = week.map { day -> itemsForDate(items, date(day)).take(DAILY_ITEM_VISIBLE_BARS) }
    val overflow = week.mapIndexed { index, day ->
        (itemsForDate(items, date(day)).size - daily[index].size).coerceAtLeast(0)
    }
    val segments = mutableListOf<PersonalItemSegment>()
    val active = mutableMapOf<Long, PersonalItemSegment>()
    daily.forEachIndexed { column, dayItems ->
        val ids = dayItems.map { it.long("id") }.toSet()
        active.keys.toList().filterNot(ids::contains).forEach(active::remove)
        dayItems.forEach { item ->
            val id = item.long("id")
            val existing = active[id]
            if (existing != null) existing.end = column
            else PersonalItemSegment(item, column, column).also { segments += it; active[id] = it }
        }
    }
    val laneEnds = IntArray(DAILY_ITEM_VISIBLE_BARS) { -1 }
    segments.sortedWith(compareBy<PersonalItemSegment> { it.start }.thenBy { it.item.long("id") }).forEach { segment ->
        val lane = laneEnds.indexOfFirst { it < segment.start }
        if (lane >= 0) {
            segment.lane = lane
            laneEnds[lane] = segment.end
        }
    }
    return segments to overflow
}

private fun shiftDailyItemPreview(item: JsonObject, sourceDay: String, targetDay: String): JsonObject? {
    val sourceMillis = parseBusinessDate(sourceDay) ?: return null
    val targetMillis = parseBusinessDate(targetDay) ?: return null
    val startMillis = parseBusinessDate(item.string("start_date")) ?: return null
    val endMillis = parseBusinessDate(item.string("end_date")) ?: return null
    val dayDelta = ((targetMillis - sourceMillis).toDouble() / DAY_MILLIS).roundToInt()
    val start = businessCalendar(startMillis).apply { add(Calendar.DAY_OF_MONTH, dayDelta) }
    val end = businessCalendar(endMillis).apply { add(Calendar.DAY_OF_MONTH, dayDelta) }
    return item.deepCopy().apply {
        addProperty("start_date", date(start))
        addProperty("end_date", date(end))
    }
}

@Composable
private fun PersonalDailyItemsMonthGrid(
    current: Calendar,
    weeks: List<List<Calendar>>,
    items: List<JsonObject>,
    calendarDays: List<JsonObject>,
    loading: Boolean,
    onDate: (String) -> Unit,
    onOpenItem: (JsonObject) -> Unit,
    onMonthChange: (Int) -> Unit,
    onMove: suspend (JsonObject, String, String) -> Boolean,
) {
    val cellBounds = remember { mutableStateMapOf<String, Rect>() }
    val barBounds = remember { mutableStateMapOf<PersonalItemBarKey, Rect>() }
    val liveCellBounds by rememberUpdatedState(cellBounds.toMap())
    val liveBarBounds by rememberUpdatedState(barBounds.toMap())
    val liveItems by rememberUpdatedState(items)
    val liveOnMove by rememberUpdatedState(onMove)
    val liveOnMonthChange by rememberUpdatedState(onMonthChange)
    var gridBounds by remember { mutableStateOf(Rect.Zero) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var draggedItem by remember { mutableStateOf<JsonObject?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var sourceDay by remember { mutableStateOf("") }
    var dropTargetDay by remember { mutableStateOf<String?>(null) }
    var edgeDirection by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val edgeZonePx = with(LocalDensity.current) { 20.dp.toPx() }
    val scope = rememberCoroutineScope()
    val selectedMonth = current.get(Calendar.MONTH)
    val selectedYear = current.get(Calendar.YEAR)
    val holidaysByDate = remember(calendarDays) { calendarDays.associateBy { it.string("date") } }
    val monthKey = current.get(Calendar.YEAR) * 12 + current.get(Calendar.MONTH)
    var previousMonthKey by remember { mutableIntStateOf(monthKey) }
    val monthSlideOffset = remember { Animatable(0f) }

    LaunchedEffect(draggingId, edgeDirection) {
        val direction = edgeDirection
        if (draggingId == null || direction == 0) return@LaunchedEffect
        while (draggingId != null && edgeDirection == direction) {
            delay(850)
            if (draggingId != null && edgeDirection == direction) liveOnMonthChange(direction)
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().padding(horizontal = 12.dp)
            .onGloballyPositioned { gridBounds = it.boundsInRoot() }
            .testTag("personal-items-calendar-grid")
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { touch ->
                        val rootTouch = gridBounds.topLeft + touch
                        val hitBar = liveBarBounds.entries.firstOrNull { (_, rect) -> rect.contains(rootTouch) }?.key
                        val item = hitBar?.let { key -> liveItems.firstOrNull { it.long("id") == key.itemId } }
                        val source = liveCellBounds.entries.firstOrNull { (_, rect) -> rect.contains(rootTouch) }?.key
                        if (item != null && source != null) {
                            draggingId = item.long("id")
                            draggedItem = item.deepCopy()
                            dragOffset = Offset.Zero
                            dragOrigin = rootTouch
                            sourceDay = source
                            dropTargetDay = source
                            edgeDirection = 0
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, amount ->
                        if (draggingId != null) {
                            change.consume()
                            val nextOffset = dragOffset + amount
                            dragOffset = nextOffset
                            val targetPoint = dragOrigin + nextOffset
                            dropTargetDay = liveCellBounds.entries
                                .firstOrNull { (_, rect) -> rect.contains(targetPoint) }?.key
                            val bounds = gridBounds
                            val localX = targetPoint.x - bounds.left
                            val nextEdgeDirection = when {
                                localX <= edgeZonePx -> -1
                                localX >= bounds.width - edgeZonePx -> 1
                                else -> 0
                            }
                            if (nextEdgeDirection != edgeDirection) {
                                edgeDirection = nextEdgeDirection
                                if (nextEdgeDirection != 0) liveOnMonthChange(nextEdgeDirection)
                            }
                        }
                    },
                    onDragEnd = {
                        val item = draggedItem
                        val targetPoint = dragOrigin + dragOffset
                        val targetDay = liveCellBounds.entries.firstOrNull { (_, rect) -> rect.contains(targetPoint) }?.key
                        val fromDay = sourceDay
                        if (item != null && targetDay != null && targetDay != fromDay) {
                            scope.launch {
                                val moved = liveOnMove(item, fromDay, targetDay)
                                draggingId = null
                                draggedItem = null
                                dragOffset = Offset.Zero
                                dropTargetDay = null
                                edgeDirection = 0
                                if (!moved) haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            }
                        } else {
                            draggingId = null
                            draggedItem = null
                            dragOffset = Offset.Zero
                            dropTargetDay = null
                            edgeDirection = 0
                        }
                    },
                    onDragCancel = {
                        draggingId = null
                        draggedItem = null
                        dragOffset = Offset.Zero
                        dropTargetDay = null
                        edgeDirection = 0
                    },
                )
            },
    ) {
        val density = LocalDensity.current
        val gridWidthPx = with(density) { maxWidth.toPx() }
        LaunchedEffect(monthKey, gridWidthPx) {
            if (monthKey != previousMonthKey) {
                val direction = if (monthKey > previousMonthKey) 1 else -1
                previousMonthKey = monthKey
                cellBounds.clear()
                barBounds.clear()
                monthSlideOffset.snapTo(gridWidthPx * direction)
                monthSlideOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                )
                if (draggingId != null) {
                    withFrameNanos { }
                    val point = dragOrigin + dragOffset
                    dropTargetDay = liveCellBounds.entries.firstOrNull { (_, rect) -> rect.contains(point) }?.key
                }
            }
        }
        val laneHeight = 25.dp
        val previewItem = draggedItem?.let { item ->
            dropTargetDay?.let { target -> shiftDailyItemPreview(item, sourceDay, target) }
        }
        val rowHeights = weeks.map { week ->
            val (segments, overflow) = personalItemSegments(week, items)
            val lanes = (segments.maxOfOrNull { it.lane } ?: -1) + 1
            maxOf(64.dp, 31.dp + laneHeight * lanes + if (overflow.any { it > 0 }) 20.dp else 3.dp)
        }
        val extraHeight = ((maxHeight - 42.dp - rowHeights.fold(0.dp) { sum, height -> sum + height }) / weeks.size).coerceAtLeast(0.dp)
        Column(
            Modifier.fillMaxWidth()
                .graphicsLayer { translationX = monthSlideOffset.value }
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                MONTH_WEEKDAYS.forEach { weekday ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(weekday, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Muted)
                    }
                }
            }
            weeks.forEachIndexed { rowIndex, week ->
                val (segments, overflow) = personalItemSegments(week, items)
                val otherSegments = draggingId?.let { id ->
                    personalItemSegments(week, items.filterNot { it.long("id") == id }).first
                }.orEmpty()
                val previewSegments = previewItem?.let { preview ->
                    val maxLane = otherSegments.maxOfOrNull { it.lane } ?: 0
                    personalItemSegments(week, listOf(preview)).first.onEach { ghost ->
                        ghost.lane = (0..maxLane).firstOrNull { lane ->
                            otherSegments.none { other ->
                                other.lane == lane && other.start <= ghost.end && other.end >= ghost.start
                            }
                        } ?: 0
                    }
                }.orEmpty()
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(rowHeights[rowIndex] + extraHeight).testTag("personal-item-week-$rowIndex"),
                ) {
                    val cellWidth = maxWidth / 7
                    Row(Modifier.fillMaxSize()) {
                        week.forEach { day ->
                            val dayKey = date(day)
                            val inMonth = day.get(Calendar.YEAR) == selectedYear && day.get(Calendar.MONTH) == selectedMonth
                            val today = dayKey == date(businessCalendar())
                            val isDropTarget = dropTargetDay == dayKey && draggingId != null
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(
                                        if (isDropTarget) C.AmberPale.copy(alpha = 0.48f) else Color.Transparent,
                                        RoundedCornerShape(9.dp),
                                    )
                                    .border(
                                        if (isDropTarget) 1.5.dp else 0.5.dp,
                                        if (isDropTarget) C.Orange else Color(0xffF1F0ED),
                                        RoundedCornerShape(9.dp),
                                    )
                                    .onGloballyPositioned { cellBounds[dayKey] = it.boundsInRoot() }
                                    .clickable { onDate(dayKey) }
                                    .testTag("personal-item-day-$dayKey")
                                    .padding(2.dp),
                            ) {
                                Box(
                                    Modifier.size(24.dp).background(
                                        if (today) C.Amber else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${day.get(Calendar.DAY_OF_MONTH)}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (inMonth || today) C.Ink else Color(0xffB6BBC4),
                                    )
                                }
                            }
                        }
                    }
                    week.forEachIndexed { column, day ->
                        holidaysByDate[date(day)]?.let { marker ->
                            Box(
                                Modifier.offset(x = cellWidth * column + 2.dp, y = 3.dp)
                                    .width(cellWidth - 6.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                HolidayMarker(marker)
                            }
                        }
                    }
                    segments.forEach { segment ->
                        val item = segment.item
                        val itemId = item.long("id")
                        val isDragging = draggingId == itemId
                        val barKey = PersonalItemBarKey(itemId, date(week.first()), segment.start)
                        Box(
                            Modifier.offset(
                                x = cellWidth * segment.start + 2.dp,
                                y = 30.dp + laneHeight * segment.lane,
                            )
                                .width(cellWidth * (segment.end - segment.start + 1) - 4.dp)
                                .height(laneHeight - 2.dp)
                                .onGloballyPositioned { barBounds[barKey] = it.boundsInRoot() }
                                .zIndex(if (isDragging) 5f else 1f)
                                .graphicsLayer {
                                    alpha = if (isDragging) 0.22f else 1f
                                }
                                .clip(RoundedCornerShape(7.dp))
                                .background(tagColor(item.string("color")))
                                .clickable(enabled = !isDragging) { onOpenItem(item) }
                                .testTag("personal-item-bar-$itemId"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                item.string("title"),
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = C.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    previewSegments.forEach { segment ->
                        val item = segment.item
                        val itemId = item.long("id")
                        val previewX by animateDpAsState(
                            targetValue = cellWidth * segment.start + 2.dp,
                            animationSpec = spring(stiffness = 700f),
                            label = "daily-item-preview-x-$itemId-$rowIndex",
                        )
                        val previewWidth by animateDpAsState(
                            targetValue = cellWidth * (segment.end - segment.start + 1) - 4.dp,
                            animationSpec = spring(stiffness = 700f),
                            label = "daily-item-preview-width-$itemId-$rowIndex",
                        )
                        val previewScale by animateFloatAsState(
                            targetValue = 1.025f,
                            animationSpec = spring(stiffness = 500f),
                            label = "daily-item-preview-scale-$itemId-$rowIndex",
                        )
                        Box(
                            Modifier.offset(
                                x = previewX,
                                y = 30.dp + laneHeight * segment.lane,
                            )
                                .width(previewWidth)
                                .height(laneHeight - 2.dp)
                                .zIndex(7f)
                                .graphicsLayer {
                                    alpha = 0.82f
                                    shadowElevation = 9.dp.toPx()
                                    scaleX = previewScale
                                    scaleY = previewScale
                                }
                                .clip(RoundedCornerShape(7.dp))
                                .background(tagColor(item.string("color")))
                                .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(7.dp))
                                .testTag("personal-item-drop-preview-$itemId-$rowIndex"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                item.string("title"),
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = C.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    week.forEachIndexed { column, day ->
                        val count = overflow[column]
                        if (count > 0) {
                            Text(
                                "+${count}项",
                                Modifier.offset(x = cellWidth * column + 3.dp, y = 31.dp + laneHeight * DAILY_ITEM_VISIBLE_BARS)
                                    .clickable { onDate(date(day)) }
                                    .testTag("personal-item-overflow-$rowIndex-$column"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = C.Muted,
                            )
                        }
                    }
                }
            }
            if (!loading && items.isEmpty()) {
                Text(
                    "这个月还没有每日事项，点击“新建事项”开始记录。",
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    fontSize = 13.sp,
                    color = C.Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PersonalDailyItemDayDialog(
    day: String,
    items: List<JsonObject>,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onOpenItem: (JsonObject) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(displayDate(day), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                        Text("${items.size} 项个人备忘", fontSize = 12.sp, color = C.Muted)
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                if (items.isEmpty()) {
                    Text("这一天还没有安排事项。", color = C.Muted, fontSize = 13.sp)
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items.forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenItem(item) }
                                    .testTag("personal-item-day-card-${item.long("id")}"),
                                shape = RoundedCornerShape(15.dp),
                                color = tagColor(item.string("color")),
                            ) {
                                Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                                    Text(item.string("title"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                                    Text(
                                        "${item.string("start_date")} – ${item.string("end_date")}",
                                        fontSize = 11.sp,
                                        color = Color(0xff515861),
                                    )
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = onCreate,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Amber, contentColor = C.Ink),
                ) { Text("＋ 在这天新建", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PersonalDailyItemDetailDialog(
    item: JsonObject,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, tonalElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(21.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(tagColor(item.string("color"))),
                )
                Text(item.string("title"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                Text("${item.string("start_date")} – ${item.string("end_date")}", fontSize = 13.sp, color = C.Muted)
                Text(
                    item.string("description").ifBlank { "暂无说明" },
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 2.dp),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = C.Ink,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("删除", color = C.Error)
                    }
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Amber, contentColor = C.Ink),
                    ) { Text("编辑", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalDailyItemFormDialog(
    api: BusinessApi,
    item: JsonObject?,
    initialDate: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val itemId = item?.long("id")
    var title by remember(itemId, initialDate) { mutableStateOf(item?.string("title").orEmpty()) }
    var startDate by remember(itemId, initialDate) { mutableStateOf(item?.string("start_date") ?: initialDate) }
    var endDate by remember(itemId, initialDate) { mutableStateOf(item?.string("end_date") ?: initialDate) }
    var description by remember(itemId, initialDate) { mutableStateOf(item?.string("description").orEmpty()) }
    var color by remember(itemId, initialDate) { mutableStateOf(item?.string("color") ?: TagColorPalette.first().hex) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (showStartPicker) PersonalDatePicker(startDate, { showStartPicker = false }) {
        startDate = it
        showStartPicker = false
    }
    if (showEndPicker) PersonalDatePicker(endDate, { showEndPicker = false }) {
        endDate = it
        showEndPicker = false
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 700.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(21.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (item == null) "新建每日事项" else "编辑每日事项", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 200) title = it; error = "" },
                    modifier = Modifier.fillMaxWidth().testTag("personal-item-title"),
                    label = { Text("事项标题") },
                    singleLine = true,
                    shape = RoundedCornerShape(13.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = { Text("${title.length}/200") },
                )
                DateField("开始日期", startDate, { showStartPicker = true })
                DateField("结束日期", endDate, { showEndPicker = true })
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().testTag("personal-item-description"),
                    label = { Text("说明（选填）") },
                    minLines = 3,
                    shape = RoundedCornerShape(13.dp),
                )
                Text("选择事项颜色", color = C.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TagColorPalette.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { option ->
                            val selected = color == option.hex
                            Box(
                                Modifier.size(43.dp).clip(CircleShape).background(tagColor(option.hex))
                                    .border(if (selected) 3.dp else 1.dp, if (selected) C.Ink else Color(0x66737C86), CircleShape)
                                    .semantics { contentDescription = "${option.name}色${if (selected) "，已选" else ""}" }
                                    .clickable { color = option.hex }
                                    .testTag("personal-item-color-${option.hex.removePrefix("#")}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Text("✓", color = C.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Text(
                    TagColorPalette.firstOrNull { it.hex == color }?.let { "已选：${it.name}" } ?: "",
                    fontSize = 12.sp,
                    color = C.Muted,
                )
                if (error.isNotBlank()) Text(error, color = C.Error, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (title.isBlank()) { error = "请填写事项标题。"; return@Button }
                            if (endDate < startDate) { error = "结束日期不能早于开始日期。"; return@Button }
                            saving = true
                            error = ""
                            scope.launch {
                                try {
                                    val body = json {
                                        addProperty("title", title.trim())
                                        addProperty("start_date", startDate)
                                        addProperty("end_date", endDate)
                                        addProperty("description", description)
                                        addProperty("color", color)
                                    }
                                    if (itemId == null) api.post(PERSONAL_ITEMS_API, body)
                                    else api.patch("$PERSONAL_ITEMS_API$itemId/", body)
                                    onSaved()
                                } catch (failure: Exception) {
                                    error = failure.businessMessage()
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.weight(1f).testTag("save-personal-daily-item"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Amber, contentColor = C.Ink),
                    ) { Text(if (saving) "保存中…" else "保存", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun DateField(label: String, value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("personal-item-date-$label"),
        shape = RoundedCornerShape(13.dp),
        color = Color(0xffF7F8FA),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xffE6E9EF)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = C.Muted, fontSize = 13.sp)
            Text(displayDate(value), color = C.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("  ▾", color = C.Selected, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalDatePicker(initialDate: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val initialMillis = parseBusinessDate(initialDate)?.let { calendarPickerMillis(businessCalendar(it)) }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onChoose(date(businessCalendar(businessDateFromCalendarPicker(it)))) }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = C.Amber,
                selectedDayContentColor = C.Ink,
                todayDateBorderColor = C.Amber,
                todayContentColor = C.Ink,
            ),
        )
    }
}

private fun displayDate(value: String): String = runCatching {
    val calendar = businessCalendar(parseBusinessDate(value) ?: return value)
    "${calendar.get(Calendar.YEAR)}年${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日"
}.getOrDefault(value)
