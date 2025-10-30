package com.ntwoods.offlineplayer.net

import android.content.Context
import com.ntwoods.offlineplayer.util.SecureStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class LicenseClient(private val scriptBaseUrl: String) {
    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()

    fun authenticate(ctx: Context, idToken: String): Boolean {
        val did = SecureStore.deviceId(ctx)
        val body = """{"idToken":"$idToken","deviceId":"$did"}"""
        val req = Request.Builder()
            .url("$scriptBaseUrl?path=auth")
            .post(RequestBody.create(jsonType, body))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val j = JSONObject(resp.body!!.string())
            if (j.optBoolean("ok")) {
                val key = j.getString("mediaKey")
                val exp = j.getJSONObject("policy").getLong("expiresAt")
                val email = j.getString("email")
                SecureStore.saveLicense(ctx, key, exp, email)
                return true
            }
        }
        return false
    }

    fun renew(ctx: Context, idToken: String): Boolean {
        val did = SecureStore.deviceId(ctx)
        val body = """{"idToken":"$idToken","deviceId":"$did"}"""
        val req = Request.Builder()
            .url("$scriptBaseUrl?path=license")
            .post(RequestBody.create(jsonType, body))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val j = JSONObject(resp.body!!.string())
            if (j.optBoolean("ok")) {
                val key = j.getString("mediaKey")
                val exp = j.getJSONObject("policy").getLong("expiresAt")
                val email = j.getString("email")
                SecureStore.saveLicense(ctx, key, exp, email)
                return true
            }
        }
        return false
    }
}