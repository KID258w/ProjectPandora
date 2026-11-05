package com.projectpandora.app.feature.views

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WorkViewTimeTest {
    private fun day(vararg ids: Int) = JsonObject().apply {
        add("bars", JsonArray().apply { ids.forEach { add(JsonObject().apply { addProperty("task_id", it); addProperty("title", "任务$it") }) } })
    }

    @Test fun monthBarsMergeOnlyVisibleAdjacentDaysAndKeepLanesSeparate() {
        val segments = weekSegments(listOf(day(1, 2), day(1, 2, 3), day(2, 3), day(1, 2, 3), day(1), null, null))
        val first = segments.single { it.bar.get("task_id").asInt == 1 && it.start == 0 }
        val later = segments.single { it.bar.get("task_id").asInt == 1 && it.start == 3 }
        assertEquals(1, first.end)
        assertEquals(4, later.end)
        for (column in 0..6) {
            val onDay = segments.filter { column in it.start..it.end }
            assertEquals(onDay.size, onDay.map { it.lane }.distinct().size)
        }
    }

    @Test fun monthBarsNeverDrawMoreThanThreeLanesPerDay() {
        val segments = weekSegments(listOf(day(1, 2, 3, 4, 5), null, null, null, null, null, null))
        assertEquals(3, segments.size)
        assertEquals(setOf(0, 1, 2), segments.map { it.lane }.toSet())
    }

    @Test fun offsetsAndFractionalSecondsUseBusinessTimezoneEvenOnUtcDevices() {
        val old = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val time = eventLocalTime("2026-11-03T16:15:30.123456Z")!!
            assertEquals(4, time.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, time.get(Calendar.HOUR_OF_DAY))
            assertEquals(15, time.get(Calendar.MINUTE))
            assertEquals(123, time.get(Calendar.MILLISECOND))
            assertEquals(9, eventLocalTime("2026-11-03T09:00:00+08:00")!!.get(Calendar.HOUR_OF_DAY))
            assertNull(eventLocalTime("invalid"))
        } finally { TimeZone.setDefault(old) }
    }

    @Test fun currentPeriodDetectionDrivesTheReturnToCurrentControl() {
        fun at(y: Int, m: Int, d: Int) = Calendar.getInstance(BUSINESS_TIME_ZONE).apply { clear(); set(y, m - 1, d, 12, 0) }
        val today = at(2026, 11, 4) // Wednesday
        assertTrue(isCurrentPeriod("月", at(2026, 11, 30), today))
        assertFalse(isCurrentPeriod("月", at(2026, 10, 31), today))
        assertTrue(isCurrentPeriod("周", at(2026, 11, 2), today))   // Monday of the same week
        assertTrue(isCurrentPeriod("周", at(2026, 11, 8), today))   // Sunday of the same week
        assertFalse(isCurrentPeriod("周", at(2026, 11, 9), today))
        assertTrue(isCurrentPeriod("日", at(2026, 11, 4), today))
        assertFalse(isCurrentPeriod("日", at(2026, 11, 5), today))
    }

    @Test fun everyBackendEventTypeHasAChineseLabel() {
        listOf("created", "created_and_submitted", "updated", "resubmitted", "approval_approved", "approval_returned", "cancelled",
            "progress_updated", "completion_submitted", "submitted_and_completed", "acceptance_approved", "acceptance_returned")
            .forEach { assertNotEquals(it, eventLabel(it)) }
    }
}
