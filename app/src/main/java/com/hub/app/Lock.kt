package com.hub.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val VERIFY = 0
private const val CREATE = 1
private const val CONFIRM = 2

@Composable
fun LockScreen(store: Store, onUnlocked: () -> Unit) {
    var phase by remember { mutableIntStateOf(if (store.hasPin()) VERIFY else CREATE) }
    var entry by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var askReset by remember { mutableStateOf(false) }

    LaunchedEffect(entry) {
        if (entry.length < 4) return@LaunchedEffect
        when (phase) {
            VERIFY -> if (store.checkPin(entry)) onUnlocked() else { error = "Code incorrect"; entry = "" }
            CREATE -> { first = entry; entry = ""; error = ""; phase = CONFIRM }
            CONFIRM -> if (entry == first) {
                store.setPin(entry)
                onUnlocked()
            } else {
                error = "Les deux codes ne correspondent pas"
                entry = ""; first = ""; phase = CREATE
            }
        }
    }

    val title = when (phase) {
        VERIFY -> "Saisissez votre code"
        CREATE -> "Créez votre code"
        else -> "Confirmez votre code"
    }
    val subtitle = when (phase) {
        CREATE -> "4 chiffres, demandés à chaque ouverture"
        CONFIRM -> "Saisissez-le une seconde fois"
        else -> ""
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(C.accent),
            contentAlignment = Alignment.Center,
        ) { Glyph("lock", C.ink, 26.dp) }
        Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (error.isNotEmpty()) error else subtitle,
            fontSize = 14.sp,
            color = if (error.isNotEmpty()) C.danger else C.sub,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            for (i in 0 until 4) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (i < entry.length) C.accent else C.s3),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { d -> Key(d) { if (entry.length < 4) { error = ""; entry += d } } }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(76.dp))
            Key("0") { if (entry.length < 4) { error = ""; entry += "0" } }
            Box(
                Modifier.size(76.dp).clip(CircleShape).clickable { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
                contentAlignment = Alignment.Center,
            ) { Glyph("erase", C.sub, 26.dp) }
        }
        Spacer(Modifier.height(20.dp))
        if (phase == VERIFY) {
            TextButton(onClick = { askReset = true }) { Text("Code oublié ?", color = C.muted) }
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Spacer(Modifier.height(16.dp))
    }

    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            containerColor = C.s2,
            title = { Text("Réinitialiser l'application ?") },
            text = {
                Text(
                    "Sans le code, il n'est pas possible de récupérer vos données : " +
                        "notes, tâches, budget… seront effacés et vous pourrez créer un nouveau code.",
                    color = C.sub,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.resetAll()
                    askReset = false
                    entry = ""; first = ""; error = ""; phase = CREATE
                }) { Text("Tout effacer", color = C.danger) }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text("Annuler", color = C.sub) } },
        )
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(C.s2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 28.sp, fontWeight = FontWeight.Medium) }
}
