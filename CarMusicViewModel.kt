package tw.carmusic.yt.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tw.carmusic.yt.data.MusicRepository
import tw.carmusic.yt.model.MusicGenre
import tw.carmusic.yt.player.YouTubeCarPlayerView
import tw.carmusic.yt.player.YouTubePlayerCallback
import tw.carmusic.yt.service.CarMediaBridge
import tw.carmusic.yt.service.MusicKeepAliveService

data class CarMusicUiState(
    val genres: List<MusicGenre> = emptyList(),
    val activeGenre: MusicGenre? = null,
    val currentTitle: String = "",
    val currentVideoId: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val remainingUnplayed: Int = 0,
    val totalInPlaylist: Int = 0,
    val statusHint: String = "點選任一音樂類型，立即連續不重複播放",
    val showVideoPreview: Boolean = true
)

class CarMusicViewModel(application: Application) : AndroidViewModel(application), YouTubePlayerCallback {

    private val repository = MusicRepository(application)
    private val _uiState = MutableStateFlow(
        CarMusicUiState(
            genres = repository.getAllGenres(),
            showVideoPreview = repository.isShowVideoPreview()
        )
    )
    val uiState: StateFlow<CarMusicUiState> = _uiState.asStateFlow()

    private var playerView: YouTubeCarPlayerView? = null

    // 不重複隨機佇列
    private var currentPool: List<String> = emptyList()
    private var unplayedQueue: ArrayDeque<String> = ArrayDeque()
    private val sessionHistory = ArrayList<String>()
    private var historyCursor = -1
    private var consecutiveErrors = 0

    init {
        CarMediaBridge.onPlayPause = { togglePlayPause() }
        CarMediaBridge.onNext = { playNextUnplayed() }
        CarMediaBridge.onPrevious = { playPreviousInHistory() }
    }

    fun attachPlayer(view: YouTubeCarPlayerView) {
        playerView = view
    }

    fun selectGenre(genre: MusicGenre) {
        repository.saveLastGenreId(genre.id)
        consecutiveErrors = 0
        currentPool = emptyList()
        unplayedQueue.clear()
        sessionHistory.clear()
        historyCursor = -1

        _uiState.update {
            it.copy(
                activeGenre = genre,
                isBuffering = true,
                isPlaying = false,
                currentTitle = "載入「${genre.title}」YouTube 歌單中…",
                statusHint = "正在取得「${genre.title}」歌曲清單並建立不重複隨機佇列…"
            )
        }
        playerView?.cuePlaylistForIds(genre.playlistId)
    }

    override fun onPlayerReady() {
        _uiState.update {
            if (it.activeGenre == null) {
                it.copy(statusHint = "YouTube 播放引擎就緒，點選類型立即開播")
            } else it
        }
    }

    override fun onPlaylistLoaded(videoIds: List<String>) {
        val genre = _uiState.value.activeGenre ?: return
        val distinctIds = videoIds.distinct()
        if (distinctIds.isEmpty()) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    statusHint = "此歌單目前無可播放曲目，請換其他類型或自訂歌單"
                )
            }
            return
        }

        currentPool = distinctIds
        buildNonRepeatingQueue(genre.id, distinctIds)
        playNextUnplayed()
    }

    private fun buildNonRepeatingQueue(genreId: String, pool: List<String>) {
        val alreadyPlayed = repository.getPlayedIds(genreId)
        var candidates = pool.filterNot { it in alreadyPlayed }
        if (candidates.isEmpty()) {
            // 整份歌單都聽過一輪了：自動清空歷史重新洗牌，達成無限不間斷
            repository.clearPlayedHistory(genreId)
            candidates = pool
        }
        unplayedQueue = ArrayDeque(candidates.shuffled())
    }

    fun playNextUnplayed() {
        val genre = _uiState.value.activeGenre ?: return
        // 若使用者剛按過「上一首」，先往歷史紀錄往前走
        if (historyCursor in 0 until sessionHistory.lastIndex) {
            historyCursor++
            val vid = sessionHistory[historyCursor]
            playVideoId(vid)
            return
        }

        if (unplayedQueue.isEmpty() && currentPool.isNotEmpty()) {
            repository.clearPlayedHistory(genre.id)
            unplayedQueue = ArrayDeque(currentPool.shuffled())
            _uiState.update { it.copy(statusHint = "「${genre.title}」已全數聽完，自動重新隨機洗牌") }
        }

        val nextId = unplayedQueue.removeFirstOrNull() ?: return
        repository.markVideoPlayed(genre.id, nextId)
        sessionHistory.add(nextId)
        historyCursor = sessionHistory.lastIndex
        playVideoId(nextId)
    }

    fun playPreviousInHistory() {
        if (historyCursor > 0) {
            historyCursor--
            val prevId = sessionHistory[historyCursor]
            playVideoId(prevId)
        } else {
            // 沒有更早紀錄時，直接換一首新的未播歌曲
            playNextUnplayed()
        }
    }

    private fun playVideoId(videoId: String) {
        _uiState.update {
            it.copy(
                currentVideoId = videoId,
                isBuffering = true,
                remainingUnplayed = unplayedQueue.size,
                totalInPlaylist = currentPool.size
            )
        }
        playerView?.playVideoById(videoId)
    }

    fun togglePlayPause() {
        val state = _uiState.value
        if (state.activeGenre == null) {
            state.genres.firstOrNull()?.let { selectGenre(it) }
            return
        }
        if (state.isPlaying) {
            playerView?.pause()
        } else {
            playerView?.play()
        }
    }

    fun toggleVideoPreview() {
        val next = !_uiState.value.showVideoPreview
        repository.setShowVideoPreview(next)
        _uiState.update { it.copy(showVideoPreview = next) }
    }

    fun resetActiveGenreHistory() {
        val genre = _uiState.value.activeGenre ?: return
        repository.clearPlayedHistory(genre.id)
        if (currentPool.isNotEmpty()) {
            unplayedQueue = ArrayDeque(currentPool.shuffled())
        }
        _uiState.update {
            it.copy(
                remainingUnplayed = unplayedQueue.size,
                statusHint = "已重置「${genre.title}」的已播紀錄並重新洗牌"
            )
        }
    }

    fun addCustomPlaylist(title: String, rawUrlOrId: String): Boolean {
        val added = repository.addCustomPlaylist(title, rawUrlOrId) ?: return false
        _uiState.update {
            it.copy(
                genres = repository.getAllGenres(),
                statusHint = "已新增自訂歌單：${added.title}"
            )
        }
        selectGenre(added)
        return true
    }

    fun removeCustomPlaylist(genreId: String) {
        repository.removeCustomPlaylist(genreId)
        _uiState.update {
            it.copy(
                genres = repository.getAllGenres(),
                statusHint = "已刪除自訂歌單"
            )
        }
    }

    override fun onVideoStarted(videoId: String, title: String) {
        consecutiveErrors = 0
        val cleanTitle = title.ifBlank { "YouTube 音樂播放中" }
        val genreTitle = _uiState.value.activeGenre?.title ?: "YouTube 音樂"
        _uiState.update {
            it.copy(
                currentVideoId = videoId,
                currentTitle = cleanTitle,
                isPlaying = true,
                isBuffering = false,
                remainingUnplayed = unplayedQueue.size,
                totalInPlaylist = currentPool.size,
                statusHint = "不重複連續播放中 · 剩餘未播 ${unplayedQueue.size} / ${currentPool.size} 首"
            )
        }
        MusicKeepAliveService.updateState(getApplication(), cleanTitle, genreTitle, true)
    }

    override fun onStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying, isBuffering = isBuffering) }
        val state = _uiState.value
        if (state.activeGenre != null) {
            MusicKeepAliveService.updateState(
                getApplication(),
                state.currentTitle.ifBlank { state.activeGenre.title },
                state.activeGenre.title,
                isPlaying
            )
        }
    }

    override fun onVideoEnded() {
        // 一首歌播完，立刻無縫切下一首未播過的歌曲！
        playNextUnplayed()
    }

    override fun onVideoError(errorCode: Int) {
        // 若單首影片被版權限制嵌入（Error 101/150）或下架，自動跳下一首不中斷
        consecutiveErrors++
        if (consecutiveErrors <= 6 && (unplayedQueue.isNotEmpty() || currentPool.isNotEmpty())) {
            _uiState.update { it.copy(statusHint = "略過受限影片（代碼 $errorCode），自動切換下一首…") }
            playNextUnplayed()
        } else {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    statusHint = "連續多首無法播放，請點選其他音樂類型或檢查車機網路"
                )
            }
        }
    }
}
