package com.hub.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.net.URLEncoder

/* ───────────────────────── Navigateur ───────────────────────── */

data class Bookmark(val title: String, val url: String)

private fun toUrl(s: String): String {
    val t = s.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(".") && !t.contains(" ") -> "https://$t"
        else -> "https://duckduckgo.com/?q=" + URLEncoder.encode(t, "UTF-8")
    }
}

@Composable
fun BrowserApp(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val marks = remember {
        Persisted(store, "bookmarks", { Bookmark(it.getString("t"), it.getString("u")) }, { JSONObject().put("t", it.title).put("u", it.url) })
    }
    var input by remember { mutableStateOf("") }
    var canBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(100) }
    val web = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    canBack = view.canGoBack(); input = url
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
            }
            loadUrl("https://duckduckgo.com")
        }
    }
    DisposableEffect(Unit) { onDispose { web.destroy() } }
    BackHandler(canBack) { web.goBack() }

    AppScreen("Navigateur", onBack, actions = {
        IconBtn("star", "Ajouter aux favoris", Color(0xFFFFD36B)) {
            val u = web.url
            if (u != null && marks.items.none { it.url == u }) {
                marks.items.add(Bookmark(web.title?.take(24)?.ifBlank { null } ?: u, u)); marks.save()
            }
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubField(input, { input = it }, "Adresse ou recherche", Modifier.weight(1f))
            AddButton { web.loadUrl(toUrl(input)) }
        }
        if (progress < 100) LinearProgressIndicator(
            progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            color = C.accent, trackColor = C.s3,
        )
        if (marks.items.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(marks.items.toList(), key = { it.url }) { b ->
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(C.s2).clickable { web.loadUrl(b.url) }
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(b.title, fontSize = 12.sp, maxLines = 1)
                        Box(
                            Modifier.size(28.dp).clickable { marks.items.remove(b); marks.save() },
                            contentAlignment = Alignment.Center,
                        ) { Glyph("x", C.muted, 12.dp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        AndroidView(
            factory = { web },
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/* ───────────────────────── Lecteur audio ───────────────────────── */

data class Track(val id: Long, val title: String, val artist: String, val dur: Long, val uri: Uri)

fun loadTracks(ctx: Context): List<Track> {
    val out = mutableListOf<Track>()
    val A = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    ctx.contentResolver.query(
        A,
        arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
        ),
        "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 2000) {
            val id = c.getLong(0)
            out.add(Track(id, c.getString(1) ?: "?", c.getString(2) ?: "", c.getLong(3), android.content.ContentUris.withAppendedId(A, id)))
        }
    }
    return out
}

/** Lecteur unique : la musique continue quand on revient à l'accueil. */
object Player {
    private var mp: MediaPlayer? = null
    private var queue: List<Track> = emptyList()
    private var index = 0
    var current by mutableStateOf<Track?>(null)
    var playing by mutableStateOf(false)

    fun play(ctx: Context, list: List<Track>, i: Int) {
        queue = list; index = i
        val t = list[i]
        mp?.release()
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build(),
            )
            p.setDataSource(ctx.applicationContext, t.uri)
            p.setOnCompletionListener { next(ctx) }
            p.prepare(); p.start()
            mp = p; current = t; playing = true
        } catch (e: Exception) {
            toast(ctx, "Lecture impossible"); current = null; playing = false
        }
    }

    fun toggle() {
        val p = mp ?: return
        if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
    }

    fun next(ctx: Context) { if (queue.isNotEmpty()) play(ctx, queue, (index + 1) % queue.size) }
    fun prev(ctx: Context) { if (queue.isNotEmpty()) play(ctx, queue, (index - 1 + queue.size) % queue.size) }
    fun position(): Int = try { mp?.currentPosition ?: 0 } catch (e: Exception) { 0 }
    fun seek(ms: Int) { try { mp?.seekTo(ms) } catch (e: Exception) { } }
    fun stop() { mp?.release(); mp = null; current = null; playing = false }
}

private fun mmss(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

@Composable
fun MusicApp(onBack: () -> Unit) {
    val ctx = LocalContext.current
    AppScreen("Audio", onBack) {
        PermissionGate(listOf(audioPerm()), "Autoriser l'audio", "Pour lister et lire la musique de votre téléphone.") {
            val tracks by rememberAsync(initial = emptyList<Track>()) { loadTracks(ctx) }
            var query by remember { mutableStateOf("") }
            val shown = tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
            HubField(query, { query = it }, "Rechercher", Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            if (shown.isEmpty()) Empty("Aucun morceau.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown.size, key = { shown[it].id }) { i ->
                    val t = shown[i]
                    val active = Player.current?.id == t.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (active) C.s3 else C.s1)
                            .clickable { Player.play(ctx, shown, i) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, maxLines = 1, color = if (active) C.accent else C.text)
                            Text(t.artist, fontSize = 11.sp, color = C.muted, maxLines = 1)
                        }
                        Text(mmss(t.dur), fontSize = 11.sp, color = C.muted)
                    }
                }
            }
            val cur = Player.current
            if (cur != null) {
                var pos by remember { mutableIntStateOf(0) }
                LaunchedEffect(cur, Player.playing) { while (true) { pos = Player.position(); delay(500) } }
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(C.s1).padding(12.dp)) {
                    Text(cur.title, maxLines = 1, fontWeight = FontWeight.Medium)
                    Slider(
                        value = pos.toFloat().coerceIn(0f, maxOf(1f, cur.dur.toFloat())),
                        onValueChange = { pos = it.toInt() },
                        onValueChangeFinished = { Player.seek(pos) },
                        valueRange = 0f..maxOf(1f, cur.dur.toFloat()),
                        colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.s3),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconBtn("prev", "Précédent", C.text) { Player.prev(ctx) }
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFFF9DB8)).clickable { Player.toggle() },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(if (Player.playing) "pause" else "play", C.ink, 24.dp) }
                        IconBtn("next", "Suivant", C.text) { Player.next(ctx) }
                        IconBtn("x", "Arrêter", C.muted) { Player.stop() }
                    }
                }
            }
        }
    }
}

/* ───────────────────────── Cartes (OpenStreetMap) ───────────────────────── */

@Composable
fun MapsApp(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osm")
            osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        }
        true
    }
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(46.6, 2.4))
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        owner.lifecycle.addObserver(obs)
        map.onResume()
        onDispose { owner.lifecycle.removeObserver(obs); map.onPause(); map.onDetach() }
    }

    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<Place?>(null) }

    fun show(p: Place) {
        target = p
        map.overlays.clear()
        val m = Marker(map)
        m.position = GeoPoint(p.lat, p.lon)
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        m.title = p.name
        map.overlays.add(m)
        map.controller.setZoom(15.0)
        map.controller.animateTo(m.position)
        map.invalidate()
    }

    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) lastLocation(ctx)?.let { show(it) } ?: toast(ctx, "Position indisponible")
    }

    AppScreen("Cartes", onBack) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubField(query, { query = it }, "Rechercher un lieu", Modifier.weight(1f))
            AddButton {
                if (query.isNotBlank()) scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching {
                            val j = JSONArray(
                                httpGet(
                                    "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(query.trim(), "UTF-8"),
                                    mapOf("User-Agent" to "HubApp/1.0"),
                                ),
                            )
                            if (j.length() == 0) null else {
                                val o = j.getJSONObject(0)
                                Place(o.getString("display_name"), o.getDouble("lat"), o.getDouble("lon"))
                            }
                        }
                    }
                    val p = r.getOrNull()
                    if (p != null) show(p) else toast(ctx, if (r.isFailure) "Pas de connexion" else "Lieu introuvable")
                }
            }
        }
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                if (hasPerm(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    lastLocation(ctx)?.let { show(it) } ?: toast(ctx, "Position indisponible")
                } else locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) { Text("Ma position", color = C.accent) }
            target?.let { t ->
                TextButton(onClick = {
                    try {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${t.lat},${t.lon}(${Uri.encode(t.name.take(40))})"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (e: Exception) { toast(ctx, "Aucune appli de navigation") }
                }) { Text("Itinéraire", color = C.accent) }
            }
        }
        AndroidView(
            factory = { map },
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.height(8.dp))
    }
}
