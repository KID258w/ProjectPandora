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
        compose.onNodeWithText("请输入账号").assertIsDisplayed()
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

    override fun hasStoredSession(): Boolean = user != null
    override suspend fun restore(): BusinessUser? = user

    override suspend fun login(account: String, password: String): BusinessUser {
        if (account == "disabled" || account != TestUsers.EMPLOYEE.account || password != TestUsers.PASSWORD) {
            throw AccountException(AccountFailureKind.INVALID_CREDENTIALS, AccountMessages.INVALID_CREDENTIALS)
        }
        user = TestUsers.EMPLOYEE
        return user!!
    }

    override fun logout() {
        user = null
    }

    fun seed(user: BusinessUser) {
        this.user = user
    }
}
