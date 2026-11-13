package com.steady90.app

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.ZonedDateTime

object Reminders {
    const val CHANNEL = "gentle_reminders"
    const val ACTION = "com.steady90.app.DAILY_PAUSE"
    const val ID = 90
    const val ROUTE = "reminder_route"
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily pause", NotificationManager.IMPORTANCE_LOW).apply {
                description = "An optional, discreet prompt at your chosen time."
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            })
    }
    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun alarm(context: Context) = PendingIntent.getBroadcast(context, ID,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarm(context))
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
    fun schedule(context: Context, plan: Plan) {
        cancel(context)
        if (!plan.onboarded || !plan.reminderEnabled) return
        val next = ReminderTime.next(ZonedDateTime.now(), plan.reminderHour, plan.reminderMinute).toInstant().toEpochMilli()
        // A supportive nudge, not a time-critical alarm. No exact-alarm access required.
        context.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC_WAKEUP, next, 30 * 60_000L, alarm(context))
    }
    private fun route(context: Context, destination: String, request: Int): PendingIntent = PendingIntent.getActivity(context, request,
        Intent(context, MainActivity::class.java).setAction("com.steady90.app.OPEN_$destination")
            .putExtra(ROUTE, destination).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun show(context: Context): Boolean {
        channel(context)
        if (!allowed(context)) return false
        val reset = route(context, "reset", 91)
        val meditate = route(context, "meditate", 92)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("A moment for yourself")
            .setContentText("Pause, put your phone down, and choose what helps you next.")
            .setVisibility(Notification.VISIBILITY_SECRET).setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(reset)
            .setTimeoutAfter(2 * 60 * 60_000L)
            .addAction(Notification.Action.Builder(null, "Take a pause", reset).build())
            .addAction(Notification.Action.Builder(null, "Meditate", meditate).build())
            .build()
        return try { context.getSystemService(NotificationManager::class.java).notify(ID, notification); true }
        catch (_: SecurityException) { false }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val valid = setOf(Reminders.ACTION, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
        if (intent.action !in valid) return
        try {
            val plan = Vault(context).read().plan
            Reminders.schedule(context, plan)
            if (intent.action == Reminders.ACTION && plan.onboarded && plan.reminderEnabled) Reminders.show(context)
        } catch (_: Exception) {
            // Do not expose private data or fall back to an unwanted reminder if storage is unavailable.
        }
    }
}
