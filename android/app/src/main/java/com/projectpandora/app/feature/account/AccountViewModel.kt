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
    var canRetrySessionValidation by mutableStateOf(false)
        private set
    var profileSaving by mutableStateOf(false)
        private set
    var profileError by mutableStateOf<String?>(null)
        private set
    var passwordSaving by mutableStateOf(false)
        private set
    var passwordError by mutableStateOf<String?>(null)
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
                canRetrySessionValidation = failure.isRetryableSessionCheck()
            } catch (_: Exception) {
                user = null
                startupDestination = AccountStartupDestination.LOGIN
                error = AccountMessages.SERVER
                canRetrySessionValidation = true
            } finally {
                initializing = false
            }
        }
    }

    fun login(account: String, password: String) {
        if (loading) return
        if (account.isBlank() || password.isEmpty()) {
            error = if (account.isBlank()) "请输入手机号" else "请输入密码"
            return
        }

        loading = true
        error = null
        viewModelScope.launch {
            try {
                user = repository.login(account.trim(), password)
                error = null
                canRetrySessionValidation = false
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

    fun clearProfileError() {
        profileError = null
    }

    fun clearPasswordError() {
        passwordError = null
    }

    fun updateProfile(request: ProfileUpdateRequest, onSuccess: () -> Unit) {
        if (profileSaving) return
        profileSaving = true
        profileError = null
        viewModelScope.launch {
            try {
                user = repository.updateProfile(request)
                onSuccess()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: AccountException) {
                if (failure.kind == AccountFailureKind.SESSION_EXPIRED || failure.kind == AccountFailureKind.FORBIDDEN) {
                    clearSessionAfterProtectedRequestFailure(failure)
                } else {
                    profileError = failure.message ?: AccountMessages.PROFILE_UPDATE_FAILED
                }
            } catch (_: Exception) {
                profileError = AccountMessages.PROFILE_UPDATE_FAILED
            } finally {
                profileSaving = false
            }
        }
    }

    fun changePassword(request: PasswordChangeRequest, onSuccess: () -> Unit) {
        if (passwordSaving) return
        passwordSaving = true
        passwordError = null
        viewModelScope.launch {
            try {
                repository.changePassword(request)
                onSuccess()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: AccountException) {
                if (failure.kind == AccountFailureKind.SESSION_EXPIRED || failure.kind == AccountFailureKind.FORBIDDEN) {
                    clearSessionAfterProtectedRequestFailure(failure)
                } else {
                    passwordError = failure.message ?: AccountMessages.PASSWORD_CHANGE_FAILED
                }
            } catch (_: Exception) {
                passwordError = AccountMessages.PASSWORD_CHANGE_FAILED
            } finally {
                passwordSaving = false
            }
        }
    }

    fun retrySessionValidation() {
        if (loading || !canRetrySessionValidation) return

        loading = true
        canRetrySessionValidation = false
        error = null
        viewModelScope.launch {
            try {
                user = repository.restore()
                if (user == null) {
                    error = AccountMessages.SESSION_EXPIRED
                } else {
                    startupDestination = AccountStartupDestination.HOME
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: AccountException) {
                user = null
                error = failure.message
                canRetrySessionValidation = failure.isRetryableSessionCheck()
            } catch (_: Exception) {
                user = null
                error = AccountMessages.SESSION_CHECK
                canRetrySessionValidation = true
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        repository.logout()
        user = null
        error = null
        canRetrySessionValidation = false
    }

    private fun clearSessionAfterProtectedRequestFailure(failure: AccountException) {
        repository.logout()
        user = null
        startupDestination = AccountStartupDestination.LOGIN
        error = failure.message
        canRetrySessionValidation = false
    }

    private fun AccountException.isRetryableSessionCheck(): Boolean =
        kind == AccountFailureKind.NETWORK || kind == AccountFailureKind.SERVER
}
