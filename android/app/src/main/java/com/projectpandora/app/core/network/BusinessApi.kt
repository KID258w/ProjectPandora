package com.projectpandora.app.core.network

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * The endpoints are still being delivered. Keep the transport separate from the screens so that
 * real responses and errors are visible, and tests can provide a deterministic implementation.
 */
interface BusinessApi {
    @GET suspend fun get(@Url path: String, @QueryMap query: Map<String, String> = emptyMap()): JsonObject
    @POST suspend fun post(@Url path: String, @Body body: JsonObject = JsonObject()): JsonObject
    @POST suspend fun postEmpty(@Url path: String): JsonObject
    @PUT suspend fun put(@Url path: String, @Body body: JsonObject): JsonObject
    @PATCH suspend fun patch(@Url path: String, @Body body: JsonObject): JsonObject
    @DELETE suspend fun delete(@Url path: String): JsonObject
}
