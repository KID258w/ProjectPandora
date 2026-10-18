package com.projectpandora.app.feature.account

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectpandora.app.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DemoSessionStore(private val preferences: SharedPreferences) {
    fun restore(): BusinessUser? = if (BuildConfig.DEBUG) {
        DemoAccounts.users.find { it.account == preferences.getString("demo_account", null) }
    } else null

    fun save(user: BusinessUser) { preferences.edit().putString("demo_account", user.account).apply() }
    fun clear() { preferences.edit().remove("demo_account").apply() }
    fun notification(key: String): Boolean = preferences.getBoolean("notification_$key", true)
    fun setNotification(key: String, enabled: Boolean) {
        preferences.edit().putBoolean("notification_$key", enabled).apply()
    }
}

class AccountViewModel(private val store: DemoSessionStore) : ViewModel() {
    var user by mutableStateOf(store.restore())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun login(account: String, password: String) {
        if (loading) return
        if (account.isBlank() || password.isEmpty()) {
            error = if (account.isBlank()) "请输入账号" else "请输入密码"
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            delay(450)
            val result = if (BuildConfig.DEBUG) DemoAccounts.authenticate(account, password)
            else LoginResult.Failure("当前为界面预览阶段，正式登录接口尚未接入。")
            when (result) {
                is LoginResult.Success -> { store.save(result.user); user = result.user }
                is LoginResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun clearError() { error = null }
    fun logout() { store.clear(); user = null; error = null }
}
