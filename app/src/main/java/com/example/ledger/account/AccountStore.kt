package com.example.ledger.account

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.ledger.BuildConfig
import com.example.ledger.data.Settings
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The signed-in Google account, as the screens see it. */
data class AccountState(
    val configured: Boolean,
    val signedIn: Boolean,
    val name: String? = null,
    val email: String? = null,
    val busy: Boolean = false,
    val error: String? = null
)

/**
 * Google sign-in and the small account record kept in Firebase.
 *
 * Sent to the server: Google name and email, sign-up / last-open dates, app and Android version,
 * phone model, Pro status, the trial start and (only with consent) feature-use counts.
 * Never sent: SMS text, amounts, balances, merchants, categories or any bank detail.
 */
class AccountStore(private val context: Context, private val settings: Settings) {
    private val app: FirebaseApp? = initFirebase()
    private val auth: FirebaseAuth? = app?.let { FirebaseAuth.getInstance(it) }
    private val db: FirebaseFirestore? = app?.let { FirebaseFirestore.getInstance(it) }

    private val _state = MutableStateFlow(current())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Usage counts not yet sent; sent with the next app open. */
    private val pendingUsage = mutableMapOf<String, Long>()

    private fun initFirebase(): FirebaseApp? {
        if (BuildConfig.FB_APP_ID.isBlank()) return null
        return try {
            FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FB_API_KEY)
                        .setApplicationId(BuildConfig.FB_APP_ID)
                        .setProjectId(BuildConfig.FB_PROJECT_ID)
                        .build()
                )
        } catch (e: Exception) {
            null
        }
    }

    private fun current(): AccountState {
        val user = auth?.currentUser
        return AccountState(
            configured = app != null && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(),
            signedIn = user != null,
            name = user?.displayName,
            email = user?.email
        )
    }

    private fun publish(error: String? = null, busy: Boolean = false) {
        _state.value = current().copy(error = error, busy = busy)
    }

    fun clearError() = publish()

    /** Shows Google's account picker, then signs in to Firebase and sets up the trial. */
    suspend fun signIn(activity: Activity, usageConsent: Boolean): Boolean {
        val a = auth ?: run { publish("Sign-in is not set up in this build."); return false }
        publish(busy = true)
        return try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                publish("That sign-in method is not supported. Please choose a Google account.")
                return false
            }
            val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
            a.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            settings.usageConsent = usageConsent
            setUpAccount()
            publish()
            true
        } catch (e: GetCredentialCancellationException) {
            publish()
            false
        } catch (e: Exception) {
            publish("Could not sign in: " + (e.message ?: "please check your internet and try again."))
            false
        }
    }

    /** Creates the account record on first sign-in and reads the trial start for this Google account. */
    private suspend fun setUpAccount() {
        val user = auth?.currentUser ?: return
        val store = db ?: return
        // The trial belongs to the Google account (stored under a one-way hash of the email), so
        // reinstalling or deleting and re-creating the account does not start a new free month.
        val email = user.email?.trim()?.lowercase()
        if (email != null) {
            val trialRef = store.collection("trials").document(sha256(email))
            val snap = trialRef.get().await()
            if (!snap.exists()) {
                trialRef.set(mapOf("start" to FieldValue.serverTimestamp())).await()
            }
            val start = trialRef.get().await().getTimestamp("start")
            if (start != null) settings.trialStartMillis = start.toDate().time
        }
        val userRef = store.collection("users").document(user.uid)
        if (!userRef.get().await().exists()) {
            userRef.set(mapOf("createdAt" to FieldValue.serverTimestamp()) + profile(), SetOptions.merge()).await()
        } else {
            userRef.set(profile(), SetOptions.merge()).await()
        }
    }

    private fun profile(): Map<String, Any?> {
        val user = auth?.currentUser
        return mapOf(
            "name" to user?.displayName,
            "email" to user?.email,
            "lastOpenAt" to FieldValue.serverTimestamp(),
            "appVersion" to BuildConfig.VERSION_NAME,
            "androidVersion" to Build.VERSION.RELEASE,
            "deviceModel" to (Build.MANUFACTURER + " " + Build.MODEL),
            "pro" to settings.proPurchased,
            "usageConsent" to settings.usageConsent
        )
    }

    /** Counts one use of a feature (only kept when the person agreed to share usage). */
    fun track(event: String) {
        if (!settings.usageConsent) return
        synchronized(pendingUsage) { pendingUsage[event] = (pendingUsage[event] ?: 0L) + 1 }
    }

    private var lastSentMillis = 0L

    /** On app open: refresh last-open, versions and Pro status, and send pending usage counts. */
    fun onAppOpen(force: Boolean = false) {
        val user = auth?.currentUser ?: return
        val store = db ?: return
        val now = System.currentTimeMillis()
        // Coming back from a file picker or the lock screen is not a new open.
        if (!force && now - lastSentMillis < 30 * 60 * 1000L) return
        lastSentMillis = now
        track("app_open")
        val counts = synchronized(pendingUsage) { pendingUsage.toMap().also { pendingUsage.clear() } }
        val data = profile().toMutableMap()
        if (counts.isNotEmpty() && settings.usageConsent) {
            data["usage"] = counts.mapValues { FieldValue.increment(it.value) }
        }
        // Firestore queues this when offline and sends it later.
        store.collection("users").document(user.uid).set(data, SetOptions.merge())
        // Keep the server's trial start (also corrects a phone whose clock was changed).
        user.email?.trim()?.lowercase()?.let { email ->
            store.collection("trials").document(sha256(email)).get().addOnSuccessListener { snap ->
                snap.getTimestamp("start")?.let { settings.trialStartMillis = it.toDate().time }
            }
        }
    }

    /** Turning usage sharing off also removes the counts already stored. */
    fun setUsageConsent(on: Boolean) {
        settings.usageConsent = on
        val user = auth?.currentUser ?: return
        val update = mutableMapOf<String, Any>("usageConsent" to on)
        if (!on) {
            update["usage"] = FieldValue.delete()
            synchronized(pendingUsage) { pendingUsage.clear() }
        }
        db?.collection("users")?.document(user.uid)?.set(update, SetOptions.merge())
    }

    suspend fun signOut() {
        auth?.signOut()
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) } catch (e: Exception) { }
        publish()
    }

    /**
     * Deletes the account record and the Firebase account. Only the one-way trial marker stays,
     * so a new account with the same email does not get another free month.
     */
    suspend fun deleteAccount(activity: Activity): Boolean {
        val user = auth?.currentUser ?: return false
        publish(busy = true)
        return try {
            db?.collection("users")?.document(user.uid)?.delete()?.await()
            try {
                user.delete().await()
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                // Google asks for a fresh sign-in before deleting: confirm the account, then delete.
                val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
                val result = CredentialManager.create(activity)
                    .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
                val token = GoogleIdTokenCredential.createFrom((result.credential as CustomCredential).data).idToken
                user.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await()
                user.delete().await()
            }
            signOut()
            true
        } catch (e: Exception) {
            publish("Could not delete the account: " + (e.message ?: "please try again when online."))
            false
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Waits for a Google Play services task without extra libraries. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { t ->
        val e = t.exception
        if (e != null) cont.resumeWithException(e)
        else if (t.isCanceled) cont.cancel()
        else @Suppress("UNCHECKED_CAST") cont.resume(t.result as T)
    }
}
