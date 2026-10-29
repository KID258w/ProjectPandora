package com.projectpandora.app.core.network

import com.projectpandora.app.BuildConfig
import com.projectpandora.app.feature.account.AppAccountApi
import com.projectpandora.app.feature.account.AppTokenRefreshApi
import com.projectpandora.app.feature.account.RefreshRequest
import com.projectpandora.app.feature.account.TokenResponse
import com.projectpandora.app.feature.account.TokenStorage
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

object ApiClient {
    fun createAppAccountApi(tokenStorage: TokenStorage, baseUrl: String = BuildConfig.API_BASE_URL): AppAccountApi {
        return authenticatedRetrofit(tokenStorage, baseUrl).create(AppAccountApi::class.java)
    }

    fun createBusinessApi(tokenStorage: TokenStorage, baseUrl: String = BuildConfig.API_BASE_URL): BusinessApi =
        authenticatedRetrofit(tokenStorage, baseUrl).create(BusinessApi::class.java)

    private fun authenticatedRetrofit(tokenStorage: TokenStorage, baseUrl: String): Retrofit {
        val refreshApi = retrofit(baseUrl, OkHttpClient.Builder().build()).create(AppTokenRefreshApi::class.java)
        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor(AppAccessTokenInterceptor(tokenStorage))
            .authenticator(AppTokenAuthenticator(refreshApi, tokenStorage))
            .build()
        return retrofit(baseUrl, authenticatedClient)
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}

private class AppAccessTokenInterceptor(private val tokenStorage: TokenStorage) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isAppAuthentication = path.startsWith("/api/v1/app/auth/token/")
        val access = if (isAppAuthentication) null else tokenStorage.read()?.access
        val authenticated = if (access.isNullOrBlank()) request else request.newBuilder()
            .header("Authorization", "Bearer $access")
            .build()
        return chain.proceed(authenticated)
    }
}

/** Refreshes an access token once after a protected endpoint returns 401. */
private class AppTokenAuthenticator(
    private val refreshApi: AppTokenRefreshApi,
    private val tokenStorage: TokenStorage,
) : Authenticator {
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? = synchronized(refreshLock) {
        if (response.request.tag(RetryMarker::class.java) != null) {
            // The refreshed access token was also rejected; avoid another refresh loop.
            tokenStorage.clear()
            return@synchronized null
        }
        if (responseCount(response) >= MAX_AUTH_ATTEMPTS) return@synchronized null
        val failedAuthorization = response.request.header("Authorization") ?: return@synchronized null
        val tokens = tokenStorage.read() ?: return@synchronized null
        val currentAuthorization = "Bearer ${tokens.access}"

        // Another request may already have refreshed the same session while this call waited.
        if (currentAuthorization != failedAuthorization) {
            return@synchronized response.request.newBuilder()
                .header("Authorization", currentAuthorization)
                .tag(RetryMarker::class.java, RetryMarker)
                .build()
        }

        val refreshResponse = try {
            refreshApi.refresh(RefreshRequest(tokens.refresh)).execute()
        } catch (_: IOException) {
            return@synchronized null
        }

        if (refreshResponse.isSuccessful) {
            val data = refreshResponse.body()?.data ?: return@synchronized null
            if (data.access.isBlank()) return@synchronized null
            tokenStorage.updateAccess(data.access, data.refresh)
            return@synchronized response.request.newBuilder()
                .header("Authorization", "Bearer ${data.access}")
                .tag(RetryMarker::class.java, RetryMarker)
                .build()
        }

        // Keep the session on server errors; a rejected client token cannot recover.
        if (refreshResponse.code() in 400..499 && refreshResponse.code() != 429) tokenStorage.clear()
        null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var previous = response.priorResponse
        while (previous != null) {
            count += 1
            previous = previous.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_AUTH_ATTEMPTS = 2
    }

    private object RetryMarker
}
