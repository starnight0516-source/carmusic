package tw.carmusic.yt.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import tw.carmusic.yt.model.MusicGenre
import tw.carmusic.yt.player.YouTubeCarPlayerView

private val BgDark = Color(0xFF0B0F17)
private val PanelDark = Color(0xFF151C28)
private val CardDark = Color(0xFF1D2636)
private val AccentCyan = Color(0xFF00E5FF)
private val TextPrimary = Color(0xFFF3F6FA)
private val TextSecondary = Color(0xFF94A3B8)
private val LiveGreen = Color(0xFF22C55E)

@Composable
fun CarMusicScreen(
    uiState: CarMusicUiState,
    playerView: YouTubeCarPlayerView,
    onSelectGenre: (MusicGenre) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleVideoPreview: () -> Unit,
    onResetHistory: () -> Unit,
    onAddCustomPlaylist: (String, String) -> Boolean,
    onDeleteCustomPlaylist: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    var showAddDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = BgDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 底部預留 54dp 避開 Foxtron CAVIRA / 路瑞寶 6350 底部常駐工具列
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (isLandscape) 16.dp else 54.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 頂部標題與快捷工具列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "車機音樂盒",
                        color = TextPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "YouTube 連播",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleVideoPreview) {
                        Icon(
                            imageVector = if (uiState.showVideoPreview) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "切換畫面預覽",
                            tint = if (uiState.showVideoPreview) AccentCyan else TextSecondary
                        )
                    }
                    IconButton(onClick = onResetHistory) {
                        Icon(Icons.Default.RestartAlt, "重置已播紀錄", tint = TextSecondary)
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "新增 YouTube 歌單", tint = AccentCyan)
                    }
                }
            }

            // 2. YouTube 播放容器（可收合為 1dp 隱形背景音訊模式，確保純聽歌不干擾行車視線）
            Box(
                modifier = if (uiState.showVideoPreview) {
                    Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 160.dp else 185.dp)
                        .clip(RoundedCornerShape(16.dp))
                } else {
                    Modifier
                        .size(1.dp)
                }
            ) {
                AndroidView(
                    factory = { playerView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. 中央音樂類型大按鈕網格（一鍵即播、自動連續不重複）
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isLandscape) 4 else 2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(uiState.genres, key = { it.id }) { genre ->
                    val isActive = uiState.activeGenre?.id == genre.id
                    GenreCard(
                        genre = genre,
                        isActive = isActive,
                        remaining = if (isActive) uiState.remainingUnplayed else null,
                        total = if (isActive) uiState.totalInPlaylist else null,
                        onClick = { onSelectGenre(genre) },
                        onDelete = if (genre.isCustom) {
                            { onDeleteCustomPlaylist(genre.id) }
                        } else null
                    )
                }
            }

            // 4. 底部駕駛順手大按鈕播放控制座艙
            BottomPlayerDeck(
                uiState = uiState,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious
            )
        }
    }

    if (showAddDialog) {
        AddYouTubePlaylistDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url ->
                val ok = onAddCustomPlaylist(title, url)
                if (ok) showAddDialog = false
                ok
            }
        )
    }
}

@Composable
private fun GenreCard(
    genre: MusicGenre,
    isActive: Boolean,
    remaining: Int?,
    total: Int?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val accent = Color(genre.accentHex)
    Surface(
        color = if (isActive) accent.copy(alpha = 0.18f) else CardDark,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (isActive) 2.5.dp else 1.dp,
            color = if (isActive) accent else Color(0xFF2B3648)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = genre.title,
                        color = if (isActive) accent else TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (isActive && total != null && total > 0) {
                        "未播 ${remaining ?: 0} / 共 $total 首"
                    } else {
                        genre.subtitle
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "刪除歌單",
                        tint = Color(0xFFF87171)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomPlayerDeck(
    uiState: CarMusicUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Surface(
        color = PanelDark,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2B3B4E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pillColor = when {
                        uiState.isPlaying -> LiveGreen
                        uiState.isBuffering -> AccentCyan
                        else -> TextSecondary
                    }
                    val pillText = when {
                        uiState.isPlaying -> "● 連續播放中"
                        uiState.isBuffering -> "● 載入中…"
                        uiState.activeGenre != null -> "● 已暫停"
                        else -> "● 待命中"
                    }
                    Surface(color = Color(0xFF202A3A), shape = RoundedCornerShape(50)) {
                        Text(
                            text = pillText,
                            color = pillColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (uiState.activeGenre != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.activeGenre.title,
                            color = Color(uiState.activeGenre.accentHex),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = uiState.currentTitle.ifBlank { "請點選上方音樂類型開始聽歌" },
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = uiState.statusHint,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onPrevious,
                    shape = RoundedCornerShape(18.dp),
                    color = CardDark,
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SkipPrevious, "上一首", tint = TextPrimary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("上一首", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = RoundedCornerShape(20.dp),
                    color = AccentCyan,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            uiState.isBuffering -> CircularProgressIndicator(
                                color = BgDark,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            uiState.isPlaying -> Icon(
                                Icons.Default.Pause,
                                "暫停",
                                tint = BgDark,
                                modifier = Modifier.size(42.dp)
                            )
                            else -> Icon(
                                Icons.Default.PlayArrow,
                                "播放",
                                tint = BgDark,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }

                Surface(
                    onClick = onNext,
                    shape = RoundedCornerShape(18.dp),
                    color = CardDark,
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("下一首", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.SkipNext, "下一首", tint = TextPrimary, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddYouTubePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Boolean
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelDark,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("新增 YouTube 歌單類型", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("貼上任意 YouTube 或 YouTube Music 公開播放清單網址（含 list=）。", fontSize = 13.sp)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("顯示名稱（例如：我的開車歌單）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorMsg = null
                    },
                    label = { Text("YouTube 歌單網址或 list ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = Color(0xFFF87171), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ok = onConfirm(title, url)
                    if (!ok) errorMsg = "無法辨識歌單 ID，請確認網址包含 list= 參數"
                },
                enabled = title.isNotBlank() && url.isNotBlank()
            ) {
                Text("新增並播放")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
