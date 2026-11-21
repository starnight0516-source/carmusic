package tw.carmusic.yt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import tw.carmusic.yt.MainActivity
import tw.carmusic.yt.R

object CarMediaBridge {
    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
}

/**
 * 專為路瑞寶 6350 等安卓車機設計的前景保活與方向盤按鍵服務：
 * - 切到導航或背景時不被系統回收
 * - 透過 MediaSessionCompat 接收車機方向盤「上一首／下一首／播放暫停」按鍵
 */
class MusicKeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "car_music_yt_playback"
        private const val NOTIFICATION_ID = 2026
        const val ACTION_UPDATE = "tw.carmusic.yt.ACTION_UPDATE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_GENRE = "extra_genre"
        const val EXTRA_PLAYING = "extra_playing"

        fun updateState(context: Context, title: String, genre: String, isPlaying: Boolean) {
            val intent = Intent(context, MusicKeepAliveService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_GENRE, genre)
                putExtra(EXTRA_PLAYING, isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CarMusicYT::PlaybackLock").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }

        mediaSession = MediaSessionCompat(this, "CarMusicYTSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { CarMediaBridge.onPlayPause?.invoke() }
                override fun onPause() { CarMediaBridge.onPlayPause?.invoke() }
                override fun onSkipToNext() { CarMediaBridge.onNext?.invoke() }
                override fun onSkipToPrevious() { CarMediaBridge.onPrevious?.invoke() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE)?.ifBlank { "車機音樂盒" } ?: "車機音樂盒"
        val genre = intent?.getStringExtra(EXTRA_GENRE)?.ifBlank { "YouTube 連續播放" } ?: "YouTube 連續播放"
        val isPlaying = intent?.getBooleanExtra(EXTRA_PLAYING, true) ?: true

        updateMediaSession(title, genre, isPlaying)
        startForeground(NOTIFICATION_ID, buildNotification(title, genre, isPlaying))
        return START_STICKY
    }

    private fun updateMediaSession(title: String, genre: String, isPlaying: Boolean) {
        val session = mediaSession ?: return
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, genre)
                .build()
        )
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    private fun buildNotification(title: String, genre: String, isPlaying: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("$genre · ${if (isPlaying) "連續播放中" else "已暫停"}")
            .setContentIntent(pi)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyle().setMediaSession(mediaSession?.sessionToken))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "車機音樂背景播放",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        mediaSession?.run {
            isActive = false
            release()
        }
        super.onDestroy()
    }
}
