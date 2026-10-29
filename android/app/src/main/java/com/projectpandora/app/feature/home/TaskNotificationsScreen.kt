package com.projectpandora.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.ui.BackButton
import com.projectpandora.app.ui.Note
import com.projectpandora.app.ui.PageTitle
import com.projectpandora.app.ui.theme.PandoraColors as C
import kotlinx.coroutines.launch

@Composable
fun TaskNotificationsScreen(api: BusinessApi, onBack: () -> Unit, onTask: (Long, Boolean) -> Unit, reloadKey: Int = 0) {
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }; var hasNext by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var reload by remember { mutableIntStateOf(0) }
    var readIds by remember { mutableStateOf(setOf<Long>()) }; val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(page, reload, reloadKey) {
        busy = true; error = ""; rows = emptyList(); readIds = emptySet()
        try { val data = api.get("api/v1/app/notifications/", mapOf("page" to page.toString(), "page_size" to "20")).data(); rows = data.array("results"); hasNext = data.string("next").isNotBlank(); listState.scrollToItem(0) }
        catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        BackButton(onBack); PageTitle("任务通知", small = true)
        if (busy) CircularProgressIndicator()
        if (error.isNotEmpty()) { Note(error); TextButton(onClick = { reload++ }) { Text("重试") } }
        if (!busy && rows.isEmpty() && error.isEmpty()) Text("暂无通知")
        LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("notification-list")) {
            items(rows, key = { it.long("id") }) { row ->
                val read = row.bool("is_read") || row.long("id") in readIds
                val notificationType = row.string("notification_type")
                val approvalDecision = row.string("approval_decision")
                val type = notificationTypeLabel(row)
                val badgeType = when {
                    notificationType == "approval_required" && row.string("approval_decision") == "returned" -> "approval_returned"
                    notificationType == "approval_required" && row.string("approval_decision") == "approved" -> "approval_approved"
                    notificationType == "approval_required" && row.string("approval_decision") == "pending" && row.string("resolved_at").isBlank() -> "approval_pending"
                    notificationType == "approval_required" && row.string("resolved_at").isNotBlank() -> "approval_resolved"
                    else -> notificationType
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable {
                        scope.launch {
                            val notificationId = row.long("id")
                            // A notification row may still be on screen after its task was
                            // returned from the detail route. Resolve its current state before
                            // deciding whether the task detail is still a valid destination.
                            val currentRow = runCatching {
                                api.get(
                                    "api/v1/app/notifications/",
                                    mapOf("page" to page.toString(), "page_size" to "20"),
                                ).data().array("results").firstOrNull { it.long("id") == notificationId }
                            }.getOrNull() ?: row
                            rows = rows.map { if (it.long("id") == notificationId) currentRow else it }
                            if (!read) {
                                try {
                                    api.post("api/v1/app/notifications/mark-read/", json { add("notification_ids", ids(listOf(notificationId))) })
                                    readIds = readIds + notificationId
                                } catch (e: Exception) { error = e.businessMessage() }
                            }
                            val currentType = currentRow.string("notification_type", notificationType)
                            val currentDecision = currentRow.string("approval_decision")
                            val approvalHasNoDestination = currentType == "approval_required" &&
                                (currentDecision == "returned" || currentRow.string("task_approval_status") == "returned" ||
                                    (currentRow.string("resolved_at").isNotBlank() && currentDecision != "approved"))
                            if (!approvalHasNoDestination) {
                                onTask(currentRow.long("task_id", row.long("task_id")), currentType == "approval_required")
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, C.Line),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(row.string("task_title", "任务通知"), Modifier.weight(1f).padding(end = 8.dp), fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            NotificationBadge(type, badgeType)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("通知时间：${displayNotificationTime(row.string("created_at"))}", color = C.Muted, fontSize = 12.sp)
                            Text(if (read) "已读" else "未读", color = if (read) C.Muted else C.Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        val summary = row.string("summary").trim()
                        if (summary.isNotBlank()) {
                            val summaryLabel = when (notificationType) {
                                "task_cancelled" -> "取消原因"
                                "submission_received" -> "完成说明"
                                "submission_returned" -> "退回原因"
                                else -> "说明"
                            }
                            Note("$summaryLabel：$summary", background = if (notificationType in setOf("task_cancelled", "submission_returned")) C.ErrorPale else C.BluePale, color = if (notificationType in setOf("task_cancelled", "submission_returned")) C.Error else C.Blue)
                        }
                        if (row.string("resolved_at").isNotBlank()) Text("该通知对应的流程已结束。", color = C.Muted, fontSize = 12.sp)
                        val approvalHasNoDestination = notificationType == "approval_required" &&
                            (approvalDecision == "returned" || row.string("task_approval_status") == "returned" ||
                                (row.string("resolved_at").isNotBlank() && approvalDecision != "approved"))
                        Text(
                            if (approvalHasNoDestination) "\u5ba1\u6279\u5df2\u9000\u56de\uff0c\u70b9\u51fb\u4ec5\u6807\u8bb0\u5df2\u8bfb" else "\u70b9\u51fb\u67e5\u770b\u4efb\u52a1",
                            color = if (approvalHasNoDestination) C.Muted else C.Blue,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = page > 1, onClick = { page-- }) { Text("上一页") }
            Text("第 $page 页")
            TextButton(enabled = hasNext, onClick = { page++ }) { Text("下一页") }
        }
    }
}

private fun notificationTypeLabel(row: JsonObject): String = when (row.string("notification_type")) {
    "approval_required" -> {
        val round = row.long("approval_round")
        val prefix = if (round > 0) "第${round}轮" else "审批"
        when (row.string("approval_decision")) {
            "pending" -> if (row.string("resolved_at").isNotBlank()) "${prefix}审批已结束" else "${prefix}待你审批"
            "returned" -> "${prefix}已退回"
            "approved" -> "${prefix}已通过"
            else -> if (row.string("resolved_at").isNotBlank()) "${prefix}已结束" else "待你审批"
        }
    }
    "assignment_received" -> "新任务"
    "task_cancelled" -> "任务已取消"
    "submission_received" -> "待验收"
    "submission_accepted" -> "验收通过"
    "submission_returned" -> "结果退回"
    else -> "任务动态"
}

@Composable
private fun NotificationBadge(label: String, type: String) {
    val (background, foreground) = when (type) {
        "task_cancelled", "submission_returned", "approval_returned" -> C.ErrorPale to C.Error
        "submission_accepted", "approval_approved" -> C.TealPale to C.Teal
        "submission_received" -> C.VioletPale to C.Violet
        "approval_required", "approval_pending" -> C.BluePale to C.Blue
        "approval_resolved" -> C.Line to C.Muted
        else -> C.AmberPale to C.Orange
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(50)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun displayNotificationTime(value: String): String = value.replace('T', ' ').take(16)
