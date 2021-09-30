package eu.h2020.helios_social.core.messaging.nodejs

import com.google.gson.annotations.Expose

class NodejsMessageReturn : NodejsMessage {
    override var type: NodejsMessageType? = NodejsMessageType.RETURN
    override var id: String?
    override var name: String?
    override var data: Any? = null
    override var error: Any? = null

    @Expose(serialize = false, deserialize = false)
    override var close: Boolean? = null

    override fun toJson(): String =
        NodejsMessage.toJson(this)

    override fun isValid(): Boolean =
        (type == NodejsMessageType.RETURN || type == NodejsMessageType.RETURN_ERROR) &&
                !id.isNullOrEmpty()

    fun hasError(): Boolean =
        type == NodejsMessageType.RETURN_ERROR

    constructor(id: String? = null, name: String? = null, data: Any? = null, error: Any? = null) {
        this.id = id
        this.name = name
        this.data = data
        this.error = error
    }
}
