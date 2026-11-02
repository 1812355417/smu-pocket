package com.smu.pocket.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 教务 JSON 的解码入口。所有容错逻辑集中在这里，模型保持干净。
 */
object JwJson {

    val format: Json = Json {
        // 真实课表一行有 40 个字段，模型只声明其中十几个，其余必须忽略
        ignoreUnknownKeys = true
        // 教务偶尔给带转义/不严格的 JSON
        isLenient = true
        // 注意：没有用 coerceInputValues / explicitNulls（两者在不同版本里
        // 的实验状态不一致，容易踩 @OptIn）。这里的 null 容错由
        // FlexibleString / FlexibleInt 序列化器和"字段全可空"的设计自己兜住。
    }

    /**
     * 课表解码。三种形态都要能吃：
     * 1. 成功：`{"code":0,"data":[...],"message":"成功"}` → data 是数组
     * 2. 成功（裸数组）：`[...]`
     * 3. **失败**：`{"code":-401,"data":"","message":"尚未登录，请先登录"}` → data 是**空字符串**
     *
     * 第 3 种如果直接按 `List<CourseData>` 解，会抛
     * `JsonDecodingException: Expected start of the array '[', but had '"'`，
     * 把一个清清楚楚的「未登录」变成看不懂的解析错误。
     */
    fun timetable(body: String): TimetableResponse {
        val text = body.trimStart()

        if (text.startsWith("[")) {
            return TimetableResponse(data = format.decodeFromString<List<CourseData>>(text))
        }

        val root = runCatching { format.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return TimetableResponse()

        val code = (root["code"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val message = (root["message"] as? JsonPrimitive)?.content.orEmpty()

        // 失败时 data 是 ""，只有成功时才是数组
        val rows = root["data"] as? JsonArray ?: JsonArray(emptyList())

        return TimetableResponse(
            code = code,
            message = message,
            data = format.decodeFromJsonElement(rows),
        )
    }

    /**
     * 成绩解码。
     *
     * 实测形态（`课程成绩json.txt`）：
     * ```json
     * { "data": "", "rows": [...], "total": 50 }
     * ```
     * `rows` 与 `data` **平级**。另外顺手兼容 `{ "data": { "rows": [...] } }`
     * 这种嵌套形态（你原先以为的那种），免得换个接口就崩。
     */
    fun scores(body: String): ScoreResponse {
        val root = runCatching { format.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return ScoreResponse()

        val directRows = root["rows"] as? JsonArray
        val nestedData = root["data"] as? JsonObject
        val nestedRows = nestedData?.get("rows") as? JsonArray
        val rowsJson = directRows ?: nestedRows ?: JsonArray(emptyList())

        val total = (root["total"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: (nestedData?.get("total") as? JsonPrimitive)?.content?.toIntOrNull()
            ?: rowsJson.size

        return ScoreResponse(
            code = (root["code"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
            message = (root["message"] as? JsonPrimitive)?.content.orEmpty(),
            data = (root["data"] as? JsonPrimitive)?.content.orEmpty(),
            rows = format.decodeFromJsonElement(rowsJson),
            total = total,
        )
    }
}
