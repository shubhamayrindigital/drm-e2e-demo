package com.ayrindigital.drme2edemo.data.api

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
)

@Serializable
data class ContentItem(
    val id: String,
    val title: String,
    val description: String?,
    val drm: Boolean,
    val entitled: Boolean,
)

@Serializable
data class ContentListResponse(
    val items: List<ContentItem>,
)

@Serializable
data class ContentDetail(
    val id: String,
    val title: String,
    val description: String?,
    val drm: Boolean,
    val manifestPath: String,
    val kid: String? = null,
    val cek: String? = null,
    val pssh: String? = null,
)

@Serializable
data class EntitlementGrantResponse(
    val ok: Boolean,
)

@Serializable
data class PlayManifestResponse(
    val manifestUrl: String,
    val licenseUrl: String,
    val playbackToken: String,
    val drmConfig: DrmConfig? = null,
)

@Serializable
data class DrmConfig(
    val kid: String,
    val cek: String,
    val pssh: String,
)
