package com.projectpandora.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.BusinessApi
import com.projectpandora.app.feature.organization.OrganizationMemberProfileScreen
import com.projectpandora.app.feature.tasks.TaskCenterScreen
import com.projectpandora.app.feature.views.ViewsScreen
import com.projectpandora.app.ui.theme.PandoraAppTheme
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class WorkViewsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun personalViewDefaultsToCurrentMonthAndSwitchesModes() {
        val api = FixtureApi(true)
        compose.setContent { PandoraAppTheme { ViewsScreen(api, true, {}, {}, { _, _ -> }) } }
        compose.waitUntil(5000) { api.requests.any { it.first.endsWith("month/") } }
        assertFalse(api.requests.first().second.containsKey("user_id"))
        compose.onNodeWithText("本月").assertDoesNotExist()
        compose.onNodeWithContentDescription("下一周期").performClick()
        compose.onNodeWithText("本月").assertIsDisplayed().performClick()
        compose.onNodeWithText("本月").assertDoesNotExist()
        compose.onNodeWithTag("mode-周").performClick()
        compose.waitUntil(5000) { api.requests.any { it.first.endsWith("week/") } }
        compose.onNodeWithText("全天").assertIsDisplayed()
        compose.onNodeWithText("23:00").assertExists()
    }

    @Test fun memberViewQueriesTheMemberAndOpensTasksReadOnly() {
        val api = FixtureApi(true)
        var opened: Pair<Long, Boolean>? = null
        var back = false
        compose.setContent { PandoraAppTheme { ViewsScreen(api, true, {}, {}, { id, readOnly -> opened = id to readOnly }, memberId = 42, memberName = "林予安", onBackToMember = { back = true }) } }
        compose.onNodeWithText("林予安的视图").assertIsDisplayed()
        compose.onNodeWithText("下属工作视图 · 只读").assertIsDisplayed()
        compose.onNodeWithTag("mode-日").performClick()
        compose.waitUntil(5000) { api.requests.any { it.first.endsWith("day/") && it.second["user_id"] == "42" } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("示例任务").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("示例任务").performClick()
        compose.runOnIdle { assertEquals(7L to true, opened) }
        assertTrue(api.requests.filter { it.first.contains("work-views") }.all { it.second["user_id"] == "42" })
        compose.onNodeWithText("‹ 返回").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test fun returnFromTaskCenterKeepsTheSelectedWorkView() {
        val api = FixtureApi(true)
        compose.setContent {
            PandoraAppTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "views") {
                    composable("views") { ViewsScreen(api, true, { nav.navigate("tasks") }, {}, { _, _ -> }) }
                    composable("tasks") { TextButton(onClick = { nav.popBackStack() }) { Text("返回视图") } }
                }
            }
        }
        compose.onNodeWithTag("mode-周").performClick()
        compose.waitUntil(5000) { api.requests.any { it.first.endsWith("week/") } }
        compose.onNodeWithText("任务中心").performClick()
        compose.onNodeWithText("返回视图").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("示例任务").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("全天").assertIsDisplayed()
        assertTrue(api.requests.last().first.endsWith("week/"))
    }

    @Test fun memberProfileShowsViewEntryOnlyWhenAuthorized() {
        var opened: Pair<Long, String>? = null
        compose.setContent { PandoraAppTheme { OrganizationMemberProfileScreen(FixtureApi(true), 42, {}, {}, {}, { id, name -> opened = id to name }) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("查看视图").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("查看视图").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(42L to "林予安", opened) }
    }

    @Test fun unauthorizedMemberProfileHasNoViewEntry() {
        compose.setContent { PandoraAppTheme { OrganizationMemberProfileScreen(FixtureApi(false), 42, {}, {}, {}, { _, _ -> }) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("林予安").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("查看视图").assertDoesNotExist()
    }

    @Test fun workViewTaskDetailDoesNotOfferWriteActions() {
        val api = object : FixtureApi(true) {
            override suspend fun get(path: String, query: Map<String, String>): JsonObject {
                val value = JsonObject()
                when {
                    path.endsWith("me/") -> value.addProperty("id", 1)
                    path.endsWith("history/") -> value.add("results", JsonArray())
                    else -> {
                        value.addProperty("id", 7); value.addProperty("title", "下属任务详情")
                        value.addProperty("lifecycle_status", "active"); value.addProperty("completion_status", "unfinished"); value.addProperty("approval_status", "approved")
                        value.add("creator", JsonObject().apply { addProperty("id", 1) }); value.add("assignee", JsonObject().apply { addProperty("id", 1) })
                    }
                }
                return JsonObject().apply { add("data", value) }
            }
        }
        compose.setContent { PandoraAppTheme { TaskCenterScreen(api, {}, 7, forceReadOnly = true) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("下属任务详情").fetchSemanticsNodes().isNotEmpty() }
        listOf("编辑任务", "追加进展", "提交完成", "取消任务").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }
}

private open class FixtureApi(private val permitted: Boolean) : BusinessApi {
    val requests: MutableList<Pair<String, Map<String, String>>> = Collections.synchronizedList(mutableListOf())
    private fun obj(block: JsonObject.() -> Unit) = JsonObject().apply(block)
    private fun array(vararg objects: JsonObject) = JsonArray().apply { objects.forEach { add(it) } }
    override suspend fun get(path: String, query: Map<String, String>): JsonObject {
        requests.add(path to query)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val value = when {
            path.contains("members/") -> obj {
                addProperty("id", 42); addProperty("display_name", "林予安"); addProperty("phone", "13800000042"); addProperty("business_role", "employee")
                addProperty("can_view_work_views", permitted); addProperty("is_self", false)
            }
            else -> obj {
                add("days", JsonArray()); add("events", JsonArray())
                add("all_day_tasks", array(obj { addProperty("task_id", 7); addProperty("title", "示例任务"); addProperty("bar_color", "#FFE9AD"); addProperty("start_date", today); addProperty("end_date", today) }))
            }
        }
        return obj { add("data", value) }
    }
    override suspend fun getBody(path: String): Response<ResponseBody> = Response.error(404, "".toResponseBody())
    override suspend fun post(path: String, body: JsonObject) = JsonObject()
    override suspend fun postEmpty(path: String) = JsonObject()
    override suspend fun put(path: String, body: JsonObject) = JsonObject()
    override suspend fun patch(path: String, body: JsonObject) = JsonObject()
    override suspend fun delete(path: String) = JsonObject()
}
