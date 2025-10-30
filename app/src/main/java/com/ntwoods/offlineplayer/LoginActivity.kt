package com.ntwoods.offlineplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AUTH"
        private const val RC_SIGN_IN = 9001

        // ✅ Web application client ID
        private const val WEB_CLIENT_ID =
            "481115074216-vg2nfnqct6b9t869h1dcn4j6ljt4j17t.apps.googleusercontent.com"

        // ✅ Apps Script URL (aapka diya hua)
        private const val SCRIPT_BASE_URL =
            "https://script.google.com/macros/s/AKfycbxfNASciXHnjv-znEICIfL6j3iaO1oEpPjLXe2kuT1nIW3VDCKnG9gvF9qUZjsWcUIb/exec"

        // SharedPrefs
        private const val PREFS = "nt_offline_prefs"
        private const val KEY_MEDIA = "mediaKey"
        private const val KEY_EXPIRES = "expiresAt"
        private const val KEY_EMAIL = "email"
    }

    private lateinit var googleSignInClient: GoogleSignInClient
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Debug info
        logRuntimeDebugInfo()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(WEB_CLIENT_ID) // always WEB client id
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<Button>(R.id.btnSignIn).setOnClickListener { signIn() }
    }

    private fun signIn() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    @Deprecated("onActivityResult used for simplicity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                Log.d(TAG, "SignIn OK | email=${account.email} | id=${account.id} | hasToken=${!idToken.isNullOrBlank()}")
                if (idToken.isNullOrBlank()) {
                    Toast.makeText(this, "Sign-in OK but idToken=null", Toast.LENGTH_LONG).show()
                    return
                }
                authenticateWithServer(idToken)
            } catch (e: ApiException) {
                Log.e(TAG, "SignIn failed | code=${e.statusCode} | msg=${e.message}", e)
                Toast.makeText(this, "Sign-In failed (code=${e.statusCode})", Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected sign-in error: ${t.message}", t)
                Toast.makeText(this, "Unexpected error: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun authenticateWithServer(idToken: String) {
        val deviceId = androidId()
        Toast.makeText(this, "Verifying on server…", Toast.LENGTH_SHORT).show()

        io.execute {
            try {
                val url = URL("$SCRIPT_BASE_URL?path=auth")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 15000
                    readTimeout = 20000
                }

                val payload = JSONObject()
                    .put("idToken", idToken)
                    .put("deviceId", deviceId)

                BufferedWriter(OutputStreamWriter(conn.outputStream, Charsets.UTF_8)).use {
                    it.write(payload.toString())
                }

                val code = conn.responseCode
                val reader = if (code in 200..299)
                    BufferedReader(InputStreamReader(conn.inputStream))
                else
                    BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                val text = reader.use { it.readText() }

                Log.d(TAG, "Server[$code]: $text")

                if (code !in 200..299) {
                    runOnUiThread { Toast.makeText(this, "Server error: HTTP $code", Toast.LENGTH_LONG).show() }
                    return@execute
                }

                val json = JSONObject(text)
                if (!json.optBoolean("ok", false)) {
                    val err = json.optString("error", "denied")
                    runOnUiThread { Toast.makeText(this, "Access denied: $err", Toast.LENGTH_LONG).show() }
                    return@execute
                }

                val email = json.optString("email", "")
                val mediaKey = json.optString("mediaKey", "")
                val policy = json.optJSONObject("policy")
                val expiresAt = policy?.optLong("expiresAt", 0L) ?: 0L

                if (mediaKey.isBlank() || expiresAt == 0L) {
                    runOnUiThread { Toast.makeText(this, "Invalid server response", Toast.LENGTH_LONG).show() }
                    return@execute
                }

                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_MEDIA, mediaKey)
                    .putLong(KEY_EXPIRES, expiresAt)
                    .putString(KEY_EMAIL, email)
                    .apply()

                runOnUiThread {
                    Toast.makeText(this, "Sign-in successful ✔", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Server auth error: ${t.message}", t)
                runOnUiThread { Toast.makeText(this, "Network/Auth error: ${t.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    // -------- Utilities --------
    @SuppressLint("HardwareIds")
    private fun androidId(): String =
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun logRuntimeDebugInfo() {
        try {
            val pkg = packageName
            val sha1 = getSigningCertSha1() ?: "unknown"
            Log.i(TAG, "AppPkg=$pkg | WEB_CLIENT_ID=$WEB_CLIENT_ID")
            Log.i(TAG, "Runtime SHA-1 (signing cert) = $sha1")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not compute runtime SHA-1: ${t.message}")
        }
    }

    /**
     * Backward/forward compatible SHA-1:
     * - API 28+: GET_SIGNING_CERTIFICATES → signingInfo?.apkContentsSigners?.firstOrNull()
     * - Older: GET_SIGNATURES (deprecated) → signatures?.firstOrNull()
     */
    private fun getSigningCertSha1(): String? {
        return try {
            val pm = packageManager
            val pkg = packageName

            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                val arr = pkgInfo.signingInfo?.apkContentsSigners
                arr?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            }

            certBytes?.let {
                val md = MessageDigest.getInstance("SHA1")
                val digest = md.digest(it)
                toColonHex(digest)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun toColonHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in bytes.indices) {
            sb.append(String.format(Locale.US, "%02X", bytes[i]))
            if (i != bytes.size - 1) sb.append(':')
        }
        return sb.toString()
    }
}
