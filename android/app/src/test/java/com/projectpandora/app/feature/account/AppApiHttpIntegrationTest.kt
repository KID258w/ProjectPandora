package com.projectpandora.app.feature.account

import com.projectpandora.app.core.network.ApiClient
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class AppApiHttpIntegrationTest {
    @Test fun loginAndCurrentUserUseTheDocumentedAppEndpoints() {
        val server = LocalApiServer { request ->
            when (request.path) {
                "/api/v1/app/auth/token/" -> HttpResult(200, envelope("""{"access":"access-login","refresh":"refresh-login"}"""))
                "/api/v1/app/me/" -> if (request.authorization == "Bearer access-login") {
                    HttpResult(200, envelope(profile))
                } else unauthorized()
                else -> HttpResult(404, envelope("{}"))
            }
        }
        try {
            val tokens = MemoryTokenStorage()
            val repository = ApiAccountRepository(ApiClient.createAppAccountApi(tokens, server.baseUrl), tokens)

            val user = runBlocking { repository.login("employee01", "Pandora-test-938!") }

            assertEquals("张三", user.name)
            assertEquals("研发部 · 客户端组", user.organization)
            assertEquals(StoredTokens("access-login", "refresh-login"), tokens.read())
            assertEquals(
                listOf("/api/v1/app/auth/token/", "/api/v1/app/me/"),
                server.requests.map { it.path },
            )
            assertTrue(server.requests[0].body.contains("\"username\":\"employee01\""))
            assertTrue(server.requests[0].body.contains("\"password\":\"Pandora-test-938!\""))
            assertEquals("Bearer access-login", server.requests[1].authorization)
        } finally {
            server.close()
        }
    }

    @Test fun rejectedLoginDoesNotClearAnExistingSession() {
        val server = LocalApiServer { request ->
            if (request.path == "/api/v1/app/auth/token/") unauthorized("账号或密码错误")
            else HttpResult(404, envelope("{}"))
        }
        try {
            val tokens = MemoryTokenStorage().apply { write(StoredTokens("saved-access", "saved-refresh")) }
            val repository = ApiAccountRepository(ApiClient.createAppAccountApi(tokens, server.baseUrl), tokens)
            var failure: AccountException? = null

            try {
                runBlocking { repository.login("employee01", "wrong-password") }
            } catch (expected: AccountException) {
                failure = expected
            }

            assertEquals(AccountFailureKind.INVALID_CREDENTIALS, failure?.kind)
            assertEquals(StoredTokens("saved-access", "saved-refresh"), tokens.read())
            assertEquals(1, server.requests.size)
            assertEquals("/api/v1/app/auth/token/", server.requests.single().path)
            assertNull(server.requests.single().authorization)
        } finally {
            server.close()
        }
    }

    @Test fun expiredAccessRefreshesAndRetriesCurrentUserOnce() {
        val server = LocalApiServer { request ->
            when (request.path) {
                "/api/v1/app/me/" -> when (request.authorization) {
                    "Bearer old-access" -> unauthorized()
                    "Bearer new-access" -> HttpResult(200, envelope(profile))
                    else -> unauthorized()
                }
                "/api/v1/app/auth/token/refresh/" -> HttpResult(
                    200,
                    envelope("""{"access":"new-access"}"""),
                )
                else -> HttpResult(404, envelope("{}"))
            }
        }
        try {
            val tokens = MemoryTokenStorage().apply { write(StoredTokens("old-access", "old-refresh")) }
            val repository = ApiAccountRepository(ApiClient.createAppAccountApi(tokens, server.baseUrl), tokens)

            val user = runBlocking { repository.restore() }

            assertEquals("张三", user?.name)
            assertEquals(StoredTokens("new-access", "old-refresh"), tokens.read())
            assertEquals(
                listOf(
                    "/api/v1/app/me/",
                    "/api/v1/app/auth/token/refresh/",
                    "/api/v1/app/me/",
                ),
                server.requests.map { it.path },
            )
            assertEquals("Bearer old-access", server.requests[0].authorization)
            assertNull("Refresh requests must not send the expired access token", server.requests[1].authorization)
            assertTrue(server.requests[1].body.contains("\"refresh\":\"old-refresh\""))
            assertEquals("Bearer new-access", server.requests[2].authorization)
        } finally {
            server.close()
        }
    }

    @Test fun rejectedRefreshClearsTheLocalSession() {
        val server = LocalApiServer { request ->
            when (request.path) {
                "/api/v1/app/me/" -> unauthorized()
                "/api/v1/app/auth/token/refresh/" -> unauthorized("刷新令牌已过期")
                else -> HttpResult(404, envelope("{}"))
            }
        }
        try {
            val tokens = MemoryTokenStorage().apply { write(StoredTokens("old-access", "expired-refresh")) }
            val repository = ApiAccountRepository(ApiClient.createAppAccountApi(tokens, server.baseUrl), tokens)
            var failure: AccountException? = null

            try {
                runBlocking { repository.restore() }
            } catch (expected: AccountException) {
                failure = expected
            }

            assertEquals(AccountFailureKind.SESSION_EXPIRED, failure?.kind)
            assertNull(tokens.read())
            assertEquals(2, server.requests.size)
        } finally {
            server.close()
        }
    }

    @Test fun temporaryRefreshFailureKeepsTheSessionAndIsNotReportedAsExpired() {
        val server = LocalApiServer { request ->
            when (request.path) {
                "/api/v1/app/me/" -> unauthorized()
                "/api/v1/app/auth/token/refresh/" -> HttpResult(
                    503,
                    """{"code":503,"message":"服务暂不可用","data":{},"requestId":"r"}""",
                )
                else -> HttpResult(404, envelope("{}"))
            }
        }
        try {
            val tokens = MemoryTokenStorage().apply { write(StoredTokens("old-access", "refresh")) }
            val repository = ApiAccountRepository(ApiClient.createAppAccountApi(tokens, server.baseUrl), tokens)
            var failure: AccountException? = null

            try {
                runBlocking { repository.restore() }
            } catch (expected: AccountException) {
                failure = expected
            }

            assertEquals(AccountFailureKind.NETWORK, failure?.kind)
            assertEquals(AccountMessages.SESSION_CHECK, failure?.message)
            assertEquals("refresh", tokens.read()?.refresh)
            assertEquals(2, server.requests.size)
        } finally {
            server.close()
        }
    }

    @Test fun profileUpdateUsesPatchAndNewPhoneIsUsedOnNextLogin() {
        var currentProfile = profile
        val server = LocalApiServer { request ->
            when {
                request.path == "/api/v1/app/auth/token/" -> HttpResult(
                    200,
                    envelope("""{"access":"access-${request.body.hashCode()}","refresh":"refresh"}"""),
                )
                request.path == "/api/v1/app/me/" && request.method == "GET" -> HttpResult(200, envelope(currentProfile))
                request.path == "/api/v1/app/me/" && request.method == "PATCH" -> {
                    if (request.body.contains("\"display_name\":\"新姓名\"")) {
                        currentProfile = currentProfile.replace("张三", "新姓名")
                    }
                    if (request.body.contains("\"username\":\"13800138421\"")) {
                        currentProfile = currentProfile.replace("employee01", "13800138421")
                    }
                    if (request.body.contains("\"id_number\":")) {
                        currentProfile = currentProfile.replace(
                            "\"is_active\":true",
                            "\"is_active\":true,\"id_number_masked\":\"**************1234\"",
                        )
                    }
                    HttpResult(200, envelope(currentProfile))
                }
                else -> HttpResult(404, envelope("{}"))
            }
        }
        try {
            val tokens = MemoryTokenStorage()
            val repository = ApiAccountRepository(ApiClient.createAppAccountApi(tokens, server.baseUrl), tokens)
            val original = runBlocking { repository.login("employee01", "Pandora-test-938!") }
            val updatedNameAndId = runBlocking {
                repository.updateProfile(ProfileUpdateRequest(displayName = "新姓名", idNumber = "110101199001011234"))
            }

            assertEquals("张三", original.name)
            assertEquals("新姓名", updatedNameAndId.name)
            assertEquals("**************1234", updatedNameAndId.idNumberMasked)
            val patches = server.requests.filter { it.method == "PATCH" }
            assertEquals(1, patches.size)
            assertEquals("Bearer ${tokens.read()?.access}", patches[0].authorization)
            assertTrue(patches[0].body.contains("\"display_name\":\"新姓名\""))
            assertTrue(patches[0].body.contains("\"id_number\":\"110101199001011234\""))
            assertFalse(patches[0].body.contains("username"))

            val updatedPhone = runBlocking {
                repository.updateProfile(ProfileUpdateRequest(username = "13800138421"))
            }
            assertEquals("13800138421", updatedPhone.account)
            assertTrue(patches[0].body.contains("display_name") && patches[0].body.contains("id_number"))
            val phonePatch = server.requests.filter { it.method == "PATCH" }[1]
            assertTrue(phonePatch.body.contains("\"username\":\"13800138421\""))
            assertFalse(phonePatch.body.contains("display_name"))
            assertFalse(phonePatch.body.contains("id_number"))

            repository.logout()
            runBlocking { repository.login("13800138421", "Pandora-test-938!") }
            val loginBodies = server.requests.filter { it.path == "/api/v1/app/auth/token/" }.map { it.body }
            assertTrue(loginBodies.any { it.contains("\"username\":\"employee01\"") })
            assertTrue(loginBodies.any { it.contains("\"username\":\"13800138421\"") })
        } finally {
            server.close()
        }
    }

    @Test fun passwordUpdateUsesTheSelfServiceEndpointAndDisplaysBackendValidation() {
        val server = LocalApiServer { request ->
            when {
                request.path == "/api/v1/app/me/password/" && request.method == "POST" -> HttpResult(
                    if (request.body.contains("\"current_password\":\"correct-current\"")) 200 else 400,
                    if (request.body.contains("\"current_password\":\"correct-current\"")) {
                        envelope("""{"password_changed":true}""")
                    } else {
                        """{"code":400,"message":"请求失败","data":{"current_password":["当前密码不正确。"]},"requestId":"r"}"""
                    },
                )
                else -> HttpResult(404, envelope("{}"))
            }
        }
        try {
            val tokens = MemoryTokenStorage().apply { write(StoredTokens("access", "refresh")) }
            val repository = ApiAccountRepository(ApiClient.createAppAccountApi(tokens, server.baseUrl), tokens)
            runBlocking {
                repository.changePassword(PasswordChangeRequest("correct-current", "new-secret", "new-secret"))
            }
            assertEquals(StoredTokens("access", "refresh"), tokens.read())

            var failure: AccountException? = null
            try {
                runBlocking {
                    repository.changePassword(PasswordChangeRequest("wrong-current", "new-secret", "new-secret"))
                }
            } catch (expected: AccountException) {
                failure = expected
            }

            assertTrue(server.requests.all { it.method == "POST" })
            assertTrue(server.requests.all { it.path == "/api/v1/app/me/password/" })
            assertTrue(server.requests.all { it.authorization == "Bearer access" })
            assertTrue(server.requests.any { it.body.contains("\"current_password\":\"wrong-current\"") })
            assertTrue(server.requests.any { it.body.contains("\"new_password_confirm\":\"new-secret\"") })
            assertEquals(AccountFailureKind.VALIDATION, failure?.kind)
            assertEquals("当前密码不正确。", failure?.message)
            assertEquals(StoredTokens("access", "refresh"), tokens.read())
        } finally {
            server.close()
        }
    }

    private data class RecordedRequest(val method: String, val path: String, val authorization: String?, val body: String)
    private data class HttpResult(val status: Int, val body: String)

    private class LocalApiServer(
        private val handler: (RecordedRequest) -> HttpResult,
    ) : AutoCloseable {
        private val executor = Executors.newCachedThreadPool()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@LocalApiServer.executor
            createContext("/") { exchange -> respond(exchange) }
            start()
        }
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val baseUrl = "http://127.0.0.1:${server.address.port}/"

        private fun respond(exchange: HttpExchange) {
            val body = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val request = RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = body,
            )
            requests += request
            val result = handler(request)
            val bytes = result.body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(result.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private class MemoryTokenStorage : TokenStorage {
        private var tokens: StoredTokens? = null
        override fun read(): StoredTokens? = tokens
        override fun write(tokens: StoredTokens) { this.tokens = tokens }
        override fun updateAccess(access: String, refresh: String?) {
            val current = tokens ?: return
            tokens = StoredTokens(access, refresh ?: current.refresh)
        }
        override fun clear() { tokens = null }
    }

    private companion object {
        const val profile = """{"id":42,"username":"employee01","account_type":"business","business_role":"employee","display_name":"张三","department_id":10,"department_name":"研发部","team_id":21,"team_name":"客户端组","manager_id":31,"manager_name":"李经理","is_active":true}"""

        fun envelope(data: String) = """{"code":200,"message":"success","data":$data,"requestId":"2e6c0997-3265-45ad-b08e-6b6b0b387aff"}"""
        fun unauthorized(detail: String = "令牌已过期") =
            HttpResult(401, """{"code":401,"message":"请求失败","data":{"detail":"$detail"},"requestId":"r"}""")
    }
}
