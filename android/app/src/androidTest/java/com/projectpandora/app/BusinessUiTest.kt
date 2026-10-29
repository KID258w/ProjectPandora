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
import com.projectpandora.app.feature.logs.TagManagementScreen
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
        compose.waitUntil(10000) { compose.onAllNodesWithTag("edit-log").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edit-log").performClick()
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
        compose.waitUntil(10000) { compose.onAllNodesWithText("审批中心").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("审批中心").performClick()
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
        compose.setContent { PandoraAppTheme { TaskNotificationsScreen(api, {}, { _, _ -> }) } }
        compose.waitUntil(10000) { api.calls.any { it.startsWith("POST") && it.contains("mark-read") } }
        val firstIds = api.lastBody!!.getAsJsonArray("notification_ids").map { it.asLong }
        assertTrue(firstIds.isNotEmpty())
        assertFalse(firstIds.contains(20L))
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("通知 20"))
        compose.waitUntil(10000) { api.marked.contains(20L) }
    }

    @Test fun approvalNotificationPreservesItsContext() {
        val api = FakeBusinessApi(); var opened: Pair<Long, Boolean>? = null
        compose.setContent { PandoraAppTheme { TaskNotificationsScreen(api, {}, { id, approval -> opened = id to approval }) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("审批通知示例", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("审批通知示例", substring = true).performClick()
        assertEquals(1L to true, opened)
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

    @Test fun approvalNotificationRequiresCurrentInboxAuthorization() {
        val api = FakeBusinessApi(inboxAllowed = true)
        compose.setContent { PandoraAppTheme { TaskCenterScreen(api, {}, initialTaskId = 9, initialApprovalContext = true) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("审批通过").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("创建人：派发人").performScrollTo().assertExists()
        compose.onNodeWithText("必需材料：评审记录").performScrollTo().assertExists()
        compose.onNodeWithText("关联说明：相关项目").performScrollTo().assertExists()
    }

    @Test fun staleApprovalNotificationCannotExposeApprovalActions() {
        val api = FakeBusinessApi(inboxAllowed = false)
        compose.setContent { PandoraAppTheme { TaskCenterScreen(api, {}, initialTaskId = 9, initialApprovalContext = true) } }
        compose.waitUntil(10000) { api.calls.any { it.contains("task-approvals/inbox") } }
        compose.waitForIdle()
        compose.onNodeWithText("审批通过").assertDoesNotExist()
        compose.onNodeWithText("审批退回").assertDoesNotExist()
    }

    @Test fun employeeSeesOnlyEligibleApprovalQueueAndFilterIsSeparate() {
        val api = FakeBusinessApi(role = "employee")
        compose.setContent { PandoraAppTheme { TaskCenterScreen(api, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("审批中心").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("下属任务").assertDoesNotExist()
        compose.onNodeWithText("截止日期起（YYYY-MM-DD）").assertDoesNotExist()
        compose.onNodeWithText("筛选").performClick()
        compose.onNodeWithText("截止日期起（YYYY-MM-DD）").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("审批中心").performClick()
        compose.onNodeWithText("待你审批").assertDoesNotExist()
        compose.onNodeWithText("待上级审批").assertExists()
        compose.onNodeWithTag("create-task").assertDoesNotExist()
        compose.waitUntil(10000) { api.calls.any { it.contains("task-approvals/outbox") } }
        assertFalse(api.calls.any { it.contains("task-approvals/inbox") })
    }

    @Test fun founderHasInboxAndNoOutbox() {
        val api = FakeBusinessApi(role = "founder")
        compose.setContent { PandoraAppTheme { TaskCenterScreen(api, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("审批中心").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("下属任务").assertExists()
        compose.onNodeWithText("审批中心").performClick()
        compose.onNodeWithText("待你审批").assertExists()
        compose.onNodeWithText("待上级审批").assertDoesNotExist()
    }

    @Test fun returnedTaskEditReturnsToDetailForResubmission() {
        val api = FakeBusinessApi(role = "employee", returnedTask = true)
        compose.setContent { PandoraAppTheme { TaskCenterScreen(api, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("审批中心").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("审批中心").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("审查任务", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("审查任务", substring = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("编辑任务").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("编辑任务").performScrollTo().performClick()
        compose.onNodeWithText("保存").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("重新送审").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("重新送审").performScrollTo().performClick()
        compose.waitUntil(10000) { api.calls.any { it.startsWith("POST") && it.contains("/resubmit/") } }
    }

    @Test fun failedTagCreationKeepsNameAndShowsError() {
        val api = FakeBusinessApi(failTag = true)
        compose.setContent { PandoraAppTheme { TagManagementScreen(api, {}) } }
        compose.onNodeWithText("新建标签").performScrollTo().performClick()
        compose.onNodeWithText("标签名称").performTextInput("新标签")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("无法连接服务器", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("新标签").assertExists()
        compose.onNodeWithText("保存").assertExists()
    }

    @Test fun failedRenameKeepsInputAndDeleteExplainsHistoricalLinks() {
        val api = FakeBusinessApi(failTag = true)
        compose.setContent { PandoraAppTheme { TagManagementScreen(api, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("重命名").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("重命名")[0].performScrollTo().performClick()
        compose.onNodeWithText("标签名称").performTextReplacement("改名后")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("无法连接服务器", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("改名后").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onAllNodesWithText("删除")[0].performScrollTo().performClick()
        compose.onNodeWithText("既有关联的任务和日志仍会显示该标签", substring = true).assertExists()
    }

    @Test fun selectedLogPersonIncludesDepartmentAndTeam() {
        val api = FakeBusinessApi()
        compose.setContent { PandoraAppTheme { LogsScreen(api) } }
        compose.onNodeWithText("选择查看对象").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("王同事 · 产品部 客户端组").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("王同事 · 产品部 客户端组").performClick()
        compose.onNodeWithText("王同事 · 产品部 · 客户端组").performScrollTo().assertExists()
        compose.onNodeWithTag("edit-log").assertDoesNotExist()
    }

    @Test fun taskFormSearchFiltersTagNames() {
        val api = FakeBusinessApi()
        compose.setContent { PandoraAppTheme { TaskCenterScreen(api, {}) } }
        compose.onNodeWithTag("create-task").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("产品").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索启用标签").performScrollTo().performTextInput("协作")
        compose.onNode(hasText("协作") and !hasSetTextAction()).assertExists()
        compose.onNodeWithText("产品").assertDoesNotExist()
    }
}

private class FakeBusinessApi(private val failFocus: Boolean = false, private val role: String = "team_leader", private val inboxAllowed: Boolean = true, private val failTag: Boolean = false, private val returnedTask: Boolean = false) : BusinessApi {
    val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile var lastBody: JsonObject? = null
    val marked = java.util.concurrent.CopyOnWriteArrayList<Long>()
    private fun parse(json: String) = JsonParser.parseString("{\"code\":200,\"message\":\"success\",\"data\":$json}").asJsonObject
    override suspend fun get(path: String, query: Map<String, String>): JsonObject {
        calls += "GET $path"
        return when {
            path.endsWith("home/") -> parse("""{"company_items":[],"company_top_tasks":[{"id":1,"title":"任务 1"},{"id":2,"title":"任务 2"},{"id":3,"title":"任务 3"},{"id":4,"title":"任务 4"},{"id":5,"title":"任务 5"},{"id":6,"title":"任务 6"}],"personal_focus_tasks":[],"recent_logs":[],"unread_notification_count":0}""")
            path.endsWith("notifications/") -> parse("{\"results\":[" + (1..20).joinToString(",") { """{"id":$it,"task_id":$it,"task_title":"${if (it == 1) "审批通知示例" else "通知 $it"}","notification_type":"${if (it == 1) "approval_required" else "assignment_received"}","is_read":false}""" } + "],\"next\":null}")
            path.endsWith("personal-focus/") -> if (failFocus) throw java.io.IOException("offline") else parse("""{"tasks":[{"task_id":1,"title":"重点 A"},{"task_id":2,"title":"重点 B"}],"initialized":true}""")
            path.endsWith("me/") -> parse("""{"id":7,"business_role":"$role"}""")
            path.endsWith("task-approvals/inbox/") -> if (inboxAllowed) parse("""{"results":[{"task_id":9,"title":"审查任务","approval_status":"pending"}],"next":null}""") else parse("""{"results":[],"next":null}""")
            path.endsWith("task-approvals/outbox/") -> if (returnedTask) parse("""{"results":[{"task_id":9,"title":"审查任务","approval_status":"returned"}],"next":null}""") else parse("""{"results":[],"next":null}""")
            path.endsWith("tasks/9/history/") -> parse("""{"results":[]}""")
            path.endsWith("tasks/9/") -> parse("""{"id":9,"title":"审查任务","description":"审批说明","creator":{"id":${if (returnedTask) 7 else 1},"display_name":"派发人"},"assignee":{"id":2},"due_at":"2026-10-30T18:00:00+08:00","required_materials":"评审记录","association_note":"相关项目","approval_status":"${if (returnedTask) "returned" else "pending"}","completion_status":"unfinished","lifecycle_status":"active","approval_history":[],"updates":[],"submissions":[]}""")
            path.endsWith("log-viewers/") -> parse("""{"results":[{"id":5,"display_name":"王同事","department_name":"产品部","team_name":"客户端组"}],"next":null}""")
            path.endsWith("tags/") -> parse("""{"results":[{"id":1,"name":"产品","is_active":true},{"id":2,"name":"协作","is_active":true}],"next":null}""")
            path.contains("logs/calendar/") -> parse("""{"days":[]}""")
            path.contains("logs/mine/") -> parse("""{"log":null,"status":"not_created","can_edit":true}""")
            else -> parse("""{"results":[],"next":null}""")
        }
    }
    override suspend fun post(path: String, body: JsonObject): JsonObject { if (failTag && path.endsWith("tags/")) throw java.io.IOException("offline"); lastBody = body; if (path.contains("mark-read")) marked.addAll(body.getAsJsonArray("notification_ids").map { it.asLong }); calls += "POST $path"; return parse("{}") }
    override suspend fun postEmpty(path: String): JsonObject { calls += "POST $path"; return parse("{}") }
    override suspend fun put(path: String, body: JsonObject): JsonObject { calls += "PUT $path"; lastBody = body; return parse("{}") }
    override suspend fun patch(path: String, body: JsonObject): JsonObject { if (failTag) throw java.io.IOException("offline"); calls += "PATCH $path"; lastBody = body; return parse("{}") }
    override suspend fun delete(path: String): JsonObject { calls += "DELETE $path"; return parse("{}") }
}
