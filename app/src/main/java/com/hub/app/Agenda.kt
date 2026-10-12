package com.hub.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

data class Ev(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

fun loadEvents(ctx: Context, days: Long): List<Ev> {
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val end = start + days * 86_400_000L
    val out = mutableListOf<Ev>()
    CalendarContract.Instances.query(
        ctx.contentResolver,
        arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
        ),
        start, end,
    )?.use { c ->
        while (c.moveToNext()) {
            out.add(Ev(c.getLong(0), c.getString(1) ?: "(sans titre)", c.getLong(2), c.getLong(3), c.getInt(4) == 1))
        }
    }
    return out.sortedBy { it.begin }
}

fun insertEvent(ctx: Context, title: String, begin: Long, end: Long): Boolean {
    var calId = -1L
    ctx.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
        null,
        "${CalendarContract.Calendars.IS_PRIMARY} DESC",
    )?.use { if (it.moveToFirst()) calId = it.getLong(0) }
    if (calId < 0) return false
    val v = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calId)
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DTSTART, begin)
        put(CalendarContract.Events.DTEND, end)
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
    }
    return ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) != null
}

@Composable
fun AgendaApp(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH) }
    val hourFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = ZoneId.systemDefault()

    AppScreen("Agenda", onBack, actions = { IconBtn("plus", "Ajouter", C.accent) { adding = true } }) {
        PermissionGate(
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            "Autoriser l'agenda",
            "Pour afficher vos événements et en créer.",
        ) {
            val events by rememberAsync(refresh, initial = emptyList<Ev>()) { loadEvents(ctx, 60) }
            if (events.isEmpty()) Empty("Aucun événement dans les 60 prochains jours.")
            val byDay = events.groupBy {
                if (it.allDay) Instant.ofEpochMilli(it.begin).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                else Instant.ofEpochMilli(it.begin).atZone(zone).toLocalDate()
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                byDay.forEach { (day, list) ->
                    item(key = "d$day") {
                        Text(
                            day.format(dayFmt).replaceFirstChar { it.uppercase() },
                            fontSize = 13.sp, color = C.accent, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    items(list, key = { "e${it.id}-${it.begin}" }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1).padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                Text(e.title)
                                val range = if (e.allDay) "Toute la journée" else
                                    Instant.ofEpochMilli(e.begin).atZone(zone).format(hourFmt) + " – " +
                                        Instant.ofEpochMilli(e.end).atZone(zone).format(hourFmt)
                                Text(range, fontSize = 11.sp, color = C.muted)
                            }
                            IconBtn("trash", "Supprimer", C.muted) {
                                try {
                                    ctx.contentResolver.delete(
                                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id), null, null,
                                    )
                                } catch (ex: Exception) { toast(ctx, "Suppression impossible") }
                                refresh++
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        var title by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now()) }
        var time by remember { mutableStateOf(LocalTime.of((LocalTime.now().hour + 1) % 24, 0)) }
        var minutes by remember { mutableIntStateOf(60) }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = C.s2,
            title = { Text("Nouvel événement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubField(title, { title = it }, "Titre", Modifier.fillMaxWidth())
                    TextButton(onClick = { pickDate(ctx, date) { date = it } }) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)), color = C.text)
                    }
                    TextButton(onClick = { pickTime(ctx, time) { time = it } }) {
                        Text("Début : " + time.format(hourFmt), color = C.text)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 120, 240).forEach { m ->
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (minutes == m) C.accent else C.s3)
                                    .clickable { minutes = m }.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) { Text(if (m < 60) "$m min" else "${m / 60} h", fontSize = 12.sp, color = if (minutes == m) C.ink else C.sub) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val b = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    val ok = try { insertEvent(ctx, title.ifBlank { "Événement" }, b, b + minutes * 60_000L) } catch (e: Exception) { false }
                    if (!ok) toast(ctx, "Aucun agenda disponible sur ce téléphone")
                    adding = false; refresh++
                }) { Text("Créer", color = C.accent) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Annuler", color = C.sub) } },
        )
    }
}
