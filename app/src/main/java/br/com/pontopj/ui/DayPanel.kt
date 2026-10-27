package br.com.pontopj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.pontopj.data.PtBR
import br.com.pontopj.data.WorkSession
import br.com.pontopj.data.brl
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.timeOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val panelDateFormat = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMM", PtBR)

/**
 * Painel analítico do dia selecionado no gráfico: ganho, comparação com a média do período,
 * números do dia e a composição do ganho por registro.
 *
 * @param average média de ganho dos dias trabalhados no período (0 se não houver).
 */
@Composable
fun DayPanel(
    date: LocalDate,
    earnings: Double,
    millis: Long,
    sessions: List<WorkSession>,
    average: Double,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val worked = sessions.isNotEmpty() && earnings > 0.0
    val diff: Double? = if (worked && average > 0.0) (earnings / average - 1.0) * 100 else null
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.SurfaceHigh, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DIA SELECIONADO", color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
                Text(
                    date.format(panelDateFormat).trimEnd('.').replaceFirstChar { it.uppercase() },
                    color = Palette.Text,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val badge = when {
                !worked -> "Sem registro"
                diff == null -> null
                diff >= 0 -> "Acima da média"
                else -> "Abaixo da média"
            }
            if (badge != null) {
                Text(
                    badge,
                    color = if (badge == "Abaixo da média" || badge == "Sem registro") Palette.Muted else Palette.Gold,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (badge == "Acima da média") Palette.Gold else Palette.Outline,
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Ganho do dia", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
                Text(
                    brl(earnings),
                    color = Palette.Gold,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
                )
            }
            if (diff != null) {
                val up = diff >= 0
                Column(horizontalAlignment = Alignment.End) {
                    Text("Comparativo", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${if (up) "▲ +" else "▼ -"}${abs(diff).roundToInt()}% vs média",
                        color = if (up) Palette.Gold else Palette.Muted,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (worked) {
            Spacer(Modifier.height(12.dp))
            val biggest = sessions.maxOf { it.earnings(now) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Registros", "${sessions.size}", Modifier.weight(1f))
                MiniStat("Horas", formatHm(millis), Modifier.weight(1f))
                MiniStat("Maior registro", brl(biggest), Modifier.weight(1f))
            }

            if (sessions.size > 1) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "COMPOSIÇÃO DO GANHO",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(6.dp))
                sessions.sortedBy { it.startMillis }.forEach { s ->
                    val value = s.earnings(now)
                    val fraction = if (earnings > 0.0) (value / earnings).toFloat().coerceIn(0f, 1f) else 0f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${timeOf(s.startMillis)} → ${s.endMillis?.let { timeOf(it) } ?: "agora"}  ·  ${formatHm(s.durationMillis(now))}",
                            color = Palette.Text,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            brl(value),
                            color = Palette.Gold,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Palette.Outline, RoundedCornerShape(50)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(5.dp)
                                .background(Palette.Gold, RoundedCornerShape(50)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Palette.Surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = Palette.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(
            value,
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}
