package kr.gaerang

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal enum class RestStage(
    val minutes: Int,
    private val demoSeconds: Long,
    val title: String,
    val message: String
) {
    PLAN(90, 15, "다음 쉼터를 미리 골라요", "반려견과 쉬어갈 곳을 미리 살펴보세요."),
    BREAK(120, 20, "함께 쉬어갈 시간이에요", "가까운 안전한 곳에서 물을 마시고 잠깐 걸어보세요.");

    fun threshold(shortDemo: Boolean): Long =
        (if (shortDemo) demoSeconds else minutes * 60L) * 1_000L
}

internal enum class RestAlert(val leadMinutes: Int, val screen: String, private val demoSeconds: Long) {
    TEN(10, "RestAlert10", 16),
    FIVE(5, "RestAlert5", 18);

    val title get() = "쉬는 시간 ${leadMinutes}분 전"

    fun threshold(shortDemo: Boolean): Long = if (shortDemo) demoSeconds * 1_000L
        else RestStage.BREAK.threshold(false) - leadMinutes * 60_000L
}

internal class RestReminders(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    val enabled: Boolean
        get() = manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "반려견 휴식 안내", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "반려견 휴식 임박", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun show(stage: RestStage) {
        if (!enabled) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_rest_notification)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentTitle("${stage.minutes}분 · ${stage.title}")
            .setContentText(stage.message)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        clear()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun clear() = manager.cancel(NOTIFICATION_ID)

    fun showAlert(alert: RestAlert) {
        if (!manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(ALERT_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val openAlert = PendingIntent.getActivity(context, ALERT_ID, Intent(context, MainActivity::class.java)
            .setAction(OPEN_ALERT).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        manager.notify(ALERT_ID, Notification.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_rest_notification)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentTitle(alert.title)
            .setContentText("주차한 뒤 알림을 열어 반려견과 쉬어갈 곳을 골라요.")
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openAlert)
            .setAutoCancel(true)
            .build())
    }

    fun clearAlert() = manager.cancel(ALERT_ID)

    companion object {
        const val OPEN_ALERT = "kr.gaerang.OPEN_REST_ALERT"
        private const val CHANNEL = "ride_rest_reminders"
        private const val ALERT_CHANNEL = "ride_rest_prealerts"
        private const val NOTIFICATION_ID = 90120
        private const val ALERT_ID = 90105
    }
}
