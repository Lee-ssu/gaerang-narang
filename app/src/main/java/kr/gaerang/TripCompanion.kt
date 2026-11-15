package kr.gaerang

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.IBinder
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.IntentCompat

internal class TripCompanion(private val context: Context) {
    private data class Status(val name: String?, val guidance: Boolean, val parked: Boolean, val others: Int?)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val service = Intent(context, TripCompanionService::class.java)
    private var shown: Status? = null
    private var running = false

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "내비 동행", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
    }

    fun update(place: Place?, guidance: Boolean, parked: Boolean, otherDogs: Int?, rideActive: Boolean) {
        if (place == null && !rideActive) {
            if (shown != null) clear()
            return
        }
        val next = Status(place?.name, guidance, parked, otherDogs)
        if (shown == next) return
        val title = when {
            place == null -> "반려견 동승 기록 중"
            parked -> "주차했어요 · 동행 화면 보기"
            guidance -> "${place.name} · 안내 중"
            else -> "${place.name} 경로 대기 중"
        }
        val text = when {
            place == null -> "주행 시간을 기록하고 쉬어갈 때를 알려드려요."
            parked -> "주행 시간과 쉬어가기 안내를 확인해요."
            otherDogs != null && otherDogs > 0 -> "다른 반려견 ${otherDogs}마리도 같은 곳으로 가는 중"
            guidance -> "개랑 나랑 동행 중"
            else -> "기본 내비에서 길 안내를 시작하면 동행을 확인해요."
        }
        val notice = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_rest_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (parked) {
            val open = PendingIntent.getActivity(context, 908, Intent(context, MainActivity::class.java)
                .setAction(OPEN_PARKED_CARE)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
            notice.setContentIntent(open)
        }
        val notification = notice.build()
        if (!running) {
            // Route requests come from the activity while the driver is parked.
            running = runCatching {
                context.startForegroundService(Intent(service).putExtra(TripCompanionService.NOTICE, notification)) != null
            }.getOrDefault(false)
        }
        if (manager.areNotificationsEnabled()) manager.notify(NOTIFICATION_ID, notification)
        shown = next
    }

    fun clear() {
        if (running) context.stopService(service)
        running = false
        manager.cancel(NOTIFICATION_ID)
        shown = null
    }

    companion object {
        const val OPEN_PARKED_CARE = "kr.gaerang.OPEN_PARKED_CARE"
        private const val CHANNEL = "navigation_companion"
        const val NOTIFICATION_ID = 908
    }
}

internal class TripCompanionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = intent?.let { IntentCompat.getParcelableExtra(it, NOTICE, Notification::class.java) }
        if (notification == null) stopSelf(startId)
        else startForeground(TripCompanion.NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    companion object { const val NOTICE = "trip_notification" }
}

internal class RunningDogView(
    context: Context,
    private val coat: Coat,
    name: String,
    private val scenery: Boolean = false,
    night: Boolean = false,
    cycleDurationMillis: Long = 820L
) : View(context) {
    private val dog = TravelDogArtwork.get(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloud = Color.parseColor(if (night) "#433529" else "#FBF3E8")
    private val hill = Color.parseColor(if (night) "#322720" else "#ECD8C0")
    private val trail = Color.parseColor(if (night) "#5A4B3E" else "#C9B59C")
    private val oval = RectF()
    private var phase = 0f
    private var running = false
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = cycleDurationMillis
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init { contentDescription = "$name 캐리커처" }

    fun updateMotion(active: Boolean) {
        if (running == active) return
        running = active
        updateAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun updateAnimation() {
        if (running && isAttachedToWindow && ValueAnimator.areAnimatorsEnabled()) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
            phase = 0f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / (if (scenery) 400f else 232f), height / 174f)
        if (scale <= 0f) return
        if (scenery) drawScenery(canvas)
        canvas.save()
        canvas.translate(width / 2f - 165f * scale,
            if (scenery) height * .94f - 194 * scale else height / 2f - 110 * scale)
        canvas.scale(scale, scale)
        dog.draw(canvas, coat, phase, running && animator.isStarted)
        canvas.restore()
    }

    private fun drawScenery(canvas: Canvas) {
        paint.color = cloud
        oval.set(width * .12f, height * .07f, width * .30f, height * .17f)
        canvas.drawOval(oval, paint)
        oval.set(width * .63f, height * .25f, width * .78f, height * .33f)
        canvas.drawOval(oval, paint)
        paint.color = hill
        canvas.drawCircle(width * .14f, height * .94f, height * .15f, paint)
        canvas.drawCircle(width * .27f, height * .96f, height * .09f, paint)
        canvas.drawCircle(width * .79f, height * .97f, height * .10f, paint)
        paint.color = trail
        val tile = width / 15f
        for (index in 0..15) {
            val x = index * tile - phase * tile
            canvas.drawRoundRect(x, height * .96f, x + tile * .5f, height * .972f, 4f, 4f, paint)
        }
    }
}
