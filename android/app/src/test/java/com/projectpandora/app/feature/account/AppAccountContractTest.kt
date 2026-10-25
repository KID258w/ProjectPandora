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

    @Test fun rejectedCredentialsKeepThePreviousSessionWithoutStoringNewTokens() = runBlocking {
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
        assertEquals(StoredTokens("old-access", "old-refresh"), tokens.read())
    }

    @Test fun networkFailureDuringLoginKeepsThePreviousSession() = runBlocking {
        val api = FakeAppAccountApi().apply { throwOnLogin = IOException("offline") }
        val tokens = MemoryTokenStorage().apply { write(StoredTokens("old-access", "old-refresh")) }
        var failure: AccountException? = null

        try {
            ApiAccountRepository(api, tokens).login("zhangsan", "password")
        } catch (expected: AccountException) {
            failure = expected
        }

        assertEquals(AccountFailureKind.NETWORK, failure?.kind)
        assertEquals(StoredTokens("old-access", "old-refresh"), tokens.read())
    }

    @Test fun transientCurrentUserFailureAfterLoginRestoresThePreviousSession() = runBlocking {
        val api = FakeAppAccountApi().apply { throwOnCurrentUser = IOException("offline") }
        val tokens = MemoryTokenStorage().apply { write(StoredTokens("old-access", "old-refresh")) }
        var failure: AccountException? = null

        try {
            ApiAccountRepository(api, tokens).login("zhangsan", "password")
        } catch (expected: AccountException) {
            failure = expected
        }

        assertEquals(AccountFailureKind.NETWORK, failure?.kind)
        assertEquals(StoredTokens("old-access", "old-refresh"), tokens.read())
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

    @Test fun profilePatchReturnsFreshMaskedProfileAndOmitsUnchangedFields() = runBlocking {
        val api = FakeAppAccountApi().apply {
            updatedUserResponse = Response.success(
                envelope(user("employee").copy(username = "13800138421", displayName = "新姓名", idNumberMasked = "************1234")),
            )
        }
        val tokens = MemoryTokenStorage().apply { write(StoredTokens("access", "refresh")) }
        val result = ApiAccountRepository(api, tokens).updateProfile(
            ProfileUpdateRequest(displayName = "新姓名", username = "13800138421"),
        )

        assertEquals(ProfileUpdateRequest(displayName = "新姓名", username = "13800138421"), api.updateRequest)
        assertEquals("13800138421", result.account)
        assertEquals("************1234", result.idNumberMasked)
        val body = Gson().toJson(api.updateRequest)
        assertTrue(body.contains("\"display_name\":\"新姓名\""))
        assertTrue(body.contains("\"username\":\"13800138421\""))
        assertFalse(body.contains("id_number"))
        assertEquals(StoredTokens("access", "refresh"), tokens.read())
    }

    @Test fun identityNumberCanBeUpdatedWithoutSubmittingNameOrPhone() = runBlocking {
        val api = FakeAppAccountApi()
        val tokens = MemoryTokenStorage().apply { write(StoredTokens("access", "refresh")) }
        val request = ProfileUpdateRequest(idNumber = "110101199001011234")

        ApiAccountRepository(api, tokens).updateProfile(request)

        assertEquals(request, api.updateRequest)
        val body = Gson().toJson(api.updateRequest)
        assertTrue(body.contains("\"id_number\":\"110101199001011234\""))
        assertFalse(body.contains("display_name"))
        assertFalse(body.contains("username"))
    }

    @Test fun rejectedRefreshDuringProfileUpdateClearsSessionAndReportsExpiry() = runBlocking {
        val tokens = MemoryTokenStorage().apply { write(StoredTokens("expired-access", "expired-refresh")) }
        val api = FakeAppAccountApi().apply {
            onUpdateCall = { tokens.clear() }
            updatedUserResponse = Response.error(
                401,
                """{"code":401,"message":"请求失败","data":{"detail":"令牌已过期"},"requestId":"r"}"""
                    .toResponseBody("application/json".toMediaType()),
            )
        }

        var failure: AccountException? = null
        try {
            ApiAccountRepository(api, tokens).updateProfile(ProfileUpdateRequest(displayName = "新姓名"))
        } catch (expected: AccountException) {
            failure = expected
        }

        assertEquals(AccountFailureKind.SESSION_EXPIRED, failure?.kind)
        assertEquals(AccountMessages.SESSION_EXPIRED, failure?.message)
        assertNull(tokens.read())
    }

    @Test fun passwordChangeUsesBackendContractAndSurfacesValidationDetail() = runBlocking {
        val api = FakeAppAccountApi()
        val tokens = MemoryTokenStorage().apply { write(StoredTokens("access", "refresh")) }
        val request = PasswordChangeRequest("old-password", "new-password", "new-password")
        ApiAccountRepository(api, tokens).changePassword(request)

        assertEquals(request, api.passwordRequest)
        val body = Gson().toJson(api.passwordRequest)
        assertTrue(body.contains("\"current_password\":\"old-password\""))
        assertTrue(body.contains("\"new_password_confirm\":\"new-password\""))
        assertEquals(StoredTokens("access", "refresh"), tokens.read())

        api.passwordResponse = Response.error(
            400,
            """{"code":400,"message":"请求失败","data":{"current_password":["当前密码不正确。"]},"requestId":"r"}"""
                .toResponseBody("application/json".toMediaType()),
        )
        var failure: AccountException? = null
        try {
            ApiAccountRepository(api, tokens).changePassword(request)
        } catch (expected: AccountException) {
            failure = expected
        }
        assertEquals(AccountFailureKind.VALIDATION, failure?.kind)
        assertEquals("当前密码不正确。", failure?.message)
        assertEquals(StoredTokens("access", "refresh"), tokens.read())
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
        var updateRequest: ProfileUpdateRequest? = null
        var updatedUserResponse: Response<ApiEnvelope<AppUserResponse>> = Response.success(envelope(user("employee")))
        var passwordRequest: PasswordChangeRequest? = null
        var passwordResponse: Response<ApiEnvelope<PasswordChangeResponse>> = Response.success(envelope(PasswordChangeResponse(true)))
        var onUpdateCall: (() -> Unit)? = null
        var throwOnLogin: IOException? = null
        var throwOnCurrentUser: IOException? = null
        var onCurrentUserCall: (() -> Unit)? = null

        override suspend fun login(request: LoginRequest): Response<ApiEnvelope<TokenResponse>> {
            loginRequest = request
            throwOnLogin?.let { throw it }
            return loginResponse
        }

        override suspend fun currentUser(): Response<ApiEnvelope<AppUserResponse>> {
            throwOnCurrentUser?.let { throw it }
            onCurrentUserCall?.invoke()
            return currentUserResponse
        }

        override suspend fun updateCurrentUser(request: ProfileUpdateRequest): Response<ApiEnvelope<AppUserResponse>> {
            updateRequest = request
            onUpdateCall?.invoke()
            return updatedUserResponse
        }

        override suspend fun changePassword(request: PasswordChangeRequest): Response<ApiEnvelope<PasswordChangeResponse>> {
            passwordRequest = request
            return passwordResponse
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
