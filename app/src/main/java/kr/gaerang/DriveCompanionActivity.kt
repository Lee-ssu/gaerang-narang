package kr.gaerang

import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class DriveCompanionState(
    val dogName: String,
    val coat: Coat,
    val destination: String,
    val otherDogs: Int?,
    val friendsDemo: Boolean,
    val parked: Boolean,
    val driving: Boolean,
    val labelLimit: Int?,
    val darkMode: Boolean,
    val parkedDemo: JSONObject?
)

/** The split window follows the trip owned by MainActivity in the same task. */
internal val driveCompanionState = MutableStateFlow<DriveCompanionState?>(null)

class DriveCompanionActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var destination: TextView
    private lateinit var otherDogs: TextView
    private lateinit var artwork: RunningDogView
    private var demoFrontend: ZipFrontend? = null
    private var observer: Job? = null
    private var startedDriving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startedDriving = savedInstanceState?.getBoolean("startedDriving") ?: false
    }

    private fun showParkedDemo(values: JSONObject, darkMode: Boolean) {
        val frontend = demoFrontend ?: ZipFrontend(this) { _, _ -> }.also {
            demoFrontend = it
            setContentView(it.view)
        }
        frontend.present(if (darkMode) "P_Drive_Night" else "P_Drive", portrait = true)
        frontend.update(values)
    }

    private fun showDriveCard(trip: DriveCompanionState) {
        val ui = RoundUi(this, trip.darkMode, trip.coat)
        val card = ui.panel(ui.tint, 24).apply { gravity = Gravity.CENTER_HORIZONTAL }
        status = ui.text("", 20, true).apply { gravity = Gravity.CENTER }
        destination = ui.text("", 18, color = ui.muted).apply {
            gravity = Gravity.CENTER
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
        }
        otherDogs = ui.text("", 17, color = ui.muted).apply { gravity = Gravity.CENTER }
        // Keep motion inside the small mascot tile, with a fixed background and no autoplay media.
        artwork = RunningDogView(this, trip.coat, trip.dogName, cycleDurationMillis = 1_850)
        card.addView(status)
        card.addView(artwork, LinearLayout.LayoutParams(ui.dp(148), ui.dp(105)))
        card.addView(destination, LinearLayout.LayoutParams(-1, -2))
        card.addView(otherDogs)
        val margin = ui.dp(20)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(ui.paper)
            addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
                setMargins(margin, margin, margin, margin)
            })
        })
    }

    override fun onStart() {
        super.onStart()
        observer = CoroutineScope(Dispatchers.Main.immediate).launch {
            driveCompanionState.collect(::display)
        }
    }

    override fun onStop() {
        observer?.cancel()
        observer = null
        demoFrontend?.hide()
        if (::artwork.isInitialized) artwork.updateMotion(false)
        super.onStop()
    }

    override fun onDestroy() {
        demoFrontend?.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("startedDriving", startedDriving)
        super.onSaveInstanceState(outState)
    }

    private fun display(trip: DriveCompanionState?) {
        if (trip == null) {
            finish()
            return
        }
        if (trip.driving) startedDriving = true
        if (startedDriving && trip.parked) {
            setResult(RESULT_OK)
            finish()
            return
        }
        val parkedDemo = trip.parkedDemo
        if (trip.parked && parkedDemo != null) {
            showParkedDemo(parkedDemo, trip.darkMode)
            return
        }
        if (demoFrontend != null || !::artwork.isInitialized) {
            demoFrontend?.close()
            demoFrontend = null
            showDriveCard(trip)
        }
        fun visibleLabel(value: String): String = trip.labelLimit?.let { value.take(it.coerceAtLeast(0)) } ?: value
        status.text = visibleLabel("${trip.dogName}와 동행 중")
        destination.text = visibleLabel(trip.destination)
        otherDogs.visibility = if ((trip.otherDogs ?: 0) > 0) View.VISIBLE else View.GONE
        otherDogs.text = visibleLabel(if (trip.friendsDemo) "시연 · 함께 가는 반려견 ${trip.otherDogs}마리"
            else "함께 가는 반려견 ${trip.otherDogs}마리")
        artwork.updateMotion(trip.driving)
    }
}
