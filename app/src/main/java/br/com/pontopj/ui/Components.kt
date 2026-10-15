package br.com.pontopj.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.pontopj.data.Privacy
import br.com.pontopj.data.WorkSession
import br.com.pontopj.data.brl
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.timeOf

/** Container padrão de cada aba: rolagem vertical e espaçamento consistente. */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        EyeToggle()
    }
}

/** Botão do olho: aberto = valores visíveis; fechado = valores em R$ ocultos em todas as telas. */
@Composable
fun EyeToggle(modifier: Modifier = Modifier) {
    val hidden = Privacy.hidden
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Palette.SurfaceHigh)
            .border(1.dp, if (hidden) Palette.Gold else Palette.Outline, CircleShape)
            .clickable(
                onClickLabel = if (hidden) "Mostrar valores" else "Ocultar valores",
            ) { Privacy.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        EyeIcon(open = !hidden, color = if (hidden) Palette.Gold else Palette.Muted, modifier = Modifier.size(24.dp))
    }
}

/** Olho desenhado na mão (aberto ou fechado com risco). */
@Composable
fun EyeIcon(open: Boolean, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        val outline = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.06f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.02f, w * 0.94f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.98f, w * 0.06f, h * 0.5f)
            close()
        }
        drawPath(outline, color, style = stroke)
        if (open) {
            drawCircle(color, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
        } else {
            drawLine(color, Offset(w * 0.18f, h * 0.84f), Offset(w * 0.82f, h * 0.16f), strokeWidth = w * 0.095f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val brush = if (highlight) {
        Brush.linearGradient(listOf(Palette.GoldSoft, Palette.Surface))
    } else {
        Brush.linearGradient(listOf(Palette.Surface, Palette.Surface))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(brush, shape)
            .border(1.dp, if (highlight) Palette.Gold.copy(alpha = 0.45f) else Palette.Outline, shape)
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Palette.Text,
    )
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = Palette.Text,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .background(Palette.Surface, shape)
            .border(1.dp, Palette.Outline, shape)
            .padding(16.dp),
    ) {
        Text(label, color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TABULAR_NUMBERS,
            ),
        )
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, color = Palette.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
fun TileRow(content: @Composable RowScope.() -> Unit) {
    // Dois StatTile lado a lado: cada um usa Modifier.weight(1f) dentro do Row.
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
fun PulseDot(color: Color, pulsing: Boolean, size: Dp = 10.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val a by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        Modifier
            .size(size)
            .alpha(if (pulsing) a else 1f)
            .background(color, CircleShape),
    )
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 110.dp,
    stroke: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "ring",
    )
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(
                color = Palette.Outline,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = Palette.Gold,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Linha de um registro de ponto (entrada → saída, duração, valor). */
@Composable
fun SessionRow(session: WorkSession, now: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val end = session.endMillis?.let { timeOf(it) } ?: "agora"
            Text(
                "${timeOf(session.startMillis)} → $end",
                color = Palette.Text,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR_NUMBERS),
            )
            val place = session.inAddress
            if (place != null) {
                Text(
                    place,
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatHm(session.durationMillis(now)),
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
            )
            Text(
                brl(session.earnings(now)),
                color = Palette.Gold,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_NUMBERS),
            )
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Gold,
    unfocusedBorderColor = Palette.Outline,
    focusedLabelColor = Palette.Gold,
    unfocusedLabelColor = Palette.Muted,
    cursorColor = Palette.Gold,
    focusedTextColor = Palette.Text,
    unfocusedTextColor = Palette.Text,
)

/** Ícone de barras desenhado na mão (evita depender do pacote de ícones estendidos). */
@Composable
fun BarChartIcon(modifier: Modifier = Modifier.size(24.dp)) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.2f
        val gap = w * 0.1f
        val startX = (w - (barWidth * 3 + gap * 2)) / 2
        val heights = listOf(0.45f, 0.8f, 0.6f)
        heights.forEachIndexed { i, fraction ->
            val barHeight = h * 0.8f * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (barWidth + gap), h * 0.9f - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3),
            )
        }
    }
}
