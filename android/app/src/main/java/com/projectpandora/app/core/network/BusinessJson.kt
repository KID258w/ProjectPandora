package com.projectpandora.app.core.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException

fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
fun JsonObject.array(key: String): List<JsonObject> =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { v -> v.isJsonObject }?.asJsonObject } ?: emptyList()
fun JsonObject.string(key: String, default: String = ""): String =
    get(key)?.takeUnless { it.isJsonNull }?.runCatching { asString }?.getOrNull() ?: default
fun JsonObject.long(key: String, default: Long = 0): Long =
    get(key)?.takeUnless { it.isJsonNull }?.runCatching { asLong }?.getOrNull() ?: default
fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    get(key)?.takeUnless { it.isJsonNull }?.runCatching { asBoolean }?.getOrNull() ?: default
fun JsonObject.data(): JsonObject = obj("data") ?: throw IllegalStateException("服务器返回的数据格式不正确")
fun json(block: JsonObject.() -> Unit): JsonObject = JsonObject().apply(block)
fun ids(values: List<Long>): JsonArray = JsonArray().apply { values.forEach { add(it) } }

suspend fun BusinessApi.all(path: String, query: Map<String, String> = emptyMap()): List<JsonObject> {
    val result = mutableListOf<JsonObject>()
    var page = 1
    do {
        val data = get(path, query + mapOf("page" to page.toString(), "page_size" to "100")).data()
        result += data.array("results")
        val next = data.string("next")
        if (next.isBlank()) break
        page++
    } while (page <= 100)
    return result
}

fun Throwable.businessMessage(): String {
    if (this is CancellationException) throw this
    return when (this) {
        is retrofit2.HttpException -> {
            val fallback = "请求失败（${code()}），请检查权限或稍后重试。"
            val parsed = runCatching {
                val root = JsonParser.parseString(response()?.errorBody()?.string().orEmpty())
                fun messages(value: JsonElement?): List<String> {
                    if (value == null || value.isJsonNull) return emptyList()
                    if (value.isJsonArray) return value.asJsonArray.flatMap(::messages)
                    if (value.isJsonObject) return value.asJsonObject.entrySet().flatMap { (key, child) ->
                        messages(child).map { if (key in setOf("detail", "non_field_errors")) it else "$key: $it" }
                    }
                    return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) listOf(value.asString) else emptyList()
                }
                if (root.isJsonObject) {
                    val obj = root.asJsonObject
                    val details = if (obj.has("data")) messages(obj.get("data")) else messages(obj)
                    if (details.isNotEmpty()) details.joinToString("；")
                    else obj.string("message").takeUnless { it.isBlank() || it == "请求失败" } ?: fallback
                } else messages(root).joinToString("；").ifBlank { fallback }
            }.getOrDefault(fallback)
            parsed
        }
        is java.io.IOException -> "无法连接服务器，请检查网络后重试。"
        else -> message ?: "操作未完成，请稍后重试。"
    }
}
