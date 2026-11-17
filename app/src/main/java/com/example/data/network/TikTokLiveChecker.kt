package com.example.data.network

import com.example.data.local.StreamQualityPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class StreamQualityOption(
    val key: String,
    val label: String,
    val hlsUrl: String?,
    val flvUrl: String?
)

data class LiveCreatorInfo(
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val roomId: String,
    val title: String,
    val viewerCount: Long,
    val selectedQualityLabel: String,
    val hlsUrl: String?,
    val flvUrl: String?,
    val availableQualities: List<StreamQualityOption>,
    val checkedAtMillis: Long = System.currentTimeMillis()
) {
    val previewStreamUrl: String?
        get() = hlsUrl ?: flvUrl
}

sealed interface LiveCheckResult {
    data class Online(val info: LiveCreatorInfo) : LiveCheckResult
    data class Offline(
        val username: String,
        val displayName: String?,
        val avatarUrl: String?,
        val reason: String
    ) : LiveCheckResult
    data class NotFound(val username: String, val reason: String) : LiveCheckResult
    data class Error(val username: String, val reason: String) : LiveCheckResult
}

class TikTokLiveChecker(
    private val client: OkHttpClient = defaultOkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun normalizeUsername(rawInput: String): String {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) return ""
            val fromUrl = Regex("""tiktok\.com/@([A-Za-z0-9._]+)""")
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
            val candidate = (fromUrl ?: trimmed)
                .removePrefix("@")
                .substringBefore("/")
                .substringBefore("?")
                .trim()
            return candidate
        }
    }

    suspend fun checkLiveStatus(
        rawUsername: String,
        preferredQuality: StreamQualityPreference
    ): LiveCheckResult = withContext(Dispatchers.IO) {
        val username = normalizeUsername(rawUsername)
        if (username.isBlank() || !username.matches(Regex("""^[A-Za-z0-9._]{1,24}$"""))) {
            return@withContext LiveCheckResult.NotFound(
                username = rawUsername.trim(),
                reason = "Enter a valid TikTok username (letters, numbers, underscores, or dots)."
            )
        }

        try {
            val livePageResult = fetchLivePageHtml(username)
            if (livePageResult == null) {
                return@withContext LiveCheckResult.Error(
                    username = username,
                    reason = "Unable to reach TikTok right now. Check your internet connection and try again."
                )
            }

            val (statusCode, html) = livePageResult
            if (statusCode == 404) {
                return@withContext LiveCheckResult.NotFound(
                    username = username,
                    reason = "No TikTok creator found for @$username."
                )
            }

            // 1. Try parsing embedded JSON in the live webpage
            val embeddedParsed = parseFromLiveHtml(username, html, preferredQuality)
            if (embeddedParsed is LiveCheckResult.Online) {
                return@withContext embeddedParsed
            }

            // 2. Extract roomId from HTML or api-live/user/room endpoint and query webcast room/info
            val roomId = extractRoomIdFromHtml(html) ?: fetchRoomIdFromApi(username)
            if (!roomId.isNullOrBlank() && roomId != "0") {
                val roomInfoResult = fetchWebcastRoomInfo(username, roomId, preferredQuality)
                if (roomInfoResult != null) {
                    return@withContext roomInfoResult
                }
            }

            if (embeddedParsed != null) {
                return@withContext embeddedParsed
            }

            LiveCheckResult.Offline(
                username = username,
                displayName = "@$username",
                avatarUrl = null,
                reason = "@$username is not live right now."
            )
        } catch (e: IOException) {
            LiveCheckResult.Error(
                username = username,
                reason = "Network error while checking @$username: ${e.message ?: "Connection failed"}"
            )
        } catch (e: Exception) {
            LiveCheckResult.Error(
                username = username,
                reason = "Failed to inspect @$username live status: ${e.message ?: "Unexpected response"}"
            )
        }
    }

    private fun fetchLivePageHtml(username: String): Pair<Int, String>? {
        val url = "https://www.tiktok.com/@$username/live"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://www.tiktok.com/")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return response.code to body
        }
    }

    private fun fetchRoomIdFromApi(username: String): String? {
        val url = "https://www.tiktok.com/api-live/user/room/?aid=1988&uniqueId=$username&sourceType=54"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Referer", "https://www.tiktok.com/@$username/live")
            .header("Accept", "application/json")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject ?: return@use null
                val liveRoom = data["liveRoom"]?.jsonObject
                val user = data["user"]?.jsonObject
                liveRoom?.get("roomId")?.jsonPrimitive?.contentOrNull
                    ?: user?.get("roomId")?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
    }

    private fun parseFromLiveHtml(
        username: String,
        html: String,
        preferredQuality: StreamQualityPreference
    ): LiveCheckResult? {
        val sigiJson = extractScriptJson(html, "SIGI_STATE")
            ?: extractScriptJson(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__")
            ?: return null

        return runCatching {
            val root = json.parseToJsonElement(sigiJson).jsonObject
            val liveRoomObj = findLiveRoomObject(root) ?: return@runCatching null
            val liveRoomUserInfo = liveRoomObj["liveRoomUserInfo"]?.jsonObject ?: liveRoomObj

            val userObj = liveRoomUserInfo["user"]?.jsonObject
            val roomObj = liveRoomUserInfo["liveRoom"]?.jsonObject ?: liveRoomObj

            val displayName = userObj?.get("nickname")?.jsonPrimitive?.contentOrNull
                ?: "@$username"
            val avatarUrl = userObj?.get("avatarLarger")?.jsonPrimitive?.contentOrNull
                ?: userObj?.get("avatarMedium")?.jsonPrimitive?.contentOrNull
                ?: userObj?.get("avatarThumb")?.jsonPrimitive?.contentOrNull
                ?: ""

            val status = roomObj["status"]?.jsonPrimitive?.intOrNull ?: 0
            val roomId = roomObj["roomId"]?.jsonPrimitive?.contentOrNull
                ?: userObj?.get("roomId")?.jsonPrimitive?.contentOrNull
                ?: ""
            val title = roomObj["title"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: "$displayName's Live"
            val viewerCount = roomObj["liveRoomStats"]?.jsonObject
                ?.get("userCount")?.jsonPrimitive?.longOrNull
                ?: roomObj["userCount"]?.jsonPrimitive?.longOrNull
                ?: 0L

            if (status != 2) {
                return@runCatching LiveCheckResult.Offline(
                    username = username,
                    displayName = displayName,
                    avatarUrl = avatarUrl.ifBlank { null },
                    reason = "$displayName (@$username) is currently offline."
                )
            }

            val streamDataRaw = roomObj["streamData"]?.jsonObject
                ?.get("pull_data")?.jsonObject
                ?.get("stream_data")?.jsonPrimitive?.contentOrNull

            val qualities = if (!streamDataRaw.isNullOrBlank()) {
                parseStreamDataPayload(streamDataRaw)
            } else {
                emptyList()
            }

            val chosen = selectQuality(qualities, preferredQuality)
            if (chosen != null && (!chosen.hlsUrl.isNullOrBlank() || !chosen.flvUrl.isNullOrBlank())) {
                LiveCheckResult.Online(
                    LiveCreatorInfo(
                        username = username,
                        displayName = displayName,
                        avatarUrl = avatarUrl,
                        roomId = roomId,
                        title = title,
                        viewerCount = viewerCount,
                        selectedQualityLabel = chosen.label,
                        hlsUrl = chosen.hlsUrl,
                        flvUrl = chosen.flvUrl,
                        availableQualities = qualities
                    )
                )
            } else {
                null
            }
        }.getOrNull()
    }

    private fun fetchWebcastRoomInfo(
        username: String,
        roomId: String,
        preferredQuality: StreamQualityPreference
    ): LiveCheckResult? {
        val url = "https://webcast.tiktok.com/webcast/room/info/?aid=1988&room_id=$roomId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Referer", "https://www.tiktok.com/@$username/live")
            .header("Accept", "application/json")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject ?: return@use null

                val status = data["status"]?.jsonPrimitive?.intOrNull ?: 0
                val owner = data["owner"]?.jsonObject
                val displayName = owner?.get("nickname")?.jsonPrimitive?.contentOrNull
                    ?: "@$username"
                val avatarUrl = owner?.get("avatar_thumb")?.jsonObject
                    ?.get("url_list")?.jsonArray
                    ?.firstOrNull()?.jsonPrimitive?.contentOrNull
                    ?: ""
                val title = data["title"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?: "$displayName's Live"
                val viewerCount = data["user_count"]?.jsonPrimitive?.longOrNull ?: 0L

                if (status != 2) {
                    return@use LiveCheckResult.Offline(
                        username = username,
                        displayName = displayName,
                        avatarUrl = avatarUrl.ifBlank { null },
                        reason = "$displayName (@$username) is currently offline."
                    )
                }

                val streamUrlObj = data["stream_url"]?.jsonObject
                val qualities = buildList {
                    val liveCoreRaw = streamUrlObj
                        ?.get("live_core_sdk_data")?.jsonObject
                        ?.get("pull_data")?.jsonObject
                        ?.get("stream_data")?.jsonPrimitive?.contentOrNull
                    if (!liveCoreRaw.isNullOrBlank()) {
                        addAll(parseStreamDataPayload(liveCoreRaw))
                    }

                    if (isEmpty() && streamUrlObj != null) {
                        val hlsMap = streamUrlObj["hls_pull_url_map"]?.jsonObject
                        val flvMap = streamUrlObj["flv_pull_url"]?.jsonObject
                        val keys = (hlsMap?.keys.orEmpty() + flvMap?.keys.orEmpty()).distinct()
                        for (key in keys) {
                            val hls = hlsMap?.get(key)?.jsonPrimitive?.contentOrNull
                            val flv = flvMap?.get(key)?.jsonPrimitive?.contentOrNull
                            if (!hls.isNullOrBlank() || !flv.isNullOrBlank()) {
                                add(
                                    StreamQualityOption(
                                        key = key,
                                        label = formatQualityLabel(key),
                                        hlsUrl = hls,
                                        flvUrl = flv
                                    )
                                )
                            }
                        }
                        if (isEmpty()) {
                            val hls = streamUrlObj["hls_pull_url"]?.jsonPrimitive?.contentOrNull
                            val flv = streamUrlObj["rtmp_pull_url"]?.jsonPrimitive?.contentOrNull
                            if (!hls.isNullOrBlank() || !flv.isNullOrBlank()) {
                                add(
                                    StreamQualityOption(
                                        key = "origin",
                                        label = "Original",
                                        hlsUrl = hls,
                                        flvUrl = flv
                                    )
                                )
                            }
                        }
                    }
                }

                val chosen = selectQuality(qualities, preferredQuality)
                if (chosen == null || (chosen.hlsUrl.isNullOrBlank() && chosen.flvUrl.isNullOrBlank())) {
                    return@use LiveCheckResult.Offline(
                        username = username,
                        displayName = displayName,
                        avatarUrl = avatarUrl.ifBlank { null },
                        reason = "@$username appears live, but no playable stream URL was exposed."
                    )
                }

                LiveCheckResult.Online(
                    LiveCreatorInfo(
                        username = username,
                        displayName = displayName,
                        avatarUrl = avatarUrl,
                        roomId = roomId,
                        title = title,
                        viewerCount = viewerCount,
                        selectedQualityLabel = chosen.label,
                        hlsUrl = chosen.hlsUrl,
                        flvUrl = chosen.flvUrl,
                        availableQualities = qualities
                    )
                )
            }
        }.getOrNull()
    }

    private fun parseStreamDataPayload(streamDataJson: String): List<StreamQualityOption> {
        return runCatching {
            val root = json.parseToJsonElement(streamDataJson).jsonObject
            val dataObj = root["data"]?.jsonObject ?: return@runCatching emptyList()
            val orderedKeys = listOf("origin", "uhd", "hd", "sd", "ld")
            val allKeys = (orderedKeys.filter { dataObj.containsKey(it) } +
                dataObj.keys.filter { it !in orderedKeys })

            allKeys.mapNotNull { key ->
                val main = dataObj[key]?.jsonObject?.get("main")?.jsonObject ?: return@mapNotNull null
                val hls = main["hls"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                val flv = main["flv"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                if (hls == null && flv == null) return@mapNotNull null
                StreamQualityOption(
                    key = key,
                    label = formatQualityLabel(key),
                    hlsUrl = hls,
                    flvUrl = flv
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun selectQuality(
        qualities: List<StreamQualityOption>,
        pref: StreamQualityPreference
    ): StreamQualityOption? {
        if (qualities.isEmpty()) return null
        return when (pref) {
            StreamQualityPreference.BEST -> qualities.firstOrNull {
                it.key.equals("origin", true) || it.key.equals("uhd", true) || it.key.contains("FULL_HD", true)
            } ?: qualities.first()
            StreamQualityPreference.HD -> qualities.firstOrNull {
                it.key.equals("hd", true) || it.key.equals("HD1", true)
            } ?: qualities.first()
            StreamQualityPreference.SD -> qualities.firstOrNull {
                it.key.equals("sd", true) || it.key.equals("SD1", true)
            } ?: qualities.last()
        }
    }

    private fun formatQualityLabel(key: String): String = when (key.lowercase()) {
        "origin", "full_hd1" -> "Source (Original)"
        "uhd" -> "UHD 1080p"
        "hd", "hd1" -> "HD 720p"
        "sd", "sd1" -> "SD 480p"
        "ld", "ld1" -> "Low 360p"
        else -> key.uppercase()
    }

    private fun extractScriptJson(html: String, scriptId: String): String? {
        val pattern = Regex(
            """<script[^>]*id=["']$scriptId["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        return pattern.find(html)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractRoomIdFromHtml(html: String): String? {
        val patterns = listOf(
            Regex(""""roomId"\s*:\s*"(\d+)""""),
            Regex(""""room_id"\s*:\s*"?(\d+)"?"""),
            Regex("""room_id=(\d+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(html)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank() && match != "0") return match
        }
        return null
    }

    private fun findLiveRoomObject(element: JsonElement): JsonObject? {
        when (element) {
            is JsonObject -> {
                if (element.containsKey("LiveRoom")) {
                    return element["LiveRoom"]?.jsonObject
                }
                if (element.containsKey("liveRoomUserInfo")) {
                    return element
                }
                for (value in element.values) {
                    val found = findLiveRoomObject(value)
                    if (found != null) return found
                }
            }
            is JsonArray -> {
                for (item in element) {
                    val found = findLiveRoomObject(item)
                    if (found != null) return found
                }
            }
            else -> Unit
        }
        return null
    }

    private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
        get() = if (isString) content else content.takeIf { it != "null" }
}
