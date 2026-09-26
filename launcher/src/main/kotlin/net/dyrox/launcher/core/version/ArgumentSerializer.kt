package net.dyrox.launcher.core.version

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads both argument shapes used in version JSONs: `"--foo"` and `{"rules": [...], "value": ...}`. */
object ArgumentSerializer : KSerializer<Argument> {
    private val rulesSerializer = ListSerializer(Rule.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("net.dyrox.launcher.core.version.Argument")

    override fun deserialize(decoder: Decoder): Argument {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Argument can only be read from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> Argument(listOf(element.content))
            is JsonObject -> {
                val rules = element["rules"]?.let { input.json.decodeFromJsonElement(rulesSerializer, it) }.orEmpty()
                val values = when (val value = element["value"]) {
                    is JsonPrimitive -> listOf(value.content)
                    is JsonArray -> value.map { it.jsonPrimitive.content }
                    else -> throw SerializationException("Argument object without a value: $element")
                }
                Argument(values, rules)
            }
            else -> throw SerializationException("Unexpected argument element: $element")
        }
    }

    override fun serialize(encoder: Encoder, value: Argument) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Argument can only be written as JSON")
        val element = if (value.rules.isEmpty() && value.values.size == 1) {
            JsonPrimitive(value.values.single())
        } else {
            buildJsonObject {
                if (value.rules.isNotEmpty()) put("rules", output.json.encodeToJsonElement(rulesSerializer, value.rules))
                put("value", JsonArray(value.values.map(::JsonPrimitive)))
            }
        }
        output.encodeJsonElement(element)
    }
}
