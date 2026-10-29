package com.example.tvlauncher

data class Shortcut(
    val title: String,
    val service: String,
    val packageName: String,
    val color: Long,
    val uri: String? = null // valfri djuplänk
)

object Config {
    // Din plats för vädret (Stockholm som standard)
    const val LAT = 59.33
    const val LON = 18.07

    // Dina egna genvägskort. Ändra, lägg till och ta bort fritt.
    val shortcuts = listOf(
        Shortcut("Öppna Netflix", "NETFLIX", "com.netflix.ninja", 0xFFE50914),
        Shortcut("Öppna YouTube", "YOUTUBE", "com.google.android.youtube.tv", 0xFFFF0000),
        Shortcut("Öppna Disney+", "DISNEY+", "com.disney.disneyplus", 0xFF113CCF),
    )
}
