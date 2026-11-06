package com.ghostmago.app.data

/**
 * Operational profiles — one-tap readiness modes.
 */
enum class OpProfile(val id: String, val fa: String, val desc: String) {
    URBAN("urban", "شهری", "تأخیر کم، cover متوسط، اعلان روشن"),
    FIELD("field", "میدانی", "تأخیر تصادفی، cover بالا، radio-aware"),
    SILENT("silent", "رادیو سکوت", "بدون beacon، فقط دریافت"),
    STANDBY("standby", "آماده به کار", "حداقل ترافیک، dead-man فعال");

    fun applyTo(state: AppState) {
        when (this) {
            URBAN -> {
                state.randomSendDelay = true
                state.coverTraffic = true
                state.coverIntervalSec = 20
                state.radioSilence = false
            }
            FIELD -> {
                state.randomSendDelay = true
                state.coverTraffic = true
                state.coverIntervalSec = 8
                state.radioSilence = false
            }
            SILENT -> {
                state.radioSilence = true
                state.coverTraffic = false
                state.randomSendDelay = true
            }
            STANDBY -> {
                state.radioSilence = true
                state.coverTraffic = false
                state.randomSendDelay = false
                state.coverIntervalSec = 60
            }
        }
    }

    companion object {
        fun from(id: String) = entries.find { it.id == id } ?: URBAN
    }
}
