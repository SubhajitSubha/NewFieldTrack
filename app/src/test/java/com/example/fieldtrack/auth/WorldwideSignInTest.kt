package com.example.fieldtrack.auth

import com.example.fieldtrack.data.auth.AuthFailure
import com.example.fieldtrack.data.auth.AuthResult
import com.example.fieldtrack.data.auth.GoogleAuth
import com.example.fieldtrack.data.auth.GoogleResult
import com.example.fieldtrack.data.auth.PhoneAuth
import com.example.fieldtrack.data.auth.SignedIn
import com.example.fieldtrack.feature.signin.SignInViewModel
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

private class RecordingPhoneAuth : PhoneAuth {
    val sent = mutableListOf<String>()
    override fun current(): SignedIn? = null
    override suspend fun sendCode(phoneE164: String, resendToken: Any?): AuthResult {
        sent += phoneE164
        return AuthResult.CodeSent("v1", null)
    }
    override suspend fun verify(verificationId: String, code: String): AuthResult =
        AuthResult.Failed(AuthFailure.WrongCode, "")
    override suspend fun signOut() = Unit
}

private class FakeGoogleAuth(override val available: Boolean = true) : GoogleAuth {
    var answer: GoogleResult = GoogleResult.Done(SignedIn("g-uid", null, "wanjiru@example.com"))
    var calls = 0
    override suspend fun signIn(): GoogleResult { calls++; return answer }
    override suspend fun link(): GoogleResult { calls++; return answer }
    override fun linkedEmail(): String? = null
}

/**
 * Signing in from any country, and with Google where texts are slow, dear or do not arrive.
 *
 * What matters is that the number the provider is asked to text is the one the person
 * meant, in whichever country they are in, and that Google is only offered when it can
 * work and only ever signs in the one account the person picked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorldwideSignInTest {

    private val dispatcher = StandardTestDispatcher()
    private val phone = RecordingPhoneAuth()
    private val signedIn = mutableListOf<SignedIn>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(dial: String = "91", google: GoogleAuth? = null) =
        SignInViewModel(phone, defaultDial = dial, google = google) { signedIn += it }

    @Test
    fun `a Kenyan number is texted at its own country code`() = runTest(dispatcher) {
        val vm = viewModel(dial = "254")
        vm.setPhone("254", "712 345 678")
        assertEquals("+254712345678", vm.state.value.phoneE164)
        assertTrue(vm.state.value.canSendCode)
        vm.sendCode()
        advanceUntilIdle()
        assertEquals(listOf("+254712345678"), phone.sent)
    }

    @Test
    fun `the code starts from the country the phone is in and can be changed`() {
        val vm = viewModel(dial = "55")
        assertEquals("55", vm.state.value.dial)
        vm.setPhone("1", "2025550142")
        assertEquals("+12025550142", vm.state.value.phoneE164)
    }

    @Test
    fun `a pasted international number brings its own code`() {
        val vm = viewModel(dial = "91")
        vm.setDigits("+44 7700 900123")
        assertEquals("44", vm.state.value.dial)
        assertEquals("7700900123", vm.state.value.digits)
        assertEquals("+447700900123", vm.state.value.phoneE164)
    }

    @Test
    fun `an Indian number keeps its strict rule and a number too short for anywhere is refused`() {
        val india = viewModel(dial = "91")
        india.setDigits("5876543210")
        assertFalse("not a mobile prefix", india.state.value.canSendCode)
        india.setDigits("9876543210")
        assertTrue(india.state.value.canSendCode)
        val kenya = viewModel(dial = "254")
        kenya.setDigits("7123")
        assertFalse(kenya.state.value.canSendCode)
    }

    @Test
    fun `Google is only offered when this build can do it`() {
        assertFalse(viewModel(google = null).state.value.googleAvailable)
        assertFalse(viewModel(google = FakeGoogleAuth(available = false)).state.value.googleAvailable)
        assertTrue(viewModel(google = FakeGoogleAuth()).state.value.googleAvailable)
    }

    @Test
    fun `choosing a Google account signs the person in as that account`() = runTest(dispatcher) {
        val google = FakeGoogleAuth()
        val vm = viewModel(google = google)
        vm.signInWithGoogle()
        advanceUntilIdle()
        assertEquals(listOf("g-uid"), signedIn.map { it.uid })
        assertEquals("wanjiru@example.com", signedIn.single().email)
        assertNull("no number, and none invented", signedIn.single().phone)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `closing the chooser is not an error`() = runTest(dispatcher) {
        val google = FakeGoogleAuth().apply { answer = GoogleResult.Cancelled }
        val vm = viewModel(google = google)
        vm.signInWithGoogle()
        advanceUntilIdle()
        assertTrue(signedIn.isEmpty())
        assertNull(vm.state.value.problem)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `no Google account on the phone is said plainly`() = runTest(dispatcher) {
        val google = FakeGoogleAuth().apply { answer = GoogleResult.NoAccount }
        val vm = viewModel(google = google)
        vm.signInWithGoogle()
        advanceUntilIdle()
        assertEquals(AuthFailure.NoGoogleAccount, vm.state.value.problem)
        assertTrue(signedIn.isEmpty())
    }

    @Test
    fun `an account already used by somebody else is not signed into`() = runTest(dispatcher) {
        val google = FakeGoogleAuth().apply { answer = GoogleResult.AlreadyUsed }
        val vm = viewModel(google = google)
        vm.signInWithGoogle()
        advanceUntilIdle()
        assertEquals(AuthFailure.GoogleAccountTaken, vm.state.value.problem)
    }

    @Test
    fun `without Google configured the button does nothing at all`() = runTest(dispatcher) {
        val google = FakeGoogleAuth(available = false)
        val vm = viewModel(google = google)
        vm.signInWithGoogle()
        advanceUntilIdle()
        assertEquals(0, google.calls)
        assertTrue(signedIn.isEmpty())
    }

    @Test
    fun `a second tap while one is under way is ignored`() = runTest(dispatcher) {
        val google = FakeGoogleAuth()
        val vm = viewModel(google = google)
        vm.signInWithGoogle()
        vm.signInWithGoogle()
        advanceUntilIdle()
        assertEquals(1, google.calls)
    }
}
