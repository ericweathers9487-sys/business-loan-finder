package com.yourco.lending.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val random = SecureRandom()
private val hex = HexFormat.of()

fun sha256Hex(text: String): String =
    hex.formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

/** Compares two hashes without leaking how many leading characters matched. */
fun sameHash(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.US_ASCII), b.toByteArray(Charsets.US_ASCII))

fun isSha256Hex(s: String): Boolean = s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' }

/** A new API key: 32 random bytes, URL-safe, with a prefix so it's recognizable if leaked. */
fun newApiKey(): String = "blf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

fun newEncryptionKey(): String = Base64.getEncoder().encodeToString(ByteArray(32).also(random::nextBytes))

/**
 * Encrypts borrower personal data before it touches the database (AES-256-GCM).
 * Each value is bound to its lead ID, so ciphertext copied onto another row
 * won't decrypt. Someone with a copy of the database file but not the key
 * sees only ciphertext.
 */
class PiiCipher(key: ByteArray) {
    init {
        require(key.size == 32) { "Encryption key must be 32 bytes" }
    }

    private val aesKey = SecretKeySpec(key, "AES")

    /** Separate key for the email lookup index, derived so one secret covers both. */
    private val indexKey = SecretKeySpec(hmac(key, "email-lookup-v1".toByteArray()), "HmacSHA256")

    fun encrypt(plain: String, leadId: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(TAG_BITS, iv))
        c.updateAAD(leadId.toByteArray(Charsets.UTF_8))
        val sealed = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return VERSION + Base64.getEncoder().encodeToString(iv + sealed)
    }

    fun decrypt(token: String, leadId: String): String {
        require(token.startsWith(VERSION)) { "Unknown ciphertext version" }
        val raw = Base64.getDecoder().decode(token.removePrefix(VERSION))
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
        c.updateAAD(leadId.toByteArray(Charsets.UTF_8))
        return String(c.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }

    /**
     * A keyed hash of the email, so a deletion request can find a borrower's
     * leads without storing the email in readable form.
     */
    fun emailIndex(email: String): String =
        hex.formatHex(Mac.getInstance("HmacSHA256").apply { init(indexKey) }
            .doFinal(email.trim().lowercase().toByteArray(Charsets.UTF_8)))

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val VERSION = "v1:"

        fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
    }
}
