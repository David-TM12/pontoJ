package br.com.pontopj.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.pontopj.data.brl
import br.com.pontopj.data.dayLabel
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.shortWeekday
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

private val LineColor: Color get() = Palette.GoldLight

/**
 * Liga os pontos com curvas suaves (tangentes horizontais em cada ponto), sem "estourar" acima
 * ou abaixo dos valores. Com [moveToFirst] = false continua o caminho atual a partir do primeiro ponto.
 */
private fun Path.curveThrough(points: List<Offset>, moveToFirst: Boolean) {
    if (points.isEmpty()) return
    if (moveToFirst) moveTo(points[0].x, points[0].y) else lineTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val mid = (a.x + b.x) / 2f
        cubicTo(mid, a.y, mid, b.y, b.x, b.y)
    }
}

/** Seletor em lista: mostra a opção atual e abre um menu ao tocar. */
@Composable
fun FilterDropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(modifier) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(Palette.SurfaceHigh)
                .border(1.dp, Palette.Gold.copy(alpha = 0.6f), shape)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                options.getOrElse(selectedIndex) { "" },
                color = Palette.Gold,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text("▾", color = Palette.Gold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (index == selectedIndex) Palette.Gold else Palette.Text,
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Botão de filtro em formato de pílula. */
@Composable
fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Palette.Gold else Palette.SurfaceHigh)
            .border(1.dp, if (selected) Palette.Gold else Palette.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Palette.OnGold else Palette.Muted,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun LegendDot(color: Color, text: String, line: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = if (line) 16.dp else 10.dp, height = if (line) 3.dp else 10.dp)
                .background(color, if (line) RoundedCornerShape(2.dp) else CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Barras de ganhos por dia + linha de média móvel (3 dias) para mostrar a tendência.
 * Tocar em uma barra seleciona o dia.
 */
@Composable
fun EarningsChart(
    values: List<Double>,
    dates: List<LocalDate>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 170.dp,
) {
    val n = values.size
    if (n == 0) return
    val maxValue = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
    val average = values.indices.map { i ->
        values.subList(maxOf(0, i - 2), i + 1).average()
    }
    val labelStep = when {
        n <= 16 -> 1
        n <= 22 -> 2
        else -> 3
    }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            Row(Modifier.fillMaxSize()) {
                for (i in 0 until n) {
                    val fraction by animateFloatAsState(
                        targetValue = (values[i] / maxValue).toFloat(),
                        animationSpec = tween(600),
                        label = "bar$i",
                    )
                    val isSelected = i == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (isSelected) Palette.Gold.copy(alpha = 0.10f) else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .pointerInput(i) { detectTapGestures { onSelect(i) } },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (n <= 8) 0.6f else 0.72f)
                                .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                .background(
                                    if (isSelected) {
                                        Brush.verticalGradient(listOf(Palette.Gold, Palette.GoldDeep))
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(Palette.GoldDeep.copy(alpha = 0.55f), Palette.GoldSoft),
                                        )
                                    },
                                    RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp),
                                ),
                        )
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cell = w / n
                fun yOf(v: Double): Float = h * (1f - (v / maxValue).toFloat())

                val path = Path()
                path.curveThrough(average.mapIndexed { i, v -> Offset(cell * (i + 0.5f), yOf(v)) }, moveToFirst = true)
                drawPath(
                    path = path,
                    color = LineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                if (n <= 16) {
                    average.forEachIndexed { i, v ->
                        drawCircle(LineColor, radius = 3.5.dp.toPx(), center = Offset(cell * (i + 0.5f), yOf(v)))
                    }
                }
                if (selected in 0 until n) {
                    drawCircle(
                        color = Palette.Gold,
                        radius = 7.dp.toPx(),
                        center = Offset(cell * (selected + 0.5f), yOf(average[selected])),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until n) {
                val show = (n - 1 - i) % labelStep == 0
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (show) {
                        Text(
                            if (n <= 8) shortWeekday(dates[i].dayOfWeek) else "${dates[i].dayOfMonth}",
                            color = if (i == selected) Palette.Gold else Palette.Muted,
                            fontSize = 10.sp,
                            fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
    }
}

/** Evolução acumulada do mês, com meta (tracejada) e projeção. */
@Composable
fun CumulativeChart(
    points: List<Double>,
    daysInMonth: Int,
    goal: Double,
    projection: Double,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 150.dp,
) {
    val maxY = maxOf(points.maxOrNull() ?: 0.0, goal, projection, 1.0) * 1.08
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            val w = size.width
            val h = size.height
            fun xOf(day: Int): Float = if (daysInMonth <= 1) 0f else w * (day - 1) / (daysInMonth - 1)
            fun yOf(v: Double): Float = h * (1f - (v / maxY).toFloat())

            for (k in 0..3) {
                val gy = h * k / 3f
                drawLine(Palette.Outline, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
            }
            if (goal > 0.0) {
                drawLine(
                    color = Palette.Muted,
                    start = Offset(0f, yOf(goal)),
                    end = Offset(w, yOf(goal)),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
                )
            }
            if (points.isNotEmpty()) {
                val pts = points.mapIndexed { i, v -> Offset(xOf(i + 1), yOf(v)) }
                val line = Path()
                line.curveThrough(pts, moveToFirst = true)
                val area = Path()
                area.moveTo(pts.first().x, h)
                area.curveThrough(pts, moveToFirst = false)
                area.lineTo(pts.last().x, h)
                area.close()
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(listOf(Palette.Gold.copy(alpha = 0.35f), Color.Transparent)),
                )
                drawPath(
                    path = line,
                    color = Palette.Gold,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                val lastX = xOf(points.size)
                val lastY = yOf(points.last())
                if (projection > 0.0 && points.size < daysInMonth) {
                    drawLine(
                        color = Palette.GoldDeep,
                        start = Offset(lastX, lastY),
                        end = Offset(xOf(daysInMonth), yOf(projection)),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                    )
                    drawCircle(
                        color = Palette.GoldDeep,
                        radius = 4.dp.toPx(),
                        center = Offset(xOf(daysInMonth), yOf(projection)),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                drawCircle(Palette.Gold, radius = 5.dp.toPx(), center = Offset(lastX, lastY))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val marks = listOf(1, daysInMonth / 3, daysInMonth * 2 / 3, daysInMonth)
            marks.forEach { Text("$it", color = Palette.Muted, style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(Palette.Gold, "Acumulado", line = true)
            if (goal > 0.0) LegendDot(Palette.Muted, "Meta", line = true)
            LegendDot(Palette.GoldDeep, "Projeção", line = true)
        }
    }
}

/** Rosca com legenda: percentual de cada fatia. */
@Composable
fun DonutChart(
    values: List<Double>,
    labels: List<String>,
    colors: List<Color>,
    centerTop: String,
    centerBottom: String,
    valueText: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val total = values.sum()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = 18.dp.toPx()
                val topLeft = Offset(strokePx / 2, strokePx / 2)
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                if (total <= 0.0) {
                    drawArc(
                        color = Palette.Outline,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokePx),
                    )
                } else {
                    var start = -90f
                    values.forEachIndexed { i, v ->
                        val sweep = (v / total * 360.0).toFloat()
                        if (sweep > 0f) {
                            drawArc(
                                color = colors[i % colors.size],
                                startAngle = start,
                                sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(strokePx),
                            )
                        }
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerTop, color = Palette.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(centerBottom, color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            values.forEachIndexed { i, v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(colors[i % colors.size], CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(labels[i], color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
                        Text(valueText(v), color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        if (total > 0.0) "${(v / total * 100).roundToInt()}%" else "0%",
                        color = Palette.Gold,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Calendário do mês: cada dia fica mais dourado quanto mais horas foram trabalhadas. */
@Composable
fun CalendarHeatmap(
    month: YearMonth,
    millisByDay: Map<LocalDate, Long>,
    earningsByDay: Map<LocalDate, Double>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    var selected by remember(month) { mutableStateOf<LocalDate?>(null) }
    val maxMillis = (millisByDay.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val offset = month.atDay(1).dayOfWeek.value - 1
    val length = month.lengthOfMonth()
    val weeks = (offset + length + 6) / 7
    val cellShape = RoundedCornerShape(8.dp)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { letter ->
                Text(
                    letter,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Palette.Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (dow in 0 until 7) {
                    val dayNumber = week * 7 + dow - offset + 1
                    if (dayNumber < 1 || dayNumber > length) {
                        Spacer(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        )
                    } else {
                        val date = month.atDay(dayNumber)
                        val ms = millisByDay[date] ?: 0L
                        val intensity = if (ms > 0L) 0.25f + 0.75f * (ms.toFloat() / maxMillis) else 0f
                        val isSelected = date == selected
                        val outline = when {
                            isSelected -> Modifier.border(1.5.dp, Palette.Text, cellShape)
                            date == today -> Modifier.border(1.5.dp, Palette.Gold, cellShape)
                            else -> Modifier
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(cellShape)
                                .background(if (ms > 0L) Palette.Gold.copy(alpha = intensity) else Palette.SurfaceHigh)
                                .then(outline)
                                .clickable { selected = if (isSelected) null else date },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$dayNumber",
                                color = if (ms > 0L && intensity > 0.55f) Palette.OnGold else Palette.Muted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        val chosen = selected
        Spacer(Modifier.height(4.dp))
        Text(
            if (chosen == null) {
                "Toque em um dia para ver os detalhes"
            } else {
                val ms = millisByDay[chosen] ?: 0L
                if (ms > 0L) {
                    "${dayLabel(chosen)}: ${formatHm(ms)} · ${brl(earningsByDay[chosen] ?: 0.0)}"
                } else {
                    "${dayLabel(chosen)}: sem registros"
                }
            },
            color = if (chosen == null) Palette.Muted else Palette.Gold,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Lista de barras horizontais com valor à direita. */
@Composable
fun HorizontalBars(
    items: List<Pair<String, Double>>,
    valueText: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val maxValue = (items.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.0001)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEachIndexed { i, item ->
            val fraction by animateFloatAsState(
                targetValue = (item.second / maxValue).toFloat(),
                animationSpec = tween(700),
                label = "hbar$i",
            )
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.first,
                        modifier = Modifier.weight(1f),
                        color = Palette.Text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        valueText(item.second),
                        color = Palette.Gold,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_NUMBERS),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Palette.SurfaceHigh),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Palette.GoldDeep, Palette.Gold))),
                    )
                }
            }
        }
    }
}
