package de.zeiterfassung.export

import de.zeiterfassung.domain.DayTotal
import de.zeiterfassung.domain.WorkReport
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExporterTest {
    private val cut = ReportExporter
    private val report = WorkReport(
        listOf(
            DayTotal(LocalDate.parse("2026-11-16"), 7 * 3_600_000L + 30 * 60_000L),
            DayTotal(LocalDate.parse("2026-11-17"), 0),
            DayTotal(LocalDate.parse("2026-11-18"), 20_000),
            DayTotal(LocalDate.parse("2026-11-19"), 20_000),
            DayTotal(LocalDate.parse("2026-11-20"), 20_000),
            DayTotal(LocalDate.parse("2026-11-22"), 3_600_000)
        )
    )

    @Test
    fun givenGermanWorkWeek_whenWritingCsv_thenWritesDailyValuesAndRawTotal() {
        val output = ByteArrayOutputStream()

        cut.writeCsv(report, output)

        val csv = output.toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF")
        assertTrue(csv.contains("Datum;Arbeitszeit"))
        assertTrue(csv.contains("16.11.2026;7,50"))
        assertTrue(csv.contains("17.11.2026;0,00"))
        assertTrue(csv.contains("Gesamt;7,52"))
        assertFalse(csv.contains("22.11.2026"))
    }

    @Test
    fun givenReport_whenWritingExcel_thenCreatesWorkbookWithDailyValues() {
        val output = ByteArrayOutputStream()

        cut.writeXlsx(report, output)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        assertTrue(entries.containsKey("xl/workbook.xml"))
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("16.11.2026"))
        assertTrue(sheet.contains("<v>7.50</v>"))
        assertTrue(sheet.contains("<v>7.52</v>"))
        assertTrue(sheet.contains("Gesamt"))
        assertFalse(sheet.contains("22.11.2026"))
    }
}
