package kr.gaerang

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.car.VehicleGear
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private enum class Page(val label: String) {
        HOME("우리 집"), PLACES("산책길"), CARE("안심"), JOURNAL("발자국"), PROFILE("프로필"),
        ARRIVAL("하차 준비"), NAV_CARD("내비 동행 미리보기")
    }

    private enum class DriveScene(val title: String) {
        NORMAL("주행 중 케어"), NIGHT("야간 케어"), REST("쉬어가기 제안"), WINDOW("창문 열림 안내"),
        REST_TEN("쉬는 시간 10분 전"), REST_FIVE("쉬는 시간 5분 전"), HIGHWAY("고속도로 휴게소")
    }

    private class WindowRaise(val target: Float?, val percent: Int? = null) {
        var deadline: Long? = null
        var submitted = false
        var failed = false
    }

    private val main = Handler(Looper.getMainLooper())
    private val clock = RideClock()
    private lateinit var store: OutingStore
    private lateinit var car: CarSignals
    private lateinit var navigation: TripNavigation
    private lateinit var reminders: RestReminders
    private lateinit var companionNotice: TripCompanion
    private lateinit var presence: OutingPresence
    private lateinit var ui: RoundUi
    private val zipFrontendDelegate = lazy { ZipFrontend(this, ::handleZipAction) }
    private val zipFrontend by zipFrontendDelegate
    private val autoCabinDelegate = lazy {
        AutoCabin(car, { profile ->
            autoCabinEnabled && activityResumed && car.parked && cabinGroup != CabinGroup.CLIMATE &&
                dog == profile
        }, ::syncCabinControls)
    }
    private val autoCabin by autoCabinDelegate
    private val autoCabinEnabled get() = getPreferences(MODE_PRIVATE).getBoolean("autoCabin", BuildConfig.DEBUG)
    private val drivePreviewEnabled = BuildConfig.DEBUG || BuildConfig.CONTEST_SHOWCASE
    private var zipVisible = false
    private var nativeProfile = false
    private var seatProfile = false
    private lateinit var content: LinearLayout
    private var page = Page.HOME
    private var drivePreview: DriveScene? = null
    private var darkDrivePreview = false
    private var renderedDriveScene: DriveScene? = null
    private var renderedSideDestination: String? = null
    private var sideCompanionDismissed = false
    private var driveCompanionOpen = false
    private var parkedDriveDemoDestination: String? = null
    private var navCareOpen = false
    private var openNavCareRequested = false
    private var activityResumed = false
    private var observedDestination: String? = null
    private var movedOnTrip = false
    private var navStopped = false
    private var presenceText: TextView? = null
    private var routeRemainingText: TextView? = null
    private var routeArrivalText: TextView? = null
    private var routeDogView: RunningDogView? = null
    private var leashReady = false
    private var wasteBagsReady = false
    private var waterReady = false
    private var seenArrival: ai.pleos.playground.navi.data.DestinationArrivedInfo? = null
    private var renderedParked: Boolean? = null
    private var category: PlaceKind? = null
    private var indoorOnly = true
    private var includeUncertain = false
    private var sampleProfile = false
    private var noticedDoorCycle = 0
    private var noticedBoardingCycle = 0
    private var autoCabinProfile: Dog? = null
    private var boardingPrompt = false
    private var shortDemo = false
    private var stage: RestStage? = null
    private var notifiedStage: RestStage? = null
    private var handledAlert: RestAlert? = null
    private var pendingAlert: RestAlert? = null
    private var openAlertRequested = false
    private var alertScreen: RestAlert? = null
    private var renderedAlertScreen: RestAlert? = null
    private var parkedDialog: AlertDialog? = null
    private var cabinControls: CabinControls? = null
    private var cabinGroup: CabinGroup? = null
    private var windowRaise: WindowRaise? = null
    private val windowRaiseTick = Runnable { requestWindowRaise() }
    private var filePicked: ((String) -> Unit)? = null
    private var dictated: ((String) -> Unit)? = null
    private var carePlayer: MediaPlayer? = null
    private var voiceRequest: Intent? = null
    private var voiceResults: List<String> = emptyList()
    private var voiceResultsTime = 0L
    private var statusText: TextView? = null
    private var clockText: TextView? = null
    private var restText: TextView? = null
    private var restCountdown: TextView? = null
    private var doorText: TextView? = null
    private var windowText: TextView? = null
    private var temperatureText: TextView? = null
    private var temperatureCaption: TextView? = null
    private var wideComposition = false
    private var vehicleNotice: TextView? = null
    private var navigationText: TextView? = null
    private var locationText: TextView? = null
    private var startRouteButton: Button? = null
    private val companions get() = store.dogs.filter { it.id in store.activeIds }.ifEmpty {
        store.dogs.take(1).ifEmpty { if (sampleProfile) listOf(Dog.sample) else emptyList() }
    }
    private val dog get() = companions.firstOrNull()
    private val dogLabel get() = dog?.name ?: "우리 강아지"
    private val seat get() = dog?.seat ?: Seat.REAR_RIGHT
    private val darkAppearance: Boolean
        get() = when (store.appearance) {
            Appearance.SYSTEM -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            Appearance.LIGHT -> false
            Appearance.DARK -> true
        }
    private val screenDark get() = if (drivePreview != null) drivePreview == DriveScene.NIGHT || darkDrivePreview
        else darkAppearance
    private val course get() = store.courseIds.mapNotNull(Places::get)
    private val displayedRoute get() = if (drivePreview == null) navigation.companionPlace ?: course.lastOrNull()
        else course.lastOrNull() ?: navigation.companionPlace
    private val nextTick = Runnable { refresh() }
    private val screenTick = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = OutingStore(this)
        reminders = RestReminders(this)
        reminders.clear()
        companionNotice = TripCompanion(this)
        companionNotice.clear()
        presence = OutingPresence(::refresh)
        openNavCareRequested = intent.action == TripCompanion.OPEN_PARKED_CARE
        openAlertRequested = intent.action == RestReminders.OPEN_ALERT
        shortDemo = BuildConfig.DEBUG && intent.getBooleanExtra("shortDemo", false)
        sampleProfile = (shortDemo || BuildConfig.CONTEST_SHOWCASE) && store.dogs.isEmpty()
        voiceRequest = intent.takeIf { it.action in voiceActions }
        car = CarSignals(this, ::refresh)
        navigation = TripNavigation(this, ::refresh)
        car.watchSeat(seat)
        refresh()
    }

    override fun onStart() {
        super.onStart()
        main.removeCallbacks(screenTick)
        main.post(screenTick)
    }

    override fun onStop() {
        main.removeCallbacks(screenTick)
        if (autoCabinDelegate.isInitialized()) autoCabin.cancel("공조 자동 조정을 멈췄어요.")
        stopCareAudio()
        clearWindowRaise()
        if (drivePreview == DriveScene.WINDOW) drivePreview = null
        closeCabinControls()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        refresh()
    }

    override fun onPause() {
        activityResumed = false
        if (zipFrontendDelegate.isInitialized()) zipFrontend.pauseCareVideo()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action in voiceActions) {
            voiceRequest = intent
            refresh()
        } else if (intent.action == TripCompanion.OPEN_PARKED_CARE) {
            openNavCareRequested = true
            refresh()
        } else if (intent.action == RestReminders.OPEN_ALERT) {
            openAlertRequested = true
            refresh()
        }
    }

    override fun onDestroy() {
        if (driveCompanionOpen) driveCompanionState.value = null
        cabinControls?.close()
        main.removeCallbacksAndMessages(null)
        reminders.clear()
        reminders.clearAlert()
        companionNotice.clear()
        presence.close()
        navigation.close()
        car.close()
        if (zipFrontendDelegate.isInitialized()) zipFrontend.close()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.screenWidthDp >= WIDE_WIDTH_DP) sideCompanionDismissed = false
        render()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 90 && page == Page.NAV_CARD) redraw()
    }

    @Deprecated("Back returns from a section to the trip home")
    override fun onBackPressed() {
        if (windowRaise != null) dismissWindowRaise()
        else if (cabinControls != null) closeCabinControls()
        else if (alertScreen != null) dismissRestAlert()
        else if (nativeProfile) show(Page.PROFILE)
        else if (car.parked && seatProfile) show(Page.PROFILE)
        else if (car.parked && drivePreview != null) show(Page.CARE)
        else if (car.parked && navCareOpen) show(Page.HOME)
        else if (renderedSideDestination != null) dismissSideCompanion()
        else if (car.parked && page == Page.NAV_CARD) show(Page.PLACES)
        else if (car.parked && page != Page.HOME) show(Page.HOME)
        else super.onBackPressed()
    }

    private fun refresh() {
        if (!::car.isInitialized || !::navigation.isInitialized) return
        val activeDog = dog
        if (activeDog != autoCabinProfile) {
            autoCabinProfile = activeDog
            if (autoCabinDelegate.isInitialized()) autoCabin.reset()
        }
        val now = SystemClock.elapsedRealtime()
        val wasResting = clock.resting
        val driving = car.gear in drivingGears
        clock.update(driving, now)
        if (wasResting && !clock.resting) notifiedStage = null
        val elapsed = clock.elapsed(now)
        stage = if (clock.active) RestStage.entries.lastOrNull { elapsed >= it.threshold(shortDemo) } else null
        if (!clock.resting && stage != null && stage != notifiedStage && reminders.enabled) {
            reminders.show(requireNotNull(stage))
            notifiedStage = stage
        }
        refreshRestAlerts(elapsed)
        main.removeCallbacks(nextTick)
        if (clock.counting) {
            val thresholds = RestStage.entries.map { it.threshold(shortDemo) } +
                RestAlert.entries.map { it.threshold(shortDemo) }
            thresholds.filter { it > elapsed }.minOrNull()?.let {
                main.postDelayed(nextTick, it - elapsed)
            }
        }
        val stateChanged = renderedParked != car.parked
        if (stateChanged && !car.parked) {
            if (autoCabinDelegate.isInitialized()) autoCabin.cancel("주차 상태가 바뀌어 자동 조정을 멈췄어요.")
            seatProfile = false
            clearWindowRaise()
            closeCabinControls()
            drivePreview = null
            navCareOpen = false
            stopCareAudio()
            parkedDialog?.dismiss()
            currentFocus?.clearFocus()
        }
        refreshNavigationCompanion(driving)
        if (car.doorCycle != noticedDoorCycle && car.parked) {
            noticedDoorCycle = car.doorCycle
            if (!clock.active && dog != null && dog?.boarding != Boarding.HELD) {
                boardingPrompt = true
                if (!stateChanged && page == Page.HOME) render()
            }
        }
        if (car.boardingCycle != noticedBoardingCycle) {
            noticedBoardingCycle = car.boardingCycle
            activeDog?.takeUnless { it.boarding == Boarding.HELD }?.let { autoCabin.apply(it, freshBoarding = true) }
        }
        if (car.parked && navigation.arrival != null && navigation.arrival !== seenArrival) {
            seenArrival = navigation.arrival
            resetArrivalChecklist()
            page = Page.ARRIVAL
            render()
        }
        if (stateChanged || renderedDriveScene != currentDriveScene() || renderedAlertScreen != alertScreen ||
            renderedSideDestination != sideCompanionDestination()?.id) render()
        statusText?.text = if (car.parked) {
            if (drivePreview != null) "P  주차 중 · D단 미리보기" else "P  주차 중"
        }
            else if (car.gear in drivingGears) "D  주행 중 · 화면 조작 제한" else "차량 상태 확인 중"
        clockText?.text = String.format(Locale.KOREA, "%02d:%02d", elapsed / 60_000, elapsed / 1000 % 60)
        windowText?.text = windowPositionLabel()
        doorText?.text = positionLabel(car.doorPosition, false)
        val (tempCaption, tempValue) = cabinReading()
        temperatureText?.text = tempValue
        temperatureCaption?.text = tempCaption
        vehicleNotice?.text = when {
            (car.windowPosition ?: 0) > 0 -> "$dogLabel 자리 쪽 창문이 열려 있어요."
            dog?.motionSickness == true -> "차멀미가 잦은 반려견이에요."
            else -> ""
        }
        vehicleNotice?.visibility = if (vehicleNotice?.text.isNullOrBlank()) View.GONE else View.VISIBLE
        restText?.text = when {
            clock.resting -> "쉬고 나면 ‘휴식 마쳤어요’를 눌러주세요."
            !clock.active -> "동승을 시작하면 주행 시간을 기록해요."
            !clock.counting && elapsed == 0L && clock.breakCompleted -> "휴식을 기록했어요. 출발하면 시간을 다시 세요."
            !clock.counting && elapsed == 0L -> "차가 출발하면 주행 시간을 세기 시작해요."
            else -> stage?.let { "${it.minutes}분 · ${it.title}" } ?: "90분에 다음 쉼터를 알려드려요."
        }
        val nextStage = RestStage.entries.firstOrNull { elapsed < it.threshold(shortDemo) }
        restCountdown?.text = if (!clock.active) "동승 시작 후 계산해요"
            else if (nextStage == null) "휴식 권장 시간이 됐어요"
            else {
                val remaining = nextStage.threshold(shortDemo) - elapsed
                val time = if (shortDemo) "${(remaining + 999) / 1_000}초(시연)"
                    else "${(remaining + 59_999) / 60_000}분"
                "${nextStage.minutes}분 안내까지  $time"
            }
        val destination = navigation.activeDestination
        val routingTarget = navigation.companionPlace ?: course.lastOrNull()
        val remaining = destination?.takeIf { it.name == routingTarget?.name && it.remainTime > 0 }?.remainTime
        routeRemainingText?.text = remaining?.let { "${(it + 59) / 60}분" } ?: "—"
        routeArrivalText?.text = remaining?.let {
            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.KOREA)
                .format(Date(System.currentTimeMillis() + it * 1000L)) + " 도착 예정"
        } ?: "내비 안내 정보 대기"
        navigationText?.text = navigation.note.ifBlank {
            if (destination != null && destination.name == course.lastOrNull()?.name && destination.remainTime > 0)
                "내비 예상 ${(destination.remainTime + 59) / 60}분"
            else ""
        }
        locationText?.text = navigation.location?.let {
            if (navigation.activeDestination?.name in Places.all.map(Place::name)) "반려견 조건과 내비 목적지 방향 기준"
            else "반려견 조건과 현재 위치 기준"
        }
            ?: "울산 시범 목록 · 차량 위치 응답 대기"
        startRouteButton?.isEnabled = car.parked && course.isNotEmpty() &&
            (course.any { !it.routable } || (navigation.connected && !navigation.pending))
        val demoFriends = demoFriends(displayedRoute)
        presenceText?.text = demoFriends?.let { "시연 · ${it}마리가 같은 곳으로 가는 중" }
            ?: presence.otherDogs?.takeIf { it > 0 }?.let { "다른 반려견 ${it}마리도 같은 곳으로 가는 중" }
            ?: if (presence.otherDogs == 0) "같은 곳으로 가는 다른 반려견이 아직 없어요."
            else if (store.shareDestination && navigation.guidedPlace != null) "동행 인원을 확인 중이에요."
            else if (renderedSideDestination != null || routeRemainingText != null) {
                if (navigation.guidedPlace != null) "길 안내 중"
                else if (routingTarget == null) "등록한 코스가 없어요"
                else "기본 내비에서 경로를 확인 중이에요."
            }
            else if (store.shareDestination) "내비 안내가 시작되면 동행을 확인해요."
            else "이 목적지 동행을 확인하려면 인원 공유를 켜주세요."
        routeDogView?.updateMotion(car.parked &&
            (page == Page.NAV_CARD || renderedSideDestination != null || drivePreview in listOf(DriveScene.NORMAL, DriveScene.NIGHT)))
        if (zipVisible) zipFrontend.update(zipState())
        openDriveCompanion()
        voiceRequest?.let {
            if (car.gear != null && car.restricted != null) {
                voiceRequest = null
                handleVoice(it)
            }
        }
    }

    private fun refreshNavigationCompanion(driving: Boolean) {
        val place = navigation.companionPlace
        val guided = navigation.guidedPlace
        if (!car.parked || parkedDriveDemoDestination != place?.id) parkedDriveDemoDestination = null
        if (observedDestination != place?.id) {
            observedDestination = place?.id
            movedOnTrip = false
            navStopped = false
        }
        if (place != null && driving) movedOnTrip = true
        val stoppedNow = place != null && movedOnTrip && car.parked
        if (stoppedNow && !navStopped && activityResumed && drivePreview == null) navCareOpen = true
        navStopped = stoppedNow
        if (openNavCareRequested && car.gear != null && car.restricted != null) {
            openNavCareRequested = false
            if (car.parked) {
                navCareOpen = true
                drivePreview = null
            }
        }
        presence.update(guided?.id, companions.size, store.shareDestination)
        companionNotice.update(place, guided != null, navStopped, presence.otherDogs, clock.active)
        if (driveCompanionOpen) updateDriveCompanion(place)
    }

    private fun updateDriveCompanion(place: Place?) {
        val pet = dog
        val parkedDemo = car.parked && parkedDriveDemoDestination != null && parkedDriveDemoDestination == place?.id
        val demoFriends = demoFriends(place)
        driveCompanionState.value = if (place != null && pet != null) DriveCompanionState(
            dogName = pet.name, coat = pet.coat, destination = place.name,
            otherDogs = demoFriends ?: presence.otherDogs,
            friendsDemo = demoFriends != null,
            parked = car.parked, driving = car.gear != VehicleGear.GEAR_REVERSE && car.gear in drivingGears,
            labelLimit = car.maxRestrictedLabelLength, darkMode = darkAppearance,
            parkedDemo = if (parkedDemo) zipState(parkedDriveDemo = true) else null
        ) else null
    }

    private fun demoFriends(place: Place?, parkedDemo: Boolean = false): Int? {
        val useExampleCounts = shortDemo || parkedDemo || (BuildConfig.CONTEST_SHOWCASE && !presence.available)
        if (!useExampleCounts) return null
        return when (place?.id) {
            "ganjeolgot" -> 7
            "taehwa" -> 4
            "anian" -> 2
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun openDriveCompanion() {
        if (!activityResumed || driveCompanionOpen || parkedDialog != null ||
            cabinControls != null || windowRaise != null || alertScreen != null) return
        val place = sideCompanionDestination() ?: return
        driveCompanionOpen = true
        updateDriveCompanion(place)
        startActivityForResult(Intent(this, DriveCompanionActivity::class.java), DRIVE_COMPANION)
    }

    private fun refreshRestAlerts(elapsed: Long) {
        if (!clock.active || clock.resting || stage == RestStage.BREAK || navigation.restWaypoint != null) {
            if (pendingAlert != null || alertScreen != null) clearRestAlert()
        } else {
            val latest = RestAlert.entries.lastOrNull { elapsed >= it.threshold(shortDemo) }
            if (latest != null && (handledAlert?.ordinal ?: -1) < latest.ordinal) {
                handledAlert = latest
                pendingAlert = latest
                reminders.showAlert(latest)
            }
        }
        if (!car.parked && alertScreen != null) alertScreen = null
        if (car.parked && (activityResumed || openAlertRequested) && parkedDialog == null) {
            if (pendingAlert != null) {
                alertScreen = pendingAlert
                nativeProfile = false
                drivePreview = null
                stopCareAudio()
            } else if (openAlertRequested) {
                page = Page.CARE
                navCareOpen = false
                render()
            }
            openAlertRequested = false
        }
    }

    private fun clearRestAlert() {
        pendingAlert = null
        alertScreen = null
        reminders.clearAlert()
    }

    private fun dismissRestAlert() {
        clearRestAlert()
        openAlertRequested = false
        redraw()
    }

    private fun chooseRestWaypoint() {
        if (alertScreen == null) {
            category = PlaceKind.REST
            show(Page.PLACES)
            return
        }
        val hasDestination = course.lastOrNull()?.routable == true || navigation.activeDestination != null
        val options = restOptions()
        if (!hasDestination || options.isEmpty()) {
            clearRestAlert()
            category = if (options.isNotEmpty()) PlaceKind.FIELD else PlaceKind.REST
            show(Page.PLACES)
            return
        }
        dialog(parkedDialogBuilder("쉬어갈 곳 고르기")
            .setNegativeButton("취소", null)
            .setItems(options.map { "${it.name} · ${it.area}" }.toTypedArray()) { _, index ->
                if (!car.parked) return@setItems
                val stop = options[index]
                if (navigation.addRestWaypoint(stop)) {
                    dismissRestAlert()
                    inform("기본 내비에 경유지 추가를 요청했어요.")
                } else if (navigation.activeDestination == null && course.size == 1) {
                    store.courseIds = listOf(stop.id, course.last().id)
                    clearRestAlert()
                    show(Page.PLACES)
                } else {
                    inform("기존 경유지와 안내 상태를 기본 내비에서 확인해 주세요.")
                }
            })
    }

    private fun restOptions(): List<Place> =
        listOf(PlaceKind.REST, PlaceKind.FIELD, PlaceKind.CHARGE)
            .flatMap { recommendations(false, it, allowUncertain = false) }
            .filter(Place::routable)
            .filterNot { it.id == course.lastOrNull()?.id || it.name == navigation.activeDestination?.name }
            .sortedBy { distance(it, navigation.companionPlace) }
            .take(2)

    private fun show(target: Page) {
        if (!car.parked) return
        clearWindowRaise()
        closeCabinControls()
        if (target != Page.CARE) stopCareAudio()
        drivePreview = null
        parkedDriveDemoDestination = null
        navCareOpen = false
        nativeProfile = false
        seatProfile = false
        page = target
        if (target == Page.PLACES) navigation.refresh()
        redraw()
    }

    private fun redraw() {
        render()
        refresh()
    }

    private fun sideCompanionDestination(): Place? {
        if (!car.parked || navCareOpen || drivePreview != null || page != Page.PLACES || sideCompanionDismissed ||
            !isInMultiWindowMode || resources.configuration.screenWidthDp >= WIDE_WIDTH_DP || dog == null) return null
        return navigation.companionPlace
    }

    private fun dismissSideCompanion() {
        sideCompanionDismissed = true
        redraw()
    }
    private fun render() {
        renderedParked = car.parked
        renderedAlertScreen = alertScreen
        val scene = currentDriveScene()
        renderedDriveScene = scene
        val sideDestination = sideCompanionDestination()
        val sideDog = dog
        val showSideCompanion = sideDestination != null && sideDog != null
        renderedSideDestination = sideDestination?.id
        wideComposition = resources.configuration.screenWidthDp >= WIDE_WIDTH_DP
        statusText = null
        clockText = null
        restText = null
        restCountdown = null
        doorText = null
        windowText = null
        temperatureText = null
        temperatureCaption = null
        vehicleNotice = null
        navigationText = null
        locationText = null
        presenceText = null
        routeRemainingText = null
        routeArrivalText = null
        routeDogView = null
        startRouteButton = null
        val dark = screenDark
        ui = RoundUi(this, dark, dog?.coat ?: Coat.BROWN)
        window.setBackgroundDrawable(ColorDrawable(ui.paper))
        window.statusBarColor = ui.paper
        window.navigationBarColor = ui.paper
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val zipScreen = if (car.parked && !nativeProfile) zipScreen(scene, dark, showSideCompanion) else null
        val previouslyZipVisible = zipVisible
        zipVisible = zipScreen != null
        if (zipScreen != null) {
            val web = zipFrontend.view
            if (findViewById<FrameLayout>(android.R.id.content).getChildAt(0) !== web) setContentView(web)
            zipFrontend.present(zipScreen, !wideComposition)
            zipFrontend.update(zipState())
            return
        }
        if (previouslyZipVisible) zipFrontend.hide()
        val panel = ui.column().apply {
            setBackgroundColor(ui.paper)
            val margin = ui.dp(if (wideComposition) 24 else 20)
            setPadding(margin, margin, margin, margin)
        }
        val shell = ui.row()
        val showNavigation = car.parked && scene == null && !showSideCompanion
        if (showNavigation && wideComposition) {
            shell.addView(buildSectionNavigation(false), LinearLayout.LayoutParams(ui.dp(112), -1))
            ui.gap(shell, 20)
        }
        if (wideComposition && scene == null && page !in listOf(Page.PLACES, Page.JOURNAL, Page.NAV_CARD)) {
            shell.addView(buildCompanion(), LinearLayout.LayoutParams(ui.dp(330), -1))
            ui.gap(shell, 20)
        }
        val scroll = object : ScrollView(this) {
            override fun onInterceptTouchEvent(event: MotionEvent) =
                car.parked && super.onInterceptTouchEvent(event)
            override fun onTouchEvent(event: MotionEvent) =
                car.parked && super.onTouchEvent(event)
        }.apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = car.parked
        }
        content = ui.column().apply { setPadding(0, 0, 0, ui.dp(12)) }
        scroll.addView(content)
        shell.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        panel.addView(shell, LinearLayout.LayoutParams(-1, 0, 1f))
        if (showNavigation && !wideComposition) {
            ui.gap(panel, 10)
            panel.addView(buildSectionNavigation(true))
        }
        setContentView(panel)
        val header = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        val currentTitle = when {
            scene != null -> scene.title
            showSideCompanion -> "내비 동행"
            page == Page.HOME -> when {
                clock.resting -> "쉬어가기"
                clock.active -> "오늘의 동승"
                else -> "출발 준비"
            }
            page == Page.PLACES -> "산책길 찾기"
            page == Page.ARRIVAL -> "도착 · 하차 준비"
            else -> page.label
        }
        statusText = ui.text("", 17, true).apply {
            background = ui.shape(ui.surface, 28)
            setPadding(ui.dp(17), ui.dp(13), ui.dp(17), ui.dp(13))
        }
        header.addView(statusText)
        ui.gap(header, 12)
        header.addView(ui.text(currentTitle, 20, true).apply { gravity = Gravity.END }, ui.weight())
        content.addView(header)
        ui.gap(content, 16)
        if (sideDestination != null && sideDog != null) {
            val actions = ui.column()
            actions.addView(navigationCompanionCard(sideDestination, sideDog))
            ui.gap(actions, 12)
            actions.addView(ui.button("산책길 보기") { dismissSideCompanion() })
            val cardWidth = (resources.configuration.screenWidthDp - 40).coerceIn(0, 520)
            content.addView(actions, LinearLayout.LayoutParams(ui.dp(cardWidth), -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
            return
        }
        if (!wideComposition && scene == null && page in listOf(Page.HOME, Page.CARE, Page.PROFILE, Page.ARRIVAL)) {
            content.addView(buildCompanion())
            ui.gap(content, 14)
        }
        if (scene != null) {
            buildDriving(scene, dark)
            if (car.parked && drivePreview != null) {
                ui.gap(content, 12)
                content.addView(ui.button("미리보기 닫기") { show(Page.CARE) })
            } else if (car.parked && navCareOpen) {
                ui.gap(content, 12)
                val actions = ui.row()
                actions.addView(ui.button("기본 내비로 돌아가기", true) {
                    if (car.parked && !navigation.showMap()) inform("기본 내비에 연결할 수 없어요.")
                }, ui.weight())
                ui.gap(actions, 8)
                actions.addView(ui.button(if (clock.canStartBreak) "휴식 시작" else "우리 집") {
                    show(Page.HOME)
                    if (clock.canStartBreak) startBreak()
                }, ui.weight())
                content.addView(actions)
            }
        }
        else when (page) {
            Page.HOME -> buildHome()
            Page.PLACES -> buildPlaces()
            Page.CARE -> buildCare()
            Page.JOURNAL -> buildJournal()
            Page.PROFILE -> buildProfiles()
            Page.ARRIVAL -> buildArrival()
            Page.NAV_CARD -> buildNavigationCard()
        }
    }

    private fun zipScreen(scene: DriveScene?, dark: Boolean, side: Boolean): String? {
        val prefix = if (wideComposition) "B_" else "P_"
        alertScreen?.let { return prefix + it.screen + if (dark) "_Night" else "" }
        if (side) return prefix + "Drive" + if (dark) "_Night" else ""
        if (scene != null) {
            val base = when (scene) {
                DriveScene.REST -> "Rest"
                DriveScene.WINDOW -> "Alert"
                DriveScene.NORMAL, DriveScene.NIGHT -> "Drive"
                DriveScene.REST_TEN -> RestAlert.TEN.screen
                DriveScene.REST_FIVE -> RestAlert.FIVE.screen
                DriveScene.HIGHWAY -> "Highway"
            }
            return prefix + base + if (dark) "_Night" else ""
        }
        return when (page) {
            Page.HOME -> prefix + "Main"
            Page.PLACES -> prefix + "Places"
            Page.CARE -> prefix + "Calm"
            Page.PROFILE -> prefix + if (!wideComposition && seatProfile) "Profile_Seat" else "Profile"
            Page.ARRIVAL -> prefix + "Arrive"
            Page.JOURNAL, Page.NAV_CARD -> null
        }
    }

    private fun cabinReading(): Pair<String, String> {
        fun format(value: Float): String = (if (value % 1f == 0f) value.toInt().toString()
            else String.format(Locale.KOREA, "%.1f", value)) + "°C"
        car.temperature?.let { value ->
            val target = car.targetTemperature?.let { (temp, zone) -> "$zone 설정 온도 " + format(temp) }
            return (target ?: "차량에서 읽은 실내 온도") to format(value)
        }
        car.targetTemperature?.let { (value, zone) -> return "$zone 에어컨 설정 온도" to format(value) }
        return "차량 온도 응답 대기" to "—"
    }

    private fun zipState(parkedDriveDemo: Boolean = false): JSONObject {
        val (tempCaption, temp) = cabinReading()
        val pet = dog
        val route = displayedRoute
        val demoFriends = demoFriends(route, parkedDriveDemo)
        val current = navigation.activeDestination?.takeIf { it.name == route?.name && it.remainTime > 0 }
        val elapsed = clock.elapsed(SystemClock.elapsedRealtime())
        val alertMinutes = if (shortDemo) when (alertScreen) {
            RestAlert.TEN -> 110
            RestAlert.FIVE -> 115
            null -> 0
        } else (elapsed / 60_000).toInt()
        val next = RestStage.entries.firstOrNull { elapsed < it.threshold(shortDemo) }
        val results = if (!parkedDriveDemo && page == Page.PLACES && renderedSideDestination == null && pet != null) {
            val destination = course.lastOrNull()
            (recommendations(false) + recommendations(true)).map { place ->
                JSONObject().put("id", place.id).put("name", place.name).put("area", place.area)
                    .put("conditions", place.conditions()).put("demo", place.sample)
                    .put("walk", place.chargeWalk?.walk).put("connectors", place.chargeWalk?.connectors)
                    .put("demoFriends", demoFriends(place))
                    .put("chargeWaypoint", place.kind == PlaceKind.CHARGE && destination != null && destination.kind != PlaceKind.CHARGE)
                    .put("selected", course.any { it.id == place.id })
                    .put("canAdd", place.routable || place.eligibility(companions,
                        place.kind == PlaceKind.CAFE && indoorOnly) == Eligibility.MATCH)
                    .put("latitude", place.latitude).put("longitude", place.longitude)
            }
        } else emptyList()
        val primary = when {
            clock.resting -> "휴식 마쳤어요"
            clock.canStartBreak -> "휴식 시작"
            clock.active -> "산책길 보기"
            pet == null -> "반려견 등록"
            else -> "좋아, 출발!"
        }
        return JSONObject()
            .put("pet", pet?.name ?: "우리 강아지").put("hasDog", pet != null)
            .put("breed", pet?.breed.orEmpty()).put("dogInfo", pet?.description.orEmpty())
            .put("ageWeight", pet?.let { "${it.age}살 · ${formatWeight(it.weight)}kg" }.orEmpty())
            .put("seat", seat.label).put("seatKey", seat.name).put("boarding", pet?.boarding?.name.orEmpty())
            .put("coat", pet?.coat?.name ?: Coat.BROWN.name)
            .put("temp", temp).put("tempCaption", tempCaption).put("currentTemp", car.temperature)
            .put("window", windowPositionLabel()).put("door", positionLabel(car.doorPosition, false))
            .put("occupied", car.occupied).put("boardingPrompt", boardingPrompt)
            .put("ride", clock.active).put("resting", clock.resting).put("homePrimary", primary)
            .put("canBreak", clock.canStartBreak).put("elapsed", "${elapsed / 60_000}분")
            .put("restDue", stage?.minutes ?: 90)
            .put("restRemaining", if (!clock.active || next == null) "—"
                else if (shortDemo) "${(next.threshold(true) - elapsed + 999) / 1000}초"
                else "${(next.threshold(false) - elapsed + 59_999) / 60_000}분")
            .put("restStop", course.firstOrNull { it.kind == PlaceKind.REST || it.kind == PlaceKind.CHARGE }?.name.orEmpty())
            .put("destination", route?.name.orEmpty())
            .put("guideTime", current?.let { "${(it.remainTime + 59) / 60}분" } ?: "—")
            .put("guideArrival", current?.let {
                DateFormat.getTimeInstance(DateFormat.SHORT, Locale.KOREA)
                    .format(Date(System.currentTimeMillis() + it.remainTime * 1000L))
            }.orEmpty())
            .put("guided", navigation.guidedPlace != null).put("navNote", navigation.note)
            .put("arrived", navigation.arrival != null)
            .put("friends", if (parkedDriveDemo) demoFriends else demoFriends ?: presence.otherDogs)
            .put("friendsDemo", demoFriends != null)
            .put("share", store.shareDestination).put("canShare", presence.available)
            .put("places", JSONArray(results)).put("visits", store.visits.size)
            .put("category", category?.name ?: "ALL").put("indoor", indoorOnly)
            .put("uncertain", includeUncertain).put("style", (store.mood ?: pet?.style)?.name.orEmpty())
            .put("course", JSONArray(course.map(Place::name))).put("routable", course.all(Place::routable))
            .put("navEnabled", navigation.connected && !navigation.pending)
            .put("leash", leashReady).put("bags", wasteBagsReady).put("water", waterReady)
            .put("audio", store.careAudio != null).put("playing", carePlayer != null)
            .put("preview", drivePreview != null).put("parkedCare", navCareOpen)
            .put("restAlertMinutes", alertMinutes).put("restAlertDue", RestStage.BREAK.minutes)
            .put("restChoices", JSONArray(if (alertScreen != null) restOptions().map { stop ->
                JSONObject().put("name", stop.name).put("area", stop.area)
            } else emptyList<JSONObject>()))
            .put("side", renderedSideDestination != null).put("drivePreviewEnabled", drivePreviewEnabled)
            .put("parkedDriveDemo", parkedDriveDemo)
            .put("darkMode", screenDark).put("appearance", store.appearance.name)
            .put("cabin", cabinState() ?: JSONObject.NULL)
            .put("cabinAuto", if (dog != null) autoCabin.state(autoCabinEnabled) else JSONObject.NULL)
            .put("windowRaise", if (windowRaise?.target == null) windowRaiseState() ?: JSONObject.NULL else JSONObject.NULL)
            .put("reduceMotion", !android.animation.ValueAnimator.areAnimatorsEnabled())
    }

    private fun handleZipAction(action: String, value: String) {
        if (!car.parked || !zipVisible) return
        when (action) {
            "home" -> show(Page.HOME)
            "places" -> show(Page.PLACES)
            "care" -> show(Page.CARE)
            "profile" -> show(Page.PROFILE)
            "appearance" -> Appearance.entries.firstOrNull { it.name == value }?.let {
                if (store.appearance != it) { store.appearance = it; redraw() }
            }
            "journal" -> show(Page.JOURNAL)
            "arrival" -> show(Page.ARRIVAL)
            "navPreview" -> show(Page.NAV_CARD)
            "homePrimary" -> when {
                clock.resting -> completeBreak()
                clock.canStartBreak -> startBreak()
                clock.active -> show(Page.PLACES)
                dog == null -> show(Page.PROFILE)
                else -> startRide()
            }
            "homeSecondary" -> when {
                clock.resting -> { clock.cancelBreak(); redraw() }
                clock.active -> dialog(parkedDialogBuilder("동승을 마칠까요?")
                    .setNegativeButton("계속하기", null).setPositiveButton("동승 마치기") { _, _ -> finishRide() })
                boardingPrompt -> { boardingPrompt = false; redraw() }
                else -> show(Page.PLACES)
            }
            "filter" -> {
                category = PlaceKind.entries.firstOrNull { it.name == value }
                if (category == PlaceKind.CAFE) indoorOnly = true
                redraw()
            }
            "placeOptions" -> showPlaceOptions()
            "indoor" -> { indoorOnly = !indoorOnly; redraw() }
            "uncertain" -> { includeUncertain = !includeUncertain; redraw() }
            "style" -> PlayStyle.entries.firstOrNull { it.name == value }?.let { store.mood = it; redraw() }
            "place" -> Places.get(value)?.let(::placeDetails)
            "addPlace" -> Places.get(value)?.let { place -> toggleCoursePlace(place, course.any { it.id == value }) }
            "route" -> requestCourse()
            "clearCourse" -> { store.courseIds = emptyList(); redraw() }
            "share" -> if (presence.available) { store.shareDestination = !store.shareDestination; refresh() }
            "profileSeats" -> if (page == Page.PROFILE) {
                seatProfile = value == "yes"
                redraw()
            }
            "editDog" -> editDog(dog?.takeIf { it.id != Dog.sample.id })
            "manageDogs" -> if (page == Page.PROFILE) {
                nativeProfile = true
                redraw()
            }
            "seat" -> Seat.entries.firstOrNull { it.name == value }?.let { next -> updateDog { it.copy(seat = next) } }
            "boarding" -> Boarding.entries.firstOrNull { it.name == value }?.let { next -> updateDog { it.copy(boarding = next) } }
            "coat" -> Coat.entries.firstOrNull { it.name == value }?.let { next -> updateDog { it.copy(coat = next) } }
            "check" -> {
                when (value) {
                    "leash" -> leashReady = !leashReady
                    "bags" -> wasteBagsReady = !wasteBagsReady
                    "water" -> waterReady = !waterReady
                }
                refresh()
            }
            "recordVisit" -> if (dog == null) show(Page.PROFILE)
                else course.lastOrNull()?.let { editVisit(it) } ?: chooseVisit()
            "careAudio" -> pickFile("audio/*") { uri -> stopCareAudio(); store.careAudio = uri; redraw() }
            "carePlay" -> toggleCarePlayback()
            "previewDrive" -> openDrivePreview()
            "endPreview" -> show(Page.CARE)
            "returnNav" -> if (!navigation.showMap()) inform("기본 내비에 연결할 수 없어요.")
            "closeSide" -> dismissSideCompanion()
            "restOptions" -> { category = PlaceKind.REST; show(Page.PLACES) }
            "dismissRestAlert" -> if (drivePreview != null) show(Page.CARE) else dismissRestAlert()
            "chooseRestWaypoint" -> chooseRestWaypoint()
            "cabin" -> CabinGroup.entries.firstOrNull { it.name == value }?.let(::showCabinControls)
            "cabinAutoToggle" -> if (dog != null && value in setOf("on", "off")) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("autoCabin", value == "on").apply()
                autoCabin.reset()
                syncCabinControls()
            }
            "cabinClose" -> closeCabinControls()
            "cabinAdjust" -> adjustCabinControl(value)
            "windowPresetSave" -> saveWindowPreset(value)
            "windowAlert" -> openWindowRaise()
            "windowAlertReady" -> startWindowRaiseCountdown()
            "windowRaiseNow" -> requestWindowRaise()
            "windowKeep" -> dismissWindowRaise()
        }
    }

    private fun updateDog(change: (Dog) -> Dog) {
        val pet = dog ?: return
        if (pet.id == Dog.sample.id) { editDog(null); return }
        val updated = change(pet)
        store.save(updated)
        if (pet.seat != updated.seat) car.watchSeat(seat)
        redraw()
    }

    private fun showPlaceOptions() {
        val options = form()
        val kinds = listOf<PlaceKind?>(null) + PlaceKind.entries
        val kind = pick(options, "장소 유형", kinds.map { it?.label ?: "전체" }, kinds.indexOf(category))
        val mood = pick(options, "다른 강아지와 만나는 방식", PlayStyle.entries.map(PlayStyle::label),
            (store.mood ?: dog?.style ?: PlayStyle.GREET).ordinal)
        val indoor = check("카페 실내 동반만", indoorOnly).also(options::addView)
        val uncertain = check("동반 조건 미확인 보기", includeUncertain).also(options::addView)
        val share = check("같은 목적지로 가는 반려견 수 공유", store.shareDestination)
        if (presence.available) options.addView(share)
        dialog(parkedDialogBuilder("추천 조건").setView(formScroll(options))
            .setNegativeButton("취소", null).setPositiveButton("적용") { _, _ ->
                if (!car.parked) return@setPositiveButton
                category = kinds[kind.selectedItemPosition]
                store.mood = PlayStyle.entries[mood.selectedItemPosition]
                indoorOnly = indoor.isChecked
                includeUncertain = uncertain.isChecked
                if (presence.available) store.shareDestination = share.isChecked
                redraw()
            })
    }

    private fun showCabinControls(group: CabinGroup) {
        if (!car.parked || dog == null || !zipVisible) return
        if (group == CabinGroup.CLIMATE) autoCabin.cancel("공조를 직접 조작하고 있어요.")
        closeCabinControls()
        cabinGroup = group
        cabinControls = car.cabinControls(seat, group) {
            if (car.parked && cabinControls != null) syncCabinControls()
        }
        syncCabinControls()
    }

    private fun closeCabinControls() {
        val controls = cabinControls ?: return
        if (windowRaise?.target != null) clearWindowRaise()
        cabinControls = null
        cabinGroup = null
        controls.close()
        syncCabinControls()
    }

    private fun syncCabinControls() {
        if (zipVisible) zipFrontend.update(zipState())
    }

    private fun adjustCabinControl(command: String) {
        val controls = cabinControls ?: return
        if (!car.parked) return
        val index = command.substringBefore(':').toIntOrNull() ?: return
        val target = command.substringAfter(':', "").toFloatOrNull()?.takeIf(Float::isFinite) ?: return
        val control = controls.rows.getOrNull(index) ?: return
        controls.adjust(control, target)
    }

    private fun cabinState(): JSONObject? {
        val group = cabinGroup ?: return null
        val controls = cabinControls ?: return null
        val state = JSONObject().put("title", group.title).put("rows", JSONArray(controls.rows.mapIndexed { index, row ->
            JSONObject().put("id", index).put("title", row.title).put("kind", row.kind.name)
                .put("zone", row.zone).put("value", row.value ?: JSONObject.NULL)
                .put("min", row.min).put("max", row.max).put("step", row.step)
                .put("canSet", row.canSet).put("available", row.available ?: JSONObject.NULL)
                .put("busy", row.busy).put("message", row.message ?: JSONObject.NULL)
        }))
        val window = windowPositionControl()
        if (window != null && window.max > window.min) {
            fun option(target: Float) = JSONObject().put("target", target)
                .put("percent", windowOpenPercent(target, window.min, window.max))
            state.put("windowPresets", JSONArray(store.windowPresets.map { option(windowTarget(window, it)) }))
            state.put("windowChoices", JSONArray((0..100).map { windowTarget(window, it) }.distinct().map(::option)))
            state.put("windowRaise", windowRaiseState() ?: JSONObject.NULL)
        }
        return state
    }

    private fun windowPositionControl() =
        cabinControls?.rows?.firstOrNull { it.kind == CabinControls.Kind.WINDOW_POSITION }

    private fun windowTarget(control: CabinControls.Control, percent: Int): Float =
        (control.min + (control.max - control.min) * percent / 100f).roundToInt().toFloat()
            .coerceIn(control.min, control.max)

    private fun saveWindowPreset(command: String) {
        val control = windowPositionControl() ?: return
        val index = command.substringBefore(':').toIntOrNull() ?: return
        val percent = command.substringAfter(':', "").toIntOrNull() ?: return
        val presets = store.windowPresets
        if (!car.parked || windowRaise != null || index !in presets.indices || percent !in 0..100) return
        if (windowOpenPercent(windowTarget(control, percent), control.min, control.max) != percent) return
        store.windowPresets = presets.mapIndexed { i, previous -> if (i == index) percent else previous }
        syncCabinControls()
    }

    private fun openWindowRaise() {
        val control = windowPositionControl() ?: return
        val value = control.value ?: return
        val target = windowTarget(control, 10)
        val percent = windowOpenPercent(target, control.min, control.max) ?: return
        if (!car.parked || !control.canSet || value <= target) return
        windowRaise = WindowRaise(target, percent)
        syncCabinControls()
    }

    private fun startWindowRaiseCountdown() {
        val request = windowRaise ?: return
        if (!car.parked || request.deadline != null || request.submitted) return
        request.deadline = SystemClock.elapsedRealtime() + 3_000L
        main.postDelayed(windowRaiseTick, 3_000L)
        syncCabinControls()
    }

    private fun requestWindowRaise() {
        val request = windowRaise ?: return
        if (!car.parked || !activityResumed || !hasWindowFocus()) {
            if (car.parked) dismissWindowRaise() else clearWindowRaise()
            return
        }
        if (request.submitted && windowRaisePhase(request) != "failed") return
        main.removeCallbacks(windowRaiseTick)
        request.submitted = true
        request.failed = false
        val target = request.target
        if (target != null) {
            val control = windowPositionControl()
            val value = control?.value
            if (control == null || value == null) request.failed = true
            else if (value > target) {
                if (control.canSet) cabinControls?.adjust(control, target) else request.failed = true
            }
        }
        syncCabinControls()
    }

    private fun windowRaisePhase(request: WindowRaise): String {
        if (!request.submitted) return "countdown"
        if (request.target == null) return "complete"
        if (request.failed) return "failed"
        val control = windowPositionControl() ?: return "failed"
        if (control.value?.let { it <= request.target } == true) return "complete"
        if (control.message != null) return "failed"
        return "applying"
    }

    private fun windowRaiseState(): JSONObject? {
        val request = windowRaise ?: return null
        val seconds = request.deadline?.let { ((it - SystemClock.elapsedRealtime() + 999L) / 1_000L).coerceIn(0, 3) } ?: 3
        return JSONObject().put("seconds", seconds).put("phase", windowRaisePhase(request))
            .put("preview", request.target == null).put("percent", request.percent ?: JSONObject.NULL)
    }

    private fun clearWindowRaise() {
        main.removeCallbacks(windowRaiseTick)
        windowRaise = null
    }

    private fun dismissWindowRaise() {
        clearWindowRaise()
        if (drivePreview == DriveScene.WINDOW) show(Page.CARE) else syncCabinControls()
    }

    private fun buildSectionNavigation(compact: Boolean): LinearLayout {
        val tabs = if (compact) ui.row() else ui.column()
        tabs.gravity = Gravity.CENTER_HORIZONTAL
        tabs.background = ui.shape(ui.surface, 36)
        val inset = ui.dp(6)
        tabs.setPadding(inset, inset, inset, inset)
        val routes = listOf(
            Page.HOME to R.drawable.icon_home, Page.PLACES to R.drawable.icon_place,
            Page.CARE to R.drawable.icon_care, Page.JOURNAL to R.drawable.ic_rest_notification,
            Page.PROFILE to designArt(R.drawable.design_choco_face, dog?.coat?.face ?: Coat.BROWN.face)
        )
        if (!compact) {
            tabs.addView(ui.image(R.drawable.ic_rest_notification).apply { setColorFilter(ui.accent) },
                LinearLayout.LayoutParams(ui.dp(34), ui.dp(66)))
        }
        routes.forEachIndexed { index, (target, icon) ->
            if (index > 0) ui.gap(tabs, if (compact) 2 else 6)
            val active = page == target || (page == Page.NAV_CARD && target == Page.PLACES)
            val color = if (active) ui.inverse else ui.ink
            val tab = ui.column().apply {
                gravity = Gravity.CENTER
                isSelected = active
                isFocusable = true
                contentDescription = target.label
                if (active) background = ui.shape(ui.primary, 26)
                setOnClickListener { show(target) }
            }
            tab.addView(ui.image(icon).apply { if (target != Page.PROFILE) setColorFilter(color) },
                LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)))
            ui.gap(tab, 4)
            tab.addView(ui.text(target.label, 15, true, color).apply { gravity = Gravity.CENTER })
            tabs.addView(tab, if (compact) LinearLayout.LayoutParams(0, ui.dp(68), 1f)
                else LinearLayout.LayoutParams(-1, ui.dp(76)))
        }
        return tabs
    }

    private fun designArt(chocoArt: Int, fallback: Int): Int =
        if (dog?.coat == Coat.BROWN) chocoArt else fallback

    private fun currentDriveScene(): DriveScene? {
        if (car.parked && !navCareOpen) return drivePreview
        if ((car.windowPosition ?: 0) > 0) return DriveScene.WINDOW
        if (stage != null) return DriveScene.REST
        return if (screenDark) DriveScene.NIGHT else DriveScene.NORMAL
    }

    private fun buildCompanion(): LinearLayout {
        val pet = dog
        val coat = pet?.coat ?: Coat.BROWN
        val message = when {
            page == Page.ARRIVAL -> "내리기 전 확인해요"
            page == Page.CARE -> "안심하고 기다려요"
            page == Page.PROFILE -> "우리 강아지"
            boardingPrompt -> "$dogLabel, 탔나요?"
            clock.resting -> "잠깐 쉬어가요"
            clock.active -> "함께 가요!"
            else -> "오늘 어디 갈까요?"
        }
        val art = when (page) {
            Page.ARRIVAL -> designArt(R.drawable.design_choco_arrive, coat.body)
            Page.CARE -> designArt(R.drawable.design_choco_calm, coat.resting)
            Page.PROFILE -> designArt(R.drawable.design_choco_profile, coat.body)
            else -> designArt(R.drawable.design_choco_ready, coat.body)
        }
        val copy = ui.panel(padding = 17)
        if (page == Page.HOME && (boardingPrompt || clock.active)) {
            copy.addView(ui.text(if (clock.active) "동승 확인 완료" else "동승 확인", 16, true, ui.accent))
            ui.gap(copy, 4)
        }
        copy.addView(ui.text(message, if (wideComposition) 31 else 27, true))
        val artView = ui.image(art, "$dogLabel 캐리커처")
        val details = ui.panel(padding = 17)
        if (pet != null) details.addView(ui.text(pet.description, 16, true))
        if (page == Page.HOME && pet != null) {
            if (boardingPrompt) addBoardingRow(details, "등록한 자리 쪽 문이 열렸다 닫혔어요.")
            car.occupied?.let { addBoardingRow(details, if (it) "등록한 자리에 착석 신호가 있어요." else "등록한 자리에 착석 신호가 없어요.") }
            addBoardingRow(details, if (clock.active) "보호자가 동승을 확인했어요." else "출발 전에 동승을 확인해 주세요.")
        }
        return if (wideComposition) {
            ui.panel(ui.tint, 23).apply {
                addView(copy)
                addView(artView, LinearLayout.LayoutParams(-1, 0, 1f))
                if (details.childCount > 0) addView(details)
            }
        } else {
            ui.row().apply {
                background = ui.shape(ui.tint, 32)
                val padding = ui.dp(14)
                setPadding(padding, padding, padding, padding)
                gravity = Gravity.CENTER_VERTICAL
                addView(artView, LinearLayout.LayoutParams(0, ui.dp(233), .68f))
                ui.gap(this, 12)
                val text = ui.column()
                text.addView(copy)
                if (details.childCount > 0) {
                    ui.gap(text, 8)
                    text.addView(details)
                }
                addView(text, ui.weight(1.32f))
            }
        }
    }

    private fun addBoardingRow(panel: LinearLayout, value: String) {
        ui.gap(panel, 7)
        ui.divider(panel)
        ui.gap(panel, 7)
        panel.addView(ui.text(value, 16, color = ui.muted))
    }

    private fun buildHome() {
        val pet = dog
        val seatPanel = ui.panel()
        val seatHeader = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        seatHeader.addView(ui.text("$dogLabel 자리", 23, true), ui.weight())
        seatHeader.addView(ui.text(seat.label, 16, true, ui.accent).apply {
            background = ui.shape(ui.tint, 18)
            setPadding(ui.dp(10), ui.dp(7), ui.dp(10), ui.dp(7))
        })
        seatPanel.addView(seatHeader)
        ui.gap(seatPanel, 10)
        seatPanel.addView(ui.seatGraphic(seat, pet?.coat ?: Coat.BROWN).apply {
            contentDescription = "$dogLabel 자리 "+seat.label
        }, LinearLayout.LayoutParams(-1, ui.dp(318)))

        val readings = ui.panel()
        readings.addView(ui.text(pet?.let { "${it.breed} ${it.name}에게 맞췄어요" } ?: "함께 출발할 준비", 23, true))
        if (pet != null) readings.addView(ui.text(pet.description, 16, color = ui.muted))
        if (!wideComposition) readings.addView(ui.text(seat.label, 16, true, ui.accent))
        temperatureText = vehicleReading(readings, R.drawable.design_icon_temperature, "실내 온도")
        temperatureCaption = ui.text("", 15, color = ui.muted).also(readings::addView)
        windowText = vehicleReading(readings, R.drawable.design_icon_window, "동승 쪽 창문")
        doorText = vehicleReading(readings, R.drawable.design_icon_door, "동승 쪽 문")
        if (clock.active) {
            clockText = vehicleReading(readings, R.drawable.ic_rest_notification,
                if (clock.breakCompleted) "마지막 휴식 이후" else "동승 중 주행 시간")
            restText = ui.text("", 16, color = ui.muted).also(readings::addView)
        }
        if (wideComposition) addPanels(seatPanel, readings, .8f, 1.2f) else content.addView(readings)
        ui.gap(content, 12)

        val actions = ui.row()
        val placesAction = if (course.isEmpty()) "오늘 갈 곳 찾기" else "코스 보기"
        when {
            clock.resting -> {
                actions.addView(ui.button("휴식 마쳤어요", true) { completeBreak() }, ui.weight())
                ui.gap(actions, 8)
                actions.addView(ui.button("휴식 취소") {
                    if (car.parked) {
                        clock.cancelBreak()
                        redraw()
                    }
                }, ui.weight())
            }
            clock.canStartBreak -> {
                actions.addView(ui.button("휴식 시작", true) { startBreak() }, ui.weight())
                ui.gap(actions, 8)
                actions.addView(ui.button(placesAction) { show(Page.PLACES) }, ui.weight())
            }
            clock.active -> actions.addView(ui.button(placesAction, true) { show(Page.PLACES) }, ui.weight())
            else -> {
                actions.addView(ui.button(if (dog == null) "반려견 등록" else "동승 시작", true) {
                    if (dog == null) show(Page.PROFILE) else startRide()
                }, ui.weight())
                ui.gap(actions, 8)
                actions.addView(ui.button(placesAction) { show(Page.PLACES) }, ui.weight())
            }
        }
        content.addView(actions)
        if (boardingPrompt && !clock.active) {
            ui.gap(content, 8)
            content.addView(ui.button("이번엔 같이 안 가요") {
                boardingPrompt = false
                redraw()
            })
        }
        if (clock.canStartBreak || clock.resting || course.isNotEmpty() || navigation.arrival != null) {
            ui.gap(content, 14)
            val trip = ui.panel(ui.tint, 16)
            trip.addView(ui.text("오늘 가는 길", 20, true))
            trip.addView(ui.text(if (course.isEmpty()) "도착하면 리드줄과 하차할 쪽을 확인해요."
                else course.joinToString(" → ") { it.name }, 17))
            ui.gap(trip, 8)
            trip.addView(ui.button("하차 준비") { show(Page.ARRIVAL) })
            content.addView(trip)
        }
        if (clock.active) {
            ui.gap(content, 12)
            content.addView(ui.button("동승 마치기") {
                dialog(parkedDialogBuilder("동승을 마칠까요?")
                    .setMessage("이번 동승의 주행 시간 기록과 휴식 알림이 종료돼요.")
                    .setNegativeButton("계속하기", null)
                    .setPositiveButton("동승 마치기") { _, _ -> finishRide() })
            })
        }
        if (clock.active && !reminders.enabled) {
            ui.gap(content, 10)
            content.addView(ui.button("차량 알림 설정") {
                if (car.parked) startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            })
        }
    }

    private fun vehicleReading(parent: LinearLayout, icon: Int, label: String): TextView {
        ui.gap(parent, 12)
        ui.divider(parent)
        ui.gap(parent, 9)
        val row = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ui.image(icon).apply {
            background = ui.shape(ui.tint, 20)
            setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
            setColorFilter(ui.accent)
        }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        ui.gap(row, 10)
        row.addView(ui.text(label, 17, true), ui.weight())
        val value = ui.text("—", 19, true, ui.accent)
        row.addView(value)
        parent.addView(row)
        return value
    }

    private fun buildDriving(scene: DriveScene, dark: Boolean) {
        when (scene) {
            DriveScene.REST, DriveScene.REST_TEN, DriveScene.REST_FIVE, DriveScene.HIGHWAY -> buildRestOffer()
            DriveScene.WINDOW -> buildWindowNotice()
            DriveScene.NORMAL, DriveScene.NIGHT -> {
                if (wideComposition) buildDrivingCare() else buildPortraitDrive(dark)
                ui.gap(content, 12)
                content.addView(ui.panel(padding = 14).apply {
                    addView(ui.text("말로 해 보세요    “쉬어갈 곳 찾아줘”", 17, true))
                })
            }
        }
    }

    private fun buildDrivingCare() {
        val readings = ui.column()
        val temperature = ui.panel(ui.tint, 26)
        val hero = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        val values = ui.column()
        values.addView(ui.text("$dogLabel 자리 실내 온도", 23, true))
        temperatureText = ui.number("—", 106).also(values::addView)
        temperatureCaption = ui.text("", 17, color = ui.muted).also(values::addView)
        hero.addView(values, ui.weight(1.5f))
        val coat = dog?.coat ?: Coat.BROWN
        hero.addView(ui.image(designArt(R.drawable.design_choco_face, coat.face), "$dogLabel 캐리커처"),
            LinearLayout.LayoutParams(0, ui.dp(242), 1f))
        temperature.addView(hero)
        readings.addView(temperature)
        ui.gap(readings, 12)
        readings.addView(buildVehicleTiles(includeTemperature = false))
        vehicleNotice = ui.text("", 16, true, ui.accent).also(readings::addView)
        addPanels(readings, buildDriveTimeline(), 1.7f, 1f)
    }

    private fun buildPortraitDrive(dark: Boolean) {
        val coat = dog?.coat ?: Coat.BROWN
        val hero = ui.panel(ui.tint, 0).apply { clipToOutline = true }
        val headline = ui.column().apply {
            val padding = ui.dp(20)
            setPadding(padding, padding, padding, 0)
        }
        val where = displayedRoute
        headline.addView(ui.text(where?.let { it.name + "까지" } ?: "오늘의 산책길", 18, true, ui.muted))
        val timeRow = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        routeRemainingText = ui.text("—", 52, true).also(timeRow::addView)
        ui.gap(timeRow, 12)
        routeArrivalText = ui.text("", 18, true, ui.muted).also { timeRow.addView(it, ui.weight()) }
        headline.addView(timeRow)
        hero.addView(headline)
        routeDogView = RunningDogView(this, coat, dogLabel, scenery = true, night = dark)
        hero.addView(routeDogView, LinearLayout.LayoutParams(-1, ui.dp(if (drivePreview == null) 179 else 362)))
        content.addView(hero)
        ui.gap(content, 12)

        val companions = ui.panel(padding = 18)
        val line = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        line.addView(ui.image(R.drawable.ic_rest_notification).apply {
            background = ui.shape(ui.tint, 24)
            setColorFilter(ui.accent)
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        ui.gap(line, 14)
        presenceText = ui.text("", 19, true).also { line.addView(it, ui.weight()) }
        companions.addView(line)
        content.addView(companions)
        ui.gap(content, 12)
        if (drivePreview == null) {
            content.addView(buildDriveTimeline())
            ui.gap(content, 12)
        }
        content.addView(buildVehicleTiles(includeTemperature = true))
        vehicleNotice = ui.text("", 16, true, ui.accent).also(content::addView)
    }

    private fun buildVehicleTiles(includeTemperature: Boolean): LinearLayout {
        val row = ui.row()
        if (includeTemperature) {
            temperatureText = addVehicleTile(row, R.drawable.design_icon_temperature, "$dogLabel 자리")
            ui.gap(row, 10)
        }
        windowText = addVehicleTile(row, R.drawable.design_icon_window, "동승 쪽 창문")
        ui.gap(row, 10)
        doorText = addVehicleTile(row, R.drawable.design_icon_door, "동승 쪽 문")
        return row
    }

    private fun addVehicleTile(row: LinearLayout, icon: Int, label: String): TextView {
        val tile = ui.panel(padding = 14)
        tile.addView(ui.image(icon).apply { setColorFilter(ui.accent) }, LinearLayout.LayoutParams(ui.dp(27), ui.dp(27)))
        ui.gap(tile, 5)
        tile.addView(ui.text(label, 15, color = ui.muted))
        val value = ui.text("—", 19, true).also(tile::addView)
        row.addView(tile, ui.weight())
        return value
    }

    private fun buildDriveTimeline(): LinearLayout {
        val timeline = ui.panel()
        timeline.addView(ui.text("오늘의 산책길", 23, true))
        clockText = ui.info(timeline, "동승 후 주행 시간")
        if (course.isEmpty()) {
            timelineRow(timeline, "아직 고른 장소가 없어요", "주차 후 산책길에서 고를 수 있어요")
        } else {
            course.forEachIndexed { i, place ->
                timelineRow(timeline, place.name, if (i == course.lastIndex) "목적지 · ${place.area}" else "경유지 · ${place.area}")
            }
        }
        navigationText = ui.text("", 16, color = ui.muted).also(timeline::addView)
        ui.gap(timeline, 12)
        ui.divider(timeline)
        restCountdown = ui.info(timeline, "다음 휴식 안내")
        return timeline
    }

    private fun buildRestOffer() {
        val coat = dog?.coat ?: Coat.BROWN
        val visual = ui.panel(ui.tint).apply {
            addView(ui.image(designArt(R.drawable.design_choco_rest_stop, coat.body), "$dogLabel 캐리커처"),
                LinearLayout.LayoutParams(-1, ui.dp(if (wideComposition) 336 else 218)))
        }
        val box = ui.panel()
        box.addView(ui.text("잠깐 쉬었다 갈까요?", 38, true))
        restText = ui.text("", 20, true, ui.accent).also(box::addView)
        clockText = ui.info(box, "동승 후 주행 시간")
        ui.gap(box, 12)
        ui.divider(box)
        val stop = course.firstOrNull { course.size > 1 && it.kind in setOf(PlaceKind.REST, PlaceKind.CHARGE) }
        if (stop != null) {
            ui.info(box, "코스에 담은 쉼터", stop.name)
            box.addView(ui.text(stop.description, 17, color = ui.muted))
        } else {
            ui.info(box, "가까운 쉼터에서 물과 산책을 챙겨요")
            box.addView(ui.text("주차한 뒤 산책길에서 쉼터를 골라 경유지에 추가할 수 있어요.", 17, color = ui.muted))
        }
        addPanels(visual, box, .85f, 1.15f)
    }

    private fun buildWindowNotice() {
        val coat = dog?.coat ?: Coat.BROWN
        val seat = ui.panel(ui.warningTint)
        val images = ui.row()
        images.addView(ui.image(designArt(R.drawable.design_choco_alert, coat.body), "$dogLabel 캐리커처"),
            LinearLayout.LayoutParams(0, ui.dp(220), 1.2f))
        images.addView(ui.seatGraphic(this.seat, coat), LinearLayout.LayoutParams(0, ui.dp(220), .8f))
        seat.addView(images)
        val warning = ui.panel()
        warning.addView(ui.text("동승 쪽 창문을 확인해요", 33, true, ui.warning))
        windowText = ui.info(warning, this.seat.label)
        doorText = ui.info(warning, "동승 쪽 문")
        ui.gap(warning, 12)
        warning.addView(ui.text("차량 창문 상태를 확인하고 필요하면 차량 조작부에서 조절해요.", 17, color = ui.muted))
        if (car.parked && drivePreview == DriveScene.WINDOW) {
            ui.gap(warning, 8)
            warning.addView(ui.text("시안 미리보기 · 상태 표시는 실제 차량 신호를 사용해요.", 15, true, ui.accent))
        }
        addPanels(seat, warning, .67f, 1.33f)
    }

    private fun addPanels(first: View, second: View, firstWeight: Float = 1f, secondWeight: Float = 1f) {
        if (wideComposition) {
            val row = ui.row()
            row.addView(first, ui.weight(firstWeight))
            ui.gap(row, 15)
            row.addView(second, ui.weight(secondWeight))
            content.addView(row)
        } else {
            content.addView(first)
            ui.gap(content, 12)
            content.addView(second)
        }
    }

    private fun timelineRow(parent: LinearLayout, title: String, detail: String) {
        ui.gap(parent, 18)
        val row = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ui.image(R.drawable.ic_rest_notification).apply {
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            setColorFilter(ui.accent)
            background = ui.shape(ui.tint)
        }, LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
        ui.gap(row, 13)
        val copy = ui.column()
        copy.addView(ui.text(title, 20, true))
        copy.addView(ui.text(detail, 16, color = ui.muted))
        row.addView(copy, ui.weight())
        parent.addView(row)
    }

    private fun buildPlaces() {
        content.addView(ui.text("$dogLabel 함께 갈 곳", 30, true))
        if (dog == null) {
            content.addView(ui.text("프로필을 등록하면 체중과 견종에 맞춰 추천해요.", 17))
            content.addView(ui.button("반려견 등록", true) { show(Page.PROFILE) })
            return
        }
        content.addView(ui.text(companions.joinToString(" · ") { "${it.name} ${formatWeight(it.weight)}kg" }, 17, color = ui.muted))
        locationText = ui.text("", 15, color = ui.muted)
        content.addView(locationText)
        ui.gap(content, 12)
        val mainKinds = listOf(null, PlaceKind.FIELD, PlaceKind.CAFE, PlaceKind.SEA)
        filterRow(mainKinds.map { it?.label ?: "전체" }, mainKinds.indexOf(category)) {
            category = mainKinds[it]
            redraw()
        }
        ui.gap(content, 8)
        val stops = ui.row()
        listOf(PlaceKind.REST, PlaceKind.CHARGE).forEach { kind ->
            if (kind == PlaceKind.CHARGE) ui.gap(stops, 6)
            stops.addView(ui.button(kind.label, category == kind) {
                category = if (category == kind) null else kind
                redraw()
            }, ui.weight())
        }
        content.addView(stops)
        ui.gap(content, 8)
        filterRow(PlayStyle.entries.map(PlayStyle::label), (store.mood ?: dog!!.style).ordinal) {
            store.mood = PlayStyle.entries[it]
            redraw()
        }
        ui.gap(content, 10)
        val options = ui.row()
        options.addView(check("카페 실내 동반만", indoorOnly) {
            indoorOnly = it
            redraw()
        }, ui.weight())
        options.addView(check("동반 조건 미확인 보기", includeUncertain) {
            includeUncertain = it
            redraw()
        }, ui.weight())
        content.addView(options)
        ui.gap(content, 14)
        val region = recommendations(examples = false)
        val examples = recommendations(examples = true)
        val results = ui.panel(padding = 18)
        results.addView(ui.text("$dogLabel 함께 갈 곳 ${region.size}곳", 22, true))
        region.forEachIndexed { index, place -> placeCard(place, results, index + 1) }
        if (region.isEmpty()) {
            addBoardingRow(results, "지금 조건이 확인된 실제 장소가 없어요.")
            results.addView(ui.text("동반 조건 미확인 보기를 켜거나 다른 장소 유형을 골라보세요.", 16, color = ui.muted))
        }
        if (examples.isNotEmpty()) {
            ui.gap(results, 16)
            results.addView(ui.text("동반 조건 시연용 장소", 20, true))
            examples.forEachIndexed { index, place -> placeCard(place, results, index + 1) }
        }
        val locations = region.filter(Place::routable).take(5)
        if (locations.isNotEmpty()) {
            val map = ui.panel(padding = 14)
            map.addView(ui.pawMap(locations.map { it to dog!!.coat }, "추천 장소 위치", numbered = true),
                LinearLayout.LayoutParams(-1, ui.dp(if (wideComposition) 300 else 165)))
            if (wideComposition) {
                val mapColumn = ui.column()
                mapColumn.addView(map)
                ui.gap(mapColumn, 12)
                if (course.isNotEmpty()) {
                    buildCourse(mapColumn)
                    ui.gap(mapColumn, 12)
                }
                mapColumn.addView(placesJournalCard())
                addPanels(mapColumn, results, .82f, 1.18f)
            } else {
                val overview = ui.row()
                overview.addView(map, LinearLayout.LayoutParams(0, -1, .9f))
                ui.gap(overview, 12)
                overview.addView(placesJournalCard(), LinearLayout.LayoutParams(0, -1, 1.1f))
                content.addView(overview)
                ui.gap(content, 12)
                if (course.isNotEmpty()) {
                    buildCourse(content)
                    ui.gap(content, 12)
                }
                content.addView(results)
            }
        } else {
            if (course.isNotEmpty()) {
                buildCourse(content)
                ui.gap(content, 12)
            }
            content.addView(results)
        }
    }

    private fun placesJournalCard(): LinearLayout = ui.panel(ui.tint, 15).apply {
        addView(ui.text("발자국 모으기", 20, true))
        ui.gap(this, 9)
        addView(ui.text("방문 기록 ${store.visits.size}개", 18, true, ui.accent))
        ui.gap(this, 10)
        addView(ui.button("발자국 보기") { show(Page.JOURNAL) })
    }

    private fun recommendations(examples: Boolean, kind: PlaceKind? = category,
                                allowUncertain: Boolean = includeUncertain, requestedIndoor: Boolean = indoorOnly): List<Place> {
        val mood = store.mood ?: dog?.style
        val likesQuiet = store.visits.take(4).any { it.mood == "조용해서 편했어요" }
        val active = navigation.activeDestination?.let { dest -> Places.all.firstOrNull { it.name == dest.name } }
        val preference = compareByDescending<Place> {
            when (mood) {
                PlayStyle.PLAY -> it.play
                PlayStyle.GREET, PlayStyle.QUIET -> it.quiet
                null -> likesQuiet && it.quiet
            }
        }.thenByDescending { likesQuiet && it.quiet }
        val order = if (kind == PlaceKind.CHARGE) {
            val target = active ?: course.lastOrNull()?.takeIf { it.kind != PlaceKind.CHARGE }
            compareBy<Place> { distance(it, target) }.then(preference)
        } else preference.thenBy { distance(it, active) }
        return Places.all.asSequence()
            .filter { it.sample == examples &&
                (if (kind == null) it.kind != PlaceKind.CHARGE && it.kind != PlaceKind.REST else it.kind == kind) }
            .filter { place ->
                val result = place.eligibility(companions, place.kind == PlaceKind.CAFE && requestedIndoor)
                result == Eligibility.MATCH || (allowUncertain && result == Eligibility.CHECK)
            }
            .sortedWith(order)
            .toList()
    }

    private fun distance(place: Place, active: Place? = null): Float {
        val here = navigation.location ?: return Float.MAX_VALUE
        val lat = place.latitude ?: return Float.MAX_VALUE
        val lon = place.longitude ?: return Float.MAX_VALUE
        fun meters(a: Double, b: Double, c: Double, d: Double): Float =
            FloatArray(1).also { android.location.Location.distanceBetween(a, b, c, d, it) }[0]
        val from = meters(here.latitude, here.longitude, lat, lon)
        val destLat = active?.latitude ?: return from
        val destLon = active.longitude ?: return from
        return from + meters(lat, lon, destLat, destLon) -
            meters(here.latitude, here.longitude, destLat, destLon)
    }

    private fun placeCard(place: Place, parent: LinearLayout, index: Int) {
        val match = place.eligibility(companions, place.kind == PlaceKind.CAFE && indoorOnly)
        val label = when {
            match == Eligibility.CHECK -> "동반 조건 확인 필요"
            place.kind == PlaceKind.CAFE && place.eligibility(companions, true) == Eligibility.MATCH -> "실내 동반 조건 일치"
            place.kind == PlaceKind.CAFE -> "야외에서 동반 가능"
            else -> "야외 동반 조건 일치"
        }
        ui.gap(parent, 8)
        ui.divider(parent)
        ui.gap(parent, 8)
        val row = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ui.number(index.toString(), 17, ui.inverse).apply {
            gravity = Gravity.CENTER
            background = ui.shape(ui.primary, 22)
        }, LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)))
        ui.gap(row, 12)
        val details = ui.column().apply {
            minimumHeight = ui.dp(46)
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = place.name + " 상세 · 후기"
            isFocusable = true
            setOnClickListener { placeDetails(place) }
        }
        details.addView(ui.text(place.name, 20, true))
        details.addView(ui.text("${place.area} · $label", 15, color = ui.muted))
        row.addView(details, ui.weight())
        if (match == Eligibility.MATCH || place.routable) {
            ui.gap(row, 8)
            val selected = course.any { it.id == place.id }
            row.addView(ui.button(if (selected) "−" else "+") { toggleCoursePlace(place, selected) }.apply {
                contentDescription = if (selected) "코스에서 빼기" else "코스에 추가"
            }, LinearLayout.LayoutParams(ui.dp(54), -2))
        }
        parent.addView(row)
    }

    private fun toggleCoursePlace(place: Place, selected: Boolean) {
        if (!car.parked) return
        val ids = store.courseIds
        val destination = course.lastOrNull()
        if (selected) store.courseIds = ids - place.id
        else if (place.kind == PlaceKind.CHARGE && destination != null && destination.kind != PlaceKind.CHARGE) {
            val withStop = listOf(place.id, destination.id)
            if (ids.size == 1) store.courseIds = withStop
            else {
                dialog(parkedDialogBuilder("충전 경유지를 바꿀까요?")
                    .setMessage("${place.name}에 들른 뒤 ${destination.name}까지 안내해요.")
                    .setNegativeButton("취소", null).setPositiveButton("바꾸기") { _, _ ->
                        if (car.parked && store.courseIds == ids) { store.courseIds = withStop; redraw() }
                    })
                return
            }
        }
        else if (ids.size < 2) store.courseIds = ids + place.id
        else {
            dialog(parkedDialogBuilder("코스에 장소가 두 곳 있어요")
                .setMessage("선택한 장소를 새 목적지로 바꿀까요?")
                .setNegativeButton("취소", null).setPositiveButton("바꾸기") { _, _ ->
                    if (car.parked) { store.courseIds = listOf(ids.first(), place.id); redraw() }
                })
            return
        }
        redraw()
    }

    private fun buildCourse(parent: LinearLayout) {
        val box = ui.panel(ui.tint)
        box.addView(ui.text("오늘의 코스", 22, true))
        course.forEachIndexed { i, place -> box.addView(ui.text(
            (if (i == course.lastIndex) "목적지  " else "경유지  ") + place.name, 17))
        }
        ui.gap(box, 8)
        val actions = ui.row()
        startRouteButton = ui.button(if (course.any { !it.routable }) "코스 미리보기" else "내비로 안내", true) {
            requestCourse()
        }
        actions.addView(startRouteButton, ui.weight())
        ui.gap(actions, 8)
        actions.addView(ui.button("코스 비우기") {
            store.courseIds = emptyList()
            redraw()
        }, ui.weight())
        box.addView(actions)
        navigationText = ui.text("", 15, color = ui.muted)
        box.addView(navigationText)
        ui.gap(box, 8)
        box.addView(ui.button("내비 동행 미리보기") { show(Page.NAV_CARD) })
        if (wideComposition && course.all(Place::routable)) {
            box.addView(ui.text("내비 화면 오른쪽 끝의 ⋮ 막대를 왼쪽으로 끌면 동행 카드를 함께 볼 수 있어요.",
                15, color = ui.muted))
        }
        if (presence.available) {
            box.addView(check("같은 목적지로 가는 반려견 수 공유", store.shareDestination) {
                store.shareDestination = it
                refresh()
            })
        }
        parent.addView(box)
    }

    private fun buildNavigationCard() {
        val place = course.lastOrNull() ?: navigation.companionPlace
        val pet = dog
        if (place == null || pet == null) {
            content.addView(ui.text("산책길에서 목적지를 먼저 골라주세요.", 23, true))
            content.addView(ui.button("산책길로 돌아가기", true) { show(Page.PLACES) })
            return
        }
        content.addView(ui.text("내비 동행 카드", 29, true))
        content.addView(ui.text("주차 중 미리보기 · Pleos 분할 화면에서 기본 내비와 함께 볼 수 있어요.", 16, color = ui.muted))
        ui.gap(content, 14)
        if (drivePreviewEnabled && course.isNotEmpty() && course.all(Place::routable)) {
            content.addView(ui.button("분할 화면에서 D단 시연") { startParkedDriveDemo() })
            ui.gap(content, 8)
            content.addView(ui.text("기본 내비 오른쪽 끝의 ⋮ 막대를 왼쪽으로 끌면 동행 화면이 열려요.",
                16, color = ui.muted))
            ui.gap(content, 14)
        }
        val preview = FrameLayout(this)
        preview.addView(ui.pawMap(course.ifEmpty { listOf(place) }.map { it to pet.coat },
            "선택한 목적지", numbered = true), FrameLayout.LayoutParams(-1, -1))
        preview.addView(navigationCompanionCard(place, pet), FrameLayout.LayoutParams(
            if (wideComposition) ui.dp(430) else -1, -2, Gravity.BOTTOM or Gravity.END
        ).apply { setMargins(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)) })
        content.addView(preview, LinearLayout.LayoutParams(-1, ui.dp(340)))
        ui.gap(content, 14)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            content.addView(ui.button("동행 알림 허용") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 90)
            })
            ui.gap(content, 8)
        }
        content.addView(ui.button("코스로 돌아가기", true) { show(Page.PLACES) })
    }

    private fun startParkedDriveDemo() {
        if (!drivePreviewEnabled || !car.parked || page != Page.NAV_CARD) return
        val destination = course.lastOrNull()?.takeIf(Place::routable) ?: return
        if (!navigation.connected || navigation.pending) {
            inform("기본 내비가 연결되고 경로 탐색이 끝나면 다시 눌러주세요.")
            return
        }
        parkedDriveDemoDestination = destination.id
        page = Page.PLACES
        if (!requestCourse()) {
            parkedDriveDemoDestination = null
            page = Page.NAV_CARD
            redraw()
            return
        }
        if (!isInMultiWindowMode) navigation.showMap()
        redraw()
    }

    private fun navigationCompanionCard(place: Place, pet: Dog): LinearLayout {
        val card = ui.panel(ui.tint, 16)
        card.background = ui.outlined(ui.tint, 24)
        val row = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        routeDogView = RunningDogView(this, pet.coat, pet.name)
        row.addView(routeDogView, LinearLayout.LayoutParams(ui.dp(118), ui.dp(88)))
        ui.gap(row, 12)
        val details = ui.column()
        details.addView(ui.text(place.name, 22, true))
        details.addView(ui.text("${pet.name}와 함께", 17, color = ui.muted))
        presenceText = ui.text("", 17, color = ui.accent).also(details::addView)
        row.addView(details, ui.weight())
        card.addView(row)
        return card
    }

    private fun requestCourse(): Boolean {
        if (!car.parked || course.isEmpty() || dog == null) return false
        if (course.any { it.eligibility(companions, it.kind == PlaceKind.CAFE && indoorOnly) != Eligibility.MATCH }) {
            dialog(AlertDialog.Builder(dialogContext()).setMessage("현재 동반 조건에 맞는 장소로 코스를 다시 골라주세요.")
                .setPositiveButton("확인", null))
            return false
        }
        if (course.any { !it.routable }) {
            dialog(parkedDialogBuilder("코스 미리보기")
                .setMessage(course.joinToString(" → ") { it.name } + "\n\n시연 장소나 위치가 확인되지 않은 장소가 포함되어 있어요.")
                .setPositiveButton("확인", null))
            return false
        }
        sideCompanionDismissed = false
        return navigation.request(course)
    }

    private fun placeDetails(place: Place) {
        val charge = place.chargeWalk
        val text = buildString {
            append(place.description)
            append("\n\n")
            if (charge == null) append(place.conditions().ifBlank { "동반 가능 구역과 체중 기준은 방문 전 확인해요." })
            else {
                append(charge.walk)
                append("\n충전 방식 · ${charge.connectors}")
                append("\n한국환경공단 충전소 등록 자료 2025.08.07 기준")
                append("\n충전기 이용 가능 여부와 차량 호환 여부는 방문 전에 확인해요.")
            }
            if (place.address.isNotBlank()) append("\n${place.address}")
            if (place.sourceDate != null) append("\n안내 자료 열람 ${place.sourceDate} · 방문 전 문의")
            else if (place.source != null) append("\n공개 안내 자료 · 현장 운영 조건은 방문 전에 확인해요.")
            if (place.sourceDate != null) append("\n안내 자료 ${place.sourceDate} 열람")
            if (place.sample) append("\n시연용 데이터")
            val notes = store.visits.filter { it.placeId == place.id && it.note.isNotBlank() }
            if (notes.isNotEmpty()) append("\n\n내 방문 기록\n" + notes.take(3).joinToString("\n") { it.note })
        }
        val builder = parkedDialogBuilder(place.name)
            .setPositiveButton("닫기", null).setNeutralButton("방문 기록 추가") { _, _ -> editVisit(place) }
        if (charge != null) {
            val information = form()
            information.addView(ui.text(text, 17))
            place.source?.let { url ->
                ui.gap(information, 12)
                information.addView(ui.button("반려견 산책 안내 보기") { openPlaceSource(url) })
            }
            builder.setView(formScroll(information)).setNegativeButton("충전소 조회") { _, _ ->
                openPlaceSource(Places.CHARGER_LOOKUP)
            }
        } else {
            builder.setMessage(text)
            place.source?.let { url -> builder.setNegativeButton("출처 보기") { _, _ -> openPlaceSource(url) } }
        }
        dialog(builder)
    }

    private fun openPlaceSource(url: String) {
        if (car.parked) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { inform("이 차량에 웹 자료를 열 수 있는 앱이 없어요.") }
    }

    private fun buildProfiles() {
        content.addView(ui.text("우리 강아지", 30, true))
        if (dog != null) {
            val pet = requireNotNull(dog)
            ui.gap(content, 12)
            val profile = ui.panel(ui.tint)
            profile.addView(ui.text("${pet.name}에게 맞춘 나들이", 22, true))
            profile.addView(ui.text("${formatWeight(pet.weight)}kg · 장소의 실내·체중 조건을 확인해요.", 17))
            ui.info(profile, "타는 자리와 방식", "${pet.seat.label} · ${pet.boarding.label}")
            ui.info(profile, "쉬어가기", "90분 사전 안내 · 120분 휴식 권장")
            content.addView(profile)
        }
        ui.gap(content, 12)
        content.addView(ui.button("반려견 추가", true) { editDog(null) })
        if (store.dogs.isEmpty()) {
            ui.gap(content, 12)
            content.addView(ui.text("이름과 체중을 등록하면 동반 조건을 자동으로 확인해요.", 17, color = ui.muted))
            return
        }
        store.dogs.forEach { profile ->
            ui.gap(content, 12)
            val panel = ui.panel()
            val line = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
            line.addView(photoOrCoat(profile.photo, profile.coat), LinearLayout.LayoutParams(ui.dp(76), ui.dp(76)))
            ui.gap(line, 14)
            val text = ui.column()
            text.addView(ui.text(profile.name, 24, true))
            text.addView(ui.text(profile.description, 17, color = ui.muted))
            text.addView(ui.text("${profile.style.label} · ${profile.seat.label} · ${profile.boarding.label}", 16))
            line.addView(text, ui.weight())
            line.addView(ui.button("편집") { editDog(profile) })
            panel.addView(line)
            val selected = companions.any { it.id == profile.id }
            panel.addView(check("오늘 함께 가요", selected) { on ->
                val current = companions.map(Dog::id).toSet()
                store.activeIds = if (on) current + profile.id else current - profile.id
                car.watchSeat(seat)
                redraw()
            }.apply { isEnabled = !selected || companions.size > 1 })
            content.addView(panel)
        }
    }

    private fun editDog(profile: Dog?) {
        if (!car.parked) return
        val form = form()
        val name = field(form, "이름", profile?.name)
        val breed = field(form, "견종", profile?.breed)
        val age = field(form, "나이 (살)", profile?.age?.toString(), InputType.TYPE_CLASS_NUMBER)
        val weight = field(form, "현재 체중 (kg)", profile?.weight?.let(::formatWeight),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val style = pick(form, "평소 놀이 방식", PlayStyle.entries.map(PlayStyle::label), profile?.style?.ordinal ?: 1)
        val seatField = pick(form, "주로 타는 좌석", Seat.entries.map(Seat::label), profile?.seat?.ordinal ?: 0)
        val coat = pick(form, "캐리커처 털 색", Coat.entries.map(Coat::label), profile?.coat?.ordinal ?: 0)
        val nursery = field(form, "유치원·단체 놀이 경험 (선택)", profile?.nursery)
        val boarding = pick(form, "타는 방식", Boarding.entries.map(Boarding::label), profile?.boarding?.ordinal ?: 0)
        val motion = check("차멀미를 하는 편이에요", profile?.motionSickness ?: false)
        form.addView(motion)
        var photo = profile?.photo
        val choosePhoto = ui.button(if (photo == null) "강아지 사진 추가" else "사진 바꾸기") {
            openPhoto { selected ->
                photo = selected
                inform("사진을 선택했어요. 저장하면 프로필에 반영돼요.")
            }
        }
        form.addView(choosePhoto)
        val builder = parkedDialogBuilder(if (profile == null) "반려견 등록" else "프로필 편집")
            .setView(formScroll(form)).setNegativeButton("취소", null).setPositiveButton("저장", null)
        if (profile != null) builder.setNeutralButton("삭제") { _, _ ->
            dialog(parkedDialogBuilder("${profile.name} 프로필을 삭제할까요?")
                .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                    if (car.parked) {
                        store.delete(profile)
                        car.watchSeat(seat)
                        redraw()
                    }
                })
        }
        val editor = dialog(builder)
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!car.parked) return@setOnClickListener
            val years = age.text.toString().toIntOrNull()
            val kg = weight.text.toString().toDoubleOrNull()
            when {
                name.text.isBlank() -> name.error = "이름을 적어주세요."
                breed.text.isBlank() -> breed.error = "견종을 적어주세요. 믹스견도 입력할 수 있어요."
                years == null || years !in 0..40 -> age.error = "0~40 사이의 나이를 입력해 주세요."
                kg == null || !kg.isFinite() || kg !in 0.1..150.0 -> weight.error = "0.1~150kg 사이의 체중을 입력해 주세요."
                else -> {
                    val dog = Dog(profile?.id ?: UUID.randomUUID().toString(), name.text.toString().trim(),
                        breed.text.toString().trim(), years, kg, Seat.entries[seatField.selectedItemPosition],
                        PlayStyle.entries[style.selectedItemPosition], nursery.text.toString().trim(),
                        Boarding.entries[boarding.selectedItemPosition], motion.isChecked, Coat.entries[coat.selectedItemPosition], photo)
                    store.save(dog)
                    if (store.activeIds.isEmpty()) store.activeIds = setOf(dog.id)
                    sampleProfile = false
                    car.watchSeat(seat)
                    editor.dismiss()
                    show(Page.HOME)
                }
            }
        }
    }

    private fun buildJournal() {
        content.addView(ui.text("발자국 지도", 30, true))
        content.addView(ui.text("다녀온 곳과 그날의 한마디", 17, color = ui.muted))
        ui.gap(content, 12)
        content.addView(ui.button("발자국 남기기", true) {
            if (dog == null) show(Page.PROFILE) else chooseVisit()
        })
        ui.gap(content, 14)
        if (store.visits.isEmpty()) {
            content.addView(ui.text("아직 발자국이 없어요. 첫 나들이를 남겨보세요.", 17, color = ui.muted))
        }
        val visitedPlaces = store.visits.mapNotNull { visit -> Places.get(visit.placeId)?.let { it to visit.coat } }
            .distinctBy { it.first.id }
        if (visitedPlaces.any { it.first.routable }) {
            content.addView(ui.pawMap(visitedPlaces), LinearLayout.LayoutParams(-1, ui.dp(190)))
            ui.gap(content, 13)
        }
        store.visits.forEach { visit ->
            val place = Places.get(visit.placeId) ?: return@forEach
            val panel = ui.panel()
            val row = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(photoOrCoat(visit.photo, visit.coat), LinearLayout.LayoutParams(ui.dp(100), ui.dp(100)))
            ui.gap(row, 16)
            val text = ui.column()
            text.addView(ui.text("${visit.dogNames}와 함께 · ${place.name}", 22, true))
            text.addView(ui.text(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.KOREA).format(Date(visit.time)), 15, color = ui.muted))
            if (visit.mood.isNotBlank()) text.addView(ui.text(visit.mood, 16, true))
            if (visit.note.isNotBlank()) text.addView(ui.text(visit.note, 17))
            row.addView(text, ui.weight())
            panel.addView(row)
            ui.gap(panel, 8)
            val actions = ui.row()
            actions.addView(ui.button("기록 열기") { editVisit(place, visit) }, ui.weight())
            ui.gap(actions, 8)
            actions.addView(ui.button("엽서 공유") {
                val text = "${visit.dogNames}와 함께 · ${place.name}" +
                    listOf(visit.mood, visit.note).filter(String::isNotBlank).joinToString("") { "\n$it" }
                share(text, visit.photo)
            }, ui.weight())
            if (place.routable) {
                ui.gap(actions, 8)
                actions.addView(ui.button("다시 가기", true) {
                    store.courseIds = listOf(place.id)
                    show(Page.PLACES)
                }, ui.weight())
            }
            panel.addView(actions)
            content.addView(panel)
            ui.gap(content, 10)
        }
    }

    private fun chooseVisit() {
        val available = (course + Places.all.filter { !it.sample }).distinctBy(Place::id)
        dialog(parkedDialogBuilder("어디 다녀왔나요?")
            .setItems(available.map(Place::name).toTypedArray()) { _, index -> editVisit(available[index]) })
    }

    private fun editVisit(place: Place, original: Visit? = null) {
        if (!car.parked) return
        if (dog == null && original == null) {
            show(Page.PROFILE)
            return
        }
        val form = form()
        val note = field(form, "그날의 한마디", original?.note, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val moods = listOf("선택 안 함", "조용해서 편했어요", "마음껏 뛰었어요", "다른 강아지가 많았어요")
        val mood = pick(form, "오늘 어땠나요?", moods, moods.indexOf(original?.mood).coerceAtLeast(0))
        var photo = original?.photo
        form.addView(ui.button("사진 고르기") {
            openPhoto {
                photo = it
                inform("사진을 선택했어요. 저장하면 발자국에 붙어요.")
            }
        })
        form.addView(ui.button("말로 한마디 남기기") {
            if (!car.parked) return@button
            dictated = { note.setText(it) }
            runCatching {
                startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR"), DICTATION)
            }.onFailure {
                dictated = null
                inform("이 차량에서 음성 입력을 열 수 없어요. 한마디를 직접 적을 수 있어요.")
            }
        })
        dialog(parkedDialogBuilder(place.name).setView(formScroll(form))
            .setNegativeButton("취소", null).setPositiveButton("발자국 저장") { _, _ ->
                if (!car.parked) return@setPositiveButton
                val visit = Visit(original?.id ?: UUID.randomUUID().toString(), place.id,
                    original?.dogNames ?: companions.joinToString("와 ") { it.name },
                    original?.time ?: System.currentTimeMillis(), note.text.toString().trim(),
                    moods[mood.selectedItemPosition].takeUnless { it == moods.first() }.orEmpty(),
                    photo, original?.coat ?: dog?.coat ?: Coat.BROWN)
                store.save(visit)
                show(Page.JOURNAL)
            })
    }

    private fun buildCare() {
        content.addView(ui.text("$dogLabel 잠깐 쉬는 동안", 32, true))
        ui.gap(content, 13)
        val panel = ui.panel()
        panel.addView(ui.text("차량 안심 상태", 23, true))
        ui.gap(panel, 10)
        doorText = ui.info(panel, "$dogLabel 쪽 문")
        windowText = ui.info(panel, "$dogLabel 쪽 창문")
        temperatureText = ui.info(panel, "실내 온도")
        temperatureCaption = ui.text("", 15, color = ui.muted).also(panel::addView)
        val audio = ui.panel()
        audio.addView(ui.text("보호자 목소리", 23, true))
        ui.gap(audio, 8)
        val playback = ui.panel(ui.tint, 15)
        playback.addView(ui.text(if (carePlayer != null) "저장한 목소리를 재생하고 있어요"
            else if (store.careAudio != null) "저장한 목소리가 준비됐어요" else "목소리 파일을 추가할 수 있어요", 18, true))
        playback.addView(ui.text("주차 중 이 화면에서 재생돼요.", 16, color = ui.muted))
        audio.addView(playback)
        ui.gap(audio, 10)
        val actions = ui.row()
        actions.addView(ui.button(if (store.careAudio == null) "음성 파일 추가" else "음성 파일 바꾸기") {
            pickFile("audio/*") { uri ->
                stopCareAudio()
                store.careAudio = uri
                redraw()
            }
        }, ui.weight())
        val recording = store.careAudio
        if (recording != null) {
            ui.gap(actions, 8)
            actions.addView(ui.button(if (carePlayer == null) "목소리 들려주기" else "재생 멈추기", true) {
                toggleCarePlayback()
            }, ui.weight())
        }
        audio.addView(actions)
        addPanels(panel, audio, .85f, 1.15f)
        ui.gap(content, 13)
        content.addView(ui.button("하차 준비") { show(Page.ARRIVAL) })
        if (drivePreviewEnabled) {
            ui.gap(content, 13)
            content.addView(ui.button("D단 화면 미리보기") { openDrivePreview() })
        }
    }

    private fun toggleCarePlayback() {
        if (!car.parked) return
        val recording = store.careAudio ?: return
        if (carePlayer != null) stopCareAudio()
        else runCatching {
            val player = requireNotNull(MediaPlayer.create(this, Uri.parse(recording)))
            carePlayer = player
            player.setOnCompletionListener { stopCareAudio(); redraw() }
            player.start()
        }.onFailure {
            stopCareAudio()
            inform("음성 파일을 재생할 수 없어요.")
        }
        redraw()
    }

    private fun openDrivePreview() {
        if (!drivePreviewEnabled || !car.parked) return
        val previews = DriveScene.entries.map { it.title to (it to false) } + listOf(
            "쉬어가기 제안 · 야간" to (DriveScene.REST to true),
            "창문 열림 안내 · 야간" to (DriveScene.WINDOW to true),
            "쉬는 시간 10분 전 · 야간" to (DriveScene.REST_TEN to true),
            "쉬는 시간 5분 전 · 야간" to (DriveScene.REST_FIVE to true),
            "고속도로 휴게소 · 야간" to (DriveScene.HIGHWAY to true)
        )
        dialog(parkedDialogBuilder("D단 시안 고르기")
            .setItems(previews.map { it.first }.toTypedArray()) { _, index ->
                if (!car.parked) return@setItems
                stopCareAudio()
                val (preview, night) = previews[index].second
                drivePreview = preview
                darkDrivePreview = night
                if (preview == DriveScene.WINDOW) windowRaise = WindowRaise(null)
                redraw()
            })
    }

    private fun buildArrival() {
        val arrival = navigation.arrival
        val place = Places.all.firstOrNull { it.name == arrival?.name } ?: course.lastOrNull()
        val checklist = ui.panel()
        checklist.addView(ui.text("문 열기 전에 리드줄부터", 28, true))
        ui.gap(checklist, 10)
        val confirmed = ui.text("", 17, true, ui.accent)
        fun updateConfirmation() {
            confirmed.text = if (leashReady) "리드줄을 확인했어요. ${seat.label} 쪽 문을 확인해요."
                else "목줄과 하네스 고리를 확인해 주세요."
        }
        checklist.addView(check("리드줄을 채웠어요", leashReady) { leashReady = it; updateConfirmation() })
        checklist.addView(check("배변 봉투를 챙겼어요", wasteBagsReady) { wasteBagsReady = it })
        checklist.addView(check("물그릇을 챙겼어요", waterReady) { waterReady = it })
        ui.gap(checklist, 12)
        updateConfirmation()
        checklist.addView(confirmed)

        val outing = ui.panel(ui.tint)
        outing.addView(ui.text(if (arrival != null && place != null) "${place.name}에 도착했어요" else "오늘의 발자국", 23, true))
        ui.info(outing, "하차할 자리", seat.label)
        doorText = ui.info(outing, "동승 쪽 문")
        if (arrival != null && arrival.distance > 0) {
            ui.info(outing, "이번 내비 경로", String.format(Locale.KOREA, "%.1f km", arrival.distance / 1000.0))
        }
        ui.gap(outing, 10)
        outing.addView(ui.button("발자국 남기기", true) {
            if (dog == null) show(Page.PROFILE) else if (place != null) editVisit(place) else chooseVisit()
        })
        addPanels(checklist, outing, 1.16f, .84f)
    }

    private fun resetArrivalChecklist() {
        leashReady = false
        wasteBagsReady = false
        waterReady = false
    }

    private fun stopCareAudio() {
        carePlayer?.release()
        carePlayer = null
        if (zipFrontendDelegate.isInitialized()) zipFrontend.pauseCareVideo()
    }

    private fun startRide() {
        val boarded = dog ?: return
        if (!car.parked || clock.active) return
        resetArrivalChecklist()
        clock.start()
        notifiedStage = null
        handledAlert = null
        clearRestAlert()
        boardingPrompt = false
        reminders.clear()
        autoCabin.apply(boarded)
        redraw()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 90)
        }
    }

    private fun startBreak() {
        if (!car.parked || !clock.canStartBreak) return
        clock.startBreak()
        reminders.clear()
        clearRestAlert()
        redraw()
    }

    private fun completeBreak() {
        if (!car.parked || !clock.resting) return
        clock.completeBreak()
        notifiedStage = null
        handledAlert = null
        clearRestAlert()
        reminders.clear()
        redraw()
    }

    private fun finishRide() {
        if (!car.parked) return
        autoCabin.reset()
        clock.finish()
        navigation.clearCompanion()
        notifiedStage = null
        handledAlert = null
        clearRestAlert()
        reminders.clear()
        redraw()
    }

    private fun filterRow(labels: List<String>, selected: Int, action: (Int) -> Unit) {
        val row = ui.row()
        labels.forEachIndexed { i, label ->
            if (i > 0) ui.gap(row, 6)
            row.addView(ui.button(label, i == selected) { if (car.parked) action(i) }, ui.weight())
        }
        content.addView(row)
    }

    private fun positionLabel(value: Int?, showPercent: Boolean): String = when {
        value == null || value < 0 -> "확인 불가"
        value == 0 -> "닫힘"
        showPercent -> "열림 ${value.coerceAtMost(100)}%"
        else -> "열림"
    }

    private fun windowPositionLabel(): String {
        val percent = car.windowPercent
        return if (percent == null) positionLabel(car.windowPosition, false) else positionLabel(percent, true)
    }

    private fun dialogContext() = ContextThemeWrapper(this,
        if (screenDark) R.style.GaerangDarkDialogTheme else R.style.GaerangDialogTheme)

    private fun parkedDialogBuilder(title: String): AlertDialog.Builder = AlertDialog.Builder(dialogContext()).setCustomTitle(
        ui.text(title, 24, true).apply {
            isAccessibilityHeading = true
            setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(10))
        }
    )

    private fun dialog(builder: AlertDialog.Builder): AlertDialog {
        val shown = builder.create()
        if (car.parked) {
            parkedDialog?.dismiss()
            parkedDialog = shown
            shown.setOnDismissListener { if (parkedDialog === shown) parkedDialog = null }
            shown.show()
            shown.window?.apply {
                setBackgroundDrawable(ui.shape(ui.surface, 30))
                decorView.clipToOutline = true
                val available = (resources.configuration.screenWidthDp * resources.displayMetrics.density).roundToInt()
                setLayout(minOf((available - ui.dp(32)).coerceAtLeast(1), ui.dp(680)), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
                shown.getButton(which)?.apply {
                    isAllCaps = false
                    minimumHeight = ui.dp(52)
                    setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(6))
                    typeface = resources.getFont(R.font.noto_kr_bold)
                    ui.styleButton(this, which == AlertDialog.BUTTON_POSITIVE)
                }
            }
        }
        return shown
    }

    private fun inform(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun form() = ui.column().apply { setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(16)) }
    private fun formScroll(form: View) = ScrollView(this).apply { addView(form) }

    private fun field(form: LinearLayout, label: String, value: String?, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val edit = EditText(dialogContext()).apply {
            id = View.generateViewId()
            hint = label
            inputType = type
            textSize = 17f
            minHeight = ui.dp(55)
            maxLines = if (type and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) 4 else 1
            setText(value.orEmpty())
        }
        form.addView(ui.text(label, 15, true).apply { labelFor = edit.id })
        form.addView(edit)
        ui.gap(form, 7)
        return edit
    }

    private fun pick(form: LinearLayout, label: String, options: List<String>, selected: Int): Spinner {
        val context = dialogContext()
        val control = Spinner(context).apply {
            id = View.generateViewId()
            minimumHeight = ui.dp(52)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(selected)
        }
        form.addView(ui.text(label, 15, true).apply { labelFor = control.id })
        form.addView(control)
        ui.gap(form, 7)
        return control
    }

    private fun check(label: String, checked: Boolean, onChange: ((Boolean) -> Unit)? = null) =
        CheckBox(this).apply {
            text = label
            textSize = 16f
            setTextColor(ui.ink)
            minHeight = ui.dp(50)
            isChecked = checked
            setOnCheckedChangeListener { _, value -> if (car.parked) onChange?.invoke(value) }
        }

    private fun photoOrCoat(photo: String?, coat: Coat): ImageView = ui.image(coat.face).apply {
        if (photo != null) runCatching {
            val source = android.graphics.ImageDecoder.createSource(contentResolver, Uri.parse(photo))
            setImageBitmap(android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val ratio = maxOf(info.size.width, info.size.height) / 270f
                if (ratio > 1) decoder.setTargetSize(
                    (info.size.width / ratio).roundToInt().coerceAtLeast(1),
                    (info.size.height / ratio).roundToInt().coerceAtLeast(1)
                )
            })
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    private fun openPhoto(onPick: (String) -> Unit) = pickFile("image/*", onPick)

    private fun pickFile(type: String, onPick: (String) -> Unit) {
        if (!car.parked) return
        filePicked = onPick
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type)
                .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), PHOTO)
        }.onFailure {
            filePicked = null
            inform("차량에서 파일 선택 화면을 열 수 없어요.")
        }
    }

    private fun share(text: String, photo: String? = null) {
        if (!car.parked) return
        val outgoing = Intent(Intent.ACTION_SEND).setType(if (photo == null) "text/plain" else "image/*")
            .putExtra(Intent.EXTRA_TEXT, text)
        if (photo != null) {
            val uri = Uri.parse(photo)
            outgoing.putExtra(Intent.EXTRA_STREAM, uri)
            outgoing.clipData = android.content.ClipData.newUri(contentResolver, "나들이 사진", uri)
            outgoing.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(outgoing, "공유하기")) }
            .onFailure { inform("이 차량에서 공유 화면을 열 수 없어요.") }
    }

    @Deprecated("Receives results from the companion, document picker, and dictation screens")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DRIVE_COMPANION) {
            driveCompanionOpen = false
            driveCompanionState.value = null
            parkedDriveDemoDestination = null
            sideCompanionDismissed = true
            if (car.parked && resultCode == RESULT_OK && navigation.companionPlace != null) navCareOpen = true
            redraw()
        } else if (requestCode == PHOTO) {
            val callback = filePicked
            filePicked = null
            val uri = data?.data
            if (car.parked && resultCode == RESULT_OK && uri != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    .onSuccess { callback?.invoke(uri.toString()) }.onFailure { inform("선택한 파일을 저장할 수 없어요.") }
            }
        } else if (requestCode == DICTATION) {
            val callback = dictated
            dictated = null
            if (car.parked && resultCode == RESULT_OK) {
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { callback?.invoke(it) }
            }
        }
    }

    private fun handleVoice(request: Intent) {
        val action = request.action ?: return
        val encoded = runCatching { request.getStringExtra("ACTION_PARAM") }.getOrNull()
        val parameters = runCatching {
            val raw = org.json.JSONTokener(encoded.orEmpty()).nextValue()
            if (raw is JSONObject) raw else JSONObject(raw.toString())
        }.getOrElse { JSONObject() }
        fun param(name: String) = parameters.optString(name).ifBlank {
            request.extras?.get(name)?.toString().orEmpty()
        }
        fun answer(status: String, message: String, data: JSONArray? = null) {
            val result = JSONObject().put("functionName", action).put("status", status).put("message", message)
            if (data != null) result.put("data", data.toString())
            sendBroadcast(Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
                .putExtra("packageName", packageName).putExtra("response", result.toString()))
        }
        if (dog == null) {
            answer("asking", "Please register a dog profile first.")
            if (car.parked) show(Page.PROFILE)
            return
        }
        when (action) {
            "GAERANG_FIND_PLACES" -> {
                val kind = PlaceKind.entries.firstOrNull { it.name.equals(param("kind"), true) }
                val indoors = param("indoor").toBooleanStrictOrNull() ?: true
                val results = recommendations(examples = false, kind = kind, allowUncertain = false, requestedIndoor = indoors).take(3)
                voiceResults = results.map(Place::id)
                voiceResultsTime = SystemClock.elapsedRealtime()
                if (car.parked) { category = kind; indoorOnly = indoors; show(Page.PLACES) }
                if (results.isEmpty()) answer("success", "No place matches the requested access conditions.")
                else answer("asking", "Choose a place by name or by its number in the results.", JSONArray(results.mapIndexed { i, place ->
                    JSONObject().put("choice", i + 1).put("placeId", place.id).put("name", place.name)
                        .put("area", place.area).put("access", place.conditions())
                }))
            }
            "GAERANG_NAVIGATE" -> {
                if (!car.parked) { answer("fail", "Please park before sending a place to the navigation app."); return }
                val recent = SystemClock.elapsedRealtime() - voiceResultsTime < 180_000
                val selected = param("choice").toIntOrNull()
                val place = Places.get(param("placeId")).takeIf { param("placeId") in voiceResults && recent }
                    ?: if (recent && selected != null) Places.get(voiceResults.getOrNull(selected - 1)) else null
                if (place == null) { answer("asking", "Please search again and choose a park or cafe from the results."); return }
                val addCafe = param("addCafe").equals("true", ignoreCase = true)
                val cafe = if (addCafe && place.kind != PlaceKind.CAFE)
                    recommendations(false, PlaceKind.CAFE, allowUncertain = false, requestedIndoor = true)
                        .filter(Place::routable).minByOrNull { separation(place, it) }
                else null
                if (addCafe && place.kind != PlaceKind.CAFE && cafe == null) {
                    answer("asking", "No confirmed indoor cafe is available for the active dog profiles. Ask whether to navigate to ${place.name} alone.")
                    return
                }
                indoorOnly = indoorOnly || addCafe
                store.courseIds = listOfNotNull(place.id, cafe?.id)
                if (showThenRequest()) answer("success", "Sent the navigation request for ${course.joinToString(" then ") { it.name }}.")
                else answer("fail", "The navigation request could not be sent. Please check the app.")
            }
            "GAERANG_START_RIDE" -> {
                if (!car.parked) answer("fail", "Park before starting a ride.")
                else {
                    if (!clock.active) startRide()
                    answer("success", "Ride tracking is on.")
                }
            }
            "GAERANG_FINISH_RIDE" -> {
                if (!car.parked) answer("fail", "Park before ending this ride.")
                else { finishRide(); answer("success", "Ride tracking has ended.") }
            }
        }
    }

    private fun showThenRequest(): Boolean {
        show(Page.PLACES)
        return requestCourse()
    }

    private fun separation(first: Place, second: Place): Float {
        if (!first.routable || !second.routable) return Float.MAX_VALUE
        val result = FloatArray(1)
        android.location.Location.distanceBetween(
            requireNotNull(first.latitude), requireNotNull(first.longitude),
            requireNotNull(second.latitude), requireNotNull(second.longitude), result
        )
        return result[0]
    }

    private companion object {
        const val WIDE_WIDTH_DP = 1020
        const val PHOTO = 2
        const val DICTATION = 3
        const val DRIVE_COMPANION = 4
        val voiceActions = setOf("GAERANG_FIND_PLACES", "GAERANG_NAVIGATE", "GAERANG_START_RIDE", "GAERANG_FINISH_RIDE")
        val drivingGears = setOf(VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_REVERSE, VehicleGear.GEAR_FIRST,
            VehicleGear.GEAR_SECOND, VehicleGear.GEAR_THIRD, VehicleGear.GEAR_FOURTH, VehicleGear.GEAR_FIFTH,
            VehicleGear.GEAR_SIXTH, VehicleGear.GEAR_SEVENTH, VehicleGear.GEAR_EIGHTH, VehicleGear.GEAR_NINTH)
    }
}
