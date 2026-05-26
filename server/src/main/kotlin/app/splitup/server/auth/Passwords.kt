package app.splitup.server.auth

import de.mkammerer.argon2.Argon2Factory

/**
 * Argon2id password hashing. Tuned to ~250 ms on modern hardware:
 *   iterations = 3, memory = 64 MiB, parallelism = 2.
 *
 * These parameters are encoded in the resulting hash string, so future changes
 * remain backwards-compatible with previously hashed passwords.
 */
object Passwords {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    private const val ITERATIONS = 3
    private const val MEMORY_KIB = 65_536
    private const val PARALLELISM = 2

    fun hash(plain: CharArray): String =
        argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, plain)

    fun verify(hash: String, plain: CharArray): Boolean = argon2.verify(hash, plain)
}
