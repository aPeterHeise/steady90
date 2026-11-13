package com.steady90.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

data class DayLog(
    val date: String,
    val readingSeconds: Int = 0,
    val manualSleepMinutes: Int? = null,
    val importedSleepMinutes: Int? = null,
    val sleepSource: String = "",
    val sleepWindow: String = "",
    val manualSteps: Long? = null,
    val importedSteps: Long? = null,
    val rested: Int = 0,
    val alcohol: String = "", // blank = not logged, free, drank
    val alcoholGrams: Double = 0.0,
    val porn: String = "", // blank = not logged, free, used
    val workout: Boolean = false,
    val bedtime: Boolean = false,
    val waterMl: Int = 0,
    val words: Int = 0,
    val coldShower: Boolean = false,
    val balancedMeal: Boolean = false,
    val wins: String = "",
    val note: String = "",
    val meditationMinutes: Int = 0
) {
    val sleepMinutes get() = manualSleepMinutes ?: importedSleepMinutes
    val steps get() = manualSteps ?: importedSteps
}
data class Urge(val at: Long, val kind: String, val trigger: String, val action: String, val before: Int, val after: Int)
data class ReadingTimer(val startedWall: Long, val startedElapsed: Long, val boot: Int, val pausedSeconds: Int = 0, val running: Boolean = true, val date: String)
data class Plan(
    val started: String = LocalDate.now().toString(), val onboarded: Boolean = false,
    val why: String = "More energy. More attention. More time for what matters.",
    val book: String = "", val readingGoal: Int = 20, val stepsGoal: Int = 7000,
    val sleepGoal: Int = 8, val writingGoal: Int = 100, val waterGoal: Int = 2000,
    val extras: Boolean = false, val reward: String = "A day trip after 20 reading sessions",
    val sleepOrigin: String = "", val lock: Boolean = false,
    val meditationGoal: Int = 5,
    val reminderEnabled: Boolean = false, val reminderHour: Int = 21, val reminderMinute: Int = 0,
    val copingPlan: String = "If I start scrolling out of boredom at night, I will put my phone down and read a page."
)
data class AppData(val plan: Plan = Plan(), val days: Map<String, DayLog> = emptyMap(), val urges: List<Urge> = emptyList(), val timer: ReadingTimer? = null) {
    fun day(date: String) = days[date] ?: DayLog(date)
    fun updateDay(date: String, change: (DayLog) -> DayLog) = copy(days = days + (date to change(day(date))))
}

object Metrics {
    // Union intervals so overlapping records never inflate sleep.
    fun unionSeconds(intervals: List<Pair<Long, Long>>): Long {
        val sorted = intervals.filter { it.second > it.first }.sortedBy { it.first }
        if (sorted.isEmpty()) return 0
        var start = sorted.first().first; var end = sorted.first().second; var total = 0L
        sorted.drop(1).forEach { (a, b) ->
            if (a <= end) end = maxOf(end, b) else { total += end - start; start = a; end = b }
        }
        return total + end - start
    }
    fun timerSeconds(t: ReadingTimer, elapsed: Long, boot: Int): Int {
        val running = if (t.running && t.boot == boot) ((elapsed - t.startedElapsed).coerceAtLeast(0) / 1000).toInt() else 0
        return (t.pausedSeconds + running).coerceIn(0, 20 * 60)
    }
    fun alcoholGrams(ml: Int, abv: Double, count: Int) = ml * (abv / 100) * 0.789 * count
    fun actions(d: DayLog): Int = listOf(d.readingSeconds > 0, d.meditationMinutes > 0, (d.steps ?: 0) > 0, d.alcohol.isNotEmpty(), d.porn.isNotEmpty(), d.wins.isNotBlank(), d.workout, d.bedtime).count { it }
    fun minutes(seconds: Int) = seconds / 60
    fun hours(minutes: Int?) = minutes?.let { "${it / 60}h ${it % 60}m" } ?: "Not recorded"
}

object Codec {
    fun encode(data: AppData): String {
        val p = data.plan
        val root = JSONObject().put("version", 1).put("plan", JSONObject()
            .put("started", p.started).put("onboarded", p.onboarded).put("why", p.why).put("book", p.book)
            .put("readingGoal", p.readingGoal).put("stepsGoal", p.stepsGoal).put("sleepGoal", p.sleepGoal)
            .put("writingGoal", p.writingGoal).put("waterGoal", p.waterGoal).put("extras", p.extras)
            .put("reward", p.reward).put("sleepOrigin", p.sleepOrigin).put("lock", p.lock)
            .put("meditationGoal", p.meditationGoal).put("reminderEnabled", p.reminderEnabled)
            .put("reminderHour", p.reminderHour).put("reminderMinute", p.reminderMinute).put("copingPlan", p.copingPlan))
        val days = JSONArray()
        data.days.values.sortedBy { it.date }.forEach { d -> days.put(JSONObject()
            .put("date", d.date).put("readingSeconds", d.readingSeconds)
            .put("manualSleepMinutes", d.manualSleepMinutes).put("importedSleepMinutes", d.importedSleepMinutes)
            .put("sleepSource", d.sleepSource).put("sleepWindow", d.sleepWindow)
            .put("manualSteps", d.manualSteps).put("importedSteps", d.importedSteps).put("rested", d.rested)
            .put("alcohol", d.alcohol).put("alcoholGrams", d.alcoholGrams).put("porn", d.porn)
            .put("workout", d.workout).put("bedtime", d.bedtime).put("waterMl", d.waterMl)
            .put("words", d.words).put("coldShower", d.coldShower).put("balancedMeal", d.balancedMeal)
            .put("wins", d.wins).put("note", d.note).put("meditationMinutes", d.meditationMinutes)) }
        root.put("days", days)
        root.put("urges", JSONArray().apply { data.urges.forEach { u -> put(JSONObject().put("at", u.at).put("kind", u.kind).put("trigger", u.trigger).put("action", u.action).put("before", u.before).put("after", u.after)) } })
        data.timer?.let { t -> root.put("timer", JSONObject().put("startedWall", t.startedWall).put("startedElapsed", t.startedElapsed).put("boot", t.boot).put("pausedSeconds", t.pausedSeconds).put("running", t.running).put("date", t.date)) }
        return root.toString(2)
    }
    fun decode(raw: String): AppData {
        val root = JSONObject(raw); require(root.getInt("version") == 1) { "Unsupported data version" }
        val p = root.getJSONObject("plan")
        val plan = Plan(p.getString("started"), p.optBoolean("onboarded"), p.optString("why"), p.optString("book"),
            p.optInt("readingGoal", 20), p.optInt("stepsGoal", 7000), p.optInt("sleepGoal", 8), p.optInt("writingGoal", 100),
            p.optInt("waterGoal", 2000), p.optBoolean("extras"), p.optString("reward"), p.optString("sleepOrigin"), p.optBoolean("lock"),
            p.optInt("meditationGoal", 5).coerceIn(1, 120), p.optBoolean("reminderEnabled"),
            p.optInt("reminderHour", 21).coerceIn(0, 23), p.optInt("reminderMinute", 0).coerceIn(0, 59), p.optString("copingPlan", Plan().copingPlan))
        val days = mutableMapOf<String, DayLog>(); val arr = root.getJSONArray("days")
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i); val date = d.getString("date")
            days[date] = DayLog(date, d.optInt("readingSeconds"), d.nullInt("manualSleepMinutes"), d.nullInt("importedSleepMinutes"),
                d.optString("sleepSource"), d.optString("sleepWindow"), d.nullLong("manualSteps"), d.nullLong("importedSteps"),
                d.optInt("rested"), d.optString("alcohol"), d.optDouble("alcoholGrams", 0.0), d.optString("porn"),
                d.optBoolean("workout"), d.optBoolean("bedtime"), d.optInt("waterMl"), d.optInt("words"),
                d.optBoolean("coldShower"), d.optBoolean("balancedMeal"), d.optString("wins"), d.optString("note"), d.optInt("meditationMinutes", 0))
        }
        val urges = mutableListOf<Urge>(); val ua = root.optJSONArray("urges") ?: JSONArray()
        for (i in 0 until ua.length()) { val u = ua.getJSONObject(i); urges += Urge(u.getLong("at"), u.getString("kind"), u.getString("trigger"), u.getString("action"), u.getInt("before"), u.getInt("after")) }
        val t = root.optJSONObject("timer")?.let { ReadingTimer(it.getLong("startedWall"), it.getLong("startedElapsed"), it.getInt("boot"), it.optInt("pausedSeconds"), it.optBoolean("running"), it.getString("date")) }
        return AppData(plan, days, urges, t)
    }
    private fun JSONObject.nullInt(key: String) = if (isNull(key)) null else getInt(key)
    private fun JSONObject.nullLong(key: String) = if (isNull(key)) null else getLong(key)
}
