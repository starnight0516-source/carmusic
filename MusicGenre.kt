package tw.carmusic.yt.model

data class MusicGenre(
    val id: String,
    val title: String,
    val subtitle: String,
    val playlistId: String,
    val accentHex: Long,
    val isCustom: Boolean = false
)
