package com.example.fieldtrack.data.local.database

class FieldFlowDatabase {
    companion object {
        fun getInstance(context: Any): FieldFlowDatabase = FieldFlowDatabase()
    }

    fun paymentDao(): Any? = null
    fun jobDao(): Any? = null
    fun organizationDao(): Any? = null
    fun organizationMemberDao(): Any? = null
    fun organizationInviteDao(): Any? = null
    fun userDao(): Any? = null
    fun sessionDao(): Any? = null
    fun activeJobsDao(): Any? = null
    fun activeJobDao(): Any? = null
    fun machineDao(): Any? = null
    fun customBrandDao(): Any? = null
    fun customModelDao(): Any? = null
    fun customerDao(): Any? = null
    fun customerFieldDao(): com.example.fieldtrack.data.local.dao.CustomerFieldDao = com.example.fieldtrack.data.local.dao.CustomerFieldDao()
    fun maintenanceDao(): Any? = null
    fun fuelDao(): Any? = null
    fun breakdownDao(): Any? = null
    fun vendorDao(): Any? = null
    fun inventoryDao(): Any? = null
    fun aiInsightDao(): Any? = null
}
