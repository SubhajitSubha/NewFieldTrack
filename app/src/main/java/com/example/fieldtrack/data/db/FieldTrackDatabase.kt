package com.example.fieldtrack.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.fieldtrack.data.db.dao.CustomerDao
import com.example.fieldtrack.data.db.dao.JobDao
import com.example.fieldtrack.data.db.dao.LedgerDao
import com.example.fieldtrack.data.db.dao.MachineDao
import com.example.fieldtrack.data.db.dao.OrgDao
import com.example.fieldtrack.data.db.dao.OutboxDao
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.FieldEntity
import com.example.fieldtrack.data.db.entity.FuelEntryEntity
import com.example.fieldtrack.data.db.entity.InviteEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.JobEventEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.OutboxEntity
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.data.db.entity.ServiceRecordEntity

/**
 * The local database — and the app's source of truth.
 *
 * Nothing in the UI reads from the network. Every screen observes this database, and
 * a background worker reconciles it with the server when there is signal. That is the
 * only arrangement that works when the person using the app is standing in a field
 * with no bars, which is most of the time.
 *
 * Schemas are exported to app/schemas so migrations can be written and tested against
 * a real previous version rather than from memory.
 */
@Database(
    entities = [
        OrgEntity::class,
        MembershipEntity::class,
        InviteEntity::class,
        CustomerEntity::class,
        FieldEntity::class,
        MachineEntity::class,
        FuelEntryEntity::class,
        ServiceRecordEntity::class,
        BreakdownEntity::class,
        RateEntity::class,
        JobEntity::class,
        JobEventEntity::class,
        LedgerEntryEntity::class,
        OutboxEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FieldTrackDatabase : RoomDatabase() {

    abstract fun orgDao(): OrgDao
    abstract fun customerDao(): CustomerDao
    abstract fun machineDao(): MachineDao
    abstract fun jobDao(): JobDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        private const val NAME = "fieldtrack.db"

        fun build(context: Context): FieldTrackDatabase =
            Room.databaseBuilder(context.applicationContext, FieldTrackDatabase::class.java, NAME)
                // No fallbackToDestructiveMigration. This database holds work that was
                // done and money that was collected; losing it to a schema change is
                // not an acceptable failure mode, so a missing migration must break
                // the build in review rather than wipe a contractor's season.
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
