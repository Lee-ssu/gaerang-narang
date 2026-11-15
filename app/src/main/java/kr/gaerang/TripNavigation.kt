package kr.gaerang

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper

internal class TripNavigation(context: Context, private val onChanged: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val helper = NaviHelper(context.applicationContext)
    var connected = false
        private set
    var pending = false
        private set
    var note = "기본 내비에 연결하는 중"
        private set
    var arrival: DestinationArrivedInfo? = null
        private set
    var location: CurrentLocationInfo? = null
        private set
    var activeDestination: DestinationInfo? = null
        private set
    var companionPlace: Place? = null
        private set
    var guidedPlace: Place? = null
        private set
    private var navigationStatus: NavigationStatus? = null
    private var waypoints: List<WaypointInfo> = emptyList()
    private var chosenRestWaypoint: Place? = null
    val restWaypoint: Place?
        get() = waypoints.firstOrNull()?.takeIf { navigationStatus == NavigationStatus.ROUTE_DRIVE }?.let { point ->
            Places.all.firstOrNull {
                (it.kind == PlaceKind.REST || it.kind == PlaceKind.CHARGE || it.id == chosenRestWaypoint?.id) && it.routable &&
                    (it.name == point.name || (it.latitude == point.latitude && it.longitude == point.longitude))
            }
        }
    val hasActiveWaypoint get() = waypoints.isNotEmpty()
    private var requestedPlace: Place? = null
    private var closed = false
    private val timeout = Runnable {
        finish("기본 내비의 경로 응답이 없어요. 내비에서 직접 확인해 주세요.")
        if (guidedPlace == null) clearCompanion()
    }

    private val events = object : NaviHelperEventListener {
        override fun onRoutingCompleted() = post(::routeSearchCompleted)

        // Older Pleos versions deliver the search result via onRouteEnded.
        override fun onRouteEnded() = post(::routeSearchCompleted)

        override fun onNavigationStatus(status: NavigationStatus) = post {
            navigationStatus = status
            if (status != NavigationStatus.ROUTE_DRIVE) waypoints = emptyList()
            else runCatching { helper.getWaypointInfo() }
            if (status == NavigationStatus.ROUTE_DRIVE && companionPlace != null) {
                syncGuidance()
                refresh()
            } else if (guidedPlace != null) {
                clearCompanion()
            }
            onChanged()
        }
        override fun onRouteCancelled() = post {
            waypoints = emptyList()
            // Replacing a route also cancels the old one before the new search completes.
            if (!pending) clearCompanion()
        }
        override fun onError(errorCode: NaviErrorCode) = post {
            if (pending) clearCompanion()
            finish("기본 내비에서 경로를 설정하지 못했어요.")
        }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = post {
            if (requestedPlace?.name == destinationArrivedInfo.name) {
                arrival = destinationArrivedInfo
                requestedPlace = null
                clearCompanion()
                onChanged()
            }
        }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = post {
            if (currentLocationInfo.latitude in -90.0..90.0 && currentLocationInfo.longitude in -180.0..180.0 &&
                (currentLocationInfo.latitude != 0.0 || currentLocationInfo.longitude != 0.0) &&
                location != currentLocationInfo) {
                location = currentLocationInfo
                onChanged()
            }
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = post {
            activeDestination = destinationInfo
            syncGuidance()
            onChanged()
        }

        override fun onDestinationChanged() = post {
            if (companionPlace != null) refresh()
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = post {
            runCatching { helper.getWaypointInfo() }
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = post {
            waypoints = waypoints.drop(1)
            runCatching { helper.getWaypointInfo() }
            onChanged()
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = post {
            waypoints = waypointInfoList
            onChanged()
        }
    }

    init {
        helper.addListener(events)
        helper.initialize(
            onConnected = { post { connected = true; note = ""; refresh(); onChanged() } },
            onFailed = { post { connected = false; note = "기본 내비에 연결할 수 없어요."; onChanged() } }
        )
    }

    fun request(course: List<Place>): Boolean {
        if (!connected || pending || course.size !in 1..2 || course.any { !it.routable }) return false
        val place = course.last()
        arrival = null
        requestedPlace = place
        companionPlace = place
        guidedPlace = null
        navigationStatus = null
        activeDestination = null
        waypoints = emptyList()
        chosenRestWaypoint = null
        pending = true
        note = "기본 내비에 경로를 요청했어요."
        onChanged()
        main.postDelayed(timeout, 12_000)
        val destination = RouteInfo(
            latitude = requireNotNull(place.latitude), longitude = requireNotNull(place.longitude),
            poiName = place.name, poiId = "", address = place.address, poiSubId = ""
        )
        runCatching {
            if (course.size == 2) {
                val first = course.first()
                val stop = waypoint(first)
                helper.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination, listOf(stop)))
            } else {
                helper.requestRoute(destination)
            }
        }.onFailure {
            requestedPlace = null
            clearCompanion()
            finish("기본 내비에 경로를 요청하지 못했어요.")
            return false
        }
        return true
    }

    fun addRestWaypoint(place: Place): Boolean {
        if (!connected || pending || navigationStatus != NavigationStatus.ROUTE_DRIVE ||
            !place.routable || hasActiveWaypoint) return false
        return runCatching {
            helper.addWaypoint(waypoint(place))
            chosenRestWaypoint = place
            helper.getWaypointInfo()
        }.isSuccess
    }

    private fun waypoint(place: Place) = RequestWaypointInfo(
        latitude = requireNotNull(place.latitude), longitude = requireNotNull(place.longitude),
        poiName = place.name, waypointIndex = WaypointIndex.FIRST,
        poiId = "", address = place.address, poiSubId = ""
    )

    fun refresh() {
        if (!connected) return
        runCatching { helper.getCurrentLocationInfo() }
        runCatching { helper.getDestinationInfo() }
    }

    fun showMap(): Boolean {
        if (!connected) return false
        return runCatching { helper.requestNavigationScreen(NavigationScreenRequest.Foreground) }.isSuccess
    }

    fun clearCompanion() {
        if (companionPlace == null && guidedPlace == null) return
        companionPlace = null
        guidedPlace = null
        onChanged()
    }

    private fun routeSearchCompleted() {
        finish("기본 내비에서 경로 탐색을 완료했어요.")
        refresh()
    }

    private fun syncGuidance() {
        val place = companionPlace ?: return
        if (navigationStatus != NavigationStatus.ROUTE_DRIVE) return
        if (destinationMatches(place)) {
            guidedPlace = place
            if (pending) finish("기본 내비에서 길 안내를 시작했어요.")
            else note = "기본 내비에서 길 안내를 시작했어요."
        } else if (guidedPlace != null) {
            clearCompanion()
        }
    }

    private fun destinationMatches(place: Place): Boolean {
        val destination = activeDestination ?: return false
        if (destination.name == place.name) return true
        val latitude = place.latitude ?: return false
        val longitude = place.longitude ?: return false
        val distance = FloatArray(1)
        android.location.Location.distanceBetween(latitude, longitude, destination.latitude, destination.longitude, distance)
        return distance[0] <= 120f
    }

    private fun post(action: () -> Unit) {
        main.post { if (!closed) action() }
    }

    private fun finish(message: String) {
        if (!pending) return
        main.removeCallbacks(timeout)
        pending = false
        note = message
        onChanged()
    }

    fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        helper.removeListener(events)
        helper.release()
    }
}
