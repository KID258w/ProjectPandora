package com.projectpandora.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.projectpandora.app.core.network.BusinessApi
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.feature.home.HomeScreen
import com.projectpandora.app.feature.home.PersonalDailyItemsScreen
import com.projectpandora.app.ui.theme.PandoraAppTheme
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class PersonalDailyItemsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun homeReplacesPersonalFocusWithTodaysDailyItemsAndOpensCalendar() {
        val api = DailyItemsFakeApi()
        compose.setContent {
            PandoraAppTheme {
                HomeScreen(BusinessUser("a", "小王", "员工"), api, {}, {})
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("今天整理资料", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dashboard-card-每日事项").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { api.getRequests.any { it.first.endsWith("personal-items/") } }
        assertTrue(api.getRequests.last { it.first.endsWith("personal-items/") }.second.containsKey("start_date"))
        compose.onNodeWithText("个人十大重点工作").assertDoesNotExist()
    }

    @Test fun calendarDateListOpensDetailUsingFullItemResponse() {
        val api = DailyItemsFakeApi()
        compose.setContent { PandoraAppTheme { PersonalDailyItemsScreen(api, {}) } }
        compose.waitUntil(10_000) { api.getRequests.any { it.first.endsWith("personal-items/") } }

        val today = businessToday()
        compose.onNodeWithTag("personal-item-day-$today").performClick()
        compose.onNodeWithTag("personal-item-day-card-1").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { api.getRequests.any { it.first.endsWith("personal-items/1/") } }
        compose.onNodeWithText("完整详情说明").assertIsDisplayed()
    }

    @Test fun createFormSendsRequiredDatesAndPresetColor() {
        val api = DailyItemsFakeApi()
        compose.setContent { PandoraAppTheme { PersonalDailyItemsScreen(api, {}) } }
        compose.waitUntil(10_000) { api.getRequests.any { it.first.endsWith("personal-items/") } }
        compose.onNodeWithTag("create-personal-daily-item").performClick()
        compose.onNodeWithTag("personal-item-title").performTextInput("准备周会")
        compose.onNodeWithTag("save-personal-daily-item").performClick()
        compose.waitUntil(10_000) { api.writeRequests.any { it.first == "POST api/v1/app/personal-items/" } }
        val body = api.writeRequests.last().second
        assertEquals("准备周会", body.get("title").asString)
        assertEquals(businessToday(), body.get("start_date").asString)
        assertEquals(body.get("start_date").asString, body.get("end_date").asString)
        assertEquals("#C7DDFF", body.get("color").asString)
    }

    @Test fun longPressDragMovesTheEntireDateRangeThroughTheMoveEndpoint() {
        val api = DailyItemsFakeApi(spanDays = 15)
        compose.setContent { PandoraAppTheme { PersonalDailyItemsScreen(api, {}) } }
        compose.waitUntil(10_000) { api.getRequests.any { it.first.endsWith("personal-items/") } }

        assertTrue(compose.onAllNodesWithTag("personal-item-bar-1").fetchSemanticsNodes().size >= 2)
        compose.onAllNodesWithTag("personal-item-bar-1").onFirst().performTouchInput {
            val origin = center
            down(origin)
            advanceEventTime(700)
            moveTo(origin + Offset(60.dp.toPx(), 0f))
            advanceEventTime(100)
            up()
        }

        compose.waitUntil(10_000) { api.moveRequests.isNotEmpty() }
        val move = api.moveRequests.last().second
        assertTrue(move.get("target_start_date").asString > businessToday())
        val moved = api.items.first { it.get("id").asLong == 1L }
        assertEquals(move.get("target_start_date").asString, moved.get("start_date").asString)
        assertEquals(addDays(moved.get("start_date").asString, 15), moved.get("end_date").asString)
        assertTrue(compose.onAllNodesWithTag("personal-item-bar-1").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithTag("personal-item-day-${moved.get("start_date").asString}").performClick()
        compose.onNodeWithTag("personal-item-day-card-1").assertIsDisplayed()
    }

    @Test fun draggingAtTheRightEdgeAdvancesMonthAndKeepsTheDragActive() {
        dragToMonthEdge(1)
    }

    @Test fun draggingAtTheLeftEdgeMovesToPreviousMonthAndKeepsTheDragActive() {
        dragToMonthEdge(-1)
    }

    private fun dragToMonthEdge(direction: Int) {
        val api = DailyItemsFakeApi(spanDays = 40)
        compose.setContent { PandoraAppTheme { PersonalDailyItemsScreen(api, {}) } }
        compose.waitUntil(10_000) { api.getRequests.any { it.first.endsWith("personal-items/") } }

        val today = businessToday()
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val todayCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            time = dayFormat.parse(today)!!
        }
        val daysToEdge = if (direction > 0) {
            (Calendar.SUNDAY - todayCalendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
        } else {
            (todayCalendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        }
        val edgeDay = addDays(today, if (direction > 0) daysToEdge else -daysToEdge)
        val barBounds = compose.onAllNodesWithTag("personal-item-bar-1").onFirst().fetchSemanticsNode().boundsInRoot
        val gridBounds = compose.onNodeWithTag("personal-items-calendar-grid").fetchSemanticsNode().boundsInRoot
        val edgeCellBounds = compose.onNodeWithTag("personal-item-day-$edgeDay").fetchSemanticsNode().boundsInRoot
        val dragOrigin = barBounds.center - gridBounds.topLeft
        val edgeInsetPx = 12f * compose.activity.resources.displayMetrics.density
        val edgePointInGrid = Offset(
            x = if (direction > 0) edgeCellBounds.right - gridBounds.left - edgeInsetPx
                else edgeCellBounds.left - gridBounds.left + edgeInsetPx,
            y = dragOrigin.y,
        )
        val adjacentMonth = todayCalendar.clone() as Calendar
        adjacentMonth.set(Calendar.DAY_OF_MONTH, 1)
        adjacentMonth.add(Calendar.MONTH, direction)
        val expectedMonth = "${adjacentMonth.get(Calendar.YEAR)}年 ${adjacentMonth.get(Calendar.MONTH) + 1}月"

        compose.onNodeWithTag("personal-items-calendar-grid").performTouchInput {
            down(dragOrigin)
            advanceEventTime(700)
            moveTo(edgePointInGrid, delayMillis = 1000)
            up()
        }

        compose.onNodeWithTag("personal-items-month-picker").assertTextContains(expectedMonth, substring = true)
        compose.waitUntil(10_000) { api.moveRequests.isNotEmpty() }
        val movedStart = api.moveRequests.last().second.get("target_start_date").asString
        assertTrue(if (direction > 0) movedStart > today else movedStart < today)
    }
}

private class DailyItemsFakeApi(private val spanDays: Int = 2) : BusinessApi {
    val getRequests = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
    val writeRequests = java.util.concurrent.CopyOnWriteArrayList<Pair<String, JsonObject>>()
    val moveRequests = java.util.concurrent.CopyOnWriteArrayList<Pair<String, JsonObject>>()
    val items = java.util.concurrent.CopyOnWriteArrayList<JsonObject>().apply {
        add(item(1, "今天整理资料", businessToday(), addDays(businessToday(), spanDays), "#C7DDFF", "完整详情说明"))
    }

    private fun item(id: Long, title: String, start: String, end: String, color: String, description: String) = JsonObject().apply {
        addProperty("id", id)
        addProperty("title", title)
        addProperty("start_date", start)
        addProperty("end_date", end)
        addProperty("color", color)
        addProperty("description", description)
        addProperty("created_at", "2026-11-12T08:00:00+08:00")
        addProperty("updated_at", "2026-11-12T08:00:00+08:00")
    }

    private fun response(data: String): JsonObject = JsonParser.parseString(
        """{"code":200,"message":"success","data":$data,"requestId":"test"}""",
    ).asJsonObject

    override suspend fun get(path: String, query: Map<String, String>): JsonObject {
        getRequests += path to query
        return when {
            path.endsWith("home/") -> response(
                """{"company_items":[],"company_top_tasks":[],"personal_focus_tasks":[],"personal_daily_items":[{"id":1,"title":"今天整理资料"}],"recent_logs":[],"unread_notification_count":0}""",
            )
            path.endsWith("notifications/unread-count/") -> response("""{"unread_count":0}""")
            path.endsWith("personal-items/") -> response("""{"items":${itemsJson()}}""")
            path.contains("personal-items/") -> {
                val id = path.substringAfter("personal-items/").substringBefore('/').toLong()
                response(items.first { it.get("id").asLong == id }.toString())
            }
            else -> response("{}")
        }
    }

    override suspend fun getBody(path: String): Response<ResponseBody> = Response.success(ByteArray(0).toResponseBody())

    override suspend fun post(path: String, body: JsonObject): JsonObject {
        writeRequests += "POST $path" to body.deepCopy()
        if (path == "api/v1/app/personal-items/") {
            val created = item(2, body.get("title").asString, body.get("start_date").asString,
                body.get("end_date").asString, body.get("color").asString, body.get("description").asString)
            items += created
            return response(created.toString())
        }
        if (path.endsWith("/move/")) {
            moveRequests += path to body.deepCopy()
            val moved = item(
                1,
                "今天整理资料",
                body.get("target_start_date").asString,
                addDays(body.get("target_start_date").asString, spanDays),
                "#C7DDFF",
                "完整详情说明",
            )
            items[items.indexOfFirst { it.get("id").asLong == 1L }] = moved
            return response(moved.toString())
        }
        return response("{}")
    }

    override suspend fun postEmpty(path: String): JsonObject = response("{}")
    override suspend fun put(path: String, body: JsonObject): JsonObject = response("{}")
    override suspend fun patch(path: String, body: JsonObject): JsonObject = response("{}")
    override suspend fun delete(path: String): JsonObject = response("""{"deleted":true,"id":1}""")

    private fun itemsJson(): String = "[${items.joinToString(",") { row ->
        JsonObject().apply {
            add("id", row.get("id"))
            add("title", row.get("title"))
            add("start_date", row.get("start_date"))
            add("end_date", row.get("end_date"))
            add("color", row.get("color"))
        }.toString()
    }}]"
}

private fun businessToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
}.format(Date())

private fun addDays(value: String, days: Int): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        isLenient = false
    }
    val date = format.parse(value)!!
    return format.format(Date(date.time + days * 86_400_000L))
}
