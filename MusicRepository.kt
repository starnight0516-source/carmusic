package tw.carmusic.yt.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tw.carmusic.yt.model.MusicGenre
import java.util.UUID

class MusicRepository(context: Context) {

    private val prefs = context.getSharedPreferences("car_music_yt_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_GENRES = "custom_genres_json"
        private const val KEY_PLAYED_PREFIX = "played_ids_"
        private const val KEY_LAST_GENRE_ID = "last_genre_id"
        private const val KEY_SHOW_VIDEO = "show_video_preview"

        // 預設台灣車用熱門 YouTube 公開歌單（皆可於 App 內隨時新增自訂 YouTube 歌單網址）
        val DEFAULT_GENRES: List<MusicGenre> = listOf(
            MusicGenre(
                id = "mandopop_hot",
                title = "流行華語",
                subtitle = "最新熱門中文流行金曲",
                playlistId = "PLF_SFjIfN2CjZcPCUwYUtfSoNXlMOQFu4",
                accentHex = 0xFF00D2FF
            ),
            MusicGenre(
                id = "taiwanese_pop",
                title = "台語流行",
                subtitle = "熱門台語新歌與必聽排行",
                playlistId = "PLtPYVfBtvBnHE_v-MHPkLRt0-7Zg8Syri",
                accentHex = 0xFFFF9800
            ),
            MusicGenre(
                id = "taiwanese_classic",
                title = "台語經典",
                subtitle = "百聽不膩經典台語老歌",
                playlistId = "PL5sNcF_0EQsQR4T5Je321MfMljjfSTtet",
                accentHex = 0xFFFFB300
            ),
            MusicGenre(
                id = "mandarin_taiwanese_mix",
                title = "國台語金曲",
                subtitle = "KTV 經典國台語精選",
                playlistId = "PLBXbV4r0J0ZjrQJgUg_1RaQsGtoKJa64S",
                accentHex = 0xFFEC4899
            ),
            MusicGenre(
                id = "mandarin_oldies",
                title = "懷舊國語老歌",
                subtitle = "長途必備經典華語老歌",
                playlistId = "PLs8NvKIvT-4P3w4eqJy9WO5zmxJkl0G5Q",
                accentHex = 0xFFA855F7
            ),
            MusicGenre(
                id = "western_pop",
                title = "西洋流行",
                subtitle = "全球熱門英文排行精選",
                playlistId = "PLR1BVFd9wv8iFTEEDkwjXgVTTwJS20pzM",
                accentHex = 0xFF22C55E
            ),
            MusicGenre(
                id = "kpop_hits",
                title = "韓樂 K-POP",
                subtitle = "人氣韓流精選節奏",
                playlistId = "PLUchfRwifqMA3o62lmuWURbZCKEsNFo6N",
                accentHex = 0xFF38BDF8
            ),
            MusicGenre(
                id = "mandopop_chart",
                title = "華語排行榜",
                subtitle = "熱播單曲不間斷連播",
                playlistId = "PLp0gu4ngDOU4-wBrmxA0DkX1PgHoylJxx",
                accentHex = 0xFFFB7185
            )
        )
    }

    fun getAllGenres(): List<MusicGenre> = DEFAULT_GENRES + getCustomGenres()

    fun getCustomGenres(): List<MusicGenre> {
        val raw = prefs.getString(KEY_CUSTOM_GENRES, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = obj.optString("title").trim()
                val pid = obj.optString("playlistId").trim()
                if (title.isBlank() || pid.isBlank()) return@mapNotNull null
                MusicGenre(
                    id = obj.optString("id").ifBlank { "custom_$i" },
                    title = title,
                    subtitle = "自訂 YouTube 歌單",
                    playlistId = pid,
                    accentHex = 0xFF14B8A6,
                    isCustom = true
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addCustomPlaylist(title: String, rawInput: String): MusicGenre? {
        val playlistId = extractPlaylistId(rawInput) ?: return null
        val newGenre = MusicGenre(
            id = "custom_${UUID.randomUUID()}",
            title = title.trim(),
            subtitle = "自訂 YouTube 歌單",
            playlistId = playlistId,
            accentHex = 0xFF14B8A6,
            isCustom = true
        )
        val updated = getCustomGenres() + newGenre
        saveCustomGenres(updated)
        return newGenre
    }

    fun removeCustomPlaylist(genreId: String) {
        val updated = getCustomGenres().filterNot { it.id == genreId }
        saveCustomGenres(updated)
        clearPlayedHistory(genreId)
    }

    private fun saveCustomGenres(list: List<MusicGenre>) {
        val arr = JSONArray()
        list.forEach { g ->
            arr.put(
                JSONObject().apply {
                    put("id", g.id)
                    put("title", g.title)
                    put("playlistId", g.playlistId)
                }
            )
        }
        prefs.edit().putString(KEY_CUSTOM_GENRES, arr.toString()).apply()
    }

    /**
     * 從完整 YouTube 網址（youtube.com/playlist?list=... 或 music.youtube.com/...）或純 ID 擷取 playlistId。
     */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val listParamRegex = Regex("[?&]list=([a-zA-Z0-9_-]+)")
        val match = listParamRegex.find(trimmed)
        if (match != null) return match.groupValues[1]
        // 若使用者直接貼上 PL... / OLAK... / RDCLAK... 歌單 ID
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{10,}$"))) return trimmed
        return null
    }

    fun getPlayedIds(genreId: String): Set<String> =
        prefs.getStringSet("$KEY_PLAYED_PREFIX$genreId", emptySet())?.toSet() ?: emptySet()

    fun markVideoPlayed(genreId: String, videoId: String): Set<String> {
        val next = getPlayedIds(genreId).toMutableSet()
        next.add(videoId)
        prefs.edit().putStringSet("$KEY_PLAYED_PREFIX$genreId", next).apply()
        return next
    }

    fun clearPlayedHistory(genreId: String) {
        prefs.edit().remove("$KEY_PLAYED_PREFIX$genreId").apply()
    }

    fun getLastGenreId(): String? = prefs.getString(KEY_LAST_GENRE_ID, null)

    fun saveLastGenreId(genreId: String) {
        prefs.edit().putString(KEY_LAST_GENRE_ID, genreId).apply()
    }

    fun isShowVideoPreview(): Boolean = prefs.getBoolean(KEY_SHOW_VIDEO, true)

    fun setShowVideoPreview(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_VIDEO, show).apply()
    }
}
