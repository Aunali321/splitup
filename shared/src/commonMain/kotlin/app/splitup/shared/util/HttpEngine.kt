package app.splitup.shared.util

import io.ktor.client.engine.HttpClientEngine

/**
 * Per-target factory for the Ktor engine used by the Splitwise client.
 * Android → OkHttp, iOS → Darwin, JVM/desktop → CIO.
 */
expect fun httpEngine(): HttpClientEngine
