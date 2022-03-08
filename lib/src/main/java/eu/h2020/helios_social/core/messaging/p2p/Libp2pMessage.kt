package eu.h2020.helios_social.core.messaging.p2p

import com.google.gson.*
import java.lang.reflect.Type


interface Libp2pMessage {
    var type: Libp2pMessageType?
    var id: String?
    var name: String?
    val data: Any?
    var close: Boolean?
    var error: Any?

    fun toJson(): String
    fun isValid(): Boolean

    class MsgDeserializer : JsonDeserializer<Libp2pMessage> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext
        ): Libp2pMessage {
            val objType = context.deserialize<Libp2pMessageType>(
                json?.asJsonObject?.get("type"),
                Libp2pMessageType::class.java
            )

            return when (objType) {
                Libp2pMessageType.EVENT -> context.deserialize<Libp2pMessageEvent>(
                    json,
                    Libp2pMessageEvent::class.java
                )
                Libp2pMessageType.STREAM -> context.deserialize<Libp2pMessageEvent>(
                    json,
                    Libp2pMessageStream::class.java
                )
                Libp2pMessageType.RETURN -> context.deserialize<Libp2pMessageEvent>(
                    json,
                    Libp2pMessageReturn::class.java
                )
                Libp2pMessageType.RETURN_ERROR -> context.deserialize<Libp2pMessageEvent>(
                    json,
                    Libp2pMessageReturn::class.java
                )
                Libp2pMessageType.CALL -> context.deserialize<Libp2pMessageEvent>(
                    json,
                    Libp2pMessageCall::class.java
                )
                null -> Libp2pMessageUntyped()
            }
        }
    }

    companion object {
        private val gsonBuilder = GsonBuilder()
        private val gson: Gson

        init {
            gson = gsonBuilder
                .registerTypeAdapter(Libp2pMessage::class.java, MsgDeserializer())
                .enableComplexMapKeySerialization()
                .create()
        }

        fun fromJson(data: String): Libp2pMessage? {
            return gson.fromJson(data, Libp2pMessage::class.java)
        }

        fun toJson(msg: Libp2pMessage): String {
            return gson.toJson(msg)
        }
    }
}

class Libp2pMessageUntyped(
    override var type: Libp2pMessageType? = null,
    override var id: String? = null,
    override var name: String? = null,
    override var data: Any? = null,
    override var close: Boolean? = null,
    override var error: Any? = null
) : Libp2pMessage {
    override fun toJson(): String = Libp2pMessage.toJson(this)
    override fun isValid(): Boolean = true
}