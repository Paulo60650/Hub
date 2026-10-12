package com.hub.app

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette « Obsidian » de la maquette. */
object C {
    val bg = Color(0xFF0D0E11)
    val s1 = Color(0xFF16181D)
    val s2 = Color(0xFF1E2128)
    val s3 = Color(0xFF2A2D35)
    val text = Color(0xFFEDEEF0)
    val sub = Color(0xFFB4B8C0)
    val muted = Color(0xFF8C919B)
    val accent = Color(0xFF9BE8C4)
    val ink = Color(0xFF0D0E11)
    val danger = Color(0xFFFF9B9B)
}

@Composable
fun HubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent, onPrimary = C.ink,
            background = C.bg, onBackground = C.text,
            surface = C.s1, onSurface = C.text,
        ),
    ) { content() }
}

/** Icônes au trait (style Feather), construites à partir de chemins SVG 24x24. */
object Ic {
    private fun circ(cx: Double, cy: Double, r: Double) =
        "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0 "

    private fun rect(x: Double, y: Double, w: Double, h: Double, r: Double) =
        "M${x + r} $y h${w - 2 * r} a$r $r 0 0 1 $r $r v${h - 2 * r} a$r $r 0 0 1 ${-r} $r " +
            "h${-(w - 2 * r)} a$r $r 0 0 1 ${-r} ${-r} v${-(h - 2 * r)} a$r $r 0 0 1 $r ${-r} z "

    private val paths: Map<String, String> = mapOf(
        "notes" to "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8",
        "todo" to "M9 11l3 3L22 4 M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11",
        "mail" to "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M22 6l-10 7L2 6",
        "messages" to "M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z",
        "phone" to "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z",
        "agenda" to rect(3.0, 4.0, 18.0, 18.0, 2.0) + "M16 2v4 M8 2v4 M3 10h18",
        "contacts" to "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 " + circ(12.0, 7.0, 4.0),
        "gallery" to rect(3.0, 3.0, 18.0, 18.0, 2.0) + circ(8.5, 8.5, 1.5) + "M21 15l-5-5L5 21",
        "clock" to circ(12.0, 12.0, 10.0) + "M12 6v6l4 2",
        "calc" to rect(4.0, 2.0, 16.0, 20.0, 2.0) + "M8 6h8 M16 14v4 M8 14h.01 M12 14h.01 M8 18h.01 M12 18h.01",
        "files" to "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z",
        "weather" to "M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z",
        "vault" to rect(3.0, 11.0, 18.0, 11.0, 2.0) + "M7 11V7a5 5 0 0 1 10 0v4",
        "budget" to "M21.21 15.89A10 10 0 1 1 8 2.83 M22 12A10 10 0 0 0 12 2v10z",
        "recorder" to "M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z M19 10v2a7 7 0 0 1-14 0v-2 M12 19v4 M8 23h8",
        "settings" to "M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6",
        "scanner" to "M8 3H5a2 2 0 0 0-2 2v3 M21 8V5a2 2 0 0 0-2-2h-3 M3 16v3a2 2 0 0 0 2 2h3 M16 21h3a2 2 0 0 0 2-2v-3",
        "habits" to "M22 12h-4l-3 9L9 3l-3 9H2",
        "journal" to "M4 19.5A2.5 2.5 0 0 1 6.5 17H20 M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z",
        "browser" to circ(12.0, 12.0, 10.0) + "M2 12h20 M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z",
        "music" to "M9 18V5l12-2v13 " + circ(6.0, 18.0, 3.0) + circ(18.0, 16.0, 3.0),
        "maps" to "M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z " + circ(12.0, 10.0, 3.0),
        "play" to "M5 3l14 9-14 9V3z",
        "pause" to "M6 4h4v16H6z M14 4h4v16h-4z",
        "stop" to "M6 6h12v12H6z",
        "next" to "M5 4l10 8-10 8V4z M19 5v14",
        "prev" to "M19 20L9 12l10-8v16z M5 19V5",
        "camera" to "M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z " + circ(12.0, 13.0, 4.0),
        "share" to "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8 M16 6l-4-4-4 4 M12 2v13",
        "eye" to "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z " + circ(12.0, 12.0, 3.0),
        "copy" to rect(9.0, 9.0, 13.0, 13.0, 2.0) + "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1",
        "refresh" to "M23 4v6h-6 M1 20v-6h6 M3.51 9a9 9 0 0 1 14.85-3.36L23 10 M1 14l4.64 4.36A9 9 0 0 0 20.49 15",
        "search" to circ(11.0, 11.0, 8.0) + "M21 21l-4.35-4.35",
        "arrow" to "M5 12h14 M12 5l7 7-7 7",
        "home" to "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10",
        "star" to "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z",
        "x" to "M18 6L6 18 M6 6l12 12",
        "pin" to "M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z " + circ(12.0, 10.0, 3.0),
        "back" to "M19 12H5 M12 19l-7-7 7-7",
        "plus" to "M12 5v14 M5 12h14",
        "erase" to "M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z M18 9l-6 6 M12 9l6 6",
        "trash" to "M3 6h18 M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6 M10 11v6 M14 11v6 M9 6V4h6v2",
        "check" to "M20 6L9 17l-5-5",
        "lock" to rect(3.0, 11.0, 18.0, 11.0, 2.0) + "M7 11V7a5 5 0 0 1 10 0v4",
    )

    private val cache = HashMap<String, ImageVector>()

    fun get(name: String): ImageVector = cache.getOrPut(name) {
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(paths.getValue(name)),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}

@Composable
fun Glyph(name: String, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = Ic.get(name), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun IconBtn(name: String, label: String, tint: Color = C.text, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip44()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(name, tint, 22.dp) }
}

private fun Modifier.clip44() = this.then(Modifier.background(Color.Transparent, RoundedCornerShape(22.dp)))

@Composable
fun HubField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, color = C.muted) },
        singleLine = singleLine,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = C.s2,
            unfocusedContainerColor = C.s2,
            focusedTextColor = C.text,
            unfocusedTextColor = C.text,
            cursorColor = C.accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Écran d'application : barre de titre avec retour + contenu. */
@Composable
fun AppScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn("back", "Retour", onClick = onBack)
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            actions()
        }
        content()
    }
}
