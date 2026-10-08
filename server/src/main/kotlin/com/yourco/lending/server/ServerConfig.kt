package com.yourco.lending.server

import com.yourco.lending.api.LeadApi
import kotlinx.serialization.Serializable
import java.util.Base64

/** One lender's login and which products' leads they receive. */
@Serializable
data class LenderAccount(
    val name: String,
    /** SHA-256 (hex) of the lender's API key. The key itself is never stored. */
    val apiKeySha256: String,
    val productIds: Set<String>,
    /** Optional https URL that gets the anonymized card (never contact details) for each new lead. */
    val webhookUrl: String? = null,
)

class ConfigException(message: String) : Exception(message)

/** Everything the server reads from its environment. See server/README.md. */
class ServerConfig(
    val port: Int,
    val databasePath: String,
    val encryptionKey: ByteArray,
    val lenders: List<LenderAccount>,
    val adminKeySha256: String?,
    val retentionDays: Int,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            fun value(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun need(name: String) = value(name) ?: throw ConfigException("$name is not set. See server/README.md.")

            val key = runCatching { Base64.getDecoder().decode(need("PII_ENCRYPTION_KEY")) }.getOrNull()
            if (key == null || key.size != 32) {
                throw ConfigException("PII_ENCRYPTION_KEY must be 32 random bytes in base64. Make one with: server new-encryption-key")
            }

            val lenders = try {
                LeadApi.json.decodeFromString<List<LenderAccount>>(need("LENDERS_JSON"))
            } catch (e: ConfigException) {
                throw e
            } catch (e: Exception) {
                throw ConfigException("LENDERS_JSON isn't a valid list of lender accounts. See server/README.md.")
            }
            validateLenders(lenders)

            val admin = value("ADMIN_API_KEY_SHA256")?.lowercase()
            if (admin != null && !isSha256Hex(admin)) throw ConfigException("ADMIN_API_KEY_SHA256 must be 64 hex characters.")

            val retentionDays = value("RETENTION_DAYS")?.let {
                it.toIntOrNull()?.takeIf { d -> d >= 1 } ?: throw ConfigException("RETENTION_DAYS must be a whole number of days, 1 or more.")
            } ?: 365

            val port = value("PORT")?.let { it.toIntOrNull() ?: throw ConfigException("PORT must be a number.") } ?: 8080

            return ServerConfig(
                port = port,
                databasePath = value("DATABASE_PATH") ?: "data/leads.db",
                encryptionKey = key,
                lenders = lenders.map { it.copy(apiKeySha256 = it.apiKeySha256.lowercase()) },
                adminKeySha256 = admin,
                retentionDays = retentionDays,
            )
        }

        private fun validateLenders(lenders: List<LenderAccount>) {
            val seenProducts = mutableMapOf<String, String>()
            for (l in lenders) {
                if (l.name.isBlank()) throw ConfigException("Every lender in LENDERS_JSON needs a name.")
                if (!isSha256Hex(l.apiKeySha256.lowercase())) {
                    throw ConfigException("${l.name}: apiKeySha256 must be 64 hex characters. Make one with: server new-lender-key")
                }
                if (l.productIds.isEmpty()) throw ConfigException("${l.name}: productIds is empty.")
                l.webhookUrl?.let { if (!it.startsWith("https://")) throw ConfigException("${l.name}: webhookUrl must start with https://") }
                for (p in l.productIds) {
                    seenProducts.put(p, l.name)?.let { other ->
                        throw ConfigException("Product $p is listed under both $other and ${l.name}.")
                    }
                }
            }
            if (lenders.map { it.apiKeySha256.lowercase() }.toSet().size != lenders.size) {
                throw ConfigException("Two lenders in LENDERS_JSON share an API key.")
            }
        }
    }
}
