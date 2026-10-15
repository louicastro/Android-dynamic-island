package com.dynamicisland.android.service

import com.dynamicisland.android.model.IslandEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton event bus connecting notification, media, call, and battery trackers
 * to the IslandOverlayService.
 */
object IslandEventBus {

    private val _events = MutableSharedFlow<IslandEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<IslandEvent> = _events.asSharedFlow()

    fun emit(event: IslandEvent) {
        _events.tryEmit(event)
    }
}
