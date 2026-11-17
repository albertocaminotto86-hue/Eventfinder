package com.lockplayer

import java.util.concurrent.ConcurrentHashMap

/** In-memory map from a queue item's id to the resolved stream, shared by the service and player. */
object TrackStore {
    private val tracks = ConcurrentHashMap<String, ResolvedTrack>()
    fun put(id: String, track: ResolvedTrack) { tracks[id] = track }
    fun get(id: String): ResolvedTrack? = tracks[id]
    fun remove(id: String) { tracks.remove(id) }
}
