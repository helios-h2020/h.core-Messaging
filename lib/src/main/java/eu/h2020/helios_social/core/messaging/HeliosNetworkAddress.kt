package eu.h2020.helios_social.core.messaging

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement

data class HeliosNetworkAddress(
        var egoId: String? = null,
        var networkId: String? = null,
        var privateKey: String? = null,
        var publicKey: String? = null,
        var networkAddress: MutableList<String>? = null) {

    companion object {
        private val gsonBuilder = GsonBuilder()
        private val gson: Gson

        init {
            gson = gsonBuilder
                    .enableComplexMapKeySerialization()
                    .create()
        }

        fun fromJson(data: String): HeliosNetworkAddress? {
            return gson.fromJson(data, HeliosNetworkAddress::class.java)
        }

        fun fromJson(address: JsonElement): HeliosNetworkAddress? {
            return gson.fromJson(address, HeliosNetworkAddress::class.java)
        }

        fun toJson(address: HeliosNetworkAddress): String {
            return gson.toJson(address)
        }

        fun fromMap(obj: Map<String, Any?>): HeliosNetworkAddress {
            val address = HeliosNetworkAddress()

            address.egoId = obj["egoId"] as? String?
            address.networkId = obj["networkId"] as? String?
            address.privateKey = obj["privateKey"] as? String?
            address.publicKey = obj["publicKey"] as? String?


            address.networkAddress = (obj["networkAddress"] as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.toMutableList()

            return address
        }
    }
}
