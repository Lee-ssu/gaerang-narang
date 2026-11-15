package kr.gaerang

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Applies one comfort preset on boarding and reports the temperature the SDK reads back. */
internal class AutoCabin(
    private val car: CarSignals,
    private val eligible: (Dog) -> Boolean,
    private val changed: () -> Unit
) {
    private class Adjustment(val dog: Dog, val celsius: Float, val controls: CabinControls) {
        var target: Float? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private var pending: Adjustment? = null
    private var appliedTo: String? = null
    private var phase = "idle"
    private var summary = "등록한 자리에서 탑승 신호가 오면 공조 설정 온도를 맞춰요."
    private val timeout = Runnable { finish("failed", "차량에서 자동 변경을 확인하지 못했어요.") }
    private val read = Runnable { advance() }

    fun apply(dog: Dog, freshBoarding: Boolean = false) {
        if (!eligible(dog) || (!freshBoarding && appliedTo == dog.id && phase == "applied") || pending != null) return
        appliedTo = dog.id
        val celsius = comfortPreset(dog)
        val controls = car.cabinControls(dog.seat, CabinGroup.CLIMATE) {
            main.removeCallbacks(read)
            main.post(read)
        }
        pending = Adjustment(dog, celsius, controls)
        phase = "applying"
        summary = "${dog.name}의 공조 설정을 적용 중이에요."
        main.postDelayed(timeout, 11_000)
        changed()
    }

    fun cancel(message: String) {
        if (pending != null) finish("idle", message)
    }

    fun reset() {
        release()
        appliedTo = null
        phase = "idle"
        summary = "등록한 자리에서 탑승 신호가 오면 공조 설정 온도를 맞춰요."
    }

    fun state(enabled: Boolean) = JSONObject().put("enabled", enabled).put("phase", phase).put("summary", summary)

    private fun advance() {
        val request = pending ?: return
        if (!eligible(request.dog)) {
            cancel("탑승 조건이 바뀌어 자동 조정을 멈췄어요.")
            return
        }
        val temperature = request.controls.rows.first { it.kind == CabinControls.Kind.TEMPERATURE }
        if (temperature.available == false || "운전석" in temperature.zone) {
            finish("failed", "등록한 자리의 공조 설정을 자동으로 바꿀 수 없어요.")
            return
        }
        val actual = temperature.value ?: return
        val target = request.target
        if (target != null) {
            if (!temperature.busy) {
                if (abs(target - actual) < .001f) confirmed(temperature)
                else finish("failed", "${temperature.zone} 설정 온도 변경을 차량에서 확인하지 못했어요.")
            }
            return
        }
        val step = temperature.step
        if (temperature.max <= temperature.min || !step.isFinite() || step <= 0f) {
            finish("failed", "차량의 공조 설정 범위를 확인하지 못했어요.")
            return
        }
        val adjusted = (temperature.min + ((request.celsius - temperature.min) / step).roundToInt() * step)
            .coerceIn(temperature.min, temperature.max)
        if (abs(adjusted - actual) < .001f) confirmed(temperature)
        else if (temperature.canSet) {
            request.target = adjusted
            request.controls.adjust(temperature, adjusted)
        } else finish("failed", "${temperature.zone} 설정 온도가 읽기 전용이에요.")
    }

    private fun confirmed(temperature: CabinControls.Control) {
        val celsius = formatWeight(requireNotNull(temperature.value).toDouble())
        finish("applied", "${temperature.zone} 공조 설정 온도 ${celsius}°C를 확인했어요.")
    }

    private fun finish(nextPhase: String, message: String) {
        release()
        phase = nextPhase
        summary = message
        changed()
    }

    private fun release() {
        main.removeCallbacks(timeout)
        main.removeCallbacks(read)
        pending?.controls?.close()
        pending = null
    }

    private fun comfortPreset(dog: Dog): Float {
        val breed = dog.breed.lowercase().filter(Char::isLetterOrDigit)
        // Prototype cabin starting points. The driver can adjust the setpoint in the climate panel.
        return when {
            shortMuzzleBreeds.any(breed::contains) || dog.weight >= 30 -> 21.5f
            dog.age <= 1 || dog.weight <= 5 -> 22.5f
            else -> 22f
        }
    }

    private companion object {
        val shortMuzzleBreeds = setOf("퍼그", "불독", "불도그", "페키니즈", "보스턴테리어", "시추", "복서",
            "pug", "bulldog", "pekingese", "bostonterrier", "shihtzu", "boxer")
    }
}
