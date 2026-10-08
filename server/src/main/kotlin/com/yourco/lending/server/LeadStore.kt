package com.yourco.lending.server

import com.yourco.lending.api.LeadApi
import com.yourco.lending.matching.LeadCard
import com.yourco.lending.matching.LeadEventType
import kotlinx.serialization.Serializable
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/** Where a lead stands with its lender. */
enum class LeadStatus { NEW, ACCEPTED, REJECTED, FUNDED }

/** One audit log entry. [actor] is "borrower-app", "server", "admin", or "lender:<name>". */
@Serializable
data class AuditEvent(val type: LeadEventType, val atEpochMillis: Long, val actor: String, val note: String = "")

/** Proof of consent: who it was for, which disclosures, when, and the exact words agreed to. */
@Serializable
data class ConsentRecord(
    val productId: String,
    val disclosureVersion: String,
    val givenOnPhoneAtEpochMillis: Long,
    val receivedAtEpochMillis: Long,
    val text: String,
)

data class StoredLead(
    val leadId: String,
    val submissionId: String,
    val productId: String,
    val status: LeadStatus,
    val createdAt: Long,
    val notifiedAt: Long?,
    /** Anonymized. What the lender sees before accepting. */
    val card: LeadCard,
    val consent: ConsentRecord,
    /** Keyed hash of the email for deletion requests. Null once erased. */
    val emailIndex: String?,
    /** Encrypted contact details and answers. Null once erased. */
    val personalData: String?,
)

/**
 * SQLite storage. Borrower personal data arrives here already encrypted, and
 * the audit log is append-only: the database itself refuses to change or
 * delete an event.
 */
class LeadStore(path: String) : AutoCloseable {

    private val conn: Connection
    private val lock = Any()

    init {
        val file = Path.of(path).toAbsolutePath()
        file.parent?.let(::createPrivateDirectory)
        conn = DriverManager.getConnection("jdbc:sqlite:$file")
        conn.createStatement().use { st ->
            st.execute("PRAGMA journal_mode = WAL")
            st.execute("PRAGMA foreign_keys = ON")
            // Overwrite erased personal data on disk instead of leaving it in free pages.
            st.execute("PRAGMA secure_delete = ON")
            SCHEMA.forEach(st::execute)
        }
    }

    /** Stores a new lead with its first audit event. False if the submission ID was already used. */
    fun insert(lead: StoredLead, created: AuditEvent): Boolean = tx { c ->
        val exists = c.prepareStatement("SELECT 1 FROM leads WHERE submission_id = ?").use { ps ->
            ps.setString(1, lead.submissionId)
            ps.executeQuery().use { it.next() }
        }
        if (exists) return@tx false
        c.prepareStatement(
            """INSERT INTO leads (lead_id, submission_id, product_id, status, created_at, notified_at,
                                  card_json, consent_json, email_index, personal_data)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            ps.setString(1, lead.leadId)
            ps.setString(2, lead.submissionId)
            ps.setString(3, lead.productId)
            ps.setString(4, lead.status.name)
            ps.setLong(5, lead.createdAt)
            ps.setObject(6, lead.notifiedAt)
            ps.setString(7, LeadApi.json.encodeToString(lead.card))
            ps.setString(8, LeadApi.json.encodeToString(lead.consent))
            ps.setString(9, lead.emailIndex)
            ps.setString(10, lead.personalData)
            ps.executeUpdate()
        }
        addEvent(c, lead.leadId, created)
        true
    }

    fun leadIdForSubmission(submissionId: String): String? = tx { c ->
        c.prepareStatement("SELECT lead_id FROM leads WHERE submission_id = ?").use { ps ->
            ps.setString(1, submissionId)
            ps.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
    }

    fun get(leadId: String): StoredLead? = tx { c ->
        c.prepareStatement("SELECT $COLUMNS FROM leads WHERE lead_id = ?").use { ps ->
            ps.setString(1, leadId)
            ps.executeQuery().use { if (it.next()) it.toLead() else null }
        }
    }

    /** Newest first. */
    fun list(productIds: Set<String>, status: LeadStatus?, limit: Int): List<StoredLead> {
        if (productIds.isEmpty()) return emptyList()
        val marks = productIds.joinToString { "?" }
        val statusClause = if (status != null) "AND status = ?" else ""
        return tx { c ->
            c.prepareStatement(
                "SELECT $COLUMNS FROM leads WHERE product_id IN ($marks) $statusClause ORDER BY created_at DESC LIMIT ?"
            ).use { ps ->
                var i = 1
                productIds.forEach { ps.setString(i++, it) }
                status?.let { ps.setString(i++, it.name) }
                ps.setInt(i, limit)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toLead()) } }
            }
        }
    }

    /**
     * Moves a lead to [to] only if it's currently in one of [from], and logs
     * [event] in the same transaction. With [requirePersonalData], also only
     * if the borrower's details haven't been erased. False if nothing changed.
     */
    fun transition(
        leadId: String,
        from: Set<LeadStatus>,
        to: LeadStatus,
        event: AuditEvent,
        requirePersonalData: Boolean = false,
    ): Boolean = tx { c ->
        val marks = from.joinToString { "?" }
        val dataClause = if (requirePersonalData) "AND personal_data IS NOT NULL" else ""
        val changed = c.prepareStatement(
            "UPDATE leads SET status = ? WHERE lead_id = ? AND status IN ($marks) $dataClause"
        ).use { ps ->
            ps.setString(1, to.name)
            ps.setString(2, leadId)
            from.forEachIndexed { i, s -> ps.setString(i + 3, s.name) }
            ps.executeUpdate()
        }
        if (changed == 1) addEvent(c, leadId, event)
        changed == 1
    }

    /** New leads whose lender hasn't been told yet, oldest first. */
    fun awaitingNotification(createdSince: Long): List<StoredLead> = tx { c ->
        c.prepareStatement(
            "SELECT $COLUMNS FROM leads WHERE notified_at IS NULL AND status = 'NEW' AND created_at >= ? ORDER BY created_at"
        ).use { ps ->
            ps.setLong(1, createdSince)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toLead()) } }
        }
    }

    fun markNotified(leadId: String, event: AuditEvent): Boolean = tx { c ->
        val changed = c.prepareStatement("UPDATE leads SET notified_at = ? WHERE lead_id = ? AND notified_at IS NULL").use { ps ->
            ps.setLong(1, event.atEpochMillis)
            ps.setString(2, leadId)
            ps.executeUpdate()
        }
        if (changed == 1) addEvent(c, leadId, event)
        changed == 1
    }

    fun leadIdsByEmailIndex(index: String): List<String> = tx { c ->
        c.prepareStatement("SELECT lead_id FROM leads WHERE email_index = ?").use { ps ->
            ps.setString(1, index)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    fun leadIdsWithPersonalDataBefore(cutoff: Long): List<String> = tx { c ->
        c.prepareStatement("SELECT lead_id FROM leads WHERE personal_data IS NOT NULL AND created_at < ?").use { ps ->
            ps.setLong(1, cutoff)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    /** Erases contact details and answers, keeping the anonymized card and the audit log. */
    fun erasePersonalData(leadId: String, event: AuditEvent): Boolean = tx { c ->
        val changed = c.prepareStatement(
            "UPDATE leads SET personal_data = NULL, email_index = NULL WHERE lead_id = ? AND personal_data IS NOT NULL"
        ).use { ps ->
            ps.setString(1, leadId)
            ps.executeUpdate()
        }
        if (changed == 1) addEvent(c, leadId, event)
        changed == 1
    }

    fun events(leadId: String): List<AuditEvent> = tx { c ->
        c.prepareStatement("SELECT type, at, actor, note FROM lead_events WHERE lead_id = ? ORDER BY id").use { ps ->
            ps.setString(1, leadId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(AuditEvent(LeadEventType.valueOf(rs.getString(1)), rs.getLong(2), rs.getString(3), rs.getString(4)))
                    }
                }
            }
        }
    }

    override fun close() = synchronized(lock) { conn.close() }

    private fun addEvent(c: Connection, leadId: String, e: AuditEvent) {
        c.prepareStatement("INSERT INTO lead_events (lead_id, type, at, actor, note) VALUES (?, ?, ?, ?, ?)").use { ps ->
            ps.setString(1, leadId)
            ps.setString(2, e.type.name)
            ps.setLong(3, e.atEpochMillis)
            ps.setString(4, e.actor)
            ps.setString(5, e.note)
            ps.executeUpdate()
        }
    }

    private fun <T> tx(block: (Connection) -> T): T = synchronized(lock) {
        conn.autoCommit = false
        try {
            block(conn).also { conn.commit() }
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    private fun ResultSet.toLead() = StoredLead(
        leadId = getString("lead_id"),
        submissionId = getString("submission_id"),
        productId = getString("product_id"),
        status = LeadStatus.valueOf(getString("status")),
        createdAt = getLong("created_at"),
        notifiedAt = getLong("notified_at").takeUnless { wasNull() },
        card = LeadApi.json.decodeFromString(getString("card_json")),
        consent = LeadApi.json.decodeFromString(getString("consent_json")),
        emailIndex = getString("email_index"),
        personalData = getString("personal_data"),
    )

    private companion object {
        const val COLUMNS = "lead_id, submission_id, product_id, status, created_at, notified_at, " +
            "card_json, consent_json, email_index, personal_data"

        val SCHEMA = listOf(
            """CREATE TABLE IF NOT EXISTS leads (
                lead_id       TEXT PRIMARY KEY,
                submission_id TEXT NOT NULL UNIQUE,
                product_id    TEXT NOT NULL,
                status        TEXT NOT NULL,
                created_at    INTEGER NOT NULL,
                notified_at   INTEGER,
                card_json     TEXT NOT NULL,
                consent_json  TEXT NOT NULL,
                email_index   TEXT,
                personal_data TEXT
            )""",
            "CREATE INDEX IF NOT EXISTS leads_by_product ON leads (product_id, created_at)",
            "CREATE INDEX IF NOT EXISTS leads_by_email ON leads (email_index)",
            """CREATE TABLE IF NOT EXISTS lead_events (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                lead_id TEXT NOT NULL REFERENCES leads (lead_id),
                type    TEXT NOT NULL,
                at      INTEGER NOT NULL,
                actor   TEXT NOT NULL,
                note    TEXT NOT NULL DEFAULT ''
            )""",
            "CREATE INDEX IF NOT EXISTS events_by_lead ON lead_events (lead_id, id)",
            """CREATE TRIGGER IF NOT EXISTS lead_events_append_only_update BEFORE UPDATE ON lead_events
               BEGIN SELECT RAISE(ABORT, 'The audit log is append-only'); END""",
            """CREATE TRIGGER IF NOT EXISTS lead_events_append_only_delete BEFORE DELETE ON lead_events
               BEGIN SELECT RAISE(ABORT, 'The audit log is append-only'); END""",
        )

        /** The data folder is readable by the server's own user only, where the OS supports it. */
        fun createPrivateDirectory(dir: Path) {
            if (Files.isDirectory(dir)) return
            if ("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
                Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            } else {
                Files.createDirectories(dir)
            }
        }
    }
}
