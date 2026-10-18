package com.projectpandora.app.feature.account

data class BusinessUser(
    val account: String,
    val name: String,
    val role: String,
    val department: String? = null,
    val team: String? = null,
    val supervisor: String? = null,
) {
    val organization: String get() = listOfNotNull(department, team).joinToString(" · ").ifEmpty { "公司" }
}

sealed interface LoginResult {
    data class Success(val user: BusinessUser) : LoginResult
    data class Failure(val message: String) : LoginResult
}

/** UI-stage fixtures only; not backend authentication or authorization. */
object DemoAccounts {
    val users = listOf(
        BusinessUser("chenning", "陈宁", "员工", "产品部", "产品一组", "周晓"),
        BusinessUser("zhouxiao", "周晓", "团队长", "产品部", "产品一组", "林经理"),
        BusinessUser("linjingli", "林经理", "部门经理", "产品部", supervisor = "周总"),
        BusinessUser("zhouzong", "周总", "创始人"),
    )
    const val PASSWORD = "Pandora123!"

    fun authenticate(account: String, password: String): LoginResult {
        if (account.isBlank()) return LoginResult.Failure("请输入账号")
        if (password.isEmpty()) return LoginResult.Failure("请输入密码")
        if (account.trim() == "disabled" && password == PASSWORD) {
            return LoginResult.Failure("该账号已停用，请联系管理员。")
        }
        val user = users.find { it.account == account.trim() }
        return if (user != null && password == PASSWORD) LoginResult.Success(user)
        else LoginResult.Failure("账号或密码错误，请检查后重试。")
    }
}
