package kr.gaerang

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.content.pm.CarPackageManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

internal enum class Seat(val label: String, val door: DoorValue, val window: WindowValue, val value: SeatValue) {
    REAR_RIGHT("뒷좌석 오른쪽", DoorValue.ROW_2_RIGHT, WindowValue.ROW_2_RIGHT, SeatValue.ROW_2_RIGHT),
    REAR_LEFT("뒷좌석 왼쪽", DoorValue.ROW_2_LEFT, WindowValue.ROW_2_LEFT, SeatValue.ROW_2_LEFT),
    FRONT_RIGHT("동승석", DoorValue.ROW_1_RIGHT, WindowValue.ROW_1_RIGHT, SeatValue.ROW_1_RIGHT)
}

internal class CarSignals(
    context: Context,
    private val onChanged: () -> Unit
) {
    var gear: Int? = null
        private set
    var restricted: Boolean? = null
        private set
    var maxRestrictedLabelLength: Int? = null
        private set
    var doorPosition: Int? = null
        private set
    var windowPosition: Int? = null
        private set
    private var windowBounds: AreaBounds<Int>? = null
    val windowPercent: Int?
        get() {
            val bounds = windowBounds ?: return null
            val position = windowPosition ?: return null
            return windowOpenPercent(position.toFloat(), bounds.minValue.toFloat(), bounds.maxValue.toFloat())
        }
    var temperature: Float? = null
        private set
    private val temperatureTargets = mutableMapOf<String, Float?>()
    val targetTemperature: Pair<Float, String>?
        get() = temperatureTargets.entries.firstNotNullOfOrNull { (zone, value) -> value?.let { it to zone } }
    var occupied: Boolean? = null
        private set
    var doorCycle: Int = 0
        private set
    var boardingCycle: Int = 0
        private set

    val parked get() = gear == VehicleGear.GEAR_PARK && restricted == false
    private val main = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context.applicationContext)
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var ux: CarUxRestrictionsManager? = null
    private var unsubscribeSeat: (() -> Unit)? = null
    private var observation = 0
    private var parkingSession = 0
    private var closed = false

    // Vehicle SDK 2.3.2 reads raw 1 as true. The ranchu simulator publishes AAOS vacant=1/occupied=2.
    private val emulatorSeatEncoding =
        (BuildConfig.DEBUG || BuildConfig.CONTEST_SHOWCASE) && Build.HARDWARE == "ranchu"

    private val gearEvents = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val next = if (value.status == CarPropertyValue.STATUS_AVAILABLE) value.value as? Int else null
            if (gear == VehicleGear.GEAR_PARK && next != VehicleGear.GEAR_PARK) parkingSession++
            gear = next
            onChanged()
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            parkingSession++
            gear = null
            onChanged()
        }
    }

    init {
        vehicle.initialize()
        car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { service, ready ->
            if (!closed && ready) {
                properties = service.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                ux = service.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                if (BuildConfig.DEBUG) runCatching {
                    val packages = service.getCarManager(Car.PACKAGE_SERVICE) as? CarPackageManager
                    val allowed = packages?.isActivityDistractionOptimized(
                        context.packageName, DriveCompanionActivity::class.java.name
                    )
                    Log.i("GaerangDrive", "Platform DO status for driving companion: $allowed")
                }
                runCatching {
                    properties?.registerCallback(gearEvents, VehiclePropertyIds.GEAR_SELECTION,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE)
                    properties?.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)?.let(gearEvents::onChangeEvent)
                }
                runCatching {
                    readUxRestrictions(ux?.currentCarUxRestrictions)
                    ux?.registerListener {
                        readUxRestrictions(it)
                        onChanged()
                    }
                }
            } else {
                gear = null
                readUxRestrictions(null)
            }
            if (!closed) onChanged()
        }
    }

    private fun readUxRestrictions(value: CarUxRestrictions?) {
        restricted = value?.isRequiresDistractionOptimization
        maxRestrictedLabelLength = value?.takeIf {
            it.isRequiresDistractionOptimization &&
                it.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH != 0
        }?.maxRestrictedStringLength
    }

    fun watchSeat(seat: Seat) {
        unsubscribeSeat?.invoke()
        val tag = ++observation
        doorPosition = null
        windowPosition = null
        windowBounds = null
        temperature = null
        temperatureTargets.clear()
        occupied = null
        val door = vehicle.getDoor()
        val window = vehicle.getWindow()
        val climate = vehicle.getHVAC()
        val seatApi = vehicle.getSeat()
        val doorArea = DoorArea(seat.door)
        val windowArea = WindowArea(seat.window)
        val seatArea = SeatArea(seat.value)
        var openedAt: Long? = null
        var closedAt: Long? = null
        var becameOccupied = false
        var doorSession = -1

        fun checkBoarding(now: Long) {
            val closed = closedAt ?: return
            if (parked && doorSession == parkingSession && now - closed <= 10_000 && becameOccupied && occupied == true) {
                boardingCycle++
                openedAt = null
                closedAt = null
                becameOccupied = false
            }
        }

        fun doorChanged(value: Int?) = publish(tag) {
            val previous = doorPosition
            doorPosition = value
            val now = SystemClock.elapsedRealtime()
            if (parked && previous == 0 && value != null && value > 0) {
                openedAt = now
                doorSession = parkingSession
                closedAt = null
                becameOccupied = false
            } else if (parked && previous != null && previous > 0 && value == 0) {
                doorCycle++
                if (openedAt?.let { now - it <= 90_000 } == true) {
                    closedAt = now
                    checkBoarding(now)
                }
            }
        }
        fun windowChanged(value: Int?) = publish(tag) { windowPosition = value }
        fun tempChanged(value: Float?) = publish(tag) { temperature = value?.takeIf { it.isFinite() && it in -50f..80f } }
        fun occupiedChanged(reading: Boolean?) = publish(tag) {
            val value = if (emulatorSeatEncoding) reading?.not() else reading
            val previous = occupied
            occupied = value
            if (value != true) becameOccupied = false
            val now = SystemClock.elapsedRealtime()
            if (parked && previous == false && value == true && openedAt?.let { now - it <= 90_000 } == true) {
                becameOccupied = true
                checkBoarding(now)
            }
        }

        val doorListener = object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) = doorChanged(pos)
            override fun onFailed(e: Exception) = doorChanged(null)
        }
        val windowListener = object : WindowPositionListener {
            override fun onWindowPositionUpdated(position: Int?) = windowChanged(position)
            override fun onFailed(e: Exception) = windowChanged(null)
        }
        val tempListener = object : HvacTemperatureListener {
            override fun onHvacTemperatureUpdated(temperature: Float?) = tempChanged(temperature)
            override fun onFailed(e: Exception) = tempChanged(null)
        }
        val occupancyListener = object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(occupancy: Boolean?) = occupiedChanged(occupancy)
            override fun onFailed(e: Exception) = occupiedChanged(null)
        }
        val exactTargetListener = targetListener(tag, seat.label)
        val frontValue = if (seat == Seat.REAR_LEFT) SeatValue.ROW_1_LEFT else SeatValue.ROW_1_RIGHT
        val frontArea = SeatArea(frontValue)
        // The emulator has two front target-temperature zones. Keep this reading labeled by zone.
        val frontTargetListener = if (frontArea == seatArea) null else targetListener(tag,
            if (frontValue == SeatValue.ROW_1_LEFT) "운전석" else "동승석")
        runCatching { door.registerDoorPosition(doorArea, doorListener) }
        runCatching { window.registerWindowPosition(windowArea, windowListener) }
        runCatching {
            window.checkWindowPositionCapabilities({ _, areas ->
                publish(tag) { windowBounds = areas[windowArea] }
            }, {})
        }
        runCatching { door.getCurrentDoorPosition(doorArea, ::doorChanged) { doorChanged(null) } }
        runCatching { window.getCurrentWindowPosition(windowArea, ::windowChanged) { windowChanged(null) } }
        runCatching { climate.registerHvacTemperatureCurrent(seatArea, tempListener) }
        runCatching { climate.getHvacTemperatureCurrent(seatArea, ::tempChanged) { tempChanged(null) } }
        runCatching { climate.registerHvacTemperature(seatArea, exactTargetListener) }
        runCatching { climate.getHvacTemperature(seatArea, TemperatureUnit.CELSIUS, exactTargetListener::onHvacTemperatureUpdated) {} }
        frontTargetListener?.let { listener ->
            runCatching { climate.registerHvacTemperature(frontArea, listener) }
            runCatching { climate.getHvacTemperature(frontArea, TemperatureUnit.CELSIUS, listener::onHvacTemperatureUpdated) {} }
        }
        runCatching { seatApi.registerSeatOccupancy(seatArea, occupancyListener) }
        runCatching { seatApi.getCurrentSeatOccupancy(seatArea, ::occupiedChanged) { occupiedChanged(null) } }
        unsubscribeSeat = {
            runCatching { door.unregisterDoorPosition(doorArea, doorListener) }
            runCatching { window.unregisterWindowPosition(windowArea, windowListener) }
            runCatching { climate.unregisterHvacTemperatureCurrent(seatArea, tempListener) }
            runCatching { climate.unregisterHvacTemperature(seatArea, exactTargetListener) }
            frontTargetListener?.let { runCatching { climate.unregisterHvacTemperature(frontArea, it) } }
            runCatching { seatApi.unregisterSeatOccupancy(seatArea, occupancyListener) }
        }
        onChanged()
    }

    private fun targetListener(tag: Int, zone: String): HvacTemperatureListener {
        temperatureTargets[zone] = null
        return object : HvacTemperatureListener {
            override fun onHvacTemperatureUpdated(temperature: Float?) = publish(tag) {
                temperatureTargets[zone] = temperature?.takeIf { it.isFinite() && it in -50f..80f }
            }

            override fun onFailed(e: Exception) = onHvacTemperatureUpdated(null)
        }
    }

    private fun publish(tag: Int, update: () -> Unit) {
        main.post {
            if (!closed && observation == tag) {
                update()
                onChanged()
            }
        }
    }

    fun cabinControls(seat: Seat, group: CabinGroup, onUpdate: () -> Unit): CabinControls =
        CabinControls(vehicle, seat, group, { !closed && parked }, onUpdate)

    fun close() {
        closed = true
        unsubscribeSeat?.invoke()
        runCatching { properties?.unregisterCallback(gearEvents) }
        runCatching { ux?.unregisterListener() }
        car?.disconnect()
        vehicle.release()
    }
}
