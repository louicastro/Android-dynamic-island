package com.dynamicisland.android.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.dynamicisland.android.model.IslandEvent
import com.dynamicisland.android.model.MediaInfo

class MediaSessionTracker(private val context: Context) {

    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private var activeController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaInfo()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMediaInfo()
        }

        override fun onSessionDestroyed() {
            activeController = null
            IslandEventBus.emit(IslandEvent.MediaUpdate(null))
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachToTopController(controllers)
        }

    fun start(notificationListenerComponent: ComponentName) {
        val manager = mediaSessionManager ?: return
        try {
            manager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                notificationListenerComponent,
                handler
            )
            val controllers = manager.getActiveSessions(notificationListenerComponent)
            attachToTopController(controllers)
        } catch (e: SecurityException) {
            // Notification Listener permission required
        }
    }

    private fun attachToTopController(controllers: List<MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)

        val playingController = controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers?.firstOrNull()

        activeController = playingController
        if (playingController != null) {
            playingController.registerCallback(controllerCallback, handler)
            updateMediaInfo()
        } else {
            IslandEventBus.emit(IslandEvent.MediaUpdate(null))
        }
    }

    private fun updateMediaInfo() {
        val controller = activeController ?: run {
            IslandEventBus.emit(IslandEvent.MediaUpdate(null))
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState

        if (metadata == null && playbackState == null) {
            IslandEventBus.emit(IslandEvent.MediaUpdate(null))
            return
        }

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Unknown Track"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: "Unknown Artist"

        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L

        val mediaInfo = MediaInfo(
            title = title,
            artist = artist,
            albumArt = albumArt,
            isPlaying = isPlaying,
            durationMs = duration,
            currentPositionMs = position,
            packageName = controller.packageName ?: ""
        )

        IslandEventBus.emit(IslandEvent.MediaUpdate(mediaInfo))
    }

    fun play() {
        activeController?.transportControls?.play()
    }

    fun pause() {
        activeController?.transportControls?.pause()
    }

    fun togglePlayPause() {
        val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun skipNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        activeController?.transportControls?.seekTo(positionMs)
    }

    fun stop() {
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        @Volatile
        private var instance: MediaSessionTracker? = null

        fun getInstance(context: Context): MediaSessionTracker {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionTracker(context.applicationContext).also { instance = it }
            }
        }
    }
}
