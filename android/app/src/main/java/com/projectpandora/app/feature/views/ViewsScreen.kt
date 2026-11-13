package com.projectpandora.app.feature.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.google.gson.JsonObject
import com.projectpandora.app.R
import com.projectpandora.app.core.network.*
import com.projectpandora.app.ui.BackButton
import com.projectpandora.app.ui.theme.PandoraColors as C
import java.text.SimpleDateFormat
import java.util.*

/** Work-view dates, “today” and event hours follow the business time zone (backend TIME_ZONE), not the device zone. */
internal val BUSINESS_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
private fun format(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = BUSINESS_TIME_ZONE }
private fun cal(time: Long = System.currentTimeMillis()) = Calendar.getInstance(BUSINESS_TIME_ZONE).apply { timeInMillis = time; firstDayOfWeek = Calendar.MONDAY }
internal fun businessCalendar(time: Long = System.currentTimeMillis()): Calendar = cal(time)
internal fun date(c: Calendar): String = format("yyyy-MM-dd").format(c.time)
internal fun parseBusinessDate(value: String): Long? = runCatching { format("yyyy-MM-dd").apply { isLenient = false }.parse(value)!!.time + 12 * 3_600_000L }.getOrNull()
private fun weekStart(c: Calendar) = cal(c.timeInMillis).apply { add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7)) }
internal val MONTH_WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/** Shared Monday-first calendar grid used by work views and personal daily items. */
internal fun monthCalendarWeeks(
    current: Calendar,
    gridStart: Calendar? = null,
    requestedRows: Int? = null,
): List<List<Calendar>> {
    val first = cal(current.timeInMillis).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val rows = requestedRows ?: ((offset + first.getActualMaximum(Calendar.DAY_OF_MONTH) + 6) / 7)
    val start = gridStart ?: cal(first.timeInMillis).apply { add(Calendar.DAY_OF_MONTH, -offset) }
    return (0 until rows).map { row -> (0..6).map { column ->
        cal(start.timeInMillis).apply { add(Calendar.DAY_OF_MONTH, row * 7 + column) }
    } }
}

internal fun calendarPickerMillis(current: Calendar): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
    clear()
    set(current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH))
}.timeInMillis

internal fun businessDateFromCalendarPicker(value: Long): Long {
    val chosen = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = value }
    return cal().apply {
        clear()
        set(chosen.get(Calendar.YEAR), chosen.get(Calendar.MONTH), chosen.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}

/** Parses server timestamps (UTC `Z` or an offset, optional micro-seconds) into the business-time-zone calendar. */
internal fun eventLocalTime(value: String): Calendar? {
    val normalized = value.replace(Regex("\\.(\\d{3})\\d+(?=Z|[+-]\\d{2}:\\d{2}$)"), ".$1")
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")
    for (pattern in patterns) {
        val instant = runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(normalized) }.getOrNull()
        if (instant != null) return cal(instant.time)
    }
    return null
}

/** True when [selected] lies in the same month / week / day period as [today]. */
internal fun isCurrentPeriod(mode: String, selected: Calendar, today: Calendar = cal()): Boolean = when (mode) {
    "月" -> selected.get(Calendar.YEAR) == today.get(Calendar.YEAR) && selected.get(Calendar.MONTH) == today.get(Calendar.MONTH)
    "周" -> date(weekStart(selected)) == date(weekStart(today))
    else -> date(selected) == date(today)
}

private fun color(value: String): Color = try { Color(android.graphics.Color.parseColor(value)) } catch (_: Exception) { Color(0xffD1D5DB) }
internal fun eventLabel(type: String) = mapOf(
    "created" to "创建任务", "created_and_submitted" to "创建并提交审批", "updated" to "修改任务",
    "resubmitted" to "重新提交审批", "approval_approved" to "审批通过", "approval_returned" to "审批退回",
    "cancelled" to "取消任务", "progress_updated" to "更新进展", "completion_submitted" to "提交完成",
    "submitted_and_completed" to "提交并完成", "acceptance_approved" to "验收通过", "acceptance_returned" to "验收退回",
)[type] ?: type

/**
 * Month / week / day work views. [memberId] = 0 shows the signed-in user; otherwise the
 * authorized member opened from Organization Relationships (read-only).
 */
@Composable
fun ViewsScreen(
    api: BusinessApi,
    canViewOrganization: Boolean,
    onOpenTaskCenter: () -> Unit,
    onOpenOrganization: () -> Unit,
    onTask: (Long, Boolean) -> Unit,
    memberId: Long = 0L,
    memberName: String = "",
    onBackToMember: (() -> Unit)? = null,
) {
    val viewingMember = memberId != 0L
    var mode by rememberSaveable { mutableStateOf("月") }
    var selected by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var expandAllDayOnEntry by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val current = cal(selected)
    if (viewingMember && onBackToMember != null) BackHandler { onBackToMember() }
    LaunchedEffect(mode, selected, reload, memberId) {
        data = null; error = ""; busy = true
        try {
            val query = if (mode == "月") mapOf("year" to current.get(Calendar.YEAR).toString(), "month" to (current.get(Calendar.MONTH) + 1).toString())
                else mapOf("date" to date(current))
            val path = "api/v1/app/work-views/" + when (mode) { "月" -> "month/"; "周" -> "week/"; else -> "day/" }
            data = api.get(path, if (viewingMember) query + ("user_id" to memberId.toString()) else query).data()
        } catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
    }
    if (showMonthPicker) MonthPicker(current, { showMonthPicker = false }) { year, month ->
        val c = cal(selected); c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.YEAR, year); c.set(Calendar.MONTH, month)
        selected = c.timeInMillis; showMonthPicker = false
    }
    if (showDatePicker) WorkViewDateCalendarDialog(current, mode, { showDatePicker = false }) {
        expandAllDayOnEntry = false
        selected = it
        showDatePicker = false
    }
    fun shift(step: Int) {
        expandAllDayOnEntry = false
        val c = cal(selected)
        c.add(when (mode) { "月" -> Calendar.MONTH; "周" -> Calendar.WEEK_OF_YEAR; else -> Calendar.DAY_OF_MONTH }, step)
        selected = c.timeInMillis
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(start = if (viewingMember) 4.dp else 20.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (viewingMember && onBackToMember != null) BackButton(onBackToMember)
            Column(Modifier.weight(1f).padding(start = if (viewingMember) 8.dp else 0.dp)) {
                Text("${memberName.ifBlank { if (viewingMember) "成员" else "我" }}的视图", fontSize = 19.sp,
                    fontWeight = FontWeight.Bold, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (viewingMember) Text("下属工作视图 · 只读", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = C.Muted)
            }
            ViewHeaderAction("任务中心", "task-center-entry", onOpenTaskCenter)
            if (canViewOrganization) {
                Spacer(Modifier.width(6.dp))
                ViewHeaderAction("组织关系", "organization-relationships-entry", onOpenOrganization)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).background(Color(0xffF5F5F3), RoundedCornerShape(12.dp)).padding(3.dp)) {
            listOf("月", "周", "日").forEach { item ->
                Box(Modifier.weight(1f).height(34.dp).background(if (mode == item) Color.White else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { expandAllDayOnEntry = false; mode = item }.testTag("mode-$item"), contentAlignment = Alignment.Center) {
                    Text(item, color = if (mode == item) C.Ink else C.Muted, fontWeight = if (mode == item) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PeriodArrow(previous = true, description = "上一周期") { shift(-1) }
            TextButton(onClick = {
                if (mode == "月") showMonthPicker = true
                else showDatePicker = true
            }, modifier = Modifier.weight(1f).testTag("work-view-period-picker")) {
                Text("${current.get(Calendar.YEAR)}年 ${current.get(Calendar.MONTH) + 1}月" + (if (mode == "日") " ${current.get(Calendar.DAY_OF_MONTH)}日" else "") + " ▾",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink)
            }
            if (!isCurrentPeriod(mode, current)) TextButton(onClick = { expandAllDayOnEntry = false; selected = System.currentTimeMillis() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(when (mode) { "月" -> "本月"; "周" -> "本周"; else -> "今天" }, color = C.Selected, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            PeriodArrow(previous = false, description = "下一周期") { shift(1) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = C.Amber)
        if (error.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f), color = C.Error, fontSize = 12.sp); TextButton(onClick = { reload++ }) { Text("重试") }
        }
        val openTask: (Long) -> Unit = { onTask(it, viewingMember) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Image(painterResource(R.drawable.pandora_footer_art), contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(110.dp).align(Alignment.BottomCenter).alpha(0.14f),
                alignment = Alignment.BottomCenter, contentScale = ContentScale.Crop)
            if (mode == "月") MonthGrid(
                current = current,
                data = data,
                onTask = openTask,
                onDate = { selected = it; expandAllDayOnEntry = false; mode = "日" },
                onOverflow = { selected = it; expandAllDayOnEntry = true; mode = "日" },
            ) else Timeline(
                current = current,
                mode = mode,
                data = data,
                onDate = { selected = it; expandAllDayOnEntry = false; if (mode == "周") mode = "日" },
                onTask = openTask,
                expandAllDayInitially = expandAllDayOnEntry,
            )
        }
    }
}

@Composable
private fun ViewHeaderAction(label: String, tag: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xffF7F8FA))
            .border(1.dp, Color(0xffE6E9EF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag(tag),
        color = C.Selected,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
internal fun PeriodArrow(previous: Boolean, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        Canvas(Modifier.size(20.dp)) {
            val left = size.width * 0.32f
            val right = size.width * 0.68f
            val startX = if (previous) right else left
            val tipX = if (previous) left else right
            val stroke = 3.dp.toPx()
            drawLine(C.Ink, Offset(startX, size.height * 0.18f), Offset(tipX, size.height * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(C.Ink, Offset(tipX, size.height * 0.5f), Offset(startX, size.height * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun MonthPicker(current: Calendar, onDismiss: () -> Unit, onChoose: (Int, Int) -> Unit) {
    var year by remember { mutableIntStateOf(current.get(Calendar.YEAR)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择年月") }, text = {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PeriodArrow(previous = true, description = "上一年") { year-- }
                Text("${year}年", fontWeight = FontWeight.Bold)
                PeriodArrow(previous = false, description = "下一年") { year++ }
            }
            repeat(4) { row -> Row(Modifier.fillMaxWidth()) {
                repeat(3) { col -> val month = row * 3 + col
                    TextButton(onClick = { onChoose(year, month) }, modifier = Modifier.weight(1f)) { Text("${month + 1}月", color = if (year == current.get(Calendar.YEAR) && month == current.get(Calendar.MONTH)) C.Selected else C.Ink) }
                }
            } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkViewDateCalendarDialog(current: Calendar, mode: String, onDismiss: () -> Unit, onChoose: (Long) -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = calendarPickerMillis(current))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("work-view-date-dialog"),
        confirmButton = {
            TextButton(
                enabled = picker.selectedDateMillis != null,
                onClick = { picker.selectedDateMillis?.let { onChoose(businessDateFromCalendarPicker(it)) } },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(
            state = picker,
            modifier = Modifier.testTag("work-view-calendar"),
            showModeToggle = false,
            title = {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (mode == "周") "选择所在周" else "选择日期", color = C.Ink)
                    Text(if (mode == "周") "点选日期，查看它所在的一周" else "点选日期，查看当天视图", color = C.Muted, fontSize = 12.sp)
                }
            },
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = C.Amber,
                selectedDayContentColor = C.Ink,
                todayDateBorderColor = C.Amber,
                todayContentColor = C.Ink,
            ),
        )
    }
}

internal data class MonthSegment(val bar: JsonObject, val start: Int, var end: Int, var lane: Int = 0)

/** Merge only the bars the server chose to expose for each individual day. */
internal fun weekSegments(cells: List<JsonObject?>): List<MonthSegment> {
    val segments = mutableListOf<MonthSegment>()
    val active = mutableMapOf<Long, MonthSegment>()
    cells.forEachIndexed { column, day ->
        val bars = day?.array("bars")?.take(3).orEmpty()
        val ids = bars.map { it.long("task_id") }.toSet()
        active.keys.toList().filter { it !in ids }.forEach { active.remove(it) }
        bars.forEach { bar ->
            val id = bar.long("task_id")
            val existing = active[id]
            if (existing != null) existing.end = column
            else MonthSegment(bar, column, column).also { segments.add(it); active[id] = it }
        }
    }
    val laneEnds = IntArray(3) { -1 }
    segments.sortedWith(compareBy<MonthSegment> { it.start }.thenBy { it.bar.long("task_id") }).forEach { segment ->
        val lane = laneEnds.indexOfFirst { it < segment.start }
        if (lane >= 0) { segment.lane = lane; laneEnds[lane] = segment.end }
    }
    return segments
}

@Composable private fun MonthGrid(
    current: Calendar,
    data: JsonObject?,
    onTask: (Long) -> Unit,
    onDate: (Long) -> Unit,
    onOverflow: (Long) -> Unit,
) {
    val first = cal(current.timeInMillis).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val count = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val apiDays = data?.array("days").orEmpty()
    val days = apiDays.associateBy { it.string("date") }
    val calendarDays = data?.array("calendar_days")?.associateBy { it.string("date") } ?: emptyMap()
    // Keep the calendar's natural month rows even if an API response omits
    // date entries with no tasks. Complete API responses can still extend the
    // grid for their adjacent-month dates.
    val rows = maxOf((offset + count + 6) / 7, (apiDays.size + 6) / 7)
    val gridStart = data?.string("calendar_start_date")?.let(::parseBusinessDate)?.let(::cal)
    val weekDates = monthCalendarWeeks(current, gridStart, rows)
    val weekCells = weekDates.map { dates -> dates.map { day -> days[date(day)] } }
    val weekBars = weekCells.map(::weekSegments)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        val laneHeight = with(LocalDensity.current) { maxOf(30.dp, 18.sp.toDp() + 10.dp) }
        val minHeights = weekCells.mapIndexed { row, cells ->
            val lanes = (weekBars[row].maxOfOrNull { it.lane } ?: -1) + 1
            val overflow = cells.any { (it?.long("overflow_count") ?: 0) > 0 }
            maxOf(56.dp, 28.dp + laneHeight * lanes + if (overflow) 22.dp else 4.dp)
        }
        val extraHeight = ((maxHeight - 40.dp - minHeights.fold(0.dp) { sum, height -> sum + height }) / rows).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { MONTH_WEEKDAYS.forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Muted) } } }
        repeat(rows) { row ->
            val dates = weekDates[row]
            val cells = weekCells[row]
            BoxWithConstraints(Modifier.fillMaxWidth().height(minHeights[row] + extraHeight)) {
                val cellWidth = maxWidth / 7
                Row(Modifier.fillMaxSize()) { dates.forEach { day ->
                    val dayKey = date(day)
                    val inSelectedMonth = day.get(Calendar.YEAR) == first.get(Calendar.YEAR) && day.get(Calendar.MONTH) == first.get(Calendar.MONTH)
                    val isToday = dayKey == date(cal())
                    Box(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color(0xffF1F0ED))
                        .clickable { onDate(day.timeInMillis) }
                        .testTag(if (inSelectedMonth) "month-day-${day.get(Calendar.DAY_OF_MONTH)}" else "month-adjacent-day-$dayKey")
                        .padding(2.dp)) {
                        Box(Modifier.size(24.dp).background(if (isToday) C.Amber else Color.Transparent, RoundedCornerShape(12.dp)), contentAlignment=Alignment.Center) {
                            Text("${day.get(Calendar.DAY_OF_MONTH)}", fontSize=14.sp, fontWeight=FontWeight.SemiBold,
                                color = if (inSelectedMonth || isToday) C.Ink else Color(0xffB6BBC4))
                        }
                    }
                } }
                cells.forEachIndexed { column, cell ->
                    val marker = cell?.string("date")?.let(calendarDays::get)
                    if (marker != null) {
                        Box(
                            Modifier.offset(x = cellWidth * column + 2.dp, y = 3.dp).width(cellWidth - 6.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            HolidayMarker(marker)
                        }
                    }
                }
                weekBars[row].forEach { segment ->
                    Box(Modifier.offset(x=cellWidth * segment.start + 2.dp, y=28.dp + laneHeight * segment.lane)
                        .width(cellWidth * (segment.end - segment.start + 1) - 4.dp).height(laneHeight - 2.dp)
                        .background(color(segment.bar.string("bar_color")), RoundedCornerShape(3.dp))
                        .clickable { onTask(segment.bar.long("task_id")) }.padding(horizontal=4.dp), contentAlignment=Alignment.Center) {
                        Text(segment.bar.string("title"), modifier=Modifier.fillMaxWidth(), textAlign=TextAlign.Center, fontSize=13.sp, lineHeight=18.sp, fontWeight=FontWeight.SemiBold, maxLines=1, overflow=TextOverflow.Ellipsis, color=C.Ink)
                    }
                }
                cells.forEachIndexed { column, day -> val more = day?.long("overflow_count") ?: 0
                    if (more > 0) Text("+${more}项", Modifier.offset(x=cellWidth * column + 3.dp, y=30.dp + laneHeight * 3)
                        .clickable { day?.string("date")?.let { value -> parseBusinessDate(value)?.let(onOverflow) } }.testTag("month-overflow-$column"), fontSize=11.sp, fontWeight=FontWeight.SemiBold, color=C.Muted)
                }
            }
        }
        }
    }
}

@Composable
internal fun HolidayMarker(marker: JsonObject) {
    val type = marker.string("day_type")
    val (backgroundColor, textColor, description) = when (type) {
        "holiday" -> Triple(Color(0xffFDECE8), Color(0xffA95E50), "法定节假日")
        "rest_day" -> Triple(Color(0xffF7F3EB), Color(0xff907A5D), "节假日休息")
        else -> Triple(Color(0xffEAF2F0), Color(0xff52796F), "调休上班")
    }
    Text(
        text = marker.string("label"),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 3.dp, vertical = 1.dp)
            .semantics { contentDescription = "$description：${marker.string("holiday_name")}" }
            .testTag("holiday-${marker.string("date")}"),
        color = textColor,
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable private fun Timeline(
    current: Calendar,
    mode: String,
    data: JsonObject?,
    onDate: (Long) -> Unit,
    onTask: (Long) -> Unit,
    expandAllDayInitially: Boolean,
) {
    val start = weekStart(current)
    val dates = (0..6).map { cal(start.timeInMillis).apply { add(Calendar.DAY_OF_MONTH, it) } }
    val today = date(cal())
    val calendarDays = data?.array("calendar_days")?.associateBy { it.string("date") } ?: emptyMap()
    var allDayExpanded by remember(mode, date(current)) { mutableStateOf(expandAllDayInitially) }
    val events = remember(data) {
        data?.array("events").orEmpty().mapNotNull { event -> eventLocalTime(event.string("occurred_at"))?.let { event to it } }
            .sortedBy { it.second.timeInMillis }
    }
    Column(Modifier.fillMaxSize()) {
        // Keep the date strip and all-day task area pinned; the time axis uses the remaining viewport.
        Row(Modifier.fillMaxWidth().padding(start = 42.dp, end = 8.dp, top = 6.dp)) {
            dates.forEach { day ->
                val key = date(day)
                val highlighted = key == date(current)
                Column(Modifier.weight(1f).clickable { onDate(day.timeInMillis) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(MONTH_WEEKDAYS[(day.get(Calendar.DAY_OF_WEEK) + 5) % 7], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Muted)
                    Box(Modifier.padding(vertical = 5.dp).size(28.dp).background(if (highlighted) C.Amber else Color.Transparent, RoundedCornerShape(14.dp))
                        .then(if (!highlighted && key == today) Modifier.border(1.5.dp, C.Amber, RoundedCornerShape(14.dp)) else Modifier)
                        .semantics { contentDescription = if (highlighted) "已选 $key" else key },
                        contentAlignment = Alignment.Center) { Text("${day.get(Calendar.DAY_OF_MONTH)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink) }
                    calendarDays[key]?.let { HolidayMarker(it) }
                }
            }
        }
        val bars = data?.array("all_day_tasks").orEmpty()
        if (bars.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 6.dp)) {
            Column(
                Modifier.width(42.dp)
                    .then(if (bars.size > 3) Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { allDayExpanded = !allDayExpanded }
                        .semantics {
                            contentDescription = if (allDayExpanded) "收起全天任务" else "展开全天任务，共${bars.size}项"
                        } else Modifier)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("全天", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = C.Muted, textAlign = TextAlign.Center)
                if (bars.size > 3) {
                    Text(
                        if (allDayExpanded) "收起⌃" else "展开⌄",
                        modifier = Modifier.testTag("all-day-task-toggle"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = C.Selected,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            BoxWithConstraints(Modifier.weight(1f).testTag("all-day-task-grid")) {
                val available = maxWidth
                val taskRows = if (allDayExpanded) bars else bars.take(3)
                val taskListModifier = if (allDayExpanded) {
                    Modifier.heightIn(max = maxHeight * 0.45f).verticalScroll(rememberScrollState())
                } else {
                    Modifier.heightIn(max = 132.dp)
                }
                Column(taskListModifier) {
                    taskRows.forEach { bar ->
                        val startColumn = if (mode == "周") dates.indexOfFirst { date(it) == bar.string("start_date") } else 0
                        val endColumn = if (mode == "周") dates.indexOfFirst { date(it) == bar.string("end_date") } else 0
                        if (startColumn < 0 || endColumn < startColumn) return@forEach
                        val columnCount = if (mode == "周") 7 else 1
                        Box(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                            Text(bar.string("title"), Modifier.offset(x = available * (startColumn.toFloat() / columnCount))
                                .width(available * ((endColumn - startColumn + 1).toFloat() / columnCount))
                                .testTag("all-day-task-${bar.long("task_id")}")
                                .background(color(bar.string("bar_color")), RoundedCornerShape(5.dp)).clickable { onTask(bar.long("task_id")) }
                                .padding(horizontal = 8.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink,
                                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xffEEEFEF))
        val columns = if (mode == "周") dates else listOf(current)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            repeat(24) { hour ->
                // IntrinsicSize.Min keeps one shared height for the hour across all columns, grown by the tallest cell.
                Row(Modifier.fillMaxWidth().padding(end = 8.dp).height(IntrinsicSize.Min)) {
                    Text("%02d:00".format(hour), Modifier.width(42.dp).padding(top = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = C.Muted, textAlign = TextAlign.Center)
                    columns.forEach { day ->
                        val key = date(day)
                        Column(Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).fillMaxHeight().border(0.5.dp, Color(0xffEEEFEF)).padding(2.dp)) {
                            events.filter { (_, time) -> date(time) == key && time.get(Calendar.HOUR_OF_DAY) == hour }.forEach { (event, time) ->
                                Column(Modifier.fillMaxWidth().padding(bottom = 3.dp).background(color(event.string("tag_color")), RoundedCornerShape(5.dp))
                                    .clickable { onTask(event.long("task_id")) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(format("HH:mm").format(time.time), textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                                    Text(event.string("task_title"), textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                                    Text(eventLabel(event.string("event_type")), textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = C.Ink)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
