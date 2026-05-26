package app.splitup.shared.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Generates IDs that are globally unique without coordination — required for
 * offline-first sync. Splitwise uses the same pattern with their `guid` field.
 */
fun interface IdGenerator {
    fun next(): String
}

@OptIn(ExperimentalUuidApi::class)
object UuidIdGenerator : IdGenerator {
    override fun next(): String = Uuid.random().toString()
}
