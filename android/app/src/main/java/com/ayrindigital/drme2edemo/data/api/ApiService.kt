package com.ayrindigital.drme2edemo.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    // Auth
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    // Catalog
    @GET("catalog")
    suspend fun listContent(): List<ContentItem>

    @GET("catalog/{id}")
    suspend fun getContent(@Path("id") id: String): ContentDetail

    @POST("catalog/{id}/entitle")
    suspend fun grantEntitlement(@Path("id") id: String): EntitlementGrantResponse

    @GET("catalog/{id}/play")
    suspend fun getPlayManifest(@Path("id") id: String): PlayManifestResponse

    // License (expects binary response)
    @POST("license/widevine")
    suspend fun getWidevineLicense(@Body challenge: ByteArray): ByteArray

    // Offline
    @POST("offline/license/renew")
    suspend fun renewOfflineLicense(@Body request: RenewOfflineLicenseRequest): ByteArray

    @POST("offline/license/release")
    suspend fun releaseOfflineLicense(@Body request: ReleaseOfflineLicenseRequest): EntitlementGrantResponse
}

@kotlinx.serialization.Serializable
data class RenewOfflineLicenseRequest(
    val contentId: String,
)

@kotlinx.serialization.Serializable
data class ReleaseOfflineLicenseRequest(
    val contentId: String,
)
