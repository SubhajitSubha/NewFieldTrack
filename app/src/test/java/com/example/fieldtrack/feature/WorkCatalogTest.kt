package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.dao.LastCharge
import com.example.fieldtrack.data.db.dao.WorkUse
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.data.repository.WorkHistory
import com.example.fieldtrack.feature.work.SuggestedRates
import com.example.fieldtrack.feature.work.WorkPick
import com.example.fieldtrack.feature.work.chargeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The work list follows the machine, and the tractor and its implement are recorded together.
 *
 * Pure rules, no screen: what comes first for a rotavator, which implement is filled in for a
 * tractor, who pulled a tool, how a typed name becomes a work, and which rate leads.
 */
class WorkCatalogTest {

    private fun machine(id: String, kind: MachineKind, status: MachineStatus = MachineStatus.Idle) =
        MachineEntity(id = id, orgId = "org", name = id, kind = kind, status = status, createdAt = 0, updatedAt = 0)

    private val tractor = machine("Swaraj", MachineKind.Tractor)
    private val rotavator = machine("Rotavator 7ft", MachineKind.Rotavator)
    private val trolley = machine("Trolley", MachineKind.Trolley)
    private val combine = machine("Kartar", MachineKind.Harvester)
    private val fleet = listOf(tractor, rotavator, trolley, combine)

    @Test
    fun `a harvester offers harvesting first and keeps everything else one tap away`() {
        val menu = WorkCatalog.menu(combine, null, fleet, WorkHistory())
        assertEquals(listOf("Harvesting"), menu.usual.map { it.key })
        assertTrue("nothing is ever missing, only further down", menu.rest.any { it.key == "Hauling" })
        assertEquals("Other is always last", WorkCatalog.OTHER, menu.rest.last().key)
    }

    @Test
    fun `a tractor offers what the business's tools let it do, then its own list`() {
        val menu = WorkCatalog.menu(tractor, null, fleet, WorkHistory())
        val usual = menu.usual.map { it.key }
        assertTrue(usual.indexOf("Rotavator") < usual.indexOf("Sowing"))
        assertTrue("a trolley means hauling", "Hauling" in usual)
        assertTrue("the tractor on hire for the day", "MachineHire" in usual)
    }

    @Test
    fun `what the machine has really done comes before what such a machine is for`() {
        val history = WorkHistory.of(
            use = listOf(
                WorkUse("Swaraj", "Hauling", jobs = 9, lastAt = 5),
                WorkUse("Swaraj", "Ploughing", jobs = 2, lastAt = 9),
            ),
            last = emptyList(),
            names = listOf("Hauling", "Ploughing"),
        )
        val menu = WorkCatalog.menu(tractor, null, fleet, history)
        assertEquals("Hauling", menu.usual.first().key)
        assertEquals("Ploughing", menu.usual[1].key)
    }

    @Test
    fun `the tool on the back puts its own work first, whatever the tractor usually does`() {
        val history = WorkHistory.of(listOf(WorkUse("Swaraj", "Hauling", 9, 5)), emptyList(), emptyList())
        val menu = WorkCatalog.menu(tractor, rotavator, fleet, history)
        assertEquals("Rotavator", menu.usual.first().key)
    }

    @Test
    fun `the business's own names are offered beside ours, before Other`() {
        val history = WorkHistory.of(emptyList(), emptyList(), listOf("Rotavator", "Stubble burning ban clearing"))
        val all = WorkCatalog.all(history.names).map { it.key }
        assertTrue("Stubble burning ban clearing" in all)
        assertEquals(WorkCatalog.OTHER, all.last())
        val own = WorkCatalog.option("Stubble burning ban clearing")
        assertEquals("shown by its own name", "Stubble burning ban clearing", own.name)
        assertEquals("charged any way at all", WorkCatalog.other.billingModes, own.billingModes)
    }

    @Test
    fun `a job done on the implement counts once in the business's usual work`() {
        val history = WorkHistory.of(
            use = listOf(
                WorkUse("Swaraj", "Rotavator", 3, 1),
                WorkUse("Rotavator 7ft", "Rotavator", 3, 1, pulled = true),
                WorkUse("Kartar", "Harvesting", 4, 2),
            ),
            last = emptyList(),
            names = emptyList(),
        )
        assertEquals(listOf("Harvesting", "Rotavator"), history.overall)
        assertEquals("the rotavator's own history", listOf("Rotavator"), history.doneOn("Rotavator 7ft"))
    }

    @Test
    fun `a tractor going out for rotavator work takes the one rotavator`() {
        assertEquals(rotavator, WorkCatalog.autoPartner(tractor, "Rotavator", fleet, now = true))
        assertNull("nothing is guessed for work any tool could do", WorkCatalog.autoPartner(tractor, "MachineHire", fleet, now = true))
        val busy = fleet.map { if (it.id == rotavator.id) it.copy(status = MachineStatus.Working) else it }
        assertNull("a rotavator out on other work is not this one", WorkCatalog.autoPartner(tractor, "Rotavator", busy, now = true))
        assertEquals("typed in afterwards, being busy now says nothing", rotavator.id,
            WorkCatalog.autoPartner(tractor, "Rotavator", busy, now = false)?.id)
    }

    @Test
    fun `a rotavator picked on its own is pulled by the one tractor, or by a farmer's own`() {
        assertTrue(WorkCatalog.asksPuller(rotavator))
        assertEquals(tractor, WorkCatalog.autoPartner(rotavator, "Rotavator", fleet, now = true))
        assertEquals(listOf(tractor), WorkCatalog.partners(rotavator, "Rotavator", fleet))
        val sprayer = machine("Boom", MachineKind.Sprayer)
        assertNull("a sprayer may have its own engine", WorkCatalog.autoPartner(sprayer, "Spraying", fleet + sprayer, now = true))
        assertTrue("a combine pulls nothing and is pulled by nothing", WorkCatalog.partners(combine, "Harvesting", fleet).isEmpty())
    }

    @Test
    fun `whichever was picked first, the job is the tractor with the tool behind it`() {
        val fromTractor = WorkPick(machines = fleet, now = false).pickMachine(tractor.id).pickWork(WorkCatalog.option("Rotavator"))
        assertEquals(tractor.id, fromTractor.jobMachineId)
        assertEquals(rotavator.id, fromTractor.implementId)

        val fromTool = WorkPick(machines = fleet, now = false).pickMachine(rotavator.id)
        assertEquals("the rotavator's work", "Rotavator", fromTool.work.key)
        assertEquals(tractor.id, fromTool.jobMachineId)
        assertEquals(rotavator.id, fromTool.implementId)

        val farmersTractor = fromTool.pickPartner(null)
        assertEquals("behind the farmer's own tractor, the job is the rotavator's", rotavator.id, farmersTractor.jobMachineId)
        assertNull(farmersTractor.implementId)
    }

    @Test
    fun `the work follows the machine until the person picks it, and is theirs after`() {
        val start = WorkPick(machines = fleet)
        val combinePicked = start.pickMachine(combine.id)
        assertEquals("Harvesting", combinePicked.work.key)
        val chosen = combinePicked.pickWork(WorkCatalog.option("Hauling"))
        assertEquals("their choice stays when the machine changes", "Hauling", chosen.pickMachine(tractor.id).work.key)
    }

    @Test
    fun `a tool filled in by itself does not move the card the person just tapped`() {
        val pick = WorkPick(machines = fleet).pickMachine(tractor.id)
        val before = pick.menu.usual.map { it.key }
        val after = pick.pickWork(WorkCatalog.option("Rotavator"))
        assertEquals(rotavator.id, after.partnerId)
        assertEquals(before, after.menu.usual.map { it.key })
    }

    @Test
    fun `a tool put on by hand comes off when the work cannot use it`() {
        val pick = WorkPick(machines = fleet).pickMachine(tractor.id).pickPartner(trolley.id)
        assertEquals("a trolley on the back means hauling", "Hauling", pick.work.key)
        val ploughing = pick.pickWork(WorkCatalog.option("Ploughing"))
        assertNull("no trolley stays on for ploughing", ploughing.partnerId)
    }

    @Test
    fun `a typed name becomes ours when it is ours, keeps its first spelling, and empty is Other`() {
        val labels = mapOf("Rotavator" to "Rotavator", "Puddling" to "कद्दू (धान का खेत)")
        fun label(o: WorkTypeOption) = labels[o.key] ?: o.key
        assertEquals(WorkCatalog.OTHER, WorkCatalog.keyForName("   ", emptyList(), ::label))
        assertEquals("Rotavator", WorkCatalog.keyForName("rotavator", emptyList(), ::label))
        assertEquals("Puddling", WorkCatalog.keyForName("कद्दू (धान का खेत)", emptyList(), ::label))
        assertEquals("spaces and case do not start a second one", "Bund repair",
            WorkCatalog.keyForName("bund  REPAIR", listOf("Bund repair"), ::label))
        assertEquals("a new name is kept as typed", "Bund repair", WorkCatalog.keyForName(" Bund repair ", emptyList(), ::label))
        assertEquals(WorkCatalog.MAX_NAME, WorkCatalog.keyForName("x".repeat(90), emptyList(), ::label).length)
    }

    @Test
    fun `a work is charged the way the business last charged it`() {
        val history = WorkHistory.of(
            emptyList(),
            listOf(LastCharge("Rotavator", BillingMode.PerHour, MeasureUnit.Hour, 1)),
            emptyList(),
        )
        assertEquals(BillingMode.PerHour to MeasureUnit.Hour, chargeFor(WorkCatalog.option("Rotavator"), history, MeasureUnit.Bigha))
        assertEquals(
            "with nothing before, by the area in the business's own unit",
            BillingMode.PerAcre to MeasureUnit.Bigha,
            chargeFor(WorkCatalog.option("Levelling"), history, MeasureUnit.Bigha),
        )
        val oldAcres = WorkHistory.of(emptyList(), listOf(LastCharge("Levelling", BillingMode.PerAcre, MeasureUnit.Acre, 1)), emptyList())
        assertEquals(
            "land in what the business measures in now",
            BillingMode.PerAcre to MeasureUnit.Bigha,
            chargeFor(WorkCatalog.option("Levelling"), oldAcres, MeasureUnit.Bigha),
        )
    }

    private fun rate(amount: Long, customer: String? = null, label: String? = null) = RateEntity(
        id = "r$amount", orgId = "org", workType = "Harvesting", billingMode = BillingMode.PerAcre,
        unit = MeasureUnit.Acre, amountMinor = amount, customerId = customer, label = label, updatedAt = 0,
    )

    @Test
    fun `the machine's own rate leads, after this customer's own price`() {
        val card = listOf(rate(1_800_00))
        val onMachine = SuggestedRates.order(card, 2_200_00, machineRateForCustomer = false, machineName = "Big combine")
        assertEquals(listOf(2_200_00L, 1_800_00L), onMachine.map { it.amountMinor })
        assertEquals("Big combine", onMachine.first().label)

        val withDeal = listOf(rate(2_000_00, customer = "ramesh"), rate(1_800_00))
        assertEquals(
            "what Ramesh agreed beats what the machine went out at for others",
            listOf(2_000_00L, 2_200_00L, 1_800_00L),
            SuggestedRates.order(withDeal, 2_200_00, machineRateForCustomer = false, machineName = "Big combine").map { it.amountMinor },
        )
        assertEquals(
            "what Ramesh paid on this machine beats everything",
            2_100_00L,
            SuggestedRates.order(withDeal, 2_100_00, machineRateForCustomer = true, machineName = null).first().amountMinor,
        )
        assertEquals(
            "the same figure is one chip, moved up",
            listOf(1_800_00L),
            SuggestedRates.order(card, 1_800_00, machineRateForCustomer = false, machineName = "Big combine").map { it.amountMinor },
        )
    }
}
