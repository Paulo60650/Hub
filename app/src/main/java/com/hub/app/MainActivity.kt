package com.hub.app

import android.graphics.Color as AColor
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    private var locked by mutableStateOf(true)
    private var stoppedAt = 0L
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appCtxHolder = applicationContext
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        store = Store(applicationContext)
        setContent {
            HubTheme {
                Surface(Modifier.fillMaxSize(), color = C.bg, contentColor = C.text) {
                    HubRoot(store, locked, onLockedChange = { locked = it })
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Reverrouillage après le délai choisi dans les réglages (30 s par défaut).
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt >= lockDelayMs(store)) locked = true
    }
}

@Composable
fun HubRoot(store: Store, locked: Boolean, onLockedChange: (Boolean) -> Unit) {
    var current by remember { mutableStateOf<AppDef?>(null) }
    var arg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(locked) { if (locked) { current = null; arg = null } }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        if (locked) {
            LockScreen(store) { onLockedChange(false) }
            return@Box
        }
        BackHandler(enabled = current != null) { current = null; arg = null }
        val back: () -> Unit = { current = null; arg = null }
        val go: (String, String?) -> Unit = { id, a ->
            val target = (page1 + page2 + dock).firstOrNull { it.id == id }
            if (target != null) { current = target; arg = a }
        }
        when (current?.id) {
            null -> HomeScreen(onOpen = { a -> current = a; arg = null }, onLock = { onLockedChange(true) })
            "notes" -> NotesApp(store, back)
            "todo" -> TodoApp(store, back)
            "budget" -> BudgetApp(store, back)
            "calc" -> CalcApp(back)
            "journal" -> JournalApp(store, back)
            "habits" -> HabitsApp(store, back)
            "vault" -> VaultApp(store, back)
            "phone" -> PhoneApp(back)
            "contacts" -> ContactsApp(back, go)
            "messages" -> MessagesApp(store, arg, back)
            "agenda" -> AgendaApp(back)
            "mail" -> MailApp(store, back)
            "gallery" -> GalleryApp(back)
            "clock" -> ClockApp(store, back)
            "files" -> FilesApp(store, back)
            "weather" -> WeatherApp(store, back)
            "recorder" -> RecorderApp(back)
            "scanner" -> ScannerApp(back)
            "browser" -> BrowserApp(store, back)
            "music" -> MusicApp(back)
            "maps" -> MapsApp(back)
            "settings" -> SettingsApp(store, back) { onLockedChange(true) }
            else -> back()
        }
    }
}
