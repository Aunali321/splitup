package app.splitup.shared.data.sync

import com.powersync.db.schema.PendingStatement
import com.powersync.db.schema.PendingStatementParameter
import com.powersync.db.schema.RawTable
import com.powersync.db.schema.Schema

/**
 * The sync boundary for the Room tables.
 *
 * Room stores timestamps as epoch millis and dates as epoch days, while PowerSync
 * sends Postgres `timestamptz` as ISO-8601 with fixed microseconds and `date` as
 * `YYYY-MM-DD` (measured against the running service, not assumed). Rather than
 * change the storage — ISO text with variable fractional seconds would break every
 * `ORDER BY date DESC, created_at DESC` — the conversion happens here, in the raw
 * table statements going down and the CRUD triggers going up.
 *
 * Everything below derives from one [Col] list per table so the download statement,
 * the upload trigger, the seed statement and the synced-column set cannot drift
 * apart.
 */

private sealed interface Col {
    val name: String

    /** Text, integer or boolean; SQLite already stores booleans as 0/1. */
    data class Plain(override val name: String) : Col

    /** Epoch millis locally, ISO-8601 instant on the wire. */
    data class Ts(override val name: String) : Col

    /** Epoch days locally, ISO date on the wire. */
    data class Day(override val name: String) : Col

    /**
     * URL that only syncs when it is remote. Local `file://` paths mean nothing on
     * another device, so they go up as null and survive downloads that carry none.
     */
    data class DeviceUrl(override val name: String) : Col
}

/**
 * @param localOnly columns that exist in Room but never cross the boundary, written
 *   with [localDefault] on insert and left untouched on update.
 */
private class SyncedTable(
    val name: String,
    val cols: List<Col>,
    val localOnly: Map<String, String> = emptyMap(),
) {
    private val idCol get() = cols.first().name

    private fun downExpr(c: Col) = when (c) {
        is Col.Plain, is Col.DeviceUrl -> "?"
        is Col.Ts -> "CAST(ROUND(unixepoch(?, 'subsec') * 1000) AS INTEGER)"
        is Col.Day -> "CAST(julianday(?) - 2440587.5 AS INTEGER)"
    }

    private fun downUpdateExpr(c: Col) = when (c) {
        is Col.DeviceUrl ->
            "${c.name} = CASE WHEN excluded.${c.name} IS NULL AND ${c.name} LIKE 'file://%' " +
                "THEN ${c.name} ELSE excluded.${c.name} END"
        else -> "${c.name} = excluded.${c.name}"
    }

    private fun upExpr(c: Col, row: String) = when (c) {
        is Col.Plain -> "$row${c.name}"
        is Col.Ts -> "strftime('%Y-%m-%dT%H:%M:%fZ', $row${c.name} / 1000.0, 'unixepoch')"
        is Col.Day -> "date($row${c.name} * 86400, 'unixepoch')"
        is Col.DeviceUrl -> "CASE WHEN $row${c.name} LIKE 'file://%' THEN NULL ELSE $row${c.name} END"
    }

    private fun payload(row: String) = cols.joinToString(", ") { "'${it.name}', ${upExpr(it, row)}" }

    fun rawTable(): RawTable {
        val names = cols.map { it.name } + localOnly.keys
        val values = cols.map { downExpr(it) } + localOnly.values
        // Upsert rather than INSERT OR REPLACE: replace deletes and re-inserts the
        // row, which would reset every localOnly column on each download.
        val updates = cols.drop(1).joinToString(", ") { downUpdateExpr(it) }
        return RawTable(
            name = name,
            put = PendingStatement(
                sql = "INSERT INTO $name (${names.joinToString(", ")}) " +
                    "VALUES (${values.joinToString(", ")}) " +
                    "ON CONFLICT($idCol) DO UPDATE SET $updates",
                parameters = listOf(PendingStatementParameter.Id) +
                    cols.drop(1).map { PendingStatementParameter.Column(it.name) },
            ),
            delete = PendingStatement(
                sql = "DELETE FROM $name WHERE $idCol = ?",
                parameters = listOf(PendingStatementParameter.Id),
            ),
        )
    }

    /**
     * Triggers emit whole rows as PUT for both insert and update. PowerSync's default
     * PATCH carries only changed columns, which cannot express "column absent" versus
     * "set to null" — and a full row is what the server's last-write-wins needs anyway.
     */
    fun triggers(): List<String> {
        val put = { event: String ->
            """
            CREATE TRIGGER IF NOT EXISTS ${name}_ps_${event.lowercase()}
            AFTER $event ON $name FOR EACH ROW
            BEGIN
              INSERT INTO powersync_crud (op, id, type, data)
              VALUES ('PUT', NEW.$idCol, '$name', json_object(${payload("NEW.")}));
            END
            """.trimIndent()
        }
        return listOf(
            put("INSERT"),
            put("UPDATE"),
            """
            CREATE TRIGGER IF NOT EXISTS ${name}_ps_delete
            AFTER DELETE ON $name FOR EACH ROW
            BEGIN
              INSERT INTO powersync_crud (op, id, type)
              VALUES ('DELETE', OLD.$idCol, '$name');
            END
            """.trimIndent(),
        )
    }

    fun triggerDrops(): List<String> = listOf("insert", "update", "delete")
        .map { "DROP TRIGGER IF EXISTS ${name}_ps_$it" }

    /** Enqueues every existing row as a PUT — how a fresh sign-in uploads local data. */
    fun seed(): String =
        "INSERT INTO powersync_crud (op, id, type, data) " +
            "SELECT 'PUT', $idCol, '$name', json_object(${payload("")}) FROM $name"
}

private val SYNCED_TABLES = listOf(
    SyncedTable(
        name = "person",
        cols = listOf(
            Col.Plain("id"), Col.Plain("account_id"), Col.Plain("first_name"),
            Col.Plain("last_name"), Col.Plain("email"), Col.Plain("phone"),
            Col.Plain("avatar_url"), Col.Plain("default_currency_code"),
            Col.Plain("country_code"), Col.Plain("is_registered"),
            Col.Plain("external_source"), Col.Plain("external_id"),
            Col.Ts("updated_at"), Col.Ts("deleted_at"),
        ),
        // is_me means "the user of this install" and is meaningless on a shared row.
        localOnly = mapOf("is_me" to "0"),
    ),
    SyncedTable(
        name = "group_",
        cols = listOf(
            Col.Plain("id"), Col.Plain("name"), Col.Plain("type"), Col.Plain("avatar_url"),
            Col.Plain("cover_url"), Col.Plain("default_currency_code"),
            Col.Plain("simplify_by_default"), Col.Plain("default_split_json"),
            Col.Plain("whiteboard"), Col.Plain("external_source"), Col.Plain("external_id"),
            Col.Ts("created_at"), Col.Ts("updated_at"), Col.Ts("archived_at"),
        ),
    ),
    SyncedTable(
        name = "group_member",
        cols = listOf(
            Col.Plain("id"), Col.Plain("group_id"), Col.Plain("person_id"),
            Col.Plain("role"), Col.Ts("joined_at"),
        ),
    ),
    SyncedTable(
        name = "expense",
        cols = listOf(
            Col.Plain("id"), Col.Plain("group_id"), Col.Plain("description"),
            Col.Plain("cost_minor_units"), Col.Plain("currency_code"), Col.Day("date"),
            Col.Plain("category_id"), Col.Plain("notes"), Col.Plain("created_by"),
            Col.Plain("split_strategy_json"), Col.Plain("is_payment"), Col.Plain("is_refund"),
            Col.DeviceUrl("receipt_url"), Col.Plain("repeat_interval"), Col.Ts("next_repeat_at"),
            Col.Plain("bundle_id"), Col.Plain("external_source"), Col.Plain("external_id"),
            Col.Ts("created_at"), Col.Ts("updated_at"), Col.Ts("deleted_at"),
        ),
    ),
    SyncedTable(
        name = "expense_share",
        cols = listOf(
            Col.Plain("id"), Col.Plain("expense_id"), Col.Plain("person_id"),
            Col.Plain("paid_minor_units"), Col.Plain("owed_minor_units"),
        ),
    ),
    SyncedTable(
        name = "comment",
        cols = listOf(
            Col.Plain("id"), Col.Plain("expense_id"), Col.Plain("author_id"),
            Col.Plain("content"), Col.Ts("created_at"), Col.Ts("updated_at"),
            Col.Ts("deleted_at"),
        ),
    ),
)

val syncSchema: Schema = Schema(SYNCED_TABLES.map { it.rawTable() })

/** Installed while signed in; every statement is `IF NOT EXISTS`. */
val crudTriggers: List<String> = SYNCED_TABLES.flatMap { it.triggers() }

/** Run on sign-out so a signed-out install writes without queueing uploads. */
val crudTriggerDrops: List<String> = SYNCED_TABLES.flatMap { it.triggerDrops() }

/** Parent tables first, so the server can authorize children against them. */
val crudSeeds: List<String> = SYNCED_TABLES.map { it.seed() }
