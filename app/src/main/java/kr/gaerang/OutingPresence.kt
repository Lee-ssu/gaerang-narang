package kr.gaerang

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

internal class OutingPresence(private val changed: () -> Unit) {
    private data class Membership(val placeId: String, val dogs: Int)
    private val endpoint = BuildConfig.OUTING_PRESENCE_URL.trimEnd('/')
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val token = UUID.randomUUID().toString().replace("-", "")
    private var membership: Membership? = null
    private var generation = 0
    private var closed = false
    val available get() = endpoint.isNotBlank()
    var otherDogs: Int? = null
        private set
    private val heartbeat = Runnable { publish() }

    fun update(placeId: String?, dogCount: Int, enabled: Boolean) {
        if (closed || !available) return
        val next = placeId?.takeIf { enabled && dogCount > 0 }?.let { Membership(it, dogCount.coerceAtMost(8)) }
        val before = membership
        if (before == next) return
        generation++
        membership = next
        otherDogs = null
        main.removeCallbacks(heartbeat)
        if (before != null && before.placeId != next?.placeId) {
            worker.execute { send("DELETE", before) }
        }
        if (next != null) publish()
    }

    private fun publish() {
        val current = membership ?: return
        val revision = generation
        worker.execute {
            val count = send("PUT", current)
            main.post {
                if (closed || revision != generation) return@post
                if (otherDogs != count) {
                    otherDogs = count
                    changed()
                }
                main.postDelayed(heartbeat, 12_000)
            }
        }
    }

    private fun send(method: String, member: Membership): Int? = runCatching {
        val connection = URL("$endpoint/v1/places/${member.placeId}/members/$token").openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.instanceFollowRedirects = false
        try {
            connection.requestMethod = method
            if (method == "PUT") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(JSONObject().put("dogs", member.dogs).toString().toByteArray()) }
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).getInt("otherDogs").takeIf { it >= 0 }
            } else null
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    fun close() {
        val active = membership
        closed = true
        main.removeCallbacksAndMessages(null)
        if (active != null) worker.execute { send("DELETE", active) }
        worker.shutdown()
    }
}
