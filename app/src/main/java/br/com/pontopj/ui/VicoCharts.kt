package br.com.pontopj.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.pontopj.data.Privacy
import br.com.pontopj.data.compactPlain
import br.com.pontopj.data.shortWeekday
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.dashed
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.compose.common.shape.dashedShape
import com.patrykandpatrick.vico.compose.common.shape.rounded
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Gráficos baseados na biblioteca Vico (https://github.com/patrykandpatrick/vico).
 * Todos seguem a paleta Obsidian Gold Analytics (via Palette) e respeitam o modo "ocultar valores".
 */

private val LineColor: Color get() = Palette.GoldLight

/** Escala "bonita" do eixo Y: passo redondo (1, 2, 2.5, 5 × 10ⁿ) e topo múltiplo desse passo. */
private class NiceScale(val step: Double, val top: Double)

private fun niceScale(max: Double, headroom: Double = 1.05, ticks: Int = 4): NiceScale {
    val safeMax = (max * headroom).coerceAtLeast(1.0)
    val raw = safeMax / ticks
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val stepBase = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    val step = stepBase * magnitude
    return NiceScale(step = step, top = ceil(safeMax / step) * step)
}

/** Barra com degradê vertical e cantos de cima arredondados. */
@Composable
private fun barComponent(top: Color, bottom: Color, thickness: Dp): LineComponent {
    val barFill = remember(top, bottom) { fill(ShaderProvider.verticalGradient(arrayOf(top, bottom))) }
    return rememberLineComponent(
        fill = barFill,
        thickness = thickness,
        shape = CorneredShape.rounded(topLeft = 6.dp, topRight = 6.dp, bottomRight = 2.dp, bottomLeft = 2.dp),
    )
}

/** Barras normais + uma barra em destaque (índice [highlight]). */
private class HighlightColumns(
    private val normal: LineComponent,
    private val highlighted: LineComponent,
    private val highlight: Int,
) : ColumnCartesianLayer.ColumnProvider {
    override fun getColumn(
        entry: ColumnCartesianLayerModel.Entry,
        seriesIndex: Int,
        extraStore: ExtraStore,
    ): LineComponent = if (entry.x.roundToInt() == highlight) highlighted else normal

    override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore): LineComponent = normal
}

/** Marcador invisível: serve só para a Vico informar qual barra foi tocada. */
private object SilentMarker : CartesianMarker

@Composable
private fun yAxisFormatter(): CartesianValueFormatter =
    remember {
        CartesianValueFormatter { _, value, _ ->
            if (Privacy.hidden) "" else compactPlain(value)
        }
    }

/**
 * Barras de ganhos por dia + linha de média móvel (3 dias) para mostrar a tendência.
 * Tocar (ou arrastar) sobre uma barra seleciona o dia.
 */
@Composable
fun EarningsChart(
    values: List<Double>,
    dates: List<LocalDate>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 200.dp,
) {
    val n = values.size
    if (n == 0) return

    val average = remember(values) {
        values.indices.map { i -> values.subList(maxOf(0, i - 2), i + 1).average() }
    }
    val scale = remember(values) { niceScale(maxOf(values.max(), average.max())) }
    val labelStep = when {
        n <= 16 -> 1
        n <= 22 -> 2
        else -> 3
    }

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, average) {
        producer.runTransaction {
            columnSeries { series(values) }
            lineSeries { series(average) }
        }
    }

    val thickness = if (n <= 8) 18.dp else 11.dp
    val gap = if (n <= 8) 16.dp else 8.dp

    val normalBar = barComponent(Palette.GoldDeep.copy(alpha = 0.55f), Palette.GoldSoft, thickness)
    val selectedBar = barComponent(Palette.Gold, Palette.GoldDeep, thickness)
    val columns = remember(normalBar, selectedBar, selected) { HighlightColumns(normalBar, selectedBar, selected) }

    val range = remember(scale) { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = scale.top) }
    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = columns,
        columnCollectionSpacing = gap,
        rangeProvider = range,
    )

    val dot = rememberShapeComponent(fill = fill(LineColor), shape = CorneredShape.Pill)
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(LineColor)),
        stroke = LineCartesianLayer.LineStroke.continuous(thickness = 2.5.dp, cap = StrokeCap.Round),
        pointConnector = LineCartesianLayer.PointConnector.cubic(),
        pointProvider = if (n <= 16) {
            LineCartesianLayer.PointProvider.single(LineCartesianLayer.point(dot, 6.dp))
        } else {
            null
        },
    )
    val lineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(line),
        rangeProvider = range,
    )

    val axisLabel = rememberAxisLabelComponent(color = Palette.Muted, textSize = 10.sp)
    val guide = rememberAxisGuidelineComponent(fill = fill(Palette.Outline), thickness = 1.dp)
    val axisLine = rememberAxisLineComponent(fill = fill(Palette.Outline), thickness = 1.dp)
    val yFormatter = yAxisFormatter()

    val datesState = rememberUpdatedState(dates)
    val xFormatter = remember(n) {
        CartesianValueFormatter { _, value, _ ->
            val d = datesState.value.getOrNull(value.roundToInt())
            when {
                d == null -> ""
                n <= 8 -> shortWeekday(d.dayOfWeek)
                else -> "${d.dayOfMonth}"
            }
        }
    }

    val onSelectState = rememberUpdatedState(onSelect)
    val markerListener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                targets.firstOrNull()?.let { onSelectState.value(it.x.roundToInt()) }
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                targets.firstOrNull()?.let { onSelectState.value(it.x.roundToInt()) }
            }
        }
    }

    val chart = rememberCartesianChart(
        columnLayer,
        lineLayer,
        startAxis = VerticalAxis.rememberStart(
            line = null,
            tick = null,
            label = axisLabel,
            guideline = guide,
            valueFormatter = yFormatter,
            itemPlacer = remember(scale) {
                VerticalAxis.ItemPlacer.step(step = { scale.step }, shiftTopLines = false)
            },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            line = axisLine,
            tick = null,
            guideline = null,
            label = axisLabel,
            valueFormatter = xFormatter,
            itemPlacer = remember(n, labelStep) {
                HorizontalAxis.ItemPlacer.aligned(
                    spacing = { labelStep },
                    offset = { (n - 1) % labelStep },
                    addExtremeLabelPadding = false,
                )
            },
        ),
        marker = SilentMarker,
        markerVisibilityListener = markerListener,
        layerPadding = remember(gap) {
            { _: ExtraStore -> cartesianLayerPadding(scalableStart = gap / 2, scalableEnd = gap / 2) }
        },
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = producer,
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}

/** Última posição do acumulado em destaque (um ponto só). */
private class LastPointProvider(
    private val lastX: Double,
    private val point: LineCartesianLayer.Point,
) : LineCartesianLayer.PointProvider {
    override fun getPoint(
        entry: LineCartesianLayerModel.Entry,
        seriesIndex: Int,
        extraStore: ExtraStore,
    ): LineCartesianLayer.Point? = if (entry.x == lastX) point else null

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = point
}

/** Evolução acumulada do mês: curva suave com área, meta tracejada e projeção. */
@Composable
fun CumulativeChart(
    points: List<Double>,
    daysInMonth: Int,
    goal: Double,
    projection: Double,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 180.dp,
) {
    if (points.isEmpty()) return
    val showProjection = projection > 0.0 && points.size < daysInMonth
    val scale = remember(points, goal, projection) {
        niceScale(maxOf(points.max(), goal, if (showProjection) projection else 0.0))
    }

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points, projection, daysInMonth) {
        producer.runTransaction {
            lineSeries {
                series(x = points.indices.map { it + 1 }, y = points)
                if (showProjection) {
                    series(x = listOf(points.size, daysInMonth), y = listOf(points.last(), projection))
                }
            }
        }
    }

    val areaFill = remember(Palette.Gold) {
        fill(ShaderProvider.verticalGradient(arrayOf(Palette.Gold.copy(alpha = 0.38f), Color.Transparent)))
    }
    val dot = rememberShapeComponent(fill = fill(Palette.Gold), shape = CorneredShape.Pill)
    val mainLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(Palette.Gold)),
        stroke = LineCartesianLayer.LineStroke.continuous(thickness = 3.dp, cap = StrokeCap.Round),
        areaFill = LineCartesianLayer.AreaFill.single(areaFill),
        pointConnector = LineCartesianLayer.PointConnector.cubic(),
        pointProvider = LastPointProvider(points.size.toDouble(), LineCartesianLayer.point(dot, 10.dp)),
    )
    val projectionLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(Palette.GoldDeep)),
        stroke = LineCartesianLayer.LineStroke.dashed(
            thickness = 2.dp,
            cap = StrokeCap.Round,
            dashLength = 6.dp,
            gapLength = 5.dp,
        ),
    )
    val lineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(mainLine, projectionLine),
        rangeProvider = remember(scale, daysInMonth) {
            CartesianLayerRangeProvider.fixed(
                minX = 1.0,
                maxX = daysInMonth.toDouble(),
                minY = 0.0,
                maxY = scale.top,
            )
        },
    )

    val axisLabel = rememberAxisLabelComponent(color = Palette.Muted, textSize = 10.sp)
    val guide = rememberAxisGuidelineComponent(fill = fill(Palette.Outline), thickness = 1.dp)
    val axisLine = rememberAxisLineComponent(fill = fill(Palette.Outline), thickness = 1.dp)
    val yFormatter = yAxisFormatter()
    val xFormatter = remember { CartesianValueFormatter { _, value, _ -> "${value.roundToInt()}" } }

    val goalLine = rememberLineComponent(
        fill = fill(Palette.Muted),
        thickness = 1.5.dp,
        shape = dashedShape(),
    )
    val goalLabel = rememberTextComponent(color = Palette.Muted, textSize = 10.sp)
    val decorations = remember(goal, goalLine, goalLabel) {
        if (goal > 0.0) {
            listOf(
                HorizontalLine(
                    y = { goal },
                    line = goalLine,
                    labelComponent = goalLabel,
                    label = { "Meta" },
                    horizontalLabelPosition = Position.Horizontal.End,
                    verticalLabelPosition = Position.Vertical.Top,
                ),
            )
        } else {
            emptyList()
        }
    }

    val chart = rememberCartesianChart(
        lineLayer,
        startAxis = VerticalAxis.rememberStart(
            line = null,
            tick = null,
            label = axisLabel,
            guideline = guide,
            valueFormatter = yFormatter,
            itemPlacer = remember(scale) {
                VerticalAxis.ItemPlacer.step(step = { scale.step }, shiftTopLines = false)
            },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            line = axisLine,
            tick = null,
            guideline = null,
            label = axisLabel,
            valueFormatter = xFormatter,
            itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 5 }, offset = { 4 }) },
        ),
        decorations = decorations,
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = producer,
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}

/** Gráfico de barras simples; a barra em destaque usa o dourado cheio. */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlight: Int = -1,
    chartHeight: Dp = 130.dp,
    valueText: (Double) -> String = { "" },
) {
    if (values.isEmpty()) return
    val scale = remember(values) { niceScale(values.max(), headroom = 1.22) }

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        producer.runTransaction { columnSeries { series(values) } }
    }

    val n = values.size
    val thickness = 22.dp
    val gap = 14.dp
    val normalBar = barComponent(Palette.GoldDeep.copy(alpha = 0.55f), Palette.GoldSoft, thickness)
    val highlightBar = barComponent(Palette.Gold, Palette.GoldDeep, thickness)
    val columns = remember(normalBar, highlightBar, highlight) { HighlightColumns(normalBar, highlightBar, highlight) }

    val valueTextState = rememberUpdatedState(valueText)
    val dataFormatter = remember { CartesianValueFormatter { _, value, _ -> valueTextState.value(value) } }
    val dataLabel = rememberTextComponent(color = Palette.Muted, textSize = 10.sp)

    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = columns,
        columnCollectionSpacing = gap,
        dataLabel = dataLabel,
        dataLabelValueFormatter = dataFormatter,
        rangeProvider = remember(scale) { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = scale.top) },
    )

    val labelsState = rememberUpdatedState(labels)
    val axisLabel = rememberAxisLabelComponent(color = Palette.Muted, textSize = 10.sp)
    val axisLine = rememberAxisLineComponent(fill = fill(Palette.Outline), thickness = 1.dp)
    val xFormatter = remember {
        CartesianValueFormatter { _, value, _ -> labelsState.value.getOrElse(value.roundToInt()) { "" } }
    }

    val chart = rememberCartesianChart(
        columnLayer,
        bottomAxis = HorizontalAxis.rememberBottom(
            line = axisLine,
            tick = null,
            guideline = null,
            label = axisLabel,
            valueFormatter = xFormatter,
            itemPlacer = remember(n) { HorizontalAxis.ItemPlacer.aligned(addExtremeLabelPadding = false) },
        ),
        layerPadding = remember(gap) {
            { _: ExtraStore -> cartesianLayerPadding(scalableStart = gap / 2, scalableEnd = gap / 2) }
        },
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = producer,
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight + 34.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}
