package br.com.pontopj.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.pontopj.AppViewModel
import br.com.pontopj.data.WorkSession
import br.com.pontopj.data.agoLabel
import br.com.pontopj.data.brl
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.dayLabel
import br.com.pontopj.data.formatClock
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.greeting
import br.com.pontopj.data.onDate
import br.com.pontopj.data.timeOf
import br.com.pontopj.data.totalEarnings
import br.com.pontopj.data.totalMillis
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    vm: AppViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val active = vm.active
    val now by produceState(System.currentTimeMillis(), active != null) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (active != null) 1_000L else 20_000L)
        }
    }
    val haptic = LocalHapticFeedback.current

    var showManual by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkSession?>(null) }

    val today = dateOf(now)
    val todaySessions = vm.sessions.onDate(today).sortedBy { it.startMillis }
    val todayMillis = todaySessions.totalMillis(now)
    val todayEarnings = todaySessions.totalEarnings(now)
    val goalMillis = (vm.dailyGoalHours * 3_600_000).toLong()
    val dayProgress = if (goalMillis > 0) todayMillis.toFloat() / goalMillis else 0f

    ScreenColumn {
        ScreenTitle(title = greeting(now), subtitle = dayLabel(today))

        if (vm.hourlyRate <= 0.0) {
            AppCard {
                Text("Defina o valor da sua hora", fontWeight = FontWeight.Bold, color = Palette.Text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sem o valor da hora não dá para calcular seus ganhos.",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenSettings) { Text("Ir para Ajustes", color = Palette.Gold) }
            }
        }

        // ---- Cartão principal: cronômetro, ganhos e botão de ponto ----
        AppCard(highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(if (active != null) Palette.Gold else Palette.Muted, pulsing = active != null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active != null) "EM EXPEDIENTE" else "FORA DO EXPEDIENTE",
                    color = if (active != null) Palette.Gold else Palette.Muted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (active != null) "Sessão atual" else "Trabalhado hoje",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                formatClock(if (active != null) active.durationMillis(now) else todayMillis),
                color = Palette.Text,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_NUMBERS,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Text("Ganhos de hoje", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
            Text(
                brl(todayEarnings),
                color = Palette.Gold,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_NUMBERS,
                ),
            )
            if (active != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Entrada às ${timeOf(active.startMillis)}  ·  ${brl(active.rate)}/h",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (active != null) vm.clockOut() else vm.clockIn()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active != null) Palette.Danger else Palette.Gold,
                    contentColor = Palette.OnGold,
                ),
            ) {
                Text(
                    if (active != null) "Registrar saída" else "Registrar entrada",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // ---- Meta do dia ----
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(progress = dayProgress, ringSize = 96.dp, stroke = 10.dp) {
                    Text(
                        "${(dayProgress * 100).roundToInt().coerceAtMost(999)}%",
                        color = Palette.Text,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.width(20.dp))
                androidx.compose.foundation.layout.Column {
                    Text("Meta do dia", color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${formatHm(todayMillis)} de ${formatGoalHours(vm.dailyGoalHours)}",
                        color = Palette.Text,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val remaining = (goalMillis - todayMillis).coerceAtLeast(0L)
                    Text(
                        if (remaining > 0) "Faltam ${formatHm(remaining)}" else "Meta batida 🎯",
                        color = if (remaining > 0) Palette.Muted else Palette.Gold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        LocationCard(vm, now, hasPermission, onRequestPermission, active)

        // ---- Registros de hoje ----
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Registros de hoje", Modifier.weight(1f))
                TextButton(onClick = { showManual = true }) { Text("+ Manual", color = Palette.Gold) }
            }
            if (todaySessions.isEmpty()) {
                Text(
                    "Nenhum registro hoje. Toque em “Registrar entrada” para começar.",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                todaySessions.forEach { s -> SessionRow(s, now) { editing = s } }
            }
        }
    }

    if (showManual) {
        SessionDialog(
            initial = null,
            defaultRate = vm.hourlyRate,
            onDismiss = { showManual = false },
            onSave = {
                vm.saveSession(it)
                showManual = false
            },
            onDelete = null,
        )
    }
    editing?.let { s ->
        SessionDialog(
            initial = s,
            defaultRate = vm.hourlyRate,
            onDismiss = { editing = null },
            onSave = {
                vm.saveSession(it)
                editing = null
            },
            onDelete = {
                vm.deleteSession(s.id)
                editing = null
            },
        )
    }
}

private fun formatGoalHours(hours: Double): String {
    val whole = hours.toInt()
    val minutes = ((hours - whole) * 60).roundToInt()
    return if (minutes == 0) "${whole}h" else "${whole}h ${minutes}min"
}

@Composable
private fun LocationCard(
    vm: AppViewModel,
    now: Long,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    active: WorkSession?,
) {
    val context = LocalContext.current
    val fix = vm.fix
    val ageSeconds = if (fix != null) ((now - fix.timeMillis) / 1000).coerceAtLeast(0L) else 0L

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Localização em tempo real",
                color = Palette.Text,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (hasPermission && fix != null) {
                val live = ageSeconds < 90
                PulseDot(if (live) Palette.Gold else Palette.Muted, pulsing = live, size = 8.dp)
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            !hasPermission -> {
                Text(
                    "Permita o acesso à localização para registrar onde você bate o ponto.",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRequestPermission, shape = RoundedCornerShape(14.dp)) {
                    Text("Permitir localização", color = Palette.Gold)
                }
            }

            fix == null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Palette.Gold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Buscando sinal de GPS…",
                        color = Palette.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                Text(
                    vm.address ?: "Resolvendo endereço…",
                    color = Palette.Text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    String.format(Locale.US, "%.5f, %.5f", fix.lat, fix.lng),
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
                )
                Text(
                    "Precisão ±${fix.accuracyM.roundToInt()} m  ·  atualizado há ${agoLabel(ageSeconds)}",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                val entryPlace = active?.inAddress
                if (entryPlace != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Entrada registrada em: $entryPlace",
                        color = Palette.Gold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        val uri = Uri.parse("geo:${fix.lat},${fix.lng}?q=${fix.lat},${fix.lng}")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: ActivityNotFoundException) {
                            // sem app de mapas instalado
                        }
                    },
                ) { Text("Abrir no mapa", color = Palette.Gold) }
            }
        }
    }
}
