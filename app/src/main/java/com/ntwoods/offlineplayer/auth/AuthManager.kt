package com.ntwoods.offlineplayer.auth

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class AuthManager(private val activity: Activity, private val serverClientId: String) {
    companion object { const val REQ_CODE = 9101 }

    fun startSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(serverClientId)
            .build()
        val client = GoogleSignIn.getClient(activity, gso)
        activity.startActivityForResult(client.signInIntent, REQ_CODE)
    }

    fun handleResult(data: Intent?, onSuccess: (GoogleSignInAccount) -> Unit, onError: (String) -> Unit) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val acct = task.getResult(ApiException::class.java)
            if (acct != null && acct.idToken != null) onSuccess(acct)
            else onError("No account or missing idToken")
        } catch (e: Exception) {
            onError(e.message ?: "Sign-in failed")
        }
    }
}