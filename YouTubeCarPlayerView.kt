package tw.carmusic.yt.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray

interface YouTubePlayerCallback {
    fun onPlayerReady()
    fun onPlaylistLoaded(videoIds: List<String>)
    fun onVideoStarted(videoId: String, title: String)
    fun onStateChanged(isPlaying: Boolean, isBuffering: Boolean)
    fun onVideoEnded()
    fun onVideoError(errorCode: Int)
}

/**
 * 覆寫 onWindowVisibilityChanged 讓車機切去導航或背景時，YouTube 音訊不中斷。
 */
@SuppressLint("SetJavaScriptEnabled")
class YouTubeCarPlayerView(
    context: Context,
    private val callback: YouTubePlayerCallback
) : WebView(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPlayerReady = false
    private var pendingPlaylistId: String? = null

    init {
        setBackgroundColor(Color.BLACK)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13; SM6350) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        }
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
        addJavascriptInterface(JsBridge(), "AndroidBridge")
        loadDataWithBaseURL(
            "https://www.youtube.com",
            PLAYER_HTML,
            "text/html",
            "UTF-8",
            null
        )
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        // 永遠維持 View.VISIBLE，避免路瑞寶 6350 切換至導航畫面時 WebView 自動暫停 YouTube 播放
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    fun cuePlaylistForIds(playlistId: String) {
        if (!isPlayerReady) {
            pendingPlaylistId = playlistId
            return
        }
        val safeId = playlistId.replace("'", "")
        mainHandler.post {
            evaluateJavascript("window.cueGenrePlaylist('$safeId');", null)
        }
    }

    fun playVideoById(videoId: String) {
        val safeId = videoId.replace("'", "")
        mainHandler.post {
            evaluateJavascript("window.playSingleVideo('$safeId');", null)
        }
    }

    fun play() {
        mainHandler.post { evaluateJavascript("window.ytPlay();", null) }
    }

    fun pause() {
        mainHandler.post { evaluateJavascript("window.ytPause();", null) }
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onReady() {
            mainHandler.post {
                isPlayerReady = true
                callback.onPlayerReady()
                pendingPlaylistId?.let { pid ->
                    pendingPlaylistId = null
                    cuePlaylistForIds(pid)
                }
            }
        }

        @JavascriptInterface
        fun onPlaylistIds(jsonIds: String) {
            val list = runCatching {
                val arr = JSONArray(jsonIds)
                (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i).takeIf { it.isNotBlank() }
                }
            }.getOrDefault(emptyList())
            mainHandler.post { callback.onPlaylistLoaded(list) }
        }

        @JavascriptInterface
        fun onTrackInfo(videoId: String, title: String) {
            mainHandler.post { callback.onVideoStarted(videoId, title) }
        }

        @JavascriptInterface
        fun onState(stateCode: Int) {
            mainHandler.post {
                when (stateCode) {
                    1 -> callback.onStateChanged(isPlaying = true, isBuffering = false)
                    3 -> callback.onStateChanged(isPlaying = false, isBuffering = true)
                    2 -> callback.onStateChanged(isPlaying = false, isBuffering = false)
                    0 -> callback.onVideoEnded()
                }
            }
        }

        @JavascriptInterface
        fun onError(code: Int) {
            mainHandler.post { callback.onVideoError(code) }
        }
    }

    companion object {
        private val PLAYER_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
              <style>
                html, body, #player { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
              </style>
            </head>
            <body>
              <div id="player"></div>
              <script src="https://www.youtube.com/iframe_api"></script>
              <script>
                var player = null;
                var waitingForPlaylist = false;

                function onYouTubeIframeAPIReady() {
                  player = new YT.Player('player', {
                    width: '100%',
                    height: '100%',
                    playerVars: {
                      autoplay: 1,
                      controls: 0,
                      rel: 0,
                      playsinline: 1,
                      modestbranding: 1,
                      fs: 0,
                      origin: 'https://www.youtube.com'
                    },
                    events: {
                      'onReady': function() {
                        AndroidBridge.onReady();
                      },
                      'onStateChange': function(e) {
                        if (waitingForPlaylist && (e.data === YT.PlayerState.CUED || e.data === YT.PlayerState.PLAYING || e.data === YT.PlayerState.BUFFERING)) {
                          var ids = player.getPlaylist();
                          if (ids && ids.length > 0) {
                            waitingForPlaylist = false;
                            AndroidBridge.onPlaylistIds(JSON.stringify(ids));
                            return;
                          }
                        }
                        if (e.data === YT.PlayerState.PLAYING) {
                          var data = player.getVideoData() || {};
                          AndroidBridge.onTrackInfo(data.video_id || '', data.title || '');
                        }
                        AndroidBridge.onState(e.data);
                      },
                      'onError': function(e) {
                        AndroidBridge.onError(e.data);
                      }
                    }
                  });
                }

                window.cueGenrePlaylist = function(playlistId) {
                  if (!player) return;
                  waitingForPlaylist = true;
                  player.cuePlaylist({ listType: 'playlist', list: playlistId, index: 0 });
                  // 保險輪詢：部分裝置 CUED 事件較慢時主動讀取清單
                  setTimeout(function() {
                    if (waitingForPlaylist && player && player.getPlaylist) {
                      var ids = player.getPlaylist();
                      if (ids && ids.length > 0) {
                        waitingForPlaylist = false;
                        AndroidBridge.onPlaylistIds(JSON.stringify(ids));
                      }
                    }
                  }, 1800);
                };

                window.playSingleVideo = function(videoId) {
                  if (!player) return;
                  waitingForPlaylist = false;
                  player.loadVideoById({ videoId: videoId, startSeconds: 0 });
                };

                window.ytPlay = function() { if (player) player.playVideo(); };
                window.ytPause = function() { if (player) player.pauseVideo(); };
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
