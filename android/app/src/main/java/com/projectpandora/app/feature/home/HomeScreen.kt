package com.projectpandora.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import com.projectpandora.app.R
import com.projectpandora.app.core.network.*
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

private const val ROOT = "api/v1/app/"

@Composable
fun HomeScreen(user: BusinessUser, api: BusinessApi, onTask: (Long) -> Unit, onLog: (String) -> Unit, onApprovalTask: (Long) -> Unit = onTask, notificationRefreshKey: Int = 0, onViews: () -> Unit = {}) {
    var page by remember { mutableStateOf("home") }
    var reload by remember { mutableIntStateOf(0) }
    var unreadNotificationCount by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
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
    LaunchedEffect(page, reload, notificationRefreshKey) {
        if (page == "home") {
            try {
                unreadNotificationCount = api.get(ROOT + "notifications/unread-count/").data().long("unread_count")
            } catch (_: Exception) {
                // The homepage panels use local sample data; preserve the last notification count while offline.
            }
        }
    }
    if (page == "notifications") {
        TaskNotificationsScreen(api, onBack = { page = "home"; reload++ }, onTask = { id, approval -> if (approval) onApprovalTask(id) else onTask(id) }, reloadKey = reload + notificationRefreshKey)
        return
    }
    val date = remember { SimpleDateFormat("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE).format(Date()) }
    PageColumn {
        BrandHeader()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PageTitle("你好，${user.name}", small = true)
            Box(Modifier.size(48.dp)) {
                IconButton(
                    onClick = { page = "notifications" },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center).testTag("task_notifications").semantics {
                        contentDescription = if (unreadNotificationCount > 0) "任务通知，${unreadNotificationCount}条未读" else "任务通知"
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.setting_notification),
                        contentDescription = null,
                        modifier = Modifier.size(25.dp),
                        tint = C.Blue,
                    )
                }
                if (unreadNotificationCount > 0) {
                    Box(
                        Modifier.align(androidx.compose.ui.Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp)
                            .size(10.dp).background(C.Error, CircleShape).border(1.dp, Color.White, CircleShape),
                    )
                }
            }
        }
        Text(user.organization + "  " + date, fontSize = 12.sp, color = C.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("公司重要事项", listOf("产品阶段评审", "客户服务升级", "系统稳定性提升"), C.AmberPale, C.Orange, Modifier.weight(1f), onViews)
            SummaryCard("公司十大重要任务", listOf("产品阶段评审", "客户需求复盘", "季度目标拆解"), C.BluePale, C.Blue, Modifier.weight(1f), onViews)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("个人重点工作", listOf("完成需求评审", "整理测试计划", "确认项目风险"), C.TealPale, C.Teal, Modifier.weight(1f), onViews)
            SummaryCard("最近日志", listOf("完成产品需求评审", "整理项目进展", "同步团队工作"), C.VioletPale, C.Violet, Modifier.weight(1f), { onLog("") }, "填写今日日志 ›")
        }
        Note("今天也把重要的工作，稳稳向前推进。")
    }
}

@Composable
private fun SummaryCard(title: String, rows: List<String>, pale: Color, accent: Color,
    modifier: Modifier, onClick: () -> Unit, action: String = "查看全部 ›") {
    Column(modifier.heightIn(min = 200.dp).background(pale, RoundedCornerShape(20.dp))
        .border(1.dp, accent.copy(alpha = 0.13f), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = accent)
        rows.forEach { Text("• $it", fontSize = 12.sp, lineHeight = 18.sp, color = C.Ink) }
        Spacer(Modifier.weight(1f))
        Text(action, fontSize = 12.sp, color = accent)
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
