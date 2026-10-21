package com.example.fieldtrack.auth

import com.example.fieldtrack.data.auth.AuthFailure
import com.example.fieldtrack.data.auth.AuthResult
import com.example.fieldtrack.data.auth.PhoneAuth
import com.example.fieldtrack.data.auth.SignedIn
import com.example.fieldtrack.data.auth.formatIndianPhone
import com.example.fieldtrack.data.auth.isPlausibleIndianMobile
import com.example.fieldtrack.data.auth.toE164India
import com.example.fieldtrack.feature.signin.SignInStep
import com.example.fieldtrack.feature.signin.SignInViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import com.example.fieldtrack.data.auth.indianMobileDigits

/** A phone provider we script, so no Firebase and no Activity are involved. */
private class FakePhoneAuth : PhoneAuth {
    var onSend: (String, Any?) -> AuthResult = { _, _ ->
        AuthResult.CodeSent("verification-1", "resend-token-1")
    }
    var onVerify: (String, String) -> AuthResult = { _, code ->
        if (code == "123456") AuthResult.Verified(SignedIn("uid-1", "+919876543210"))
        else AuthResult.Failed(AuthFailure.WrongCode, "invalid code")
    }

    val sent = mutableListOf<Pair<String, Any?>>()
    var signedOut = false

    override fun current(): SignedIn? = null
    override suspend fun sendCode(phoneE164: String, resendToken: Any?): AuthResult {
        sent += phoneE164 to resendToken
        return onSend(phoneE164, resendToken)
    }
    override suspend fun verify(verificationId: String, code: String): AuthResult =
        onVerify(verificationId, code)
    override suspend fun signOut() { signedOut = true }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SignInTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var auth: FakePhoneAuth
    private val signedIn = mutableListOf<SignedIn>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        auth = FakePhoneAuth()
        signedIn.clear()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SignInViewModel(auth) { signedIn += it }

    // --------------------------------------------------------- what counts as a number

    @Test
    fun `an Indian mobile is ten digits starting six to nine`() {
        assertTrue(isPlausibleIndianMobile("9876543210"))
        assertTrue(isPlausibleIndianMobile("6000000000"))
        assertFalse("landline-style prefixes are not mobiles", isPlausibleIndianMobile("5876543210"))
        assertFalse("too short", isPlausibleIndianMobile("987654321"))
        assertFalse("too long", isPlausibleIndianMobile("98765432101"))
        assertFalse(isPlausibleIndianMobile(""))
    }

    @Test
    fun `numbers are sent in the form the provider wants and shown in the form people read`() {
        assertEquals("+919876543210", toE164India("9876543210"))
        assertEquals("+91 98765 43210", formatIndianPhone("+919876543210"))
    }

    @Test
    fun `typing strips everything that is not a digit`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("98765-43210")

        assertEquals("9876543210", vm.state.value.digits)
    }

    @Test
    fun `a number pasted with its country code is not mangled`() = runTest(dispatcher) {
        val vm = viewModel()
        // How it is written on a visiting card, into a field that already says +91.
        vm.setDigits("+91 98765 43210")

        assertEquals(
            "taking the first ten digits would send the code to 9198765432",
            "9876543210",
            vm.state.value.digits,
        )
        assertTrue(vm.state.value.canSendCode)
    }

    @Test
    fun `a number dialled with a leading zero is understood`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("09876543210")

        assertEquals("9876543210", vm.state.value.digits)
    }

    @Test
    fun `a number longer than ten digits stops at ten`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("98765432109999")

        assertEquals("9876543210", vm.state.value.digits)
        assertTrue(vm.state.value.canSendCode)
    }

    @Test
    fun `the send button stays off until the number could be real`() = runTest(dispatcher) {
        val vm = viewModel()
        assertFalse(vm.state.value.canSendCode)

        vm.setDigits("98765")
        assertFalse(vm.state.value.canSendCode)

        vm.setDigits("9876543210")
        assertTrue(vm.state.value.canSendCode)
    }

    // ------------------------------------------------------------------ the happy path

    @Test
    fun `asking for a code moves to the code step`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("9876543210")

        vm.sendCode()
        advanceUntilIdle()

        assertEquals(SignInStep.Code, vm.state.value.step)
        assertEquals("+919876543210", auth.sent.single().first)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `the right code signs them in`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("9876543210")
        vm.sendCode()
        advanceUntilIdle()

        vm.setCode("123456")
        vm.verify()
        advanceUntilIdle()

        assertEquals(1, signedIn.size)
        assertEquals("uid-1", signedIn.single().uid)
        assertEquals("+919876543210", signedIn.single().phone)
    }

    @Test
    fun `a handset that reads the SMS itself skips the code step entirely`() = runTest(dispatcher) {
        auth.onSend = { _, _ -> AuthResult.Verified(SignedIn("uid-auto", "+919876543210")) }
        val vm = viewModel()
        vm.setDigits("9876543210")

        vm.sendCode()
        advanceUntilIdle()

        assertEquals("signed in without anybody typing", 1, signedIn.size)
        assertEquals("uid-auto", signedIn.single().uid)
    }

    // ------------------------------------------------------------------ when it goes wrong

    @Test
    fun `a wrong code clears the box and says so`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("9876543210")
        vm.sendCode()
        advanceUntilIdle()

        vm.setCode("000000")
        vm.verify()
        advanceUntilIdle()

        assertEquals(AuthFailure.WrongCode, vm.state.value.problem)
        assertEquals("the box is cleared to retype, not left wrong", "", vm.state.value.code)
        assertEquals(SignInStep.Code, vm.state.value.step)
        assertTrue(signedIn.isEmpty())
    }

    @Test
    fun `a refused number sends them back to correct it`() = runTest(dispatcher) {
        auth.onSend = { _, _ -> AuthResult.Failed(AuthFailure.BadNumber, "invalid phone number") }
        val vm = viewModel()
        vm.setDigits("9876543210")

        vm.sendCode()
        advanceUntilIdle()

        assertEquals(SignInStep.Phone, vm.state.value.step)
        assertEquals(AuthFailure.BadNumber, vm.state.value.problem)
    }

    @Test
    fun `an expired code keeps them on the code step to ask for another`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("9876543210")
        vm.sendCode()
        advanceUntilIdle()

        auth.onVerify = { _, _ -> AuthResult.Failed(AuthFailure.CodeExpired, "expired") }
        vm.setCode("123456")
        vm.verify()
        advanceUntilIdle()

        assertEquals(SignInStep.Code, vm.state.value.step)
        assertEquals(AuthFailure.CodeExpired, vm.state.value.problem)
    }

    @Test
    fun `a failure never leaves the screen stuck busy`() = runTest(dispatcher) {
        auth.onSend = { _, _ -> AuthResult.Failed(AuthFailure.Unreachable, "no network") }
        val vm = viewModel()
        vm.setDigits("9876543210")

        vm.sendCode()
        advanceUntilIdle()

        assertFalse("a spinner nobody can cancel is worse than an error", vm.state.value.busy)
        assertTrue(vm.state.value.canSendCode)
    }

    // -------------------------------------------------------------------- resending

    @Test
    fun `resend is barred until the countdown runs out`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("9876543210")
        vm.sendCode()
        // Far enough for the request to finish, not far enough to drain the timer.
        advanceTimeBy(100)

        assertFalse("every resend is an SMS somebody pays for", vm.state.value.canResend)
        assertTrue(vm.state.value.resendInSeconds > 0)

        vm.resend()
        advanceUntilIdle()
        assertEquals("the barred press did nothing", 1, auth.sent.size)
    }

    @Test
    fun `resend works once the countdown is done, and reuses the token`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("9876543210")
        vm.sendCode()
        advanceTimeBy(31_000)

        assertTrue(vm.state.value.canResend)
        vm.resend()
        advanceUntilIdle()

        assertEquals(2, auth.sent.size)
        assertEquals(
            "resending carries the token so the provider does not start over",
            "resend-token-1",
            auth.sent[1].second,
        )
    }

    @Test
    fun `the first request does not carry a resend token`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("9876543210")

        vm.sendCode()
        advanceUntilIdle()

        assertNull(auth.sent.single().second)
    }

    // ------------------------------------------------------------- changing the number

    @Test
    fun `changing the number keeps it so a typo can be corrected`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("9876543210")
        vm.sendCode()
        advanceUntilIdle()

        vm.changeNumber()

        assertEquals(SignInStep.Phone, vm.state.value.step)
        assertEquals("retyping ten digits to fix one is a punishment", "9876543210", vm.state.value.digits)
        assertEquals("", vm.state.value.code)
    }

    @Test
    fun `verifying does nothing until six digits are in`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setDigits("9876543210")
        vm.sendCode()
        advanceUntilIdle()

        vm.setCode("1234")
        assertFalse(vm.state.value.canVerify)
        vm.verify()
        advanceUntilIdle()

        assertTrue(signedIn.isEmpty())
    }

    @Test
    fun `a number already carrying its country code is not given a second one`() {
        // Somebody who joined with an invite has their signed-in identity stored,
        // which arrives in full international form; somebody typed in by hand is
        // ten digits. Both land in the same column and both reach the same screen,
        // and the member screen used to read "+91 +919000000002" for the first.
        assertEquals("+91 90000 00002", formatIndianPhone("+919000000002"))
        assertEquals("+91 90000 00002", formatIndianPhone("9000000002"))
        assertEquals("+91 98765 43210", formatIndianPhone("+91 98765 43210"))
    }

    @Test
    fun `what gets stored is the bare ten digits, whichever form came in`() {
        assertEquals("9000000002", indianMobileDigits("+919000000002"))
        assertEquals("9000000002", indianMobileDigits("9000000002"))
    }

}
