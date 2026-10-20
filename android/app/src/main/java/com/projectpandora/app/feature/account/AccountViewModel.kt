package com.projectpandora.app.feature.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AccountViewModel(private val repository: AccountRepository) : ViewModel() {
    var user by mutableStateOf<BusinessUser?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var initializing by mutableStateOf(true)
        private set
    var startupDestination by mutableStateOf(AccountStartupDestination.WELCOME)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            try {
                if (repository.hasStoredSession()) {
                    user = repository.restore()
                    startupDestination = if (user == null) AccountStartupDestination.WELCOME
                    else AccountStartupDestination.HOME
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: AccountException) {
                user = null
                startupDestination = AccountStartupDestination.LOGIN
                error = failure.message
            } catch (_: Exception) {
                user = null
                startupDestination = AccountStartupDestination.LOGIN
                error = AccountMessages.SERVER
            } finally {
                initializing = false
            }
        }
    }

    fun login(account: String, password: String) {
        if (loading) return
        if (account.isBlank() || password.isEmpty()) {
            error = if (account.isBlank()) "请输入账号" else "请输入密码"
            return
        }

        loading = true
        error = null
        viewModelScope.launch {
            try {
                user = repository.login(account.trim(), password)
                error = null
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: AccountException) {
                user = null
                error = failure.message
            } catch (_: Exception) {
                user = null
                error = AccountMessages.SERVER
            } finally {
                loading = false
            }
        }
    }

    fun clearError() {
        error = null
    }

    fun logout() {
        repository.logout()
        user = null
        error = null
    }
}
