package blbl.cat3399.feature.player

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.BaseActivity
import blbl.cat3399.core.ui.Immersive
import blbl.cat3399.databinding.ActivityLocalPlayerBinding

/** 离线下载视频的应用内播放器。 */
class LocalVideoPlayerActivity : BaseActivity() {
    private lateinit var binding: ActivityLocalPlayerBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)
        val uri = intent.getStringExtra(EXTRA_URI)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        setTitle(title)
        if (uri == null) {
            finish()
            return
        }
        val exo =
            ExoPlayer.Builder(this).build().also {
                it.setMediaItem(MediaItem.fromUri(uri))
                it.prepare()
                it.playWhenReady = true
                it.addListener(
                    object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                    },
                )
            }
        player = exo
        binding.playerView.player = exo
        binding.playerView.setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "local_video_uri"
        const val EXTRA_TITLE = "local_video_title"
    }
}
