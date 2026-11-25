package com.intec.immichfire.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlbumSummary(
    val id: String,
    @SerialName("albumName") val name: String,
    @SerialName("assetCount") val assetCount: Int = 0,
    @SerialName("albumThumbnailAssetId") val thumbnailAssetId: String? = null,
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
    val type: String = "IMAGE",
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
