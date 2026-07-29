package com.vidbridge.library

import com.vidbridge.core.database.LibraryItemRow
import com.vidbridge.core.database.MediaLibraryDao
import com.vidbridge.core.database.MetadataRecordEntity
import com.vidbridge.core.database.ArtworkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Optional online enrichment. Local NFO remains the source of truth when no key is configured. */
class TmdbMetadataRepository(
    private val library: MediaLibraryDao,
    private val client: OkHttpClient,
) {
    suspend fun needsRefresh(item: LibraryItemRow): Boolean = withContext(Dispatchers.IO) {
        if (item.kind == "VIDEO") return@withContext false
        val existing = library.getMetadata("${item.mediaKey}:tmdb")
        existing == null || System.currentTimeMillis() - existing.updatedAtEpochMs >= METADATA_TTL_MS
    }

    suspend fun enrich(item: LibraryItemRow, apiKey: String) = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || item.kind == "VIDEO") return@withContext
        val mediaType = if (item.kind == "EPISODE") "tv" else "movie"
        val query = if (item.kind == "EPISODE") item.groupTitle else item.title
        if (query.isBlank()) return@withContext
        val metadataKey = "${item.mediaKey}:tmdb"
        val existing = library.getMetadata(metadataKey)
        if (existing != null && System.currentTimeMillis() - existing.updatedAtEpochMs < METADATA_TTL_MS) {
            return@withContext
        }
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("api.themoviedb.org")
            .addPathSegment("3")
            .addPathSegment("search")
            .addPathSegment(mediaType)
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("query", query)
            .addQueryParameter("language", "zh-CN")
            .addQueryParameter("include_adult", "false")
        item.year?.let { year ->
            urlBuilder.addQueryParameter(
                if (mediaType == "tv") "first_air_date_year" else "year",
                year.toString(),
            )
        }
        val url = urlBuilder.build()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext
            val body = response.body?.string().orEmpty()
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext
            if (results.length() == 0) {
                library.upsertMetadata(
                    listOf(
                        MetadataRecordEntity(
                            metadataKey = metadataKey,
                            mediaKey = item.mediaKey,
                            provider = "TMDB",
                            title = null,
                            originalTitle = null,
                            plot = null,
                            year = null,
                            rating = null,
                            director = null,
                            castMembers = null,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    ),
                )
                return@withContext
            }
            val result = results.optJSONObject(0) ?: return@withContext
            val title = result.optString(if (mediaType == "tv") "name" else "title").ifBlank { null }
            val originalTitle = result.optString(if (mediaType == "tv") "original_name" else "original_title").ifBlank { null }
            val plot = result.optString("overview").ifBlank { null }
            val date = result.optString(if (mediaType == "tv") "first_air_date" else "release_date")
            val year = date.take(4).toIntOrNull()
            val rating = result.optDouble("vote_average").takeUnless { it.isNaN() }?.toFloat()
            val credits = fetchCredits(mediaType, result.optInt("id", -1), apiKey)
            val director = credits?.optJSONArray("crew")
                ?.let { crew ->
                    (0 until crew.length()).asSequence()
                        .mapNotNull { crew.optJSONObject(it) }
                        .firstOrNull { it.optString("job") == "Director" || it.optString("job") == "Creator" }
                        ?.optString("name")
                        ?.ifBlank { null }
                }
            val castMembers = credits?.optJSONArray("cast")
                ?.let { cast ->
                    (0 until cast.length()).asSequence()
                        .mapNotNull { cast.optJSONObject(it)?.optString("name")?.ifBlank { null } }
                        .distinct()
                        .take(8)
                        .joinToString("\n")
                        .ifBlank { null }
                }
            library.upsertMetadata(
                listOf(
                    MetadataRecordEntity(
                        metadataKey = metadataKey,
                        mediaKey = item.mediaKey,
                        provider = "TMDB",
                        title = title,
                        originalTitle = originalTitle,
                        plot = plot,
                        year = year,
                        rating = rating,
                        director = director,
                        castMembers = castMembers,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                ),
            )
            val artwork = buildList {
                result.optString("poster_path").ifBlank { null }?.let { poster ->
                    add(ArtworkEntity("${item.mediaKey}:poster-tmdb", item.mediaKey, "POSTER", imageUrl("w780", poster), System.currentTimeMillis()))
                }
                result.optString("backdrop_path").ifBlank { null }?.let { backdrop ->
                    add(ArtworkEntity("${item.mediaKey}:backdrop-tmdb", item.mediaKey, "BACKDROP", imageUrl("w1280", backdrop), System.currentTimeMillis()))
                }
            }
            if (artwork.isNotEmpty()) library.upsertArtwork(artwork)
        }
    }

    private fun imageUrl(size: String, path: String): String = "https://image.tmdb.org/t/p/$size$path"

    private fun fetchCredits(mediaType: String, id: Int, apiKey: String): JSONObject? {
        if (id <= 0) return null
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.themoviedb.org")
            .addPathSegment("3")
            .addPathSegment(mediaType)
            .addPathSegment(id.toString())
            .addPathSegment("credits")
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", "zh-CN")
            .build()
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.let(::JSONObject) else null
            }
        }.getOrNull()
    }

    private companion object {
        const val METADATA_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
