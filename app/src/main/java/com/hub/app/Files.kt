package com.hub.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile

/** Navigateur de fichiers : l'utilisateur choisit un dossier racine (accès sécurisé SAF). */
@Composable
fun FilesApp(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var rootUri by remember { mutableStateOf(store.get("files_root")) }
    val stack = remember { mutableStateListOf<Uri>() }
    var refresh by remember { mutableIntStateOf(0) }
    var newFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<DocumentFile?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) { /* permission non persistante */ }
            store.put("files_root", uri.toString())
            rootUri = uri.toString()
            stack.clear()
        }
    }

    BackHandler(stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val root = rootUri?.let { Uri.parse(it) }
    if (root != null && stack.isEmpty()) stack.add(root)
    val current = stack.lastOrNull()
    val dir = current?.let { DocumentFile.fromTreeUri(ctx, it) ?: DocumentFile.fromSingleUri(ctx, it) }

    AppScreen(
        dir?.name ?: "Fichiers",
        onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() },
        actions = {
            if (root != null) {
                IconBtn("plus", "Nouveau dossier", C.accent) { newFolder = true }
                IconBtn("files", "Changer de dossier", C.sub) { picker.launch(null) }
            }
        },
    ) {
        if (dir == null) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Choisissez un dossier à parcourir", fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Par sécurité, Android demande de désigner le dossier (ex. Documents ou Téléchargements). " +
                        "Hub n'a accès qu'à celui-là.",
                    color = C.sub, fontSize = 14.sp,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Choisir un dossier") { picker.launch(null) }
            }
        } else {
            // Le « refresh » force le rechargement après création / suppression.
            val children = remember(current, refresh) {
                (dir.listFiles().toList()).sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name?.lowercase() }))
            }
            if (children.isEmpty()) Empty("Dossier vide.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(children, key = { it.uri.toString() }) { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1)
                            .clickable {
                                if (f.isDirectory) stack.add(f.uri) else openFile(ctx, f.uri, f.type ?: "*/*")
                            }.padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(if (f.isDirectory) "files" else "notes", if (f.isDirectory) Color(0xFF7FDCE8) else C.sub, 22.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp)) {
                            Text(f.name ?: "?", maxLines = 1)
                            if (!f.isDirectory) Text(humanSize(f.length()), fontSize = 11.sp, color = C.muted)
                        }
                        if (!f.isDirectory) IconBtn("check", "Partager", C.sub) { shareUri(ctx, f.uri, f.type ?: "*/*") }
                        IconBtn("trash", "Supprimer", C.muted) { confirmDelete = f }
                    }
                }
            }

            if (newFolder) {
                var name by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { newFolder = false },
                    containerColor = C.s2,
                    title = { Text("Nouveau dossier") },
                    text = { HubField(name, { name = it }, "Nom", Modifier.fillMaxWidth()) },
                    confirmButton = {
                        TextButton(onClick = {
                            if (name.isNotBlank()) dir.createDirectory(name.trim())
                            newFolder = false; refresh++
                        }) { Text("Créer", color = C.accent) }
                    },
                    dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Annuler", color = C.sub) } },
                )
            }
            confirmDelete?.let { f ->
                AlertDialog(
                    onDismissRequest = { confirmDelete = null },
                    containerColor = C.s2,
                    title = { Text("Supprimer « ${f.name} » ?") },
                    text = { Text("Cette action est définitive.", color = C.sub) },
                    confirmButton = {
                        TextButton(onClick = { f.delete(); confirmDelete = null; refresh++ }) { Text("Supprimer", color = C.danger) }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annuler", color = C.sub) } },
                )
            }
        }
    }
}

fun humanSize(b: Long): String = when {
    b >= 1_000_000_000 -> String.format("%.1f Go", b / 1e9)
    b >= 1_000_000 -> String.format("%.1f Mo", b / 1e6)
    b >= 1_000 -> String.format("%.0f Ko", b / 1e3)
    else -> "$b o"
}
