package com.projectpandora.app.feature.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
fun TaskCenterScreen(api: BusinessApi, onBack: () -> Unit, initialTaskId: Long? = null, initialApprovalContext: Boolean = false) {
    var page by remember { mutableStateOf(if (initialTaskId == null) "list" else "detail") }
    var section by remember { mutableStateOf("mine") }
    var role by remember { mutableStateOf("") }
    var detailOrigin by remember { mutableStateOf(if (initialTaskId != null) "external" else "list") }
    var approvalVerified by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
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
    LaunchedEffect(Unit) { try { val current = api.get(ROOT + "me/").data(); me = current.long("id"); role = current.string("business_role") } catch (e: Exception) { error = e.businessMessage() } }
    LaunchedEffect(section, reload, page) {
        if (page != "list" && page != "approval") return@LaunchedEffect
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
        busy = true; error = ""; task = null; approvalVerified = false
        try {
            task = api.get(ROOT + "tasks/$taskId/").data()
            if ((detailOrigin == "approval" && section == "inbox") || (detailOrigin == "external" && initialApprovalContext)) {
                approvalVerified = api.all(ROOT + "task-approvals/inbox/").any { it.long("task_id") == taskId && it.string("approval_status") == "pending" }
            }
            history = api.get(ROOT + "tasks/$taskId/history/").data().array("results")
        }
        catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
    }
    fun open(id: Long) { taskId = id; detailOrigin = page; page = "detail" }
    fun back() { when { page == "list" -> onBack(); page == "approval" -> { section = "mine"; page = "list"; reload++ }; page == "detail" && detailOrigin == "external" -> onBack(); page == "detail" -> { page = detailOrigin; reload++ }; page == "form" && task != null -> { page = "detail"; reload++ }; else -> { page = "list"; reload++ } } }
    BackHandler(enabled = page != "list" && !showFilter) { back() }
    if (page == "form") {
        TaskForm(api, task, onBack = ::back, onSaved = { page = if (task == null) "list" else "detail"; reload++ })
        return
    }
    if (page == "detail") {
        TaskDetail(api, task, history, me, approvalVerified, detailOrigin == "list" && section == "subordinates", busy, error, onBack = ::back,
            onEdit = { page = "form" }, onRefresh = { reload++ })
        return
    }
    val canInspect = role in setOf("founder", "department_manager", "team_leader")
    val canInbox = canInspect
    val canOutbox = role in setOf("department_manager", "team_leader", "employee")
    Box(Modifier.fillMaxSize()) {
        PageColumn {
            BackButton(::back)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PageTitle(if (page == "approval") "审批中心" else if (section == "subordinates") "下属任务" else "任务中心", small = true)
                if (page == "list") {
                    Row {
                        if (canInbox || canOutbox) TextButton(onClick = { section = if (canInbox) "inbox" else "outbox"; page = "approval" }) { Text("审批中心") }
                        if (canInspect && section != "subordinates") TextButton(onClick = { section = "subordinates"; scope.launch { try { people = api.all(ROOT + "organization/visible-subordinates/") } catch (e: Exception) { error = e.businessMessage() } } }) { Text("下属任务") }
                    }
                }
            }
            if (page == "approval") {
                Row {
                    if (canInbox) TextButton(onClick = { section = "inbox" }) { Text("待你审批") }
                    if (canOutbox) TextButton(onClick = { section = "outbox" }) { Text("待上级审批") }
                }
            } else {
                Text(if (section == "subordinates") "下属任务 · 只读" else when (section) { "created" -> "我创建的"; "all" -> "全部任务"; else -> "我的任务" })
                Row { TextButton(onClick = { showFilter = true }) { Text("筛选") }; if (section == "subordinates") TextButton(onClick = { section = "mine" }) { Text("返回我的任务") } }
            }
            if (busy) CircularProgressIndicator()
            if (error.isNotBlank()) { Note(error); TextButton(onClick = { reload++ }) { Text("重试") } }
            if (!busy && rows.isEmpty() && error.isBlank()) Text("暂无任务")
            rows.forEach { row -> val id = row.long("task_id", row.long("id")); TextButton(onClick = { open(id) }, modifier = Modifier.fillMaxWidth()) { Text(row.string("title", row.string("task_title", "任务 #$id")) + " · " + taskStatusLabel(row, page == "approval")) } }
            Spacer(Modifier.height(80.dp))
        }
        if (page == "list" && section != "subordinates") FloatingActionButton(onClick = { task = null; detailOrigin = "list"; page = "form" }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("create-task").semantics { contentDescription = "创建任务" }, shape = androidx.compose.foundation.shape.CircleShape) { Text("＋") }
    }
    if (showFilter && page == "list") {
        var draftSection by remember { mutableStateOf(section) }
        var draftStatus by remember { mutableStateOf(status) }
        var draftLifecycle by remember { mutableStateOf(lifecycle) }
        var draftStart by remember { mutableStateOf(startDate) }
        var draftEnd by remember { mutableStateOf(endDate) }
        var draftScope by remember { mutableStateOf(subordinateScope) }
        var draftPerson by remember { mutableStateOf(selectedPerson) }
        var draftQuery by remember { mutableStateOf(subordinateQuery) }
        AlertDialog(onDismissRequest = { showFilter = false }, title = { Text("筛选任务") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Text("任务范围")
            if (section == "subordinates") {
                Row { listOf("assigned" to "承担的", "created" to "创建的").forEach { (v, label) -> TextButton(onClick = { draftScope = v }) { Text(if (draftScope == v) "✓$label" else label) } } }
                OutlinedTextField(draftQuery, { draftQuery = it }, label = { Text("搜索授权下属") })
                people.filter { it.string("display_name").contains(draftQuery, true) }.forEach { p -> TextButton(onClick = { draftPerson = p }) { Text((if (draftPerson?.long("id") == p.long("id")) "✓" else "") + p.string("display_name")) } }
                TextButton(onClick = { draftPerson = null }) { Text("全部下属") }
            } else Row { listOf("mine" to "我的任务", "created" to "我创建的", "all" to "全部").forEach { (v, label) -> TextButton(onClick = { draftSection = v }) { Text(if (draftSection == v) "✓$label" else label) } } }
            Text("完成状态")
            Row { listOf("all" to "全部", "unfinished" to "未完成", "completed" to "已完成").forEach { (v, label) -> TextButton(onClick = { draftStatus = v }) { Text(if (draftStatus == v) "✓$label" else label) } } }
            Row { listOf("active" to "进行中", "cancelled" to "已归档", "all" to "全部").forEach { (v, label) -> TextButton(onClick = { draftLifecycle = v }) { Text(if (draftLifecycle == v) "✓$label" else label) } } }
            OutlinedTextField(draftStart, { draftStart = it }, label = { Text("截止日期起（YYYY-MM-DD）") })
            OutlinedTextField(draftEnd, { draftEnd = it }, label = { Text("截止日期止（YYYY-MM-DD）") })
        }
    }, confirmButton = { TextButton(onClick = { section = draftSection; status = draftStatus; lifecycle = draftLifecycle; startDate = draftStart; endDate = draftEnd; subordinateScope = draftScope; selectedPerson = draftPerson; subordinateQuery = draftQuery; showFilter = false; reload++ }) { Text("应用筛选") } }, dismissButton = { TextButton(onClick = { showFilter = false }) { Text("取消") } })
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
    var tagSearch by remember { mutableStateOf("") }
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
        OutlinedTextField(tagSearch, { tagSearch = it }, Modifier.fillMaxWidth(), label = { Text("搜索启用标签") })
        (selectedTags + tags).distinctBy { it.long("id") }.filter { it.string("name").contains(tagSearch, true) }.forEach { tag -> Row { val checked = selectedTags.any { it.long("id") == tag.long("id") }; Checkbox(checked, { selectedTags = if (it) selectedTags + tag else selectedTags.filterNot { v -> v.long("id") == tag.long("id") } }); Text(tag.string("name"), Modifier.padding(top = 12.dp)) } }
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
            Text(t.string("title")); Text(t.string("description")); Text("创建人：${t.obj("creator")?.string("display_name") ?: ""}"); Text("必需材料：${t.string("required_materials").ifBlank { "无" }}"); Text("关联说明：${t.string("association_note").ifBlank { "无" }}"); Text("负责人：${t.obj("assignee")?.string("display_name") ?: ""} · 截止：${t.string("due_at")}")
            Text("审批：${taskStatusLabel(t, true)} · ${taskStatusLabel(t, false)}")
            Text("标签：" + t.array("tags").joinToString("、") { it.string("name") })
            Text("审批记录"); t.array("approval_history").forEach { Text("${it.obj("approver")?.string("display_name") ?: it.string("approver_name")} · ${it.string("decision")} · ${it.string("comment")}") }
            Text("执行进展"); t.array("updates").forEach { Text(it.string("content")) }
            Text("完成提交"); t.array("submissions").forEach { Text("${it.string("completion_note")} · ${it.string("review_status")}") }
            Text("变更历史"); history.forEach { Text("${it.string("created_at")} · ${it.string("change_type")} · ${it.obj("changes") ?: ""}") }
            if (!readOnly && active) {
                if (creator) TextButton(onClick = onEdit) { Text("编辑任务") }
                if (creator && t.string("approval_status") == "returned") TextButton(onClick = { action("resubmit/") }) { Text("重新送审") }
                if (inInbox && t.string("approval_status") == "pending") { TextButton(onClick = { action("approval/", json { addProperty("decision", "approved"); addProperty("comment", "") }) }) { Text("审批通过") }; TextButton(onClick = { operation = "approval-return" }) { Text("审批退回") } }
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

internal fun taskStatusLabel(row: JsonObject, approval: Boolean): String = if (approval) {
    when (row.string("approval_status")) { "pending" -> "待审批"; "returned" -> "已退回"; "approved" -> "已通过"; else -> "审批状态未知" }
} else if (row.string("lifecycle_status") == "cancelled") "已取消" else when (row.string("completion_status")) { "completed" -> "已完成"; "unfinished" -> "未完成"; else -> "状态未知" }
