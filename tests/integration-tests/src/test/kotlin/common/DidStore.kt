package common

import net.serenitybdd.screenplay.Actor
import org.hyperledger.identus.client.models.ManagedDID

/**
 * Simple per-actor DID store to avoid relying on the last response across steps.
 */
object DidStore {
    private const val Key = "didStore"

    private fun Actor.didStore(): MutableMap<String, ManagedDID> =
        recall<MutableMap<String, ManagedDID>>(Key) ?: mutableMapOf<String, ManagedDID>().also { remember(Key, it) }

    fun Actor.storeDid(label: String, did: ManagedDID) {
        didStore()[label] = did
    }

    fun Actor.requireDid(label: String): ManagedDID =
        didStore()[label] ?: throw IllegalStateException("No DID stored under label '$label' for actor ${name}")
}
