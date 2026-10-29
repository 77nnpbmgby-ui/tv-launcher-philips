package com.example.tvlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherScreen() }
    }
}

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap)

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    return pm.queryIntentActivities(q, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.loadIcon(pm).toBitmap(192, 192).asImageBitmap()
            )
        }
        .sortedBy { it.label }
}

fun openApp(ctx: Context, pkg: String, uri: String? = null) {
    val i = if (uri != null) Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg)
    else ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg)
        ?: ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (i != null) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) {}
    }
}

suspend fun fetchTemp(): String? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${Config.LAT}&longitude=${Config.LON}&current=temperature_2m")
        val t = JSONObject(url.readText()).getJSONObject("current").getDouble("temperature_2m")
        "${t.roundToInt()}°"
    } catch (e: Exception) { null }
}

@Composable
fun Wallpaper(drift: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF2A2A66),
                0.30f + drift * 0.06f to Color(0xFF7B4E92),
                0.60f to Color(0xFFE0758F),
                0.80f to Color(0xFFEFA08C),
                1f to Color(0xFF241A2E)
            )
        )
        val far = Path().apply {
            moveTo(0f, h * .74f); lineTo(w * .18f, h * .62f); lineTo(w * .34f, h * .69f)
            lineTo(w * .55f, h * .60f); lineTo(w * .80f, h * .70f); lineTo(w, h * .64f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(far, Color(0xFF8A5A9E).copy(alpha = .55f))
        val peak = Path().apply {
            moveTo(w * .05f, h * .80f); lineTo(w * .18f, h * .64f); lineTo(w * .27f, h * .52f)
            lineTo(w * .31f, h * .46f); lineTo(w * .34f, h * .50f); lineTo(w * .38f, h * .55f)
            lineTo(w * .47f, h * .64f); lineTo(w * .62f, h * .74f); lineTo(w * .62f, h)
            lineTo(w * .05f, h); close()
        }
        drawPath(peak, Color(0xFF2B2338))
        val ground = Path().apply {
            moveTo(0f, h * .80f)
            quadraticBezierTo(w * .35f, h * .72f, w * .70f, h * .80f)
            quadraticBezierTo(w * .90f, h * .84f, w, h * .80f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(ground, Color(0xFF16121F))
        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000)))
    }
}

@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    onClick: () -> Unit,
    onFocus: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.08f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 300f), label = "scale"
    )
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "glow")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Color.Black.copy(alpha = 0.35f), shape)
            .background(Color.White.copy(alpha = 0.12f * glow), shape)
            .border(2.dp, Color.White.copy(alpha = glow), shape)
            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun ShortcutCard(s: Shortcut, onClick: () -> Unit) {
    FocusCard(Modifier.size(width = 300.dp, height = 96.dp), onClick = onClick) {
        Row(
            Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(s.color), Color(s.color).copy(alpha = .45f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(s.service.take(1), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(s.service, color = Color.White.copy(alpha = .7f), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(s.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AppTile(a: AppItem, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val label by animateFloatAsState(if (f) 1f else 0f, tween(200), label = "label")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(Modifier.size(88.dp), RoundedCornerShape(20.dp), onClick, onFocus = { f = it }) {
            Image(
                a.icon, a.label,
                Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(14.dp))
            )
        }
        Text(
            a.label, color = Color.White, fontSize = 13.sp, maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(100.dp).padding(top = 8.dp).alpha(label)
        )
    }
}

@Composable
fun LauncherScreen() {
    val ctx = LocalContext.current
    val apps = remember { loadApps(ctx) }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(Unit) {
        val sv = Locale("sv")
        val tf = SimpleDateFormat("H:mm", sv)
        val df = SimpleDateFormat("EEEE d MMMM", sv)
        while (true) {
            val now = Date()
            val t = tf.format(now)
            if (t != time) {
                time = t
                date = df.format(now).replaceFirstChar { it.uppercase() }
            }
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) { while (true) { temp = fetchTemp(); delay(30 * 60 * 1000L) } }
    BackHandler {}

    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(900), label = "appear")
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "driftValue"
    )

    Box(Modifier.fillMaxSize()) {
        Wallpaper(drift)
        Column(
            Modifier.fillMaxSize().alpha(appear)
                .padding(start = 64.dp, end = 64.dp, top = 44.dp, bottom = 28.dp)
        ) {
            Text(
                date + (temp?.let { "  ·  $it" } ?: ""),
                color = Color.White.copy(alpha = .85f), fontSize = 22.sp
            )
            Text(
                time,
                style = TextStyle(
                    fontSize = 160.sp, fontWeight = FontWeight.Thin, lineHeight = 170.sp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = .95f), Color(0xFFFFC4D6).copy(alpha = .75f))
                    )
                )
            )
            Spacer(Modifier.weight(1f))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
            ) {
                items(Config.shortcuts) { s ->
                    ShortcutCard(s) { openApp(ctx, s.packageName, s.uri) }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
            ) {
                items(apps) { a -> AppTile(a) { openApp(ctx, a.pkg) } }
            }
        }
    }
}
