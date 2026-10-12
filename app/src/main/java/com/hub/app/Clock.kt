package com.hub.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AlarmItem(val id: Long, val hour: Int, val minute: Int, val label: String, val enabled: Boolean, val repeat: Boolean)

fun parseAlarm(o: JSONObject) =
    AlarmItem(o.getLong("id"), o.getInt("h"), o.getInt("m"), o.getString("l"), o.getBoolean("e"), o.getBoolean("r"))
fun encAlarm(a: AlarmItem) =
    JSONObject().put("id", a.id).put("h", a.hour).put("m", a.minute).put("l", a.label).put("e", a.enabled).put("r", a.repeat)

/** Planification des réveils / minuteur via AlarmManager (setAlarmClock : fiable, sans permission spéciale). */
object Alarms {
    const val TIMER_ID = -1L

    private fun pending(ctx: Context, id: Long, label: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).putExtra("id", id).putExtra("label", label)
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun nextTrigger(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        var t = ZonedDateTime.now(zone).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!t.isAfter(ZonedDateTime.now(zone))) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    fun scheduleAt(ctx: Context, id: Long, label: String, at: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(ctx, id, label))
    }

    fun schedule(ctx: Context, a: AlarmItem) = scheduleAt(ctx, a.id, a.label.ifBlank { "Réveil" }, nextTrigger(a.hour, a.minute))

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, id, ""))
    }

    fun ring(ctx: Context, label: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("alarm", "Réveils et minuteur", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Touchez pour ouvrir Hub")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(label.hashCode(), n)
    }

    /** Replanifie tous les réveils actifs (après redémarrage du téléphone). */
    fun rescheduleAll(ctx: Context) {
        val store = Store(ctx)
        store.loadList("alarms", ::parseAlarm).filter { it.enabled }.forEach { schedule(ctx, it) }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        val label = intent.getStringExtra("label") ?: "Réveil"
        Alarms.ring(ctx, label)
        if (id != Alarms.TIMER_ID) {
            val store = Store(ctx)
            val list = store.loadList("alarms", ::parseAlarm).toMutableList()
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) {
                val a = list[i]
                if (a.repeat) Alarms.schedule(ctx, a) else list[i] = a.copy(enabled = false)
                store.saveList("alarms", list, ::encAlarm)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Alarms.rescheduleAll(ctx)
    }
}

private fun two(n: Long) = n.toString().padStart(2, '0')

@Composable
fun ClockApp(store: Store, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    AppScreen("Horloge", onBack) {
        Tabs(listOf("Heure", "Réveils", "Minuteur", "Chrono"), tab) { tab = it }
        when (tab) {
            0 -> WorldClock()
            1 -> AlarmsTab(store)
            2 -> TimerTab(store)
            else -> StopwatchTab()
        }
    }
}

@Composable
private fun WorldClock() {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000) } }
    val zones = listOf(
        "Paris" to "Europe/Paris", "Londres" to "Europe/London", "New York" to "America/New_York",
        "Los Angeles" to "America/Los_Angeles", "Montréal" to "America/Toronto", "Dubaï" to "Asia/Dubai",
        "Tokyo" to "Asia/Tokyo", "Sydney" to "Australia/Sydney",
    )
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), fontSize = 54.sp, fontWeight = FontWeight.Light)
    Text(
        now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() },
        color = C.sub, modifier = Modifier.padding(bottom = 16.dp),
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(zones) { (name, id) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.s1).padding(14.dp)) {
                Text(name, Modifier.weight(1f))
                Text(now.withZoneSameInstant(ZoneId.of(id)).format(fmt), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AlarmsTab(store: Store) {
    val ctx = LocalContext.current
    val data = remember { Persisted(store, "alarms", ::parseAlarm, ::encAlarm) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotif() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End) {
        AddButton {
            ensureNotif()
            pickTime(ctx, LocalTime.of(7, 0)) { t ->
                val a = AlarmItem(newId(), t.hour, t.minute, "Réveil", true, true)
                data.items.add(a); data.save(); Alarms.schedule(ctx, a)
            }
        }
    }
    if (data.items.isEmpty()) Empty("Aucun réveil.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(data.items.sortedBy { it.hour * 60 + it.minute }, key = { it.id }) { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(String.format("%02d:%02d", a.hour, a.minute), fontSize = 30.sp, fontWeight = FontWeight.Light)
                    Text(if (a.repeat) "Tous les jours" else "Une fois", fontSize = 11.sp, color = C.muted)
                }
                Switch(
                    checked = a.enabled,
                    onCheckedChange = { on ->
                        val i = data.items.indexOfFirst { it.id == a.id }
                        data.items[i] = a.copy(enabled = on); data.save()
                        if (on) { ensureNotif(); Alarms.schedule(ctx, a) } else Alarms.cancel(ctx, a.id)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.accent, checkedThumbColor = C.ink),
                )
                IconBtn("trash", "Supprimer", C.muted) {
                    Alarms.cancel(ctx, a.id); data.items.remove(a); data.save()
                }
            }
        }
    }
}

@Composable
private fun TimerTab(store: Store) {
    val ctx = LocalContext.current
    var endAt by remember { mutableStateOf(store.get("timer_end")?.toLongOrNull() ?: 0L) }
    var minutes by remember { mutableStateOf("5") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val remaining = endAt - now
    if (endAt > 0 && remaining > 0) {
        val s = remaining / 1000
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text("${two(s / 60)}:${two(s % 60)}", fontSize = 72.sp, fontWeight = FontWeight.Light)
        }
        PrimaryButton("Annuler", Modifier.fillMaxWidth(), color = C.danger) {
            Alarms.cancel(ctx, Alarms.TIMER_ID); endAt = 0; store.put("timer_end", "0")
        }
    } else {
        if (endAt > 0) { endAt = 0; store.put("timer_end", "0") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            listOf(1, 5, 10, 15, 30).forEach { m ->
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(C.s2).clickable { minutes = m.toString() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) { Text("$m min", fontSize = 13.sp) }
            }
        }
        HubField(minutes, { minutes = it.filter { c -> c.isDigit() } }, "Minutes", Modifier.fillMaxWidth(), keyboard = KeyboardType.Number)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Démarrer", Modifier.fillMaxWidth()) {
            val m = minutes.toLongOrNull() ?: 0L
            if (m > 0) {
                if (Build.VERSION.SDK_INT >= 33 && !hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
                    notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val at = System.currentTimeMillis() + m * 60_000L
                Alarms.scheduleAt(ctx, Alarms.TIMER_ID, "Minuteur terminé", at)
                endAt = at; store.put("timer_end", at.toString())
            }
        }
    }
}

@Composable
private fun StopwatchTab() {
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var acc by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }
    LaunchedEffect(running) {
        while (running) { tick = SystemClock.elapsedRealtime(); delay(30) }
    }
    val total = acc + if (running) (maxOf(tick, startedAt) - startedAt) else 0L
    fun fmt(ms: Long) = "${two(ms / 60000)}:${two(ms / 1000 % 60)}.${two(ms / 10 % 100)}"
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(fmt(total), fontSize = 56.sp, fontWeight = FontWeight.Light)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PrimaryButton(if (running) "Pause" else "Démarrer", Modifier.weight(1f)) {
            if (running) { acc = total; running = false } else { startedAt = SystemClock.elapsedRealtime(); tick = startedAt; running = true }
        }
        PrimaryButton(if (running) "Tour" else "Réinitialiser", Modifier.weight(1f), color = C.s3) {
            if (running) laps.add(0, total) else { acc = 0; laps.clear() }
        }
    }
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(laps.size) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Text("Tour ${laps.size - i}", Modifier.weight(1f), color = C.sub)
                Text(fmt(laps[i]))
            }
        }
    }
}
