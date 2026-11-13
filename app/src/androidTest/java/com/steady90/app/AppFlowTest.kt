package com.steady90.app

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun resetLocalTestData() { ui.runOnIdle { ui.activity.deleteData(); ui.activity.clearNotice() } }
    private fun start() {
        ui.onNodeWithText("Begin my 90 days").performScrollTo().assertIsNotEnabled()
        ui.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        ui.onNodeWithText("Begin my 90 days").performClick()
        ui.waitForIdle()
    }
    @Test fun onboardingAndNavigation() {
        start()
        ui.onNodeWithText("Read", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Start 20 minutes").assertExists()
        ui.onNodeWithText("Reset", useUnmergedTree = true).performClick()
        ui.onNodeWithText("An urge isn't\nan instruction.").assertExists()
        ui.onNodeWithText("Progress", useUnmergedTree = true).performClick()
        ui.onNodeWithText("minutes read").assertExists()
        ui.onNodeWithText("Plan", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Ambition, with\nroom to breathe.").assertExists()
    }
    @Test fun readingTimerSurvivesActivityRecreationAndSavesOnce() {
        start()
        ui.onNodeWithText("Read", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Start 20 minutes").performScrollTo().performClick()
        SystemClock.sleep(1500)
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Pause").performScrollTo().performClick()
        ui.onNodeWithText("Finish & save 0 minutes").performScrollTo().performClick()
        ui.runOnIdle {
            assertNull(ui.activity.data.timer)
            assertTrue(ui.activity.data.day(ui.activity.today).readingSeconds >= 1)
        }
        ui.onNodeWithText("Start 20 minutes").assertExists()
    }
    @Test fun changingAndFinishingBooksPreservesTimerAndSurvivesRecreation() {
        start()
        ui.onNodeWithText("Read", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Choose a book").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("First book")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithText("Start 20 minutes").performScrollTo().performClick()
        ui.onNodeWithText("Change book").performScrollTo().performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("Second book")
        ui.onNodeWithText("Save").performClick()
        lateinit var timer: ReadingTimer
        ui.runOnIdle {
            assertEquals("Second book", ui.activity.data.plan.book)
            assertTrue(ui.activity.data.finishedBooks.isEmpty())
            timer = ui.activity.data.timer!!
            ui.activity.editDay(ui.activity.today) { it.copy(readingSeconds = 600) }
        }
        ui.onNodeWithText("Mark as finished").performClick()
        ui.onNodeWithText("Keep reading").performClick()
        ui.runOnIdle { assertEquals("Second book", ui.activity.data.plan.book) }
        ui.onNodeWithText("Mark as finished").performClick()
        ui.onNodeWithText("Finish book").performClick()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Choose next book").assertExists()
        ui.onNodeWithText("Books finished").performScrollTo().assertIsDisplayed()
        ui.runOnIdle {
            assertEquals(listOf(FinishedBook("Second book", ui.activity.today)), ui.activity.data.finishedBooks)
            assertEquals(timer, ui.activity.data.timer)
            assertEquals(600, ui.activity.data.day(ui.activity.today).readingSeconds)
        }
        ui.onNodeWithText("Undo last finish").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals("Second book", ui.activity.data.plan.book)
            assertTrue(ui.activity.data.finishedBooks.isEmpty())
            assertEquals(timer, ui.activity.data.timer)
        }
        ui.onNodeWithText("Mark as finished").performScrollTo().performClick()
        ui.onNodeWithText("Finish book").performClick()
        ui.onNodeWithText("Choose next book").performScrollTo().performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("Third book")
        ui.onNodeWithText("Save").performClick()
        ui.activityRule.scenario.recreate()
        ui.runOnIdle {
            assertEquals("Third book", ui.activity.data.plan.book)
            assertEquals(1, ui.activity.data.finishedBooks.size)
            assertEquals(timer, ui.activity.data.timer)
        }
    }
    @Test fun honestCheckInsAndManualSleepSurviveRecreation() {
        start()
        ui.onNodeWithText("Edit", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("450")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithText("Alcohol-free").performScrollTo().performClick()
        ui.onNodeWithText("Used porn").performScrollTo().performClick()
        ui.activityRule.scenario.recreate()
        ui.runOnIdle {
            val d = ui.activity.data.day(ui.activity.today)
            assertEquals(450, d.manualSleepMinutes)
            assertEquals("free", d.alcohol)
            assertEquals("used", d.porn)
        }
    }
    @Test fun encryptedVaultAndDeletion() {
        start()
        ui.runOnIdle { ui.activity.editDay(ui.activity.today) { it.copy(wins = "private phrase for storage test") } }
        ui.runOnIdle {
            val activity = ui.activity
            val file = java.io.File(activity.filesDir, "steady.vault")
            assertTrue(file.exists())
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("private phrase"))
            assertEquals("private phrase for storage test", Vault(activity).read().day(activity.today).wins)
            activity.deleteData()
            assertFalse(file.exists())
            assertFalse(activity.data.plan.onboarded)
        }
        ui.onNodeWithText("Small promises.\nA fuller life.").assertExists()
    }
    @Test fun meditationLoggingPersistsAndCanBeCleared() {
        start()
        ui.onNodeWithText("Log meditation").performScrollTo().performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("5")
        ui.onNodeWithText("Save").performClick()
        ui.activityRule.scenario.recreate()
        ui.runOnIdle { assertEquals(5, ui.activity.data.day(ui.activity.today).meditationMinutes) }
        ui.onNodeWithText("Log meditation").performScrollTo().performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("0")
        ui.onNodeWithText("Save").performClick()
        ui.runOnIdle { assertEquals(0, ui.activity.data.day(ui.activity.today).meditationMinutes) }
    }
    @Test fun reminderIsPrivateAndOffSwitchCancelsIt() {
        start()
        val context = ui.activity
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        ui.runOnIdle {
            context.setReminders(true)
            ReminderReceiver().onReceive(context, android.content.Intent(Reminders.ACTION))
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            val posted = manager.activeNotifications.single { it.id == Reminders.ID }.notification
            assertEquals(android.app.Notification.VISIBILITY_SECRET, posted.visibility)
            assertEquals("A moment for yourself", posted.extras.getString(android.app.Notification.EXTRA_TITLE))
            assertFalse(posted.extras.toString().contains("porn", ignoreCase = true))
            assertEquals(2, posted.actions.size)
            context.setReminders(false)
            ReminderReceiver().onReceive(context, android.content.Intent(Reminders.ACTION))
            assertTrue(manager.activeNotifications.none { it.id == Reminders.ID })
        }
    }
    @Test fun notificationMeditationActionRespectsAppLock() {
        start()
        ui.runOnIdle { ui.activity.locked = true; ui.activity.reminderRoute = "meditate" }
        ui.onNodeWithText("Unlock").assertExists()
        ui.onNodeWithText("Daily meditation").assertDoesNotExist()
        ui.runOnIdle { ui.activity.locked = false }
        ui.onNodeWithText("Sit comfortably.", substring = true).assertExists()
        ui.onNodeWithText("Cancel").performClick()
        ui.runOnIdle { assertEquals(0, ui.activity.data.day(ui.activity.today).meditationMinutes) }
    }
}
