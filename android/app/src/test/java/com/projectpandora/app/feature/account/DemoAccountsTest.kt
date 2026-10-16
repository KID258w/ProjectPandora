package com.projectpandora.app.feature.account

import org.junit.Assert.*
import org.junit.Test

class DemoAccountsTest {
    @Test fun blankAccountIsRejected() {
        assertEquals(LoginResult.Failure("请输入账号"), DemoAccounts.authenticate("  ", "password"))
    }
    @Test fun emptyPasswordIsRejected() {
        assertEquals(LoginResult.Failure("请输入密码"), DemoAccounts.authenticate("chenning", ""))
    }
    @Test fun wrongCredentialsAreRejected() {
        assertTrue(DemoAccounts.authenticate("chenning", "incorrect") is LoginResult.Failure)
        assertTrue(DemoAccounts.authenticate("unknown", DemoAccounts.PASSWORD) is LoginResult.Failure)
    }
    @Test fun passwordIsNotTrimmed() {
        assertTrue(DemoAccounts.authenticate("chenning", " ${DemoAccounts.PASSWORD}") is LoginResult.Failure)
    }
    @Test fun disabledAccountIsRejected() {
        assertEquals(LoginResult.Failure("该账号已停用，请联系管理员。"),
            DemoAccounts.authenticate("disabled", DemoAccounts.PASSWORD))
    }
    @Test fun allFourRolesCanBePreviewed() {
        DemoAccounts.users.forEach {
            assertEquals(LoginResult.Success(it), DemoAccounts.authenticate(it.account, DemoAccounts.PASSWORD))
        }
        assertEquals(4, DemoAccounts.users.map { it.role }.toSet().size)
    }
    @Test fun accountWhitespaceIsTrimmed() {
        assertEquals(LoginResult.Success(DemoAccounts.users.first()),
            DemoAccounts.authenticate(" chenning ", DemoAccounts.PASSWORD))
    }
    @Test fun organizationHasNoEmptySeparators() {
        assertEquals("产品部 · 产品一组", DemoAccounts.users[0].organization)
        assertEquals("产品部", DemoAccounts.users[2].organization)
        assertEquals("公司", DemoAccounts.users[3].organization)
    }
}
