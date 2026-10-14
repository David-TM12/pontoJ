package br.com.pontopj.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paleta Obsidian Gold Analytics (veja docs/DESIGN.md). */
object Palette {
    val Black = Color(0xFF0D0D11)
    val Surface = Color(0xFF17171C)
    val SurfaceHigh = Color(0xFF1F1F25)
    val Outline = Color(0xFF2C2C34)
    val Gold = Color(0xFFF5BA1E)
    val GoldDeep = Color(0xFFE5A910)
    val GoldLight = Color(0xFFFFC837)
    val GoldSoft = Color(0xFF3A2F0A)
    val Text = Color(0xFFF4F1E8)
    val Muted = Color(0xFF9C9684)
    val Danger = Color(0xFFE5533D)
}

/** Números com largura fixa, para o cronômetro não ficar "pulando". */
const val TABULAR_NUMBERS = "tnum"

private val DarkScheme = darkColorScheme(
    primary = Palette.Gold,
    onPrimary = Palette.Black,
    primaryContainer = Palette.GoldSoft,
    onPrimaryContainer = Palette.Gold,
    secondary = Palette.GoldDeep,
    onSecondary = Palette.Black,
    background = Palette.Black,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Danger,
    onError = Palette.Black,
)

@Composable
fun PontoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
