package app.splitup.ui.oauth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Single-shot channel that the platform shell uses to deliver OAuth redirect
 * payloads to whatever view-model is waiting for them.
 *
 * The Android intent handler in MainActivity posts to this when the user returns
 * from Splitwise's authorize page. The importer screen collects from it.
 */
class OAuthCallbackBus {
    private val channel = Channel<Payload>(capacity = Channel.BUFFERED)

    val events: Flow<Payload> = channel.receiveAsFlow()

    suspend fun publish(payload: Payload) { channel.send(payload) }

    data class Payload(
        val provider: String,
        val code: String?,
        val state: String?,
        val error: String? = null,
    )
}
