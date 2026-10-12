package com.hub.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

val lockDelays = listOf("Immédiat" to 0L, "30 s" to 30_000L, "1 min" to 60_000L, "5 min" to 300_000L, "15 min" to 900_000L)

fun lockDelayMs(store: Store): Long = store.get("lock_delay")?.toLongOrNull() ?: 30_000L

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.s1).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun RowLink(text: String, color: androidx.compose.ui.graphics.Color = C.text, onClick: () -> Unit) {
    Text(text, color = color, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp))
}

@Composable
fun SettingsApp(store: Store, onBack: () -> Unit, onReset: () -> Unit) {
    val ctx = LocalContext.current
    var delay by remember { mutableStateOf(lockDelayMs(store)) }
    var changePin by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    AppScreen("Réglages", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("SÉCURITÉ") {
                RowLink("Changer le code PIN") { changePin = true }
                Text("Verrouillage automatique après", fontSize = 13.sp, color = C.sub)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    lockDelays.forEach { (label, ms) ->
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(if (delay == ms) C.accent else C.s3)
                                .clickable { delay = ms; store.put("lock_delay", ms.toString()) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) { Text(label, fontSize = 12.sp, color = if (delay == ms) C.ink else C.sub, maxLines = 1) }
                    }
                }
                Text("Délai passé hors de l'appli avant de redemander le code.", fontSize = 11.sp, color = C.muted)
            }
            Section("TÉLÉPHONE") {
                RowLink("Autorisations de Hub") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                RowLink("Réglages Android") {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                RowLink("Wi-Fi") { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                RowLink("Bluetooth") { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                RowLink("Affichage / luminosité") { ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                RowLink("Son") { ctx.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            Section("DONNÉES") {
                RowLink("Tout effacer et réinitialiser Hub", C.danger) { confirmReset = true }
            }
            Section("À PROPOS") {
                Text("Hub 2.0 — données stockées uniquement sur ce téléphone.", fontSize = 13.sp, color = C.sub)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (changePin) {
        var old by remember { mutableStateOf("") }
        var n1 by remember { mutableStateOf("") }
        var n2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf("") }
        fun digits(s: String) = s.filter { it.isDigit() }.take(4)
        AlertDialog(
            onDismissRequest = { changePin = false },
            containerColor = C.s2,
            title = { Text("Changer le code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HubField(old, { old = digits(it) }, "Code actuel", Modifier.fillMaxWidth(), keyboard = KeyboardType.NumberPassword)
                    HubField(n1, { n1 = digits(it) }, "Nouveau code (4 chiffres)", Modifier.fillMaxWidth(), keyboard = KeyboardType.NumberPassword)
                    HubField(n2, { n2 = digits(it) }, "Confirmer", Modifier.fillMaxWidth(), keyboard = KeyboardType.NumberPassword)
                    if (err.isNotEmpty()) Text(err, color = C.danger, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        !store.checkPin(old) -> err = "Code actuel incorrect"
                        n1.length != 4 -> err = "Le nouveau code doit avoir 4 chiffres"
                        n1 != n2 -> err = "Les deux codes diffèrent"
                        else -> { store.setPin(n1); changePin = false; toast(ctx, "Code modifié") }
                    }
                }) { Text("Valider", color = C.accent) }
            },
            dismissButton = { TextButton(onClick = { changePin = false }) { Text("Annuler", color = C.sub) } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = C.s2,
            title = { Text("Tout effacer ?") },
            text = {
                Text(
                    "Notes, tâches, budget, journal, coffre, mails configurés, enregistrements et scans seront supprimés. " +
                        "Vos SMS, contacts, photos et agenda ne sont pas touchés.",
                    color = C.sub,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.loadList("alarms", ::parseAlarm).forEach { Alarms.cancel(ctx, it.id) }
                    Alarms.cancel(ctx, Alarms.TIMER_ID)
                    Player.stop()
                    store.resetAll()
                    Crypto.wipe()
                    confirmReset = false
                    onReset()
                }) { Text("Effacer", color = C.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annuler", color = C.sub) } },
        )
    }
}
