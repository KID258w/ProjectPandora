package com.projectpandora.app.feature.account

data class BusinessUser(
    val account: String,
    val name: String,
    val role: String,
    val department: String? = null,
    val team: String? = null,
    val supervisor: String? = null,
) {
    val organization: String
        get() = listOfNotNull(department, team).joinToString(" · ").ifEmpty { "公司" }
}

data class StoredTokens(val access: String, val refresh: String) {
    override fun toString(): String = "StoredTokens(access=REDACTED, refresh=REDACTED)"
}

enum class AccountFailureKind {
    INVALID_CREDENTIALS,
    SESSION_EXPIRED,
    FORBIDDEN,
    NETWORK,
    SERVER,
    VALIDATION,
}

class AccountException(
    val kind: AccountFailureKind,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

interface AccountRepository {
    fun hasStoredSession(): Boolean
    suspend fun restore(): BusinessUser?
    suspend fun login(account: String, password: String): BusinessUser
    fun logout()
}

enum class AccountStartupDestination { WELCOME, LOGIN, HOME }

object AccountMessages {
    const val INVALID_CREDENTIALS = "账号或密码错误；如果账号已停用，请联系管理员。"
    const val SESSION_EXPIRED = "登录状态已过期，请重新登录。"
    const val FORBIDDEN = "该账号无权使用 App，请联系管理员。"
    const val NETWORK = "暂时无法连接服务器，请检查网络后重试。"
    const val SESSION_CHECK = "暂时无法验证登录状态，请检查网络或稍后重试。"
    const val SERVER = "服务暂时不可用，请稍后重试。"
    const val VALIDATION = "登录信息格式不正确，请检查后重试。"
}
