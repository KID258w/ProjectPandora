package com.projectpandora.app.feature.account

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class AppAccountContractTest {
    @Test fun parsesBackendEnvelopeAndMapsCurrentBusinessUser() {
        val json = """
            {
              "code":200,
              "message":"success",
              "data":{
                "id":42,
                "username":"zhangsan",
                "account_type":"business",
                "business_role":"employee",
                "display_name":"张三",
                "department_id":10,
                "department_name":"研发部",
                "team_id":21,
                "team_name":"客户端组",
                "manager_id":31,
                "manager_name":"李经理",
                "is_active":true
              },
              "requestId":"2e6c0997-3265-45ad-b08e-6b6b0b387aff"
            }
        """.trimIndent()
        val type = object : TypeToken<ApiEnvelope<AppUserResponse>>() {}.type

        val response: ApiEnvelope<AppUserResponse> = Gson().fromJson(json, type)
        val user = response.data.toBusinessUser()

        assertEquals(200, response.code)
        assertEquals("zhangsan", user.account)
        assertEquals("张三", user.name)
        assertEquals("员工", user.role)
        assertEquals("研发部 · 客户端组", user.organization)
        assertEquals("李经理", user.supervisor)
    }

    @Test fun mapsAllBackendBusinessRoles() {
        val roles = mapOf(
            "founder" to "创始人",
            "department_manager" to "部门经理",
            "team_leader" to "团队长",
            "employee" to "员工",
        )
        roles.forEach { (role, expected) ->
            assertEquals(expected, user(role).toBusinessUser().role)
        }
        assertEquals("公司", user("founder").toBusinessUser().organization)
    }

    @Test fun loginUsesBackendFieldsAndStoresOnlyReturnedTokens() = runBlocking {
        val api = FakeAppAccountApi()
        val tokens = MemoryTokenStorage().apply { write(StoredTokens("old-access", "old-refresh")) }
        val repository = ApiAccountRepository(api, tokens)

        val user = repository.login(" zhangsan ", "not-trimmed-password")

        assertEquals(LoginRequest("zhangsan", "not-trimmed-password"), api.loginRequest)
        assertEquals(StoredTokens("access-secret", "refresh-secret"), tokens.read())
        assertEquals("测试用户", user.name)
        assertFalse(tokens.toString().contains("not-trimmed-password"))
    }

    @Test fun rejectedCredentialsDoNotPersistTokens() = runBlocking {
        val api = FakeAppAccountApi().apply {
            loginResponse = Response.error(
                401,
                """{"code":401,"message":"请求失败","data":{"detail":"账号或密码错误"},"requestId":"r"}"""
                    .toResponseBody("application/json".toMediaType()),
            )
        }
        val tokens = MemoryTokenStorage().apply { write(StoredTokens("old-access", "old-refresh")) }

        var failure: AccountException? = null
        try {
            ApiAccountRepository(api, tokens).login("unknown", "wrong")
        } catch (expected: AccountException) {
            failure = expected
        }

        assertEquals(AccountFailureKind.INVALID_CREDENTIALS, failure?.kind)
        assertEquals(AccountMessages.INVALID_CREDENTIALS, failure?.message)
        assertNull(tokens.read())
    }

    @Test fun expiredSessionClearsTokensWhileNetworkErrorKeepsThem() = runBlocking {
        val expiredTokens = MemoryTokenStorage().apply { write(StoredTokens("old-access", "expired-refresh")) }
        val expiredApi = FakeAppAccountApi().apply {
            onCurrentUserCall = { expiredTokens.clear() }
            currentUserResponse = Response.error(
                401,
                """{"code":401,"message":"请求失败","data":{"detail":"令牌已过期"},"requestId":"r"}"""
                    .toResponseBody("application/json".toMediaType()),
            )
        }
        var expired: AccountException? = null
        try {
            ApiAccountRepository(expiredApi, expiredTokens).restore()
        } catch (expected: AccountException) {
            expired = expected
        }
        assertEquals(AccountFailureKind.SESSION_EXPIRED, expired?.kind)
        assertNull(expiredTokens.read())

        val offlineApi = FakeAppAccountApi().apply { throwOnCurrentUser = IOException("offline") }
        val offlineTokens = MemoryTokenStorage().apply { write(StoredTokens("access", "refresh")) }
        var offline: AccountException? = null
        try {
            ApiAccountRepository(offlineApi, offlineTokens).restore()
        } catch (expected: AccountException) {
            offline = expected
        }
        assertEquals(AccountFailureKind.NETWORK, offline?.kind)
        assertEquals("refresh", offlineTokens.read()?.refresh)
    }

    private fun user(role: String) = AppUserResponse(
        id = 1,
        username = "tester",
        accountType = "business",
        businessRole = role,
        displayName = "测试用户",
        departmentId = null,
        departmentName = null,
        teamId = null,
        teamName = null,
        managerId = null,
        managerName = null,
        isActive = true,
    )

    private inner class FakeAppAccountApi : AppAccountApi {
        var loginRequest: LoginRequest? = null
        var loginResponse: Response<ApiEnvelope<TokenResponse>> = Response.success(envelope(TokenResponse("access-secret", "refresh-secret")))
        var currentUserResponse: Response<ApiEnvelope<AppUserResponse>> = Response.success(envelope(user("employee")))
        var throwOnCurrentUser: IOException? = null
        var onCurrentUserCall: (() -> Unit)? = null

        override suspend fun login(request: LoginRequest): Response<ApiEnvelope<TokenResponse>> {
            loginRequest = request
            return loginResponse
        }

        override suspend fun currentUser(): Response<ApiEnvelope<AppUserResponse>> {
            throwOnCurrentUser?.let { throw it }
            onCurrentUserCall?.invoke()
            return currentUserResponse
        }
    }

    private class MemoryTokenStorage : TokenStorage {
        private var tokens: StoredTokens? = null

        override fun read(): StoredTokens? = tokens
        override fun write(tokens: StoredTokens) { this.tokens = tokens }
        override fun updateAccess(access: String, refresh: String?) {
            val existing = tokens ?: return
            tokens = StoredTokens(access, refresh ?: existing.refresh)
        }
        override fun clear() { tokens = null }
        override fun toString(): String = "MemoryTokenStorage(tokens=REDACTED)"
    }

    private companion object {
        fun <T> envelope(data: T) = ApiEnvelope(
            code = 200,
            message = "success",
            data = data,
            requestId = "2e6c0997-3265-45ad-b08e-6b6b0b387aff",
        )
    }
}
