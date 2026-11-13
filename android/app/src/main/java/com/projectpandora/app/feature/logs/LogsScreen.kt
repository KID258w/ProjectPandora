package com.projectpandora.app.feature.logs

import android.widget.NumberPicker
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.feature.profile.ReadOnlyProfileAvatar
import com.projectpandora.app.feature.tags.TagChipList
import com.projectpandora.app.feature.tags.TagSelectionDialog
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    initialPersonId: Long? = null,
    onBack: (() -> Unit)? = null,
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val now = remember { Calendar.getInstance() }
    val parts = remember(initialDate) { initialDate.split("-").mapNotNull { it.toIntOrNull() } }
    var year by remember { mutableIntStateOf(parts.getOrNull(0)?.takeIf { it in 1900..2100 } ?: now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(parts.getOrNull(1)?.takeIf { it in 1..12 } ?: (now.get(Calendar.MONTH) + 1)) }
    var day by remember { mutableIntStateOf(parts.getOrNull(2)?.takeIf { it in 1..monthDays(year, month) } ?: now.get(Calendar.DAY_OF_MONTH)) }
    var person by remember(initialPersonId) { mutableStateOf<JsonObject?>(null) }
    var profileReload by remember(initialPersonId) { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<JsonObject?>(null) }
    var canEdit by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var marks by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var commentsReload by remember { mutableIntStateOf(0) }
    var quickMonth by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    BackHandler(enabled = edit || showDetail || showComments) {
        when {
            showComments -> showComments = false
            edit -> edit = false
            showDetail -> showDetail = false
        }
    }
    LaunchedEffect(edit, showDetail) { onFullscreenChange(edit || showDetail) }
    val date = dateString(year, month, day)
    fun shift(delta: Int) {
        val calendar = Calendar.getInstance().apply { set(year, month - 1, 1); add(Calendar.MONTH, delta) }
        year = calendar.get(Calendar.YEAR); month = calendar.get(Calendar.MONTH) + 1
        day = minOf(day, monthDays(year, month))
    }
    LaunchedEffect(initialPersonId, profileReload) {
        if (initialPersonId != null) {
            try {
                val member = api.get(ROOT + "organization/members/$initialPersonId/").data()
                person = JsonObject().apply {
                    addProperty("id", member.long("id"))
                    addProperty("display_name", member.string("display_name"))
                    addProperty("business_role", member.string("business_role"))
                    addProperty("department_name", member.string("department_name"))
                    addProperty("team_name", member.string("team_name"))
                }
            } catch (e: Exception) {
                error = e.businessMessage()
            }
        }
    }
    LaunchedEffect(year, month, person, reload) {
        if (initialPersonId != null && person == null) return@LaunchedEffect
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
        if (initialPersonId != null && person == null) return@LaunchedEffect
        showDetail = false
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
    } else if (showDetail && selected != null) {
        LogReadOnlyDetail(
            api = api,
            log = selected!!,
            date = date,
            isOwnLog = person == null,
            commentsReloadKey = commentsReload,
            onBack = { showDetail = false },
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.weight(1f).height(50.dp).clickable { showComments = true },
                    color = Color(0xFFF5F6F8),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, C.Line),
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("写下你的评论…", color = C.Muted, fontSize = 14.sp)
                        Text("评论", color = C.Blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (person == null && canEdit && selected!!.string("status") == "submitted") {
                    Button(
                        onClick = { showDetail = false; edit = true },
                        modifier = Modifier.height(50.dp).widthIn(min = 112.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Amber, contentColor = C.Ink),
                    ) { Text("修改") }
                }
            }
        }
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
        if (person != null) {
            val current = listOf(person!!.string("display_name"), businessRoleLabel(person!!.string("business_role")), person!!.string("department_name"), person!!.string("team_name"))
                .filter(String::isNotBlank).joinToString(" · ")
            Note("查看对象：$current · 只读")
        }
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) {
            Note(error)
            TextButton(onClick = {
                if (initialPersonId != null && person == null) profileReload++ else reload++
            }) { Text("重试") }
        }
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
                    val tags = selected!!.array("tags")
                    if (tags.isNotEmpty()) {
                        Text("关联标签", color = C.Muted, fontSize = 12.sp)
                        TagChipList(tags)
                    }
                    val updatedAt = selected!!.string("updated_at")
                    if (updatedAt.isNotBlank()) Text("最后更新：${displayLogTimestamp(updatedAt)}", color = C.Muted, fontSize = 12.sp)
                    if (selected!!.string("status") == "submitted" || !canEdit || person != null) {
                        TextButton(onClick = {
                            showDetail = true
                        }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("点击查看详情")
                        }
                    }
                }
                if (person == null && !canEdit) Text("该日期不可编辑。", color = C.Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(80.dp))
    }
    if (!edit && !showDetail && person == null && canEdit && (selected == null || selected?.string("status") == "draft")) {
        FloatingActionButton(
            onClick = { edit = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("edit-log").semantics {
                contentDescription = if (selected?.string("status") == "draft") "继续填写日志" else "填写日志"
            },
            shape = androidx.compose.foundation.shape.CircleShape,
        ) { Text("＋") }
    }
    }
    if (showComments && selected != null) {
        val selectedLog = selected!!
        LogCommentComposerDialog(
            api = api,
            logId = selectedLog.long("id"),
            onDismiss = { showComments = false },
            onPosted = {
                showComments = false
                commentsReload++
            },
        )
    }
    if (quickMonth) {
        var pickedYear by remember { mutableIntStateOf(year) }
        var pickedMonth by remember { mutableIntStateOf(month) }
        AlertDialog(
            onDismissRequest = { quickMonth = false },
            title = { Text("选择年月") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("年份", Modifier.weight(1f), textAlign = TextAlign.Center, color = C.Muted)
                        Text("月份", Modifier.weight(1f), textAlign = TextAlign.Center, color = C.Muted)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AndroidView(
                            modifier = Modifier.weight(1f).height(160.dp),
                            factory = { context -> NumberPicker(context).apply { minValue = 1900; maxValue = 2100; wrapSelectorWheel = true } },
                            update = { picker ->
                                picker.setOnValueChangedListener { _, _, value -> pickedYear = value }
                                if (picker.value != pickedYear) picker.value = pickedYear
                            },
                        )
                        AndroidView(
                            modifier = Modifier.weight(1f).height(160.dp),
                            factory = { context -> NumberPicker(context).apply { minValue = 1; maxValue = 12; displayedValues = Array(12) { "${it + 1}月" }; wrapSelectorWheel = true } },
                            update = { picker ->
                                picker.setOnValueChangedListener { _, _, value -> pickedMonth = value }
                                if (picker.value != pickedMonth) picker.value = pickedMonth
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    year = pickedYear; month = pickedMonth; day = minOf(day, monthDays(year, month)); quickMonth = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { quickMonth = false }) { Text("取消") } },
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showTagPicker = true }) { Text("选择标签") }
            TagChipList(selected, emptyText = "未选择")
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
private fun LogReadOnlyDetail(
    api: BusinessApi,
    log: JsonObject,
    date: String,
    isOwnLog: Boolean,
    commentsReloadKey: Int,
    onBack: () -> Unit,
) {
    val logAuthorId = log.long("author_id", log.obj("author")?.long("id") ?: 0L)
    PageColumn {
        BackButton(onBack)
        BrandHeader()
        PageTitle("日志详情", small = true)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, C.Line),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(date, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    if (isOwnLog) LogStatusBadge(if (log.string("status") == "submitted") "已提交" else "草稿")
                    else Text("只读", color = C.Muted, fontSize = 12.sp)
                }
                Text("当日完成", fontWeight = FontWeight.SemiBold, color = C.Ink)
                Text(log.string("completed_work"), color = C.Ink, lineHeight = 22.sp)
                val plan = log.string("next_day_plan")
                if (plan.isNotBlank()) {
                    Text("次日计划", fontWeight = FontWeight.SemiBold, color = C.Ink)
                    Text(plan, color = C.Ink, lineHeight = 22.sp)
                }
                val tags = log.array("tags")
                if (tags.isNotEmpty()) {
                    Text("关联标签", color = C.Muted, fontSize = 12.sp)
                    TagChipList(tags)
                }
                val updatedAt = log.string("updated_at")
                if (updatedAt.isNotBlank()) Text("最后更新：${displayLogTimestamp(updatedAt)}", color = C.Muted, fontSize = 12.sp)
            }
        }
        LogCommentsSection(
            api = api,
            logId = log.long("id"),
            logAuthorId = logAuthorId,
            reloadKey = commentsReloadKey,
        )
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun LogCommentsSection(
    api: BusinessApi,
    logId: Long,
    logAuthorId: Long,
    reloadKey: Int,
) {
    var comments by remember(logId) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember(logId) { mutableStateOf(true) }
    var error by remember(logId) { mutableStateOf("") }
    var reload by remember(logId) { mutableIntStateOf(0) }

    LaunchedEffect(logId, reloadKey, reload) {
        loading = true
        error = ""
        try {
            comments = api.all(ROOT + "logs/$logId/comments/")
        } catch (failure: Exception) {
            error = failure.businessMessage()
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("评论", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink)
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "…" else comments.size.toString(), color = C.Muted, fontSize = 13.sp)
        }
        if (loading && comments.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
        if (error.isNotBlank()) {
            Note(error)
            TextButton(onClick = { reload++ }) { Text("重新加载评论") }
        }
        if (!loading && error.isBlank() && comments.isEmpty()) {
            Note("还没有评论，写下第一条吧。")
        }
        comments.forEach { comment ->
            val author = comment.obj("author")
            val name = author?.string("display_name").orEmpty().ifBlank { "用户" }
            val department = author?.string("department_name").orEmpty()
            val team = author?.string("team_name").orEmpty()
            val role = businessRoleLabel(author?.string("business_role").orEmpty())
            val isLogAuthor = comment.bool("is_log_author") || (logAuthorId > 0 && author?.long("id") == logAuthorId)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogCommentAuthorAvatar(api, author?.long("id") ?: 0L, name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(name, fontWeight = FontWeight.SemiBold, color = C.Ink, fontSize = 14.sp)
                        Text(role, color = C.Blue, fontSize = 11.sp)
                        if (isLogAuthor) {
                            Surface(color = C.AmberPale, shape = RoundedCornerShape(50)) {
                                Text("日志本人", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = C.Ink, fontSize = 10.sp)
                            }
                        }
                    }
                    val org = listOf(department, team).filter(String::isNotBlank).joinToString(" · ")
                    if (org.isNotBlank()) Text(org, color = C.Muted, fontSize = 11.sp)
                    Text(comment.string("content"), color = C.Ink, lineHeight = 21.sp, fontSize = 14.sp)
                    Text(displayLogTimestamp(comment.string("created_at")), color = C.Muted, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = C.Line.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun LogCommentAuthorAvatar(api: BusinessApi, authorId: Long, name: String) {
    var avatarBytes by remember(authorId) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(api, authorId) {
        avatarBytes = if (authorId > 0L) {
            try {
                val response = api.getBody(ROOT + "organization/members/$authorId/avatar/")
                if (response.isSuccessful) {
                    withContext(Dispatchers.IO) { response.body()?.use { it.bytes() } }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }
    ReadOnlyProfileAvatar(name, avatarBytes, size = 38.dp)
}

@Composable
private fun LogCommentComposerDialog(api: BusinessApi, logId: Long, onDismiss: () -> Unit, onPosted: () -> Unit) {
    var content by remember(logId) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember(logId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("发表评论", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 2000) content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("写下你的评论") },
                    supportingText = { Text("${content.length}/2000") },
                    minLines = 4,
                    maxLines = 8,
                )
                if (error.isNotBlank()) Note(error)
                Button(
                    onClick = {
                        val value = content.trim()
                        if (value.isNotEmpty()) scope.launch {
                            sending = true
                            error = ""
                            try {
                                api.post(ROOT + "logs/$logId/comments/", json { addProperty("content", value) })
                                onPosted()
                            } catch (failure: Exception) {
                                error = failure.businessMessage()
                            } finally {
                                sending = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !sending && content.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Amber, contentColor = C.Ink),
                ) { Text(if (sending) "发送中…" else "发送评论") }
            }
        }
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
