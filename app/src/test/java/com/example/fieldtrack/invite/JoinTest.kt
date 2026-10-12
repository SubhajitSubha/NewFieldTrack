package com.example.fieldtrack.invite

import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.InviteCodeDoc
import com.example.fieldtrack.data.sync.InviteLookup
import com.example.fieldtrack.data.sync.InviteLookupResult
import com.example.fieldtrack.feature.join.JoinProblem
import com.example.fieldtrack.feature.join.JoinStep
import com.example.fieldtrack.feature.join.JoinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val VALID = InviteCodeDoc(
    code = "3D9ADF",
    orgId = "org-1",
    orgName = "Singh Agro Services",
    inviteId = "inv-1",
    role = MemberRole.Operator,
    canSeeMoney = false,
    expiresAt = Long.MAX_VALUE,
)

private class FakeLookup(var answer: (String) -> InviteLookupResult) : InviteLookup {
    val asked = mutableListOf<String>()
    override suspend fun find(code: String): InviteLookupResult {
        asked += code
        return answer(code)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class JoinTest {

    private val dispatcher = StandardTestDispatcher()
    private val joined = mutableListOf<Pair<InviteCodeDoc, String>>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher); joined.clear() }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        lookup: InviteLookup,
        name: String = "",
    ) = JoinViewModel(lookup, name) { invite, who -> joined += invite to who }

    @Test
    fun `a good code shows the business before anything is committed`() = runTest(dispatcher) {
        val vm = viewModel(FakeLookup { InviteLookupResult.Found(VALID) })
        vm.setCode("3D9ADF")

        vm.check()
        advanceUntilIdle()

        assertEquals(JoinStep.Preview, vm.state.value.step)
        assertEquals("Singh Agro Services", vm.state.value.invite?.orgName)
        assertTrue("nothing is joined until they say so", joined.isEmpty())
    }

    @Test
    fun `the code is tidied as it is typed`() = runTest(dispatcher) {
        val vm = viewModel(FakeLookup { InviteLookupResult.Found(VALID) })

        vm.setCode("3d9-adf")

        assertEquals("3D9ADF", vm.state.value.code)
        assertTrue(vm.state.value.canCheck)
    }

    @Test
    fun `an O typed for a zero still finds the business`() = runTest(dispatcher) {
        val lookup = FakeLookup { InviteLookupResult.Found(VALID) }
        val vm = viewModel(lookup)

        vm.setCode("O3D9AD")

        assertEquals("03D9AD", vm.state.value.code)
    }

    @Test
    fun `checking does nothing until the code could be real`() = runTest(dispatcher) {
        val lookup = FakeLookup { InviteLookupResult.Found(VALID) }
        val vm = viewModel(lookup)
        vm.setCode("3D9")

        assertFalse(vm.state.value.canCheck)
        vm.check()
        advanceUntilIdle()

        assertTrue("no pointless round trip for an impossible code", lookup.asked.isEmpty())
    }

    @Test
    fun `a code nobody recognises says so plainly`() = runTest(dispatcher) {
        val vm = viewModel(FakeLookup { InviteLookupResult.NotFound })
        vm.setCode("3D9ADF")

        vm.check()
        advanceUntilIdle()

        assertEquals(JoinProblem.NotFound, vm.state.value.problem)
        assertEquals(JoinStep.Code, vm.state.value.step)
    }

    @Test
    fun `a lapsed code is told apart from one already used`() = runTest(dispatcher) {
        val lapsed = VALID.copy(expiresAt = 1L)
        val vm = viewModel(FakeLookup { InviteLookupResult.NoLongerValid(lapsed) })
        vm.setCode("3D9ADF")
        vm.check()
        advanceUntilIdle()
        assertEquals(JoinProblem.Expired, vm.state.value.problem)

        val used = VALID.copy(redeemedAt = 123L)
        val vm2 = viewModel(FakeLookup { InviteLookupResult.NoLongerValid(used) })
        vm2.setCode("3D9ADF")
        vm2.check()
        advanceUntilIdle()
        assertEquals(JoinProblem.AlreadyUsed, vm2.state.value.problem)
    }

    @Test
    fun `no signal is not the same as a bad code`() = runTest(dispatcher) {
        val vm = viewModel(FakeLookup { InviteLookupResult.Unreachable("no network") })
        vm.setCode("3D9ADF")

        vm.check()
        advanceUntilIdle()

        assertEquals(
            "telling somebody their code is wrong when it is the network is cruel",
            JoinProblem.Unreachable,
            vm.state.value.problem,
        )
        assertTrue("and they must be able to try again", vm.state.value.canCheck)
    }

    @Test
    fun `joining passes the driver's own name and the invite`() = runTest(dispatcher) {
        val vm = viewModel(FakeLookup { InviteLookupResult.Found(VALID) })
        vm.setCode("3D9ADF")
        vm.check()
        advanceUntilIdle()

        vm.setName("Jaswant Kumar")
        vm.join()
        advanceUntilIdle()

        assertEquals(1, joined.size)
        assertEquals("Jaswant Kumar", joined.single().second)
        assertEquals("org-1", joined.single().first.orgId)
    }

    @Test
    fun `joining without a name does nothing`() = runTest(dispatcher) {
        val vm = viewModel(FakeLookup { InviteLookupResult.Found(VALID) })
        vm.setCode("3D9ADF")
        vm.check()
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()

        assertTrue(joined.isEmpty())
    }

    @Test
    fun `going back keeps the code so one wrong character can be fixed`() = runTest(dispatcher) {
        val vm = viewModel(FakeLookup { InviteLookupResult.Found(VALID) })
        vm.setCode("3D9ADF")
        vm.check()
        advanceUntilIdle()

        vm.back()

        assertEquals(JoinStep.Code, vm.state.value.step)
        assertEquals("3D9ADF", vm.state.value.code)
        assertNull(vm.state.value.invite)
    }

    @Test
    fun `a failure while joining does not strand the screen busy`() = runTest(dispatcher) {
        val vm = JoinViewModel(
            FakeLookup { InviteLookupResult.Found(VALID) }, "",
        ) { _, _ -> throw IllegalStateException("write failed") }
        vm.setCode("3D9ADF")
        vm.check()
        advanceUntilIdle()
        vm.setName("Jaswant")

        vm.join()
        advanceUntilIdle()

        assertFalse(vm.state.value.busy)
        assertTrue("and they can try again", vm.state.value.canJoin)
    }

    @Test
    fun `the name they signed up with is offered first`() = runTest(dispatcher) {
        val vm = viewModel(FakeLookup { InviteLookupResult.Found(VALID) }, name = "Jaswant")

        assertEquals("Jaswant", vm.state.value.name)
    }
}
