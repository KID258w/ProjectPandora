package com.projectpandora.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.projectpandora.app.core.network.BusinessApi
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.feature.home.HomeScreen
import com.projectpandora.app.feature.home.TaskNotificationsScreen
import com.projectpandora.app.feature.home.FocusScreen
import com.projectpandora.app.feature.logs.LogsScreen
import com.projectpandora.app.feature.tasks.TaskCenterScreen
import com.projectpandora.app.ui.theme.PandoraAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BusinessUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun logRequiresCompletedWorkBeforeSubmissionAndSavesDraft() {
        val api = FakeBusinessApi()
        compose.setContent { PandoraAppTheme { LogsScreen(api) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("填写日志").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("填写日志").performScrollTo().performClick()
        compose.onNodeWithText("提交日志").performScrollTo().performClick()
        compose.onNodeWithText("提交时请填写已完成工作。", substring = true).performScrollTo().assertExists()
        assertFalse(api.calls.any { it.startsWith("PUT ") })
        compose.onNodeWithText("已完成工作（提交必填）").performTextInput("完成评审")
        compose.onNodeWithText("保存草稿").performScrollTo().performClick()
        compose.waitUntil(10000) { api.calls.any { it.startsWith("PUT ") } }
        assertEquals("完成评审", api.lastBody?.get("completed_work")?.asString)
        assertFalse(api.calls.any { it.contains("/submit/") })
    }

    @Test fun homeShowsAtMostFiveItemsAndOpensSelectedTask() {
        val api = FakeBusinessApi(); var opened = 0L
        compose.setContent { PandoraAppTheme { HomeScreen(BusinessUser("a", "小王", "员工"), api, { opened = it }, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("任务 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("任务 1").performScrollTo().performClick()
        assertEquals(1L, opened)
        compose.onNodeWithText("任务 6").assertDoesNotExist()
    }

    @Test fun approvalReturnRequiresReasonAndSendsIt() {
        val api = FakeBusinessApi()
        compose.setContent { PandoraAppTheme { TaskCenterScreen(api, {}) } }
        compose.onNodeWithText("待我审批").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("审查任务", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("审查任务", substring = true).performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("审批退回").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("审批退回").performScrollTo().performClick()
        compose.onNodeWithText("确定").performClick()
        assertFalse(api.calls.any { it.contains("/approval/") && it.startsWith("POST") })
        compose.onNodeWithText("内容 / 原因").performTextInput("补充说明")
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(10000) { api.calls.any { it.startsWith("POST") && it.contains("/approval/") } }
        assertEquals("returned", api.lastBody?.get("decision")?.asString)
        assertEquals("补充说明", api.lastBody?.get("comment")?.asString)
    }

    @Test fun notificationsOnlyMarkRowsThatWereVisible() {
        val api = FakeBusinessApi()
        compose.setContent { PandoraAppTheme { TaskNotificationsScreen(api, {}, {}) } }
        compose.waitUntil(10000) { api.calls.any { it.startsWith("POST") && it.contains("mark-read") } }
        val firstIds = api.lastBody!!.getAsJsonArray("notification_ids").map { it.asLong }
        assertTrue(firstIds.isNotEmpty())
        assertFalse(firstIds.contains(20L))
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("通知 20"))
        compose.waitUntil(10000) { api.marked.contains(20L) }
    }

    @Test fun focusOrderSavesTheUserChosenOrder() {
        val api = FakeBusinessApi()
        compose.setContent { PandoraAppTheme { FocusScreen(api, {}, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("重点 B").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("上移")[1].performScrollTo().performClick()
        compose.onNodeWithText("保存顺序").performScrollTo().performClick()
        compose.waitUntil(10000) { api.calls.any { it.startsWith("PUT") && it.contains("personal-focus") } }
        assertEquals(listOf(2L, 1L), api.lastBody!!.getAsJsonArray("task_ids").map { it.asLong })
    }

    @Test fun focusLoadFailureCannotSaveAnEmptyReplacement() {
        val api = FakeBusinessApi(failFocus = true)
        compose.setContent { PandoraAppTheme { FocusScreen(api, {}, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("无法连接服务器", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("保存顺序").performScrollTo().assertIsNotEnabled()
        assertFalse(api.calls.any { it.startsWith("PUT") })
    }
}

private class FakeBusinessApi(private val failFocus: Boolean = false) : BusinessApi {
    val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile var lastBody: JsonObject? = null
    val marked = java.util.concurrent.CopyOnWriteArrayList<Long>()
    private fun parse(json: String) = JsonParser.parseString("{\"code\":200,\"message\":\"success\",\"data\":$json}").asJsonObject
    override suspend fun get(path: String, query: Map<String, String>): JsonObject {
        calls += "GET $path"
        return when {
            path.endsWith("home/") -> parse("""{"company_items":[],"company_top_tasks":[{"id":1,"title":"任务 1"},{"id":2,"title":"任务 2"},{"id":3,"title":"任务 3"},{"id":4,"title":"任务 4"},{"id":5,"title":"任务 5"},{"id":6,"title":"任务 6"}],"personal_focus_tasks":[],"recent_logs":[],"unread_notification_count":0}""")
            path.endsWith("notifications/") -> parse("{\"results\":[" + (1..20).joinToString(",") { """{"id":$it,"task_id":$it,"task_title":"通知 $it","is_read":false}""" } + "],\"next\":null}")
            path.endsWith("personal-focus/") -> if (failFocus) throw java.io.IOException("offline") else parse("""{"tasks":[{"task_id":1,"title":"重点 A"},{"task_id":2,"title":"重点 B"}],"initialized":true}""")
            path.endsWith("me/") -> parse("""{"id":7}""")
            path.endsWith("task-approvals/inbox/") -> parse("""{"results":[{"task_id":9,"title":"审查任务","approval_status":"pending"}],"next":null}""")
            path.endsWith("tasks/9/history/") -> parse("""{"results":[]}""")
            path.endsWith("tasks/9/") -> parse("""{"id":9,"title":"审查任务","description":"审批说明","creator":{"id":1},"assignee":{"id":2},"approval_status":"pending","completion_status":"unfinished","lifecycle_status":"active","approval_history":[],"updates":[],"submissions":[]}""")
            path.contains("logs/calendar/") -> parse("""{"days":[]}""")
            path.contains("logs/mine/") -> parse("""{"log":null,"status":"not_created","can_edit":true}""")
            else -> parse("""{"results":[],"next":null}""")
        }
    }
    override suspend fun post(path: String, body: JsonObject): JsonObject { lastBody = body; if (path.contains("mark-read")) marked.addAll(body.getAsJsonArray("notification_ids").map { it.asLong }); calls += "POST $path"; return parse("{}") }
    override suspend fun postEmpty(path: String): JsonObject { calls += "POST $path"; return parse("{}") }
    override suspend fun put(path: String, body: JsonObject): JsonObject { calls += "PUT $path"; lastBody = body; return parse("{}") }
    override suspend fun patch(path: String, body: JsonObject): JsonObject { calls += "PATCH $path"; lastBody = body; return parse("{}") }
    override suspend fun delete(path: String): JsonObject { calls += "DELETE $path"; return parse("{}") }
}
