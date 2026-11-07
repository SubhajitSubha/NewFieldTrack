package com.example.fieldtrack.data.export

import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.formatBillNumber
import com.example.fieldtrack.data.region.BusinessClock
import com.example.fieldtrack.designsystem.format.Money
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first

/** Which days an export covers. The people, machines and fields in it are always all of them. */
enum class ExportPeriod { Everything, ThisYear, LastYear }

/** What the person is allowed to take out, which is what they are allowed to see. */
data class ExportScope(val money: Boolean, val peopleCost: Boolean)

/**
 * One value in a comma-separated file, quoted the way spreadsheets expect.
 *
 * A value a spreadsheet could take for a formula (starting with = + @, or a minus before
 * a letter) gets a leading apostrophe: a farmer's name or note must never run as
 * something when somebody opens the file.
 */
fun csvCell(raw: String?): String {
    var value = raw.orEmpty()
    if (value.isNotEmpty() && value[0] in "=+@\t\r" ) value = "'$value"
    // A minus sign is only a danger in front of letters; "-500" is a number and stays one.
    if (value.length > 1 && value[0] == '-' && !value[1].isDigit() && value[1] != '.') value = "'$value"
    return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

fun csvLine(cells: List<String?>): String = cells.joinToString(",") { csvCell(it) }

/**
 * Everything a business has recorded, as a zip of plain CSV files any spreadsheet opens.
 *
 * This is the way out, and it is meant to be a real one: nothing is summarised away, every
 * amount is written as an ordinary decimal number in the business's own currency, every
 * date as year-month-day in the business's own time zone, and people and things are named
 * rather than left as codes. A bank, an accountant or a person moving to another app
 * should be able to use it without anybody explaining it.
 */
class RecordsExport(private val db: FieldTrackDatabase) {

    /** Writes the zip into [dir] and returns it. */
    suspend fun build(
        orgId: String,
        orgName: String,
        period: ExportPeriod,
        scope: ExportScope,
        dir: File,
        now: Long,
        zone: ZoneId = BusinessClock.zone,
        startMonth: Int = BusinessClock.fiscalStartMonth,
    ): File {
        dir.mkdirs()
        val digits = Money.style.digits
        fun money(minor: Long?): String = minor?.let { BigDecimal.valueOf(it, digits).toPlainString() }.orEmpty()
        fun day(millis: Long?): String = millis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }.orEmpty()
        fun dayNumber(day: Long?): String = day?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()

        val range = rangeOf(period, now, zone, startMonth)
        fun inRange(millis: Long) = millis >= range.first && millis < range.last

        val dao = db.exportDao()
        val customers = dao.customers(orgId)
        val fields = dao.fields(orgId)
        val machines = dao.machines(orgId)
        val team = dao.team(orgId)
        val customerName = customers.associate { it.id to it.name }
        val fieldName = fields.associate { it.id to it.name }
        val machineName = machines.associate { it.id to it.name }
        val personName = team.associate { it.personId to it.displayName }

        val files = linkedMapOf<String, String>()

        files["README.txt"] = buildString {
            appendLine("${orgName.ifBlank { "FieldTrack" }} — records taken out of FieldTrack on ${day(now)}")
            appendLine(
                when (period) {
                    ExportPeriod.Everything -> "Covers: everything recorded."
                    ExportPeriod.ThisYear -> "Covers: the accounting year from ${day(range.first)}."
                    ExportPeriod.LastYear -> "Covers: the accounting year from ${day(range.first)} to ${day(range.last - 1)}."
                }
            )
            appendLine("Amounts are plain numbers in ${Money.style.currencyCode}. Dates are year-month-day.")
            appendLine("Every file is a table: open it in any spreadsheet.")
        }

        if (scope.money) {
            val balances: Map<String, Long> = db.ledgerDao().observeBalances(orgId).first().associate { it.customerId to it.balanceMinor }
            files["customers.csv"] = table(
                listOf("Name", "Phone", "Village", "Note", "Owes (+) or is owed (-)"),
                customers.map { listOf(it.name, it.phone, it.village, it.note, money(balances[it.id] ?: 0L)) },
            )
        } else {
            files["customers.csv"] = table(
                listOf("Name", "Phone", "Village", "Note"),
                customers.map { listOf(it.name, it.phone, it.village, it.note) },
            )
        }
        files["fields.csv"] = table(
            listOf("Field", "Customer", "Area", "Unit", "Crop"),
            fields.map { listOf(it.name, customerName[it.customerId], it.area.toString(), it.unit.name, it.crop) },
        )
        files["machines.csv"] = table(
            listOf("Machine", "Kind", "Own name for it", "Registration", "Hours used"),
            machines.map { listOf(it.name, it.kind.name, it.typeLabel, it.plate, it.hoursUsed.toString()) },
        )
        files["jobs.csv"] = table(
            buildList {
                addAll(listOf("Date", "Customer", "Field", "Machine", "Driver", "Work", "Charged by", "Quantity", "Unit"))
                if (scope.money) addAll(listOf("Rate", "Amount", "Bill number"))
                add("Note")
            },
            dao.jobs(orgId).filter { inRange(it.workedAt) }.map { j ->
                buildList {
                    add(day(j.workedAt)); add(customerName[j.customerId]); add(fieldName[j.fieldId]); add(machineName[j.machineId])
                    add(personName[j.operatorPersonId]); add(j.workType); add(j.billingMode.name)
                    add(j.quantity.toString()); add(j.unit.name)
                    if (scope.money) {
                        add(money(j.rateMinor)); add(money(j.amountMinor))
                        add(if (j.billFy != null && j.billSeq != null) formatBillNumber(j.billFy, j.billSeq) else "")
                    }
                    add(j.note)
                }
            },
        )

        if (scope.money) {
            files["money.csv"] = table(
                listOf("Date", "Customer", "What", "Amount", "How paid", "Note"),
                dao.ledger(orgId).filter { inRange(it.atMillis) }.map {
                    listOf(day(it.atMillis), customerName[it.customerId], it.kind.name, money(it.amountMinor), it.method?.name, it.note)
                },
            )
            files["fuel.csv"] = table(
                listOf("Date", "Machine", "Litres", "Cost"),
                dao.fuel(orgId).filter { inRange(it.atMillis) }.map {
                    listOf(day(it.atMillis), machineName[it.machineId], it.litres.toString(), money(it.costMinor))
                },
            )
            files["service.csv"] = table(
                listOf("Date", "Machine", "What was done", "Cost", "At hours", "Who did it", "Parts"),
                dao.services(orgId).filter { inRange(it.atMillis) }.map {
                    listOf(day(it.atMillis), machineName[it.machineId], it.description, money(it.costMinor), it.atMachineHours?.toString(), it.vendor, it.partsNote)
                },
            )
            files["job-costs.csv"] = table(
                listOf("Date", "Kind", "People", "Amount", "Note"),
                dao.jobCosts(orgId).filter { inRange(it.atMillis) }.map {
                    listOf(day(it.atMillis), it.kind.name, it.headcount?.toString(), money(it.amountMinor), it.note)
                },
            )
        }
        if (scope.peopleCost) {
            files["pay.csv"] = table(
                listOf("Date", "Person", "For", "People", "Amount"),
                dao.payouts(orgId).filter { inRange(it.atMillis) }.map {
                    listOf(day(it.atMillis), it.toName ?: personName[it.toPersonId], it.covers.name, it.headcount.toString(), money(it.amountMinor))
                },
            )
        }
        files["team.csv"] = table(
            listOf("Name", "Role", "Phone"),
            team.map { listOf(it.displayName, it.role.name, it.phone) },
        )
        files["bookings.csv"] = table(
            listOf("Day", "Part of day", "Customer", "Field", "Work", "Machine", "Driver", "Status", "Note"),
            dao.bookings(orgId).filter { period == ExportPeriod.Everything || inRange(LocalDate.ofEpochDay(it.day).atStartOfDay(zone).toInstant().toEpochMilli()) }.map {
                listOf(dayNumber(it.day), it.part.name, customerName[it.customerId], fieldName[it.fieldId], it.workType, machineName[it.machineId], personName[it.operatorPersonId], it.status.name, it.note)
            },
        )

        val out = File(dir, "fieldtrack-${safe(orgName)}-${day(now)}.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                // A byte-order mark so a spreadsheet reads Hindi, Swahili or Arabic names as they are.
                if (name.endsWith(".csv")) zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out
    }

    private fun table(header: List<String>, rows: List<List<String?>>): String =
        (listOf(csvLine(header)) + rows.map { csvLine(it) }).joinToString("\r\n", postfix = "\r\n")

    private fun safe(name: String) = name.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').ifEmpty { "business" }.take(40)

    companion object {
        /** The first millisecond covered and the one after the last. */
        fun rangeOf(period: ExportPeriod, now: Long, zone: ZoneId, startMonth: Int): LongRange {
            if (period == ExportPeriod.Everything) return Long.MIN_VALUE..Long.MAX_VALUE
            val thisYear = com.example.fieldtrack.feature.customer.financialYearStart(now, zone, startMonth)
            val start = if (period == ExportPeriod.ThisYear) thisYear else thisYear.minusYears(1)
            val end = start.plusYears(1)
            return start.atStartOfDay(zone).toInstant().toEpochMilli()..end.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
}
