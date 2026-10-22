package com.projectpandora.app.feature.account

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class ApiEnvelope<T>(
    val code: Int,
    val message: String,
    val data: T,
    @SerializedName("requestId") val requestId: String,
)

data class LoginRequest(
    val username: String,
    val password: String,
)

data class RefreshRequest(
    val refresh: String,
)

data class TokenResponse(
    val access: String,
    val refresh: String? = null,
)

data class AppUserResponse(
    val id: Long,
    val username: String,
    @SerializedName("account_type") val accountType: String,
    @SerializedName("business_role") val businessRole: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("department_id") val departmentId: Long?,
    @SerializedName("department_name") val departmentName: String?,
    @SerializedName("team_id") val teamId: Long?,
    @SerializedName("team_name") val teamName: String?,
    @SerializedName("manager_id") val managerId: Long?,
    @SerializedName("manager_name") val managerName: String?,
    @SerializedName("is_active") val isActive: Boolean,
) {
    fun toBusinessUser(): BusinessUser {
        val roleLabel = when (businessRole) {
            "founder" -> "创始人"
            "department_manager" -> "部门经理"
            "team_leader" -> "团队长"
            "employee" -> "员工"
            else -> "业务用户"
        }
        return BusinessUser(
            account = username,
            name = displayName.ifBlank { username },
            role = roleLabel,
            department = departmentName,
            team = teamName,
            supervisor = managerName,
        )
    }
}

interface AppAccountApi {
    @POST("api/v1/app/auth/token/")
    suspend fun login(@Body request: LoginRequest): Response<ApiEnvelope<TokenResponse>>

    @GET("api/v1/app/me/")
    suspend fun currentUser(): Response<ApiEnvelope<AppUserResponse>>
}

interface AppTokenRefreshApi {
    @POST("api/v1/app/auth/token/refresh/")
    fun refresh(@Body request: RefreshRequest): Call<ApiEnvelope<TokenResponse>>
}
