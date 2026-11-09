package com.example.fieldtrack.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Schema changes, written out rather than fallen back from.
 *
 * This database holds work that was done and money that was collected. A contractor
 * who loses a season to a version bump has no way to get it back, so every change
 * lands as a migration even while the app is unreleased.
 */

/**
 * Letterhead details on the business, and a bill number on the job.
 *
 * Every column is nullable with no default: existing rows keep working, jobs billed
 * before this version simply have no bill number, and nothing has to be back-filled.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE organisations ADD COLUMN phone TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN addressLine TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN gstin TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN upiId TEXT")
        db.execSQL("ALTER TABLE jobs ADD COLUMN billFy TEXT")
        db.execSQL("ALTER TABLE jobs ADD COLUMN billSeq INTEGER")
    }
}


/**
 * Trips priced one at a time.
 *
 * Back-fills the new column for jobs billed before it existed, where every trip was
 * necessarily at the same rate — so their totals come out exactly as they did when
 * they were billed, and nothing already sitting on a khata moves by a rupee.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN tripTotalRupees INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE jobs SET tripTotalRupees = CAST(ROUND(quantity * rateRupees) AS INTEGER) " +
                "WHERE billingMode = 'PerTrip'"
        )
    }
}


/**
 * More than one rate for the same kind of work.
 *
 * Nullable with no default, so every rate already on the card keeps working and keeps
 * being found: a row with no label is the plain rate for that work, exactly as it was
 * before this column existed.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rates ADD COLUMN label TEXT")
    }
}


/**
 * Memberships keyed so a security rule can find them.
 *
 * A rule may fetch a document whose path it can build and may not search a
 * collection, so a membership identified by a random id with the person named
 * inside it cannot be used to answer "is this caller a member here". Rebuilding the
 * id from the two ids it already contains makes it findable, and costs nothing: the
 * row is the same row, and nothing outside this table refers to it by id.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE memberships SET id = orgId || '_' || personId")
    }
}


/**
 * A way past a change the server will never take.
 *
 * Nullable with no default: every change already queued is, by definition, not one
 * the user has set aside.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE outbox ADD COLUMN blockedAt INTEGER")
    }
}


/**
 * The invite a driver came in on, kept so the server can verify it.
 *
 * Nullable with no default: an owner creates their business rather than joining it,
 * and every membership written before this column existed belongs to somebody who
 * is already inside.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memberships ADD COLUMN joinedWithCode TEXT")
    }
}


/**
 * What a driver's phone is still carrying.
 *
 * Two columns rather than one, because a count on its own invites being read as
 * current when it may be days old. The owner is shown both.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memberships ADD COLUMN unsentCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE memberships ADD COLUMN unsentAt INTEGER")
    }
}


/**
 * Who was let in, who was let go, and who was given sight of the money.
 *
 * Starts empty on purpose. Nothing in the app wrote these down before now, so
 * there is no history to backfill — the log begins the day it ships.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS team_events (
                id TEXT NOT NULL PRIMARY KEY,
                orgId TEXT NOT NULL,
                atMillis INTEGER NOT NULL,
                kind TEXT NOT NULL,
                byPersonId TEXT,
                byName TEXT NOT NULL DEFAULT '',
                aboutPersonId TEXT,
                aboutName TEXT,
                detail TEXT,
                syncState TEXT NOT NULL DEFAULT 'Pending'
            )
            """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_team_events_orgId ON team_events (orgId)")
    }
}


/**
 * Money going out, and who is allowed to touch it.
 *
 * The two capability columns sit on the membership so the SERVER can read them.
 * A rule can only enforce what it can see, and what each person is paid is the
 * one figure in this app that cannot be un-seen once a driver has seen it.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memberships ADD COLUMN canPayOut INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE memberships ADD COLUMN canSeePeopleCost INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS payouts (
                id TEXT NOT NULL PRIMARY KEY,
                orgId TEXT NOT NULL,
                amountRupees INTEGER NOT NULL,
                atMillis INTEGER NOT NULL,
                covers TEXT NOT NULL,
                toPersonId TEXT,
                toName TEXT,
                headcount INTEGER NOT NULL DEFAULT 1,
                jobId TEXT,
                method TEXT,
                note TEXT,
                byPersonId TEXT,
                createdAt INTEGER NOT NULL,
                syncState TEXT NOT NULL DEFAULT 'Pending',
                deviceId TEXT
            )
            """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payouts_orgId ON payouts (orgId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payouts_jobId ON payouts (jobId)")
    }
}


/**
 * A rank between owner and driver.
 *
 * Not a role: the app has always worked in capabilities, and a role enum would
 * make every rule a three-way question for no gain. A manager is somebody with
 * these switched on, and the rules read the switches.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memberships ADD COLUMN canEditTeam INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE memberships ADD COLUMN canEditMachines INTEGER NOT NULL DEFAULT 0")
    }
}


/**
 * The job's own cost sheet, and the link that keeps a rupee counted once.
 *
 * Job-attached money used to live only in `payouts`, and a machine's people
 * cost was summed by joining payouts to jobs. Totalling `job_costs` as well
 * would count every settled cost twice, so the two have to be tied together in
 * the same breath they are separated: every existing job-attached payment
 * becomes a cost here, and points back at it.
 *
 * The derived id (`jc_` + the payment's own id) is deliberate. It makes the
 * copy idempotent and lets the UPDATE below find its partner without a join,
 * on a statement that has to be right the first time because there is no
 * second run of a migration.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `job_costs` (
                `id` TEXT NOT NULL,
                `orgId` TEXT NOT NULL,
                `jobId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `headcount` INTEGER,
                `amountRupees` INTEGER,
                `note` TEXT,
                `byPersonId` TEXT,
                `atMillis` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `syncState` TEXT NOT NULL,
                `deviceId` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_costs_orgId` ON `job_costs` (`orgId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_costs_jobId` ON `job_costs` (`jobId`)")

        db.execSQL("ALTER TABLE payouts ADD COLUMN jobCostId TEXT")

        // Every payment already laid against a job becomes that job's cost.
        // Labour for all of them: paying people is the only thing the screen
        // that wrote these could ever have been used for. A name, where there
        // is one, survives in the note rather than being thrown away.
        db.execSQL(
            """
            INSERT INTO job_costs
                (id, orgId, jobId, kind, headcount, amountRupees, note,
                 byPersonId, atMillis, createdAt, syncState, deviceId)
            SELECT 'jc_' || id, orgId, jobId, 'Labour', headcount, amountRupees,
                   COALESCE(note, toName), byPersonId, atMillis, createdAt,
                   'Pending', deviceId
              FROM payouts
             WHERE jobId IS NOT NULL
            """.trimIndent()
        )
        db.execSQL("UPDATE payouts SET jobCostId = 'jc_' || id WHERE jobId IS NOT NULL")
    }
}

/**
 * An index on the outbox's entity id.
 *
 * Every queued change now updates its row's sent state, and every accepted one
 * asks whether anything else is still queued for the same row ([SentState]).
 * Without this each of those questions reads the whole queue — fine at ten
 * changes, not at the few thousand a phone out of signal for a week can hold.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_entityId` ON `outbox` (`entityId`)")
    }
}

/**
 * What a machine really costs: derived hours, a private ownership record, and the
 * parts changed at a service.
 *
 * `hoursAsOf` is set to the machine's creation time for machines that exist now.
 * Whoever entered a reading did so at some point after that, so every job finished
 * since the machine was added is counted on top of it — which may count a few
 * hours twice for somebody who corrected the meter along the way, and counts none
 * of them for everybody else, as it did before this. Over-reading a service
 * reminder by a few hours is the safer way to be wrong than the old silence.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE machines ADD COLUMN hoursAsOf INTEGER")
        db.execSQL("UPDATE machines SET hoursAsOf = createdAt")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS machine_finance (
                id TEXT NOT NULL PRIMARY KEY,
                orgId TEXT NOT NULL,
                monthlyRupees INTEGER,
                hoursPerYear INTEGER,
                updatedAt INTEGER NOT NULL,
                syncState TEXT NOT NULL DEFAULT 'Pending'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_machine_finance_orgId` ON `machine_finance` (`orgId`)")

        db.execSQL("ALTER TABLE service_records ADD COLUMN partsNote TEXT")
    }
}

/**
 * When the business's seasons begin.
 *
 * Nullable with no default: a business that has never said keeps the usual June
 * and November, read by [com.example.fieldtrack.data.season.SeasonCalendar] as
 * "nothing set". Nothing is back-filled, so nobody's "this season" moves the day
 * they update, and a row written by an older build — which has no such column and
 * sends none — reads the same way.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE organisations ADD COLUMN seasons TEXT")
    }
}

/**
 * Bookings, who may make them, and when the plan's day turns over.
 *
 * `canPlanWork` starts as `canEditTeam`: whoever already runs the team was running
 * the yard, and planning its days is part of that. Nobody else gains it silently, and
 * nobody loses anything — no plan existed before.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bookings` (
                `id` TEXT NOT NULL,
                `orgId` TEXT NOT NULL,
                `customerId` TEXT NOT NULL,
                `fieldId` TEXT,
                `workType` TEXT NOT NULL,
                `day` INTEGER NOT NULL,
                `part` TEXT NOT NULL,
                `startMinute` INTEGER,
                `machineId` TEXT,
                `operatorPersonId` TEXT,
                `note` TEXT,
                `status` TEXT NOT NULL,
                `cancelReason` TEXT,
                `movedCount` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                `syncState` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookings_orgId` ON `bookings` (`orgId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookings_orgId_day` ON `bookings` (`orgId`, `day`)")
        db.execSQL("ALTER TABLE jobs ADD COLUMN bookingId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_bookingId` ON `jobs` (`bookingId`)")
        db.execSQL("ALTER TABLE organisations ADD COLUMN planDayStartHour INTEGER")
        db.execSQL("ALTER TABLE organisations ADD COLUMN driversSeeAll INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE memberships ADD COLUMN canPlanWork INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE memberships SET canPlanWork = canEditTeam")
    }
}

/** Every migration, in order. Add to this list and the builder picks it up. */
/**
 * Money moves from whole rupees to minor units, and a business gets a currency.
 *
 * Every amount is multiplied by 100 (a rupee is 100 paise) and the columns drop
 * their `Rupees` names for `Minor`. SQLite on the oldest supported phones cannot
 * rename a column, so each table that holds money is rebuilt: made again under its
 * new shape, filled from the old one with the amounts scaled, then swapped in.
 *
 * Three more things travel with it:
 *  - every business so far kept its books in rupees, so existing rows get `INR` and a
 *    whole-rupee rounding step, and each job is stamped with that step;
 *  - the price of each trip, and of a rate change, lives in a job event's decimal
 *    `numericValue` among litres and seconds, and only those three kinds of event are
 *    scaled;
 *  - changes queued on this phone and not yet sent carry the old field names, and are
 *    rewritten, or they would reach the server in the shape it no longer reads.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE organisations ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'INR'")
        db.execSQL("ALTER TABLE organisations ADD COLUMN moneyStepMinor INTEGER NOT NULL DEFAULT 100")

        rebuild(
            db,
            table = "payouts",
            create = "CREATE TABLE IF NOT EXISTS `payouts_new` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `atMillis` INTEGER NOT NULL, `covers` TEXT NOT NULL, `toPersonId` TEXT, `toName` TEXT, `headcount` INTEGER NOT NULL, `jobId` TEXT, `jobCostId` TEXT, `method` TEXT, `note` TEXT, `byPersonId` TEXT, `createdAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, `deviceId` TEXT, PRIMARY KEY(`id`))",
            indices = listOf(
            "CREATE INDEX IF NOT EXISTS `index_payouts_orgId` ON `payouts` (`orgId`)",
            "CREATE INDEX IF NOT EXISTS `index_payouts_jobId` ON `payouts` (`jobId`)"
            ),
            fromOld = mapOf("amountMinor" to "amountRupees"),
        )
        rebuild(
            db,
            table = "job_costs",
            create = "CREATE TABLE IF NOT EXISTS `job_costs_new` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `jobId` TEXT NOT NULL, `kind` TEXT NOT NULL, `headcount` INTEGER, `amountMinor` INTEGER, `note` TEXT, `byPersonId` TEXT, `atMillis` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, `deviceId` TEXT, PRIMARY KEY(`id`))",
            indices = listOf(
            "CREATE INDEX IF NOT EXISTS `index_job_costs_orgId` ON `job_costs` (`orgId`)",
            "CREATE INDEX IF NOT EXISTS `index_job_costs_jobId` ON `job_costs` (`jobId`)"
            ),
            fromOld = mapOf("amountMinor" to "amountRupees"),
        )
        rebuild(
            db,
            table = "machine_finance",
            create = "CREATE TABLE IF NOT EXISTS `machine_finance_new` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `monthlyMinor` INTEGER, `hoursPerYear` INTEGER, `updatedAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))",
            indices = listOf(
            "CREATE INDEX IF NOT EXISTS `index_machine_finance_orgId` ON `machine_finance` (`orgId`)"
            ),
            fromOld = mapOf("monthlyMinor" to "monthlyRupees"),
        )
        rebuild(
            db,
            table = "fuel_entries",
            create = "CREATE TABLE IF NOT EXISTS `fuel_entries_new` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `machineId` TEXT NOT NULL, `jobId` TEXT, `litres` REAL NOT NULL, `costMinor` INTEGER NOT NULL, `atMillis` INTEGER NOT NULL, `byPersonId` TEXT, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))",
            indices = listOf(
            "CREATE INDEX IF NOT EXISTS `index_fuel_entries_orgId` ON `fuel_entries` (`orgId`)",
            "CREATE INDEX IF NOT EXISTS `index_fuel_entries_machineId` ON `fuel_entries` (`machineId`)",
            "CREATE INDEX IF NOT EXISTS `index_fuel_entries_jobId` ON `fuel_entries` (`jobId`)"
            ),
            fromOld = mapOf("costMinor" to "costRupees"),
        )
        rebuild(
            db,
            table = "service_records",
            create = "CREATE TABLE IF NOT EXISTS `service_records_new` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `machineId` TEXT NOT NULL, `description` TEXT NOT NULL, `costMinor` INTEGER, `atMachineHours` REAL, `atMillis` INTEGER NOT NULL, `vendor` TEXT, `partsNote` TEXT, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))",
            indices = listOf(
            "CREATE INDEX IF NOT EXISTS `index_service_records_orgId` ON `service_records` (`orgId`)",
            "CREATE INDEX IF NOT EXISTS `index_service_records_machineId` ON `service_records` (`machineId`)"
            ),
            fromOld = mapOf("costMinor" to "costRupees"),
        )
        rebuild(
            db,
            table = "rates",
            create = "CREATE TABLE IF NOT EXISTS `rates_new` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `workType` TEXT NOT NULL, `billingMode` TEXT NOT NULL, `unit` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `customerId` TEXT, `label` TEXT, `updatedAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))",
            indices = listOf(
            "CREATE INDEX IF NOT EXISTS `index_rates_orgId` ON `rates` (`orgId`)",
            "CREATE INDEX IF NOT EXISTS `index_rates_orgId_workType` ON `rates` (`orgId`, `workType`)"
            ),
            fromOld = mapOf("amountMinor" to "amountRupees"),
        )
        rebuild(
            db,
            table = "jobs",
            create = "CREATE TABLE IF NOT EXISTS `jobs_new` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `customerId` TEXT NOT NULL, `fieldId` TEXT, `machineId` TEXT, `operatorPersonId` TEXT, `bookingId` TEXT, `workType` TEXT NOT NULL, `billingMode` TEXT NOT NULL, `unit` TEXT NOT NULL, `rateMinor` INTEGER NOT NULL, `stepMinor` INTEGER NOT NULL DEFAULT 100, `quantity` REAL NOT NULL, `tripTotalMinor` INTEGER NOT NULL, `elapsedSeconds` INTEGER NOT NULL, `distanceMetres` INTEGER, `status` TEXT NOT NULL, `workedAt` INTEGER NOT NULL, `recordedAt` INTEGER NOT NULL, `entryMethod` TEXT NOT NULL, `startedAt` INTEGER, `completedAt` INTEGER, `runningSince` INTEGER, `note` TEXT, `billFy` TEXT, `billSeq` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncState` TEXT NOT NULL, `lastWriterDeviceId` TEXT, `revision` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            indices = listOf(
            "CREATE INDEX IF NOT EXISTS `index_jobs_orgId` ON `jobs` (`orgId`)",
            "CREATE INDEX IF NOT EXISTS `index_jobs_customerId` ON `jobs` (`customerId`)",
            "CREATE INDEX IF NOT EXISTS `index_jobs_machineId` ON `jobs` (`machineId`)",
            "CREATE INDEX IF NOT EXISTS `index_jobs_operatorPersonId` ON `jobs` (`operatorPersonId`)",
            "CREATE INDEX IF NOT EXISTS `index_jobs_orgId_status` ON `jobs` (`orgId`, `status`)",
            "CREATE INDEX IF NOT EXISTS `index_jobs_orgId_workedAt` ON `jobs` (`orgId`, `workedAt`)",
            "CREATE INDEX IF NOT EXISTS `index_jobs_bookingId` ON `jobs` (`bookingId`)"
            ),
            fromOld = mapOf("rateMinor" to "rateRupees", "tripTotalMinor" to "tripTotalRupees", "stepMinor" to "100"),
        )
        rebuild(
            db,
            table = "ledger_entries",
            create = "CREATE TABLE IF NOT EXISTS `ledger_entries_new` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `customerId` TEXT NOT NULL, `kind` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `jobId` TEXT, `method` TEXT, `note` TEXT, `atMillis` INTEGER NOT NULL, `byPersonId` TEXT, `reversesEntryId` TEXT, `createdAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, `deviceId` TEXT, PRIMARY KEY(`id`))",
            indices = listOf(
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_orgId` ON `ledger_entries` (`orgId`)",
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_customerId` ON `ledger_entries` (`customerId`)",
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_jobId` ON `ledger_entries` (`jobId`)",
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_orgId_atMillis` ON `ledger_entries` (`orgId`, `atMillis`)"
            ),
            fromOld = mapOf("amountMinor" to "amountRupees"),
        )

        db.execSQL(
            "UPDATE job_events SET numericValue = numericValue * 100 " +
                "WHERE numericValue IS NOT NULL AND type IN ('TripAdded', 'TripRemoved', 'RateChanged')"
        )

        rewriteQueuedChanges(db)
    }

    /**
     * Makes [table] again in its new shape. [fromOld] names, for each new column that
     * is not a straight copy, the old column to scale by 100 (or, for a value that did
     * not exist before, the constant to start with).
     */
    private fun rebuild(
        db: SupportSQLiteDatabase,
        table: String,
        create: String,
        indices: List<String>,
        fromOld: Map<String, String>,
    ) {
        db.execSQL(create)
        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(`${table}_new`)").use { c ->
            while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
        }
        val select = columns.joinToString(", ") { column ->
            val old = fromOld[column]
            when {
                old == null -> "`$column`"
                old.all { it.isDigit() } -> old
                else -> "`$old` * 100"
            }
        }
        db.execSQL(
            "INSERT INTO `${table}_new` (${columns.joinToString(", ") { "`$it`" }}) " +
                "SELECT $select FROM `$table`"
        )
        db.execSQL("DROP TABLE `$table`")
        db.execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
        indices.forEach { db.execSQL(it) }
    }

    private fun rewriteQueuedChanges(db: SupportSQLiteDatabase) {
        val rows = mutableListOf<Triple<Long, String, String>>()
        db.query("SELECT seq, tableName, payloadJson FROM outbox").use { c ->
            while (c.moveToNext()) rows += Triple(c.getLong(0), c.getString(1), c.getString(2))
        }
        for ((seq, table, payload) in rows) {
            val parsed = runCatching { Json.parseToJsonElement(payload) }.getOrNull() as? JsonObject
                ?: continue
            val upgraded = LegacyMoney.upgrade(table, parsed, formatted = false)
            if (upgraded != parsed) {
                db.execSQL(
                    "UPDATE outbox SET payloadJson = ? WHERE seq = ?",
                    arrayOf(upgraded.toString(), seq),
                )
            }
        }
    }
}

/**
 * When the accounting year begins and which time zone the business counts its days in.
 *
 * Every business so far was Indian: a year from April, and a phone that knows the
 * zone. So the month is April, and the zone is left empty, which follows the phone
 * exactly as before.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE organisations ADD COLUMN fiscalStartMonth INTEGER NOT NULL DEFAULT 4")
        db.execSQL("ALTER TABLE organisations ADD COLUMN timeZoneId TEXT")
    }
}

/**
 * Which ways of paying a business offers, what to print under "Pay to", and an optional
 * tax line. All empty: a business offers what is usual for its currency, prints no
 * payment details, and shows no tax until it says so.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE organisations ADD COLUMN paymentMethods TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN payDetails TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN taxName TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN taxPercentBps INTEGER")
    }
}

/**
 * Terms a job can carry beyond its rate: a minimum, an arrival charge, who pays for the
 * diesel, waiting time, a discount; the implement and the extra hands on it; the token
 * taken at booking; and what an owner calls a machine the list has no name for.
 *
 * Everything starts empty, which bills exactly as before.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN minimumMinor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE jobs ADD COLUMN arrivalMinor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE jobs ADD COLUMN fuelTerms TEXT NOT NULL DEFAULT 'Included'")
        db.execSQL("ALTER TABLE jobs ADD COLUMN fuelBilledMinor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE jobs ADD COLUMN waitingRateMinor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE jobs ADD COLUMN waitingMinor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE jobs ADD COLUMN discountMinor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE jobs ADD COLUMN implementMachineId TEXT")
        db.execSQL("ALTER TABLE jobs ADD COLUMN coOperatorIds TEXT")
        db.execSQL("ALTER TABLE rates ADD COLUMN minimumMinor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rates ADD COLUMN arrivalMinor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rates ADD COLUMN fuelTerms TEXT NOT NULL DEFAULT 'Included'")
        db.execSQL("ALTER TABLE rates ADD COLUMN waitingRateMinor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bookings ADD COLUMN advanceMinor INTEGER")
        db.execSQL("ALTER TABLE machines ADD COLUMN typeLabel TEXT")
    }
}

/**
 * Getting paid and paying people: the language a customer reads, a credit limit, a day to
 * chase them, and how much of a driver's advance a payment settles. All empty, which
 * behaves exactly as before.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customers ADD COLUMN language TEXT")
        db.execSQL("ALTER TABLE customers ADD COLUMN creditLimitMinor INTEGER")
        db.execSQL("ALTER TABLE customers ADD COLUMN chaseOnDay INTEGER")
        db.execSQL("ALTER TABLE payouts ADD COLUMN recoversAdvanceMinor INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Which identity a person used before proving who they are, so a left-behind identity is
 * not listed as a second person on every other phone.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memberships ADD COLUMN replacesPersonId TEXT")
    }
}

/**
 * 22 to 23: the papers a machine carries, and what it is worth on the books.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `machine_papers` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, " +
                "`machineId` TEXT NOT NULL, `kind` TEXT NOT NULL, `number` TEXT, `expiresDay` INTEGER, " +
                "`note` TEXT, `photoId` TEXT, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "`syncState` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_machine_papers_orgId` ON `machine_papers` (`orgId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_machine_papers_machineId` ON `machine_papers` (`machineId`)")
        db.execSQL("ALTER TABLE machine_finance ADD COLUMN purchasedDay INTEGER")
        db.execSQL("ALTER TABLE machine_finance ADD COLUMN purchaseMinor INTEGER")
        db.execSQL("ALTER TABLE machine_finance ADD COLUMN resaleMinor INTEGER")
        db.execSQL("ALTER TABLE machine_finance ADD COLUMN lifeYears INTEGER")
    }
}

/**
 * 23 to 24: proof of work. The meter's reading, a mark of where the phone was, and a photo,
 * at the start and the finish of a job; and the business's switch for asking for them.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (column in listOf("meterStart REAL", "meterEnd REAL", "startLat REAL", "startLng REAL", "endLat REAL", "endLng REAL", "startPhotoId TEXT", "endPhotoId TEXT")) {
            db.execSQL("ALTER TABLE jobs ADD COLUMN $column")
        }
        db.execSQL("ALTER TABLE organisations ADD COLUMN proofOfWork INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 24 to 25: the booking link. The business's switch and secret, and the requests that come in.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE organisations ADD COLUMN bookingLink INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE organisations ADD COLUMN bookingSlug TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN bookingShowRates INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `booking_requests` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`phone` TEXT, `village` TEXT, `workType` TEXT, `day` INTEGER, `note` TEXT, `status` TEXT NOT NULL, " +
                "`bookingId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_booking_requests_orgId` ON `booking_requests` (`orgId`)")
    }
}

/**
 * 25 to 26: what a business has paid for. Empty for everybody until the server says otherwise,
 * which is the free plan.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `entitlements` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, `tier` TEXT NOT NULL, " +
                "`untilDay` INTEGER, `founding` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entitlements_orgId` ON `entitlements` (`orgId`)")
    }
}

/**
 * 26 to 27: a machine can belong to somebody else.
 *
 * Six optional columns on the private machine record, all empty: every machine already in
 * a phone is the business's own, which is exactly what empty means.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `machine_finance` ADD COLUMN `ownerKind` TEXT")
        db.execSQL("ALTER TABLE `machine_finance` ADD COLUMN `ownerPersonId` TEXT")
        db.execSQL("ALTER TABLE `machine_finance` ADD COLUMN `ownerName` TEXT")
        db.execSQL("ALTER TABLE `machine_finance` ADD COLUMN `hireBasis` TEXT")
        db.execSQL("ALTER TABLE `machine_finance` ADD COLUMN `hireRateMinor` INTEGER")
        db.execSQL("ALTER TABLE `machine_finance` ADD COLUMN `hireSharePercent` INTEGER")
        db.execSQL("ALTER TABLE `machine_finance` ADD COLUMN `hireFrom` INTEGER")
    }
}

/** 27 to 28: a payment can be taken back. Every payment already there stands. */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `payouts` ADD COLUMN `voidedAt` INTEGER")
    }
}

/** 28 to 29: an owner can make roles of their own. Nobody has made one yet. */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `organisations` ADD COLUMN `customRoles` TEXT")
    }
}

/**
 * Trust and money: who took a payment back, cash handed over, each person's particulars and
 * how they are paid.
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `payouts` ADD COLUMN `voidedByPersonId` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cash_handovers` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, " +
                "`fromPersonId` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `atMillis` INTEGER NOT NULL, " +
                "`byPersonId` TEXT, `note` TEXT, `voidedAt` INTEGER, `voidedByPersonId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, `deviceId` TEXT, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_handovers_orgId` ON `cash_handovers` (`orgId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_handovers_fromPersonId` ON `cash_handovers` (`fromPersonId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `member_details` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, " +
                "`personId` TEXT NOT NULL, `payoutUpi` TEXT, `payoutBank` TEXT, `licenceNumber` TEXT, " +
                "`licenceExpiresDay` INTEGER, `emergencyName` TEXT, `emergencyPhone` TEXT, " +
                "`updatedAt` INTEGER NOT NULL, `updatedByPersonId` TEXT, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_member_details_orgId` ON `member_details` (`orgId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `member_pay_terms` (`id` TEXT NOT NULL, `orgId` TEXT NOT NULL, " +
                "`personId` TEXT NOT NULL, `basis` TEXT NOT NULL, `rateMinor` INTEGER NOT NULL, " +
                "`fromMillis` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `updatedByPersonId` TEXT, " +
                "`syncState` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_member_pay_terms_orgId` ON `member_pay_terms` (`orgId`)")
    }
}

/** Every "Other" nameable: a paper's own name, a cancelled booking's own reason, a way of paying's own name. */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `machine_papers` ADD COLUMN `label` TEXT")
        db.execSQL("ALTER TABLE `bookings` ADD COLUMN `cancelNote` TEXT")
        db.execSQL("ALTER TABLE `ledger_entries` ADD COLUMN `methodLabel` TEXT")
        db.execSQL("ALTER TABLE `payouts` ADD COLUMN `methodLabel` TEXT")
    }
}

/** A business's own land unit, by its own name. */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `organisations` ADD COLUMN `ownLandUnit` TEXT")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
    MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32,
)