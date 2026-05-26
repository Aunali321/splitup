package app.splitup.shared.util

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual fun httpEngine(): HttpClientEngine = CIO.create()
