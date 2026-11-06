package com.ghostmago.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MeshRoom(
    val id: String,
    val name: String,
    val password: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Commander multi-room registry. Creating a mesh group as host
 * registers here; dashboard can enable/disable and activate a room.
 */
object RoomStore {
    fun list(): List<MeshRoom> {
        return try {
            val arr = JSONArray(AppState.roomsJson.ifBlank { "[]" })
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MeshRoom(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    password = o.optString("password", ""),
                    enabled = o.optBoolean("enabled", true),
                    createdAt = o.optLong("createdAt", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(context: Context, rooms: List<MeshRoom>) {
        val arr = JSONArray()
        rooms.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("password", r.password)
                put("enabled", r.enabled)
                put("createdAt", r.createdAt)
            })
        }
        AppState.roomsJson = arr.toString()
        AppState.save(context)
    }

    fun add(context: Context, name: String, password: String): MeshRoom {
        val id = "r" + (System.currentTimeMillis() % 1_000_000)
        return addWithId(context, id, name.ifBlank { "اتاق $id" }, password)
    }

    fun addWithId(context: Context, id: String, name: String, password: String): MeshRoom {
        val existing = list().firstOrNull { it.id == id }
        if (existing != null) {
            // update name/password keep enabled
            val updated = existing.copy(
                name = name.ifBlank { existing.name },
                password = password.ifBlank { existing.password }
            )
            saveAll(context, list().map { if (it.id == id) updated else it })
            return updated
        }
        val room = MeshRoom(id, name.ifBlank { "اتاق $id" }, password, true)
        val rooms = list().toMutableList()
        rooms.add(0, room)
        saveAll(context, rooms)
        AppState.groupId = room.id
        AppState.save(context)
        try {
            AuditLog.append(context, "ROOM_CREATE", "${room.name}/${room.id}")
        } catch (_: Exception) {
        }
        return room
    }

    /** Register current mesh group so it appears on commander dashboard. */
    fun ensureFromActiveMesh(context: Context, groupId: String, password: String, displayName: String = "") {
        if (groupId.isBlank() || password.length < 4) return
        val name = displayName.ifBlank { "گروه $groupId" }
        addWithId(context, groupId, name, password)
    }

    fun select(context: Context, room: MeshRoom) {
        if (!room.enabled) return
        AppState.groupId = room.id
        AppState.lastGroupId = room.id
        AppState.lastGroupPassword = room.password
        AppState.pendingMeshPassword = room.password
        AppState.pendingMeshAutoJoin = false
        AppState.save(context)
        try {
            AuditLog.append(context, "ROOM_SELECT", room.id)
        } catch (_: Exception) {
        }
    }

    /** Activate room credentials for mesh screen (user still opens mesh and connects). */
    fun activate(context: Context, room: MeshRoom): Boolean {
        if (!room.enabled) return false
        select(context, room)
        return true
    }

    fun setEnabled(context: Context, roomId: String, enabled: Boolean) {
        val rooms = list().map {
            if (it.id == roomId) it.copy(enabled = enabled) else it
        }
        saveAll(context, rooms)
        // If disabling the active group, clear auto credentials softly
        if (!enabled && AppState.groupId == roomId) {
            AppState.pendingMeshAutoJoin = false
            AppState.save(context)
        }
        try {
            AuditLog.append(context, if (enabled) "ROOM_ON" else "ROOM_OFF", roomId)
        } catch (_: Exception) {
        }
    }

    fun remove(context: Context, roomId: String) {
        saveAll(context, list().filter { it.id != roomId })
        try {
            AuditLog.append(context, "ROOM_DEL", roomId)
        } catch (_: Exception) {
        }
    }
}
