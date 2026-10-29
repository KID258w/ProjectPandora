package com.projectpandora.app.feature.logs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.feature.tags.TagSelectionDialog
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val ROOT = "api/v1/app/"
private fun dateString(year: Int, month: Int, day: Int) = "%04d-%02d-%02d".format(Locale.US, year, month, day)
private fun monthDays(year: Int, month: Int): Int = Calendar.getInstance().apply { set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
private fun businessRoleLabel(role: String): String = when (role) {
    "founder" -> "创始人"
    "department_manager" -> "部门经理"
    "team_leader" -> "团队长"
    "employee" -> "员工"
    else -> role.ifBlank { "职位未知" }
}

@Composable
fun LogsScreen(
    api: BusinessApi,
    initialDate: String = "",
    onBack: (() -> Unit)? = null,
    canViewSubordinates: Boolean = false,
) {
    val now = remember { Calendar.getInstance() }
    val parts = remember(initialDate) { initialDate.split("-").mapNotNull { it.toIntOrNull() } }
    var year by remember { mutableIntStateOf(parts.getOrNull(0)?.takeIf { it in 1900..2100 } ?: now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(parts.getOrNull(1)?.takeIf { it in 1..12 } ?: (now.get(Calendar.MONTH) + 1)) }
    var day by remember { mutableIntStateOf(parts.getOrNull(2)?.takeIf { it in 1..monthDays(year, month) } ?: now.get(Calendar.DAY_OF_MONTH)) }
    var person by remember { mutableStateOf<JsonObject?>(null) }
    var selected by remember { mutableStateOf<JsonObject?>(null) }
    var canEdit by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var viewers by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var marks by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var choosePerson by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var quickMonth by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    BackHandler(enabled = edit) { edit = false }
    val scope = rememberCoroutineScope()
    val date = dateString(year, month, day)
    fun shift(delta: Int) {
        val calendar = Calendar.getInstance().apply { set(year, month - 1, 1); add(Calendar.MONTH, delta) }
        year = calendar.get(Calendar.YEAR); month = calendar.get(Calendar.MONTH) + 1
        day = minOf(day, monthDays(year, month))
    }
    LaunchedEffect(year, month, person, reload) {
        loading = true; error = ""; rows = emptyList(); marks = emptyList()
        try {
            val query = mutableMapOf("year" to year.toString(), "month" to month.toString())
            person?.let { query["user_id"] = it.long("id").toString() }
            marks = api.get(ROOT + "logs/calendar/", query).data().array("days").filter { it.bool("has_submitted_log") }.map { it.string("date") }
            val listQuery = mutableMapOf("start_date" to dateString(year, month, 1), "end_date" to dateString(year, month, monthDays(year, month)))
            person?.let { listQuery["user_id"] = it.long("id").toString() }
            rows = api.all(ROOT + "logs/", listQuery)
        } catch (e: Exception) { error = e.businessMessage() } finally { loading = false }
    }
    LaunchedEffect(date, person, reload) {
        selected = null; canEdit = false
        try {
            if (person == null) {
                val data = api.get(ROOT + "logs/mine/$date/").data()
                selected = data.obj("log"); canEdit = data.bool("can_edit")
            } else {
                val row = rows.firstOrNull { it.string("log_date") == date && it.string("status") == "submitted" }
                if (row != null) selected = api.get(ROOT + "logs/${row.long("id")}/").data()
            }
        } catch (e: Exception) { error = e.businessMessage() }
    }
    LaunchedEffect(rows, person) {
        if (person != null) {
            selected = null
            val row = rows.firstOrNull { it.string("log_date") == date && it.string("status") == "submitted" }
            if (row != null) try { selected = api.get(ROOT + "logs/${row.long("id")}/").data() } catch (e: Exception) { error = e.businessMessage() }
        }
    }
    Box(Modifier.fillMaxSize()) {
    if (edit) {
        LogEditor(api, date, selected, onBack = { edit = false }, onSaved = { edit = false; reload++ })
    } else PageColumn {
        if (onBack != null) BackButton(onBack)
        BrandHeader(); PageTitle("日志", small = true)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, C.Line),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { shift(-1) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("‹") }
                    TextButton(onClick = { quickMonth = true }) { Text("${year}年${month}月", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.Ink) }
                    TextButton(onClick = { shift(1) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("›") }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { weekday ->
                        Text(weekday, Modifier.weight(1f), textAlign = TextAlign.Center, color = C.Muted, fontSize = 12.sp)
                    }
                }
                val first = remember(year, month) { Calendar.getInstance().apply { set(year, month - 1, 1) }.get(Calendar.DAY_OF_WEEK) - 1 }
                val cells = (0 until first).map { 0 } + (1..monthDays(year, month)).toList()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().height(294.dp),
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(cells) { value ->
                        if (value == 0) Spacer(Modifier.fillMaxWidth().aspectRatio(1f)) else {
                            val isSelected = value == day
                            val hasLog = dateString(year, month, value) in marks
                            Column(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { day = value },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(if (isSelected) C.Amber else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(value.toString(), color = C.Ink, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                                Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(if (hasLog) C.Orange else Color.Transparent))
                            }
                        }
                    }
                }
                Text("圆点表示当天有已提交日志", color = C.Muted, fontSize = 12.sp)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, C.Line),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("查看对象", fontSize = 12.sp, color = C.Muted)
                    val current = person?.let { listOf(it.string("display_name"), businessRoleLabel(it.string("business_role")), it.string("department_name"), it.string("team_name")).filter(String::isNotBlank).joinToString(" · ") } ?: "本人"
                    Text(current, fontWeight = FontWeight.SemiBold, color = C.Ink)
                }
                if (person != null) TextButton(onClick = { person = null }) { Text("返回本人") }
                if (canViewSubordinates) {
                    TextButton(onClick = {
                        scope.launch { try { viewers = api.all(ROOT + "log-viewers/"); choosePerson = true } catch (e: Exception) { error = e.businessMessage() } }
                    }) { Text("选择对象") }
                }
            }
        }
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) { Note(error); TextButton(onClick = { reload++ }) { Text("重试") } }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, C.Line),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(date, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    if (person == null) LogStatusBadge(when (selected?.string("status")) { "draft" -> "草稿"; "submitted" -> "已提交"; else -> "未填写" })
                    else Text("只读", color = C.Muted, fontSize = 12.sp)
                }
                if (selected == null) {
                    Text(if (person == null) "这一天还没有日志。" else "该日期暂无可查看的日志。", color = C.Muted)
                } else {
                    Text("当日完成", fontWeight = FontWeight.SemiBold, color = C.Ink)
                    Text(selected!!.string("completed_work"), color = C.Ink, lineHeight = 22.sp)
                    val plan = selected!!.string("next_day_plan")
                    if (plan.isNotBlank()) {
                        Text("次日计划", fontWeight = FontWeight.SemiBold, color = C.Ink)
                        Text(plan, color = C.Ink, lineHeight = 22.sp)
                    }
                    val tagNames = selected!!.array("tags").joinToString("、") { it.string("name") }
                    if (tagNames.isNotBlank()) Text("关联标签：$tagNames", color = C.Muted)
                    val updatedAt = selected!!.string("updated_at")
                    if (updatedAt.isNotBlank()) Text("最后更新：${displayLogTimestamp(updatedAt)}", color = C.Muted, fontSize = 12.sp)
                }
                if (person == null && !canEdit) Text("该日期不可编辑。", color = C.Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(80.dp))
    }
    if (!edit && person == null && canEdit) FloatingActionButton(onClick = { edit = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("edit-log").semantics { contentDescription = if (selected == null) "填写日志" else "编辑日志" }, shape = androidx.compose.foundation.shape.CircleShape) { Text("＋") }
    }
    if (quickMonth) {
        var y by remember { mutableStateOf(year.toString()) }; var m by remember { mutableStateOf(month.toString()) }
        AlertDialog(onDismissRequest = { quickMonth = false }, title = { Text("选择年月") }, text = {
            Column { OutlinedTextField(y, { y = it }, label = { Text("年") }); OutlinedTextField(m, { m = it }, label = { Text("月") }) }
        }, confirmButton = { TextButton(onClick = { val yy = y.toIntOrNull(); val mm = m.toIntOrNull(); if (yy != null && yy in 1900..2100 && mm != null && mm in 1..12) { year = yy; month = mm; day = minOf(day, monthDays(year, month)); quickMonth = false } }) { Text("确定") } }, dismissButton = { TextButton(onClick = { quickMonth = false }) { Text("取消") } })
    }
    if (choosePerson) {
        var keyword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { choosePerson = false },
            title = { Text("选择查看对象") },
            text = {
                Column {
                    OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), label = { Text("搜索姓名、团队或部门") })
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        val matches = viewers.filter { listOf(it.string("display_name"), it.string("team_name"), it.string("department_name")).any { value -> value.contains(keyword, true) } }
                        if (matches.isEmpty()) Text("没有找到授权范围内的对象。", Modifier.padding(vertical = 12.dp), color = C.Muted)
                        matches.forEach { viewer ->
                            TextButton(onClick = { person = viewer; choosePerson = false }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text("${viewer.string("display_name")} · ${businessRoleLabel(viewer.string("business_role"))}", color = C.Ink, fontWeight = FontWeight.Medium)
                                    Text(listOf(viewer.string("department_name"), viewer.string("team_name")).filter(String::isNotBlank).joinToString(" · "), color = C.Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosePerson = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun LogEditor(api: BusinessApi, date: String, log: JsonObject?, onBack: () -> Unit, onSaved: () -> Unit) {
    var completed by remember(log) { mutableStateOf(log?.string("completed_work") ?: "") }
    var plan by remember(log) { mutableStateOf(log?.string("next_day_plan") ?: "") }
    var selected by remember(log) { mutableStateOf(log?.array("tags") ?: emptyList()) }
    var tags by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var showTagPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { tags = api.all(ROOT + "tags/") } catch (e: Exception) { error = e.businessMessage() } }
    fun save(submit: Boolean) {
        if (submit && completed.isBlank()) { error = "提交时请填写已完成工作。"; return }
        scope.launch {
            busy = true; error = ""
            try {
                val body = json { addProperty("completed_work", completed); addProperty("next_day_plan", plan); add("tag_ids", ids(selected.map { it.long("id") })) }
                api.put(ROOT + "logs/mine/$date/", body)
                if (submit) api.post(ROOT + "logs/mine/$date/submit/")
                onSaved()
            } catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
        }
    }
    PageColumn {
        BackButton(onBack); PageTitle("填写日志", small = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(date, fontWeight = FontWeight.SemiBold, color = C.Ink)
            log?.let { LogStatusBadge(if (it.string("status") == "submitted") "已提交" else "草稿") }
        }
        OutlinedTextField(completed, { completed = it }, Modifier.fillMaxWidth(), label = { Text("已完成工作（提交必填）") }, minLines = 4)
        OutlinedTextField(plan, { plan = it }, Modifier.fillMaxWidth(), label = { Text("明日计划（选填）") }, minLines = 3)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showTagPicker = true }) { Text("选择标签") }
            Text(selected.joinToString("、") { it.string("name") }.ifBlank { "未选择" }, Modifier.weight(1f).padding(start = 12.dp))
        }
        if (error.isNotBlank()) Note(error)
        PrimaryButton("保存${if (log?.string("status") == "submitted") "修改" else "草稿"}", { save(false) }, enabled = !busy)
        if (log?.string("status") != "submitted") PrimaryButton("提交日志", { save(true) }, enabled = !busy)
    }
    if (showTagPicker) {
        TagSelectionDialog(
            activeTags = tags,
            selectedTags = selected,
            onDismiss = { showTagPicker = false },
            onConfirm = { selected = it; showTagPicker = false },
        )
    }
}

@Composable
private fun LogStatusBadge(label: String) {
    val (background, foreground) = when (label) {
        "已提交" -> C.TealPale to C.Teal
        "草稿" -> C.AmberPale to C.Orange
        else -> C.BluePale to C.Blue
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(50)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun displayLogTimestamp(value: String): String = value.replace('T', ' ').take(16)
