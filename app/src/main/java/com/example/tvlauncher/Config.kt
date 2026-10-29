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
