package br.com.pontopj.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private val Bg = Color(0xFF0D0D11)
private val TextColor = Color(0xFFF4F1E8)
private val Muted = Color(0xFF9C9684)
private val Track = Color(0xFF2C2C34)
private val OnAccent = Color(0xFF17171C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StateRepo.load(this)
        setContent { WatchApp() }
    }

    override fun onResume() {
        super.onResume()
        PhoneLink.refresh(this)
    }
}

private fun formatClock(millis: Long): String {
    val total = (millis / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

private fun formatBrl(value: Double, hidden: Boolean): String =
    if (hidden) "R$ ••••" else NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)

/** Cinco páginas (deslize para os lados): Ponto, Hoje, Semana, Mês e Ajustes. */
@Composable
fun WatchApp() {
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ClockPage()
                1 -> TodayPage()
                2 -> WeekPage()
                3 -> SummaryPage()
                else -> SettingsPage()
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        ) {
            repeat(PAGE_COUNT) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == i) TextColor else Track),
                )
            }
        }
    }
}


private const val PAGE_COUNT = 5

/** Hora atual que anda sozinha a cada segundo. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    return now
}

private fun clockLabel(epochMs: Long): String =
    java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(epochMs))

private fun formatHm(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000L
    return String.format(Locale.US, "%dh %02dmin", minutes / 60, minutes % 60)
}

/** Hoje: previsão de saída, quanto falta para a meta e os períodos do dia. */
@Composable
private fun TodayPage() {
    val state by StateRepo.state.collectAsState()
    val now = rememberNow()
    val st = state
    val accent = Color(st?.accent ?: StateRepo.DEFAULT_ACCENT)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (st == null) {
            Label("Sincronize com o celular", Muted)
            return@Box
        }
        val goalMs = (st.goalHours * 3_600_000.0).toLong()
        val dayMs = st.dayMillis(now)
        val missing = goalMs - dayMs
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Label("HOJE", accent, bold = true)
            Spacer(Modifier.height(2.dp))
            when {
                goalMs <= 0L -> BigText(formatHm(dayMs), TextColor)
                missing <= 0L -> {
                    BigText("Meta batida", accent)
                    Label("+${formatHm(-missing)} extras", Muted)
                }
                st.active -> {
                    Label("Saída prevista", Muted)
                    BigText(clockLabel(now + missing), accent)
                    Label("faltam ${formatHm(missing)}", Muted)
                }
                else -> {
                    Label("Faltam para a meta", Muted)
                    BigText(formatHm(missing), TextColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            val spans = st.todaySpans.chunked(2).filter { it.size == 2 }.takeLast(3)
            spans.forEach { span ->
                Label("${clockLabel(span[0])} → ${clockLabel(span[1])}", TextColor)
            }
            if (st.active) {
                Label("${clockLabel(st.openStart)} → agora", accent)
            }
            if (spans.isEmpty() && !st.active) Label("Nenhum registro hoje", Muted)
            Spacer(Modifier.height(4.dp))
            Label("Total: ${formatHm(dayMs)}", Muted)
        }
    }
}

@Composable
private fun BigText(text: String, color: Color) {
    BasicText(
        text,
        style = TextStyle(
            color = color,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontFeatureSettings = "tnum",
        ),
    )
}

/** Semana: horas de segunda a domingo, em barras. */
@Composable
private fun WeekPage() {
    val state by StateRepo.state.collectAsState()
    val now = rememberNow()
    val st = state
    val accent = Color(st?.accent ?: StateRepo.DEFAULT_ACCENT)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (st == null) {
            Label("Sincronize com o celular", Muted)
            return@Box
        }
        val week = st.weekLive(now)
        val total = week.sum()
        val worked = week.count { it > 0L }
        val letters = listOf("S", "T", "Q", "Q", "S", "S", "D")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Label("ESTA SEMANA", accent, bold = true)
            BigText(formatHm(total), TextColor)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.width(120.dp).height(46.dp)) {
                val maxValue = (week.maxOrNull() ?: 0L).coerceAtLeast(1L)
                val slot = size.width / 7f
                val barWidth = slot * 0.58f
                week.forEachIndexed { i, value ->
                    val h = (value.toFloat() / maxValue * size.height).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = when {
                            i == st.weekToday -> accent
                            value > 0L -> accent.copy(alpha = 0.5f)
                            else -> Track
                        },
                        topLeft = Offset(slot * i + (slot - barWidth) / 2f, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
            Row(Modifier.width(120.dp)) {
                letters.forEachIndexed { i, l ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BasicText(
                            l,
                            style = TextStyle(color = if (i == st.weekToday) accent else Muted, fontSize = 9.sp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (worked > 0) Label("Média: ${formatHm(total / worked)} por dia", Muted)
            val goalWeekMs = (st.goalHours * 3_600_000.0).toLong() * 5
            if (goalWeekMs > 0L) {
                Label(
                    String.format(Locale.US, "%.0f%% de %s", (total.toFloat() / goalWeekMs * 100f).coerceAtMost(999f), formatHm(goalWeekMs)),
                    Muted,
                )
            }
        }
    }
}

/** Ajustes rápidos: ocultar valores e sincronizar. */
@Composable
private fun SettingsPage() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by StateRepo.state.collectAsState()
    val st = state
    val accent = Color(st?.accent ?: StateRepo.DEFAULT_ACCENT)
    var message by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Label("AJUSTES", accent, bold = true)
            Spacer(Modifier.height(8.dp))
            if (st != null) {
                PillButton(if (st.hide) "Mostrar valores" else "Ocultar valores", accent, enabled = true) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    message = null
                    StateRepo.optimistic(context) { it.copy(hide = !it.hide) }
                    PhoneLink.send(context, WearProtocol.PATH_TOGGLE_HIDE) { delivered ->
                        if (!delivered) message = "Sem conexão com o celular"
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            PillButton("Sincronizar", accent, enabled = true) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                PhoneLink.refresh(context)
                message = "Pedido enviado ao celular"
            }
            Spacer(Modifier.height(6.dp))
            val updated = st?.updatedAt ?: 0L
            Label(
                message ?: if (updated > 0L) "Atualizado às ${clockLabel(updated)}" else "Ainda sem dados",
                if (message == "Sem conexão com o celular") Color(0xFFE5533D) else Muted,
            )
        }
    }
}

private fun formatSignedHm(millis: Long): String {
    val minutes = kotlin.math.abs(millis) / 60_000L
    return String.format(Locale.US, "%s%dh %02dmin", if (millis < 0) "-" else "+", minutes / 60, minutes % 60)
}

@Composable
private fun SummaryPage() {
    val state by StateRepo.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val st = state
    val accent = Color(st?.accent ?: StateRepo.DEFAULT_ACCENT)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (st == null) {
            Label("Sincronize com o celular", Muted)
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Label("RESUMO DO MÊS", accent, bold = true)
            val monthEarnings = st.monthEarnings(now)
            BasicText(
                formatBrl(monthEarnings, st.hide),
                style = TextStyle(color = TextColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            )
            if (st.monthGoal > 0.0) {
                val fraction = (monthEarnings / st.monthGoal).coerceIn(0.0, 1.0).toFloat()
                Spacer(Modifier.height(3.dp))
                Canvas(Modifier.width(110.dp).height(5.dp)) {
                    drawRoundRect(Track, cornerRadius = CornerRadius(size.height / 2f))
                    if (fraction > 0f) {
                        drawRoundRect(
                            accent,
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                }
                Label(String.format(Locale.US, "%.0f%% da meta mensal", fraction * 100f), Muted)
            } else {
                Label("Defina a meta no celular", Muted)
            }
            val missingMonth = st.monthGoal - monthEarnings
            if (!st.hide && missingMonth > 0.0 && st.remainingDays > 0) {
                Label("Ritmo: ${formatBrl(missingMonth / st.remainingDays, false)} por dia útil", Muted)
            }
            Spacer(Modifier.height(8.dp))
            val bars = st.last7Live(now)
            Canvas(Modifier.width(112.dp).height(34.dp)) {
                val maxValue = (bars.maxOrNull() ?: 0f).coerceAtLeast(0.01f)
                val slot = size.width / bars.size
                val barWidth = slot * 0.56f
                bars.forEachIndexed { i, value ->
                    val h = (value / maxValue * size.height).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = if (i == bars.lastIndex) accent else accent.copy(alpha = 0.45f),
                        topLeft = Offset(slot * i + (slot - barWidth) / 2f, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
            Label("últimos 7 dias", Muted)
            Spacer(Modifier.height(6.dp))
            Label("Saldo de horas: ${formatSignedHm(st.balance(now))}", if (st.balance(now) >= 0) TextColor else Color(0xFFE5533D))
            Label(if (st.streak == 1) "Sequência: 1 dia" else "Sequência: ${st.streak} dias", Muted)
            if (st.projection > 0.0 && !st.hide) {
                Label("Projeção: ${formatBrl(st.projection, false)}", Muted)
            }
        }
    }
}

@Composable
private fun ClockPage() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by StateRepo.state.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Relógio interno: atualiza o cronômetro a cada segundo.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    // O celular confirmou (o estado mudou): libera o botão.
    LaunchedEffect(state?.active, state?.updatedAt) {
        pending = false
    }
    // Se o celular não responder, libera o botão depois de um tempo.
    LaunchedEffect(pending) {
        if (pending) {
            delay(8000L)
            pending = false
        }
    }

    val st = state
    val accent = Color(st?.accent ?: StateRepo.DEFAULT_ACCENT)

    // Vibra uma vez quando a meta do dia é batida com o ponto aberto (não vibra se já estava batida ao abrir).
    val goalReached = st != null && st.goalHours > 0.0 && st.dayMillis(now) >= (st.goalHours * 3_600_000.0).toLong()
    var lastReached by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(goalReached) {
        if (lastReached == false && goalReached && st?.active == true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastReached = goalReached
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.Center,
    ) {
        // Anel da meta diária ao redor da tela.
        val progress = st?.goalProgress(now) ?: 0f
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f + 4.dp.toPx()
            val diameter = size.minDimension - inset * 2
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = Track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke),
            )
            if (progress > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (st == null) {
                Label("PONTO PJ", accent, bold = true)
                Spacer(Modifier.height(8.dp))
                BasicText(
                    "Abra o Ponto PJ no celular para sincronizar",
                    style = TextStyle(color = TextColor, fontSize = 13.sp, textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(12.dp))
                PillButton("Sincronizar", accent, enabled = true) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    PhoneLink.refresh(context)
                }
            } else {
                Label(if (st.active) "● JORNADA ATIVA" else "PONTO FECHADO", if (st.active) accent else Muted, bold = true)
                BasicText(
                    formatClock(st.dayMillis(now)),
                    style = TextStyle(
                        color = TextColor,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                )
                BasicText(
                    formatBrl(st.dayEarnings(now), st.hide),
                    style = TextStyle(
                        color = accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                )
                Spacer(Modifier.height(2.dp))
                val goalText = when {
                    st.goalHours <= 0.0 -> "Hoje"
                    progress >= 1f -> "Meta do dia batida"
                    else -> String.format(Locale.forLanguageTag("pt-BR"), "%.0f%% da meta", progress * 100f)
                }
                Label(goalText, Muted)
                Spacer(Modifier.height(8.dp))
                PillButton(
                    text = when {
                        pending -> "Enviando…"
                        st.active -> "Registrar saída"
                        else -> "Registrar entrada"
                    },
                    color = accent,
                    enabled = !pending,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    pending = true
                    message = null
                    val path = if (st.active) WearProtocol.PATH_CLOCK_OUT else WearProtocol.PATH_CLOCK_IN
                    PhoneLink.send(context, path) { delivered ->
                        if (!delivered) {
                            pending = false
                            message = "Sem conexão com o celular"
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Label(message ?: "Mês: ${formatBrl(st.monthEarnings(now), st.hide)}", if (message != null) Color(0xFFE5533D) else Muted)
            }
        }
    }
}

@Composable
private fun Label(text: String, color: Color, bold: Boolean = false) {
    BasicText(
        text,
        style = TextStyle(
            color = color,
            fontSize = 10.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun PillButton(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(132.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) color else color.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = TextStyle(color = OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}
