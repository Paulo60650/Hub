package com.hub.app

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* ───────────────────────── Galerie ───────────────────────── */

fun loadImages(ctx: Context): List<Uri> {
    val out = mutableListOf<Uri>()
    ctx.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID),
        null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 600) {
            out.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)))
        }
    }
    return out
}

@Suppress("DEPRECATION")
fun loadThumb(ctx: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(uri, Size(320, 320), null)
    else MediaStore.Images.Thumbnails.getThumbnail(
        ctx.contentResolver, ContentUris.parseId(uri), MediaStore.Images.Thumbnails.MINI_KIND, null,
    )
} catch (e: Exception) { null }

fun decodeSampled(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
} catch (e: Exception) { null }

@Composable
fun AsyncBitmap(uri: Uri, maxSide: Int?, modifier: Modifier, scale: ContentScale) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri, maxSide) {
        value = withContext(Dispatchers.IO) {
            if (maxSide == null) loadThumb(ctx, uri) else decodeSampled(ctx, uri, maxSide)
        }
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, modifier, contentScale = scale)
    else Box(modifier.background(C.s2))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryApp(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var viewing by remember { mutableStateOf<Int?>(null) }
    BackHandler(viewing != null) { viewing = null }
    AppScreen("Galerie", onBack = { if (viewing != null) viewing = null else onBack() }) {
        PermissionGate(listOf(imagesPerm()), "Autoriser la galerie", "Pour afficher les photos de votre téléphone.") {
            val images by rememberAsync(initial = emptyList<Uri>()) { loadImages(ctx) }
            val v = viewing
            if (v == null) {
                if (images.isEmpty()) Empty("Aucune photo.")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(images.size) { i ->
                        AsyncBitmap(
                            images[i], null,
                            Modifier.aspectRatio(1f).clickable { viewing = i }, ContentScale.Crop,
                        )
                    }
                }
            } else {
                val pager = rememberPagerState(initialPage = v.coerceAtMost(images.size - 1)) { images.size }
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { p ->
                        AsyncBitmap(images[p], 1600, Modifier.fillMaxSize(), ContentScale.Fit)
                    }
                    PrimaryButton("Partager", Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        shareUri(ctx, images[pager.currentPage], "image/*")
                    }
                }
            }
        }
    }
}
