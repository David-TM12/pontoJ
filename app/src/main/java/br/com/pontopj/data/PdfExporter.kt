package br.com.pontopj.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val BLACK = 0xFF0A0A0A.toInt()
private val GOLD = 0xFFE0A800.toInt()
private val GOLD_SOFT = 0xFFEBCB6B.toInt()
private val INK = 0xFF1B1A16.toInt()
private val MUTED = 0xFF6F6A5C.toInt()
private val MUTED_LIGHT = 0xFFB9B3A0.toInt()
private val CARD = 0xFFF7F3E6.toInt()
private val LINE = 0xFFE4DFCC.toInt()
private val WHITE = 0xFFFFFFFF.toInt()

private class Col(val label: String, val x: Float, val align: Paint.Align)

/** Desenha as páginas A4 do relatório (cabeçalho preto com dourado, igual ao app). */
private class PdfWriter(
    private val doc: PdfDocument,
    private val period: String,
    private val generated: String,
) {
    private val width = 595
    private val height = 842
    val margin = 36f
    private var number = 0
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var y = 0f

    private fun text(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = align
        }

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun stroke(color: Int, widthPx: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = widthPx
        strokeCap = Paint.Cap.ROUND
    }

    fun newPage(first: Boolean) {
        if (number > 0) closePage()
        number++
        page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, number).create())
        canvas = page.canvas
        if (first) drawHero() else drawSlimHeader()
    }

    fun finish() {
        closePage()
    }

    private fun closePage() {
        canvas.drawLine(margin, height - 40f, width - margin, height - 40f, stroke(LINE, 1f))
        canvas.drawText("Ponto PJ · Relatório de ganhos", margin, height - 24f, text(8f, MUTED))
        canvas.drawText("Página $number", width - margin, height - 24f, text(8f, MUTED, align = Paint.Align.RIGHT))
        doc.finishPage(page)
    }

    private fun drawHero() {
        val w = width.toFloat()
        canvas.drawRect(0f, 0f, w, 128f, fill(BLACK))
        canvas.drawRect(0f, 128f, w, 132f, fill(GOLD))
        val cx = margin + 18f
        val cy = 62f
        val ring = stroke(GOLD, 4f)
        canvas.drawCircle(cx, cy, 17f, ring)
        canvas.drawLine(cx, cy - 9f, cx, cy, ring)
        canvas.drawLine(cx, cy, cx + 6f, cy + 4f, ring)
        canvas.drawText("Relatório de Ganhos", margin + 48f, 60f, text(24f, WHITE, true))
        canvas.drawText(period, margin + 48f, 82f, text(12f, GOLD))
        canvas.drawText("Ponto PJ", w - margin, 56f, text(14f, GOLD, true, Paint.Align.RIGHT))
        canvas.drawText("Gerado em $generated", w - margin, 74f, text(9f, MUTED_LIGHT, align = Paint.Align.RIGHT))
        y = 156f
    }

    private fun drawSlimHeader() {
        val w = width.toFloat()
        canvas.drawRect(0f, 0f, w, 40f, fill(BLACK))
        canvas.drawRect(0f, 40f, w, 43f, fill(GOLD))
        canvas.drawText("Relatório de Ganhos · Ponto PJ", margin, 26f, text(10f, GOLD, true))
        canvas.drawText(period, w - margin, 26f, text(9f, MUTED_LIGHT, align = Paint.Align.RIGHT))
        y = 66f
    }

    /** Abre uma página nova se não houver [space] de altura livre. Devolve true se abriu. */
    fun ensure(space: Float): Boolean {
        if (y + space > height - 52f) {
            newPage(false)
            return true
        }
        return false
    }

    fun gap(amount: Float) {
        y += amount
    }

    fun kpiRow(items: List<Pair<String, String>>, highlightFirst: Boolean) {
        val gapX = 12f
        val cardW = (width - 2 * margin - gapX * (items.size - 1)) / items.size
        val cardH = 58f
        items.forEachIndexed { i, item ->
            val left = margin + i * (cardW + gapX)
            val dark = highlightFirst && i == 0
            canvas.drawRoundRect(RectF(left, y, left + cardW, y + cardH), 10f, 10f, fill(if (dark) BLACK else CARD))
            canvas.drawRoundRect(RectF(left, y + 10f, left + 4f, y + cardH - 10f), 2f, 2f, fill(GOLD))
            canvas.drawText(item.first, left + 16f, y + 22f, text(8.5f, if (dark) MUTED_LIGHT else MUTED))
            canvas.drawText(item.second, left + 16f, y + 44f, text(16f, if (dark) GOLD else INK, true))
        }
        y += cardH + 12f
    }

    fun sectionTitle(title: String) {
        canvas.drawText(title, margin, y + 12f, text(12f, INK, true))
        canvas.drawRect(margin, y + 17f, margin + 28f, y + 19f, fill(GOLD))
        y += 30f
    }

    fun barChart(values: List<Double>, labels: List<String>) {
        if (values.isEmpty()) return
        val chartH = 90f
        val left = margin
        val totalW = width - 2 * margin
        val maxV = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
        val n = values.size
        val slot = totalW / n
        val barW = (slot * 0.62f).coerceAtMost(26f)
        val base = y + chartH + 12f
        canvas.drawLine(left, base, left + totalW, base, stroke(LINE, 1f))
        values.forEachIndexed { i, v ->
            val barH = (v / maxV * chartH).toFloat().coerceAtLeast(if (v > 0.0) 2f else 0f)
            val x = left + slot * i + (slot - barW) / 2
            canvas.drawRoundRect(RectF(x, base - barH, x + barW, base), 3f, 3f, fill(if (v == maxV) GOLD else GOLD_SOFT))
            if (n <= 16 && v > 0.0) {
                canvas.drawText(compact(v), x + barW / 2, base - barH - 3f, text(7f, MUTED, align = Paint.Align.CENTER))
            }
            if (n <= 16 || i % 2 == 0) {
                canvas.drawText(labels[i], x + barW / 2, base + 11f, text(7.5f, MUTED, align = Paint.Align.CENTER))
            }
        }
        y = base + 28f
    }

    fun tableHeader(cols: List<Col>) {
        canvas.drawRoundRect(RectF(margin, y, width - margin, y + 22f), 6f, 6f, fill(BLACK))
        cols.forEach { canvas.drawText(it.label, it.x, y + 15f, text(8.5f, GOLD, true, it.align)) }
        y += 24f
    }

    fun tableRow(cols: List<Col>, values: List<String>, zebra: Boolean) {
        if (zebra) canvas.drawRect(margin, y, width - margin, y + 20f, fill(CARD))
        values.forEachIndexed { i, value ->
            canvas.drawText(value, cols[i].x, y + 14f, text(9f, INK, i == values.lastIndex, cols[i].align))
        }
        y += 20f
    }

    fun totalBand(hours: String, total: String, hoursX: Float) {
        y += 6f
        canvas.drawRoundRect(RectF(margin, y, width - margin, y + 30f), 8f, 8f, fill(BLACK))
        canvas.drawText("TOTAL", margin + 10f, y + 20f, text(10f, GOLD, true))
        canvas.drawText(hours, hoursX, y + 20f, text(10f, WHITE, true))
        canvas.drawText(total, width - margin - 10f, y + 20f, text(12f, GOLD, true, Paint.Align.RIGHT))
        y += 36f
    }

    fun note(message: String) {
        canvas.drawText(message, margin, y + 12f, text(10f, MUTED))
        y += 24f
    }
}

object PdfExporter {
    fun generate(
        context: Context,
        sessions: List<WorkSession>,
        from: LocalDate,
        to: LocalDate,
        now: Long,
    ): SavedFile? {
        val inRange = sessions
            .filter { dateOf(it.startMillis) in from..to }
            .sortedBy { it.startMillis }
        val totalMs = inRange.totalMillis(now)
        val totalEarn = inRange.totalEarnings(now)
        val daysWorked = inRange.map { dateOf(it.startMillis) }.toSet().size

        val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val period = "${from.format(dateFormat)} a ${to.format(dateFormat)}"
        val generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

        val doc = PdfDocument()
        try {
            val w = PdfWriter(doc, period, generated)
            w.newPage(first = true)

            w.kpiRow(
                listOf(
                    "TOTAL GANHO" to brl(totalEarn),
                    "HORAS TRABALHADAS" to formatHm(totalMs),
                    "DIAS TRABALHADOS" to "$daysWorked",
                ),
                highlightFirst = true,
            )
            w.kpiRow(
                listOf(
                    "REGISTROS" to "${inRange.size}",
                    "MÉDIA POR DIA" to formatHm(if (daysWorked > 0) totalMs / daysWorked else 0L),
                    "GANHO MÉDIO POR DIA" to brl(if (daysWorked > 0) totalEarn / daysWorked else 0.0),
                ),
                highlightFirst = false,
            )

            // ---- Gráfico: por dia (até 31 dias) ou por mês ----
            val spanDays = java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt() + 1
            val chartValues = ArrayList<Double>()
            val chartLabels = ArrayList<String>()
            val months = ArrayList<YearMonth>()
            if (spanDays <= 31) {
                val byDate = inRange.groupBy { dateOf(it.startMillis) }
                var d = from
                while (!d.isAfter(to)) {
                    chartValues.add(byDate[d]?.totalEarnings(now) ?: 0.0)
                    chartLabels.add("${d.dayOfMonth}")
                    d = d.plusDays(1)
                }
            } else {
                var ym = YearMonth.from(from)
                val last = YearMonth.from(to)
                val multiYear = ym.year != last.year
                while (!ym.isAfter(last)) {
                    months.add(ym)
                    val earned = inRange.filter { YearMonth.from(dateOf(it.startMillis)) == ym }.totalEarnings(now)
                    chartValues.add(earned)
                    chartLabels.add(shortMonth(ym) + if (multiYear) "/${ym.year % 100}" else "")
                    ym = ym.plusMonths(1)
                }
            }
            w.ensure(150f)
            w.sectionTitle(if (spanDays <= 31) "Ganhos por dia" else "Ganhos por mês")
            w.barChart(chartValues, chartLabels)

            // ---- Resumo por mês (quando o período cobre mais de um mês) ----
            if (months.size > 1) {
                w.ensure(70f)
                w.sectionTitle("Resumo por mês")
                val cols = listOf(
                    Col("Mês", w.margin + 10f, Paint.Align.LEFT),
                    Col("Horas", 330f, Paint.Align.RIGHT),
                    Col("Dias", 410f, Paint.Align.RIGHT),
                    Col("Ganho", 595f - w.margin - 10f, Paint.Align.RIGHT),
                )
                w.tableHeader(cols)
                months.forEachIndexed { i, ym ->
                    if (w.ensure(24f)) w.tableHeader(cols)
                    val list = inRange.filter { YearMonth.from(dateOf(it.startMillis)) == ym }
                    w.tableRow(
                        cols,
                        listOf(
                            monthLabel(ym),
                            formatHm(list.totalMillis(now)),
                            "${list.map { dateOf(it.startMillis) }.toSet().size}",
                            brl(list.totalEarnings(now)),
                        ),
                        zebra = i % 2 == 1,
                    )
                }
                w.gap(14f)
            }

            // ---- Registros ----
            w.ensure(70f)
            w.sectionTitle("Registros")
            if (inRange.isEmpty()) {
                w.note("Nenhum registro encontrado neste período.")
            } else {
                val cols = listOf(
                    Col("Data", w.margin + 10f, Paint.Align.LEFT),
                    Col("Entrada", 170f, Paint.Align.LEFT),
                    Col("Saída", 235f, Paint.Align.LEFT),
                    Col("Duração", 305f, Paint.Align.LEFT),
                    Col("Valor/hora", 450f, Paint.Align.RIGHT),
                    Col("Ganho", 595f - w.margin - 10f, Paint.Align.RIGHT),
                )
                w.tableHeader(cols)
                inRange.forEachIndexed { i, s ->
                    if (w.ensure(24f)) w.tableHeader(cols)
                    val day = dateOf(s.startMillis)
                    w.tableRow(
                        cols,
                        listOf(
                            "${day.format(dateFormat)} · ${shortWeekday(day.dayOfWeek)}",
                            timeOf(s.startMillis),
                            s.endMillis?.let { timeOf(it) } ?: "em andamento",
                            formatHm(s.durationMillis(now)),
                            brl(s.rate),
                            brl(s.earnings(now)),
                        ),
                        zebra = i % 2 == 1,
                    )
                }
                w.ensure(44f)
                w.totalBand(formatHm(totalMs), brl(totalEarn), 305f)
            }

            w.finish()
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            val name = "Relatorio_PontoPJ_${from}_a_${to}.pdf"
            return saveToDownloads(context, name, "application/pdf", out.toByteArray())
        } finally {
            doc.close()
        }
    }
}
