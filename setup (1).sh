#!/bin/bash
# Kör i roten av ditt tv-launcher-repo: bash setup.sh
set -e
P=app/src/main/java/com/example/tvlauncher
mkdir -p $P .github/workflows

cat > .gitignore <<'EOF'
.gradle/
build/
local.properties
.idea/
EOF

cat > settings.gradle.kts <<'EOF'
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "tv-launcher"
include(":app")
EOF

cat > build.gradle.kts <<'EOF'
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
EOF

cat > gradle.properties <<'EOF'
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2g
EOF

cat > app/build.gradle.kts <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.tvlauncher"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.tvlauncher"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
}
EOF

cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent>
    </queries>

    <application
        android:label="TV Launcher"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > $P/Config.kt <<'EOF'
package com.example.tvlauncher

data class Shortcut(
    val title: String,
    val packageName: String,
    val uri: String? = null // valfri djuplänk, t.ex. en YouTube-video
)

object Config {
    // Din plats för vädret (Stockholm som standard)
    const val LAT = 59.33
    const val LON = 18.07

    // Dina egna genvägskort. Ändra, lägg till och ta bort fritt.
    val shortcuts = listOf(
        Shortcut("Netflix", "com.netflix.ninja"),
        Shortcut("YouTube", "com.google.android.youtube.tv"),
        Shortcut("Disney+", "com.disney.disneyplus"),
    )
}
EOF

cat > $P/MainActivity.kt <<'EOF'
package com.example.tvlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                it.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
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
fun FocusCard(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .scale(if (focused) 1.08f else 1f)
            .background(Color(0x66000000), shape)
            .border(3.dp, if (focused) Color.White else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun LauncherScreen() {
    val ctx = LocalContext.current
    val apps = remember { loadApps(ctx) }
    var now by remember { mutableStateOf(Date()) }
    var temp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { while (true) { now = Date(); delay(5_000) } }
    LaunchedEffect(Unit) { while (true) { temp = fetchTemp(); delay(30 * 60 * 1000L) } }
    BackHandler {} // hemskärmen ska inte stängas med Back

    val sv = Locale("sv")
    val date = SimpleDateFormat("EEEE d MMMM", sv).format(now).replaceFirstChar { it.uppercase() }
    val time = SimpleDateFormat("HH:mm", sv).format(now)

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF3B2A5C), Color(0xFFD9707F), Color(0xFF14141C)))
        )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            Text(
                text = date + (temp?.let { "  ·  $it" } ?: ""),
                color = Color.White, fontSize = 22.sp
            )
            Text(text = time, color = Color.White, fontSize = 140.sp, fontWeight = FontWeight.Thin)
            Spacer(Modifier.weight(1f))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(Config.shortcuts) { s ->
                    FocusCard(Modifier.size(width = 260.dp, height = 100.dp), onClick = { openApp(ctx, s.packageName, s.uri) }) {
                        Text(s.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(apps) { a ->
                    FocusCard(Modifier.size(96.dp), onClick = { openApp(ctx, a.pkg) }) {
                        Image(a.icon, a.label, Modifier.size(64.dp))
                    }
                }
            }
        }
    }
}
EOF

cat > .github/workflows/build.yml <<'EOF'
name: Build APK
on:
  push:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.9"
      - run: gradle assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: tv-launcher-apk
          path: app/build/outputs/apk/debug/app-debug.apk
EOF

echo "Klart! Kör nu: git add . && git commit -m 'Första versionen' && git push"
