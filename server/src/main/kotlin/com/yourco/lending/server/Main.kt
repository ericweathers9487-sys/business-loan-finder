package com.yourco.lending.server

import com.yourco.lending.catalog.SampleProducts
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null -> serve()
        "new-lender-key" -> {
            val key = newApiKey()
            println("API key (send to the lender privately; it's shown only once):")
            println("  $key")
            println("apiKeySha256 (put this in LENDERS_JSON or ADMIN_API_KEY_SHA256):")
            println("  ${sha256Hex(key)}")
        }
        "new-encryption-key" -> {
            println("PII_ENCRYPTION_KEY (store it as a secret and back it up; lose it and stored leads can't be read):")
            println("  ${newEncryptionKey()}")
        }
        else -> {
            System.err.println("Usage: server [new-lender-key | new-encryption-key]")
            exitProcess(2)
        }
    }
}

private fun serve() {
    val log = LoggerFactory.getLogger("LeadServer")
    val config = try {
        ServerConfig.fromEnv()
    } catch (e: ConfigException) {
        System.err.println("Can't start: ${e.message}")
        exitProcess(1)
    }

    val store = LeadStore(config.databasePath)
    val service = LeadService(
        // The same catalog the app ships. Real lenders go in core/.../catalog/SampleProducts.kt.
        products = SampleProducts.all,
        lenders = config.lenders,
        store = store,
        cipher = PiiCipher(config.encryptionKey),
        notifier = WebhookNotifier(),
        retentionDays = config.retentionDays,
    )
    service.configWarnings().forEach { log.warn(it) }
    log.info(
        "Starting on port {} with {} lender account(s); personal data kept {} days",
        config.port, config.lenders.size, config.retentionDays,
    )

    Runtime.getRuntime().addShutdownHook(Thread { store.close() })
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        leadServer(service, config.adminKeySha256)
    }.start(wait = true)
}
