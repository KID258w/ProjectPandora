package com.projectpandora.app.feature.views

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.projectpandora.app.core.network.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.Calendar
import java.util.TimeZone

/**
 * Contract tests against real responses captured from the Django backend of PR #31
 * (`feature/06-work-views-backend`, seeded demo data, viewer = team leader, target = employee).
 * Run on a UTC default zone to prove grouping follows the business zone, not the device.
 */
class WorkViewContractTest {
    private lateinit var old: TimeZone
    @Before fun utcDevice() { old = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restore() = TimeZone.setDefault(old)

    private fun fixture(name: String): String = checkNotNull(javaClass.classLoader?.getResource("work-views/$name.json")).readText()
    private fun data(name: String): JsonObject = JsonParser.parseString(fixture(name)).asJsonObject.data()

    @Test fun monthResponseDrawsAtMostThreeLanesAndExposesExactOverflow() {
        val month = data("month")
        val days = month.array("days")
        assertEquals(Calendar.getInstance().apply { set(month.long("year").toInt(), month.long("month").toInt() - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH), days.size)
        days.forEach { assertTrue(it.array("bars").size <= 3); assertTrue(it.long("overflow_count") >= 0) }
        assertTrue("seed data has an over-full day", days.any { it.long("overflow_count") > 0 && it.array("bars").size == 3 })
        days.chunked(7).forEach { week ->
            val segments = weekSegments(week)
            assertTrue(segments.all { it.lane in 0..2 })
            week.indices.forEach { column -> val onDay = segments.filter { column in it.start..it.end }; assertEquals(onDay.size, onDay.map { it.lane }.distinct().size) }
        }
        days.flatMap { it.array("bars") }.forEach { bar ->
            assertTrue(bar.string("title").isNotBlank()); assertTrue(bar.string("bar_color").matches(Regex("#[0-9A-Fa-f]{6}")))
            assertFalse("bars carry no body text", bar.has("description"))
        }
    }

    @Test fun weekResponseHasSevenDatesAndAllDayBarsInsideTheWeek() {
        val week = data("week")
        val dates = week.get("dates").asJsonArray.map { it.asString }
        assertEquals(7, dates.size)
        assertEquals(week.string("start_date"), dates.first())
        week.array("all_day_tasks").forEach { bar ->
            assertEquals(dates.first(), bar.string("start_date"))
            assertTrue(bar.string("end_date") in dates)
        }
        week.array("events").forEach { event -> assertTrue(date(eventLocalTime(event.string("occurred_at"))!!) in dates) }
    }

    @Test fun dayEventsParseIntoTheRequestedBusinessDateInOrder() {
        val day = data("day")
        val events = day.array("events")
        assertTrue(events.isNotEmpty())
        val times = events.map { eventLocalTime(it.string("occurred_at")) }
        assertTrue("every timestamp format from Django parses (UTC Z, micro-seconds)", times.all { it != null })
        times.forEach { assertEquals(day.string("date"), date(it!!)) }
        assertEquals(times.map { it!!.timeInMillis }.sorted(), times.map { it!!.timeInMillis })
        events.forEach { event ->
            assertNotEquals(event.string("event_type"), eventLabel(event.string("event_type")))
            assertEquals(setOf("task_id", "task_title", "event_type", "occurred_at", "tag_color"), event.keySet())
        }
    }

    @Test fun memberProfileExposesTheWorkViewPermission() {
        val member = data("member")
        assertTrue(member.bool("can_view_work_views"))
        assertFalse(member.bool("is_self"))
    }

    @Test fun forbiddenTargetSurfacesTheServerMessage() {
        val body = fixture("forbidden").toResponseBody("application/json".toMediaType())
        assertEquals("不能查看该成员的工作视图。", HttpException(Response.error<Any>(403, body)).businessMessage())
    }
}
