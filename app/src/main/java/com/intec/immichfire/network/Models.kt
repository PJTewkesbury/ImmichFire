package com.intec.immichfire.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlbumSummary(
    val id: String,
    @SerialName("albumName") val name: String,
    @SerialName("assetCount") val assetCount: Int = 0,
    @SerialName("albumThumbnailAssetId") val thumbnailAssetId: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
)

@Serializable
data class AlbumDetails(
    val id: String,
    @SerialName("albumName") val name: String,
    val assets: List<Asset> = emptyList(),
)

@Serializable
data class Asset(
    val id: String,
    // Nullable so a future Immich payload that emits `type: null` (e.g. for an unknown or
    // placeholder asset) doesn't silently decode as IMAGE and get mis-played as a photo.
    // Consumers should filter with `type.equals("IMAGE", ignoreCase = true) == true`.
    val type: String? = null,
    val originalFileName: String? = null,
    val fileCreatedAt: String? = null,
    val localDateTime: String? = null,
    val exifInfo: ExifInfo? = null,
)

@Serializable
data class ExifInfo(
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val rating: Int? = null,
    val dateTimeOriginal: String? = null,
)

@Serializable
data class SearchResult(
    val assets: SearchAssetsPage,
)

@Serializable
data class SearchAssetsPage(
    val items: List<Asset> = emptyList(),
    val total: Int = 0,
    val count: Int = 0,
    val nextPage: String? = null,
)

@Serializable
data class SearchMetadataRequest(
    val type: String = "IMAGE",
    val order: String = "desc",
    val page: Int = 1,
    val size: Int = 500,
    val isNotInAlbum: Boolean? = null,
    val albumIds: List<String>? = null,
    val withExif: Boolean = true,
)

@Serializable
data class ServerPing(val res: String = "")

@Serializable
data class FoldersResponse(
    val folders: List<String> = emptyList(),
)

@Serializable
data class Library(
    val id: String,
    @SerialName("importPaths") val importPaths: List<String> = emptyList(),
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val userId: String? = null,
    val name: String? = null,
    val isAdmin: Boolean? = null,
)

@Serializable
data class CreateApiKeyRequest(
    val name: String,
    // Required by Immich 1.106+ — "all" matches the default when a user creates a key by
    // hand in the Immich account screen, so an auto-provisioned key behaves identically.
    val permissions: List<String> = listOf("all"),
)

@Serializable
data class CreateApiKeyResponse(
    val secret: String,
    val apiKey: ApiKeyInfo? = null,
)

@Serializable
data class ApiKeyInfo(
    val id: String? = null,
    val name: String? = null,
)
