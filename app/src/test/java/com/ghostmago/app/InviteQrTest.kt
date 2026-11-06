package com.ghostmago.app

import com.ghostmago.app.mesh.InviteQr
import org.junit.Assert.*
import org.junit.Test

class InviteQrTest {
    @Test
    fun roundTrip() {
        val raw = InviteQr.buildPayload("g1", "password12", "cmd", "soldier", 600)
        val inv = InviteQr.parse(raw)
        assertNotNull(inv)
        assertEquals("g1", inv!!.groupId)
        assertEquals("password12", inv.groupPassword)
        assertEquals(600, inv.ttlSec)
    }

    @Test
    fun rejectsTampered() {
        val raw = InviteQr.buildPayload("g1", "password12", "cmd", ttlSec = 600)
        val bad = raw.dropLast(2) + "00"
        assertNull(InviteQr.parse(bad))
    }
}
