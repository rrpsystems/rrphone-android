package com.rrpsystems.rrphone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Sempre escuro e sem cor dinâmica: o app deve ter a cara do desktop em
// qualquer aparelho, não a do papel de parede de quem o usa.
private val RrpColorScheme = darkColorScheme(
    primary = Rrp.AccentBlue,
    onPrimary = Rrp.TextPrimary,
    secondary = Rrp.AccentTeal,
    onSecondary = Rrp.TextPrimary,
    tertiary = Rrp.Green,
    background = Rrp.Background,
    onBackground = Rrp.TextPrimary,
    surface = Rrp.Panel,
    onSurface = Rrp.TextPrimary,
    surfaceVariant = Rrp.Button,
    onSurfaceVariant = Rrp.TextSecondary,
    surfaceContainer = Rrp.Panel,
    surfaceContainerHigh = Rrp.Button,
    surfaceContainerHighest = Rrp.Button,
    outline = Rrp.Border,
    error = Rrp.Red,
)

@Composable
fun RRPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RrpColorScheme,
        typography = Typography,
        content = content
    )
}
