package kr.gaerang

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import kotlin.math.roundToInt

private typealias Failure = (Exception) -> Unit
private typealias Capability<A, V> = ((CapabilityConfig, Map<A, AreaBounds<V>>) -> Unit, Failure) -> Unit
private typealias Read<A, V> = (A, (V?) -> Unit, Failure) -> Unit
private typealias Write<A, V> = (A, V, (A, V?) -> Unit, Failure) -> Unit

internal enum class CabinGroup(val title: String) {
    CLIMATE("공조 조작"), WINDOW("동승 쪽 창문"), DOOR("동승 쪽 문"), SEAT("동승 좌석 통풍")
}

internal class CabinControls(
    vehicle: Vehicle,
    private val seat: Seat,
    group: CabinGroup,
    private val parked: () -> Boolean,
    private val changed: () -> Unit
) {
    enum class Kind { TEMPERATURE, LEVEL, TOGGLE, WINDOW_POSITION, DOOR_POSITION }

    class Control(val title: String, val kind: Kind) {
        var zone: String = ""
        var available: Boolean? = null
        var value: Float? = null
        var min = 0f
        var max = 0f
        var step = 1f
        var write: ((Float, () -> Unit, Failure) -> Unit)? = null
        var read: (() -> Unit)? = null
        var busy = false
        var requestedValue: Float? = null
        var message: String? = null
        var readVersion = 0
        var writeVersion = 0
        var readInProgress = false
        var consecutiveReadFailures = 0
        var writeRejected = false
        val canSet get() = write != null && !busy && value != null
    }

    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private val controls = mutableListOf<Control>()
    val rows: List<Control> get() = controls
    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            if (parked()) controls.forEach { it.read?.invoke() }
            main.postDelayed(this, if (controls.any(Control::busy)) 500 else 1_500)
        }
    }

    init {
        val selected = SeatArea(seat.value)
        val front = if (seat == Seat.REAR_LEFT) SeatValue.ROW_1_LEFT else SeatValue.ROW_1_RIGHT
        val climateZone: (Set<SeatArea>) -> SeatArea? = { areas ->
            areas.firstOrNull { it == selected }
                ?: areas.filter { seat.value in it.values }.minByOrNull { it.values.size }
                ?: areas.filter { front in it.values }.minByOrNull { it.values.size }
        }
        val seatZone: (Set<SeatArea>) -> SeatArea? = { areas -> areas.firstOrNull { it == selected } }
        val zoneName: (SeatArea) -> String = { area ->
            area.values.joinToString("·") { value ->
                when (value) {
                    SeatValue.ROW_1_LEFT -> "운전석"
                    SeatValue.ROW_1_RIGHT -> "동승석"
                    else -> Seat.entries.firstOrNull { it.value == value }?.label
                        ?: when (value) {
                            SeatValue.ROW_1_CENTER -> "앞좌석 가운데"
                            SeatValue.ROW_2_CENTER -> "2열 가운데"
                            else -> "3열"
                        }
                }
            }
        }
        when (group) {
            CabinGroup.CLIMATE -> {
                val climate = vehicle.getHVAC()
                val temperature = control("설정 온도", Kind.TEMPERATURE)
                temperature.step = 0.5f
                bind(temperature, { ready, failed ->
                    climate.checkHvacTemperatureCapabilities({ config, areas, conversion ->
                        val increment = conversion?.incrementCelsius?.takeIf { it.isFinite() && it > 0 }
                        main.post { if (!closed && increment != null) temperature.step = increment }
                        ready(config, areas)
                    }, failed)
                }, climateZone, zoneName, { area, ready, failed ->
                    climate.getHvacTemperature(area, TemperatureUnit.CELSIUS, ready, failed)
                }, { area, value, ready, failed ->
                    climate.setHvacTemperature(area, value, TemperatureUnit.CELSIUS, ready, failed)
                }, { it }, { it })
                toggle("공조 전원", climate::checkHvacPowerCapabilities, climateZone, zoneName,
                    climate::getHvacPowerState, climate::setHvacPower)
                toggle("에어컨 냉방", climate::checkHvacAcCapabilities, climateZone, zoneName,
                    climate::getHvacAcState, climate::setHvacAc)
                count("바람 세기", Kind.LEVEL, climate::checkHvacFanSpeedCapabilities, climateZone, zoneName,
                    climate::getHvacFanSpeed, climate::setHvacFanSpeed)
                toggle("공기청정", climate::checkHvacAirCleanerCapabilities, climateZone, zoneName,
                    climate::getHvacAirCleanerState, climate::setHvacAirCleaner)
            }
            CabinGroup.WINDOW -> {
                val window = vehicle.getWindow()
                val area = WindowArea(seat.window)
                val zone: (Set<WindowArea>) -> WindowArea? = { areas -> areas.firstOrNull { it == area } }
                count("창문 열림", Kind.WINDOW_POSITION, window::checkWindowPositionCapabilities, zone, { seat.label },
                    window::getCurrentWindowPosition, window::setWindowPosition)
                toggle("창문 잠금", window::checkWindowLockCapabilities, zone, { seat.label },
                    window::getCurrentWindowLockState, window::setWindowLock)
            }
            CabinGroup.DOOR -> {
                val door = vehicle.getDoor()
                val area = DoorArea(seat.door)
                val zone: (Set<DoorArea>) -> DoorArea? = { areas -> areas.firstOrNull { it == area } }
                count("문 열림", Kind.DOOR_POSITION, door::checkDoorPositionCapabilities, zone, { seat.label },
                    door::getCurrentDoorPosition, door::setDoorPosition)
                toggle("문 잠금", door::checkDoorLockCapabilities, zone, { seat.label },
                    door::getDoorLockState, door::setDoorLock)
            }
            CabinGroup.SEAT -> {
                val chair = vehicle.getSeat()
                count("시트 통풍", Kind.LEVEL, chair::checkSeatVentilationCapabilities, seatZone, zoneName,
                    chair::getHvacSeatVentilationState, chair::setHvacSeatVentilation)
            }
        }
        main.postDelayed(poll, 1_500)
    }

    fun adjust(control: Control, target: Float) {
        val send = control.write ?: return
        if (closed || !parked() || !control.canSet || !target.isFinite()) return
        val next = target.coerceIn(control.min, control.max)
        if (control.value == next) return
        control.busy = true
        control.requestedValue = next
        control.writeRejected = false
        control.readVersion++
        control.readInProgress = false
        control.message = null
        val version = ++control.writeVersion
        changed()
        fun checkActualValue(error: Exception? = null) {
            main.post {
                if (closed || control.writeVersion != version || !control.busy) return@post
                if (error != null) {
                    control.writeRejected = true
                    Log.w("GaerangCabin", "${control.title} SDK write failed", error)
                }
                control.read?.invoke()
            }
        }
        runCatching { send(next, { checkActualValue() }, ::checkActualValue) }
            .onFailure { error -> checkActualValue(Exception(error)) }
        // Read the vehicle state even if the SDK setter callback is deferred or lost.
        main.postDelayed({
            if (!closed && control.writeVersion == version && control.busy) control.read?.invoke()
        }, 300)
        val confirmationTimeMs = when (control.kind) {
            Kind.WINDOW_POSITION, Kind.DOOR_POSITION -> 16_000L
            else -> 8_000L
        }
        main.postDelayed({
            if (closed || control.writeVersion != version || !control.busy) return@postDelayed
            control.busy = false
            control.message = if (control.writeRejected) "차량이 변경 요청을 완료하지 못했어요."
                else "차량에서 변경된 값을 확인하지 못했어요."
            changed()
        }, confirmationTimeMs)
    }

    fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
    }

    private fun control(title: String, kind: Kind) = Control(title, kind).also(controls::add)

    private fun <A> count(title: String, kind: Kind, check: Capability<A, Int>,
                          choose: (Set<A>) -> A?, name: (A) -> String, get: Read<A, Int>, set: Write<A, Int>): Control =
        control(title, kind).also { bind(it, check, choose, name, get, set, Int::toFloat, Float::roundToInt) }

    private fun <A> toggle(title: String, check: ((CapabilityConfig, List<A>) -> Unit, Failure) -> Unit,
                           choose: (Set<A>) -> A?, name: (A) -> String, get: Read<A, Boolean>, set: Write<A, Boolean>) {
        bind(control(title, Kind.TOGGLE), { ready, failed ->
            check({ config, areas -> ready(config, areas.associateWith { AreaBounds(false, true) }) }, failed)
        }, choose, name, get, set, { if (it) 1f else 0f }, { it == 1f })
    }

    private fun <A, V> bind(control: Control, check: Capability<A, V>, choose: (Set<A>) -> A?,
                            name: (A) -> String, get: Read<A, V>, set: Write<A, V>,
                            number: (V) -> Float, fromNumber: (Float) -> V) {
        val failed: (Throwable) -> Unit = { error ->
            main.post {
                if (!closed) {
                    control.available = false
                    control.message = "차량 지원 정보를 읽을 수 없어요."
                    Log.w("GaerangCabin", "${control.title} SDK capability query failed", error)
                    changed()
                }
            }
        }
        runCatching {
            check({ config, areas ->
                main.post {
                    if (closed) return@post
                    val area = choose(areas.keys)
                    val bounds = areas[area]
                    if (area == null || bounds == null || config.accessType == AccessType.NONE) {
                        control.available = false
                        changed()
                        return@post
                    }
                    control.zone = name(area)
                    control.available = true
                    control.min = number(bounds.minValue)
                    control.max = number(bounds.maxValue)
                    val validRange = control.min.isFinite() && control.max.isFinite() && control.max > control.min
                    if (validRange && control.kind == Kind.WINDOW_POSITION) {
                        control.step = ((control.max - control.min) / 10f).roundToInt().coerceAtLeast(1).toFloat()
                    }
                    if (validRange && config.accessType == AccessType.READ_WRITE) {
                        control.write = { value, success, failure ->
                            set(area, fromNumber(value), { _, _ -> success() }, failure)
                        }
                    }
                    if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
                        control.read = read@{
                            if (closed || !parked() || control.readInProgress) return@read
                            control.readInProgress = true
                            val version = ++control.readVersion
                            fun receive(value: V?) {
                                main.post {
                                    if (closed || version != control.readVersion || !control.readInProgress) return@post
                                    control.readInProgress = false
                                    val previousValue = control.value
                                    val wasBusy = control.busy
                                    val previousMessage = control.message
                                    val sample = value?.let(number)?.takeIf(Float::isFinite)
                                    if (sample != null) {
                                        control.value = sample
                                        control.consecutiveReadFailures = 0
                                    } else if (++control.consecutiveReadFailures >= 2 && !control.busy) {
                                        control.value = null
                                    }
                                    val target = control.requestedValue
                                    if (sample != null && target != null && abs(sample - target) < .001f) {
                                        control.busy = false
                                        control.requestedValue = null
                                        control.message = null
                                    }
                                    if (previousValue != control.value || wasBusy != control.busy ||
                                        previousMessage != control.message) changed()
                                }
                            }
                            runCatching { get(area, ::receive) { receive(null) } }
                                .onFailure { receive(null) }
                            // Keep one valid read active and retry if the SDK never replies.
                            main.postDelayed({ receive(null) }, 3_000)
                        }
                        control.read?.invoke()
                    }
                    changed()
                }
            }, failed)
        }.onFailure(failed)
    }
}

internal fun windowOpenPercent(value: Float, min: Float, max: Float): Int? =
    if (max > min) ((value - min) / (max - min) * 100).roundToInt().coerceIn(0, 100) else null
