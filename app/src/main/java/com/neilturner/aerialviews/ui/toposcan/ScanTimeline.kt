package com.neilturner.aerialviews.ui.toposcan

data class ScanSettings(
    val scan: Double = 32.0,
    val freezeDelay: Double = 4.0,
    val hold: Double = 5.0,
    val field: Double = 8.0,
    val bandHeight: Float = 3f,
    val width: Int = 3840,
    val direction: String = "alternate",
)

enum class ScanPhase { FIELD, REVEAL, HOLD, OUT, WAIT }

/** A consumed video frame owns one scan step; stalled time is never caught up. */
class ScanTimeline(
    val settings: ScanSettings,
) {
    var phase = ScanPhase.WAIT
        private set
    var time = 0.0
        private set
    var video = false
        private set
    var direction = 1f
        private set
    var paused = false
    private var fps = 30.0
    private var count = 0
    private var timestamp = Long.MIN_VALUE

    val progress: Float
        get() = (time / duration()).coerceIn(0.0, 1.0).toFloat()
    val reveal: Float
        get() = (time / settings.scan).coerceIn(0.0, 1.0).toFloat()
    val freeze: Float
        get() = ((time - if (video) settings.freezeDelay else 0.0) / settings.scan).coerceIn(0.0, 1.0).toFloat()

    fun begin(
        isVideo: Boolean,
        frameRate: Double,
    ) {
        video = isVideo
        fps = frameRate.takeIf { it.isFinite() && it in 1.0..240.0 } ?: 30.0
        direction =
            when (settings.direction) {
                "right" -> -1f
                "left" -> 1f
                else -> if (count % 2 == 0) 1f else -1f
            }
        count++
        phase = ScanPhase.FIELD
        time = 0.0
        timestamp = Long.MIN_VALUE
        paused = false
    }

    fun advance(
        wallSeconds: Double,
        frameTimestamp: Long? = null,
    ) {
        val fresh = frameTimestamp != null && frameTimestamp != timestamp
        if (frameTimestamp != null) timestamp = frameTimestamp
        if (paused || phase == ScanPhase.WAIT) return
        val step =
            if (phase == ScanPhase.REVEAL && video) {
                if (fresh) 1.0 / fps else 0.0
            } else {
                wallSeconds.coerceIn(0.0, 0.1)
            }
        time = minOf(duration(), time + step)
        // Keep the endpoint for one render before moving into the next phase.
    }

    fun finishFrame() {
        if (paused || phase == ScanPhase.WAIT || time < duration()) return
        phase = ScanPhase.entries[phase.ordinal + 1]
        time = 0.0
    }

    private fun duration(): Double =
        when (phase) {
            ScanPhase.FIELD -> settings.field
            ScanPhase.REVEAL -> settings.scan + if (video) settings.freezeDelay else 0.0
            ScanPhase.HOLD -> settings.hold
            ScanPhase.OUT -> settings.scan
            ScanPhase.WAIT -> Double.POSITIVE_INFINITY
        }
}
