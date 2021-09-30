package eu.h2020.helios_social.core.messaging.nodejs

import com.google.gson.*
import java.lang.reflect.Type


interface NodejsMessage {
    var type: NodejsMessageType?
    var id: String?
    var name: String?
    val data: Any?
    var close: Boolean?
    var error: Any?

    fun toJson(): String
    fun isValid(): Boolean

    class MsgDeserializer : JsonDeserializer<NodejsMessage> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext
        ): NodejsMessage {
            val objType = context.deserialize<NodejsMessageType>(
                json?.asJsonObject?.get("type"),
                NodejsMessageType::class.java
            )

            return when (objType) {
                NodejsMessageType.EVENT -> context.deserialize<NodejsMessageEvent>(
                    json,
                    NodejsMessageEvent::class.java
                )
                NodejsMessageType.STREAM -> context.deserialize<NodejsMessageEvent>(
                    json,
                    NodejsMessageStream::class.java
                )
                NodejsMessageType.RETURN -> context.deserialize<NodejsMessageEvent>(
                    json,
                    NodejsMessageReturn::class.java
                )
                NodejsMessageType.RETURN_ERROR -> context.deserialize<NodejsMessageEvent>(
                    json,
                    NodejsMessageReturn::class.java
                )
                NodejsMessageType.CALL -> context.deserialize<NodejsMessageEvent>(
                    json,
                    NodejsMessageCall::class.java
                )
                null -> NodejsMessageUntyped()
            }
        }
    }

    companion object {
        private val gsonBuilder = GsonBuilder()
        private val gson: Gson

        init {
            gson = gsonBuilder
                .registerTypeAdapter(NodejsMessage::class.java, MsgDeserializer())
                .enableComplexMapKeySerialization()
                .create()
        }

        fun fromJson(data: String): NodejsMessage? {
            return gson.fromJson(data, NodejsMessage::class.java)
        }

        fun toJson(msg: NodejsMessage): String {
            return gson.toJson(msg)
        }
    }
}

class NodejsMessageUntyped(
    override var type: NodejsMessageType? = null,
    override var id: String? = null,
    override var name: String? = null,
    override var data: Any? = null,
    override var close: Boolean? = null,
    override var error: Any? = null
) : NodejsMessage {
    override fun toJson(): String = NodejsMessage.toJson(this)
    override fun isValid(): Boolean = true
}