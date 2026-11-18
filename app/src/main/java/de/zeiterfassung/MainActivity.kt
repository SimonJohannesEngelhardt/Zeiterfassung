package de.zeiterfassung

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.zeiterfassung.data.SQLiteWorkIntervalStore
import de.zeiterfassung.domain.DayTotal
import de.zeiterfassung.domain.ReportCalculator
import de.zeiterfassung.domain.TimeTracking
import de.zeiterfassung.domain.TimeTrackingException
import de.zeiterfassung.domain.WorkInterval
import de.zeiterfassung.domain.WorkReport
import de.zeiterfassung.export.ReportExporter
import de.zeiterfassung.timer.TimerNotificationService
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TimeTrackingApp(
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }
}

private enum class AppTab(val label: String) {
    TIMER("Timer"),
    HISTORY("Verlauf"),
    REPORT("Auswertung")
}

private enum class ExportFormat(val fileName: String) {
    CSV("Arbeitszeit.csv"),
    XLSX("Arbeitszeit.xlsx")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeTrackingApp(onRequestNotificationPermission: () -> Unit) {
    val context = LocalContext.current
    val tracking = remember { TimeTracking(SQLiteWorkIntervalStore(context)) }
    val zone = remember { ZoneId.systemDefault() }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var intervals by remember { mutableStateOf(tracking.intervals()) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedTab by remember { mutableStateOf(AppTab.TIMER) }
    var editingInterval by remember { mutableStateOf<WorkInterval?>(null) }
    var creatingInterval by remember { mutableStateOf(false) }
    var deletingInterval by remember { mutableStateOf<WorkInterval?>(null) }
    var showExportChoices by remember { mutableStateOf(false) }
    var exportReport by remember { mutableStateOf<WorkReport?>(null) }
    val currentMonday = remember { LocalDate.now(zone).with(java.time.DayOfWeek.MONDAY) }
    var reportFrom by remember { mutableStateOf(currentMonday) }
    var reportTo by remember { mutableStateOf(currentMonday.plusDays(4)) }

    fun refresh() {
        intervals = tracking.intervals()
        nowMillis = System.currentTimeMillis()
    }

    fun showError(message: String) {
        scope.launch { snackbarHost.showSnackbar(message) }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val report = exportReport
        if (uri != null && report != null) {
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Die Exportdatei konnte nicht geöffnet werden.")
                output.use { ReportExporter.writeCsv(report, it) }
            } catch (exception: IOException) {
                showError("CSV-Export fehlgeschlagen: ${exception.localizedMessage}")
            } catch (exception: SecurityException) {
                showError("Keine Berechtigung zum Speichern der CSV-Datei.")
            }
        }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        val report = exportReport
        if (uri != null && report != null) {
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Die Exportdatei konnte nicht geöffnet werden.")
                output.use { ReportExporter.writeXlsx(report, it) }
            } catch (exception: IOException) {
                showError("Excel-Export fehlgeschlagen: ${exception.localizedMessage}")
            } catch (exception: SecurityException) {
                showError("Keine Berechtigung zum Speichern der Excel-Datei.")
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000.milliseconds)
            refresh()
        }
    }

    val activeInterval = intervals.firstOrNull(WorkInterval::isRunning)
    LaunchedEffect(activeInterval?.id) {
        if (activeInterval != null) {
            context.startForegroundService(Intent(context, TimerNotificationService::class.java))
        }
    }

    val report = remember(intervals, reportFrom, reportTo, nowMillis) {
        ReportCalculator.calculate(intervals, reportFrom, reportTo, zone, nowMillis)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.label.take(1)) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            AppTab.TIMER -> TimerScreen(
                activeInterval = activeInterval,
                nowMillis = nowMillis,
                onStart = {
                    try {
                        tracking.start()
                        refresh()
                        onRequestNotificationPermission()
                    } catch (exception: TimeTrackingException) {
                        showError(exception.message ?: "Die Erfassung konnte nicht gestartet werden.")
                    }
                },
                onStop = {
                    try {
                        tracking.stop()
                        context.stopService(Intent(context, TimerNotificationService::class.java))
                        refresh()
                    } catch (exception: TimeTrackingException) {
                        showError(exception.message ?: "Die Erfassung konnte nicht gestoppt werden.")
                    }
                },
                modifier = Modifier.padding(padding)
            )

            AppTab.HISTORY -> HistoryScreen(
                intervals = intervals,
                nowMillis = nowMillis,
                zone = zone,
                onAdd = { creatingInterval = true },
                onEdit = { editingInterval = it },
                onDelete = { deletingInterval = it },
                modifier = Modifier.padding(padding)
            )

            AppTab.REPORT -> ReportScreen(
                report = report,
                from = reportFrom,
                to = reportTo,
                onPreviousWeek = {
                    reportFrom = reportFrom.minusWeeks(1)
                    reportTo = reportFrom.plusDays(4)
                },
                onCurrentWeek = {
                    reportFrom = currentMonday
                    reportTo = currentMonday.plusDays(4)
                },
                onNextWeek = {
                    reportFrom = reportFrom.plusWeeks(1)
                    reportTo = reportFrom.plusDays(4)
                },
                onFromChange = { date ->
                    reportFrom = date
                    if (reportTo.isBefore(date)) reportTo = date
                },
                onToChange = { date ->
                    reportTo = date
                    if (reportFrom.isAfter(date)) reportFrom = date
                },
                onExport = {
                    exportReport = report
                    showExportChoices = true
                },
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (creatingInterval || editingInterval != null) {
        IntervalEditorDialog(
            interval = editingInterval,
            nowMillis = nowMillis,
            zone = zone,
            onDismiss = {
                creatingInterval = false
                editingInterval = null
            },
            onSave = { start, end ->
                try {
                    if (editingInterval == null) {
                        tracking.saveManual(start, end)
                    } else {
                        tracking.update(editingInterval!!.copy(
                            startEpochMillis = start,
                            endEpochMillis = end
                        ))
                    }
                    refresh()
                    creatingInterval = false
                    editingInterval = null
                    null
                } catch (exception: TimeTrackingException) {
                    exception.message ?: "Der Eintrag konnte nicht gespeichert werden."
                }
            }
        )
    }

    deletingInterval?.let { interval ->
        AlertDialog(
            onDismissRequest = { deletingInterval = null },
            title = { Text("Eintrag löschen?") },
            text = { Text("Dieser Arbeitszeiteintrag wird dauerhaft gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        tracking.delete(interval.id)
                        refresh()
                        deletingInterval = null
                    } catch (exception: IllegalStateException) {
                        showError(exception.message ?: "Der Eintrag konnte nicht gelöscht werden.")
                    }
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deletingInterval = null }) { Text("Abbrechen") }
            }
        )
    }

    if (showExportChoices) {
        AlertDialog(
            onDismissRequest = { showExportChoices = false },
            title = { Text("Exportformat") },
            text = { Text("Wähle das Dateiformat für den angezeigten Zeitraum.") },
            confirmButton = {
                TextButton(onClick = {
                    showExportChoices = false
                    csvLauncher.launch(ExportFormat.CSV.fileName)
                }) { Text("CSV") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExportChoices = false
                        xlsxLauncher.launch(ExportFormat.XLSX.fileName)
                    }) { Text("Excel") }
                    TextButton(onClick = { showExportChoices = false }) { Text("Abbrechen") }
                }
            }
        )
    }
}

@Composable
private fun TimerScreen(
    activeInterval: WorkInterval?,
    nowMillis: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (activeInterval == null) "Friedrich Merz ist enttäuscht" else "Friedrich Merz freut sich",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(20.dp))
        Text(
            activeInterval?.let {
                formatDuration(nowMillis - it.startEpochMillis)
            } ?: "0:00",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = if (activeInterval == null) onStart else onStop,
            modifier = Modifier.size(180.dp),
            shape = CircleShape
        ) {
            Text(
                if (activeInterval == null) "Start" else "Stopp",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (activeInterval == null) {
                "Schalte den Timer auch für Pausen aus."
            } else {
                "Die Zeit läuft weiter, auch wenn du die App schließt."
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun HistoryScreen(
    intervals: List<WorkInterval>,
    nowMillis: Long,
    zone: ZoneId,
    onAdd: () -> Unit,
    onEdit: (WorkInterval) -> Unit,
    onDelete: (WorkInterval) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Verlauf", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onAdd) { Text("Hinzufügen") }
        }
        if (intervals.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Noch keine Arbeitszeiten erfasst.")
            }
        } else {
            val grouped = intervals.groupBy {
                Instant.ofEpochMilli(it.startEpochMillis).atZone(zone).toLocalDate()
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                grouped.toSortedMap(compareByDescending { it }).forEach { (date, dailyIntervals) ->
                    item(key = "header-$date") {
                        Text(
                            dateFormatter.format(date).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(dailyIntervals, key = WorkInterval::id) { interval ->
                        IntervalCard(
                            interval = interval,
                            nowMillis = nowMillis,
                            zone = zone,
                            onEdit = { onEdit(interval) },
                            onDelete = { onDelete(interval) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntervalCard(
    interval: WorkInterval,
    nowMillis: Long,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val start = Instant.ofEpochMilli(interval.startEpochMillis).atZone(zone)
    val end = interval.endEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val duration = (interval.endEpochMillis ?: nowMillis) - interval.startEpochMillis
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (end == null) {
                    "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} – läuft"
                } else if (start.toLocalDate() == end.toLocalDate()) {
                    "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} – ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                } else {
                    "${start.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))} – ${end.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))}"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(formatDuration(duration), style = MaterialTheme.typography.bodyLarge)
            if (!interval.isRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) { Text("Bearbeiten") }
                    TextButton(onClick = onDelete) { Text("Löschen") }
                }
            }
        }
    }
}

@Composable
private fun IntervalEditorDialog(
    interval: WorkInterval?,
    nowMillis: Long,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> String?
) {
    val now = remember(nowMillis) { Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime() }
    var start by remember(interval?.id) {
        mutableStateOf(
            interval?.let { Instant.ofEpochMilli(it.startEpochMillis).atZone(zone).toLocalDateTime() }
                ?: now.minusHours(1)
        )
    }
    var end by remember(interval?.id) {
        mutableStateOf(
            interval?.endEpochMillis?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime()
            } ?: now
        )
    }
    val context = LocalContext.current
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var saveError by remember(interval?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (interval == null) "Arbeitszeit ergänzen" else "Arbeitszeit bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Beginn", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                start = start.withYear(year).withMonth(month + 1).withDayOfMonth(day)
                            },
                            start.year,
                            start.monthValue - 1,
                            start.dayOfMonth
                        ).show()
                    }) { Text(dateFormat.format(start)) }
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> start = start.withHour(hour).withMinute(minute).withSecond(0).withNano(0) },
                            start.hour,
                            start.minute,
                            true
                        ).show()
                    }) { Text(timeFormat.format(start)) }
                }
                Text("Ende", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                end = end.withYear(year).withMonth(month + 1).withDayOfMonth(day)
                            },
                            end.year,
                            end.monthValue - 1,
                            end.dayOfMonth
                        ).show()
                    }) { Text(dateFormat.format(end)) }
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> end = end.withHour(hour).withMinute(minute).withSecond(0).withNano(0) },
                            end.hour,
                            end.minute,
                            true
                        ).show()
                    }) { Text(timeFormat.format(end)) }
                }
                saveError?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Speichern fehlgeschlagen",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                saveError = onSave(
                    start.atZone(zone).toInstant().toEpochMilli(),
                    end.atZone(zone).toInstant().toEpochMilli()
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportScreen(
    report: WorkReport,
    from: LocalDate,
    to: LocalDate,
    onPreviousWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onFromChange: (LocalDate) -> Unit,
    onToChange: (LocalDate) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Auswertung", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onPreviousWeek) { Text("‹ Woche") }
            TextButton(onClick = onCurrentWeek) { Text("Diese Woche") }
            TextButton(onClick = onNextWeek) { Text("Woche ›") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateSelectionButton("Von", from, onFromChange, Modifier.weight(1f))
            DateSelectionButton("Bis", to, onToChange, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text("Gesamt: ${formatDuration(report.totalRoundedMinutes * 60_000L)}", style = MaterialTheme.typography.titleLarge)
        if (report.days.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Im gewählten Zeitraum liegen keine Werktage.")
            }
        } else {
            WorkChart(report.days, Modifier.weight(1f).fillMaxWidth())
        }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("CSV oder Excel exportieren")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionButton(
    label: String,
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Text("$label: ${formatter.format(date)}")
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    showPicker = false
                }) { Text("Auswählen") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun WorkChart(days: List<DayTotal>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val maxHours = (days.maxOfOrNull(DayTotal::workedMillis) ?: 0L).coerceAtLeast(3_600_000L)
    val labelFormatter = remember { DateTimeFormatter.ofPattern("dd.MM") }
    BoxWithConstraints(modifier) {
        val chartWidth = maxOf(maxWidth, (days.size * 62).dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(chartWidth).height(250.dp).padding(top = 16.dp)) {
                val left = 20.dp.toPx()
                val right = 12.dp.toPx()
                val top = 18.dp.toPx()
                val bottom = 34.dp.toPx()
                val plotHeight = size.height - top - bottom
                val slot = (size.width - left - right) / days.size
                val barWidth = (slot * 0.56f).coerceAtLeast(4.dp.toPx())
                val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = onSurface.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 11.dp.toPx()
                }
                val valuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = onSurface.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 10.dp.toPx()
                }
                drawLine(
                    color = Color.LightGray,
                    start = androidx.compose.ui.geometry.Offset(left, top + plotHeight),
                    end = androidx.compose.ui.geometry.Offset(size.width - right, top + plotHeight),
                    strokeWidth = 1.dp.toPx()
                )
                days.forEachIndexed { index, day ->
                    val x = left + slot * (index + 0.5f)
                    val barHeight = plotHeight * day.workedMillis.toFloat() / maxHours.toFloat()
                    if (barHeight > 0) {
                        drawRect(
                            color = primary,
                            topLeft = androidx.compose.ui.geometry.Offset(x - barWidth / 2, top + plotHeight - barHeight),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                        )
                    }
                    val dateLabel = day.date.format(labelFormatter)
                    val valueLabel = formatDuration(day.roundedMinutes * 60_000L)
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(dateLabel, x, size.height - 4.dp.toPx(), labelPaint)
                        canvas.nativeCanvas.drawText(
                            valueLabel,
                            x,
                            (top + plotHeight - barHeight - 5.dp.toPx()).coerceAtLeast(top + valuePaint.textSize),
                            valuePaint
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMillis: Long): String {
    val minutes = (durationMillis.coerceAtLeast(0L) + 30_000L) / 60_000L
    return "%d:%02d".format(Locale.GERMANY, minutes / 60, minutes % 60)
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
