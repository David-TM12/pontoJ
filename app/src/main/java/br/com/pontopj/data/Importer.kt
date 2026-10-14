package br.com.pontopj.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import kotlin.math.floor

class ImportException(message: String) : Exception(message)

/** Arquivo aberto: lista de planilhas (abas) e uma função que devolve as células de cada uma. */
class ImportedSheet(
    val fileName: String,
    val sheetNames: List<String>,
    val loadSheet: (Int) -> List<List<String>>,
)

// ---------------------------------------------------------------------------------------------
// Leitura de arquivos (.xlsx e .csv)
// ---------------------------------------------------------------------------------------------

object SheetReader {
    private const val MAX_BYTES = 20 * 1024 * 1024

    fun open(context: Context, uri: Uri): ImportedSheet {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ImportException("Não foi possível abrir o arquivo.")
        if (bytes.size > MAX_BYTES) throw ImportException("Arquivo muito grande (limite de 20 MB).")
        val name = displayName(context, uri) ?: "arquivo"
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        val isOldExcel = bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte()
        return when {
            isZip -> readXlsx(bytes, name)
            isOldExcel -> throw ImportException(
                "Arquivos .xls (Excel antigo) não são suportados. No Excel, use Salvar como > " +
                    "Pasta de Trabalho do Excel (.xlsx) ou CSV.",
            )
            else -> readCsv(bytes, name)
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // ---------- CSV ----------

    private fun readCsv(bytes: ByteArray, fileName: String): ImportedSheet {
        var text = String(bytes, Charsets.UTF_8)
        if (text.contains('�')) text = String(bytes, Charsets.ISO_8859_1)
        text = text.removePrefix("﻿")
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val delimiter = listOf(';', ',', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ';'
        val rows = parseCsv(text, delimiter)
        if (rows.isEmpty()) throw ImportException("O arquivo está vazio.")
        return ImportedSheet(fileName, listOf("CSV")) { rows }
    }

    private fun parseCsv(text: String, delimiter: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(c)
                }
            } else if (c == '"') {
                inQuotes = true
            } else if (c == delimiter) {
                row.add(cell.toString())
                cell.setLength(0)
            } else if (c == '\n') {
                row.add(cell.toString())
                cell.setLength(0)
                rows.add(row)
                row = ArrayList()
            } else if (c != '\r') {
                cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    // ---------- XLSX ----------

    private fun readXlsx(bytes: ByteArray, fileName: String): ImportedSheet {
        val entries = HashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val n = entry.name
                    if (n.startsWith("xl/") && (n.endsWith(".xml") || n.endsWith(".rels"))) {
                        entries[n] = zip.readBytes()
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            throw ImportException("Não foi possível ler o arquivo Excel.")
        }

        val shared = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val rels = parseRels(entries["xl/_rels/workbook.xml.rels"])
        var sheets: List<Pair<String, String>> = parseWorkbook(entries["xl/workbook.xml"]).mapNotNull { sheet ->
            val target = rels[sheet.second]
            if (target == null) {
                null
            } else {
                val path = if (target.startsWith("/")) target.trimStart('/') else "xl/$target"
                if (entries.containsKey(path)) sheet.first to path else null
            }
        }
        if (sheets.isEmpty()) {
            sheets = entries.keys
                .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
                .sorted()
                .mapIndexed { i, p -> "Planilha ${i + 1}" to p }
        }
        if (sheets.isEmpty()) throw ImportException("Nenhuma planilha encontrada no arquivo.")

        val finalSheets = sheets
        return ImportedSheet(fileName, finalSheets.map { it.first }) { index ->
            parseSheet(entries[finalSheets[index].second] ?: ByteArray(0), shared)
        }
    }

    private fun newParser(xml: ByteArray): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml), null)
        return parser
    }

    private fun parseWorkbook(xml: ByteArray?): List<Pair<String, String>> {
        if (xml == null) return emptyList()
        val p = newParser(xml)
        val out = ArrayList<Pair<String, String>>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "sheet") {
                var name = ""
                var rid = ""
                for (i in 0 until p.attributeCount) {
                    val attr = p.getAttributeName(i)
                    if (attr == "name") {
                        name = p.getAttributeValue(i)
                    } else if (attr == "id" || attr.endsWith(":id")) {
                        rid = p.getAttributeValue(i)
                    }
                }
                out.add(name to rid)
            }
            ev = p.next()
        }
        return out
    }

    private fun parseRels(xml: ByteArray?): Map<String, String> {
        if (xml == null) return emptyMap()
        val p = newParser(xml)
        val out = HashMap<String, String>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "Relationship") {
                var id = ""
                var target = ""
                for (i in 0 until p.attributeCount) {
                    val attr = p.getAttributeName(i)
                    if (attr == "Id") id = p.getAttributeValue(i)
                    if (attr == "Target") target = p.getAttributeValue(i)
                }
                out[id] = target
            }
            ev = p.next()
        }
        return out
    }

    private fun parseSharedStrings(xml: ByteArray?): List<String> {
        if (xml == null) return emptyList()
        val p = newParser(xml)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var phonetic = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "si" -> {
                        inSi = true
                        sb.setLength(0)
                    }
                    "t" -> if (inSi && phonetic == 0) inT = true
                    "rPh" -> phonetic++
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inT) sb.append(p.text)
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "t" -> inT = false
                    "rPh" -> phonetic--
                    "si" -> {
                        out.add(sb.toString())
                        inSi = false
                    }
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch.isLetter()) n = n * 26 + (ch.uppercaseChar() - 'A' + 1) else break
        }
        return (n - 1).coerceAtLeast(0)
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        if (xml.isEmpty()) return emptyList()
        val p = newParser(xml)
        val rows = ArrayList<List<String>>()
        var current = ArrayList<String>()
        var column = 0
        var type = ""
        val value = StringBuilder()
        var inV = false
        var inInline = false
        var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "row" -> {
                        current = ArrayList()
                        val r = p.getAttributeValue(null, "r")?.toIntOrNull()
                        if (r != null) {
                            while (rows.size < r - 1 && rows.size < 100_000) rows.add(emptyList())
                        }
                    }
                    "c" -> {
                        column = columnIndex(p.getAttributeValue(null, "r") ?: "A")
                        type = p.getAttributeValue(null, "t") ?: ""
                        value.setLength(0)
                    }
                    "v" -> inV = true
                    "is" -> inInline = true
                    "t" -> if (inInline) inT = true
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inV || inT) value.append(p.text)
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "is" -> inInline = false
                    "c" -> {
                        val text = when (type) {
                            "s" -> shared.getOrNull(value.toString().trim().toIntOrNull() ?: -1) ?: ""
                            "b" -> if (value.toString().trim() == "1") "TRUE" else "FALSE"
                            "e" -> ""
                            else -> value.toString()
                        }
                        while (current.size <= column) current.add("")
                        current[column] = text
                    }
                    "row" -> {
                        rows.add(current)
                        if (rows.size > 60_000) return rows
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }
}

// ---------------------------------------------------------------------------------------------
// Tabela, mapeamento de colunas e conversão em registros
// ---------------------------------------------------------------------------------------------

class SheetTable(val headers: List<String>, val rows: List<List<String>>)

/** Índices das colunas escolhidas (-1 = não usar). */
data class ColumnMapping(
    val date: Int = -1,
    val start: Int = -1,
    val end: Int = -1,
    val start2: Int = -1,
    val end2: Int = -1,
    val hours: Int = -1,
    val rate: Int = -1,
)

class ImportResult(val sessions: List<WorkSession>, val skipped: Int, val total: Int)

private fun norm(text: String): String {
    val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
    return decomposed
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9$/ ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun isDateHeader(n: String): Boolean =
    n == "data" || n.startsWith("data ") || n == "dia" || n.startsWith("dia ") || n == "date"

private fun columnLetter(index: Int): String {
    var n = index
    var s = ""
    do {
        s = ('A' + n % 26).toString() + s
        n = n / 26 - 1
    } while (n >= 0)
    return s
}

/** Procura a linha de cabeçalho (a que tem "Data") e monta a tabela. */
fun buildTable(raw: List<List<String>>): SheetTable {
    val limit = minOf(raw.size, 15)
    val headerIndex = (0 until limit).firstOrNull { i -> raw[i].any { isDateHeader(norm(it)) } } ?: 0
    val width = raw.maxOfOrNull { it.size } ?: 0
    val header = raw.getOrNull(headerIndex) ?: emptyList()
    val headers = (0 until width).map { c ->
        val name = header.getOrNull(c)?.trim() ?: ""
        if (name.isEmpty()) "Coluna ${columnLetter(c)}" else name
    }
    val rows = raw.drop(headerIndex + 1)
        .map { r -> List(width) { c -> r.getOrNull(c)?.trim() ?: "" } }
        .filter { r -> r.any { it.isNotEmpty() } }
    return SheetTable(headers, rows)
}

/** Sugere o mapeamento a partir dos nomes das colunas. */
fun autoMap(headers: List<String>): ColumnMapping {
    val n = headers.map { norm(it) }
    val date = n.indexOfFirst { isDateHeader(it) }
    val rate = n.indexOfFirst {
        (it.contains("valor") && it.contains("hora")) || it.contains("r$/h") || it.contains("rate")
    }
    val timeWords = listOf("entrada", "inicio", "chegada", "retorno", "volta", "saida", "fim", "termino")
    val timeColumns = n.indices.filter { i -> i != date && i != rate && timeWords.any { n[i].contains(it) } }
    val hours = n.indices.firstOrNull { i ->
        i != date && i != rate && i !in timeColumns &&
            (n[i].contains("horas") || n[i].contains("total") || n[i].contains("duracao"))
    } ?: -1
    return ColumnMapping(
        date = date,
        start = timeColumns.getOrElse(0) { -1 },
        end = timeColumns.getOrElse(1) { -1 },
        start2 = timeColumns.getOrElse(2) { -1 },
        end2 = timeColumns.getOrElse(3) { -1 },
        hours = hours,
        rate = rate,
    )
}

private val isoDateRegex = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})")
private val brDateRegex = Regex("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})")
private val clockRegex = Regex("(\\d{1,2})\\s*[:hH]\\s*(\\d{2})")
private val hourOnlyRegex = Regex("^(\\d{1,2})\\s*[hH]$")
private val durationRegex = Regex("^(\\d{1,3})\\s*[:hH]\\s*(\\d{1,2})")

fun parseDateCell(raw: String): LocalDate? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val number = t.replace(',', '.').toDoubleOrNull()
    if (number != null) {
        // Data em formato de número do Excel (dias desde 30/12/1899).
        return if (number in 20000.0..80000.0) {
            LocalDate.of(1899, 12, 30).plusDays(floor(number).toLong())
        } else {
            null
        }
    }
    isoDateRegex.find(t)?.let { m ->
        return runCatching {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull()
    }
    brDateRegex.find(t)?.let { m ->
        var year = m.groupValues[3].toInt()
        if (year < 100) year += 2000
        return runCatching {
            LocalDate.of(year, m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }.getOrNull()
    }
    return null
}

fun parseTimeCell(raw: String): LocalTime? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    clockRegex.find(t)?.let { m ->
        return runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
    }
    hourOnlyRegex.find(t)?.let { m ->
        return runCatching { LocalTime.of(m.groupValues[1].toInt(), 0) }.getOrNull()
    }
    val number = t.replace(',', '.').toDoubleOrNull() ?: return null
    if (number < 0.0) return null
    // Hora do Excel é uma fração do dia (0,3541 = 08:30); datas com hora trazem a fração depois da vírgula.
    if (number >= 1.0 && number < 20000.0) return null
    val fraction = number - floor(number)
    val minutes = (Math.round(fraction * 1440.0) % 1440L).toInt()
    return LocalTime.of(minutes / 60, minutes % 60)
}

/** Duração em milissegundos a partir de "8:30", "8h30", 0,3541 (fração do dia) ou 8,5 (horas decimais). */
fun parseDurationCell(raw: String): Long? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    durationRegex.find(t)?.let { m ->
        val minutes = m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()
        return if (minutes in 1L..1440L) minutes * 60_000L else null
    }
    val number = t.replace(',', '.').toDoubleOrNull() ?: return null
    val minutes = Math.round(if (number < 1.0) number * 1440.0 else number * 60.0)
    return if (minutes in 1L..1440L) minutes * 60_000L else null
}

private fun LocalDateTime.toMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Converte as linhas da tabela em registros, usando o mapeamento escolhido. */
fun parseImport(table: SheetTable, mapping: ColumnMapping, defaultRate: Double): ImportResult {
    val out = ArrayList<WorkSession>()
    var skipped = 0
    val base = System.currentTimeMillis()
    var sequence = 0L

    for (row in table.rows) {
        fun cell(index: Int): String = if (index in row.indices) row[index] else ""

        val date = if (mapping.date >= 0) parseDateCell(cell(mapping.date)) else null
        if (date == null) {
            skipped++
            continue
        }
        val rate = if (mapping.rate >= 0) parseMoney(cell(mapping.rate)) ?: defaultRate else defaultRate
        val periods = ArrayList<Pair<Long, Long>>()

        fun addPeriod(startColumn: Int, endColumn: Int) {
            if (startColumn < 0 || endColumn < 0) return
            val start = parseTimeCell(cell(startColumn))
            val end = parseTimeCell(cell(endColumn))
            if (start == null || end == null) return
            val s = LocalDateTime.of(date, start)
            var e = LocalDateTime.of(date, end)
            if (!e.isAfter(s)) e = e.plusDays(1) // virou a meia-noite
            if (Duration.between(s, e).toMinutes() <= 16 * 60) {
                periods.add(s.toMillis() to e.toMillis())
            }
        }
        addPeriod(mapping.start, mapping.end)
        addPeriod(mapping.start2, mapping.end2)

        if (periods.isEmpty() && mapping.hours >= 0) {
            val duration = parseDurationCell(cell(mapping.hours))
            if (duration != null) {
                val s = LocalDateTime.of(date, LocalTime.of(8, 0)).toMillis()
                periods.add(s to s + duration)
            }
        }
        if (periods.isEmpty()) {
            skipped++
            continue
        }
        for (period in periods) {
            out.add(
                WorkSession(
                    id = base + sequence,
                    startMillis = period.first,
                    endMillis = period.second,
                    rate = rate,
                    manual = true,
                ),
            )
            sequence++
        }
    }
    return ImportResult(out, skipped, table.rows.size)
}

// ---------------------------------------------------------------------------------------------
// Exportação / modelo
// ---------------------------------------------------------------------------------------------

/** CSV no mesmo formato que a importação lê (pode ser aberto no Excel). */
fun sessionsToCsv(sessions: List<WorkSession>): String {
    val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    return buildString {
        append('﻿')
        appendLine("Data;Entrada;Saída;Valor hora")
        sessions.sortedBy { it.startMillis }.forEach { s ->
            val end = s.endMillis ?: return@forEach
            appendLine(
                "${dateOf(s.startMillis).format(dateFormat)};${timeOf(s.startMillis)};${timeOf(end)};" +
                    String.format(PtBR, "%.2f", s.rate),
            )
        }
    }
}

fun templateCsv(): String = buildString {
    append('﻿')
    appendLine("Data;Entrada;Saída;Valor hora")
    appendLine("13/10/2026;08:00;12:00;85,00")
    appendLine("13/10/2026;13:00;17:30;85,00")
    appendLine("14/10/2026;09:00;18:00;")
}
