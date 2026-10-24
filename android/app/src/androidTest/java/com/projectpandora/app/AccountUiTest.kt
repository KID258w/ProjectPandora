package com.projectpandora.app

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.projectpandora.app.feature.account.*
import com.projectpandora.app.navigation.PandoraApp
import com.projectpandora.app.ui.theme.PandoraAppTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AccountUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("pandora_preferences", Context.MODE_PRIVATE)
    private lateinit var repository: FakeAccountRepository

    @Before fun clearSession() {
        preferences.edit().clear().commit()
        context.getSharedPreferences("pandora_secure_session", Context.MODE_PRIVATE).edit().clear().commit()
        repository = FakeAccountRepository()
    }

    private fun start() {
        compose.setContent { PandoraAppTheme { PandoraApp(startupDelayMillis = 0, accountRepository = repository) } }
    }

    private fun login(account: String = TestUsers.EMPLOYEE.account) {
        compose.onNodeWithText("进入潘多拉").performScrollTo().performClick()
        compose.onNodeWithTag("account").performTextInput(account)
        compose.onNodeWithTag("password").performTextInput(TestUsers.PASSWORD)
        compose.onNodeWithText("登录", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("你好，${TestUsers.byAccount(account).name}").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun emptyValidationAndPasswordVisibility() {
        start()
        screenshot("01-welcome")
        compose.onNodeWithText("进入潘多拉").performScrollTo().performClick()
        screenshot("02-login")
        compose.onNodeWithText("登录").performScrollTo().performClick()
        compose.onNodeWithText("请输入手机号").assertIsDisplayed()
        compose.onNodeWithTag("account").performTextInput("chenning")
        compose.onNodeWithText("登录").performScrollTo().performClick()
        compose.onNodeWithText("请输入密码").assertIsDisplayed()
        compose.onNodeWithTag("password").performTextInput(TestUsers.PASSWORD)
        compose.onNodeWithContentDescription("显示密码").performClick()
        compose.onNodeWithContentDescription("隐藏密码").assertExists()
        compose.onNodeWithTag("password").assertTextContains(TestUsers.PASSWORD)
        compose.onNodeWithContentDescription("隐藏密码").performClick()
        compose.onNodeWithContentDescription("显示密码").assertExists()
    }

    @Test fun rejectedAndDisabledAccountsShowBackendSafeMessage() {
        start()
        compose.onNodeWithText("进入潘多拉").performScrollTo().performClick()
        compose.onNodeWithTag("account").performTextInput("chenning")
        compose.onNodeWithTag("password").performTextInput("wrong")
        compose.onNodeWithText("登录").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(AccountMessages.INVALID_CREDENTIALS).fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("03-login-error")
        compose.onNodeWithTag("account").performTextReplacement("disabled")
        compose.onNodeWithTag("password").performTextReplacement(TestUsers.PASSWORD)
        compose.onNodeWithText("登录").performScrollTo().performClick()
        compose.onNodeWithText(AccountMessages.INVALID_CREDENTIALS).assertIsDisplayed()
    }

    @Test fun temporaryRestoreFailureOffersRetryAndKeepsSession() {
        repository.seed(TestUsers.EMPLOYEE)
        repository.restoreFailuresRemaining = 2
        start()

        compose.onNodeWithText(AccountMessages.SESSION_CHECK).assertIsDisplayed()
        compose.onNodeWithTag("retry_session_validation").assertIsDisplayed().performClick()
        compose.onNodeWithText(AccountMessages.SESSION_CHECK).assertIsDisplayed()
        compose.onNodeWithTag("retry_session_validation").assertIsDisplayed().performClick()
        compose.onNodeWithText("你好，陈宁").assertIsDisplayed()
        assertTrue(repository.hasStoredSession())
    }

    @Test fun completeProfileSettingsAndLogoutFlow() {
        start(); login(); screenshot("04-home")
        compose.onNodeWithText("我的").performClick()
        screenshot("05-profile")
        compose.onNodeWithText("个人资料").performScrollTo().performClick()
        compose.onNodeWithText("直属上级").assertExists()
        compose.onNodeWithText("周晓").assertExists()
        screenshot("06-personal-info")
        compose.onNodeWithText("‹ 返回").performClick()
        compose.onNodeWithText("通知设置").performScrollTo().performClick()
        compose.onNodeWithTag("notification_task").assertIsOn().performClick().assertIsOff()
        screenshot("07-notifications")
        compose.onNodeWithText("‹ 返回").performClick()
        compose.onNodeWithText("通知设置").performScrollTo().performClick()
        compose.onNodeWithTag("notification_task").assertIsOff()
        compose.onNodeWithText("‹ 返回").performClick()
        compose.onNodeWithText("帮助与反馈").performScrollTo().performClick()
        compose.onNodeWithText("联系本单位管理员").assertIsDisplayed()
        screenshot("08-help")
        compose.onNodeWithText("‹ 返回").performClick()
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        screenshot("09-logout")
        compose.onNodeWithText("继续使用").performClick()
        compose.onNodeWithText("个人设置").assertExists()
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_logout").performClick()
        compose.onNodeWithText("欢迎回来").assertExists()
        assertFalse(repository.hasStoredSession())
        compose.onNodeWithTag("password").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }

    @Test fun savedSessionStartsAtHomeAndAllTabsNavigate() {
        repository.seed(TestUsers.EMPLOYEE)
        start()
        compose.onNodeWithText("你好，陈宁").assertExists()
        listOf("视图", "日志", "AI地图", "我的", "首页").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("你好，陈宁").assertIsDisplayed()
        compose.onNodeWithText("视图").performClick()
        compose.onNodeWithText("打开任务中心").performClick()
        compose.onNodeWithText("‹ 返回").performClick()
        compose.onNodeWithText("工作视图").assertIsDisplayed()
    }

    @Test fun founderProfileHasCompanyOrganization() {
        repository.seed(TestUsers.FOUNDER)
        start()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("创始人").assertExists()
        compose.onNodeWithText("个人资料").performScrollTo().performClick()
        compose.onNodeWithText("业务身份").assertExists()
        compose.onNodeWithText("公司").assertExists()
    }

    @Test fun profileCanUpdateNameAndIdentityTogetherAndShowMaskedValue() {
        repository.seed(TestUsers.EMPLOYEE.copy(idNumberMasked = "**************5678"))
        start()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("个人资料").performScrollTo().performClick()
        compose.onNodeWithText("**************5678").assertIsDisplayed()
        compose.onNodeWithText("编辑个人资料").performScrollTo().performClick()
        compose.onNodeWithTag("profile_name").performTextReplacement("陈宁新")
        compose.onNodeWithTag("profile_id_number").performTextInput("110101199001011234")
        compose.onNodeWithTag("save_personal_info").performScrollTo().performClick()

        compose.onAllNodesWithText("资料已保存").onFirst().assertIsDisplayed()
        assertEquals("陈宁新", repository.lastProfileUpdate?.displayName)
        assertEquals("110101199001011234", repository.lastProfileUpdate?.idNumber)
        assertNull("Unchanged phone must be omitted", repository.lastProfileUpdate?.username)
        assertEquals("**************1234", repository.latestUser?.idNumberMasked)
        compose.onNodeWithText("返回个人资料").performClick()
        compose.onAllNodesWithText("陈宁新").onFirst().assertIsDisplayed()
        compose.onNodeWithText("**************1234").assertIsDisplayed()

        val persistedValues = context.getSharedPreferences("pandora_secure_session", Context.MODE_PRIVATE).all.values
            .filterIsInstance<String>()
        assertTrue(persistedValues.none { it.contains("110101199001011234") })
        val ordinaryPreferences = context.getSharedPreferences("pandora_preferences", Context.MODE_PRIVATE).all.values
            .filterIsInstance<String>()
        assertTrue(ordinaryPreferences.none { it.contains("110101199001011234") })
    }

    @Test fun passwordFormShowsBackendErrorAndDoesNotPersistPlaintext() {
        repository.seed(TestUsers.EMPLOYEE)
        repository.passwordFailure = AccountException(AccountFailureKind.VALIDATION, "当前密码不正确。")
        start()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("个人资料").performScrollTo().performClick()
        compose.onNodeWithText("修改密码").performScrollTo().performClick()
        compose.onNodeWithTag("current_password").performTextInput("wrong-current")
        compose.onNodeWithTag("new_password").performTextInput("new-secret-938!")
        compose.onNodeWithTag("new_password_confirm").performTextInput("new-secret-938!")
        compose.onNodeWithTag("submit_password_change").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("当前密码不正确。").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("wrong-current", repository.lastPasswordChange?.currentPassword)
        assertEquals("new-secret-938!", repository.lastPasswordChange?.newPassword)

        val persistedValues = context.getSharedPreferences("pandora_secure_session", Context.MODE_PRIVATE).all.values
            .filterIsInstance<String>()
        assertTrue(persistedValues.none { it.contains("wrong-current") || it.contains("new-secret-938!") })
        val ordinaryPreferences = context.getSharedPreferences("pandora_preferences", Context.MODE_PRIVATE).all.values
            .filterIsInstance<String>()
        assertTrue(ordinaryPreferences.none { it.contains("wrong-current") || it.contains("new-secret-938!") })
    }

    @Test fun expiredSessionDuringProfileUpdateReturnsToLoginAndClearsLocalState() {
        repository.seed(TestUsers.EMPLOYEE)
        repository.profileFailure = AccountException(AccountFailureKind.SESSION_EXPIRED, AccountMessages.SESSION_EXPIRED)
        start()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("个人资料").performScrollTo().performClick()
        compose.onNodeWithText("编辑个人资料").performScrollTo().performClick()
        compose.onNodeWithTag("profile_name").performTextReplacement("陈宁新")
        compose.onNodeWithTag("save_personal_info").performScrollTo().performClick()

        compose.waitUntil(10000) {
            compose.onAllNodesWithText("欢迎回来").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(AccountMessages.SESSION_EXPIRED).assertIsDisplayed()
        assertFalse(repository.hasStoredSession())
    }

    @Test fun tokenStorageEncryptsAndClearsCredentialsWithoutSavingPasswords() {
        val tokens = StoredTokens("access-secret-value", "refresh-secret-value")
        val store = AndroidTokenStorage(context)
        store.write(tokens)

        assertEquals(tokens, store.read())
        val persistedValues = context.getSharedPreferences("pandora_secure_session", Context.MODE_PRIVATE).all.values
            .filterIsInstance<String>()
        assertTrue(persistedValues.isNotEmpty())
        assertTrue(persistedValues.none { it.contains(tokens.access) || it.contains(tokens.refresh) || it.contains(TestUsers.PASSWORD) })

        store.clear()
        assertNull(store.read())
    }
}

private object TestUsers {
    const val PASSWORD = "Pandora-test-938!"
    val EMPLOYEE = BusinessUser("chenning", "陈宁", "员工", "研发部", "Android组", "周晓")
    val FOUNDER = BusinessUser("founder", "周总", "创始人")
    private val users = listOf(EMPLOYEE, FOUNDER)

    fun byAccount(account: String): BusinessUser = users.first { it.account == account }
}

private class FakeAccountRepository(initialUser: BusinessUser? = null) : AccountRepository {
    private var user: BusinessUser? = initialUser
    val latestUser get() = user
    var lastProfileUpdate: ProfileUpdateRequest? = null
        private set
    var lastPasswordChange: PasswordChangeRequest? = null
        private set
    var profileFailure: AccountException? = null
    var passwordFailure: AccountException? = null
    var restoreFailuresRemaining = 0

    override fun hasStoredSession(): Boolean = user != null
    override suspend fun restore(): BusinessUser? {
        if (restoreFailuresRemaining > 0) {
            restoreFailuresRemaining -= 1
            throw AccountException(AccountFailureKind.NETWORK, AccountMessages.SESSION_CHECK)
        }
        return user
    }

    override suspend fun login(account: String, password: String): BusinessUser {
        if (account == "disabled" || account != TestUsers.EMPLOYEE.account || password != TestUsers.PASSWORD) {
            throw AccountException(AccountFailureKind.INVALID_CREDENTIALS, AccountMessages.INVALID_CREDENTIALS)
        }
        user = TestUsers.EMPLOYEE
        return user!!
    }

    override suspend fun updateProfile(request: ProfileUpdateRequest): BusinessUser {
        profileFailure?.let { throw it }
        lastProfileUpdate = request
        val current = checkNotNull(user)
        user = current.copy(
            account = request.username ?: current.account,
            name = request.displayName ?: current.name,
            idNumberMasked = request.idNumber?.let {
                if (it.length <= 4) "*".repeat(it.length) else "*".repeat(it.length - 4) + it.takeLast(4)
            } ?: current.idNumberMasked,
        )
        return user!!
    }

    override suspend fun changePassword(request: PasswordChangeRequest) {
        lastPasswordChange = request
        passwordFailure?.let { throw it }
    }

    override fun logout() {
        user = null
    }

    fun seed(user: BusinessUser) {
        this.user = user
    }
}
