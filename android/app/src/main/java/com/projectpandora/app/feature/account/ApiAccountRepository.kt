package com.projectpandora.app.feature.account

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException

class ApiAccountRepository(
    private val api: AppAccountApi,
    private val tokenStorage: TokenStorage,
) : AccountRepository {
    override fun hasStoredSession(): Boolean = tokenStorage.read() != null

    override suspend fun restore(): BusinessUser? {
        if (!hasStoredSession()) return null
        return try {
            fetchCurrentUser()
        } catch (failure: AccountException) {
            throw resolveProtectedFailure(failure)
        }
    }

    override suspend fun login(account: String, password: String): BusinessUser {
        // Keep an existing session until the new credentials and current-user request both succeed.
        val previousTokens = tokenStorage.read()
        val envelope = request(isLogin = true) { api.login(LoginRequest(account.trim(), password)) }
        val tokenResponse = envelope.data
        if (tokenResponse.access.isBlank() || tokenResponse.refresh.isNullOrBlank()) {
            throw AccountException(AccountFailureKind.SERVER, AccountMessages.SERVER)
        }

        tokenStorage.write(StoredTokens(tokenResponse.access, tokenResponse.refresh))
        return try {
            fetchCurrentUser()
        } catch (failure: AccountException) {
            val resolved = resolveRefreshFailure(failure)
            restorePreviousSession(previousTokens)
            throw resolved
        } catch (failure: CancellationException) {
            restorePreviousSession(previousTokens)
            throw failure
        }
    }

    override fun logout() = tokenStorage.clear()

    override suspend fun updateProfile(request: ProfileUpdateRequest): BusinessUser {
        val hasUpdate = request.displayName != null || request.username != null || request.idNumber != null
        if (!hasUpdate) {
            throw AccountException(AccountFailureKind.VALIDATION, "请至少修改一项个人资料。")
        }
        return try {
            this.request(isLogin = false) { api.updateCurrentUser(request) }.data.toBusinessUser()
        } catch (failure: AccountException) {
            throw resolveProtectedFailure(failure)
        }
    }

    override suspend fun changePassword(request: PasswordChangeRequest) {
        try {
            val changed = this.request(isLogin = false) { api.changePassword(request) }.data.passwordChanged
            if (!changed) {
                throw AccountException(AccountFailureKind.SERVER, AccountMessages.PASSWORD_CHANGE_FAILED)
            }
        } catch (failure: AccountException) {
            throw resolveProtectedFailure(failure)
        }
    }

    private suspend fun fetchCurrentUser(): BusinessUser {
        val user = request(isLogin = false) { api.currentUser() }.data
        if (user.accountType != "business" || !user.isActive) {
            throw AccountException(AccountFailureKind.FORBIDDEN, AccountMessages.FORBIDDEN)
        }
        return user.toBusinessUser()
    }

    private fun resolveRefreshFailure(failure: AccountException): AccountException {
        if (failure.kind != AccountFailureKind.SESSION_EXPIRED || tokenStorage.read() == null) return failure
        // The authenticator clears tokens after a definitive refresh rejection. If they remain,
        // refresh could not be verified because of a transport or temporary server failure.
        return AccountException(AccountFailureKind.NETWORK, AccountMessages.SESSION_CHECK, failure)
    }

    private fun resolveProtectedFailure(failure: AccountException): AccountException {
        val resolved = resolveRefreshFailure(failure)
        if (resolved.kind == AccountFailureKind.SESSION_EXPIRED || resolved.kind == AccountFailureKind.FORBIDDEN) {
            tokenStorage.clear()
        }
        return resolved
    }

    private fun restorePreviousSession(tokens: StoredTokens?) {
        if (tokens == null) tokenStorage.clear() else tokenStorage.write(tokens)
    }

    private suspend fun <T> request(
        isLogin: Boolean,
        call: suspend () -> Response<ApiEnvelope<T>>,
    ): ApiEnvelope<T> {
        val response = try {
            call()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            throw AccountException(AccountFailureKind.NETWORK, AccountMessages.NETWORK, failure)
        } catch (failure: Exception) {
            throw AccountException(AccountFailureKind.SERVER, AccountMessages.SERVER, failure)
        }

        if (!response.isSuccessful) {
            val serverDetail = readDetail(response.errorBody()?.string())
            val status = response.code()
            val (kind, message) = when {
                isLogin && status == 401 -> AccountFailureKind.INVALID_CREDENTIALS to AccountMessages.INVALID_CREDENTIALS
                status == 401 -> AccountFailureKind.SESSION_EXPIRED to AccountMessages.SESSION_EXPIRED
                status == 403 -> AccountFailureKind.FORBIDDEN to AccountMessages.FORBIDDEN
                status in 400..499 -> AccountFailureKind.VALIDATION to AccountMessages.VALIDATION
                else -> AccountFailureKind.SERVER to AccountMessages.SERVER
            }
            val safeMessage = if (kind == AccountFailureKind.VALIDATION) {
                serverDetail?.takeIf(String::isNotBlank) ?: message
            } else message
            throw AccountException(kind, safeMessage)
        }

        val envelope = response.body()
            ?: throw AccountException(AccountFailureKind.SERVER, AccountMessages.SERVER)
        if (envelope.code !in 200..299) {
            throw AccountException(AccountFailureKind.SERVER, AccountMessages.SERVER)
        }
        return envelope
    }

    private fun readDetail(body: String?): String? = try {
        val root = body?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        data.get("detail")?.takeIf { it.isJsonPrimitive }?.asString
            ?: data.entrySet().firstNotNullOfOrNull { (_, value) ->
                when {
                    value.isJsonPrimitive -> value.asString
                    value.isJsonArray && value.asJsonArray.size() > 0 -> value.asJsonArray[0].asString
                    else -> null
                }
            }
    } catch (_: RuntimeException) {
        null
    }
}
