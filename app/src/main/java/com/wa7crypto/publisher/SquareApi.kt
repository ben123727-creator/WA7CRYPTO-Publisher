package com.wa7crypto.publisher

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object ApiKeyVault {
    private const val ALIAS = "wa7crypto_square_openapi"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }
    fun save(context: Context, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        context.getSharedPreferences("wa7_secret", 0).edit()
            .putString("cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
    }
    fun read(context: Context): String? {
        val p = context.getSharedPreferences("wa7_secret", 0)
        val data = p.getString("cipher", null) ?: return null
        val iv = p.getString("iv", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
    fun clear(context: Context) {
        context.getSharedPreferences("wa7_secret", 0).edit().clear().apply()
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
    }
}

internal object SquareApi {
    private const val V1 = "https://www.binance.com/bapi/composite/v1/public/pgc/openApi"
    private const val V2 = "https://www.binance.com/bapi/composite/v2/public/pgc/openApi"

    private fun request(url: String, key: String, body: JSONObject): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 20000; c.readTimeout = 30000; c.doOutput = true
            c.setRequestProperty("X-Square-OpenAPI-Key", key)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("clienttype", "binanceSkill")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code == 504 && url.endsWith("/content/add")) return JSONObject().put("data", JSONObject())
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(raw) } catch (_: Exception) { throw IllegalStateException("Binance returned HTTP $code.") }
            if (json.optString("code") != "000000") throw IllegalStateException("Binance error ${json.optString("code")}: ${json.optString("message")}")
            return json
        } finally { c.disconnect() }
    }

    private fun upload(context: Context, key: String, uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
        val presign = request("$V2/image/presignedUrl", key, JSONObject().put("imageName", "wa7_${System.currentTimeMillis()}.$ext")).getJSONObject("data")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read selected image.")
        val put = URL(presign.getString("presignedUrl")).openConnection() as HttpURLConnection
        try {
            put.requestMethod = "PUT"; put.connectTimeout = 30000; put.readTimeout = 60000; put.doOutput = true
            put.setRequestProperty("Content-Type", mime)
            put.outputStream.use { it.write(bytes) }
            if (put.responseCode !in 200..299) error("Image upload failed: HTTP ${put.responseCode}")
        } finally { put.disconnect() }
        repeat(10) { n ->
            val status = request("$V2/image/imageStatus", key, JSONObject().put("fileTicket", presign.getString("fileTicket"))).getJSONObject("data")
            if (status.optInt("status") == 1) return status.getString("imageUrl")
            if (status.optInt("status") == 2) error("Image processing failed.")
            if (n < 9) Thread.sleep(3000)
        }
        error("Image processing timed out.")
    }

    fun publish(context: Context, key: String, text: String, images: List<Uri>): String {
        val body = JSONObject().put("contentType", 1).put("bodyTextOnly", text)
        if (images.isNotEmpty()) {
            val list = JSONArray()
            images.forEach { list.put(upload(context, key, it)) }
            body.put("imageList", list)
        }
        val data = request("$V1/content/add", key, body).optJSONObject("data")
        val link = data?.optString("shareLink").orEmpty()
        return if (link.isNotBlank() && link != "null") link else "Binance accepted the request; post link unavailable."
    }
}
