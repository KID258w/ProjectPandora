package com.projectpandora.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.ui.BackButton
import com.projectpandora.app.ui.Note
import com.projectpandora.app.ui.PageTitle
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun TaskNotificationsScreen(api: BusinessApi, onBack: () -> Unit, onTask: (Long) -> Unit) {
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }; var hasNext by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var reload by remember { mutableIntStateOf(0) }
    var readIds by remember { mutableStateOf(setOf<Long>()) }; val listState = rememberLazyListState()
    LaunchedEffect(page, reload) {
        busy = true; error = ""; rows = emptyList(); readIds = emptySet()
        try { val data = api.get("api/v1/app/notifications/", mapOf("page" to page.toString(), "page_size" to "20")).data(); rows = data.array("results"); hasNext = data.string("next").isNotBlank(); listState.scrollToItem(0) }
        catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
    }
    LaunchedEffect(rows, page) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { info -> (info.key as? Long) }.toSet() }.distinctUntilChanged().collect { visible ->
            val idsToMark = rows.filter { it.long("id") in visible && !it.bool("is_read") && it.long("id") !in readIds }.map { it.long("id") }
            if (idsToMark.isNotEmpty()) {
                try { api.post("api/v1/app/notifications/mark-read/", json { add("notification_ids", ids(idsToMark)) }); readIds = readIds + idsToMark }
                catch (e: Exception) { error = e.businessMessage() }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        BackButton(onBack); PageTitle("任务通知", small = true)
        if (busy) CircularProgressIndicator()
        if (error.isNotEmpty()) { Note(error); TextButton(onClick = { reload++ }) { Text("重试") } }
        if (!busy && rows.isEmpty() && error.isEmpty()) Text("暂无通知")
        LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("notification-list")) {
            items(rows, key = { it.long("id") }) { row ->
                val read = row.bool("is_read") || row.long("id") in readIds
                Column(Modifier.fillMaxWidth().clickable { onTask(row.long("task_id")) }.padding(vertical = 14.dp)) {
                    val type = when (row.string("notification_type")) {
                        "approval_required" -> "待审批"
                        "task_assigned" -> "新任务"
                        else -> "任务动态"
                    }
                    Text((if (read) "" else "● ") + row.string("task_title")); Text(type + " · " + row.string("created_at"))
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
