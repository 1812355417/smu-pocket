package com.smu.pocket.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * 同一个字段在不同请求里类型不一样，这是南医大教务的真实情况，不是我多虑：
 *
 * - `zc`：整学期总表返回 `"12,13"`（字符串），按周返回 `8`（数字）
 * - `zcjfs`：实测是 `88.2`（数字），其它系统/其它接口可能给 `"88.20"`
 *
 * 直接声明成 String 或 Double，都会在另一条路径上抛
 * `JsonDecodingException: Expected string, but had numeric literal`。
 * 这个异常在运行时才炸，而且只在切到那条查询条件时才炸 —— 最难查的一类 bug。
 *
 * 所以统一用「柔性」序列化器：什么进来都转成目标类型，转不动给默认值。
 * 宁可某个字段解析成 0，也不要整页崩掉。
 */
object FlexibleStringSerializer : KSerializer<String> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder
            ?: return runCatching { decoder.decodeString() }.getOrDefault("")

        val element = json.decodeJsonElement()
        if (element is JsonNull) return ""
        // JsonPrimitive.content 对字符串和数字都能给出正确文本："88.20" → 88.20，"88.2" → 88.2
        return if (element is JsonPrimitive) element.content else element.toString()
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

object FlexibleIntSerializer : KSerializer<Int> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder
            ?: return runCatching { decoder.decodeInt() }.getOrDefault(0)

        val element = json.decodeJsonElement()
        if (element is JsonNull) return 0

        val raw = (if (element is JsonPrimitive) element.content else element.toString()).trim()
        return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}
