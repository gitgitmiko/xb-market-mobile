package com.xbmarket.mobile.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Akun XB Market: login Google lewat Credential Manager lalu Firebase Auth.
 * [available] false bila build tidak punya google-services.json; semua fitur akun disembunyikan.
 */
object Account {
    var available = false
        private set
    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    fun init(context: Context) {
        if (available) return
        available = FirebaseApp.getApps(context).isNotEmpty() && webClientId(context) != null
        if (!available) return
        val auth = FirebaseAuth.getInstance()
        _user.value = auth.currentUser
        auth.addAuthStateListener { _user.value = it.currentUser }
    }

    suspend fun signInWithGoogle(activityContext: Context) {
        val clientId = webClientId(activityContext) ?: error("Login Google belum dikonfigurasi")
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("Kredensial Google tidak dikenali")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    suspend fun signOut(context: Context) {
        CloudSync.beforeSignOut()
        FirebaseAuth.getInstance().signOut()
        // Agar pemilih akun muncul lagi saat login berikutnya.
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Dibuat plugin google-services dari google-services.json; dicari saat runtime agar build tanpa file itu tetap jalan. */
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }
}
