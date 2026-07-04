package com.example.fieldtrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.fieldtrack.data.local.dao.CustomerDao
import com.example.fieldtrack.data.local.dao.FieldDao
import com.example.fieldtrack.data.local.dao.JobDao
import com.example.fieldtrack.data.local.dao.MachineDao
import com.example.fieldtrack.data.local.entity.ActiveWorkEntity
import com.example.fieldtrack.data.local.entity.AuditLogEntity
import com.example.fieldtrack.data.local.entity.CompletionSheetEntity
import com.example.fieldtrack.data.local.entity.CustomerEntity
import com.example.fieldtrack.data.local.entity.DeviceEntity
import com.example.fieldtrack.data.local.entity.FieldEntity
import com.example.fieldtrack.data.local.entity.FuelEntryEntity
import com.example.fieldtrack.data.local.entity.BreakdownEntity
import com.example.fieldtrack.data.local.entity.InventoryItemEntity
import com.example.fieldtrack.data.local.entity.InviteEntity
import com.example.fieldtrack.data.local.entity.JobEntity
import com.example.fieldtrack.data.local.entity.JobEventEntity
import com.example.fieldtrack.data.local.entity.MachineEntity
import com.example.fieldtrack.data.local.entity.MaintenanceEntity
import com.example.fieldtrack.data.local.entity.OrganizationEntity
import com.example.fieldtrack.data.local.entity.PaymentEntity
import com.example.fieldtrack.data.local.entity.TeamMemberEntity
import com.example.fieldtrack.data.local.entity.VendorEntity
import com.example.fieldtrack.data.local.entity.AIInsightEntity
import com.example.fieldtrack.data.local.dao.MaintenanceDao
import com.example.fieldtrack.data.local.dao.FuelDao
import com.example.fieldtrack.data.local.dao.BreakdownDao
import com.example.fieldtrack.data.local.dao.VendorDao
import com.example.fieldtrack.data.local.dao.InventoryDao
import com.example.fieldtrack.data.local.dao.AIInsightDao

@Database(
    entities = [
        OrganizationEntity::class,
        CustomerEntity::class,
        MachineEntity::class,
        TeamMemberEntity::class,
        JobEntity::class,
        FieldEntity::class,
        ActiveWorkEntity::class,
        CompletionSheetEntity::class,
        PaymentEntity::class,
        JobEventEntity::class,
        InviteEntity::class,
        AuditLogEntity::class,
        DeviceEntity::class,
        MaintenanceEntity::class,
        FuelEntryEntity::class,
        BreakdownEntity::class,
        VendorEntity::class,
        InventoryItemEntity::class,
        AIInsightEntity::class
    ], 
    version = 12, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun jobDao(): JobDao
    abstract fun machineDao(): MachineDao
    abstract fun fieldDao(): FieldDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun fuelDao(): FuelDao
    abstract fun breakdownDao(): BreakdownDao
    abstract fun vendorDao(): VendorDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun aiInsightDao(): AIInsightDao
}
