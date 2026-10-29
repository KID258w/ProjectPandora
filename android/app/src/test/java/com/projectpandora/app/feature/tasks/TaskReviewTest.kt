package com.projectpandora.app.feature.tasks

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.businessMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class TaskReviewTest {
    @Test fun ordinaryListUsesLifecycleAndCompletionNotApproval() {
        fun row(json: String) = JsonParser.parseString(json).asJsonObject
        assertEquals("未完成", taskStatusLabel(row("""{"approval_status":"approved","completion_status":"unfinished"}"""), false))
        assertEquals("已完成", taskStatusLabel(row("""{"approval_status":"approved","completion_status":"completed"}"""), false))
        assertEquals("已取消", taskStatusLabel(row("""{"lifecycle_status":"cancelled","completion_status":"unfinished"}"""), false))
        assertEquals("待审批", taskStatusLabel(row("""{"approval_status":"pending","completion_status":"unfinished"}"""), true))
    }
    private fun error(status: Int, body: String) = HttpException(Response.error<JsonObject>(status, body.toResponseBody("application/json".toMediaType())))
    @Test fun showsBackendDetailAndFieldValidation() {
        assertEquals("仅本轮直属上级可审批。", error(403, """{"message":"请求失败","data":{"detail":"仅本轮直属上级可审批。"}}""").businessMessage())
        assertEquals("comment: 请填写原因", error(400, """{"message":"请求失败","data":{"comment":["请填写原因"]}}""").businessMessage())
        assertEquals("业务状态不允许", error(400, """{"message":"业务状态不允许","data":null}""").businessMessage())
        assertEquals("无权访问。", error(403, """{"detail":"无权访问。"}""").businessMessage())
        assertEquals("请求失败（500），请检查权限或稍后重试。", error(500, "not json").businessMessage())
    }
}
