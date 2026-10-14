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

@Composable
fun WatchApp() {
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
