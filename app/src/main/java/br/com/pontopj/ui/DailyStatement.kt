package br.com.pontopj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.pontopj.data.DaySummary
import br.com.pontopj.data.PtBR
import br.com.pontopj.data.brl
import br.com.pontopj.data.formatHm
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val rowDateFormat = DateTimeFormatter.ofPattern("EEEE, dd MMM", PtBR)

private fun String.capFirst(): String = replaceFirstChar { it.uppercase() }

/**
 * Extrato diário recente: os últimos dias trabalhados, com ganho do dia e a variação
 * em relação ao dia trabalhado anterior.
 *
 * @param days todos os dias com registro, do mais recente para o mais antigo.
 */
@Composable
fun DailyStatementCard(
    days: List<DaySummary>,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    limit: Int = 4,
) {
    AppCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatementIcon(Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Extrato diário recente",
                modifier = Modifier.weight(1f),
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Ver tudo",
                color = Palette.Gold,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(onClick = onSeeAll)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))

        if (days.isEmpty()) {
            Text(
                "Quando você registrar o ponto, os últimos dias aparecem aqui.",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            return@AppCard
        }

        val shown = days.take(limit)
        shown.forEachIndexed { index, day ->
            val previous = days.getOrNull(index + 1)
            val change = if (previous != null && previous.earnings > 0.0) {
                (day.earnings - previous.earnings) / previous.earnings * 100.0
            } else {
                null
            }
            StatementRow(day, change)
            if (index < shown.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Palette.Outline),
                )
            }
        }
    }
}

@Composable
private fun StatementRow(day: DaySummary, change: Double?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selo da data: dia da semana abreviado + número.
        Column(
            modifier = Modifier
                .size(width = 46.dp, height = 50.dp)
                .background(Palette.SurfaceHigh, RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, PtBR).trimEnd('.').uppercase(),
                color = Palette.Muted,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                day.date.dayOfMonth.toString(),
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                day.date.format(rowDateFormat).trimEnd('.').capFirst(),
                color = Palette.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            val count = day.sessions.size
            Text(
                "${if (count == 1) "1 registro" else "$count registros"} · ${formatHm(day.millis)}",
                color = Palette.Muted,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                brl(day.earnings),
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
            )
            if (change != null) {
                val positive = change >= 0.0
                Text(
                    String.format(PtBR, "%s%.1f%%", if (positive) "+" else "-", kotlin.math.abs(change)),
                    color = if (positive) Palette.Gold else Palette.Muted,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Ícone de extrato (cartão com listras), desenhado na mão. */
@Composable
private fun StatementIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        val color = Palette.Gold
        drawRoundRect(
            color = color,
            topLeft = Offset(sw / 2, h * 0.12f),
            size = Size(w - sw, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.14f),
            style = Stroke(width = sw),
        )
        drawLine(color, Offset(w * 0.2f, h * 0.4f), Offset(w * 0.8f, h * 0.4f), strokeWidth = sw)
        drawLine(color, Offset(w * 0.2f, h * 0.6f), Offset(w * 0.55f, h * 0.6f), strokeWidth = sw)
    }
}
