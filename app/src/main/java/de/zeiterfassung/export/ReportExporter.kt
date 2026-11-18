package de.zeiterfassung.export

import de.zeiterfassung.domain.DayTotal
import de.zeiterfassung.domain.WorkReport
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportExporter {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

    fun writeCsv(report: WorkReport, output: OutputStream) {
        val days = report.days.filter { it.date.dayOfWeek.value <= 5 }
        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("Datum;Arbeitszeit")
            days.forEach { day ->
                writer.append(dateFormatter.format(day.date))
                    .append(';')
                    .append(formatDecimal(day.workedMillis).replace('.', ','))
                    .appendLine()
            }
            writer.append("Gesamt;")
                .append(formatDecimal(days.sumOf(DayTotal::workedMillis)).replace('.', ','))
                .appendLine()
        }
    }

    fun writeXlsx(report: WorkReport, output: OutputStream) {
        val days = report.days.filter { it.date.dayOfWeek.value <= 5 }
        ZipOutputStream(output).use { zip ->
            zip.addXml(
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """.trimIndent()
            )
            zip.addXml(
                "_rels/.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """.trimIndent()
            )
            zip.addXml(
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Arbeitszeit" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """.trimIndent()
            )
            zip.addXml(
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """.trimIndent()
            )
            zip.addXml(
                "xl/styles.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts>
                  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
                  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
                  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                  </cellXfs>
                  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>
                """.trimIndent()
            )
            zip.addXml("xl/worksheets/sheet1.xml", createWorksheet(days))
        }
    }

    private fun createWorksheet(days: List<DayTotal>): String {
        val rows = buildString {
            append("<row r=\"1\">")
            append(inlineStringCell("A1", "Datum"))
            append(inlineStringCell("B1", "Arbeitszeit"))
            append("</row>")
            days.forEachIndexed { index, day ->
                val rowNumber = index + 2
                append("<row r=\"$rowNumber\">")
                append(inlineStringCell("A$rowNumber", dateFormatter.format(day.date)))
                append(numberCell("B$rowNumber", day.workedMillis))
                append("</row>")
            }
            val totalRow = days.size + 2
            append("<row r=\"$totalRow\">")
            append(inlineStringCell("A$totalRow", "Gesamt"))
            append(numberCell("B$totalRow", days.sumOf(DayTotal::workedMillis)))
            append("</row>")
        }

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>$rows</sheetData>
            </worksheet>
        """.trimIndent()
    }

    private fun inlineStringCell(reference: String, value: String): String =
        "<c r=\"$reference\" t=\"inlineStr\"><is><t>${xmlEscape(value)}</t></is></c>"

    private fun numberCell(reference: String, millis: Long): String =
        "<c r=\"$reference\" s=\"1\"><v>${formatDecimal(millis)}</v></c>"

    private fun formatDecimal(millis: Long): String =
        BigDecimal.valueOf(millis)
            .divide(MILLIS_PER_HOUR, 2, RoundingMode.HALF_UP)
            .toPlainString()

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun ZipOutputStream.addXml(path: String, contents: String) {
        putNextEntry(ZipEntry(path))
        write(contents.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private val MILLIS_PER_HOUR = BigDecimal.valueOf(3_600_000L)
}
