package com.hub.app

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppDef(val id: String, val label: String, val color: Color, val internal: Boolean = true)

val page1 = listOf(
    AppDef("notes", "Notes", Color(0xFFF4DC7A), true),
    AppDef("todo", "À faire", Color(0xFF9BE8C4), true),
    AppDef("contacts", "Contacts", Color(0xFF8CC8FF)),
    AppDef("gallery", "Galerie", Color(0xFFFF9DB8)),
    AppDef("clock", "Horloge", Color(0xFFFFB48A)),
    AppDef("calc", "Calculette", Color(0xFFC6E87A), true),
    AppDef("files", "Fichiers", Color(0xFF7FDCE8)),
    AppDef("weather", "Météo", Color(0xFF9DB4FF)),
    AppDef("vault", "Coffre", Color(0xFFB9A8FF), true),
    AppDef("budget", "Budget", Color(0xFFE3A0F0), true),
    AppDef("recorder", "Dictaphone", Color(0xFFFF8F85)),
    AppDef("settings", "Réglages", Color(0xFFC9CDD6)),
)

val page2 = listOf(
    AppDef("scanner", "Scanner", Color(0xFF7FDCE8)),
    AppDef("habits", "Habitudes", Color(0xFFC6E87A), true),
    AppDef("journal", "Journal", Color(0xFFE8C79A), true),
    AppDef("browser", "Navigateur", Color(0xFF9DB4FF)),
    AppDef("music", "Audio", Color(0xFFFF9DB8)),
    AppDef("maps", "Cartes", Color(0xFF9BE8C4)),
)

val dock = listOf(
    AppDef("phone", "Appels", Color(0xFF8FE0A5)),
    AppDef("messages", "Messages", Color(0xFF8CC8FF)),
    AppDef("mail", "Mail", Color(0xFFFFB48A)),
    AppDef("agenda", "Agenda", Color(0xFFFF8F85)),
)

/** Ouvre l'application du téléphone qui correspond (mail, messages, etc.). */
fun launchSystem(ctx: Context, id: String) {
    fun category(c: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c)
    val intent: Intent = when (id) {
        "mail" -> category(Intent.CATEGORY_APP_EMAIL)
        "messages" -> category(Intent.CATEGORY_APP_MESSAGING)
        "agenda" -> category(Intent.CATEGORY_APP_CALENDAR)
        "gallery" -> category(Intent.CATEGORY_APP_GALLERY)
        "music" -> category(Intent.CATEGORY_APP_MUSIC)
        "maps" -> category(Intent.CATEGORY_APP_MAPS)
        "browser" -> category(Intent.CATEGORY_APP_BROWSER)
        "phone" -> Intent(Intent.ACTION_DIAL)
        "contacts" -> Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        "clock" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        "files" -> Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        "weather" -> Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "météo")
        "recorder" -> Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        "scanner" -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        "settings" -> Intent(Settings.ACTION_SETTINGS)
        else -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
    }
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Aucune application disponible pour cette action", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(onOpen: (AppDef) -> Unit, onLock: () -> Unit) {
    val pager = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = pager.currentPage == 1) { scope.launch { pager.animateScrollToPage(0) } }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); delay(20_000) }
    }
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
        .replaceFirstChar { it.uppercase() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(time, fontSize = 52.sp, fontWeight = FontWeight.Light)
                Text(date, fontSize = 15.sp, color = C.sub)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(C.s2).clickable(onClick = onLock),
                contentAlignment = Alignment.Center,
            ) { Glyph("lock", C.sub, 20.dp) }
        }
        Spacer(Modifier.height(20.dp))

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            AppGrid(if (page == 0) page1 else page2, onOpen)
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            for (i in 0 until 2) {
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) C.accent else C.s3),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp)
                .clip(RoundedCornerShape(28.dp)).background(C.s1).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            dock.forEach { app -> AppTile(app, showLabel = false, onOpen) }
        }
    }
}

@Composable
private fun AppGrid(apps: List<AppDef>, onOpen: (AppDef) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        AppTile(app, showLabel = true, onOpen)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppDef, showLabel: Boolean, onOpen: (AppDef) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onOpen(app) },
    ) {
        Box(
            Modifier.size(if (showLabel) 62.dp else 54.dp)
                .clip(RoundedCornerShape(if (showLabel) 19.dp else 17.dp))
                .background(app.color),
            contentAlignment = Alignment.Center,
        ) { Glyph(app.id, C.ink, if (showLabel) 26.dp else 24.dp) }
        if (showLabel) {
            Spacer(Modifier.height(7.dp))
            Text(app.label, fontSize = 11.sp, color = C.sub, maxLines = 1)
        }
    }
}
