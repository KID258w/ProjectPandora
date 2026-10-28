package com.projectpandora.app.feature.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val ROOT = "api/v1/app/"

@Composable
fun TaskCenterScreen(api: BusinessApi, onBack: () -> Unit, initialTaskId: Long? = null) {
    var page by remember { mutableStateOf(if (initialTaskId == null) "list" else "detail") }
    var section by remember { mutableStateOf("mine") }
    var taskId by remember { mutableLongStateOf(initialTaskId ?: 0L) }
    var task by remember { mutableStateOf<JsonObject?>(null) }
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var history by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var people by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var me by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("all") }; var lifecycle by remember { mutableStateOf("active") }
    var startDate by remember { mutableStateOf("") }; var endDate by remember { mutableStateOf("") }
    var subordinateQuery by remember { mutableStateOf("") }; var subordinateScope by remember { mutableStateOf("assigned") }
    var selectedPerson by remember { mutableStateOf<JsonObject?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { me = api.get(ROOT + "me/").data().long("id") } catch (e: Exception) { error = e.businessMessage() } }
    LaunchedEffect(section, reload, page) {
        if (page != "list") return@LaunchedEffect
        busy = true; error = ""; rows = emptyList()
        try {
            val filters = mutableMapOf("completion_status" to status, "lifecycle_status" to lifecycle)
            if (startDate.isNotBlank()) filters["due_from"] = startDate
            if (endDate.isNotBlank()) filters["due_to"] = endDate
            rows = when (section) {
                "inbox", "outbox" -> api.all(ROOT + "task-approvals/$section/")
                "subordinates" -> { filters["task_scope"] = subordinateScope; selectedPerson?.let { filters["user_id"] = it.long("id").toString() }; api.all(ROOT + "tasks/subordinates/", filters) }
                else -> { filters["scope"] = section; api.all(ROOT + "tasks/", filters) }
            }
        } catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
    }
    LaunchedEffect(taskId, page, reload) {
        if (page != "detail" || taskId == 0L) return@LaunchedEffect
        busy = true; error = ""; task = null
        try { task = api.get(ROOT + "tasks/$taskId/").data(); history = api.get(ROOT + "tasks/$taskId/history/").data().array("results") }
        catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
    }
    fun open(id: Long) { taskId = id; page = "detail" }
    fun back() { if (page == "list") onBack() else { page = "list"; reload++ } }
    if (page == "form") {
        TaskForm(api, task, onBack = ::back, onSaved = { page = "list"; reload++ })
        return
    }
    if (page == "detail") {
        TaskDetail(api, task, history, me, section == "inbox", section == "subordinates", busy, error, onBack = ::back,
            onEdit = { page = "form" }, onRefresh = { reload++ })
        return
    }
    PageColumn {
        BackButton(onBack); PageTitle("任务中心", small = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { section = "mine" }) { Text("我的任务") }
            TextButton(onClick = { section = "created" }) { Text("我创建的") }
            TextButton(onClick = { section = "subordinates"; scope.launch { try { people = api.all(ROOT + "organization/visible-subordinates/") } catch (e: Exception) { error = e.businessMessage() } } }) { Text("下属") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { section = "inbox" }) { Text("待我审批") }
            TextButton(onClick = { section = "outbox" }) { Text("我发出的审批") }
        }
        Text("当前：${when (section) { "mine" -> "我的任务"; "created" -> "我创建的"; "subordinates" -> "下属任务（只读）"; "inbox" -> "待我审批"; else -> "我发出的审批" }}")
        if (section !in listOf("inbox", "outbox")) {
            Row { listOf("all" to "全部", "unfinished" to "未完成", "completed" to "已完成").forEach { (key, label) -> TextButton(onClick = { status = key; reload++ }) { Text(if (status == key) "✓$label" else label) } } }
            Row { listOf("active" to "进行中", "cancelled" to "已归档").forEach { (key, label) -> TextButton(onClick = { lifecycle = key; reload++ }) { Text(if (lifecycle == key) "✓$label" else label) } } }
            OutlinedTextField(startDate, { startDate = it }, Modifier.fillMaxWidth(), label = { Text("截止日期起（YYYY-MM-DD）") })
            OutlinedTextField(endDate, { endDate = it }, Modifier.fillMaxWidth(), label = { Text("截止日期止（YYYY-MM-DD）") })
            if (section == "subordinates") {
                Row { TextButton(onClick = { subordinateScope = "assigned"; reload++ }) { Text("承担的") }; TextButton(onClick = { subordinateScope = "created"; reload++ }) { Text("创建的") } }
                OutlinedTextField(subordinateQuery, { subordinateQuery = it }, Modifier.fillMaxWidth(), label = { Text("搜索授权下属") })
                people.filter { it.string("display_name").contains(subordinateQuery, true) }.forEach { p -> TextButton(onClick = { selectedPerson = p; reload++ }) { Text(p.string("display_name")) } }
                TextButton(onClick = { selectedPerson = null; reload++ }) { Text("查看全部授权下属") }
            }
            TextButton(onClick = { reload++ }) { Text("应用筛选") }
        }
        if (busy) CircularProgressIndicator()
        if (error.isNotBlank()) { Note(error); TextButton(onClick = { reload++ }) { Text("重试") } }
        if (!busy && rows.isEmpty() && error.isBlank()) Text("暂无任务")
        rows.forEach { row -> val id = row.long("task_id", row.long("id")); TextButton(onClick = { open(id) }, modifier = Modifier.fillMaxWidth()) { Text(row.string("title", row.string("task_title", "任务 #$id")) + " · " + row.string("approval_status", row.string("completion_status"))) } }
        if (section == "mine" || section == "created") PrimaryButton("创建任务", { task = null; page = "form" })
    }
}

@Composable
private fun TaskForm(api: BusinessApi, existing: JsonObject?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var title by remember(existing) { mutableStateOf(existing?.string("title") ?: "") }
    var description by remember(existing) { mutableStateOf(existing?.string("description") ?: "") }
    var materials by remember(existing) { mutableStateOf(existing?.string("required_materials") ?: "") }
    var association by remember(existing) { mutableStateOf(existing?.string("association_note") ?: "") }
    var due by remember(existing) { mutableStateOf(existing?.string("due_at") ?: "") }
    var assignee by remember(existing) { mutableLongStateOf(existing?.obj("assignee")?.long("id") ?: 0L) }
    var people by remember { mutableStateOf<List<JsonObject>>(emptyList()) }; var tags by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var selectedTags by remember(existing) { mutableStateOf(existing?.array("tags") ?: emptyList()) }
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { people = api.all(ROOT + "organization/assignable-users/"); tags = api.all(ROOT + "tags/") } catch (e: Exception) { error = e.businessMessage() } }
    PageColumn {
        BackButton(onBack); PageTitle(if (existing == null) "创建任务" else "编辑任务", small = true)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题 *") })
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("任务说明 *") }, minLines = 3)
        Text("负责人 *")
        people.forEach { p -> Row { RadioButton(selected = assignee == p.long("id"), onClick = { assignee = p.long("id") }); Text(p.string("display_name"), Modifier.padding(top = 12.dp)) } }
        OutlinedTextField(due, { due = it }, Modifier.fillMaxWidth(), label = { Text("截止时间 *（ISO 8601）") })
        TextButton(onClick = {
            val calendar = Calendar.getInstance()
            DatePickerDialog(context, { _, year, month, day ->
                calendar.set(year, month, day)
                TimePickerDialog(context, { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute); calendar.set(Calendar.SECOND, 0)
                    due = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(calendar.time)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }) { Text("选择截止日期和时间") }
        OutlinedTextField(materials, { materials = it }, Modifier.fillMaxWidth(), label = { Text("预期交付材料") })
        OutlinedTextField(association, { association = it }, Modifier.fillMaxWidth(), label = { Text("关联说明") })
        Text("标签")
        (selectedTags + tags).distinctBy { it.long("id") }.forEach { tag -> Row { val checked = selectedTags.any { it.long("id") == tag.long("id") }; Checkbox(checked, { selectedTags = if (it) selectedTags + tag else selectedTags.filterNot { v -> v.long("id") == tag.long("id") } }); Text(tag.string("name"), Modifier.padding(top = 12.dp)) } }
        if (error.isNotEmpty()) Note(error)
        PrimaryButton("保存", { if (title.isBlank() || description.isBlank() || due.isBlank() || assignee == 0L) error = "请填写标题、说明、负责人和截止时间。" else scope.launch {
            busy = true; try { val body = json { addProperty("title", title.trim()); addProperty("description", description.trim()); addProperty("assignee_id", assignee); addProperty("due_at", due.trim()); addProperty("required_materials", materials); addProperty("association_note", association); add("tag_ids", ids(selectedTags.map { it.long("id") })) }; if (existing == null) api.post(ROOT + "tasks/", body) else api.patch(ROOT + "tasks/${existing.long("id")}/", body); onSaved() } catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
        } }, enabled = !busy)
    }
}

@Composable
private fun TaskDetail(api: BusinessApi, task: JsonObject?, history: List<JsonObject>, me: Long, inInbox: Boolean, readOnly: Boolean, busy: Boolean, error: String, onBack: () -> Unit, onEdit: () -> Unit, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope(); var operation by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }; var materials by remember { mutableStateOf("") }; var actionError by remember { mutableStateOf("") }; var running by remember { mutableStateOf(false) }
    val id = task?.long("id") ?: 0L; val creator = me != 0L && task?.obj("creator")?.long("id") == me; val assignee = me != 0L && task?.obj("assignee")?.long("id") == me
    val active = task?.string("lifecycle_status") == "active" && task.string("completion_status") == "unfinished"
    val approved = task?.string("approval_status") == "approved"
    val submission = task?.array("submissions")?.lastOrNull { it.string("review_status") == "waiting" }
    fun action(path: String, body: JsonObject = JsonObject()) { scope.launch { running = true; try { if (path == "start/" || path == "resubmit/") api.postEmpty(ROOT + "tasks/$id/$path") else api.post(ROOT + "tasks/$id/$path", body); operation = ""; message = ""; materials = ""; onRefresh() } catch (e: Exception) { actionError = e.businessMessage() } finally { running = false } } }
    PageColumn {
        BackButton(onBack); PageTitle("任务详情", small = true)
        if (busy) CircularProgressIndicator()
        if (error.isNotEmpty()) { Note(error); TextButton(onClick = onRefresh) { Text("重试") } }
        task?.let { t ->
            Text(t.string("title")); Text(t.string("description")); Text("负责人：${t.obj("assignee")?.string("display_name") ?: ""} · 截止：${t.string("due_at")}")
            Text("审批：${t.string("approval_status")} · 完成：${t.string("completion_status")} · 状态：${t.string("lifecycle_status")}")
            Text("标签：" + t.array("tags").joinToString("、") { it.string("name") })
            Text("审批记录"); t.array("approval_history").forEach { Text("${it.string("approver_name")} · ${it.string("decision")} · ${it.string("comment")}") }
            Text("执行进展"); t.array("updates").forEach { Text(it.string("content")) }
            Text("完成提交"); t.array("submissions").forEach { Text("${it.string("completion_note")} · ${it.string("review_status")}") }
            Text("变更历史"); history.forEach { Text("${it.string("created_at")} · ${it.string("change_type")} · ${it.obj("changes") ?: ""}") }
            if (!readOnly && active) {
                if (creator) TextButton(onClick = onEdit) { Text("编辑任务") }
                if (creator && t.string("approval_status") == "returned") TextButton(onClick = { action("resubmit/") }) { Text("重新送审") }
                if (inInbox) { TextButton(onClick = { action("approval/", json { addProperty("decision", "approved"); addProperty("comment", "") }) }) { Text("审批通过") }; TextButton(onClick = { operation = "approval-return" }) { Text("审批退回") } }
                if (assignee && approved) { TextButton(onClick = { action("start/") }) { Text("开始任务") }; TextButton(onClick = { operation = "update" }) { Text("追加进展") }; TextButton(onClick = { operation = "submit" }) { Text("提交完成") } }
                if (creator && approved && submission != null && !assignee) { TextButton(onClick = { action("submissions/${submission.long("id")}/review/", json { addProperty("decision", "accepted"); addProperty("comment", "") }) }) { Text("验收通过") }; TextButton(onClick = { operation = "review-return" }) { Text("验收退回") } }
                if (creator) TextButton(onClick = { operation = "cancel" }) { Text("取消任务") }
            }
        }
        if (actionError.isNotEmpty()) Note(actionError)
    }
    if (operation.isNotEmpty()) AlertDialog(onDismissRequest = { operation = "" }, title = { Text(when (operation) { "update" -> "追加进展"; "submit" -> "提交完成"; "cancel" -> "取消任务"; else -> "填写退回原因" }) }, text = { Column { OutlinedTextField(message, { message = it }, label = { Text(if (operation == "submit") "完成说明" else "内容 / 原因") }); if (operation == "submit") OutlinedTextField(materials, { materials = it }, label = { Text("交付材料说明") }); if (actionError.isNotBlank()) Text(actionError, color = MaterialTheme.colorScheme.error) } }, confirmButton = { TextButton(enabled = !running, onClick = {
        if (message.isBlank()) { actionError = "请填写内容或原因。"; return@TextButton }
        if (operation == "submit" && task?.string("required_materials")?.isNotBlank() == true && materials.isBlank()) { actionError = "请填写交付材料说明。"; return@TextButton }
        when (operation) { "update" -> action("updates/", json { addProperty("content", message) }); "submit" -> action("submissions/", json { addProperty("completion_note", message); addProperty("materials_note", materials) }); "cancel" -> action("cancel/", json { addProperty("reason", message) }); "approval-return" -> action("approval/", json { addProperty("decision", "returned"); addProperty("comment", message) }); "review-return" -> submission?.let { action("submissions/${it.long("id")}/review/", json { addProperty("decision", "returned"); addProperty("comment", message) }) } }
    }) { Text("确定") } }, dismissButton = { TextButton(onClick = { operation = "" }) { Text("取消") } })
}
