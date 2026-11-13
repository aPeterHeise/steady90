package com.steady90.app

import android.app.KeyguardManager
import android.Manifest
import android.os.Build
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var vault: Vault
    private lateinit var health: HealthBridge
    var data by mutableStateOf(AppData()); private set
    var notice by mutableStateOf(""); private set
    var healthMessage by mutableStateOf("Connect sleep & steps from Google Fit")
    var origins by mutableStateOf(emptyList<String>())
    var syncing by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var loadFailed by mutableStateOf(false)
    var today by mutableStateOf(LocalDate.now().toString())
    var reminderRoute by mutableStateOf("")
    var notificationsAllowed by mutableStateOf(false)
    private var backgroundAt = 0L
    private var authenticating = false
    private var dataGeneration = 0
    private val permissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { syncHealth() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = Reminders.allowed(this)
        if (granted) setReminders(true) else notice = "Notifications weren't allowed. You can enable them later in Plan."
    }
    private val unlockResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        authenticating = false
        if (it.resultCode == RESULT_OK) { locked = false; backgroundAt = 0; syncHealth() }
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            contentResolver.openOutputStream(uri)?.use { it.write(Codec.encode(data.copy(timer = null)).toByteArray()) }
                ?: error("No output stream")
            notice = "Export saved. Keep this readable file somewhere private."
        } catch (_: Exception) { notice = "Export could not be saved. Your app data is unchanged." }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = Vault(this); health = HealthBridge(this)
        try { data = vault.read(); locked = data.plan.lock } catch (_: Exception) { loadFailed = true }
        protectWindow()
        Reminders.channel(this)
        reminderRoute = savedInstanceState?.getString("pendingReminderRoute").orEmpty()
        receiveReminderRoute(intent)
        setContent { SteadyTheme { SteadyApp(this) } }
    }
    override fun onResume() {
        super.onResume(); today = LocalDate.now().toString()
        if (!::health.isInitialized || loadFailed) return
        notificationsAllowed = Reminders.allowed(this)
        scheduleReminders()
        if (!authenticating && data.plan.lock && backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt > 30_000) locked = true
        if (!locked && data.plan.onboarded) syncHealth()
    }
    override fun onStop() { super.onStop(); if (!authenticating) backgroundAt = SystemClock.elapsedRealtime() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("pendingReminderRoute", reminderRoute); super.onSaveInstanceState(outState) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveReminderRoute(intent) }
    private fun receiveReminderRoute(intent: Intent) {
        val route = intent.getStringExtra(Reminders.ROUTE)
        if (route == "reset" || route == "meditate") {
            reminderRoute = route
            intent.removeExtra(Reminders.ROUTE)
            getSystemService(NotificationManager::class.java).cancel(Reminders.ID)
        }
    }
    private fun scheduleReminders() {
        try { Reminders.schedule(this, data.plan) }
        catch (_: Exception) { notice = "Reminder could not be scheduled. Try switching it off and on in Plan." }
    }
    fun setReminders(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS); return
        }
        change { it.copy(plan = it.plan.copy(reminderEnabled = enabled)) }
        notificationsAllowed = Reminders.allowed(this)
    }
    fun notificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
    fun testReminder() {
        notice = if (Reminders.show(this)) "Preview sent. Check your notification shade."
        else "Notifications are blocked. Enable them in Android notification settings."
    }
    fun change(f: (AppData) -> AppData) {
        if (loadFailed || locked) return
        val next = f(data)
        val old = data.plan
        try {
            vault.save(next); data = next
            if (old.reminderEnabled != next.plan.reminderEnabled || old.reminderHour != next.plan.reminderHour || old.reminderMinute != next.plan.reminderMinute) scheduleReminders()
        } catch (_: Exception) { notice = "Could not save. Please free up device storage and try again." }
    }
    fun editDay(date: String, f: (DayLog) -> DayLog) = change { it.updateDay(date, f) }
    fun clearNotice() { notice = "" }
    fun notify(message: String) { notice = message }
    fun boot() = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
    fun timerSeconds() = data.timer?.let { Metrics.timerSeconds(it, SystemClock.elapsedRealtime(), boot()) } ?: 0
    fun startTimer() {
        val t = data.timer
        change { it.copy(timer = ReadingTimer(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(), t?.let { timerSeconds() } ?: 0, true, t?.date ?: today)) }
    }
    fun pauseTimer() { change { it.copy(timer = it.timer?.copy(pausedSeconds = timerSeconds(), running = false)) } }
    fun saveTimer() {
        val t = data.timer ?: return; val seconds = timerSeconds()
        change { it.updateDay(t.date) { d -> d.copy(readingSeconds = d.readingSeconds + seconds) }.copy(timer = null) }
        if (data.timer == null) notice = "${seconds / 60}m ${seconds % 60}s of reading saved. That time is yours."
    }
    fun connectHealth() {
        when (health.status()) {
            HealthConnectClient.SDK_AVAILABLE -> try { permissions.launch(health.permissions) } catch (_: Exception) { healthMessage = "Permission screen unavailable. Open Health Connect in Android Settings." }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> openLink("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
            else -> healthMessage = "Health Connect isn't available on this device. Manual logging still works."
        }
    }
    fun syncHealth() {
        if (syncing || locked || loadFailed) return
        if (health.status() != HealthConnectClient.SDK_AVAILABLE) {
            healthMessage = "Health Connect needs installation or an update. Manual logging is available."; return
        }
        syncing = true
        val generation = dataGeneration
        lifecycleScope.launch {
            try {
                val result = health.read(data.plan.sleepOrigin); origins = result.origins
                if (generation != dataGeneration) return@launch
                change { current ->
                    var updated = current
                    result.days.forEach { (date, h) -> updated = updated.updateDay(date) { d -> d.copy(
                        importedSleepMinutes = h.sleep, importedSteps = h.steps, sleepSource = h.source, sleepWindow = h.window) } }
                    updated
                }
                healthMessage = when {
                    !result.sleepGranted && !result.stepsGranted -> "Allow sleep and steps access to import your records."
                    !result.sleepGranted -> "Steps connected. Sleep permission isn't enabled."
                    result.days.values.none { it.sleep != null } -> "Connected · no sleep records shared yet. In Google Fit, enable Sync Fit with Health Connect and sleep sharing."
                    !result.stepsGranted -> "Sleep synced · steps permission isn't enabled."
                    else -> "Synced just now · sleep & steps"
                }
            } catch (_: SecurityException) { healthMessage = "Access changed. Reconnect to review permissions." }
            catch (_: Exception) { healthMessage = "Sync unavailable. Previous records are kept; try again shortly." }
            finally { syncing = false }
        }
    }
    fun openLink(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) { notice = "No app available to open this link." } }
    fun exportData() { export.launch("steady90-${LocalDate.now()}.json") }
    fun deleteData() {
        try { vault.delete(); Reminders.cancel(this); dataGeneration++; data = AppData(); loadFailed = false; locked = false; reminderRoute = ""; origins = emptyList(); healthMessage = "Connect sleep & steps from Google Fit"; notice = "Local records deleted." }
        catch (_: Exception) { notice = "Unable to delete local records." }
    }
    fun setLock(enabled: Boolean) {
        val manager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (enabled && !manager.isDeviceSecure) { notice = "Set a device PIN or screen lock in Android Settings first."; return }
        change { it.copy(plan = it.plan.copy(lock = enabled)) }
        protectWindow()
    }
    private fun protectWindow() {
        if (data.plan.lock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    @Suppress("DEPRECATION") fun unlock() {
        val manager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = manager.createConfirmDeviceCredentialIntent("Unlock Steady 90", "Your habits stay private.")
        if (intent != null) { authenticating = true; unlockResult.launch(intent) }
        else { notice = "Device authentication unavailable. Restore your device screen lock in Android Settings." }
    }
}

const val PRIVACY = "Steady 90 stores habit logs, reading, reflections and imported health records on this device, encrypted with a device-bound key. It has no account, ads, analytics or network permission. Android backups are disabled. Uninstalling or losing this device can lose your records.\n\nWith your permission, Health Connect supplies sleep and step records for your dashboard and trends. We never write to Google Fit or Health Connect. Sync runs when you open the app or tap Sync; this version does not read health data in the background. Only the most recent eight calendar days are refreshed. Previously imported older records stay on your phone.\n\nYou can revoke access in Android Settings → Health Connect → App permissions. Revoking access does not erase all previously imported records; Delete local data removes those too. Manual entries override imports until you clear the manual value.\n\nExport creates a readable JSON file at a destination you choose. That file is not encrypted by Steady 90; your file provider may upload it. Delete local data does not remove exported files or the original records in Google Fit or Health Connect.\n\nOptional local reminders use a time you choose. Their neutral wording contains no habit or health details and is hidden on the lock screen by default. You can disable reminders in Plan or Android settings. There is no browsing monitoring.\n\nOptional app lock uses your device credentials. It locks on a cold start or after more than 30 seconds away. Steady 90 is a personal habit aid, not a diagnosis or treatment service."

class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SteadyTheme { PrivacyScreen { finish() } } } }
}
