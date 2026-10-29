package com.projectpandora.app.feature.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.BuildConfig
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

private const val ROOT = "api/v1/app/"

@Composable
fun HomeScreen(user: BusinessUser, api: BusinessApi, onTask: (Long) -> Unit, onLog: (String) -> Unit, onApprovalTask: (Long) -> Unit = onTask, notificationRefreshKey: Int = 0) {
    val context = LocalContext.current
    var page by remember { mutableStateOf("home") }; var itemReturn by remember { mutableStateOf("home") }; var data by remember { mutableStateOf<JsonObject?>(null) }
    var list by remember { mutableStateOf<List<JsonObject>>(emptyList()) }; var item by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var reload by remember { mutableIntStateOf(0) }
    var unreadNotificationCount by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(page, lifecycleOwner) {
        if (page == "home") {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(30_000)
                    try { unreadNotificationCount = api.get(ROOT + "notifications/unread-count/").data().long("unread_count") }
                    catch (_: Exception) { /* Keep the last known count during a transient connection failure. */ }
                }
            }
        }
    }
    LaunchedEffect(page, reload) {
        error = ""; busy = true
        try {
            when (page) {
                "home" -> {
                    try { data = api.get(ROOT + "home/").data() }
                    catch (e: Exception) { error = e.businessMessage() }
                    try { unreadNotificationCount = api.get(ROOT + "notifications/unread-count/").data().long("unread_count") }
                    catch (e: Exception) { if (error.isBlank()) error = e.businessMessage() }
                }
                "items" -> list = api.all(ROOT + "company-items/").take(10)
                "company-tasks" -> list = api.all(ROOT + "home/company-top-tasks/").take(10)
            }
        } catch (e: Exception) { error = e.businessMessage() } finally { busy = false }
    }
    if (page == "focus") { FocusScreen(api, onBack = { page = "home"; reload++ }, onTask = onTask); return }
    if (page == "notifications") { TaskNotificationsScreen(api, onBack = { page = "home"; reload++ }, onTask = { id, approval -> if (approval) onApprovalTask(id) else onTask(id) }, reloadKey = reload + notificationRefreshKey); return }
    PageColumn {
        if (page != "home") BackButton { page = if (page == "item") itemReturn else "home"; reload++ }
        if (page == "home") {
            BrandHeader(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PageTitle("你好，${user.name}", small = true)
                TextButton(onClick = { page = "notifications" }) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("任务通知", color = C.Ink)
                        if (unreadNotificationCount > 0) Box(Modifier.size(8.dp).background(C.Error, CircleShape))
                    }
                }
            }
            Text(user.organization)
        } else PageTitle(when (page) { "items" -> "公司重要事项"; "item" -> "事项详情"; "company-tasks" -> "公司十大重要任务"; else -> "任务通知" }, small = true)
        if (busy) CircularProgressIndicator()
        if (error.isNotEmpty()) { Note(error); TextButton(onClick = { reload++ }) { Text("重试") } }
        when (page) {
            "home" -> data?.let { d ->
                Panel("公司重要事项", d.array("company_items"), { it.string("title") }, { page = "items" }) { row -> scope.launch { try { item = api.get(ROOT + "company-items/${row.long("id")}/").data(); itemReturn = "home"; page = "item" } catch (e: Exception) { error = e.businessMessage() } } }
                Panel("公司十大重要任务", d.array("company_top_tasks"), { it.string("title") }, { page = "company-tasks" }) { onTask(it.long("id")) }
                Panel("个人十大重点工作", d.array("personal_focus_tasks"), { it.string("title") }, { page = "focus" }) { onTask(it.long("id", it.long("task_id"))) }
                Panel("最近日志", d.array("recent_logs"), { it.string("log_date") + " · " + it.string("summary") }, { onLog("") }) { onLog(it.string("log_date")) }
            }
            "items" -> list.forEach { row -> TextButton(onClick = { scope.launch { try { item = api.get(ROOT + "company-items/${row.long("id")}/").data(); itemReturn = "items"; page = "item" } catch (e: Exception) { error = e.businessMessage() } } }, modifier = Modifier.fillMaxWidth()) { Text(row.string("title") + "  " + row.string("summary")) } }
            "item" -> item?.let { Text(it.string("title")); Text(it.string("body")); it.array("attachments").forEach { attachment ->
                val url = attachment.string("url")
                TextButton(onClick = {
                    try {
                        val uri = Uri.parse(BuildConfig.API_BASE_URL).resolveAsset(url)
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) { error = "无法打开附件。" }
                }, enabled = url.isNotBlank()) { Text("附件：" + attachment.string("original_name")) }
            } }
            "company-tasks" -> list.forEach { row -> TextButton(onClick = { onTask(row.long("id")) }, modifier = Modifier.fillMaxWidth()) { Text(row.string("title")) } }
            "notifications" -> list.forEach { row -> TextButton(onClick = { onTask(row.long("task_id")) }, modifier = Modifier.fillMaxWidth()) { Text(row.string("task_title") + " · " + row.string("notification_type")) } }
        }
        if (page != "home" && page != "item" && !busy && list.isEmpty() && error.isEmpty()) Text("暂无内容")
    }
}

private fun Uri.resolveAsset(path: String): Uri {
    val resolved = java.net.URI(toString()).resolve(path)
    require(resolved.scheme == "http" || resolved.scheme == "https")
    return Uri.parse(resolved.toString())
}

@Composable
private fun Panel(title: String, rows: List<JsonObject>, label: (JsonObject) -> String, onAll: () -> Unit, onRow: (JsonObject) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.AmberPale, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); TextButton(onClick = onAll) { Text("查看全部 ›") } }
        if (rows.isEmpty()) Text("暂无内容")
        rows.take(5).forEach { row -> Text(label(row), Modifier.fillMaxWidth().clickable { onRow(row) }.padding(vertical = 8.dp)) }
    }
}

@Composable
internal fun FocusScreen(api: BusinessApi, onBack: () -> Unit, onTask: (Long) -> Unit) {
    var selected by remember { mutableStateOf<List<JsonObject>>(emptyList()) }; var candidates by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var loaded by remember { mutableStateOf(false) }; var keyword by remember { mutableStateOf("") }; var reload by remember { mutableIntStateOf(0) }; val scope = rememberCoroutineScope()
    fun id(row: JsonObject) = row.long("task_id", row.long("id"))
    LaunchedEffect(reload) { busy = true; loaded = false; error = ""; try { val current = api.get(ROOT + "home/personal-focus/").data().array("tasks"); val available = api.all(ROOT + "home/personal-focus/candidates/"); selected = current; candidates = available; loaded = true } catch (e: Exception) { error = e.businessMessage() } finally { busy = false } }
    fun move(index: Int, delta: Int) { val target = index + delta; if (target in selected.indices) { val copy = selected.toMutableList(); val v = copy.removeAt(index); copy.add(target, v); selected = copy } }
    PageColumn {
        BackButton(onBack); PageTitle("个人十大重点工作", small = true); Text("已选 ${selected.size} / 10 · 长按拖动调整顺序")
        if (busy) CircularProgressIndicator(); if (error.isNotEmpty()) { Note(error); TextButton(onClick = { reload++ }) { Text("重试") } }
        selected.forEachIndexed { index, row ->
            var drag by remember(id(row)) { mutableFloatStateOf(0f) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("☰", Modifier.pointerInput(id(row), index) { detectDragGesturesAfterLongPress(onDragEnd = { drag = 0f }) { change, amount -> change.consume(); drag += amount.y; if (drag > 48f) { move(index, 1); drag = 0f } else if (drag < -48f) { move(index, -1); drag = 0f } } }.padding(12.dp))
                Text(row.string("title"), Modifier.weight(1f).clickable { onTask(id(row)) }.padding(12.dp))
                TextButton(onClick = { move(index, -1) }, enabled = index > 0) { Text("上移") }
                TextButton(onClick = { selected = selected.filterNot { id(it) == id(row) } }) { Text("移除") }
            }
        }
        OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), label = { Text("搜索可选任务") })
        candidates.filter { it.string("title").contains(keyword, true) && selected.none { s -> id(s) == id(it) } }.forEach { row -> TextButton(onClick = { if (selected.size < 10) selected = selected + row else error = "最多选择 10 项。" }) { Text("＋ ${row.string("title")}") } }
        PrimaryButton("保存顺序", { scope.launch { busy = true; try { api.put(ROOT + "home/personal-focus/", json { add("task_ids", ids(selected.map(::id))) }); onBack() } catch (e: Exception) { error = e.businessMessage() } finally { busy = false } } }, enabled = !busy && loaded)
    }
}
