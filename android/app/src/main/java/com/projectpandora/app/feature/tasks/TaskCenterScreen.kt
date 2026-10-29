package com.projectpandora.app.feature.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.feature.tags.TagSelectionDialog
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val ROOT = "api/v1/app/"
private fun businessRoleLabel(role: String): String = when (role) {
    "founder" -> "创始人"
    "department_manager" -> "部门经理"
    "team_leader" -> "团队长"
    "employee" -> "员工"
    else -> role.ifBlank { "职位未知" }
}
private fun assigneeOptionLabel(person: JsonObject, currentUserId: Long, currentUserRole: String): String {
    val name = person.string("display_name")
    if (person.long("id") == currentUserId) return "$name（你）"

    val personRole = person.string("business_role")
    val organization = when {
        currentUserRole == "department_manager" && personRole == "team_leader" -> person.string("team_name")
        currentUserRole == "founder" && personRole == "department_manager" -> person.string("department_name")
        personRole == "team_leader" || personRole == "employee" -> person.string("team_name")
        personRole == "department_manager" -> person.string("department_name")
        else -> ""
    }
    val roleAndOrg = listOf(businessRoleLabel(personRole), organization).filter(String::isNotBlank).joinToString(" · ")
    return if (roleAndOrg.isBlank()) name else "$name（$roleAndOrg）"
}

@Composable
fun TaskCenterScreen(api: BusinessApi, onBack: () -> Unit, initialTaskId: Long? = null, initialApprovalContext: Boolean = false, initialApprovalCenter: Boolean = false) {
    var page by remember { mutableStateOf(if (initialTaskId != null) "detail" else if (initialApprovalCenter) "approval" else "list") }
    var approvalCenterDestination by remember { mutableStateOf(initialApprovalCenter) }
    var section by remember { mutableStateOf(if (initialApprovalCenter) "inbox" else "mine") }
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
        catch (e: Exception) {
            if (initialApprovalContext && e is retrofit2.HttpException && e.code() == 404) {
                // If the approval ended after the notification was loaded, return to that
                // notification list instead of exposing a dead task-detail route.
                onBack()
            } else {
                error = e.businessMessage()
            }
        } finally { busy = false }
    }
    fun open(id: Long) { taskId = id; detailOrigin = page; page = "detail" }
    fun back() { when { page == "list" -> onBack(); page == "approval" && approvalCenterDestination -> onBack(); page == "approval" -> { section = "mine"; page = "list"; reload++ }; page == "detail" && detailOrigin == "external" -> onBack(); page == "detail" -> { page = detailOrigin; reload++ }; page == "form" && task != null -> { page = "detail"; reload++ }; else -> { page = "list"; reload++ } } }
    BackHandler(enabled = page != "list" && !showFilter) { back() }
    if (page == "form") {
        TaskForm(api, task, me, role, onBack = ::back, onSaved = { page = if (task == null) "list" else "detail"; reload++ })
        return
    }
    if (page == "detail") {
        TaskDetail(api, task, history, me, approvalVerified, detailOrigin == "list" && section == "subordinates", busy, error, onBack = ::back,
            onEdit = { page = "form" }, onRefresh = { reload++ }, onApprovalReturned = ::back)
        return
    }
    val canInspect = role in setOf("founder", "department_manager", "team_leader")
    val canInbox = canInspect
    val canOutbox = role in setOf("department_manager", "team_leader", "employee")
    Box(Modifier.fillMaxSize()) {
        PageColumn {
            BackButton(::back)
            PageTitle(if (page == "approval") "审批中心" else if (section == "subordinates") "下属任务" else "任务中心", small = true)
            if (page == "list" && (canInbox || canOutbox || (canInspect && section != "subordinates"))) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canInbox || canOutbox) OutlinedButton(onClick = { section = if (canInbox) "inbox" else "outbox"; page = "approval" }) { Text("审批中心") }
                    if (canInspect && section != "subordinates") OutlinedButton(onClick = { section = "subordinates"; scope.launch { try { people = api.all(ROOT + "organization/visible-subordinates/") } catch (e: Exception) { error = e.businessMessage() } } }) { Text("下属任务") }
                }
            }
            if (page == "approval") {
                Text("按审批流程处理任务；待审批任务不会进入普通任务列表。", color = C.Muted, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canInbox) FilterChip(selected = section == "inbox", onClick = { section = "inbox" }, label = { Text("待你审批${if (section == "inbox") " · ${rows.size}" else ""}") })
                    if (canOutbox) FilterChip(selected = section == "outbox", onClick = { section = "outbox" }, label = { Text("待上级审批${if (section == "outbox") " · ${rows.size}" else ""}") })
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (section == "subordinates") selectedPerson?.let { "${it.string("display_name")} · ${businessRoleLabel(it.string("business_role"))}" } ?: "全部下属" else when (section) { "created" -> "我创建的"; "all" -> "全部任务"; else -> "我的任务" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = C.Ink)
                    OutlinedButton(onClick = { showFilter = true }) { Text("筛选") }
                }
                if (section == "subordinates") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("下属任务 · 只读", color = C.Muted, fontSize = 12.sp)
                    TextButton(onClick = { section = "mine" }) { Text("返回我的任务") }
                }
            }
            if (busy) CircularProgressIndicator()
            if (error.isNotBlank()) { Note(error); TextButton(onClick = { reload++ }) { Text("重试") } }
            if (!busy && rows.isEmpty() && error.isBlank()) TaskEmptyState(
                when {
                    page == "approval" && section == "inbox" -> "目前没有待你审批的任务。"
                    page == "approval" -> "目前没有待上级审批或退回待修改的任务。"
                    section == "subordinates" -> "当前筛选下没有已审批的下属任务。"
                    else -> "没有符合条件的任务。"
                },
            )
            rows.forEach { row ->
                val id = row.long("task_id", row.long("id"))
                if (page == "approval") ApprovalQueueCard(row, section == "inbox") { open(id) }
                else TaskSummaryCard(row, section, subordinateScope, selectedPerson) { open(id) }
            }
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
                people.filter { it.string("display_name").contains(draftQuery, true) }.forEach { p -> TextButton(onClick = { draftPerson = p }) { Text((if (draftPerson?.long("id") == p.long("id")) "✓" else "") + p.string("display_name") + " · " + businessRoleLabel(p.string("business_role"))) } }
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
private fun TaskEmptyState(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, C.Line),
    ) {
        Text(message, Modifier.fillMaxWidth().padding(20.dp), color = C.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ApprovalQueueCard(row: JsonObject, inbox: Boolean, onClick: () -> Unit) {
    val status = when {
        inbox -> "待你审批"
        row.string("approval_status") == "returned" -> "已退回待修改"
        else -> "待上级审批"
    }
    val creator = row.obj("creator")?.string("display_name").orEmpty()
    val assignee = row.obj("assignee")?.string("display_name").orEmpty()
    val approver = row.obj("approver")?.string("display_name").orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, C.Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(row.string("title", "未命名任务"), Modifier.weight(1f).padding(end = 8.dp), fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TaskStatusBadge(status)
            }
            HorizontalDivider(color = C.Line)
            TaskMetadata("创建人", creator.ifBlank { "未知" })
            TaskMetadata("负责人", assignee.ifBlank { "未知" })
            TaskMetadata(if (inbox) "审批人" else "当前审批人", approver.ifBlank { "未知" })
            TaskMetadata("送审时间", displayTimestamp(row.string("submitted_at")))
            if (!inbox && row.string("approval_status") == "returned") {
                Text("已被上级退回，打开任务查看原因并修改后重新送审。", color = C.Error, fontSize = 12.sp)
            }
            Text("点击查看任务详情", color = C.Blue, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TaskSummaryCard(row: JsonObject, section: String, subordinateScope: String, selectedPerson: JsonObject?, onClick: () -> Unit) {
    val creator = row.string("creator_name")
    val assignee = row.string("assignee_name")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, C.Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(row.string("title", "未命名任务"), Modifier.weight(1f).padding(end = 8.dp), fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (row.string("lifecycle_status") == "cancelled") TaskStatusBadge("已取消")
                    TaskStatusBadge("完成：${if (row.string("completion_status") == "completed") "已完成" else "未完成"}")
                }
            }
            HorizontalDivider(color = C.Line)
            when {
                section == "subordinates" && subordinateScope == "assigned" -> TaskMetadata("负责人", assignee.ifBlank { selectedPerson?.string("display_name") ?: "下属" })
                section == "subordinates" -> TaskMetadata("创建人", creator.ifBlank { selectedPerson?.string("display_name") ?: "下属" })
                section == "created" -> TaskMetadata("负责人", assignee.ifBlank { "未知" })
                section == "mine" -> TaskMetadata("创建人", creator.ifBlank { "本人" })
                else -> {
                    TaskMetadata("创建人", creator.ifBlank { "未知" })
                    TaskMetadata("负责人", assignee.ifBlank { "未知" })
                }
            }
            TaskMetadata("截止时间", displayTimestamp(row.string("due_at")))
            Text("点击查看任务详情", color = C.Blue, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TaskMetadata(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = C.Muted, fontSize = 12.sp)
        Text(value, color = C.Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TaskStatusBadge(label: String) {
    val (background, foreground) = when (label) {
        "已完成", "验收通过", "完成：已完成" -> C.TealPale to C.Teal
        "已取消", "已退回待修改" -> C.ErrorPale to C.Error
        "待你审批", "待上级审批" -> C.BluePale to C.Blue
        "待验收" -> C.VioletPale to C.Violet
        else -> C.AmberPale to C.Orange
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(50)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun TaskForm(api: BusinessApi, existing: JsonObject?, currentUserId: Long, currentUserRole: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var title by remember(existing) { mutableStateOf(existing?.string("title") ?: "") }
    var description by remember(existing) { mutableStateOf(existing?.string("description") ?: "") }
    var materials by remember(existing) { mutableStateOf(existing?.string("required_materials") ?: "") }
    var association by remember(existing) { mutableStateOf(existing?.string("association_note") ?: "") }
    var due by remember(existing) { mutableStateOf(existing?.string("due_at") ?: "") }
    var assignee by remember(existing) { mutableLongStateOf(existing?.obj("assignee")?.long("id") ?: 0L) }
    var people by remember { mutableStateOf<List<JsonObject>>(emptyList()) }; var tags by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var assigneeSearch by remember { mutableStateOf("") }
    var showAssigneePicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var selectedTags by remember(existing) { mutableStateOf(existing?.array("tags") ?: emptyList()) }
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { people = api.all(ROOT + "organization/assignable-users/"); tags = api.all(ROOT + "tags/") } catch (e: Exception) { error = e.businessMessage() } }
    PageColumn {
        BackButton(onBack); PageTitle(if (existing == null) "创建任务" else "编辑任务", small = true)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题 *") })
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("任务说明 *") }, minLines = 3)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { assigneeSearch = ""; showAssigneePicker = true }) { Text("选择负责人 *") }
            val chosen = people.firstOrNull { it.long("id") == assignee } ?: existing?.obj("assignee")
            Text(chosen?.let { assigneeOptionLabel(it, currentUserId, currentUserRole) } ?: "未选择", Modifier.weight(1f).padding(start = 12.dp))
        }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showTagPicker = true }) { Text("选择标签") }
            Text(selectedTags.joinToString("、") { it.string("name") }.ifBlank { "未选择" }, Modifier.weight(1f).padding(start = 12.dp))
        }
        if (error.isNotEmpty()) Note(error)
        PrimaryButton("保存", { if (title.isBlank() || description.isBlank() || due.isBlank() || assignee == 0L) error = "请填写标题、说明、负责人和截止时间。" else scope.launch {
            busy = true; try { val body = json { addProperty("title", title.trim()); addProperty("description", description.trim()); addProperty("assignee_id", assignee); addProperty("due_at", due.trim()); addProperty("required_materials", materials); addProperty("association_note", association); add("tag_ids", ids(selectedTags.map { it.long("id") })) }; if (existing == null) api.post(ROOT + "tasks/", body) else api.patch(ROOT + "tasks/${existing.long("id")}/", body); onSaved() } catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
        } }, enabled = !busy)
    }
    if (showAssigneePicker) {
        AlertDialog(
            onDismissRequest = { showAssigneePicker = false },
            title = { Text("选择负责人") },
            text = {
                Column {
                    OutlinedTextField(assigneeSearch, { assigneeSearch = it }, Modifier.fillMaxWidth(), label = { Text("搜索姓名、部门或团队") })
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        val matches = people.filter { person ->
                            listOf(person.string("display_name"), person.string("department_name"), person.string("team_name"), businessRoleLabel(person.string("business_role")))
                                .any { it.contains(assigneeSearch, ignoreCase = true) }
                        }
                        if (matches.isEmpty()) Text("没有找到符合条件的负责人。", Modifier.padding(vertical = 12.dp))
                        matches.forEach { person ->
                            TextButton(
                                onClick = { assignee = person.long("id"); showAssigneePicker = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    (if (assignee == person.long("id")) "✓ " else "") + assigneeOptionLabel(person, currentUserId, currentUserRole),
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAssigneePicker = false }) { Text("关闭") } },
        )
    }
    if (showTagPicker) {
        TagSelectionDialog(
            activeTags = tags,
            selectedTags = selectedTags,
            onDismiss = { showTagPicker = false },
            onConfirm = { selectedTags = it; showTagPicker = false },
        )
    }
}

@Composable
private fun TaskDetail(api: BusinessApi, task: JsonObject?, history: List<JsonObject>, me: Long, inInbox: Boolean, readOnly: Boolean, busy: Boolean, error: String, onBack: () -> Unit, onEdit: () -> Unit, onRefresh: () -> Unit, onApprovalReturned: () -> Unit) {
    val scope = rememberCoroutineScope(); var operation by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }; var materials by remember { mutableStateOf("") }; var actionError by remember { mutableStateOf("") }; var running by remember { mutableStateOf(false) }
    val id = task?.long("id") ?: 0L; val creator = me != 0L && task?.obj("creator")?.long("id") == me; val assignee = me != 0L && task?.obj("assignee")?.long("id") == me
    val active = task?.string("lifecycle_status") == "active" && task.string("completion_status") == "unfinished"
    val approved = task?.string("approval_status") == "approved"
    val submission = task?.array("submissions")?.lastOrNull { it.string("review_status") == "waiting" }
    fun action(path: String, body: JsonObject = JsonObject()) { scope.launch { running = true; try { if (path == "resubmit/") api.postEmpty(ROOT + "tasks/$id/$path") else api.post(ROOT + "tasks/$id/$path", body); operation = ""; message = ""; materials = ""; if (path == "approval/" && body.string("decision") == "returned") onApprovalReturned() else onRefresh() } catch (e: Exception) { actionError = e.businessMessage() } finally { running = false } } }
    PageColumn {
        BackButton(onBack); PageTitle("任务详情", small = true)
        if (busy) CircularProgressIndicator()
        if (error.isNotEmpty()) { Note(error); TextButton(onClick = onRefresh) { Text("重试") } }
        task?.let { t ->
            val approvalHistory = t.array("approval_history")
            val showApprovalDetails = approvalHistory.isNotEmpty() &&
                (creator || approvalHistory.any { it.obj("approver")?.long("id") == me })
            TaskDetailSection {
                Text(t.string("title"), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.string("lifecycle_status") == "cancelled") TaskStatusBadge("已取消")
                    else if (showApprovalDetails) TaskStatusBadge("审批：${approvalStatusLabel(t.string("approval_status"))}")
                    if (approved) TaskStatusBadge("完成：${completionStatusLabel(t.string("completion_status"))}")
                }
                Text(t.string("description"), color = C.Ink, lineHeight = 22.sp)
                HorizontalDivider(color = C.Line)
                TaskMetadata("创建人", t.obj("creator")?.string("display_name") ?: "未知")
                TaskMetadata("负责人", t.obj("assignee")?.string("display_name") ?: "未知")
                TaskMetadata("截止时间", displayTimestamp(t.string("due_at")))
                if (t.string("required_materials").isNotBlank()) TaskMetadata("预期提交材料", t.string("required_materials"))
                if (t.string("association_note").isNotBlank()) TaskMetadata("关联说明", t.string("association_note"))
                val tagNames = t.array("tags").joinToString("、") { it.string("name") }
                if (tagNames.isNotBlank()) TaskMetadata("关联标签", tagNames)
                if (t.string("lifecycle_status") == "cancelled" && t.string("cancellation_reason").isNotBlank()) {
                    Note("取消原因：${t.string("cancellation_reason")}", background = C.ErrorPale, color = C.Error)
                }
            }
            if (showApprovalDetails) {
                TaskDetailSection("审批记录") {
                    approvalHistory.forEach { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("第 ${row.long("round")} 轮 · ${approvalDecisionLabel(row.string("decision"))}", fontWeight = FontWeight.SemiBold, color = C.Ink)
                            Text("审批人：${row.obj("approver")?.string("display_name") ?: "未知审批人"}", color = C.Muted, fontSize = 13.sp)
                            if (row.string("submitted_at").isNotBlank()) Text("送审：${displayTimestamp(row.string("submitted_at"))}", color = C.Muted, fontSize = 12.sp)
                            if (row.string("decided_at").isNotBlank()) Text("处理：${displayTimestamp(row.string("decided_at"))}", color = C.Muted, fontSize = 12.sp)
                            if (row.string("comment").isNotBlank()) Note("审批意见：${row.string("comment")}", background = if (row.string("decision") == "returned") C.ErrorPale else C.BluePale, color = if (row.string("decision") == "returned") C.Error else C.Blue)
                        }
                        if (row != approvalHistory.lastOrNull()) HorizontalDivider(color = C.Line)
                    }
                }
            }
            if (approved) {
                TaskDetailSection("执行进展") {
                    if (t.array("updates").isEmpty()) Text("暂无执行进展。", color = C.Muted)
                    t.array("updates").forEach { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.obj("author")?.string("display_name") ?: "未知成员", fontWeight = FontWeight.SemiBold, color = C.Ink)
                            Text(displayTimestamp(row.string("created_at")), color = C.Muted, fontSize = 12.sp)
                            Text(row.string("content"), color = C.Ink, lineHeight = 21.sp)
                        }
                        if (row != t.array("updates").lastOrNull()) HorizontalDivider(color = C.Line)
                    }
                }
                TaskDetailSection("完成提交与验收") {
                    if (t.array("submissions").isEmpty()) Text("尚未提交完成结果。", color = C.Muted)
                    t.array("submissions").forEach { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${row.obj("submitted_by")?.string("display_name") ?: "未知成员"} · ${displayTimestamp(row.string("submitted_at"))}", color = C.Muted, fontSize = 12.sp)
                                TaskStatusBadge(submissionStatusLabel(row.string("review_status")))
                            }
                            Text("完成说明：${row.string("completion_note")}", color = C.Ink, lineHeight = 21.sp)
                            if (row.string("materials_note").isNotBlank()) Text("交付材料说明：${row.string("materials_note")}", color = C.Ink)
                            if (row.string("review_comment").isNotBlank()) {
                                val label = if (row.string("review_status") == "returned") "退回原因" else "验收意见"
                                Note("$label：${row.string("review_comment")}", background = if (row.string("review_status") == "returned") C.ErrorPale else C.TealPale, color = if (row.string("review_status") == "returned") C.Error else C.Teal)
                            }
                            row.obj("reviewed_by")?.let { reviewer -> Text("验收人：${reviewer.string("display_name")} · ${displayTimestamp(row.string("reviewed_at"))}", color = C.Muted, fontSize = 12.sp) }
                        }
                        if (row != t.array("submissions").lastOrNull()) HorizontalDivider(color = C.Line)
                    }
                }
            }
            if (history.isNotEmpty()) TaskDetailSection("任务变更记录") {
                history.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${changeTypeLabel(row.string("change_type"))} · ${row.obj("actor")?.string("display_name") ?: "未知成员"}", fontWeight = FontWeight.SemiBold, color = C.Ink)
                        Text(displayTimestamp(row.string("created_at")), color = C.Muted, fontSize = 12.sp)
                        Text(formatTaskChanges(row.obj("changes")), color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    if (row != history.lastOrNull()) HorizontalDivider(color = C.Line)
                }
            }
            if (!readOnly && active) {
                TaskDetailSection("可用操作") {
                    if (creator && submission == null) OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("编辑任务") }
                    if (creator && t.string("approval_status") == "returned") Button(onClick = { action("resubmit/") }, modifier = Modifier.fillMaxWidth()) { Text("修改完成，重新送审") }
                    if (inInbox && t.string("approval_status") == "pending") {
                        Button(onClick = { action("approval/", json { addProperty("decision", "approved"); addProperty("comment", "") }) }, modifier = Modifier.fillMaxWidth()) { Text("审批通过") }
                        OutlinedButton(onClick = { operation = "approval-return" }, modifier = Modifier.fillMaxWidth()) { Text("退回并填写原因") }
                    }
                if (assignee && approved) {
                    OutlinedButton(onClick = { operation = "update" }, modifier = Modifier.fillMaxWidth()) { Text("追加执行进展") }
                    if (submission == null) Button(onClick = { operation = "submit" }, modifier = Modifier.fillMaxWidth()) { Text("提交完成结果") }
                    else Note("完成结果已提交，等待派发者验收。", background = C.BluePale, color = C.Blue)
                }
                    if (creator && approved && submission != null && !assignee) {
                        Button(onClick = { action("submissions/${submission.long("id")}/review/", json { addProperty("decision", "accepted"); addProperty("comment", "") }) }, modifier = Modifier.fillMaxWidth()) { Text("验收通过") }
                        OutlinedButton(onClick = { operation = "review-return" }, modifier = Modifier.fillMaxWidth()) { Text("退回并填写原因") }
                    }
                    if (creator) TextButton(onClick = { operation = "cancel" }, modifier = Modifier.fillMaxWidth()) { Text("取消任务") }
                }
            }
        }
        if (actionError.isNotEmpty()) Note(actionError)
    }
    if (operation.isNotEmpty()) AlertDialog(onDismissRequest = { if (!running) operation = "" }, title = { Text(when (operation) { "update" -> "追加执行进展"; "submit" -> "提交完成结果"; "cancel" -> "取消任务"; else -> "退回任务" }) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), label = { Text(when (operation) { "submit" -> "完成说明（必填）"; "cancel" -> "取消原因（必填）"; "update" -> "进展内容（必填）"; else -> "退回原因（必填）" }) }, minLines = 3); if (operation == "submit" && task?.string("required_materials")?.isNotBlank() == true) OutlinedTextField(materials, { materials = it }, Modifier.fillMaxWidth(), label = { Text("交付材料说明（必填）") }); if (actionError.isNotBlank()) Text(actionError, color = C.Error) } }, confirmButton = { TextButton(enabled = !running, onClick = {
        if (message.isBlank()) { actionError = "请填写内容或原因。"; return@TextButton }
        if (operation == "submit" && task?.string("required_materials")?.isNotBlank() == true && materials.isBlank()) { actionError = "请填写交付材料说明。"; return@TextButton }
        when (operation) { "update" -> action("updates/", json { addProperty("content", message) }); "submit" -> action("submissions/", json { addProperty("completion_note", message); addProperty("materials_note", materials) }); "cancel" -> action("cancel/", json { addProperty("reason", message) }); "approval-return" -> action("approval/", json { addProperty("decision", "returned"); addProperty("comment", message) }); "review-return" -> submission?.let { action("submissions/${it.long("id")}/review/", json { addProperty("decision", "returned"); addProperty("comment", message) }) } }
    }) { Text("确定") } }, dismissButton = { TextButton(onClick = { operation = "" }) { Text("取消") } })
}

@Composable
private fun TaskDetailSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, C.Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, fontWeight = FontWeight.SemiBold, color = C.Ink, fontSize = 15.sp)
            content()
        }
    }
}

private fun displayTimestamp(value: String): String = value.replace('T', ' ').take(16)

private fun approvalStatusLabel(value: String): String = when (value) {
    "pending" -> "待审批"
    "approved" -> "已通过"
    "returned" -> "已退回"
    else -> "未知"
}

private fun completionStatusLabel(value: String): String = when (value) {
    "unfinished" -> "未完成"
    "completed" -> "已完成"
    else -> "未知"
}

private fun changeTypeLabel(value: String): String = when (value) {
    "created" -> "创建任务"
    "updated" -> "修改任务"
    else -> "任务变更"
}

private fun formatTaskChanges(changes: JsonObject?): String {
    if (changes == null || changes.entrySet().isEmpty()) return "没有记录字段变更。"
    val labels = mapOf(
        "title" to "标题", "description" to "任务详情", "assignee_id" to "负责人",
        "due_at" to "截止时间", "required_materials" to "预期提交材料",
        "association_note" to "关联说明", "tag_ids" to "关联标签",
    )
    return changes.entrySet().joinToString("\n") { (key, value) ->
        val diff = value.takeIf { it.isJsonObject }?.asJsonObject
        val before = formatJsonValue(diff?.get("before"))
        val after = formatJsonValue(diff?.get("after"))
        "${labels[key] ?: key}：$before → $after"
    }
}

private fun formatJsonValue(value: JsonElement?): String = when {
    value == null || value.isJsonNull -> "无"
    value.isJsonArray -> value.asJsonArray.joinToString("、") { formatJsonValue(it) }.ifBlank { "无" }
    value.isJsonPrimitive -> value.asJsonPrimitive.asString
    else -> value.toString()
}

private fun approvalDecisionLabel(value: String): String = when (value) {
    "pending" -> "待审批"
    "approved" -> "已通过"
    "returned" -> "已退回"
    else -> value
}

private fun submissionStatusLabel(value: String): String = when (value) {
    "waiting" -> "待验收"
    "accepted" -> "验收通过"
    "returned" -> "已退回"
    "self_completed" -> "本人完成"
    else -> value
}

internal fun taskStatusLabel(row: JsonObject, approval: Boolean): String = if (approval) {
    when (row.string("approval_status")) { "pending" -> "待审批"; "returned" -> "已退回"; "approved" -> "已通过"; else -> "审批状态未知" }
} else if (row.string("lifecycle_status") == "cancelled") "已取消" else when (row.string("completion_status")) { "completed" -> "已完成"; "unfinished" -> "未完成"; else -> "状态未知" }
