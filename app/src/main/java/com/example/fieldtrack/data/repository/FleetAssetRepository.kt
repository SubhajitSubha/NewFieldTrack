package com.example.fieldtrack.data.repository

import com.example.fieldtrack.data.local.dao.MaintenanceDao
import com.example.fieldtrack.data.local.dao.FuelDao
import com.example.fieldtrack.data.local.dao.BreakdownDao
import com.example.fieldtrack.data.local.dao.VendorDao
import com.example.fieldtrack.data.local.dao.InventoryDao
import com.example.fieldtrack.data.local.dao.MachineDao
import com.example.fieldtrack.data.local.entity.MaintenanceEntity
import com.example.fieldtrack.data.local.entity.FuelEntryEntity
import com.example.fieldtrack.data.local.entity.BreakdownEntity
import com.example.fieldtrack.data.local.entity.VendorEntity
import com.example.fieldtrack.data.local.entity.InventoryItemEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class FleetAssetRepository(
    private val machineDao: MachineDao,
    private val maintenanceDao: MaintenanceDao,
    private val fuelDao: FuelDao,
    private val breakdownDao: BreakdownDao,
    private val vendorDao: VendorDao,
    private val inventoryDao: InventoryDao
) {
    suspend fun recordMaintenance(entity: MaintenanceEntity) {
        maintenanceDao.insertMaintenance(entity)
    }

    fun getMaintenanceByMachine(machineId: String): Flow<List<MaintenanceEntity>> {
        return maintenanceDao.getMaintenanceByMachine(machineId)
    }

    suspend fun recordFuel(entity: FuelEntryEntity) {
        fuelDao.insertFuelEntry(entity)
    }

    fun getFuelEntriesByMachine(machineId: String): Flow<List<FuelEntryEntity>> {
        return fuelDao.getFuelEntriesByMachine(machineId)
    }

    suspend fun recordBreakdown(entity: BreakdownEntity) {
        breakdownDao.insertBreakdown(entity)
    }

    fun getBreakdownsByMachine(machineId: String): Flow<List<BreakdownEntity>> {
        return breakdownDao.getBreakdownsByMachine(machineId)
    }

    suspend fun addVendor(entity: VendorEntity) {
        vendorDao.insertVendor(entity)
    }

    fun getVendorsByOrg(orgId: String): Flow<List<VendorEntity>> {
        return vendorDao.getVendorsByOrg(orgId)
    }

    suspend fun addInventoryItem(entity: InventoryItemEntity) {
        inventoryDao.insertInventoryItem(entity)
    }

    fun getInventoryByOrg(orgId: String): Flow<List<InventoryItemEntity>> {
        return inventoryDao.getInventoryItemsByOrg(orgId)
    }
}
