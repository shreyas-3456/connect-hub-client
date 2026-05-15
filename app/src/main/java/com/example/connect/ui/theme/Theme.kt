// ui/theme/Theme.kt
package com.example.connect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


// --- Your brand colors ---
val ElectricCyan = Color(0xFF00E5FF)   // connected / active
val Charcoal900  = Color(0xFF0D0D0D)  // deepest background
val Charcoal800  = Color(0xFF1A1A1A)  // surface / card background
val Charcoal700  = Color(0xFF2A2A2A)  // elevated surface
val Charcoal600  = Color(0xFF3A3A3A)  // borders
val AmberWarning = Color(0xFFFFB300)  // warnings
val RedError     = Color(0xFFFF3D00)  // errors
val TextPrimary  = Color(0xFFEEEEEE)  // main text on dark
val TextSecondary = Color(0xFF9E9E9E) // muted text

// Material 3 uses a "color scheme" object — we map our brand
// colors onto its named slots so every Compose component
// automatically picks up the right color.
private val ConnectDarkColorScheme = darkColorScheme(
    primary          = ElectricCyan,
    onPrimary        = Charcoal900,   // text ON a cyan button
    background       = Charcoal900,
    onBackground     = TextPrimary,
    surface          = Charcoal800,
    onSurface        = TextPrimary,
    surfaceVariant   = Charcoal700,
    onSurfaceVariant = TextSecondary,
    outline          = Charcoal600,
    error            = RedError,
    onError          = TextPrimary,
)

@Composable
fun ConnectTheme(content: @Composable () -> Unit) {
    // We only define a dark scheme — the app is dark-first.
    MaterialTheme(
        colorScheme = ConnectDarkColorScheme,
        content     = content
    )
}