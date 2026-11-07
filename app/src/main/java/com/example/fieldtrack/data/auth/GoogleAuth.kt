package com.example.fieldtrack.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** What came of asking for a Google account. */
sealed interface GoogleResult {
    /** Signed in as, or linked to, this Google account. */
    data class Done(val user: SignedIn) : GoogleResult

    /** The person closed the chooser. Nothing to say. */
    data object Cancelled : GoogleResult

    /** There is no Google account on this phone to choose. */
    data object NoAccount : GoogleResult

    /** That Google account already belongs to somebody else's sign-in here. */
    data object AlreadyUsed : GoogleResult

    data class Failed(val reason: AuthFailure, val message: String) : GoogleResult
}

/**
 * A second way to prove who you are, besides a code sent by SMS.
 *
 * Behind an interface for the same reason the phone flow is: the real thing needs an
 * Activity and Google Play services, and everything the screen decides (when to offer it,
 * what to say when it is refused) is ordinary code that can be tested against a fake.
 */
interface GoogleAuth {
    /** False when this build has no Google client configured, so nothing is offered that cannot work. */
    val available: Boolean

    /** Signs in as the Google account the person picks. */
    suspend fun signIn(): GoogleResult

    /** Adds the Google account to whoever is signed in now, so either way gets them back in. */
    suspend fun link(): GoogleResult

    /** The Google account already linked to the person signed in now, if any. */
    fun linkedEmail(): String?
}

class FirebaseGoogleAuth(
    private val context: Context,
    private val activityProvider: () -> Activity?,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : GoogleAuth {

    /**
     * The server client id the Firebase plugin writes into the build when Google sign-in
     * is switched on for the project. Looked up by name so that a build made before it is
     * switched on still compiles and simply does not offer the button.
     */
    private val webClientId: String? by lazy {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    override val available: Boolean get() = webClientId != null

    override suspend fun signIn(): GoogleResult = run { credential ->
        val result = auth.signInWithCredential(credential).await()
        GoogleResult.Done(result.user.asSignedIn())
    }

    override suspend fun link(): GoogleResult = run { credential ->
        val user = auth.currentUser ?: return@run GoogleResult.Failed(AuthFailure.Unknown, "Not signed in")
        val result = user.linkWithCredential(credential).await()
        GoogleResult.Done(result.user.asSignedIn())
    }

    override fun linkedEmail(): String? =
        auth.currentUser?.providerData?.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }?.email

    private suspend fun run(
        then: suspend (com.google.firebase.auth.AuthCredential) -> GoogleResult,
    ): GoogleResult {
        val clientId = webClientId ?: return GoogleResult.Failed(AuthFailure.Unknown, "Google sign-in is not set up")
        val activity = activityProvider() ?: return GoogleResult.Failed(AuthFailure.Unknown, "The screen is no longer open")
        return try {
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleResult.Failed(AuthFailure.Unknown, "Unexpected credential")
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            then(GoogleAuthProvider.getCredential(token, null))
        } catch (e: GetCredentialCancellationException) {
            GoogleResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleResult.NoAccount
        } catch (e: FirebaseAuthUserCollisionException) {
            GoogleResult.AlreadyUsed
        } catch (e: GetCredentialException) {
            GoogleResult.Failed(AuthFailure.Unreachable, e.message.orEmpty())
        } catch (e: java.io.IOException) {
            GoogleResult.Failed(AuthFailure.Unreachable, e.message.orEmpty())
        } catch (e: Exception) {
            GoogleResult.Failed(AuthFailure.Unknown, e.message.orEmpty())
        }
    }

    private fun com.google.firebase.auth.FirebaseUser?.asSignedIn() = SignedIn(
        uid = this?.uid.orEmpty(),
        phone = this?.phoneNumber?.takeIf { it.isNotBlank() },
        email = this?.email,
    )
}
