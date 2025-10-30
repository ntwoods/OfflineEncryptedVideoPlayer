package com.ntwoods.offlineplayer.util

import android.content.Context
import com.ntwoods.offlineplayer.net.LicenseClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.ntwoods.offlineplayer.auth.AuthManager

object LicenseGate {
    // Return license key or null
    fun ensureKey(ctx: Context, scriptBaseUrl: String, webClientId: String): String? {
        val (key, exp, _) = SecureStore.getLicense(ctx)
        val now = System.currentTimeMillis()
        if (key != null && now < exp) return key

        val acct = GoogleSignIn.getLastSignedInAccount(ctx) ?: return null
        val idToken = acct.idToken ?: return null
        val ok = LicenseClient(scriptBaseUrl).renew(ctx, idToken)
        return if (ok) SecureStore.getLicense(ctx).first else null
    }
}