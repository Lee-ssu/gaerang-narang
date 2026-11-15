package kr.gaerang

/**
 * Counts time in a driving gear since the caregiver started the outing or confirmed a break.
 * Red lights do not reset it. Unknown gear and Park pause it.
 */
internal class RideClock {
    var active = false
        private set
    var breakCompleted = false
        private set
    var resting = false
        private set
    private var accumulated = 0L
    private var startedAt: Long? = null
    val counting get() = startedAt != null
    val canStartBreak get() = active && !counting && !resting && accumulated > 0

    fun start() {
        if (active) return
        active = true
        breakCompleted = false
        resting = false
    }

    fun startBreak() {
        if (canStartBreak) resting = true
    }

    fun cancelBreak() {
        resting = false
    }

    fun completeBreak() {
        if (!resting) return
        resting = false
        breakCompleted = true
        accumulated = 0
    }

    fun finish() {
        active = false
        breakCompleted = false
        resting = false
        accumulated = 0
        startedAt = null
    }

    fun update(drivingGear: Boolean, now: Long) {
        if (active && drivingGear) {
            resting = false
            if (startedAt == null) startedAt = now
        } else {
            startedAt?.let { accumulated += now - it }
            startedAt = null
        }
    }

    fun elapsed(now: Long): Long = accumulated + (startedAt?.let { now - it } ?: 0)
}
