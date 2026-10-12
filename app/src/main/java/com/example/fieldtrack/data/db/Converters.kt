package com.example.fieldtrack.data.db

import androidx.room.TypeConverter

/**
 * Enums are stored by `name`, not by ordinal.
 *
 * Ordinals would mean that inserting a constant in the middle of an enum silently
 * reinterprets every existing row. Names cost a few bytes and make the database
 * readable in a SQLite browser, which matters when debugging a sync problem from a
 * log file someone mailed in.
 *
 * Unknown values decode to null rather than throwing: a device running an older build
 * should show a job it does not fully understand, not crash on the list screen.
 */
class Converters {

    @TypeConverter fun billingModeToDb(v: BillingMode?): String? = v?.name
    @TypeConverter fun billingModeFromDb(v: String?): BillingMode? = v?.let { decode<BillingMode>(it) }

    @TypeConverter fun unitToDb(v: MeasureUnit?): String? = v?.name
    @TypeConverter fun unitFromDb(v: String?): MeasureUnit? = v?.let { decode<MeasureUnit>(it) }

    @TypeConverter fun jobStatusToDb(v: JobStatus?): String? = v?.name
    @TypeConverter fun jobStatusFromDb(v: String?): JobStatus? = v?.let { decode<JobStatus>(it) }

    @TypeConverter fun entryMethodToDb(v: EntryMethod?): String? = v?.name
    @TypeConverter fun entryMethodFromDb(v: String?): EntryMethod? = v?.let { decode<EntryMethod>(it) }

    @TypeConverter fun eventTypeToDb(v: JobEventType?): String? = v?.name
    @TypeConverter fun eventTypeFromDb(v: String?): JobEventType? = v?.let { decode<JobEventType>(it) }

    @TypeConverter fun ledgerKindToDb(v: LedgerKind?): String? = v?.name
    @TypeConverter fun ledgerKindFromDb(v: String?): LedgerKind? = v?.let { decode<LedgerKind>(it) }

    @TypeConverter fun methodToDb(v: PaymentMethod?): String? = v?.name
    @TypeConverter fun methodFromDb(v: String?): PaymentMethod? = v?.let { decode<PaymentMethod>(it) }

    @TypeConverter fun machineKindToDb(v: MachineKind?): String? = v?.name
    @TypeConverter fun machineKindFromDb(v: String?): MachineKind? = v?.let { decode<MachineKind>(it) }

    @TypeConverter fun machineStatusToDb(v: MachineStatus?): String? = v?.name
    @TypeConverter fun machineStatusFromDb(v: String?): MachineStatus? = v?.let { decode<MachineStatus>(it) }

    @TypeConverter fun roleToDb(v: MemberRole?): String? = v?.name
    @TypeConverter fun roleFromDb(v: String?): MemberRole? = v?.let { decode<MemberRole>(it) }

    @TypeConverter fun breakdownToDb(v: BreakdownCategory?): String? = v?.name
    @TypeConverter fun breakdownFromDb(v: String?): BreakdownCategory? = v?.let { decode<BreakdownCategory>(it) }

    @TypeConverter fun syncStateToDb(v: SyncState?): String? = v?.name
    @TypeConverter fun syncStateFromDb(v: String?): SyncState? = v?.let { decode<SyncState>(it) }

    @TypeConverter fun outboxOpToDb(v: OutboxOp?): String? = v?.name
    @TypeConverter fun outboxOpFromDb(v: String?): OutboxOp? = v?.let { decode<OutboxOp>(it) }

    private inline fun <reified T : Enum<T>> decode(raw: String): T? =
        enumValues<T>().firstOrNull { it.name == raw }
}
