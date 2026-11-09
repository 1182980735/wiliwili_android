package blbl.cat3399.feature.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.app.NotificationCompat.MediaStyle
import blbl.cat3399.R
import blbl.cat3399.core.log.AppLog

/**
 * 轻量前台播放服务：负责在后台/PiP 时保持通知和媒体控制入口。
 * 实际播放器仍由 PlayerActivity 持有；播放控制通过 [PlaybackControlBridge] 回调。
 */
class PlaybackNotificationService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                PlaybackControlBridge.controller?.stopPlayback()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE -> {
                PlaybackControlBridge.controller?.togglePlayback()
                updateNotification(intent)
                return START_NOT_STICKY
            }

            ACTION_UPDATE -> {
                updateNotification(intent)
                return START_NOT_STICKY
            }

            else -> {
                startForegroundCompat(buildNotification(intent))
                return START_NOT_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(intent: Intent) {
        val notification = buildNotification(intent)
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager?.notify(NOTIFICATION_ID, notification) }
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching { startForeground(NOTIFICATION_ID, notification) }
            .onFailure { AppLog.w(TAG, "startForeground failed", it) }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(intent: Intent?): Notification {
        val title = intent?.getStringExtra(EXTRA_TITLE)?.trim().orEmpty().ifBlank { "wiliwili_A" }
        val artist = intent?.getStringExtra(EXTRA_ARTIST)?.trim().orEmpty().ifBlank { "视频播放" }
        val playing = intent?.getBooleanExtra(EXTRA_PLAYING, true) ?: true
        @Suppress("DEPRECATION")
        val token = intent?.getParcelableExtra<MediaSessionCompat.Token>(EXTRA_SESSION_TOKEN)

        val contentIntent =
            PendingIntent.getActivity(
                this,
                10,
                Intent(this, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val toggleIntent =
            PendingIntent.getService(
                this,
                11,
                Intent(this, PlaybackNotificationService::class.java).setAction(ACTION_TOGGLE)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_ARTIST, artist)
                    .putExtra(EXTRA_PLAYING, !playing),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                12,
                Intent(this, PlaybackNotificationService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(playing)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                    if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                    if (playing) "暂停" else "播放",
                    toggleIntent,
                )
                .addAction(R.drawable.ic_player_close, "关闭", stopIntent)

        if (token != null) {
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1),
            )
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "视频后台播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "后台播放控制"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val TAG = "PlaybackNotification"
        private const val CHANNEL_ID = "wiliwili_playback"
        private const val NOTIFICATION_ID = 41021
        private const val ACTION_UPDATE = "blbl.playback.UPDATE"
        private const val ACTION_TOGGLE = "blbl.playback.TOGGLE"
        private const val ACTION_STOP = "blbl.playback.STOP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_PLAYING = "playing"
        private const val EXTRA_SESSION_TOKEN = "session_token"

        fun start(
            context: Context,
            title: String,
            artist: String,
            playing: Boolean,
            sessionToken: MediaSessionCompat.Token?,
        ) {
            val intent =
                Intent(context, PlaybackNotificationService::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_ARTIST, artist)
                    .putExtra(EXTRA_PLAYING, playing)
                    .putExtra(EXTRA_SESSION_TOKEN, sessionToken)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { AppLog.w(TAG, "start failed", it) }
        }

        fun update(
            context: Context,
            title: String,
            artist: String,
            playing: Boolean,
            sessionToken: MediaSessionCompat.Token?,
        ) {
            val intent =
                Intent(context, PlaybackNotificationService::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_ARTIST, artist)
                    .putExtra(EXTRA_PLAYING, playing)
                    .putExtra(EXTRA_SESSION_TOKEN, sessionToken)
            runCatching { context.startService(intent) }
                .onFailure { AppLog.w(TAG, "update failed", it) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, PlaybackNotificationService::class.java).setAction(ACTION_STOP),
                )
            }.onFailure { AppLog.w(TAG, "stop failed", it) }
        }
    }
}

/** PlayerActivity 与通知服务之间的播放控制桥。 */
object PlaybackControlBridge {
    interface Controller {
        fun togglePlayback()
        fun stopPlayback()
    }

    @Volatile
    var controller: Controller? = null
}
