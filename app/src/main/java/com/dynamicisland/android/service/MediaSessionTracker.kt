package com.dynamicisland.android.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.dynamicisland.android.model.IslandEvent
import com.dynamicisland.android.model.MediaInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitors active media sessions across audio and video apps.
 *
 * Implements:
 * - Proper cleanup and nullification on session disconnection/stop (Finding 9)
 * - Periodic progress ticker and monotonic elapsed estimation (Finding 10)
 * - Memory protection via clamped album art bitmaps (Finding 32)
 * - Deduplication of redundant playback events (Finding 33)
 */
class MediaSessionTracker(private val context: Context) {

    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private var activeController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressTickerJob: Job? = null

    private var lastEmittedMediaInfo: MediaInfo? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaInfo()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMediaInfo()
        }

        override fun onSessionDestroyed() {
            activeController = null
            stopProgressTicker()
            lastEmittedMediaInfo = null
            IslandEventBus.updateMedia(null)
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
            Log.w(TAG, "Notification listener permission required for media session access: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register active media sessions listener", e)
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
            stopProgressTicker()
            lastEmittedMediaInfo = null
            IslandEventBus.updateMedia(null)
        }
    }

    private fun updateMediaInfo() {
        val controller = activeController ?: run {
            stopProgressTicker()
            lastEmittedMediaInfo = null
            IslandEventBus.updateMedia(null)
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState

        if (metadata == null && playbackState == null) {
            stopProgressTicker()
            lastEmittedMediaInfo = null
            IslandEventBus.updateMedia(null)
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

        val rawArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val albumArt = downsampleAlbumArt(rawArt)

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L
        val speed = playbackState?.playbackSpeed ?: 1.0f

        val mediaInfo = MediaInfo(
            title = title,
            artist = artist,
            albumArt = albumArt,
            isPlaying = isPlaying,
            durationMs = duration,
            currentPositionMs = position,
            packageName = controller.packageName ?: "",
            playbackSpeed = speed,
            lastPositionUpdateTimestamp = SystemClock.elapsedRealtime()
        )

        // Deduplication: Avoid emitting identical state if track, state, and position haven't meaningfully changed
        val prev = lastEmittedMediaInfo
        if (prev != null &&
            prev.title == mediaInfo.title &&
            prev.artist == mediaInfo.artist &&
            prev.isPlaying == mediaInfo.isPlaying &&
            prev.durationMs == mediaInfo.durationMs &&
            Math.abs(prev.currentPositionMs - mediaInfo.currentPositionMs) < 500L
        ) {
            return
        }

        lastEmittedMediaInfo = mediaInfo
        IslandEventBus.updateMedia(mediaInfo)

        if (isPlaying) {
            startProgressTicker()
        } else {
            stopProgressTicker()
        }
    }

    private fun startProgressTicker() {
        if (progressTickerJob?.isActive == true) return
        progressTickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val controller = activeController ?: break
                val playbackState = controller.playbackState
                if (playbackState?.state != PlaybackState.STATE_PLAYING) break

                val prev = lastEmittedMediaInfo ?: break
                val updated = prev.copy(
                    currentPositionMs = playbackState.position,
                    lastPositionUpdateTimestamp = SystemClock.elapsedRealtime()
                )
                lastEmittedMediaInfo = updated
                IslandEventBus.updateMedia(updated)
            }
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = null
    }

    /**
     * Scales album art to a maximum of 256x256 pixels to prevent high memory consumption.
     */
    private fun downsampleAlbumArt(bitmap: Bitmap?): Bitmap? {
        bitmap ?: return null
        val maxDim = 256
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) {
            return bitmap
        }
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetWidth = if (ratio >= 1f) maxDim else (maxDim * ratio).toInt()
        val targetHeight = if (ratio >= 1f) (maxDim / ratio).toInt() else maxDim
        return try {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM downsampling album art", e)
            null
        }
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
        stopProgressTicker()
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        lastEmittedMediaInfo = null
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing active sessions listener: ${e.message}")
        }
        // Authoritatively clear persistent media state on listener disconnection
        IslandEventBus.updateMedia(null)
    }

    companion object {
        private const val TAG = "MediaSessionTracker"

        @Volatile
        private var instance: MediaSessionTracker? = null

        fun getInstance(context: Context): MediaSessionTracker {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionTracker(context.applicationContext).also { instance = it }
            }
        }
    }
}
