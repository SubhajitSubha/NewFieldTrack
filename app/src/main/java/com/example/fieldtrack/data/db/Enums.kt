package com.example.fieldtrack.data.db

/**
 * Domain vocabulary, stored in the database as the enum's own `name`.
 *
 * Renaming any constant here is a data migration, not a refactor.
 */

/** What a job is charged by. Decides which number the tracking screen makes the hero. */
enum class BillingMode {
    PerAcre,
    PerHour,
    PerTrip,
    /** Bag, quintal, piece — anything counted rather than measured. */
    PerUnit,
    PerKm,
    Fixed;

    /** True when the billable figure is counted up during the job rather than at the end. */
    val countsUpLive: Boolean
        get() = this != Fixed
}

/** How land or output is measured. Regional, so the business picks its own default. */
enum class MeasureUnit {
    Acre, Bigha, Kanal, Guntha, Katha,
    Hour, Trip, Quintal, Bag, Piece, Kilometre,
}

enum class JobStatus { Running, Paused, Completed, Discarded }

/**
 * Whether a job was timed by the app or typed in afterwards.
 *
 * Recorded because an owner and a driver will eventually disagree about a day in
 * October, and both deserve to know which records came off a running clock.
 */
enum class EntryMethod { Timed, Manual }

enum class JobEventType {
    Started, Paused, Resumed, QuantitySet, TripAdded, TripRemoved,
    FuelAdded, BreakdownReported, NoteAdded, PhotoAdded, HandedOver, Completed,
    RateChanged,
}

/**
 * A line in a customer's khata. The ledger is append-only: a mistake is corrected by
 * adding a reversing entry, never by editing one. That is what makes two people
 * recording payments offline merge without conflict.
 */
enum class LedgerKind {
    /** Work billed to the customer. Increases what they owe. */
    Charge,
    /** Money received. Decreases what they owe. */
    Payment,
    /** Balance carried in from a paper notebook when the customer was created. */
    Opening,
    /** A deliberate correction — a discount, a write-off, a reversal. */
    Adjustment,
}

enum class PaymentMethod { Cash, Upi, Bank, Cheque }

enum class MachineKind {
    Tractor, Harvester, Rotavator, Cultivator, Trolley, Thresher,
    Sprayer, Transplanter, PowerTiller, Baler, Leveller, Other,
}

enum class MachineStatus { Idle, Working, Down }

enum class MemberRole { Owner, Operator }

enum class BreakdownCategory { Hydraulics, Engine, Tyre, Electrical, Implement, Other }

/**
 * Where a row stands relative to the server.
 *
 * [Conflicted] means the server and this device both changed the same job and the
 * merge rules could not decide — a human has to pick. See `MergePolicy`.
 */
enum class SyncState { Pending, Synced, Conflicted }

enum class OutboxOp { Upsert, Delete }
