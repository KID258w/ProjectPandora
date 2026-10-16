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
    private val preferences get() = context.getSharedPreferences("pandora_demo_ui", Context.MODE_PRIVATE)

    @Before fun clearSession() { preferences.edit().clear().commit() }

    private fun start() {
        compose.setContent { PandoraAppTheme { PandoraApp(startupDelayMillis = 0) } }
    }
    private fun login(account: String = "chenning") {
        compose.onNodeWithText("进入潘多拉").performScrollTo().performClick()
        compose.onNodeWithTag("account").performTextInput(account)
        compose.onNodeWithTag("password").performTextInput(DemoAccounts.PASSWORD)
        compose.onNodeWithText("登录", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("你好，${DemoAccounts.users.first { it.account == account }.name}").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
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
        compose.onNodeWithTag("password").performTextInput(DemoAccounts.PASSWORD)
        compose.onNodeWithContentDescription("显示密码").performClick()
        compose.onNodeWithContentDescription("隐藏密码").assertExists()
        compose.onNodeWithTag("password").assertTextContains(DemoAccounts.PASSWORD)
        compose.onNodeWithContentDescription("隐藏密码").performClick()
        compose.onNodeWithContentDescription("显示密码").assertExists()
    }
    @Test fun rejectedAndDisabledAccountsShowErrors() {
        start()
        compose.onNodeWithText("进入潘多拉").performScrollTo().performClick()
        compose.onNodeWithTag("account").performTextInput("chenning")
        compose.onNodeWithTag("password").performTextInput("wrong")
        compose.onNodeWithText("登录").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("账号或密码错误，请检查后重试。").fetchSemanticsNodes().isNotEmpty() }
        screenshot("03-login-error")
        compose.onNodeWithTag("account").performTextReplacement("disabled")
        compose.onNodeWithTag("password").performTextReplacement(DemoAccounts.PASSWORD)
        compose.onNodeWithText("登录").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("该账号已停用，请联系管理员。").fetchSemanticsNodes().isNotEmpty() }
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
        assertNull(preferences.getString("demo_account", null))
        compose.onNodeWithTag("password").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }
    @Test fun savedSessionStartsAtHomeAndAllTabsNavigate() {
        DemoSessionStore(preferences).save(DemoAccounts.users[0])
        start()
        compose.onNodeWithText("你好，陈宁").assertExists()
        listOf("视图", "日志", "AI地图", "我的", "首页").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("你好，陈宁").assertIsDisplayed()
        compose.onNodeWithText("视图").performClick()
        compose.onNodeWithText("打开任务中心").performClick()
        compose.onNodeWithText("‹ 返回").performClick()
        compose.onNodeWithText("工作视图").assertIsDisplayed()
    }
    @Test fun leaderAndFounderOrganizationFixturesAreRestored() {
        DemoSessionStore(preferences).save(DemoAccounts.users[3])
        start()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("创始人").assertExists()
        compose.onNodeWithText("个人资料").performScrollTo().performClick()
        compose.onNodeWithText("业务身份").assertExists()
    }
    @Test fun sessionStoresNoPasswordAndCanBeCleared() {
        val store = DemoSessionStore(preferences)
        store.save(DemoAccounts.users[1])
        assertEquals(DemoAccounts.users[1], DemoSessionStore(preferences).restore())
        assertFalse(preferences.all.values.contains(DemoAccounts.PASSWORD))
        store.clear()
        assertNull(DemoSessionStore(preferences).restore())
    }
}
