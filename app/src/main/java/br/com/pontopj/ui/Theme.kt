package br.com.pontopj.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    System("Sistema"),
    Dark("Escuro"),
    Light("Claro"),
}

class AccentOption(val name: String, val color: Color)

/** Cores de destaque que o usuário pode escolher. O dourado é o padrão (Obsidian Gold Analytics). */
val Accents = listOf(
    AccentOption("Dourado", Color(0xFFF5BA1E)),
    AccentOption("Azul", Color(0xFF4DA3FF)),
    AccentOption("Verde", Color(0xFF2FCB8A)),
    AccentOption("Roxo", Color(0xFFA78BFA)),
    AccentOption("Coral", Color(0xFFFF7A6B)),
)

/** Tema escolhido (modo claro/escuro e cor de destaque). Mudar aqui redesenha o app inteiro. */
object ThemeState {
    var mode by mutableStateOf(ThemeMode.System)
    var accentIndex by mutableIntStateOf(0)
    var systemDark by mutableStateOf(true)

    val dark: Boolean
        get() = when (mode) {
            ThemeMode.System -> systemDark
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
        }
}

private fun darken(c: Color, amount: Float) = lerp(c, Color.Black, amount)
private fun lighten(c: Color, amount: Float) = lerp(c, Color.White, amount)

/**
 * Paleta do app (veja docs/DESIGN.md). Os nomes "Gold*" significam "cor de destaque": no tema padrão
 * são dourados e mudam conforme o destaque escolhido. Os valores acompanham o tema claro/escuro.
 */
object Palette {
    private val dark: Boolean get() = ThemeState.dark
    private val accent: Color get() = Accents[ThemeState.accentIndex.coerceIn(Accents.indices)].color

    val Black: Color get() = if (dark) Color(0xFF0D0D11) else Color(0xFFF5F3EC)
    val Surface: Color get() = if (dark) Color(0xFF17171C) else Color(0xFFFFFFFF)
    val SurfaceHigh: Color get() = if (dark) Color(0xFF1F1F25) else Color(0xFFEDEAE0)
    val Outline: Color get() = if (dark) Color(0xFF2C2C34) else Color(0xFFDAD5C5)

    val Gold: Color get() = if (dark) accent else darken(accent, 0.22f)
    val GoldDeep: Color get() = if (dark) darken(accent, 0.08f) else darken(accent, 0.34f)
    val GoldLight: Color get() = if (dark) lighten(accent, 0.25f) else darken(accent, 0.12f)
    val GoldSoft: Color get() = if (dark) lerp(Color(0xFF17171C), accent, 0.2f) else lerp(Color.White, accent, 0.25f)

    /** Texto/ícone sobre fundo de destaque (botões, barra selecionada). */
    val OnGold: Color get() = Color(0xFF17171C)

    val Text: Color get() = if (dark) Color(0xFFF4F1E8) else Color(0xFF1B1B1F)
    val Muted: Color get() = if (dark) Color(0xFF9C9684) else Color(0xFF6B675A)
    val Danger: Color get() = if (dark) Color(0xFFE5533D) else Color(0xFFC63A25)
}

/** Números com largura fixa, para o cronômetro não ficar "pulando". */
const val TABULAR_NUMBERS = "tnum"

@Composable
fun PontoTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    SideEffect { ThemeState.systemDark = systemDark }

    val dark = ThemeState.dark
    val scheme = if (dark) {
        darkColorScheme(
            primary = Palette.Gold,
            onPrimary = Palette.OnGold,
            primaryContainer = Palette.GoldSoft,
            onPrimaryContainer = Palette.Gold,
            secondary = Palette.GoldDeep,
            onSecondary = Palette.OnGold,
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
            onError = Palette.OnGold,
        )
    } else {
        lightColorScheme(
            primary = Palette.Gold,
            onPrimary = Palette.OnGold,
            primaryContainer = Palette.GoldSoft,
            onPrimaryContainer = Palette.Gold,
            secondary = Palette.GoldDeep,
            onSecondary = Palette.OnGold,
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
            onError = Color.White,
        )
    }

    // Ícones da barra de status e de navegação legíveis nos dois temas.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
