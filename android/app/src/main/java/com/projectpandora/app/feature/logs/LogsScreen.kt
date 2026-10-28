package com.projectpandora.app.feature.logs

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
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.ui.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val ROOT = "api/v1/app/"
private fun dateString(year: Int, month: Int, day: Int) = "%04d-%02d-%02d".format(Locale.US, year, month, day)
private fun monthDays(year: Int, month: Int): Int = Calendar.getInstance().apply { set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)

@Composable
fun LogsScreen(api: BusinessApi, initialDate: String = "", onBack: (() -> Unit)? = null) {
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
    if (edit) {
        LogEditor(api, date, selected, onBack = { edit = false }, onSaved = { edit = false; reload++ })
    } else PageColumn {
        if (onBack != null) BackButton(onBack)
        BrandHeader(); PageTitle("日志", small = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { shift(-1) }) { Text("‹ 上月") }
            TextButton(onClick = { quickMonth = true }) { Text("${year}年${month}月") }
            TextButton(onClick = { shift(1) }) { Text("下月 ›") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("日", "一", "二", "三", "四", "五", "六").forEach { Text(it) } }
        val first = remember(year, month) { Calendar.getInstance().apply { set(year, month - 1, 1) }.get(Calendar.DAY_OF_WEEK) - 1 }
        val cells = (0 until first).map { 0 } + (1..monthDays(year, month)).toList()
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().height(300.dp), userScrollEnabled = false) {
            items(cells) { value ->
                if (value == 0) Spacer(Modifier.height(46.dp)) else TextButton(onClick = { day = value }, contentPadding = PaddingValues(0.dp)) {
                    Text("${if (value == day) "[" else ""}$value${if (dateString(year, month, value) in marks) "•" else ""}${if (value == day) "]" else ""}")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(person?.string("display_name") ?: "我的日志")
            TextButton(onClick = {
                scope.launch { try { viewers = api.all(ROOT + "log-viewers/"); choosePerson = true } catch (e: Exception) { error = e.businessMessage() } }
            }) { Text("选择查看对象") }
        }
        if (person != null) TextButton(onClick = { person = null }) { Text("返回我的日志") }
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) { Note(error); TextButton(onClick = { reload++ }) { Text("重试") } }
        Text("$date · ${when (selected?.string("status")) { "draft" -> "草稿"; "submitted" -> "已提交"; else -> "未填写" }}")
        selected?.let { log ->
            Text("已完成工作\n${log.string("completed_work")}")
            Text("明日计划\n${log.string("next_day_plan")}")
            val tags = log.array("tags").joinToString("、") { it.string("name") }
            if (tags.isNotEmpty()) Text("标签：$tags")
        } ?: Text("这一天没有可查看的日志。")
        if (person == null && canEdit) PrimaryButton(if (selected == null) "填写日志" else "编辑日志", { edit = true })
        else if (person == null) Text("该日期只读。")
        Text("历史日志")
        rows.forEach { row -> TextButton(onClick = { day = row.string("log_date").takeLast(2).toIntOrNull() ?: day }) { Text("${row.string("log_date")} · ${if (row.string("status") == "draft") "草稿" else "已提交"}") } }
    }
    if (quickMonth) {
        var y by remember { mutableStateOf(year.toString()) }; var m by remember { mutableStateOf(month.toString()) }
        AlertDialog(onDismissRequest = { quickMonth = false }, title = { Text("选择年月") }, text = {
            Column { OutlinedTextField(y, { y = it }, label = { Text("年") }); OutlinedTextField(m, { m = it }, label = { Text("月") }) }
        }, confirmButton = { TextButton(onClick = { val yy = y.toIntOrNull(); val mm = m.toIntOrNull(); if (yy != null && yy in 1900..2100 && mm != null && mm in 1..12) { year = yy; month = mm; day = minOf(day, monthDays(year, month)); quickMonth = false } }) { Text("确定") } }, dismissButton = { TextButton(onClick = { quickMonth = false }) { Text("取消") } })
    }
    if (choosePerson) {
        var keyword by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { choosePerson = false }, title = { Text("选择查看对象") }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { OutlinedTextField(keyword, { keyword = it }, label = { Text("搜索姓名、团队或部门") }); viewers.filter { listOf(it.string("display_name"), it.string("team_name"), it.string("department_name")).any { s -> s.contains(keyword, true) } }.forEach { p -> Text(p.string("display_name") + " · " + p.string("department_name") + " " + p.string("team_name"), Modifier.fillMaxWidth().clickable { person = p; choosePerson = false }.padding(12.dp)) } }
        }, confirmButton = { TextButton(onClick = { choosePerson = false }) { Text("关闭") } })
    }
}

@Composable
private fun LogEditor(api: BusinessApi, date: String, log: JsonObject?, onBack: () -> Unit, onSaved: () -> Unit) {
    var completed by remember(log) { mutableStateOf(log?.string("completed_work") ?: "") }
    var plan by remember(log) { mutableStateOf(log?.string("next_day_plan") ?: "") }
    var selected by remember(log) { mutableStateOf(log?.array("tags") ?: emptyList()) }
    var tags by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var keyword by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
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
        BackButton(onBack); PageTitle("填写日志", small = true); Text(date)
        OutlinedTextField(completed, { completed = it }, Modifier.fillMaxWidth(), label = { Text("已完成工作（提交必填）") }, minLines = 4)
        OutlinedTextField(plan, { plan = it }, Modifier.fillMaxWidth(), label = { Text("明日计划（选填）") }, minLines = 3)
        Text("标签")
        OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), label = { Text("搜索启用标签") })
        val choices = (selected + tags).distinctBy { it.long("id") }.filter { it.string("name").contains(keyword, true) }
        choices.forEach { tag -> val checked = selected.any { it.long("id") == tag.long("id") }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(tag.string("name"), Modifier.weight(1f).padding(top = 12.dp)); Checkbox(checked, { value -> selected = if (value) selected + tag else selected.filterNot { it.long("id") == tag.long("id") } }) } }
        if (error.isNotBlank()) Note(error)
        PrimaryButton("保存${if (log?.string("status") == "submitted") "修改" else "草稿"}", { save(false) }, enabled = !busy)
        if (log?.string("status") != "submitted") PrimaryButton("提交日志", { save(true) }, enabled = !busy)
    }
}
