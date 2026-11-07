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
    Fixed,

    /** Charged by the day, half days allowed: a machine and its driver hired for the day. */
    PerDay;

    /** True when the billable figure is counted up during the job rather than at the end. */
    val countsUpLive: Boolean
        get() = this != Fixed
}

/** How land or output is measured. Regional, so the business picks its own default. */
enum class MeasureUnit {
    Acre, Bigha, Kanal, Guntha, Katha,
    Hour, Trip, Quintal, Bag, Piece, Kilometre,

    /** Added with the worldwide units: ground in the metric system, and the loads and weights sold by it. */
    Hectare, SquareMetre, Bale, Tonne, Bushel, Kilogram, Litre,

    /** Hired by the day. */
    Day;

    /**
     * Whether this measures ground.
     *
     * Needed wherever a day's work is added up: acres, trips and quintals are all
     * numbers, and adding them together produces one that means nothing. Only the
     * land units can be totalled into "how much ground did I cover today".
     */
    val isLand: Boolean
        get() = this == Acre || this == Bigha || this == Kanal ||
            this == Guntha || this == Katha || this == Hectare || this == SquareMetre
}

/**
 * Who pays for the diesel on a job, which changes what the rate means.
 *
 * [Included] is the usual: the rate covers it and the contractor buys it. [CustomerSupplies]
 * means the farmer fills the tank, so the rate is for the work alone. [BilledExtra] means the
 * diesel the contractor puts in is added to the bill at what it cost.
 */
enum class FuelTerms { Included, CustomerSupplies, BilledExtra }

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

    /**
     * A clock left running, stopped afterwards at the time work really ended.
     *
     * Written when it was put right, not at the time it names: [JobEventEntity.textValue]
     * carries the moment the clock was stopped at and [JobEventEntity.numericValue]
     * the seconds taken off. Both halves stay on the record, because "the bill says
     * nine hours but the clock said twenty-two" is exactly the argument this
     * timeline exists to settle.
     *
     * Added after release. An older build reading one of these skips that single
     * event rather than the restore — the applier reads row by row — so the
     * job itself still arrives with its corrected time.
     */
    ClockCorrected,
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

/**
 * How a customer paid.
 *
 * Which of these a business offers is its own choice, kept on the business: [Upi] is the
 * instant bank-to-bank transfer of India, [MobileMoney] covers M-Pesa, MTN MoMo, bKash and
 * their like, [InKind] is grain or produce handed over instead of money.
 * Stored by name, so new ones are added at the end and none is renamed.
 */
enum class PaymentMethod { Cash, Upi, Bank, Cheque, MobileMoney, Card, InKind }

/**
 * What a payment out can be laid against.
 *
 * [Period] is the one that costs something. A month's wages cannot honestly be
 * put against a machine or a job, so anything totalling by either has to leave
 * it out — and say that it has, rather than quietly reporting a profit that is
 * too high.
 */
enum class PayoutCovers {
    Job, Day, Period,

    /** Money given to a team member ahead of the work, to be taken off later pay. */
    Advance,
}

/**
 * What a job cost, other than the machine and its diesel.
 *
 * Two kinds rather than five, because the split that matters is whether the
 * money was a wage. Wages are held behind [Capability.SeePeopleCost]; the tea
 * and the tempo that carried the crew out are not a secret from anybody who
 * may see money at all. Which tea, and which tempo, lives in the note.
 */
enum class JobCostKind { Labour, Other }

/** The papers a machine has to carry, each with a date it runs out. */
enum class PaperKind { Registration, Insurance, Fitness, Permit, Pollution, Other }

enum class MachineKind {
    Tractor, Harvester, Rotavator, Cultivator, Trolley, Thresher,
    Sprayer, Transplanter, PowerTiller, Baler, Leveller, Other,

    // Added with the worldwide list. Stored by name, so these go at the end.
    Excavator, Seeder, Mulcher, Plough, Harrow, DroneSprayer, WaterTanker,
    SugarcaneHarvester, PotatoDigger, Ridger, GrainCart, Pump,
}

enum class MachineStatus { Idle, Working, Down }

/** The part of a day a booking is for. Farm work does not keep to the clock, so no finer. */
enum class DayPart { Morning, Afternoon, WholeDay }

/**
 * Where a booking stands, as somebody who plans said it.
 *
 * Not "started": that is read off the job that was started from the booking, so
 * a driver's phone never has to write the plan. Done is for work that happened
 * without the app's clock and was entered afterwards.
 */
enum class BookingStatus { Planned, Cancelled, Done }

/** Where a farmer's request for work, made through the booking link, stands. */
enum class RequestStatus { New, Accepted, Declined }

/** Why a booking did not happen. Cheap to say, and the question is always asked later. */
enum class CancelReason { Rain, Farmer, Machine, Other }

/**
 * What somebody is in a business. A [Viewer] looks and cannot change: a partner, an
 * accountant, a son at university. Added after the other two, so it goes last.
 */
enum class MemberRole { Owner, Operator, Viewer }

/**
 * The kinds of change worth writing down about a team.
 *
 * Only the ones that alter who may do what, or who is here at all. A log that
 * records everything is a log nobody reads, and the question this answers is
 * narrow: who was let in, who was let go, and who was given sight of the money.
 */
enum class TeamEventKind {
    Joined,
    Removed,
    TakenBack,
    MadeOwner,
    SteppedBack,
    PermissionsChanged,
    CodeSent,
    CodeCancelled,
    Merged,
}

enum class BreakdownCategory { Hydraulics, Engine, Tyre, Electrical, Implement, Other }

/**
 * Where a row stands relative to the server.
 *
 * [Conflicted] means the server and this device both changed the same job and the
 * merge rules could not decide — a human has to pick. See `MergePolicy`.
 */
enum class SyncState { Pending, Synced, Conflicted }

enum class OutboxOp { Upsert, Delete }
