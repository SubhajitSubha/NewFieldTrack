package com.example.fieldtrack.data.repository

class SessionRepository(vararg args: Any?)
class UserRepository(vararg args: Any?)
class PaymentRepository(vararg args: Any?)
class ActiveJobsRepository(vararg args: Any?)
class ActiveJobRepository(vararg args: Any?)
class MachineRepository(vararg args: Any?)
class CustomBrandModelRepository(vararg args: Any?)
class FirestoreRepository(vararg args: Any?)
class JobRepository(vararg args: Any?)
class CustomerRepository(vararg args: Any?)

class CustomerFieldDao(vararg args: Any?)

fun Any.paymentDao(): Any? = null
fun Any.jobDao(): Any? = null
fun Any.organizationDao(): Any? = null
fun Any.organizationMemberDao(): Any? = null
fun Any.organizationInviteDao(): Any? = null
fun Any.userDao(): Any? = null
fun Any.sessionDao(): Any? = null
fun Any.activeJobsDao(): Any? = null
fun Any.activeJobDao(): Any? = null
fun Any.machineDao(): Any? = null
fun Any.customBrandDao(): Any? = null
fun Any.customModelDao(): Any? = null
fun Any.customerFieldDao(): CustomerFieldDao? = null
fun Any.customerDao(): Any? = null





