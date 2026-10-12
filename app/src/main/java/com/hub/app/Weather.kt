package com.hub.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Place(val name: String, val lat: Double, val lon: Double)
data class DayW(val date: String, val code: Int, val min: Double, val max: Double, val rain: Int)
data class Wx(val temp: Double, val feels: Double, val code: Int, val wind: Double, val hum: Int, val days: List<DayW>)

fun wxLabel(code: Int): Pair<String, String> = when (code) {
    0 -> "☀️" to "Dégagé"
    1, 2 -> "🌤️" to "Peu nuageux"
    3 -> "☁️" to "Couvert"
    45, 48 -> "🌫️" to "Brouillard"
    in 51..57 -> "🌦️" to "Bruine"
    in 61..67 -> "🌧️" to "Pluie"
    in 71..77 -> "❄️" to "Neige"
    in 80..82 -> "🌦️" to "Averses"
    85, 86 -> "🌨️" to "Averses de neige"
    95, 96, 99 -> "⛈️" to "Orage"
    else -> "🌡️" to "—"
}

fun searchPlaces(q: String): List<Place> {
    val url = "https://geocoding-api.open-meteo.com/v1/search?count=5&language=fr&name=" + URLEncoder.encode(q, "UTF-8")
    val arr = JSONObject(httpGet(url)).optJSONArray("results") ?: return emptyList()
    return (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        val label = listOf(o.optString("name"), o.optString("admin1"), o.optString("country"))
            .filter { s -> s.isNotBlank() }.joinToString(", ")
        Place(label, o.getDouble("latitude"), o.getDouble("longitude"))
    }
}

fun fetchWeather(p: Place): Wx {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
        "&timezone=auto&forecast_days=7"
    val j = JSONObject(httpGet(url))
    val c = j.getJSONObject("current")
    val d = j.getJSONObject("daily")
    val times = d.getJSONArray("time")
    val days = (0 until times.length()).map {
        DayW(
            times.getString(it), d.getJSONArray("weather_code").getInt(it),
            d.getJSONArray("temperature_2m_min").getDouble(it), d.getJSONArray("temperature_2m_max").getDouble(it),
            d.getJSONArray("precipitation_probability_max").optInt(it, 0),
        )
    }
    return Wx(
        c.getDouble("temperature_2m"), c.getDouble("apparent_temperature"), c.getInt("weather_code"),
        c.getDouble("wind_speed_10m"), c.getInt("relative_humidity_2m"), days,
    )
}

@SuppressLint("MissingPermission")
fun lastLocation(ctx: Context): Place? {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val loc = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time } ?: return null
    return Place("Ma position", loc.latitude, loc.longitude)
}

@Composable
fun WeatherApp(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var place by remember {
        mutableStateOf(
            store.get("wx_place")?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?.let { Place(it.getString("n"), it.getDouble("la"), it.getDouble("lo")) },
        )
    }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Place>()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun choose(p: Place) {
        place = p; results = emptyList(); query = ""
        store.put("wx_place", JSONObject().put("n", p.name).put("la", p.lat).put("lo", p.lon).toString())
    }

    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) lastLocation(ctx)?.let { choose(it) } ?: toast(ctx, "Position indisponible")
    }

    AppScreen("Météo", onBack) {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubField(query, { query = it }, "Rechercher une ville", Modifier.weight(1f))
            AddButton {
                if (query.isNotBlank()) scope.launch {
                    error = null
                    val r = withContext(Dispatchers.IO) { runCatching { searchPlaces(query.trim()) } }
                    r.onSuccess { results = it; if (it.isEmpty()) error = "Aucun résultat" }
                        .onFailure { error = "Pas de connexion" }
                }
            }
        }
        TextButton(onClick = {
            if (hasPerm(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                lastLocation(ctx)?.let { choose(it) } ?: toast(ctx, "Position indisponible")
            } else locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }) { Text("Utiliser ma position", color = C.accent) }
        error?.let { Text(it, color = C.danger, fontSize = 13.sp) }
        results.forEach { r ->
            Text(
                r.name,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.s1)
                    .clickable { choose(r) }.padding(14.dp),
            )
            Spacer(Modifier.height(6.dp))
        }

        val p = place
        if (p == null) {
            Empty("Choisissez une ville pour voir la météo.")
        } else {
            var refresh by remember { mutableIntStateOf(0) }
            val wx by rememberAsync(p, refresh, initial = null as Wx?) { fetchWeather(p) }
            val w = wx
            if (w == null) {
                Empty("Chargement…")
            } else {
                val (emoji, label) = wxLabel(w.code)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.s1).padding(20.dp)) {
                    Text(p.name, fontSize = 14.sp, color = C.sub)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${w.temp.toInt()}°", fontSize = 64.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                        Text(emoji, fontSize = 52.sp)
                    }
                    Text("$label · ressenti ${w.feels.toInt()}°", color = C.sub)
                    Text("Vent ${w.wind.toInt()} km/h · Humidité ${w.hum} %", fontSize = 12.sp, color = C.muted)
                }
                Spacer(Modifier.height(12.dp))
                val fmt = DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH)
                w.days.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(C.s1)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            LocalDate.parse(d.date).format(fmt).replaceFirstChar { it.uppercase() },
                            Modifier.weight(1f),
                        )
                        Text("${d.rain} %", fontSize = 11.sp, color = C.muted, modifier = Modifier.padding(end = 10.dp))
                        Text(wxLabel(d.code).first, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                        Text("${d.min.toInt()}° / ${d.max.toInt()}°", fontSize = 13.sp)
                    }
                }
            }
        }
      }
    }
}
