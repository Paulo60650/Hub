package com.hub.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ───────────────────────── Coffre chiffré ───────────────────────── */

data class Secret(val id: Long, val title: String, val login: String, val secret: String, val note: String)

private fun loadVault(store: Store): List<Secret> {
    val raw = store.get("vault") ?: return emptyList()
    return try {
        val arr = JSONArray(Crypto.dec(raw))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Secret(o.getLong("id"), o.getString("t"), o.getString("l"), o.getString("s"), o.getString("n"))
        }
    } catch (e: Exception) { emptyList() }
}

private fun saveVault(store: Store, list: List<Secret>) {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().put("id", it.id).put("t", it.title).put("l", it.login).put("s", it.secret).put("n", it.note)) }
    store.put("vault", Crypto.enc(arr.toString()))
}

private fun copySensitive(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Hub", text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    cm.setPrimaryClip(clip)
    toast(ctx, "Copié")
}

private fun generatePassword(len: Int = 18): String {
    val chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!#$%&*+-?"
    val r = SecureRandom()
    return (1..len).map { chars[r.nextInt(chars.length)] }.joinToString("")
}

@Composable
fun VaultApp(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val list = remember { mutableStateListOf<Secret>().also { it.addAll(loadVault(store)) } }
    var adding by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Long?>(null) }

    AppScreen("Coffre", onBack, actions = { IconBtn("plus", "Ajouter", C.accent) { adding = true } }) {
        Text("Chiffré (AES-256) avec une clé protégée par le téléphone.", fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(bottom = 10.dp))
        if (list.isEmpty()) Empty("Aucun secret enregistré.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list.toList(), key = { it.id }) { s ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.Medium)
                            if (s.login.isNotBlank()) Text(s.login, fontSize = 12.sp, color = C.muted)
                        }
                        IconBtn("eye", "Afficher", C.sub) { shown = if (shown == s.id) null else s.id }
                        IconBtn("copy", "Copier", C.sub) { copySensitive(ctx, s.secret) }
                        IconBtn("trash", "Supprimer", C.muted) { list.remove(s); saveVault(store, list) }
                    }
                    if (shown == s.id) {
                        Text(s.secret, color = C.accent, modifier = Modifier.padding(top = 6.dp))
                        if (s.note.isNotBlank()) Text(s.note, fontSize = 13.sp, color = C.sub, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }

    if (adding) {
        var t by remember { mutableStateOf("") }
        var l by remember { mutableStateOf("") }
        var p by remember { mutableStateOf("") }
        var n by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = C.s2,
            title = { Text("Nouveau secret") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HubField(t, { t = it }, "Titre (site, carte…)", Modifier.fillMaxWidth())
                    HubField(l, { l = it }, "Identifiant", Modifier.fillMaxWidth())
                    HubField(p, { p = it }, "Mot de passe / code", Modifier.fillMaxWidth())
                    TextButton(onClick = { p = generatePassword() }) { Text("Générer un mot de passe", color = C.accent) }
                    HubField(n, { n = it }, "Note", Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (t.isNotBlank()) {
                        list.add(0, Secret(newId(), t.trim(), l.trim(), p, n.trim()))
                        saveVault(store, list)
                    }
                    adding = false
                }) { Text("Enregistrer", color = C.accent) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Annuler", color = C.sub) } },
        )
    }
}

/* ───────────────────────── Dictaphone ───────────────────────── */

private class RecState {
    var recorder: MediaRecorder? = null
    var player: MediaPlayer? = null
    var current: File? = null
}

@Composable
fun RecorderApp(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dir = remember { File(ctx.filesDir, "records").apply { mkdirs() } }
    val st = remember { RecState() }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            try { st.recorder?.stop() } catch (e: Exception) { }
            st.recorder?.release(); st.recorder = null
            st.player?.release(); st.player = null
        }
    }
    LaunchedEffect(recording) {
        val t0 = System.currentTimeMillis()
        while (recording) { elapsed = System.currentTimeMillis() - t0; delay(200) }
    }

    fun start() {
        val f = File(dir, "memo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".m4a")
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            st.recorder = r; st.current = f; recording = true
        } catch (e: Exception) {
            toast(ctx, "Enregistrement impossible"); f.delete()
        }
    }
    fun stop() {
        try { st.recorder?.stop() } catch (e: Exception) { st.current?.delete() }
        st.recorder?.release(); st.recorder = null
        recording = false; refresh++
    }
    fun play(f: File) {
        st.player?.release(); st.player = null
        if (playing == f.name) { playing = null; return }
        try {
            val mp = MediaPlayer()
            mp.setDataSource(f.absolutePath); mp.prepare(); mp.start()
            mp.setOnCompletionListener { playing = null }
            st.player = mp; playing = f.name
        } catch (e: Exception) { toast(ctx, "Lecture impossible") }
    }

    AppScreen("Dictaphone", onBack) {
        PermissionGate(listOf(Manifest.permission.RECORD_AUDIO), "Autoriser le micro", "Pour enregistrer des mémos vocaux.") {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%02d:%02d".format(elapsed / 60000, elapsed / 1000 % 60),
                        fontSize = 44.sp, fontWeight = FontWeight.Light, color = if (recording) C.danger else C.muted,
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(Color(0xFFFF8F85))
                            .clickable { if (recording) stop() else { elapsed = 0; start() } },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(if (recording) "stop" else "recorder", C.ink, 32.dp) }
                }
            }
            val files = remember(refresh) { (dir.listFiles()?.toList() ?: emptyList()).sortedByDescending { it.lastModified() } }
            if (files.isEmpty()) Empty("Aucun enregistrement.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.name }) { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1).padding(start = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBtn(if (playing == f.name) "pause" else "play", "Lecture", C.accent) { play(f) }
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(SimpleDateFormat("d MMM yyyy HH:mm", Locale.FRENCH).format(Date(f.lastModified())))
                            Text(humanSize(f.length()), fontSize = 11.sp, color = C.muted)
                        }
                        IconBtn("share", "Partager", C.sub) { shareUri(ctx, fileUri(ctx, f), "audio/mp4") }
                        IconBtn("trash", "Supprimer", C.muted) {
                            if (playing == f.name) { st.player?.release(); st.player = null; playing = null }
                            f.delete(); refresh++
                        }
                    }
                }
            }
        }
    }
}

/* ───────────────────────── Scanner de documents ───────────────────────── */

fun decodeUpright(f: File, maxSide: Int): Bitmap? {
    val bmp = decodeSampled(appCtxHolder!!, Uri.fromFile(f), maxSide) ?: return null
    val orient = try {
        ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
    val deg = when (orient) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (deg == 0f) return bmp
    val m = Matrix().apply { postRotate(deg) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

/** Contexte applicatif, renseigné au démarrage (utilisé par les utilitaires hors composition). */
var appCtxHolder: Context? = null

fun buildPdf(pages: List<File>, out: File) {
    val doc = PdfDocument()
    pages.forEachIndexed { i, f ->
        val bmp = decodeUpright(f, 2000) ?: return@forEachIndexed
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
        val scale = minOf(595f / bmp.width, 842f / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        page.canvas.drawBitmap(bmp, null, RectF((595 - w) / 2, (842 - h) / 2, (595 + w) / 2, (842 + h) / 2), null)
        doc.finishPage(page)
    }
    out.outputStream().use { doc.writeTo(it) }
    doc.close()
}

@Composable
fun ScannerApp(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tmpDir = remember { File(ctx.filesDir, "scans/tmp").apply { mkdirs() } }
    val pdfDir = remember { File(ctx.filesDir, "scans/pdf").apply { mkdirs() } }
    val pages = remember { mutableStateListOf<File>() }
    val pending = remember { arrayOfNulls<File>(1) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending[0]
        if (f != null) { if (ok) pages.add(f) else f.delete() }
        pending[0] = null
    }

    AppScreen("Scanner", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("＋ Page (photo)", Modifier.weight(1f)) {
                val f = File(tmpDir, "p_${System.currentTimeMillis()}.jpg")
                pending[0] = f
                try { camera.launch(fileUri(ctx, f)) } catch (e: Exception) { toast(ctx, "Appareil photo indisponible") }
            }
            if (pages.isNotEmpty()) {
                PrimaryButton(if (busy) "…" else "Créer le PDF", Modifier.weight(1f), color = Color(0xFF7FDCE8)) {
                    if (!busy) {
                        busy = true
                        val snapshot = pages.toList()
                        val out = File(pdfDir, "scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".pdf")
                        Thread {
                            try { buildPdf(snapshot, out) } catch (e: Exception) { out.delete() }
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                snapshot.forEach { it.delete() }
                                pages.clear(); busy = false; refresh++
                            }
                        }.start()
                    }
                }
            }
        }
        if (pages.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pages.toList(), key = { it.name }) { f ->
                    Box {
                        AsyncBitmap(Uri.fromFile(f), 400, Modifier.size(90.dp, 120.dp).clip(RoundedCornerShape(10.dp)), ContentScale.Crop)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(C.bg)
                                .clickable { pages.remove(f); f.delete() },
                            contentAlignment = Alignment.Center,
                        ) { Glyph("x", C.text, 14.dp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Mes scans", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        val pdfs = remember(refresh) { (pdfDir.listFiles()?.toList() ?: emptyList()).sortedByDescending { it.lastModified() } }
        if (pdfs.isEmpty()) Empty("Aucun PDF pour le moment.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pdfs, key = { it.name }) { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1)
                        .clickable { openFile(ctx, fileUri(ctx, f), "application/pdf") }.padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(f.name, maxLines = 1, fontSize = 14.sp)
                        Text(humanSize(f.length()), fontSize = 11.sp, color = C.muted)
                    }
                    IconBtn("share", "Partager", C.sub) { shareUri(ctx, fileUri(ctx, f), "application/pdf") }
                    IconBtn("trash", "Supprimer", C.muted) { f.delete(); refresh++ }
                }
            }
        }
    }
}
