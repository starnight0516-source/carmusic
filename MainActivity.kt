package tw.carmusic.yt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import tw.carmusic.yt.player.YouTubeCarPlayerView
import tw.carmusic.yt.ui.CarMusicScreen
import tw.carmusic.yt.ui.CarMusicViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CarMusicViewModel by viewModels()
    private lateinit var playerView: YouTubeCarPlayerView

    private val requestNotificationPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 行車前景常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        playerView = YouTubeCarPlayerView(this, viewModel)
        viewModel.attachPlayer(playerView)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            MaterialTheme(colorScheme = darkColorScheme()) {
                CarMusicScreen(
                    uiState = uiState,
                    playerView = playerView,
                    onSelectGenre = viewModel::selectGenre,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNextUnplayed,
                    onPrevious = viewModel::playPreviousInHistory,
                    onToggleVideoPreview = viewModel::toggleVideoPreview,
                    onResetHistory = viewModel::resetActiveGenreHistory,
                    onAddCustomPlaylist = viewModel::addCustomPlaylist,
                    onDeleteCustomPlaylist = viewModel::removeCustomPlaylist
                )
            }
        }
    }

    override fun onDestroy() {
        playerView.destroy()
        super.onDestroy()
    }
}
