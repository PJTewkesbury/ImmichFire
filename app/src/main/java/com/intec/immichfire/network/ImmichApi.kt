package com.intec.immichfire.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ImmichApi {

    @GET("api/server/ping")
    suspend fun ping(): ServerPing

    @GET("api/albums")
    suspend fun listAlbums(): List<AlbumSummary>

    @GET("api/albums/{id}")
    suspend fun getAlbum(@retrofit2.http.Path("id") id: String): AlbumDetails

    @POST("api/search/metadata")
    suspend fun searchMetadata(@Body request: SearchMetadataRequest): SearchResult

    @GET("api/view/folder/unique-paths")
    suspend fun listFolderPaths(): List<String>

    @GET("api/view/folder")
    suspend fun listFolderAssets(@Query("path") path: String): List<Asset>
}
