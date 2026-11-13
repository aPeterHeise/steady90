package com.steady90.app

import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable fun SteadyApp(a: MainActivity) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var meditationDate by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(a.reminderRoute, a.locked, a.data.plan.onboarded) {
        if (!a.locked && a.data.plan.onboarded && !a.loadFailed) {
            when (a.reminderRoute) {
                "reset" -> tab = 2
                "meditate" -> { tab = 0; meditationDate = a.today }
            }
            a.reminderRoute = ""
        }
    }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(a.notice) { if (a.notice.isNotBlank()) { snack.showSnackbar(a.notice); a.clearNotice() } }
    LaunchedEffect(Unit) { while (true) { a.today = LocalDate.now().toString(); delay(15_000) } }
    Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
        if (a.data.plan.onboarded && !a.locked && !a.loadFailed) NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
            val labels = listOf("Today", "Read", "Reset", "Progress", "Plan")
            val icons = listOf(Icons.Outlined.WbSunny, Icons.Outlined.MenuBook, Icons.Outlined.Spa, Icons.Outlined.Insights, Icons.Outlined.Tune)
            labels.forEachIndexed { i, title -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], title) }, label = { Text(title, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Panel, selectedIconColor = Lime, selectedTextColor = Lime)) }
        }
    }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) {
        when {
            a.loadFailed -> Page { Heading("YOUR DATA", "Storage needs attention", "The encrypted records could not be opened. They have not been overwritten."); Text("Try restarting the app. If your device encryption key was lost, the records cannot be recovered."); DeleteButton(a) }
            a.locked -> Page { Spacer(Modifier.height(70.dp)); Icon(Icons.Outlined.Lock, null, tint = Lime, modifier = Modifier.size(48.dp)); Heading("STEADY 90", "A little space for you.", "Unlock with your device credentials."); Button(onClick = { a.unlock() }) { Text("Unlock") } }
            !a.data.plan.onboarded -> Welcome(a)
            tab == 0 -> TodayScreen(a, { tab = 1 }, { tab = 2 }, { meditationDate = it })
            tab == 1 -> ReadingScreen(a)
            tab == 2 -> ResetScreen(a)
            tab == 3 -> ProgressScreen(a)
            else -> PlanScreen(a)
        }
    } }
    if (meditationDate.isNotEmpty() && !a.locked && a.data.plan.onboarded) {
        val date = meditationDate
        NumberDialog("Daily meditation", "Sit comfortably. Notice your breath; when your mind wanders, gently return. Eyes open is fine. Start with a minute if that feels easier. Afterwards, enter your total minutes for this day.",
            a.data.day(date).meditationMinutes.toString(), 0..240, { meditationDate = "" }) { minutes ->
            a.editDay(date) { it.copy(meditationMinutes = minutes ?: 0) }
        }
    }
}

@Composable fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
}
@Composable fun Heading(kicker: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = Lime)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle.isNotBlank()) Text(subtitle, color = Mist, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun PanelCard(color: Color = Panel, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(color).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable fun SectionTitle(title: String, caption: String = "") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.labelSmall, color = Mist)
    }
}
@Composable fun TinyLabel(text: String, color: Color = Mist) { Text(text.uppercase(), color = color, style = MaterialTheme.typography.labelSmall) }
@Composable fun FullButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text(text) }
}
@Composable fun Welcome(a: MainActivity) {
    var why by rememberSaveable { mutableStateOf(a.data.plan.why) }
    var book by rememberSaveable { mutableStateOf("") }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    Page {
        TinyLabel("STEADY / 90", Lime)
        Spacer(Modifier.height(8.dp))
        Text("Small promises.\nA fuller life.", style = MaterialTheme.typography.displayLarge)
        Text("90 days of showing up for yourself. Your progress belongs to you—even on the messy days.", color = Mist)
        PanelCard {
            Text("Start with what matters", style = MaterialTheme.typography.titleLarge)
            Text("Read 20 minutes. Make room for sleep. Move a little. Check in honestly.")
            Text("No streak resets. No public scores. Private on your phone.", color = Lime)
        }
        OutlinedTextField(why, { why = it }, label = { Text("Why are you doing this?") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
        OutlinedTextField(book, { book = it }, label = { Text("Your current book (optional)") }, modifier = Modifier.fillMaxWidth())
        PanelCard {
            TinyLabel("BEFORE YOU START", Peach)
            Text("Alcohol: safety comes first", style = MaterialTheme.typography.titleLarge)
            Text("If you drink heavily or get shaking, sweating, nausea or strong anxiety when you skip alcohol, get medical advice before stopping abruptly. Seizures, hallucinations or confusion need urgent medical help.", style = MaterialTheme.typography.bodyMedium)
            Text("This app supports habits; it cannot assess dependence or manage withdrawal. Porn goals are personal: focus on control and wellbeing, not shame.", color = Mist, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(acknowledged, { acknowledged = it })
                Text("I understand this safety guidance.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        FullButton("Begin my 90 days", { a.change { it.copy(plan = it.plan.copy(onboarded = true, started = a.today, why = why.trim(), book = book.trim())) } }, acknowledged)
        Text("You can change your goals any time in Plan.", color = Mist, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun TodayScreen(a: MainActivity, read: () -> Unit, reset: () -> Unit, meditate: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("") }
    val date = selected.ifBlank { a.today }; val d = a.data.day(date); val p = a.data.plan
    var dialog by remember { mutableStateOf("") }
    val dayNumber = (ChronoUnit.DAYS.between(LocalDate.parse(p.started), LocalDate.parse(a.today)) + 1).coerceAtLeast(1)
    Page {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TinyLabel("STEADY / 90", Lime); TinyLabel("DAY $dayNumber", Lime) }
        Heading(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), if (date == a.today) "Make room for\nwhat matters." else "A day in your story.", "One next step is enough to begin.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selected = LocalDate.parse(date).minusDays(1).toString() }, enabled = date > p.started) { Text("‹ Previous") }
            TextButton(onClick = { selected = "" }) { Text(if (date == a.today) "Today" else "Back to today") }
            TextButton(onClick = { selected = LocalDate.parse(date).plusDays(1).toString() }, enabled = date < a.today) { Text("Next ›") }
        }
        PanelCard(Color(0xFF2C3C2B)) {
            TinyLabel("YOUR REASON", Lime)
            Text(p.why.ifBlank { "More time for the life I want." }, style = MaterialTheme.typography.titleLarge)
            Text("${Metrics.actions(d)} ways you showed up today. Nothing here gets erased by a difficult day.", color = Mist, style = MaterialTheme.typography.bodyMedium)
        }
        SectionTitle("Your foundations")
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NightsStay, null, tint = Lilac)
                Column(Modifier.weight(1f)) { TinyLabel("LAST NIGHT"); Text(Metrics.hours(d.sleepMinutes), style = MaterialTheme.typography.headlineMedium) }
                TextButton(onClick = { dialog = "sleep" }) { Text("Edit") }
            }
            Text(if (d.manualSleepMinutes != null) "Manually recorded" else d.sleepWindow.ifBlank { "Missing sleep is not a failed day." }, color = Mist, style = MaterialTheme.typography.bodyMedium)
            Text("Your reference: ${p.sleepGoal} hours · no pass or fail", color = Mist, fontSize = 12.sp)
            if (d.sleepSource.isNotBlank() && d.manualSleepMinutes == null) Text("Source: ${d.sleepSource}", color = Mist, fontSize = 11.sp)
        }
        ActionCard(Icons.Outlined.MenuBook, "Read a little, grow a little", "${Metrics.minutes(d.readingSeconds)} / ${p.readingGoal} minutes", d.readingSeconds.toFloat() / (p.readingGoal * 60), Lime,
            if (date == a.today) "Open reading timer" else "Log reading", if (date == a.today) read else { { dialog = "reading" } })
        if (date == a.today) TextButton(onClick = { dialog = "reading" }) { Text("Edit today's reading total") }
        ActionCard(Icons.Outlined.Spa, "Daily meditation", "${d.meditationMinutes} / ${p.meditationGoal} minutes",
            d.meditationMinutes.toFloat() / p.meditationGoal, Lilac, "Log meditation", { meditate(date) })
        ActionCard(Icons.Outlined.DirectionsWalk, "Keep moving", "${d.steps?.toString() ?: "—"} / ${p.stepsGoal} steps", (d.steps ?: 0).toFloat() / p.stepsGoal, Peach, "Edit steps", { dialog = "steps" })
        ToggleRow("Strength session", "Aim for two whole-body sessions a week", d.workout) { a.editDay(date) { it.copy(workout = !d.workout) } }
        ToggleRow("Made time for bed", "Celebrate the routine you can control", d.bedtime) { a.editDay(date) { it.copy(bedtime = !d.bedtime) } }
        SectionTitle("An honest check-in", "PRIVATE")
        PanelCard {
            CheckIn("Alcohol", d.alcohol, "Alcohol-free", "Drank", { a.editDay(date) { it.copy(alcohol = "free", alcoholGrams = 0.0) } }, { dialog = "alcohol" }, { a.editDay(date) { it.copy(alcohol = "", alcoholGrams = 0.0) } })
            if (d.alcohol == "drank") Text("${"%.1f".format(d.alcoholGrams)} g alcohol recorded · tap Drank to edit daily total", color = Mist, fontSize = 12.sp)
            HorizontalDivider(color = Ink)
            CheckIn("Porn", d.porn, "Porn-free", "Used porn", { a.editDay(date) { it.copy(porn = "free") } }, { a.editDay(date) { it.copy(porn = "used") } }, { a.editDay(date) { it.copy(porn = "") } })
            if (d.porn == "used" || d.alcohol == "drank") Text("Thank you for being honest. Notice the trigger, then choose your next step. Your earlier progress still counts.", color = Peach, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = reset, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Spa, null); Spacer(Modifier.width(8.dp)); Text("I’m having an urge") }
        }
        PanelCard {
            TinyLabel("TAKE SOMETHING GOOD WITH YOU", Lime)
            Text("Today's small wins", style = MaterialTheme.typography.titleLarge)
            Text(d.wins.ifBlank { "Read a page? Took a walk? Asked for help? It counts." }, color = if (d.wins.isBlank()) Mist else Cream)
            TextButton(onClick = { dialog = "wins" }) { Text(if (d.wins.isBlank()) "Add a win" else "Edit wins") }
            if (d.note.isNotBlank()) Text(d.note, color = Mist)
            TextButton(onClick = { dialog = "note" }) { Text("Reflection / weekly review") }
        }
        if (p.extras) {
            SectionTitle("Optional extras")
            PanelCard {
                Text("Water: ${d.waterMl} / ${p.waterGoal} ml")
                Row { TextButton(onClick = { a.editDay(date) { it.copy(waterMl = (it.waterMl - 250).coerceAtLeast(0)) } }) { Text("− 250 ml") }; TextButton(onClick = { a.editDay(date) { it.copy(waterMl = (it.waterMl + 250).coerceAtMost(10000)) } }) { Text("+ 250 ml") } }
                Text("Writing: ${d.words} / ${p.writingGoal} words")
                TextButton(onClick = { dialog = "words" }) { Text("Log words") }
                ToggleRow("Balanced meal", "Variety over a fixed egg quota", d.balancedMeal) { a.editDay(date) { it.copy(balancedMeal = !d.balancedMeal) } }
                ToggleRow("Cold shower", "Optional, never required", d.coldShower) { a.editDay(date) { it.copy(coldShower = !d.coldShower) } }
            }
        }
    }
    when (dialog) {
        "sleep" -> NumberDialog("Sleep in minutes", "For example, 450 = 7h 30m. Clear to use imported sleep.", d.sleepMinutes?.toString() ?: "", 0..1440, { dialog = "" }, { value -> a.editDay(date) { it.copy(manualSleepMinutes = value) } })
        "steps" -> NumberDialog("Daily step total", "Manual total replaces the import. Clear to use Health Connect.", d.steps?.toString() ?: "", 0..150000, { dialog = "" }, { value -> a.editDay(date) { it.copy(manualSteps = value?.toLong()) } })
        "reading" -> NumberDialog("Daily reading minutes", "Set the total for this day. This replaces the saved total.", (d.readingSeconds / 60).toString(), 0..1440, { dialog = "" }, { value -> a.editDay(date) { it.copy(readingSeconds = (value ?: 0) * 60) } })
        "words" -> NumberDialog("Words written", "Set today's total.", d.words.toString(), 0..100000, { dialog = "" }, { value -> a.editDay(date) { it.copy(words = value ?: 0) } })
        "wins" -> TextDialog("Small wins", "One to three things you want to remember", d.wins, { dialog = "" }) { value -> a.editDay(date) { it.copy(wins = value) } }
        "note" -> TextDialog("Reflect, then look forward", "What helped? What got in the way? One change for tomorrow?", d.note, { dialog = "" }) { value -> a.editDay(date) { it.copy(note = value) } }
        "alcohol" -> AlcoholDialog(d.alcoholGrams, { dialog = "" }) { grams -> a.editDay(date) { it.copy(alcohol = "drank", alcoholGrams = grams) } }
    }
}

@Composable fun ActionCard(icon: ImageVector, title: String, value: String, progress: Float, tint: Color, action: String, onClick: () -> Unit) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null, tint = tint); Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)) }
        Text(value, color = tint)
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = tint, trackColor = Ink)
        TextButton(onClick = onClick) { Text(action, color = tint) }
    }
}
@Composable fun ToggleRow(title: String, subtitle: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(subtitle, color = Mist, style = MaterialTheme.typography.bodyMedium) }
        Checkbox(checked, { onClick() })
    }
}
@Composable fun CheckIn(title: String, status: String, good: String, other: String, setGood: () -> Unit, setOther: () -> Unit, clear: () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = status == "free", onClick = setGood, label = { Text(good) })
        FilterChip(selected = status == "drank" || status == "used", onClick = setOther, label = { Text(other) })
    }
    if (status.isEmpty()) Text("Not checked in yet", color = Mist, fontSize = 12.sp)
    else TextButton(onClick = clear, contentPadding = PaddingValues(0.dp)) { Text("Clear check-in", color = Mist, fontSize = 12.sp) }
}

@Composable fun ReadingScreen(a: MainActivity) {
    var tick by remember { mutableIntStateOf(0) }
    var editBook by rememberSaveable { mutableStateOf(false) }
    var finishBook by rememberSaveable { mutableStateOf(false) }
    val currentBook = a.data.plan.book
    LaunchedEffect(a.data.timer) { while (true) { tick++; delay(1000) } }
    val elapsed = tick.let { a.timerSeconds() }; val remaining = (1200 - elapsed).coerceAtLeast(0)
    val t = a.data.timer; val running = t?.running == true && t.boot == a.boot() && remaining > 0
    Page {
        Heading("A LITTLE EVERY DAY", "Turn a page.\nFind some space.", "Twenty minutes with a book. Nothing else to keep up with.")
        PanelCard {
            TinyLabel("ON YOUR NIGHTSTAND", Lime)
            Text(currentBook.ifBlank { "Your next good book" }, style = MaterialTheme.typography.headlineMedium)
            if (currentBook.isBlank()) {
                OutlinedButton(onClick = { editBook = true }) { Text(if (a.data.finishedBooks.isEmpty()) "Choose a book" else "Choose next book") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editBook = true }, modifier = Modifier.weight(1f)) { Text("Change book", textAlign = TextAlign.Center) }
                    TextButton(onClick = { finishBook = true }, modifier = Modifier.weight(1f)) { Text("Mark as finished", textAlign = TextAlign.Center) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp)) {
                drawArc(Panel, -90f, 360f, false, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                if (elapsed > 0) drawArc(Lime, -90f, 360f * elapsed / 1200, false, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:%02d".format(remaining / 60, remaining % 60), fontSize = 57.sp, fontFamily = FontFamily.Serif, color = Cream)
                TinyLabel(if (remaining == 0) "A PROMISE KEPT" else if (running) "JUST YOU & YOUR BOOK" else "TAKE YOUR TIME", Lime)
            }
        }
        if (remaining > 0) FullButton(if (running) "Pause" else if (t != null) "Continue reading" else "Start 20 minutes", { if (running) a.pauseTimer() else a.startTimer() })
        if (t != null) {
            FullButton(if (remaining == 0) "Save my reading session" else "Finish & save ${elapsed / 60} minutes", { a.saveTimer() }, elapsed > 0)
            TextButton(onClick = { a.change { it.copy(timer = null) } }) { Text("Discard this session", color = Mist) }
        }
        Text("Put your screen away. The timer keeps elapsed time with the screen off and when you leave the app. Return to save it; each session is capped at 20 minutes and logged on its starting date. No alarm will interrupt you.", color = Mist, style = MaterialTheme.typography.bodyMedium)
        if (t != null && t.boot != a.boot()) Text("Your phone restarted. Only time saved before the restart is retained; continue or start a new session.", color = Peach)
        PanelCard {
            TinyLabel("ALREADY YOURS")
            Text("${a.data.days.values.sumOf { it.readingSeconds } / 60} minutes of reading", style = MaterialTheme.typography.titleLarge)
            Text("A short session still counts. You can also edit a day's reading total on Today.", color = Mist)
        }
        if (a.data.finishedBooks.isNotEmpty()) PanelCard {
            SectionTitle("Books finished", a.data.finishedBooks.size.toString())
            a.data.finishedBooks.asReversed().forEach { book ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium)
                    Text("Finished ${LocalDate.parse(book.finishedOn).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", color = Mist, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (currentBook.isBlank()) TextButton(onClick = { a.change { it.undoLastBookFinish() } }) { Text("Undo last finish") }
        }
    }
    if (editBook) TextDialog(if (currentBook.isBlank()) "Choose a book" else "Change book", "Title and author", currentBook, { editBook = false }) { title -> a.change { it.copy(plan = it.plan.copy(book = title)) } }
    if (finishBook) AlertDialog(
        onDismissRequest = { finishBook = false },
        title = { Text("Finished this book?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(currentBook, style = MaterialTheme.typography.titleMedium)
            Text("Add it to your finished books. Then choose your next read whenever you're ready.")
            if (t != null) Text("Your timer stays as it is. Save your reading session separately.", color = Mist)
        } },
        confirmButton = { TextButton(onClick = { a.change { it.finishBook(a.today) }; finishBook = false }) { Text("Finish book") } },
        dismissButton = { TextButton(onClick = { finishBook = false }) { Text("Keep reading") } }
    )
}

@Composable fun ResetScreen(a: MainActivity) {
    var kind by rememberSaveable { mutableStateOf("Alcohol") }
    var trigger by rememberSaveable { mutableStateOf("Stress") }
    var action by rememberSaveable { mutableStateOf("Take a short walk") }
    var before by rememberSaveable { mutableFloatStateOf(5f) }
    var after by rememberSaveable { mutableFloatStateOf(5f) }
    var pauseEnd by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pauseEnd) { while (pauseEnd > 0 && now < pauseEnd) { now = System.currentTimeMillis(); delay(500) } }
    val seconds = ((pauseEnd - now) / 1000).coerceIn(0, 120)
    Page {
        Heading("PAUSE. NOTICE. CHOOSE.", "An urge isn't\nan instruction.", "You don't have to solve the whole day. Give yourself a little room for the next choice.")
        PanelCard { TinyLabel("YOUR IF–THEN PLAN", Lime); Text(a.data.plan.copingPlan); Text("Edit this in Plan → A reminder with a plan.", color = Mist, fontSize = 12.sp) }
        PanelCard(Color(0xFF31352C)) {
            Text("01   Change the scene", color = Peach, style = MaterialTheme.typography.titleLarge)
            Text("Set down the device or drink. Move to another room. Notice the urge without arguing with it.")
            Text("If you have withdrawal symptoms, seek medical help rather than using this timer to manage them.", color = Mist, style = MaterialTheme.typography.bodyMedium)
        }
        PanelCard {
            Text("02   Name what's here", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf("Alcohol", "Porn").forEach { value -> FilterChip(kind == value, { kind = value }, label = { Text(value) }) } }
            Text("Urge intensity: ${before.roundToInt()} / 10")
            Slider(before, { before = it }, valueRange = 0f..10f, steps = 9)
            ChoiceRows(listOf("Stress", "Boredom", "Loneliness", "Habit", "Social situation", "Other"), trigger) { trigger = it }
        }
        PanelCard {
            Text("03   Create a little space", style = MaterialTheme.typography.titleLarge)
            Text(if (pauseEnd == 0L) "Try a two-minute pause. Breathe comfortably and notice what changes." else if (seconds > 0) "%d:%02d remaining".format(seconds / 60, seconds % 60) else "Pause complete. Choose what helps you next.", color = Lime)
            FullButton(if (pauseEnd == 0L) "Start a 2-minute pause" else "Restart pause", { now = System.currentTimeMillis(); pauseEnd = now + 120_000 })
            ChoiceRows(listOf("Take a short walk", "Read a page", "Contact someone", "Leave the situation", "Make tea"), action) { action = it }
        }
        PanelCard {
            Text("04   Check back in", style = MaterialTheme.typography.titleLarge)
            Text("Urge now: ${after.roundToInt()} / 10")
            Slider(after, { after = it }, valueRange = 0f..10f, steps = 9)
            FullButton("Save this check-in", {
                val u = Urge(System.currentTimeMillis(), kind, trigger, action, before.roundToInt(), after.roundToInt())
                a.change { it.copy(urges = it.urges + u) }; pauseEnd = 0
                a.notify("Check-in saved. Asking for space is an action worth noticing.")
            })
            Text("An urge doesn't have to disappear for you to choose a helpful action. A check-in does not mark today alcohol- or porn-free.", color = Mist, style = MaterialTheme.typography.bodyMedium)
        }
        Text("If this keeps feeling hard to control, a clinician or therapist can help you make a plan. You don't have to wait for things to get worse.", color = Mist)
        TextButton(onClick = { a.openLink("https://www.niaaa.nih.gov/publications/brochures-and-fact-sheets/treatment-alcohol-problems-finding-and-getting-help") }) { Text("Alcohol treatment information ↗") }
        TextButton(onClick = { a.openLink("https://pmc.ncbi.nlm.nih.gov/articles/PMC12231474/") }) { Text("Evidence on therapy for problematic porn use ↗") }
    }
}
@Composable fun ChoiceRows(options: List<String>, selected: String, choose: (String) -> Unit) {
    options.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { value -> FilterChip(selected == value, onClick = { choose(value) }, label = { Text(value, fontSize = 12.sp) }) } } }
}

@Composable fun ProgressScreen(a: MainActivity) {
    val data = a.data; val days = data.days.values; val today = LocalDate.parse(a.today); val start = LocalDate.parse(data.plan.started)
    val last7 = (6 downTo 0).map { today.minusDays(it.toLong()) }.filter { !it.isBefore(start) }.map { data.day(it.toString()) }
    val sleep = last7.mapNotNull { it.sleepMinutes }
    val readingDays = days.count { it.readingSeconds >= data.plan.readingGoal * 60 }
    Page {
        Heading("PROGRESS THAT STAYS", "Look what\nyou're building.", "Your history is a collection of efforts, not a streak to protect.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("${days.sumOf { it.readingSeconds } / 60}", "minutes read", Modifier.weight(1f), Lime)
            Stat("${days.count { it.alcohol == "free" }}", "alcohol-free days", Modifier.weight(1f), Peach)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("${days.count { it.porn == "free" }}", "porn-free days", Modifier.weight(1f), Lilac)
            Stat("${data.urges.size}", "urge check-ins", Modifier.weight(1f), Lime)
        }
        Text("Only explicit check-ins count. An unlogged day stays unknown.", color = Mist, style = MaterialTheme.typography.bodyMedium)
        PanelCard {
            SectionTitle("Your 90-day path")
            Text("A square lights up when you record an action. More actions add colour; missed days never reset the path.", color = Mist, style = MaterialTheme.typography.bodyMedium)
            Canvas(Modifier.fillMaxWidth().height(146.dp).semantics { contentDescription = "90-day grid. ${days.count { Metrics.actions(it) > 0 }} days with recorded actions." }) {
                val gap = 5.dp.toPx(); val side = (size.width - 14 * gap) / 15
                repeat(90) { i ->
                    val date = start.plusDays(i.toLong()); val count = Metrics.actions(data.day(date.toString()))
                    val color = when { date > today -> Ink; count == 0 -> Color(0xFF354239); count < 3 -> Color(0xFF687F50); else -> Lime }
                    drawRoundRect(color, Offset((i % 15) * (side + gap), (i / 15) * (side + gap)), Size(side, side), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                }
            }
            Text("Day 1: ${data.plan.started}  ·  Day 90: ${start.plusDays(89)}", color = Mist, fontSize = 12.sp)
        }
        PanelCard {
            SectionTitle("This week", "LAST 7 DAYS")
            Text("${last7.sumOf { it.readingSeconds } / 60} reading minutes · ${last7.count { it.workout }} strength sessions")
            Text("${last7.sumOf { it.meditationMinutes }} meditation minutes · ${last7.count { it.meditationMinutes > 0 }} days of practice", color = Lilac)
            Text(if (sleep.isEmpty()) "No sleep records yet" else "Average sleep: ${Metrics.hours(sleep.average().roundToInt())} across ${sleep.size} recorded nights", color = Lilac)
            last7.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(LocalDate.parse(d.date).format(DateTimeFormatter.ofPattern("EEE")), modifier = Modifier.width(32.dp), color = Mist, fontSize = 12.sp)
                    LinearProgressIndicator(progress = { (d.readingSeconds.toFloat() / (data.plan.readingGoal * 60)).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(7.dp).clip(CircleShape), color = Lime, trackColor = Ink)
                    Text("${d.readingSeconds / 60}m", fontSize = 12.sp, modifier = Modifier.width(35.dp), textAlign = TextAlign.End)
                }
            }
            Text("Bars show reading minutes toward your daily goal.", color = Mist, fontSize = 12.sp)
        }
        PanelCard {
            TinyLabel("YOUR NEXT MILESTONE", Peach)
            val target = listOf(5, 10, 20, 40, 60, 90).firstOrNull { it > readingDays } ?: readingDays
            Text("$readingDays / $target reading days", style = MaterialTheme.typography.headlineMedium)
            Text(data.plan.reward, color = Mist)
            Text("Choose a reward that supports the life you want.", color = Mist, fontSize = 12.sp)
        }
        if (data.urges.isNotEmpty()) PanelCard {
            SectionTitle("Patterns to notice")
            val frequent = data.urges.groupingBy { it.trigger }.eachCount().maxBy { it.value }
            Text("Most logged trigger: ${frequent.key} (${frequent.value} check-ins)")
            Text("These are your observations, not a diagnosis or proof of cause.", color = Mist, style = MaterialTheme.typography.bodyMedium)
            data.urges.takeLast(3).reversed().forEach { u -> Text("${u.kind} · ${u.trigger}\n${u.before} → ${u.after} / 10 · ${u.action}", style = MaterialTheme.typography.bodyMedium) }
        }
        PanelCard { Text("Your weekly reflection", style = MaterialTheme.typography.titleLarge); Text("What made showing up easier? What deserves a smaller goal? What is one thing to try next week?", color = Mist); Text("Save your reflection from Today → Reflection / weekly review.", color = Lime, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun Stat(value: String, label: String, modifier: Modifier, tint: Color) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Panel).padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = tint); Text(label, style = MaterialTheme.typography.bodyMedium, color = Mist)
    }
}

@Composable fun PlanScreen(a: MainActivity) {
    val p = a.data.plan
    var edit by remember { mutableStateOf("") }
    var exportConfirm by remember { mutableStateOf(false) }
    Page {
        Heading("MAKE IT YOURS", "Ambition, with\nroom to breathe.", "Start small. Adjust the plan as you learn what helps.")
        PanelCard {
            SectionTitle("Your compass")
            Text(p.why)
            TextButton(onClick = { edit = "why" }) { Text("Edit my reason") }
            Text("Reward: ${p.reward}", color = Mist)
            TextButton(onClick = { edit = "reward" }) { Text("Choose a reward") }
        }
        PanelCard {
            SectionTitle("Daily targets")
            SettingRow("Reading", "${p.readingGoal} minutes") { edit = "reading" }
            SettingRow("Meditation", "${p.meditationGoal} minutes") { edit = "meditation" }
            SettingRow("Walking", "${p.stepsGoal} steps") { edit = "steps" }
            SettingRow("Sleep reference", "${p.sleepGoal} hours") { edit = "sleep" }
            Text("Sleep is a reference, never a pass/fail score. A consistent routine is the action to celebrate.", color = Mist, style = MaterialTheme.typography.bodyMedium)
            ToggleRow("Show optional extras", "Water, writing, balanced meals & cold showers", p.extras) { a.change { it.copy(plan = it.plan.copy(extras = !p.extras)) } }
            if (p.extras) { SettingRow("Water", "${p.waterGoal} ml") { edit = "water" }; SettingRow("Writing", "${p.writingGoal} words") { edit = "writing" } }
        }
        PanelCard {
            SectionTitle("A reminder with a plan")
            Text("Choose a time before a situation you find difficult. One gentle prompt each day can help you remember your next action; it cannot block websites or detect an urge.", color = Mist)
            ToggleRow("Daily pause reminder", "Discreet wording; off until you choose it", p.reminderEnabled) { a.setReminders(!p.reminderEnabled) }
            SettingRow("Around", "%02d:%02d".format(p.reminderHour, p.reminderMinute)) {
                android.app.TimePickerDialog(a, { _, hour, minute -> a.change { it.copy(plan = it.plan.copy(reminderHour = hour, reminderMinute = minute)) } }, p.reminderHour, p.reminderMinute, true).show()
            }
            Text("“A moment for yourself”\nPause, put your phone down, and choose what helps you next.", color = Lime)
            Text("Opens the urge-pause screen or meditation. No porn, alcohol, or personal log details in the notification. Hidden on the lock screen by default. Android may delay delivery to save battery.", color = Mist, style = MaterialTheme.typography.bodyMedium)
            if (p.reminderEnabled && !a.notificationsAllowed) Text("Notifications are blocked in Android settings. Enable them below to receive reminders.", color = Peach)
            Row { TextButton(onClick = { a.testReminder() }, enabled = p.reminderEnabled && a.notificationsAllowed) { Text("Send a preview") }; TextButton(onClick = { a.notificationSettings() }) { Text("Android settings") } }
            HorizontalDivider()
            TinyLabel("MAKE YOUR NEXT ACTION SPECIFIC")
            Text(p.copingPlan)
            TextButton(onClick = { edit = "coping" }) { Text("Edit my if–then plan") }
            Text("Try charging your phone outside the bedroom, leaving the triggering situation, or contacting someone. Keep whichever reminder helps; turn it off if it becomes noise.", color = Mist, style = MaterialTheme.typography.bodyMedium)
        }
        PanelCard {
            SectionTitle("Sleep & steps")
            Text(if (a.syncing) "Syncing sleep & steps…" else a.healthMessage, color = Mist)
            Text("Sleep and steps sync automatically when you open the app. Manage the connection or sync again here.", color = Mist, style = MaterialTheme.typography.bodyMedium)
            Text("Google Fit → Profile → Settings → Sync Fit with Health Connect. Allow sleep and steps sharing, then connect here.", style = MaterialTheme.typography.bodyMedium)
            FullButton("Connect Health Connect", { a.connectHealth() })
            TextButton(onClick = { a.syncHealth() }, enabled = !a.syncing) { Text(if (a.syncing) "Syncing…" else "Sync now") }
            Text("Sync refreshes the last 8 days. Missing records stay unknown. Sleep is assigned to the date you woke up; stages are used when supplied, otherwise session duration is an estimate.", color = Mist, style = MaterialTheme.typography.bodyMedium)
            TinyLabel("SLEEP DATA SOURCE")
            FilterChip(p.sleepOrigin.isEmpty(), { a.change { it.copy(plan = it.plan.copy(sleepOrigin = "")) }; a.syncHealth() }, label = { Text("Automatic · prefer Google Fit") })
            a.origins.forEach { origin -> FilterChip(p.sleepOrigin == origin, { a.change { it.copy(plan = it.plan.copy(sleepOrigin = origin)) }; a.syncHealth() }, label = { Text(origin, fontSize = 11.sp) }) }
            Text("One sleep source is used at a time to avoid counting multiple trackers twice. Steps use Health Connect's aggregate totals.", color = Mist, style = MaterialTheme.typography.bodyMedium)
        }
        PanelCard {
            SectionTitle("Private by default")
            Text("Encrypted on this phone. No account, ads or analytics. No automatic backups.", color = Mist)
            ToggleRow("Device-credential lock", "On launch and after 30 seconds away", p.lock) { a.setLock(!p.lock) }
            TextButton(onClick = { a.startActivity(Intent(a, PrivacyActivity::class.java)) }) { Text("Read privacy & data policy") }
            TextButton(onClick = { exportConfirm = true }) { Text("Export my records") }
            DeleteButton(a)
        }
        SectionTitle("Why these goals?")
        Evidence(a)
        Text("Steady 90 · personal preview 0.3\nA habit companion, not medical treatment.", color = Mist, fontSize = 12.sp)
    }
    when (edit) {
        "meditation" -> NumberDialog("Meditation goal", "Minutes per day. Five is a starting point, not a required therapeutic dose.", p.meditationGoal.toString(), 1..120, { edit = "" }) { v -> a.change { it.copy(plan = it.plan.copy(meditationGoal = v ?: 5)) } }
        "coping" -> TextDialog("My if–then plan", "If [situation], then I will [specific action].", p.copingPlan, { edit = "" }) { v -> a.change { it.copy(plan = it.plan.copy(copingPlan = v)) } }
        "why" -> TextDialog("Your reason", "What do you want more time and energy for?", p.why, { edit = "" }) { v -> a.change { it.copy(plan = it.plan.copy(why = v)) } }
        "reward" -> TextDialog("Your reward", "Something to look forward to", p.reward, { edit = "" }) { v -> a.change { it.copy(plan = it.plan.copy(reward = v)) } }
        "reading" -> NumberDialog("Reading goal", "Minutes per day. Timer sessions are 20 minutes; shorter sessions count too.", p.readingGoal.toString(), 1..180, { edit = "" }) { v -> a.change { it.copy(plan = it.plan.copy(readingGoal = v ?: 20)) } }
        "steps" -> NumberDialog("Step goal", "Build gradually from your baseline.", p.stepsGoal.toString(), 500..30000, { edit = "" }) { v -> a.change { it.copy(plan = it.plan.copy(stepsGoal = v ?: 7000)) } }
        "sleep" -> NumberDialog("Sleep reference", "Most adults need around 7–9 hours. This is not a score.", p.sleepGoal.toString(), 6..10, { edit = "" }) { v -> a.change { it.copy(plan = it.plan.copy(sleepGoal = v ?: 8)) } }
        "water" -> NumberDialog("Water reference", "Adjust for your needs and any medical fluid limits. Other fluids and food also count toward hydration.", p.waterGoal.toString(), 250..4000, { edit = "" }) { v -> a.change { it.copy(plan = it.plan.copy(waterGoal = v ?: 2000)) } }
        "writing" -> NumberDialog("Writing goal", "Words per day. Choose a number you can repeat.", p.writingGoal.toString(), 10..5000, { edit = "" }) { v -> a.change { it.copy(plan = it.plan.copy(writingGoal = v ?: 100)) } }
    }
    if (exportConfirm) AlertDialog(onDismissRequest = { exportConfirm = false }, title = { Text("Export private records?") }, text = { Text("This creates a readable file containing your habit, health and urge logs. Anyone with the file can read it, and the storage provider you choose may upload it.") }, confirmButton = { TextButton(onClick = { exportConfirm = false; a.exportData() }) { Text("Choose destination") } }, dismissButton = { TextButton(onClick = { exportConfirm = false }) { Text("Cancel") } })
}
@Composable fun SettingRow(title: String, value: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Text("$value  ›", color = Lime) }
}
@Composable fun DeleteButton(a: MainActivity) {
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick = { confirm = true }) { Text("Delete local data", color = Peach) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete all local records?") }, text = { Text("This permanently removes your plan, reading, habit logs and imported health records from Steady 90. Original health records and exported files are unaffected. This cannot be undone.") }, confirmButton = { TextButton(onClick = { confirm = false; a.deleteData() }) { Text("Delete all local records", color = Peach) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep my records") } })
}
@Composable fun Evidence(a: MainActivity) {
    val entries = listOf(
        Triple("Reminders · an aid, not a proven prevention", "Structured online self-help for problematic porn use has promising trial results, but high dropout limits confidence. Those results do not establish that notifications alone prevent porn use. Our prompt is a cue to use your coping plan; try it and review whether it helps.", "https://pmc.ncbi.nlm.nih.gov/articles/PMC8987418/"),
        Triple("Meditation · start gently", "Mindfulness may help some people with stress or anxiety. A short daily practice is a personal habit, not a demonstrated cure for problematic porn use. If it makes you feel worse, stop and try another grounding activity.", "https://www.nccih.nih.gov/health/meditation-and-mindfulness-effectiveness-and-safety/"),
        Triple("Sleep & movement · established guidance", "Most adults need 7–9 hours. Build toward 150–300 minutes of moderate activity weekly, plus strength work at least twice a week. Start at your current ability.", "https://www.who.int/europe/news-room/fact-sheets/item/physical-activity"),
        Triple("Sleep guidance", "Use sleep trends and how you feel. A tracker estimate is not a diagnosis or a grade.", "https://health.nih.gov/health-topics-a-z/healthy-sleep"),
        Triple("Alcohol · effective help exists", "Tracking, coping strategies, counselling and clinician-prescribed medication can help. Heavy drinking or withdrawal symptoms call for medical guidance before stopping.", "https://www.niaaa.nih.gov/publications/brochures-and-fact-sheets/treatment-alcohol-problems-finding-and-getting-help"),
        Triple("Porn · focus on control and wellbeing", "CBT and acceptance-based therapy show promise for problematic use, but research quality is limited. There is no universal 90-day reset. Distress and interference with life matter more than a streak.", "https://pmc.ncbi.nlm.nih.gov/articles/PMC12231474/"),
        Triple("Reading · a worthwhile routine", "A trial found improved self-reported sleep with bedtime reading. Twenty minutes is your chosen goal, not a medical threshold.", "https://pmc.ncbi.nlm.nih.gov/articles/PMC8740874/"),
        Triple("Steps · build from your baseline", "Benefits appear below 10,000 steps. Observational evidence supports about 7,000 as a useful target, not a requirement or proof of cause.", "https://www.thelancet.com/pdfs/journals/lanpub/PIIS2468-2667%2825%2900164-1.pdf"),
        Triple("Habits · no magic deadline", "Habit formation varies widely and often takes months. Ninety days is a useful review period, not a promise of transformation.", "https://pmc.ncbi.nlm.nih.gov/articles/PMC11641623/"),
        Triple("Cold showers · optional", "Evidence for lasting wellbeing benefits is limited. They are not necessary for progress, and cold exposure is not suitable for everyone.", "https://pmc.ncbi.nlm.nih.gov/articles/PMC11778651/"),
        Triple("Food & water · individual needs", "Choose varied meals rather than a fixed egg quota. Hydration depends on activity, weather and medical needs; avoid forcing a universal water amount.", "https://www.nhs.uk/live-well/eat-well/food-guidelines-and-food-labels/water-drinks-nutrition/"),
        Triple("Small wins · gentle reflection", "Gratitude interventions show benefits on average. There is no established need to log exactly five wins or write 1,000 words.", "https://pmc.ncbi.nlm.nih.gov/articles/PMC10393216/")
    )
    entries.forEach { (title, body, url) -> PanelCard { Text(title, style = MaterialTheme.typography.titleLarge); Text(body, color = Mist, style = MaterialTheme.typography.bodyMedium); TextButton(onClick = { a.openLink(url) }) { Text("Read the source ↗") } } }
}
@Composable fun PrivacyScreen(close: () -> Unit) { Surface(color = Ink) { Page { Heading("YOUR INFORMATION", "Privacy & data", "Steady 90 · November 2026"); Text(PRIVACY); FullButton("Done", close) } } }

@Composable fun NumberDialog(title: String, hint: String, initial: String, range: IntRange, dismiss: () -> Unit, save: (Int?) -> Unit) {
    var value by remember { mutableStateOf(initial) }; val valid = value.isBlank() || value.toIntOrNull()?.let { it in range } == true
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(hint); OutlinedTextField(value, { value = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = !valid, label = { Text("Value") }); if (!valid) Text("Enter ${range.first}–${range.last}.", color = Peach) } },
        confirmButton = { TextButton(enabled = valid, onClick = { save(value.toIntOrNull()); dismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
@Composable fun TextDialog(title: String, hint: String, initial: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(value, { if (it.length <= 4000) value = it }, label = { Text(hint) }, minLines = 3, maxLines = 8) }, confirmButton = { TextButton(onClick = { save(value.trim()); dismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
@Composable fun AlcoholDialog(previousGrams: Double, dismiss: () -> Unit, save: (Double) -> Unit) {
    var volume by remember { mutableStateOf("500") }; var abv by remember { mutableStateOf("5") }; var count by remember { mutableStateOf("1") }
    var direct by remember { mutableStateOf(false) }; var gramsText by remember { mutableStateOf(if (previousGrams > 0) "%.1f".format(java.util.Locale.US, previousGrams) else "") }
    val v = volume.toIntOrNull(); val strength = abv.replace(',', '.').toDoubleOrNull(); val n = count.toIntOrNull()
    val grams = if (direct) gramsText.replace(',', '.').toDoubleOrNull() else if (v != null && v in 1..3000 && strength != null && strength > 0 && strength <= 100 && n != null && n in 1..30) Metrics.alcoholGrams(v, strength, n) else null
    val valid = grams != null && grams.isFinite() && grams > 0 && grams <= 2000
    AlertDialog(onDismissRequest = dismiss, title = { Text("Alcohol: daily total") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("This replaces today's total. For different drink types, use the total grams option. No amount here is labelled safe.", style = MaterialTheme.typography.bodyMedium)
        if (previousGrams > 0) Text("Currently recorded: ${"%.1f".format(previousGrams)} g", color = Peach)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(direct, { direct = it }); Text("Enter total grams instead") }
        if (direct) OutlinedTextField(gramsText, { gramsText = it }, label = { Text("Grams of pure alcohol") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        else {
            OutlinedTextField(volume, { volume = it }, label = { Text("Drink size (ml)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(abv, { abv = it }, label = { Text("Alcohol by volume (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(count, { count = it }, label = { Text("Number of these drinks today") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        if (valid) Text("Daily total: ${"%.1f".format(grams)} g alcohol", color = Peach)
        Text("If stopping causes shaking, sweating or nausea, seek medical advice. Severe symptoms need urgent care.", color = Mist, fontSize = 12.sp)
    } }, confirmButton = { TextButton(enabled = valid, onClick = { save(grams!!); dismiss() }) { Text("Save daily total") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
